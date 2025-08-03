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

package com.google.android.chre.test.chqts;

import android.hardware.location.NanoAppBinary;

import com.google.android.utils.chre.ChreApiTestUtil;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

import dev.chre.rpc.proto.ChreApiTest;
public class ContextHubNanoAppRequirementsTestExecutor extends ContextHubChreApiTestExecutor {
    private final List<Long> mPreloadedNanoappIds;

    private static final int CHRE_SENSOR_ACCELEROMETER_INTERVAL_NS = 20000000;
    private static final int CHRE_SENSOR_GYROSCOPE_INTERVAL_NS = 2500000;

    // TODO(b/262043286): Enable this once BLE is available
    /*
    private static final int CHRE_BLE_CAPABILITIES_SCAN = 1 << 0;
    private static final int CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA = 1 << 7;
    */

    private static final int CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT = 2;
    private static final int CHRE_SENSOR_TYPE_ACCELEROMETER = 1;
    private static final int CHRE_SENSOR_TYPE_GYROSCOPE = 6;

    private static final int CHRE_AUDIO_MIN_BUFFER_SIZE_NS = 2000000000;

    private static final int RPC_TIMEOUT_IN_SECONDS = 2;
    private static final int MAX_AUDIO_SOURCES_TO_TRY = 10;

    /**
     * Formats for audio that can be provided to a nanoapp. See enum chreAudioDataFormat in the
     * CHRE API.
     */
    public enum ChreAudioDataFormat {
        /**
         * Unsigned, 8-bit u-Law encoded data as specified by ITU-T G.711.
         */
        CHRE_AUDIO_DATA_FORMAT_8_BIT_U_LAW(0),

        /**
         * Signed, 16-bit linear PCM data. Endianness must be native to the local
         * processor.
         */
        CHRE_AUDIO_DATA_FORMAT_16_BIT_SIGNED_PCM(1);

        private final int mId;

        ChreAudioDataFormat(int id) {
            mId = id;
        }

        /**
         * Returns the ID.
         *
         * @return int      the ID
         */
        public int getId() {
            return mId;
        }
    }

    public ContextHubNanoAppRequirementsTestExecutor(NanoAppBinary nanoapp) {
        super(nanoapp);
        mPreloadedNanoappIds = new ArrayList<Long>();
        for (long nanoappId: mContextHubManager.getPreloadedNanoAppIds(mContextHub)) {
            mPreloadedNanoappIds.add(nanoappId);
        }
    }

    /**
     * Tests for specific sensors for activity.
     */
    public void assertActivitySensors() throws Exception {
        findDefaultSensorAndAssertItExists(CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT);
        int accelerometerHandle =
                findDefaultSensorAndAssertItExists(CHRE_SENSOR_TYPE_ACCELEROMETER);
        getSensorInfoAndVerifyInterval(accelerometerHandle,
                CHRE_SENSOR_ACCELEROMETER_INTERVAL_NS);
    }

    /**
     * Tests for specific sensors for movement.
     */
    public void assertMovementSensors() throws Exception {
        findDefaultSensorAndAssertItExists(CHRE_SENSOR_TYPE_ACCELEROMETER);
        int gyroscopeHandle =
                findDefaultSensorAndAssertItExists(CHRE_SENSOR_TYPE_GYROSCOPE);
        getSensorInfoAndVerifyInterval(gyroscopeHandle,
                CHRE_SENSOR_GYROSCOPE_INTERVAL_NS);

        findAudioSourceAndAssertItExists(CHRE_AUDIO_MIN_BUFFER_SIZE_NS,
                ChreAudioDataFormat.CHRE_AUDIO_DATA_FORMAT_16_BIT_SIGNED_PCM);
    }

    /**
     * Tests for specific BLE capabilities.
     */
    public void assertBleSensors() throws Exception {
        // TODO(b/262043286): Enable this once BLE is available
        /*
        mExecutor.getBleCapabilitiesAndAssertCapabilityExists(CHRE_BLE_CAPABILITIES_SCAN);
        mExecutor.getBleFilterCapabilitiesAndAssertCapabilityExists(
                CHRE_BLE_FILTER_CAPABILITIES_SERVICE_DATA);
        */
    }

    /**
     * Returns true if the nanoappId represents a preloaded nanoapp; false otherwise.
     */
    public boolean isNanoappPreloaded(long nanoappId) {
        return mPreloadedNanoappIds.contains(nanoappId);
    }

    /**
     * Finds the default sensor for the given type and asserts that it exists.
     *
     * @param sensorType        the type of the sensor (constant)
     *
     * @return                  the handle of the sensor
     */
    public int findDefaultSensorAndAssertItExists(int sensorType) throws Exception {
        ChreApiTest.ChreSensorFindDefaultInput input = ChreApiTest.ChreSensorFindDefaultInput
                .newBuilder().setSensorType(sensorType).build();
        ChreApiTest.ChreSensorFindDefaultOutput response =
                ChreApiTestUtil.callUnaryRpcMethodSync(getRpcClient(),
                        "chre.rpc.ChreApiTestService.ChreSensorFindDefault", input);
        Assert.assertTrue("Did not find sensor with type: " + sensorType,
                response.getFoundSensor());
        return response.getSensorHandle();
    }

    /**
     * Gets the sensor samping status and verifies the minimum interval from chreGetSensorInfo
     * is less than or equal to the expected interval -> the sensor is at least as fast at sampling
     * as is required.
     *
     * @param sensorHandle          the handle to the sensor
     * @param expectedInterval      the true sampling interval
     */
    public void getSensorInfoAndVerifyInterval(int sensorHandle, long expectedInterval)
            throws Exception {
        ChreApiTest.ChreHandleInput input =
                ChreApiTest.ChreHandleInput.newBuilder()
                .setHandle(sensorHandle).build();
        ChreApiTest.ChreGetSensorInfoOutput response =
                ChreApiTestUtil.callUnaryRpcMethodSync(getRpcClient(),
                        "chre.rpc.ChreApiTestService.ChreGetSensorInfo", input);
        Assert.assertTrue("Failed to get sensor info for sensor with handle: " + sensorHandle,
                response.getStatus());
        Assert.assertTrue("The sensor with handle: " + sensorHandle
                + " does not sample at a fast enough rate.",
                response.getMinInterval() <= expectedInterval);
    }

    /**
     * Iterates through possible audio sources to find a source that has a minimum buffer
     * size in ns of expectedMinBufferSizeNs and a format of format.
     *
     * @param expectedMinBufferSizeInNs         the minimum buffer size in nanoseconds (ns)
     * @param format                            the audio format enum
     */
    public void findAudioSourceAndAssertItExists(long expectedMinBufferSizeNs,
            ChreAudioDataFormat format) throws Exception {
        boolean foundAcceptableAudioSource = false;
        for (int i = 0; i < MAX_AUDIO_SOURCES_TO_TRY; ++i) {
            ChreApiTest.ChreHandleInput input =
                    ChreApiTest.ChreHandleInput.newBuilder()
                    .setHandle(i).build();
            ChreApiTest.ChreAudioGetSourceOutput response =
                    ChreApiTestUtil.callUnaryRpcMethodSync(getRpcClient(),
                            "chre.rpc.ChreApiTestService.ChreAudioGetSource", input);
            if (response.getStatus()
                    && response.getMinBufferDuration() >= expectedMinBufferSizeNs
                    && response.getFormat() == format.getId()) {
                foundAcceptableAudioSource = true;
                break;
            }
        }
        Assert.assertTrue("Did not find an acceptable audio source with a minimum buffer "
                + "size of " + expectedMinBufferSizeNs
                + " ns and format: " + format.name(),
                foundAcceptableAudioSource);
    }

    // TODO(b/262043286): Enable this once BLE is available
    /*
    /**
     * Gets the BLE capabilities and asserts the capability exists.
     *
     * @param capability        the capability to assert exists
     *
    public void getBleCapabilitiesAndAssertCapabilityExists(int capability) throws Exception {
        getCapabilitiesAndAssertCapabilityExists(
                "chre.rpc.ChreApiTestService.ChreBleGetCapabilities",
                capability,
                "Did not find the BLE capabilities");
    }

    /**
     * Gets the BLE filter capabilities and asserts the capability exists.
     *
     * @param capability        the capability to assert exists
     *
    public void getBleFilterCapabilitiesAndAssertCapabilityExists(int capability) throws Exception {
        getCapabilitiesAndAssertCapabilityExists(
                "chre.rpc.ChreApiTestService.ChreBleGetFilterCapabilities",
                capability,
                "Did not find the BLE filter capabilities");
    }
    */

    // TODO(b/262043286): Enable this once BLE is available
    /*
    /**
     * Gets the capabilities returned by RPC function: function and asserts that
     * capability exists with a failure message: errorMessage.
     *
     * @param function          the function to call
     * @param capability        the capability to assert exists
     * @param errorMessage      the error message to show when there is an assertion failure
     *
    private void getCapabilitiesAndAssertCapabilityExists(String function,
            int capability, String errorMessage) throws Exception {
        ChreApiTest.Capabilities capabilitiesResponse =
                ChreApiTestUtil.callUnaryRpcMethodSync(getRpcClient(), function);
        int capabilities = capabilitiesResponse.getCapabilities();
        Assert.assertTrue(errorMessage + ": " + capability,
                (capabilities & capability) != 0);
    }
    */
}
