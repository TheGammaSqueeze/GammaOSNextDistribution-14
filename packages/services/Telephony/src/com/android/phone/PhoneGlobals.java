/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.sysprop.TelephonyProperties;
import android.telecom.TelecomManager;
import android.telephony.AnomalyReporter;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyLocalConnection;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.widget.Toast;

import com.android.ims.ImsFeatureBinderRepository;
import com.android.internal.os.BinderCallsStats;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConfigurationManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SettingsObserver;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.data.DataEvaluation.DataDisallowedReason;
import com.android.internal.telephony.domainselection.DomainSelectionResolver;
import com.android.internal.telephony.emergency.EmergencyStateTracker;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.flags.FeatureFlagsImpl;
import com.android.internal.telephony.ims.ImsResolver;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.uicc.UiccPort;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.settings.SettingsConstants;
import com.android.phone.vvm.CarrierVvmPackageInstalledReceiver;
import com.android.services.telephony.rcs.TelephonyRcsService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Global state for the telephony subsystem when running in the primary
 * phone process.
 */
public class PhoneGlobals extends ContextWrapper {
    public static final String LOG_TAG = "PhoneGlobals";

    /**
     * Phone app-wide debug level:
     *   0 - no debug logging
     *   1 - normal debug logging if ro.debuggable is set (which is true in
     *       "eng" and "userdebug" builds but not "user" builds)
     *   2 - ultra-verbose debug logging
     *
     * Most individual classes in the phone app have a local DBG constant,
     * typically set to
     *   (PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1)
     * or else
     *   (PhoneApp.DBG_LEVEL >= 2)
     * depending on the desired verbosity.
     *
     * ***** DO NOT SUBMIT WITH DBG_LEVEL > 0 *************
     */
    public static final int DBG_LEVEL = 0;

    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);

    // Message codes; see mHandler below.
    private static final int EVENT_SIM_NETWORK_LOCKED = 3;
    private static final int EVENT_SIM_STATE_CHANGED = 8;
    private static final int EVENT_DATA_ROAMING_DISCONNECTED = 10;
    private static final int EVENT_DATA_ROAMING_CONNECTED = 11;
    private static final int EVENT_DATA_ROAMING_OK = 12;
    private static final int EVENT_UNSOL_CDMA_INFO_RECORD = 13;
    private static final int EVENT_DATA_ROAMING_SETTINGS_CHANGED = 15;
    private static final int EVENT_MOBILE_DATA_SETTINGS_CHANGED = 16;
    private static final int EVENT_CARRIER_CONFIG_CHANGED = 17;
    private static final int EVENT_MULTI_SIM_CONFIG_CHANGED = 18;

    // The MMI codes are also used by the InCallScreen.
    public static final int MMI_INITIATE = 51;
    public static final int MMI_COMPLETE = 52;
    public static final int MMI_CANCEL = 53;
    // Don't use message codes larger than 99 here; those are reserved for
    // the individual Activities of the Phone UI.

    public static final int AIRPLANE_ON = 1;
    public static final int AIRPLANE_OFF = 0;

    /**
     * Allowable values for the wake lock code.
     *   SLEEP means the device can be put to sleep.
     *   PARTIAL means wake the processor, but we display can be kept off.
     *   FULL means wake both the processor and the display.
     */
    public enum WakeState {
        SLEEP,
        PARTIAL,
        FULL
    }

    private static PhoneGlobals sMe;

    CallManager mCM;
    CallNotifier notifier;
    NotificationMgr notificationMgr;
    TelephonyRcsService mTelephonyRcsService;
    public PhoneInterfaceManager phoneMgr;
    public ImsRcsController imsRcsController;
    public ImsStateCallbackController mImsStateCallbackController;
    public ImsProvisioningController mImsProvisioningController;
    CarrierConfigLoader configLoader;

    private Phone phoneInEcm;

    static boolean sVoiceCapable = true;

    // TODO: Remove, no longer used.
    CdmaPhoneCallState cdmaPhoneCallState;

    // The currently-active PUK entry activity and progress dialog.
    // Normally, these are the Emergency Dialer and the subsequent
    // progress dialog.  null if there is are no such objects in
    // the foreground.
    private Activity mPUKEntryActivity;
    private ProgressDialog mPUKEntryProgressDialog;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"ROAMING_NOTIFICATION_REASON_"},
            value = {
                    ROAMING_NOTIFICATION_REASON_DATA_SETTING_CHANGED,
                    ROAMING_NOTIFICATION_REASON_DATA_ROAMING_SETTING_CHANGED,
                    ROAMING_NOTIFICATION_REASON_CARRIER_CONFIG_CHANGED,
                    ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED,
                    ROAMING_NOTIFICATION_REASON_DEFAULT_DATA_SUBS_CHANGED})
    public @interface RoamingNotificationReason {}
    private static final int ROAMING_NOTIFICATION_REASON_DATA_SETTING_CHANGED = 0;
    private static final int ROAMING_NOTIFICATION_REASON_DATA_ROAMING_SETTING_CHANGED = 1;
    private static final int ROAMING_NOTIFICATION_REASON_CARRIER_CONFIG_CHANGED = 2;
    private static final int ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED = 3;
    private static final int ROAMING_NOTIFICATION_REASON_DEFAULT_DATA_SUBS_CHANGED = 4;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"ROAMING_NOTIFICATION_"},
            value = {
                    ROAMING_NOTIFICATION_NO_NOTIFICATION,
                    ROAMING_NOTIFICATION_CONNECTED,
                    ROAMING_NOTIFICATION_DISCONNECTED})
    public @interface RoamingNotification {}

    private static final int ROAMING_NOTIFICATION_NO_NOTIFICATION = 0;
    private static final int ROAMING_NOTIFICATION_CONNECTED       = 1;
    private static final int ROAMING_NOTIFICATION_DISCONNECTED    = 2;

    @RoamingNotification
    private int mCurrentRoamingNotification = ROAMING_NOTIFICATION_NO_NOTIFICATION;

    /**
     * Reasons that have already shown notification to prevent duplicate shows for the same reason.
     */
    private ArraySet<String> mShownNotificationReasons = new ArraySet<>();

    // For reorganize_roaming_notification feature disabled.
    @RoamingNotification
    private int mPrevRoamingNotification = ROAMING_NOTIFICATION_NO_NOTIFICATION;

    // For reorganize_roaming_notification feature disabled.
    /** Operator numerics for which we've shown is-roaming notifications. **/
    private ArraySet<String> mPrevRoamingOperatorNumerics = new ArraySet<>();

    private WakeState mWakeState = WakeState.SLEEP;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mPartialWakeLock;
    private KeyguardManager mKeyguardManager;

    private int mDefaultDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private final LocalLog mDataRoamingNotifLog = new LocalLog(50);

    // Broadcast receiver for various intent broadcasts (see onCreate())
    private final BroadcastReceiver mReceiver = new PhoneAppBroadcastReceiver();

    private final CarrierVvmPackageInstalledReceiver mCarrierVvmPackageInstalledReceiver =
            new CarrierVvmPackageInstalledReceiver();

    private SettingsObserver mSettingsObserver;
    private BinderCallsStats.SettingsObserver mBinderCallsSettingsObserver;

    // Mapping of phone ID to the associated TelephonyCallback. These should be registered without
    // fine or coarse location since we only use ServiceState for
    private PhoneAppCallback[] mTelephonyCallbacks;

    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    private class PhoneAppCallback extends TelephonyCallback implements
            TelephonyCallback.ServiceStateListener {
        private final int mSubId;

        PhoneAppCallback(int subId) {
            mSubId = subId;
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            // Note when registering that we should be registering with INCLUDE_LOCATION_DATA_NONE.
            // PhoneGlobals only uses the state and roaming status, which does not require location.
            handleServiceStateChanged(serviceState, mSubId);
        }

        public int getSubId() {
            return mSubId;
        }
    }

    private static class EventSimStateChangedBag {
        final int mPhoneId;
        final String mIccStatus;

        EventSimStateChangedBag(int phoneId, String iccStatus) {
            mPhoneId = phoneId;
            mIccStatus = iccStatus;
        }
    }

    // Some carrier config settings disable the network lock screen, so we call handleSimLock
    // when either SIM_LOCK or CARRIER_CONFIG changes so that no matter which one happens first,
    // we still do the right thing
    private void handleSimLock(int subType, Phone phone) {
        PersistableBundle cc = getCarrierConfigForSubId(phone.getSubId());
        if (!CarrierConfigManager.isConfigForIdentifiedCarrier(cc)) {
            // If we only have the default carrier config just return, to avoid popping up the
            // the SIM lock screen when it's disabled by the carrier.
            Log.i(LOG_TAG, "Not showing 'SIM network unlock' screen. Carrier config not loaded");
            return;
        }
        if (cc.getBoolean(CarrierConfigManager.KEY_IGNORE_SIM_NETWORK_LOCKED_EVENTS_BOOL)) {
            // Some products don't have the concept of a "SIM network lock"
            Log.i(LOG_TAG, "Not showing 'SIM network unlock' screen. Disabled by carrier config");
            return;
        }

        // if passed in subType is unknown, retrieve it here.
        if (subType == -1) {
            final UiccPort uiccPort = phone.getUiccPort();
            if (uiccPort == null) {
                Log.e(LOG_TAG,
                        "handleSimLock: uiccPort for phone " + phone.getPhoneId() + " is null");
                return;
            }
            final UiccProfile uiccProfile = uiccPort.getUiccProfile();
            if (uiccProfile == null) {
                Log.e(LOG_TAG,
                        "handleSimLock: uiccProfile for phone " + phone.getPhoneId() + " is null");
                return;
            }
            subType = uiccProfile.getApplication(
                    uiccProfile.mCurrentAppType).getPersoSubState().ordinal();
        }
        // Normal case: show the "SIM network unlock" PIN entry screen.
        // The user won't be able to do anything else until
        // they enter a valid SIM network PIN.
        Log.i(LOG_TAG, "show sim depersonal panel");
        IccNetworkDepersonalizationPanel.showDialog(phone, subType);
    }

    private boolean isSimLocked(Phone phone) {
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        return tm.createForSubscriptionId(phone.getSubId()).getSimState()
                == TelephonyManager.SIM_STATE_NETWORK_LOCKED;
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            PhoneConstants.State phoneState;
            if (VDBG) Log.v(LOG_TAG, "event=" + msg.what);
            switch (msg.what) {
                // TODO: This event should be handled by the lock screen, just
                // like the "SIM missing" and "Sim locked" cases (bug 1804111).
                case EVENT_SIM_NETWORK_LOCKED:
                    int subType = (Integer) ((AsyncResult) msg.obj).result;
                    Phone phone = (Phone) ((AsyncResult) msg.obj).userObj;
                    handleSimLock(subType, phone);
                    break;

                case EVENT_DATA_ROAMING_DISCONNECTED:
                    notificationMgr.showDataRoamingNotification(msg.arg1, false);
                    break;

                case EVENT_DATA_ROAMING_CONNECTED:
                    notificationMgr.showDataRoamingNotification(msg.arg1, true);
                    break;

                case EVENT_DATA_ROAMING_OK:
                    notificationMgr.hideDataRoamingNotification();
                    break;

                case MMI_COMPLETE:
                    onMMIComplete((AsyncResult) msg.obj);
                    break;

                case MMI_CANCEL:
                    PhoneUtils.cancelMmiCode(mCM.getFgPhone());
                    break;

                case EVENT_SIM_STATE_CHANGED:
                    EventSimStateChangedBag bag = (EventSimStateChangedBag)msg.obj;
                    // Dismiss the "No services" notification if the SIM is removed.
                    if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(bag.mIccStatus)) {
                        notificationMgr.dismissNetworkSelectionNotificationForInactiveSubId();
                    }

                    // Marks the event where the SIM goes into ready state.
                    // Right now, this is only used for the PUK-unlocking process.
                    if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(bag.mIccStatus)
                            || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(bag.mIccStatus)
                            || IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(bag.mIccStatus)
                            || IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(bag.mIccStatus)) {
                        // When the right event is triggered and there are UI objects in the
                        // foreground, we close them to display the lock panel.
                        if (mPUKEntryActivity != null) {
                            Log.i(LOG_TAG, "Dismiss puk entry activity");
                            mPUKEntryActivity.finish();
                            mPUKEntryActivity = null;
                        }
                        if (mPUKEntryProgressDialog != null) {
                            Log.i(LOG_TAG, "Dismiss puk progress dialog");
                            mPUKEntryProgressDialog.dismiss();
                            mPUKEntryProgressDialog = null;
                        }
                        Log.i(LOG_TAG, "Dismissing depersonal panel" + (bag.mIccStatus));
                        IccNetworkDepersonalizationPanel.dialogDismiss(bag.mPhoneId);
                    }
                    break;

                case EVENT_UNSOL_CDMA_INFO_RECORD:
                    //TODO: handle message here;
                    break;
                case EVENT_DATA_ROAMING_SETTINGS_CHANGED:
                    if (mFeatureFlags.reorganizeRoamingNotification()) {
                        updateDataRoamingStatus(
                                ROAMING_NOTIFICATION_REASON_DATA_ROAMING_SETTING_CHANGED);
                    } else {
                        updateDataRoamingStatusForFeatureDisabled(null);
                    }
                    break;
                case EVENT_MOBILE_DATA_SETTINGS_CHANGED:
                    if (mFeatureFlags.reorganizeRoamingNotification()) {
                        updateDataRoamingStatus(ROAMING_NOTIFICATION_REASON_DATA_SETTING_CHANGED);
                    } else {
                        updateDataRoamingStatusForFeatureDisabled(null);
                    }
                    break;
                case EVENT_CARRIER_CONFIG_CHANGED:
                    int subId = (Integer) msg.obj;
                    // The voicemail number could be overridden by carrier config, so need to
                    // refresh the message waiting (voicemail) indicator.
                    refreshMwiIndicator(subId);
                    phone = getPhone(subId);
                    if (phone != null) {
                        if (isSimLocked(phone)) {
                            // pass in subType=-1 so handleSimLock can find the actual subType if
                            // needed. This is safe as valid values for subType are >= 0
                            handleSimLock(-1, phone);
                        }
                        TelephonyManager tm = getSystemService(TelephonyManager.class);
                        PhoneAppCallback callback = mTelephonyCallbacks[phone.getPhoneId()];
                        // TODO: We may need to figure out a way to unregister if subId is invalid
                        tm.createForSubscriptionId(callback.getSubId())
                                .unregisterTelephonyCallback(callback);
                        callback = new PhoneAppCallback(subId);
                        tm.createForSubscriptionId(subId).registerTelephonyCallback(
                                TelephonyManager.INCLUDE_LOCATION_DATA_COARSE, mHandler::post,
                                callback);
                        mTelephonyCallbacks[phone.getPhoneId()] = callback;
                    }
                    break;
                case EVENT_MULTI_SIM_CONFIG_CHANGED:
                    int activeModems = (int) ((AsyncResult) msg.obj).result;
                    TelephonyManager tm = getSystemService(TelephonyManager.class);
                    // Unregister all previous callbacks
                    for (int phoneId = 0; phoneId < mTelephonyCallbacks.length; phoneId++) {
                        PhoneAppCallback callback = mTelephonyCallbacks[phoneId];
                        if (callback != null) {
                            tm.createForSubscriptionId(callback.getSubId())
                                    .unregisterTelephonyCallback(callback);
                            mTelephonyCallbacks[phoneId] = null;
                        }
                    }
                    // Register callbacks for all active modems
                    for (int phoneId = 0; phoneId < activeModems; phoneId++) {
                        int sub = PhoneFactory.getPhone(phoneId).getSubId();
                        PhoneAppCallback callback = new PhoneAppCallback(sub);
                        tm.createForSubscriptionId(sub).registerTelephonyCallback(
                                TelephonyManager.INCLUDE_LOCATION_DATA_NONE, mHandler::post,
                                callback);
                        mTelephonyCallbacks[phoneId] = callback;
                    }
                    break;
            }
        }
    };

    public PhoneGlobals(Context context) {
        super(context);
        sMe = this;
        if (mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                mSettingsObserver = new SettingsObserver(context, mHandler);
            }
        } else {
            mSettingsObserver = new SettingsObserver(context, mHandler);
        }
    }

    public void onCreate() {
        if (VDBG) Log.v(LOG_TAG, "onCreate()...");

        ContentResolver resolver = getContentResolver();

        if (mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()) {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                Log.v(LOG_TAG, "onCreate()... but not defined FEATURE_TELEPHONY");
                return;
            }
        }

        // Initialize the shim from frameworks/opt/telephony into packages/services/Telephony.
        TelephonyLocalConnection.setInstance(new LocalConnectionImpl(this));

        TelephonyManager tm = getSystemService(TelephonyManager.class);
        // Cache the "voice capable" flag.
        // This flag currently comes from a resource (which is
        // overrideable on a per-product basis):
        sVoiceCapable = tm.isVoiceCapable();
        // ...but this might eventually become a PackageManager "system
        // feature" instead, in which case we'd do something like:
        // sVoiceCapable =
        //   getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_VOICE_CALLS);

        if (mCM == null) {
            // Initialize AnomalyReporter early so that it can be used
            AnomalyReporter.initialize(this);

            // Inject telephony component factory if configured using other jars.
            XmlResourceParser parser = getResources().getXml(R.xml.telephony_injection);
            TelephonyComponentFactory.getInstance().injectTheComponentFactory(parser);

            // Create DomainSelectionResolver always, but it MUST be initialized only when
            // the device supports AOSP domain selection architecture and
            // has new IRadio that supports its related HAL APIs.
            String dssComponentName = getResources().getString(
                    R.string.config_domain_selection_service_component_name);
            DomainSelectionResolver.make(this, dssComponentName);

            // Initialize the telephony framework
            PhoneFactory.makeDefaultPhones(this, mFeatureFlags);

            // Initialize the DomainSelectionResolver after creating the Phone instance
            // to check the Radio HAL version.
            if (DomainSelectionResolver.getInstance().isDomainSelectionSupported()) {
                DomainSelectionResolver.getInstance().initialize();
                // Initialize EmergencyStateTracker if domain selection is supported
                boolean isSuplDdsSwitchRequiredForEmergencyCall = getResources()
                        .getBoolean(R.bool.config_gnss_supl_requires_default_data_for_emergency);
                EmergencyStateTracker.make(this, isSuplDdsSwitchRequiredForEmergencyCall);
            }

            // Only bring up ImsResolver if the device supports having an IMS stack.
            if (getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_IMS)) {
                // Get the package name of the default IMS implementation.
                String defaultImsMmtelPackage = getResources().getString(
                        R.string.config_ims_mmtel_package);
                String defaultImsRcsPackage = getResources().getString(
                        R.string.config_ims_rcs_package);
                ImsResolver.make(this, defaultImsMmtelPackage,
                        defaultImsRcsPackage, PhoneFactory.getPhones().length,
                        new ImsFeatureBinderRepository());
                ImsResolver.getInstance().initialize();

                // With the IMS phone created, load static config.xml values from the phone process
                // so that it can be provided to the ImsPhoneCallTracker.
                for (Phone p : PhoneFactory.getPhones()) {
                    Phone imsPhone = p.getImsPhone();
                    if (imsPhone != null && imsPhone instanceof ImsPhone) {
                        ImsPhone theImsPhone = (ImsPhone) imsPhone;
                        if (theImsPhone.getCallTracker() instanceof ImsPhoneCallTracker) {
                            ImsPhoneCallTracker ict = (ImsPhoneCallTracker)
                                    theImsPhone.getCallTracker();

                            ImsPhoneCallTracker.Config config = new ImsPhoneCallTracker.Config();
                            config.isD2DCommunicationSupported = getResources().getBoolean(
                                    R.bool.config_use_device_to_device_communication);
                            ict.setConfig(config);
                        }
                    }
                }
                RcsProvisioningMonitor.make(this);
            }

            // Start TelephonyDebugService After the default phone is created.
            Intent intent = new Intent(this, TelephonyDebugService.class);
            startService(intent);

            mCM = CallManager.getInstance();

            // Create the NotificationMgr singleton, which is used to display
            // status bar icons and control other status bar behavior.
            notificationMgr = NotificationMgr.init(this);

            // Create the SatelliteController singleton, which acts as a backend service for
            // {@link android.telephony.satellite.SatelliteManager}.
            SatelliteController.make(this, mFeatureFlags);

            // Create an instance of CdmaPhoneCallState and initialize it to IDLE
            cdmaPhoneCallState = new CdmaPhoneCallState();
            cdmaPhoneCallState.CdmaPhoneCallStateInit();

            // before registering for phone state changes
            mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, LOG_TAG);
            // lock used to keep the processor awake, when we don't care for the display.
            mPartialWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, LOG_TAG);

            mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

            phoneMgr = PhoneInterfaceManager.init(this, mFeatureFlags);

            imsRcsController = ImsRcsController.init(this, mFeatureFlags);

            configLoader = CarrierConfigLoader.init(this, mFeatureFlags);

            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS)) {
                mImsStateCallbackController =
                        ImsStateCallbackController.make(this, PhoneFactory.getPhones().length);
                mTelephonyRcsService = new TelephonyRcsService(this,
                        PhoneFactory.getPhones().length);
                mTelephonyRcsService.initialize();
                imsRcsController.setRcsService(mTelephonyRcsService);
                mImsProvisioningController =
                        ImsProvisioningController.make(this, PhoneFactory.getPhones().length);
            }

            // Create the CallNotifier singleton, which handles
            // asynchronous events from the telephony layer (like
            // launching the incoming-call UI when an incoming call comes
            // in.)
            notifier = CallNotifier.init(this);

            PhoneUtils.registerIccStatus(mHandler, EVENT_SIM_NETWORK_LOCKED);

            // register for MMI/USSD
            mCM.registerForMmiComplete(mHandler, MMI_COMPLETE, null);

            // Initialize cell status using current airplane mode.
            handleAirplaneModeChange(
                    Settings.Global.getInt(
                                    getContentResolver(),
                                    Settings.Global.AIRPLANE_MODE_ON,
                                    AIRPLANE_OFF)
                            == AIRPLANE_ON);

            // Register for misc other intent broadcasts.
            IntentFilter intentFilter =
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
            intentFilter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
            registerReceiver(mReceiver, intentFilter);
            int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId)) {
                if (VDBG) {
                    Log.v(LOG_TAG, "Loaded initial default data sub: " + defaultDataSubId);
                }
                mDefaultDataSubId = defaultDataSubId;
                registerSettingsObserver();
                updateDataRoamingStatus(ROAMING_NOTIFICATION_REASON_DEFAULT_DATA_SUBS_CHANGED);
            }

            PhoneConfigurationManager.registerForMultiSimConfigChange(
                    mHandler, EVENT_MULTI_SIM_CONFIG_CHANGED, null);

            mTelephonyCallbacks = new PhoneAppCallback[tm.getSupportedModemCount()];
            if (tm.getSupportedModemCount() > 0) {
                for (Phone phone : PhoneFactory.getPhones()) {
                    int subId = phone.getSubId();
                    PhoneAppCallback callback = new PhoneAppCallback(subId);
                    tm.createForSubscriptionId(subId).registerTelephonyCallback(
                            TelephonyManager.INCLUDE_LOCATION_DATA_NONE, mHandler::post, callback);
                    mTelephonyCallbacks[phone.getPhoneId()] = callback;
                }
            }
            mCarrierVvmPackageInstalledReceiver.register(this);

            //set the default values for the preferences in the phone.
            PreferenceManager.setDefaultValues(this, R.xml.call_feature_setting, false);
        }

        // XXX pre-load the SimProvider so that it's ready
        resolver.getType(Uri.parse("content://icc/adn"));

        // TODO: Register for Cdma Information Records
        // phone.registerCdmaInformationRecord(mHandler, EVENT_UNSOL_CDMA_INFO_RECORD, null);

        // Read HAC settings and configure audio hardware
        if (getResources().getBoolean(R.bool.hac_enabled)) {
            int hac = android.provider.Settings.System.getInt(
                    getContentResolver(),
                    android.provider.Settings.System.HEARING_AID,
                    0);
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameters(
                    SettingsConstants.HAC_KEY + "=" + (hac == SettingsConstants.HAC_ENABLED
                            ? SettingsConstants.HAC_VAL_ON : SettingsConstants.HAC_VAL_OFF));
        }

        // Start tracking Binder latency for the phone process.
        mBinderCallsSettingsObserver = new BinderCallsStats.SettingsObserver(
            getApplicationContext(),
            new BinderCallsStats(
                    new BinderCallsStats.Injector(),
                    com.android.internal.os.BinderLatencyProto.Dims.TELEPHONY));
    }

    /**
     * Returns the singleton instance of the PhoneApp.
     */
    public static PhoneGlobals getInstance() {
        if (sMe == null) {
            throw new IllegalStateException("No PhoneGlobals here!");
        }
        return sMe;
    }

    /**
     * Returns the default phone.
     *
     * WARNING: This method should be used carefully, now that there may be multiple phones.
     */
    public static Phone getPhone() {
        return PhoneFactory.getDefaultPhone();
    }

    public static Phone getPhone(int subId) {
        return PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
    }

    /* package */ CallManager getCallManager() {
        return mCM;
    }

    public PersistableBundle getCarrierConfig() {
        return getCarrierConfigForSubId(SubscriptionManager.getDefaultSubscriptionId());
    }

    public PersistableBundle getCarrierConfigForSubId(int subId) {
        return configLoader.getConfigForSubIdWithFeature(subId, getOpPackageName(),
                getAttributionTag());
    }

    private void registerSettingsObserver() {
        mSettingsObserver.unobserve();
        String dataRoamingSetting = Settings.Global.DATA_ROAMING;
        String mobileDataSetting = Settings.Global.MOBILE_DATA;
        if (TelephonyManager.getDefault().getSimCount() > 1) {
            int subId = mDefaultDataSubId;
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                dataRoamingSetting += subId;
                mobileDataSetting += subId;
            }
        }

        // Listen for user data roaming setting changed event
        mSettingsObserver.observe(Settings.Global.getUriFor(dataRoamingSetting),
                EVENT_DATA_ROAMING_SETTINGS_CHANGED);

        // Listen for mobile data setting changed event
        mSettingsObserver.observe(Settings.Global.getUriFor(mobileDataSetting),
                EVENT_MOBILE_DATA_SETTINGS_CHANGED);
    }

    /**
     * Sets the activity responsible for un-PUK-blocking the device
     * so that we may close it when we receive a positive result.
     * mPUKEntryActivity is also used to indicate to the device that
     * we are trying to un-PUK-lock the phone. In other words, iff
     * it is NOT null, then we are trying to unlock and waiting for
     * the SIM to move to READY state.
     *
     * @param activity is the activity to close when PUK has
     * finished unlocking. Can be set to null to indicate the unlock
     * or SIM READYing process is over.
     */
    void setPukEntryActivity(Activity activity) {
        Log.i(LOG_TAG, "setPukEntryActivity - set to " + (activity == null ? "null" : "activity"));
        mPUKEntryActivity = activity;
    }

    Activity getPUKEntryActivity() {
        return mPUKEntryActivity;
    }

    /**
     * Sets the dialog responsible for notifying the user of un-PUK-
     * blocking - SIM READYing progress, so that we may dismiss it
     * when we receive a positive result.
     *
     * @param dialog indicates the progress dialog informing the user
     * of the state of the device.  Dismissed upon completion of
     * READYing process
     */
    void setPukEntryProgressDialog(ProgressDialog dialog) {
        Log.i(LOG_TAG, "setPukEntryProgressDialog - set to "
                + (dialog == null ? "null" : "activity"));
        mPUKEntryProgressDialog = dialog;
    }

    KeyguardManager getKeyguardManager() {
        return mKeyguardManager;
    }

    private void onMMIComplete(AsyncResult r) {
        if (VDBG) Log.d(LOG_TAG, "onMMIComplete()...");
        MmiCode mmiCode = (MmiCode) r.result;
        PhoneUtils.displayMMIComplete(mmiCode.getPhone(), getInstance(), mmiCode, null, null);
    }

    private void initForNewRadioTechnology() {
        if (DBG) Log.d(LOG_TAG, "initForNewRadioTechnology...");
        notifier.updateCallNotifierRegistrationsAfterRadioTechnologyChange();
    }

    private void handleAirplaneModeChange(boolean isAirplaneNewlyOn) {
        Log.i(LOG_TAG, "handleAirplaneModeChange: isAirplaneNewlyOn=" + isAirplaneNewlyOn);
        int cellState =
                Settings.Global.getInt(
                        getContentResolver(), Settings.Global.CELL_ON, PhoneConstants.CELL_ON_FLAG);
        switch (cellState) {
            case PhoneConstants.CELL_OFF_FLAG:
                // Airplane mode does not affect the cell radio if user has turned it off.
                Log.i(LOG_TAG, "Ignore airplane mode change due to cell off.");
                break;
            case PhoneConstants.CELL_ON_FLAG:
                maybeTurnCellOff(isAirplaneNewlyOn);
                break;
            case PhoneConstants.CELL_OFF_DUE_TO_AIRPLANE_MODE_FLAG:
                maybeTurnCellOn(isAirplaneNewlyOn);
                break;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            phone.getServiceStateTracker().onAirplaneModeChanged(isAirplaneNewlyOn);
        }
    }

    /*
     * Returns true if the radio must be turned off when entering airplane mode.
     */
    private boolean isCellOffInAirplaneMode() {
        String airplaneModeRadios =
                Settings.Global.getString(
                        getContentResolver(), Settings.Global.AIRPLANE_MODE_RADIOS);
        return airplaneModeRadios == null
                || airplaneModeRadios.contains(Settings.Global.RADIO_CELL);
    }

    private void setRadioPowerOff() {
        Log.i(LOG_TAG, "Turning radio off - airplane");
        Settings.Global.putInt(
                getContentResolver(),
                Settings.Global.CELL_ON,
                PhoneConstants.CELL_OFF_DUE_TO_AIRPLANE_MODE_FLAG);
        Settings.Global.putInt(getContentResolver(), Settings.Global.ENABLE_CELLULAR_ON_BOOT, 0);
        TelephonyProperties.airplane_mode_on(true); // true means int value 1
        PhoneUtils.setRadioPower(false);
        clearCacheOnRadioOff();
    }

    /** Clear fields on power off radio **/
    private void clearCacheOnRadioOff() {
        // Re-show is-roaming notifications after APM mode
        if (mFeatureFlags.reorganizeRoamingNotification()) {
            mShownNotificationReasons.clear();
        } else {
            mPrevRoamingOperatorNumerics.clear();
        }
    }

    private void setRadioPowerOn() {
        Log.i(LOG_TAG, "Turning radio on - airplane");
        Settings.Global.putInt(
                getContentResolver(), Settings.Global.CELL_ON, PhoneConstants.CELL_ON_FLAG);
        Settings.Global.putInt(getContentResolver(), Settings.Global.ENABLE_CELLULAR_ON_BOOT, 1);
        TelephonyProperties.airplane_mode_on(false); // false means int value 0
        PhoneUtils.setRadioPower(true);
    }

    private void maybeTurnCellOff(boolean isAirplaneNewlyOn) {
        if (isAirplaneNewlyOn) {
            // If we are trying to turn off the radio, make sure there are no active
            // emergency calls.  If there are, switch airplane mode back to off.
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);

            if (tm != null && tm.isInEmergencyCall()) {
                // Switch airplane mode back to off.
                ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                cm.setAirplaneMode(false);
                Toast.makeText(this, R.string.radio_off_during_emergency_call, Toast.LENGTH_LONG)
                        .show();
                Log.i(LOG_TAG, "Ignoring airplane mode: emergency call. Turning airplane off");
            } else if (isCellOffInAirplaneMode()) {
                setRadioPowerOff();
            } else {
                Log.i(LOG_TAG, "Ignoring airplane mode: settings prevent cell radio power off");
            }
        } else {
            Log.i(LOG_TAG, "Ignoring airplane mode: not newly on");
        }
    }

    private void maybeTurnCellOn(boolean isAirplaneNewlyOn) {
        if (!isAirplaneNewlyOn) {
            setRadioPowerOn();
        } else {
            Log.i(LOG_TAG, "Ignoring airplane mode off: radio is already on.");
        }
    }

    /**
     * Receiver for misc intent broadcasts the Phone app cares about.
     */
    private class PhoneAppBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneMode = intent.getBooleanExtra("state", false);
                handleAirplaneModeChange(airplaneMode);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                // re-register as it may be a new IccCard
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                if (SubscriptionManager.isValidPhoneId(phoneId)) {
                    PhoneUtils.unregisterIccStatus(mHandler, phoneId);
                    PhoneUtils.registerIccStatus(mHandler, EVENT_SIM_NETWORK_LOCKED, phoneId);
                }
                String iccStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_SIM_STATE_CHANGED,
                        new EventSimStateChangedBag(phoneId, iccStatus)));
            } else if (action.equals(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED)) {
                String newPhone = intent.getStringExtra(PhoneConstants.PHONE_NAME_KEY);
                Log.d(LOG_TAG, "Radio technology switched. Now " + newPhone + " is active.");
                initForNewRadioTechnology();
            } else if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY, 0);
                phoneInEcm = PhoneFactory.getPhone(phoneId);
                Log.d(LOG_TAG, "Emergency Callback Mode. phoneId:" + phoneId);
                if (phoneInEcm != null) {
                    if (TelephonyCapabilities.supportsEcm(phoneInEcm)) {
                        Log.d(LOG_TAG, "Emergency Callback Mode arrived in PhoneApp.");
                        // Start Emergency Callback Mode service
                        if (intent.getBooleanExtra(
                                TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false)) {
                            context.startService(new Intent(context,
                                    EmergencyCallbackModeService.class));
                        } else {
                            phoneInEcm = null;
                        }
                    } else {
                        // It doesn't make sense to get ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
                        // on a device that doesn't support ECM in the first place.
                        Log.e(LOG_TAG, "Got ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, but "
                                + "ECM isn't supported for phone: " + phoneInEcm.getPhoneName());
                        phoneInEcm = null;
                    }
                } else {
                    Log.w(LOG_TAG, "phoneInEcm is null.");
                }
            } else if (action.equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                // Roaming status could be overridden by carrier config, so we need to update it.
                if (VDBG) Log.v(LOG_TAG, "carrier config changed.");
                if (mFeatureFlags.reorganizeRoamingNotification()) {
                    updateDataRoamingStatus(ROAMING_NOTIFICATION_REASON_CARRIER_CONFIG_CHANGED);
                } else {
                    updateDataRoamingStatusForFeatureDisabled(null);
                }
                updateLimitedSimFunctionForDualSim();
                int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                if (SubscriptionManager.isValidSubscriptionId(subId)) {
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARRIER_CONFIG_CHANGED,
                            new Integer(subId)));
                }
            } else if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                // We also need to pay attention when default data subscription changes.
                if (VDBG) Log.v(LOG_TAG, "default data sub changed.");
                mDefaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
                registerSettingsObserver();
                Phone phone = getPhone(mDefaultDataSubId);
                if (phone != null) {
                    if (mFeatureFlags.reorganizeRoamingNotification()) {
                        updateDataRoamingStatus(
                                ROAMING_NOTIFICATION_REASON_DEFAULT_DATA_SUBS_CHANGED);
                    } else {
                        updateDataRoamingStatusForFeatureDisabled(null);
                    }
                }
            }
        }
    }

    private void handleServiceStateChanged(ServiceState serviceState, int subId) {
        if (VDBG) Log.v(LOG_TAG, "handleServiceStateChanged");
        int state = serviceState.getState();
        notificationMgr.updateNetworkSelection(state, subId);

        if (VDBG) {
            Log.v(LOG_TAG, "subId=" + subId + ", mDefaultDataSubId="
                    + mDefaultDataSubId + ", ss roaming=" + serviceState.getDataRoaming());
        }
        if (subId == mDefaultDataSubId) {
            if (mFeatureFlags.reorganizeRoamingNotification()) {
                updateDataRoamingStatus(ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED);
            } else {
                updateDataRoamingStatusForFeatureDisabled(serviceState.getOperatorNumeric());
            }
        }
    }

    /**
     * When roaming, if mobile data cannot be established due to data roaming not enabled, we need
     * to notify the user so they can enable it through settings. Vise versa if the condition
     * changes, we need to dismiss the notification.
     * @param reason to inform which event is called for notification update.
     */
    private void updateDataRoamingStatus(@RoamingNotificationReason int reason) {
        if (VDBG) Log.v(LOG_TAG, "updateDataRoamingStatus");
        Phone phone = getPhone(mDefaultDataSubId);
        if (phone == null) {
            Log.w(LOG_TAG, "Can't get phone with sub id = " + mDefaultDataSubId);
            return;
        }

        ServiceState serviceState = phone.getServiceState();
        if (serviceState == null) {
            Log.e(LOG_TAG, "updateDataRoamingStatus: serviceState is null");
            return;
        }

        String roamingNumeric = serviceState.getOperatorNumeric();
        String roamingNumericReason = "RoamingNumeric=" + roamingNumeric;
        String callingReason = "CallingReason=" + reason;
        boolean dataIsNowRoaming = serviceState.getDataRoaming();
        boolean dataAllowed;
        boolean notAllowedDueToRoamingOff;
        List<DataDisallowedReason> reasons = phone.getDataNetworkController()
                .getInternetDataDisallowedReasons();
        dataAllowed = reasons.isEmpty();
        notAllowedDueToRoamingOff = (reasons.size() == 1
                && reasons.contains(DataDisallowedReason.ROAMING_DISABLED));
        StringBuilder sb = new StringBuilder("updateDataRoamingStatus");
        sb.append(" dataAllowed=").append(dataAllowed);
        sb.append(", reasons=").append(reasons);
        sb.append(", dataIsNowRoaming=").append(dataIsNowRoaming);
        sb.append(", ").append(roamingNumericReason);
        sb.append(", ").append(callingReason);
        mDataRoamingNotifLog.log(sb.toString());
        if (VDBG) {
            Log.v(LOG_TAG, sb.toString());
        }

        // Determine if a given roaming numeric has never been shown.
        boolean shownInThisNumeric = false;
        if (reason == ROAMING_NOTIFICATION_REASON_CARRIER_CONFIG_CHANGED
                || reason == ROAMING_NOTIFICATION_REASON_SERVICE_STATE_CHANGED) {
            shownInThisNumeric = mShownNotificationReasons.contains(roamingNumericReason);
        }
        // Determine if a notification has never been shown by given calling reason.
        boolean shownForThisReason = mShownNotificationReasons.contains(callingReason);

        if (!dataAllowed && notAllowedDueToRoamingOff) {
            if (!shownInThisNumeric && roamingNumeric != null) {
                mShownNotificationReasons.add(roamingNumericReason);
            }
            if (!shownForThisReason
                    && reason == ROAMING_NOTIFICATION_REASON_CARRIER_CONFIG_CHANGED) {
                mShownNotificationReasons.add(callingReason);
            }
            // No need to show it again if we never cancelled it explicitly.
            if (getCurrentRoamingNotification() == ROAMING_NOTIFICATION_DISCONNECTED) {
                return;
            }

            // If the only reason of no data is data roaming disabled, then we notify the user
            // so the user can turn on data roaming.
            if (!shownInThisNumeric && !shownForThisReason) {
                updateDataRoamingNotification(ROAMING_NOTIFICATION_DISCONNECTED);
            } else {
                // Don't show roaming notification if we've already shown for this MccMnc
                Log.d(LOG_TAG, "Skip roaming disconnected notification since already"
                        + " shownInThisNumeric=" + shownInThisNumeric
                        + " shownForThisReason=" + shownForThisReason);
                // Dismiss notification if the other notification is shown.
                if (getCurrentRoamingNotification() != ROAMING_NOTIFICATION_NO_NOTIFICATION) {
                    updateDataRoamingNotification(ROAMING_NOTIFICATION_NO_NOTIFICATION);
                }
            }
        } else if (dataAllowed && dataIsNowRoaming) {
            if (!shownInThisNumeric && roamingNumeric != null) {
                mShownNotificationReasons.add(roamingNumericReason);
            }
            if (!shownForThisReason
                    && reason == ROAMING_NOTIFICATION_REASON_CARRIER_CONFIG_CHANGED) {
                mShownNotificationReasons.add(callingReason);
            }
            boolean shouldShowRoamingNotification = shouldShowRoamingNotification(roamingNumeric);
            // No need to show it again if we never cancelled it explicitly.
            if (getCurrentRoamingNotification() == ROAMING_NOTIFICATION_CONNECTED) {
                return;
            }

            // Inform users that roaming charges may apply.
            if (!shownInThisNumeric && !shownForThisReason && shouldShowRoamingNotification) {
                updateDataRoamingNotification(ROAMING_NOTIFICATION_CONNECTED);
            } else {
                // Don't show roaming notification if we've already shown for this MccMnc or
                // disabled from carrier config.
                Log.d(LOG_TAG, "Skip roaming connected notification since already"
                        + " shownInThisNumeric:" + shownInThisNumeric
                        + " shownForThisReason:" + shownForThisReason
                        + " shouldShowRoamingNotification:" + shouldShowRoamingNotification);
                // Dismiss notification if the other notification is shown.
                if (getCurrentRoamingNotification() != ROAMING_NOTIFICATION_NO_NOTIFICATION) {
                    updateDataRoamingNotification(ROAMING_NOTIFICATION_NO_NOTIFICATION);
                }
            }
        } else if (getCurrentRoamingNotification() != ROAMING_NOTIFICATION_NO_NOTIFICATION) {
            // Otherwise we either 1) we are not roaming or 2) roaming is off but ROAMING_DISABLED
            // is not the only data disable reason. In this case we dismiss the notification we
            // showed earlier.
            updateDataRoamingNotification(ROAMING_NOTIFICATION_NO_NOTIFICATION);
        }
    }

    private void updateDataRoamingNotification(@RoamingNotification int roamingNotification) {
        int event;
        switch (roamingNotification) {
            case ROAMING_NOTIFICATION_NO_NOTIFICATION:
                Log.d(LOG_TAG, "Dismiss roaming notification");
                mDataRoamingNotifLog.log("Hide roaming.");
                event = EVENT_DATA_ROAMING_OK;
                break;
            case ROAMING_NOTIFICATION_CONNECTED:
                Log.d(LOG_TAG, "Show roaming connected notification");
                mDataRoamingNotifLog.log("Show roaming on.");
                event = EVENT_DATA_ROAMING_CONNECTED;
                break;
            case ROAMING_NOTIFICATION_DISCONNECTED:
                Log.d(LOG_TAG, "Show roaming disconnected notification");
                mDataRoamingNotifLog.log("Show roaming off.");
                event = EVENT_DATA_ROAMING_DISCONNECTED;
                break;
            default:
                Log.d(LOG_TAG, "Should never reach here.");
                mDataRoamingNotifLog.log("Should never reach here.");
                return;
        }
        mCurrentRoamingNotification = roamingNotification;
        mHandler.obtainMessage(event, mDefaultDataSubId, 0).sendToTarget();
    }

    private @RoamingNotification int getCurrentRoamingNotification() {
        return mCurrentRoamingNotification;
    }

    // For reorganize_roaming_notification feature disabled.
    /**
     * When roaming, if mobile data cannot be established due to data roaming not enabled, we need
     * to notify the user so they can enable it through settings. Vise versa if the condition
     * changes, we need to dismiss the notification.
     * @param roamingOperatorNumeric The operator numeric for the current roaming. {@code null} if
     *                               the current roaming operator numeric didn't change.
     */
    private void updateDataRoamingStatusForFeatureDisabled(
            @Nullable String roamingOperatorNumeric) {
        if (VDBG) Log.v(LOG_TAG, "updateDataRoamingStatusForFeatureDisabled");
        Phone phone = getPhone(mDefaultDataSubId);
        if (phone == null) {
            Log.w(LOG_TAG, "Can't get phone with sub id = " + mDefaultDataSubId);
            return;
        }

        boolean dataAllowed;
        boolean notAllowedDueToRoamingOff;
        List<DataDisallowedReason> reasons = phone.getDataNetworkController()
                .getInternetDataDisallowedReasons();
        dataAllowed = reasons.isEmpty();
        notAllowedDueToRoamingOff = (reasons.size() == 1
                && reasons.contains(DataDisallowedReason.ROAMING_DISABLED));
        mDataRoamingNotifLog.log("dataAllowed=" + dataAllowed + ", reasons=" + reasons
                + ", roamingOperatorNumeric=" + roamingOperatorNumeric);
        if (VDBG) {
            Log.v(LOG_TAG, "dataAllowed=" + dataAllowed + ", reasons=" + reasons
                    + ", roamingOperatorNumeric=" + roamingOperatorNumeric);
        }

        if (!dataAllowed && notAllowedDueToRoamingOff) {
            // Don't show roaming notification if we've already shown for this MccMnc
            if (roamingOperatorNumeric != null
                    && !mPrevRoamingOperatorNumerics.add(roamingOperatorNumeric)) {
                Log.d(LOG_TAG, "Skip roaming disconnected notification since already shown in "
                        + "MccMnc " + roamingOperatorNumeric);
                return;
            }
            // No need to show it again if we never cancelled it explicitly.
            if (mPrevRoamingNotification == ROAMING_NOTIFICATION_DISCONNECTED) return;
            // If the only reason of no data is data roaming disabled, then we notify the user
            // so the user can turn on data roaming.
            mPrevRoamingNotification = ROAMING_NOTIFICATION_DISCONNECTED;
            Log.d(LOG_TAG, "Show roaming disconnected notification");
            mDataRoamingNotifLog.log("Show roaming off.");
            Message msg = mHandler.obtainMessage(EVENT_DATA_ROAMING_DISCONNECTED);
            msg.arg1 = mDefaultDataSubId;
            msg.sendToTarget();
        } else if (dataAllowed && dataIsNowRoaming(mDefaultDataSubId)) {
            if (!shouldShowRoamingNotification(roamingOperatorNumeric != null
                        ? roamingOperatorNumeric : phone.getServiceState().getOperatorNumeric())) {
                Log.d(LOG_TAG, "Skip showing roaming connected notification.");
                return;
            }
            // Don't show roaming notification if we've already shown for this MccMnc
            if (roamingOperatorNumeric != null
                    && !mPrevRoamingOperatorNumerics.add(roamingOperatorNumeric)) {
                Log.d(LOG_TAG, "Skip roaming connected notification since already shown in "
                        + "MccMnc " + roamingOperatorNumeric);
                return;
            }
            // No need to show it again if we never cancelled it explicitly, or carrier config
            // indicates this is not needed.
            if (mPrevRoamingNotification == ROAMING_NOTIFICATION_CONNECTED) return;
            mPrevRoamingNotification = ROAMING_NOTIFICATION_CONNECTED;
            Log.d(LOG_TAG, "Show roaming connected notification");
            mDataRoamingNotifLog.log("Show roaming on.");
            Message msg = mHandler.obtainMessage(EVENT_DATA_ROAMING_CONNECTED);
            msg.arg1 = mDefaultDataSubId;
            msg.sendToTarget();
        } else if (mPrevRoamingNotification != ROAMING_NOTIFICATION_NO_NOTIFICATION) {
            // Otherwise we either 1) we are not roaming or 2) roaming is off but ROAMING_DISABLED
            // is not the only data disable reason. In this case we dismiss the notification we
            // showed earlier.
            mPrevRoamingNotification = ROAMING_NOTIFICATION_NO_NOTIFICATION;
            Log.d(LOG_TAG, "Dismiss roaming notification");
            mDataRoamingNotifLog.log("Hide. data allowed=" + dataAllowed);
            mHandler.sendEmptyMessage(EVENT_DATA_ROAMING_OK);
        }
    }

    /**
     *
     * @param subId to check roaming on
     * @return whether we have transitioned to dataRoaming
     */
    private boolean dataIsNowRoaming(int subId) {
        return getPhone(subId).getServiceState().getDataRoaming();
    }

    private boolean shouldShowRoamingNotification(String roamingNumeric) {
        PersistableBundle config = getCarrierConfigForSubId(mDefaultDataSubId);
        boolean showRoamingNotification = config.getBoolean(
                CarrierConfigManager.KEY_SHOW_DATA_CONNECTED_ROAMING_NOTIFICATION_BOOL);

        if (TextUtils.isEmpty(roamingNumeric) || !mFeatureFlags.hideRoamingIcon()) {
            Log.d(LOG_TAG, "shouldShowRoamingNotification: roamingNumeric=" + roamingNumeric
                    + ", hideRoaming=" + mFeatureFlags.hideRoamingIcon());
            return showRoamingNotification;
        }

        String[] includedMccMncs = config.getStringArray(CarrierConfigManager
                .KEY_DATA_CONNECTED_ROAMING_NOTIFICATION_INCLUDED_MCC_MNCS_STRING_ARRAY);
        if (includedMccMncs != null) {
            for (String mccMnc : includedMccMncs) {
                if (roamingNumeric.equals(mccMnc)) {
                    Log.d(LOG_TAG, "shouldShowRoamingNotification: show for MCC/MNC " + mccMnc);
                    return showRoamingNotification;
                }
            }
        }

        String[] excludedMccs = config.getStringArray(CarrierConfigManager
                .KEY_DATA_CONNECTED_ROAMING_NOTIFICATION_EXCLUDED_MCCS_STRING_ARRAY);
        String roamingMcc = roamingNumeric.length() < 3 ? "" : roamingNumeric.substring(0, 3);
        if (excludedMccs != null && !TextUtils.isEmpty(roamingMcc)) {
            for (String mcc : excludedMccs) {
                if (roamingMcc.equals(mcc)) {
                    Log.d(LOG_TAG, "shouldShowRoamingNotification: ignore for MCC " + mcc);
                    return false;
                }
            }
        }

        if (showRoamingNotification) {
            Log.d(LOG_TAG, "shouldShowRoamingNotification: show for numeric " + roamingNumeric);
        }
        return showRoamingNotification;
    }

    private void updateLimitedSimFunctionForDualSim() {
        if (DBG) Log.d(LOG_TAG, "updateLimitedSimFunctionForDualSim");
        // check conditions to display limited SIM function notification under dual SIM
        SubscriptionManager subMgr = (SubscriptionManager) getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> subList = subMgr.getActiveSubscriptionInfoList(false);
        if (subList != null && subList.size() > 1) {
            CarrierConfigManager configMgr = (CarrierConfigManager)
                    getSystemService(Context.CARRIER_CONFIG_SERVICE);
            for (SubscriptionInfo info : subList) {
                PersistableBundle b = configMgr.getConfigForSubId(info.getSubscriptionId());
                if (b != null) {
                    if (b.getBoolean(CarrierConfigManager
                            .KEY_LIMITED_SIM_FUNCTION_NOTIFICATION_FOR_DSDS_BOOL)) {
                        notificationMgr.showLimitedSimFunctionWarningNotification(
                                info.getSubscriptionId(),
                                info.getDisplayName().toString());
                    } else {
                        notificationMgr.dismissLimitedSimFunctionWarningNotification(
                                info.getSubscriptionId());
                    }
                }
            }
        } else {
            // cancel notifications for all subs
            notificationMgr.dismissLimitedSimFunctionWarningNotification(
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }
        notificationMgr.dismissLimitedSimFunctionWarningNotificationForInactiveSubs();

    }

    public Phone getPhoneInEcm() {
        return phoneInEcm;
    }

    /**
     * Triggers a refresh of the message waiting (voicemail) indicator.
     *
     * @param subId the subscription id we should refresh the notification for.
     */
    public void refreshMwiIndicator(int subId) {
        notificationMgr.refreshMwi(subId);
    }

    /**
     * Called when the network selection on the subscription {@code subId} is changed by the user.
     *
     * @param subId the subscription id.
     */
    public void onNetworkSelectionChanged(int subId) {
        Phone phone = getPhone(subId);
        if (phone != null) {
            notificationMgr.updateNetworkSelection(phone.getServiceState().getState(), subId);
        } else {
            Log.w(LOG_TAG, "onNetworkSelectionChanged on null phone, subId: " + subId);
        }
    }

    /**
     * @return whether the device supports RCS User Capability Exchange or not.
     */
    public boolean getDeviceUceEnabled() {
        return (mTelephonyRcsService == null) ? false : mTelephonyRcsService.isDeviceUceEnabled();
    }

    /**
     * Set the device supports RCS User Capability Exchange.
     * @param isEnabled true if the device supports UCE.
     */
    public void setDeviceUceEnabled(boolean isEnabled) {
        if (mTelephonyRcsService != null) {
            mTelephonyRcsService.setDeviceUceEnabled(isEnabled);
        }
    }

    /**
     * Dump the state of the object, add calls to other objects as desired.
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("------- PhoneGlobals -------");
        pw.increaseIndent();
        pw.println("FeatureFlags:");
        pw.increaseIndent();
        pw.println("reorganizeRoamingNotification="
                + mFeatureFlags.reorganizeRoamingNotification());
        pw.println("dismissNetworkSelectionNotificationOnSimDisable="
                + mFeatureFlags.dismissNetworkSelectionNotificationOnSimDisable());
        pw.decreaseIndent();
        if (mFeatureFlags.reorganizeRoamingNotification()) {
            pw.println("mCurrentRoamingNotification=" + mCurrentRoamingNotification);
        } else {
            pw.println("mPrevRoamingNotification=" + mPrevRoamingNotification);
        }
        pw.println("mDefaultDataSubId=" + mDefaultDataSubId);
        pw.println("isSmsCapable=" + TelephonyManager.from(this).isSmsCapable());
        pw.println("mDataRoamingNotifLog:");
        pw.increaseIndent();
        mDataRoamingNotifLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.println("ImsResolver:");
        pw.increaseIndent();
        try {
            if (ImsResolver.getInstance() != null) ImsResolver.getInstance().dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.decreaseIndent();
        pw.println("RcsService:");
        try {
            if (mTelephonyRcsService != null) mTelephonyRcsService.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.println("ImsStateCallbackController:");
        try {
            if (mImsStateCallbackController != null) mImsStateCallbackController.dump(pw);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.println("DomainSelectionResolver:");
        pw.increaseIndent();
        try {
            if (DomainSelectionResolver.getInstance() != null) {
                DomainSelectionResolver.getInstance().dump(fd, pw, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
        if (mFeatureFlags.reorganizeRoamingNotification()) {
            pw.println("mShownNotificationReasons=" + mShownNotificationReasons);
        } else {
            pw.println("mPrevRoamingOperatorNumerics:" + mPrevRoamingOperatorNumerics);
        }
        pw.println("------- End PhoneGlobals -------");
    }
}
