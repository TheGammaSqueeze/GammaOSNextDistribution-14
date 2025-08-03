/**
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.telephony.testimsmediahal;

import android.os.Parcel;
import android.telephony.imsmedia.ImsMediaSession;

import com.android.telephony.imsmedia.JNIImsMediaService;

/**
 * Connect with {@link libimsmedia} and sending request for opensession
 * or close session, on success it will get respose back based on success
 * or failure case.
 */

public class JNIConnector{

    private static String TAG = "ImsMediaConnector";

    private static long mNativeObject;
    private static AudioListenerProxy mNativeListener = null;
    private static JNIConnector mInstance;
    private static JNIImsMediaService mJNIServiceInstance;

    private JNIConnector() {
        mJNIServiceInstance = JNIImsMediaService.getInstance();
    }

    public static JNIConnector getInstance() {
        if (mInstance == null)
        {
            mInstance = new JNIConnector();
        }

        return mInstance;
    }

    /**
     * Sends RTP session request to {@link libimsmedia}.
     *
     * @param sessionId used to identify unique session.
     * @param Parcel contains event identifier and session related info.
     */

    public void sendRequest(int sessionId, Parcel parcel) {
        if (mNativeObject != 0) {
            byte[] data = parcel.marshall();
            parcel.recycle();
            parcel = null;
            //send to native
            mJNIServiceInstance.sendMessage(mNativeObject, sessionId, data);
        }
    }

    /**
     * Doing connection with {@link libimsmedia} via {@link libimsmediahaljni}
     * @param sessionId The unique identification for session listener
     */
    public void connectJni(final int sessionId) {
        mNativeListener = AudioListenerProxy.getInstance();
        mNativeObject = mJNIServiceInstance.getInterface(ImsMediaSession.SESSION_TYPE_AUDIO);
        mJNIServiceInstance.setListener(sessionId, mNativeListener);
    }
}