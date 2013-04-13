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

import de.inovex.andsync.rest.RepeatingRestClient;
import android.util.Log;
import de.inovex.andsync.cache.Cache;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import de.inovex.andsync.cache.CacheDocument;
import de.inovex.andsync.cache.CacheInformation;
import de.inovex.andsync.rest.RestClient;
import de.inovex.andsync.rest.RestClient.RestResponse;
import de.inovex.andsync.util.BsonConverter;
import de.inovex.andsync.util.TimeUtil;
import de.inovex.jmom.FieldList;
import de.inovex.jmom.Storage;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bson.types.ObjectId;
import static de.inovex.andsync.Constants.*;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class RestStorageHandler implements Storage.DBHandler {

	private final RepeatingRestClient mRest;
	private final CallCollector mCallCollector;
	private final Cache mCache;
	private final CacheInformation mCacheInformation;

	public RestStorageHandler(RestClient restClient, Cache cache) {
		assert restClient != null && cache != null;
		mRest = new RepeatingRestClient(restClient);
		mCallCollector = new CallCollector(cache, mRest);
		mCache = cache;
		mCacheInformation = cache.getCacheInformation();
		untransmitted();
	}
	
	/**
	 * Transmit all untransmitted changes or newly created objects in another {@link Thread}
	 * to the server.
	 */
	private synchronized void untransmitted() {
		
		new Thread(new Runnable() {	
			public void run() {
			
				// Get a list of all untransmitted objects
				Collection<CacheDocument> untransmitted = mCache.getUntransmitted();
				for(CacheDocument doc : untransmitted) {
					// Decide what to do with the object depending on its transmitted state
					switch(doc.getState()) {
						case DELETED:
							deleteObject(doc.getCollection(), (ObjectId)doc.getDBObject().get(MONGO_ID));
							break;
						case NEVER_TRANSMITTED:
							newObject(doc.getCollection(), doc.getDBObject());
							break;
						case UPDATE_NOT_TRANSMITTED:
							updateObject(doc.getCollection(), doc.getDBObject());
							break;
					}
				}
				
			}
		}).start();
		
	}

	/**
	 * Updates the change of an object to the server via a POST call. The call will be stored in
	 * the {@link #mCallCollector} so that multiple POST calls can be collected and bundled.
	 * 
	 * The call collector will make sure to mark the object as transmitted as soon as it has been.
	 * 
	 * This method will return immediately, before the object has been transmitted to the server.
	 * 
	 * @param collection
	 * @param dbo 
	 */
	private void updateObject(final String collection, final DBObject dbo) {
		mCallCollector.postCall(collection, dbo);
	}

	/**
	 * Transfer an object to the server, that has just been created on that device and never
	 * has been transfered to the server.
	 * 
	 * This will add a PUT call to the {@link #mCallCollector call collector}, so multiple PUT calls
	 * can be collected and bundled. The object should have been put to cache (marked as never transmitted)
	 * before this method is called. The call collector will take care of marking the object as
	 * transmitted as soon as it has been.
	 * 
	 * This method will return immediately, before the object has been transmitted to the server.
	 * 
	 * @param collection The collection of that object.
	 * @param dbo The object to transfer.
	 */
	private void newObject(final String collection, final DBObject dbo) {
		mCallCollector.putCall(collection, dbo);
	}
	
	/**
	 * Send the server a notification to delete an object. This will add a DELETE call to the 
	 * {@link #mCallCollector call collector}, so multiple DELETE calls can be collected and bundled.
	 * The call collector is responsible for finally deleting the object from cache once it has been
	 * transmitted to server.
	 * 
	 * @param collection The collection of that object.
	 * @param id The {@link ObjectId id} of the object to delete.
	 */
	private void deleteObject(final String collection, final ObjectId id) {
		mCallCollector.deleteCall(collection, id);
	}

	/**
	 * Saves an {@link DBObject} into the specified collection. This method will detect if the 
	 * object has never been transmitted to the server (it doesn't contain an {@link ObjectId}) or
	 * if it has already been transfered. The object will be stored into the cache (marked as either
	 * never transmitted or updated) and then either PUT to server or POSTed to server via the
	 * REST interface.
	 * 
	 * This method need to take care of the caching of the object (and not the 
	 * {@link CacheStorageHandler#onSave(java.lang.String, com.mongodb.DBObject) method) since 
	 * this is the only place where the difference between UPDATE and CREATE of an object can be
	 * detected.
	 * 
	 * @param collection The name of the collection for that object.
	 * @param dbo The object to save.
	 */
	@Override
	public void onSave(final String collection, final DBObject dbo, FieldList fl) {
		if (dbo.containsField(MONGO_ID)) {
			// Object already has an id, update object
			// Put the object also in cache (see CacheStorageHandler.onSave() for explanation)
			mCache.putUpdated(collection, dbo);
			updateObject(collection, dbo);
		} else {
			// Object is new in storage
			// Create an id for the DBObject.
			dbo.put(MONGO_ID, ObjectId.get());
			// Put the object also in cache (see CacheStorageHandler.onSave() for explanation)
			mCache.put(collection, dbo);
			newObject(collection, dbo);
		}
	}
	
	/**
	 * Returns a collection of all {@link DBObject DBObjects} in a specified collection.
	 * 
	 * @param collection The name of the collection.
	 * @param fl The FieldList containing all the fields, the decoded objects require.
	 * @return The collection of all objects.
	 */
	@Override
	public Collection<DBObject> onGet(final String collection, final FieldList fl) {
		
		// Get timestamp of last fetch (or 0 if never fetched before)
		long lastFetched = mCacheInformation.getLastModified(collection);
		
		// Get the time of the last deletion in this collection.
		RestResponse deletionRes = mRest.get(REST_META_PATH, collection, REST_META_DELETION_PATH);
		
		boolean refetchAll;
		long lastDeletion = 0;
		try {
			lastDeletion = Long.valueOf(new String(deletionRes.data));
			// If retrieving deletion time failed or last deletion time is newer then last fetch
			// we need to refetch the whole collection.
			refetchAll = deletionRes == null || lastDeletion > lastFetched;
		} catch(Exception ex) {
			refetchAll = true;
		}

		RestResponse response = null;

		// Either fetch all objects (if objects got deleted, so we can check what to delete form cache later)
		if(refetchAll) {
			response = mRest.get(REST_OBJECT_PATH, collection);
		} else {
			response = mRest.get(REST_OBJECT_PATH, collection, REST_MTIME_PATH, String.valueOf(lastFetched));
		}

		try {
			long lastModification = Math.max(
					Long.valueOf(response.headers.get(HTTP_MODIFIED_HEADER).get(0)),
					lastDeletion);
			
			// Store timestamp of the last modification (taken from http header) as last fetch,
			// (or last deletion time if newer) so next call will only get objects from that 
			mCacheInformation.setLastModified(collection, lastModification);
		} catch(Exception ex) {
			Log.w(LOG_TAG, String.format("Could not save last modification time from server. "
					+ "Client will fetch these objects from server again with the next call. "
					+ "[Caused by: %s]", ex.getMessage()));
		}
		
		final List<DBObject> objects = (response.data == null || response.code == HttpURLConnection.HTTP_NO_CONTENT)
				? new ArrayList<DBObject>(0) : BsonConverter.fromBsonList(response.data);
		
		long beginCacheUpdate = TimeUtil.getTimestamp();

		// Save all retrieved documents in cache
		mCache.putTransmitted(collection, objects);
		
		if(refetchAll) {
			// Delete all objects from cache, that doesn't exist on the server anymore
			// -> Haven't been updated in this session (so update timestamp is older than beginCacheUpdate
			// Only do this when we fetched all objects, otherwise we would delete here a lot of 
			// objects that just hasn't been transfered in this call from the server, but still exists.
			mCache.deleted(collection, beginCacheUpdate);
		}

		return objects;

	}

	/**
	 * Returns an {@link DBObject} by its id. This method will request the object via GET from the
	 * server.
	 * 
	 * @param collection The collection of the object to return.
	 * @param id The id of the object.
	 * @return The object with the requested id.
	 */
	@Override
	public DBObject onGetById(String collection, ObjectId id) {
		DBObject dbo = mCallCollector.callForId(collection, id);
		mCache.putTransmitted(collection, dbo);
		return dbo;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DBRef onCreateRef(String collection, DBObject dbo) {
		return new DBRef(null, collection, dbo.get("_id"));
	}

	/**
	 * Fetches an object, that has been references via a {@link DBRef}. This will query for
	 * the object by its id and its collection. It will store the received object in the cache.
	 * 
	 * @param dbref The {@link DBRef} to resolve.
	 * @return The object, referenced by the specified {@link DBRef}.
	 */
	@Override
	public DBObject onFetchRef(DBRef dbref) {

		if (dbref == null || dbref.getId() == null) {
			return null;
		}

		DBObject dbobj = mCallCollector.callForId(dbref.getRef(), (ObjectId) dbref.getId());
		mCache.putTransmitted(dbref.getRef(), dbobj);
		return dbobj;

	}

	/**
	 * {@inheritDoc}
	 */
	public void onDelete(String collection, ObjectId oi) {
		deleteObject(collection, oi);
	}

}
