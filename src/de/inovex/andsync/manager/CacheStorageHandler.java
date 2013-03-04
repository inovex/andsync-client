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
import de.inovex.andsync.cache.CacheDocument;
import de.inovex.jmom.FieldList;
import de.inovex.jmom.Storage;
import java.util.ArrayList;
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
	@Override
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
	@Override
	public Collection<DBObject> onGet(String collection, FieldList fl) {
		/**
		 * This should never be called. If a list of all objects in cache should be retrieved
		 * a {@link LazyList} should be used. That list takes care of loading the elements in background
		 * and not everything at the beginning.
		 */
		assert false;
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public DBObject onGetById(String string, ObjectId id) {
		CacheDocument doc = mCache.getById(id);
		if(doc == null) return null;
		return doc.getDBObject();
	}	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DBRef onCreateRef(String collection, DBObject dbo) {
		return new DBRef(null, collection, dbo.get("_id"));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DBObject onFetchRef(DBRef dbref) {
		if(dbref == null) return null;
		CacheDocument cd = mCache.getById((ObjectId)dbref.getId());
		if(cd == null) return null;
		return cd.getDBObject();
	}

	/**
	 * {@inheritDoc} 
	 */
	@Override
	public void onDelete(String collection, ObjectId objectId) {
		mCache.markDeleted(collection, objectId);
	}
	
}
