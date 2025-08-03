/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

import android.content.ContentResolver;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telecom.CallDiagnosticService;
import android.telecom.CallDiagnostics;
import android.telecom.CallRedirectionService;
import android.telephony.ims.ImsReasonInfo;

import java.util.concurrent.TimeUnit;

/**
 * A helper class which serves only to make it easier to lookup timeout values. This class should
 * never be instantiated, and only accessed through the {@link #get(String, long)} method.
 *
 * These methods are safe to call from any thread, including the UI thread.
 */
public final class Timeouts {
    public static class Adapter {
        public Adapter() {
        }

        public long getCallScreeningTimeoutMillis(ContentResolver cr) {
            return Timeouts.getCallScreeningTimeoutMillis(cr);
        }

        public long getCallBindBluetoothInCallServicesDelay(ContentResolver cr) {
            return Timeouts.getCallBindBluetoothInCallServicesDelay(cr);
        }

        public long getCallRemoveUnbindInCallServicesDelay(ContentResolver cr) {
            return Timeouts.getCallRemoveUnbindInCallServicesDelay(cr);
        }

        public long getRetryBluetoothConnectAudioBackoffMillis(ContentResolver cr) {
            return Timeouts.getRetryBluetoothConnectAudioBackoffMillis(cr);
        }

        public long getBluetoothPendingTimeoutMillis(ContentResolver cr) {
            return Timeouts.getBluetoothPendingTimeoutMillis(cr);
        }

        public long getEmergencyCallbackWindowMillis(ContentResolver cr) {
            return Timeouts.getEmergencyCallbackWindowMillis(cr);
        }

        public long getUserDefinedCallRedirectionTimeoutMillis(ContentResolver cr) {
            return Timeouts.getUserDefinedCallRedirectionTimeoutMillis(cr);
        }

        public long getCarrierCallRedirectionTimeoutMillis(ContentResolver cr) {
            return Timeouts.getCarrierCallRedirectionTimeoutMillis(cr);
        }

        public long getPhoneAccountSuggestionServiceTimeout(ContentResolver cr) {
            return Timeouts.getPhoneAccountSuggestionServiceTimeout(cr);
        }

        public long getCallRecordingToneRepeatIntervalMillis(ContentResolver cr) {
            return Timeouts.getCallRecordingToneRepeatIntervalMillis(cr);
        }

        public long getCallDiagnosticServiceTimeoutMillis(ContentResolver cr) {
            return Timeouts.getCallDiagnosticServiceTimeoutMillis(cr);
        }

        public long getCallStartAppOpDebounceIntervalMillis() {
            return Timeouts.getCallStartAppOpDebounceIntervalMillis();
        }

        public long getVoipCallTransitoryStateTimeoutMillis() {
            return Timeouts.getVoipCallTransitoryStateTimeoutMillis();
        }

        public long getVoipEmergencyCallTransitoryStateTimeoutMillis() {
            return Timeouts.getVoipEmergencyCallTransitoryStateTimeoutMillis();
        }

        public long getNonVoipCallTransitoryStateTimeoutMillis() {
            return Timeouts.getNonVoipCallTransitoryStateTimeoutMillis();
        }

        public long getNonVoipEmergencyCallTransitoryStateTimeoutMillis() {
            return Timeouts.getNonVoipEmergencyCallTransitoryStateTimeoutMillis();
        }

        public long getVoipCallIntermediateStateTimeoutMillis() {
            return Timeouts.getVoipCallIntermediateStateTimeoutMillis();
        }

        public long getVoipEmergencyCallIntermediateStateTimeoutMillis() {
            return Timeouts.getVoipEmergencyCallIntermediateStateTimeoutMillis();
        }

        public long getNonVoipCallIntermediateStateTimeoutMillis() {
            return Timeouts.getNonVoipCallIntermediateStateTimeoutMillis();
        }

        public long getNonVoipEmergencyCallIntermediateStateTimeoutMillis() {
            return Timeouts.getNonVoipEmergencyCallIntermediateStateTimeoutMillis();
        }

        public long getEmergencyCallTimeBeforeUserDisconnectThresholdMillis(){
            return Timeouts.getEmergencyCallTimeBeforeUserDisconnectThresholdMillis();
        }

        public long getEmergencyCallActiveTimeThresholdMillis(){
            return Timeouts.getEmergencyCallActiveTimeThresholdMillis();
        }

        public int getDaysBackToSearchEmergencyDiagnosticEntries(){
            return Timeouts.getDaysBackToSearchEmergencyDiagnosticEntries();

        }
    }

    /** A prefix to use for all keys so to not clobber the global namespace. */
    private static final String PREFIX = "telecom.";

    /**
     * threshold used to filter out ecalls that the user may have dialed by mistake
     * It is used only when the disconnect cause is LOCAL by EmergencyDiagnosticLogger
     */
    private static final String EMERGENCY_CALL_TIME_BEFORE_USER_DISCONNECT_THRESHOLD_MILLIS =
            "emergency_call_time_before_user_disconnect_threshold_millis";

    /**
     * Returns the threshold used to detect ecalls that transition to active but only for a very
     * short duration. These short duration active calls can result in Diagnostic data collection.
     */
    private static final String EMERGENCY_CALL_ACTIVE_TIME_THRESHOLD_MILLIS =
            "emergency_call_active_time_threshold_millis";

    /**
     * Time in Days that is used to filter out old dropbox entries for emergency call diagnostic
     * data. Entries older than this are ignored
     */
    private static final String DAYS_BACK_TO_SEARCH_EMERGENCY_DROP_BOX_ENTRIES =
            "days_back_to_search_emergency_drop_box_entries";

    /**
     * A prefix to use for {@link DeviceConfig} for the transitory state timeout of
     * VoIP Call, in millis.
     */
    private static final String TRANSITORY_STATE_VOIP_NORMAL_TIMEOUT_MILLIS =
            "transitory_state_voip_normal_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the transitory state timeout of
     * VoIP emergency Call, in millis.
     */
    private static final String TRANSITORY_STATE_VOIP_EMERGENCY_TIMEOUT_MILLIS =
            "transitory_state_voip_emergency_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the transitory state timeout of
     * non-VoIP call, in millis.
     */
    private static final String TRANSITORY_STATE_NON_VOIP_NORMAL_TIMEOUT_MILLIS =
            "transitory_state_non_voip_normal_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the transitory state timeout of
     * non-VoIP emergency call, in millis.
     */
    private static final String TRANSITORY_STATE_NON_VOIP_EMERGENCY_TIMEOUT_MILLIS =
            "transitory_state_non_voip_emergency_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the intermediate state timeout of
     * VoIP call, in millis.
     */
    private static final String INTERMEDIATE_STATE_VOIP_NORMAL_TIMEOUT_MILLIS =
            "intermediate_state_voip_normal_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the intermediate state timeout of
     * VoIP emergency call, in millis.
     */
    private static final String INTERMEDIATE_STATE_VOIP_EMERGENCY_TIMEOUT_MILLIS =
            "intermediate_state_voip_emergency_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the intermediate state timeout of
     * non-VoIP call, in millis.
     */
    private static final String INTERMEDIATE_STATE_NON_VOIP_NORMAL_TIMEOUT_MILLIS =
            "intermediate_state_non_voip_normal_timeout_millis";

    /**
     * A prefix to use for {@link DeviceConfig} for the intermediate state timeout of
     * non-VoIP emergency call, in millis.
     */
    private static final String INTERMEDIATE_STATE_NON_VOIP_EMERGENCY_TIMEOUT_MILLIS =
            "intermediate_state_non_voip_emergency_timeout_millis";

    private Timeouts() {
    }

    /**
     * Returns the timeout value from Settings or the default value if it hasn't been changed. This
     * method is safe to call from any thread, including the UI thread.
     *
     * @param contentResolver The content resolved.
     * @param key             Settings key to retrieve.
     * @param defaultValue    Default value, in milliseconds.
     * @return The timeout value from Settings or the default value if it hasn't been changed.
     */
    private static long get(ContentResolver contentResolver, String key, long defaultValue) {
        return Settings.Secure.getLongForUser(contentResolver, PREFIX + key, defaultValue,
                        contentResolver.getUserId());
    }

    /**
     * Returns the amount of time to wait before disconnecting a call that was canceled via
     * NEW_OUTGOING_CALL broadcast. This timeout allows apps which repost the call using a gateway
     * to reuse the existing call, preventing the call from causing a start->end->start jank in the
     * in-call UI.
     */
    public static long getNewOutgoingCallCancelMillis(ContentResolver contentResolver) {
        return get(contentResolver, "new_outgoing_call_cancel_ms", 500L);
    }

    /**
     * Returns the maximum amount of time to wait before disconnecting a call that was canceled via
     * NEW_OUTGOING_CALL broadcast. This prevents malicious or poorly configured apps from
     * forever tying up the Telecom stack.
     */
    public static long getMaxNewOutgoingCallCancelMillis(ContentResolver contentResolver) {
        return get(contentResolver, "max_new_outgoing_call_cancel_ms", 10000L);
    }

    /**
     * Returns the amount of time to play each DTMF tone after post dial continue.
     * This timeout allows the current tone to play for a certain amount of time before either being
     * interrupted by the next tone or terminated.
     */
    public static long getDelayBetweenDtmfTonesMillis(ContentResolver contentResolver) {
        return get(contentResolver, "delay_between_dtmf_tones_ms", 300L);
    }

    /**
     * Returns the amount of time to wait for an emergency call to be placed before routing to
     * a different call service. A value of 0 or less means no timeout should be used.
     */
    public static long getEmergencyCallTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "emergency_call_timeout_millis", 25000L /* 25 seconds */);
    }

    /**
     * Returns the amount of time to wait for an emergency call to be placed before routing to
     * a different call service. This timeout is used only when the radio is powered off (for
     * example in airplane mode). A value of 0 or less means no timeout should be used.
     */
    public static long getEmergencyCallTimeoutRadioOffMillis(ContentResolver contentResolver) {
        return get(contentResolver, "emergency_call_timeout_radio_off_millis",
                60000L /* 1 minute */);
    }

    public static long getCallBindBluetoothInCallServicesDelay(ContentResolver contentResolver) {
        return get(contentResolver, "call_bind_bluetooth_in_call_services_delay",
                2000L /* 2 seconds */);
    }

    /**
     * Returns the amount of delay before unbinding the in-call services after all the calls
     * are removed.
     */
    public static long getCallRemoveUnbindInCallServicesDelay(ContentResolver contentResolver) {
        return get(contentResolver, "call_remove_unbind_in_call_services_delay",
                2000L /* 2 seconds */);
    }

    /**
     * Returns the amount of time for which bluetooth is considered connected after requesting
     * connection. This compensates for the amount of time it takes for the audio route to
     * actually change to bluetooth.
     */
    public static long getBluetoothPendingTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "bluetooth_pending_timeout_millis", 5000L);
    }

    /**
     * Returns the amount of time to wait before retrying the connectAudio call. This is
     * necessary to account for the HeadsetStateMachine sometimes not being ready when we want to
     * connect to bluetooth audio immediately after a device connects.
     */
    public static long getRetryBluetoothConnectAudioBackoffMillis(ContentResolver contentResolver) {
        return get(contentResolver, "retry_bluetooth_connect_audio_backoff_millis", 500L);
    }

    /**
     * Returns the amount of time to wait for the phone account suggestion service to reply.
     */
    public static long getPhoneAccountSuggestionServiceTimeout(ContentResolver contentResolver) {
        return get(contentResolver, "phone_account_suggestion_service_timeout",
                5000L /* 5 seconds */);
    }

    /**
     * Returns the amount of time to wait for the call screening service to allow or disallow a
     * call.
     */
    public static long getCallScreeningTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "call_screening_timeout", 5000L /* 5 seconds */);
    }

    /**
     * Returns the amount of time after an emergency call that incoming calls should be treated
     * as potential emergency callbacks.
     */
    public static long getEmergencyCallbackWindowMillis(ContentResolver contentResolver) {
        return get(contentResolver, "emergency_callback_window_millis",
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    }

    /**
     * Returns the amount of time for an user-defined {@link CallRedirectionService}.
     *
     * @param contentResolver The content resolver.
     */
    public static long getUserDefinedCallRedirectionTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "user_defined_call_redirection_timeout",
                5000L /* 5 seconds */);
    }

    /**
     * Returns the amount of time for a carrier {@link CallRedirectionService}.
     *
     * @param contentResolver The content resolver.
     */
    public static long getCarrierCallRedirectionTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "carrier_call_redirection_timeout", 5000L /* 5 seconds */);
    }

    /**
     * Returns the number of milliseconds between two plays of the call recording tone.
     */
    public static long getCallRecordingToneRepeatIntervalMillis(ContentResolver contentResolver) {
        return get(contentResolver, "call_recording_tone_repeat_interval", 15000L /* 15 seconds */);
    }

    /**
     * Returns the maximum amount of time a {@link CallDiagnosticService} is permitted to take to
     * return back from {@link CallDiagnostics#onCallDisconnected(ImsReasonInfo)} and
     * {@link CallDiagnostics#onCallDisconnected(int, int)}.
     * @param contentResolver The resolver for the config option.
     * @return The timeout in millis.
     */
    public static long getCallDiagnosticServiceTimeoutMillis(ContentResolver contentResolver) {
        return get(contentResolver, "call_diagnostic_service_timeout", 2000L /* 2 sec */);
    }

    /**
     * Returns the duration of time a VoIP call can be in a transitory state before Telecom will
     * try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getVoipCallTransitoryStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                TRANSITORY_STATE_VOIP_NORMAL_TIMEOUT_MILLIS, 5000L);
    }


    /**
     * Returns the threshold used to filter out ecalls that the user may have dialed by mistake
     * It is used only when the disconnect cause is LOCAL by EmergencyDiagnosticLogger
     * @return the threshold in milliseconds
     */
    public static long getEmergencyCallTimeBeforeUserDisconnectThresholdMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                EMERGENCY_CALL_TIME_BEFORE_USER_DISCONNECT_THRESHOLD_MILLIS, 20000L);
    }

    /**
     * Returns the threshold used to detect ecalls that transition to active but only for a very
     * short duration. These short duration active calls can result in Diagnostic data collection.
     * @return the threshold in milliseconds
     */
    public static long getEmergencyCallActiveTimeThresholdMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                EMERGENCY_CALL_ACTIVE_TIME_THRESHOLD_MILLIS, 15000L);
    }

    /**
     * Time in Days that is used to filter out old dropbox entries for emergency call diagnostic
     * data. Entries older than this are ignored
     */
    public static int getDaysBackToSearchEmergencyDiagnosticEntries() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_TELEPHONY,
                DAYS_BACK_TO_SEARCH_EMERGENCY_DROP_BOX_ENTRIES, 30);
    }

    /**
     * Returns the duration of time an emergency VoIP call can be in a transitory state before
     * Telecom will try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getVoipEmergencyCallTransitoryStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                TRANSITORY_STATE_VOIP_EMERGENCY_TIMEOUT_MILLIS, 5000L);
    }

    /**
     * Returns the duration of time a non-VoIP call can be in a transitory state before Telecom
     * will try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getNonVoipCallTransitoryStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                TRANSITORY_STATE_NON_VOIP_NORMAL_TIMEOUT_MILLIS, 10000L);
    }

    /**
     * Returns the duration of time an emergency non-VoIp call can be in a transitory state before
     * Telecom will try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getNonVoipEmergencyCallTransitoryStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                TRANSITORY_STATE_NON_VOIP_EMERGENCY_TIMEOUT_MILLIS, 10000L);
    }

    /**
     * Returns the duration of time a VoIP call can be in an intermediate state before Telecom will
     * try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getVoipCallIntermediateStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                INTERMEDIATE_STATE_VOIP_NORMAL_TIMEOUT_MILLIS, 60000L);
    }

    /**
     * Returns the duration of time an emergency VoIP call can be in an intermediate state before
     * Telecom will try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getVoipEmergencyCallIntermediateStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                INTERMEDIATE_STATE_VOIP_EMERGENCY_TIMEOUT_MILLIS, 60000L);
    }

    /**
     * Returns the duration of time a non-VoIP call can be in an intermediate state before Telecom
     * will try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getNonVoipCallIntermediateStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                INTERMEDIATE_STATE_NON_VOIP_NORMAL_TIMEOUT_MILLIS, 120000L);
    }

    /**
     * Returns the duration of time an emergency non-VoIP call can be in an intermediate state
     * before Telecom will try to clean up the call.
     * @return the state timeout in millis.
     */
    public static long getNonVoipEmergencyCallIntermediateStateTimeoutMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                INTERMEDIATE_STATE_NON_VOIP_EMERGENCY_TIMEOUT_MILLIS, 60000L);
    }

    public static long getCallStartAppOpDebounceIntervalMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_PRIVACY, "app_op_debounce_time", 250L);
    }

    /**
     * Returns the number of milliseconds for which the system should exempt the default dialer from
     * power save restrictions due to the dialer needing to handle a missed call notification
     * (update call log, check VVM, etc...).
     */
    public static long getDialerMissedCallPowerSaveExemptionTimeMillis(
            ContentResolver contentResolver) {
        return get(contentResolver, "dialer_missed_call_power_save_exemption_time_millis",
                30000L /*30 seconds*/);
    }
}
