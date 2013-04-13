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
package de.inovex.andsync.cache;

/**
 * Offers meta information about the cache. An instance can be retrieved via {@link Cache#getCacheInformation()}.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public interface CacheInformation {
	
	/**
	 * Returns the timestamp in milliseconds of the last modified object in cache in the specified collection.
	 * This time is used for the next call to get objects, so only newer objects will be transfered.
	 * 
	 * @param collection The name of the collection.
	 * @return The millisecond timestamp of the last modification in this collection.
	 */
	long getLastModified(String collection);
	
	/**
	 * Sets the last modified time of a specific collection in cache. This value will be returned
	 * from {@link #getLastModified(java.lang.String)}. If the cache is corrupted or errors occur,
	 * that cannot guarantee cache integrity anymore, this should be set to 0. This should be
	 * used independent from this client's time, so this should be set to the highest last modification
	 * time of the objects retrieved from server.
	 * 
	 * @param collection The name of the collection.
	 * @param timestamp The millisecond timestamp of the last modification of this collection.
	 */
	void setLastModified(String collection, long timestamp);
	
	/**
	 * Clears all stored last modification timestamps. Directly after this call every call to
	 * {@link #getLastModified(java.lang.String)} will return 0.
	 */
	void clearLastModified();
	
}
