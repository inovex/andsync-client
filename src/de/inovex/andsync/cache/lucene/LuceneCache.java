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
import de.inovex.andsync.util.TimeUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
				Log.w(LOG_TAG, String.format("Cache dir was corrupted, clearing cache. [Caused by: %s]", ex.getMessage()));
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
	public Collection<DBObject> getAll(String collection) {
		
		try {
			TermQuery tq = new TermQuery(CacheDocument.getTermForCollection(collection));
			if(mReader.numDocs() > 0) {
				ScoreDoc[] docs = mSearcher.search(tq, mReader.numDocs()).scoreDocs;
				return convertScoreDocs(docs);
			}
		} catch (IOException ex) {
			Log.e("Cannot get documents from cache. [Caused by: %s]", ex.getMessage());
		}
		
		return new ArrayList<DBObject>(0);
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override	
	public DBObject getById(ObjectId id) {
		try {
			return getDocById(id).getDBObject();
		} catch(IOException ex) {
			Log.e("Cannot get document from cache. [Caused by: %s]", ex.getMessage());
			return null;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, false);
		}
	}
		
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(String collection, DBObject dbo) {
		putObject(collection, dbo, false);
	}

	/**
	 * {@inheritDoc}
	 */
	public void putTransmitted(String Collection, DBObject dbo) {
		putObject(Collection, dbo, true);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void putTransmitted(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, true);
		}
	}
	
	private synchronized void putObject(String collection, DBObject dbo, boolean transmitted) {
		if(dbo == null) return;
		ObjectId id = (ObjectId)dbo.get(MONGO_ID);
		if(id == null) {
			throw new IllegalArgumentException("Cannot save object without an id.");
		}
		
		try {
			CacheDocument cacheDoc = new CacheDocument(collection, dbo, 
					transmitted ? TimeUtil.getTimestamp() : 0L);
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
			mWriter.deleteDocuments(CacheDocument.getTermForId(id));
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Cannot remove object with id %s from cache. [Caused by: %s]", 
					id.toString(), ex.getMessage()));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void delete(String collection, long timestamp) {
		try {
			mWriter.deleteDocuments(CacheDocument.getQueryForDeletion(collection, timestamp));
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Cannot remove objects from cache. [Caused by: %s]", ex.getMessage()));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public synchronized void transmitted(String collection, ObjectId id) {
		Log.i("ANDSYNC", "Mark ObjectId " + id.toString() + " as transmitted.");
		
		try {
			CacheDocument doc = getDocById(id);
			doc.setTransmitted(TimeUtil.getTimestamp());
			mWriter.updateDocument(doc.getIdTerm(), doc);
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Error marking document with id %s in cache as transmitted. "
					+ "[Caused by: %s]", id.toString(), ex.getMessage()));
		}
		
	}	

	/**
	 * {@inheritDoc}
	 */
	public void commit() {
		try {
			mWriter.commit();
		} catch (IOException ex) {
			Log.e("Cache cannot commit changes made. [Caused by: %s]", ex.getMessage());
		}
	}
	
	private CacheDocument getDocById(ObjectId id) throws IOException {
		TermQuery tq = new TermQuery(CacheDocument.getTermForId(id));
		ScoreDoc[] sdocs = mSearcher.search(tq, 1).scoreDocs;
		if(sdocs.length < 1) {
			Log.e(LOG_TAG, String.format("Couldn't find object for id %s in cache.", id.toString()));
			return null;
		}
		return new CacheDocument(mReader.document(sdocs[0].doc));
	}
	
	private Collection<DBObject> convertScoreDocs(ScoreDoc[] scoreDocs) throws IOException {
		
		List<DBObject> dbos = new ArrayList<DBObject>(scoreDocs.length);
		
		for(ScoreDoc doc : scoreDocs) {
			dbos.add(new CacheDocument(mReader.document(doc.doc)).getDBObject());
		}
		
		return dbos;
		
	}
	
}
