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

import android.hardware.radio.ims.media.CodecMode;
import android.hardware.radio.ims.media.CodecParams;
import android.hardware.radio.ims.media.CodecSpecificParams;
import android.hardware.radio.ims.media.DtmfParams;
import android.hardware.radio.ims.media.RtpAddress;
import android.hardware.radio.ims.media.RtpSessionParams;
import android.net.InetAddresses;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AmrParams;
import android.telephony.imsmedia.AnbrMode;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.EvsParams;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.RtcpConfig;
import android.telephony.imsmedia.RtpConfig;

import com.android.telephony.imsmedia.ImsMediaController.OpenSessionCallback;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * Class consists of utility methods and sub classes
 *
 * @hide
 */
public final class Utils {

    static final int UNUSED = -1;

    /** Class to encapsulate open session parameters */
    static final class OpenSessionParams {
        private final ParcelFileDescriptor rtpFd;
        private final ParcelFileDescriptor rtcpFd;
        private final RtpConfig rtpConfig;
        private final OpenSessionCallback callback;

        OpenSessionParams(final ParcelFileDescriptor rtpFd,
                final ParcelFileDescriptor rtcpFd,
                final RtpConfig rtpConfig,
                final OpenSessionCallback callback) {
            this.rtpFd = rtpFd;
            this.rtcpFd = rtcpFd;
            this.rtpConfig = rtpConfig;
            this.callback = callback;
        }

        ParcelFileDescriptor getRtpFd() {
            return rtpFd;
        }

        ParcelFileDescriptor getRtcpFd() {
            return rtcpFd;
        }

        RtpConfig getRtpConfig() {
            return rtpConfig;
        }

        OpenSessionCallback getCallback() {
            return callback;
        }
    }

    static void sendMessage(final Handler handler, final int command) {
        final Message msg = handler.obtainMessage(command);
        msg.sendToTarget();
    }

    static void sendMessage(final Handler handler, final int command, final Object argument) {
        final Message msg = handler.obtainMessage(command, argument);
        msg.sendToTarget();
    }

    static void sendMessage(final Handler handler,
            final int command, final int arg1, final int arg2) {
        final Message msg = handler.obtainMessage(command, arg1, arg2);
        msg.sendToTarget();
    }

    static void sendMessage(final Handler handler, final int command,
            final int arg1, final int arg2, final Object object) {
        final Message msg = handler.obtainMessage(command, arg1, arg2, object);
        msg.sendToTarget();
    }

    private static RtpAddress buildRtpAddress(final AudioConfig audioConfig) {
        final RtpAddress addr = new RtpAddress();

        addr.ipAddress = audioConfig.getRemoteRtpAddress().getAddress().getHostAddress();
        addr.portNumber = audioConfig.getRemoteRtpAddress().getPort();

        return addr;
    }

    private static DtmfParams buildDtmfParams(final AudioConfig audioConfig) {
        final DtmfParams dtmfParams = new DtmfParams();

        dtmfParams.txPayloadTypeNumber = audioConfig.getTxDtmfPayloadTypeNumber();
        dtmfParams.rxPayloadTypeNumber = audioConfig.getRxDtmfPayloadTypeNumber();
        dtmfParams.samplingRateKHz = audioConfig.getDtmfSamplingRateKHz();

        return dtmfParams;
    }

    private static android.hardware.radio.ims.media.AmrParams
           buildAmrParams(final AudioConfig audioConfig) {
        final android.hardware.radio.ims.media.AmrParams amrParams =
                new android.hardware.radio.ims.media.AmrParams();

        amrParams.amrMode = audioConfig.getAmrParams().getAmrMode();
        amrParams.octetAligned = audioConfig.getAmrParams().getOctetAligned();
        amrParams.maxRedundancyMillis = audioConfig.getAmrParams().getMaxRedundancyMillis();

        return amrParams;
    }

    private static android.hardware.radio.ims.media.EvsParams
            buildEvsParams(final AudioConfig audioConfig) {
        final android.hardware.radio.ims.media.EvsParams evsParams =
                new android.hardware.radio.ims.media.EvsParams();

        evsParams.bandwidth = audioConfig.getEvsParams().getEvsBandwidth();
        evsParams.evsMode = audioConfig.getEvsParams().getEvsMode();
        evsParams.channelAwareMode = audioConfig.getEvsParams().getChannelAwareMode();
        evsParams.useHeaderFullOnly =
                audioConfig.getEvsParams().getUseHeaderFullOnly();
        evsParams.codecModeRequest = audioConfig.getEvsParams().getCodecModeRequest();

        return evsParams;
    }

    private static CodecParams buildCodecParams(final AudioConfig audioConfig) {
        final CodecParams codecParams = new CodecParams();

        codecParams.codecType = audioConfig.getCodecType();
        codecParams.rxPayloadTypeNumber = audioConfig.getRxPayloadTypeNumber();
        codecParams.txPayloadTypeNumber = audioConfig.getTxPayloadTypeNumber();
        codecParams.samplingRateKHz = audioConfig.getSamplingRateKHz();
        codecParams.dtxEnabled = audioConfig.getDtxEnabled();

        if (audioConfig.getCodecType() == AudioConfig.CODEC_AMR
              || audioConfig.getCodecType() == AudioConfig.CODEC_AMR_WB) {
            codecParams.codecSpecificParams = new CodecSpecificParams();
            codecParams.codecSpecificParams.setAmr(buildAmrParams(audioConfig));
        } else if (audioConfig.getCodecType() == AudioConfig.CODEC_EVS) {
            codecParams.codecSpecificParams = new CodecSpecificParams();
            codecParams.codecSpecificParams.setEvs(buildEvsParams(audioConfig));
        }

        return codecParams;
    }

    private static android.hardware.radio.ims.media.AnbrMode
            buildAnbrMode(final AudioConfig audioConfig) {
        final android.hardware.radio.ims.media.AnbrMode anbrModeParams =
                new android.hardware.radio.ims.media.AnbrMode();
        if (audioConfig.getAnbrMode() == null) {
            return null;
        } else {
            if (audioConfig.getCodecType() == AudioConfig.CODEC_AMR
                    || audioConfig.getCodecType() == AudioConfig.CODEC_AMR_WB) {
                anbrModeParams.anbrUplinkMode = new CodecMode();
                anbrModeParams.anbrDownlinkMode = new CodecMode();

                anbrModeParams.anbrUplinkMode.setAmr(
                        audioConfig.getAnbrMode().getAnbrUplinkCodecMode());
                anbrModeParams.anbrDownlinkMode.setAmr(
                        audioConfig.getAnbrMode().getAnbrDownlinkCodecMode());
            } else if (audioConfig.getCodecType() == AudioConfig.CODEC_EVS) {
                anbrModeParams.anbrUplinkMode = new CodecMode();
                anbrModeParams.anbrDownlinkMode = new CodecMode();

                anbrModeParams.anbrUplinkMode.setEvs(
                        audioConfig.getAnbrMode().getAnbrUplinkCodecMode());
                anbrModeParams.anbrDownlinkMode.setEvs(
                        audioConfig.getAnbrMode().getAnbrDownlinkCodecMode());
            }

            return anbrModeParams;
        }
    }

    private static RtpSessionParams buildSessionParams(final AudioConfig audioConfig) {
        final RtpSessionParams sessionParams = new RtpSessionParams();

        sessionParams.pTimeMillis = audioConfig.getPtimeMillis();
        sessionParams.maxPtimeMillis = audioConfig.getMaxPtimeMillis();
        sessionParams.dscp = audioConfig.getDscp();
        sessionParams.dtmfParams = buildDtmfParams(audioConfig);
        sessionParams.codecParams = buildCodecParams(audioConfig);

        return sessionParams;
    }

    private static android.hardware.radio.ims.media.RtcpConfig
            buildRtcpConfig(final AudioConfig audioConfig) {
        final android.hardware.radio.ims.media.RtcpConfig rtcpConfig =
                new android.hardware.radio.ims.media.RtcpConfig();

        rtcpConfig.canonicalName = audioConfig.getRtcpConfig().getCanonicalName();
        rtcpConfig.transmitPort = audioConfig.getRtcpConfig().getTransmitPort();
        rtcpConfig.transmitIntervalSec = audioConfig.getRtcpConfig().getIntervalSec();
        rtcpConfig.rtcpXrBlocks = audioConfig.getRtcpConfig().getRtcpXrBlockTypes();

        return rtcpConfig;
    }

    /** Converts {@link AudioConfig} to HAL RtpConfig */
    public static android.hardware.radio.ims.media.RtpConfig convertToRtpConfig(
            final AudioConfig audioConfig) {
        final android.hardware.radio.ims.media.RtpConfig rtpConfig;

        if (audioConfig == null) {
            rtpConfig = null;
        } else {
            rtpConfig = new android.hardware.radio.ims.media.RtpConfig();
            rtpConfig.direction = audioConfig.getMediaDirection();
            rtpConfig.accessNetwork = audioConfig.getAccessNetwork();
            rtpConfig.remoteAddress = buildRtpAddress(audioConfig);
            rtpConfig.sessionParams = buildSessionParams(audioConfig);
            rtpConfig.rtcpConfig = buildRtcpConfig(audioConfig);
            rtpConfig.anbrModeParams = buildAnbrMode(audioConfig);
        }

        return rtpConfig;
    }

    /** Converts {@link MediaQuailtyStatus} to HAL MediaQuailtyStatus */
    public static android.hardware.radio.ims.media.MediaQualityStatus
            convertToHalMediaQualityStatus(final MediaQualityStatus status) {
        final android.hardware.radio.ims.media.MediaQualityStatus halStatus =
                new android.hardware.radio.ims.media.MediaQualityStatus();
        halStatus.rtpInactivityTimeMillis = status.getRtpInactivityTimeMillis();
        halStatus.rtcpInactivityTimeMillis = status.getRtcpInactivityTimeMillis();
        halStatus.rtpPacketLossRate = status.getRtpPacketLossRate();
        halStatus.rtpJitterMillis = status.getRtpJitterMillis();
        return halStatus;
    }

    private static RtcpConfig buildRtcpConfig(
            final android.hardware.radio.ims.media.RtpConfig rtpConfig) {
        final RtcpConfig rtcpConfig;

        if (rtpConfig.rtcpConfig == null) {
            rtcpConfig = null;
        } else {
            rtcpConfig = new RtcpConfig.Builder()
                    .setCanonicalName(rtpConfig.rtcpConfig.canonicalName)
                    .setTransmitPort(rtpConfig.rtcpConfig.transmitPort)
                    .setIntervalSec(rtpConfig.rtcpConfig.transmitIntervalSec)
                    .setRtcpXrBlockTypes(rtpConfig.rtcpConfig.rtcpXrBlocks)
                    .build();
        }

        return rtcpConfig;
    }

    private static EvsParams buildEvsParams(
            final android.hardware.radio.ims.media.RtpConfig rtpConfig) {
        final EvsParams evsParams;

        if (rtpConfig == null
                || rtpConfig.sessionParams == null
                || rtpConfig.sessionParams.codecParams == null
                || rtpConfig.sessionParams.codecParams.codecSpecificParams == null
                || rtpConfig.sessionParams.codecParams.codecSpecificParams.getTag()
                    != CodecSpecificParams.evs) {
            evsParams = null;
        } else {
            final android.hardware.radio.ims.media.EvsParams evs =
                    rtpConfig.sessionParams.codecParams.codecSpecificParams.getEvs();
            evsParams = new EvsParams.Builder()
                .setEvsbandwidth(evs.bandwidth)
                .setEvsMode(evs.evsMode)
                .setChannelAwareMode(evs.channelAwareMode)
                .setHeaderFullOnly(evs.useHeaderFullOnly)
                .setCodecModeRequest(evs.codecModeRequest)
                .build();
        }

        return evsParams;
    }

    private static AmrParams buildAmrParams(
            final android.hardware.radio.ims.media.RtpConfig rtpConfig) {
        final AmrParams amrParams;

        if (rtpConfig == null
                || rtpConfig.sessionParams == null
                || rtpConfig.sessionParams.codecParams == null
                || rtpConfig.sessionParams.codecParams.codecSpecificParams == null
                || rtpConfig.sessionParams.codecParams.codecSpecificParams.getTag()
                    != CodecSpecificParams.amr) {
            amrParams = null;
        } else {
            final android.hardware.radio.ims.media.AmrParams amr =
                    rtpConfig.sessionParams.codecParams.codecSpecificParams.getAmr();
            amrParams = new AmrParams.Builder()
                .setAmrMode(amr.amrMode)
                .setOctetAligned(amr.octetAligned)
                .setMaxRedundancyMillis(amr.maxRedundancyMillis)
                .build();
        }

        return amrParams;
    }

    private static AnbrMode buildAnbrMode(
            final android.hardware.radio.ims.media.RtpConfig rtpConfig) {
        final AnbrMode anbrMode;

        if (rtpConfig == null
                || rtpConfig.sessionParams == null
                || rtpConfig.sessionParams.codecParams == null) {
            anbrMode = null;
        } else {
            if (rtpConfig.sessionParams.codecParams.codecType == AudioConfig.CODEC_EVS) {
                anbrMode = new AnbrMode.Builder()
                        .setAnbrUplinkCodecMode(rtpConfig.anbrModeParams.anbrUplinkMode.getEvs())
                        .setAnbrDownlinkCodecMode(
                                rtpConfig.anbrModeParams.anbrDownlinkMode.getEvs())
                        .build();
            } else if (rtpConfig.sessionParams.codecParams.codecType == AudioConfig.CODEC_AMR
                    || rtpConfig.sessionParams.codecParams.codecType == AudioConfig.CODEC_AMR_WB) {
                anbrMode = new AnbrMode.Builder()
                        .setAnbrUplinkCodecMode(rtpConfig.anbrModeParams.anbrUplinkMode.getAmr())
                        .setAnbrDownlinkCodecMode(
                                rtpConfig.anbrModeParams.anbrDownlinkMode.getAmr())
                        .build();
            } else {
                anbrMode = new AnbrMode.Builder()
                        .setAnbrUplinkCodecMode(0)
                        .setAnbrDownlinkCodecMode(0)
                        .build();
            }
        }

        return anbrMode;
    }

    private static InetSocketAddress buildRtpAddress(
            final android.hardware.radio.ims.media.RtpConfig rtpConfig) {
        final InetSocketAddress rtpAddress;

        if (rtpConfig.remoteAddress == null) {
            rtpAddress = null;
        } else {
            rtpAddress = new InetSocketAddress(
                     InetAddresses.parseNumericAddress(rtpConfig.remoteAddress.ipAddress),
                     rtpConfig.remoteAddress.portNumber);
        }

        return rtpAddress;
    }

    /** Converts HAL RtpConfig to AudioConfig */
    public static AudioConfig convertToAudioConfig(
            final android.hardware.radio.ims.media.RtpConfig rtpConfig) {
        final AudioConfig audioConfig;

        if (rtpConfig == null) {
            audioConfig = null;
        } else {
            audioConfig = new AudioConfig.Builder()
                    .setMediaDirection(rtpConfig.direction)
                    .setAccessNetwork(rtpConfig.accessNetwork)
                    .setRemoteRtpAddress(buildRtpAddress(rtpConfig))
                    .setRtcpConfig(buildRtcpConfig(rtpConfig))
                    .setEvsParams(buildEvsParams(rtpConfig))
                    .setAmrParams(buildAmrParams(rtpConfig))
                    .setAnbrMode(buildAnbrMode(rtpConfig))
                    .build();

            /** Populate session related parameters if present */
            if (rtpConfig.sessionParams != null) {
                audioConfig.setDscp(rtpConfig.sessionParams.dscp);
                audioConfig.setPtimeMillis(rtpConfig.sessionParams.pTimeMillis);
                audioConfig.setMaxPtimeMillis(rtpConfig.sessionParams.maxPtimeMillis);

                /** Populate DTMF parameter */
                final DtmfParams dtmfParams = rtpConfig.sessionParams.dtmfParams;
                if (dtmfParams != null) {
                    audioConfig.setTxDtmfPayloadTypeNumber(dtmfParams.txPayloadTypeNumber);
                    audioConfig.setRxDtmfPayloadTypeNumber(dtmfParams.rxPayloadTypeNumber);
                    audioConfig.setDtmfSamplingRateKHz(dtmfParams.samplingRateKHz);
                }

                /** Populate codec parameters */
                final CodecParams codecParams = rtpConfig.sessionParams.codecParams;
                if (codecParams != null) {
                    audioConfig.setCodecType(codecParams.codecType);
                    audioConfig.setRxPayloadTypeNumber(codecParams.rxPayloadTypeNumber);
                    audioConfig.setTxPayloadTypeNumber(codecParams.txPayloadTypeNumber);
                    audioConfig.setSamplingRateKHz(codecParams.samplingRateKHz);
                    audioConfig.setDtxEnabled(codecParams.dtxEnabled);
                }
            }
        }

        return audioConfig;
    }

    /** Converts {@link MediaQualityThreshold} to HAL MediaQualityThreshold */
    public static android.hardware.radio.ims.media.MediaQualityThreshold
            convertMediaQualityThreshold(final MediaQualityThreshold in) {
        final android.hardware.radio.ims.media.MediaQualityThreshold out;

        if (in == null) {
            out =  null;
        } else {
            out = new android.hardware.radio.ims.media.MediaQualityThreshold();
            out.rtpInactivityTimerMillis = Arrays.copyOf(in.getRtpInactivityTimerMillis(),
                    in.getRtpInactivityTimerMillis().length);
            out.rtcpInactivityTimerMillis = in.getRtcpInactivityTimerMillis();
            out.rtpHysteresisTimeInMillis = in.getRtpHysteresisTimeInMillis();
            out.rtpPacketLossDurationMillis = in.getRtpPacketLossDurationMillis();
            out.rtpPacketLossRate = Arrays.copyOf(in.getRtpPacketLossRate(),
                    in.getRtpPacketLossRate().length);
            out.rtpJitterMillis = Arrays.copyOf(in.getRtpJitterMillis(),
                    in.getRtpJitterMillis().length);
            out.notifyCurrentStatus = in.getNotifyCurrentStatus();
        }

        return out;
    }

    /** Converts HAL MediaQualityThreshold to {@link MediaQualityThreshold} */
    public static MediaQualityThreshold convertMediaQualityThreshold(
            final android.hardware.radio.ims.media.MediaQualityThreshold in) {
        return (in == null) ? null : new MediaQualityThreshold.Builder()
                .setRtpInactivityTimerMillis(in.rtpInactivityTimerMillis)
                .setRtcpInactivityTimerMillis(in.rtcpInactivityTimerMillis)
                .setRtpHysteresisTimeInMillis(in.rtpHysteresisTimeInMillis)
                .setRtpPacketLossDurationMillis(in.rtpPacketLossDurationMillis)
                .setRtpPacketLossRate(in.rtpPacketLossRate)
                .setRtpJitterMillis(in.rtpJitterMillis)
                .setNotifyCurrentStatus(in.notifyCurrentStatus)
                .setVideoBitrateBps(0)
                .build();
    }

    /** Converts HAL MediaQualityStatus to {@link MediaQualityStatus} */
    public static MediaQualityStatus convertMediaQualityStatus(
            final android.hardware.radio.ims.media.MediaQualityStatus in) {
        return (in == null) ? null : new MediaQualityStatus.Builder()
                .setRtpInactivityTimeMillis(in.rtpInactivityTimeMillis)
                .setRtcpInactivityTimeMillis(in.rtcpInactivityTimeMillis)
                .setRtpPacketLossRate(in.rtpPacketLossRate)
                .setRtpJitterMillis(in.rtpJitterMillis)
                .build();
    }

    public static android.hardware.radio.ims.media.RtpHeaderExtension
            convertRtpHeaderExtension(final RtpHeaderExtension in) {
        final android.hardware.radio.ims.media.RtpHeaderExtension out;

        if (in == null) {
            out = null;
        } else {
            out = new android.hardware.radio.ims.media.RtpHeaderExtension();
            out.localId = in.getLocalIdentifier();
            out.data = in.getExtensionData();
        }

        return out;
    }

    public static RtpHeaderExtension convertRtpHeaderExtension(
            final android.hardware.radio.ims.media.RtpHeaderExtension in) {
        return (in == null) ? null : new RtpHeaderExtension(in.localId, in.data);
    }

    /** Converts HAL CallQuality to {@link CallQuality} */
    public static CallQuality convertCallQuality(
            final android.hardware.radio.ims.media.CallQuality in) {
        return (in == null) ? null : new CallQuality.Builder()
                .setDownlinkCallQualityLevel(in.downlinkCallQualityLevel)
                .setUplinkCallQualityLevel(in.uplinkCallQualityLevel)
                .setCallDurationMillis(in.callDuration)
                .setNumRtpPacketsTransmitted(in.numRtpPacketsTransmitted)
                .setNumRtpPacketsReceived(in.numRtpPacketsReceived)
                .setNumRtpPacketsTransmittedLost(in.numRtpPacketsTransmittedLost)
                .setNumRtpPacketsNotReceived(in.numRtpPacketsNotReceived)
                .setAverageRelativeJitter(in.averageRelativeJitter)
                .setMaxRelativeJitter(in.maxRelativeJitter)
                .setAverageRoundTripTimeMillis(in.averageRoundTripTime)
                .setCodecType(in.codecType)
                .setRtpInactivityDetected(in.rtpInactivityDetected)
                .setIncomingSilenceDetectedAtCallSetup(in.rxSilenceDetected)
                .setOutgoingSilenceDetectedAtCallSetup(in.txSilenceDetected)
                .setNumVoiceFrames(in.numVoiceFrames)
                .setNumNoDataFrames(in.numNoDataFrames)
                .setNumDroppedRtpPackets(in.numDroppedRtpPackets)
                .setMinPlayoutDelayMillis(in.minPlayoutDelayMillis)
                .setMaxPlayoutDelayMillis(in.maxPlayoutDelayMillis)
                .setNumRtpSidPacketsReceived(in.numRtpSidPacketsReceived)
                .setNumRtpDuplicatePackets(in.numRtpDuplicatePackets)
                .build();
    }
}
