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

package com.android.telephony.qns.wfc;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/** This class supports loading WFC config */
public class WfcCarrierConfigManager {
    private static final String TAG = WfcActivationActivity.TAG;
    private final int mSubId;
    private final Context mContext;
    protected int mCurrCarrierId;

    private boolean mIsShowVowifiPortalAfterTimeout;
    private boolean mIsJsCallbackForVowifiPortal;

    private int mVowifiRegistrationTimerForVowifiActivation;

    private String mVowifiEntitlementServerUrl;

    /**
     * The address of the VoWiFi entitlement server for Emergency Address Registration.
     *
     * <p>Note: this is effective only if the {@link #KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING}
     * is set to the QNS app.
     */
    public static final String KEY_QNS_VOWIFI_ENTITLEMENT_SERVER_URL_STRING =
            "qns.vowifi_entitlement_server_url_string";

    /**
     * Specifies the wait time in milliseconds that VoWiFi registration in VoWiFi activation
     * process.
     *
     * <p>Note: this is effective only if the {@link #KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING}
     * is set to the QNS app.
     */
    public static final String KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT =
            "qns.vowifi_registation_timer_for_vowifi_activation_int";

    /**
     * Indicates whether to pop up a web portal of the carrier or to turn on WFC directly when
     * {@link #KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT} is expired in VoWiFi
     * activation process
     *
     * <p>{@code true} - show the VoWiFi portal after the timer expires. {@code false}
     * - turn on WFC UI after the timer expires.
     *
     * <p>Note: this is effective only if the {@link #KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING}
     * is set to the QNS app.
     */
    public static final String KEY_QNS_SHOW_VOWIFI_PORTAL_AFTER_TIMEOUT_BOOL =
            "qns.show_vowifi_portal_after_timeout_bool";

    /**
     * Indicates whether web portal {@link #KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING} of the
     * carrier supports JavaScript callback interfaces
     *
     * <p>{@code true} - use webview with JavaScript callback interfaces to display web content.
     * {@code false} - use chrome with custom tabs to display web content.
     *
     * <p>Note: this is effective only if the {@link #KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING}
     * is set to the QNS app.
     */
    public static final String KEY_QNS_JS_CALLBACK_FOR_VOWIFI_PORTAL_BOOL =
            "qns.js_callback_for_vowifi_portal_bool";

    public static final int CONFIG_DEFAULT_VOWIFI_REGISTATION_TIMER = 120000;

    WfcCarrierConfigManager(Context context, int subId) {
        mSubId = subId;
        mContext = context;
    }

    private static boolean getDefaultBooleanValueForKey(String key) {
        Log.d(TAG, "Use default value for key: " + key);
        switch (key) {
            case KEY_QNS_SHOW_VOWIFI_PORTAL_AFTER_TIMEOUT_BOOL:
                return true;
            case KEY_QNS_JS_CALLBACK_FOR_VOWIFI_PORTAL_BOOL:
                return false;
            default:
                break;
        }
        return false;
    }

    private static String getDefaultStringValueForKey(String key) {
        Log.d(TAG, "Use default value for key: " + key);
        switch (key) {
            case KEY_QNS_VOWIFI_ENTITLEMENT_SERVER_URL_STRING:
                return "";
            default:
                break;
        }
        return "";
    }

    private static int getDefaultIntValueForKey(String key) {
        Log.d(TAG, "Use default value for key: " + key);
        switch (key) {
            case KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT:
                return CONFIG_DEFAULT_VOWIFI_REGISTATION_TIMER;
            default:
                break;
        }
        return 0;
    }

    private PersistableBundle readFromCarrierConfigManager(Context context) {
        PersistableBundle carrierConfigBundle;
        CarrierConfigManager carrierConfigManager =
                context.getSystemService(CarrierConfigManager.class);

        if (carrierConfigManager == null) {
            throw new IllegalStateException("Carrier config manager is null.");
        }
        carrierConfigBundle = carrierConfigManager.getConfigForSubId(mSubId);

        return carrierConfigBundle;
    }

    @VisibleForTesting
    void loadConfigurations() {
        PersistableBundle carrierConfigBundle = readFromCarrierConfigManager(mContext);
        Log.d(TAG, "CarrierConfig Bundle for subId: " + mSubId + carrierConfigBundle);
        loadConfigurationsFromCarrierConfig(carrierConfigBundle);
    }

    @VisibleForTesting
    void loadConfigurationsFromCarrierConfig(PersistableBundle carrierConfigBundle) {
        mVowifiEntitlementServerUrl =
                getStringConfig(carrierConfigBundle,
                                KEY_QNS_VOWIFI_ENTITLEMENT_SERVER_URL_STRING);
        mVowifiRegistrationTimerForVowifiActivation =
                getIntConfig(
                        carrierConfigBundle,
                        KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT);
        mIsShowVowifiPortalAfterTimeout =
                getBooleanConfig(carrierConfigBundle,
                                 KEY_QNS_SHOW_VOWIFI_PORTAL_AFTER_TIMEOUT_BOOL);
        mIsJsCallbackForVowifiPortal =
                getBooleanConfig(carrierConfigBundle,
                                 KEY_QNS_JS_CALLBACK_FOR_VOWIFI_PORTAL_BOOL);
    }

    private boolean getBooleanConfig(PersistableBundle bundleCarrier, String key) {
        if (bundleCarrier == null || bundleCarrier.get(key) == null) {
            return getDefaultBooleanValueForKey(key);
        }
        return bundleCarrier.getBoolean(key);
    }

    private int getIntConfig(PersistableBundle bundleCarrier, String key) {
        if (bundleCarrier == null || bundleCarrier.get(key) == null) {
            return getDefaultIntValueForKey(key);
        }
        return bundleCarrier.getInt(key);
    }

    private String getStringConfig(PersistableBundle bundleCarrier, String key) {
        if (bundleCarrier == null || bundleCarrier.get(key) == null) {
            return getDefaultStringValueForKey(key);
        }
        return bundleCarrier.getString(key);
    }

    /**
     * This method returns the URL of the VoWiFi entitlement server for an emergency address
     * registration
     */
    @VisibleForTesting(visibility = PACKAGE)
    String getVowifiEntitlementServerUrl() {
        return mVowifiEntitlementServerUrl;
    }

    /**
     * This method returns the wait timer in milliseconds that VoWiFi registration in VoWiFi
     * activation process
     */
    @VisibleForTesting(visibility = PACKAGE)
    int getVowifiRegistrationTimerForVowifiActivation() {
        return mVowifiRegistrationTimerForVowifiActivation;
    }

    /**
     * This method returns true if a web portal of the carrier is poped up when
     * {@link #KEY_QNS_VOWIFI_REGISTATION_TIMER_FOR_VOWIFI_ACTIVATION_INT} is expired in VoWiFi
     * activation process; Otherwise, WFC is tuned on directly.
     */
    @VisibleForTesting(visibility = PACKAGE)
    boolean isShowVowifiPortalAfterTimeout() {
        return mIsShowVowifiPortalAfterTimeout;
    }

    /**
     * This method returns true if JavaScript callback interface is not support for web portal
     * {@link #KEY_WFC_EMERGENCY_ADDRESS_CARRIER_APP_STRING} of the carrier
     */
    @VisibleForTesting(visibility = PACKAGE)
    boolean supportJsCallbackForVowifiPortal() {
        return mIsJsCallbackForVowifiPortal;
    }
}
