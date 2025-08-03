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

package com.android.cts.deviceowner;

import static android.hardware.SensorPrivacyManager.Sources.OTHER;

import android.hardware.SensorPrivacyManager;
import android.os.UserManager;

import com.android.compatibility.common.util.ShellIdentityUtils;

/**
 * Test interaction between user restriction controlling sensor toggles (
 * {@link UserManager#DISALLOW_CAMERA_TOGGLE} and {@link UserManager#DISALLOW_MICROPHONE_TOGGLE})
 * and {@link android.hardware.SensorPrivacyManager}.
 */
public class SensorToggleRestrictionTest extends BaseDeviceOwnerTest {

    private static final long RESTRICTION_WAITING_TIMEOUT_NANO = 30_000_000_000L;

    private SensorPrivacyManager mSensorPrivacyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSensorPrivacyManager = mContext.getSystemService(SensorPrivacyManager.class);

    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_CAMERA_TOGGLE);
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_MICROPHONE_TOGGLE);
        super.tearDown();
    }

    public void testCameraToggle_RestrictionSet_CannotChangeSensorPrivacy() {
        if (!mSensorPrivacyManager.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA)) {
            return;
        }
        assertFalse("Camera sensor privacy should be off by default",
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSensorPrivacyManager,
                        m -> m.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA)));

        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_CAMERA_TOGGLE);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSensorPrivacyManager,
                m -> m.setSensorPrivacy(OTHER, SensorPrivacyManager.Sensors.CAMERA, true));

        assertFalse("Camera sensor privacy should not be enabled given admin restriction",
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSensorPrivacyManager,
                        m -> m.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA)));
    }

    public void testMicrophoneToggle_RestrictionSet_CannotChangeSensorPrivacy() {
        if (!mSensorPrivacyManager.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)) {
            return;
        }
        assertFalse("Microphone sensor privacy should be off by default",
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSensorPrivacyManager,
                        m -> m.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE)));

        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_MICROPHONE_TOGGLE);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSensorPrivacyManager,
                m -> m.setSensorPrivacy(OTHER, SensorPrivacyManager.Sensors.MICROPHONE, true));

        assertFalse("Microphone sensor privacy should not be enabled given admin restriction",
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSensorPrivacyManager,
                        m -> m.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE)));
    }

    public void testCameraToggle_RestrictionSet_ResetSensorPrivacy() {
        if (!mSensorPrivacyManager.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA)) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSensorPrivacyManager,
                m -> m.setSensorPrivacy(OTHER, SensorPrivacyManager.Sensors.CAMERA, true));

        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_CAMERA_TOGGLE);

        long deadline = System.nanoTime() + RESTRICTION_WAITING_TIMEOUT_NANO;
        while (System.nanoTime() < deadline) {
            if (!ShellIdentityUtils.invokeMethodWithShellPermissions(mSensorPrivacyManager,
                    m -> m.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA))) {
                return;
            }
        }
        fail("Camera sensor privacy did not get reset in time");
    }

    public void testMicrophoneToggle_RestrictionSet_ResetSensorPrivacy() {
        if (!mSensorPrivacyManager.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSensorPrivacyManager,
                m -> m.setSensorPrivacy(OTHER, SensorPrivacyManager.Sensors.MICROPHONE, true));

        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_MICROPHONE_TOGGLE);

        long deadline = System.nanoTime() + RESTRICTION_WAITING_TIMEOUT_NANO;
        while (System.nanoTime() < deadline) {
            if (!ShellIdentityUtils.invokeMethodWithShellPermissions(mSensorPrivacyManager,
                    m -> m.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE))) {
                return;
            }
        }
        fail("Microphone sensor privacy did not get reset in time");
    }
}
