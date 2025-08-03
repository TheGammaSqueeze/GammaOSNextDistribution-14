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

package com.android.car.provision;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

/**
 * Default implementing UserNoticeUI. There will not be any UI shown but only message in logcat.
 */
public final class UserNoticeUiService extends Service {

    private static final String TAG = UserNoticeUiService.class.getSimpleName();

    private static final String IUSER_NOTICE_BINDER_DESCRIPTOR = "android.car.user.IUserNotice";
    private static final int IUSER_NOTICE_TR_ON_LOGGED =
            android.os.IBinder.FIRST_CALL_TRANSACTION;

    private static final String IUSER_NOTICE_UI_BINDER_DESCRIPTOR =
            "android.car.user.IUserNoticeUI";
    private static final int IUSER_NOTICE_UI_BINDER_TR_SET_CALLBACK =
            android.os.IBinder.FIRST_CALL_TRANSACTION;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final Object mLock = new Object();

    // Do not use IUserNoticeUI class intentionally to show how it can be
    // implemented without accessing the hidden API.
    private final IBinder mIUserNoticeUiBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case IUSER_NOTICE_UI_BINDER_TR_SET_CALLBACK:
                    Log.d(TAG, "onTransact, received call to set callBack");
                    data.enforceInterface(IUSER_NOTICE_UI_BINDER_DESCRIPTOR);
                    IBinder binder = data.readStrongBinder();
                    onSetCallbackBinder(binder);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    };

    @GuardedBy("mLock")
    private IBinder mIUserNoticeService;

    @Override
    public IBinder onBind(Intent intent) {
        return mIUserNoticeUiBinder;
    }

    private void onSetCallbackBinder(IBinder binder) {
        if (binder == null) {
            Log.wtf(TAG, "No binder set in onSetCallbackBinder call", new RuntimeException());
            return;
        }
        mMainHandler.post(() -> {
            synchronized (mLock) {
                mIUserNoticeService = binder;
            }
            showMessage();
            stopService();
        });
    }

    private void showMessage() {
        Log.i(TAG, "showing user notice for user: " + getUserId());
    }

    private void stopService() {
        IBinder userNotice;
        synchronized (mLock) {
            userNotice = mIUserNoticeService;
            mIUserNoticeService = null;
        }
        if (userNotice != null) {
            sendOnLoggedToCarService(userNotice);
        }
        stopSelf();
    }

    private void sendOnLoggedToCarService(IBinder userNotice) {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IUSER_NOTICE_BINDER_DESCRIPTOR);
        try {
            userNotice.transact(IUSER_NOTICE_TR_ON_LOGGED, data, null, 0);
        } catch (RemoteException e) {
            Log.w(TAG, "CarService crashed, finish now");
            stopSelf();
        }
    }
}
