/*
 * Copyright (C) 2016 The Android Open Source Project
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

import org.junit.Assert;

/**
 * These values come from nanoapps/general_test/test_names.h and
 * nanoapps/shared/send_message.h.
 *
 *  TODO: This is very fragile.  We should try to automate keeping this
 *     in sync.  That's made mildly more challenging by different
 *     language constructs and naming conventions.
 */
public class ContextHubTestConstants {
    /**
     * Names of the tests we support.
     */
    public enum TestNames {
        /**
         * Value which should never be used.
         */
        INVALID_TEST(0x0400),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[HelloWorld]
         */
        HELLO_WORLD(0x0401),

        /**
         * Test: ContextHubSimpleHeapAllocNanoAppTest
         */
        SIMPLE_HEAP_ALLOC(0x0402),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[HeapAllocStress]
         */
        HEAP_ALLOC_STRESS(0x0403),

        /**
         * Test: ContextHubGetTimeNanoAppTest
         */
        GET_TIME(0x0404),

        /**
         * Test: ContextHubEventBetweenAppsNanoAppTest
         */
        EVENT_BETWEEN_APPS0(0x0405),

        /**
         * Test: ContextHubEventBetweenAppsNanoAppTest
         */
        EVENT_BETWEEN_APPS1(0x0406),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[SendEvent]
         */
        SEND_EVENT(0x0407),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicAccelerometer]
         */
        BASIC_ACCELEROMETER(0x0408),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicInstantMotionDetect]
         */
        BASIC_INSTANT_MOTION_DETECT(0x0409),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicStationaryDetect]
         */
        BASIC_STATIONARY_DETECT(0x040A),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicGyroscope]
         */
        BASIC_GYROSCOPE(0x040B),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicMagnetometer]
         */
        BASIC_MAGNETOMETER(0x040C),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicBarometer]
         */
        BASIC_BAROMETER(0x040D),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicLightSensor]
         */
        BASIC_LIGHT_SENSOR(0x040E),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicProximity]
         */
        BASIC_PROXIMITY(0x040F),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[VersionConsistency]
         */
        VERSION_CONSISTENCY(0x0410),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[LoggingConsistency]
         */
        LOGGING_CONSISTENCY(0x0411),

        /**
         * Test: ContextHubSendMessageToHostNanoAppTest
         */
        SEND_MESSAGE_TO_HOST(0x0412),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[TimerSet]
         */
        TIMER_SET(0x0413),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[TimerCancel]
         */
        TIMER_CANCEL(0x0414),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[TimerStress]
         */
        TIMER_STRESS(0x0415),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[SendEventStress]
         */
        SEND_EVENT_STRESS(0x0416),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[HeapExhaustionStability]
         */
        HEAP_EXHAUSTION_STABILITY(0x0417),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[GnssCapabilities]
         */
        GNSS_CAPABILITIES(0x0418),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[WifiCapabilities]
         */
        WIFI_CAPABILITIES(0x0419),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[WwanCapabilities]
         */
        WWAN_CAPABILITIES(0x041A),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[SensorInfoTest]
         */
        SENSOR_INFO(0x041B),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[WwanCellInfo]
         */
        WWAN_CELL_INFO(0x041C),

        /**
         * Test: ContextHubEstimatedHostTimeTest
         */
        ESTIMATED_HOST_TIME(0x041D),

        /**
         * Test: ContextHubNanoAppInfoByIdTests[NanoappInfoByAppId]
         */
        NANOAPP_INFO_BY_APP_ID(0x041E),

        /**
         * Test: ContextHubNanoAppInfoByIdTests[NanoappInfoByInstanceId]
         */
        NANOAPP_INFO_BY_INSTANCE_ID(0x041F),

        /**
         * Test: ContextHubNanoAppInfoEventsTest (Performer)
         */
        NANO_APP_INFO_EVENTS_PERFORMER(0x0420),

        /**
         * Test: ContextHubNanoAppInfoEventsTest (Observer)
         */
        NANO_APP_INFO_EVENTS_OBSERVER(0x0421),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicAudioTest]
         */
        BASIC_AUDIO_TEST(0x0422),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[HostAwakeSuspend]
         */
        HOST_AWAKE_SUSPEND(0x0423),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicGnssTest]
         */
        BASIC_GNSS_TEST(0x0424),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicWifiTest]
         */
        BASIC_WIFI_TEST(0x0425),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicSensorFlushAsyncTest]
         */
        BASIC_SENSOR_FLUSH_ASYNC_TEST(0x0426),

        /**
         * Test: ContextHubSimpleGeneralNanoAppTests[BasicBleTest]
         */
        BASIC_BLE_TEST(0x0427);

        private final int mValue;
        TestNames(int value) {
            this.mValue = value;
        }

        /**
         * Get the integer value for this TestNames.
         *
         * This is very efficient, O(1).
         *
         * @return integer value
         */
        public int asInt() {
            return mValue;
        }
    }

    /**
     * Messages types which are sent between Nanoapps and the Java Host testing
     * code.
     */
    public enum MessageType {
        /**
         * Value which should never be used.
         *
         * This type should never be sent by Host or Nanoapp code.
         */
        INVALID_MESSAGE_TYPE(0x0400),

        /**
         * Test has completed in success.
         *
         * Upon receiving this message type, the infrastructure considers
         * the test a success and concludes testing.
         *
         * This type should only be sent by the Nanoapp code.
         */
        SUCCESS(0x0401),

        /**
         * Test has failed.
         *
         * Upon receiving this message type, the infrastructure considers
         * the test to be a failure, and concludes testing.  The presumption
         * is that the failure is due to an issue in the CHRE implementation
         * and/or Context Hub HAL implementation.
         *
         * This type should only be sent by the Nanoapp code.
         */
        FAILURE(0x0402),

        /**
         * Test has failed due to an issue within the testing framework.
         *
         * Upon receiving this message type, the infrastructure considers
         * the test to be a failure, and concludes testing.  The presumption
         * is that the failure is due to an issue in the test itself, or
         * the testing framework code, and that the CHRE and/or Context Hub HAL
         * implementations may not have any issues.
         *
         * This type should only be sent by the Nanoapp code.
         */
        INTERNAL_FAILURE(0x0403),

        /**
         * Test is being skipped because it is not relevant.
         *
         * Upon receiving this message type, the infrastructure considers
         * the test to be "skipped".  That is, this test is not considered
         * relevant, and shouldn't considered a failure, but it never
         * executed.
         *
         * This type should only be send by the Nanoapp code.
         */
        SKIPPED(0x0404),

        /**
         * A generic message indicating that the test should continue.
         *
         * The meaning of this generic message depends on the specific test.
         * In general, it means something along the lines of "The test is
         * successful thus far, please proceed to the next stage."
         *
         * This type can be sent by the Host or Nanoapp code.
         */
        CONTINUE(0x0405),

        /**
         * A message type to be used to test sending messages through the ContextHubClient.
         *
         * This message type should be used only for tests under the ContextHubServiceTestBase
         * framework.
         */
        SERVICE_MESSAGE(0x0406);

        private final int mValue;
        MessageType(int value) {
            this.mValue = value;
        }

        /**
         * Get the integer value for this message type.
         *
         * This is very efficient, O(1).
         *
         * @return integer value
         */
        public int asInt() {
            return mValue;
        }

        /**
         * Get the MessageType for a given integer value.
         *
         * This method will abort the running test if the given value does
         * not correspond to any MessageType.
         *
         * This is inefficient: O(N).
         *
         * @param value  The value we're finding the MessageType for.
         * @param errorMessage  In the case 'value' does not match any
         *     MessageType, our test failure message will include this.
         * @return The matching MessageType.
         */
        public static MessageType fromInt(int value, String errorMessage) {
            for (MessageType type : MessageType.values()) {
                if (value == type.asInt()) {
                    return type;
                }
            }

            Assert.fail("Unknown MessageType (" + value + ") from "
                        + errorMessage);
            // Will never get here.
            return MessageType.INVALID_MESSAGE_TYPE;
        }
    }
}
