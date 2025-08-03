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
import android.hardware.tv.tuner.Result;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.media.tv.tuner.frontend.FrontendSettings;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

public class TunerResourceTestService extends Service {
    private static final String TAG = "TunerResourceTestService";
    private Context mContext = null;
    private Tuner mTuner = null;
    private FrontendSettings mFeSettings;
    private FrontendInfo mFeInfo;
    private final Object mLock = new Object();

    @Override
    public IBinder onBind(Intent intent) {
        mContext = this;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        synchronized (mLock) {
            closeTunerInternal();
            return false;
        }
    }

    private void closeTunerInternal() {
        if (mTuner != null) {
            mTuner.close();
            mTuner = null;
        }
    }

    private int tuneInternal(int frontendIndex) {
        // make sure mTuner is not null
        if (mTuner == null) {
            Log.e(TAG, "tune called on null tuner");
            return Result.INVALID_STATE;
        }

        // construct FrontendSettings to tune
        List<FrontendInfo> infos = mTuner.getAvailableFrontendInfos();
        mFeInfo = infos.get(frontendIndex);
        mFeSettings = TunerTest.createFrontendSettings(mFeInfo);

        // tune
        return  mTuner.tune(mFeSettings);
    }

    private final ITunerResourceTestServer.Stub mBinder = new ITunerResourceTestServer.Stub() {
        public void createTuner(int useCase) {
            synchronized (mLock) {
                closeTunerInternal();
                mTuner = new Tuner(mContext, null, useCase);
            }
        }

        public void tuneAsync(int frontendIndex) {
            synchronized (mLock) {
                tuneInternal(frontendIndex);
            }
        }

        public int tune(int frontendIndex) {
            synchronized (mLock) {
                return tuneInternal(frontendIndex);
            }
        }

        public void closeTuner() {
            synchronized (mLock) {
                closeTunerInternal();
            }
        }

        public boolean verifyTunerIsNull() {
            synchronized (mLock) {
                return mTuner == null;
            }
        }
    };
}
