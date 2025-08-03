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

package com.android.telephony.qns;

import static com.android.telephony.qns.QnsConstants.MIN_THRESHOLD_GAP;
import static com.android.telephony.qns.QnsConstants.POLICY_BAD;
import static com.android.telephony.qns.QnsConstants.POLICY_GOOD;

import android.net.wifi.WifiInfo;
import android.os.PersistableBundle;
import android.telephony.SignalThresholdInfo;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;

/**
 * This class supports loading QnsConfigArray of Thresholds & Policies (Good, Bad ,Worst) values in
 * case of cellular & (Good,Bad) in case of Wi-Fi Thresholds defined .
 */
class QnsCarrierAnspSupportConfig {
    /**
     * List of 3 customized eutran(4g) RSRP thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE registered) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSRP}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSRP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSRP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_RSRP_GOOD}"
     *   <LI>"Bad: {@link QnsConstants#KEY_DEFAULT_THRESHOLD_RSRP_BAD}"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_EUTRAN_RSRP_INT_ARRAY = "qns.idle_eutran_rsrp_int_array";

    /**
     * List of 3 customized eutran(4g) RSRP thresholds to be considered for rove-in & rove-out in 4G
     * (VoLTE) & in voice call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSRP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSRP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSRP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_RSRP_GOOD}"
     *   <LI>"Bad: {@link QnsConstants#KEY_DEFAULT_THRESHOLD_RSRP_BAD}"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_EUTRAN_RSRP_INT_ARRAY = "qns.voice_eutran_rsrp_int_array";

    /**
     * List of 3 customized eutran(4g) RSRP thresholds to be considered for rove-in & rove-out in 4G
     * (VoLTE) & in video call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSRP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSRP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSRP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_EUTRAN_RSRP_INT_ARRAY = "qns.video_eutran_rsrp_int_array";

    /**
     * List of 3 customized eutran(4g) RSRQ thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE registered) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSRQ}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSRQ_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSRQ_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_EUTRAN_RSRQ_INT_ARRAY = "qns.idle_eutran_rsrq_int_array";

    /**
     * List of 3 customized eutran(4g) RSRQ thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE) & in Voice Call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSRQ}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSRQ_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSRQ_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_EUTRAN_RSRQ_INT_ARRAY = "qns.voice_eutran_rsrq_int_array";

    /**
     * List of 3 customized eutran(4g) RSRQ thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE) & in Video Call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSRQ}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSRQ_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSRQ_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_EUTRAN_RSRQ_INT_ARRAY = "qns.video_eutran_rsrq_int_array";

    /**
     * List of 3 customized eutran(4g) RSSNR thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE registered) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSSNR}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSSNR_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSSNR_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_EUTRAN_RSSNR_INT_ARRAY = "qns.idle_eutran_rssnr_int_array";

    /**
     * List of 3 customized eutran(4g) RSSNR thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE registered) & in Voice Call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSSNR}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSSNR_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSSNR_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_EUTRAN_RSSNR_INT_ARRAY = "qns.voice_eutran_rssnr_int_array";

    /**
     * List of 3 customized eutran(4g) RSSNR thresholds to be considered for rove-in & rove-out in
     * 4G(VoLTE registered) & in Video Call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSSNR}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSSNR_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSSNR_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_EUTRAN_RSSNR_INT_ARRAY = "qns.video_eutran_rssnr_int_array";

    /**
     * List of 3 customized ngran(5g) SSRSRP thresholds to be considered for rove-in & rove-out in
     * 5g(VoNR registered) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSRSRP}
     *
     * <p>3 threshold integers must be within the boundaries Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSRSRP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSRSRP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD}"
     *   <LI>"Bad: {@link QnsConstants#KEY_DEFAULT_THRESHOLD_SSRSRP_BAD}"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_NGRAN_SSRSRP_INT_ARRAY = "qns.idle_ngran_ssrsrp_int_array";

    /**
     * List of 3 customized ngran(5g) SSRSRP thresholds to be considered for rove-in & rove-out in
     * 5g(VoNR) & in voice call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSRSRP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSRSRP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSRSRP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD}"
     *   <LI>"Bad: {@link QnsConstants#KEY_DEFAULT_THRESHOLD_SSRSRP_BAD}"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_NGRAN_SSRSRP_INT_ARRAY = "qns.voice_ngran_ssrsrp_int_array";

    /**
     * List of 3 customized ngran(5g) SSRSRP thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in video call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSRSRP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSRSRP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSRSRP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_NGRAN_SSRSRP_INT_ARRAY = "qns.video_ngran_ssrsrp_int_array";

    /**
     * List of 3 customized ngran(5g) SSRSRQ thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSRSRQ}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSRSRQ_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSRSRQ_MAX_VALUE}
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply. {@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_NGRAN_SSRSRQ_INT_ARRAY = "qns.idle_ngran_ssrsrq_int_array";

    /**
     * List of 3 customized ngran(5g) SSRSRQ thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in voice call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSRSRQ}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSRSRQ_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSRSRQ_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_NGRAN_SSRSRQ_INT_ARRAY = "qns.voice_ngran_ssrsrq_int_array";

    /**
     * List of 3 customized ngran(5g) SSRSRQ thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in video call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSRSRQ}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSRSRQ_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSRSRQ_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_NGRAN_SSRSRQ_INT_ARRAY = "qns.video_ngran_ssrsrq_int_array";

    /**
     * List of 3 customized ngran(5g) SSSINR thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSSINR}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSSINR_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSSINR_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_NGRAN_SSSINR_INT_ARRAY = "qns.idle_ngran_sssinr_int_array";

    /**
     * List of 3 customized ngran(5g) SSSINR thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in voice call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSSINR}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSSINR_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSSINR_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_NGRAN_SSSINR_INT_ARRAY = "qns.voice_ngran_sssinr_int_array";

    /**
     * List of 3 customized ngran(5g) SSSINR thresholds to be considered for rove-in & rove-out in
     * 5G (VoNR) & in video call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_SSSINR}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_SSSINR_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_SSSINR_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_NGRAN_SSSINR_INT_ARRAY = "qns.video_ngran_sssinr_int_array";

    /**
     * List of 3 customized utran(3g) RSCP thresholds to be considered for rove-in & rove-out in 3g
     * (IMS registered) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSCP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSCP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSCP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_RSCP_GOOD}"
     *   <LI>"Bad: {@link QnsConstants#KEY_DEFAULT_THRESHOLD_RSCP_BAD}"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_UTRAN_RSCP_INT_ARRAY = "qns.idle_utran_rscp_int_array";

    /**
     * List of 3 customized utran(3g) RSCP thresholds to be considered for rove-in & rove-out in 3g
     * (IMS registered) & in voice call(SRVCC) state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSCP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSCP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSCP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_UTRAN_RSCP_INT_ARRAY = "qns.voice_utran_rscp_int_array";

    /**
     * List of 3 customized utran(3g) RSCP thresholds to be considered for rove-in & rove-out in 3g
     * (IMS registered) & in video call(SRVCC) state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSCP}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSCP_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSCP_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_UTRAN_RSCP_INT_ARRAY = "qns.video_utran_rscp_int_array";

    /**
     * List of 3 customized utran(3G) Ec/No threshold values which are considered for rove-in and
     * rove-out in idle state.
     *
     * <p>3 threshold integers must be within the boundaries: Note: When a value is set to "65535",
     * it means an invalid threshold value. {@link SignalThresholdInfo#SIGNAL_ECNO_MIN_VALUE} {@link
     * SignalThresholdInfo#SIGNAL_ECNO_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value will be applied.
     */
    static final String KEY_IDLE_UTRAN_ECNO_INT_ARRAY = "qns.idle_utran_ecno_int_array";

    /**
     * List of 3 customized geran(2g) RSSI thresholds to be considered for rove-in & rove-out in 2g
     * (IMS registered) & in idle state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSSI}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSSI_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSSI_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_GERAN_RSSI_INT_ARRAY = "qns.idle_geran_rssi_int_array";

    /**
     * List of 3 customized geran(2g) RSSI thresholds to be considered for rove-in & rove-out in 2g
     * (IMS registered) & in voice call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSSI}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSSI_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSSI_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_GERAN_RSSI_GOOD}"
     *   <LI>"Bad: {@link QnsConstants#KEY_DEFAULT_THRESHOLD_GERAN_RSSI_BAD}"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_GERAN_RSSI_INT_ARRAY = "qns.voice_geran_rssi_int_array";

    /**
     * List of 3 customized geran(2g) RSSI thresholds to be considered for rove-in & rove-out in 2g
     * (IMS registered) & in video call state.
     *
     * <p>Reference: {@link SignalThresholdInfo#SIGNAL_MEASUREMENT_TYPE_RSSI}
     *
     * <p>3 threshold integers must be within the boundaries: Note: In case of "worst" criteria is
     * not relevant the same is set @ "65535" {@link SignalThresholdInfo#SIGNAL_RSSI_MIN_VALUE}
     * {@link SignalThresholdInfo#SIGNAL_RSSI_MAX_VALUE}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_GERAN_RSSI_INT_ARRAY = "qns.video_geran_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in idle state.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 2 values defined by default(Good, Bad)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD}"
     *   <LI>"Bad:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD}"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_WIFI_RSSI_INT_ARRAY = "qns.idle_wifi_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in voice call state.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 2 values defined by default(Good, Bad)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD}"
     *   <LI>"Bad:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD}"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_WIFI_RSSI_INT_ARRAY = "qns.voice_wifi_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in video call state.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 2 values defined by default(Good, Bad)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD}"
     *   <LI>"Bad:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD}"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_WIFI_RSSI_INT_ARRAY = "qns.video_wifi_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in idle state to be considered during Overriding with Wifi Pref settings.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 2 values defined by default(Good, Bad)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD}"
     *   <LI>"Bad:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD}"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_OVERRIDE_WIFI_PREF_IDLE_WIFI_RSSI_INT_ARRAY =
            "qns.override_wifi_pref_idle_wifi_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in voice call state to be considered during Overriding with Wifi Pref
     * settings.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 2 values defined by default(Good, Bad)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD}"
     *   <LI>"Bad:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD}"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_OVERRIDE_WIFI_PREF_VOICE_WIFI_RSSI_INT_ARRAY =
            "qns.override_wifi_pref_voice_wifi_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in video call state to be considered during Overriding with Wifi Pref
     * settings.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 2 values defined by default(Good, Bad)}
     *
     * <UL>
     *   <LI>"Good:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD}"
     *   <LI>"Bad:{@link QnsConstants#KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD}"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_OVERRIDE_WIFI_PREF_VIDEO_WIFI_RSSI_INT_ARRAY =
            "qns.override_wifi_pref_video_wifi_rssi_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in idle state. Without cellular coverage, it sets the wifi rove-in &
     * rove-out threshold of the UE.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_IDLE_WIFI_RSSI_WITHOUT_CELLULAR_INT_ARRAY =
            "qns.idle_wifi_rssi_without_cellular_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in voice call state. Without cellular coverage, it sets the wifi rove-in &
     * rove-out threshold of the UE.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VOICE_WIFI_RSSI_WITHOUT_CELLULAR_INT_ARRAY =
            "qns.voice_wifi_rssi_without_cellular_int_array";

    /**
     * List of 2 customized wifi RSSI thresholds to be considered for rove-in & rove-out in wifi
     * (IMS registered) & in video call state. Without cellular coverage, it sets the wifi rove-in &
     * rove-out threshold of the UE.
     *
     * <p>2 threshold integers must be within the boundaries: {@link WifiInfo#MIN_RSSI} {@link
     * WifiInfo#MIN_RSSI}
     *
     * <p>{@code 3 values defined by default(Good, Bad, Worst)}
     *
     * <UL>
     *   <LI>"Good:65535"
     *   <LI>"Bad:65535"
     *   <LI>"Worst:65535"
     * </UL>
     *
     * <p>This key is considered invalid if the format is violated. If the key not configured, a
     * default value set will apply.
     */
    static final String KEY_VIDEO_WIFI_RSSI_WITHOUT_CELLULAR_INT_ARRAY =
            "qns.video_wifi_rssi_without_cellular_int_array";

    // Internal Policy Rule/keys Updates
    static final String KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_in_idle_wifi_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_in_voice_wifi_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_in_video_wifi_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_in_idle_cell_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_in_voice_cell_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_in_video_cell_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_out_idle_wifi_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_out_voice_wifi_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_out_video_wifi_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_out_idle_cell_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_out_voice_cell_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_HOME_STRING_ARRAY =
            "qns.condition_rove_out_video_cell_pref_home_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_in_idle_wifi_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_in_voice_wifi_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_in_video_wifi_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_in_idle_cell_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_in_voice_cell_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_in_video_cell_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_out_idle_wifi_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_out_voice_wifi_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_out_video_wifi_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_out_idle_cell_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_out_voice_cell_pref_roam_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_ROAM_STRING_ARRAY =
            "qns.condition_rove_out_video_cell_pref_roam_string_array";

    /**
     * Define keys for extended policy rules. Different handover criteria based on whether
     * hysteresis timer is running or not.
     */
    static final String KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_idle_wifi_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_HOME_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_idle_wifi_pref_home_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_voice_wifi_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_HOME_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_voice_wifi_pref_home_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_video_wifi_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_HOME_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_video_wifi_pref_home_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_idle_cell_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_HOME_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_idle_cell_pref_home_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_voice_cell_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_HOME_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_voice_cell_pref_home_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_video_cell_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_HOME_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_video_cell_pref_home_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_idle_wifi_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_HOME_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_idle_wifi_pref_home_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_voice_wifi_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_HOME_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_voice_wifi_pref_home_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_video_wifi_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_HOME_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_video_wifi_pref_home_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_idle_cell_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_HOME_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_idle_cell_pref_home_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_voice_cell_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_HOME_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_voice_cell_pref_home_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_video_cell_pref_home_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_HOME_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_video_cell_pref_home_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_idle_wifi_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_idle_wifi_pref_roam_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_voice_wifi_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_voice_wifi_pref_roam_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_video_wifi_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_video_wifi_pref_roam_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_idle_cell_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_idle_cell_pref_roam_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_voice_cell_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_voice_cell_pref_roam_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_in_video_cell_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY =
            "qns.condition_rove_in_video_cell_pref_roam_guarding_wifi_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_idle_wifi_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_ROAM_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_idle_wifi_pref_roam_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_voice_wifi_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_ROAM_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_voice_wifi_pref_roam_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_video_wifi_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_ROAM_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_video_wifi_pref_roam_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_idle_cell_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_ROAM_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_idle_cell_pref_roam_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_voice_cell_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_ROAM_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_voice_cell_pref_roam_guarding_cell_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY =
            "qns.condition_rove_out_video_cell_pref_roam_guarding_none_string_array";
    static final String KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_ROAM_GUARDING_CELL_STRING_ARRAY =
            "qns.condition_rove_out_video_cell_pref_roam_guarding_cell_string_array";

    private final HashMap<String, int[]> mQnsRatThresholdMap = new HashMap<>();

    private final HashMap<String, String[]> mQnsPolicyMap = new HashMap<>();
    private final String mLogTag;

    static final String[] THRESHOLD_KEYS =
            new String[] {
                KEY_IDLE_EUTRAN_RSRP_INT_ARRAY,
                KEY_VOICE_EUTRAN_RSRP_INT_ARRAY,
                KEY_VIDEO_EUTRAN_RSRP_INT_ARRAY,
                KEY_IDLE_EUTRAN_RSRQ_INT_ARRAY,
                KEY_VOICE_EUTRAN_RSRQ_INT_ARRAY,
                KEY_VIDEO_EUTRAN_RSRQ_INT_ARRAY,
                KEY_IDLE_EUTRAN_RSSNR_INT_ARRAY,
                KEY_VOICE_EUTRAN_RSSNR_INT_ARRAY,
                KEY_VIDEO_EUTRAN_RSSNR_INT_ARRAY,
                KEY_IDLE_NGRAN_SSRSRP_INT_ARRAY,
                KEY_VOICE_NGRAN_SSRSRP_INT_ARRAY,
                KEY_VIDEO_NGRAN_SSRSRP_INT_ARRAY,
                KEY_IDLE_NGRAN_SSRSRQ_INT_ARRAY,
                KEY_VOICE_NGRAN_SSRSRQ_INT_ARRAY,
                KEY_VIDEO_NGRAN_SSRSRQ_INT_ARRAY,
                KEY_IDLE_NGRAN_SSSINR_INT_ARRAY,
                KEY_VOICE_NGRAN_SSSINR_INT_ARRAY,
                KEY_VIDEO_NGRAN_SSSINR_INT_ARRAY,
                KEY_IDLE_UTRAN_RSCP_INT_ARRAY,
                KEY_VOICE_UTRAN_RSCP_INT_ARRAY,
                KEY_VIDEO_UTRAN_RSCP_INT_ARRAY,
                KEY_IDLE_UTRAN_ECNO_INT_ARRAY,
                KEY_IDLE_GERAN_RSSI_INT_ARRAY,
                KEY_VOICE_GERAN_RSSI_INT_ARRAY,
                KEY_VIDEO_GERAN_RSSI_INT_ARRAY,
                KEY_IDLE_WIFI_RSSI_INT_ARRAY,
                KEY_VOICE_WIFI_RSSI_INT_ARRAY,
                KEY_VIDEO_WIFI_RSSI_INT_ARRAY,
                KEY_OVERRIDE_WIFI_PREF_IDLE_WIFI_RSSI_INT_ARRAY,
                KEY_OVERRIDE_WIFI_PREF_VOICE_WIFI_RSSI_INT_ARRAY,
                KEY_OVERRIDE_WIFI_PREF_VIDEO_WIFI_RSSI_INT_ARRAY,
                KEY_IDLE_WIFI_RSSI_WITHOUT_CELLULAR_INT_ARRAY,
                KEY_VOICE_WIFI_RSSI_WITHOUT_CELLULAR_INT_ARRAY,
                KEY_VIDEO_WIFI_RSSI_WITHOUT_CELLULAR_INT_ARRAY
            };

    /**
     * Constructor to Slot & Context whose Access Network selection policy related support configs
     * needs to be loaded, along with Other QNS Configurations on which the related Carrier Config
     * ID to be loaded.
     *
     * @param slotIndex Constructor for initialising QnsCarrierAnspSupportConfig to current SlotID
     */
    QnsCarrierAnspSupportConfig(int slotIndex) {
        mLogTag =
                QnsConstants.QNS_TAG
                        + "_"
                        + QnsCarrierAnspSupportConfig.class.getSimpleName()
                        + "_"
                        + slotIndex;
    }

    /**
     * This method loads the Threshold Array & Policy Array rules for building Access Network
     * Selection policies
     *
     * @param bundleCarrier : Carrier config Manager (pb config) persistent bundle
     * @param bundleAsset : asset config (xml) persistent bundle
     */
    void loadQnsAnspSupportArray(PersistableBundle bundleCarrier, PersistableBundle bundleAsset) {
        updateAnspThresholdArrayList(bundleCarrier, bundleAsset);
        updateAnspPolicyArrayList(bundleCarrier, bundleAsset);
    }

    private void updateAnspThresholdArrayList(
            PersistableBundle bundleCarrier, PersistableBundle bundleAsset) {

        for (String key : THRESHOLD_KEYS) {
            int[] anspThresholdArray = QnsUtils.getConfig(bundleCarrier, bundleAsset, key);
            if (anspThresholdArray != null && anspThresholdArray.length > 1) {
                anspThresholdArray = validateAndAdjustThresholdArray(anspThresholdArray, key);
            }
            mQnsRatThresholdMap.put(key, anspThresholdArray);
        }
    }

    private int[] validateAndAdjustThresholdArray(int[] thresholds, String thresholdKey) {
        if (thresholds[POLICY_GOOD] != QnsCarrierConfigManager.QnsConfigArray.INVALID
                && thresholds[POLICY_BAD] != QnsCarrierConfigManager.QnsConfigArray.INVALID
                && thresholds[POLICY_GOOD] - thresholds[POLICY_BAD] < MIN_THRESHOLD_GAP) {
            if (thresholds[POLICY_GOOD] - thresholds[POLICY_BAD] < 0) {
                Log.d(mLogTag, "invalid Thresholds for " + thresholdKey + " use default.");
                return QnsUtils.getConfig(null, null, thresholdKey);
            } else if (thresholds[POLICY_GOOD] - thresholds[POLICY_BAD] < MIN_THRESHOLD_GAP) {
                int currentGap = thresholds[POLICY_GOOD] - thresholds[POLICY_BAD];
                int[] adjust = thresholds.clone();
                for (int i = currentGap; i < MIN_THRESHOLD_GAP; i++) {
                    if ((i - currentGap) % 2 == 0) {
                        adjust[POLICY_GOOD]++;
                    } else {
                        adjust[POLICY_BAD]--;
                    }
                }
                Log.d(
                        mLogTag,
                        "Thresholds("
                                + thresholdKey
                                + ") gap is too small adjust:"
                                + "["
                                + thresholds[POLICY_GOOD]
                                + "] > ["
                                + adjust[POLICY_GOOD]
                                + "]"
                                + "["
                                + thresholds[POLICY_BAD]
                                + "] > ["
                                + adjust[POLICY_BAD]
                                + "]");
                return adjust;
            }
        }
        return thresholds;
    }

    private void updateAnspPolicyArrayList(
            PersistableBundle bundleCarrier, PersistableBundle bundleAsset) {

        String[] policyKeys =
                new String[] {
                    KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_HOME_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_ROAM_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_HOME_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_HOME_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_HOME_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_HOME_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_HOME_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_HOME_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_HOME_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_HOME_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_HOME_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_HOME_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_HOME_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_HOME_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_HOME_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_WIFI_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_WIFI_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_WIFI_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_IDLE_CELL_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VOICE_CELL_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_IN_VIDEO_CELL_PREF_ROAM_GUARDING_WIFI_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_WIFI_PREF_ROAM_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_WIFI_PREF_ROAM_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_WIFI_PREF_ROAM_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_IDLE_CELL_PREF_ROAM_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VOICE_CELL_PREF_ROAM_GUARDING_CELL_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_ROAM_GUARDING_NONE_STRING_ARRAY,
                    KEY_CONDITION_ROVE_OUT_VIDEO_CELL_PREF_ROAM_GUARDING_CELL_STRING_ARRAY,
                };
        for (String key : policyKeys) {
            String[] anspPolicyArray = QnsUtils.getConfig(bundleCarrier, bundleAsset, key);
            mQnsPolicyMap.put(key, anspPolicyArray);
        }
    }

    int[] getAnspCarrierThreshold(String key) {
        return mQnsRatThresholdMap.get(key);
    }

    String[] getAnspCarrierPolicy(String key) {
        return mQnsPolicyMap.get(key);
    }

    /**
     * Check if Threshold config was Updated.
     *
     * @param configBundle : Carrier config Manager (pb config) persistent bundle
     * @param assetBundle : asset config (xml) persistent bundle
     * @return true/false
     */
    synchronized boolean checkQnsAnspConfigChange(
            PersistableBundle configBundle, PersistableBundle assetBundle) {
        return isThresholdConfigChanged(configBundle, assetBundle);
    }

    private boolean isThresholdConfigChanged(
            PersistableBundle configChangeBundle, PersistableBundle assetBundle) {
        HashMap<String, int[]> qnsRatThresUpdatedMap = new HashMap<>();

        for (String key : THRESHOLD_KEYS) {
            int[] anspThresholdArray = QnsUtils.getConfig(configChangeBundle, assetBundle, key);
            qnsRatThresUpdatedMap.put(key, anspThresholdArray);
        }

        for (String k : mQnsRatThresholdMap.keySet()) {
            if (!Arrays.equals(mQnsRatThresholdMap.get(k), qnsRatThresUpdatedMap.get(k))) {
                mQnsRatThresholdMap.putAll(qnsRatThresUpdatedMap);
                return true;
            }
        }

        return false;
    }
}
