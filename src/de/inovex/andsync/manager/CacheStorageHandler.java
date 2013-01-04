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
import de.inovex.andsync.lucene.Cache;
import de.inovex.jmom.FieldList;
import de.inovex.jmom.Storage;
import java.util.Collection;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class CacheStorageHandler implements Storage.DBHandler {
	
	private Cache mCache;
	
	public CacheStorageHandler(Cache cache) {
		this.mCache = cache;
	}

	public void onSave(String string, DBObject dbo) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public DBObject onGetFirst(String string, FieldList fl) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Collection<DBObject> onGet(String string, FieldList fl) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public DBRef onCreateRef(String string, DBObject dbo) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public DBObject onFetchRef(DBRef dbref) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public void onDelete(String string, ObjectId oi) {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
}
