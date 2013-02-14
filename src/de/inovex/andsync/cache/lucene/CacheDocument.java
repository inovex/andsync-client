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

import com.mongodb.DBObject;
import static de.inovex.andsync.Constants.*;
import de.inovex.andsync.util.BsonConverter;
import de.inovex.andsync.util.TimeUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class CacheDocument implements Iterable<IndexableField> {
	
	private static final String KEY_ID = "ID";
	private static final String DATA_ID = "DATA";
	private static final String CLASS_ID = "CLASS";
	private static final String UPDATE_ID = "UPDATE";
	private static final String TRANSMITTED_ID = "TRANSMITTED";

	private Term idTerm;
	
	private final Map<String,IndexableField> fields = new HashMap<String, IndexableField>(3);
	
	/**
	 * Creates a new {@link CacheDocument} from a {@link Document Lucene document}. If not all
	 * required fields exist in the document an {@link IllegalArgumentException} will be thrown.
	 * 
	 * @param doc The lucene document from which to create a {@code CacheDocument}.
	 */
	public CacheDocument(Document doc) {
		
		String id = doc.get(KEY_ID);
		if(!ObjectId.isValid(id)) {
			throw new IllegalArgumentException("Cache document didn't contain a valid ObjectId.");
		}
		
		String clazz = doc.get(CLASS_ID);
		if(clazz == null || clazz.length() <= 0) {
			throw new IllegalArgumentException("Cache document didn't contain a class name.");
		}
		
		BytesRef bson = doc.getBinaryValue(DATA_ID);
		if(bson == null) {
			throw new IllegalArgumentException("Cache document didn't contain data.");
		}
		
		StoredField updated = (StoredField)doc.getField(UPDATE_ID);		
		if(updated == null) {
			throw new IllegalArgumentException("Cache document didn't contain an update timestamp.");
		}
		
		StoredField transmitted = (StoredField)doc.getField(TRANSMITTED_ID);
		if(transmitted == null) {
			throw new IllegalArgumentException("Cache document didn't contain an transmitted field.");
		}
		
		initializeFields(id, clazz, bson.bytes, updated.numericValue().longValue(), 
				transmitted.numericValue().longValue());
		
	}
	
	public CacheDocument(String collection, DBObject object, long timestamp) {
		
		Object id = object.get(MONGO_ID);
		if(id == null || !(id instanceof ObjectId)) {
			throw new IllegalArgumentException("DBObject didn't contain a valid ObjectId.");
		}
		
		initializeFields(id.toString(), collection, BsonConverter.bsonObjectAsBytes(object), 
				TimeUtil.getTimestamp(), timestamp);
		
		// TODO: Remove string representation, just for debugging purposes
		fields.put("TO_STRING", new StoredField("TO_STRING", object.toString()));
		
	}

	private void initializeFields(String id, String clazz, byte[] bsonData, long update, long transmitted) {
		fields.put(KEY_ID, new StringField(KEY_ID, id, Field.Store.YES));
		fields.put(CLASS_ID, new StringField(CLASS_ID, clazz, Field.Store.YES));
		fields.put(DATA_ID, new StoredField(DATA_ID, bsonData));
		
		fields.put(UPDATE_ID, new LongField(UPDATE_ID, update, Field.Store.YES));
		fields.put(TRANSMITTED_ID, new LongField(TRANSMITTED_ID, transmitted, Field.Store.YES));
	}
	
	public DBObject getDBObject() {
		return BsonConverter.fromBson(fields.get(DATA_ID).binaryValue().bytes);
	}
	
	/**
	 * Set the transmitted field of this document to a specific timestamp.
	 * 
	 * @param timestamp The timestamp to set.
	 */
	public void setTransmitted(long timestamp) {
		IndexableField field = fields.get(TRANSMITTED_ID);
		LongField lf;
		if(field == null || !(field instanceof LongField)) {
			lf = new LongField(TRANSMITTED_ID, timestamp, Field.Store.YES);
		} else {
			lf = (LongField)field;
			lf.setLongValue(timestamp);
		}
		fields.put(TRANSMITTED_ID, lf);
	}
	
	/**
	 * Returns the Lucene {@link Term} to use to find that (or any previous cached object with that
	 * id) in the cache.
	 * 
	 * @return The {@link Term} to find the object by its id.
	 */
	public Term getIdTerm() {
		if(idTerm == null) {
			idTerm = new Term(KEY_ID, fields.get(KEY_ID).stringValue());
		}
		return idTerm;
	}

	/**
	 * Returns {@link Iterator} for all the fields of this document.
	 * 
	 * @return Iterator for the fields.
	 */
	public Iterator<IndexableField> iterator() {
		return fields.values().iterator();
	}

	public static Term getTermForId(ObjectId id) {
		return new Term(KEY_ID, id.toString());
	}

	public static Term getTermForCollection(String collection) {
		return new Term(CLASS_ID, collection);
	}

	public static Query getQueryForDeletion(String collection, long timestamp) {
		BooleanQuery query = new BooleanQuery();
		query.add(NumericRangeQuery.newLongRange(UPDATE_ID, null, timestamp, true, false), Occur.MUST);
		// Only delete old entries that has been transmitted to server already. So don't delete 
		// entries that this client has created, but hadn't been transfered to server yet.
		query.add(NumericRangeQuery.newLongRange(TRANSMITTED_ID, 0L, 0L, true, true), Occur.MUST_NOT);
		return query;
	}
	
}