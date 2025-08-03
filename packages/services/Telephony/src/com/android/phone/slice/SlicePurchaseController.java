/*
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

package com.android.phone.slice;

import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED;
import static android.telephony.TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.provider.DeviceConfig;
import android.sysprop.TelephonyProperties;
import android.telephony.AnomalyReporter;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.data.RouteSelectionDescriptor;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.UrspRule;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;
import android.webkit.WebView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.flags.FeatureFlags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The SlicePurchaseController controls the purchase and availability of all cellular premium
 * capabilities. Applications can check whether premium capabilities are available by calling
 * {@link TelephonyManager#isPremiumCapabilityAvailableForPurchase(int)}. If this returns true,
 * they can then call {@link TelephonyManager#purchasePremiumCapability(int, Executor, Consumer)}
 * to purchase the premium capability. If all conditions are met, a notification will be displayed
 * to the user prompting them to purchase the premium capability. If the user confirms on the
 * notification, a {@link WebView} will open that allows the user to purchase the premium capability
 * from the carrier. If the purchase is successful, the premium capability will be available for
 * all applications to request through {@link ConnectivityManager#requestNetwork}.
 */
public class SlicePurchaseController extends Handler {
    @NonNull private static final String TAG = "SlicePurchaseController";

    /** Unknown failure code. */
    public static final int FAILURE_CODE_UNKNOWN = 0;
    /** Performance boost purchase failed because the carrier URL is unavailable. */
    public static final int FAILURE_CODE_CARRIER_URL_UNAVAILABLE = 1;
    /** Performance boost purchase failed because user authentication failed. */
    public static final int FAILURE_CODE_AUTHENTICATION_FAILED = 2;
    /** Performance boost purchase failed because the payment failed. */
    public static final int FAILURE_CODE_PAYMENT_FAILED = 3;
    /**
     * Performance boost purchase failed because the content type was specified but
     * user data does not exist.
     */
    public static final int FAILURE_CODE_NO_USER_DATA = 4;

    /**
     * Failure codes that the carrier website can return when a premium capability purchase fails.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "FAILURE_CODE_" }, value = {
            FAILURE_CODE_UNKNOWN,
            FAILURE_CODE_CARRIER_URL_UNAVAILABLE,
            FAILURE_CODE_AUTHENTICATION_FAILED,
            FAILURE_CODE_PAYMENT_FAILED,
            FAILURE_CODE_NO_USER_DATA})
    public @interface FailureCode {}

    /** Value for an invalid premium capability. */
    public static final int PREMIUM_CAPABILITY_INVALID = -1;

    /** Asset URL for the slice_purchase_test.html file. */
    public static final String SLICE_PURCHASE_TEST_FILE =
            "file:///android_asset/slice_purchase_test.html";

    /** Purchasing the premium capability is no longer throttled. */
    private static final int EVENT_PURCHASE_UNTHROTTLED = 1;
    /** Slicing config changed. */
    private static final int EVENT_SLICING_CONFIG_CHANGED = 2;
    /** Start slice purchase application. */
    private static final int EVENT_START_SLICE_PURCHASE_APP = 3;
    /**
     * Premium capability was not purchased within the timeout specified by
     * {@link CarrierConfigManager#KEY_PREMIUM_CAPABILITY_NOTIFICATION_DISPLAY_TIMEOUT_MILLIS_LONG}.
     */
    private static final int EVENT_PURCHASE_TIMEOUT = 4;
    /**
     * Network did not set up the slicing configuration within the timeout specified by
     * {@link CarrierConfigManager#KEY_PREMIUM_CAPABILITY_NETWORK_SETUP_TIME_MILLIS_LONG}.
     */
    private static final int EVENT_SETUP_TIMEOUT = 5;
    /** Device config changed. */
    private static final int EVENT_DEVICE_CONFIG_CHANGED = 6;

    /** UUID to report an anomaly when a premium capability is throttled twice in a row. */
    private static final String UUID_CAPABILITY_THROTTLED_TWICE =
            "15574927-e2e2-4593-99d4-2f340d22b383";
    /** UUID to report an anomaly when receiving an invalid phone ID. */
    private static final String UUID_INVALID_PHONE_ID = "ced79f1a-8ac0-4260-8cf3-08b54c0494f3";
    /** UUID to report an anomaly when receiving an unknown action. */
    private static final String UUID_UNKNOWN_ACTION = "0197efb0-dab1-4b0a-abaf-ac9336ec7923";
    /** UUID to report an anomaly when receiving an unknown failure code with a non-empty reason. */
    private static final String UUID_UNKNOWN_FAILURE_CODE = "76943b23-4415-400c-9855-b534fc4fc62c";
    /**
     * UUID to report an anomaly when the network fails to set up a slicing configuration after
     * the user purchases a premium capability.
     */
    private static final String UUID_NETWORK_SETUP_FAILED = "12eeffbf-08f8-40ed-9a00-d344199552fc";

    /**
     * Action to start the slice purchase application and display the
     * performance boost notification.
     */
    public static final String ACTION_START_SLICE_PURCHASE_APP =
            "com.android.phone.slice.action.START_SLICE_PURCHASE_APP";
    /** Action indicating the premium capability purchase was not completed in time. */
    public static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_TIMEOUT =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_TIMEOUT";
    /** Action indicating the performance boost notification or WebView was canceled. */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_CANCELED =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_CANCELED";
    /** Action indicating a carrier error prevented premium capability purchase. */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR";
    /**
     * Action indicating a Telephony or slice purchase application error prevented premium
     * capability purchase.
     */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED";
    /** Action indicating the purchase request was not made on the default data subscription. */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION =
            "com.android.phone.slice.action."
                    + "SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION";
    /**
     * Action indicating the performance boost notification was not shown because the user
     * disabled notifications for the application or channel.
     */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED";
    /** Action indicating the purchase request was successful. */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_SUCCESS =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_SUCCESS";
    /**
     * Action indicating the slice purchase application showed the performance boost notification.
     */
    private static final String ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN =
            "com.android.phone.slice.action.SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN";

    /** Extra for the phone index to send to the slice purchase application. */
    public static final String EXTRA_PHONE_ID = "com.android.phone.slice.extra.PHONE_ID";
    /** Extra for the subscription ID to send to the slice purchase application. */
    public static final String EXTRA_SUB_ID = "com.android.phone.slice.extra.SUB_ID";
    /**
     * Extra for the requested premium capability to purchase from the slice purchase application.
     */
    public static final String EXTRA_PREMIUM_CAPABILITY =
            "com.android.phone.slice.extra.PREMIUM_CAPABILITY";
    /** Extra for the carrier URL to display to the user to allow premium capability purchase. */
    public static final String EXTRA_PURCHASE_URL = "com.android.phone.slice.extra.PURCHASE_URL";
    /** Extra for the duration of the purchased premium capability. */
    public static final String EXTRA_PURCHASE_DURATION =
            "com.android.phone.slice.extra.PURCHASE_DURATION";
    /** Extra for the {@link FailureCode} why the premium capability purchase failed. */
    public static final String EXTRA_FAILURE_CODE = "com.android.phone.slice.extra.FAILURE_CODE";
    /** Extra for the human-readable reason why the premium capability purchase failed. */
    public static final String EXTRA_FAILURE_REASON =
            "com.android.phone.slice.extra.FAILURE_REASON";
    /** Extra for the user's carrier. */
    public static final String EXTRA_CARRIER = "com.android.phone.slice.extra.CARRIER";
    /** Extra for the user data received from the entitlement service to send to the webapp. */
    public static final String EXTRA_USER_DATA = "com.android.phone.slice.extra.USER_DATA";
    /** Extra for the contents type received from the entitlement service to send to the webapp. */
    public static final String EXTRA_CONTENTS_TYPE = "com.android.phone.slice.extra.CONTENTS_TYPE";
    /**
     * Extra for the canceled PendingIntent that the slice purchase application can send as a
     * response if the performance boost notification or WebView was canceled by the user.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_CANCELED}.
     */
    public static final String EXTRA_INTENT_CANCELED =
            "com.android.phone.slice.extra.INTENT_CANCELED";
    /**
     * Extra for the carrier error PendingIntent that the slice purchase application can send as a
     * response if the premium capability purchase request failed due to a carrier error.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR}.
     * Sender can modify the intent to specify the failure code and reason for failure with
     * {@link #EXTRA_FAILURE_CODE} and {@link #EXTRA_FAILURE_REASON}.
     */
    public static final String EXTRA_INTENT_CARRIER_ERROR =
            "com.android.phone.slice.extra.INTENT_CARRIER_ERROR";
    /**
     * Extra for the request failed PendingIntent that the slice purchase application can send as a
     * response if the premium capability purchase request failed due to an error in Telephony or
     * the slice purchase application.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED}.
     */
    public static final String EXTRA_INTENT_REQUEST_FAILED =
            "com.android.phone.slice.extra.INTENT_REQUEST_FAILED";
    /**
     * Extra for the not-default data subscription ID PendingIntent that the slice purchase
     * application can send as a response if the premium capability purchase request failed because
     * it was not requested on the default data subscription.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION}.
     */
    public static final String EXTRA_INTENT_NOT_DEFAULT_DATA_SUBSCRIPTION =
            "com.android.phone.slice.extra.INTENT_NOT_DEFAULT_DATA_SUBSCRIPTION";
    /**
     * Extra for the notifications disabled PendingIntent that the slice purchase application can
     * send as a response if the premium capability purchase request failed because the user
     * disabled notifications for the application or channel.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED}.
     */
    public static final String EXTRA_INTENT_NOTIFICATIONS_DISABLED =
            "com.android.phone.slice.extra.INTENT_NOTIFICATIONS_DISABLED";
    /**
     * Extra for the success PendingIntent that the slice purchase application can send as a
     * response if the premium capability purchase request was successful.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_SUCCESS}.
     * Sender can modify the intent to specify a purchase duration with
     * {@link #EXTRA_PURCHASE_DURATION}.
     */
    public static final String EXTRA_INTENT_SUCCESS =
            "com.android.phone.slice.extra.INTENT_SUCCESS";
    /**
     * Extra for the PendingIntent that the slice purchase application can send to indicate
     * that it displayed the performance boost notification to the user.
     * Sends {@link #ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN}.
     */
    public static final String EXTRA_INTENT_NOTIFICATION_SHOWN =
            "com.android.phone.slice.extra.NOTIFICATION_SHOWN";

    /** Component name for the SlicePurchaseBroadcastReceiver. */
    private static final ComponentName SLICE_PURCHASE_APP_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.android.carrierdefaultapp/.SlicePurchaseBroadcastReceiver");

    /** Shared preference name for performance boost notification preferences. */
    private static final String PERFORMANCE_BOOST_NOTIFICATION_PREFERENCES =
            "performance_boost_notification_preferences";
    /** Shared preference key for daily count of performance boost notifications. */
    private static final String KEY_DAILY_NOTIFICATION_COUNT = "daily_notification_count";
    /** Shared preference key for monthly count of performance boost notifications. */
    private static final String KEY_MONTHLY_NOTIFICATION_COUNT = "monthly_notification_count";
    /** DeviceConfig key for whether the slicing upsell feature is enabled. */
    private static final String KEY_ENABLE_SLICING_UPSELL = "enable_slicing_upsell";
    /**
     * Shared preference key for the date the daily or monthly counts of performance boost
     * notifications were last reset.
     * A String with ISO-8601 format {@code YYYY-MM-DD}, from {@link LocalDate#toString}.
     * For example, if the count was last updated on December 25, 2020, this would be `2020-12-25`.
     */
    private static final String KEY_NOTIFICATION_COUNT_LAST_RESET_DATE =
            "notification_count_last_reset_date";

    /** Map of phone ID -> SlicePurchaseController instances. */
    @NonNull private static final Map<Integer, SlicePurchaseController> sInstances =
            new HashMap<>();

    /** The Phone instance used to create the SlicePurchaseController. */
    @NonNull private final Phone mPhone;
    /** Feature flags to control behavior and errors. */
    @NonNull private final FeatureFlags mFeatureFlags;
    /** The set of capabilities that are pending network setup. */
    @NonNull private final Set<Integer> mPendingSetupCapabilities = new HashSet<>();
    /** The set of throttled capabilities. */
    @NonNull private final Set<Integer> mThrottledCapabilities = new HashSet<>();
    /** A map of pending capabilities to the onComplete message for the purchase request. */
    @NonNull private final Map<Integer, Message> mPendingPurchaseCapabilities = new HashMap<>();
    /**
     * A map of capabilities to the SlicePurchaseControllerBroadcastReceiver to handle
     * slice purchase application responses.
     */
    @NonNull private final Map<Integer, SlicePurchaseControllerBroadcastReceiver>
            mSlicePurchaseControllerBroadcastReceivers = new HashMap<>();
    /** The current network slicing configuration. */
    @Nullable private NetworkSlicingConfig mSlicingConfig;

    /** LocalDate to use when resetting notification counts. {@code null} except when testing. */
    @Nullable private LocalDate mLocalDate;
    /** The number of times the performance boost notification has been shown today. */
    private int mDailyCount;
    /** The number of times the performance boost notification has been shown this month. */
    private int mMonthlyCount;
    /** {@code true} if the slicing upsell feature is enabled and {@code false} otherwise. */
    private boolean mIsSlicingUpsellEnabled;

    /**
     * BroadcastReceiver to receive responses from the slice purchase application.
     */
    private class SlicePurchaseControllerBroadcastReceiver extends BroadcastReceiver {
        @TelephonyManager.PremiumCapability private final int mCapability;

        /**
         * Create a SlicePurchaseControllerBroadcastReceiver for the given capability
         *
         * @param capability The requested premium capability to listen to response for.
         */
        SlicePurchaseControllerBroadcastReceiver(
                @TelephonyManager.PremiumCapability int capability) {
            mCapability = capability;
        }

        /**
         * Process responses from the slice purchase application.
         *
         * @param context The Context in which the receiver is running.
         * @param intent The Intent being received.
         */
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            logd("SlicePurchaseControllerBroadcastReceiver("
                    + TelephonyManager.convertPremiumCapabilityToString(mCapability)
                    + ") received action: " + action);
            int phoneId = intent.getIntExtra(EXTRA_PHONE_ID,
                    SubscriptionManager.INVALID_PHONE_INDEX);
            int capability = intent.getIntExtra(EXTRA_PREMIUM_CAPABILITY,
                    PREMIUM_CAPABILITY_INVALID);
            if (SlicePurchaseController.getInstance(phoneId) == null) {
                reportAnomaly(UUID_INVALID_PHONE_ID, "SlicePurchaseControllerBroadcastReceiver( "
                        + TelephonyManager.convertPremiumCapabilityToString(mCapability)
                        + ") received invalid phoneId: " + phoneId);
                return;
            } else if (capability != mCapability) {
                logd("SlicePurchaseControllerBroadcastReceiver("
                        + TelephonyManager.convertPremiumCapabilityToString(mCapability)
                        + ") ignoring intent for capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability));
                return;
            }
            switch (action) {
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_CANCELED: {
                    logd("Slice purchase application canceled for capability: "
                            + TelephonyManager.convertPremiumCapabilityToString(capability));
                    SlicePurchaseController.getInstance(phoneId)
                            .handlePurchaseResult(capability,
                            TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED,
                            true);
                    break;
                }
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR: {
                    int failureCode = intent.getIntExtra(EXTRA_FAILURE_CODE, FAILURE_CODE_UNKNOWN);
                    String failureReason = intent.getStringExtra(EXTRA_FAILURE_REASON);
                    SlicePurchaseController.getInstance(phoneId).onCarrierError(
                            capability, failureCode, failureReason);
                    break;
                }
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED: {
                    logd("Purchase premium capability request failed for capability: "
                            + TelephonyManager.convertPremiumCapabilityToString(capability));
                    SlicePurchaseController.getInstance(phoneId)
                            .handlePurchaseResult(capability,
                            TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_REQUEST_FAILED,
                            false);
                    break;
                }
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION: {
                    logd("Purchase premium capability request was not made on the default data "
                            + "subscription for capability: "
                            + TelephonyManager.convertPremiumCapabilityToString(capability));
                    SlicePurchaseController.getInstance(phoneId)
                            .handlePurchaseResult(capability,
                            PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION,
                            false);
                    break;
                }
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED: {
                    logd("Slice purchase application unable to show notification for capability: "
                            + TelephonyManager.convertPremiumCapabilityToString(capability)
                            + " because the user has disabled notifications.");
                    int error = mFeatureFlags.slicingAdditionalErrorCodes()
                            ? TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_DISABLED
                            : TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED;
                    SlicePurchaseController.getInstance(phoneId)
                            .handlePurchaseResult(capability, error, true);
                    break;
                }
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_SUCCESS: {
                    long duration = intent.getLongExtra(EXTRA_PURCHASE_DURATION, 0);
                    SlicePurchaseController.getInstance(phoneId).onCarrierSuccess(
                            capability, duration);
                    break;
                }
                case ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN: {
                    SlicePurchaseController.getInstance(phoneId).onNotificationShown();
                    break;
                }
                default:
                    reportAnomaly(UUID_UNKNOWN_ACTION, "SlicePurchaseControllerBroadcastReceiver("
                            + TelephonyManager.convertPremiumCapabilityToString(mCapability)
                            + ") received unknown action: " + action);
                    break;
            }
        }
    }

    /**
     * Get the static SlicePurchaseController instance for the given phone or create one if it
     * doesn't exist.
     *
     * @param phone The Phone to get the SlicePurchaseController for.
     * @return The static SlicePurchaseController instance.
     */
    @NonNull public static synchronized SlicePurchaseController getInstance(@NonNull Phone phone,
            @NonNull FeatureFlags featureFlags) {
        // TODO: Add listeners for multi sim setting changed (maybe carrier config changed too)
        //  that dismiss notifications and update SlicePurchaseController instance
        int phoneId = phone.getPhoneId();
        if (sInstances.get(phoneId) == null) {
            HandlerThread handlerThread = new HandlerThread("SlicePurchaseController");
            handlerThread.start();
            sInstances.put(phoneId,
                    new SlicePurchaseController(phone, featureFlags, handlerThread.getLooper()));
        }
        return sInstances.get(phoneId);
    }

    /**
     * Get the static SlicePurchaseController instance for the given phone ID if it exists.
     *
     * @param phoneId The phone ID to get the SlicePurchaseController for.
     * @return The static SlicePurchaseController instance or
     *         {@code null} if it hasn't been created yet.
     */
    @Nullable private static SlicePurchaseController getInstance(int phoneId) {
        return sInstances.get(phoneId);
    }

    /**
     * Create a SlicePurchaseController for the given phone on the given looper.
     *
     * @param phone The Phone to create the SlicePurchaseController for.
     * @param featureFlags The FeatureFlags that are supported.
     * @param looper The Looper to run the SlicePurchaseController on.
     */
    @VisibleForTesting
    public SlicePurchaseController(@NonNull Phone phone, @NonNull FeatureFlags featureFlags,
            @NonNull Looper looper) {
        super(looper);
        mPhone = phone;
        mFeatureFlags = featureFlags;
        // TODO: Create a cached value for slicing config in DataIndication and initialize here
        mPhone.mCi.registerForSlicingConfigChanged(this, EVENT_SLICING_CONFIG_CHANGED, null);
        mIsSlicingUpsellEnabled = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_TELEPHONY, KEY_ENABLE_SLICING_UPSELL, false);
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_TELEPHONY, Runnable::run,
                properties -> {
                    if (TextUtils.equals(DeviceConfig.NAMESPACE_TELEPHONY,
                            properties.getNamespace())) {
                        sendEmptyMessage(EVENT_DEVICE_CONFIG_CHANGED);
                    }
                });
        updateNotificationCounts();
    }

    /**
     * Set the LocalDate to use for resetting daily and monthly notification counts.
     *
     * @param localDate The LocalDate instance to use.
     */
    @VisibleForTesting
    public void setLocalDate(@NonNull LocalDate localDate) {
        mLocalDate = localDate;
    }

    /**
     * Set the NetworkSlicingConfig to use for determining whether the premium capability was
     * successfully set up on the carrier network.
     *
     * @param slicingConfig The LocalDate instance to use.
     */
    @VisibleForTesting
    public void setSlicingConfig(@NonNull NetworkSlicingConfig slicingConfig) {
        mSlicingConfig = slicingConfig;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case EVENT_PURCHASE_UNTHROTTLED: {
                int capability = (int) msg.obj;
                logd("EVENT_PURCHASE_UNTHROTTLED: for capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability));
                mThrottledCapabilities.remove(capability);
                break;
            }
            case EVENT_SLICING_CONFIG_CHANGED: {
                AsyncResult ar = (AsyncResult) msg.obj;
                NetworkSlicingConfig config = (NetworkSlicingConfig) ar.result;
                logd("EVENT_SLICING_CONFIG_CHANGED: previous= " + mSlicingConfig);
                logd("EVENT_SLICING_CONFIG_CHANGED: current= " + config);
                mSlicingConfig = config;
                onSlicingConfigChanged();
                break;
            }
            case EVENT_START_SLICE_PURCHASE_APP: {
                int capability = (int) msg.obj;
                logd("EVENT_START_SLICE_PURCHASE_APP: "
                        + TelephonyManager.convertPremiumCapabilityToString(capability));
                onStartSlicePurchaseApplication(capability);
                break;
            }
            case EVENT_PURCHASE_TIMEOUT: {
                int capability = (int) msg.obj;
                logd("EVENT_PURCHASE_TIMEOUT: for capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability));
                onTimeout(capability);
                break;
            }
            case EVENT_SETUP_TIMEOUT:
                int capability = (int) msg.obj;
                logd("EVENT_SETUP_TIMEOUT: for capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability));
                onSetupTimeout(capability);
                break;
            case EVENT_DEVICE_CONFIG_CHANGED:
                boolean isSlicingUpsellEnabled = DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_TELEPHONY, KEY_ENABLE_SLICING_UPSELL, false);
                if (isSlicingUpsellEnabled != mIsSlicingUpsellEnabled) {
                    logd("EVENT_DEVICE_CONFIG_CHANGED: from " + mIsSlicingUpsellEnabled + " to "
                            + isSlicingUpsellEnabled);
                    mIsSlicingUpsellEnabled = isSlicingUpsellEnabled;
                }
                break;
            default:
                loge("Unknown event: " + msg.obj);
        }
    }

    /**
     * Check whether the given premium capability is available for purchase from the carrier.
     *
     * @param capability The premium capability to check.
     * @return Whether the given premium capability is available to purchase.
     */
    public boolean isPremiumCapabilityAvailableForPurchase(
            @TelephonyManager.PremiumCapability int capability) {
        if (!arePremiumCapabilitiesSupportedByDevice()) {
            logd("Premium capabilities unsupported by the device.");
            return false;
        }
        if (!isPremiumCapabilitySupportedByCarrier(capability)) {
            logd("Premium capability "
                    + TelephonyManager.convertPremiumCapabilityToString(capability)
                    + " unsupported by the carrier.");
            return false;
        }
        if (!isDefaultDataSub()) {
            logd("Premium capability "
                    + TelephonyManager.convertPremiumCapabilityToString(capability)
                    + " unavailable on the non-default data subscription.");
            return false;
        }
        logd("Premium capability "
                + TelephonyManager.convertPremiumCapabilityToString(capability)
                + " is available for purchase.");
        return true;
    }

    /**
     * Purchase the given premium capability from the carrier.
     *
     * @param capability The premium capability to purchase.
     * @param onComplete The callback message to send when the purchase request is complete.
     */
    public synchronized void purchasePremiumCapability(
            @TelephonyManager.PremiumCapability int capability, @NonNull Message onComplete) {
        logd("purchasePremiumCapability: "
                + TelephonyManager.convertPremiumCapabilityToString(capability));
        // Check whether the premium capability can be purchased.
        if (!arePremiumCapabilitiesSupportedByDevice()) {
            sendPurchaseResult(capability,
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED,
                    onComplete);
            return;
        }
        if (!isPremiumCapabilitySupportedByCarrier(capability)) {
            sendPurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED,
                    onComplete);
            return;
        }
        if (!isDefaultDataSub()) {
            sendPurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION,
                    onComplete);
            return;
        }
        if (isSlicingConfigActive(capability)) {
            sendPurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED,
                    onComplete);
            return;
        }
        if (mPendingSetupCapabilities.contains(capability)) {
            sendPurchaseResult(capability,
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_PENDING_NETWORK_SETUP,
                    onComplete);
            return;
        }
        if (mThrottledCapabilities.contains(capability)) {
            sendPurchaseResult(capability,
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED,
                    onComplete);
            return;
        }
        if (!isNetworkAvailable()) {
            sendPurchaseResult(capability,
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE,
                    onComplete);
            return;
        }

        if (mPendingPurchaseCapabilities.containsKey(capability)) {
            sendPurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS,
                    onComplete);
            return;
        }

        // All state checks passed. Mark purchase pending and start the slice purchase application.
        // Process through the handler since this method is synchronized.
        mPendingPurchaseCapabilities.put(capability, onComplete);
        sendMessage(obtainMessage(EVENT_START_SLICE_PURCHASE_APP, capability));
    }

    private void sendPurchaseResult(@TelephonyManager.PremiumCapability int capability,
            @TelephonyManager.PurchasePremiumCapabilityResult int result,
            @NonNull Message onComplete) {
        // Send the onComplete message with the purchase result.
        logd("Purchase result for capability "
                + TelephonyManager.convertPremiumCapabilityToString(capability)
                + ": " + TelephonyManager.convertPurchaseResultToString(result));
        AsyncResult.forMessage(onComplete, result, null);
        onComplete.sendToTarget();
    }

    private void handlePurchaseResult(
            @TelephonyManager.PremiumCapability int capability,
            @TelephonyManager.PurchasePremiumCapabilityResult int result, boolean throttle) {
        SlicePurchaseControllerBroadcastReceiver receiver =
                mSlicePurchaseControllerBroadcastReceivers.remove(capability);
        if (receiver != null) {
            mPhone.getContext().unregisterReceiver(receiver);
        }
        removeMessages(EVENT_PURCHASE_TIMEOUT, capability);
        if (throttle) {
            throttleCapability(capability, getThrottleDuration(result));
        }
        sendPurchaseResult(capability, result, mPendingPurchaseCapabilities.remove(capability));
    }

    private void throttleCapability(@TelephonyManager.PremiumCapability int capability,
            long throttleDuration) {
        // Throttle subsequent requests if necessary.
        if (!mThrottledCapabilities.contains(capability)) {
            if (throttleDuration > 0) {
                logd("Throttle purchase requests for capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability) + " for "
                        + TimeUnit.MILLISECONDS.toMinutes(throttleDuration) + " minutes.");
                mThrottledCapabilities.add(capability);
                sendMessageDelayed(obtainMessage(EVENT_PURCHASE_UNTHROTTLED, capability),
                        throttleDuration);
            }
        } else {
            reportAnomaly(UUID_CAPABILITY_THROTTLED_TWICE,
                    TelephonyManager.convertPremiumCapabilityToString(capability)
                            + " is already throttled.");
        }
    }

    private void onSlicingConfigChanged() {
        for (int capability : new int[] {TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY}) {
            if (isSlicingConfigActive(capability) && hasMessages(EVENT_SETUP_TIMEOUT, capability)) {
                logd("Successfully set up slicing configuration for "
                        + TelephonyManager.convertPremiumCapabilityToString(capability));
                mPendingSetupCapabilities.remove(capability);
                removeMessages(EVENT_SETUP_TIMEOUT, capability);
            }
        }
    }

    /**
     * @return A new PremiumNetworkEntitlementApi object.
     */
    @VisibleForTesting
    public PremiumNetworkEntitlementApi getPremiumNetworkEntitlementApi() {
        return new PremiumNetworkEntitlementApi(mPhone, getCarrierConfigs());
    }

    private void onStartSlicePurchaseApplication(
            @TelephonyManager.PremiumCapability int capability) {
        updateNotificationCounts();
        if (mMonthlyCount >= getCarrierConfigs().getInt(
                CarrierConfigManager.KEY_PREMIUM_CAPABILITY_MAXIMUM_MONTHLY_NOTIFICATION_COUNT_INT)
                || mDailyCount >= getCarrierConfigs().getInt(
                CarrierConfigManager.KEY_PREMIUM_CAPABILITY_MAXIMUM_DAILY_NOTIFICATION_COUNT_INT)) {
            logd("Reached maximum number of performance boost notifications.");
            handlePurchaseResult(capability,
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED, false);
            return;
        }

        final PremiumNetworkEntitlementApi premiumNetworkEntitlementApi =
                getPremiumNetworkEntitlementApi();
        PremiumNetworkEntitlementResponse premiumNetworkEntitlementResponse =
                premiumNetworkEntitlementApi.checkEntitlementStatus(capability);

        // invalid response for entitlement check
        if (premiumNetworkEntitlementResponse == null
                || premiumNetworkEntitlementResponse.isInvalidResponse()) {
            loge("Invalid response for entitlement check: " + premiumNetworkEntitlementResponse);
            handlePurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR, true);
            return;
        }

        if (!premiumNetworkEntitlementResponse.isPremiumNetworkCapabilityAllowed()) {
            loge("Entitlement Check: Not allowed.");
            handlePurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED, true);
            return;
        }

        if (premiumNetworkEntitlementResponse.isAlreadyPurchased()) {
            logd("Entitlement Check: Already purchased/provisioned.");
            handlePurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED, true);
            return;
        }

        if (premiumNetworkEntitlementResponse.isProvisioningInProgress()) {
            logd("Entitlement Check: In progress.");
            handlePurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS, true);
            return;
        }

        String purchaseUrl = getPurchaseUrl(premiumNetworkEntitlementResponse);
        String carrier = getSimOperator();
        if (TextUtils.isEmpty(purchaseUrl) || TextUtils.isEmpty(carrier)) {
            handlePurchaseResult(capability,
                    PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED, false);
            return;
        }

        // Start timeout for purchase completion.
        long timeout = getCarrierConfigs().getLong(CarrierConfigManager
                .KEY_PREMIUM_CAPABILITY_NOTIFICATION_DISPLAY_TIMEOUT_MILLIS_LONG);
        logd("Start purchase timeout for "
                + TelephonyManager.convertPremiumCapabilityToString(capability) + " for "
                + TimeUnit.MILLISECONDS.toMinutes(timeout) + " minutes.");
        sendMessageDelayed(obtainMessage(EVENT_PURCHASE_TIMEOUT, capability), timeout);

        // Broadcast start intent to start the slice purchase application
        Intent intent = new Intent(ACTION_START_SLICE_PURCHASE_APP);
        intent.setComponent(SLICE_PURCHASE_APP_COMPONENT_NAME);
        intent.putExtra(EXTRA_PHONE_ID, mPhone.getPhoneId());
        intent.putExtra(EXTRA_SUB_ID, mPhone.getSubId());
        intent.putExtra(EXTRA_PREMIUM_CAPABILITY, capability);
        intent.putExtra(EXTRA_PURCHASE_URL, purchaseUrl);
        intent.putExtra(EXTRA_CARRIER, carrier);
        intent.putExtra(EXTRA_USER_DATA, premiumNetworkEntitlementResponse.mServiceFlowUserData);
        intent.putExtra(EXTRA_CONTENTS_TYPE,
                premiumNetworkEntitlementResponse.mServiceFlowContentsType);
        intent.putExtra(EXTRA_INTENT_CANCELED, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_CANCELED, capability, false));
        intent.putExtra(EXTRA_INTENT_CARRIER_ERROR, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR, capability, true));
        intent.putExtra(EXTRA_INTENT_REQUEST_FAILED, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED, capability, false));
        intent.putExtra(EXTRA_INTENT_NOT_DEFAULT_DATA_SUBSCRIPTION, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION, capability,
                false));
        intent.putExtra(EXTRA_INTENT_NOTIFICATIONS_DISABLED, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED, capability, false));
        intent.putExtra(EXTRA_INTENT_SUCCESS, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_SUCCESS, capability, true));
        intent.putExtra(EXTRA_INTENT_NOTIFICATION_SHOWN, createPendingIntent(
                ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN, capability, false));
        logd("Broadcasting start intent to SlicePurchaseBroadcastReceiver.");
        mPhone.getContext().sendBroadcast(intent);

        // Listen for responses from the slice purchase application
        mSlicePurchaseControllerBroadcastReceivers.put(capability,
                new SlicePurchaseControllerBroadcastReceiver(capability));
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_CANCELED);
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_CARRIER_ERROR);
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_REQUEST_FAILED);
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_NOT_DEFAULT_DATA_SUBSCRIPTION);
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATIONS_DISABLED);
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_SUCCESS);
        filter.addAction(ACTION_SLICE_PURCHASE_APP_RESPONSE_NOTIFICATION_SHOWN);
        mPhone.getContext().registerReceiver(
                mSlicePurchaseControllerBroadcastReceivers.get(capability), filter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Get a valid purchase URL from either entitlement response or carrier configs, if one exists.
     *
     * @param entitlementResponse The entitlement response to get the purchase URL from.
     * @return A valid purchase URL or an empty string if one doesn't exist.
     */
    @VisibleForTesting
    @NonNull public String getPurchaseUrl(
            @NonNull PremiumNetworkEntitlementResponse entitlementResponse) {
        String purchaseUrl = entitlementResponse.mServiceFlowURL;
        if (!isUrlValid(purchaseUrl)) {
            purchaseUrl = getCarrierConfigs().getString(
                    CarrierConfigManager.KEY_PREMIUM_CAPABILITY_PURCHASE_URL_STRING);
            if (!isUrlValid(purchaseUrl)) {
                purchaseUrl = "";
            }
        }
        return purchaseUrl;
    }

    /**
     * Get the SIM operator. This is the carrier name from the SIM rather than from the network,
     * which will be the same regardless of whether the user is roaming or not.
     *
     * @return The operator name from the SIM.
     */
    @VisibleForTesting
    @Nullable public String getSimOperator() {
        if (mPhone.getPhoneId() < TelephonyProperties.icc_operator_alpha().size()) {
            return TelephonyProperties.icc_operator_alpha().get(mPhone.getPhoneId());
        }
        return null;
    }

    /**
     * Create the PendingIntent to allow the slice purchase application to send back responses.
     *
     * @param action The action that will be sent for this PendingIntent
     * @param capability The premium capability that was requested.
     * @param mutable {@code true} if the PendingIntent should be mutable and
     *                {@code false} if it should be immutable.
     * @return The PendingIntent for the given action and capability.
     */
    @VisibleForTesting
    @NonNull public PendingIntent createPendingIntent(@NonNull String action,
            @TelephonyManager.PremiumCapability int capability, boolean mutable) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_PHONE_ID, mPhone.getPhoneId());
        intent.putExtra(EXTRA_PREMIUM_CAPABILITY, capability);
        intent.setPackage(mPhone.getContext().getPackageName());
        return PendingIntent.getBroadcast(mPhone.getContext(), capability, intent,
                PendingIntent.FLAG_CANCEL_CURRENT
                        | (mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE));
    }

    private void onTimeout(@TelephonyManager.PremiumCapability int capability) {
        logd("onTimeout: " + TelephonyManager.convertPremiumCapabilityToString(capability));
        // Broadcast timeout intent to clean up the slice purchase notification and activity
        Intent intent = new Intent(ACTION_SLICE_PURCHASE_APP_RESPONSE_TIMEOUT);
        intent.setComponent(SLICE_PURCHASE_APP_COMPONENT_NAME);
        intent.putExtra(EXTRA_PHONE_ID, mPhone.getPhoneId());
        intent.putExtra(EXTRA_PREMIUM_CAPABILITY, capability);
        logd("Broadcasting timeout intent to SlicePurchaseBroadcastReceiver.");
        mPhone.getContext().sendBroadcast(intent);

        handlePurchaseResult(
                capability, TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT, true);
    }

    private void onCarrierError(@TelephonyManager.PremiumCapability int capability,
            @FailureCode int failureCode, @Nullable String failureReason) {
        logd("Carrier error for capability: "
                + TelephonyManager.convertPremiumCapabilityToString(capability) + " with code: "
                + convertFailureCodeToString(failureCode) + " and reason: " + failureReason);
        if (failureCode == FAILURE_CODE_UNKNOWN && !TextUtils.isEmpty(failureReason)) {
            reportAnomaly(UUID_UNKNOWN_FAILURE_CODE,
                    "Failure code needs to be added for: " + failureReason);
        }
        handlePurchaseResult(capability,
                TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR, true);
    }

    private void onCarrierSuccess(@TelephonyManager.PremiumCapability int capability,
            long duration) {
        logd("Successfully purchased premium capability "
                + TelephonyManager.convertPremiumCapabilityToString(capability) + (duration > 0
                ? " for " + TimeUnit.MILLISECONDS.toMinutes(duration) + " minutes." : "."));
        mPendingSetupCapabilities.add(capability);
        long setupDuration = getCarrierConfigs().getLong(
                CarrierConfigManager.KEY_PREMIUM_CAPABILITY_NETWORK_SETUP_TIME_MILLIS_LONG);
        logd("Waiting " + TimeUnit.MILLISECONDS.toMinutes(setupDuration) + " minutes for the "
                + "network to set up the slicing configuration.");
        sendMessageDelayed(obtainMessage(EVENT_SETUP_TIMEOUT, capability), setupDuration);
        handlePurchaseResult(
                capability, TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS, false);
    }

    private void onSetupTimeout(@TelephonyManager.PremiumCapability int capability) {
        logd("onSetupTimeout: " + TelephonyManager.convertPremiumCapabilityToString(capability));
        mPendingSetupCapabilities.remove(capability);
        if (!isSlicingConfigActive(capability)) {
            reportAnomaly(UUID_NETWORK_SETUP_FAILED,
                    "Failed to set up slicing configuration for capability "
                            + TelephonyManager.convertPremiumCapabilityToString(capability)
                            + " within the time specified.");
        }
    }

    private void onNotificationShown() {
        SharedPreferences sp = mPhone.getContext().getSharedPreferences(
                PERFORMANCE_BOOST_NOTIFICATION_PREFERENCES, 0);
        mDailyCount = sp.getInt((KEY_DAILY_NOTIFICATION_COUNT + mPhone.getPhoneId()), 0) + 1;
        mMonthlyCount = sp.getInt((KEY_MONTHLY_NOTIFICATION_COUNT + mPhone.getPhoneId()), 0) + 1;
        logd("Performance boost notification was shown " + mDailyCount + " times today and "
                + mMonthlyCount + " times this month.");

        SharedPreferences.Editor editor = sp.edit();
        editor.putInt((KEY_DAILY_NOTIFICATION_COUNT + mPhone.getPhoneId()), mDailyCount);
        editor.putInt((KEY_MONTHLY_NOTIFICATION_COUNT + mPhone.getPhoneId()), mMonthlyCount);
        editor.apply();

        // Don't call updateNotificationCounts here because it will be called whenever a new
        // purchase request comes in or when SlicePurchaseController is initialized.
    }

    /**
     * Update the current daily and monthly performance boost notification counts.
     * If it has been at least a day since the last daily reset or at least a month since the last
     * monthly reset, reset the current daily or monthly notification counts.
     */
    @VisibleForTesting
    public void updateNotificationCounts() {
        SharedPreferences sp = mPhone.getContext().getSharedPreferences(
                PERFORMANCE_BOOST_NOTIFICATION_PREFERENCES, 0);
        mDailyCount = sp.getInt((KEY_DAILY_NOTIFICATION_COUNT + mPhone.getPhoneId()), 0);
        mMonthlyCount = sp.getInt((KEY_MONTHLY_NOTIFICATION_COUNT + mPhone.getPhoneId()), 0);

        if (mLocalDate == null) {
            // Standardize to UTC to prevent default time zone dependency
            mLocalDate = LocalDate.now(ZoneId.of("UTC"));
        }
        LocalDate lastLocalDate = LocalDate.of(1, 1, 1);
        String lastLocalDateString = sp.getString(
                (KEY_NOTIFICATION_COUNT_LAST_RESET_DATE + mPhone.getPhoneId()), "");
        if (!TextUtils.isEmpty(lastLocalDateString)) {
            try {
                lastLocalDate = LocalDate.parse(lastLocalDateString);
            } catch (DateTimeParseException e) {
                loge("Error parsing LocalDate from SharedPreferences: " + e);
            }
        }
        logd("updateNotificationCounts: mDailyCount=" + mDailyCount + ", mMonthlyCount="
                + mMonthlyCount + ", mLocalDate=" + mLocalDate + ", lastLocalDate="
                + lastLocalDate);

        boolean resetMonthly = lastLocalDate.getYear() != mLocalDate.getYear()
                || lastLocalDate.getMonthValue() != mLocalDate.getMonthValue();
        boolean resetDaily = resetMonthly
                || lastLocalDate.getDayOfMonth() != mLocalDate.getDayOfMonth();
        if (resetDaily) {
            logd("Resetting daily" + (resetMonthly ? " and monthly" : "") + " notification count.");
            SharedPreferences.Editor editor = sp.edit();
            if (resetMonthly) {
                mMonthlyCount = 0;
                editor.putInt((KEY_MONTHLY_NOTIFICATION_COUNT + mPhone.getPhoneId()),
                        mMonthlyCount);
            }
            mDailyCount = 0;
            editor.putInt((KEY_DAILY_NOTIFICATION_COUNT + mPhone.getPhoneId()), mDailyCount);
            editor.putString((KEY_NOTIFICATION_COUNT_LAST_RESET_DATE + mPhone.getPhoneId()),
                    mLocalDate.toString());
            editor.apply();
        }
    }

    @Nullable private PersistableBundle getCarrierConfigs() {
        return mPhone.getContext().getSystemService(CarrierConfigManager.class)
                .getConfigForSubId(mPhone.getSubId());
    }

    private long getThrottleDuration(@TelephonyManager.PurchasePremiumCapabilityResult int result) {
        if (result == TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED
                || result == TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT
                || result == TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_DISABLED) {
            return getCarrierConfigs().getLong(CarrierConfigManager
                    .KEY_PREMIUM_CAPABILITY_NOTIFICATION_BACKOFF_HYSTERESIS_TIME_MILLIS_LONG);
        }
        if (result == TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED
                || result == TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR) {
            return getCarrierConfigs().getLong(CarrierConfigManager
                    .KEY_PREMIUM_CAPABILITY_PURCHASE_CONDITION_BACKOFF_HYSTERESIS_TIME_MILLIS_LONG);
        }
        return 0;
    }

    private boolean isPremiumCapabilitySupportedByCarrier(
            @TelephonyManager.PremiumCapability int capability) {
        int[] supportedCapabilities = getCarrierConfigs().getIntArray(
                CarrierConfigManager.KEY_SUPPORTED_PREMIUM_CAPABILITIES_INT_ARRAY);
        if (supportedCapabilities == null) {
            logd("No premium capabilities are supported by the carrier.");
            return false;
        }
        return Arrays.stream(supportedCapabilities)
                .anyMatch(supportedCapability -> supportedCapability == capability);
    }

    private boolean isUrlValid(@Nullable String url) {
        if (!URLUtil.isValidUrl(url)) {
            loge("Invalid URL: " + url);
            return false;
        }
        if (URLUtil.isAssetUrl(url) && !url.equals(SLICE_PURCHASE_TEST_FILE)) {
            loge("Invalid asset: " + url);
            return false;
        }
        try {
            new URL(url).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            loge("Invalid URI: " + url);
            return false;
        }
        logd("Valid URL: " + url);
        return true;
    }

    private boolean arePremiumCapabilitiesSupportedByDevice() {
        if ((mPhone.getCachedAllowedNetworkTypesBitmask()
                & TelephonyManager.NETWORK_TYPE_BITMASK_NR) == 0) {
            logd("Premium capabilities unsupported because NR is not allowed on the device.");
            return false;
        }
        if (!mIsSlicingUpsellEnabled) {
            logd("Premium capabilities unsupported because "
                    + "slicing upsell is disabled on the device.");
        }
        return mIsSlicingUpsellEnabled;
    }

    private boolean isDefaultDataSub() {
        return mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId();
    }

    /**
     * Check whether the current network slicing configuration indicates that the given premium
     * capability is active and set up on the carrier network.
     * @param capability The premium capability to check for.
     * @return {@code true} if the slicing config indicates the capability is active and
     * {@code false} otherwise.
     */
    @VisibleForTesting
    public boolean isSlicingConfigActive(@TelephonyManager.PremiumCapability int capability) {
        if (mSlicingConfig == null) {
            return false;
        }
        for (UrspRule urspRule : mSlicingConfig.getUrspRules()) {
            for (TrafficDescriptor trafficDescriptor : urspRule.getTrafficDescriptors()) {
                TrafficDescriptor.OsAppId osAppId =
                        new TrafficDescriptor.OsAppId(trafficDescriptor.getOsAppId());
                if (osAppId.getAppId().equals(getAppId(capability))) {
                    for (RouteSelectionDescriptor rsd : urspRule.getRouteSelectionDescriptor()) {
                        for (NetworkSliceInfo sliceInfo : rsd.getSliceInfo()) {
                            if (sliceInfo.getStatus() == NetworkSliceInfo.SLICE_STATUS_ALLOWED
                                    && getSliceServiceTypes(capability).contains(
                                            sliceInfo.getSliceServiceType())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get the application ID associated with the given premium capability.
     * The app ID is a field in {@link TrafficDescriptor} that helps match URSP rules to determine
     * whether the premium capability was successfully set up on the carrier network.
     * @param capability The premium capability to get the app ID for.
     * @return The application ID associated with the premium capability.
     */
    @VisibleForTesting
    @NonNull public static String getAppId(@TelephonyManager.PremiumCapability int capability) {
        if (capability == TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY) {
            return "PRIORITIZE_LATENCY";
        }
        return "";
    }

    /**
     * Get the slice service types associated with the given premium capability.
     * The slice service type is a field in {@link NetworkSliceInfo} that helps to match determine
     * whether the premium capability was successfully set up on the carrier network.
     * @param capability The premium capability to get the associated slice service types for.
     * @return A set of slice service types associated with the premium capability.
     */
    @VisibleForTesting
    @NonNull @NetworkSliceInfo.SliceServiceType public static Set<Integer> getSliceServiceTypes(
            @TelephonyManager.PremiumCapability int capability) {
        Set<Integer> sliceServiceTypes = new HashSet<>();
        if (capability == TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY) {
            sliceServiceTypes.add(NetworkSliceInfo.SLICE_SERVICE_TYPE_EMBB);
            sliceServiceTypes.add(NetworkSliceInfo.SLICE_SERVICE_TYPE_URLLC);
        } else {
            sliceServiceTypes.add(NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE);
        }
        return sliceServiceTypes;
    }

    private boolean isNetworkAvailable() {
        if (mPhone.getServiceState().getDataRoaming()) {
            logd("Network unavailable because device is roaming.");
            return false;
        }

        if (!mPhone.getDataSettingsManager().isDataEnabledForReason(
                TelephonyManager.DATA_ENABLED_REASON_USER)) {
            logd("Network unavailable because user data is disabled.");
            return false;
        }

        // TODO (b/251558673): Create a listener for data network type changed to dismiss
        //  notification and activity when the network is no longer available.
        switch (mPhone.getServiceState().getDataNetworkType()) {
            case TelephonyManager.NETWORK_TYPE_NR:
                return true;
            case TelephonyManager.NETWORK_TYPE_LTE:
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                return getCarrierConfigs().getBoolean(
                        CarrierConfigManager.KEY_PREMIUM_CAPABILITY_SUPPORTED_ON_LTE_BOOL);
        }
        return false;
    }

    /**
     * Returns the failure code {@link FailureCode} as a String.
     *
     * @param failureCode The failure code.
     * @return The failure code as a String.
     */
    @NonNull private static String convertFailureCodeToString(@FailureCode int failureCode) {
        switch (failureCode) {
            case FAILURE_CODE_UNKNOWN: return "UNKNOWN";
            case FAILURE_CODE_CARRIER_URL_UNAVAILABLE: return "CARRIER_URL_UNAVAILABLE";
            case FAILURE_CODE_AUTHENTICATION_FAILED: return "AUTHENTICATION_FAILED";
            case FAILURE_CODE_PAYMENT_FAILED: return "PAYMENT_FAILED";
            case FAILURE_CODE_NO_USER_DATA: return "NO_USER_DATA";
            default:
                return "UNKNOWN(" + failureCode + ")";
        }
    }

    private void reportAnomaly(@NonNull String uuid, @NonNull String log) {
        loge(log);
        AnomalyReporter.reportAnomaly(UUID.fromString(uuid), log);
    }

    private void logd(String s) {
        Log.d(TAG + "-" + mPhone.getPhoneId(), s);
    }

    private void loge(String s) {
        Log.e(TAG + "-" + mPhone.getPhoneId(), s);
    }
}
