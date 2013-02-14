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

import com.mongodb.DBObject;
import com.mongodb.DBRef;
import de.inovex.andsync.cache.Cache;
import de.inovex.jmom.FieldList;
import de.inovex.jmom.Storage;
import java.util.Collection;
import org.bson.types.ObjectId;

/**
 * This handler is used in the {@link Storage cache storage} inside the {@link ObjectManager}
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
class CacheStorageHandler implements Storage.DBHandler {
	
	private Cache mCache;
	
	public CacheStorageHandler(Cache cache) {
		assert cache != null;
		this.mCache = cache;
	}

	/**
	 * {@inheritDoc}
	 */
	public void onSave(String string, DBObject dbo) {
		/**
		 * Save isn't done by the cache Storage, but by the REST storage. This is needed, because
		 * the REST storage generates the ObjectIds for new objects, but the cache needs this id
		 * to save an object. The option to create the Id outside of both storages, doesn't work,
		 * since the REST storage need to detect, if an object has already been send to the server
		 * or if it's new. This is determined by checking if the object has already an id.
		 */
		assert false;
	}

	/**
	 * {@inheritDoc}
	 */
	public Collection<DBObject> onGet(String collection, FieldList fl) {
		Collection<DBObject> dbos = mCache.getAll(collection);
		return dbos;
	}

	/**
	 * {@inheritDoc}
	 */
	public DBRef onCreateRef(String collection, DBObject dbo) {
		return new DBRef(null, collection, dbo.get("_id"));
	}

	/**
	 * {@inheritDoc}
	 */
	public DBObject onFetchRef(DBRef dbref) {
		return mCache.getById((ObjectId)dbref.getId());
	}

	/**
	 * {@inheritDoc} 
	 */
	public void onDelete(String collection, ObjectId objectId) {
		mCache.delete(collection, objectId);
	}
	
}
