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
package de.inovex.andsync.cache;

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
import de.inovex.andsync.util.Log;
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
		File cacheDir = new File(AndSyncApplication.getAppContext().getCacheDir(), LUCENE_CACHE_DIR);
		// Use NativeFSLockFactory, so we don't need to unlock something, so we don't need to
		// force the user to tell us about his app's lifecycle.
		mStore = new SimpleFSDirectory(cacheDir, new NativeFSLockFactory());
	
		Analyzer a = new StandardAnalyzer(Version.LUCENE_41);
		Codec.setDefault(new Lucene41Codec());
		
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_41, a);
		config.setRAMBufferSizeMB(5);
		mWriter = new IndexWriter(mStore, config);
		mWriter.commit();
		
		mReader = DirectoryReader.open(mStore);
		mSearcher = new IndexSearcher(mReader);
		
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
			TermQuery tq = new TermQuery(CacheDocument.getTermForId(id));
			ScoreDoc sdoc = mSearcher.search(tq, 1).scoreDocs[0];
			return convertScoreDoc(sdoc);
		} catch(IOException ex) {
			Log.e("Cannot get document from cache. [Caused by: %s]", ex.getMessage());
			return null;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void put(String collection, List<DBObject> dbos) {
		for(DBObject dbo : dbos) {
			put(collection, dbo);
		}
	}
		
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void put(String collection, DBObject dbo) {
		if(dbo == null) return;
		ObjectId id = (ObjectId)dbo.get(MONGO_ID);
		if(id == null) {
			throw new IllegalArgumentException("Cannot save object without an id.");
		}
		
		try {
			CacheDocument cacheDoc = new CacheDocument(collection, dbo);
			mWriter.updateDocument(cacheDoc.getIdTerm(), cacheDoc);
		} catch(Exception ex) {
			Log.e("Cannot cache object with id %s. [Caused by: %s]", id.toString(), ex.getMessage());
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
			Log.e("Cannot remove object with id %s from cache. [Caused by: %s]", id.toString(), ex.getMessage());
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
	
	private Collection<DBObject> convertScoreDocs(ScoreDoc[] scoreDocs) throws IOException {
		
		List<DBObject> dbos = new ArrayList<DBObject>(scoreDocs.length);
		
		for(ScoreDoc doc : scoreDocs) {
			dbos.add(convertScoreDoc(doc));
		}
		
		return dbos;
		
	}
	
	private DBObject convertScoreDoc(ScoreDoc scoreDoc) throws IOException {
		CacheDocument cdoc = new CacheDocument(mReader.document(scoreDoc.doc));
		return cdoc.getDBObject();
	}
	
}
