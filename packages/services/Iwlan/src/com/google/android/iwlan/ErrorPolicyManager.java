/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import android.content.Context;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.DataFailCause;
import android.telephony.TelephonyManager;
import android.telephony.data.DataService;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ErrorPolicyManager {

    /**
     * This type is not to be used in config. This is only used internally to catch errors in
     * parsing the error type.
     */
    private static final int UNKNOWN_ERROR_TYPE = -1;

    /**
     * This value represents that the error tye is to be used as a fallback to represent all the
     * errors.
     */
    private static final int FALLBACK_ERROR_TYPE = 1;

    /**
     * This value represents rest of the errors that are not defined above. ErrorDetails should
     * mention the specific error. If it doesn't - the policy will be used as a fallback global
     * policy. Currently, Supported ErrorDetails "IO_EXCEPTION" "TIMEOUT_EXCEPTION"
     * "SERVER_SELECTION_FAILED" "TUNNEL_TRANSFORM_FAILED"
     */
    private static final int GENERIC_ERROR_TYPE = 2;

    /**
     * This value represents IKE Protocol Error/Notify Error.
     *
     * @see <a href="https://tools.ietf.org/html/rfc4306#section-3.10.1">RFC 4306,Internet Key
     *     Exchange (IKEv2) Protocol </a> for global errors and carrier specific requirements for
     *     other carrier specific error codes. ErrorDetails defined for this type is always in
     *     numeric form representing the error codes. Examples: "24", "9000-9050"
     */
    private static final int IKE_PROTOCOL_ERROR_TYPE = 3;

    static ErrorPolicy.Builder builder() {
        return new AutoValue_ErrorPolicyManager_ErrorPolicy.Builder()
                .setInfiniteRetriesWithLastRetryTime(false);
    }

    @IntDef({UNKNOWN_ERROR_TYPE, FALLBACK_ERROR_TYPE, GENERIC_ERROR_TYPE, IKE_PROTOCOL_ERROR_TYPE})
    @interface ErrorPolicyErrorType {}

    private static final String[] GENERIC_ERROR_DETAIL_STRINGS = {
        "*",
        "IO_EXCEPTION",
        "TIMEOUT_EXCEPTION",
        "SERVER_SELECTION_FAILED",
        "TUNNEL_TRANSFORM_FAILED"
    };

    /** Private IKEv2 notify message types. As defined in TS 124 302 (section 8.1.2.2) */
    private static final int IKE_PROTOCOL_ERROR_PDN_CONNECTION_REJECTION = 8192;

    private static final int IKE_PROTOCOL_ERROR_MAX_CONNECTION_REACHED = 8193;
    private static final int IKE_PROTOCOL_ERROR_SEMANTIC_ERROR_IN_THE_TFT_OPERATION = 8241;
    private static final int IKE_PROTOCOL_ERROR_SYNTACTICAL_ERROR_IN_THE_TFT_OPERATION = 8242;
    private static final int IKE_PROTOCOL_ERROR_SEMANTIC_ERRORS_IN_PACKET_FILTERS = 8244;
    private static final int IKE_PROTOCOL_ERROR_SYNTACTICAL_ERRORS_IN_PACKET_FILTERS = 8245;
    private static final int IKE_PROTOCOL_ERROR_NON_3GPP_ACCESS_TO_EPC_NOT_ALLOWED = 9000;
    private static final int IKE_PROTOCOL_ERROR_USER_UNKNOWN = 9001;
    private static final int IKE_PROTOCOL_ERROR_NO_APN_SUBSCRIPTION = 9002;
    private static final int IKE_PROTOCOL_ERROR_AUTHORIZATION_REJECTED = 9003;
    private static final int IKE_PROTOCOL_ERROR_ILLEGAL_ME = 9006;
    private static final int IKE_PROTOCOL_ERROR_NETWORK_FAILURE = 10500;
    private static final int IKE_PROTOCOL_ERROR_RAT_TYPE_NOT_ALLOWED = 11001;
    private static final int IKE_PROTOCOL_ERROR_IMEI_NOT_ACCEPTED = 11005;
    private static final int IKE_PROTOCOL_ERROR_PLMN_NOT_ALLOWED = 11011;
    private static final int IKE_PROTOCOL_ERROR_UNAUTHENTICATED_EMERGENCY_NOT_SUPPORTED = 11055;

    /** Private IKEv2 notify message types, as defined in TS 124 502 (section 9.2.4.1) */
    private static final int IKE_PROTOCOL_ERROR_CONGESTION = 15500;

    private static final int IWLAN_NO_ERROR_RETRY_TIME = -1;

    private static final ErrorPolicy FALLBACK_ERROR_POLICY =
            builder()
                    .setErrorType(FALLBACK_ERROR_TYPE)
                    .setRetryArray(List.of(5, -1))
                    .setErrorDetails(List.of("*"))
                    .setUnthrottlingEvents(List.of())
                    .build();

    private final String LOG_TAG;

    private static final Map<Integer, ErrorPolicyManager> mInstances = new ConcurrentHashMap<>();
    private final Context mContext;
    private final int mSlotId;

    // Policies read from defaultiwlanerrorconfig.json
    // String APN as key to identify the ErrorPolicies associated with it.
    private final Map<String, List<ErrorPolicy>> mDefaultPolicies = new HashMap<>();

    // Policies read from CarrierConfig
    // String APN as key to identify the ErrorPolicies associated with it.
    private final Map<String, List<ErrorPolicy>> mCarrierConfigPolicies = new HashMap<>();

    /** String APN as key to identify the {@link ApnRetryActionStore} associated with that APN */
    private final Map<String, ApnRetryActionStore> mRetryActionStoreByApn =
            new ConcurrentHashMap<>();

    // Records the most recently reported IwlanError (including NO_ERROR), and the corresponding
    // APN.
    private ApnWithIwlanError mMostRecentError;

    // List of current Unthrottling events registered with IwlanEventListener
    private Set<Integer> mUnthrottlingEvents;

    private final ErrorStats mErrorStats = new ErrorStats();

    private HandlerThread mHandlerThread;
    @VisibleForTesting Handler mHandler;

    private int carrierId = TelephonyManager.UNKNOWN_CARRIER_ID;

    private String mCarrierConfigErrorPolicyString;

    @VisibleForTesting
    static final String KEY_ERROR_POLICY_CONFIG_STRING = "iwlan.key_error_policy_config_string";

    /**
     * Returns ErrorPolicyManager instance for the subId
     *
     * @param context
     * @param slotId
     */
    public static ErrorPolicyManager getInstance(@NonNull Context context, int slotId) {
        return mInstances.computeIfAbsent(slotId, k -> new ErrorPolicyManager(context, slotId));
    }

    @VisibleForTesting
    public static void resetAllInstances() {
        mInstances.clear();
    }

    /**
     * Release or reset the instance.
     */
    public void releaseInstance() {
        Log.d(LOG_TAG, "Release Instance with slotId: " + mSlotId);
        IwlanEventListener.getInstance(mContext, mSlotId).removeEventListener(mHandler);
        mHandlerThread.quit();
        mInstances.remove(mSlotId);
    }

    /**
     * Updates the last error details and returns the retry time. Return value is -1, which should
     * be ignored, when the error is IwlanError.NO_ERROR.
     *
     * @param apn apn name for which the error happened
     * @param iwlanError Error
     * @return retry time. 0 = immediate retry, -1 = fail and n = retry after n seconds
     */
    public synchronized long reportIwlanError(String apn, IwlanError iwlanError) {
        // Fail by default
        mMostRecentError = new ApnWithIwlanError(apn, iwlanError);

        if (iwlanError.getErrorType() == IwlanError.NO_ERROR) {
            Log.d(LOG_TAG, "reportIwlanError: NO_ERROR");
            mRetryActionStoreByApn.remove(apn);
            return IWLAN_NO_ERROR_RETRY_TIME;
        }
        mErrorStats.update(apn, iwlanError);

        PolicyDerivedRetryAction newRetryAction =
                mRetryActionStoreByApn
                        .computeIfAbsent(apn, ApnRetryActionStore::new)
                        .generateRetryAction(iwlanError);

        Log.d(
                LOG_TAG,
                "Current RetryAction index: "
                        + newRetryAction.currentRetryIndex()
                        + " and time: "
                        + newRetryAction.totalRetryTimeMs());
        return newRetryAction.totalRetryTimeMs() / 1000;
    }

    /**
     * Updates the last error details with backoff time.
     *
     * @param apn apn name for which the error happened
     * @param iwlanError Error
     * @param backoffTime in seconds
     * @return retry time which is the backoff time. -1 if it is {@link IwlanError#NO_ERROR}
     */
    public synchronized long reportIwlanError(String apn, IwlanError iwlanError, long backoffTime) {
        // Fail by default
        if (iwlanError.getErrorType() == IwlanError.NO_ERROR) {
            Log.d(LOG_TAG, "reportIwlanError: NO_ERROR");
            mRetryActionStoreByApn.remove(apn);
            return IWLAN_NO_ERROR_RETRY_TIME;
        }
        mErrorStats.update(apn, iwlanError);

        IkeBackoffNotifyRetryAction newRetryAction =
                mRetryActionStoreByApn
                        .computeIfAbsent(apn, ApnRetryActionStore::new)
                        .generateRetryAction(iwlanError, backoffTime);
        Log.d(LOG_TAG, "Current configured backoff time: " + newRetryAction.backoffTime());

        return newRetryAction.backoffTime();
    }

    /**
     * Checks whether we can bring up Epdg Tunnel - Based on lastErrorForApn
     *
     * @param apn apn for which tunnel bring up needs to be checked
     * @return true if tunnel can be brought up, false otherwise
     */
    public synchronized boolean canBringUpTunnel(String apn) {
        RetryAction lastRetryAction = getLastRetryAction(apn);
        boolean canBringUp =
                lastRetryAction == null || getRemainingRetryTimeMs(lastRetryAction) <= 0;
        Log.d(LOG_TAG, "canBringUpTunnel: " + canBringUp);
        return canBringUp;
    }

    // TODO: Modify framework/base/Android.bp to get access to Annotation.java to use
    // @DataFailureCause
    // annotation as return type here. (after moving to aosp?)
    /**
     * Returns the DataFailCause based on the lastErrorForApn
     *
     * @param apn apn name for which DataFailCause is needed
     * @return DataFailCause corresponding to the error for the apn
     */
    public synchronized int getDataFailCause(String apn) {
        RetryAction lastRetryAction = getLastRetryAction(apn);
        return lastRetryAction == null
                ? DataFailCause.NONE
                : getDataFailCause(lastRetryAction.error());
    }

    private int getDataFailCause(IwlanError error) {
        int ret = DataFailCause.ERROR_UNSPECIFIED;

        if (error.getErrorType() == IwlanError.NO_ERROR) {
            ret = DataFailCause.NONE;
        } else if (error.getErrorType() == IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED) {
            ret = DataFailCause.IWLAN_DNS_RESOLUTION_NAME_FAILURE;
        } else if (error.getErrorType() == IwlanError.EPDG_ADDRESS_ONLY_IPV4_ALLOWED) {
            ret = DataFailCause.ONLY_IPV4_ALLOWED;
        } else if (error.getErrorType() == IwlanError.EPDG_ADDRESS_ONLY_IPV6_ALLOWED) {
            ret = DataFailCause.ONLY_IPV6_ALLOWED;
        } else if (error.getErrorType() == IwlanError.IKE_INTERNAL_IO_EXCEPTION) {
            ret = DataFailCause.IWLAN_IKEV2_MSG_TIMEOUT;
        } else if (error.getErrorType() == IwlanError.SIM_NOT_READY_EXCEPTION) {
            ret = DataFailCause.SIM_CARD_CHANGED;
        } else if (error.getErrorType()
                == IwlanError.IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED) {
            ret = DataFailCause.IWLAN_IKE_SESSION_CLOSED_BEFORE_CHILD_SESSION_OPENED;
        } else if (error.getErrorType() == IwlanError.TUNNEL_NOT_FOUND) {
            ret = DataFailCause.IWLAN_TUNNEL_NOT_FOUND;
        } else if (error.getErrorType() == IwlanError.IKE_INIT_TIMEOUT) {
            ret = DataFailCause.IWLAN_IKE_INIT_TIMEOUT;
        } else if (error.getErrorType() == IwlanError.IKE_MOBILITY_TIMEOUT) {
            ret = DataFailCause.IWLAN_IKE_MOBILITY_TIMEOUT;
        } else if (error.getErrorType() == IwlanError.IKE_DPD_TIMEOUT) {
            ret = DataFailCause.IWLAN_IKE_DPD_TIMEOUT;
        } else if (error.getErrorType() == IwlanError.TUNNEL_TRANSFORM_FAILED) {
            ret = DataFailCause.IWLAN_TUNNEL_TRANSFORM_FAILED;
        } else if (error.getErrorType() == IwlanError.IKE_NETWORK_LOST_EXCEPTION) {
            ret = DataFailCause.IWLAN_IKE_NETWORK_LOST_EXCEPTION;
        } else if (error.getErrorType() == IwlanError.IKE_PROTOCOL_EXCEPTION) {
            Exception exception = error.getException();
            if (exception instanceof IkeProtocolException) {
                int protocolErrorType = ((IkeProtocolException) exception).getErrorType();
                switch (protocolErrorType) {
                    case IkeProtocolException.ERROR_TYPE_AUTHENTICATION_FAILED:
                        ret = DataFailCause.IWLAN_IKEV2_AUTH_FAILURE;
                        break;
                    case IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE:
                        ret = DataFailCause.IWLAN_EPDG_INTERNAL_ADDRESS_FAILURE;
                        break;
                    case IKE_PROTOCOL_ERROR_PDN_CONNECTION_REJECTION:
                        ret = DataFailCause.IWLAN_PDN_CONNECTION_REJECTION;
                        break;
                    case IKE_PROTOCOL_ERROR_MAX_CONNECTION_REACHED:
                        ret = DataFailCause.IWLAN_MAX_CONNECTION_REACHED;
                        break;
                    case IKE_PROTOCOL_ERROR_SEMANTIC_ERROR_IN_THE_TFT_OPERATION:
                        ret = DataFailCause.IWLAN_SEMANTIC_ERROR_IN_THE_TFT_OPERATION;
                        break;
                    case IKE_PROTOCOL_ERROR_SYNTACTICAL_ERROR_IN_THE_TFT_OPERATION:
                        ret = DataFailCause.IWLAN_SYNTACTICAL_ERROR_IN_THE_TFT_OPERATION;
                        break;
                    case IKE_PROTOCOL_ERROR_SEMANTIC_ERRORS_IN_PACKET_FILTERS:
                        ret = DataFailCause.IWLAN_SEMANTIC_ERRORS_IN_PACKET_FILTERS;
                        break;
                    case IKE_PROTOCOL_ERROR_SYNTACTICAL_ERRORS_IN_PACKET_FILTERS:
                        ret = DataFailCause.IWLAN_SYNTACTICAL_ERRORS_IN_PACKET_FILTERS;
                        break;
                    case IKE_PROTOCOL_ERROR_NON_3GPP_ACCESS_TO_EPC_NOT_ALLOWED:
                        ret = DataFailCause.IWLAN_NON_3GPP_ACCESS_TO_EPC_NOT_ALLOWED;
                        break;
                    case IKE_PROTOCOL_ERROR_USER_UNKNOWN:
                        ret = DataFailCause.IWLAN_USER_UNKNOWN;
                        break;
                    case IKE_PROTOCOL_ERROR_NO_APN_SUBSCRIPTION:
                        ret = DataFailCause.IWLAN_NO_APN_SUBSCRIPTION;
                        break;
                    case IKE_PROTOCOL_ERROR_AUTHORIZATION_REJECTED:
                        ret = DataFailCause.IWLAN_AUTHORIZATION_REJECTED;
                        break;
                    case IKE_PROTOCOL_ERROR_ILLEGAL_ME:
                        ret = DataFailCause.IWLAN_ILLEGAL_ME;
                        break;
                    case IKE_PROTOCOL_ERROR_NETWORK_FAILURE:
                        ret = DataFailCause.IWLAN_NETWORK_FAILURE;
                        break;
                    case IKE_PROTOCOL_ERROR_RAT_TYPE_NOT_ALLOWED:
                        ret = DataFailCause.IWLAN_RAT_TYPE_NOT_ALLOWED;
                        break;
                    case IKE_PROTOCOL_ERROR_IMEI_NOT_ACCEPTED:
                        ret = DataFailCause.IWLAN_IMEI_NOT_ACCEPTED;
                        break;
                    case IKE_PROTOCOL_ERROR_PLMN_NOT_ALLOWED:
                        ret = DataFailCause.IWLAN_PLMN_NOT_ALLOWED;
                        break;
                    case IKE_PROTOCOL_ERROR_UNAUTHENTICATED_EMERGENCY_NOT_SUPPORTED:
                        ret = DataFailCause.IWLAN_UNAUTHENTICATED_EMERGENCY_NOT_SUPPORTED;
                        break;
                    case IKE_PROTOCOL_ERROR_CONGESTION:
                        ret = DataFailCause.IWLAN_CONGESTION;
                        break;
                    default:
                        ret = DataFailCause.IWLAN_IKE_PRIVATE_PROTOCOL_ERROR;
                        break;
                }
            }
        }
        return ret;
    }

    public synchronized int getMostRecentDataFailCause() {
        if (mMostRecentError != null) {
            return getDataFailCause(mMostRecentError.mIwlanError);
        }
        return DataFailCause.NONE;
    }

    /**
     * Returns the current retryTime based on the lastErrorForApn
     *
     * @param apn apn name for which curren retry time is needed
     * @return long current retry time in milliseconds
     */
    public synchronized long getRemainingRetryTimeMs(String apn) {
        RetryAction lastRetryAction = getLastRetryAction(apn);
        return lastRetryAction == null ? -1 : getRemainingRetryTimeMs(lastRetryAction);
    }

    /**
     * Get the remaining time in millis should be waited before retry, based on the current time and
     * the RetryAction.
     */
    private static long getRemainingRetryTimeMs(RetryAction retryAction) {
        long totalRetryTimeMs = retryAction.totalRetryTimeMs();
        long errorTime = retryAction.lastErrorTime();
        long currentTime = IwlanHelper.elapsedRealtime();
        return Math.max(0, totalRetryTimeMs - (currentTime - errorTime));
    }

    /**
     * Gets the last error count of the APN
     *
     * @param apn the APN
     * @return the error count for the last error cause of the APN, 0 if no error or unthrottled
     */
    public synchronized int getLastErrorCountOfSameCause(String apn) {
        RetryAction retryAction = getLastRetryAction(apn);
        return retryAction != null ? retryAction.errorCountOfSameCause() : 0;
    }

    /**
     * Returns the index of the FQDN to use for ePDG server selection, based on how many FQDNs are
     * available, the position of the RetryArray index, and configuration of 'NumAttemptsPerFqdn'.
     * This method assumes backoff time is not configured.
     *
     * @param numFqdns number of FQDNs discovered during ePDG server selection.
     * @return int index of the FQDN to use for ePDG server selection. -1 (invalid) if RetryArray or
     *     'NumAttemptsPerFqdn' is not specified in the ErrorPolicy.
     */
    public synchronized int getCurrentFqdnIndex(int numFqdns) {
        String apn = mMostRecentError.mApn;
        RetryAction lastRetryAction = getLastRetryAction(apn);
        return lastRetryAction == null ? -1 : lastRetryAction.getCurrentFqdnIndex(numFqdns);
    }

    @Nullable
    private synchronized RetryAction getLastRetryAction(String apn) {
        ApnRetryActionStore retryActionStore = mRetryActionStoreByApn.get(apn);
        return retryActionStore == null ? null : retryActionStore.getLastRetryAction();
    }

    /**
     * Returns the last error for that apn
     *
     * @param apn apn name
     * @return IwlanError or null if there is no error
     */
    public synchronized IwlanError getLastError(String apn) {
        RetryAction lastRetryAction = getLastRetryAction(apn);
        return lastRetryAction == null
                ? new IwlanError(IwlanError.NO_ERROR)
                : lastRetryAction.error();
    }

    /**
     * Returns whether framework should retry tunnel setup with initial PDN bringup request when
     * handover request fails.
     *
     * @param apn apn name
     * @return boolean result of whether framework should retry tunnel setup with initial PDN
     *     bringup request when handover request fails
     */
    public synchronized boolean shouldRetryWithInitialAttach(String apn) {
        RetryAction retryAction = getLastRetryAction(apn);
        return retryAction != null && retryAction.shouldRetryWithInitialAttach();
    }

    public void logErrorPolicies() {
        Log.d(LOG_TAG, "mCarrierConfigPolicies:");
        for (Map.Entry<String, List<ErrorPolicy>> entry : mCarrierConfigPolicies.entrySet()) {
            Log.d(LOG_TAG, "Apn: " + entry.getKey());
            for (ErrorPolicy policy : entry.getValue()) {
                policy.log();
            }
        }
        Log.d(LOG_TAG, "mDefaultPolicies:");
        for (Map.Entry<String, List<ErrorPolicy>> entry : mDefaultPolicies.entrySet()) {
            Log.d(LOG_TAG, "Apn: " + entry.getKey());
            for (ErrorPolicy policy : entry.getValue()) {
                policy.log();
            }
        }
    }

    public synchronized void dump(PrintWriter pw) {
        pw.println("---- ErrorPolicyManager ----");
        mRetryActionStoreByApn.forEach(
                (apn, retryActionStore) -> {
                    pw.println("APN: " + apn);
                    pw.println("Last RetryAction: " + retryActionStore.getLastRetryAction());
                    retryActionStore.mLastRetryActionByCause.forEach(
                            (cause, retryAction) -> {
                                pw.println(cause);
                                pw.println(retryAction);
                            });
                });
        pw.println(mErrorStats);
        pw.println("----------------------------");
    }

    private ErrorPolicyManager(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;
        LOG_TAG = ErrorPolicyManager.class.getSimpleName() + "[" + slotId + "]";

        initHandler();

        // read from default error policy config file
        try {
            mDefaultPolicies.putAll(readErrorPolicies(new JSONArray(getDefaultJSONConfig())));
        } catch (IOException | JSONException | IllegalArgumentException e) {
            throw new AssertionError(e);
        }

        mCarrierConfigErrorPolicyString = null;
        readFromCarrierConfig(IwlanHelper.getCarrierId(mContext, mSlotId));
        updateUnthrottlingEvents();
    }

    private ErrorPolicy findErrorPolicy(String apn, IwlanError iwlanError) {
        ErrorPolicy policy = null;

        if (mCarrierConfigPolicies.containsKey(apn)) {
            policy = getPreferredErrorPolicy(mCarrierConfigPolicies.get(apn), iwlanError);
        }
        if (policy == null && mCarrierConfigPolicies.containsKey("*")) {
            policy = getPreferredErrorPolicy(mCarrierConfigPolicies.get("*"), iwlanError);
        }
        if (policy == null && mDefaultPolicies.containsKey(apn)) {
            policy = getPreferredErrorPolicy(mDefaultPolicies.get(apn), iwlanError);
        }
        if (policy == null && mDefaultPolicies.containsKey("*")) {
            policy = getPreferredErrorPolicy(mDefaultPolicies.get("*"), iwlanError);
        }

        if (policy == null) {
            // there should at least be one default policy defined in Default config
            // that will apply to all errors.
            // should not reach here in any situation, default config should be configured in
            // defaultiwlanerrorconfig.json. here is just for prevent runtime exception
            logErrorPolicies();
            Log.e(LOG_TAG, "No matched error policy");
            policy = FALLBACK_ERROR_POLICY;
        }
        return policy;
    }

    private ErrorPolicy getPreferredErrorPolicy(
            List<ErrorPolicy> errorPolicies, IwlanError iwlanError) {

        ErrorPolicy selectedPolicy = null;
        for (ErrorPolicy policy : errorPolicies) {
            if (policy.match(iwlanError)) {
                if (!policy.isFallback()) {
                    selectedPolicy = policy;
                    break;
                }
                if (selectedPolicy == null || policy.getErrorType() != GENERIC_ERROR_TYPE) {
                    selectedPolicy = policy;
                }
            }
        }
        return selectedPolicy;
    }

    @VisibleForTesting
    void initHandler() {
        mHandler = new EpmHandler(getLooper());
    }

    @VisibleForTesting
    Looper getLooper() {
        mHandlerThread = new HandlerThread("ErrorPolicyManagerThread");
        mHandlerThread.start();
        return mHandlerThread.getLooper();
    }

    private String getDefaultJSONConfig() throws IOException {
        String str;
        StringBuilder stringBuilder = new StringBuilder();
        InputStream is = mContext.getAssets().open("defaultiwlanerrorconfig.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        while ((str = reader.readLine()) != null && str.length() > 0) {
            // ignore the lines starting with '#' as they are intended to be
            // comments
            if (str.charAt(0) == '#') {
                continue;
            }
            stringBuilder.append(str).append("\n");
        }
        is.close();
        return stringBuilder.toString();
    }

    @VisibleForTesting
    Map<String, List<ErrorPolicy>> readErrorPolicies(JSONArray apnArray)
            throws JSONException, IllegalArgumentException {
        Map<String, List<ErrorPolicy>> errorPolicies = new HashMap<>();
        for (int i = 0; i < apnArray.length(); i++) {
            JSONObject apnDetails = apnArray.getJSONObject(i);

            String apnName = ((String) apnDetails.get("ApnName")).trim();
            JSONArray errorTypeArray = (JSONArray) apnDetails.get("ErrorTypes");

            for (int j = 0; j < errorTypeArray.length(); j++) {
                JSONObject errorTypeObject = errorTypeArray.getJSONObject(j);

                String errorTypeStr = ((String) errorTypeObject.get("ErrorType")).trim();
                JSONArray errorDetailArray = (JSONArray) errorTypeObject.get("ErrorDetails");
                int errorType;

                if ((errorType = getErrorPolicyErrorType(errorTypeStr)) == UNKNOWN_ERROR_TYPE) {
                    throw new IllegalArgumentException("Unknown error type in the parsing");
                }

                List<Integer> retryArray =
                        parseRetryArray((JSONArray) errorTypeObject.get("RetryArray"));

                ErrorPolicy.Builder errorPolicyBuilder =
                        builder()
                                .setErrorType(errorType)
                                .setErrorDetails(parseErrorDetails(errorType, errorDetailArray))
                                .setRetryArray(retryArray)
                                .setUnthrottlingEvents(
                                        parseUnthrottlingEvents(
                                                (JSONArray)
                                                        errorTypeObject.get("UnthrottlingEvents")));

                if (!retryArray.isEmpty() && retryArray.get(retryArray.size() - 1) == -1L) {
                    errorPolicyBuilder.setInfiniteRetriesWithLastRetryTime(true);
                }

                if (errorTypeObject.has("NumAttemptsPerFqdn")) {
                    errorPolicyBuilder.setNumAttemptsPerFqdn(
                            errorTypeObject.getInt("NumAttemptsPerFqdn"));
                }

                if (errorTypeObject.has("HandoverAttemptCount")) {
                    if (errorType != IKE_PROTOCOL_ERROR_TYPE) {
                        throw new IllegalArgumentException(
                                "Handover attempt count should not be applied when errorType is not"
                                        + " explicitly defined as IKE_PROTOCOL_ERROR_TYPE");
                    }
                    errorPolicyBuilder.setHandoverAttemptCount(
                            errorTypeObject.getInt("HandoverAttemptCount"));
                }

                ErrorPolicy errorPolicy = errorPolicyBuilder.build();

                errorPolicies.putIfAbsent(apnName, new ArrayList<>());
                errorPolicies.get(apnName).add(errorPolicy);
            }
        }
        return errorPolicies;
    }

    private List<Integer> parseRetryArray(JSONArray retryArray)
            throws JSONException, IllegalArgumentException {
        List<Integer> ret = new ArrayList<>();
        for (int i = 0; i < retryArray.length(); i++) {
            String retryTime = retryArray.getString(i).trim();

            // catch misplaced -1 retry times in the array.
            // 1. if it is not placed at the last position in the array
            // 2. if it is placed in the first position (catches the case where it is
            //    the only element).
            if (retryTime.equals("-1") && (i != retryArray.length() - 1 || i == 0)) {
                throw new IllegalArgumentException("Misplaced -1 in retry array");
            }
            if (TextUtils.isDigitsOnly(retryTime) || retryTime.equals("-1")) {
                ret.add(Integer.parseInt(retryTime));
            } else if (retryTime.contains("+r")) {
                // randomized retry time
                String[] times = retryTime.split("\\+r");
                if (times.length == 2
                        && TextUtils.isDigitsOnly(times[0])
                        && TextUtils.isDigitsOnly(times[1])) {
                    ret.add(
                            Integer.parseInt(times[0])
                                    + (int) (Math.random() * Long.parseLong(times[1])));
                } else {
                    throw new IllegalArgumentException(
                            "Randomized Retry time is not in acceptable format");
                }
            } else {
                throw new IllegalArgumentException("Retry time is not in acceptable format");
            }
        }
        return ret;
    }

    private List<Integer> parseUnthrottlingEvents(JSONArray unthrottlingEvents)
            throws JSONException, IllegalArgumentException {
        List<Integer> ret = new ArrayList<>();
        for (int i = 0; i < unthrottlingEvents.length(); i++) {
            int event =
                    IwlanEventListener.getUnthrottlingEvent(unthrottlingEvents.getString(i).trim());
            if (event == IwlanEventListener.UNKNOWN_EVENT) {
                throw new IllegalArgumentException(
                        "Unexpected unthrottlingEvent " + unthrottlingEvents.getString(i));
            }
            ret.add(event);
        }
        return ret;
    }

    private List<String> parseErrorDetails(int errorType, JSONArray errorDetailArray)
            throws JSONException, IllegalArgumentException {
        List<String> ret = new ArrayList<>();
        boolean isValidErrorDetail = true;

        for (int i = 0; i < errorDetailArray.length(); i++) {
            String errorDetail = errorDetailArray.getString(i).trim();
            switch (errorType) {
                case IKE_PROTOCOL_ERROR_TYPE:
                    isValidErrorDetail = verifyIkeProtocolErrorDetail(errorDetail);
                    break;
                case GENERIC_ERROR_TYPE:
                    isValidErrorDetail = verifyGenericErrorDetail(errorDetail);
                    break;
            }
            if (!isValidErrorDetail) {
                throw new IllegalArgumentException(
                        "Invalid ErrorDetail: " + errorDetail + " for ErrorType: " + errorType);
            }
            ret.add(errorDetail);
        }
        return ret;
    }

    /** Allowed formats are: number(Integer), range(Integers separated by -) and "*" */
    private boolean verifyIkeProtocolErrorDetail(String errorDetailStr) {
        boolean ret = true;
        if (errorDetailStr.contains("-")) {
            // verify range format
            String[] rangeNumbers = errorDetailStr.split("-");
            if (rangeNumbers.length == 2) {
                for (String range : rangeNumbers) {
                    if (!TextUtils.isDigitsOnly(range)) {
                        ret = false;
                    }
                }
            } else {
                ret = false;
            }
        } else if (!errorDetailStr.equals("*") && !TextUtils.isDigitsOnly(errorDetailStr)) {
            ret = false;
        }
        return ret;
    }

    /**
     * Allowed strings are: "IO_EXCEPTION", "TIMEOUT_EXCEPTION", "SERVER_SELECTION_FAILED",
     * "TUNNEL_TRANSFORM_FAILED" and "*"
     */
    private boolean verifyGenericErrorDetail(String errorDetailStr) {
        boolean ret = false;
        for (String str : GENERIC_ERROR_DETAIL_STRINGS) {
            if (errorDetailStr.equals(str)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    private @ErrorPolicyErrorType int getErrorPolicyErrorType(String errorType) {
        int ret = UNKNOWN_ERROR_TYPE;
        switch (errorType) {
            case "IKE_PROTOCOL_ERROR_TYPE":
                ret = IKE_PROTOCOL_ERROR_TYPE;
                break;
            case "GENERIC_ERROR_TYPE":
                ret = GENERIC_ERROR_TYPE;
                break;
            case "*":
                ret = FALLBACK_ERROR_TYPE;
                break;
        }
        return ret;
    }

    private synchronized Set<Integer> getAllUnthrottlingEvents() {
        Set<Integer> events = new HashSet<>();
        for (Map.Entry<String, List<ErrorPolicy>> entry : mCarrierConfigPolicies.entrySet()) {
            List<ErrorPolicy> errorPolicies = entry.getValue();
            for (ErrorPolicy errorPolicy : errorPolicies) {
                events.addAll(errorPolicy.unthrottlingEvents());
            }
        }
        for (Map.Entry<String, List<ErrorPolicy>> entry : mDefaultPolicies.entrySet()) {
            List<ErrorPolicy> errorPolicies = entry.getValue();
            for (ErrorPolicy errorPolicy : errorPolicies) {
                events.addAll(errorPolicy.unthrottlingEvents());
            }
        }
        events.add(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT);
        return events;
    }

    /**
     * This method is called once on initialization of this class And is also called from handler on
     * CARRIER_CONFIG_CHANGED event. There is no race condition between both as we register for the
     * events after the calling this method.
     */
    private synchronized void readFromCarrierConfig(int currentCarrierId) {
        String carrierConfigErrorPolicy =
                IwlanHelper.getConfig(KEY_ERROR_POLICY_CONFIG_STRING, mContext, mSlotId);
        if (carrierConfigErrorPolicy == null) {
            Log.e(LOG_TAG, "ErrorPolicy from Carrier Config is NULL");
            mCarrierConfigPolicies.clear();
            mCarrierConfigErrorPolicyString = null;
            return;
        }
        try {
            Map<String, List<ErrorPolicy>> errorPolicies =
                    readErrorPolicies(new JSONArray(carrierConfigErrorPolicy));
            if (errorPolicies.size() > 0) {
                mCarrierConfigErrorPolicyString = carrierConfigErrorPolicy;
                carrierId = currentCarrierId;
                mCarrierConfigPolicies.clear();
                mCarrierConfigPolicies.putAll(errorPolicies);
            }
        } catch (JSONException | IllegalArgumentException e) {
            Log.e(
                    LOG_TAG,
                    "Unable to parse the ErrorPolicy from CarrierConfig\n"
                            + carrierConfigErrorPolicy);
            mCarrierConfigPolicies.clear();
            mCarrierConfigErrorPolicyString = null;
            e.printStackTrace();
        }
    }

    private void updateUnthrottlingEvents() {
        Set<Integer> registerEvents, unregisterEvents;
        unregisterEvents = mUnthrottlingEvents;
        registerEvents = getAllUnthrottlingEvents();
        mUnthrottlingEvents = getAllUnthrottlingEvents();

        if (unregisterEvents != null) {
            registerEvents.removeAll(unregisterEvents);
            unregisterEvents.removeAll(mUnthrottlingEvents);
        }

        IwlanEventListener.getInstance(mContext, mSlotId)
                .addEventListener(new ArrayList<>(registerEvents), mHandler);
        if (unregisterEvents != null) {
            IwlanEventListener.getInstance(mContext, mSlotId)
                    .removeEventListener(new ArrayList<>(unregisterEvents), mHandler);
        }
        Log.d(
                LOG_TAG,
                "UnthrottlingEvents: "
                        + (mUnthrottlingEvents != null
                                ? Arrays.toString(mUnthrottlingEvents.toArray())
                                : "null"));
    }

    private synchronized void unthrottleLastErrorOnEvent(int event) {
        Log.d(LOG_TAG, "unthrottleLastErrorOnEvent: " + event);
        // Pass the other events to RetryActionStore to check if can unthrottle
        mRetryActionStoreByApn.forEach(
                (apn, retryActionStore) -> retryActionStore.handleUnthrottlingEvent(event));
        // Carrier Config Changed should clear all RetryActionStore
        if (event == IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT) {
            mRetryActionStoreByApn.clear();
        }
    }

    @VisibleForTesting
    ErrorStats getErrorStats() {
        return mErrorStats;
    }

    @AutoValue
    abstract static class ErrorPolicy {
        private static final String LOG_TAG = ErrorPolicyManager.class.getSimpleName();

        abstract @ErrorPolicyErrorType int errorType();

        abstract List<String> errorDetails();

        abstract List<Integer> retryArray();

        abstract Boolean infiniteRetriesWithLastRetryTime();

        abstract List<Integer> unthrottlingEvents();

        abstract Optional<Integer> numAttemptsPerFqdn();

        abstract Optional<Integer> handoverAttemptCount();

        @AutoValue.Builder
        abstract static class Builder {
            abstract Builder setErrorType(int errorType);

            abstract Builder setErrorDetails(List<String> errorDetails);

            abstract Builder setRetryArray(List<Integer> retryArray);

            abstract Builder setInfiniteRetriesWithLastRetryTime(
                    Boolean infiniteRetriesWithLastRetryTime);

            abstract Builder setUnthrottlingEvents(List<Integer> unthrottlingEvents);

            abstract Builder setNumAttemptsPerFqdn(Integer numAttemptsPerFqdn);

            abstract Builder setHandoverAttemptCount(Integer handoverAttemptCount);

            abstract ErrorPolicy build();
        }

        long getRetryTime(int index) {
            long retryTime = -1;
            if (retryArray().size() > 0) {
                // If the index is greater than or equal to the last element's index
                // and if the last item in the retryArray is "-1" use the retryTime
                // of the element before the last element to repeat the element.
                if (infiniteRetriesWithLastRetryTime()) {
                    index = Math.min(index, retryArray().size() - 2);
                }
                if (index >= 0 && index < retryArray().size()) {
                    retryTime = retryArray().get(index);
                }
            }

            // retryTime -1 represents indefinite failure. In that case
            // return time that represents 1 day to not retry for that day.
            if (retryTime == -1L) {
                retryTime = TimeUnit.DAYS.toSeconds(1);
            }
            return retryTime;
        }

        int getCurrentFqdnIndex(int retryIndex, int numFqdns) {
            int result = -1;
            if (numAttemptsPerFqdn().isEmpty() || retryArray().size() <= 0) {
                return result;
            }
            // Cycles between 0 and (numFqdns - 1), based on the current attempt count and size of
            // mRetryArray.
            return (retryIndex + 1) / numAttemptsPerFqdn().get() % numFqdns;
        }

        @ErrorPolicyErrorType
        int getErrorType() {
            return errorType();
        }

        int getHandoverAttemptCount() {
            return handoverAttemptCount().orElse(Integer.MAX_VALUE);
        }

        synchronized boolean canUnthrottle(int event) {
            return unthrottlingEvents().contains(event);
        }

        boolean match(IwlanError iwlanError) {
            // Generic by default to match to generic policy.
            String iwlanErrorDetail;
            if (errorType() == FALLBACK_ERROR_TYPE) {
                return true;
            } else if (errorType() == IKE_PROTOCOL_ERROR_TYPE
                    && iwlanError.getErrorType() == IwlanError.IKE_PROTOCOL_EXCEPTION) {
                IkeProtocolException exception = (IkeProtocolException) iwlanError.getException();
                iwlanErrorDetail = String.valueOf(exception.getErrorType());
            } else if (errorType() == GENERIC_ERROR_TYPE) {
                iwlanErrorDetail = getGenericErrorDetailString(iwlanError);
                if (iwlanErrorDetail.equals("UNKNOWN")) {
                    return false;
                }
            } else {
                return false;
            }

            boolean ret = false;
            for (String errorDetail : errorDetails()) {
                if (errorType() == IKE_PROTOCOL_ERROR_TYPE
                        && iwlanError.getErrorType() == IwlanError.IKE_PROTOCOL_EXCEPTION
                        && errorDetail.contains("-")) {
                    // error detail is stored in range format.
                    // ErrorPolicyManager#verifyIkeProtocolErrorDetail will make sure that
                    // this is stored correctly in "min-max" format.
                    String[] range = errorDetail.split("-");
                    int min = Integer.parseInt(range[0]);
                    int max = Integer.parseInt(range[1]);
                    int error = Integer.parseInt(iwlanErrorDetail);
                    if (error >= min && error <= max) {
                        ret = true;
                        break;
                    }
                } else if (errorDetail.equals(iwlanErrorDetail) || errorDetail.equals("*")) {
                    ret = true;
                    break;
                }
            }
            return ret;
        }

        void log() {
            Log.d(LOG_TAG, "ErrorType: " + errorType());
            Log.d(LOG_TAG, "ErrorDetail: " + Arrays.toString(errorDetails().toArray()));
            Log.d(LOG_TAG, "RetryArray: " + Arrays.toString(retryArray().toArray()));
            Log.d(
                    LOG_TAG,
                    "InfiniteRetriesWithLastRetryTime: " + infiniteRetriesWithLastRetryTime());
            Log.d(
                    LOG_TAG,
                    "UnthrottlingEvents: " + Arrays.toString(unthrottlingEvents().toArray()));
            Log.d(LOG_TAG, "NumAttemptsPerFqdn: " + numAttemptsPerFqdn());
            Log.d(LOG_TAG, "handoverAttemptCount: " + handoverAttemptCount());
        }

        boolean isFallback() {
            return (errorType() == FALLBACK_ERROR_TYPE)
                    || (errorDetails().size() == 1 && errorDetails().get(0).equals("*"));
        }

        String getGenericErrorDetailString(IwlanError iwlanError) {
            String ret = "UNKNOWN";
            switch (iwlanError.getErrorType()) {
                case IwlanError.IKE_INTERNAL_IO_EXCEPTION:
                    ret = "IO_EXCEPTION";
                    break;
                case IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED:
                    ret = "SERVER_SELECTION_FAILED";
                    break;
                case IwlanError.TUNNEL_TRANSFORM_FAILED:
                    ret = "TUNNEL_TRANSFORM_FAILED";
                    break;
                case IwlanError.IKE_NETWORK_LOST_EXCEPTION:
                    ret = "IKE_NETWORK_LOST_EXCEPTION";
                    break;
                case IwlanError.EPDG_ADDRESS_ONLY_IPV4_ALLOWED:
                    ret = "EPDG_ADDRESS_ONLY_IPV4_ALLOWED";
                    break;
                case IwlanError.EPDG_ADDRESS_ONLY_IPV6_ALLOWED:
                    ret = "EPDG_ADDRESS_ONLY_IPV6_ALLOWED";
                    break;
                    // TODO: Add TIMEOUT_EXCEPTION processing
                case IwlanError.IKE_INIT_TIMEOUT:
                    ret = "IKE_INIT_TIMEOUT";
                    break;
                case IwlanError.IKE_MOBILITY_TIMEOUT:
                    ret = "IKE_MOBILITY_TIMEOUT";
                    break;
                case IwlanError.IKE_DPD_TIMEOUT:
                    ret = "IKE_DPD_TIMEOUT";
                    break;
            }
            return ret;
        }
    }

    /**
     * A data class to store the error cause and the applied error policy. This class is responsible
     * to calculate the retry time base on the error policy / config.
     */
    interface RetryAction {
        IwlanError error();

        ErrorPolicy errorPolicy();

        long lastErrorTime();

        /** The total time should be waited between lastErrorTime and next retry. */
        long totalRetryTimeMs();

        /** The number of same cause error observed since last success / unthrottle event. */
        int errorCountOfSameCause();

        boolean shouldRetryWithInitialAttach();

        int getCurrentFqdnIndex(int numFqdns);
    }

    /** RetryAction with retry time defined by retry index and error policy */
    @AutoValue
    abstract static class PolicyDerivedRetryAction implements RetryAction {
        abstract int currentRetryIndex();

        @Override
        public long totalRetryTimeMs() {
            return TimeUnit.SECONDS.toMillis(errorPolicy().getRetryTime(currentRetryIndex()));
        }

        @Override
        public int getCurrentFqdnIndex(int numFqdns) {
            ErrorPolicy errorPolicy = errorPolicy();
            return errorPolicy.getCurrentFqdnIndex(currentRetryIndex(), numFqdns);
        }

        @Override
        public boolean shouldRetryWithInitialAttach() {
            // UE should only uses initial attach to reset network failure, not for UE internal or
            // DNS errors. When the number of handover failures due to network issues exceeds the
            // configured threshold, UE should request network with initial attach instead of
            // handover request.
            ErrorPolicy errorPolicy = errorPolicy();
            return errorPolicy.getErrorType() == IKE_PROTOCOL_ERROR_TYPE
                    && currentRetryIndex() + 1 >= errorPolicy.getHandoverAttemptCount();
        }

        /** Create a new PolicyDerivedRetryAction */
        static PolicyDerivedRetryAction create(
                IwlanError error,
                ErrorPolicy errorPolicy,
                int errorCountOfSameCause,
                int currentRetryIndex) {
            return new AutoValue_ErrorPolicyManager_PolicyDerivedRetryAction(
                    error,
                    errorPolicy,
                    IwlanHelper.elapsedRealtime(),
                    errorCountOfSameCause,
                    currentRetryIndex);
        }
    }

    /** RetryAction with retry time defined by backoff time in tunnel config */
    @AutoValue
    abstract static class IkeBackoffNotifyRetryAction implements RetryAction {
        abstract long backoffTime();

        @Override
        public long totalRetryTimeMs() {
            return TimeUnit.SECONDS.toMillis(backoffTime());
        }

        @Override
        public int getCurrentFqdnIndex(int numFqdns) {
            // Not applicable for backoff time configured case, therefore returning 0 here
            return 0;
        }

        @Override
        public boolean shouldRetryWithInitialAttach() {
            // TODO(b/308745683): Initial attach condition is undefined for backoff config case
            ErrorPolicy errorPolicy = errorPolicy();
            return errorPolicy.getErrorType() == IKE_PROTOCOL_ERROR_TYPE
                    && errorPolicy.getHandoverAttemptCount() == 0;
        }

        static IkeBackoffNotifyRetryAction create(
                IwlanError error,
                ErrorPolicy errorPolicy,
                int errorCountOfSameCause,
                long backoffTime) {
            return new AutoValue_ErrorPolicyManager_IkeBackoffNotifyRetryAction(
                    error,
                    errorPolicy,
                    IwlanHelper.elapsedRealtime(),
                    errorCountOfSameCause,
                    backoffTime);
        }
    }

    interface ErrorCause {
        @IwlanError.IwlanErrorType
        int iwlanErrorType();

        static ErrorCause fromIwlanError(IwlanError iwlanError) {
            if (iwlanError.getErrorType() == IwlanError.IKE_PROTOCOL_EXCEPTION) {
                return new AutoValue_ErrorPolicyManager_IkeProtocolErrorCause(
                        /* ikeProtocolErrorType= */ ((IkeProtocolException)
                                        iwlanError.getException())
                                .getErrorType());
            }
            return new AutoValue_ErrorPolicyManager_NonIkeProtocolErrorCause(
                    /* iwlanErrorType= */ iwlanError.getErrorType());
        }
    }

    @AutoValue
    abstract static class NonIkeProtocolErrorCause implements ErrorCause {}

    /**
     * An IkeProtocolErrorCause will carry the ike protocol error type, so that different protocol
     * error will be treated as different error cause
     */
    @AutoValue
    abstract static class IkeProtocolErrorCause implements ErrorCause {
        @Override
        @IwlanError.IwlanErrorType
        public int iwlanErrorType() {
            return IwlanError.IKE_PROTOCOL_EXCEPTION;
        }

        // @IkeProtocolException.ErrorType is hidden API
        abstract int ikeProtocolErrorType();
    }

    /**
     * This class manage and store the RetryAction of the APN, and responsible to create RetryAction
     * when IwlanError received.
     */
    class ApnRetryActionStore {
        final String mApn;
        final ConcurrentHashMap<ErrorCause, RetryAction> mLastRetryActionByCause;
        @Nullable RetryAction mLastRetryAction;

        ApnRetryActionStore(String apn) {
            mApn = apn;
            mLastRetryActionByCause = new ConcurrentHashMap<>();
        }

        /**
         * Determines whether the new {@link RetryAction} should accumulate the retry index from
         * {@code prevRetryAction}.
         *
         * @param prevRetryAction the previous RetryAction (can be null).
         * @param newIwlanError the new IwlanError.
         * @return true if {@code prevRetryAction} is an instance of {@link
         *     PolicyDerivedRetryAction} and is the same {@link ErrorCause} as {@code
         *     newIwlanError}, false otherwise.
         */
        private boolean shouldAccumulateRetryIndex(
                @Nullable RetryAction prevRetryAction, IwlanError newIwlanError) {
            if (!(prevRetryAction instanceof PolicyDerivedRetryAction)) {
                return false;
            }

            boolean isSameIwlanError = prevRetryAction.error().equals(newIwlanError);
            // If prev and current error are both IKE_PROTOCOL_EXCEPTION, keep the retry index
            // TODO: b/292312000 - Workaround for RetryIndex lost
            boolean areBothIkeProtocolException =
                    (newIwlanError.getErrorType() == IwlanError.IKE_PROTOCOL_EXCEPTION
                            && prevRetryAction.error().getErrorType()
                                    == IwlanError.IKE_PROTOCOL_EXCEPTION);
            boolean shouldAccumulateRetryIndex = isSameIwlanError || areBothIkeProtocolException;

            if (!shouldAccumulateRetryIndex) {
                Log.d(LOG_TAG, "Doesn't match to the previous error" + newIwlanError);
            }

            return shouldAccumulateRetryIndex;
        }

        private PolicyDerivedRetryAction generateRetryAction(IwlanError iwlanError) {
            ErrorCause errorCause = ErrorCause.fromIwlanError(iwlanError);

            @Nullable RetryAction prevRetryAction = mLastRetryActionByCause.get(errorCause);
            int newErrorCount =
                    prevRetryAction != null ? prevRetryAction.errorCountOfSameCause() + 1 : 1;
            boolean shouldAccumulateRetryIndex =
                    shouldAccumulateRetryIndex(prevRetryAction, iwlanError);
            int newRetryIndex =
                    shouldAccumulateRetryIndex
                            ? ((PolicyDerivedRetryAction) prevRetryAction).currentRetryIndex() + 1
                            : 0;

            ErrorPolicy policy = findErrorPolicy(mApn, iwlanError);
            PolicyDerivedRetryAction newRetryAction =
                    PolicyDerivedRetryAction.create(
                            iwlanError, policy, newErrorCount, newRetryIndex);
            mLastRetryActionByCause.put(errorCause, newRetryAction);
            mLastRetryAction = newRetryAction;

            return newRetryAction;
        }

        private IkeBackoffNotifyRetryAction generateRetryAction(
                IwlanError iwlanError, long backoffTime) {
            ErrorCause errorCause = ErrorCause.fromIwlanError(iwlanError);
            @Nullable RetryAction prevRetryAction = mLastRetryActionByCause.get(errorCause);
            int newErrorCount =
                    prevRetryAction != null ? prevRetryAction.errorCountOfSameCause() + 1 : 1;
            ErrorPolicy policy = findErrorPolicy(mApn, iwlanError);
            // For configured back off time case, simply create new RetryAction, nothing need to
            // keep
            IkeBackoffNotifyRetryAction newRetryAction =
                    IkeBackoffNotifyRetryAction.create(
                            iwlanError, policy, newErrorCount, backoffTime);
            mLastRetryActionByCause.put(errorCause, newRetryAction);
            mLastRetryAction = newRetryAction;

            return newRetryAction;
        }

        /**
         * Set {@code lastRetryAction} to null if {@code lastRetryAction} can be unthrottled by the
         * event. Clear those reserved retry index and the {@link RetryAction} if any {@link
         * RetryAction} in {@code mLastRetryActionByCause} can be unthrottled by the event.
         *
         * @param event the handling event
         */
        private void handleUnthrottlingEvent(int event) {
            if (event == IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT) {
                mLastRetryActionByCause.clear();
            } else {
                // Check all stored RetryAction, remove from the store if it can be unthrottle.
                // By removing it, the retry index (for PolicyDerived) will reset as 0
                mLastRetryActionByCause
                        .entrySet()
                        .removeIf(it -> it.getValue().errorPolicy().canUnthrottle(event));
            }

            DataService.DataServiceProvider provider =
                    IwlanDataService.getDataServiceProvider(mSlotId);

            boolean isCarrierConfigChanged =
                    event == IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT;
            boolean isLastRetryActionCanUnthrottle =
                    mLastRetryAction != null && mLastRetryAction.errorPolicy().canUnthrottle(event);
            if (isCarrierConfigChanged || isLastRetryActionCanUnthrottle) {
                mLastRetryAction = null;

                if (provider == null) {
                    Log.w(LOG_TAG, "DataServiceProvider not found for slot: " + mSlotId);
                } else {
                    provider.notifyApnUnthrottled(mApn);
                    Log.d(LOG_TAG, "unthrottled error for: " + mApn);
                }
            }
        }

        @Nullable
        private RetryAction getLastRetryAction() {
            return mLastRetryAction;
        }
    }

    static class ApnWithIwlanError {
        @NonNull final String mApn;
        @NonNull final IwlanError mIwlanError;

        ApnWithIwlanError(@NonNull String apn, @NonNull IwlanError iwlanError) {
            mApn = apn;
            mIwlanError = iwlanError;
        }
    }

    private boolean isValidCarrierConfigChangedEvent(int currentCarrierId) {
        String errorPolicyConfig =
                IwlanHelper.getConfig(KEY_ERROR_POLICY_CONFIG_STRING, mContext, mSlotId);
        return (currentCarrierId != carrierId)
                || (mCarrierConfigErrorPolicyString == null)
                || (errorPolicyConfig != null
                        && !Objects.equals(mCarrierConfigErrorPolicyString, errorPolicyConfig));
    }

    private final class EpmHandler extends Handler {
        private final String TAG = EpmHandler.class.getSimpleName();

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "msg.what = " + msg.what);
            switch (msg.what) {
                case IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT:
                    Log.d(TAG, "On CARRIER_CONFIG_CHANGED_EVENT");
                    int currentCarrierId = IwlanHelper.getCarrierId(mContext, mSlotId);
                    if (isValidCarrierConfigChangedEvent(currentCarrierId)) {
                        Log.d(TAG, "Unthrottle last error and read from carrier config");
                        unthrottleLastErrorOnEvent(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT);
                        readFromCarrierConfig(currentCarrierId);
                        updateUnthrottlingEvents();
                    }
                    break;
                case IwlanEventListener.APM_ENABLE_EVENT:
                case IwlanEventListener.APM_DISABLE_EVENT:
                case IwlanEventListener.WIFI_DISABLE_EVENT:
                case IwlanEventListener.WIFI_CALLING_DISABLE_EVENT:
                case IwlanEventListener.WIFI_AP_CHANGED_EVENT:
                    unthrottleLastErrorOnEvent(msg.what);
                    break;
                default:
                    Log.d(TAG, "Unknown message received!");
                    break;
            }
        }

        EpmHandler(Looper looper) {
            super(looper);
        }
    }

    @VisibleForTesting
    static class ErrorStats {
        @VisibleForTesting Map<String, Map<String, Long>> mStats = new HashMap<>();
        private Date mStartTime;
        private int mStatCount;
        private static final int APN_COUNT_MAX = 10;
        private static final int ERROR_COUNT_MAX = 1000;

        ErrorStats() {
            mStartTime = Calendar.getInstance().getTime();
            mStatCount = 0;
        }

        void update(String apn, IwlanError error) {
            if (mStats.size() >= APN_COUNT_MAX || mStatCount >= ERROR_COUNT_MAX) {
                reset();
            }
            if (!mStats.containsKey(apn)) {
                mStats.put(apn, new HashMap<>());
            }
            Map<String, Long> errorMap = mStats.get(apn);
            String errorString = error.toString();
            if (!errorMap.containsKey(errorString)) {
                errorMap.put(errorString, 0L);
            }
            long count = errorMap.get(errorString);
            errorMap.put(errorString, ++count);
            mStats.put(apn, errorMap);
            mStatCount++;
        }

        void reset() {
            mStartTime = Calendar.getInstance().getTime();
            mStats = new HashMap<>();
            mStatCount = 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("mStartTime: ").append(mStartTime);
            sb.append("\nErrorStats");
            for (Map.Entry<String, Map<String, Long>> entry : mStats.entrySet()) {
                sb.append("\n\tApn: ").append(entry.getKey());
                for (Map.Entry<String, Long> errorEntry : entry.getValue().entrySet()) {
                    sb.append("\n\t  ")
                            .append(errorEntry.getKey())
                            .append(" : ")
                            .append(errorEntry.getValue());
                }
            }
            return sb.toString();
        }
    }
}
