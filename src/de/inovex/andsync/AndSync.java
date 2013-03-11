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
package de.inovex.andsync;

import android.util.Log;
import android.app.Application;
import android.content.Context;
import de.inovex.andsync.manager.ObjectListener;
import de.inovex.andsync.manager.AndSyncManager;
import java.util.List;

/**
 * Servers as interface to AndSync. Use the methods of this class, to save, update or delete
 * objects. You need to call {@link #initialize(de.inovex.andsync.Config)} one time before using 
 * any other methods.
 * 
 * This class solely serves as a facade for the {@link AndSyncManager} class. You should always use
 * this class to use AndSync.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class AndSync {

	private static AndSyncManager sManager;
	private static Context sContext;
	private static Config sConfig;

	public static void initialize(Application context, Config config) {
		Log.w("ANDSYNC", "Initialize");
		
		if (sContext != null) {
			throw new IllegalStateException("Cannot initialize AndSync again.");
		}

		sConfig = config;
		sContext = context;
		sManager = new AndSyncManager(config);
		
		Log.w("ANDSYNC", "Initialized");
		
	}

	/**
	 * Checks whether AndSync has already been initialized. Throws an exception if it hasn't initialized
	 * yet.
	 * @throws IllegalStateException Will be thrown, if AndSync hasn't been initialized.
	 */
	private static void checkState() {
		if (sContext == null) {
			throw new IllegalStateException("AndSync.initialize(..) has to be called before using AndSync.");
		}
	}
	
	static AndSyncManager getManager() {
		checkState();
		return sManager;
	}
	
	public static Config getConfig() {
		checkState();
		return sConfig;
	}

	public static Context getContext() {
		checkState();
		return sContext;
	}

	public static void save(Object obj) {
		checkState();
		sManager.save(obj);
	}

	public static void saveMultiple(Iterable<?> objects) {
		checkState();
		sManager.saveMultiple(objects);
	}

	public static <T> List<T> findAll(Class<T> clazz, UpdateListener<T> listener) {
		Log.w("ANDSYNC", "findAll " + clazz.getName());
		checkState();
		return sManager.findAll(clazz, listener);
	}

	public static void delete(Object obj) {
		checkState();
		sManager.delete(obj);
	}
	
	public interface UpdateListener<T> {
		
		/**
		 * Will be called when new data has been loaded. The new complete list of data will be
		 * passed to this method. You can just use this instead of the previous used one.
		 * 
		 * There are several constraints you need to respect when implementing this method:
		 * <ul><li>
		 * The framework might never call this method (if there is no new data), but it can
		 * also call it multiple times, even if you called {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}
		 * just one time.
		 * </li><li>
		 * It will always be called from another thread, then you called {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}.
		 * If you want to update UI, you have to make sure to do that back in your UI thread and
		 * NOT the one this method will be called.
		 * </li></ul>
		 * 
		 * @param data An updated version of the data you requested with {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}.
		 *		This data is at the time of calling complete, so you don't need to merge this data with
		 *		previously retrieved data.
		 */
		void onData(List<T> data);
		
		/**
		 * Will be called by the framework when new data is available at the server.
		 * Normally you just call {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}
		 * in the implementation of this method, and pass it this {@link UpdateListener} as parameter.
		 * So the framework will just collect the new data and pass it the the same {@link #onData(java.util.List)}
		 * method, that anyway needs to handle new data.
		 */
		void onUpdate();
		
	}
}