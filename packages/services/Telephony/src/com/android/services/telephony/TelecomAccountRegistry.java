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

package com.android.services.telephony;

import android.app.ActivityManager;
import android.app.PropertyInvalidatedCache;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.text.TextUtils;

import com.android.ims.ImsManager;
import com.android.internal.telephony.ExponentialBackoff;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SimultaneousCallingTracker;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.telephony.Rlog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Owns all data we have registered with Telecom including handling dynamic addition and
 * removal of SIMs and SIP accounts.
 */
public class TelecomAccountRegistry {
    private static final boolean DBG = false; /* STOP SHIP if true */
    private static final String LOG_TAG = "TelecomAccountRegistry";

    // This icon is the one that is used when the Slot ID that we have for a particular SIM
    // is not supported, i.e. SubscriptionManager.INVALID_SLOT_ID or the 5th SIM in a phone.
    private final static int DEFAULT_SIM_ICON =  R.drawable.ic_multi_sim;
    private final static String GROUP_PREFIX = "group_";

    private static final int REGISTER_START_DELAY_MS = 1 * 1000; // 1 second
    private static final int REGISTER_MAXIMUM_DELAY_MS = 60 * 1000; // 1 minute

    /**
     * Indicates the {@link SubscriptionManager.OnSubscriptionsChangedListener} has not yet been
     * registered.
     */
    private static final int LISTENER_STATE_UNREGISTERED = 0;

    /**
     * Indicates the first {@link SubscriptionManager.OnSubscriptionsChangedListener} registration
     * attempt failed and we are performing backoff registration.
     */
    private static final int LISTENER_STATE_PERFORMING_BACKOFF = 2;

    /**
     * Indicates the {@link SubscriptionManager.OnSubscriptionsChangedListener} has been registered.
     */
    private static final int LISTENER_STATE_REGISTERED = 3;

    /**
     * Copy-pasted from android.telecom.PhoneAccount -- hidden constant which is unfortunately being
     * used by some 1P apps, so we're keeping it here until we can remove it.
     */
    private static final String EXTRA_SUPPORTS_VIDEO_CALLING_FALLBACK =
            "android.telecom.extra.SUPPORTS_VIDEO_CALLING_FALLBACK";

    private Handler mHandler;

    final class AccountEntry implements PstnPhoneCapabilitiesNotifier.Listener {
        private final Phone mPhone;
        private PhoneAccount mAccount;
        private SimultaneousCallingTracker mSCT;
        private final PstnIncomingCallNotifier mIncomingCallNotifier;
        private final PstnPhoneCapabilitiesNotifier mPhoneCapabilitiesNotifier;
        private boolean mIsEmergency;
        private boolean mIsRttCapable;
        private boolean mIsCallComposerCapable;
        private boolean mIsAdhocConfCapable;
        private boolean mIsEmergencyPreferred;
        private MmTelFeature.MmTelCapabilities mMmTelCapabilities;
        private ImsMmTelManager.CapabilityCallback mMmtelCapabilityCallback;
        private RegistrationManager.RegistrationCallback mImsRegistrationCallback;
        private SimultaneousCallingTracker.Listener mSimultaneousCallingTrackerListener;
        private ImsMmTelManager mMmTelManager;
        private final boolean mIsTestAccount;
        private boolean mIsVideoCapable;
        private boolean mIsVideoPresenceSupported;
        private boolean mIsVideoPauseSupported;
        private boolean mIsMergeCallSupported;
        private boolean mIsMergeImsCallSupported;
        private boolean mIsVideoConferencingSupported;
        private boolean mIsMergeOfWifiCallsAllowedWhenVoWifiOff;
        private boolean mIsManageImsConferenceCallSupported;
        private boolean mIsUsingSimCallManager;
        private boolean mIsShowPreciseFailedCause;
        private Set<Integer> mSimultaneousCallSupportedSubIds;

        AccountEntry(Phone phone, boolean isEmergency, boolean isTest) {
            mPhone = phone;
            mIsEmergency = isEmergency;
            mIsTestAccount = isTest;
            mIsAdhocConfCapable = mPhone.isImsRegistered();
            if (Flags.simultaneousCallingIndications()) {
                mSCT = SimultaneousCallingTracker.getInstance();
                mSimultaneousCallSupportedSubIds =
                        mSCT.getSubIdsSupportingSimultaneousCalling(mPhone.getSubId());
            }
            mAccount = registerPstnPhoneAccount(isEmergency, isTest);
            Log.i(this, "Registered phoneAccount: %s with handle: %s",
                    mAccount, mAccount.getAccountHandle());
            mIncomingCallNotifier = new PstnIncomingCallNotifier((Phone) mPhone);
            mPhoneCapabilitiesNotifier = new PstnPhoneCapabilitiesNotifier((Phone) mPhone,
                    this);

            if (mIsTestAccount || isEmergency) {
                // For test and emergency entries, there is no sub ID that can be assigned, so do
                // not register for capabilities callbacks.
                return;
            }

            try {
                if (mPhone.getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_IMS)) {
                    mMmTelManager = ImsMmTelManager.createForSubscriptionId(getSubId());
                }
            } catch (IllegalArgumentException e) {
                Log.i(this, "Not registering MmTel capabilities listener because the subid '"
                        + getSubId() + "' is invalid: " + e.getMessage());
                return;
            }

            mMmtelCapabilityCallback = new ImsMmTelManager.CapabilityCallback() {
                @Override
                public void onCapabilitiesStatusChanged(
                        MmTelFeature.MmTelCapabilities capabilities) {
                    mMmTelCapabilities = capabilities;
                    updateRttCapability();
                    updateCallComposerCapability(capabilities);
                }
            };
            registerMmTelCapabilityCallback();

            mImsRegistrationCallback = new RegistrationManager.RegistrationCallback() {
                @Override
                public void onRegistered(int imsRadioTech) {
                    updateAdhocConfCapability(true);
                }

                @Override
                public void onRegistering(int imsRadioTech) {
                    updateAdhocConfCapability(false);
                }

                @Override
                public void onUnregistered(ImsReasonInfo imsReasonInfo) {
                    updateAdhocConfCapability(false);
                }
            };
            registerImsRegistrationCallback();

            if (Flags.simultaneousCallingIndications()) {
                //Register SimultaneousCallingTracker listener:
                mSimultaneousCallingTrackerListener = new SimultaneousCallingTracker.Listener() {
                    @Override
                    public void onSimultaneousCallingSupportChanged(Map<Integer,
                            Set<Integer>> simultaneousCallSubSupportMap) {
                        updateSimultaneousCallSubSupportMap(simultaneousCallSubSupportMap);
                    }
                };
                SimultaneousCallingTracker.getInstance()
                        .addListener(mSimultaneousCallingTrackerListener);
                Log.d(LOG_TAG, "Finished registering mSimultaneousCallingTrackerListener for "
                        + "phoneId = " + mPhone.getPhoneId() + "; subId = " + mPhone.getSubId());
            }
        }

        void teardown() {
            mIncomingCallNotifier.teardown();
            mPhoneCapabilitiesNotifier.teardown();
            if (mMmTelManager != null) {
                if (mMmtelCapabilityCallback != null) {
                    mMmTelManager.unregisterMmTelCapabilityCallback(mMmtelCapabilityCallback);
                }

                if (mImsRegistrationCallback != null) {
                    mMmTelManager.unregisterImsRegistrationCallback(mImsRegistrationCallback);
                }
            }
            if (Flags.simultaneousCallingIndications()) {
                SimultaneousCallingTracker.getInstance()
                        .removeListener(mSimultaneousCallingTrackerListener);
            }
        }

        private void registerMmTelCapabilityCallback() {
            if (mMmTelManager == null || mMmtelCapabilityCallback == null) {
                // The subscription id associated with this account is invalid or not associated
                // with a subscription. Do not register in this case.
                return;
            }

            try {
                mMmTelManager.registerMmTelCapabilityCallback(mContext.getMainExecutor(),
                        mMmtelCapabilityCallback);
            } catch (ImsException e) {
                Log.w(this, "registerMmTelCapabilityCallback: registration failed, no ImsService"
                        + " available. Exception: " + e.getMessage());
                return;
            } catch (IllegalArgumentException e) {
                Log.w(this, "registerMmTelCapabilityCallback: registration failed, invalid"
                        + " subscription, Exception" + e.getMessage());
                return;
            }
        }

        private void registerImsRegistrationCallback() {
            if (mMmTelManager == null || mImsRegistrationCallback == null) {
                return;
            }

            try {
                mMmTelManager.registerImsRegistrationCallback(mContext.getMainExecutor(),
                        mImsRegistrationCallback);
            } catch (ImsException e) {
                Log.w(this, "registerImsRegistrationCallback: registration failed, no ImsService"
                        + " available. Exception: " + e.getMessage());
                return;
            } catch (IllegalArgumentException e) {
                Log.w(this, "registerImsRegistrationCallback: registration failed, invalid"
                        + " subscription, Exception" + e.getMessage());
                return;
            }
        }

        /**
         * Trigger re-registration of this account.
         */
        public void reRegisterPstnPhoneAccount() {
            PhoneAccount newAccount = buildPstnPhoneAccount(mIsEmergency, mIsTestAccount);
            if (!newAccount.equals(mAccount)) {
                Log.i(this, "reRegisterPstnPhoneAccount: subId: " + getSubId()
                        + " - re-register due to account change.");
                mTelecomManager.registerPhoneAccount(newAccount);
                mAccount = newAccount;
            } else {
                Log.i(this, "reRegisterPstnPhoneAccount: subId: " + getSubId() + " - no change");
            }
        }

        private PhoneAccount registerPstnPhoneAccount(boolean isEmergency, boolean isTestAccount) {
            PhoneAccount account = buildPstnPhoneAccount(mIsEmergency, mIsTestAccount);
            Log.i(this, "registerPstnPhoneAccount: Registering account=%s with "
                    + "Telecom. subId=%d", account, getSubId());
            // Register with Telecom and put into the account entry.
            mTelecomManager.registerPhoneAccount(account);
            return account;
        }

        /**
         * Registers the specified account with Telecom as a PhoneAccountHandle.
         */
        private PhoneAccount buildPstnPhoneAccount(boolean isEmergency, boolean isTestAccount) {
            String testPrefix = isTestAccount ? "Test " : "";

            // Check if we are registering another user. If we are, ensure that the account
            // is registered to that user handle.
            int subId = mPhone.getSubId();
            // Get user handle from phone's sub id (if we get null, then system user will be used)
            UserHandle userToRegister = mPhone.getUserHandle();

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle =
                    PhoneUtils.makePstnPhoneAccountHandleWithPrefix(
                            mPhone, testPrefix, isEmergency, userToRegister);

            // Populate the phone account data.
            String subscriberId = mPhone.getSubscriberId();
            int color = PhoneAccount.NO_HIGHLIGHT_COLOR;
            int slotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
            String line1Number = mTelephonyManager.getLine1Number(subId);
            if (line1Number == null) {
                line1Number = "";
            }
            String subNumber = mPhone.getLine1Number();
            if (subNumber == null) {
                subNumber = "";
            }

            String label = "";
            String description = "";
            Icon icon = null;

            // We can only get the real slotId from the SubInfoRecord, we can't calculate the
            // slotId from the subId or the phoneId in all instances.
            SubscriptionInfo record =
                    mSubscriptionManager.getActiveSubscriptionInfo(subId);
            TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);

            if (isEmergency) {
                label = mContext.getResources().getString(R.string.sim_label_emergency_calls);
                description =
                        mContext.getResources().getString(R.string.sim_description_emergency_calls);
            } else if (mTelephonyManager.getPhoneCount() == 1) {
                // For single-SIM devices, we show the label and description as whatever the name of
                // the network is.
                if (record != null) {
                    description = label = String.valueOf(record.getDisplayName());
                }
            } else {
                CharSequence subDisplayName = null;

                if (record != null) {
                    subDisplayName = record.getDisplayName();
                    slotId = record.getSimSlotIndex();
                    color = record.getIconTint();
                    icon = Icon.createWithBitmap(record.createIconBitmap(mContext));
                }

                String slotIdString;
                if (SubscriptionManager.isValidSlotIndex(slotId)) {
                    slotIdString = Integer.toString(slotId);
                } else {
                    slotIdString = mContext.getResources().getString(R.string.unknown);
                }

                if (TextUtils.isEmpty(subDisplayName)) {
                    // Either the sub record is not there or it has an empty display name.
                    Log.w(this, "Could not get a display name for subid: %d", subId);
                    subDisplayName = mContext.getResources().getString(
                            R.string.sim_description_default, slotIdString);
                }

                // The label is user-visible so let's use the display name that the user may
                // have set in Settings->Sim cards.
                label = testPrefix + subDisplayName;
                description = testPrefix + mContext.getResources().getString(
                                R.string.sim_description_default, slotIdString);
            }

            // By default all SIM phone accounts can place emergency calls.
            int capabilities = PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                    PhoneAccount.CAPABILITY_CALL_PROVIDER;

            // This is enabled by default. To support work profiles, it should not be enabled.
            if (userToRegister == null) {
                capabilities |= PhoneAccount.CAPABILITY_MULTI_USER;
            }

            if (mContext.getResources().getBoolean(R.bool.config_pstnCanPlaceEmergencyCalls)) {
                capabilities |= PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS;
            }

            mIsEmergencyPreferred = isEmergencyPreferredAccount(subId, mActiveDataSubscriptionId);
            if (mIsEmergencyPreferred) {
                capabilities |= PhoneAccount.CAPABILITY_EMERGENCY_PREFERRED;
            }

            if (isRttCurrentlySupported()) {
                capabilities |= PhoneAccount.CAPABILITY_RTT;
                mIsRttCapable = true;
            } else {
                mIsRttCapable = false;
            }

            if (mIsCallComposerCapable) {
                capabilities |= PhoneAccount.CAPABILITY_CALL_COMPOSER;
            }

            mIsVideoCapable = mPhone.isVideoEnabled();
            boolean isVideoEnabledByPlatform = ImsManager.getInstance(mPhone.getContext(),
                    mPhone.getPhoneId()).isVtEnabledByPlatform();

            if (!mIsPrimaryUser) {
                Log.i(this, "Disabling video calling for secondary user.");
                mIsVideoCapable = false;
                isVideoEnabledByPlatform = false;
            }

            if (mIsVideoCapable) {
                capabilities |= PhoneAccount.CAPABILITY_VIDEO_CALLING;
            }

            if (isVideoEnabledByPlatform) {
                capabilities |= PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING;
            }

            mIsVideoPresenceSupported = isCarrierVideoPresenceSupported();
            if (mIsVideoCapable && mIsVideoPresenceSupported) {
                capabilities |= PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE;
            }

            if (mIsVideoCapable && isCarrierEmergencyVideoCallsAllowed()) {
                capabilities |= PhoneAccount.CAPABILITY_EMERGENCY_VIDEO_CALLING;
            }

            mIsVideoPauseSupported = isCarrierVideoPauseSupported();
            Bundle extras = new Bundle();
            if (isCarrierInstantLetteringSupported()) {
                capabilities |= PhoneAccount.CAPABILITY_CALL_SUBJECT;
                extras.putAll(getPhoneAccountExtras());
            }

            if (mIsAdhocConfCapable && isCarrierAdhocConferenceCallSupported()) {
                capabilities |= PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING;
            } else {
                capabilities &= ~PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING;
            }

            final boolean isHandoverFromSupported = mContext.getResources().getBoolean(
                    R.bool.config_support_handover_from);
            if (isHandoverFromSupported && !isEmergency) {
                // Only set the extra is handover is supported and this isn't the emergency-only
                // acct.
                extras.putBoolean(PhoneAccount.EXTRA_SUPPORTS_HANDOVER_FROM,
                        isHandoverFromSupported);
            }

            final boolean isTelephonyAudioDeviceSupported = mContext.getResources().getBoolean(
                    R.bool.config_support_telephony_audio_device);
            if (isTelephonyAudioDeviceSupported && !isEmergency
                    && isCarrierUseCallRecordingTone()) {
                extras.putBoolean(PhoneAccount.EXTRA_PLAY_CALL_RECORDING_TONE, true);
            }

            extras.putBoolean(EXTRA_SUPPORTS_VIDEO_CALLING_FALLBACK,
                    mContext.getResources()
                            .getBoolean(R.bool.config_support_video_calling_fallback));

            if (slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                extras.putInt(PhoneAccount.EXTRA_SORT_ORDER, slotId);
            }

            mIsMergeCallSupported = isCarrierMergeCallSupported();
            mIsMergeImsCallSupported = isCarrierMergeImsCallSupported();
            mIsVideoConferencingSupported = isCarrierVideoConferencingSupported();
            mIsMergeOfWifiCallsAllowedWhenVoWifiOff =
                    isCarrierMergeOfWifiCallsAllowedWhenVoWifiOff();
            mIsManageImsConferenceCallSupported = isCarrierManageImsConferenceCallSupported();
            mIsUsingSimCallManager = isCarrierUsingSimCallManager();
            mIsShowPreciseFailedCause = isCarrierShowPreciseFailedCause();

            // Set CAPABILITY_EMERGENCY_CALLS_ONLY flag if either
            // - Carrier config overrides subscription is not voice capable, or
            // - Resource config overrides it be emergency_calls_only
            // TODO(b/316183370:): merge the two cases when clearing up flag
            if (Flags.dataOnlyServiceAllowEmergencyCallOnly()) {
                if (!isSubscriptionVoiceCapableByCarrierConfig()) {
                    capabilities |= PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY;
                }
            }
            if (isEmergency && mContext.getResources().getBoolean(
                    R.bool.config_emergency_account_emergency_calls_only)) {
                capabilities |= PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY;
            }

            if (icon == null) {
                // TODO: Switch to using Icon.createWithResource() once that supports tinting.
                Resources res = mContext.getResources();
                Drawable drawable = res.getDrawable(DEFAULT_SIM_ICON, null);
                drawable.setTint(res.getColor(R.color.default_sim_icon_tint_color, null));
                drawable.setTintMode(PorterDuff.Mode.SRC_ATOP);

                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);

                icon = Icon.createWithBitmap(bitmap);
            }

            // Check to see if the newly registered account should replace the old account.
            String groupId = "";
            String[] mergedImsis = mTelephonyManager.getMergedSubscriberIds();
            boolean isMergedSim = false;
            if (mergedImsis != null && subscriberId != null && !isEmergency) {
                for (String imsi : mergedImsis) {
                    if (imsi.equals(subscriberId)) {
                        isMergedSim = true;
                        break;
                    }
                }
            }
            if(isMergedSim) {
                groupId = GROUP_PREFIX + line1Number;
                Log.i(this, "Adding Merged Account with group: " + Rlog.pii(LOG_TAG, groupId));
            }

            PhoneAccount.Builder accountBuilder = PhoneAccount.builder(phoneAccountHandle, label)
                    .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, line1Number, null))
                    .setSubscriptionAddress(
                            Uri.fromParts(PhoneAccount.SCHEME_TEL, subNumber, null))
                    .setCapabilities(capabilities)
                    .setIcon(icon)
                    .setHighlightColor(color)
                    .setShortDescription(description)
                    .setSupportedUriSchemes(Arrays.asList(
                            PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
                    .setExtras(extras)
                    .setGroupId(groupId);

            if (Flags.simultaneousCallingIndications()) {
                Set <PhoneAccountHandle> simultaneousCallingHandles =
                        mSimultaneousCallSupportedSubIds.stream()
                                .map(subscriptionId -> PhoneUtils.makePstnPhoneAccountHandleWithId(
                                        String.valueOf(subscriptionId), userToRegister))
                                .collect(Collectors.toSet());
                accountBuilder.setSimultaneousCallingRestriction(simultaneousCallingHandles);
            }


            return accountBuilder.build();
        }

        public PhoneAccountHandle getPhoneAccountHandle() {
            return mAccount != null ? mAccount.getAccountHandle() : null;
        }

        public int getSubId() {
            return mPhone.getSubId();
        }

        /**
         * In some cases, we need to try sending the emergency call over this PhoneAccount due to
         * restrictions and limitations in MSIM configured devices. This includes the following:
         * 1) The device does not support GNSS SUPL requests on the non-DDS subscription due to
         *   modem limitations. If the device does not support SUPL on non-DDS, we need to try the
         *   emergency call on the DDS subscription first to allow for SUPL to be completed.
         *
         * @return true if Telecom should prefer this PhoneAccount, false if there is no preference
         * needed.
         */
        private boolean isEmergencyPreferredAccount(int subId, int activeDataSubId) {
            Log.d(this, "isEmergencyPreferredAccount: subId=" + subId + ", activeData="
                    + activeDataSubId);
            final boolean gnssSuplRequiresDefaultData = mContext.getResources().getBoolean(
                    R.bool.config_gnss_supl_requires_default_data_for_emergency);
            if (!gnssSuplRequiresDefaultData) {
                Log.d(this, "isEmergencyPreferredAccount: Device does not require preference.");
                // No preference is necessary.
                return false;
            }

            if (SubscriptionManagerService.getInstance() == null) {
                Log.d(this,
                        "isEmergencyPreferredAccount: SubscriptionManagerService not "
                                + "available.");
                return false;
            }
            // Only set an emergency preference on devices with multiple active subscriptions
            // (include opportunistic subscriptions) in this check.
            // API says never null, but this can return null in testing.
            int[] activeSubIds = SubscriptionManagerService.getInstance()
                    .getActiveSubIdList(false);
            if (activeSubIds == null || activeSubIds.length <= 1) {
                Log.d(this, "isEmergencyPreferredAccount: one or less active subscriptions.");
                return false;
            }

            // Check to see if this PhoneAccount is associated with the default Data subscription.
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                Log.d(this, "isEmergencyPreferredAccount: provided subId " + subId + "is not "
                        + "valid.");
                return false;
            }
            int userDefaultData = SubscriptionManager.getDefaultDataSubscriptionId();
            boolean isActiveDataValid = SubscriptionManager.isValidSubscriptionId(activeDataSubId);

            SubscriptionInfo subInfo = SubscriptionManagerService.getInstance()
                    .getSubscriptionInfo(activeDataSubId);
            boolean isActiveDataOpportunistic = isActiveDataValid && subInfo != null
                    && subInfo.isOpportunistic();

            // compare the activeDataSubId to the subId specified only if it is valid and not an
            // opportunistic subscription (only supports data). If not, use the current default
            // defined by the user.
            Log.d(this, "isEmergencyPreferredAccount: userDefaultData=" + userDefaultData
                    + ", isActiveDataOppurtunistic=" + isActiveDataOpportunistic);
            return subId == ((isActiveDataValid && !isActiveDataOpportunistic) ? activeDataSubId :
                    userDefaultData);
        }

        /**
         * Determines from carrier configuration whether pausing of IMS video calls is supported.
         *
         * @return {@code true} if pausing IMS video calls is supported.
         */
        private boolean isCarrierVideoPauseSupported() {
            // Check if IMS video pause is supported.
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_PAUSE_IMS_VIDEO_CALLS_BOOL);
        }

        /**
         * Determines from carrier configuration and user setting whether RCS presence indication
         * for video calls is supported.
         *
         * @return {@code true} if RCS presence indication for video calls is supported.
         */
        private boolean isCarrierVideoPresenceSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            if (b == null) return false;

            // If using the new RcsUceAdapter API, this should be true if
            // KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL is set. If using the old
            // KEY_USE_RCS_PRESENCE_BOOL key, we have to also check the user setting.
            return b.getBoolean(
                    CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL)
                    || (b.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL)
                    && isUserContactDiscoverySettingEnabled());
        }

        /**
         * @return true if the user has enabled contact discovery for the subscription associated
         * with this account entry, false otherwise.
         */
        private boolean isUserContactDiscoverySettingEnabled() {
            try {
                ImsRcsManager manager = mImsManager.getImsRcsManager(mPhone.getSubId());
                return manager.getUceAdapter().isUceSettingEnabled();
            } catch (Exception e) {
                Log.w(LOG_TAG, "isUserContactDiscoverySettingEnabled caught exception: " + e);
                return false;
            }
        }

        /**
         * Determines from carrier config whether instant lettering is supported.
         *
         * @return {@code true} if instant lettering is supported, {@code false} otherwise.
         */
        private boolean isCarrierInstantLetteringSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_CARRIER_INSTANT_LETTERING_AVAILABLE_BOOL);
        }

        /**
         * Determines from carrier config whether adhoc conference calling is supported.
         *
         * @return {@code true} if adhoc conference calling is supported, {@code false} otherwise.
         */
        private boolean isCarrierAdhocConferenceCallSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_ADHOC_CONFERENCE_CALLS_BOOL);
        }


        /**
         * Determines from carrier config whether merging calls is supported.
         *
         * @return {@code true} if merging calls is supported, {@code false} otherwise.
         */
        private boolean isCarrierMergeCallSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether merging IMS calls is supported.
         *
         * @return {@code true} if merging IMS calls is supported, {@code false} otherwise.
         */
        private boolean isCarrierMergeImsCallSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b.getBoolean(CarrierConfigManager.KEY_SUPPORT_IMS_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether emergency video calls are supported.
         *
         * @return {@code true} if emergency video calls are allowed, {@code false} otherwise.
         */
        private boolean isCarrierEmergencyVideoCallsAllowed() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_ALLOW_EMERGENCY_VIDEO_CALLS_BOOL);
        }

        /**
         * Determines from carrier config whether video conferencing is supported.
         *
         * @return {@code true} if video conferencing is supported, {@code false} otherwise.
         */
        private boolean isCarrierVideoConferencingSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_VIDEO_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether merging of wifi calls is allowed when VoWIFI is
         * turned off.
         *
         * @return {@code true} merging of wifi calls when VoWIFI is disabled should be prevented,
         *      {@code false} otherwise.
         */
        private boolean isCarrierMergeOfWifiCallsAllowedWhenVoWifiOff() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null && b.getBoolean(
                    CarrierConfigManager.KEY_ALLOW_MERGE_WIFI_CALLS_WHEN_VOWIFI_OFF_BOOL);
        }

        /**
         * Determines from carrier config whether managing IMS conference calls is supported.
         *
         * @return {@code true} if managing IMS conference calls is supported,
         *         {@code false} otherwise.
         */
        private boolean isCarrierManageImsConferenceCallSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b.getBoolean(CarrierConfigManager.KEY_SUPPORT_MANAGE_IMS_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether the carrier uses a sim call manager.
         *
         * @return {@code true} if the carrier uses a sim call manager,
         *         {@code false} otherwise.
         */
        private boolean isCarrierUsingSimCallManager() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return !TextUtils.isEmpty(
                    b.getString(CarrierConfigManager.KEY_DEFAULT_SIM_CALL_MANAGER_STRING));
        }

        /**
         * Determines from carrier config whether showing percise call diconnect cause to user
         * is supported.
         *
         * @return {@code true} if showing percise call diconnect cause to user is supported,
         *         {@code false} otherwise.
         */
        private boolean isCarrierShowPreciseFailedCause() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b.getBoolean(CarrierConfigManager.KEY_SHOW_PRECISE_FAILED_CAUSE_BOOL);
        }

        /**
         * Determines from carrier config whether the carrier requires the use of a call recording
         * tone.
         *
         * @return {@code true} if a call recording tone should be used, {@code false} otherwise.
         */
        private boolean isCarrierUseCallRecordingTone() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b.getBoolean(CarrierConfigManager.KEY_PLAY_CALL_RECORDING_TONE_BOOL);
        }

        /**
         * Determines from carrier config whether to always allow RTT while roaming.
         */
        private boolean isCarrierAllowRttWhenRoaming() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b.getBoolean(CarrierConfigManager.KEY_RTT_SUPPORTED_WHILE_ROAMING_BOOL);
        }

        /**
         * Where a device supports instant lettering and call subjects, retrieves the necessary
         * PhoneAccount extras for those features.
         *
         * @return The {@link PhoneAccount} extras associated with the current subscription.
         */
        private Bundle getPhoneAccountExtras() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());

            int instantLetteringMaxLength = b.getInt(
                    CarrierConfigManager.KEY_CARRIER_INSTANT_LETTERING_LENGTH_LIMIT_INT);
            String instantLetteringEncoding = b.getString(
                    CarrierConfigManager.KEY_CARRIER_INSTANT_LETTERING_ENCODING_STRING);
            Bundle phoneAccountExtras = new Bundle();
            phoneAccountExtras.putInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH,
                    instantLetteringMaxLength);
            phoneAccountExtras.putString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING,
                    instantLetteringEncoding);
            return phoneAccountExtras;
        }

        /**
         * @return true if the subscription is voice capable by the carrier config.
         */
        private boolean isSubscriptionVoiceCapableByCarrierConfig() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            if (b == null) {
                return true; // For any abnormal case, we assume subscription is voice capable
            }
            final int[] serviceCapabilities = b.getIntArray(
                    CarrierConfigManager.KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY);
            return Arrays.stream(serviceCapabilities).anyMatch(
                    i -> i == SubscriptionManager.SERVICE_CAPABILITY_VOICE);
        }

        /**
         * Receives callback from {@link PstnPhoneCapabilitiesNotifier} when the video capabilities
         * have changed.
         *
         * @param isVideoCapable {@code true} if video is capable.
         */
        @Override
        public void onVideoCapabilitiesChanged(boolean isVideoCapable) {
            mIsVideoCapable = isVideoCapable;
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a video
                    // update that lost the race for the mAccountsLock.  In such a scenario by the
                    // time we get here, the original phone account could have been torn down.
                    return;
                }
                mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
            }
        }

        public void updateSimultaneousCallSubSupportMap(Map<Integer,
                Set<Integer>> simultaneousCallSubSupportMap) {
            if (!Flags.simultaneousCallingIndications()) { return; }
            //Check if the simultaneous call support subIds for this account have changed:
            Set<Integer> updatedSimultaneousCallSupportSubIds = new HashSet<>(3);
            updatedSimultaneousCallSupportSubIds.addAll(
                    simultaneousCallSubSupportMap.get(mPhone.getSubId()));
            if (!updatedSimultaneousCallSupportSubIds.equals(mSimultaneousCallSupportedSubIds)) {
                //If necessary, update cache and re-register mAccount:
                mSimultaneousCallSupportedSubIds = updatedSimultaneousCallSupportSubIds;
                synchronized (mAccountsLock) {
                    if (!mAccounts.contains(this)) {
                        // Account has already been torn down, don't try to register it again.
                        // This handles the case where teardown has already happened, and we got a
                        // simultaneous calling support update that lost the race for the
                        // mAccountsLock. In such a scenario by the time we get here, the original
                        // phone account could have been torn down.
                        return;
                    }
                    mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
                }
            }
        }

        public void updateAdhocConfCapability(boolean isAdhocConfCapable) {
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a Ims
                    // registartion update that lost the race for the mAccountsLock.  In such a
                    // scenario by the time we get here, the original phone account could have been
                    // torn down.
                    return;
                }

                if (isAdhocConfCapable !=  mIsAdhocConfCapable) {
                    Log.i(this, "updateAdhocConfCapability - changed, new value: "
                            + isAdhocConfCapable);
                    mIsAdhocConfCapable = isAdhocConfCapable;
                    mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
                }
            }
        }

        public void updateVideoPresenceCapability() {
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a Ims
                    // registration update that lost the race for the mAccountsLock.  In such a
                    // scenario by the time we get here, the original phone account could have been
                    // torn down.
                    return;
                }

                boolean isVideoPresenceSupported = isCarrierVideoPresenceSupported();
                if (mIsVideoPresenceSupported != isVideoPresenceSupported) {
                    Log.i(this, "updateVideoPresenceCapability for subId=" + mPhone.getSubId()
                            + ", new value= " + isVideoPresenceSupported);
                    mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
                }
            }
        }

        public void updateRttCapability() {
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a Ims
                    // registartion update that lost the race for the mAccountsLock.  In such a
                    // scenario by the time we get here, the original phone account could have been
                    // torn down.
                    return;
                }

                boolean isRttEnabled = isRttCurrentlySupported();
                if (isRttEnabled != mIsRttCapable) {
                    Log.i(this, "updateRttCapability - changed, new value: " + isRttEnabled);
                    mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
                }
            }
        }

        public void updateCallComposerCapability(MmTelFeature.MmTelCapabilities capabilities) {
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a Ims
                    // registartion update that lost the race for the mAccountsLock.  In such a
                    // scenario by the time we get here, the original phone account could have been
                    // torn down.
                    return;
                }

                boolean isCallComposerCapable = capabilities.isCapable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER);
                if (isCallComposerCapable != mIsCallComposerCapable) {
                    mIsCallComposerCapable = isCallComposerCapable;
                    Log.i(this, "updateCallComposerCapability - changed, new value: "
                            + isCallComposerCapable);
                    mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
                }
            }
        }

        public void updateDefaultDataSubId(int activeDataSubId) {
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a Ims
                    // registartion update that lost the race for the mAccountsLock.  In such a
                    // scenario by the time we get here, the original phone account could have been
                    // torn down.
                    return;
                }

                boolean isEmergencyPreferred = isEmergencyPreferredAccount(mPhone.getSubId(),
                        activeDataSubId);
                if (isEmergencyPreferred != mIsEmergencyPreferred) {
                    Log.i(this,
                            "updateDefaultDataSubId - changed, new value: " + isEmergencyPreferred);
                    mAccount = registerPstnPhoneAccount(mIsEmergency, mIsTestAccount);
                }
            }
        }

        /**
         * Determines whether RTT is supported given the current state of the
         * device.
         */
        private boolean isRttCurrentlySupported() {
            // First check the emergency case -- if it's supported and turned on,
            // we want to present RTT as available on the emergency-only phone account
            if (mIsEmergency) {
                // First check whether the device supports it
                boolean devicesSupportsRtt =
                        mContext.getResources().getBoolean(R.bool.config_support_rtt);
                boolean deviceSupportsEmergencyRtt = mContext.getResources().getBoolean(
                        R.bool.config_support_simless_emergency_rtt);
                if (!(deviceSupportsEmergencyRtt && devicesSupportsRtt)) {
                    Log.i(this, "isRttCurrentlySupported -- emergency acct and no device support");
                    return false;
                }
                // Next check whether we're in or near a country that supports it
                String country =
                        mPhone.getServiceStateTracker().getLocaleTracker()
                                .getLastKnownCountryIso().toLowerCase(Locale.ROOT);

                String[] supportedCountries = mContext.getResources().getStringArray(
                        R.array.config_simless_emergency_rtt_supported_countries);
                if (supportedCountries == null || Arrays.stream(supportedCountries).noneMatch(
                        Predicate.isEqual(country))) {
                    Log.i(this, "isRttCurrentlySupported -- emergency acct and"
                            + " not supported in this country: " + country);
                    return false;
                }

                return true;
            }

            boolean hasVoiceAvailability = isImsVoiceAvailable();

            boolean isRttSupported = PhoneGlobals.getInstance().phoneMgr
                    .isRttEnabled(mPhone.getSubId());

            boolean isRoaming = mTelephonyManager.isNetworkRoaming(mPhone.getSubId());
            boolean isOnWfc = mPhone.getImsRegistrationTech()
                    == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
            boolean alwaysAllowWhileRoaming = isCarrierAllowRttWhenRoaming();

            boolean shouldDisableBecauseRoamingOffWfc =
                    (isRoaming && !isOnWfc) && !alwaysAllowWhileRoaming;

            Log.i(this, "isRttCurrentlySupported -- regular acct,"
                    + " hasVoiceAvailability: " + hasVoiceAvailability + "\n"
                    + " isRttSupported: " + isRttSupported + "\n"
                    + " alwaysAllowWhileRoaming: " + alwaysAllowWhileRoaming + "\n"
                    + " isRoaming: " + isRoaming + "\n"
                    + " isOnWfc: " + isOnWfc + "\n");

            return hasVoiceAvailability && isRttSupported && !shouldDisableBecauseRoamingOffWfc;
        }

        /**
         * Indicates whether this account supports pausing video calls.
         * @return {@code true} if the account supports pausing video calls, {@code false}
         * otherwise.
         */
        public boolean isVideoPauseSupported() {
            return mIsVideoCapable && mIsVideoPauseSupported;
        }

        /**
         * Indicates whether this account supports merging calls (i.e. conferencing).
         * @return {@code true} if the account supports merging calls, {@code false} otherwise.
         */
        public boolean isMergeCallSupported() {
            return mIsMergeCallSupported;
        }

        /**
         * Indicates whether this account supports merging IMS calls (i.e. conferencing).
         * @return {@code true} if the account supports merging IMS calls, {@code false} otherwise.
         */
        public boolean isMergeImsCallSupported() {
            return mIsMergeImsCallSupported;
        }

        /**
         * Indicates whether this account supports video conferencing.
         * @return {@code true} if the account supports video conferencing, {@code false} otherwise.
         */
        public boolean isVideoConferencingSupported() {
            return mIsVideoConferencingSupported;
        }

        /**
         * Indicate whether this account allow merging of wifi calls when VoWIFI is off.
         * @return {@code true} if allowed, {@code false} otherwise.
         */
        public boolean isMergeOfWifiCallsAllowedWhenVoWifiOff() {
            return mIsMergeOfWifiCallsAllowedWhenVoWifiOff;
        }

        /**
         * Indicates whether this account supports managing IMS conference calls
         * @return {@code true} if the account supports managing IMS conference calls,
         *         {@code false} otherwise.
         */
        public boolean isManageImsConferenceCallSupported() {
            return mIsManageImsConferenceCallSupported;
        }

        /**
         * Indicates whether this account uses a sim call manger.
         * @return {@code true} if the account uses a sim call manager,
         *         {@code false} otherwise.
         */
        public boolean isUsingSimCallManager() {
            return mIsUsingSimCallManager;
        }

        /**
         * Indicates whether this account supports showing the precise call disconnect cause
         * to user (i.e. conferencing).
         * @return {@code true} if the account supports showing the precise call disconnect cause,
         *         {@code false} otherwise.
         */
        public boolean isShowPreciseFailedCause() {
            return mIsShowPreciseFailedCause;
        }

        private boolean isImsVoiceAvailable() {
            if (mMmTelCapabilities != null) {
                return mMmTelCapabilities.isCapable(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
            }

            if (mMmTelManager == null) {
                // The Subscription is invalid, so IMS is unavailable.
                return false;
            }

            // In the rare case that mMmTelCapabilities hasn't been set, try fetching it
            // directly and register callback.
            registerMmTelCapabilityCallback();
            return mMmTelManager.isAvailable(ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)
                    || mMmTelManager.isAvailable(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)
                    || mMmTelManager.isAvailable(
                            ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM,
                    MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        }
    }

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            if (mSubscriptionListenerState != LISTENER_STATE_REGISTERED) {
                mRegisterSubscriptionListenerBackoff.stop();
                mHandlerThread.quitSafely();
            }
            mSubscriptionListenerState = LISTENER_STATE_REGISTERED;

            // Any time the SubscriptionInfo changes rerun the setup
            Log.i(this, "TelecomAccountRegistry: onSubscriptionsChanged - update accounts");
            tearDownAccounts();
            setupAccounts();
        }

        @Override
        public void onAddListenerFailed() {
            // Woe!  Failed to add the listener!
            Log.w(this, "TelecomAccountRegistry: onAddListenerFailed - failed to register "
                    + "OnSubscriptionsChangedListener");

            // Even though registering the listener failed, we will still try to setup the phone
            // accounts now; the phone instances should already be present and ready, so even if
            // telephony registry is poking along we can still try to setup the phone account.
            tearDownAccounts();
            setupAccounts();

            if (mSubscriptionListenerState == LISTENER_STATE_UNREGISTERED) {
                // Initial registration attempt failed; start exponential backoff.
                mSubscriptionListenerState = LISTENER_STATE_PERFORMING_BACKOFF;
                mRegisterSubscriptionListenerBackoff.start();
            } else {
                // We're already doing exponential backoff and a registration failed.
                mRegisterSubscriptionListenerBackoff.notifyFailed();
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_SWITCHED.equals(intent.getAction())) {
                Log.i(this, "TelecomAccountRegistry: User changed, re-registering phone accounts.");

                UserHandle currentUser = intent.getParcelableExtra(Intent.EXTRA_USER);
                mIsPrimaryUser = currentUser == null ? true : currentUser.isSystem();

                // Any time the user changes, re-register the accounts.
                tearDownAccounts();
                setupAccounts();
            } else if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(
                    intent.getAction())) {
                Log.i(this, "Carrier-config changed, checking for phone account updates.");
                int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                handleCarrierConfigChange(subId);
            }
        }
    };

    private BroadcastReceiver mLocaleChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(this, "Locale change; re-registering phone accounts.");
            tearDownAccounts();
            setupAccounts();
        }
    };

    private final TelephonyCallback mTelephonyCallback = new TelecomAccountTelephonyCallback();

    private class TelecomAccountTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.ActiveDataSubscriptionIdListener,
            TelephonyCallback.ServiceStateListener {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            int newState = serviceState.getState();
            Log.i(this, "onServiceStateChanged: newState=%d, mServiceState=%d",
                    newState, mServiceState);
            if (newState == ServiceState.STATE_IN_SERVICE && mServiceState != newState) {
                Log.i(this, "onServiceStateChanged: Tearing down and re-setting up accounts.");
                tearDownAccounts();
                setupAccounts();
            } else {
                synchronized (mAccountsLock) {
                    for (AccountEntry account : mAccounts) {
                        account.updateRttCapability();
                    }
                }
            }
            mServiceState = newState;
        }

        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            mActiveDataSubscriptionId = subId;
            synchronized (mAccountsLock) {
                for (AccountEntry account : mAccounts) {
                    account.updateDefaultDataSubId(mActiveDataSubscriptionId);
                }
            }
        }
    }

    private static TelecomAccountRegistry sInstance;
    private final Context mContext;
    private final TelecomManager mTelecomManager;
    private final android.telephony.ims.ImsManager mImsManager;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private List<AccountEntry> mAccounts = new LinkedList<AccountEntry>();
    private final Object mAccountsLock = new Object();
    private int mSubscriptionListenerState = LISTENER_STATE_UNREGISTERED;
    private int mServiceState = ServiceState.STATE_POWER_OFF;
    private int mActiveDataSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private boolean mIsPrimaryUser = UserHandle.of(ActivityManager.getCurrentUser()).isSystem();
    private ExponentialBackoff mRegisterSubscriptionListenerBackoff;
    private final HandlerThread mHandlerThread = new HandlerThread("TelecomAccountRegistry");

    // TODO: Remove back-pointer from app singleton to Service, since this is not a preferred
    // pattern; redesign. This was added to fix a late release bug.
    private TelephonyConnectionService mTelephonyConnectionService;

    // Used to register subscription changed listener when initial attempts fail.
    private Runnable mRegisterOnSubscriptionsChangedListenerRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSubscriptionListenerState != LISTENER_STATE_REGISTERED) {
                Log.i(this, "TelecomAccountRegistry: performing delayed register.");
                SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                        mOnSubscriptionsChangedListener);
            }
        }
    };

    TelecomAccountRegistry(Context context) {
        mContext = context;
        mTelecomManager = context.getSystemService(TelecomManager.class);
        mImsManager = context.getSystemService(android.telephony.ims.ImsManager.class);
        mTelephonyManager = TelephonyManager.from(context);
        mSubscriptionManager = SubscriptionManager.from(context);
        mHandlerThread.start();
        mHandler = new Handler(Looper.getMainLooper());
        mRegisterSubscriptionListenerBackoff = new ExponentialBackoff(
                REGISTER_START_DELAY_MS,
                REGISTER_MAXIMUM_DELAY_MS,
                2, /* multiplier */
                mHandlerThread.getLooper(),
                mRegisterOnSubscriptionsChangedListenerRunnable);
    }

    /**
     * Get the singleton instance.
     */
    public static synchronized TelecomAccountRegistry getInstance(Context context) {
        if (sInstance == null && context != null) {
            if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
                PackageManager pm = context.getPackageManager();
                if (pm != null && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                        && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING)) {
                    sInstance = new TelecomAccountRegistry(context);
                } else {
                    Log.d(LOG_TAG, "Not initializing TelecomAccountRegistry: "
                            + "missing telephony/calling feature(s)");
                }
            } else {
                sInstance = new TelecomAccountRegistry(context);
            }
        }
        return sInstance;
    }

    void setTelephonyConnectionService(TelephonyConnectionService telephonyConnectionService) {
        this.mTelephonyConnectionService = telephonyConnectionService;
    }

    public TelephonyConnectionService getTelephonyConnectionService() {
        return mTelephonyConnectionService;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * pausing video calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if video pausing is supported.
     */
    boolean isVideoPauseSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isVideoPauseSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * merging calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if merging calls is supported.
     */
    public boolean isMergeCallSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isMergeCallSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * video conferencing.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if video conferencing is supported.
     */
    public boolean isVideoConferencingSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isVideoConferencingSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} allows
     * merging of wifi calls when VoWIFI is disabled.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if merging of wifi calls is allowed when VoWIFI is disabled.
     */
    public boolean isMergeOfWifiCallsAllowedWhenVoWifiOff(final PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            Optional<AccountEntry> result = mAccounts.stream().filter(
                    entry -> entry.getPhoneAccountHandle().equals(handle)).findFirst();

            if (result.isPresent()) {
                return result.get().isMergeOfWifiCallsAllowedWhenVoWifiOff();
            } else {
                return false;
            }
        }
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * merging IMS calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if merging IMS calls is supported.
     */
    public boolean isMergeImsCallSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isMergeImsCallSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * managing IMS conference calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if managing IMS conference calls is supported.
     */
    boolean isManageImsConferenceCallSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isManageImsConferenceCallSupported();
                }
            }
        }
        return false;
    }

    /**
     * showing precise call disconnect cause to the user.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if showing precise call disconnect cause to the user is supported.
     */
    boolean isShowPreciseFailedCause(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isShowPreciseFailedCause();
                }
            }
        }
        return false;
    }

    /**
     * @return Reference to the {@code TelecomAccountRegistry}'s subscription manager.
     */
    SubscriptionManager getSubscriptionManager() {
        return mSubscriptionManager;
    }

    /**
     * Returns the address (e.g. the phone number) associated with a subscription.
     *
     * @param handle The phone account handle to find the subscription address for.
     * @return The address.
     */
    public Uri getAddress(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.mAccount.getAddress();
                }
            }
        }
        return null;
    }

    public void refreshAdhocConference(boolean isEnableAdhocConf) {
        synchronized (mAccountsLock) {
            Log.v(this, "refreshAdhocConference isEnable = " + isEnableAdhocConf);
            for (AccountEntry entry : mAccounts) {
                boolean hasAdhocConfCapability = entry.mAccount.hasCapabilities(
                        PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING);
                if (!isEnableAdhocConf && hasAdhocConfCapability) {
                    entry.updateAdhocConfCapability(isEnableAdhocConf);
                } else if (isEnableAdhocConf && !hasAdhocConfCapability) {
                    entry.updateAdhocConfCapability(entry.mPhone.isImsRegistered());
                }
            }
        }
    }

    /**
     * Returns whethere a the subscription associated with a {@link PhoneAccountHandle} is using a
     * sim call manager.
     *
     * @param handle The phone account handle to find the subscription address for.
     * @return {@code true} if a sim call manager is in use, {@code false} otherwise.
     */
    public boolean isUsingSimCallManager(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isUsingSimCallManager();
                }
            }
        }
        return false;
    }

    /**
     * Sets up all the phone accounts for SIMs on first boot.
     */
    public void setupOnBoot() {
        // TODO: When this object "finishes" we should unregister by invoking
        // SubscriptionManager.getInstance(mContext).unregister(mOnSubscriptionsChangedListener);
        // This is not strictly necessary because it will be unregistered if the
        // notification fails but it is good form.

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionsChanged the first time.
        Log.i(this, "TelecomAccountRegistry: setupOnBoot - register subscription listener");
        SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                mOnSubscriptionsChangedListener);

        // We also need to listen for changes to the service state (e.g. emergency -> in service)
        // because this could signal a removal or addition of a SIM in a single SIM phone.
        mTelephonyManager.registerTelephonyCallback(TelephonyManager.INCLUDE_LOCATION_DATA_NONE,
                new HandlerExecutor(mHandler),
                mTelephonyCallback);

        // Listen for user switches.  When the user switches, we need to ensure that if the current
        // use is not the primary user we disable video calling.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.registerReceiver(mReceiver, filter);

        //We also need to listen for locale changes
        //(e.g. system language changed -> SIM card name changed)
        IntentFilter localeChangeFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        localeChangeFilter.addAction(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED);
        mContext.registerReceiver(mLocaleChangeReceiver, localeChangeFilter);

        registerContentObservers();
    }

    private void registerContentObservers() {
        // Listen to the RTT system setting so that we update it when the user flips it.
        ContentObserver rttUiSettingObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                synchronized (mAccountsLock) {
                    for (AccountEntry account : mAccounts) {
                        account.updateRttCapability();
                    }
                }
            }
        };

        Uri rttSettingUri = Settings.Secure.getUriFor(Settings.Secure.RTT_CALLING_MODE);
        mContext.getContentResolver().registerContentObserver(
                rttSettingUri, false, rttUiSettingObserver);

        // Listen to the changes to the user's Contacts Discovery Setting.
        ContentObserver contactDiscoveryObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                synchronized (mAccountsLock) {
                    for (AccountEntry account : mAccounts) {
                        account.updateVideoPresenceCapability();
                    }
                }
            }
        };
        Uri contactDiscUri = Uri.withAppendedPath(Telephony.SimInfo.CONTENT_URI,
                Telephony.SimInfo.COLUMN_IMS_RCS_UCE_ENABLED);
        mContext.getContentResolver().registerContentObserver(
                contactDiscUri, true /*notifyForDescendants*/, contactDiscoveryObserver);
    }

    /**
     * Determines if the list of {@link AccountEntry}(s) contains an {@link AccountEntry} with a
     * specified {@link PhoneAccountHandle}.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if an entry exists.
     */
    boolean hasAccountEntryForPhoneAccount(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return true;
                }
            }
        }
        return false;
    }

    PhoneAccountHandle getPhoneAccountHandleForSubId(int subId) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getSubId() == subId) {
                    return entry.getPhoneAccountHandle();
                }
            }
        }
        return null;
    }

    /**
     * Un-registers any {@link PhoneAccount}s which are no longer present in the list
     * {@code AccountEntry}(s).
     */
    private void cleanupPhoneAccounts() {
        ComponentName telephonyComponentName =
                new ComponentName(mContext, TelephonyConnectionService.class);
        // This config indicates whether the emergency account was flagged as emergency calls only
        // in which case we need to consider all phone accounts, not just the call capable ones.
        final boolean emergencyCallsOnlyEmergencyAccount = mContext.getResources().getBoolean(
                R.bool.config_emergency_account_emergency_calls_only);
        List<PhoneAccountHandle> accountHandles = emergencyCallsOnlyEmergencyAccount
                ? mTelecomManager.getAllPhoneAccountHandles()
                : mTelecomManager.getCallCapablePhoneAccounts();

        for (PhoneAccountHandle handle : accountHandles) {
            if (telephonyComponentName.equals(handle.getComponentName()) &&
                    !hasAccountEntryForPhoneAccount(handle)) {
                Log.i(this, "Unregistering phone account %s.", handle);
                mTelecomManager.unregisterPhoneAccount(handle);
            }
        }
    }

    private void setupAccounts() {
        // Go through SIM-based phones and register ourselves -- registering an existing account
        // will cause the existing entry to be replaced.
        Phone[] phones = PhoneFactory.getPhones();
        Log.i(this, "setupAccounts: Found %d phones.  Attempting to register.", phones.length);

        final boolean phoneAccountsEnabled = mContext.getResources().getBoolean(
                R.bool.config_pstn_phone_accounts_enabled);

        synchronized (mAccountsLock) {
            try {
                if (phoneAccountsEnabled) {
                    for (Phone phone : phones) {
                        int subscriptionId = phone.getSubId();
                        Log.i(this, "setupAccounts: Phone with subscription id %d", subscriptionId);
                        // setupAccounts can be called multiple times during service changes.
                        // Don't add an account if subscription is not ready.
                        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                            Log.d(this, "setupAccounts: skipping invalid subid %d", subscriptionId);
                            continue;
                        }
                        // Don't add account if it's opportunistic subscription, which is considered
                        // data only for now.
                        SubscriptionInfo info = SubscriptionManager.from(mContext)
                                .getActiveSubscriptionInfo(subscriptionId);
                        if (info == null || info.isOpportunistic()) {
                            Log.d(this, "setupAccounts: skipping unknown or opportunistic subid %d",
                                    subscriptionId);
                            continue;
                        }

                        mAccounts.add(new AccountEntry(phone, false /* emergency */,
                                false /* isTest */));
                    }
                }
            } finally {
                // If we did not list ANY accounts, we need to provide a "default" SIM account
                // for emergency numbers since no actual SIM is needed for dialing emergency
                // numbers but a phone account is.
                if (mAccounts.isEmpty()) {
                    Log.i(this, "setupAccounts: adding default");
                    mAccounts.add(
                            new AccountEntry(PhoneFactory.getDefaultPhone(), true /* emergency */,
                                    false /* isTest */));
                }
            }

            // Add a fake account entry.
            if (DBG && phones.length > 0 && "TRUE".equals(System.getProperty("test_sim"))) {
                Log.i(this, "setupAccounts: adding a fake AccountEntry");
                mAccounts.add(new AccountEntry(phones[0], false /* emergency */,
                        true /* isTest */));
            }
        }

        // Clean up any PhoneAccounts that are no longer relevant
        cleanupPhoneAccounts();
    }

    private void tearDownAccounts() {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                entry.teardown();
            }
            mAccounts.clear();
        }
        // Invalidate the TelephonyManager cache which maps phone account handles to sub ids since
        // all the phone account handles are being recreated at this point.
        PropertyInvalidatedCache.invalidateCache(TelephonyManager.CACHE_KEY_PHONE_ACCOUNT_TO_SUBID);
    }

    /**
     * Handles changes to the carrier configuration which may impact a phone account.  There are
     * some extras defined in the {@link PhoneAccount} which are based on carrier config options.
     * Only checking for carrier config changes when the subscription is configured runs the risk of
     * missing carrier config changes which happen later.
     * @param subId The subid the carrier config changed for, if applicable.  Will be
     *              {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID} if not specified.
     */
    private void handleCarrierConfigChange(int subId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getSubId() == subId) {
                    Log.d(this, "handleCarrierConfigChange: subId=%d, accountSubId=%d", subId,
                            entry.getSubId());
                    entry.reRegisterPstnPhoneAccount();
                }
            }
        }
    }
}
