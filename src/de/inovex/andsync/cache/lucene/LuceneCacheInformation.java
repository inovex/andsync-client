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
package de.inovex.andsync.cache.lucene;

import android.content.Context;
import android.content.SharedPreferences;
import de.inovex.andsync.AndSync;
import de.inovex.andsync.cache.CacheInformation;

/**
 * Implements {@link CacheInformation} by using Android's {@link SharedPreferences} to store the
 * last modification times. This will be used by {@link LuceneCache}.
 * 
 * @see LuceneCache
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
final class LuceneCacheInformation implements CacheInformation  {

	private final SharedPreferences mPrefs;
	
	/**
	 * Creates new instance of {@link LuceneCacheInformation} using the class name of {@link CacheInformation}
	 * as the name of the {@link SharedPreferences}.
	 */
	public LuceneCacheInformation() {
		mPrefs = AndSync.getContext()
				.getSharedPreferences(CacheInformation.class.getName(), Context.MODE_PRIVATE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public synchronized long getLastModified(String collection) {
		return mPrefs.getLong(collection, 0L);
	}

	/**
	 * {@inheritDoc} 
	 */
	public synchronized void setLastModified(String collection, long timestamp) {
		mPrefs.edit().putLong(collection, timestamp).commit();
	}

	/**
	 * {@inheritDoc}
	 */
	public void clearLastModified() {
		mPrefs.edit().clear().commit();
	}
	
}