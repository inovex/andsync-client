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

import android.util.Log;
import de.inovex.andsync.cache.Cache;
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
	private Cache mCache;
	
	public RestStorageHandler(RestClient restClient, Cache cache) {
		assert restClient != null && cache != null;
		this.mRest = restClient;
		this.mCache = cache;
	}

	public void onSave(final String collection, final DBObject dbo) {
		if (dbo.containsField("_id")) {
			// Object already has an id, update object
			// Put the object also in cache (see CacheStorageHandler.onSave() for explanation)
			mCache.put(collection, dbo);
			updateObject(collection, dbo);
		} else {
			// Object is new in storage
			// Create an id for the DBObject.
			dbo.put("_id", ObjectId.get());
			// Put the object also in cache (see CacheStorageHandler.onSave() for explanation)
			mCache.put(collection, dbo);
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

		final byte[] data = BsonConverter.toBSON(dbo);
		try {
			mRest.put(data, REST_OBJECT_PATH, collection);
		} catch (RestException ex) {
			Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public Collection<DBObject> onGet(final String collection, final FieldList fl) {

		long nano = System.currentTimeMillis();
		
		RestResponse response;
		try {
			response = mRest.get(REST_OBJECT_PATH, collection);
		} catch (RestException ex) {
			Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

		final List<DBObject> objects = BsonConverter.fromBsonList(response.data);
		
		// TODO remove time recording
		System.out.println("-- Start Caching --");
		
		mCache.put(collection, objects);

		System.out.println("-- End Caching objects --");
		Log.w("TOSLOW", String.format("-- Elapsed Time " + (System.currentTimeMillis() - nano)/1000.0 + "s -- "));
		
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
			DBObject obj = BsonConverter.fromBSONFirst(response.data);
			mCache.put(dbref.getRef(), obj);
			return obj;
		} catch (RestException ex) {
			Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
		}
		
		return null;
		
	}

	public void onDelete(String collection, ObjectId oi) {
		try {
			mRest.delete(REST_OBJECT_PATH, collection, oi.toString());
		} catch (RestException ex) {
			Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
	
}
