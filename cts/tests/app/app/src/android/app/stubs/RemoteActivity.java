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

package android.app.stubs;

import android.app.Activity;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * An empty helper activity.
 */
public final class RemoteActivity extends Activity {

    /** Extras to the launching intent */
    public static final String EXTRA_CALLBACK = "callback";

    private final IBinder mStub = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case IBinder.FIRST_CALL_TRANSACTION:
                    finish();
                    return true;
                default:
                    return false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final IBinder callback = intent.getExtras().getBinder(EXTRA_CALLBACK);
        final Parcel data = Parcel.obtain();
        try {
            data.writeStrongBinder(mStub);
            callback.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        } finally {
            data.recycle();
        }
    }
}
