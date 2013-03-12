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

import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import de.inovex.andsync.cache.Cache;
import de.inovex.andsync.Config;
import java.util.concurrent.ExecutorService;
import android.util.Log;
import de.inovex.andsync.AndSync;
import de.inovex.andsync.cache.CacheMock;
import de.inovex.andsync.cache.lucene.LuceneCache;
import de.inovex.andsync.rest.RestClient;
import de.inovex.jmom.Storage;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import static de.inovex.andsync.Constants.*;

/**
 * The {@code AndSyncManager} is the core of the client library. It takes care
 * to load and save Objects, using the LuceneCache and requesting objects from server
 * if needed.
 * 
 * An instance of this object is used by the {@link AndSync} class, that solely serves as 
 * a {@code static} facade to this class. You shouldn't use this class directly, use {@link AndSync}
 * instead.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class AndSyncManager {
	
	private Config mConfig;
	
	private PushManager mPushManager;
	
	private StorageWrapper mCacheStorage;
	private StorageWrapper mRestStorage;
	
	private Cache mCache;
	private Storage.Cache mSharedStorageCache;
	
	private ExecutorService mExecutor = Executors.newCachedThreadPool();
	
	/**
	 * The list of update listener. This must be instantiated with a type that supports the remove
	 * method on its iterator.
	 */
	private Set<WeakReference<AndSync.UpdateListener<?>>> mUpdateListener;
	
	private Map<Class<?>,Set<WeakReference<AndSync.UpdateListener<?>>>> mListeners;
	
	
	public AndSyncManager(Config config) {
	
		if(config == null) 
			throw new IllegalArgumentException("Config isn't allowed to be null.");
		
		this.mUpdateListener = new HashSet<WeakReference<AndSync.UpdateListener<?>>>();
		
		this.mConfig = config;
		this.mPushManager = new PushManager(config.getGcmKey());
		mPushManager.init();
		
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
		
		Runnable r = new Runnable() {
			public void run() {
				mRestStorage.save(obj);
				// Commit cache after we saved our object
				mCache.commit();
			}
		};
		
		mExecutor.submit(r);
		
		// We don't call save on the cache, since saving in cache will be done from inside the
		// REST storage. See CacheStorageHandler.onSave() for explanation.
		
	}
	
	public void saveMultiple(final Iterable<? extends Object> objects) {
		
		Runnable r = new Runnable() {
			public void run() {
				mRestStorage.saveMultiple(objects);
				mCache.commit();
			}
		};
		
		mExecutor.submit(r);
		
		// We don't call save on the cache, since saving in cache will be done from inside the
		// REST storage. See CacheStorageHandler.onSave() for explanation.
		
	}
	
	public <T> List<T> findAll(final Class<T> clazz, final AndSync.UpdateListener<T> listener) {
		
		
		addListener(listener);
		
		long milli = System.currentTimeMillis();

		// Create a new lazy list, that will be used to load the cache data in background
		final List<T> findAll = new LazyList<T>(mCacheStorage, mCache, clazz);
		
		Log.w("ANDSYNC_TIME", " -- [findAll Cache] Elapsed Time: " + (System.currentTimeMillis() - milli)/1000.0 + "s / #Objects: " + findAll.size() + " --");
		
		Runnable r = new Runnable() {
			public void run() {
				
				long beforeUpdate = System.currentTimeMillis();
				
				List<T> objs = mRestStorage.findAll(clazz);
				
				// Commit cache changes since we have not all objects from that fetch
				mCache.commit();
				
				// TODO: Remove time recording
				Log.w("ANDSYNC_TIME", "-- [findAll REST] Elapsed Time: " + (System.currentTimeMillis() - beforeUpdate)/1000.0 + "s / #Objects: " + objs.size() + " --");
				
				List<T> objects = new LazyList<T>(mCacheStorage, mCache, clazz);
				listener.onData(objects);

			}
		};
		
		mExecutor.submit(r);
		
		return findAll;
		
	}
	
	public void delete(final Object obj) {
		
		Runnable r = new Runnable() {
			public void run() {
				mRestStorage.delete(obj);
				mCache.commit();
			}
		};
		
		mExecutor.submit(r);
		
		mCacheStorage.delete(obj);
		mCache.commit();
		
	}
	
	private void addListener(AndSync.UpdateListener<?> listener) {
		for(WeakReference<AndSync.UpdateListener<?>> ref : mUpdateListener) {
			if(ref.get() == listener) return;
		}
		mUpdateListener.add(new WeakReference<AndSync.UpdateListener<?>>(listener));
	}
	
	public void onServerUpdate() {
		int t = 0;
		for(Iterator<WeakReference<AndSync.UpdateListener<?>>> iterator = mUpdateListener.iterator(); 
				iterator.hasNext(); ) {
			Log.w("ANDSYNC", "Update listener [" + (++t) + "]");
			AndSync.UpdateListener<?> listener = iterator.next().get();
			if(listener == null) {
				iterator.remove();
				Log.w("ANDSYNC", "Removed listener [" + (t) + "]");
			} else {
				listener.onUpdate();
				Log.w("ANDSYNC", "Updated listener [" + (t) + "]");
			}
		}
	}
	
}