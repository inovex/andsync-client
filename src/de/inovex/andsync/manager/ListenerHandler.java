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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class ListenerHandler {
	
	private List<ObjectListener> globalListeners = new LinkedList<ObjectListener>();
	private Map<Class<?>, List<ObjectListener>> listeners = new HashMap<Class<?>, List<ObjectListener>>();
	
	
	/**
	 * Registers an {@link ObjectListener}, that will be informed about any updates on any type of
	 * objects. If you want to limit the notifications to one or multiple specific classes, use
	 * {@link #registerListener(de.inovex.andsync.ObjectListener, java.lang.Class<?>[])}.
	 * 
	 * @param listener The {@link ObjectListener} that get informed about updates.
	 */
	public void registerListener(ObjectListener listener) {
		registerListener(listener, (Class<?>)null);
	}
	
	public void registerListener(ObjectListener listener, Class<?>... classes) {
		if(classes == null || classes.length == 0) {
			globalListeners.add(listener);
		} else {
			for(Class<?> clazz : classes) {
				List<ObjectListener> l = listeners.get(clazz);
				if(l == null) {
					l = new LinkedList<ObjectListener>();
				}
				l.add(listener);
				listeners.put(clazz, l);
			}
		}
	}
	
	public void removeListener(ObjectListener listener) {
		globalListeners.remove(listener);
		for(List<ObjectListener> l : listeners.values()) {
			l.remove(listener);
		}
	}
	
	public <T> void updateListener(Class<T> clazz, List<T> objects) {
		for(ObjectListener l : globalListeners) {
			l.onUpdate(clazz, objects);
		}
		
		List<ObjectListener> list = listeners.get(clazz);
		for(ObjectListener l : list) {
			l.onUpdate(clazz, objects);
		}
	}
	
}
