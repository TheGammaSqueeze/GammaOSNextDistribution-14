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

import com.android.server.biometrics.BiometricServiceStateProto;
import com.android.server.biometrics.SensorServiceStateProto;
import com.android.server.biometrics.SensorStateProto;

import java.util.HashMap;
import java.util.Map;

/**
 * Helpers for the current sensor state.
 *
 * This is partially copied from the on-device utilities:
 * cts/tests/framework/base/biometrics/src/android/server/biometrics/SensorStates.java
 *
 * TODO(b/253318030): replace with the test vhal / common utility for on-device & host side tests
 */
public class SensorInfo {

    private final Map<Integer, Info> mSensors = new HashMap<>();

    /** Information about a single sensor. */
    public static class Info {
        private final int mStrength;
        private final SensorStateProto.Modality mModality;

        /** Parse from the given descriptor. */
        public Info(SensorStateProto descriptor) {
            mStrength = descriptor.getCurrentStrength();
            mModality = descriptor.getModality();
        }

        /** If the sensor is configured as a convenience. */
        public boolean isConvenience() {
            return mStrength == 0x0FFF; // BiometricManager.Authenticators.BIOMETRIC_CONVENIENCE;
        }
    }

    /** Parse from the given descriptor. */
    public SensorInfo(BiometricServiceStateProto descriptor) {
        for (SensorServiceStateProto serviceStateProto : descriptor.getSensorServiceStatesList()) {
            for (SensorStateProto sensorStateProto : serviceStateProto.getSensorStatesList()) {
                mSensors.put(sensorStateProto.getSensorId(), new Info(sensorStateProto));
            }
        }
    }

    /** If there is a face sensor with weak or stronger strength. */
    public boolean hasWeakOrGreaterFaceSensor() {
        for (Info info : mSensors.values()) {
            if (info.mModality == SensorStateProto.Modality.FACE && !info.isConvenience()) {
                return true;
            }
        }
        return false;
    }

    /** If there is a fingerprint sensor with weak or stronger strength. */
    public boolean hasWeakOrGreaterFingerprintSensor() {
        for (Info info : mSensors.values()) {
            if (info.mModality == SensorStateProto.Modality.FINGERPRINT && !info.isConvenience()) {
                return true;
            }
        }
        return false;
    }
}
