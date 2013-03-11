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
package de.inovex.andsync.gcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import com.google.android.gcm.GCMBroadcastReceiver;

/**
 * This {@link BroadcastReceiver} receives the messages from the GCM server. It forwards all the
 * messages to an {@link GCMIntentService}, which type (class name) must be returned from 
 * {@link #getGCMIntentServiceClassName(android.content.Context)}.
 * 
 * @author Tim Roes <tim.roes@inovex.de>
 */
public class GCMReceiver extends GCMBroadcastReceiver {

	/**
	 * Returns the class name of the {@link GCMIntentService}, that will handle all the received
	 * messages.
	 * 
	 * @param context The current context.
	 * @return The class name of the service, that should handle all messages.
	 */
	@Override
	protected String getGCMIntentServiceClassName(Context context) {
		return de.inovex.andsync.GCMIntentService.class.getName();
	}
	
}
