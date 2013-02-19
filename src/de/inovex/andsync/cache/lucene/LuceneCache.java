/*
 * Copyright 2012 Tim Roes <tim.roes@inovex.de>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inovex.andsync.cache.lucene;

import de.inovex.andsync.cache.CacheDocument;
import android.util.Log;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.IndexReader;
import java.util.Collection;
import org.apache.lucene.codecs.lucene41.Lucene41Codec;
import java.util.List;
import com.mongodb.DBObject;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.analysis.Analyzer;
import de.inovex.andsync.AndSyncApplication;
import de.inovex.andsync.cache.Cache;
import de.inovex.andsync.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.bson.types.ObjectId;
import static de.inovex.andsync.Constants.*;

/**
 * Implements {@link Cache} using Apache Lucene to store the objects.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class LuceneCache implements Cache {
	
	private static final String LUCENE_CACHE_DIR = "andsync";
	
	private Directory mStore;
	private IndexWriter mWriter;
	private IndexReader mReader;
	private IndexSearcher mSearcher;
	
	public LuceneCache() throws IOException {
		
		boolean cacheCreated = false;
		boolean retried = false;
		while(!cacheCreated) {
			
			// Try closing the store if perhaps opened from last try.
			if(mStore != null) {
				mStore.close();
			}
			
			File cacheDir = new File(AndSyncApplication.getAppContext().getExternalCacheDir(), LUCENE_CACHE_DIR);
			if(!cacheDir.exists()) {
				cacheDir.mkdir();
			}
			
			// Use NativeFSLockFactory, so we don't need to unlock something, so we don't need to
			// force the user to tell us about his app's lifecycle.
			mStore = new SimpleFSDirectory(cacheDir, new NativeFSLockFactory());

			Analyzer a = new StandardAnalyzer(Version.LUCENE_41);
			Codec.setDefault(new Lucene41Codec());

			try {
				IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_41, a);
				//config.setRAMBufferSizeMB(5);
				mWriter = new IndexWriter(mStore, config);
				mWriter.commit();
			
				mReader = DirectoryReader.open(mStore);
				mSearcher = new IndexSearcher(mReader);
				
				cacheCreated = true;
				
			} catch(IOException ex) {
				if(mWriter != null) mWriter.close();
				Log.w(LOG_TAG, String.format("Cache dir was corrupted, reseting cache. [Caused by: %s]", ex.getMessage()));
				if(!retried) {
					FileUtil.delete(cacheDir);
					retried = true;
				} else {
					throw new IOException("Cannot create cache dir.", ex);
				}
				continue;
			}
			
		}
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<CacheDocument> getAll(String collection) {
		
		try {
			TermQuery tq = new TermQuery(LuceneCacheDocument.getTermForCollection(collection));
			if(mReader.numDocs() > 0) {
				ScoreDoc[] docs = mSearcher.search(tq, mReader.numDocs()).scoreDocs;
				return convertScoreDocs(docs);
			}
		} catch (IOException ex) {
			Log.e("Cannot get documents from cache. [Caused by: %s]", ex.getMessage());
		}
		
		return new ArrayList<CacheDocument>(0);
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override	
	public CacheDocument getById(ObjectId id) {
		try {
			return getDocById(id);
		} catch(IOException ex) {
			Log.e("Cannot get document from cache. [Caused by: %s]", ex.getMessage());
			return null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<CacheDocument> getUntransmitted() {
		
		List<CacheDocument> untransmitted = new LinkedList<CacheDocument>();
		
		ScoreDoc[] docs;
		try {
			docs = mSearcher.search(LuceneCacheDocument.getQueryForUntransmitted(), mReader.numDocs()).scoreDocs;
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Could not get list of untransmitted objects from cache. [Caused by: %s]", 
					ex.getMessage()));
			return untransmitted;
		}
		
		for(ScoreDoc doc : docs) {
			try {
				LuceneCacheDocument cacheDoc = getCacheDoc(doc);
				untransmitted.add(cacheDoc);
			} catch (IOException ex) {
				Log.w(LOG_TAG, String.format("Could not retreive untransmitted object. [Caused by: %s]", ex.getMessage()));
			}
		}
		
		return untransmitted;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, LuceneCacheDocument.TransmittedState.NEVER_TRANSMITTED);
		}
	}
		
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(String collection, DBObject dbo) {
		putObject(collection, dbo, LuceneCacheDocument.TransmittedState.NEVER_TRANSMITTED);
		Log.w("ANDSYND", String.format("Put never transmitted object into cache [id: %s]", dbo.get(MONGO_ID)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putUpdated(String collection, DBObject dbo) {
		putObject(collection, dbo, LuceneCacheDocument.TransmittedState.UPDATE_NOT_TRANSMITTED);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putUpdated(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, LuceneCacheDocument.TransmittedState.UPDATE_NOT_TRANSMITTED);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putTransmitted(String Collection, DBObject dbo) {
		putObject(Collection, dbo, LuceneCacheDocument.TransmittedState.TRANSMITTED);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putTransmitted(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, LuceneCacheDocument.TransmittedState.TRANSMITTED);
		}
	}
	
	private synchronized void putObject(String collection, DBObject dbo, LuceneCacheDocument.TransmittedState transmitted) {
		if(dbo == null) return;
		ObjectId id = (ObjectId)dbo.get(MONGO_ID);
		if(id == null) {
			throw new IllegalArgumentException("Cannot save object without an id.");
		}
		
		try {
			LuceneCacheDocument cacheDoc = new LuceneCacheDocument(collection, dbo, transmitted);
			mWriter.updateDocument(cacheDoc.getIdTerm(), cacheDoc);
		} catch(Exception ex) {
			Log.w(LOG_TAG, String.format("Cannot cache object with id %s. [Caused by: %s]", 
					id.toString(), ex.getMessage()));
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void delete(String collection, ObjectId id) {
		try {
			mWriter.deleteDocuments(LuceneCacheDocument.getTermForId(id));
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Cannot remove object with id %s from cache. [Caused by: %s]", 
					id.toString(), ex.getMessage()));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void delete(String collection, long timestamp) {
		try {
			mWriter.deleteDocuments(LuceneCacheDocument.getQueryForDeletion(collection, timestamp));
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Cannot remove objects from cache. [Caused by: %s]", ex.getMessage()));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void commit() {
		try {
			mWriter.commit();
		} catch (IOException ex) {
			Log.e("Cache cannot commit changes made. [Caused by: %s]", ex.getMessage());
		}
	}
	
	private LuceneCacheDocument getDocById(ObjectId id) throws IOException {
		TermQuery tq = new TermQuery(LuceneCacheDocument.getTermForId(id));
		ScoreDoc[] sdocs = mSearcher.search(tq, 1).scoreDocs;
		if(sdocs.length < 1) {
			Log.e(LOG_TAG, String.format("Couldn't find object for id %s in cache.", id.toString()));
			return null;
		}
		return getCacheDoc(sdocs[0]);
	}
	
	private Collection<CacheDocument> convertScoreDocs(ScoreDoc[] scoreDocs) throws IOException {
		
		List<CacheDocument> dbos = new ArrayList<CacheDocument>(scoreDocs.length);
		
		for(ScoreDoc doc : scoreDocs) {
			dbos.add(getCacheDoc(doc));
		}
		
		return dbos;
		
	}
	
	private LuceneCacheDocument getCacheDoc(ScoreDoc doc) throws IOException {
		return new LuceneCacheDocument(mReader.document(doc.doc));
	}
	
}
