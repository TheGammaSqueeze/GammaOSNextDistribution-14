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

import static com.android.server.biometrics.nano.BiometricServiceStateProto.*;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.server.biometrics.nano.BiometricServiceStateProto;
import com.android.server.biometrics.nano.SensorServiceStateProto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class BiometricServiceState {

    /**
     * Defined in biometrics.proto
     */
    @IntDef({
            STATE_AUTH_IDLE,
            STATE_AUTH_CALLED,
            STATE_AUTH_STARTED,
            STATE_AUTH_STARTED_UI_SHOWING,
            STATE_AUTH_PAUSED,
            STATE_AUTH_PAUSED_RESUMING,
            STATE_AUTH_PENDING_CONFIRM,
            STATE_AUTHENTICATED_PENDING_SYSUI,
            STATE_ERROR_PENDING_SYSUI,
            STATE_SHOWING_DEVICE_CREDENTIAL})
    @Retention(RetentionPolicy.SOURCE)
    @interface AuthSessionState {}

    @AuthSessionState public final int mState;
    @NonNull public final SensorStates mSensorStates;

    @NonNull
    public static BiometricServiceState parseFrom(@NonNull BiometricServiceStateProto proto) {
        final List<SensorStates> sensorStates = new ArrayList<>();
        for (SensorServiceStateProto sensorServiceState : proto.sensorServiceStates) {
            sensorStates.add(SensorStates.parseFrom(sensorServiceState));
        }

        @AuthSessionState int state = proto.authSessionState;

        return new BiometricServiceState(SensorStates.merge(sensorStates), state);
    }

    private BiometricServiceState(@NonNull SensorStates sensorStates, @AuthSessionState int state) {
        mSensorStates = sensorStates;
        mState = state;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AuthSessionState: ").append(mState).append(". ");
        sb.append(mSensorStates.toString());
        return sb.toString();
    }
}
