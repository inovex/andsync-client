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

import static de.inovex.andsync.Constants.*;
import de.inovex.andsync.util.BsonConverter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.bson.BSONObject;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class CacheDocument {
	
	private static final String KEY_ID = "ID";
	private static final String DATA_ID = "DATA";
	
	private final Document doc;
	
	public CacheDocument(BSONObject object) {
		
		Object id = object.get(MONGO_ID);
		if(id == null || !(id instanceof ObjectId)) {
			throw new IllegalArgumentException("BSONObject didn't contain a valid ObjectId.");
		}
		
		
		doc = new Document();
		//doc.add(new Field(KEY_ID, id.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		//doc.add(new Field(DATA_ID, id.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new StoredField(KEY_ID, id.toString()));
		doc.add(new StoredField(DATA_ID, BsonConverter.bsonObjectAsBytes(object)));
		
	}
	
	Document getLuceneDoc() {
		return doc;
	}
	
}