/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class TrimMemService extends Service {
    private static final int COMMAND_TRIM_MEMORY_LEVEL = IBinder.FIRST_CALL_TRANSACTION;
    private static final int COMMAND_CACHED = IBinder.FIRST_CALL_TRANSACTION + 1;

    private static final long POLL_INTERVAL_MS = 1000L;

    private static final int INVALID_ADJ = -10000; // ProcessList.INVALID_ADJ
    private static final int CACHED_APP_MIN_ADJ = 900; // ProcessList.CACHED_APP_MIN_ADJ

    private Binder mRemote = new Binder();
    private IBinder mCallback;
    private Handler mHandler = new Handler();
    private final Runnable mOomScoreAdjChecker = this::pollOomScoreAdjForCached;

    @Override
    public IBinder onBind(Intent intent) {
        final Bundle extras = intent.getExtras();
        mCallback = extras.getBinder(CommandReceiver.EXTRA_CALLBACK);
        pollOomScoreAdjForCached();
        return mRemote;
    }

    @Override
    public void onTrimMemory(int level) {
        sendSimpleTransaction(COMMAND_TRIM_MEMORY_LEVEL, level);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mOomScoreAdjChecker);
    }

    private void sendSimpleTransaction(int command, int payload) {
        if (mCallback != null) {
            final Parcel data = Parcel.obtain();
            final Parcel reply = Parcel.obtain();
            data.writeInt(payload);
            try {
                mCallback.transact(command, data, reply, 0);
            } catch (RemoteException e) {
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    private void pollOomScoreAdjForCached() {
        if (getOomScoreAdj() >= CACHED_APP_MIN_ADJ) {
            sendSimpleTransaction(COMMAND_CACHED, 0 /* ignored */);
        } else {
            mHandler.postDelayed(mOomScoreAdjChecker, POLL_INTERVAL_MS);
        }
    }

    private int getOomScoreAdj() {
        try (BufferedReader reader = new BufferedReader(
                new FileReader("/proc/self/oom_score_adj"))) {
            return Integer.parseInt(reader.readLine().trim());
        } catch (IOException | NumberFormatException e) {
            return INVALID_ADJ;
        }
    }

    private static class MyMemFactorCallback extends Binder {
        private CountDownLatch[] mLatchHolder;
        private int[] mLevelHolder;
        private CountDownLatch mCachedLatch;

        MyMemFactorCallback(CountDownLatch[] latchHolder, int[] levelHolder,
                CountDownLatch cachedLatch) {
            mLatchHolder = latchHolder;
            mLevelHolder = levelHolder;
            mCachedLatch = cachedLatch;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case COMMAND_TRIM_MEMORY_LEVEL:
                    mLevelHolder[0] = data.readInt();
                    mLatchHolder[0].countDown();
                    return true;
                case COMMAND_CACHED:
                    mCachedLatch.countDown();
                    return true;
                default:
                    return false;
            }
        }
    }

    private static class MyServiceConnection implements ServiceConnection {
        private CountDownLatch mLatch;

        MyServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    public static ServiceConnection bindToTrimMemService(String packageName, String instanceName,
            CountDownLatch[] latchHolder, int[] levelHolder, Context context) throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final Intent intent = new Intent();
        intent.setClassName(packageName, "android.app.stubs.TrimMemService");
        final Bundle extras = new Bundle();
        extras.putBinder(CommandReceiver.EXTRA_CALLBACK,
                new MyMemFactorCallback(latchHolder, levelHolder, latch));
        intent.putExtras(extras);
        final MyServiceConnection conn = new MyServiceConnection(latch);
        context.bindIsolatedService(intent, Context.BIND_AUTO_CREATE | Context.BIND_WAIVE_PRIORITY,
                instanceName, AsyncTask.THREAD_POOL_EXECUTOR, conn);
        latch.await();
        return conn;
    }
}

