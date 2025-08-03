/*
 * Copyright 2021 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.cts.ISharedFilterTestServer;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.SharedFilter;
import android.media.tv.tuner.filter.SharedFilterCallback;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;

public class SharedFilterTestService extends Service {
    private static final String TAG = "SharedFilterTestService";
    private Context mContext = null;
    private Tuner mTuner = null;
    private Filter mFilter = null;
    private boolean mTuning = false;

    @Override
    public IBinder onBind(Intent intent) {
        mContext = this;
        mTuner = new Tuner(mContext, null, 100);
        return new SharedFilterTestServer();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mTuner.close();
        mTuner = null;
        return false;
    }

    private class SharedFilterTestServer extends ISharedFilterTestServer.Stub {
        @Override
        public String acquireSharedFilterToken() {
            mFilter = TunerTest.createTsSectionFilter(
                    mTuner, getExecutor(), getFilterCallback());

            // Tune a frontend before start the filter
            List<FrontendInfo> infos = mTuner.getAvailableFrontendInfos();
            mTuner.tune(TunerTest.createFrontendSettings(infos.get(0)));
            mTuning = true;

            return mFilter.acquireSharedFilterToken();
        }

        @Override
        public void closeFilter() {
            if (mTuning) {
                mTuner.cancelTuning();
                mTuning = false;
            }
            mFilter.close();
            mFilter = null;
        }

        @Override
        public void freeSharedFilterToken(String token) {
            if (mTuning) {
                mTuner.cancelTuning();
                mTuning = false;
            }
            mFilter.freeSharedFilterToken(token);
        }

        @Override
        public boolean verifySharedFilter(String token) {
            SharedFilter f = Tuner.openSharedFilter(
                    mContext, token, getExecutor(), getSharedFilterCallback());
            if (f == null) {
                Log.e(TAG, "SharedFilter is null");
                return false;
            }
            if (f.start() != Tuner.RESULT_SUCCESS) {
                f = null;
                Log.e(TAG, "Failed to start SharedFilter");
                return false;
            }
            if (f.flush() != Tuner.RESULT_SUCCESS) {
                f.close();
                f = null;
                Log.e(TAG, "Failed to flush SharedFilter");
                return false;
            }
            int size = f.read(new byte[3], 0, 3);
            if (size < 0 || size > 3) {
                f.close();
                f = null;
                Log.e(TAG, "Failed to read from SharedFilter");
                return false;
            }
            if (f.stop() != Tuner.RESULT_SUCCESS) {
                f.close();
                f = null;
                Log.e(TAG, "Failed to stop SharedFilter");
                return false;
            }
            f.close();
            f = null;
            return true;
        }
    }

    private FilterCallback getFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
    }

    private SharedFilterCallback getSharedFilterCallback() {
        return new SharedFilterCallback() {
            @Override
            public void onFilterEvent(SharedFilter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(SharedFilter filter, int status) {}
        };
    }

    private Executor getExecutor() { return Runnable::run; }
}
