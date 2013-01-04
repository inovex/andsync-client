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
package de.inovex.andsync.lucene;

import org.apache.lucene.analysis.Analyzer;
import de.inovex.andsync.AndSyncApplication;
import java.io.File;
import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.bson.BSONObject;
import org.bson.types.ObjectId;
import static de.inovex.andsync.Constants.*;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class Cache {
	
	private Directory store;
	private IndexWriter writer;
	
	public Cache() throws IOException {
		File cacheDir = AndSyncApplication.getAppContext().getCacheDir();
		store = new SimpleFSDirectory(cacheDir);
		Analyzer a = new StandardAnalyzer(Version.LUCENE_40);
		//Codec.setDefault(new Lucene40Codec());
		//IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_40 , a);
		//writer = new IndexWriter(store, config);
	}
	
	public void save(BSONObject bson) throws IOException {
		
		ObjectId id = (ObjectId)bson.get(MONGO_ID);
		if(id == null) {
			throw new IllegalArgumentException("Cannot save object without an id.");
		}
		
		writer.addDocument(new CacheDocument(bson).getLuceneDoc());
	}
	
}
