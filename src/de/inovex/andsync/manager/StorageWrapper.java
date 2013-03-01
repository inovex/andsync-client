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

import de.inovex.jmom.Config;
import de.inovex.jmom.Storage;
import de.inovex.jmom.Storage.Cache;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class StorageWrapper {
	
	private Storage mStorage;
	
	public StorageWrapper() {
		
	}
	
	public StorageWrapper(Storage storage) {
		this.mStorage = storage;
	}

	public void setConfig(Config config) {
		if(this.mStorage != null) {
			mStorage.setConfig(config);
		}
	}

	public void setCache(Cache cache) {
		if(this.mStorage != null) {
			mStorage.setCache(cache);
		}
	}

	public Cache getCache() {
		return (this.mStorage != null) ? mStorage.getCache() : null;
	}
	
	public void saveMultiple(Iterable<?> objects) {
		if(this.mStorage != null) {
			mStorage.saveMultiple(objects);
		}
	}

	public void save(Object obj) {
		if(this.mStorage != null) {
			mStorage.save(obj);
		}
	}

	public Config getConfig() {
		return (this.mStorage != null) ? mStorage.getConfig() : new Config();
	}

	public <T> List<T> findAll(Class<T> clazz) {
		return (this.mStorage != null) ? mStorage.findAll(clazz) : new ArrayList<T>();
	}

	public <T> T findByObjectId(Class<T> clazz, ObjectId id) {
		return (this.mStorage != null) ? mStorage.findByObjectId(clazz, id): null;
	}

	public void delete(Object obj) {
		if(this.mStorage != null) {
			mStorage.delete(obj);
		}
	}
	
}
