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
import android.util.SparseArray;
import de.inovex.andsync.cache.Cache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.util.NamedThreadFactory;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class LazyList<T> implements List<T> {

	private SparseArray<T> mObjects;
	private List<ObjectId> mIds;
	
	private StorageWrapper mCacheStorage;
	private Cache mCache;
	private Class<T> mClazz;
	
	private Map<ObjectId,Object> mIdLocks = new ConcurrentHashMap<ObjectId, Object>();
	
	private Executor mExecutor = Executors.newSingleThreadExecutor(
			new NamedThreadFactory("LazyList Preloader"));
	
	LazyList(StorageWrapper cacheStorage, Cache cache, Class<T> clazz) {
		mCacheStorage = cacheStorage;
		mCache = cache;
		mClazz = clazz;
		
		// Load all ids into the id list
		Collection<ObjectId> ids = mCache.getAllIds(clazz.getName());
		
		mObjects = new SparseArray<T>(ids.size());
		mIds = new ArrayList<ObjectId>(ids.size());
		
		for(ObjectId id : ids) {
			mIdLocks.put(id, new Object());
			mIds.add(id);
		}
		
		mExecutor.execute(mLoaderRunnable);
		
	}
	
	public synchronized void add(int index, T obj) {
		// Move all objects above index that should be included to their above position
		for(int i = mIds.size(); i > index; i--) {
			mObjects.put(i, mObjects.get(i - 1));
		}
		// Add object to actual end of list
		mObjects.put(index, obj);
		// Add null for that object to id list
		mIds.add(index, null);
	}

	public synchronized boolean add(T obj) {
		mObjects.put(mIds.size(), obj);
		mIds.add(null);
		return true;
	}

	public boolean addAll(int arg0, Collection<? extends T> arg1) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean addAll(Collection<? extends T> arg0) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public synchronized void clear() {
		mObjects.clear();
		mIds.clear();
	}

	public boolean contains(Object obj) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean containsAll(Collection<?> arg0) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * Loads the requested object at the given position from the list.
	 * 
	 * This implementation pauses until the background thread has loaded the requested value from
	 * the cache.
	 * 
	 * @param index The zero-based index of the item.
	 * @return The object at the given index.
	 */
	public synchronized T get(int index) {
		
		ObjectId id = mIds.get(index);
		Object lock = (id != null) ? mIdLocks.get(id) : new Object();
		T obj = mObjects.get(index);
		
		// If null was added to the list (so no id available for a null object) just return null
		if(id == null && obj == null) return null;
		while(obj == null) {
			if(id == null) return null;
			synchronized(lock) {
				try {
					lock.wait(2000);
				} catch (InterruptedException ex) {
					Logger.getLogger(LazyList.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			id = mIds.get(index);
			lock = mIdLocks.get(id);
			obj = mObjects.get(index);
		}
		
		return obj;
		
	}

	public int indexOf(Object arg0) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public synchronized boolean isEmpty() {
		return mIds.isEmpty();
	}

	public Iterator<T> iterator() {
		return listIterator(0);
	}

	public synchronized int lastIndexOf(Object arg0) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public ListIterator<T> listIterator() {
		return listIterator(0);
	}

	public ListIterator<T> listIterator(int position) {
		return new LazyIterator(position);
	}

	/**
	 * Removes an object from the list and maybe return that object.
	 * Since this list implements lazy loading the element is only returned if it already has been
	 * loaded. If the object has not been loaded yet, it will be removed and {@code null} will be
	 * returned.
	 * 
	 * If you need to work on the object afterwards, retrieve it with a call to {@link #get(int)}.
	 * This method will wait until the object has been loaded.
	 * 
	 * @param index The index of the object to delete.
	 * @return The removed object or {@code null} if it hasn't been loaded yet.
	 */
	public synchronized T remove(int index) {
		if(index < 0 || index >= size()) {
			throw new IndexOutOfBoundsException();
		}
		ObjectId id = mIds.get(index);
		synchronized(mIdLocks.get(id)) {
			mIds.remove(id);
			mIdLocks.remove(id);
			T obj = mObjects.get(index);
			mObjects.remove(index);
			return obj;
		}
	}

	public synchronized boolean remove(Object obj) {
		if(obj == null) {
			for(int i = 0; i < size(); i++) {
				if(get(i) == null) {
					mObjects.remove(i);
					ObjectId id = mIds.remove(i);
					mIdLocks.remove(id);
					return true;
				}
			}
		} else {
			int size = size();
			for(int i = 0; i < size; i++) {
				if(obj.equals(get(i))) {
					mObjects.remove(i);
					for(int j = i; j + 1 < size; j++) {
						mObjects.put(j, get(j + 1));
					}
					mObjects.remove(size - 1);
					ObjectId id = mIds.remove(i);
					mIdLocks.remove(id);
					return true;
				}
			}
		}
		return false;
	}

	public boolean removeAll(Collection<?> arg0) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean retainAll(Collection<?> arg0) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public synchronized T set(int index, T obj) {
		mObjects.put(index, obj);
		mIds.set(index, null);
		return obj;
	}

	public int size() {
		return mIds.size();
	}

	public List<T> subList(int arg0, int arg1) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public synchronized Object[] toArray() {
		Object[] out = new Object[mIds.size()];
		for(int i = 0; i < mIds.size(); i++) {
			out[i] = get(i);
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	public synchronized <T> T[] toArray(T[] array) {
		return (T[])toArray();
	}
	
	private class LazyIterator implements ListIterator<T> {
		
		private final int mInitialSize;
		
		private int mCurrent = -1;
		private int mLastPosition = -1;
		
		private LazyIterator(int position) {
			mInitialSize = size();
			if(position < 0 || position >= LazyList.this.size()) {
				throw new IndexOutOfBoundsException(String.format("Start position %d of iterator is "
						+ "outside list size of %d", position, mInitialSize));
			}
			this.mCurrent = position - 1;
		}
		
		private void checKForConcurrentModification() {
			if(mInitialSize != size()) {
				throw new ConcurrentModificationException();
			}
		}
		
		public void add(T obj) {
			checKForConcurrentModification();
			throw new UnsupportedOperationException("The iterator doesn't support that operation.");
		}

		public boolean hasNext() {
			return mCurrent + 1 < LazyList.this.size();
		}

		public boolean hasPrevious() {
			return mCurrent >= 0;
		}

		public T next() {
			checKForConcurrentModification();
			T res = get(mCurrent + 1);
			mLastPosition = ++mCurrent;
			return res;
		}

		public int nextIndex() {
			return Math.min(mCurrent + 1, LazyList.this.size());
		}

		public T previous() {
			checKForConcurrentModification();
			T res = get(mCurrent);
			mLastPosition = mCurrent;
			mCurrent--;
			return res;
		}

		public int previousIndex() {
			return Math.max(mCurrent -1 , -1);
		}

		public void remove() {
			checKForConcurrentModification();
			throw new UnsupportedOperationException("The iterator doesn't support that operation.");
		}

		public void set(T obj) {
			checKForConcurrentModification();
			LazyList.this.set(mLastPosition, obj);
		}
		
	}
	
	/**
	 * Runnable to load all the objects in background.
	 */
	Runnable mLoaderRunnable = new Runnable() {

		public void run() {
			int size = mIds.size();
			for(int i = 0; i < size;) {
				ObjectId id = mIds.get(i);
				if(id == null) continue;
				Object lock = mIdLocks.get(id);
				synchronized(lock) {
					// If the object has been removed (or moved due to an other object has been added)
					// skip the fetch (but also don't increase iterator, since we need to fetch
					// the new element at this position in the next round).
					if(mIds.get(i) != id) continue;
					T object = mCacheStorage.findByObjectId(mClazz, id);
					mObjects.put(i, object);
					lock.notifyAll();
					// Get new size of list and point to next element
					size = mIds.size();
					i++;
				}
				
			}
		}
		
	};

}