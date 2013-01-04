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
 * 
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class AndSync {

	private static ObjectManager sManager;
	private static Config sConfig;
	
	public static Config getConfig() {
		return sConfig;
	}
	
	public static void initialize(Config config) {
		if(sConfig != null) {
			throw new IllegalStateException("Cannot initialize AndSync again. Config already exists.");
		}
		sConfig = config;
		sManager = new ObjectManager();
	}
	
	/**
	 * Checks whether AndSync has already been initialized. Throws an exception if it hasn't initialized
	 * yet.
	 * @throws IllegalStateException Will be thrown, if AndSync hasn't been initialized.
	 */
	private static void checkState() {
		if(sConfig == null) {
			throw new IllegalStateException("AndSync.initialize(..) has to be called before using AndSync.");
		}
	}
	
	public static void save(Object obj) {
		checkState();
		sManager.save(obj);
	}
	
	public static <T> T findFirst(Class<T> clazz) {
		checkState();
		return sManager.findFirst(clazz);
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