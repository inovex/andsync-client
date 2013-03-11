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

import org.apache.lucene.search.Query;
import de.inovex.andsync.cache.CacheDocument;
import android.util.Log;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.IndexReader;
import java.util.Collection;
import org.apache.lucene.codecs.lucene41.Lucene41Codec;
import java.util.List;
import com.mongodb.DBObject;
import de.inovex.andsync.AndSync;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.analysis.Analyzer;
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
	
	public LuceneCache() throws IOException {
		
		boolean cacheCreated = false;
		boolean retried = false;
		while(!cacheCreated) {
			
			// Try closing the store if perhaps opened from last try.
			if(mStore != null) {
				mStore.close();
			}
			
			File cacheDir = new File(AndSync.getContext().getExternalCacheDir(), LUCENE_CACHE_DIR);
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
				mWriter = new IndexWriter(mStore, config);
				mWriter.commit();
				
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
	 * Returns an {@link IndexSearcher} instance to search for objects.
	 * This instance must be passed to {@link #releaseSearcher(org.apache.lucene.search.IndexSearcher)}
	 * when it isn't used anymore.
	 * 
	 * The current implementation creates a new instance each time this method is called, to make
	 * sure the searcher always sees the newest index state.
	 * 
	 * @return An IndexSearcher instance.
	 * @throws IOException
	 */
	private IndexSearcher getSearcher() throws IOException {
		IndexReader reader = DirectoryReader.open(mWriter, true);
		return new IndexSearcher(reader);
	}
	
	/**
	 * Releases an {@link IndexSearcher} after it has been used.
	 * This must be called for each {@link IndexSearcher} retrieved via {@link #getSearcher()}.
	 * After this method is called no method on that {@code IndexSearcher} must be called anymore.
	 * 
	 * @param searcher The {@link IndexSearcher} no longer needed.
	 */
	private void releaseSearcher(IndexSearcher searcher) {
		if(searcher == null) return;
		try {
			searcher.getIndexReader().close();
		} catch (IOException ex) {
			Log.i(LOG_TAG, String.format("Couldn't close IndexReader successfully. [Caused by: %s]", 
					ex.getMessage()));
		}
	}
	
	private int getSearchLimit(IndexSearcher searcher) {
		int numDocs = searcher.getIndexReader().numDocs();
		return numDocs > 0 ? numDocs : 1;
	}
	
	private int getNumDocs(IndexSearcher searcher) {
		return searcher.getIndexReader().numDocs();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized Collection<CacheDocument> getAll(String collection) {
		
		IndexSearcher searcher = null;
		try {
			searcher = getSearcher();
			Query tq = LuceneCacheDocument.getTermForCollection(collection);
			if(getNumDocs(searcher) > 0) {
				ScoreDoc[] docs = searcher.search(tq, getSearchLimit(searcher)).scoreDocs;
				return convertScoreDocs(searcher, docs);
			}
		} catch (IOException ex) {
			Log.e(LOG_TAG, String.format("Cannot get documents from cache. [Caused by: %s]", ex.getMessage()));
		} finally {
			releaseSearcher(searcher);
		}
		
		return new ArrayList<CacheDocument>(0);
		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<ObjectId> getAllIds(String collection) {
		IndexSearcher searcher = null;
		try {
			searcher  = getSearcher();
			Query tq = LuceneCacheDocument.getTermForCollection(collection);
			if(getNumDocs(searcher) > 0) {
				ScoreDoc[] docs = searcher.search(tq, getSearchLimit(searcher)).scoreDocs;
				List<ObjectId> ids = new ArrayList<ObjectId>(docs.length);
				for(ScoreDoc doc : docs) {
					ids.add(getCacheDoc(searcher, doc).getObjectId());
				}
				return ids;
			}
		} catch(IOException ex) {
			Log.w(LOG_TAG, String.format("Cannot load all ids from cache. [Caused by: %s]", ex.getMessage()));
		} finally {
			releaseSearcher(searcher);
		}
		
		return new ArrayList<ObjectId>(0);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override	
	public synchronized CacheDocument getById(ObjectId id) {
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
	public synchronized Collection<CacheDocument> getUntransmitted() {
		
		List<CacheDocument> untransmitted = new LinkedList<CacheDocument>();
		
		ScoreDoc[] docs;
		IndexSearcher searcher = null;
		try {
			searcher = getSearcher();
			docs = searcher.search(LuceneCacheDocument.getQueryForUntransmitted(), 
					getSearchLimit(searcher)).scoreDocs;
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Could not get list of untransmitted objects from cache. [Caused by: %s]", 
					ex.getMessage()));
			releaseSearcher(searcher);
			return untransmitted;
		}
		
		for(ScoreDoc doc : docs) {
			try {
				LuceneCacheDocument cacheDoc = getCacheDoc(searcher, doc);
				untransmitted.add(cacheDoc);
			} catch (IOException ex) {
				Log.w(LOG_TAG, String.format("Could not retreive untransmitted object. [Caused by: %s]", ex.getMessage()));
			}
		}
		
		releaseSearcher(searcher);
		
		return untransmitted;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void put(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, LuceneCacheDocument.TransmittedState.NEVER_TRANSMITTED);
		}
	}
		
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void put(String collection, DBObject dbo) {
		putObject(collection, dbo, LuceneCacheDocument.TransmittedState.NEVER_TRANSMITTED);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void putUpdated(String collection, DBObject dbo) {
		putObject(collection, dbo, LuceneCacheDocument.TransmittedState.UPDATE_NOT_TRANSMITTED);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void putUpdated(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, LuceneCacheDocument.TransmittedState.UPDATE_NOT_TRANSMITTED);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void putTransmitted(String Collection, DBObject dbo) {
		putObject(Collection, dbo, LuceneCacheDocument.TransmittedState.TRANSMITTED);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void putTransmitted(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			putObject(collection, dbo, LuceneCacheDocument.TransmittedState.TRANSMITTED);
		}
	}
	
	private void putObject(String collection, DBObject dbo, LuceneCacheDocument.TransmittedState transmitted) {
		if(dbo == null) return;
		ObjectId id = (ObjectId)dbo.get(MONGO_ID);
		if(id == null) {
			throw new IllegalArgumentException("Cannot save object without an id.");
		}
		
		try {		
			// If object has never been transmitted to server, but updated on client, don't set its 
			// client state to UPDATED, but keep its never transmitted state, so it will be send
			// to server via PUT and not via POST.
			if(transmitted == CacheDocument.TransmittedState.UPDATE_NOT_TRANSMITTED
					&& getDocById(id).getState() == CacheDocument.TransmittedState.NEVER_TRANSMITTED) {
				transmitted = CacheDocument.TransmittedState.NEVER_TRANSMITTED;
			}
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
	public synchronized void markDeleted(String collection, ObjectId id) {
		try {
			LuceneCacheDocument doc = getDocById(id);
			Log.w("ANDSYNC", "Mark Deleted " + id.toString() + " object: " + String.valueOf(doc));
			if(doc != null && doc.getState() == CacheDocument.TransmittedState.NEVER_TRANSMITTED) {
				// If document should be marked as deleted but never has been transmitted to server,
				// just delete that object from cache.ok
				mWriter.deleteDocuments(doc.getIdTerm());
			} else if(doc != null) {
				doc.setTransmittedState(CacheDocument.TransmittedState.DELETED);
				mWriter.updateDocument(doc.getIdTerm(), doc);
			}
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Cannot mark object with id %s as deleted in cache. [Caused by: %s]", 
					id.toString(), ex.getMessage()));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public synchronized void deleted(String collection, ObjectId id) {
		try {
			Log.w("ANDSYNC", "Deleted " + id.toString());
			mWriter.deleteDocuments(LuceneCacheDocument.getTermForId(id));
		} catch (IOException ex) {
			Log.w(LOG_TAG, String.format("Could not delete object with id %s from cache. [Caused by: %s]",
					id.toString(), ex.getMessage()));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void deleted(String collection, long timestamp) {
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
	public synchronized void commit() {
		try {
			mWriter.commit();
		} catch (IOException ex) {
			Log.e("Cache cannot commit changes made. [Caused by: %s]", ex.getMessage());
		}
	}
	
	private LuceneCacheDocument getDocById(ObjectId id) throws IOException {
		IndexSearcher searcher = null;
		try {
			TermQuery tq = new TermQuery(LuceneCacheDocument.getTermForId(id));
			searcher = getSearcher();
			ScoreDoc[] sdocs = searcher.search(tq, 1).scoreDocs;
			if(sdocs.length < 1) {
				Log.e(LOG_TAG, String.format("Couldn't find object for id %s in cache.", id.toString()));
				return null;
			}
			return getCacheDoc(searcher, sdocs[0]);
		} finally {
			releaseSearcher(searcher);
		}
	}
	
	private Collection<CacheDocument> convertScoreDocs(IndexSearcher searcher, ScoreDoc[] scoreDocs) throws IOException {
		
		List<CacheDocument> dbos = new ArrayList<CacheDocument>(scoreDocs.length);
		
		for(ScoreDoc doc : scoreDocs) {
			dbos.add(getCacheDoc(searcher, doc));
		}
		
		return dbos;
		
	}
	
	private LuceneCacheDocument getCacheDoc(IndexSearcher searcher, ScoreDoc doc) throws IOException {
		return new LuceneCacheDocument(searcher.doc(doc.doc));
	}
	
}
