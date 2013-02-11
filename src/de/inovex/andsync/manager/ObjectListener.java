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
package de.inovex.andsync.manager;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public abstract class ObjectListener {
	

	/**
	 * Gets called whenever a new object has been added. Will get the object and its class as parameters.
	 * Override this to handle the creation of an object yourself. If you don't override this method,
	 * it will call {@link #onUpdate(java.lang.Class, java.util.List)} with a list solely containing
	 * the created object.
	 * 
	 * @param <T> The type of the object, that was created.
	 * @param clazz The {@link Class} object of the type.
	 * @param obj The object, that has been created.
	 */
	@SuppressWarnings("unchecked")
	public <T> void onCreate(Class<T> clazz, T obj) {
		List<T> list = new ArrayList<T>(1);
		list.add(obj);
		onUpdate((Class<T>)obj.getClass(), list);
	}
	
	public abstract <T> void onUpdate(Class<T> clazz, List<T> objects);
	
	public abstract void onDelete(Object obj);
	
}
