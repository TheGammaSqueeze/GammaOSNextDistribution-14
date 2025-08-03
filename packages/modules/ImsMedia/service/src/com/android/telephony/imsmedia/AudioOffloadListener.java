/**
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

package com.android.telephony.imsmedia;

import android.hardware.radio.ims.media.CallQuality;
import android.hardware.radio.ims.media.IImsMediaSessionListener;
import android.hardware.radio.ims.media.MediaQualityStatus;
import android.hardware.radio.ims.media.RtpConfig;
import android.hardware.radio.ims.media.RtpHeaderExtension;
import android.os.Handler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This implements the handlers for all indication messages from media HAL
 */
final class AudioOffloadListener extends IImsMediaSessionListener.Stub {
    private Handler handler;

    @Override
    public String getInterfaceHash() {
        return IImsMediaSessionListener.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IImsMediaSessionListener.VERSION;
    }

    @Override
    public void onModifySessionResponse(RtpConfig config, int result) {
        Utils.sendMessage(handler,
                AudioSession.EVENT_MODIFY_SESSION_RESPONSE, result,
                Utils.UNUSED, Utils.convertToAudioConfig(config));
    }

    @Override
    public void onFirstMediaPacketReceived(RtpConfig config) {
        Utils.sendMessage(handler, AudioSession.EVENT_FIRST_MEDIA_PACKET_IND,
                Utils.convertToAudioConfig(config));
    }

    @Override
    public void onHeaderExtensionReceived(List<RtpHeaderExtension> extensions) {
        Utils.sendMessage(handler, AudioSession.EVENT_RTP_HEADER_EXTENSION_IND,
                extensions.stream().map(Utils::convertRtpHeaderExtension)
                .collect(Collectors.toList()));
    }

    @Override
    public void notifyMediaQualityStatus(MediaQualityStatus status) {
        Utils.sendMessage(handler, AudioSession.EVENT_MEDIA_QUALITY_STATUS_IND,
                Utils.convertMediaQualityStatus(status));
    }

    @Override
    public void triggerAnbrQuery(RtpConfig config) {
        Utils.sendMessage(handler, AudioSession.EVENT_TRIGGER_ANBR_QUERY_IND,
                Utils.convertToAudioConfig(config));
    }

    @Override
    public void onDtmfReceived(char dtmfDigit, int durationMs) {
        Utils.sendMessage(handler, AudioSession.EVENT_DTMF_RECEIVED_IND, dtmfDigit, durationMs);
    }

    @Override
    public void onCallQualityChanged(CallQuality callQuality) {
        Utils.sendMessage(handler, AudioSession.EVENT_CALL_QUALITY_CHANGE_IND,
                Utils.convertCallQuality(callQuality));
    }

    AudioOffloadListener(Handler handler) {
        this.handler = handler;
    }
}
