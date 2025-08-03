/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.flags.Flags;

public class CdmaCallOptions extends TimeConsumingPreferenceActivity {
    private static final String LOG_TAG = "CdmaCallOptions";

    private static final String BUTTON_VP_KEY = "button_voice_privacy_key";
    private static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    private static final String CALL_WAITING_KEY = "call_waiting_key";

    private class UtCallback extends ImsMmTelManager.CapabilityCallback {
        @Override
        public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities capabilities) {
            boolean isUtAvailable = capabilities.isCapable(
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT);
            updatePreferencesEnabled(isUtAvailable);
        }
    }

    private Preference mCallForwardingPref;
    private CdmaCallWaitingPreference mCallWaitingPref;
    private UtCallback mUtCallback;
    private ImsMmTelManager mMmTelManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getWindow().addSystemFlags(
                android.view.WindowManager.LayoutParams
                        .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        addPreferencesFromResource(R.xml.cdma_call_privacy);

        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        subInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.labelCdmaMore_with_label);

        CdmaVoicePrivacySwitchPreference buttonVoicePrivacy =
                (CdmaVoicePrivacySwitchPreference) findPreference(BUTTON_VP_KEY);
        buttonVoicePrivacy.setPhone(subInfoHelper.getPhone());
        PersistableBundle carrierConfig;
        int subId;
        if (subInfoHelper.hasSubId()) {
            subId = subInfoHelper.getSubId();
        } else {
            subId = SubscriptionManager.getDefaultSubscriptionId();
        }
        carrierConfig = PhoneGlobals.getInstance().getCarrierConfigForSubId(subId);
        if (subInfoHelper.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA
                || carrierConfig.getBoolean(CarrierConfigManager.KEY_VOICE_PRIVACY_DISABLE_UI_BOOL)) {
            buttonVoicePrivacy.setEnabled(false);
        }

        // If mobile network configs are restricted, then hide the mCallForwardingPref and
        // mCallWaitingPref.
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        boolean mobileNetworkConfigsRestricted =
                userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        if (Flags.ensureAccessToCallSettingsIsRestricted() && mobileNetworkConfigsRestricted) {
            Log.i(LOG_TAG, "Mobile network configs are restricted, hiding CDMA call forwarding "
                    + "and CDMA call waiting options.");
        }

        mCallForwardingPref = getPreferenceScreen().findPreference(CALL_FORWARDING_KEY);
        if (carrierConfig != null && carrierConfig.getBoolean(
                CarrierConfigManager.KEY_CALL_FORWARDING_VISIBILITY_BOOL) &&
                (!mobileNetworkConfigsRestricted ||
                        !Flags.ensureAccessToCallSettingsIsRestricted())) {
            mCallForwardingPref.setIntent(
                    subInfoHelper.getIntent(CdmaCallForwardOptions.class));
        } else {
            getPreferenceScreen().removePreference(mCallForwardingPref);
            mCallForwardingPref = null;
        }

        mCallWaitingPref = (CdmaCallWaitingPreference) getPreferenceScreen()
                .findPreference(CALL_WAITING_KEY);
        if (carrierConfig == null || !carrierConfig.getBoolean(
                CarrierConfigManager.KEY_ADDITIONAL_SETTINGS_CALL_WAITING_VISIBILITY_BOOL) ||
                (Flags.ensureAccessToCallSettingsIsRestricted() &&
                        mobileNetworkConfigsRestricted)) {
            getPreferenceScreen().removePreference(mCallWaitingPref);
            mCallWaitingPref = null;
        }
        // Do not go further if the preferences are removed.
        if (mCallForwardingPref == null && mCallWaitingPref == null) return;

        boolean isSsOverCdmaEnabled = carrierConfig != null && carrierConfig.getBoolean(
                CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL);
        boolean isSsOverUtEnabled = carrierConfig != null && carrierConfig.getBoolean(
                CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL);

        if (isSsOverCdmaEnabled && mCallWaitingPref != null) {
            // If SS over CDMA is enabled, then the preference will always be enabled,
            // independent of SS over UT status. Initialize it now.
            mCallWaitingPref.init(this, subInfoHelper.getPhone());
            return;
        }
        // Since SS over UT availability can change, first disable the preferences that rely on it
        // and only enable it if UT is available.
        updatePreferencesEnabled(false);
        if (isSsOverUtEnabled) {
            // Register a callback to listen to SS over UT state. This will enable the preferences
            // once the callback notifies settings that UT is enabled.
            registerMmTelCapsCallback(subId);
        } else {
            Log.w(LOG_TAG, "SS over UT and CDMA disabled, but preferences are visible.");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterMmTelCapsCallback();
    }

    private void unregisterMmTelCapsCallback() {
        if (mMmTelManager == null || mUtCallback == null) return;
        mMmTelManager.unregisterMmTelCapabilityCallback(mUtCallback);
        mUtCallback = null;
        Log.d(LOG_TAG, "unregisterMmTelCapsCallback: UT availability callback unregistered");
    }

    private void registerMmTelCapsCallback(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) return;
        ImsManager imsManager = getSystemService(ImsManager.class);
        try {
            if (imsManager != null) {
                mUtCallback = new UtCallback();
                mMmTelManager = imsManager.getImsMmTelManager(subId);
                // Callback will call back with the state as soon as it is available.
                mMmTelManager.registerMmTelCapabilityCallback(getMainExecutor(), mUtCallback);
                Log.d(LOG_TAG, "registerMmTelCapsCallback: UT availability callback "
                        + "registered");
            } else {
                Log.w(LOG_TAG, "registerMmTelCapsCallback: couldn't get ImsManager, assuming "
                        + "UT is not available: ");
                updatePreferencesEnabled(false);
            }
        } catch (IllegalArgumentException | ImsException e) {
            Log.w(LOG_TAG, "registerMmTelCapsCallback: couldn't register callback, assuming "
                    + "UT is not available: " + e);
            updatePreferencesEnabled(false);
        }
    }

    private void updatePreferencesEnabled(boolean isEnabled) {
        Log.d(LOG_TAG, "updatePreferencesEnabled: " + isEnabled);
        if (mCallForwardingPref != null) mCallForwardingPref.setEnabled(isEnabled);

        if (mCallWaitingPref == null || mCallWaitingPref.isEnabled() == isEnabled) return;
        mCallWaitingPref.setActionAvailable(isEnabled);
        if (isEnabled) {
            SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
            // kick off the normal process to populate the Call Waiting status.
            mCallWaitingPref.init(this, subInfoHelper.getPhone());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(BUTTON_VP_KEY)) {
            return true;
        }
        return false;
    }
}
