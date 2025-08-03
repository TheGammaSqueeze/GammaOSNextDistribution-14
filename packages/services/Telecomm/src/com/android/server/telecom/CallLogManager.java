/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import static android.provider.CallLog.AddCallParams.AddCallParametersBuilder.MAX_NUMBER_OF_CHARACTERS;
import static android.provider.CallLog.Calls.BLOCK_REASON_NOT_BLOCKED;
import static android.telephony.CarrierConfigManager.KEY_SUPPORT_IMS_CONFERENCE_EVENT_PACKAGE_BOOL;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Country;
import android.location.CountryDetector;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.UserHandle;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.flags.Flags;

import org.lineageos.lib.phone.SensitivePhoneNumbers;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Helper class that provides functionality to write information about calls and their associated
 * caller details to the call log. All logging activity will be performed asynchronously in a
 * background thread to avoid blocking on the main thread.
 */
@VisibleForTesting
public final class CallLogManager extends CallsManagerListenerBase {

    public interface LogCallCompletedListener {
        void onLogCompleted(@Nullable Uri uri);
    }

    /**
     * Parameter object to hold the arguments to add a call in the call log DB.
     */
    private static class AddCallArgs {
        public AddCallArgs(Context context, CallLog.AddCallParams params,
                @Nullable LogCallCompletedListener logCallCompletedListener,
                @NonNull Call call) {
            this.context = context;
            this.params = params;
            this.logCallCompletedListener = logCallCompletedListener;
            this.call = call;

        }
        // Since the members are accessed directly, we don't use the
        // mXxxx notation.
        public final Context context;
        public final CallLog.AddCallParams params;
        public final Call call;
        @Nullable
        public final LogCallCompletedListener logCallCompletedListener;
    }

    private static final String TAG = CallLogManager.class.getSimpleName();

    // Copied from android.telephony.DisconnectCause.toString
    // TODO: come up with a better way to indicate in a android.telecom.DisconnectCause that
    // a conference was merged successfully
    private static final String REASON_IMS_MERGED_SUCCESSFULLY = "IMS_MERGED_SUCCESSFULLY";
    private static final UUID LOG_CALL_FAILED_ANOMALY_ID =
            UUID.fromString("d9b38771-ff36-417b-8723-2363a870c702");
    private static final String LOG_CALL_FAILED_ANOMALY_DESC =
            "Based on the current user, Telecom detected failure to record a call to the call log.";

    private final Context mContext;
    private final CarrierConfigManager mCarrierConfigManager;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final MissedCallNotifier mMissedCallNotifier;
    private AnomalyReporterAdapter mAnomalyReporterAdapter;
    private static final String ACTION_CALLS_TABLE_ADD_ENTRY =
            "com.android.server.telecom.intent.action.CALLS_ADD_ENTRY";
    private static final String PERMISSION_PROCESS_CALLLOG_INFO =
            "android.permission.PROCESS_CALLLOG_INFO";
    private static final String CALL_TYPE = "callType";
    private static final String CALL_DURATION = "duration";

    private final Object mLock = new Object();
    private Country mCurrentCountry;
    private String mCurrentCountryIso;
    private HandlerExecutor mCountryCodeExecutor;
    private SensitivePhoneNumbers mSensitivePhoneNumbers;

    private final FeatureFlags mFeatureFlags;

    public CallLogManager(Context context, PhoneAccountRegistrar phoneAccountRegistrar,
            MissedCallNotifier missedCallNotifier, AnomalyReporterAdapter anomalyReporterAdapter,
            FeatureFlags featureFlags) {
        mContext = context;
        mCarrierConfigManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mMissedCallNotifier = missedCallNotifier;
        mAnomalyReporterAdapter = anomalyReporterAdapter;
        mCountryCodeExecutor = new HandlerExecutor(new Handler(Looper.getMainLooper()));
        mFeatureFlags = featureFlags;
        mSensitivePhoneNumbers = SensitivePhoneNumbers.getInstance();
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        int disconnectCause = call.getDisconnectCause().getCode();
        boolean isNewlyDisconnected =
                newState == CallState.DISCONNECTED || newState == CallState.ABORTED;
        boolean isCallCanceled = isNewlyDisconnected && disconnectCause == DisconnectCause.CANCELED;

        if (!isNewlyDisconnected) {
            return;
        }

        if (shouldLogDisconnectedCall(call, oldState, isCallCanceled)) {
            int type;
            if (!call.isIncoming()) {
                type = Calls.OUTGOING_TYPE;
            } else if (disconnectCause == DisconnectCause.MISSED) {
                type = Calls.MISSED_TYPE;
            } else if (disconnectCause == DisconnectCause.ANSWERED_ELSEWHERE) {
                type = Calls.ANSWERED_EXTERNALLY_TYPE;
            } else if (disconnectCause == DisconnectCause.REJECTED) {
                type = Calls.REJECTED_TYPE;
            } else {
                type = Calls.INCOMING_TYPE;
            }
            // Always show the notification for managed calls. For self-managed calls, it is up to
            // the app to show the notification, so suppress the notification when logging the call.
            boolean showNotification = !call.isSelfManaged();
            logCall(call, type, showNotification, null /*result*/);
        }
    }

    /**
     * Log newly disconnected calls only if all of below conditions are met:
     * Call was NOT in the "choose account" phase when disconnected
     * Call is NOT a conference call which had children (unless it was remotely hosted).
     * Call is NOT a child call from a conference which was remotely hosted.
     * Call has NOT indicated it should be skipped for logging in its extras
     * Call is NOT simulating a single party conference.
     * Call was NOT explicitly canceled, except for disconnecting from a conference.
     * Call is NOT an external call or an external call on watch.
     * Call is NOT disconnected because of merging into a conference.
     * Call is NOT a self-managed call OR call is a self-managed call which has indicated it
     * should be logged in its PhoneAccount
     */
    @VisibleForTesting
    public boolean shouldLogDisconnectedCall(Call call, int oldState, boolean isCallCanceled) {
        boolean shouldCallSelfManagedLogged = call.isLoggedSelfManaged()
                && (call.getHandoverState() == HandoverState.HANDOVER_NONE
                || call.getHandoverState() == HandoverState.HANDOVER_COMPLETE);

        // "Choose account" phase when disconnected
        if (oldState == CallState.SELECT_PHONE_ACCOUNT) {
            return false;
        }
        // A conference call which had children should not be logged, unless it was remotely hosted.
        if (call.isConference() && call.hadChildren() &&
                !call.hasProperty(Connection.PROPERTY_REMOTELY_HOSTED)) {
            return false;
        }

        // A conference call which had no children should not be logged; this case will occur on IMS
        // when no conference event package data is received.  We will have logged the participants
        // as they merge into the conference, so we should not log the conference itself.
        if (call.isConference() && !call.hadChildren() &&
                !call.hasProperty(Connection.PROPERTY_REMOTELY_HOSTED)) {
            return false;
        }

        if (mFeatureFlags.telecomSkipLogBasedOnExtra() && call.getExtras() != null
                && call.getExtras().containsKey(TelecomManager.EXTRA_DO_NOT_LOG_CALL)) {
            return false;
        }

        // A child call of a conference which was remotely hosted; these didn't originate on this
        // device and should not be logged.
        if (call.getParentCall() != null && call.hasProperty(Connection.PROPERTY_REMOTELY_HOSTED)) {
            return false;
        }

        DisconnectCause cause = call.getDisconnectCause();
        if (isCallCanceled) {
            // No log when disconnecting to simulate a single party conference.
            if (cause != null
                    && DisconnectCause.REASON_EMULATING_SINGLE_CALL.equals(cause.getReason())) {
                return false;
            }
            // Explicitly canceled
            // Conference children connections only have CAPABILITY_DISCONNECT_FROM_CONFERENCE.
            // Log them when they are disconnected from conference.
            return (call.getConnectionCapabilities()
                    & Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
                    == Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE;
        }
        // An external and non-watch call
        if (call.isExternalCall() && (!mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH)
                || !mFeatureFlags.telecomLogExternalWearableCalls())) {
            return false;
        }

        // Call merged into conferences and marked with IMS_MERGED_SUCCESSFULLY.
        // Return false if the conference supports the participants packets for the carrier.
        // Otherwise, fall through. Merged calls would be associated with disconnected
        // connections because of special carrier requirements. Those calls don't look like
        // merged, e.g. could be one active and the other on hold.
        if (cause != null && REASON_IMS_MERGED_SUCCESSFULLY.equals(cause.getReason())) {
            int subscriptionId = mPhoneAccountRegistrar
                    .getSubscriptionIdForPhoneAccount(call.getTargetPhoneAccount());
            // By default, the conference should return a list of participants.
            if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return false;
            }

            PersistableBundle b = mCarrierConfigManager.getConfigForSubId(subscriptionId);
            if (b == null) {
                return false;
            }

            if (b.getBoolean(KEY_SUPPORT_IMS_CONFERENCE_EVENT_PACKAGE_BOOL, true)) {
                return false;
            }
        }

        // Call is NOT a self-managed call OR call is a self-managed call which has indicated it
        // should be logged in its PhoneAccount
        return !call.isSelfManaged() || shouldCallSelfManagedLogged;
    }

    void logCall(Call call, int type, boolean showNotificationForMissedCall, CallFilteringResult
            result) {
        if ((type == Calls.MISSED_TYPE || type == Calls.BLOCKED_TYPE) &&
                showNotificationForMissedCall) {
            logCall(call, type, new LogCallCompletedListener() {
                @Override
                public void onLogCompleted(@Nullable Uri uri) {
                    if (mFeatureFlags.addCallUriForMissedCalls()){
                        mMissedCallNotifier.showMissedCallNotification(
                                new MissedCallNotifier.CallInfo(call), uri);
                    } else {
                        mMissedCallNotifier.showMissedCallNotification(
                                new MissedCallNotifier.CallInfo(call), /* uri= */ null);
                    }
                }
            }, result);
        } else {
            logCall(call, type, null, result);
        }
    }

    /**
     * Logs a call to the call log based on the {@link Call} object passed in.
     *
     * @param call The call object being logged
     * @param callLogType The type of call log entry to log this call as. See:
     *     {@link android.provider.CallLog.Calls#INCOMING_TYPE}
     *     {@link android.provider.CallLog.Calls#OUTGOING_TYPE}
     *     {@link android.provider.CallLog.Calls#MISSED_TYPE}
     *     {@link android.provider.CallLog.Calls#BLOCKED_TYPE}
     * @param logCallCompletedListener optional callback called after the call is logged.
     * @param result is generated when call type is
     *     {@link android.provider.CallLog.Calls#BLOCKED_TYPE}.
     */
    void logCall(Call call, int callLogType,
            @Nullable LogCallCompletedListener logCallCompletedListener, CallFilteringResult result) {

        CallLog.AddCallParams.AddCallParametersBuilder paramBuilder =
                new CallLog.AddCallParams.AddCallParametersBuilder();
        if (call.getConnectTimeMillis() != 0
                && call.getConnectTimeMillis() < call.getCreationTimeMillis()) {
            // If connected time is available, use connected time. The connected time might be
            // earlier than created time since it might come from carrier sent special SMS to
            // notifier user earlier missed call.
            paramBuilder.setStart(call.getConnectTimeMillis());
        } else {
            paramBuilder.setStart(call.getCreationTimeMillis());
        }

        paramBuilder.setDuration((int) (call.getAgeMillis() / 1000));

        String logNumber = getLogNumber(call);
        paramBuilder.setNumber(logNumber);

        Log.d(TAG, "logNumber set to: %s", Log.pii(logNumber));

        String formattedViaNumber = PhoneNumberUtils.formatNumber(call.getViaNumber(),
                getCountryIso());
        formattedViaNumber = (formattedViaNumber != null) ?
                formattedViaNumber : call.getViaNumber();
        paramBuilder.setViaNumber(formattedViaNumber);

        final PhoneAccountHandle emergencyAccountHandle =
                TelephonyUtil.getDefaultEmergencyPhoneAccount().getAccountHandle();
        PhoneAccountHandle accountHandle = call.getTargetPhoneAccount();
        if (emergencyAccountHandle.equals(accountHandle)) {
            accountHandle = null;
        }
        paramBuilder.setAccountHandle(accountHandle);

        paramBuilder.setDataUsage(call.getCallDataUsage() == Call.DATA_USAGE_NOT_SET
                ? Long.MIN_VALUE : call.getCallDataUsage());

        paramBuilder.setFeatures(getCallFeatures(call.getVideoStateHistory(),
                call.getDisconnectCause().getCode() == DisconnectCause.CALL_PULLED,
                call.wasHighDefAudio(), call.wasWifi(),
                (call.getConnectionProperties() & Connection.PROPERTY_ASSISTED_DIALING) ==
                        Connection.PROPERTY_ASSISTED_DIALING,
                call.wasEverRttCall(),
                call.wasVolte()));

        if (result == null) {
            result = new CallFilteringResult.Builder()
                    .setCallScreeningAppName(call.getCallScreeningAppName())
                    .setCallScreeningComponentName(call.getCallScreeningComponentName())
                    .build();
        }
        if (callLogType == Calls.BLOCKED_TYPE || callLogType == Calls.MISSED_TYPE) {
            paramBuilder.setCallBlockReason(result.mCallBlockReason);
            paramBuilder.setCallScreeningComponentName(result.mCallScreeningComponentName);
            paramBuilder.setCallScreeningAppName(result.mCallScreeningAppName);
        } else {
            paramBuilder.setCallBlockReason(BLOCK_REASON_NOT_BLOCKED);
        }

        PhoneAccount phoneAccount = mPhoneAccountRegistrar.getPhoneAccountUnchecked(accountHandle);
        UserHandle initiatingUser = call.getAssociatedUser();
        if (phoneAccount != null &&
                phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_MULTI_USER)) {
            if (initiatingUser != null &&
                    UserUtil.isManagedProfile(mContext, initiatingUser, mFeatureFlags)) {
                paramBuilder.setUserToBeInsertedTo(initiatingUser);
                paramBuilder.setAddForAllUsers(false);
            } else {
                paramBuilder.setAddForAllUsers(true);
            }
        } else {
            if (accountHandle == null) {
                paramBuilder.setAddForAllUsers(true);
            } else {
                paramBuilder.setUserToBeInsertedTo(accountHandle.getUserHandle());
                paramBuilder.setAddForAllUsers(accountHandle.getUserHandle() == null);
            }
        }
        if (call.getIntentExtras() != null) {
            if (call.getIntentExtras().containsKey(TelecomManager.EXTRA_PRIORITY)) {
                paramBuilder.setPriority(call.getIntentExtras()
                        .getInt(TelecomManager.EXTRA_PRIORITY));
            }
            if (call.getIntentExtras().containsKey(TelecomManager.EXTRA_CALL_SUBJECT)) {
                paramBuilder.setSubject(call.getIntentExtras()
                        .getString(TelecomManager.EXTRA_CALL_SUBJECT));
            }
            if (call.getIntentExtras().containsKey(TelecomManager.EXTRA_PICTURE_URI)) {
                paramBuilder.setPictureUri(call.getIntentExtras()
                        .getParcelable(TelecomManager.EXTRA_PICTURE_URI));
            }
            // The picture uri can end up either in extras or in intent extras due to how these
            // two bundles are set. For incoming calls they're in extras, but for outgoing calls
            // they're in intentExtras.
            if (call.getExtras() != null
                    && call.getExtras().containsKey(TelecomManager.EXTRA_PICTURE_URI)) {
                paramBuilder.setPictureUri(call.getExtras()
                        .getParcelable(TelecomManager.EXTRA_PICTURE_URI));
            }
            if (call.getIntentExtras().containsKey(TelecomManager.EXTRA_LOCATION)) {
                Location l = call.getIntentExtras().getParcelable(TelecomManager.EXTRA_LOCATION);
                if (l != null) {
                    paramBuilder.setLatitude(l.getLatitude());
                    paramBuilder.setLongitude(l.getLongitude());
                }
            }
        }

        paramBuilder.setCallerInfo(call.getCallerInfo());
        paramBuilder.setPostDialDigits(call.getPostDialDigits());
        paramBuilder.setPresentation(call.getHandlePresentation());
        paramBuilder.setCallType(callLogType);
        paramBuilder.setIsRead(call.isSelfManaged());
        paramBuilder.setMissedReason(call.getMissedReason());
        if (mFeatureFlags.businessCallComposer() && call.getExtras() != null) {
            Bundle extras = call.getExtras();
            boolean isBusinessCall =
                    extras.getBoolean(android.telecom.Call.EXTRA_IS_BUSINESS_CALL, false);
            paramBuilder.setIsBusinessCall(isBusinessCall);
            if (isBusinessCall) {
                Log.i(TAG, "logging business call");
                String assertedDisplayName =
                        extras.getString(android.telecom.Call.EXTRA_ASSERTED_DISPLAY_NAME, "");
                if (assertedDisplayName.length() > MAX_NUMBER_OF_CHARACTERS) {
                    // avoid throwing an IllegalArgumentException and only log the first 256
                    // characters of the name.
                    paramBuilder.setAssertedDisplayName(
                            assertedDisplayName.substring(0, MAX_NUMBER_OF_CHARACTERS));
                } else {
                    paramBuilder.setAssertedDisplayName(assertedDisplayName);
                }
            }
        }
        sendAddCallBroadcast(callLogType, call.getAgeMillis());

        boolean okayToLog =
                okayToLogCall(accountHandle, logNumber, call.isEmergencyCall());
        if (okayToLog) {
            AddCallArgs args = new AddCallArgs(mContext, paramBuilder.build(),
                    logCallCompletedListener, call);
            Log.addEvent(call, LogUtils.Events.LOG_CALL, "number=" + Log.piiHandle(logNumber)
                    + ",postDial=" + Log.piiHandle(call.getPostDialDigits()) + ",pres="
                    + call.getHandlePresentation());
            logCallAsync(args);
        } else {
            Log.addEvent(call, LogUtils.Events.SKIP_CALL_LOG);
        }
    }

    boolean okayToLogCall(PhoneAccountHandle accountHandle, String number, boolean isEmergency) {
        // On some devices, to avoid accidental redialing of emergency numbers, we *never* log
        // emergency calls to the Call Log.  (This behavior is set on a per-product basis, based
        // on carrier requirements.)
        boolean okToLogEmergencyNumber = false;
        CarrierConfigManager configManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        int subId = mPhoneAccountRegistrar.getSubscriptionIdForPhoneAccount(accountHandle);
        PersistableBundle configBundle = configManager.getConfigForSubId(subId);
        if (configBundle != null) {
            okToLogEmergencyNumber = configBundle.getBoolean(
                    CarrierConfigManager.KEY_ALLOW_EMERGENCY_NUMBERS_IN_CALL_LOG_BOOL);
        }

        // Don't log sensitive numbers.
        boolean isSensitiveNumber = mSensitivePhoneNumbers.isSensitiveNumber(mContext, number,
                subId);

        // Don't log emergency numbers if the device doesn't allow it.
        return (!isEmergency || okToLogEmergencyNumber)
                && !isUnloggableNumber(number, configBundle) && !isSensitiveNumber;
    }

    private boolean isUnloggableNumber(String callNumber, PersistableBundle carrierConfig) {
        String normalizedNumber = PhoneNumberUtils.normalizeNumber(callNumber);
        String[] unloggableNumbersFromCarrierConfig = carrierConfig == null ? null
                : carrierConfig.getStringArray(
                        CarrierConfigManager.KEY_UNLOGGABLE_NUMBERS_STRING_ARRAY);
        String[] unloggableNumbersFromMccConfig = mContext.getResources()
                .getStringArray(com.android.internal.R.array.unloggable_phone_numbers);
        return Stream.concat(
                unloggableNumbersFromCarrierConfig == null ?
                        Stream.empty() : Arrays.stream(unloggableNumbersFromCarrierConfig),
                unloggableNumbersFromMccConfig == null ?
                        Stream.empty() : Arrays.stream(unloggableNumbersFromMccConfig)
        ).anyMatch(unloggableNumber -> Objects.equals(unloggableNumber, normalizedNumber));
    }

    /**
     * Based on the video state of the call, determines the call features applicable for the call.
     *
     * @param videoState The video state.
     * @param isPulledCall {@code true} if this call was pulled to another device.
     * @param isStoreHd {@code true} if this call was used HD.
     * @param isWifi {@code true} if this call was used wifi.
     * @param isUsingAssistedDialing {@code true} if this call used assisted dialing.
     * @return The call features.
     */
    private static int getCallFeatures(int videoState, boolean isPulledCall, boolean isStoreHd,
            boolean isWifi, boolean isUsingAssistedDialing, boolean isRtt, boolean isVolte) {
        int features = 0;
        if (VideoProfile.isVideo(videoState)) {
            features |= Calls.FEATURES_VIDEO;
        }
        if (isPulledCall) {
            features |= Calls.FEATURES_PULLED_EXTERNALLY;
        }
        if (isStoreHd) {
            features |= Calls.FEATURES_HD_CALL;
        }
        if (isWifi) {
            features |= Calls.FEATURES_WIFI;
        }
        if (isUsingAssistedDialing) {
            features |= Calls.FEATURES_ASSISTED_DIALING_USED;
        }
        if (isRtt) {
            features |= Calls.FEATURES_RTT;
        }
        if (isVolte) {
            features |= Calls.FEATURES_VOLTE;
        }
        return features;
    }

    /**
     * Retrieve the phone number from the call, and then process it before returning the
     * actual number that is to be logged.
     *
     * @param call The phone connection.
     * @return the phone number to be logged.
     */
    private String getLogNumber(Call call) {
        Uri handle = call.getOriginalHandle();

        if (handle == null) {
            return null;
        }

        String handleString = handle.getSchemeSpecificPart();
        if (!PhoneNumberUtils.isUriNumber(handleString)) {
            handleString = PhoneNumberUtils.stripSeparators(handleString);
        }
        return handleString;
    }

    /**
     * Adds the call defined by the parameters in the provided AddCallArgs to the CallLogProvider
     * using an AsyncTask to avoid blocking the main thread.
     *
     * @param args Prepopulated call details.
     * @return A handle to the AsyncTask that will add the call to the call log asynchronously.
     */
    public AsyncTask<AddCallArgs, Void, Uri[]> logCallAsync(AddCallArgs args) {
        return new LogCallAsyncTask().execute(args);
    }

    /**
     * Helper AsyncTask to access the call logs database asynchronously since database operations
     * can take a long time depending on the system's load. Since it extends AsyncTask, it uses
     * its own thread pool.
     */
    private class LogCallAsyncTask extends AsyncTask<AddCallArgs, Void, Uri[]> {

        private LogCallCompletedListener[] mListeners;

        @Override
        protected Uri[] doInBackground(AddCallArgs... callList) {
            int count = callList.length;
            Uri[] result = new Uri[count];
            mListeners = new LogCallCompletedListener[count];
            for (int i = 0; i < count; i++) {
                AddCallArgs c = callList[i];
                mListeners[i] = c.logCallCompletedListener;
                try {
                    Pair<Integer, Integer> startStats = getCallLogStats(c.call);
                    Log.i(TAG, "LogCall; about to log callId=%s, "
                                    + "startCount=%d, startMaxId=%d",
                            c.call.getId(), startStats.first, startStats.second);

                    result[i] = Calls.addCall(c.context, c.params);
                    Pair<Integer, Integer> endStats = getCallLogStats(c.call);
                    Log.i(TAG, "LogCall; logged callId=%s, uri=%s, "
                                    + "endCount=%d, endMaxId=%s",
                            c.call.getId(), result, endStats.first, endStats.second);
                    if ((endStats.second - startStats.second) <= 0) {
                        // No call was added or even worse we lost a call in the log.  Trigger an
                        // anomaly report.  Note: it technically possible that an app modified the
                        // call log while we were writing to it here; that is pretty unlikely, and
                        // the goal here is to try and identify potential anomalous conditions with
                        // logging calls.
                        mAnomalyReporterAdapter.reportAnomaly(LOG_CALL_FAILED_ANOMALY_ID,
                                LOG_CALL_FAILED_ANOMALY_DESC);
                    }
                } catch (Exception e) {
                    // This is very rare but may happen in legitimate cases.
                    // E.g. If the phone is encrypted and thus write request fails, it may cause
                    // some kind of Exception (right now it is IllegalArgumentException, but this
                    // might change).
                    //
                    // We don't want to crash the whole process just because of that, so just log
                    // it instead.
                    Log.e(TAG, e, "LogCall: Exception raised adding callId=%s", c.call.getId());
                    result[i] = null;
                    mAnomalyReporterAdapter.reportAnomaly(LOG_CALL_FAILED_ANOMALY_ID,
                            LOG_CALL_FAILED_ANOMALY_DESC);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(Uri[] result) {
            for (int i = 0; i < result.length; i++) {
                Uri uri = result[i];
                /*
                 Performs a simple correctness check to make sure the call was written in the
                 database.
                 Typically there is only one result per call so it is easy to identify which one
                 failed.
                 */
                if (uri == null) {
                    Log.w(TAG, "Failed to write call to the log.");
                }
                if (mListeners[i] != null) {
                    mListeners[i].onLogCompleted(uri);
                }
            }
        }
    }

    private void sendAddCallBroadcast(int callType, long duration) {
        Intent callAddIntent = new Intent(ACTION_CALLS_TABLE_ADD_ENTRY);
        callAddIntent.putExtra(CALL_TYPE, callType);
        callAddIntent.putExtra(CALL_DURATION, duration);
        mContext.sendBroadcastAsUser(callAddIntent, UserHandle.SYSTEM,
                PERMISSION_PROCESS_CALLLOG_INFO);
    }

    private String getCountryIsoFromCountry(Country country) {
        if(country == null) {
            // Fallback to Locale if there are issues with CountryDetector
            Log.w(TAG, "Value for country was null. Falling back to Locale.");
            return Locale.getDefault().getCountry();
        }

        return country.getCountryCode();
    }

    /**
     * Get the current country code
     *
     * @return the ISO 3166-1 two letters country code of current country.
     */
    public String getCountryIso() {
        synchronized (mLock) {
            if (mCurrentCountryIso == null) {
                // Moving this into the constructor will pose issues if the service is not yet set
                // up, causing a RemoteException to be thrown. Note that the callback is only
                // registered if the country iso cache is null (so in an ideal setting, this should
                // only require a one-time configuration).
                final CountryDetector countryDetector =
                        (CountryDetector) mContext.getSystemService(Context.COUNTRY_DETECTOR);
                if (countryDetector != null) {
                    countryDetector.registerCountryDetectorCallback(
                            mCountryCodeExecutor, this::countryCodeConsumer);
                }
                mCurrentCountryIso = getCountryIsoFromCountry(mCurrentCountry);
            }
            return mCurrentCountryIso;
        }
    }

    /** Consumer to receive the country code if it changes. */
    private void countryCodeConsumer(Country newCountry) {
        Log.startSession("CLM.cCC");
        try {
            Log.i(TAG, "Country ISO changed. Retrieving new ISO...");
            synchronized (mLock) {
                mCurrentCountry = newCountry;
                mCurrentCountryIso = getCountryIsoFromCountry(newCountry);
            }
        } finally {
            Log.endSession();
        }
    }

    /**
     * Returns a pair containing the number of rows in the call log, as well as the maximum call log
     * ID.  There is a limit of 500 entries in the call log for a phone account, so once we hit 500
     * we can reasonably expect that number to not change before and after logging a call.
     * We determine the maximum ID in the call log since this is a way we can objectively check if
     * the provider did record a call log entry or not.  Ideally there should be more call log
     * entries after logging than before, and certainly not less.
     * @return pair with number of rows in the call log and max id.
     */
    private Pair<Integer, Integer> getCallLogStats(@NonNull Call call) {
        try {
            // Ensure we query the call log based on the current user.
            final Context currentUserContext = mContext.createContextAsUser(
                    call.getAssociatedUser(), /* flags= */ 0);
            final ContentResolver currentUserResolver = currentUserContext.getContentResolver();
            final UserManager userManager = currentUserContext.getSystemService(UserManager.class);
            final int currentUserId = userManager.getProcessUserId();

            // Use shadow provider based on current user unlock state.
            Uri providerUri;
            boolean isCurrentUserUnlocked = mFeatureFlags.telecomResolveHiddenDependencies()
                    ? userManager.isUserUnlocked(UserHandle.CURRENT)
                    : userManager.isUserUnlocked(currentUserId);
            if (isCurrentUserUnlocked) {
                providerUri = Calls.CONTENT_URI;
            } else {
                providerUri = Calls.SHADOW_CONTENT_URI;
            }
            int maxCallId = -1;
            int numFound;
            try (Cursor countCursor = currentUserResolver.query(providerUri,
                    new String[]{Calls._ID},
                    null,
                    null,
                    Calls._ID + " DESC")) {
                numFound = countCursor.getCount();
                if (numFound > 0) {
                    countCursor.moveToFirst();
                    maxCallId = countCursor.getInt(0);
                }
            }
            return new Pair<>(numFound, maxCallId);
        } catch (Exception e) {
            // Oh jeepers, we crashed getting the call count.
            Log.e(TAG, e, "getCountOfCallLogRows: failed");
            return new Pair<>(-1, -1);
        }
    }

    @VisibleForTesting
    public void setAnomalyReporterAdapter(AnomalyReporterAdapter anomalyReporterAdapter){
        mAnomalyReporterAdapter = anomalyReporterAdapter;
    }
}
