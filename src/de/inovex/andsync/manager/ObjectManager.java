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

import java.util.concurrent.ExecutorService;
import android.util.Log;
import de.inovex.andsync.AndSync;
import de.inovex.andsync.lucene.Cache;
import de.inovex.andsync.rest.RestClient;
import de.inovex.jmom.Storage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import static de.inovex.andsync.Constants.*;

/**
 * The {@code ObjectManager} is the core of the client library. It takes care
 * to load and save Objects, using the Cache and requesting objects from server
 * if needed.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class ObjectManager {
	
	private Storage mCacheStorage;
	private Storage mRestStorage;
	
	private Cache cache;
	
	private ExecutorService executor = Executors.newCachedThreadPool();
	
	private ListenerHandler listeners = new ListenerHandler();
	
	public ObjectManager() {
		
		try {
			cache = new Cache();
			mCacheStorage = Storage.getInstance(new CacheStorageHandler(cache));
		} catch (Exception ex) {
			Log.w(LOG_TAG, "Cannot create cache dir. Disabling cache for this session.", ex);
		}
		
		
		RestClient restClient = RestClient.create(AndSync.getConfig().getUrl());
		mRestStorage = Storage.getInstance(new RestStorageHandler(restClient));	
		
	}
	
	public void save(final Object obj) {
		
		if(mCacheStorage != null) {
			mCacheStorage.save(obj);
		}
		
		Runnable r = new Runnable() {
			public void run() {
				mRestStorage.save(obj);
			}
		};
		
		//mRestStorage.save(obj);
	}
	
	public <T> T findFirst(Class<T> clazz) {
		return mRestStorage.findFirst(clazz);
	}
	
	public <T> List<T> findAll(final Class<T> clazz) {
		
		Runnable r = new Runnable() {
			public void run() {
				List<T> objs = mRestStorage.findAll(clazz);
				listeners.updateListener(clazz, objs);
			}
		};
		
		executor.submit(r);
		
		if(mCacheStorage == null)
			return new ArrayList<T>();
		
		return mCacheStorage.findAll(clazz);
		
	}
	
	public void delete(Object obj) {
		mRestStorage.delete(obj);
	}
	
	public void registerListener(ObjectListener listener, Class<?>[] classes) {
		listeners.registerListener(listener, classes);
	}

	public void removeListener(ObjectListener listener) {
		listeners.removeListener(listener);
	}

}