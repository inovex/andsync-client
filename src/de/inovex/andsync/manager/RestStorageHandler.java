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

import java.util.concurrent.ScheduledFuture;
import com.mongodb.BasicDBObject;
import java.util.Map;
import java.util.Set;
import android.util.Log;
import com.mongodb.BasicDBList;
import de.inovex.andsync.cache.Cache;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import de.inovex.andsync.rest.RestClient;
import de.inovex.andsync.rest.RestClient.RestResponse;
import de.inovex.andsync.rest.RestException;
import de.inovex.andsync.util.Base64;
import de.inovex.andsync.util.BsonConverter;
import de.inovex.jmom.FieldList;
import de.inovex.jmom.Storage;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.util.NamedThreadFactory;
import org.bson.types.ObjectId;
import static de.inovex.andsync.Constants.*;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class RestStorageHandler implements Storage.DBHandler {

	private RestClient mRest;
	private CallCollector mCallCollector;
	private Cache mCache;

	public RestStorageHandler(RestClient restClient, Cache cache) {
		assert restClient != null && cache != null;
		this.mCallCollector = new CallCollector();
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
		final byte[] data = BsonConverter.toByteArrayList(dbo);
		try {
			mRest.post(data, REST_OBJECT_PATH, collection);
		} catch (RestException ex) {
			Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void newObject(final String collection, final DBObject dbo) {

		final byte[] data = BsonConverter.toByteArrayList(dbo);
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

		Log.w("TOSLOW", String.format("-- Elapsed Time [Caching objects (without references)]" + (System.currentTimeMillis() - nano) / 1000.0 + "s -- "));

		return objects;

	}

	public DBRef onCreateRef(String collection, DBObject dbo) {
		return new DBRef(null, collection, dbo.get("_id"));
	}

	public DBObject onFetchRef(DBRef dbref) {

		if (dbref == null || dbref.getId() == null) {
			return null;
		}

		DBObject dbobj = mCallCollector.callForId(dbref.getRef(), (ObjectId) dbref.getId());
		mCache.put(dbref.getRef(), dbobj);
		return dbobj;

	}

	public void onDelete(String collection, ObjectId oi) {
		try {
			mRest.delete(REST_OBJECT_PATH, collection, oi.toString());
		} catch (RestException ex) {
			Logger.getLogger(ObjectManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private class CallCollector {

		/**
		 * Limit of calls that get cached for one collection before sending a REST request.
		 */
		private final static int CALL_COLLECT_LIMIT = 100;
		/**
		 * Time limit after which a REST call will be made (even when less than {@link #CALL_COLLECT_LIMIT}
		 * calls are pending.
		 */
		private final static int CALL_COLLECT_TIME_LIMIT = 3;

		/**
		 * A list of {@link ObjectId} that are waiting to be fetched from server. Separated by their
		 * collection.
		 */
		private Map<String, Set<ObjectId>> mIdCalls = new ConcurrentHashMap<String, Set<ObjectId>>();
		/**
		 * The {@link DBObject DBObjects} returned from the server for each {@link ObjectId}.
		 */
		private Map<ObjectId, DBObject> mResults = new ConcurrentHashMap<ObjectId, DBObject>();

		/**
		 * The waiting locks for each call made. Each call holds a list of locks for all pending calls 
		 * to this {@link ObjectId}.
		 */
		private Map<ObjectId, Collection<Object>> mIdLocks = Collections.synchronizedMap(new HashMap<ObjectId, Collection<Object>>());
		/**
		 * This is a map containing a lock for each collection. This is used when the call for a collection
		 * is made and when modifying the {@link #mIdCalls pending calls} for that collection.
		 * So the pending call ids aren't modified while reading from the list to fetch them via REST.
		 */
		private Map<String, Object> mCollectionLocks = Collections.synchronizedMap(new HashMap<String, Object>());
		/**
		 * This lock is used when modifying the periodic check for pending calls.
		 * @see #mScheduled
		 * @see #mCheckForPendingCalls
		 * @see #mExecutor
		 */
		private final Object mSchedulerLock = new Object();
		/**
		 * This lock is used when modifying the {@link #mIdLocks id locks}.
		 */
		private final Object mLockCreationLock = new Object();
		/**
		 * This lock is used when modifying the {@link #mResults results list}.
		 */
		private final Object mResultsLock = new Object();


		private ScheduledFuture<?> mScheduled;
		private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Periodic AndSync Thread "));
		private final Runnable mCheckForPendingCalls = new Runnable() {

			public void run() {
				// Check if any pending calls exist. If they do, do REST calls.
				for (String collection : mIdCalls.keySet()) {
					if (numPendingcalls(collection) > 0) {
						doCall(collection);
					}
				}

				// Check if any calls are left over, if so reschedule the periodic check, if not
				// don't do any further periodic check.
				synchronized(mSchedulerLock) {
					if (hasPendingCalls()) {
						mScheduled = mExecutor.schedule(mCheckForPendingCalls, CALL_COLLECT_TIME_LIMIT, 
								TimeUnit.SECONDS);
					} else {
						mScheduled = null;
					}
				}

			}
		};

		private boolean hasPendingCalls() {
			for (Set<ObjectId> pending : mIdCalls.values()) {
				if (pending.size() > 0) {
					return true;
				}
			}
			return false;
		}

		private int numPendingcalls(String collection) {
			Set<ObjectId> calls = mIdCalls.get(collection);
			return calls != null ? mIdCalls.get(collection).size() : 0;
		}

		public DBObject callForId(String collection, ObjectId id) {

			// Wait till a current call for that collection has been finished,
			// before adding this call to the queue.
			synchronized (getCollectionLock(collection)) {
				// Get the queue (a Set, since we only need to fetch each object one time)
				// or create a new queue for that collection.
				Set<ObjectId> idSet = mIdCalls.get(collection);
				if (idSet == null) {
					idSet = Collections.newSetFromMap(new ConcurrentHashMap<ObjectId, Boolean>());
					mIdCalls.put(collection, idSet);
				}
				// Add the id of the object we want to get to the queue for its collection.
				idSet.add(id);
			}

			synchronized (mSchedulerLock) {
				if (mScheduled == null) {
					mScheduled = mExecutor.schedule(mCheckForPendingCalls,
							CALL_COLLECT_TIME_LIMIT, TimeUnit.SECONDS);
				} else {
					if(mScheduled.cancel(true)) {
						mScheduled = mExecutor.schedule(mCheckForPendingCalls, CALL_COLLECT_TIME_LIMIT, TimeUnit.SECONDS);
					}
				}
			}

			Object lockForId = newLock(id);
			synchronized (lockForId) {
				// Call if we have enough requests collected
				if (numPendingcalls(collection) >= CALL_COLLECT_LIMIT) {
					doCall(collection);
				}
				
				while (!mResults.containsKey(id)) {
					try {
						lockForId.wait(10000);
					} catch (InterruptedException ex) {
						// Do nothing
						//Log.w("ANDSYNC", String.format("Thread got interrupted %s", Thread.currentThread().getId()));
					}
				}
				
				boolean removeObject = false;
				synchronized(mLockCreationLock) {
					mIdLocks.get(id).remove(lockForId);
					if(mIdLocks.get(id) != null && mIdLocks.get(id).isEmpty()) {
						mIdLocks.remove(id);
						removeObject = true;
					}
					synchronized(mResultsLock) {
						DBObject dbo = (removeObject) ? mResults.remove(id) : mResults.get(id);
						return dbo;
					}
				}

			}

		}

		private synchronized Object getCollectionLock(String collection) {
			Object lock = mCollectionLocks.get(collection);
			if (lock == null) {
				lock = new Object();
				mCollectionLocks.put(collection, lock);
			}
			return lock;
		}

		private Object newLock(ObjectId id) {
			synchronized(mLockCreationLock) {
				Collection<Object> locksForId = mIdLocks.get(id);
				if(locksForId == null) {
					locksForId = new ConcurrentLinkedQueue<Object>();
					mIdLocks.put(id, locksForId);
				}
				Object newLock = new Object();
				locksForId.add(newLock);
				return newLock;
			}
		}

		private void doCall(final String collection) {

			Runnable r = new Runnable() {

				public void run() {

					synchronized (getCollectionLock(collection)) {

						if (numPendingcalls(collection) == 0) {
							return;
						}

						Set<ObjectId> requestedIds = mIdCalls.get(collection);

						// Build list of all ids to fetch
						BasicDBList keyList = new BasicDBList();
						for (ObjectId id : requestedIds) {
							keyList.add(id);
						}

						try {
							RestResponse response = mRest.get(REST_OBJECT_PATH, collection,
									Base64.encode(BsonConverter.toByteArray(keyList), 0));

							BasicDBObject objects = (BasicDBObject) BsonConverter.fromBson(response.data);

							for (Object o : objects.values()) {

								if (!(o instanceof DBObject)) {
									Log.w(LOG_TAG, "Received object from client wasn't a DBObject.");
									continue;
								}

								DBObject dbo = (DBObject) o;
								ObjectId id = (ObjectId) dbo.get(MONGO_ID);

								synchronized(mResultsLock) {
									mResults.put(id, dbo);
								}
								requestedIds.remove(id);

								Collection<Object> locksForId = mIdLocks.get(id);
								if(locksForId != null)
								for(Object lock : locksForId) {
									synchronized (lock) {
										lock.notifyAll();
									}
								}

							}

						} catch (RestException ex) {
							Logger.getLogger(RestStorageHandler.class.getName()).log(Level.SEVERE, null, ex);
						}

					}
				}
			};

			new Thread(r).start();

		}
	}
}
