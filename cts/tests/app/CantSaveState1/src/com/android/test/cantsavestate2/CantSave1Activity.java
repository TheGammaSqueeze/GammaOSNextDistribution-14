/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.test.cantsavestate1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public class CantSave1Activity extends Activity {

    public static final String ACTION_FINISH = "com.android.test.action.FINISH";
    public static final String EXTRA_CALLBACK = "android.app.stubs.extra.callback";

    private IBinder mCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cant_save_1_activity);
        getWindow().getDecorView().requestFocus();
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            mCallback = extras.getBinder(EXTRA_CALLBACK);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        if (mCallback != null) {
            final Parcel data = Parcel.obtain();
            final Parcel reply = Parcel.obtain();
            data.writeInt(level);
            try {
                mCallback.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);
            } catch (RemoteException e) {
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (ACTION_FINISH.equals(intent.getAction())) {
            finish();
        }
    }
}
