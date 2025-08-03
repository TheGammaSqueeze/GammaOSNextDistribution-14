/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.gba.cts;

final class TestGbaConfig {

    static final int STATE_UNKNOWN = 0;
    static final int STATE_CREATED = 1;
    static final int STATE_BOUND   = 2;
    static final int STATE_UNBOUND = 3;
    static final int STATE_REMOVED = 4;

    private boolean mIsAuthSuccess;
    private byte[] mGbaKey;
    private String mBTid;
    private int mFailReason;
    private int mServiceState;

    private static TestGbaConfig sInstance;

    private TestGbaConfig() {
    }

    static TestGbaConfig getInstance() {
        if (sInstance == null) {
            sInstance = new TestGbaConfig();
        }
        return sInstance;
    }

    void setConfig(boolean success, byte[] key, String id, int reason) {
        synchronized (this) {
            mIsAuthSuccess = success;
            mGbaKey = key;
            mBTid = id;
            mFailReason = reason;
        }
    }

    boolean isAuthSuccess() {
        synchronized (this) {
            return mIsAuthSuccess;
        }
    }

    byte[] getGbaKey() {
        synchronized (this) {
            return mGbaKey;
        }
    }

    String getBTid() {
        synchronized (this) {
            return mBTid;
        }
    }

    int getFailReason() {
        synchronized (this) {
            return mFailReason;
        }
    }

    void setServiceState(int state) {
        synchronized (this) {
            mServiceState = state;
            this.notify();
        }
    }

    int getServiceState() {
        synchronized (this) {
            return mServiceState;
        }
    }
}
