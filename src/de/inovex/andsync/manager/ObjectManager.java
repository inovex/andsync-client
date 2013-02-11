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
package de.inovex.andsync.manager;

import org.bson.types.ObjectId;
import de.inovex.andsync.cache.Cache;
import de.inovex.andsync.Config;
import java.util.concurrent.ExecutorService;
import android.util.Log;
import de.inovex.andsync.AndSync;
import de.inovex.andsync.cache.CacheMock;
import de.inovex.andsync.cache.LuceneCache;
import de.inovex.andsync.rest.RestClient;
import de.inovex.jmom.Storage;
import java.util.List;
import java.util.concurrent.Executors;
import static de.inovex.andsync.Constants.*;

/**
 * The {@code ObjectManager} is the core of the client library. It takes care
 * to load and save Objects, using the LuceneCache and requesting objects from server
 * if needed.
 * 
 * An instance of this object is used by the {@link AndSync} class, that solely serves as 
 * a {@code static} facade to this class. You shouldn't use this class directly, use {@link AndSync}
 * instead.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class ObjectManager {
	
	private Config mConfig;
	
	private StorageWrapper mCacheStorage;
	private StorageWrapper mRestStorage;
	
	private Cache mCache;
	private Storage.Cache mSharedStorageCache;
	
	private ExecutorService mExecutor = Executors.newCachedThreadPool();
	
	private ListenerHandler mListeners = new ListenerHandler();
	
	public ObjectManager(Config config) {
		
		if(config == null) 
			throw new IllegalArgumentException("Config isn't allowed to be null.");
		
		this.mConfig = config;
		this.mSharedStorageCache = new Storage.DefaultCache();
		
		mCache = null;
		try {
			mCache = new LuceneCache();
		} catch (Throwable ex) {
			Log.w(LOG_TAG, "Cannot create cache dir. Disabling cache for this session.", ex);
		}
		
		if(mCache == null) {
			// If cache is disabled, use empty StorageWrapper.
			mCacheStorage = new StorageWrapper();
			// Initialize a mockup Cache for the use in RestStorageHandler.
			mCache = new CacheMock();
		} else {
			mCacheStorage = new StorageWrapper(Storage.getInstance(new CacheStorageHandler(mCache)));
		}
		
		// Create RestClient for configured URL.
		RestClient restClient = RestClient.create(mConfig.getUrl());
		Storage restStorage = Storage.getInstance(new RestStorageHandler(restClient, mCache));
		mRestStorage = new StorageWrapper(restStorage);
		
		// Make both storages share the same Object-ObjectId-Cache
		mCacheStorage.setCache(mSharedStorageCache);
		mRestStorage.setCache(mSharedStorageCache);
		
		// Enable Multithreading for the REST-Storage.
		mRestStorage.getConfig().setMultithreadingEnabled(true);
		mCacheStorage.getConfig().setMultithreadingEnabled(true);
		
	}
	
	public Config getConfig() {
		return mConfig;
	}
	
	public void save(final Object obj) {
		
		ObjectId id = mSharedStorageCache.getId(obj);
		if(id == null) {
			// If object is new, so we don't have any id yet, create one for it.
			
		}

		Runnable r = new Runnable() {
			public void run() {
				mRestStorage.save(obj);
			}
		};
		
		mExecutor.submit(r);
		
		// We don't call save on the cache, since saving in cache will be done from inside the
		// REST storage. See CacheStorageHandler.onSave() for explanation.
		
	}
	
	public <T> List<T> findAll(final Class<T> clazz) {
		
		Runnable r = new Runnable() {
			public void run() {
				long milli = System.currentTimeMillis();
				List<T> objs = mRestStorage.findAll(clazz);
				// Commit cache changes since we have not all objects from that fetch
				mCache.commit();
				// TODO: Remove time recording
				Log.w("TOSLOW", "-- Elapsed Time [findAll REST] " + (System.currentTimeMillis() - milli)/1000.0 + "s --");
				mListeners.updateListener(clazz, objs);
			}
		};
		
		mExecutor.submit(r);
		
		long milli = System.currentTimeMillis();
		
		List<T> findAll = mCacheStorage.findAll(clazz);
		
		Log.w("TOSLOW", " -- Elapsed Time [findAll Cache] " + (System.currentTimeMillis() - milli)/1000.0 + "s --");
		
		return findAll;
		
	}
	
	public void delete(final Object obj) {
		
		Runnable r = new Runnable() {
			public void run() {
				mRestStorage.delete(obj);
			}
		};
		
		mExecutor.submit(r);
		
		mCacheStorage.delete(obj);
		
	}
	
	public void registerListener(ObjectListener listener, Class<?>[] classes) {
		mListeners.registerListener(listener, classes);
	}

	public void removeListener(ObjectListener listener) {
		mListeners.removeListener(listener);
	}

}