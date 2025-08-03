/*
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

package com.android.telephony.qns;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

/** @hide */
class QnsRegistrant {
    protected WeakReference<Handler> mRefH;
    protected int mWhat;
    protected Object mUserObj;

    QnsRegistrant(Handler h, int what, Object obj) {
        mRefH = new WeakReference<>(h);
        mWhat = what;
        mUserObj = obj;
    }

    /** Clear registrant. */
    void clear() {
        mRefH = null;
        mUserObj = null;
    }

    /**
     * notify result
     *
     * @param result Object to notify
     */
    void notifyResult(Object result) {
        internalNotifyRegistrant(result, null);
    }

    /**
     * notify registrant
     *
     * @param ar QnsAsyncResult to notify
     */
    void notifyRegistrant(QnsAsyncResult ar) {
        internalNotifyRegistrant(ar.mResult, ar.mException);
    }

    protected void internalNotifyRegistrant(Object result, Throwable exception) {
        Handler h = getHandler();

        if (h == null) {
            clear();
        } else {
            Message msg = Message.obtain();

            msg.what = mWhat;
            msg.obj = new QnsAsyncResult(mUserObj, result, exception);
            h.sendMessage(msg);
        }
    }

    /**
     * get a handler.
     *
     * @return Handler
     */
    Handler getHandler() {
        if (mRefH == null) {
            return null;
        }

        return mRefH.get();
    }
}
