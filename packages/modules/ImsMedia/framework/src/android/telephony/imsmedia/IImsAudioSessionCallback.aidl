/**
 * Copyright (c) 2022 The Android Open Source Project
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

package android.telephony.imsmedia;

import android.telephony.CallQuality;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.IImsAudioSession;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.RtpReceptionStats;

/**
 * See ImsAudioSessionCallback for more information.
 *
 * {@hide}
 */
oneway interface IImsAudioSessionCallback {
    void onOpenSessionSuccess(IImsAudioSession session);
    void onOpenSessionFailure(int error);
    void onSessionClosed();
    void onModifySessionResponse(in AudioConfig config, int result);
    void onAddConfigResponse(in AudioConfig config, int result);
    void onConfirmConfigResponse(in AudioConfig config, int result);
    void onFirstMediaPacketReceived(in AudioConfig config);
    void onHeaderExtensionReceived(in List<RtpHeaderExtension> extensions);
    void notifyMediaQualityStatus(in MediaQualityStatus status);
    void onCallQualityChanged(in CallQuality callQuality);
    void triggerAnbrQuery(in AudioConfig config);
    void onDtmfReceived(in char dtmfDigit, int durationMs);
    void notifyRtpReceptionStats(in RtpReceptionStats stats);
}
