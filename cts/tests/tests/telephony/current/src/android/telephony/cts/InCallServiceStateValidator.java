/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.telephony.cts;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class InCallServiceStateValidator  extends InCallService {

    private static final String LOG_TAG = "InCallServiceStateValidator";
    private static final Object sLock = new Object();
    private static InCallServiceCallbacks sCallbacks;
    private static CountDownLatch sLatch  = new CountDownLatch(1);

    private final List<Call> mCalls = Collections.synchronizedList(new ArrayList<>());
    private final List<Call> mConferenceCalls = Collections.synchronizedList(new ArrayList<>());
    private final Object mLock = new Object();
    private boolean mIsServiceBound = false;

    public static class InCallServiceCallbacks {
        private InCallServiceStateValidator mService = null;
        public Semaphore lock = new Semaphore(0);

        public void onCallAdded(Call call, int numCalls) {};
        public void onCallRemoved(Call call, int numCalls) {};
        public void onCallStateChanged(Call call, int state) {};
        public void onChildrenChanged(Call call, List<Call> children) {};
        public void onConnectionEvent(Call call, String event, Bundle extras) {};

        public InCallServiceStateValidator getService() {
            if (mService == null) {
                try {
                    sLatch.await(3000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.d(LOG_TAG, "InterruptedException");
                }
                sLatch  = new CountDownLatch(1);
            }
            return mService;
        }

        public void setService(InCallServiceStateValidator service) {
            mService = service;
        }

        public void resetLock() {
            lock = new Semaphore(0);
        }
    }

    public static void setCallbacks(InCallServiceCallbacks callbacks) {
        synchronized (sLock) {
            sCallbacks = callbacks;
        }
    }

    private InCallServiceCallbacks getCallbacks() {
        synchronized (sLock) {
            if (sCallbacks != null) {
                sCallbacks.setService(this);
            }
            return sCallbacks;
        }
    }

    /**
     * Note that the super implementations of the callback methods are all no-ops, but we call
     * them anyway to make sure that the CTS coverage tool detects that we are testing them.
     */
    private Call.Callback mCallCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            if (getCallbacks() != null) {
                getCallbacks().onCallStateChanged(call, state);
            }
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            super.onChildrenChanged(call, children);
            if (getCallbacks() != null) {
                getCallbacks().onChildrenChanged(call, children);
            }
        }

        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {
            super.onConnectionEvent(call, event, extras);
            if (getCallbacks() != null) {
                getCallbacks().onConnectionEvent(call, event, extras);
            }
        }
    };

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        if (getCallbacks() != null) {
            getCallbacks().setService(this);
        }
        mIsServiceBound = true;
        sLatch.countDown();
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mIsServiceBound = false;
        mCalls.clear();
        return super.onUnbind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
            if (!mConferenceCalls.contains(call)) {
                mConferenceCalls.add(call);
                call.registerCallback(mCallCallback);
            }
        } else {
            if (!mCalls.contains(call)) {
                mCalls.add(call);
                call.registerCallback(mCallCallback);
            }
        }

        if (getCallbacks() != null) {
            getCallbacks().onCallAdded(call, mCalls.size());
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
            mConferenceCalls.remove(call);
        } else {
            mCalls.remove(call);
        }

        if (getCallbacks() != null) {
            getCallbacks().onCallRemoved(call, mCalls.size());
        }
        call.unregisterCallback(mCallCallback);
    }

    /**
    * @return the number of calls currently added to the {@code InCallService}.
    */
    public int getCallCount() {
        return mCalls.size();
    }

    public boolean isServiceBound() {
        return mIsServiceBound;
    }

    public boolean isServiceUnBound() {
        return !mIsServiceBound;
    }

    /**
     * @return the number of conference calls currently added to the {@code InCallService}.
     */
    public int getConferenceCallCount() {
        Log.d(LOG_TAG, "getConferenceCallCount = " + mConferenceCalls.size());
        return mConferenceCalls.size();
    }

    /**
     * @return the most recently added conference call that exists inside the {@code InCallService}
     */
    public Call getLastConferenceCall() {
        if (!mConferenceCalls.isEmpty()) {
            return mConferenceCalls.get(mConferenceCalls.size() - 1);
        }
        return null;
    }
}
