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

package com.android.telephony.imsmedia;

import android.os.Handler;
import android.os.Parcel;
import android.telephony.imsmedia.TextConfig;

import com.android.telephony.imsmedia.util.Log;

/**
 * Text listener to process JNI messages from local AP based RTP stack
 */
public class TextListener implements JNIImsMediaListener {
    private static final String TAG = "TextListener";
    private final Handler mHandler;
    private ImsMediaController.OpenSessionCallback mCallback;
    private long mNativeObject;

    TextListener(final Handler handler) {
        Log.d(TAG, "TextListener() -" + TextListener.this);
        mHandler = handler;
    }

    /**
     * Sets callback to call ImsMediaController to handle responses when openSession method called
     * in @TextService
     *
     * @param callback A Callback of @ImsMediaController#OpenSessionCallback
     */
    public void setMediaCallback(final ImsMediaController.OpenSessionCallback callback) {
        mCallback = callback;
    }

    /**
     * Sets native object to identify the instance of @BaseManager
     *
     * @param object the native instance of TextManager
     */
    public void setNativeObject(final long object) {
        mNativeObject = object;
    }

    /**
     * Processes parcel messages from native code and posts messages to {@link TextSession}
     *
     * @param parcel A parcel from native @TextManager in libimsmedia library
     */
    @Override
    public void onMessage(final Parcel parcel) {
        final int event = parcel.readInt();
        Log.dc(TAG, "onMessage() -" + TextListener.this + ", event=" + event);
        switch (event) {
            case TextSession.EVENT_OPEN_SESSION_SUCCESS:
                final int sessionId = parcel.readInt();
                mCallback.onOpenSessionSuccess(sessionId,
                        new TextLocalSession(sessionId, mNativeObject));
                break;
            case TextSession.EVENT_OPEN_SESSION_FAILURE:
                mCallback.onOpenSessionFailure(parcel.readInt(),
                        parcel.readInt());
                break;
            case TextSession.EVENT_MODIFY_SESSION_RESPONSE:
                final int result = parcel.readInt();
                final TextConfig config = TextConfig.CREATOR.createFromParcel(parcel);
                Utils.sendMessage(mHandler, event, result, Utils.UNUSED, config);
                break;
            case TextSession.EVENT_MEDIA_INACTIVITY_IND:
                Utils.sendMessage(mHandler, event, parcel.readInt(), Utils.UNUSED);
                break;
            case TextSession.EVENT_RTT_RECEIVED:
                final String text = parcel.readString();
                Utils.sendMessage(mHandler, event, text);
                break;
            case TextSession.EVENT_SESSION_CLOSED:
                mCallback.onSessionClosed(parcel.readInt());
                break;
            default:
                break;
        }
    }
}
