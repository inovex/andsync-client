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
package de.inovex.andsync.rest;

import android.util.Log;
import static de.inovex.andsync.Constants.*;

/**
 * Keeps retrying REST calls to the server if connection fails.
 * Every method of this client will block until the call has finished or {@link #MAX_RETRIES}
 * calls has been tried and failed. If no result could be retrieved till then, the method will 
 * return {@code null}.
 */
public class RepeatingRestClient extends RestClient {

	/**
	 * The wrapped {@link RestClient} to use for communication.
	 */
	private RestClient mWrapped;

	/**
	 * The maximum delay in milliseconds after trying a call again.
	 */
	private final long MAX_RETRY_TIME = 60000;
	private final int MAX_RETRIES = 10;

	/**
	 * Creates a new repeating REST client. The specified REST client will be used for the actual calls.
	 * 
	 * @param wrappedClient The {@link RestClien} all calls will be made through.
	 */
	public RepeatingRestClient(RestClient wrappedClient) {
		assert mWrapped != null;
		mWrapped = wrappedClient;
	}

	@Override
	public RestResponse get(final String... path) {
		return call(new RestCall() {
			public RestResponse doCall() throws RestException {
				return mWrapped.get(path);
			}
		});
	}

	@Override
	public RestResponse delete(final String... path) {
		return call(new RestCall() {
			public RestResponse doCall() throws RestException {
				return mWrapped.delete(path);
			}
		});
	}

	@Override
	public RestResponse put(final byte[] data, final String... path) {
		return call(new RestCall() {
			public RestResponse doCall() throws RestException {
				return mWrapped.put(data, path);
			}
		});
	}

	@Override
	public RestResponse post(final byte[] data, final String... path) {
		return call(new RestCall() {
			public RestResponse doCall() throws RestException {
				return mWrapped.post(data, path);
			}
		});
	}

	/**
	 * Retry the given call until it succeeds, or the maximum limit of tries has been exceeded.
	 * Double the delay every time a call fails, until the {@link #MAX_RETRY_TIME maximum
	 * time limit} has been reached.
	 * 
	 * TODO: This method should be rewritten in a cleaner way.
	 * 
	 * @param call The call to repeat.
	 * @return The response from that call.
	 */
	private RestResponse call(RestCall call) {
		RestResponse response = null;
		long wait = 2000;
		int retry = 0;
		do {
			try {
				// Try to make a call.
				response = call.doCall();
			} catch (RestException ex) {
				Log.d(LOG_TAG, String.format("Could not make REST call. Trying again in %d ms. "
						+ "[Caused by %s]", wait, ex.getCause().getMessage()));
				try {
					// If an exception has been thrown, sleep and double sleeping time for next
					// sleep (until MAX_RETRY_TIME has been reached).
					Thread.sleep(wait);
					wait = Math.min(wait * 2, MAX_RETRY_TIME);
				} catch (InterruptedException ex1) {
				//	Logger.getLogger(RestStorageHandler.class.getName()).log(Level.SEVERE, null, ex1);
				}
			}
			retry++;
		// Retry until we have a response or we tried MAX_RETRIES times.
		} while ((response == null || response.code < 200 || response.code >= 300) && retry < MAX_RETRIES);
		return response;
	}

	private interface RestCall {
		RestResponse doCall() throws RestException;
	}

}
