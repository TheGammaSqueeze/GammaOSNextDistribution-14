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

package android.telephony.imsmedia;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * API to manage the text session
 *
 * @hide
 */
public class ImsTextSession implements ImsMediaSession {
    private static final String TAG = "ImsTextSession";
    private final IImsTextSession mSession;

    /** @hide */
    public ImsTextSession(final IImsTextSession session) {
        mSession = session;
    }

    /** @hide */
    @Override
    public IBinder getBinder() {
        return mSession.asBinder();
    }

    /** {@inheritDoc} */
    public int getSessionId() {
        try {
            return mSession.getSessionId();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get session ID: " + e);
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public void modifySession(final RtpConfig config) {
        try {
            mSession.modifySession((TextConfig) config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to modify session: " + e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setMediaQualityThreshold(final MediaQualityThreshold threshold) {
        try {
            mSession.setMediaQualityThreshold(threshold);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set media quality threshold: " + e);
        }
    }

    /**
     * Send Rtt text stream
     *
     * @param text The text string
     */
    public void sendRtt(final String text) {
        try {
            mSession.sendRtt(text);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to request video data usage: " + e);
        }
    }
}
