/*
 * Copyright 2012 Tim Roes <tim.roes@inovex.de>.
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
package de.inovex.andsync;

import de.inovex.andsync.manager.ObjectListener;
import de.inovex.andsync.manager.ObjectManager;
import java.util.List;

/**
 * Servers as interface to AndSync. Use the methods of this class, to save, update or delete
 * objects. You need to call {@link #initialize(de.inovex.andsync.Config)} one time before using 
 * any other methods.
 * 
 * This class solely serves as a facade for the {@link ObjectManager} class. You should always use
 * this class to use AndSync.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class AndSync {

	private static ObjectManager sManager;
	
	public static void initialize(Config config) {
		if(sManager != null) {
			throw new IllegalStateException("Cannot initialize AndSync again.");
		}
		sManager = new ObjectManager(config);
	}
	
	/**
	 * Checks whether AndSync has already been initialized. Throws an exception if it hasn't initialized
	 * yet.
	 * @throws IllegalStateException Will be thrown, if AndSync hasn't been initialized.
	 */
	private static void checkState() {
		if(sManager == null) {
			throw new IllegalStateException("AndSync.initialize(..) has to be called before using AndSync.");
		}
	}
	
	public static Config getConfig() {
		checkState();
		return sManager.getConfig();
	}
	
	public static void save(Object obj) {
		checkState();
		sManager.save(obj);
	}
	
	public static void saveMultiple(Iterable<?> objects) {
		checkState();
		sManager.saveMultiple(objects);
	}

	public static <T> List<T> findAll(Class<T> clazz) {
		checkState();
		return sManager.findAll(clazz);
	}
	
	public static void delete(Object obj) {
		checkState();
		sManager.delete(obj);
	}
	
	public static void registerListener(ObjectListener listener, Class<?>... classes) {
		checkState();
		sManager.registerListener(listener, classes);
	}
	
	public static void removeListener(ObjectListener listener) {
		checkState();
		sManager.removeListener(listener);
	}
	
}