/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.hardware.CarPropertyValue;
import android.car.test.CarTestManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class E2eCarTestBase {
    private static final String TAG = Utils.concatTag(E2eCarTestBase.class);
    private static final int DEFAULT_WAIT_TIMEOUT_MS = 5000;
    private static final String COMMAND_SAVE_PROP = "--save-prop";
    private static final String COMMAND_RESTORE_PROP = "--restore-prop";
    private static final String COMMAND_GENFAKEDATA = "--genfakedata";
    private static final String COMMAND_INJECT_EVENT = "--inject-event";
    private static final List<String> REQUIRED_COMMANDS = List.of(COMMAND_SAVE_PROP,
            COMMAND_RESTORE_PROP, COMMAND_GENFAKEDATA, COMMAND_INJECT_EVENT);

    private final CarConnectionListener mConnectionListener = new CarConnectionListener();

    protected static final long VHAL_DUMP_TIMEOUT_MS = 1000;
    protected Car mCar;
    protected Context mContext;
    protected CarTestManager mCarTestManager;

    @Before
    public void connectToCarService() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mCar = Car.createCar(mContext, mConnectionListener);
        assertThat(mCar).isNotNull();
        mCar.connect();
        mConnectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        mCarTestManager = (CarTestManager) mCar.getCarManager(Car.TEST_SERVICE);
    }

    @After
    public void disconnect() {
        if (mCar != null) {
            mCar.disconnect();
            mCar = null;
        }
    }

    // Check whether reference AIDL VHAL is used, must run before any test that depends on
    // reference AIDL VHAL behavior.
    protected void checkRefAidlVHal() throws Exception {
        // Only run the tests for AIDL reference VHAL because the tests depend on debug commands
        // that are only supported on it.
        assumeTrue("AIDL VHAL is not used, skip VHAL integration test",
                mCarTestManager.hasAidlVhal());

        String helpInfo = mCarTestManager.dumpVhal(List.of("--help"), VHAL_DUMP_TIMEOUT_MS);

        List<String> unsupportedCommands = new ArrayList<>();

        for (int i = 0; i < REQUIRED_COMMANDS.size(); i++) {
            String command = REQUIRED_COMMANDS.get(i);
            if (!commandSupported(helpInfo, command)) {
                unsupportedCommands.add(command);
            }
        }
        assumeTrue("The debug commands: " + unsupportedCommands + " is not supported by VHAL, is "
                + "reference VHAL used?", unsupportedCommands.size() == 0);
    }

    protected void saveProperty(int propId, int areaId) throws Exception {
        String propIdStr = Integer.toString(propId);
        String areaIdStr = Integer.toString(areaId);

        String result = mCarTestManager.dumpVhal(List.of("--save-prop", propIdStr, "-a",
                areaIdStr), VHAL_DUMP_TIMEOUT_MS);

        if (!result.contains("saved")) {
            throw new Exception("Unable to save property, result: " + result);
        }
    }

    protected void restoreProperty(int propId, int areaId) throws Exception {
        String propIdStr = Integer.toString(propId);
        String areaIdStr = Integer.toString(areaId);

        String result = mCarTestManager.dumpVhal(List.of("--restore-prop", propIdStr, "-a",
                areaIdStr), VHAL_DUMP_TIMEOUT_MS);

        if (!result.contains("restored")) {
            throw new Exception("Unable to restore property, result: " + result);
        }
    }

    protected List<CarPropertyValue> getExpectedEvents(String fileName)
            throws IOException, JSONException {
        try (InputStream in = mContext.getAssets().open(fileName)) {
            Log.d(TAG, "Reading expected events from file: " + fileName);
            return VhalJsonReader.readFromJson(in);
        }
    }

    protected String getTestFileContent(String fileName) throws IOException {
        try (InputStream in = mContext.getAssets().open(fileName)) {
            Log.d(TAG, "Reading test data from file: " + fileName);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private boolean commandSupported(String helpInfo, String command) throws Exception {
        return helpInfo.contains(command);
    }

    private static class CarConnectionListener implements ServiceConnection {
        private final ConditionVariable mConnectionWait = new ConditionVariable();

        void waitForConnection(long timeoutMs) {
            mConnectionWait.block(timeoutMs);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mConnectionWait.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    }
}
