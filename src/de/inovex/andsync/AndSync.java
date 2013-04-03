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

import android.app.Application;
import android.content.Context;
import de.inovex.andsync.manager.AndSyncManager;
import de.inovex.andsync.manager.LazyList;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Serves as interface to AndSync. Use the methods of this class, to save, update or delete
 * objects. You need to call {@link #initialize(de.inovex.andsync.Config)} one time before using 
 * any other method, otherwise an {@link IllegalStateException} will be thrown.
 * 
 * This class solely serves as a facade for the {@link AndSyncManager} class. You must always use
 * this class to use AndSync.
 * 
 * All methods except {@link #initialize(android.app.Application, de.inovex.andsync.Config)} can throw
 * an {@link IllegalStateException} if they are called before {@link #initialize(android.app.Application, de.inovex.andsync.Config)}
 * has been called.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class AndSync {
	
	/**
	 * Prevent inheritance and instantiation.
	 */
	private AndSync() { }

	private static AndSyncManager sManager;
	private static Context sContext;
	private static Config sConfig;

	/**
	 * Initializes AndSync with the given {@link Config} and {@link Application}.
	 * This method must be called before any other method is called.
	 * You normally should call this method from the {@link Application#onCreate()} method.
	 * If you don't use an {@code Application} class yet, create one and overwrite the {@code onCreate}
	 * method. You need to update your {@code AndroidManifest.xml} to use your {@code Application}.
	 * <p>
	 * An sample {@code onCreate} method could look as follows:
	 * <pre>
	 * {@code
	 * onCreate() {
	 *   super.onCreate();
	 *   Config config = new Config.Builder("server url").build();
	 *   AndSync.initialize(this, config);
	 * }
	 * }
	 * </pre>
	 * 
	 * See the {@link Config} class for more information on configuration AndSync.
	 * 
	 * @see Config
	 * 
	 * @param application The Android {@link Application} of your App. This is, among other things,
	 *		needed to get the cache directory and shared preferences.
	 * @param config The configuration AndSync should use. This configuration cannot be changed
	 *		afterwards anymore.
	 */
	public static void initialize(Application application, Config config) {
		
		if (sContext != null) {
			throw new IllegalStateException("Cannot initialize AndSync again.");
		}

		sConfig = config;
		sContext = application;
		sManager = new AndSyncManager(config);
		
	}

	/**
	 * Checks whether AndSync has already been initialized. Throws an exception if it hasn't initialized
	 * yet.
	 * 
	 * @throws IllegalStateException Will be thrown, if AndSync hasn't been initialized.
	 */
	private static void checkState() {
		if (sContext == null) {
			throw new IllegalStateException("AndSync.initialize(..) has to be called before using AndSync.");
		}
	}
	
	/**
	 * Returns the {@link AndSyncManager} that is the core class of this framework. This method will
	 * be called by the background services to get the manager.
	 * 
	 * @return The {@link AndSyncManager}.
	 */
	static AndSyncManager getManager() {
		checkState();
		return sManager;
	}
	
	/**
	 * Returns the {@link Config configuration} the user passed to 
	 * {@link #initialize(android.app.Application, de.inovex.andsync.Config)}.
	 * 
	 * @return Configuration of AndSync.
	 */
	public static Config getConfig() {
		checkState();
		return sConfig;
	}

	/**
	 * Returns the Android {@link Context} of the application. This will always be an 
	 * {@link Application Application context} and will always be valid as long as the application
	 * or any part of it, is running.
	 * 
	 * @return Application context as passed in to {@link #initialize(android.app.Application, de.inovex.andsync.Config)}
	 */
	public static Context getContext() {
		checkState();
		return sContext;
	}

	/**
	 * Saves an object via AndSync. This can be nearly any Java object. The limitations of the
	 * used mapper jMOM (https://github.com/inovex/jMOM/wiki/Limitations) also apply to objects
	 * passed to AndSync. If you put in objects that break with this limitations the outcome is
	 * undefined. (It might crash, might save the objects in a wrong way or even might work for
	 * some cases).
	 * <p>
	 * If the object is already known the framework will update it and send the modified version
	 * to the server. It won't check if the object is really modified since its last save (since
	 * the framework doesn't hold a shadow copy of the object to compare against).
	 * <p>
	 * If the object has never been known to the framework (it wasn't retrieved via a call to
	 * {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)} nor has been
	 * saved yet, it will be saved as a new object and send it to the server.
	 * <p>
	 * If you want to save multiple objects at once use the {@link #saveMultiple(java.lang.Iterable)}
	 * method. If you iterate over the objects yourself and call this method for each object it will
	 * be slower and create unnecessary load. If you put the actual {@link List} (or any container, 
	 * that holds the elements) into this object, it will just produce wrong outcome, since the list
	 * itself will be saved and not its elements.
	 * <p>
	 * If an object has already been saved will be checked by its identity (and not it's equality).
	 * Saving the same object multiple times will just update it (as above mentioned) and not save it
	 * a second time. Meaning if you have a list of your objects and store the very same object
	 * two times in it, a call to {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}
	 * will only return the object one time.
	 * 
	 * @param obj An Java object respecting the above mentioned limitations.
	 */
	public static void save(Object obj) {
		checkState();
		sManager.save(obj);
	}

	/**
	 * Saves several Java objects via AndSync. Use this method instead of {@link #save(java.lang.Object)}
	 * if you wish to save multiple objects. All other restriction and behavior is described in the
	 * documentation of {@link #save(java.lang.Object)}.
	 * 
	 * @param objects Any {@link Iterable} of objects.
	 */
	public static void saveMultiple(Iterable<?> objects) {
		checkState();
		sManager.saveMultiple(objects);
	}

	/**
	 * Returns a list of all objects for the given {@link Clazz}. The order of the objects in this
	 * list is not guaranteed to have any special order.
	 * <p>
	 * This method will return a {@link LazyList} with all cached objects and return this. The
	 * {@code LazyList} will load all elements in background to be able to return even large lists
	 * immediately from this method. See the documentation of {@link LazyList} for more information.
	 * The framework will load data from the server in background and call 
	 * {@link UpdateListener#onData(de.inovex.andsync.manager.LazyList) } on the specified {@link UpdateListener}
	 * as soon as the data has been fetched. If there will be new data on the server at any later time 
	 * (e.g. someone else updated the data) {@link UpdateListener#onUpdate()} will be called. See the 
	 * documentation of {@link UpdateListener} on how to implement these methods.
	 * <p>
	 * <b>Important:</b> Since most {@link UpdateListener} are linked to activities and might want
	 * to change the UI, it could come to problems, when data is updated while the UI is already
	 * destroyed. To prevent this, the framework does only save a {@link WeakReference} to the
	 * {@code UpdateListener}. This means it can be cleaned up by the GC as soon as you don't hold
	 * a reference to it anymore. So you need to hold a reference to the listener as long as you want
	 * to have updates (most likely you will hold a reference in an object field). I.e. don't create
	 * the {@code UpdateListener} as an anonymous object, it will be immediately free for garbage 
	 * collection, and you won't get any data or updates.
	 * 
	 * @see LazyList
	 * @see UpdateListener
	 * 
	 * @param clazz The {@link Class} of which all objects should be returned.
	 * @param listener An {@link UpdateListener} that will be notified about new data or available data.
	 * @return A {@link LazyList} containing all objects found in cache. New data from internet will
	 *		be passed to the specified {@link UpdateListener} when it's available.
	 */
	public static <T> LazyList<T> findAll(Class<T> clazz, UpdateListener<T> listener) {
		checkState();
		return sManager.findAll(clazz, listener);
	}

	/**
	 * Deletes an object from the mapper. This removes the object from the cache and from the server
	 * as soon as possible. If the object has never been registered to AndSync this call won't have
	 * any effect, but won't fail.
	 * 
	 * @param obj The object to delete.
	 */
	public static void delete(Object obj) {
		checkState();
		sManager.delete(obj);
	}
	
	/**
	 * Gets notification when new data for a requested call is available or could be fetched from
	 * server. Please pay attention about the lifespan of objects as mentioned in the documentation
	 * of {@link #findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}.
	 * 
	 * TODO: Use more reasonable method names
	 * 
	 * @param <T> The type of data that was requested from the framework.
	 */
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
		void onData(LazyList<T> data);
		
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