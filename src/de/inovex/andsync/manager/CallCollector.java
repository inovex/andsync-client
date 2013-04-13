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

import de.inovex.andsync.util.Base64;
import de.inovex.andsync.rest.RepeatingRestClient;
import de.inovex.andsync.cache.Cache;
import android.util.Log;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import de.inovex.andsync.rest.RestClient.RestResponse;
import de.inovex.andsync.util.BsonConverter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.util.NamedThreadFactory;
import org.bson.types.BasicBSONList;
import org.bson.types.ObjectId;
import static de.inovex.andsync.Constants.*;

/**
 * Collects different calls to the REST interface and try to minimize the required HTTP connections,
 * by bunching calls together. Calls will be collected separated by collection names and until
 * {@link #CALL_COLLECT_LIMIT} calls have been collected for that collection and HTTP method
 * or {@link #CALL_COLLECT_TIME_LIMIT} has been passed after the first call for this collection
 * and HTTP method has been added to the {@code CallCollector}.
 * <p>
 * Only the public methods of this class are meant to be used in the outer class. These are the
 * methods to add calls to the {@link CallCollector}, all other methods are solely meant for
 * internal use of this inner class:
 * <ul>
 * <li>{@link #deleteCall(java.lang.String, org.bson.types.ObjectId)}</li>
 * <li>{@link #postCall(java.lang.String, com.mongodb.DBObject)}</li>
 * <li>{@link #putCall(java.lang.String, com.mongodb.DBObject)}</li>
 * <li>{@link #callForId(java.lang.String, org.bson.types.ObjectId)}</li>
 * </ul>
 * <p>
 * This class makes large use of synchronizing code.
 * In the code the synchronized are only tagged with a number, that refers to the following explanations.
 * <p>
 * (1)	Every call for an ObjectId will create an own lock, meaning even multiple calls for the same ObjectId
 *		will have multiple different locks of this level. These locks are stored in mIdLocks.
 *		This map has a list of locks for each ObjectId. This list contains one lock for each call
 *		to that ObjectId.
 *		This lock is actually not used for synchronized access, only for waiting. The call to
 *		{@link #callForId(java.lang.String, org.bson.types.ObjectId)} will use this lock to 
 *		wait on. {@link #doGetByIdCalls(java.lang.String)} will iterate over all locks for an
 *		ObjectId, when it retrieved that object and notify on all these locks. So the waiting 
 *		call in {@code callForId} will wake up, get the result and return it.
 * 
 * (2)	The {@link #mLockCreationLock} is used to synchronize modification of the {@link #mIdLocks}
 *		map. Whenever this map should be modified the modification (and requirement checks for
 *		that modification) must be synchronized with this lock.
 * 
 * (3)	The {@link #mResultsLock} is used to synchronize modification of {@link #mResults}.
 *		Whenever this map should be modified (or requirement checks for modification should be done)
 *		you must synchronize with {@link #mResultsLock}.
 * 
 * (4)	Via {@link #getCollectionLock(java.lang.String)} a lock (from {@link #mCollectionLocks})
 *		is returned for every collection. This lock is used to block adding new calls, while
 *		a call to the server is currently running for that collection.
 *		{@link #doPutCalls(java.lang.String)}, {@link #doPostCalls(java.lang.String)},
 *		{@link #doDeleteCalls(java.lang.String)} and {@link #doGetByIdCalls(java.lang.String)}
 *		are using this lock to block while they do the call. Adding a new call is blocked
 *		in {@link #savePendingCall(java.util.Map, java.lang.String, java.lang.Object)} until
 *		the lock for this collection is released.
 * 
 * (5)	The {@link #mSchedulerLock} is used to synchronize scheduling new locks in {@link #scheduleCall()}
 *		and {@link #mCheckForPendingCalls}, so (re)scheduling will be done in a synchronized block,
 *		and no dead threads will be kept over.
 * 
 * ...
 */
class CallCollector {

	/**
	 * Limit of calls that get cached for one collection before sending a REST request.
	 * 
	 * TODO: Make configurable
	 */
	private final static int CALL_COLLECT_LIMIT = 100;

	/**
	 * Time limit in seconds after which a REST call will be made (even when less than 
	 * {@link #CALL_COLLECT_LIMIT} calls are pending.
	 * 
	 * TODO: Make configurable
	 */
	private final static int CALL_COLLECT_TIME_LIMIT = 3;

	private RepeatingRestClient mRest;
	private Cache mCache;
	
	/**
	 * A list of {@link ObjectId} that are waiting to be fetched from server. Separated by their
	 * collection.
	 */
	private Map<String, Set<ObjectId>> mIdCalls = new ConcurrentHashMap<String, Set<ObjectId>>();

	/**
	 * A map of all collections and their pending PUT calls.
	 */
	private Map<String, Set<DBObject>> mPutCalls = new ConcurrentHashMap<String, Set<DBObject>>();

	/**
	 * A map of all collections and their pending POST calls.
	 */
	private Map<String, Set<DBObject>> mPostCalls = new ConcurrentHashMap<String, Set<DBObject>>();

	/**
	 * A map of all collections and their pending DELETE calls.
	 */
	private Map<String, Set<ObjectId>> mDeleteCalls = new ConcurrentHashMap<String, Set<ObjectId>>();

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

			// Check for pending put calls.
			for(String collection : mPutCalls.keySet()) {
				if(numPendingPutCalls(collection) > 0) {
					doPutCalls(collection);
				}
			}

			// Check for pending post calls.
			for(String collection : mPostCalls.keySet()) {
				if(numPendingPostCalls(collection) > 0) {
					doPostCalls(collection);
				}
			}

			// Check if any pending calls exist. If they do, do REST calls.
			for (String collection : mIdCalls.keySet()) {
				if (numPendingIdCalls(collection) > 0) {
					doGetByIdCalls(collection);
				}
			}

			// Check for pending deleted calls.
			for(String collection : mDeleteCalls.keySet()) {
				if(numPendingDeleteCalls(collection) > 0) {
					doDeleteCalls(collection);
				}
			}

			// Check if any calls are left over, if so reschedule the periodic check, if not
			// don't do any further periodic check.
			synchronized (mSchedulerLock) { // (5) see CallCollector docs
				if (hasPendingCalls()) {
					mScheduled = mExecutor.schedule(mCheckForPendingCalls, CALL_COLLECT_TIME_LIMIT,
							TimeUnit.SECONDS);
				} else {
					mScheduled = null;
				}
			}

		}
	};

	public CallCollector(Cache cache, RepeatingRestClient rest) {
		mRest = rest;
		mCache = cache;
	}

	/**
	 * Adds a put call to the {@link CallCollector}. The PUT call will be send out with the
	 * next bundle of PUT calls for that collection. All collected objects will be send in one
	 * PUT call to the server. This method returns immediately since it doesn't need to return
	 * anything and calls will anyway be made sometime later in background.
	 * 
	 * @param collection The collection used in the query of the PUT call.
	 * @param dbo The {@link DBObject} that will be putted to the server.
	 */
	public void putCall(final String collection, final DBObject dbo) {

		// Adding the call will be done in another thread, so this method can return immediately.
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Save this call in the mPutCalls map, that holds all pending PUT calls.
				savePendingCall(mPutCalls, collection, dbo);

				// If the number of pending PUT calls for that collection exceeds the limit
				// start the PUT calls. Otherwise schedule a call to wait the maximum time limit
				// and send calls then.
				if(numPendingPutCalls(collection) >= CALL_COLLECT_LIMIT) {
					doPutCalls(collection);
				} else {
					// TODO: This has been moved into the else block, but not enough tested yet
					// if it works. If it doesn't work, just move this out of the else block 
					// after the if block.
					scheduleCall();
				}
			}

		}).start();

	}

	/**
	 * Adds a POST call to the {@link CallCollector}. The POST call will be send out with the
	 * next bundle of POST calls for that collection. All collected objects will be send in 
	 * one POST call to the server. This method returns immediately since it doesn't need to return
	 * anything and calls will anyway be made sometime later in the background.
	 * 
	 * @param collection The collection to post to.
	 * @param dbo The {@link DBObject} that will be posted to the server.
	 */
	public void postCall(final String collection, final DBObject dbo) {

		// Adding the call will be done in another thread, so this method can return immediately.
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Save thie call in the mPostCalls map, that holds all pending POST calls.
				savePendingCall(mPostCalls, collection, dbo);

				// If the number of pending POST calls for that collection exceeds the limit
				// start the POST calls. Otherwise schedule a call to wait the maximum time limit
				// and send calls then.
				if(numPendingPostCalls(collection) >= CALL_COLLECT_LIMIT) {
					doPostCalls(collection);			
				} else {
					// TODO: This has been moved into the else block, but not enough tested yet
					// if it works. If it doesn't work, just move this out of the else block 
					// after the if block.
					scheduleCall();
				}

			}

		}).start();

	}

	/**
	 * Adds a DELETE call to the {@link CallCollector}. The DELETE call will be send out with
	 * the next bundle of DELETE calls for that collection. This method returns immediately since
	 * it doesn't need to return anything and calls will anyway be made sometime later in the
	 * background.
	 * 
	 * @param collection The collection to post to.
	 * @param id The {@link ObjectId} of the object that should be deleted.
	 */
	public void deleteCall(final String collection, final ObjectId id) {

		// Adding the call will be done in another thread, so this method can return immediately.
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Save this call in the mDeleteCalls map, that holds all pending DELETE calls.
				savePendingCall(mDeleteCalls, collection, id);

				// If the number of pending DELETE calls for that collection exceeds the limit
				// start the DELETE calls. Otherwise schedule a call to wait the maximum time limit
				// and send calls then.
				if(numPendingDeleteCalls(collection) >= CALL_COLLECT_LIMIT) {
					doDeleteCalls(collection);
				} else {
					// TODO: This has been moved into the else block, but not enough tested yet
					// if it works. If it doesn't work, just move this out of the else block 
					// after the if block.
					scheduleCall();
				}

			}

		}).start();

	}

	/**
	 * Adds a call to retrieve an object by its {@link ObjectId}. The call will be made
	 * together with other calls as soon as a specific limit has been reached, but lately after
	 * a specific time has been passed. This method will wait for that call and its result,
	 * and return when the requested object has been retrieved from the server.
	 * 
	 * @param collection The collection to query.
	 * @param id The id of the object that will be retrieved.
	 */
	public DBObject callForId(String collection, ObjectId id) {

		// Add the call to mIdCalls map, that holds all the pending id calls.
		savePendingCall(mIdCalls, collection, id);

		// Schedule a call. 
		// TODO: This should be moved to the else call of the if check, 8 lines further down
		// and tested if it works.
		scheduleCall();

		// Create a lock for the requested id.
		Object lockForId = newLock(id);
		synchronized (lockForId) { // (1) see CallCollector doc

			// Call if we have enough requests collected
			if (numPendingIdCalls(collection) >= CALL_COLLECT_LIMIT) {
				doGetByIdCalls(collection);
			}

			// Wait for the object to be in the mResults list, which hold the retrieved objects
			// from server until all waiting calls have returned this object.
			while (!mResults.containsKey(id)) {
				try {
					// Wait on the individual call lock (1), until we get notified again.
					// Wait a maximum 10 seconds to prevent some maybe existing racing conditions
					// and dead locks.
					lockForId.wait(10000);
				} catch (InterruptedException ex) {
					// Do nothing, just continue and check again
				}
			}

			// The request object is now available, so we can remove our waiting lock for this
			// call and check if we are the last call waiting for that object, then we can
			// also remove the object from the result list.
			synchronized (mLockCreationLock) { // (2) see CallCollector docs

				// Remove the lock (1) for this call
				mIdLocks.get(id).remove(lockForId);

				// Check if the list of locks for this id is null or empty, meaning we are
				// the last method call waiting for that object. If so remove the empty list
				// from the mIdLocks map.
				boolean removeObject = false;
				if (mIdLocks.get(id) != null && mIdLocks.get(id).isEmpty()) {
					mIdLocks.remove(id);
					removeObject = true;
				}

				// If we have been the last waiting call for this object (as determined by the
				// above 'if' and saved in removeObject, delete this object from list and return
				// it. Otherwise just get the object from list and return it.
				synchronized (mResultsLock) { // (3) see CallCollector docs
					DBObject dbo = (removeObject) ? mResults.remove(id) : mResults.get(id);
					return dbo;
				}
			}

		}

	}

	/**
	 * Checks whether any pending call exists. This will check all type of pending calls and
	 * return true, if any call (no matter what type) is pending.
	 * 
	 * @return Whether any pending call exists.
	 */
	private boolean hasPendingCalls() {
		for (Set<ObjectId> pending : mIdCalls.values()) {
			if (pending.size() > 0) return true;
		}
		for(Set<DBObject> pending : mPostCalls.values()) {
			if(pending.size() > 0) return true;
		}
		for(Set<DBObject> pending : mPutCalls.values()) {
			if(pending.size() > 0) return true;
		}
		for(Set<ObjectId> pending : mDeleteCalls.values()) {
			if(pending.size() > 0) return true;
		}
		return false;
	}

	/**
	 * Returns number of pending ID calls for a specified collection.
	 * 
	 * @param collection The name of the collection.
	 * @return The number of pending id calls for that collection.
	 */
	private int numPendingIdCalls(String collection) {
		Set<ObjectId> calls = mIdCalls.get(collection);
		return calls != null ? mIdCalls.get(collection).size() : 0;
	}

	/**
	 * Returns number of pending PUT calls for a specific collection.
	 * 
	 * @param collection The name of the collection.
	 * @return The number of pending PUT calls for that collection.
	 */
	private int numPendingPutCalls(String collection) {
		Set<DBObject> calls = mPutCalls.get(collection);
		return calls != null ? calls.size() : 0;
	}

	/**
	 * Returns number of pending POST calls for a specific collection.
	 * 
	 * @param collection The name of the collection.
	 * @return The number of pending POST calls for that collection.
	 */
	private int numPendingPostCalls(String collection) {
		Set<DBObject> calls = mPostCalls.get(collection);
		return calls != null ? calls.size() : 0;
	}

	/**
	 * Returns number of pending DELETE calls for a specific collection.
	 * 
	 * @param collection The name of the collection.
	 * @return The number of pending DELETE calls for that collection.
	 */
	private int numPendingDeleteCalls(String collection) {
		Set<?> calls = mDeleteCalls.get(collection);
		return calls != null ? calls.size() : 0;
	}

	/**
	 * Add a call to {@link CallCollector}.
	 * 
	 * @param pendingCache The Map to which the new call should be added.
	 * @param collection The collection this call should be made for.
	 * @param object An object representing the calls data. The type depends on the type of call.
	 */
	private <T> void savePendingCall(Map<String,Set<T>> pendingCache, String collection, T object) {

		synchronized(getCollectionLock(collection)) { // (4) see CallCollector docs
			Set<T> pending = pendingCache.get(collection);
			if(pending == null) {
				// Create concurrent set, if none exists for that collection yet
				pending = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());
				pendingCache.put(collection, pending);
			}
			pending.add(object);
		}
	}

	/**
	 * Schedules the next call, meaning this will schedule {@link #mCheckForPendingCalls} to run
	 * in {@link #CALL_COLLECT_TIME_LIMIT} and check if any pending calls exist and if so, do these
	 * calls.
	 */
	private void scheduleCall() {
		synchronized (mSchedulerLock) { // (5) see CallCollector docs
			// If there is nothing scheduled yet, schedule a new call
			if (mScheduled == null) {
				mScheduled = mExecutor.schedule(mCheckForPendingCalls,
						CALL_COLLECT_TIME_LIMIT, TimeUnit.SECONDS);
			} else {
				// If a call is already scheduled, try to cancel it and if this succeed schedule a new one.
				if (mScheduled.cancel(true)) {
					mScheduled = mExecutor.schedule(mCheckForPendingCalls, CALL_COLLECT_TIME_LIMIT, TimeUnit.SECONDS);
				}
			}
		}
	}

	/**
	 * Returns the collection lock (see (4) in CallCollector docs) for a specified collection.
	 * This method is synchronized, so that two parallel calls for a non existing collection lock,
	 * won't produce this method two create two different locks.
	 * 
	 * @param collection The name of the collection.
	 * @return A lock for that collection.
	 */
	private synchronized Object getCollectionLock(String collection) {
		Object lock = mCollectionLocks.get(collection);
		if (lock == null) {
			lock = new Object();
			mCollectionLocks.put(collection, lock);
		}
		return lock;
	}

	/**
	 * Create a new lock for a call (see (1) in CallCollector docs). The returned lock will 
	 * be used to wait on until the requested object has been received.
	 * 
	 * @param id The {@link ObjectId} to create a new lock for.
	 * @return A new lock for this id.
	 */
	private Object newLock(ObjectId id) {
		synchronized (mLockCreationLock) { //(2) see CallCollector docs
			Collection<Object> locksForId = mIdLocks.get(id);
			if (locksForId == null) {
				// Create a new queue for locks for that id, if non exists yet.
				locksForId = new ConcurrentLinkedQueue<Object>();
				mIdLocks.put(id, locksForId);
			}
			Object newLock = new Object();
			locksForId.add(newLock);
			return newLock;
		}
	}

	/**
	 * Executes all pending PUT calls for the specified collection in a new {@link Thread}.
	 * The new Thread will synchronize on the collection lock (4) so that adding calls to the collection
	 * is blocked.
	 * 
	 * @param collection The collection name.
	 */
	private void doPutCalls(final String collection) {

		new Thread(new Runnable() {

			public void run() {

				synchronized(getCollectionLock(collection)) { // (4) see CallCollector docs

					// Get all objects that should be putted to this collection.
					Set<DBObject> putCalls = mPutCalls.get(collection);

					if(putCalls == null || putCalls.size() <= 0) return;

					// Put all objects into one BSON list.
					BasicBSONList bsonList = new BasicBSONList();
					for(DBObject call : putCalls) {
						bsonList.add(call);
					}

					// Try to transfer objects and if succeeded, mark them as transmitted in the cache.
					if(mRest.put(BsonConverter.bsonObjectAsBytes(bsonList), REST_OBJECT_PATH, collection) != null) {
						for(DBObject call : putCalls) {
							mCache.putTransmitted(collection, call);
						}
						mCache.commit();
						putCalls.clear();
					}

				}
			}

		}).start();

	}

	/**
	 * Executes all pending POST calls for the specified collection in a new {@link Thread}.
	 * The new Thread will synchronize on the collection lock (4) so that adding calls to the collection
	 * is blocked.
	 * 
	 * @param collection The collection name.
	 */
	private void doPostCalls(final String collection) {

		new Thread(new Runnable() {

			public void run() {
				synchronized(getCollectionLock(collection)) { // (4) see CallCollector docs

					// Get all objects to post to that collection
					Set<DBObject> postCalls = mPostCalls.get(collection);

					if(postCalls == null || postCalls.size() <= 0) return;

					// Add all objects to one BSON list.
					BasicBSONList bsonList = new BasicBSONList();
					for(DBObject call : postCalls) {
						bsonList.add(call);
					}

					// Try to transfer objects and if succeeded, mark them as transmitted in the cache.
					if(mRest.post(BsonConverter.bsonObjectAsBytes(bsonList), REST_OBJECT_PATH, collection) != null) {
						for(DBObject call : postCalls) {
							mCache.putTransmitted(collection, call);
						}
						mCache.commit();
						postCalls.clear();
					}

				}
			}

		}).start();

	}

	/**
	 * Executes all pending DELETE calls for the specified collection in a new {@link Thread}.
	 * The new Thread will synchronize on the collection lock (4) so that adding calls to the collection
	 * is blocked.
	 * 
	 * @param collection The collection name.
	 */
	private void doDeleteCalls(final String collection) {
		new Thread(new Runnable() {

			public void run() {
				synchronized(getCollectionLock(collection)) { // (4) see CallCollector docs

					// Get all objects, that should be deleted from that collection.
					Set<ObjectId> deletions = mDeleteCalls.get(collection);

					if(deletions == null || deletions.size() <= 0) return;

					// Execute one call for each object.
					// TODO: Bundle them into one HTTP call (and modify server to understand
					//		that call.
					for(ObjectId id : deletions) {
						if(mRest.delete(REST_OBJECT_PATH, collection, id.toString()) != null) {
							mCache.deleted(collection, id);
						}
					}

					deletions.clear();
				}
			}

		}).start();
	}		

	/**
	 * Execute all pending calls for an specific object by its id. This method will return immediately
	 * and execute the calls in the background.
	 * 
	 * @param collection The collection to execute calls for.
	 */
	private void doGetByIdCalls(final String collection) {

		new Thread(new Runnable() {

			public void run() {

				synchronized(getCollectionLock(collection)) { // (4) see CallCollector docs

					// Get all pending id calls.
					Set<ObjectId> requestedIds = mIdCalls.get(collection);

					// Build list of all ids to fetch
					BasicDBList keyList = new BasicDBList();
					for (ObjectId id : requestedIds) {
						keyList.add(id);
					}

					// Make server call with base64 encoded ids
					RestResponse response = mRest.get(REST_OBJECT_PATH, collection,
							Base64.encode(BsonConverter.toByteArray(keyList), 0));

					if (response.code != 200) {
						Log.w(LOG_TAG, String.format("Server returned error code %d.", response.code));
						return;
					}
					
					BasicDBObject objects = (BasicDBObject) BsonConverter.fromBson(response.data);

					if (objects == null) {
						Log.w(LOG_TAG, "Received object list from server wasn't a valid BSON object.");
						return;
					}

					// Check every object and inform the waiting calls.
					for (Object o : objects.values()) {

						if (!(o instanceof DBObject)) {
							Log.w(LOG_TAG, "Received object from server wasn't a DBObject.");
							continue;
						}

						DBObject dbo = (DBObject) o;
						ObjectId id = (ObjectId) dbo.get(MONGO_ID);

						// Store result in result list, from which all the 
						synchronized (mResultsLock) { // (3) see CallCollector docs
							mResults.put(id, dbo);
						}
						// Remove this id from the list of pending ids
						requestedIds.remove(id);

						// Wake up all waiting callForId-methods, that this object has now been
						// retrieved. See (1) in CallCollector documentation.
						Collection<Object> locksForId = mIdLocks.get(id);
						if (locksForId != null) {
							for (Object lock : locksForId) {
								synchronized (lock) { // (1) see CallCollector docs
									lock.notifyAll();
								}
							}
						}

					}
				}
			}

		}).start();

	}
}