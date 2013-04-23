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
import de.inovex.andsync.AndSync;
import de.inovex.andsync.AndSync.UpdateListener;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class ListenerManager {
	
	private Map<Class<?>,ListenerHandler<?>> mHandlers = new ConcurrentHashMap<Class<?>, ListenerHandler<?>>();
	private Map<Class<?>,LazyList<?>> mLists = new ConcurrentHashMap<Class<?>, LazyList<?>>();
	private Map<Class<?>,Object> mLocks = new ConcurrentHashMap<Class<?>, Object>();
	
	private Set<WeakReference<UpdateListener<?>>> mUpdateListeners = new HashSet<WeakReference<UpdateListener<?>>>();
	
	public synchronized Object getLock(Class<?> clazz) {
		Object lock = mLocks.get(clazz);
		if(lock == null) {
			lock = new Object();
			mLocks.put(clazz, lock);
		}
		return lock;		
	}
	
	public synchronized <T> LazyList<T> getRunningCall(Class<T> clazz) {
		
		@SuppressWarnings("unchecked") // We put lists always in with its class as they key. See (1)
		LazyList<T> list = (LazyList<T>) mLists.get(clazz);
		return list;
		
	}
	
	public synchronized <T> void addRunningCall(Class<T> clazz, LazyList<T> list) {
		
		// Only one call at a time should be executed. So adding a new call only works, when there
		// is no call currently running.
		assert mLists.get(clazz) != null;
		
		mLists.put(clazz, list); // (1)
		
	}
	
	public synchronized <T> void clearRunningCall(Class<T> clazz) {
		mLists.remove(clazz);
	}
	
	public synchronized <T> void addUpdateListener(Class<T> clazz, UpdateListener<T> listener) {
				
		@SuppressWarnings("unchecked") // We always put the right types together. See (2)
		ListenerHandler<T> handler = (ListenerHandler<T>)mHandlers.get(clazz);
		
		if(handler == null) {
			handler = new ListenerHandler<T>();
			mHandlers.put(clazz, handler); // (2)
		}
		
		// Add listener to the handler for this class
		WeakReference<UpdateListener<T>> ref = new WeakReference<UpdateListener<T>>(listener);
		handler.add(ref);
		
		// Add listener to set for all update listeners
		for(WeakReference<UpdateListener<?>> r : mUpdateListeners) {
			if(r.get() == listener) return;
		}
		WeakReference<UpdateListener<?>> updateRef = new WeakReference<UpdateListener<?>>(listener);
		mUpdateListeners.add(updateRef);
	
	}
	
	public synchronized void notifyAllUpdateListeners() {
		int t = 0;
		for(Iterator<WeakReference<AndSync.UpdateListener<?>>> iterator = mUpdateListeners.iterator(); 
				iterator.hasNext(); ) {
			Log.w("ANDSYNC", "Update listener [" + (++t) + "]");
			AndSync.UpdateListener<?> listener = iterator.next().get();
			if(listener == null) {
				iterator.remove();
				Log.w("ANDSYNC", "Removed listener [" + (t) + "]");
			} else {
				listener.onDataAvailable();
				Log.w("ANDSYNC", "Updated listener [" + (t) + "]");
			}
		}
	}
	
	public synchronized <T> Set<WeakReference<UpdateListener<T>>> getCallListeners(Class<T> clazz) {
		
		@SuppressWarnings("unchecked") // We always put the right types together. See (2)
		ListenerHandler<T> handler = (ListenerHandler<T>)mHandlers.get(clazz);
		
		if(handler != null) {
			return handler.mDataListeners;
		}
		
		// This should never be reached. This method is only called when a call finished, so at least
		// one listener should be there to wait for updates.
		assert false; 
		return null;
		
	}

	private class ListenerHandler<T> {
		
		private Set<WeakReference<UpdateListener<T>>> mDataListeners;
		
		private ListenerHandler() {
			mDataListeners = new HashSet<WeakReference<UpdateListener<T>>>();
		}
		
		public void add(WeakReference<UpdateListener<T>> weakRef) {
			addToSet(weakRef, mDataListeners);
		}
		
		private void addToSet(WeakReference<UpdateListener<T>> weakRef, Set<WeakReference<UpdateListener<T>>> set) {
			for(WeakReference<UpdateListener<T>> ref : set) {
				if(ref.get() == weakRef.get()) return;
			}
			set.add(weakRef);
		}
		
	}
	
}
