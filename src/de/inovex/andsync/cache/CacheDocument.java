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
import org.bson.types.ObjectId;

/**
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public interface CacheDocument {
	
	/**
	 * Represents the transmitted state of an object. This will change if
	 * depending if the server knows about the object, changes of the object or its deletion.
	 * 
	 * The numeric value of each element is used to store in the cache.
	 */
	public enum TransmittedState {
		NEVER_TRANSMITTED(0),
		UPDATE_NOT_TRANSMITTED(1),
		DELETED(2),
		TRANSMITTED(100);
		
		private int mNumValue;
		
		public static TransmittedState fromNumValue(int value) {
			for(TransmittedState state : TransmittedState.values()) {
				if(state.getNumValue() == value) return state;
			}
			return null;
		}
		
		TransmittedState(int numValue) {
			mNumValue = numValue;
		}
		
		public int getNumValue() {
			return mNumValue;
		}
	};
	
	TransmittedState getState();
	
	String getCollection();
	
	ObjectId getObjectId();
	
	DBObject getDBObject();
	
}
