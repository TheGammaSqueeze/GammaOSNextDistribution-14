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

package android.car.cts.app;

import android.app.Activity;
import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.annotation.concurrent.GuardedBy;

/**
 * To test car power policy:
 *
 *     <pre class="prettyprint">
 *         adb shell am force-stop android.car.cts.app
 *         adb shell am start -n android.car.cts.app/.PowerPolicyTestActivity \
 *                --es "powerpolicy" "action"
 *         action:
 *            settest [testcase_name]
 *            cleartest
 *            dumppolicy [policyId]
 *            addlistener [component_name]
 *            removelistener [component_name]
 *            dumplistener [component_name]
 *            resetlisteners
 *            waitlisteners
 *     </pre>
 */
public final class PowerPolicyTestActivity extends Activity {
    private static final int RESULT_LOG_SIZE = 4096;
    private static final String TAG = PowerPolicyTestActivity.class.getSimpleName();

    private final Object mLock = new Object();

    // LocalLog is not available for cts. Use StringWriter instead.
    // The host side test will kill and restart the app for each test case,
    // therefore 4KB buffer size is sufficient.
    private final StringWriter mResultBuf = new StringWriter(RESULT_LOG_SIZE);
    private final PrintWriter mResultLog = new PrintWriter(mResultBuf);
    private final PowerPolicyTestClient mTestClient = new PowerPolicyTestClient(mResultLog);

    private Car mCarApi;
    @GuardedBy("mLock")
    private CarPowerManager mPowerManager;

    @Nullable
    public CarPowerManager getPowerManager() {
        synchronized (mLock) {
            return mPowerManager;
        }
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.printf("%s\n", mResultBuf);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.d(TAG, "onNewIntent(): empty extras");
            return;
        }

        try {
            PowerPolicyTestCommand cmd = mTestClient.parseCommand(extras);
            if (cmd == null) {
                Log.d(TAG, "onNewIntent(): null policy test command");
                return;
            }
            cmd.execute();
        } catch (Exception e) {
            Log.e(TAG, "onNewIntent(): failed to handle cmd", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initCarApi();

        Log.d(TAG, "onCreate");
        onNewIntent(getIntent());
    }

    private void initCarApi() {
        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
            mCarApi = null;
        }
        mCarApi = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    initManagers(car, ready);
                });
        mTestClient.setPowerManager(mPowerManager);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        super.onDestroy();
    }

    private void initManagers(Car car, boolean ready) {
        synchronized (mLock) {
            if (ready) {
                mPowerManager = (CarPowerManager) car.getCarManager(
                        android.car.Car.POWER_SERVICE);
                Log.d(TAG, "initManagers() completed");
            } else {
                mPowerManager = null;
                Log.wtf(TAG, "initManagers() set to be null");
            }
        }
    }
}
