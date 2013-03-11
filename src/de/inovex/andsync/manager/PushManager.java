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
package de.inovex.andsync.manager;

import de.inovex.andsync.AndSync;
import android.content.Context;
import android.util.Log;
import com.google.android.gcm.GCMRegistrar;
import static de.inovex.andsync.Constants.*;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class PushManager {
	
	private Context mContext;
	
	private boolean mIsEnabled = true;
	
	private String mProjectId;
	
	public PushManager(String projectId) {
		mContext = AndSync.getContext();
		if(projectId == null || projectId.isEmpty()) {
			mIsEnabled = false;
		} else {
			mProjectId = projectId;
			try {
				GCMRegistrar.checkDevice(mContext);
			} catch(UnsupportedOperationException ex) {
				// Device doesn't support GCM, so disable the push service
				mIsEnabled = false;
			}
		}
	}
	
	public void init() {
		if(!isEnabled()) return;
		String regId = GCMRegistrar.getRegistrationId(mContext);
		if("".equals(regId)) {
			Log.i(LOG_TAG, "Register for GCM messages.");
			GCMRegistrar.register(mContext, mProjectId);
		} else {
			Log.i(LOG_TAG, "Already registered for GCM messages " + regId);
			// TODO: check if not registered at server yet.
			//GCMRegistrar.register(mContext, mProjectId);
		}
	}
	
	private boolean isEnabled() {
		return mIsEnabled;
	}

	
	
}
