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
package de.inovex.andsync;

import de.inovex.andsync.gcm.GCMReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.android.gcm.GCMBaseIntentService;
import de.inovex.andsync.manager.AndSyncManager;
import de.inovex.andsync.rest.RestClient;
import de.inovex.andsync.rest.RestException;
import static de.inovex.andsync.Constants.*;

/**
 * Receives all messages from the {@link GCMReceiver} and handle the messages.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class GCMIntentService extends GCMBaseIntentService {
	
	private RestClient mRestClient = RestClient.create(AndSync.getConfig().getUrl());

	private AndSyncManager mManager = AndSync.getManager();			
	
	/**
	 * Update message has been received.
	 * 
	 * @param cntxt The context.
	 * @param intent The intent holding information from the message.
	 */
	@Override
	protected void onMessage(Context cntxt, Intent intent) {
		Log.w(LOG_TAG, "Update message received.");
		mManager.onServerUpdate();
	}

	@Override
	protected void onError(Context cntxt, String string) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * Registration has been completed. Send the new id to the application server, so it can store
	 * it to send push messages.
	 * 
	 * @param cntxt The context.
	 * @param string The registration id received from GCM.
	 */
	@Override
	protected void onRegistered(Context cntxt, String id) {
		try {
			mRestClient.put(null, Constants.REST_CONTROL_PATH, id);
		} catch (RestException ex) {
			// TODO repeat authentication to server
			Log.w(LOG_TAG, "Registration id has not been send to server successfully.");
		}
	}

	@Override
	protected void onUnregistered(Context cntxt, String id) {
		try {
			mRestClient.delete(null, Constants.REST_CONTROL_PATH, id);
		} catch(RestException ex) {
			// TODO reapeat it
			Log.w(LOG_TAG, "Could not unregister from server.");
		}
	}
	
}
