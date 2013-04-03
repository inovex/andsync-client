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
 * The interface any {@link Cache} need to implement for the documents it stores.
 * A cache document must store a {@link DBObject} and information about it (the collection
 * and its ObjectId). In what way this is stored by the cache depends on the specific implementation
 * of this interface and the {@link Cache} interface.
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
		/**
		 * The document has been created on this client and never transmitted to the server.
		 */
		NEVER_TRANSMITTED(0),
		/**
		 * The document has already been transmitted to the server (by any client), and has been
		 * updated on this client. The updates haven't been commited to the server yet.
		 */
		UPDATE_NOT_TRANSMITTED(1),
		/**
		 * The client deleted the document, but the deletion hasn't been commited to the server yet.
		 */
		DELETED(2),
		/**
		 * The current version of the document has already been transmitted to the server.
		 */
		TRANSMITTED(100);
		
		private int mNumValue;
		
		/**
		 * Get the {@link TransmittedState} from its integer value (the one returned by {@link #getNumValue()}
		 * of that object.
		 * 
		 * @see #getNumValue()
		 * 
		 * @param value The integer value of the transmitted state.
		 * @return The transmitted state for that value.
		 */
		public static TransmittedState fromNumValue(int value) {
			for(TransmittedState state : TransmittedState.values()) {
				if(state.getNumValue() == value) return state;
			}
			return null;
		}
		
		TransmittedState(int numValue) {
			mNumValue = numValue;
		}
		
		/**
		 * Returns the numerical value for this {@link TransmittedState}. This calue can be used
		 * in {@link #fromNumValue(int)} to get the TransmittedState object.
		 * 
		 * @see #fromNumValue(int)
		 * 
		 * @return The numeric value of that state.
		 */
		public int getNumValue() {
			return mNumValue;
		}
	};
	
	/**
	 * Returns the {@link TransmittedState} of that cache document. This describes if it has already
	 * been posted to server, or updated since the last post, etc. See the {@link TransmittedState} 
	 * documentation for all the values.
	 * 
	 * @return its transmitted state
	 */
	TransmittedState getState();
	
	/**
	 * Returns the name of the collection this cache document belongs to. The collection is always the
	 * full class name of the class this cache document represents.
	 * 
	 * @return The name of the collection
	 */
	String getCollection();
	
	/**
	 * Returns the {@link ObjectId} of this object. The {@code ObjectId} is given by the mapper before 
	 * passed to the cache. So this method can never return {@code null} or an invalid {@code ObjectId}.
	 * 
	 * @return The ObjectId of the object.
	 */
	ObjectId getObjectId();
	
	/**
	 * Returns the {@link DBObject} represented by this cache document.
	 * 
	 * @return The {@link DBObject} represented by this cache document.
	 */
	DBObject getDBObject();
	
}
