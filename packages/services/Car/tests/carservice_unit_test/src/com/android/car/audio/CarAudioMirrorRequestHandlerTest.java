/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.audio;

import static android.car.media.CarAudioManager.AUDIO_MIRROR_CAN_ENABLE;
import static android.car.media.CarAudioManager.AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.media.IAudioZonesMirrorStatusCallback;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioDeviceAttributes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioMirrorRequestHandlerTest extends AbstractExpectableTestCase {

    private static final int TEST_ZONE_1 = PRIMARY_AUDIO_ZONE + 1;
    private static final int TEST_ZONE_2 = PRIMARY_AUDIO_ZONE + 2;
    private static final int TEST_ZONE_3 = PRIMARY_AUDIO_ZONE + 3;
    public static final int[] TEST_ZONE_IDS = new int[]{TEST_ZONE_1, TEST_ZONE_2};
    private CarAudioMirrorRequestHandler mCarAudioMirrorRequestHandler;
    private TestAudioZonesMirrorStatusCallbackCallback mTestCallback;
    @Mock
    private CarAudioDeviceInfo mCarAudioDeviceInfoOne;
    @Mock
    private AudioDeviceAttributes mAudioDeviceOne;
    @Mock
    private CarAudioDeviceInfo mCarAudioDeviceInfoTwo;
    @Mock
    private AudioDeviceAttributes mAudioDeviceTwo;
    private List<CarAudioDeviceInfo> mTestCarAudioDeviceInfos;

    @Before
    public void setUp() {
        mCarAudioMirrorRequestHandler = new CarAudioMirrorRequestHandler();
        mTestCallback = new TestAudioZonesMirrorStatusCallbackCallback();
        mTestCarAudioDeviceInfos = List.of(mCarAudioDeviceInfoOne, mCarAudioDeviceInfoTwo);
        mCarAudioMirrorRequestHandler.setMirrorDeviceInfos(mTestCarAudioDeviceInfos);
        when(mCarAudioDeviceInfoOne.getAudioDevice()).thenReturn(mAudioDeviceOne);
        when(mCarAudioDeviceInfoTwo.getAudioDevice()).thenReturn(mAudioDeviceTwo);
    }

    @Test
    public void getUniqueRequestIdAndAssignMirrorDevice() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Unique request id").that(requestId).isEqualTo(0);
    }

    @Test
    public void getUniqueRequestIdAndAssignMirrorDevice_multipleTimes() {
        long requestIdOne = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        long requestIdTwo = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Unique request id one").that(requestIdOne).isEqualTo(0);
        expectWithMessage("Unique request id two").that(requestIdTwo).isEqualTo(1);
    }

    @Test
    public void getUniqueRequestIdAndAssignMirrorDevice_forOutOfAvailableDevices() {
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        long requestIdThree = mCarAudioMirrorRequestHandler
                .getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Invalid request id for request with no more output devices")
                .that(requestIdThree).isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void getUniqueRequestIdAndAssignMirrorDevice_forEmptyMirrorDevices() {
        CarAudioMirrorRequestHandler audioMirrorRequestHandler = new CarAudioMirrorRequestHandler();

        long requestId = audioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Invalid request id for request with no more output devices")
                .that(requestId).isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void registerAudioZonesMirrorStatusCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                    /* callback= */ null);
        });

        expectWithMessage("Null audio zones mirror status callback register exception")
                .that(thrown).hasMessageThat().contains("Audio zones mirror status");
    }

    @Test
    public void unregisterAudioZonesMirrorStatusCallback_withNullCallback_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mCarAudioMirrorRequestHandler.unregisterAudioZonesMirrorStatusCallback(
                    /* callback= */ null);
        });

        expectWithMessage("Null audio zones mirror status callback unregister exception")
                .that(thrown).hasMessageThat().contains("Audio zones mirror status");
    }

    @Test
    public void registerAudioZonesMirrorStatusCallback() {
        boolean registered = mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                mTestCallback);

        expectWithMessage("Register status")
                .that(registered).isTrue();
    }

    @Test
    public void registerAudioZonesMirrorStatusCallback_withNoMirroringDeviceAddress() {
        CarAudioMirrorRequestHandler carAudioMirrorRequestHandler =
                new CarAudioMirrorRequestHandler();

        boolean registered = carAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                mTestCallback);

        expectWithMessage("Register status with mirroring disabled")
                .that(registered).isFalse();
    }

    @Test
    public void registerAudioZonesMirrorStatusCallback_reRegisterFails() {
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                mTestCallback);

        boolean registered = mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                mTestCallback);

        expectWithMessage("Re-register status")
                .that(registered).isTrue();
    }

    @Test
    public void unregisterAudioZonesMirrorStatusCallback() {
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                mTestCallback);

        boolean unregister = mCarAudioMirrorRequestHandler
                .unregisterAudioZonesMirrorStatusCallback(mTestCallback);

        expectWithMessage("Unregister status")
                .that(unregister).isTrue();
    }

    @Test
    public void unregisterAudioZonesMirrorStatusCallback_withoutRegister() {
        boolean unregister = mCarAudioMirrorRequestHandler
                .unregisterAudioZonesMirrorStatusCallback(mTestCallback);

        expectWithMessage("Unregister status for non register callback")
                .that(unregister).isFalse();
    }

    @Test
    public void unregisterAudioZonesMirrorStatusCallback_afterUnregister() {
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(
                mTestCallback);
        mCarAudioMirrorRequestHandler.unregisterAudioZonesMirrorStatusCallback(
                mTestCallback);

        boolean unregister = mCarAudioMirrorRequestHandler
                .unregisterAudioZonesMirrorStatusCallback(mTestCallback);

        expectWithMessage("Unregister status for already unregistered callback")
                .that(unregister).isFalse();
    }

    @Test
    public void setMirrorDeviceInfos_withNullMirrorDeviceAddress() {
        CarAudioMirrorRequestHandler carAudioMirrorRequestHandler =
                new CarAudioMirrorRequestHandler();

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            carAudioMirrorRequestHandler.setMirrorDeviceInfos(/* mirroringDevices= */ null);
        });

        expectWithMessage("Null mirror device infos exception")
                .that(thrown).hasMessageThat().contains("Mirror devices");
    }

    @Test
    public void setMirrorDeviceInfos() {
        CarAudioMirrorRequestHandler carAudioMirrorRequestHandler =
                new CarAudioMirrorRequestHandler();

        carAudioMirrorRequestHandler.setMirrorDeviceInfos(mTestCarAudioDeviceInfos);

        expectWithMessage("Audio mirror enabled status")
                .that(carAudioMirrorRequestHandler.isMirrorAudioEnabled()).isTrue();
    }

    @Test
    public void getMirroringDeviceInfos() {
        CarAudioMirrorRequestHandler carAudioMirrorRequestHandler =
                new CarAudioMirrorRequestHandler();
        carAudioMirrorRequestHandler.setMirrorDeviceInfos(mTestCarAudioDeviceInfos);

        expectWithMessage("Audio mirror device infos")
                .that(carAudioMirrorRequestHandler.getMirroringDeviceInfos())
                .containsExactlyElementsIn(mTestCarAudioDeviceInfos);
    }

    @Test
    public void getMirroringDeviceInfos_withEmptyDeviceInfos() {
        CarAudioMirrorRequestHandler carAudioMirrorRequestHandler =
                new CarAudioMirrorRequestHandler();
        carAudioMirrorRequestHandler.setMirrorDeviceInfos(List.of());

        expectWithMessage("Empty audio mirror device infos")
                .that(carAudioMirrorRequestHandler.getMirroringDeviceInfos()).isEmpty();
    }

    @Test
    public void getAudioDeviceInfo() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Audio mirror device info for request %s", requestId)
                .that(mCarAudioMirrorRequestHandler.getAudioDevice(requestId))
                .isEqualTo(mAudioDeviceOne);
    }

    @Test
    public void getAudioDeviceInfo_forMultipleRequests() {
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        long requestIdTwo = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Audio mirror device info for second request %s", requestIdTwo)
                .that(mCarAudioMirrorRequestHandler.getAudioDevice(requestIdTwo))
                .isEqualTo(mAudioDeviceTwo);
    }

    @Test
    public void getAudioDeviceInfo_forInvalidRequestIdFails() {
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioMirrorRequestHandler.getAudioDevice(INVALID_REQUEST_ID));

        expectWithMessage("Get mirror device with invalid request id exception")
                .that(thrown).hasMessageThat().contains("Request id for device");
    }

    @Test
    public void isMirrorAudioEnabled_withoutMirrorDeviceAvailable() {
        CarAudioMirrorRequestHandler carAudioMirrorRequestHandler =
                new CarAudioMirrorRequestHandler();

        expectWithMessage("Audio mirror enabled status, with no mirror device address")
                .that(carAudioMirrorRequestHandler.isMirrorAudioEnabled()).isFalse();
    }

    @Test
    public void enableMirrorForZones() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, TEST_ZONE_IDS);

        expectWithMessage("Zone configs for request id %s", requestId)
                .that(mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(requestId))
                .asList().containsExactly(TEST_ZONE_1, TEST_ZONE_2);
        for (int index = 0; index < TEST_ZONE_IDS.length; index++) {
            int zoneId = TEST_ZONE_IDS[index];
            expectWithMessage("Enabled mirror status for audio zone %s", zoneId)
                    .that(mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(zoneId)).isTrue();
        }
    }

    @Test
    public void enableMirrorForZones_withNullZoneIds_fails() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, /* audioZones= */ null);
        });

        expectWithMessage("Null audio zones enabled exception")
                .that(thrown).hasMessageThat().contains("Mirror audio zones");
    }

    @Test
    public void enableMirrorForZones_withInvalidRequestId() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            mCarAudioMirrorRequestHandler.enableMirrorForZones(INVALID_REQUEST_ID, TEST_ZONE_IDS);
        });

        expectWithMessage("Enable mirror for zones with invalid request id exception")
                .that(thrown).hasMessageThat().contains("INVALID_REQUEST_ID");
    }

    @Test
    public void enableMirrorForZones_afterRegisteringCallback() throws Exception {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(mTestCallback);

        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, TEST_ZONE_IDS);

        mTestCallback.waitForCallback();
        expectWithMessage("Audio mirror status after enabled").that(mTestCallback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED);
        expectWithMessage("Audio mirror enables zones").that(mTestCallback.mZoneIds)
                .asList().containsExactly(TEST_ZONE_1, TEST_ZONE_2);
    }

    @Test
    public void isMirrorEnabledForZone_withNoAudioMirrorEnabled() {
        expectWithMessage("Enabled mirror status for audio zone for not yet enabled zone")
                .that(mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(TEST_ZONE_1)).isFalse();
    }

    @Test
    public void getMirrorAudioZonesForAudioZone_withNoAudioMirrorEnabled() {
        expectWithMessage("Zone configs for not yet enabled zone").that(
                mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(TEST_ZONE_1))
                .isNull();
    }

    @Test
    public void rejectMirrorForZones() throws Exception {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(mTestCallback);

        mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, TEST_ZONE_IDS);

        mTestCallback.waitForCallback();
        expectWithMessage("Audio mirror rejected status").that(mTestCallback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
        expectWithMessage("Audio mirror rejected zones").that(mTestCallback.mZoneIds)
                .asList().containsExactly(TEST_ZONE_1, TEST_ZONE_2);
        expectWithMessage("Audio device info for rejected request")
                .that(mCarAudioMirrorRequestHandler.getAudioDevice(requestId)).isNull();
    }

    @Test
    public void rejectMirrorForZones_withNullZones_fails() throws Exception {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId,
                        /* audioZones = */ null
                ));

        expectWithMessage("Null zones to reject exception").that(thrown).hasMessageThat()
                .contains("Rejected audio zones");
    }

    @Test
    public void rejectMirrorForZones_withEmptyZones_fails() throws Exception {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, new int[0]));

        expectWithMessage("Empty zones to reject exception").that(thrown).hasMessageThat()
                .contains("Rejected audio zones");
    }

    @Test
    public void updateMirrorConfigurationForZones() throws Exception {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(mTestCallback);
        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, TEST_ZONE_IDS);
        mTestCallback.waitForCallback();
        mTestCallback.reset();

        mCarAudioMirrorRequestHandler.updateRemoveMirrorConfigurationForZones(
                requestId, /* newConfig= */ new int[0]);

        mTestCallback.waitForCallback();
        expectWithMessage("Audio mirror status after removed").that(mTestCallback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
        expectWithMessage("Audio mirror disabled zones").that(mTestCallback.mZoneIds)
                .asList().containsExactly(TEST_ZONE_1, TEST_ZONE_2);
    }

    @Test
    public void updateMirrorConfigurationForZones_forSingleZone_inThreeZoneConfig()
            throws Exception {
        int[] threeZoneConfig = new int[] {TEST_ZONE_1, TEST_ZONE_2, TEST_ZONE_3};
        mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(mTestCallback);
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, threeZoneConfig);
        mTestCallback.waitForCallback();
        mTestCallback.reset();

        mCarAudioMirrorRequestHandler.updateRemoveMirrorConfigurationForZones(requestId,
                TEST_ZONE_IDS
        );

        mTestCallback.waitForCallback();
        expectWithMessage("Audio mirror status after removed one zone in three zone config")
                .that(mTestCallback.mStatus).isEqualTo(
                        CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
        expectWithMessage("Audio mirror disabled a single zone in three zone config")
                .that(mTestCallback.mZoneIds).asList().containsExactly(TEST_ZONE_3);
    }

    @Test
    public void calculateAudioConfigurationAfterRemovingZonesFromRequestId() {
        int[] threeZoneConfig = new int[] {TEST_ZONE_1, TEST_ZONE_2, TEST_ZONE_3};
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, threeZoneConfig);

        int[] newConfig = mCarAudioMirrorRequestHandler
                .calculateAudioConfigurationAfterRemovingZonesFromRequestId(requestId, TEST_ZONE_IDS
                );

        expectWithMessage("New audio zone configuration after removing two zones")
                .that(newConfig).asList().containsExactly(TEST_ZONE_3);
    }

    @Test
    public void calculateAudioConfigurationAfterRemovingZonesFromRequestId_withNotYetSetConfig() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        int[] newConfig = mCarAudioMirrorRequestHandler
                .calculateAudioConfigurationAfterRemovingZonesFromRequestId(requestId, TEST_ZONE_IDS
                );

        expectWithMessage("New audio zone configuration for not yet set configuration")
                .that(newConfig).isNull();
    }

    @Test
    public void verifyValidRequestId_withNoLongerValidRequest() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, TEST_ZONE_IDS);
        mCarAudioMirrorRequestHandler.updateRemoveMirrorConfigurationForZones(
                requestId, /* newConfig= */ new int[0]);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            mCarAudioMirrorRequestHandler.verifyValidRequestId(requestId);
        });

        expectWithMessage("No longer valid request id verification exception")
                .that(thrown).hasMessageThat().contains("is not valid");
    }

    @Test
    public void verifyValidRequestId_withInvalidRequestId() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            mCarAudioMirrorRequestHandler.verifyValidRequestId(INVALID_REQUEST_ID);
        });

        expectWithMessage("Invalid request id verification exception")
                .that(thrown).hasMessageThat().contains("is not valid");
    }

    @Test
    public void getRequestIdForAudioZone() {
        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, TEST_ZONE_IDS);

        expectWithMessage("Request id for audio zone")
                .that(mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(TEST_ZONE_1))
                .isEqualTo(requestId);
    }

    @Test
    public void getRequestIdForAudioZone_forNotYetSetZones() {
        expectWithMessage("Request id for not yet set audio zone")
                .that(mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(TEST_ZONE_1))
                .isEqualTo(INVALID_REQUEST_ID);
    }

    @Test
    public void canEnableAudioMirror_withDefaultHandler() {
        CarAudioMirrorRequestHandler audioMirrorRequestHandler = new CarAudioMirrorRequestHandler();

        expectWithMessage("Default status for enabling mirroring")
                .that(audioMirrorRequestHandler.canEnableAudioMirror())
                .isEqualTo(AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES);
    }

    @Test
    public void canEnableAudioMirror() {
        expectWithMessage("Status for enabling mirroring with audio mirroring enabled")
                .that(mCarAudioMirrorRequestHandler.canEnableAudioMirror())
                .isEqualTo(AUDIO_MIRROR_CAN_ENABLE);
    }

    @Test
    public void canEnableAudioMirror_withPendingRequest() {
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Status for enabling mirroring with outstanding mirror request")
                .that(mCarAudioMirrorRequestHandler.canEnableAudioMirror())
                .isEqualTo(AUDIO_MIRROR_CAN_ENABLE);
    }

    @Test
    public void canEnableAudioMirror_withPendingRequestsAndOutOfDevices() {
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();
        mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        expectWithMessage("Status for enabling mirroring with outstanding mirror request"
                + " and out of devices").that(mCarAudioMirrorRequestHandler.canEnableAudioMirror())
                .isEqualTo(AUDIO_MIRROR_OUT_OF_OUTPUT_DEVICES);
    }

    private static final class TestAudioZonesMirrorStatusCallbackCallback extends
            IAudioZonesMirrorStatusCallback.Stub {

        private static final long TEST_CALLBACK_TIMEOUT_MS = 100;

        private int[] mZoneIds;
        private int mStatus;
        private CountDownLatch mStatusLatch = new CountDownLatch(1);

        @Override
        public void onAudioZonesMirrorStatusChanged(int[] zoneIds, int status) {
            mZoneIds = zoneIds;
            mStatus = status;
            mStatusLatch.countDown();
        }

        private void waitForCallback() throws Exception {
            mStatusLatch.await(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public void reset() {
            mZoneIds = null;
            mStatus = 0;
            mStatusLatch = new CountDownLatch(1);
        }
    }
}
