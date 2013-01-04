/*
 * Copyright 2013 Tim Roes <tim.roes@inovex.de>.
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
package de.inovex.andsync.manager;

import com.mongodb.DBObject;
import com.mongodb.DBRef;
import de.inovex.andsync.rest.RestClient;
import de.inovex.andsync.rest.RestClient.RestResponse;
import de.inovex.andsync.rest.RestException;
import de.inovex.andsync.util.BsonConverter;
import de.inovex.jmom.FieldList;
import de.inovex.jmom.Storage;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.types.ObjectId;
import static de.inovex.andsync.Constants.*;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class RestStorageHandler implements Storage.DBHandler {
	
	private RestClient mRest;
	
	public RestStorageHandler(RestClient restClient) {
		this.mRest = restClient;
	}

	public void onSave(String collection, DBObject dbo) {
			if (dbo.containsField("_id")) {
				// Object already has an id, update object
				updateObject(collection, dbo);
			} else {
				// Object is new in storage
				newObject(collection, dbo);
			}
		}
		
		private void updateObject(final String collection, final DBObject dbo) {
			final byte[] data = BsonConverter.toBSON(dbo);
			try {
				mRest.post(data, REST_OBJECT_PATH, collection);
			} catch (RestException ex) {
				Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		private void newObject(final String collection, final DBObject dbo) {
			// Create an id for the DBObject.
			dbo.put("_id", ObjectId.get());
			final byte[] data = BsonConverter.toBSON(dbo);
			try {
				mRest.put(data, REST_OBJECT_PATH, collection);
			} catch (RestException ex) {
				Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		public DBObject onGetFirst(String collection, FieldList fl) {
			RestResponse response = null;
			try {
				response = mRest.get(REST_OBJECT_PATH, collection);
			} catch (RestException ex) {
				Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
				return null;
			}
			final List<DBObject> objects = BsonConverter.fromBSON(response.data);
			return objects.get(0);
		}

		public Collection<DBObject> onGet(final String collection, final FieldList fl) {
					
			RestResponse response;
			try {
				response = mRest.get(REST_OBJECT_PATH, collection);
			} catch (RestException ex) {
				Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
				return null;
			}

			final List<DBObject> objects = BsonConverter.fromBSON(response.data);
			
			return objects;
			
		}

		public DBRef onCreateRef(String collection, DBObject dbo) {
			return new DBRef(null, collection, dbo.get("_id"));
		}

		public DBObject onFetchRef(DBRef dbref) {
			if(dbref == null || dbref.getId() == null)
				return null;

			RestResponse response;
			try {
				response = mRest.get(REST_OBJECT_PATH, dbref.getRef(), dbref.getId().toString());
			} catch (RestException ex) {
				Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
				return null;
			}
		
			return BsonConverter.fromBSONFirst(response.data);
		}

		public void onDelete(String collection, ObjectId oi) {
			try {
				mRest.delete(REST_OBJECT_PATH, collection, oi.toString());
			} catch (RestException ex) {
				Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	
}
