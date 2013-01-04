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
package de.inovex.andsync.rest;

import java.net.URL;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
public abstract class RestClient {
	
	/**
	 * Create a new {@link RestClient} to the given {@link URL}.
	 * 
	 * @param baseUrl The {@link URL base URL}, that request are sent to.
	 * @return An {@link RestClient} to use for communication with the given {@link URL}.
	 */
	public static RestClient create(URL baseUrl) {
		return new HttpUrlConnectionRestClient(baseUrl);
	}
	
	/**
	 * Sends a {@code GET} request to the server.
	 * 
	 * @param path The path, that should be appended to the base URL. 
	 * @return The {@link RestResponse response} from the server.
	 * @throws RestException if some error occurs.
	 */
	public abstract RestResponse get(String... path) throws RestException;
	
	/**
	 * Sends a {@code DELETE} request to the server.
	 * 
	 * @param path The path, that should be appended to the base URL.
	 * @return The {@link RestResponse response} from the server. The response won't hold any data 
	 *		(only the response code).
	 * @throws RestException if some error occurs.
	 */
	public abstract RestResponse delete(String... path) throws RestException;
	
	/**
	 * Sends a {@code PUT} request to the server.
	 * 
	 * @param data The data to send in the body of the request.
	 * @param path The path, that should be appended to the base URL.
	 * @return The {@link RestResponse response} from the server. The response won't hold any data
	 *		(only the response code).
	 * @throws RestException if some error occurs.
	 */
	public abstract RestResponse put(byte[] data, String... path) throws RestException;
	
	/**
	 * Sends a {@code POST} request to the server.
	 * 
	 * @param data The data to send in the body of the request.
	 * @param path The path, that should be appended to the base URL.
	 * @return The {@link RestResponse response} from the server. The response won't hold any data
	 *		(only the response code).
	 * @throws RestException if some error occurs.
	 */
	public abstract RestResponse post(byte[] data, String... path) throws RestException;
	
	/**
	 * Represents the response for a REST request.
	 * This will hold the response code and the data of the response.
	 */
	public static class RestResponse {
		
		/**
		 * The HTTP response code.
		 */
		public final int code;
		
		/**
		 * The response content.
		 */
		public final byte[] data;

		/**
		 * Creates a new response with the given response code and data.
		 * 
		 * @param responseCode HTTP response code.
		 * @param responseData The content data of the response.
		 */
		public RestResponse(int responseCode, byte[] responseData) {
			this.code = responseCode;
			this.data = responseData;
		}	
		
		/**
		 * Creates a new response with the given response code.
		 * The response data will be {@code null}.
		 */
		public RestResponse(int responseCode) {
			this(responseCode, null);
		}
		
	}
	
}
