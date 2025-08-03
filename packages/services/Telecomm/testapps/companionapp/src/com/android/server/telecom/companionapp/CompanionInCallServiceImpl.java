/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.companionapp;

import android.content.Intent;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.Phone;
import android.util.Log;
import android.widget.Toast;

import java.lang.Override;
import java.lang.String;

/**
 * Test Companion In-Call service implementation.
 */
public class CompanionInCallServiceImpl extends InCallService {
    private static final String TAG = "CompanionInCallServiceImpl";

    private Phone mPhone;

    private Phone.Listener mPhoneListener = new Phone.Listener() {
        @Override
        public void onCallAdded(Phone phone, Call call) {
            Log.i(TAG, "onCallAdded: " + call.toString());
            Toast.makeText(
                    CompanionInCallServiceImpl.this, "onCallAdded", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCallRemoved(Phone phone, Call call) {
            Log.i(TAG, "onCallRemoved: " + call.toString());
            Toast.makeText(
                    CompanionInCallServiceImpl.this, "onCallRemoved", Toast.LENGTH_LONG).show();
        }
    };

    @Override
    public void onPhoneCreated(Phone phone) {
        Log.i(TAG, "onPhoneCreated");
        mPhone = phone;
        mPhone.addListener(mPhoneListener);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Companion TestApp InCallService unbind");
        mPhone.removeListener(mPhoneListener);
        mPhone = null;
        return super.onUnbind(intent);
    }

    /**
     * Used to bind a call
     * @param intent
     * @return
     */
    @Override
    public android.os.IBinder onBind(Intent intent) {
        Log.d(TAG, "Companion TestApp InCallService bind");
        return super.onBind(intent);
    }
}
