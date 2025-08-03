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

package com.android.server.telecom.testapps;

import android.telecom.BluetoothCallQualityReport;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallDiagnosticService;
import android.telecom.CallDiagnostics;
import android.telecom.Log;
import android.telephony.CallQuality;
import android.telephony.ims.ImsReasonInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TestCallDiagnosticService extends CallDiagnosticService {

    public static final class TestCallDiagnostics extends CallDiagnostics {
        public Call.Details details;

        TestCallDiagnostics(Call.Details details) {
            this.details = details;
        }

        @Override
        public void onCallDetailsChanged(@NonNull Call.Details details) {
            Log.i(this, "onCallDetailsChanged; %s", details);
        }

        @Override
        public void onReceiveDeviceToDeviceMessage(int message, int value) {
            Log.i(this, "onReceiveDeviceToDeviceMessage; %d/%d", message, value);
        }

        @Nullable
        @Override
        public CharSequence onCallDisconnected(int disconnectCause, int preciseDisconnectCause) {
            Log.i(this, "onCallDisconnected");
            return "GSM/CDMA call dropped because " + disconnectCause;
        }

        @Nullable
        @Override
        public CharSequence onCallDisconnected(@NonNull ImsReasonInfo disconnectReason) {
            Log.i(this, "onCallDisconnected");
            return "ImsCall dropped because something happened " + disconnectReason.mExtraMessage;
        }

        @Override
        public void onCallQualityReceived(@NonNull CallQuality callQuality) {
            Log.i(this, "onCallQualityReceived %s", callQuality);
        }
    }

    @NonNull
    @Override
    public CallDiagnostics onInitializeCallDiagnostics(@NonNull Call.Details call) {
        Log.i(this, "onInitiatlizeDiagnosticCall %s", call);
        return new TestCallDiagnostics(call);
    }

    @Override
    public void onRemoveCallDiagnostics(@NonNull CallDiagnostics call) {
        Log.i(this, "onRemoveDiagnosticCall %s", call);
    }

    @Override
    public void onCallAudioStateChanged(@NonNull CallAudioState audioState) {
        Log.i(this, "onCallAudioStateChanged %s", audioState);
    }

    @Override
    public void onBluetoothCallQualityReportReceived(
            @NonNull BluetoothCallQualityReport qualityReport) {
        Log.i(this, "onBluetoothCallQualityReportReceived %s", qualityReport);
    }
}
