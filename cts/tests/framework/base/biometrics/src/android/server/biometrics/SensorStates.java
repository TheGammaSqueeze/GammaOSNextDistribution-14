/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.biometrics;

import androidx.annotation.NonNull;

import com.android.server.biometrics.nano.BiometricSchedulerProto;
import com.android.server.biometrics.nano.BiometricsProto;
import com.android.server.biometrics.nano.SensorServiceStateProto;
import com.android.server.biometrics.nano.SensorStateProto;
import com.android.server.biometrics.nano.UserStateProto;

import com.google.common.primitives.Ints;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The overall state for a list of sensors. This could be either:
 *
 * 1) A list of sensors from a single instance of a <Biometric>Service such as
 * {@link com.android.server.biometrics.sensors.fingerprint.FingerprintService} or
 * {@link com.android.server.biometrics.sensors.face.FaceService}, or
 *
 * 2) A list of sensors from multiple instances of <Biometric>Services.
 *
 * Note that a single service may provide multiple sensors.
 */
public class SensorStates {

    @NonNull public final Map<Integer, SensorState> sensorStates;

    public static class SchedulerState {
        private final int mCurrentOperation;
        private final int mTotalOperations;
        @NonNull private final List<Integer> mRecentOperations;

        public static SchedulerState parseFrom(@NonNull BiometricSchedulerProto proto) {
            return new SchedulerState(proto.currentOperation, proto.totalOperations,
                    Ints.asList(proto.recentOperations));
        }

        public SchedulerState(int currentOperation, int totalOperations,
                @NonNull List<Integer> recentOperations) {
            mCurrentOperation = currentOperation;
            mTotalOperations = totalOperations;
            mRecentOperations = recentOperations;
        }

        @NonNull
        public List<Integer> getRecentOperations() {
            return mRecentOperations;
        }
    }

    public static class SensorState {
        private final SchedulerState mSchedulerState;
        private final int mModality;
        private final int[] mModalityFlags;
        private final int mCurrentStrength;
        @NonNull private final Map<Integer, UserState> mUserStates;
        private final boolean mResetLockoutRequiresHardwareAuthToken;
        private final boolean mResetLockoutRequiresChallenge;

        public SensorState(@NonNull SchedulerState schedulerState,
                int modality, int[] modalityFlags,
                int currentStrength, @NonNull Map<Integer, UserState> userStates,
                boolean resetLockoutRequiresHardwareAuthToken,
                boolean resetLockoutRequiresChallenge) {
            mSchedulerState = schedulerState;
            mModality = modality;
            mModalityFlags = modalityFlags;
            mCurrentStrength = currentStrength;
            mUserStates = userStates;
            mResetLockoutRequiresHardwareAuthToken = resetLockoutRequiresHardwareAuthToken;
            mResetLockoutRequiresChallenge = resetLockoutRequiresChallenge;
        }

        public SchedulerState getSchedulerState() {
            return mSchedulerState;
        }

        public boolean isBusy() {
            return mSchedulerState.mCurrentOperation != BiometricsProto.CM_NONE;
        }

        public int getModality() {
            return mModality;
        }

        public boolean hasModalityFlag(int flag) {
            for (int f : mModalityFlags) {
                if (f == flag) {
                    return true;
                }
            }

            return false;
        }

        public int getCurrentStrength() {
            return mCurrentStrength;
        }

        @NonNull public Map<Integer, UserState> getUserStates() {
            return mUserStates;
        }

        public boolean isResetLockoutRequiresHardwareAuthToken() {
            return mResetLockoutRequiresHardwareAuthToken;
        }

        public boolean isResetLockoutRequiresChallenge() {
            return mResetLockoutRequiresChallenge;
        }
    }

    public static class UserState {
        public final int numEnrolled;

        public UserState(int numEnrolled) {
            this.numEnrolled = numEnrolled;
        }
    }

    @NonNull
    public static SensorStates parseFrom(@NonNull SensorServiceStateProto proto) {
        final Map<Integer, SensorState> sensorStates = new HashMap<>();

        for (SensorStateProto sensorStateProto : proto.sensorStates) {
            final Map<Integer, UserState> userStates = new HashMap<>();
            for (UserStateProto userStateProto : sensorStateProto.userStates) {
                userStates.put(userStateProto.userId, new UserState(userStateProto.numEnrolled));
            }

            final SchedulerState schedulerState =
                    SchedulerState.parseFrom(sensorStateProto.scheduler);
            final SensorState sensorState = new SensorState(schedulerState,
                    sensorStateProto.modality, sensorStateProto.modalityFlags,
                    sensorStateProto.currentStrength, userStates,
                    sensorStateProto.resetLockoutRequiresHardwareAuthToken,
                    sensorStateProto.resetLockoutRequiresChallenge);
            sensorStates.put(sensorStateProto.sensorId, sensorState);
        }

        return new SensorStates(sensorStates);
    }

    /**
     * Combines multiple {@link SensorStates} into a single instance.
     */
    @NonNull
    public static SensorStates merge(@NonNull List<SensorStates> sensorServiceStates) {
        final Map<Integer, SensorState> sensorStates = new HashMap<>();

        for (SensorStates sensorServiceState : sensorServiceStates) {
            for (Integer sensorId : sensorServiceState.sensorStates.keySet()) {
                if (sensorStates.containsKey(sensorId)) {
                    throw new IllegalStateException("Duplicate sensorId found: " + sensorId);
                }
                sensorStates.put(sensorId, sensorServiceState.sensorStates.get(sensorId));
            }
        }

        return new SensorStates(sensorStates);
    }

    public boolean areAllSensorsIdle() {
        for (SensorState state : sensorStates.values()) {
            if (state.isBusy()) {
                return false;
            }
        }

        return true;
    }

    public boolean containsModality(int modality) {
        for (SensorState state : sensorStates.values()) {
            if (state.getModality() == modality) {
                return true;
            }
        }

        return false;
    }

    public boolean containsModalityFlag(int flag) {
        for (SensorState state : sensorStates.values()) {
            if (state.hasModalityFlag(flag)) {
                return true;
            }
        }

        return false;
    }

    private SensorStates(@NonNull Map<Integer, SensorState> sensorStates) {
        this.sensorStates = sensorStates;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        for (Integer sensorId : sensorStates.keySet()) {
            sb.append("{SensorId: ").append(sensorId);
            sb.append(", Operation: ").append(sensorStates.get(sensorId)
                    .getSchedulerState().mCurrentOperation);

            final Map<Integer, UserState> userStates = sensorStates.get(sensorId).getUserStates();
            for (Integer userId : userStates.keySet()) {
                sb.append(", UserId: ").append(userId);
                sb.append(", NumEnrolled: ").append(userStates.get(userId).numEnrolled);
            }
            sb.append("} ");
        }
        return sb.toString();
    }
}
