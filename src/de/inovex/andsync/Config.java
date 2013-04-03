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

import de.inovex.andsync.manager.LazyList;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Configuration of AndSync. You need to create an object (via its {@link Builder}) and pass
 * it to {@link AndSync#initialize(android.app.Application, de.inovex.andsync.Config)} in your
 * {@link android.app.Application#onCreate() Applications's onCreate} method. 
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class Config {
	
	private final URL mUrl;
	private final String mGcmKey;
	private final boolean mCommitList;
	
	private Config(Builder builder) {
		this.mUrl = builder.mUrl;
		this.mGcmKey = builder.mGcmKey;
		this.mCommitList = builder.mCommitList;
	}
	
	/**
	 * Returns the server URL of the AndSync server.
	 * 
	 * @return Server URL
	 */
	public URL getUrl() {
		return mUrl;
	}
	
	/**
	 * Returns the GCM project key of the AndSync server.
	 * 
	 * @see Builder#gcmKey(java.lang.String)
	 * 
	 * @return GCM project key
	 */
	public String getGcmKey() {
		return mGcmKey;
	}
	
	/**
	 * Returns the default auto commit state for {@link LazyList}.
	 * 
	 * @see Builder#defaultCommitList(boolean) 
	 * 
	 * @return Default auto commit state
	 */
	public boolean getDefaultAutoCommit() {
		return mCommitList;
	}
	
	/**
	 * Creates a {@link Config configuration}.
	 */
	public static class Builder {
		
		private URL mUrl;
		private String mGcmKey;
		private boolean mCommitList;
		
		/**
		 * Creates a new Builder to create a {@link Config}.
		 * 
		 * @param url The {@link URL} of the AndSync server to connect to and store objects.
		 */
		public Builder(URL url) {
			this.mUrl = url;
		}
		
		/**
		 * Creates a new Builder to create a {@link Config}.
		 * 
		 * @param url The {@code URL} of the AndSync server to connect to and store objects (as a
		 *		String).
		 * @throws MalformedURLException will be thrown, if the given string wasn't a valid URL.
		 */
		public Builder(String url) throws MalformedURLException {
			this.mUrl = new URL(url);
		}
		
		/**
		 * Sets the GCM project key. This is the project key (part of your Google API console URL
		 * as described in: http://developer.android.com/google/gcm/gs.html#create-proj 
		 * (Don't confuse it with the API key, that is needed on the server to send messages.)
		 * <p>
		 * If you don't set a key no PUSH messaging is used to inform this client about modified
		 * objects. Only a call to {@link AndSync#findAll(java.lang.Class, de.inovex.andsync.AndSync.UpdateListener)}
		 * will start a new request to the server.
		 * 
		 * @param key The project key as described above.
		 * @return This builder.
		 */
		public Builder gcmKey(String key) {
			this.mGcmKey = key;
			return this;
		}
		
		/**
		 * Sets the default commit behavior for all {@link de.inovex.andsync.manager.LazyList LazyLists}.
		 * By default you need to submit all modifications (including adding and deleting objects) to
		 * AndSync manually. If you use the {@code LazyList} as received by AndSynd and set this option
		 * to {@code true} adding of new objects and deletion of objects will be recognized automatically.
		 * <p>
		 * Every method call to any changing method of the list (e.g. {@code add}, {@code remove}
		 * or {@code set}) will result in AndSync saving that object. You still have to save the object
		 * manually, if you just change the object (but without any impact on the list).
		 * <p>
		 * This option sets the default behavior for every {@link LazyList}. You can change it 
		 * for each list individually with {@link LazyList#setAutoCommit(boolean)}.
		 * 
		 * @see LazyList#setAutoCommit(boolean)
		 * 
		 * @param autoCommit Whether to automatically commit changes on {@link LazyList LazyLists}
		 *		to AndSync.
		 * @return This builder.
		 */
		public Builder defaultCommitList(boolean autoCommit) {
			this.mCommitList = autoCommit;
			return this;
		}
			
		/**
		 * Creates the {@link Config configuration}.
		 * 
		 * @return The configuration with all set parameters.
		 */
		public Config build() {
			return new Config(this);
		}
		
	}
	
}
