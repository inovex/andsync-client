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

import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bson.types.ObjectId;

/**
 * This is and empty implementation of the {@link Cache} interface. It doesn't do anything.
 * It can be used, to prevent a check for {@code null} before every call to the cache.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class CacheMock implements Cache {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<CacheDocument> getAll(String collection) {
		return new ArrayList<CacheDocument>(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<ObjectId> getAllIds(String collection) {
		return new ArrayList<ObjectId>(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheDocument getById(ObjectId id) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<CacheDocument> getUntransmitted() {
		return new ArrayList<CacheDocument>(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(String collection, DBObject dbo) { }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void put(String collection, List<DBObject> dbos) { }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putUpdated(String collection, DBObject dbo) { }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putUpdated(String collection, List<DBObject> dbos) { }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putTransmitted(String Collection, DBObject dbo) { }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putTransmitted(String collection, List<DBObject> dbos) { }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void markDeleted(String collection, ObjectId id) { }

	/**
	 * {@inheritDoc}
	 */
	public void deleted(String collection, ObjectId id) { }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deleted(String collection, long timestamp) {	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void commit() { }	
	
}
