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

package android.server.biometrics.cts;

import android.cts.statsdatom.lib.DeviceUtils;

import com.android.server.biometrics.BiometricServiceStateProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

/** Base class for hostside biometrics tests that includes common utility methods. */
abstract class BiometricDeviceTestCase extends DeviceTestCase implements IBuildReceiver {

    private static final String FEATURE_FINGERPRINT = "android.hardware.fingerprint";
    private static final String FEATURE_FACE = "android.hardware.biometrics.face";

    protected IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    /** If any AIDL biometric feature is present. */
    protected boolean hasAidlBiometrics() throws Exception {
        return hasFeatureFace(true /* aidlOnly */) || hasFeatureFingerprint(true /* aidlOnly */);
    }

    /** {@see PackageManager.FEATURE_FACE}. */
    protected boolean hasFeatureFace(boolean aidlOnly) throws Exception {
        final boolean hasFeature = DeviceUtils.hasFeature(getDevice(), FEATURE_FACE);
        return aidlOnly ? hasFeature && hasAidlFaceSensorId() : hasFeature;
    }

    /** {@see PackageManager.FEATURE_FINGERPRINT}. */
    protected boolean hasFeatureFingerprint(boolean aidlOnly) throws Exception {
        final boolean hasFeature = DeviceUtils.hasFeature(getDevice(), FEATURE_FINGERPRINT);
        return aidlOnly ? hasFeature && hasAidlFingerprintSensorId() : hasFeature;
    }

    /** Get info about all sensors on the device. */
    public SensorInfo getSensorInfo() throws Exception {
        return new SensorInfo(getDump(BiometricServiceStateProto.parser(),
                "dumpsys biometric --proto"));
    }

    private <T extends MessageLite> T getDump(Parser<T> parser, String command) throws Exception {
        final CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        getDevice().executeShellCommand(command, receiver);
        return parser.parseFrom(receiver.getOutput());
    }

    /** If there is an AIDL fingerprint HAL on the test device. */
    protected boolean hasAidlFingerprintSensorId() throws Exception {
        return getAidlSensorId("dumpsys fingerprint", ", provider: FingerprintProvider") > -1;
    }

    /** If there is an AIDL face HAL on the test device. */
    protected boolean hasAidlFaceSensorId() throws Exception {
        return getAidlSensorId("dumpsys face", ", provider: FaceProvider") > -1;
    }

    private int getAidlSensorId(String adbCommand, String providerRegex) throws Exception {
        final String dumpsys = getDevice().executeShellCommand(adbCommand);
        return dumpsys.indexOf(providerRegex);
    }
}
