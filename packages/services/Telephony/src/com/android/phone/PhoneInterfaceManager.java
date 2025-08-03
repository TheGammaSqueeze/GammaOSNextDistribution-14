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

import static android.content.pm.PackageManager.FEATURE_TELEPHONY_IMS;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.permission.flags.Flags.opEnableMobileDataByUser;
import static android.telephony.TelephonyManager.ENABLE_FEATURE_MAPPING;
import static android.telephony.TelephonyManager.HAL_SERVICE_NETWORK;
import static android.telephony.TelephonyManager.HAL_SERVICE_RADIO;
import static android.telephony.satellite.SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ACCESS_BARRED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.PhoneConstants.PHONE_TYPE_CDMA;
import static com.android.internal.telephony.PhoneConstants.PHONE_TYPE_GSM;
import static com.android.internal.telephony.PhoneConstants.PHONE_TYPE_IMS;
import static com.android.internal.telephony.PhoneConstants.SUBSCRIPTION_KEY;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__CLIENT_PARAMS_SENT;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PropertyInvalidatedCache;
import android.app.compat.CompatChanges;
import android.app.role.RoleManager;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.LocaleList;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.preference.PreferenceManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.provider.Telephony;
import android.service.carrier.CarrierIdentifier;
import android.sysprop.TelephonyProperties;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.ActivityStatsTechSpecificInfo;
import android.telephony.Annotation.ApnType;
import android.telephony.Annotation.DataActivityType;
import android.telephony.Annotation.ThermalMitigationResult;
import android.telephony.AnomalyReporter;
import android.telephony.CallForwardingInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierRestrictionRules;
import android.telephony.CellBroadcastIdRange;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoWcdma;
import android.telephony.ClientRequestStats;
import android.telephony.DataThrottlingRequest;
import android.telephony.IBootstrapAuthenticationCallback;
import android.telephony.ICellInfoCallback;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.LocationAccessPolicy;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.PhoneCapability;
import android.telephony.PhoneNumberRange;
import android.telephony.RadioAccessFamily;
import android.telephony.RadioAccessSpecifier;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyHistogram;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.SimState;
import android.telephony.TelephonyScanManager;
import android.telephony.ThermalMitigationRequest;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.UiccSlotInfo;
import android.telephony.UiccSlotMapping;
import android.telephony.UssdResponse;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.gba.GbaAuthRequest;
import android.telephony.gba.UaSecurityProtocolIdentifier;
import android.telephony.ims.ImsException;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RcsClientConfiguration;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.aidl.IFeatureProvisioningCallback;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsConfigCallback;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.IImsRegistrationCallback;
import android.telephony.ims.aidl.IRcsConfigCallback;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.satellite.INtnSignalStrengthCallback;
import android.telephony.satellite.ISatelliteCapabilitiesCallback;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.ISatelliteModemStateCallback;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.ISatelliteTransmissionUpdateCallback;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.NtnSignalStrengthCallback;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteProvisionStateCallback;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.EventLog;
import android.util.Log;
import android.util.Pair;

import com.android.ims.ImsManager;
import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.ims.rcs.uce.eab.EabUtil;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CarrierPrivilegesTracker;
import com.android.internal.telephony.CarrierResolver;
import com.android.internal.telephony.CellNetworkScanResult;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GbaManager;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.HalVersion;
import com.android.internal.telephony.IBooleanConsumer;
import com.android.internal.telephony.ICallForwardingInfoCallback;
import com.android.internal.telephony.IImsStateCallback;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.INumberVerificationCallback;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccLogicalChannelRequest;
import com.android.internal.telephony.LocaleTracker;
import com.android.internal.telephony.NetworkScanRequestTracker;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConfigurationManager;
import com.android.internal.telephony.PhoneConstantConversions;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RadioInterfaceCapabilityController;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsController;
import com.android.internal.telephony.SmsPermissions;
import com.android.internal.telephony.TelephonyCountryDetector;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyPermissions;
import com.android.internal.telephony.data.DataUtils;
import com.android.internal.telephony.domainselection.DomainSelectionResolver;
import com.android.internal.telephony.emergency.EmergencyNumberTracker;
import com.android.internal.telephony.euicc.EuiccConnector;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.ims.ImsResolver;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccPort;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.internal.telephony.uicc.UiccSlot;
import com.android.internal.telephony.util.LocaleUtils;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.internal.telephony.util.VoicemailNotificationSettingsUtil;
import com.android.internal.util.FunctionalUtils;
import com.android.internal.util.HexDump;
import com.android.phone.callcomposer.CallComposerPictureManager;
import com.android.phone.callcomposer.CallComposerPictureTransfer;
import com.android.phone.callcomposer.ImageData;
import com.android.phone.satellite.accesscontrol.SatelliteAccessController;
import com.android.phone.satellite.entitlement.SatelliteEntitlementController;
import com.android.phone.settings.PickSmsSubscriptionActivity;
import com.android.phone.slice.SlicePurchaseController;
import com.android.phone.utils.CarrierAllowListInfo;
import com.android.phone.vvm.PhoneAccountHandleConverter;
import com.android.phone.vvm.RemoteVvmTaskManager;
import com.android.phone.vvm.VisualVoicemailSettingsUtil;
import com.android.phone.vvm.VisualVoicemailSmsFilterConfig;
import com.android.services.telephony.TelecomAccountRegistry;
import com.android.services.telephony.TelephonyConnectionService;
import com.android.telephony.Rlog;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Implementation of the ITelephony interface.
 */
public class PhoneInterfaceManager extends ITelephony.Stub {
    private static final String LOG_TAG = "PhoneInterfaceManager";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    private static final boolean DBG_LOC = false;
    private static final boolean DBG_MERGE = false;

    // Message codes used with mMainThreadHandler
    private static final int CMD_HANDLE_PIN_MMI = 1;
    private static final int CMD_TRANSMIT_APDU_LOGICAL_CHANNEL = 7;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 8;
    private static final int CMD_OPEN_CHANNEL = 9;
    private static final int EVENT_OPEN_CHANNEL_DONE = 10;
    private static final int CMD_CLOSE_CHANNEL = 11;
    private static final int EVENT_CLOSE_CHANNEL_DONE = 12;
    private static final int CMD_NV_READ_ITEM = 13;
    private static final int EVENT_NV_READ_ITEM_DONE = 14;
    private static final int CMD_NV_WRITE_ITEM = 15;
    private static final int EVENT_NV_WRITE_ITEM_DONE = 16;
    private static final int CMD_NV_WRITE_CDMA_PRL = 17;
    private static final int EVENT_NV_WRITE_CDMA_PRL_DONE = 18;
    private static final int CMD_RESET_MODEM_CONFIG = 19;
    private static final int EVENT_RESET_MODEM_CONFIG_DONE = 20;
    private static final int CMD_GET_ALLOWED_NETWORK_TYPES_BITMASK = 21;
    private static final int EVENT_GET_ALLOWED_NETWORK_TYPES_BITMASK_DONE = 22;
    private static final int CMD_SEND_ENVELOPE = 25;
    private static final int EVENT_SEND_ENVELOPE_DONE = 26;
    private static final int CMD_INVOKE_OEM_RIL_REQUEST_RAW = 27;
    private static final int EVENT_INVOKE_OEM_RIL_REQUEST_RAW_DONE = 28;
    private static final int CMD_TRANSMIT_APDU_BASIC_CHANNEL = 29;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 30;
    private static final int CMD_EXCHANGE_SIM_IO = 31;
    private static final int EVENT_EXCHANGE_SIM_IO_DONE = 32;
    private static final int CMD_SET_VOICEMAIL_NUMBER = 33;
    private static final int EVENT_SET_VOICEMAIL_NUMBER_DONE = 34;
    private static final int CMD_SET_NETWORK_SELECTION_MODE_AUTOMATIC = 35;
    private static final int EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_DONE = 36;
    private static final int CMD_GET_MODEM_ACTIVITY_INFO = 37;
    private static final int EVENT_GET_MODEM_ACTIVITY_INFO_DONE = 38;
    private static final int CMD_PERFORM_NETWORK_SCAN = 39;
    private static final int EVENT_PERFORM_NETWORK_SCAN_DONE = 40;
    private static final int CMD_SET_NETWORK_SELECTION_MODE_MANUAL = 41;
    private static final int EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_DONE = 42;
    private static final int CMD_SET_ALLOWED_CARRIERS = 43;
    private static final int EVENT_SET_ALLOWED_CARRIERS_DONE = 44;
    private static final int CMD_GET_ALLOWED_CARRIERS = 45;
    private static final int EVENT_GET_ALLOWED_CARRIERS_DONE = 46;
    private static final int CMD_HANDLE_USSD_REQUEST = 47;
    private static final int CMD_GET_FORBIDDEN_PLMNS = 48;
    private static final int EVENT_GET_FORBIDDEN_PLMNS_DONE = 49;
    private static final int CMD_SWITCH_SLOTS = 50;
    private static final int EVENT_SWITCH_SLOTS_DONE = 51;
    private static final int CMD_GET_NETWORK_SELECTION_MODE = 52;
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 53;
    private static final int CMD_GET_CDMA_ROAMING_MODE = 54;
    private static final int EVENT_GET_CDMA_ROAMING_MODE_DONE = 55;
    private static final int CMD_SET_CDMA_ROAMING_MODE = 56;
    private static final int EVENT_SET_CDMA_ROAMING_MODE_DONE = 57;
    private static final int CMD_SET_CDMA_SUBSCRIPTION_MODE = 58;
    private static final int EVENT_SET_CDMA_SUBSCRIPTION_MODE_DONE = 59;
    private static final int CMD_GET_ALL_CELL_INFO = 60;
    private static final int EVENT_GET_ALL_CELL_INFO_DONE = 61;
    private static final int CMD_GET_CELL_LOCATION = 62;
    private static final int EVENT_GET_CELL_LOCATION_DONE = 63;
    private static final int CMD_MODEM_REBOOT = 64;
    private static final int EVENT_CMD_MODEM_REBOOT_DONE = 65;
    private static final int CMD_REQUEST_CELL_INFO_UPDATE = 66;
    private static final int EVENT_REQUEST_CELL_INFO_UPDATE_DONE = 67;
    private static final int CMD_REQUEST_ENABLE_MODEM = 68;
    private static final int EVENT_ENABLE_MODEM_DONE = 69;
    private static final int CMD_GET_MODEM_STATUS = 70;
    private static final int EVENT_GET_MODEM_STATUS_DONE = 71;
    private static final int CMD_SET_FORBIDDEN_PLMNS = 72;
    private static final int EVENT_SET_FORBIDDEN_PLMNS_DONE = 73;
    private static final int CMD_ERASE_MODEM_CONFIG = 74;
    private static final int EVENT_ERASE_MODEM_CONFIG_DONE = 75;
    private static final int CMD_CHANGE_ICC_LOCK_PASSWORD = 76;
    private static final int EVENT_CHANGE_ICC_LOCK_PASSWORD_DONE = 77;
    private static final int CMD_SET_ICC_LOCK_ENABLED = 78;
    private static final int EVENT_SET_ICC_LOCK_ENABLED_DONE = 79;
    private static final int CMD_SET_SYSTEM_SELECTION_CHANNELS = 80;
    private static final int EVENT_SET_SYSTEM_SELECTION_CHANNELS_DONE = 81;
    private static final int MSG_NOTIFY_USER_ACTIVITY = 82;
    private static final int CMD_GET_CALL_FORWARDING = 83;
    private static final int EVENT_GET_CALL_FORWARDING_DONE = 84;
    private static final int CMD_SET_CALL_FORWARDING = 85;
    private static final int EVENT_SET_CALL_FORWARDING_DONE = 86;
    private static final int CMD_GET_CALL_WAITING = 87;
    private static final int EVENT_GET_CALL_WAITING_DONE = 88;
    private static final int CMD_SET_CALL_WAITING = 89;
    private static final int EVENT_SET_CALL_WAITING_DONE = 90;
    private static final int CMD_ENABLE_NR_DUAL_CONNECTIVITY = 91;
    private static final int EVENT_ENABLE_NR_DUAL_CONNECTIVITY_DONE = 92;
    private static final int CMD_IS_NR_DUAL_CONNECTIVITY_ENABLED = 93;
    private static final int EVENT_IS_NR_DUAL_CONNECTIVITY_ENABLED_DONE = 94;
    private static final int CMD_GET_CDMA_SUBSCRIPTION_MODE = 95;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_MODE_DONE = 96;
    private static final int CMD_GET_SYSTEM_SELECTION_CHANNELS = 97;
    private static final int EVENT_GET_SYSTEM_SELECTION_CHANNELS_DONE = 98;
    private static final int CMD_SET_DATA_THROTTLING = 99;
    private static final int EVENT_SET_DATA_THROTTLING_DONE = 100;
    private static final int CMD_SET_SIM_POWER = 101;
    private static final int EVENT_SET_SIM_POWER_DONE = 102;
    private static final int CMD_SET_SIGNAL_STRENGTH_UPDATE_REQUEST = 103;
    private static final int EVENT_SET_SIGNAL_STRENGTH_UPDATE_REQUEST_DONE = 104;
    private static final int CMD_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST = 105;
    private static final int EVENT_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST_DONE = 106;
    private static final int CMD_SET_ALLOWED_NETWORK_TYPES_FOR_REASON = 107;
    private static final int EVENT_SET_ALLOWED_NETWORK_TYPES_FOR_REASON_DONE = 108;
    private static final int CMD_PREPARE_UNATTENDED_REBOOT = 109;
    private static final int CMD_GET_SLICING_CONFIG = 110;
    private static final int EVENT_GET_SLICING_CONFIG_DONE = 111;
    private static final int CMD_ERASE_DATA_SHARED_PREFERENCES = 112;
    private static final int CMD_ENABLE_VONR = 113;
    private static final int EVENT_ENABLE_VONR_DONE = 114;
    private static final int CMD_IS_VONR_ENABLED = 115;
    private static final int EVENT_IS_VONR_ENABLED_DONE = 116;
    private static final int CMD_PURCHASE_PREMIUM_CAPABILITY = 117;
    private static final int EVENT_PURCHASE_PREMIUM_CAPABILITY_DONE = 118;

    // Parameters of select command.
    private static final int SELECT_COMMAND = 0xA4;
    private static final int SELECT_P1 = 0x04;
    private static final int SELECT_P2 = 0;
    private static final int SELECT_P3 = 0x10;

    // Toggling null cipher and integrity support was added in IRadioNetwork 2.1
    private static final int MIN_NULL_CIPHER_AND_INTEGRITY_VERSION = 201;
    // Cellular identifier disclosure transparency was added in IRadioNetwork 2.2
    private static final int MIN_IDENTIFIER_DISCLOSURE_VERSION = 202;
    // Null cipher notification support was added in IRadioNetwork 2.2
    private static final int MIN_NULL_CIPHER_NOTIFICATION_VERSION = 202;

    /** The singleton instance. */
    private static PhoneInterfaceManager sInstance;
    private static List<String> sThermalMitigationAllowlistedPackages = new ArrayList<>();

    private final PhoneGlobals mApp;
    private FeatureFlags mFeatureFlags;
    private final CallManager mCM;
    private final ImsResolver mImsResolver;

    private final SatelliteController mSatelliteController;
    private final SatelliteAccessController mSatelliteAccessController;
    private final UserManager mUserManager;
    private final AppOpsManager mAppOps;
    private final MainThreadHandler mMainThreadHandler;
    private final SharedPreferences mTelephonySharedPreferences;
    private final PhoneConfigurationManager mPhoneConfigurationManager;
    private final RadioInterfaceCapabilityController mRadioInterfaceCapabilities;
    private PackageManager mPackageManager;

    /** User Activity */
    private final AtomicBoolean mNotifyUserActivity;
    private static final int USER_ACTIVITY_NOTIFICATION_DELAY = 200;
    private final Set<Integer> mCarrierPrivilegeTestOverrideSubIds = new ArraySet<>();

    private static final String PREF_CARRIERS_ALPHATAG_PREFIX = "carrier_alphtag_";
    private static final String PREF_CARRIERS_NUMBER_PREFIX = "carrier_number_";
    private static final String PREF_CARRIERS_SUBSCRIBER_PREFIX = "carrier_subscriber_";
    private static final String PREF_PROVISION_IMS_MMTEL_PREFIX = "provision_ims_mmtel_";

    // String to store multi SIM allowed
    private static final String PREF_MULTI_SIM_RESTRICTED = "multisim_restricted";

    // The AID of ISD-R.
    private static final String ISDR_AID = "A0000005591010FFFFFFFF8900000100";

    private NetworkScanRequestTracker mNetworkScanRequestTracker;

    private static final int TYPE_ALLOCATION_CODE_LENGTH = 8;
    private static final int MANUFACTURER_CODE_LENGTH = 8;

    private static final int SET_DATA_THROTTLING_MODEM_THREW_INVALID_PARAMS = -1;
    private static final int MODEM_DOES_NOT_SUPPORT_DATA_THROTTLING_ERROR_CODE = -2;

    private static final String PURCHASE_PREMIUM_CAPABILITY_ERROR_UUID =
            "24bf97a6-e8a6-44d8-a6a4-255d7548733c";

    /**
     * Experiment flag to enable erase modem config on reset network, default value is false
     */
    public static final String RESET_NETWORK_ERASE_MODEM_CONFIG_ENABLED =
            "reset_network_erase_modem_config_enabled";

    private static final int SET_NETWORK_SELECTION_MODE_AUTOMATIC_TIMEOUT_MS = 2000; // 2 seconds

    private static final int MODEM_ACTIVITY_TIME_OFFSET_CORRECTION_MS = 50;

    /**
     * With support for MEP(multiple enabled profile) in Android T, a SIM card can have more than
     * one ICCID active at the same time.
     * Apps should use below API signatures if targeting SDK is T and beyond.
     *
     * @hide
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public static final long GET_API_SIGNATURES_FROM_UICC_PORT_INFO = 202110963L;

    /**
     * Apps targeting on Android T and beyond will get exception whenever icc close channel
     * operation fails.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public static final long ICC_CLOSE_CHANNEL_EXCEPTION_ON_FAILURE = 208739934L;

    /**
     * A request object to use for transmitting data to an ICC.
     */
    private static final class IccAPDUArgument {
        public int channel, cla, command, p1, p2, p3;
        public String data;

        public IccAPDUArgument(int channel, int cla, int command,
                int p1, int p2, int p3, String data) {
            this.channel = channel;
            this.cla = cla;
            this.command = command;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.data = data;
        }
    }

    /**
     * A request object to use for transmitting data to an ICC.
     */
    private static final class ManualNetworkSelectionArgument {
        public OperatorInfo operatorInfo;
        public boolean persistSelection;

        public ManualNetworkSelectionArgument(OperatorInfo operatorInfo, boolean persistSelection) {
            this.operatorInfo = operatorInfo;
            this.persistSelection = persistSelection;
        }
    }

    private static final class PurchasePremiumCapabilityArgument {
        public @TelephonyManager.PremiumCapability int capability;
        public @NonNull IIntegerConsumer callback;

        PurchasePremiumCapabilityArgument(@TelephonyManager.PremiumCapability int capability,
                @NonNull IIntegerConsumer callback) {
            this.capability = capability;
            this.callback = callback;
        }
    }

    /**
     * A request object for use with {@link MainThreadHandler}. Requesters should wait() on the
     * request after sending. The main thread will notify the request when it is complete.
     */
    private static final class MainThreadRequest {
        /** The argument to use for the request */
        public Object argument;
        /** The result of the request that is run on the main thread */
        public Object result;
        // The subscriber id that this request applies to. Defaults to
        // SubscriptionManager.INVALID_SUBSCRIPTION_ID
        public Integer subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        // In cases where subId is unavailable, the caller needs to specify the phone.
        public Phone phone;

        public WorkSource workSource;

        public MainThreadRequest(Object argument) {
            this.argument = argument;
        }

        MainThreadRequest(Object argument, Phone phone, WorkSource workSource) {
            this.argument = argument;
            if (phone != null) {
                this.phone = phone;
            }
            this.workSource = workSource;
        }

        MainThreadRequest(Object argument, Integer subId, WorkSource workSource) {
            this.argument = argument;
            if (subId != null) {
                this.subId = subId;
            }
            this.workSource = workSource;
        }
    }

    private static final class IncomingThirdPartyCallArgs {
        public final ComponentName component;
        public final String callId;
        public final String callerDisplayName;

        public IncomingThirdPartyCallArgs(ComponentName component, String callId,
                String callerDisplayName) {
            this.component = component;
            this.callId = callId;
            this.callerDisplayName = callerDisplayName;
        }
    }

    /**
     * A handler that processes messages on the main thread in the phone process. Since many
     * of the Phone calls are not thread safe this is needed to shuttle the requests from the
     * inbound binder threads to the main thread in the phone process.  The Binder thread
     * may provide a {@link MainThreadRequest} object in the msg.obj field that they are waiting
     * on, which will be notified when the operation completes and will contain the result of the
     * request.
     *
     * <p>If a MainThreadRequest object is provided in the msg.obj field,
     * note that request.result must be set to something non-null for the calling thread to
     * unblock.
     */
    private final class MainThreadHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            MainThreadRequest request;
            Message onCompleted;
            AsyncResult ar;
            UiccPort uiccPort;
            IccAPDUArgument iccArgument;
            final Phone defaultPhone = getDefaultPhone();

            switch (msg.what) {
                case CMD_HANDLE_USSD_REQUEST: {
                    request = (MainThreadRequest) msg.obj;
                    final Phone phone = getPhoneFromRequest(request);
                    Pair<String, ResultReceiver> ussdObject = (Pair) request.argument;
                    String ussdRequest = ussdObject.first;
                    ResultReceiver wrappedCallback = ussdObject.second;

                    if (!isUssdApiAllowed(request.subId)) {
                        // Carrier does not support use of this API, return failure.
                        Rlog.w(LOG_TAG, "handleUssdRequest: carrier does not support USSD apis.");
                        UssdResponse response = new UssdResponse(ussdRequest, null);
                        Bundle returnData = new Bundle();
                        returnData.putParcelable(TelephonyManager.USSD_RESPONSE, response);
                        wrappedCallback.send(TelephonyManager.USSD_RETURN_FAILURE, returnData);

                        request.result = true;
                        notifyRequester(request);
                        return;
                    }

                    try {
                        request.result = phone != null
                                ? phone.handleUssdRequest(ussdRequest, wrappedCallback) : false;
                    } catch (CallStateException cse) {
                        request.result = false;
                    }
                    // Wake up the requesting thread
                    notifyRequester(request);
                    break;
                }

                case CMD_HANDLE_PIN_MMI: {
                    request = (MainThreadRequest) msg.obj;
                    final Phone phone = getPhoneFromRequest(request);
                    request.result = phone != null ?
                            getPhoneFromRequest(request).handlePinMmi((String) request.argument)
                            : false;
                    // Wake up the requesting thread
                    notifyRequester(request);
                    break;
                }

                case CMD_TRANSMIT_APDU_LOGICAL_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    iccArgument = (IccAPDUArgument) request.argument;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("iccTransmitApduLogicalChannel: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE,
                                request);
                        uiccPort.iccTransmitApduLogicalChannel(
                                iccArgument.channel, iccArgument.cla, iccArgument.command,
                                iccArgument.p1, iccArgument.p2, iccArgument.p3, iccArgument.data,
                                onCompleted);
                    }
                    break;

                case EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        if (ar.result == null) {
                            loge("iccTransmitApduLogicalChannel: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("iccTransmitApduLogicalChannel: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("iccTransmitApduLogicalChannel: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_TRANSMIT_APDU_BASIC_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    iccArgument = (IccAPDUArgument) request.argument;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("iccTransmitApduBasicChannel: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE,
                                request);
                        uiccPort.iccTransmitApduBasicChannel(
                                iccArgument.cla, iccArgument.command, iccArgument.p1,
                                iccArgument.p2,
                                iccArgument.p3, iccArgument.data, onCompleted);
                    }
                    break;

                case EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        if (ar.result == null) {
                            loge("iccTransmitApduBasicChannel: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("iccTransmitApduBasicChannel: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("iccTransmitApduBasicChannel: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_EXCHANGE_SIM_IO:
                    request = (MainThreadRequest) msg.obj;
                    iccArgument = (IccAPDUArgument) request.argument;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("iccExchangeSimIO: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_EXCHANGE_SIM_IO_DONE,
                                request);
                        uiccPort.iccExchangeSimIO(iccArgument.cla, /* fileID */
                                iccArgument.command, iccArgument.p1, iccArgument.p2, iccArgument.p3,
                                iccArgument.data, onCompleted);
                    }
                    break;

                case EVENT_EXCHANGE_SIM_IO_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6f, 0, (byte[]) null);
                    }
                    notifyRequester(request);
                    break;

                case CMD_SEND_ENVELOPE:
                    request = (MainThreadRequest) msg.obj;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("sendEnvelopeWithStatus: No UICC");
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_SEND_ENVELOPE_DONE, request);
                        uiccPort.sendEnvelopeWithStatus((String) request.argument, onCompleted);
                    }
                    break;

                case EVENT_SEND_ENVELOPE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IccIoResult(0x6F, 0, (byte[]) null);
                        if (ar.result == null) {
                            loge("sendEnvelopeWithStatus: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("sendEnvelopeWithStatus: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("sendEnvelopeWithStatus: exception:" + ar.exception);
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_OPEN_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    uiccPort = getUiccPortFromRequest(request);
                    IccLogicalChannelRequest openChannelRequest =
                            (IccLogicalChannelRequest) request.argument;
                    if (uiccPort == null) {
                        loge("iccOpenLogicalChannel: No UICC");
                        request.result = new IccOpenLogicalChannelResponse(-1,
                                IccOpenLogicalChannelResponse.STATUS_MISSING_RESOURCE, null);
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_OPEN_CHANNEL_DONE, request);
                        uiccPort.iccOpenLogicalChannel(openChannelRequest.aid,
                                openChannelRequest.p2, onCompleted);
                    }
                    break;

                case EVENT_OPEN_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    IccOpenLogicalChannelResponse openChannelResp;
                    if (ar.exception == null && ar.result != null) {
                        int[] result = (int[]) ar.result;
                        int channelId = result[0];
                        byte[] selectResponse = null;
                        if (result.length > 1) {
                            selectResponse = new byte[result.length - 1];
                            for (int i = 1; i < result.length; ++i) {
                                selectResponse[i - 1] = (byte) result[i];
                            }
                        }
                        openChannelResp = new IccOpenLogicalChannelResponse(channelId,
                                IccOpenLogicalChannelResponse.STATUS_NO_ERROR, selectResponse);

                        uiccPort = getUiccPortFromRequest(request);
                        if (uiccPort == null) {
                            loge("EVENT_OPEN_CHANNEL_DONE: UiccPort is null");
                        } else {
                            IccLogicalChannelRequest channelRequest =
                                    (IccLogicalChannelRequest) request.argument;
                            channelRequest.channel = channelId;
                            uiccPort.onLogicalChannelOpened(channelRequest);
                        }
                    } else {
                        if (ar.result == null) {
                            loge("iccOpenLogicalChannel: Empty response");
                        }
                        if (ar.exception != null) {
                            loge("iccOpenLogicalChannel: Exception: " + ar.exception);
                        }

                        int errorCode = IccOpenLogicalChannelResponse.STATUS_UNKNOWN_ERROR;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.MISSING_RESOURCE) {
                                errorCode = IccOpenLogicalChannelResponse.STATUS_MISSING_RESOURCE;
                            } else if (error == CommandException.Error.NO_SUCH_ELEMENT) {
                                errorCode = IccOpenLogicalChannelResponse.STATUS_NO_SUCH_ELEMENT;
                            }
                        }
                        openChannelResp = new IccOpenLogicalChannelResponse(
                                IccOpenLogicalChannelResponse.INVALID_CHANNEL, errorCode, null);
                    }
                    request.result = openChannelResp;
                    notifyRequester(request);
                    break;

                case CMD_CLOSE_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("iccCloseLogicalChannel: No UICC");
                        request.result = new IllegalArgumentException(
                                "iccCloseLogicalChannel: No UICC");
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_CLOSE_CHANNEL_DONE, request);
                        uiccPort.iccCloseLogicalChannel((Integer) request.argument, onCompleted);
                    }
                    break;

                case EVENT_CLOSE_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result = true;
                        uiccPort = getUiccPortFromRequest(request);
                        if (uiccPort == null) {
                            loge("EVENT_CLOSE_CHANNEL_DONE: UiccPort is null");
                        } else {
                            final int channelId = (Integer) request.argument;
                            uiccPort.onLogicalChannelClosed(channelId);
                        }
                    } else {
                        request.result = false;
                        Exception exception = null;
                        if (ar.exception instanceof CommandException) {
                            loge("iccCloseLogicalChannel: CommandException: " + ar.exception);
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.INVALID_ARGUMENTS) {
                                // should only throw exceptions from the binder threads.
                                exception = new IllegalArgumentException(
                                        "iccCloseLogicalChannel: invalid argument ");
                            }
                        } else {
                            loge("iccCloseLogicalChannel: Unknown exception");
                        }
                        request.result = (exception != null) ? exception :
                                new IllegalStateException(
                                        "exception from modem to close iccLogical Channel");
                    }
                    notifyRequester(request);
                    break;

                case CMD_NV_READ_ITEM:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_READ_ITEM_DONE, request);
                    defaultPhone.nvReadItem((Integer) request.argument, onCompleted,
                            request.workSource);
                    break;

                case EVENT_NV_READ_ITEM_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;     // String
                    } else {
                        request.result = "";
                        if (ar.result == null) {
                            loge("nvReadItem: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("nvReadItem: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("nvReadItem: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_NV_WRITE_ITEM:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_WRITE_ITEM_DONE, request);
                    Pair<Integer, String> idValue = (Pair<Integer, String>) request.argument;
                    defaultPhone.nvWriteItem(idValue.first, idValue.second, onCompleted,
                            request.workSource);
                    break;

                case EVENT_NV_WRITE_ITEM_DONE:
                    handleNullReturnEvent(msg, "nvWriteItem");
                    break;

                case CMD_NV_WRITE_CDMA_PRL:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NV_WRITE_CDMA_PRL_DONE, request);
                    defaultPhone.nvWriteCdmaPrl((byte[]) request.argument, onCompleted);
                    break;

                case EVENT_NV_WRITE_CDMA_PRL_DONE:
                    handleNullReturnEvent(msg, "nvWriteCdmaPrl");
                    break;

                case CMD_RESET_MODEM_CONFIG:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_RESET_MODEM_CONFIG_DONE, request);
                    defaultPhone.resetModemConfig(onCompleted);
                    break;

                case EVENT_RESET_MODEM_CONFIG_DONE:
                    handleNullReturnEvent(msg, "resetModemConfig");
                    break;

                case CMD_IS_NR_DUAL_CONNECTIVITY_ENABLED: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_IS_NR_DUAL_CONNECTIVITY_ENABLED_DONE,
                            request);
                    Phone phone = getPhoneFromRequest(request);
                    if (phone != null) {
                        phone.isNrDualConnectivityEnabled(onCompleted, request.workSource);
                    } else {
                        loge("isNRDualConnectivityEnabled: No phone object");
                        request.result = false;
                        notifyRequester(request);
                    }
                    break;
                }

                case EVENT_IS_NR_DUAL_CONNECTIVITY_ENABLED_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        // request.result must be set to something non-null
                        // for the calling thread to unblock
                        if (ar.result != null) {
                            request.result = ar.result;
                        } else {
                            request.result = false;
                        }
                        if (ar.result == null) {
                            loge("isNRDualConnectivityEnabled: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("isNRDualConnectivityEnabled: CommandException: "
                                    + ar.exception);
                        } else {
                            loge("isNRDualConnectivityEnabled: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_IS_VONR_ENABLED: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_IS_VONR_ENABLED_DONE,
                            request);
                    Phone phone = getPhoneFromRequest(request);
                    if (phone != null) {
                        phone.isVoNrEnabled(onCompleted, request.workSource);
                    } else {
                        loge("isVoNrEnabled: No phone object");
                        request.result = false;
                        notifyRequester(request);
                    }
                    break;
                }

                case EVENT_IS_VONR_ENABLED_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        // request.result must be set to something non-null
                        // for the calling thread to unblock
                        if (ar.result != null) {
                            request.result = ar.result;
                        } else {
                            request.result = false;
                        }
                        if (ar.result == null) {
                            loge("isVoNrEnabled: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("isVoNrEnabled: CommandException: "
                                    + ar.exception);
                        } else {
                            loge("isVoNrEnabled: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_ENABLE_NR_DUAL_CONNECTIVITY: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_ENABLE_NR_DUAL_CONNECTIVITY_DONE, request);
                    Phone phone = getPhoneFromRequest(request);
                    if (phone != null) {
                        phone.setNrDualConnectivityState((int) request.argument, onCompleted,
                                request.workSource);
                    } else {
                        loge("enableNrDualConnectivity: No phone object");
                        request.result =
                                TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_RADIO_NOT_AVAILABLE;
                        notifyRequester(request);
                    }
                    break;
                }

                case EVENT_ENABLE_NR_DUAL_CONNECTIVITY_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result =
                                TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_SUCCESS;
                    } else {
                        request.result =
                                TelephonyManager
                                        .ENABLE_NR_DUAL_CONNECTIVITY_RADIO_ERROR;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.RADIO_NOT_AVAILABLE) {
                                request.result =
                                        TelephonyManager
                                                .ENABLE_NR_DUAL_CONNECTIVITY_RADIO_NOT_AVAILABLE;
                            } else if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                request.result =
                                        TelephonyManager
                                                .ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED;
                            }
                            loge("enableNrDualConnectivity" + ": CommandException: "
                                    + ar.exception);
                        } else {
                            loge("enableNrDualConnectivity" + ": Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;
                }

                case CMD_ENABLE_VONR: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_ENABLE_VONR_DONE, request);
                    Phone phone = getPhoneFromRequest(request);
                    if (phone != null) {
                        phone.setVoNrEnabled((boolean) request.argument, onCompleted,
                                request.workSource);
                    } else {
                        loge("setVoNrEnabled: No phone object");
                        request.result =
                                TelephonyManager.ENABLE_VONR_RADIO_NOT_AVAILABLE;
                        notifyRequester(request);
                    }
                    break;
                }

                case EVENT_ENABLE_VONR_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result = TelephonyManager.ENABLE_VONR_SUCCESS;
                    } else {
                        request.result = TelephonyManager.ENABLE_VONR_RADIO_ERROR;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.RADIO_NOT_AVAILABLE) {
                                request.result = TelephonyManager.ENABLE_VONR_RADIO_NOT_AVAILABLE;
                            } else if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                request.result = TelephonyManager.ENABLE_VONR_REQUEST_NOT_SUPPORTED;
                            } else {
                                request.result = TelephonyManager.ENABLE_VONR_RADIO_ERROR;
                            }
                            loge("setVoNrEnabled" + ": CommandException: "
                                    + ar.exception);
                        } else {
                            loge("setVoNrEnabled" + ": Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;
                }

                case CMD_GET_ALLOWED_NETWORK_TYPES_BITMASK:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_ALLOWED_NETWORK_TYPES_BITMASK_DONE,
                            request);
                    getPhoneFromRequest(request).getAllowedNetworkTypesBitmask(onCompleted);
                    break;

                case EVENT_GET_ALLOWED_NETWORK_TYPES_BITMASK_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;     // Integer
                    } else {
                        // request.result must be set to something non-null
                        // for the calling thread to unblock
                        request.result = new int[]{-1};
                        if (ar.result == null) {
                            loge("getAllowedNetworkTypesBitmask: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("getAllowedNetworkTypesBitmask: CommandException: "
                                    + ar.exception);
                        } else {
                            loge("getAllowedNetworkTypesBitmask: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_SET_ALLOWED_NETWORK_TYPES_FOR_REASON:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_ALLOWED_NETWORK_TYPES_FOR_REASON_DONE,
                            request);
                    Pair<Integer, Long> reasonWithNetworkTypes =
                            (Pair<Integer, Long>) request.argument;
                    getPhoneFromRequest(request).setAllowedNetworkTypes(
                            reasonWithNetworkTypes.first,
                            reasonWithNetworkTypes.second,
                            onCompleted);
                    break;

                case EVENT_SET_ALLOWED_NETWORK_TYPES_FOR_REASON_DONE:
                    handleNullReturnEvent(msg, "setAllowedNetworkTypesForReason");
                    break;

                case CMD_INVOKE_OEM_RIL_REQUEST_RAW:
                    request = (MainThreadRequest)msg.obj;
                    onCompleted = obtainMessage(EVENT_INVOKE_OEM_RIL_REQUEST_RAW_DONE, request);
                    defaultPhone.invokeOemRilRequestRaw((byte[]) request.argument, onCompleted);
                    break;

                case EVENT_INVOKE_OEM_RIL_REQUEST_RAW_DONE:
                    ar = (AsyncResult)msg.obj;
                    request = (MainThreadRequest)ar.userObj;
                    request.result = ar;
                    notifyRequester(request);
                    break;

                case CMD_SET_VOICEMAIL_NUMBER:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_VOICEMAIL_NUMBER_DONE, request);
                    Pair<String, String> tagNum = (Pair<String, String>) request.argument;
                    getPhoneFromRequest(request).setVoiceMailNumber(tagNum.first, tagNum.second,
                            onCompleted);
                    break;

                case EVENT_SET_VOICEMAIL_NUMBER_DONE:
                    handleNullReturnEvent(msg, "setVoicemailNumber");
                    break;

                case CMD_SET_NETWORK_SELECTION_MODE_AUTOMATIC:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_DONE,
                            request);
                    getPhoneFromRequest(request).setNetworkSelectionModeAutomatic(onCompleted);
                    break;

                case EVENT_SET_NETWORK_SELECTION_MODE_AUTOMATIC_DONE:
                    handleNullReturnEvent(msg, "setNetworkSelectionModeAutomatic");
                    break;

                case CMD_PERFORM_NETWORK_SCAN:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_PERFORM_NETWORK_SCAN_DONE, request);
                    getPhoneFromRequest(request).getAvailableNetworks(onCompleted);
                    break;

                case CMD_GET_CALL_FORWARDING: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_CALL_FORWARDING_DONE, request);
                    Pair<Integer, TelephonyManager.CallForwardingInfoCallback> args =
                            (Pair<Integer, TelephonyManager.CallForwardingInfoCallback>)
                                    request.argument;
                    int callForwardingReason = args.first;
                    request.phone.getCallForwardingOption(callForwardingReason, onCompleted);
                    break;
                }
                case EVENT_GET_CALL_FORWARDING_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    TelephonyManager.CallForwardingInfoCallback callback =
                            ((Pair<Integer, TelephonyManager.CallForwardingInfoCallback>)
                                    request.argument).second;
                    if (ar.exception == null && ar.result != null) {
                        CallForwardingInfo callForwardingInfo = null;
                        CallForwardInfo[] callForwardInfos = (CallForwardInfo[]) ar.result;
                        for (CallForwardInfo callForwardInfo : callForwardInfos) {
                            // Service Class is a bit mask per 3gpp 27.007. Search for
                            // any service for voice call.
                            if ((callForwardInfo.serviceClass
                                    & CommandsInterface.SERVICE_CLASS_VOICE) > 0) {
                                callForwardingInfo = new CallForwardingInfo(
                                        callForwardInfo.status
                                                == CommandsInterface.CF_ACTION_ENABLE,
                                        callForwardInfo.reason,
                                        callForwardInfo.number,
                                        callForwardInfo.timeSeconds);
                                break;
                            }
                        }
                        // Didn't find a call forward info for voice call.
                        if (callForwardingInfo == null) {
                            callForwardingInfo = new CallForwardingInfo(false /* enabled */,
                                    0 /* reason */, null /* number */, 0 /* timeout */);
                        }
                        callback.onCallForwardingInfoAvailable(callForwardingInfo);
                    } else {
                        if (ar.result == null) {
                            loge("EVENT_GET_CALL_FORWARDING_DONE: Empty response");
                        }
                        if (ar.exception != null) {
                            loge("EVENT_GET_CALL_FORWARDING_DONE: Exception: " + ar.exception);
                        }
                        int errorCode = TelephonyManager
                                .CallForwardingInfoCallback.RESULT_ERROR_UNKNOWN;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.FDN_CHECK_FAILURE) {
                                errorCode = TelephonyManager
                                        .CallForwardingInfoCallback.RESULT_ERROR_FDN_CHECK_FAILURE;
                            } else if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                errorCode = TelephonyManager
                                        .CallForwardingInfoCallback.RESULT_ERROR_NOT_SUPPORTED;
                            }
                        }
                        callback.onError(errorCode);
                    }
                    break;
                }

                case CMD_SET_CALL_FORWARDING: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_CALL_FORWARDING_DONE, request);
                    request = (MainThreadRequest) msg.obj;
                    CallForwardingInfo callForwardingInfoToSet =
                            ((Pair<CallForwardingInfo, Consumer<Integer>>)
                                    request.argument).first;
                    request.phone.setCallForwardingOption(
                            callForwardingInfoToSet.isEnabled()
                                    ? CommandsInterface.CF_ACTION_REGISTRATION
                                    : CommandsInterface.CF_ACTION_DISABLE,
                            callForwardingInfoToSet.getReason(),
                            callForwardingInfoToSet.getNumber(),
                            callForwardingInfoToSet.getTimeoutSeconds(), onCompleted);
                    break;
                }

                case EVENT_SET_CALL_FORWARDING_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    Consumer<Integer> callback =
                            ((Pair<CallForwardingInfo, Consumer<Integer>>)
                                    request.argument).second;
                    if (ar.exception != null) {
                        loge("setCallForwarding exception: " + ar.exception);
                        int errorCode = TelephonyManager.CallForwardingInfoCallback
                                .RESULT_ERROR_UNKNOWN;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.FDN_CHECK_FAILURE) {
                                errorCode = TelephonyManager.CallForwardingInfoCallback
                                        .RESULT_ERROR_FDN_CHECK_FAILURE;
                            } else if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                errorCode = TelephonyManager.CallForwardingInfoCallback
                                        .RESULT_ERROR_NOT_SUPPORTED;
                            }
                        }
                        callback.accept(errorCode);
                    } else {
                        callback.accept(TelephonyManager.CallForwardingInfoCallback.RESULT_SUCCESS);
                    }
                    break;
                }

                case CMD_GET_CALL_WAITING: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_CALL_WAITING_DONE, request);
                    getPhoneFromRequest(request).getCallWaiting(onCompleted);
                    break;
                }

                case EVENT_GET_CALL_WAITING_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    Consumer<Integer> callback = (Consumer<Integer>) request.argument;
                    int callWaitingStatus = TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR;
                    if (ar.exception == null && ar.result != null) {
                        int[] callForwardResults = (int[]) ar.result;
                        // Service Class is a bit mask per 3gpp 27.007.
                        // Search for any service for voice call.
                        if (callForwardResults.length > 1
                                && ((callForwardResults[1]
                                & CommandsInterface.SERVICE_CLASS_VOICE) > 0)) {
                            callWaitingStatus = callForwardResults[0] == 0
                                    ? TelephonyManager.CALL_WAITING_STATUS_DISABLED
                                    : TelephonyManager.CALL_WAITING_STATUS_ENABLED;
                        } else {
                            callWaitingStatus = TelephonyManager.CALL_WAITING_STATUS_DISABLED;
                        }
                    } else {
                        if (ar.result == null) {
                            loge("EVENT_GET_CALL_WAITING_DONE: Empty response");
                        }
                        if (ar.exception != null) {
                            loge("EVENT_GET_CALL_WAITING_DONE: Exception: " + ar.exception);
                        }
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                callWaitingStatus =
                                        TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED;
                            } else if (error == CommandException.Error.FDN_CHECK_FAILURE) {
                                callWaitingStatus =
                                        TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE;
                            }
                        }
                    }
                    callback.accept(callWaitingStatus);
                    break;
                }

                case CMD_SET_CALL_WAITING: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_CALL_WAITING_DONE, request);
                    boolean enable = ((Pair<Boolean, Consumer<Integer>>) request.argument).first;
                    getPhoneFromRequest(request).setCallWaiting(enable, onCompleted);
                    break;
                }

                case EVENT_SET_CALL_WAITING_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    boolean enable = ((Pair<Boolean, Consumer<Integer>>) request.argument).first;
                    Consumer<Integer> callback =
                            ((Pair<Boolean, Consumer<Integer>>) request.argument).second;
                    if (ar.exception != null) {
                        loge("setCallWaiting exception: " + ar.exception);
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                callback.accept(TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED);
                            } else if (error == CommandException.Error.FDN_CHECK_FAILURE) {
                                callback.accept(
                                        TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE);
                            } else {
                                callback.accept(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
                            }
                        } else {
                            callback.accept(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
                        }
                    } else {
                        callback.accept(enable ? TelephonyManager.CALL_WAITING_STATUS_ENABLED
                                : TelephonyManager.CALL_WAITING_STATUS_DISABLED);
                    }
                    break;
                }
                case EVENT_PERFORM_NETWORK_SCAN_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    CellNetworkScanResult cellScanResult;
                    if (ar.exception == null && ar.result != null) {
                        cellScanResult = new CellNetworkScanResult(
                                CellNetworkScanResult.STATUS_SUCCESS,
                                (List<OperatorInfo>) ar.result);
                    } else {
                        if (ar.result == null) {
                            loge("getCellNetworkScanResults: Empty response");
                        }
                        if (ar.exception != null) {
                            loge("getCellNetworkScanResults: Exception: " + ar.exception);
                        }
                        int errorCode = CellNetworkScanResult.STATUS_UNKNOWN_ERROR;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.RADIO_NOT_AVAILABLE) {
                                errorCode = CellNetworkScanResult.STATUS_RADIO_NOT_AVAILABLE;
                            } else if (error == CommandException.Error.GENERIC_FAILURE) {
                                errorCode = CellNetworkScanResult.STATUS_RADIO_GENERIC_FAILURE;
                            }
                        }
                        cellScanResult = new CellNetworkScanResult(errorCode, null);
                    }
                    request.result = cellScanResult;
                    notifyRequester(request);
                    break;

                case CMD_SET_NETWORK_SELECTION_MODE_MANUAL:
                    request = (MainThreadRequest) msg.obj;
                    ManualNetworkSelectionArgument selArg =
                            (ManualNetworkSelectionArgument) request.argument;
                    onCompleted = obtainMessage(EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_DONE,
                            request);
                    getPhoneFromRequest(request).selectNetworkManually(selArg.operatorInfo,
                            selArg.persistSelection, onCompleted);
                    break;

                case EVENT_SET_NETWORK_SELECTION_MODE_MANUAL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result = true;
                    } else {
                        request.result = false;
                        loge("setNetworkSelectionModeManual " + ar.exception);
                    }
                    notifyRequester(request);
                    mApp.onNetworkSelectionChanged(request.subId);
                    break;

                case CMD_GET_MODEM_ACTIVITY_INFO:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_MODEM_ACTIVITY_INFO_DONE, request);
                    if (defaultPhone != null) {
                        defaultPhone.getModemActivityInfo(onCompleted, request.workSource);
                    } else {
                        ResultReceiver result = (ResultReceiver) request.argument;
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(TelephonyManager.MODEM_ACTIVITY_RESULT_KEY,
                                new ModemActivityInfo(0, 0, 0,
                                        new int[ModemActivityInfo.getNumTxPowerLevels()], 0));
                        result.send(0, bundle);
                    }
                    break;

                case EVENT_GET_MODEM_ACTIVITY_INFO_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    ResultReceiver result = (ResultReceiver) request.argument;
                    int error = 0;
                    ModemActivityInfo ret = null;
                    if (mLastModemActivityInfo == null) {
                        mLastModemActivitySpecificInfo = new ActivityStatsTechSpecificInfo[1];
                        mLastModemActivitySpecificInfo[0] =
                                new ActivityStatsTechSpecificInfo(
                                        0,
                                        0,
                                        new int[ModemActivityInfo.getNumTxPowerLevels()],
                                        0);
                        mLastModemActivityInfo =
                                new ModemActivityInfo(0, 0, 0, mLastModemActivitySpecificInfo);
                    }

                    if (ar.exception == null && ar.result != null) {
                        // Update the last modem activity info and the result of the request.
                        ModemActivityInfo info = (ModemActivityInfo) ar.result;
                        if (isModemActivityInfoValid(info)) {
                            mergeModemActivityInfo(info);
                        } else {
                            loge("queryModemActivityInfo: invalid response");
                        }
                        // This is needed to decouple ret from mLastModemActivityInfo
                        // We don't want to return mLastModemActivityInfo which is updated
                        // inside mergeModemActivityInfo()
                        ret = new ModemActivityInfo(
                                mLastModemActivityInfo.getTimestampMillis(),
                                mLastModemActivityInfo.getSleepTimeMillis(),
                                mLastModemActivityInfo.getIdleTimeMillis(),
                                deepCopyModemActivitySpecificInfo(mLastModemActivitySpecificInfo));

                    } else {
                        if (ar.result == null) {
                            loge("queryModemActivityInfo: Empty response");
                            error = TelephonyManager.ModemActivityInfoException
                                    .ERROR_INVALID_INFO_RECEIVED;
                        } else if (ar.exception instanceof CommandException) {
                            loge("queryModemActivityInfo: CommandException: " + ar.exception);
                            error = TelephonyManager.ModemActivityInfoException
                                    .ERROR_MODEM_RESPONSE_ERROR;
                        } else {
                            loge("queryModemActivityInfo: Unknown exception");
                            error = TelephonyManager.ModemActivityInfoException
                                    .ERROR_UNKNOWN;
                        }
                    }
                    Bundle bundle = new Bundle();
                    if (ret != null) {
                        bundle.putParcelable(
                                TelephonyManager.MODEM_ACTIVITY_RESULT_KEY,
                                ret);
                    } else {
                        bundle.putInt(TelephonyManager.EXCEPTION_RESULT_KEY, error);
                    }
                    result.send(0, bundle);
                    notifyRequester(request);
                    break;
                }

                case CMD_SET_ALLOWED_CARRIERS: {
                    request = (MainThreadRequest) msg.obj;
                    CarrierRestrictionRules argument =
                            (CarrierRestrictionRules) request.argument;
                    onCompleted = obtainMessage(EVENT_SET_ALLOWED_CARRIERS_DONE, request);
                    defaultPhone.setAllowedCarriers(argument, onCompleted, request.workSource);
                    break;
                }

                case EVENT_SET_ALLOWED_CARRIERS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = TelephonyManager.SET_CARRIER_RESTRICTION_ERROR;
                        if (ar.exception instanceof CommandException) {
                            loge("setAllowedCarriers: CommandException: " + ar.exception);
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                request.result =
                                        TelephonyManager.SET_CARRIER_RESTRICTION_NOT_SUPPORTED;
                            }
                        } else {
                            loge("setAllowedCarriers: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_GET_ALLOWED_CARRIERS:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_ALLOWED_CARRIERS_DONE, request);
                    defaultPhone.getAllowedCarriers(onCompleted, request.workSource);
                    break;

                case EVENT_GET_ALLOWED_CARRIERS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IllegalStateException(
                                "Failed to get carrier restrictions");
                        if (ar.result == null) {
                            loge("getAllowedCarriers: Empty response");
                        } else if (ar.exception instanceof CommandException) {
                            loge("getAllowedCarriers: CommandException: " +
                                    ar.exception);
                        } else {
                            loge("getAllowedCarriers: Unknown exception");
                        }
                    }
                    if (request.argument != null) {
                        // This is for the implementation of carrierRestrictionStatus.
                        CallerCallbackInfo callbackInfo = (CallerCallbackInfo) request.argument;
                        Consumer<Integer> callback = callbackInfo.getConsumer();
                        int callerCarrierId = callbackInfo.getCarrierId();
                        int lockStatus = TelephonyManager.CARRIER_RESTRICTION_STATUS_UNKNOWN;
                        if (ar.exception == null && ar.result instanceof CarrierRestrictionRules) {
                            CarrierRestrictionRules carrierRestrictionRules =
                                    (CarrierRestrictionRules) ar.result;
                            int carrierId = -1;
                            try {
                                CarrierIdentifier carrierIdentifier =
                                        carrierRestrictionRules.getAllowedCarriers().get(0);
                                carrierId = CarrierResolver.getCarrierIdFromIdentifier(mApp,
                                        carrierIdentifier);
                            } catch (NullPointerException | IndexOutOfBoundsException ex) {
                                Rlog.e(LOG_TAG, "CarrierIdentifier exception = " + ex);
                            }
                            lockStatus = carrierRestrictionRules.getCarrierRestrictionStatus();
                            if (carrierId != -1 && callerCarrierId == carrierId && lockStatus
                                    == TelephonyManager.CARRIER_RESTRICTION_STATUS_RESTRICTED) {
                                lockStatus = TelephonyManager
                                        .CARRIER_RESTRICTION_STATUS_RESTRICTED_TO_CALLER;
                            }
                        } else {
                            Rlog.e(LOG_TAG,
                                    "getCarrierRestrictionStatus: exception ex = " + ar.exception);
                        }
                        callback.accept(lockStatus);
                    } else {
                        // This is for the implementation of getAllowedCarriers.
                        notifyRequester(request);
                    }
                    break;

                case EVENT_GET_FORBIDDEN_PLMNS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IllegalArgumentException(
                                "Failed to retrieve Forbidden Plmns");
                        if (ar.result == null) {
                            loge("getForbiddenPlmns: Empty response");
                        } else {
                            loge("getForbiddenPlmns: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;

                case CMD_GET_FORBIDDEN_PLMNS:
                    request = (MainThreadRequest) msg.obj;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("getForbiddenPlmns() UiccPort is null");
                        request.result = new IllegalArgumentException(
                                "getForbiddenPlmns() UiccPort is null");
                        notifyRequester(request);
                        break;
                    }
                    Integer appType = (Integer) request.argument;
                    UiccCardApplication uiccApp = uiccPort.getApplicationByType(appType);
                    if (uiccApp == null) {
                        loge("getForbiddenPlmns() no app with specified type -- "
                                + appType);
                        request.result = new IllegalArgumentException("Failed to get UICC App");
                        notifyRequester(request);
                        break;
                    } else {
                        if (DBG) logv("getForbiddenPlmns() found app " + uiccApp.getAid()
                                + " specified type -- " + appType);
                    }
                    onCompleted = obtainMessage(EVENT_GET_FORBIDDEN_PLMNS_DONE, request);
                    ((SIMRecords) uiccApp.getIccRecords()).getForbiddenPlmns(
                            onCompleted);
                    break;

                case CMD_SWITCH_SLOTS:
                    request = (MainThreadRequest) msg.obj;
                    List<UiccSlotMapping> slotMapping = (List<UiccSlotMapping>) request.argument;
                    onCompleted = obtainMessage(EVENT_SWITCH_SLOTS_DONE, request);
                    UiccController.getInstance().switchSlots(slotMapping, onCompleted);
                    break;

                case EVENT_SWITCH_SLOTS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    request.result = (ar.exception == null);
                    notifyRequester(request);
                    break;
                case CMD_GET_NETWORK_SELECTION_MODE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_NETWORK_SELECTION_MODE_DONE, request);
                    getPhoneFromRequest(request).getNetworkSelectionMode(onCompleted);
                    break;

                case EVENT_GET_NETWORK_SELECTION_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception != null) {
                        request.result = TelephonyManager.NETWORK_SELECTION_MODE_UNKNOWN;
                    } else {
                        int mode = ((int[]) ar.result)[0];
                        if (mode == 0) {
                            request.result = TelephonyManager.NETWORK_SELECTION_MODE_AUTO;
                        } else {
                            request.result = TelephonyManager.NETWORK_SELECTION_MODE_MANUAL;
                        }
                    }
                    notifyRequester(request);
                    break;
                case CMD_GET_CDMA_ROAMING_MODE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_CDMA_ROAMING_MODE_DONE, request);
                    getPhoneFromRequest(request).queryCdmaRoamingPreference(onCompleted);
                    break;
                case EVENT_GET_CDMA_ROAMING_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception != null) {
                        request.result = TelephonyManager.CDMA_ROAMING_MODE_RADIO_DEFAULT;
                    } else {
                        request.result = ((int[]) ar.result)[0];
                    }
                    notifyRequester(request);
                    break;
                case CMD_SET_CDMA_ROAMING_MODE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_CDMA_ROAMING_MODE_DONE, request);
                    int mode = (int) request.argument;
                    getPhoneFromRequest(request).setCdmaRoamingPreference(mode, onCompleted);
                    break;
                case EVENT_SET_CDMA_ROAMING_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    request.result = ar.exception == null;
                    notifyRequester(request);
                    break;
                case CMD_GET_CDMA_SUBSCRIPTION_MODE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_CDMA_SUBSCRIPTION_MODE_DONE, request);
                    getPhoneFromRequest(request).queryCdmaSubscriptionMode(onCompleted);
                    break;
                case EVENT_GET_CDMA_SUBSCRIPTION_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception != null) {
                        request.result = TelephonyManager.CDMA_SUBSCRIPTION_RUIM_SIM;
                    } else {
                        request.result = ((int[]) ar.result)[0];
                    }
                    notifyRequester(request);
                    break;
                case CMD_SET_CDMA_SUBSCRIPTION_MODE:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_CDMA_SUBSCRIPTION_MODE_DONE, request);
                    int subscriptionMode = (int) request.argument;
                    getPhoneFromRequest(request).setCdmaSubscriptionMode(
                            subscriptionMode, onCompleted);
                    break;
                case EVENT_SET_CDMA_SUBSCRIPTION_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    request.result = ar.exception == null;
                    notifyRequester(request);
                    break;
                case CMD_GET_ALL_CELL_INFO:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_ALL_CELL_INFO_DONE, request);
                    request.phone.requestCellInfoUpdate(request.workSource, onCompleted);
                    break;
                case EVENT_GET_ALL_CELL_INFO_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    // If a timeout occurs, the response will be null
                    request.result = (ar.exception == null && ar.result != null)
                            ? ar.result : new ArrayList<CellInfo>();
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;
                case CMD_REQUEST_CELL_INFO_UPDATE:
                    request = (MainThreadRequest) msg.obj;
                    request.phone.requestCellInfoUpdate(request.workSource,
                            obtainMessage(EVENT_REQUEST_CELL_INFO_UPDATE_DONE, request));
                    break;
                case EVENT_REQUEST_CELL_INFO_UPDATE_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    ICellInfoCallback cb = (ICellInfoCallback) request.argument;
                    try {
                        if (ar.exception != null) {
                            Log.e(LOG_TAG, "Exception retrieving CellInfo=" + ar.exception);
                            cb.onError(
                                    TelephonyManager.CellInfoCallback.ERROR_MODEM_ERROR,
                                    ar.exception.getClass().getName(),
                                    ar.exception.toString());
                        } else if (ar.result == null) {
                            Log.w(LOG_TAG, "Timeout Waiting for CellInfo!");
                            cb.onError(TelephonyManager.CellInfoCallback.ERROR_TIMEOUT, null, null);
                        } else {
                            // use the result as returned
                            cb.onCellInfo((List<CellInfo>) ar.result);
                        }
                    } catch (RemoteException re) {
                        Log.w(LOG_TAG, "Discarded CellInfo due to Callback RemoteException");
                    }
                    break;
                case CMD_GET_CELL_LOCATION: {
                    request = (MainThreadRequest) msg.obj;
                    WorkSource ws = (WorkSource) request.argument;
                    Phone phone = getPhoneFromRequest(request);
                    phone.getCellIdentity(ws, obtainMessage(EVENT_GET_CELL_LOCATION_DONE, request));
                    break;
                }
                case EVENT_GET_CELL_LOCATION_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result = ar.result;
                    } else {
                        Phone phone = getPhoneFromRequest(request);
                        request.result = (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA)
                                ? new CellIdentityCdma() : new CellIdentityGsm();
                    }

                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;
                }
                case CMD_MODEM_REBOOT:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_RESET_MODEM_CONFIG_DONE, request);
                    defaultPhone.rebootModem(onCompleted);
                    break;
                case EVENT_CMD_MODEM_REBOOT_DONE:
                    handleNullReturnEvent(msg, "rebootModem");
                    break;
                case CMD_REQUEST_ENABLE_MODEM: {
                    request = (MainThreadRequest) msg.obj;
                    boolean enable = (boolean) request.argument;
                    onCompleted = obtainMessage(EVENT_ENABLE_MODEM_DONE, request);
                    onCompleted.arg1 = enable ? 1 : 0;
                    PhoneConfigurationManager.getInstance()
                            .enablePhone(request.phone, enable, onCompleted);
                    break;
                }
                case EVENT_ENABLE_MODEM_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    request.result = (ar.exception == null);
                    int phoneId = request.phone.getPhoneId();
                    //update the cache as modem status has changed
                    if ((boolean) request.result) {
                        mPhoneConfigurationManager.addToPhoneStatusCache(phoneId, msg.arg1 == 1);
                        updateModemStateMetrics();
                    } else {
                        Log.e(LOG_TAG, msg.what + " failure. Not updating modem status."
                                + ar.exception);
                    }
                    notifyRequester(request);
                    break;
                }
                case CMD_GET_MODEM_STATUS:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_MODEM_STATUS_DONE, request);
                    PhoneConfigurationManager.getInstance()
                            .getPhoneStatusFromModem(request.phone, onCompleted);
                    break;
                case EVENT_GET_MODEM_STATUS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    int id = request.phone.getPhoneId();
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                        //update the cache as modem status has changed
                        mPhoneConfigurationManager.addToPhoneStatusCache(id,
                                (boolean) request.result);
                    } else {
                        // Return true if modem status cannot be retrieved. For most cases,
                        // modem status is on. And for older version modems, GET_MODEM_STATUS
                        // and disable modem are not supported. Modem is always on.
                        // TODO: this should be fixed in R to support a third
                        // status UNKNOWN b/131631629
                        request.result = true;
                        Log.e(LOG_TAG, msg.what + " failure. Not updating modem status."
                                + ar.exception);
                    }
                    notifyRequester(request);
                    break;
                case CMD_SET_SYSTEM_SELECTION_CHANNELS: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_SYSTEM_SELECTION_CHANNELS_DONE, request);
                    Pair<List<RadioAccessSpecifier>, Consumer<Boolean>> args =
                            (Pair<List<RadioAccessSpecifier>, Consumer<Boolean>>) request.argument;
                    request.phone.setSystemSelectionChannels(args.first, onCompleted);
                    break;
                }
                case EVENT_SET_SYSTEM_SELECTION_CHANNELS_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    Pair<List<RadioAccessSpecifier>, Consumer<Boolean>> args =
                            (Pair<List<RadioAccessSpecifier>, Consumer<Boolean>>) request.argument;
                    args.second.accept(ar.exception == null);
                    notifyRequester(request);
                    break;
                }
                case CMD_GET_SYSTEM_SELECTION_CHANNELS: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_SYSTEM_SELECTION_CHANNELS_DONE, request);
                    Phone phone = getPhoneFromRequest(request);
                    if (phone != null) {
                        phone.getSystemSelectionChannels(onCompleted);
                    } else {
                        loge("getSystemSelectionChannels: No phone object");
                        request.result = new ArrayList<RadioAccessSpecifier>();
                        notifyRequester(request);
                    }
                    break;
                }
                case EVENT_GET_SYSTEM_SELECTION_CHANNELS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = new IllegalStateException(
                                "Failed to retrieve system selecton channels");
                        if (ar.result == null) {
                            loge("getSystemSelectionChannels: Empty response");
                        } else {
                            loge("getSystemSelectionChannels: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;
                case EVENT_SET_FORBIDDEN_PLMNS_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        request.result = -1;
                        loge("Failed to set Forbidden Plmns");
                        if (ar.result == null) {
                            loge("setForbidenPlmns: Empty response");
                        } else if (ar.exception != null) {
                            loge("setForbiddenPlmns: Exception: " + ar.exception);
                            request.result = -1;
                        } else {
                            loge("setForbiddenPlmns: Unknown exception");
                        }
                    }
                    notifyRequester(request);
                    break;
                case CMD_SET_FORBIDDEN_PLMNS:
                    request = (MainThreadRequest) msg.obj;
                    uiccPort = getUiccPortFromRequest(request);
                    if (uiccPort == null) {
                        loge("setForbiddenPlmns: UiccPort is null");
                        request.result = -1;
                        notifyRequester(request);
                        break;
                    }
                    Pair<Integer, List<String>> setFplmnsArgs =
                            (Pair<Integer, List<String>>) request.argument;
                    appType = setFplmnsArgs.first;
                    List<String> fplmns = setFplmnsArgs.second;
                    uiccApp = uiccPort.getApplicationByType(appType);
                    if (uiccApp == null) {
                        loge("setForbiddenPlmns: no app with specified type -- " + appType);
                        request.result = -1;
                        loge("Failed to get UICC App");
                        notifyRequester(request);
                    } else {
                        onCompleted = obtainMessage(EVENT_SET_FORBIDDEN_PLMNS_DONE, request);
                        ((SIMRecords) uiccApp.getIccRecords())
                                .setForbiddenPlmns(onCompleted, fplmns);
                    }
                    break;
                case CMD_ERASE_MODEM_CONFIG:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_ERASE_MODEM_CONFIG_DONE, request);
                    defaultPhone.eraseModemConfig(onCompleted);
                    break;
                case EVENT_ERASE_MODEM_CONFIG_DONE:
                    handleNullReturnEvent(msg, "eraseModemConfig");
                    break;

                case CMD_ERASE_DATA_SHARED_PREFERENCES:
                    request = (MainThreadRequest) msg.obj;
                    request.result = defaultPhone.eraseDataInSharedPreferences();
                    notifyRequester(request);
                    break;

                case CMD_CHANGE_ICC_LOCK_PASSWORD:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_CHANGE_ICC_LOCK_PASSWORD_DONE, request);
                    Pair<String, String> changed = (Pair<String, String>) request.argument;
                    getPhoneFromRequest(request).getIccCard().changeIccLockPassword(
                            changed.first, changed.second, onCompleted);
                    break;
                case EVENT_CHANGE_ICC_LOCK_PASSWORD_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result = TelephonyManager.CHANGE_ICC_LOCK_SUCCESS;
                        // If the operation is successful, update the PIN storage
                        Pair<String, String> passwords = (Pair<String, String>) request.argument;
                        int phoneId = getPhoneFromRequest(request).getPhoneId();
                        UiccController.getInstance().getPinStorage()
                                .storePin(passwords.second, phoneId);
                    } else {
                        request.result = msg.arg1;
                    }
                    notifyRequester(request);
                    break;

                case CMD_SET_ICC_LOCK_ENABLED: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_ICC_LOCK_ENABLED_DONE, request);
                    Pair<Boolean, String> enabled = (Pair<Boolean, String>) request.argument;
                    getPhoneFromRequest(request).getIccCard().setIccLockEnabled(
                            enabled.first, enabled.second, onCompleted);
                    break;
                }
                case EVENT_SET_ICC_LOCK_ENABLED_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null) {
                        request.result = TelephonyManager.CHANGE_ICC_LOCK_SUCCESS;
                        // If the operation is successful, update the PIN storage
                        Pair<Boolean, String> enabled = (Pair<Boolean, String>) request.argument;
                        int phoneId = getPhoneFromRequest(request).getPhoneId();
                        if (enabled.first) {
                            UiccController.getInstance().getPinStorage()
                                    .storePin(enabled.second, phoneId);
                        } else {
                            UiccController.getInstance().getPinStorage().clearPin(phoneId);
                        }
                    } else {
                        request.result = msg.arg1;
                    }


                    notifyRequester(request);
                    break;

                case MSG_NOTIFY_USER_ACTIVITY:
                    removeMessages(MSG_NOTIFY_USER_ACTIVITY);
                    Intent intent = new Intent(TelephonyIntents.ACTION_USER_ACTIVITY_NOTIFICATION);
                    intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                    getDefaultPhone().getContext().sendBroadcastAsUser(
                            intent, UserHandle.ALL, permission.USER_ACTIVITY);
                    break;

                case CMD_SET_DATA_THROTTLING: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_DATA_THROTTLING_DONE, request);
                    DataThrottlingRequest dataThrottlingRequest =
                            (DataThrottlingRequest) request.argument;
                    Phone phone = getPhoneFromRequest(request);
                    if (phone != null) {
                        phone.setDataThrottling(onCompleted,
                                request.workSource, dataThrottlingRequest.getDataThrottlingAction(),
                                dataThrottlingRequest.getCompletionDurationMillis());
                    } else {
                        loge("setDataThrottling: No phone object");
                        request.result =
                                TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
                        notifyRequester(request);
                    }

                    break;
                }
                case EVENT_SET_DATA_THROTTLING_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;

                    if (ar.exception == null) {
                        request.result = TelephonyManager.THERMAL_MITIGATION_RESULT_SUCCESS;
                    } else if (ar.exception instanceof CommandException) {
                        loge("setDataThrottling: CommandException: " + ar.exception);
                        CommandException.Error error =
                                ((CommandException) (ar.exception)).getCommandError();

                        if (error == CommandException.Error.RADIO_NOT_AVAILABLE) {
                            request.result = TelephonyManager
                                    .THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
                        } else if (error == CommandException.Error.INVALID_ARGUMENTS) {
                            request.result = SET_DATA_THROTTLING_MODEM_THREW_INVALID_PARAMS;
                        } else if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                            request.result = MODEM_DOES_NOT_SUPPORT_DATA_THROTTLING_ERROR_CODE;
                        } else {
                            request.result =
                                    TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_ERROR;
                        }
                    } else {
                        request.result = TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_ERROR;
                    }
                    Log.w(LOG_TAG, "DataThrottlingResult = " + request.result);
                    notifyRequester(request);
                    break;

                case CMD_SET_SIM_POWER: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_SET_SIM_POWER_DONE, request);
                    request = (MainThreadRequest) msg.obj;
                    int stateToSet =
                            ((Pair<Integer, IIntegerConsumer>)
                                    request.argument).first;
                    request.phone.setSimPowerState(stateToSet, onCompleted, request.workSource);
                    break;
                }
                case EVENT_SET_SIM_POWER_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    IIntegerConsumer callback =
                            ((Pair<Integer, IIntegerConsumer>) request.argument).second;
                    if (ar.exception != null) {
                        loge("setSimPower exception: " + ar.exception);
                        int errorCode = TelephonyManager.CallForwardingInfoCallback
                                .RESULT_ERROR_UNKNOWN;
                        if (ar.exception instanceof CommandException) {
                            CommandException.Error error =
                                    ((CommandException) (ar.exception)).getCommandError();
                            if (error == CommandException.Error.SIM_ERR) {
                                errorCode = TelephonyManager.SET_SIM_POWER_STATE_SIM_ERROR;
                            } else if (error == CommandException.Error.INVALID_ARGUMENTS) {
                                errorCode = TelephonyManager.SET_SIM_POWER_STATE_ALREADY_IN_STATE;
                            } else if (error == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                                errorCode = TelephonyManager.SET_SIM_POWER_STATE_NOT_SUPPORTED;
                            } else {
                                errorCode = TelephonyManager.SET_SIM_POWER_STATE_MODEM_ERROR;
                            }
                        }
                        try {
                            callback.accept(errorCode);
                        } catch (RemoteException e) {
                            // Ignore if the remote process is no longer available to call back.
                            Log.w(LOG_TAG, "setSimPower: callback not available.");
                        }
                    } else {
                        try {
                            callback.accept(TelephonyManager.SET_SIM_POWER_STATE_SUCCESS);
                        } catch (RemoteException e) {
                            // Ignore if the remote process is no longer available to call back.
                            Log.w(LOG_TAG, "setSimPower: callback not available.");
                        }
                    }
                    break;
                }
                case CMD_SET_SIGNAL_STRENGTH_UPDATE_REQUEST: {
                    request = (MainThreadRequest) msg.obj;

                    final Phone phone = getPhoneFromRequest(request);
                    if (phone == null || phone.getServiceStateTracker() == null) {
                        request.result = new IllegalStateException("Phone or SST is null");
                        notifyRequester(request);
                        break;
                    }

                    Pair<Integer, SignalStrengthUpdateRequest> pair =
                            (Pair<Integer, SignalStrengthUpdateRequest>) request.argument;
                    onCompleted = obtainMessage(EVENT_SET_SIGNAL_STRENGTH_UPDATE_REQUEST_DONE,
                            request);
                    phone.getSignalStrengthController().setSignalStrengthUpdateRequest(
                            request.subId, pair.first /*callingUid*/,
                            pair.second /*request*/, onCompleted);
                    break;
                }
                case EVENT_SET_SIGNAL_STRENGTH_UPDATE_REQUEST_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    // request.result will be the exception of ar if present, true otherwise.
                    // Be cautious not to leave result null which will wait() forever
                    request.result = ar.exception != null ? ar.exception : true;
                    notifyRequester(request);
                    break;
                }
                case CMD_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST: {
                    request = (MainThreadRequest) msg.obj;

                    Phone phone = getPhoneFromRequest(request);
                    if (phone == null || phone.getServiceStateTracker() == null) {
                        request.result = new IllegalStateException("Phone or SST is null");
                        notifyRequester(request);
                        break;
                    }

                    Pair<Integer, SignalStrengthUpdateRequest> pair =
                            (Pair<Integer, SignalStrengthUpdateRequest>) request.argument;
                    onCompleted = obtainMessage(EVENT_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST_DONE,
                            request);
                    phone.getSignalStrengthController().clearSignalStrengthUpdateRequest(
                            request.subId, pair.first /*callingUid*/,
                            pair.second /*request*/, onCompleted);
                    break;
                }
                case EVENT_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    request.result = ar.exception != null ? ar.exception : true;
                    notifyRequester(request);
                    break;
                }

                case CMD_GET_SLICING_CONFIG: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_GET_SLICING_CONFIG_DONE, request);
                    request.phone.getSlicingConfig(onCompleted);
                    break;
                }
                case EVENT_GET_SLICING_CONFIG_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    ResultReceiver result = (ResultReceiver) request.argument;

                    NetworkSlicingConfig slicingConfig = null;
                    Bundle bundle = new Bundle();
                    int resultCode = 0;
                    if (ar.exception != null) {
                        Log.e(LOG_TAG, "Exception retrieving slicing configuration="
                                + ar.exception);
                        resultCode = TelephonyManager.NetworkSlicingException.ERROR_MODEM_ERROR;
                    } else if (ar.result == null) {
                        Log.w(LOG_TAG, "Timeout Waiting for slicing configuration!");
                        resultCode = TelephonyManager.NetworkSlicingException.ERROR_TIMEOUT;
                    } else {
                        // use the result as returned
                        resultCode = TelephonyManager.NetworkSlicingException.SUCCESS;
                        slicingConfig = (NetworkSlicingConfig) ar.result;
                    }

                    if (slicingConfig == null) {
                        slicingConfig = new NetworkSlicingConfig();
                    }
                    bundle.putParcelable(TelephonyManager.KEY_SLICING_CONFIG_HANDLE, slicingConfig);
                    result.send(resultCode, bundle);
                    notifyRequester(request);
                    break;
                }

                case CMD_PURCHASE_PREMIUM_CAPABILITY: {
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_PURCHASE_PREMIUM_CAPABILITY_DONE, request);
                    PurchasePremiumCapabilityArgument arg =
                            (PurchasePremiumCapabilityArgument) request.argument;
                    SlicePurchaseController.getInstance(request.phone, mFeatureFlags)
                            .purchasePremiumCapability(arg.capability, onCompleted);
                    break;
                }

                case EVENT_PURCHASE_PREMIUM_CAPABILITY_DONE: {
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    PurchasePremiumCapabilityArgument arg =
                            (PurchasePremiumCapabilityArgument) request.argument;
                    try {
                        int result = (int) ar.result;
                        arg.callback.accept(result);
                        log("purchasePremiumCapability: capability="
                                + TelephonyManager.convertPremiumCapabilityToString(arg.capability)
                                + ", result="
                                + TelephonyManager.convertPurchaseResultToString(result));
                    } catch (RemoteException e) {
                        String logStr = "Purchase premium capability "
                                + TelephonyManager.convertPremiumCapabilityToString(arg.capability)
                                + " failed: " + e;
                        if (DBG) log(logStr);
                        AnomalyReporter.reportAnomaly(
                                UUID.fromString(PURCHASE_PREMIUM_CAPABILITY_ERROR_UUID), logStr);
                    }
                    break;
                }

                case CMD_PREPARE_UNATTENDED_REBOOT:
                    request = (MainThreadRequest) msg.obj;
                    request.result =
                            UiccController.getInstance().getPinStorage()
                                    .prepareUnattendedReboot(request.workSource);
                    notifyRequester(request);
                    break;

                default:
                    Log.w(LOG_TAG, "MainThreadHandler: unexpected message code: " + msg.what);
                    break;
            }
        }

        private void notifyRequester(MainThreadRequest request) {
            synchronized (request) {
                request.notifyAll();
            }
        }

        private void handleNullReturnEvent(Message msg, String command) {
            AsyncResult ar = (AsyncResult) msg.obj;
            MainThreadRequest request = (MainThreadRequest) ar.userObj;
            if (ar.exception == null) {
                request.result = true;
            } else {
                request.result = false;
                if (ar.exception instanceof CommandException) {
                    loge(command + ": CommandException: " + ar.exception);
                } else {
                    loge(command + ": Unknown exception");
                }
            }
            notifyRequester(request);
        }
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument) {
        return sendRequest(command, argument, SubscriptionManager.INVALID_SUBSCRIPTION_ID, null,
                null, -1 /*timeoutInMs*/);
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument, WorkSource workSource) {
        return sendRequest(command, argument,  SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                null, workSource, -1 /*timeoutInMs*/);
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument, Integer subId) {
        return sendRequest(command, argument, subId, null, null, -1 /*timeoutInMs*/);
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete for at most {@code timeoutInMs}, and returns the result
     * if not timeout or null otherwise.
     * @see #sendRequestAsync
     */
    private @Nullable Object sendRequest(int command, Object argument, Integer subId,
            long timeoutInMs) {
        return sendRequest(command, argument, subId, null, null, timeoutInMs);
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument, int subId, WorkSource workSource) {
        return sendRequest(command, argument, subId, null, workSource, -1 /*timeoutInMs*/);
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
     */
    private Object sendRequest(int command, Object argument, Phone phone, WorkSource workSource) {
        return sendRequest(command, argument, SubscriptionManager.INVALID_SUBSCRIPTION_ID, phone,
                workSource, -1 /*timeoutInMs*/);
    }

    /**
     * Posts the specified command to be executed on the main thread. If {@code timeoutInMs} is
     * negative, waits for the request to complete, and returns the result. Otherwise, wait for
     * maximum of {@code timeoutInMs} milliseconds, interrupt and return null.
     * @see #sendRequestAsync
     */
    private @Nullable Object sendRequest(int command, Object argument, Integer subId, Phone phone,
            WorkSource workSource, long timeoutInMs) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread.");
        }

        MainThreadRequest request = null;
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && phone != null) {
            throw new IllegalArgumentException("subId and phone cannot both be specified!");
        } else if (phone != null) {
            request = new MainThreadRequest(argument, phone, workSource);
        } else {
            request = new MainThreadRequest(argument, subId, workSource);
        }

        Message msg = mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();


        synchronized (request) {
            if (timeoutInMs >= 0) {
                // Wait for at least timeoutInMs before returning null request result
                long now = SystemClock.elapsedRealtime();
                long deadline = now + timeoutInMs;
                while (request.result == null && now < deadline) {
                    try {
                        request.wait(deadline - now);
                    } catch (InterruptedException e) {
                        // Do nothing, go back and check if request is completed or timeout
                    } finally {
                        now = SystemClock.elapsedRealtime();
                    }
                }
            } else {
                // Wait for the request to complete
                while (request.result == null) {
                    try {
                        request.wait();
                    } catch (InterruptedException e) {
                        // Do nothing, go back and wait until the request is complete
                    }
                }
            }
        }
        if (request.result == null) {
            Log.wtf(LOG_TAG,
                    "sendRequest: Blocking command timed out. Something has gone terribly wrong.");
        }
        return request.result;
    }

    /**
     * Asynchronous ("fire and forget") version of sendRequest():
     * Posts the specified command to be executed on the main thread, and
     * returns immediately.
     * @see #sendRequest
     */
    private void sendRequestAsync(int command) {
        mMainThreadHandler.sendEmptyMessage(command);
    }

    /**
     * Same as {@link #sendRequestAsync(int)} except it takes an argument.
     * @see {@link #sendRequest(int)}
     */
    private void sendRequestAsync(int command, Object argument) {
        sendRequestAsync(command, argument, null, null);
    }

    /**
     * Same as {@link #sendRequestAsync(int,Object)} except it takes a Phone and WorkSource.
     * @see {@link #sendRequest(int,Object)}
     */
    private void sendRequestAsync(
            int command, Object argument, Phone phone, WorkSource workSource) {
        MainThreadRequest request = new MainThreadRequest(argument, phone, workSource);
        Message msg = mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();
    }

    /**
     * Initialize the singleton PhoneInterfaceManager instance.
     * This is only done once, at startup, from PhoneApp.onCreate().
     */
    /* package */ static PhoneInterfaceManager init(PhoneGlobals app, FeatureFlags featureFlags) {
        synchronized (PhoneInterfaceManager.class) {
            if (sInstance == null) {
                sInstance = new PhoneInterfaceManager(app, featureFlags);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /** Private constructor; @see init() */
    private PhoneInterfaceManager(PhoneGlobals app, FeatureFlags featureFlags) {
        mApp = app;
        mFeatureFlags = featureFlags;
        mCM = PhoneGlobals.getInstance().mCM;
        mImsResolver = ImsResolver.getInstance();
        mSatelliteController = SatelliteController.getInstance();
        mUserManager = (UserManager) app.getSystemService(Context.USER_SERVICE);
        mAppOps = (AppOpsManager)app.getSystemService(Context.APP_OPS_SERVICE);
        mMainThreadHandler = new MainThreadHandler();
        mTelephonySharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApp);
        mNetworkScanRequestTracker = new NetworkScanRequestTracker();
        mPhoneConfigurationManager = PhoneConfigurationManager.getInstance();
        mRadioInterfaceCapabilities = RadioInterfaceCapabilityController.getInstance();
        mNotifyUserActivity = new AtomicBoolean(false);
        mPackageManager = app.getPackageManager();
        mSatelliteAccessController = SatelliteAccessController.getOrCreateInstance(
                getDefaultPhone().getContext(), featureFlags);
        PropertyInvalidatedCache.invalidateCache(TelephonyManager.CACHE_KEY_PHONE_ACCOUNT_TO_SUBID);
        publish();
        CarrierAllowListInfo.loadInstance(mApp);

        // Create the SatelliteEntitlementController singleton, for using the get the
        // entitlementStatus for satellite service.
        SatelliteEntitlementController.make(mApp, mFeatureFlags);
    }

    @VisibleForTesting
    public SharedPreferences getSharedPreferences() {
        return mTelephonySharedPreferences;
    }

    /**
     * Get the default phone for this device.
     */
    @VisibleForTesting
    public Phone getDefaultPhone() {
        Phone thePhone = getPhone(getDefaultSubscription());
        return (thePhone != null) ? thePhone : PhoneFactory.getDefaultPhone();
    }

    private void publish() {
        if (DBG) log("publish: " + this);

        TelephonyFrameworkInitializer
                .getTelephonyServiceManager()
                .getTelephonyServiceRegisterer()
                .register(this);
    }

    private Phone getPhoneFromRequest(MainThreadRequest request) {
        if (request.phone != null) {
            return request.phone;
        } else {
            return getPhoneFromSubId(request.subId);
        }
    }

    private Phone getPhoneFromSubId(int subId) {
        return (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                ? getDefaultPhone() : getPhone(subId);
    }

    /**
     * Get phone object associated with a subscription.
     * Return default phone if phone object associated with subscription is null
     * @param subId - subscriptionId
     * @return phone object associated with a subscription or default phone if null.
     */
    private @NonNull Phone getPhoneFromSubIdOrDefault(int subId) {
        Phone phone = getPhoneFromSubId(subId);
        if (phone == null) {
            loge("Called with invalid subId: " + subId + ". Retrying with default phone.");
            phone = getDefaultPhone();
        }
        return phone;
    }

    @Nullable
    private UiccPort getUiccPortFromRequest(@NonNull MainThreadRequest request) {
        Phone phone = getPhoneFromRequest(request);
        return phone == null ? null :
                UiccController.getInstance().getUiccPort(phone.getPhoneId());
    }

    /**
     * @param subId The sub Id that associates the phone. If the device has no active SIM, passing
     *              in {@link SubscriptionManager#DEFAULT_SUBSCRIPTION_ID} or any sub <=
     *              {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID} will return {@code null}.
     * @return The Phone associated the sub Id
     */
    private @Nullable Phone getPhone(int subId) {
        return PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
    }

    private void sendEraseModemConfig(@NonNull Phone phone) {
        Boolean success = (Boolean) sendRequest(CMD_ERASE_MODEM_CONFIG, null);
        if (DBG) log("eraseModemConfig:" + ' ' + (success ? "ok" : "fail"));
    }

    private void sendEraseDataInSharedPreferences(@NonNull Phone phone) {
        Boolean success = (Boolean) sendRequest(CMD_ERASE_DATA_SHARED_PREFERENCES, null);
        if (DBG) log("eraseDataInSharedPreferences:" + ' ' + (success ? "ok" : "fail"));
    }

    private boolean isImsAvailableOnDevice() {
        PackageManager pm = getDefaultPhone().getContext().getPackageManager();
        if (pm == null) {
            // For some reason package manger is not available.. This will fail internally anyway,
            // so do not throw error and allow.
            return true;
        }
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS, 0);
    }

    public void dial(String number) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "dial");

        dialForSubscriber(getPreferredVoiceSubscription(), number);
    }

    public void dialForSubscriber(int subId, String number) {
        if (DBG) log("dial: " + number);
        // No permission check needed here: This is just a wrapper around the
        // ACTION_DIAL intent, which is available to any app since it puts up
        // the UI before it does anything.

        final long identity = Binder.clearCallingIdentity();
        try {
            String url = createTelUrl(number);
            if (url == null) {
                return;
            }

            // PENDING: should we just silently fail if phone is offhook or ringing?
            PhoneConstants.State state = mCM.getState(subId);
            if (state != PhoneConstants.State.OFFHOOK && state != PhoneConstants.State.RINGING) {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mApp.startActivity(intent);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public void call(String callingPackage, String number) {
        callForSubscriber(getPreferredVoiceSubscription(), callingPackage, number);
    }

    public void callForSubscriber(int subId, String callingPackage, String number) {
        if (DBG) log("call: " + number);

        // This is just a wrapper around the ACTION_CALL intent, but we still
        // need to do a permission check since we're calling startActivity()
        // from the context of the phone app.
        enforceCallPermission();

        if (mAppOps.noteOp(AppOpsManager.OPSTR_CALL_PHONE, Binder.getCallingUid(), callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "call");

        final long identity = Binder.clearCallingIdentity();
        try {
            String url = createTelUrl(number);
            if (url == null) {
                return;
            }

            boolean isValid = false;
            final List<SubscriptionInfo> slist = getActiveSubscriptionInfoListPrivileged();
            if (slist != null) {
                for (SubscriptionInfo subInfoRecord : slist) {
                    if (subInfoRecord.getSubscriptionId() == subId) {
                        isValid = true;
                        break;
                    }
                }
            }
            if (!isValid) {
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
            intent.putExtra(SUBSCRIPTION_KEY, subId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mApp.startActivity(intent);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean supplyPinForSubscriber(int subId, String pin) {
        int [] resultArray = supplyPinReportResultForSubscriber(subId, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    public boolean supplyPukForSubscriber(int subId, String puk, String pin) {
        int [] resultArray = supplyPukReportResultForSubscriber(subId, puk, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    public int[] supplyPinReportResultForSubscriber(int subId, String pin) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "supplyPinReportResultForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            final UnlockSim checkSimPin = new UnlockSim(phone.getPhoneId(), phone.getIccCard());
            checkSimPin.start();
            return checkSimPin.unlockSim(null, pin);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public int[] supplyPukReportResultForSubscriber(int subId, String puk, String pin) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "supplyPukForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            final UnlockSim checkSimPuk = new UnlockSim(phone.getPhoneId(), phone.getIccCard());
            checkSimPuk.start();
            return checkSimPuk.unlockSim(puk, pin);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Helper thread to turn async call to SimCard#supplyPin into
     * a synchronous one.
     */
    private static class UnlockSim extends Thread {

        private final IccCard mSimCard;
        private final int mPhoneId;

        private boolean mDone = false;
        private int mResult = PhoneConstants.PIN_GENERAL_FAILURE;
        private int mRetryCount = -1;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int SUPPLY_PIN_COMPLETE = 100;

        UnlockSim(int phoneId, IccCard simCard) {
            mPhoneId = phoneId;
            mSimCard = simCard;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (UnlockSim.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case SUPPLY_PIN_COMPLETE:
                                Log.d(LOG_TAG, "SUPPLY_PIN_COMPLETE");
                                synchronized (UnlockSim.this) {
                                    mRetryCount = msg.arg1;
                                    if (ar.exception != null) {
                                        CommandException.Error error = null;
                                        if (ar.exception instanceof CommandException) {
                                            error = ((CommandException) (ar.exception))
                                                    .getCommandError();
                                        }
                                        if (error == CommandException.Error.PASSWORD_INCORRECT) {
                                            mResult = PhoneConstants.PIN_PASSWORD_INCORRECT;
                                        } else if (error == CommandException.Error.ABORTED) {
                                            /* When UiccCardApp dispose, handle message and return
                                             exception */
                                            mResult = PhoneConstants.PIN_OPERATION_ABORTED;
                                        } else {
                                            mResult = PhoneConstants.PIN_GENERAL_FAILURE;
                                        }
                                    } else {
                                        mResult = PhoneConstants.PIN_RESULT_SUCCESS;
                                    }
                                    mDone = true;
                                    UnlockSim.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                UnlockSim.this.notifyAll();
            }
            Looper.loop();
        }

        /*
         * Use PIN or PUK to unlock SIM card
         *
         * If PUK is null, unlock SIM card with PIN
         *
         * If PUK is not null, unlock SIM card with PUK and set PIN code
         *
         * Besides, since it is reused in class level, the thread's looper will be stopped to avoid
         * its thread leak.
         */
        synchronized int[] unlockSim(String puk, String pin) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SUPPLY_PIN_COMPLETE);

            if (puk == null) {
                mSimCard.supplyPin(pin, callback);
            } else {
                mSimCard.supplyPuk(puk, pin, callback);
            }

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "done");
            int[] resultArray = new int[2];
            resultArray[0] = mResult;
            resultArray[1] = mRetryCount;

            if (mResult == PhoneConstants.PIN_RESULT_SUCCESS && pin.length() > 0) {
                UiccController.getInstance().getPinStorage().storePin(pin, mPhoneId);
            }
            // This instance is no longer reused, so quit its thread's looper.
            mHandler.getLooper().quitSafely();

            return resultArray;
        }
    }

    /**
     * This method has been removed due to privacy and stability concerns.
     */
    @Override
    public void updateServiceLocation() {
        Log.e(LOG_TAG, "Call to unsupported method updateServiceLocation()");
        return;
    }

    @Override
    public void updateServiceLocationWithPackageName(String callingPackage) {
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        final int targetSdk = TelephonyPermissions.getTargetSdk(mApp, callingPackage);
        if (targetSdk > android.os.Build.VERSION_CODES.R) {
            // Callers targeting S have no business invoking this method.
            return;
        }

        LocationAccessPolicy.LocationPermissionResult locationResult =
                LocationAccessPolicy.checkLocationPermission(mApp,
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(callingPackage)
                                .setCallingFeatureId(null)
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("updateServiceLocation")
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.BASE)
                                .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                .build());
        // Apps that lack location permission have no business calling this method;
        // however, because no permission was declared in the public API, denials must
        // all be "soft".
        switch (locationResult) {
            case DENIED_HARD: /* fall through */
            case DENIED_SOFT:
                return;
        }

        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            getPhoneFromSubIdOrDefault(getDefaultSubscription()).updateServiceLocation(workSource);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Deprecated
    @Override
    public boolean isRadioOn(String callingPackage) {
        return isRadioOnWithFeature(callingPackage, null);
    }


    @Override
    public boolean isRadioOnWithFeature(String callingPackage, String callingFeatureId) {
        return isRadioOnForSubscriberWithFeature(getDefaultSubscription(), callingPackage,
                callingFeatureId);
    }

    @Deprecated
    @Override
    public boolean isRadioOnForSubscriber(int subId, String callingPackage) {
        return isRadioOnForSubscriberWithFeature(subId, callingPackage, null);
    }

    @Override
    public boolean isRadioOnForSubscriberWithFeature(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "isRadioOnForSubscriber")) {
            return false;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "isRadioOnWithFeature");

        final long identity = Binder.clearCallingIdentity();
        try {
            return isRadioOnForSubscriber(subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isRadioOnForSubscriber(int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getServiceState().getState() != ServiceState.STATE_POWER_OFF;
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public void toggleRadioOnOff() {
        toggleRadioOnOffForSubscriber(getDefaultSubscription());
    }

    public void toggleRadioOnOffForSubscriber(int subId) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "toggleRadioOnOffForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                phone.setRadioPower(!isRadioOnForSubscriber(subId));
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean setRadio(boolean turnOn) {
        return setRadioForSubscriber(getDefaultSubscription(), turnOn);
    }

    public boolean setRadioForSubscriber(int subId, boolean turnOn) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null) {
                return false;
            }
            if ((phone.getServiceState().getState() != ServiceState.STATE_POWER_OFF) != turnOn) {
                toggleRadioOnOffForSubscriber(subId);
            }
            return true;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean needMobileRadioShutdown() {
        enforceReadPrivilegedPermission("needMobileRadioShutdown");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "needMobileRadioShutdown");

        /*
         * If any of the Radios are available, it will need to be
         * shutdown. So return true if any Radio is available.
         */
        final long identity = Binder.clearCallingIdentity();
        try {
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                Phone phone = PhoneFactory.getPhone(i);
                if (phone != null && phone.isRadioAvailable()) return true;
            }
            logv(TelephonyManager.getDefault().getPhoneCount() + " Phones are shutdown.");
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void shutdownMobileRadios() {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "shutdownMobileRadios");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                logv("Shutting down Phone " + i);
                shutdownRadioUsingPhoneId(i);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void shutdownRadioUsingPhoneId(int phoneId) {
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null && phone.isRadioAvailable()) {
            phone.shutdownRadio();
        }
    }

    public boolean setRadioPower(boolean turnOn) {
        enforceModifyPermission();

        if (!turnOn) {
            log("setRadioPower off: callingPackage=" + getCurrentPackageName());
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone defaultPhone = PhoneFactory.getDefaultPhone();
            if (defaultPhone != null) {
                defaultPhone.setRadioPower(turnOn);
                return true;
            } else {
                loge("There's no default phone.");
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean setRadioPowerForSubscriber(int subId, boolean turnOn) {
        enforceModifyPermission();

        if (!turnOn) {
            log("setRadioPowerForSubscriber off: subId=" + subId
                    + ",callingPackage=" + getCurrentPackageName());
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                phone.setRadioPower(turnOn);
                return true;
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Vote on powering off the radio for a reason. The radio will be turned on only when there is
     * no reason to power it off. When any of the voters want to power it off, it will be turned
     * off. In case of emergency, the radio will be turned on even if there are some reasons for
     * powering it off, and these radio off votes will be cleared.
     * Multiple apps can vote for the same reason and the last vote will take effect. Each app is
     * responsible for its vote. A powering-off vote of a reason will be maintained until it is
     * cleared by calling {@link clearRadioPowerOffForReason} for that reason, or an emergency call
     * is made, or the device is rebooted. When an app comes backup from a crash, it needs to make
     * sure if its vote is as expected. An app can use the API {@link getRadioPowerOffReasons} to
     * check its vote.
     *
     * @param subId The subscription ID.
     * @param reason The reason for powering off radio.
     * @return true on success and false on failure.
     */
    public boolean requestRadioPowerOffForReason(int subId,
            @TelephonyManager.RadioPowerReason int reason) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "requestRadioPowerOffForReason");

        log("requestRadioPowerOffForReason: subId=" + subId
                + ",reason=" + reason + ",callingPackage=" + getCurrentPackageName());
        final long identity = Binder.clearCallingIdentity();
        try {
            boolean result = false;
            for (Phone phone : PhoneFactory.getPhones()) {
                result = true;
                phone.setRadioPowerForReason(false, reason);
            }
            if (!result) {
                loge("requestRadioPowerOffForReason: no phone exists");
            }
            return result;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Remove the vote on powering off the radio for a reason, as requested by
     * {@link requestRadioPowerOffForReason}.
     *
     * @param subId The subscription ID.
     * @param reason The reason for powering off radio.
     * @return true on success and false on failure.
     */
    public boolean clearRadioPowerOffForReason(int subId,
            @TelephonyManager.RadioPowerReason int reason) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "clearRadioPowerOffForReason");

        final long identity = Binder.clearCallingIdentity();
        try {
            boolean result = false;
            for (Phone phone : PhoneFactory.getPhones()) {
                result = true;
                phone.setRadioPowerForReason(true, reason);
            }
            if (!result) {
                loge("clearRadioPowerOffForReason: no phone exists");
            }
            return result;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get reasons for powering off radio, as requested by {@link requestRadioPowerOffForReason}.
     *
     * @param subId The subscription ID.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     * @return List of reasons for powering off radio.
     */
    public List getRadioPowerOffReasons(int subId, String callingPackage, String callingFeatureId) {
        enforceReadPrivilegedPermission("getRadioPowerOffReasons");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getRadioPowerOffReasons");

        final long identity = Binder.clearCallingIdentity();
        List result = new ArrayList();
        try {
            if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mApp, subId,
                    callingPackage, callingFeatureId, "getRadioPowerOffReasons")) {
                return result;
            }

            final Phone phone = getPhoneFromSubIdOrDefault(subId);
            if (phone != null) {
                result.addAll(phone.getRadioPowerOffReasons());
            } else {
                loge("getRadioPowerOffReasons: phone is null");
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return result;
    }

    // FIXME: subId version needed
    @Override
    public boolean enableDataConnectivity(String callingPackage) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_DATA, "enableDataConnectivity");

        final long identity = Binder.clearCallingIdentity();
        try {
            int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            final Phone phone = getPhone(subId);
            if (phone != null) {
                phone.getDataSettingsManager().setDataEnabled(
                        TelephonyManager.DATA_ENABLED_REASON_USER, true, callingPackage);
                return true;
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    // FIXME: subId version needed
    @Override
    public boolean disableDataConnectivity(String callingPackage) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_DATA, "disableDataConnectivity");

        final long identity = Binder.clearCallingIdentity();
        try {
            int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            final Phone phone = getPhone(subId);
            if (phone != null) {
                phone.getDataSettingsManager().setDataEnabled(
                        TelephonyManager.DATA_ENABLED_REASON_USER, false, callingPackage);
                return true;
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isDataConnectivityPossible(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isDataConnectivityPossible");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.isDataAllowed();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean handlePinMmi(String dialString) {
        return handlePinMmiForSubscriber(getDefaultSubscription(), dialString);
    }

    public void handleUssdRequest(int subId, String ussdRequest, ResultReceiver wrappedCallback) {
        enforceCallPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "handleUssdRequest");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                return;
            }
            Pair<String, ResultReceiver> ussdObject = new Pair(ussdRequest, wrappedCallback);
            sendRequest(CMD_HANDLE_USSD_REQUEST, ussdObject, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    };

    public boolean handlePinMmiForSubscriber(int subId, String dialString) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "handlePinMmiForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                return false;
            }
            return (Boolean) sendRequest(CMD_HANDLE_PIN_MMI, dialString, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @deprecated  This method is deprecated and is only being kept due to an UnsupportedAppUsage
     * tag on getCallState Binder call.
     */
    @Deprecated
    @Override
    public int getCallState() {
        if (CompatChanges.isChangeEnabled(
                TelecomManager.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION,
                Binder.getCallingUid())) {
            // Do not allow this API to be called on API version 31+, it should only be
            // called on old apps using this Binder call directly.
            throw new SecurityException("This method can only be used for applications "
                    + "targeting API version 30 or less.");
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhoneFromSubIdOrDefault(getDefaultSubscription());
            return PhoneConstantConversions.convertCallState(phone.getState());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCallStateForSubscription(int subId, String callingPackage, String featureId) {
        if (CompatChanges.isChangeEnabled(
                TelecomManager.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION,
                Binder.getCallingUid())) {
            // Check READ_PHONE_STATE for API version 31+
            if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mApp, subId, callingPackage,
                    featureId, "getCallStateForSubscription")) {
                throw new SecurityException("getCallState requires READ_PHONE_STATE for apps "
                        + "targeting API level 31+.");
            }
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "getCallStateForSubscription");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            return phone == null ? TelephonyManager.CALL_STATE_IDLE :
                    PhoneConstantConversions.convertCallState(phone.getState());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getDataState() {
        return getDataStateForSubId(SubscriptionManager.getDefaultDataSubscriptionId());
    }

    @Override
    public int getDataStateForSubId(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "getDataStateForSubId");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getDataNetworkController().getInternetDataNetworkState();
            } else {
                return PhoneConstantConversions.convertDataState(
                        PhoneConstants.DataState.DISCONNECTED);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public @DataActivityType int getDataActivity() {
        return getDataActivityForSubId(SubscriptionManager.getDefaultDataSubscriptionId());
    }

    @Override
    public @DataActivityType int getDataActivityForSubId(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "getDataActivityForSubId");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getDataActivityState();
            } else {
                return TelephonyManager.DATA_ACTIVITY_NONE;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public CellIdentity getCellLocation(String callingPackage, String callingFeatureId) {
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        LocationAccessPolicy.LocationPermissionResult locationResult =
                LocationAccessPolicy.checkLocationPermission(mApp,
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(callingPackage)
                                .setCallingFeatureId(callingFeatureId)
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("getCellLocation")
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.BASE)
                                .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                .build());
        switch (locationResult) {
            case DENIED_HARD:
                throw new SecurityException("Not allowed to access cell location");
            case DENIED_SOFT:
                return (getDefaultPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA)
                        ? new CellIdentityCdma() : new CellIdentityGsm();
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getCellLocation");

        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG_LOC) log("getCellLocation: is active user");
            int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            return (CellIdentity) sendRequest(CMD_GET_CELL_LOCATION, workSource, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getNetworkCountryIsoForPhone(int phoneId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getNetworkCountryIsoForPhone");

        // Reporting the correct network country is ambiguous when IWLAN could conflict with
        // registered cell info, so return a NULL country instead.
        final long identity = Binder.clearCallingIdentity();
        try {
            if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                // Get default phone in this case.
                phoneId = SubscriptionManager.DEFAULT_PHONE_INDEX;
            }
            final int subId = SubscriptionManager.getSubscriptionId(phoneId);
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone == null) return "";
            ServiceStateTracker sst = phone.getServiceStateTracker();
            if (sst == null) return "";
            LocaleTracker lt = sst.getLocaleTracker();
            if (lt == null) return "";
            return lt.getCurrentCountry();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * This method was removed due to potential issues caused by performing partial
     * updates of service state, and lack of a credible use case.
     *
     * This has the ability to break the telephony implementation by disabling notification of
     * changes in device connectivity. DO NOT USE THIS!
     */
    @Override
    public void enableLocationUpdates() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
    }

    /**
     * This method was removed due to potential issues caused by performing partial
     * updates of service state, and lack of a credible use case.
     *
     * This has the ability to break the telephony implementation by disabling notification of
     * changes in device connectivity. DO NOT USE THIS!
     */
    @Override
    public void disableLocationUpdates() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<NeighboringCellInfo> getNeighboringCellInfo(String callingPackage,
            String callingFeatureId) {
        try {
            mApp.getSystemService(AppOpsManager.class)
                    .checkPackage(Binder.getCallingUid(), callingPackage);
        } catch (SecurityException e) {
            EventLog.writeEvent(0x534e4554, "190619791", Binder.getCallingUid());
            throw e;
        }

        final int targetSdk = TelephonyPermissions.getTargetSdk(mApp, callingPackage);
        if (targetSdk >= android.os.Build.VERSION_CODES.Q) {
            throw new SecurityException(
                    "getNeighboringCellInfo() is unavailable to callers targeting Q+ SDK levels.");
        }

        if (mAppOps.noteOp(AppOpsManager.OPSTR_NEIGHBORING_CELLS, Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return null;
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getNeighboringCellInfo");

        if (DBG_LOC) log("getNeighboringCellInfo: is active user");

        List<CellInfo> info = getAllCellInfo(callingPackage, callingFeatureId);
        if (info == null) return null;

        List<NeighboringCellInfo> neighbors = new ArrayList<NeighboringCellInfo>();
        for (CellInfo ci : info) {
            if (ci instanceof CellInfoGsm) {
                neighbors.add(new NeighboringCellInfo((CellInfoGsm) ci));
            } else if (ci instanceof CellInfoWcdma) {
                neighbors.add(new NeighboringCellInfo((CellInfoWcdma) ci));
            }
        }
        return (neighbors.size()) > 0 ? neighbors : null;
    }

    private List<CellInfo> getCachedCellInfo() {
        List<CellInfo> cellInfos = new ArrayList<CellInfo>();
        for (Phone phone : PhoneFactory.getPhones()) {
            List<CellInfo> info = phone.getAllCellInfo();
            if (info != null) cellInfos.addAll(info);
        }
        return cellInfos;
    }

    @Override
    public List<CellInfo> getAllCellInfo(String callingPackage, String callingFeatureId) {
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        LocationAccessPolicy.LocationPermissionResult locationResult =
                LocationAccessPolicy.checkLocationPermission(mApp,
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(callingPackage)
                                .setCallingFeatureId(callingFeatureId)
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("getAllCellInfo")
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.BASE)
                                .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                .build());
        switch (locationResult) {
            case DENIED_HARD:
                throw new SecurityException("Not allowed to access cell info");
            case DENIED_SOFT:
                return new ArrayList<>();
        }

        final int targetSdk = TelephonyPermissions.getTargetSdk(mApp, callingPackage);
        if (targetSdk >= android.os.Build.VERSION_CODES.Q) {
            return getCachedCellInfo();
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getAllCellInfo");

        if (DBG_LOC) log("getAllCellInfo: is active user");
        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            List<CellInfo> cellInfos = new ArrayList<CellInfo>();
            for (Phone phone : PhoneFactory.getPhones()) {
                final List<CellInfo> info = (List<CellInfo>) sendRequest(
                        CMD_GET_ALL_CELL_INFO, null, phone, workSource);
                if (info != null) cellInfos.addAll(info);
            }
            return cellInfos;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void requestCellInfoUpdate(int subId, ICellInfoCallback cb, String callingPackage,
            String callingFeatureId) {
        requestCellInfoUpdateInternal(subId, cb, callingPackage, callingFeatureId,
                getWorkSource(Binder.getCallingUid()));
    }

    @Override
    public void requestCellInfoUpdateWithWorkSource(int subId, ICellInfoCallback cb,
            String callingPackage, String callingFeatureId, WorkSource workSource) {
        enforceModifyPermission();
        requestCellInfoUpdateInternal(subId, cb, callingPackage, callingFeatureId, workSource);
    }

    private void requestCellInfoUpdateInternal(int subId, ICellInfoCallback cb,
            String callingPackage, String callingFeatureId, WorkSource workSource) {
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        LocationAccessPolicy.LocationPermissionResult locationResult =
                LocationAccessPolicy.checkLocationPermission(mApp,
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(callingPackage)
                                .setCallingFeatureId(callingFeatureId)
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("requestCellInfoUpdate")
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.BASE)
                                .setMinSdkVersionForFine(Build.VERSION_CODES.BASE)
                                .build());
        switch (locationResult) {
            case DENIED_HARD:
                if (TelephonyPermissions
                        .getTargetSdk(mApp, callingPackage) < Build.VERSION_CODES.Q) {
                    // Safetynet logging for b/154934934
                    EventLog.writeEvent(0x534e4554, "154934934", Binder.getCallingUid());
                }
                throw new SecurityException("Not allowed to access cell info");
            case DENIED_SOFT:
                if (TelephonyPermissions
                        .getTargetSdk(mApp, callingPackage) < Build.VERSION_CODES.Q) {
                    // Safetynet logging for b/154934934
                    EventLog.writeEvent(0x534e4554, "154934934", Binder.getCallingUid());
                }
                try {
                    cb.onCellInfo(new ArrayList<CellInfo>());
                } catch (RemoteException re) {
                    // Drop without consequences
                }
                return;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "requestCellInfoUpdateInternal");

        final Phone phone = getPhoneFromSubId(subId);
        if (phone == null) throw new IllegalArgumentException("Invalid Subscription Id: " + subId);

        sendRequestAsync(CMD_REQUEST_CELL_INFO_UPDATE, cb, phone, workSource);
    }

    @Override
    public void setCellInfoListRate(int rateInMillis, int subId) {
        enforceModifyPermission();
        WorkSource workSource = getWorkSource(Binder.getCallingUid());

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) {
                getDefaultPhone().setCellInfoListRate(rateInMillis, workSource);
            } else {
                phone.setCellInfoListRate(rateInMillis, workSource);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getImeiForSlot(int slotIndex, String callingPackage, String callingFeatureId) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone == null) {
            return null;
        }
        int subId = phone.getSubId();
        enforceCallingPackage(callingPackage, Binder.getCallingUid(), "getImeiForSlot");
        if (!TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mApp, subId,
                callingPackage, callingFeatureId, "getImeiForSlot")) {
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_GSM, "getImeiForSlot");

        final long identity = Binder.clearCallingIdentity();
        try {
            return phone.getImei();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getPrimaryImei(String callingPackage, String callingFeatureId) {
        enforceCallingPackage(callingPackage, Binder.getCallingUid(), "getPrimaryImei");
        if (!checkCallingOrSelfReadDeviceIdentifiersForAnySub(mApp, callingPackage,
                callingFeatureId, "getPrimaryImei")) {
            throw new SecurityException("Caller does not have permission");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_GSM, "getPrimaryImei");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone : PhoneFactory.getPhones()) {
                if (phone.getImeiType() == Phone.IMEI_TYPE_PRIMARY) {
                    return phone.getImei();
                }
            }
            throw new UnsupportedOperationException("Operation not supported");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getTypeAllocationCodeForSlot(int slotIndex) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_GSM, "getTypeAllocationCodeForSlot");

        Phone phone = PhoneFactory.getPhone(slotIndex);
        String tac = null;
        if (phone != null) {
            String imei = phone.getImei();
            try {
                tac = imei == null ? null : imei.substring(0, TYPE_ALLOCATION_CODE_LENGTH);
            } catch (IndexOutOfBoundsException e) {
                Log.e(LOG_TAG, "IMEI length shorter than upper index.");
                return null;
            }
        }
        return tac;
    }

    @Override
    public String getMeidForSlot(int slotIndex, String callingPackage, String callingFeatureId) {
        try {
            mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        } catch (SecurityException se) {
            EventLog.writeEvent(0x534e4554, "186530496", Binder.getCallingUid());
            throw new SecurityException("Package " + callingPackage + " does not belong to "
                    + Binder.getCallingUid());
        }
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone == null) {
            return null;
        }

        int subId = phone.getSubId();
        if (!TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mApp, subId,
                callingPackage, callingFeatureId, "getMeidForSlot")) {
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CDMA, "getMeidForSlot");

        final long identity = Binder.clearCallingIdentity();
        try {
            return phone.getMeid();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getManufacturerCodeForSlot(int slotIndex) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "getManufacturerCodeForSlot");

        Phone phone = PhoneFactory.getPhone(slotIndex);
        String manufacturerCode = null;
        if (phone != null) {
            String meid = phone.getMeid();
            try {
                manufacturerCode =
                        meid == null ? null : meid.substring(0, MANUFACTURER_CODE_LENGTH);
            } catch (IndexOutOfBoundsException e) {
                Log.e(LOG_TAG, "MEID length shorter than upper index.");
                return null;
            }
        }
        return manufacturerCode;
    }

    @Override
    public String getDeviceSoftwareVersionForSlot(int slotIndex, String callingPackage,
            String callingFeatureId) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone == null) {
            return null;
        }
        int subId = phone.getSubId();
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getDeviceSoftwareVersionForSlot")) {
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY, "getDeviceSoftwareVersionForSlot");

        final long identity = Binder.clearCallingIdentity();
        try {
            return phone.getDeviceSvn();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getSubscriptionCarrierId(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getSubscriptionCarrierId");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            return phone == null ? TelephonyManager.UNKNOWN_CARRIER_ID : phone.getCarrierId();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getSubscriptionCarrierName(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getSubscriptionCarrierName");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            return phone == null ? null : phone.getCarrierName();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getSubscriptionSpecificCarrierId(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getSubscriptionSpecificCarrierId");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            return phone == null ? TelephonyManager.UNKNOWN_CARRIER_ID
                    : phone.getSpecificCarrierId();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getSubscriptionSpecificCarrierName(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "getSubscriptionSpecificCarrierName");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            return phone == null ? null : phone.getSpecificCarrierName();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCarrierIdFromMccMnc(int slotIndex, String mccmnc, boolean isSubscriptionMccMnc) {
        if (!isSubscriptionMccMnc) {
            enforceReadPrivilegedPermission("getCarrierIdFromMccMnc");
        }
        final Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone == null) {
            return TelephonyManager.UNKNOWN_CARRIER_ID;
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getCarrierIdFromMccMnc");

        final long identity = Binder.clearCallingIdentity();
        try {
            return CarrierResolver.getCarrierIdFromMccMnc(phone.getContext(), mccmnc);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    //
    // Internal helper methods.
    //

    /**
     * Make sure the caller is the calling package itself
     *
     * @throws SecurityException if the caller is not the calling package
     */
    private void enforceCallingPackage(String callingPackage, int callingUid, String message) {
        int packageUid = -1;
        PackageManager pm = mApp.getBaseContext().createContextAsUser(
                UserHandle.getUserHandleForUid(callingUid), 0).getPackageManager();
        try {
            packageUid = pm.getPackageUid(callingPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            // packageUid is -1
        }
        if (packageUid != callingUid) {
            throw new SecurityException(message + ": Package " + callingPackage
                    + " does not belong to " + callingUid);
        }
    }

    /**
     * Make sure the caller has the MODIFY_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    @VisibleForTesting
    public void enforceModifyPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE, null);
    }

    /**
     * Make sure the caller has the READ_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    @VisibleForTesting
    public void enforceReadPermission() {
        enforceReadPermission(null);
    }

    /**
     * Make sure the caller has the READ_PHONE_STATE permissions.
     *
     * @throws SecurityException if the caller does not have the READ_PHONE_STATE permission.
     */
    @VisibleForTesting
    public void enforceReadPermission(String msg) {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE, msg);
    }

    private void enforceActiveEmergencySessionPermission() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.READ_ACTIVE_EMERGENCY_SESSION, null);
    }

    /**
     * Make sure the caller has the CALL_PHONE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceCallPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE, null);
    }

    private void enforceSettingsPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.NETWORK_SETTINGS, null);
    }

    private void enforceRebootPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.REBOOT, null);
    }

    /**
     * Make sure the caller has SATELLITE_COMMUNICATION permission.
     * @param message - log message to print.
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceSatelliteCommunicationPermission(String message) {
        mApp.enforceCallingOrSelfPermission(permission.SATELLITE_COMMUNICATION, message);
    }

    private String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        return "tel:" + number;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private static void logv(String msg) {
        Log.v(LOG_TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }

    @Override
    public int getActivePhoneType() {
        return getActivePhoneTypeForSlot(getSlotForDefaultSubscription());
    }

    @Override
    public int getActivePhoneTypeForSlot(int slotIndex) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY, "getActivePhoneTypeForSlot");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = PhoneFactory.getPhone(slotIndex);
            if (phone == null) {
                return PhoneConstants.PHONE_TYPE_NONE;
            } else {
                return phone.getPhoneType();
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the CDMA ERI icon index to display
     */
    @Override
    public int getCdmaEriIconIndex(String callingPackage, String callingFeatureId) {
        return getCdmaEriIconIndexForSubscriber(getDefaultSubscription(), callingPackage,
                callingFeatureId);
    }

    @Override
    public int getCdmaEriIconIndexForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getCdmaEriIconIndexForSubscriber")) {
            return -1;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CDMA,
                "getCdmaEriIconIndexForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getCdmaEriIconIndex();
            } else {
                return -1;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    @Override
    public int getCdmaEriIconMode(String callingPackage, String callingFeatureId) {
        return getCdmaEriIconModeForSubscriber(getDefaultSubscription(), callingPackage,
                callingFeatureId);
    }

    @Override
    public int getCdmaEriIconModeForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getCdmaEriIconModeForSubscriber")) {
            return -1;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getCdmaEriIconMode();
            } else {
                return -1;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the CDMA ERI text,
     */
    @Override
    public String getCdmaEriText(String callingPackage, String callingFeatureId) {
        return getCdmaEriTextForSubscriber(getDefaultSubscription(), callingPackage,
                callingFeatureId);
    }

    @Override
    public String getCdmaEriTextForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getCdmaEriIconTextForSubscriber")) {
            return null;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getCdmaEriText();
            } else {
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the CDMA MDN.
     */
    @Override
    public String getCdmaMdn(int subId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "getCdmaMdn");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "getCdmaMdn");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null && phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                return phone.getLine1Number();
            } else {
                loge("getCdmaMdn: no phone found. Invalid subId: " + subId);
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the CDMA MIN.
     */
    @Override
    public String getCdmaMin(int subId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "getCdmaMin");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "getCdmaMin");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null && phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                return phone.getCdmaMin();
            } else {
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void requestNumberVerification(PhoneNumberRange range, long timeoutMillis,
            INumberVerificationCallback callback, String callingPackage) {
        if (mApp.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                != PERMISSION_GRANTED) {
            throw new SecurityException("Caller must hold the MODIFY_PHONE_STATE permission");
        }
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        String authorizedPackage = NumberVerificationManager.getAuthorizedPackage(mApp);
        if (!TextUtils.equals(callingPackage, authorizedPackage)) {
            throw new SecurityException("Calling package must be configured in the device config: "
                    + "calling package: " + callingPackage
                    + ", configured package: " + authorizedPackage);
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "requestNumberVerification");

        if (range == null) {
            throw new NullPointerException("Range must be non-null");
        }

        timeoutMillis = Math.min(timeoutMillis,
                TelephonyManager.getMaxNumberVerificationTimeoutMillis());

        NumberVerificationManager.getInstance().requestVerification(range, callback, timeoutMillis);
    }

    /**
     * Returns true if CDMA provisioning needs to run.
     */
    public boolean needsOtaServiceProvisioning() {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "needsOtaServiceProvisioning");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getDefaultPhone().needsOtaServiceProvisioning();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the voice mail number of a given subId.
     */
    @Override
    public boolean setVoiceMailNumber(int subId, String alphaTag, String number) {
        TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(
                mApp, subId, "setVoiceMailNumber");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "setVoiceMailNumber");

        final long identity = Binder.clearCallingIdentity();
        try {
            Boolean success = (Boolean) sendRequest(CMD_SET_VOICEMAIL_NUMBER,
                    new Pair<String, String>(alphaTag, number), new Integer(subId));
            return success;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public Bundle getVisualVoicemailSettings(String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        TelecomManager tm = mApp.getSystemService(TelecomManager.class);
        String systemDialer = tm.getSystemDialerPackage();
        if (!TextUtils.equals(callingPackage, systemDialer)) {
            throw new SecurityException("caller must be system dialer");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "getVisualVoicemailSettings");

        final long identity = Binder.clearCallingIdentity();
        try {
            PhoneAccountHandle phoneAccountHandle = PhoneAccountHandleConverter.fromSubId(subId);
            if (phoneAccountHandle == null) {
                return null;
            }
            return VisualVoicemailSettingsUtil.dump(mApp, phoneAccountHandle);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getVisualVoicemailPackageName(String callingPackage, String callingFeatureId,
            int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getVisualVoicemailPackageName")) {
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "getVisualVoicemailPackageName");

        final long identity = Binder.clearCallingIdentity();
        try {
            return RemoteVvmTaskManager.getRemotePackage(mApp, subId).getPackageName();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void enableVisualVoicemailSmsFilter(String callingPackage, int subId,
            VisualVoicemailSmsFilterSettings settings) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "enableVisualVoicemailSmsFilter");
        enforceVisualVoicemailPackage(callingPackage, subId);
        final long identity = Binder.clearCallingIdentity();
        try {
            VisualVoicemailSmsFilterConfig.enableVisualVoicemailSmsFilter(
                    mApp, callingPackage, subId, settings);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void disableVisualVoicemailSmsFilter(String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "disableVisualVoicemailSmsFilter");
        enforceVisualVoicemailPackage(callingPackage, subId);
        final long identity = Binder.clearCallingIdentity();
        try {
            VisualVoicemailSmsFilterConfig.disableVisualVoicemailSmsFilter(
                    mApp, callingPackage, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public VisualVoicemailSmsFilterSettings getVisualVoicemailSmsFilterSettings(
            String callingPackage, int subId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        final long identity = Binder.clearCallingIdentity();
        try {
            return VisualVoicemailSmsFilterConfig.getVisualVoicemailSmsFilterSettings(
                    mApp, callingPackage, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public VisualVoicemailSmsFilterSettings getActiveVisualVoicemailSmsFilterSettings(int subId) {
        enforceReadPrivilegedPermission("getActiveVisualVoicemailSmsFilterSettings");

        final long identity = Binder.clearCallingIdentity();
        try {
            return VisualVoicemailSmsFilterConfig.getActiveVisualVoicemailSmsFilterSettings(
                    mApp, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void sendVisualVoicemailSmsForSubscriber(String callingPackage,
            String callingAttributionTag, int subId, String number, int port, String text,
            PendingIntent sentIntent) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        enforceVisualVoicemailPackage(callingPackage, subId);
        enforceSendSmsPermission();

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "sendVisualVoicemailSmsForSubscriber");

        SmsController smsController = PhoneFactory.getSmsController();
        smsController.sendVisualVoicemailSmsForSubscriber(callingPackage, callingAttributionTag,
                subId, number, port, text, sentIntent);
    }

    /**
     * Sets the voice activation state of a given subId.
     */
    @Override
    public void setVoiceActivationState(int subId, int activationState) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setVoiceActivationState");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "setVoiceActivationState");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                phone.setVoiceActivationState(activationState);
            } else {
                loge("setVoiceActivationState fails with invalid subId: " + subId);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the data activation state of a given subId.
     */
    @Override
    public void setDataActivationState(int subId, int activationState) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setDataActivationState");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "setDataActivationState");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                phone.setDataActivationState(activationState);
            } else {
                loge("setDataActivationState fails with invalid subId: " + subId);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the voice activation state of a given subId.
     */
    @Override
    public int getVoiceActivationState(int subId, String callingPackage) {
        enforceReadPrivilegedPermission("getVoiceActivationState");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "getVoiceActivationState");

        final Phone phone = getPhone(subId);
        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone != null) {
                return phone.getVoiceActivationState();
            } else {
                return TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the data activation state of a given subId.
     */
    @Override
    public int getDataActivationState(int subId, String callingPackage) {
        enforceReadPrivilegedPermission("getDataActivationState");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_DATA, "getDataActivationState");

        final Phone phone = getPhone(subId);
        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone != null) {
                return phone.getDataActivationState();
            } else {
                return TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the unread count of voicemails for a subId
     */
    @Override
    public int getVoiceMessageCountForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getVoiceMessageCountForSubscriber")) {
            return 0;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getVoiceMessageCount();
            } else {
                return 0;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * returns true, if the device is in a state where both voice and data
     * are supported simultaneously. This can change based on location or network condition.
     */
    @Override
    public boolean isConcurrentVoiceAndDataAllowed(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isConcurrentVoiceAndDataAllowed");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getPhoneFromSubIdOrDefault(subId).isConcurrentVoiceAndDataAllowed();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Send the dialer code if called from the current default dialer or the caller has
     * carrier privilege.
     * @param inputCode The dialer code to send
     */
    @Override
    public void sendDialerSpecialCode(String callingPackage, String inputCode) {
        final Phone defaultPhone = getDefaultPhone();
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        TelecomManager tm = defaultPhone.getContext().getSystemService(TelecomManager.class);
        String defaultDialer = tm.getDefaultDialerPackage();
        if (!TextUtils.equals(callingPackage, defaultDialer)) {
            TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mApp,
                    getDefaultSubscription(), "sendDialerSpecialCode");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "sendDialerSpecialCode");

        final long identity = Binder.clearCallingIdentity();
        try {
            defaultPhone.sendDialerSpecialCode(inputCode);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getNetworkSelectionMode(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "getNetworkSelectionMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getNetworkSelectionMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!isActiveSubscription(subId)) {
                return TelephonyManager.NETWORK_SELECTION_MODE_UNKNOWN;
            }
            return (int) sendRequest(CMD_GET_NETWORK_SELECTION_MODE, null /* argument */, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isInEmergencySmsMode() {
        enforceReadPrivilegedPermission("isInEmergencySmsMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_MESSAGING, "isInEmergencySmsMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone : PhoneFactory.getPhones()) {
                if (phone.isInEmergencySmsMode()) {
                    return true;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return false;
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param c The callback that will be used to send the result.
     */
    @Override
    public void registerImsRegistrationCallback(int subId, IImsRegistrationCallback c)
            throws RemoteException {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "registerImsRegistrationCallback");

        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            verifyImsMmTelConfiguredOrThrow(slotId);

            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.addRegistrationCallbackForSubscription(c, subId);
                } else {
                    throw new ServiceSpecificException(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
                }
            } else {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION);
            }
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param c The callback that will be used to send the result.
     */
    @Override
    public void unregisterImsRegistrationCallback(int subId, IImsRegistrationCallback c) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "unregisterImsRegistrationCallback");
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }
        final long token = Binder.clearCallingIdentity();

        try {
            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.removeRegistrationCallbackForSubscription(c, subId);
                } else {
                    Log.i(LOG_TAG, "unregisterImsRegistrationCallback: " + subId
                            + "is inactive, ignoring unregister.");
                    // If the ImsManager is not valid, just return, since the callback
                    // will already have been removed internally.
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param c The callback that will be used to send the result.
     */
    @Override
    public void registerImsEmergencyRegistrationCallback(int subId, IImsRegistrationCallback c)
            throws RemoteException {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "registerImsEmergencyRegistrationCallback");

        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            verifyImsMmTelConfiguredOrThrow(slotId);

            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.addEmergencyRegistrationCallbackForSubscription(c, subId);
                } else {
                    throw new ServiceSpecificException(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
                }
            } else {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION);
            }
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param c The callback that will be used to send the result.
     */
    @Override
    public void unregisterImsEmergencyRegistrationCallback(int subId, IImsRegistrationCallback c) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "unregisterImsEmergencyRegistrationCallback");
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }
        final long token = Binder.clearCallingIdentity();

        try {
            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.removeEmergencyRegistrationCallbackForSubscription(c, subId);
                } else {
                    Log.i(LOG_TAG, "unregisterImsEmergencyRegistrationCallback: " + subId
                            + "is inactive, ignoring unregister.");
                    // If the ImsManager is not valid, just return, since the callback
                    // will already have been removed internally.
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Get the IMS service registration state for the MmTelFeature associated with this sub id.
     */
    @Override
    public void getImsMmTelRegistrationState(int subId, IIntegerConsumer consumer) {
        enforceReadPrivilegedPermission("getImsMmTelRegistrationState");
        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) {
                Log.w(LOG_TAG, "getImsMmTelRegistrationState: called with an invalid subscription '"
                        + subId + "'");
                throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
            }
            phone.getImsRegistrationState(regState -> {
                try {
                    consumer.accept((regState == null)
                            ? RegistrationManager.REGISTRATION_STATE_NOT_REGISTERED : regState);
                } catch (RemoteException e) {
                    // Ignore if the remote process is no longer available to call back.
                    Log.w(LOG_TAG, "getImsMmTelRegistrationState: callback not available.");
                }
            });
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Get the transport type for the IMS service registration state.
     */
    @Override
    public void getImsMmTelRegistrationTransportType(int subId, IIntegerConsumer consumer) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getImsMmTelRegistrationTransportType");
        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) {
                Log.w(LOG_TAG, "getImsMmTelRegistrationState: called with an invalid subscription '"
                        + subId + "'");
                throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
            }
            phone.getImsRegistrationTech(regTech -> {
                // Convert registration tech from ImsRegistrationImplBase -> RegistrationManager
                int regTechConverted = (regTech == null)
                        ? ImsRegistrationImplBase.REGISTRATION_TECH_NONE : regTech;
                regTechConverted = RegistrationManager.IMS_REG_TO_ACCESS_TYPE_MAP.get(
                        regTechConverted);
                try {
                    consumer.accept(regTechConverted);
                } catch (RemoteException e) {
                    // Ignore if the remote process is no longer available to call back.
                    Log.w(LOG_TAG, "getImsMmTelRegistrationState: callback not available.");
                }
            });
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param c The callback that will be used to send the result.
     */
    @Override
    public void registerMmTelCapabilityCallback(int subId, IImsCapabilityCallback c)
            throws RemoteException {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "registerMmTelCapabilityCallback");
        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            verifyImsMmTelConfiguredOrThrow(slotId);

            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.addCapabilitiesCallbackForSubscription(c, subId);
                } else {
                    throw new ServiceSpecificException(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
                }
            } else {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION);
            }
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param c The callback that will be used to send the result.
     */
    @Override
    public void unregisterMmTelCapabilityCallback(int subId, IImsCapabilityCallback c) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "unregisterMmTelCapabilityCallback");
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }

        final long token = Binder.clearCallingIdentity();
        try {
            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.removeCapabilitiesCallbackForSubscription(c, subId);
                } else {
                    Log.i(LOG_TAG, "unregisterMmTelCapabilityCallback: " + subId
                            + " is inactive, ignoring unregister.");
                    // If the ImsManager is not valid, just return, since the callback
                    // will already have been removed internally.
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public boolean isCapable(int subId, int capability, int regTech) {
        enforceReadPrivilegedPermission("isCapable");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isCapable");

        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            verifyImsMmTelConfiguredOrThrow(slotId);
            return ImsManager.getInstance(mApp, slotId).queryMmTelCapability(capability, regTech);
        } catch (com.android.ims.ImsException e) {
            Log.w(LOG_TAG, "IMS isCapable - service unavailable: " + e.getMessage());
            return false;
        } catch (ImsException e) {
            Log.i(LOG_TAG, "isCapable: " + subId + " is inactive, returning false.");
            return false;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public boolean isAvailable(int subId, int capability, int regTech) {
        enforceReadPrivilegedPermission("isAvailable");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isAvailable");

        final long token = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) return false;
            return phone.isImsCapabilityAvailable(capability, regTech);
        } catch (com.android.ims.ImsException e) {
            Log.w(LOG_TAG, "IMS isAvailable - service unavailable: " + e.getMessage());
            return false;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Determines if the MmTel feature capability is supported by the carrier configuration for this
     * subscription.
     * @param subId The subscription to use to check the configuration.
     * @param callback The callback that will be used to send the result.
     * @param capability The MmTelFeature capability that will be used to send the result.
     * @param transportType The transport type of the MmTelFeature capability.
     */
    @Override
    public void isMmTelCapabilitySupported(int subId, IIntegerConsumer callback, int capability,
            int transportType) {
        enforceReadPrivilegedPermission("isMmTelCapabilitySupported");
        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndex(subId);
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.w(LOG_TAG, "isMmTelCapabilitySupported: called with an inactive subscription '"
                        + subId + "'");
                throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
            }
            verifyImsMmTelConfiguredOrThrow(slotId);
            ImsManager.getInstance(mApp, slotId).isSupported(capability,
                    transportType, aBoolean -> {
                        try {
                            callback.accept((aBoolean == null) ? 0 : (aBoolean ? 1 : 0));
                        } catch (RemoteException e) {
                            Log.w(LOG_TAG, "isMmTelCapabilitySupported: remote caller is not "
                                    + "running. Ignore");
                        }
                    });
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override
    public boolean isAdvancedCallingSettingEnabled(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isAdvancedCallingSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isAdvancedCallingSettingEnabled");

        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).isEnhanced4gLteModeSettingEnabledByUser();
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void setAdvancedCallingSettingEnabled(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setAdvancedCallingSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setAdvancedCallingSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setEnhanced4gLteModeSetting(isEnabled);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override
    public boolean isVtSettingEnabled(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isVtSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isVtSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).isVtEnabledByUser();
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVtSettingEnabled(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setVtSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setVtSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setVtSetting(isEnabled);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override
    public boolean isVoWiFiSettingEnabled(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isVoWiFiSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isVoWiFiSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).isWfcEnabledByUser();
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVoWiFiSettingEnabled(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setVoWiFiSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setVoWiFiSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setWfcSetting(isEnabled);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @return true if the user's setting for Voice over Cross SIM is enabled and false if it is not
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override
    public boolean isCrossSimCallingEnabledByUser(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isCrossSimCallingEnabledByUser");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isCrossSimCallingEnabledByUser");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).isCrossSimCallingEnabledByUser();
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the user's setting for whether or not Voice over Cross SIM is enabled.
     * Requires MODIFY_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     * @param isEnabled true if the user's setting for Voice over Cross SIM is enabled,
     *                 false otherwise
     */
    @Override
    public void setCrossSimCallingEnabled(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setCrossSimCallingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setCrossSimCallingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setCrossSimCallingEnabled(isEnabled);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override

    public boolean isVoWiFiRoamingSettingEnabled(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isVoWiFiRoamingSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isVoWiFiRoamingSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).isWfcRoamingEnabledByUser();
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVoWiFiRoamingSettingEnabled(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setVoWiFiRoamingSettingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setVoWiFiRoamingSettingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setWfcRoamingSetting(isEnabled);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVoWiFiNonPersistent(int subId, boolean isCapable, int mode) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setVoWiFiNonPersistent");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setVoWiFiNonPersistent");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting will be ignored if the ImsService isn't up.
            ImsManager.getInstance(mApp, slotId).setWfcNonPersistent(isCapable, mode);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override
    public int getVoWiFiModeSetting(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getVoWiFiModeSetting");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "getVoWiFiModeSetting");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).getWfcMode(false /*isRoaming*/);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVoWiFiModeSetting(int subId, int mode) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setVoWiFiModeSetting");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setVoWiFiModeSetting");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setWfcMode(mode, false /*isRoaming*/);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getVoWiFiRoamingModeSetting(int subId) {
        enforceReadPrivilegedPermission("getVoWiFiRoamingModeSetting");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "getVoWiFiRoamingModeSetting");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).getWfcMode(true /*isRoaming*/);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVoWiFiRoamingModeSetting(int subId, int mode) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setVoWiFiRoamingModeSetting");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setVoWiFiRoamingModeSetting");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setWfcMode(mode, true /*isRoaming*/);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setRttCapabilitySetting(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setRttCapabilityEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setRttCapabilitySetting");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify. The
            // new setting will be picked up when the ImsService comes up next if it isn't up.
            ImsManager.getInstance(mApp, slotId).setRttEnabled(isEnabled);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @param subId The subscription to use to check the configuration.
     */
    @Override
    public boolean isTtyOverVolteEnabled(int subId) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isTtyOverVolteEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isTtyOverVolteEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            // This setting doesn't require an active ImsService connection, so do not verify.
            return ImsManager.getInstance(mApp, slotId).isTtyOnVoLteCapable();
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void registerImsProvisioningChangedCallback(int subId, IImsConfigCallback callback) {
        enforceReadPrivilegedPermission("registerImsProvisioningChangedCallback");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!isImsAvailableOnDevice()) {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                        "IMS not available on device.");
            }
            int slotId = getSlotIndexOrException(subId);
            verifyImsMmTelConfiguredOrThrow(slotId);

            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.addProvisioningCallbackForSubscription(callback, subId);
                } else {
                    throw new ServiceSpecificException(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE);
                }
            } else {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION);
            }
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void unregisterImsProvisioningChangedCallback(int subId, IImsConfigCallback callback) {
        enforceReadPrivilegedPermission("unregisterImsProvisioningChangedCallback");

        final long identity = Binder.clearCallingIdentity();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }
        try {
            ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
            if (controller != null) {
                ImsManager imsManager = controller.getImsManager(subId);
                if (imsManager != null) {
                    imsManager.removeProvisioningCallbackForSubscription(callback, subId);
                } else {
                    Log.i(LOG_TAG, "unregisterImsProvisioningChangedCallback: " + subId
                            + " is inactive, ignoring unregister.");
                    // If the ImsManager is not valid, just return, since the callback will already
                    // have been removed internally.
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void registerFeatureProvisioningChangedCallback(int subId,
            IFeatureProvisioningCallback callback) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "registerFeatureProvisioningChangedCallback");

        final long identity = Binder.clearCallingIdentity();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }

        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                        "Device does not support IMS");
            }
            controller.addFeatureProvisioningChangedCallback(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void unregisterFeatureProvisioningChangedCallback(int subId,
            IFeatureProvisioningCallback callback) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "unregisterFeatureProvisioningChangedCallback");

        final long identity = Binder.clearCallingIdentity();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }

        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("unregisterFeatureProvisioningChangedCallback: Device does not support IMS");
                return;
            }
            controller.removeFeatureProvisioningChangedCallback(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void checkModifyPhoneStatePermission(int subId, String message) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                message);
    }

    @Override
    public void setRcsProvisioningStatusForCapability(int subId, int capability, int tech,
            boolean isProvisioned) {
        checkModifyPhoneStatePermission(subId, "setRcsProvisioningStatusForCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setRcsProvisioningStatusForCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("setRcsProvisioningStatusForCapability: Device does not support IMS");
                return;
            }
            controller.setRcsProvisioningStatusForCapability(
                    subId, capability, tech, isProvisioned);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }


    @Override
    public boolean getRcsProvisioningStatusForCapability(int subId, int capability, int tech) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getRcsProvisioningStatusForCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "getRcsProvisioningStatusForCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("getRcsProvisioningStatusForCapability: Device does not support IMS");

                // device does not support IMS, this method will return true always.
                return true;
            }
            return controller.getRcsProvisioningStatusForCapability(subId, capability, tech);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setImsProvisioningStatusForCapability(int subId, int capability, int tech,
            boolean isProvisioned) {
        checkModifyPhoneStatePermission(subId, "setImsProvisioningStatusForCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setImsProvisioningStatusForCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("setImsProvisioningStatusForCapability: Device does not support IMS");
                return;
            }
            controller.setImsProvisioningStatusForCapability(
                    subId, capability, tech, isProvisioned);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean getImsProvisioningStatusForCapability(int subId, int capability, int tech) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getProvisioningStatusForCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "getImsProvisioningStatusForCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("getImsProvisioningStatusForCapability: Device does not support IMS");

                // device does not support IMS, this method will return true always.
                return true;
            }
            return controller.getImsProvisioningStatusForCapability(subId, capability, tech);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isProvisioningRequiredForCapability(int subId, int capability, int tech) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isProvisioningRequiredForCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isProvisioningRequiredForCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("isProvisioningRequiredForCapability: Device does not support IMS");

                // device does not support IMS, this method will return false
                return false;
            }
            return controller.isImsProvisioningRequiredForCapability(subId, capability, tech);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isRcsProvisioningRequiredForCapability(int subId, int capability, int tech) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "isProvisioningRequiredForCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "isRcsProvisioningRequiredForCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("isRcsProvisioningRequiredForCapability: Device does not support IMS");

                // device does not support IMS, this method will return false
                return false;
            }
            return controller.isRcsProvisioningRequiredForCapability(subId, capability, tech);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getImsProvisioningInt(int subId, int key) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription id '" + subId + "'");
        }
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getImsProvisioningInt");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "getImsProvisioningInt");

        final long identity = Binder.clearCallingIdentity();
        try {
            // TODO: Refactor to remove ImsManager dependence and query through ImsPhone directly.
            int slotId = getSlotIndex(subId);
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.w(LOG_TAG, "getImsProvisioningInt: called with an inactive subscription '"
                        + subId + "' for key:" + key);
                return ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
            }

            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("getImsProvisioningInt: Device does not support IMS");

                // device does not support IMS, this method will return CONFIG_RESULT_UNKNOWN.
                return ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
            }
            int retVal = controller.getProvisioningValue(subId, key);
            if (retVal != ImsConfigImplBase.CONFIG_RESULT_UNKNOWN) {
                return retVal;
            }

            return ImsManager.getInstance(mApp, slotId).getConfigInt(key);
        } catch (com.android.ims.ImsException e) {
            Log.w(LOG_TAG, "getImsProvisioningInt: ImsService is not available for subscription '"
                    + subId + "' for key:" + key);
            return ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getImsProvisioningString(int subId, int key) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription id '" + subId + "'");
        }
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getImsProvisioningString");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "getImsProvisioningString");

        final long identity = Binder.clearCallingIdentity();
        try {
            // TODO: Refactor to remove ImsManager dependence and query through ImsPhone directly.
            int slotId = getSlotIndex(subId);
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.w(LOG_TAG, "getImsProvisioningString: called for an inactive subscription id '"
                        + subId + "' for key:" + key);
                return ProvisioningManager.STRING_QUERY_RESULT_ERROR_GENERIC;
            }
            return ImsManager.getInstance(mApp, slotId).getConfigString(key);
        } catch (com.android.ims.ImsException e) {
            Log.w(LOG_TAG, "getImsProvisioningString: ImsService is not available for sub '"
                    + subId + "' for key:" + key);
            return ProvisioningManager.STRING_QUERY_RESULT_ERROR_NOT_READY;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int setImsProvisioningInt(int subId, int key, int value) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription id '" + subId + "'");
        }
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setImsProvisioningInt");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setImsProvisioningInt");

        final long identity = Binder.clearCallingIdentity();
        try {
            // TODO: Refactor to remove ImsManager dependence and query through ImsPhone directly.
            int slotId = getSlotIndex(subId);
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.w(LOG_TAG, "setImsProvisioningInt: called with an inactive subscription id '"
                        + subId + "' for key:" + key);
                return ImsConfigImplBase.CONFIG_RESULT_FAILED;
            }

            ImsProvisioningController controller = ImsProvisioningController.getInstance();
            if (controller == null) {
                loge("setImsProvisioningInt: Device does not support IMS");

                // device does not support IMS, this method will return CONFIG_RESULT_FAILED.
                return ImsConfigImplBase.CONFIG_RESULT_FAILED;
            }
            int retVal = controller.setProvisioningValue(subId, key, value);
            if (retVal != ImsConfigImplBase.CONFIG_RESULT_UNKNOWN) {
                return retVal;
            }

            return ImsManager.getInstance(mApp, slotId).setConfig(key, value);
        } catch (com.android.ims.ImsException | RemoteException e) {
            Log.w(LOG_TAG, "setImsProvisioningInt: ImsService unavailable for sub '" + subId
                    + "' for key:" + key, e);
            return ImsConfigImplBase.CONFIG_RESULT_FAILED;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int setImsProvisioningString(int subId, int key, String value) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription id '" + subId + "'");
        }
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp, subId,
                "setImsProvisioningString");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                FEATURE_TELEPHONY_IMS, "setImsProvisioningString");

        final long identity = Binder.clearCallingIdentity();
        try {
            // TODO: Refactor to remove ImsManager dependence and query through ImsPhone directly.
            int slotId = getSlotIndex(subId);
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.w(LOG_TAG, "setImsProvisioningString: called with an inactive subscription id '"
                        + subId + "' for key:" + key);
                return ImsConfigImplBase.CONFIG_RESULT_FAILED;
            }
            return ImsManager.getInstance(mApp, slotId).setConfig(key, value);
        } catch (com.android.ims.ImsException | RemoteException e) {
            Log.w(LOG_TAG, "setImsProvisioningString: ImsService unavailable for sub '" + subId
                    + "' for key:" + key, e);
            return ImsConfigImplBase.CONFIG_RESULT_FAILED;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Throw an ImsException if the IMS resolver does not have an ImsService configured for MMTEL
     * for the given slot ID or no ImsResolver instance has been created.
     * @param slotId The slot ID that the IMS service is created for.
     * @throws ImsException If there is no ImsService configured for this slot.
     */
    private void verifyImsMmTelConfiguredOrThrow(int slotId) throws ImsException {
        if (mImsResolver == null || !mImsResolver.isImsServiceConfiguredForFeature(slotId,
                ImsFeature.FEATURE_MMTEL)) {
            throw new ImsException("This subscription does not support MMTEL over IMS",
                    ImsException.CODE_ERROR_UNSUPPORTED_OPERATION);
        }
    }

    private int getSlotIndexOrException(int subId) throws ImsException {
        int slotId = SubscriptionManager.getSlotIndex(subId);
        if (!SubscriptionManager.isValidSlotIndex(slotId)) {
            throw new ImsException("Invalid Subscription Id, subId=" + subId,
                    ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
        }
        return slotId;
    }

    private int getSlotIndex(int subId) {
        int slotId = SubscriptionManager.getSlotIndex(subId);
        if (!SubscriptionManager.isValidSlotIndex(slotId)) {
            return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        }
        return slotId;
    }

    /**
     * Returns the data network type for a subId; does not throw SecurityException.
     */
    @Override
    public int getNetworkTypeForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        try {
            mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        } catch (SecurityException se) {
            EventLog.writeEvent(0x534e4554, "186776740", Binder.getCallingUid());
            throw new SecurityException("Package " + callingPackage + " does not belong to "
                    + Binder.getCallingUid());
        }
        final int targetSdk = TelephonyPermissions.getTargetSdk(mApp, callingPackage);
        if (targetSdk > android.os.Build.VERSION_CODES.Q) {
            return getDataNetworkTypeForSubscriber(subId, callingPackage, callingFeatureId);
        } else if (targetSdk == android.os.Build.VERSION_CODES.Q
                && !TelephonyPermissions.checkCallingOrSelfReadPhoneStateNoThrow(
                mApp, subId, callingPackage, callingFeatureId,
                "getNetworkTypeForSubscriber")) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getNetworkTypeForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getServiceState().getDataNetworkType();
            } else {
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the data network type
     */
    @Override
    public int getDataNetworkType(String callingPackage, String callingFeatureId) {
        return getDataNetworkTypeForSubscriber(SubscriptionManager.getDefaultDataSubscriptionId(),
                callingPackage, callingFeatureId);
    }

    /**
     * Returns the data network type for a subId
     */
    @Override
    public int getDataNetworkTypeForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        String functionName = "getDataNetworkTypeForSubscriber";
        if (!TelephonyPermissions.checkCallingOrSelfReadNonDangerousPhoneStateNoThrow(
                mApp, functionName)) {
            if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                    mApp, subId, callingPackage, callingFeatureId, functionName)) {
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getDataNetworkTypeForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getServiceState().getDataNetworkType();
            } else {
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the Voice network type for a subId
     */
    @Override
    public int getVoiceNetworkTypeForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        String functionName = "getVoiceNetworkTypeForSubscriber";
        if (!TelephonyPermissions.checkCallingOrSelfReadNonDangerousPhoneStateNoThrow(
                mApp, functionName)) {
            if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                    mApp, subId, callingPackage, callingFeatureId, functionName)) {
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "getVoiceNetworkTypeForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getServiceState().getVoiceNetworkType();
            } else {
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        // FIXME Make changes to pass defaultSimId of type int
        return hasIccCardUsingSlotIndex(SubscriptionManager.getSlotIndex(
                getDefaultSubscription()));
    }

    /**
     * @return true if a ICC card is present for a slotIndex
     */
    @Override
    public boolean hasIccCardUsingSlotIndex(int slotIndex) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "hasIccCardUsingSlotIndex");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = PhoneFactory.getPhone(slotIndex);
            if (phone != null) {
                return phone.getIccCard().hasIccCard();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @param callingPackage the name of the package making the call.
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link Phone#LTE_ON_CDMA_TRUE}
     */
    @Override
    public int getLteOnCdmaMode(String callingPackage, String callingFeatureId) {
        return getLteOnCdmaModeForSubscriber(getDefaultSubscription(), callingPackage,
                callingFeatureId);
    }

    @Override
    public int getLteOnCdmaModeForSubscriber(int subId, String callingPackage,
            String callingFeatureId) {
        try {
            enforceReadPrivilegedPermission("getLteOnCdmaModeForSubscriber");
        } catch (SecurityException e) {
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getLteOnCdmaModeForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null) {
                return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
            } else {
                return TelephonyProperties.lte_on_cdma_device()
                        .orElse(PhoneConstants.LTE_ON_CDMA_FALSE);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * {@hide}
     * Returns Default subId, 0 in the case of single standby.
     */
    private int getDefaultSubscription() {
        return SubscriptionManager.getDefaultSubscriptionId();
    }

    private int getSlotForDefaultSubscription() {
        return SubscriptionManager.getPhoneId(getDefaultSubscription());
    }

    private int getPreferredVoiceSubscription() {
        return SubscriptionManager.getDefaultVoiceSubscriptionId();
    }

    private boolean isActiveSubscription(int subId) {
        return getSubscriptionManagerService().isActiveSubId(subId,
                mApp.getOpPackageName(), mApp.getFeatureId());
    }

    /**
     * @see android.telephony.TelephonyManager.WifiCallingChoices
     */
    public int getWhenToMakeWifiCalls() {
        final long identity = Binder.clearCallingIdentity();
        try {
            return Settings.System.getInt(mApp.getContentResolver(),
                    Settings.System.WHEN_TO_MAKE_WIFI_CALLS,
                    getWhenToMakeWifiCallsDefaultPreference());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @see android.telephony.TelephonyManager.WifiCallingChoices
     */
    public void setWhenToMakeWifiCalls(int preference) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("setWhenToMakeWifiCallsStr, storing setting = " + preference);
            Settings.System.putInt(mApp.getContentResolver(),
                    Settings.System.WHEN_TO_MAKE_WIFI_CALLS, preference);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static int getWhenToMakeWifiCallsDefaultPreference() {
        // TODO: Use a build property to choose this value.
        return TelephonyManager.WifiCallingChoices.ALWAYS_USE;
    }

    private Phone getPhoneFromSlotPortIndexOrThrowException(int slotIndex, int portIndex) {
        int phoneId = UiccController.getInstance().getPhoneIdFromSlotPortIndex(slotIndex,
                portIndex);
        if (phoneId == -1) {
            throw new IllegalArgumentException("Given slot index: " + slotIndex + " port index: "
                    + portIndex + " does not correspond to an active phone");
        }
        return PhoneFactory.getPhone(phoneId);
    }

    @Override
    public IccOpenLogicalChannelResponse iccOpenLogicalChannel(
            @NonNull IccLogicalChannelRequest request) {

        Phone phone = getPhoneFromValidIccLogicalChannelRequest(request,
                /*message=*/ "iccOpenLogicalChannel");

        if (DBG) log("iccOpenLogicalChannel: request=" + request);
        // Verify that the callingPackage in the request belongs to the calling UID
        mAppOps.checkPackage(Binder.getCallingUid(), request.callingPackage);

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "iccOpenLogicalChannel");

        return iccOpenLogicalChannelWithPermission(phone, request);
    }

    private Phone getPhoneFromValidIccLogicalChannelRequest(
            @NonNull IccLogicalChannelRequest request, String message) {
        Phone phone;
        if (request.subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, request.subId, message);
            phone = getPhoneFromSubId(request.subId);
        } else if (request.slotIndex != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            enforceModifyPermission();
            phone = getPhoneFromSlotPortIndexOrThrowException(request.slotIndex, request.portIndex);
        } else {
            throw new IllegalArgumentException("Both subId and slotIndex in request are invalid.");
        }
        return phone;
    }

    private IccOpenLogicalChannelResponse iccOpenLogicalChannelWithPermission(Phone phone,
            IccLogicalChannelRequest channelRequest) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (TextUtils.equals(ISDR_AID, channelRequest.aid)) {
                // Only allows LPA to open logical channel to ISD-R.
                ComponentInfo bestComponent = EuiccConnector.findBestComponent(getDefaultPhone()
                        .getContext().getPackageManager());
                if (bestComponent == null || !TextUtils.equals(channelRequest.callingPackage,
                        bestComponent.packageName)) {
                    loge("The calling package is not allowed to access ISD-R.");
                    throw new SecurityException(
                            "The calling package is not allowed to access ISD-R.");
                }
            }

            IccOpenLogicalChannelResponse response = (IccOpenLogicalChannelResponse) sendRequest(
                    CMD_OPEN_CHANNEL, channelRequest, phone, null /* workSource */);
            if (DBG) log("iccOpenLogicalChannelWithPermission: response=" + response);
            return response;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean iccCloseLogicalChannel(@NonNull IccLogicalChannelRequest request) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "iccCloseLogicalChannel");

        Phone phone = getPhoneFromValidIccLogicalChannelRequest(request,
                /*message=*/"iccCloseLogicalChannel");

        if (DBG) log("iccCloseLogicalChannel: request=" + request);

        return iccCloseLogicalChannelWithPermission(phone, request);
    }

    private boolean iccCloseLogicalChannelWithPermission(Phone phone,
            IccLogicalChannelRequest request) {
        // before this feature is enabled, this API should only return false if
        // the operation fails instead of throwing runtime exception for
        // backward-compatibility.
        final boolean shouldThrowExceptionOnFailure = CompatChanges.isChangeEnabled(
                ICC_CLOSE_CHANNEL_EXCEPTION_ON_FAILURE, Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            if (request.channel < 0) {
                throw new IllegalArgumentException("request.channel is less than 0");
            }
            Object result = sendRequest(CMD_CLOSE_CHANNEL, request.channel, phone,
                    null /* workSource */);
            Boolean success = false;
            if (result instanceof RuntimeException) {
                // if there is an exception returned, throw from the binder thread here.
                if (shouldThrowExceptionOnFailure) {
                    throw (RuntimeException) result;
                } else {
                    return false;
                }
            } else if (result instanceof Boolean) {
                success = (Boolean) result;
            } else {
                loge("iccCloseLogicalChannelWithPermission: supported return type " + result);
            }
            if (DBG) log("iccCloseLogicalChannelWithPermission: success=" + success);
            return success;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String iccTransmitApduLogicalChannel(int subId, int channel, int cla,
            int command, int p1, int p2, int p3, String data) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "iccTransmitApduLogicalChannel");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "iccTransmitApduLogicalChannel");

        if (DBG) {
            log("iccTransmitApduLogicalChannel: subId=" + subId + " chnl=" + channel
                    + " cla=" + cla + " cmd=" + command + " p1=" + p1 + " p2=" + p2 + " p3="
                    + p3 + " data=" + data);
        }
        return iccTransmitApduLogicalChannelWithPermission(getPhoneFromSubId(subId), channel, cla,
                command, p1, p2, p3, data);
    }

    @Override
    public String iccTransmitApduLogicalChannelByPort(int slotIndex, int portIndex, int channel,
            int cla, int command, int p1, int p2, int p3, String data) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "iccTransmitApduLogicalChannelBySlot");

        if (DBG) {
            log("iccTransmitApduLogicalChannelByPort: slotIndex=" + slotIndex + " portIndex="
                    + portIndex + " chnl=" + channel + " cla=" + cla + " cmd=" + command + " p1="
                    + p1 + " p2=" + p2 + " p3=" + p3 + " data=" + data);
        }
        return iccTransmitApduLogicalChannelWithPermission(
                getPhoneFromSlotPortIndexOrThrowException(slotIndex, portIndex), channel, cla,
                command, p1, p2, p3, data);
    }

    private String iccTransmitApduLogicalChannelWithPermission(Phone phone, int channel, int cla,
            int command, int p1, int p2, int p3, String data) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (channel <= 0) {
                return "";
            }

            IccIoResult response = (IccIoResult) sendRequest(CMD_TRANSMIT_APDU_LOGICAL_CHANNEL,
                    new IccAPDUArgument(channel, cla, command, p1, p2, p3, data), phone,
                    null /* workSource */);
            if (DBG) log("iccTransmitApduLogicalChannelWithPermission: " + response);

            // Append the returned status code to the end of the response payload.
            String s = Integer.toHexString(
                    (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
            if (response.payload != null) {
                s = IccUtils.bytesToHexString(response.payload) + s;
            }
            return s;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String iccTransmitApduBasicChannel(int subId, String callingPackage, int cla,
            int command, int p1, int p2, int p3, String data) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "iccTransmitApduBasicChannel");
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "iccTransmitApduBasicChannel");

        if (DBG) {
            log("iccTransmitApduBasicChannel: subId=" + subId + " cla=" + cla + " cmd="
                    + command + " p1=" + p1 + " p2=" + p2 + " p3=" + p3 + " data=" + data);
        }
        return iccTransmitApduBasicChannelWithPermission(getPhoneFromSubId(subId), callingPackage,
                cla, command, p1, p2, p3, data);
    }

    @Override
    public String iccTransmitApduBasicChannelByPort(int slotIndex, int portIndex,
            String callingPackage, int cla, int command, int p1, int p2, int p3, String data) {
        enforceModifyPermission();
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "iccTransmitApduBasicChannelBySlot");

        if (DBG) {
            log("iccTransmitApduBasicChannelByPort: slotIndex=" + slotIndex + " portIndex="
                    + portIndex + " cla=" + cla + " cmd=" + command + " p1=" + p1 + " p2="
                    + p2 + " p3=" + p3 + " data=" + data);
        }

        return iccTransmitApduBasicChannelWithPermission(
                getPhoneFromSlotPortIndexOrThrowException(slotIndex, portIndex), callingPackage,
                cla, command, p1, p2, p3, data);
    }

    // open APDU basic channel assuming the caller has sufficient permissions
    private String iccTransmitApduBasicChannelWithPermission(Phone phone, String callingPackage,
            int cla, int command, int p1, int p2, int p3, String data) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (command == SELECT_COMMAND && p1 == SELECT_P1 && p2 == SELECT_P2 && p3 == SELECT_P3
                    && TextUtils.equals(ISDR_AID, data)) {
                // Only allows LPA to select ISD-R.
                ComponentInfo bestComponent = EuiccConnector.findBestComponent(getDefaultPhone()
                        .getContext().getPackageManager());
                if (bestComponent == null
                        || !TextUtils.equals(callingPackage, bestComponent.packageName)) {
                    loge("The calling package is not allowed to select ISD-R.");
                    throw new SecurityException(
                            "The calling package is not allowed to select ISD-R.");
                }
            }

            IccIoResult response = (IccIoResult) sendRequest(CMD_TRANSMIT_APDU_BASIC_CHANNEL,
                    new IccAPDUArgument(0, cla, command, p1, p2, p3, data), phone,
                    null /* workSource */);
            if (DBG) log("iccTransmitApduBasicChannelWithPermission: " + response);

            // Append the returned status code to the end of the response payload.
            String s = Integer.toHexString(
                    (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
            if (response.payload != null) {
                s = IccUtils.bytesToHexString(response.payload) + s;
            }
            return s;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public byte[] iccExchangeSimIO(int subId, int fileID, int command, int p1, int p2, int p3,
            String filePath) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "iccExchangeSimIO");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "iccExchangeSimIO");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) {
                log("Exchange SIM_IO " + subId + ":" + fileID + ":" + command + " "
                        + p1 + " " + p2 + " " + p3 + ":" + filePath);
            }

            IccIoResult response =
                    (IccIoResult) sendRequest(CMD_EXCHANGE_SIM_IO,
                            new IccAPDUArgument(-1, fileID, command, p1, p2, p3, filePath),
                            subId);

            if (DBG) {
                log("Exchange SIM_IO [R]" + response);
            }

            byte[] result = null;
            int length = 2;
            if (response.payload != null) {
                length = 2 + response.payload.length;
                result = new byte[length];
                System.arraycopy(response.payload, 0, result, 0, response.payload.length);
            } else {
                result = new byte[length];
            }

            result[length - 1] = (byte) response.sw2;
            result[length - 2] = (byte) response.sw1;
            return result;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the forbidden PLMN List from the given app type (ex APPTYPE_USIM)
     * on a particular subscription
     */
    public String[] getForbiddenPlmns(int subId, int appType, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "getForbiddenPlmns")) {
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getForbiddenPlmns");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (appType != TelephonyManager.APPTYPE_USIM
                    && appType != TelephonyManager.APPTYPE_SIM) {
                loge("getForbiddenPlmnList(): App Type must be USIM or SIM");
                return null;
            }
            Object response = sendRequest(
                    CMD_GET_FORBIDDEN_PLMNS, new Integer(appType), subId);
            if (response instanceof String[]) {
                return (String[]) response;
            }
            // Response is an Exception of some kind
            // which is signalled to the user as a NULL retval
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the forbidden PLMN list from the given app type (ex APPTYPE_USIM) on a particular
     * subscription.
     *
     * @param subId the id of the subscription.
     * @param appType the uicc app type, must be USIM or SIM.
     * @param fplmns the Forbiden plmns list that needed to be written to the SIM.
     * @param callingPackage the op Package name.
     * @param callingFeatureId the feature in the package.
     * @return number of fplmns that is successfully written to the SIM.
     */
    public int setForbiddenPlmns(int subId, int appType, List<String> fplmns, String callingPackage,
            String callingFeatureId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setForbiddenPlmns");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "setForbiddenPlmns");

        if (appType != TelephonyManager.APPTYPE_USIM && appType != TelephonyManager.APPTYPE_SIM) {
            loge("setForbiddenPlmnList(): App Type must be USIM or SIM");
            throw new IllegalArgumentException("Invalid appType: App Type must be USIM or SIM");
        }
        if (fplmns == null) {
            throw new IllegalArgumentException("Fplmn List provided is null");
        }
        for (String fplmn : fplmns) {
            if (!CellIdentity.isValidPlmn(fplmn)) {
                throw new IllegalArgumentException("Invalid fplmn provided: " + fplmn);
            }
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            Object response = sendRequest(
                    CMD_SET_FORBIDDEN_PLMNS,
                    new Pair<Integer, List<String>>(new Integer(appType), fplmns),
                    subId);
            return (int) response;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String sendEnvelopeWithStatus(int subId, String content) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "sendEnvelopeWithStatus");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "sendEnvelopeWithStatus");

        final long identity = Binder.clearCallingIdentity();
        try {
            IccIoResult response = (IccIoResult) sendRequest(CMD_SEND_ENVELOPE, content, subId);
            if (response.payload == null) {
                return "";
            }

            // Append the returned status code to the end of the response payload.
            String s = Integer.toHexString(
                    (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
            s = IccUtils.bytesToHexString(response.payload) + s;
            return s;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Read one of the NV items defined in {@link com.android.internal.telephony.RadioNVItems}
     * and {@code ril_nv_items.h}. Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @return the NV item as a String, or null on error.
     */
    @Override
    public String nvReadItem(int itemID) {
        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, getDefaultSubscription(), "nvReadItem");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("nvReadItem: item " + itemID);
            String value = (String) sendRequest(CMD_NV_READ_ITEM, itemID, workSource);
            if (DBG) log("nvReadItem: item " + itemID + " is \"" + value + '"');
            return value;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Write one of the NV items defined in {@link com.android.internal.telephony.RadioNVItems}
     * and {@code ril_nv_items.h}. Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param itemValue the value to write, as a String
     * @return true on success; false on any failure
     */
    @Override
    public boolean nvWriteItem(int itemID, String itemValue) {
        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, getDefaultSubscription(), "nvWriteItem");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("nvWriteItem: item " + itemID + " value \"" + itemValue + '"');
            Boolean success = (Boolean) sendRequest(CMD_NV_WRITE_ITEM,
                    new Pair<Integer, String>(itemID, itemValue), workSource);
            if (DBG) log("nvWriteItem: item " + itemID + ' ' + (success ? "ok" : "fail"));
            return success;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Update the CDMA Preferred Roaming List (PRL) in the radio NV storage.
     * Used for device configuration by some CDMA operators.
     *
     * @param preferredRoamingList byte array containing the new PRL
     * @return true on success; false on any failure
     */
    @Override
    public boolean nvWriteCdmaPrl(byte[] preferredRoamingList) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, getDefaultSubscription(), "nvWriteCdmaPrl");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("nvWriteCdmaPrl: value: " + HexDump.toHexString(preferredRoamingList));
            Boolean success = (Boolean) sendRequest(CMD_NV_WRITE_CDMA_PRL, preferredRoamingList);
            if (DBG) log("nvWriteCdmaPrl: " + (success ? "ok" : "fail"));
            return success;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Rollback modem configurations to factory default except some config which are in whitelist.
     * Used for device configuration by some CDMA operators.
     *
     * @param slotIndex - device slot.
     *
     * @return true on success; false on any failure
     */
    @Override
    public boolean resetModemConfig(int slotIndex) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone != null) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, phone.getSubId(), "resetModemConfig");

            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "resetModemConfig");

            final long identity = Binder.clearCallingIdentity();
            try {
                Boolean success = (Boolean) sendRequest(CMD_RESET_MODEM_CONFIG, null);
                if (DBG) log("resetModemConfig:" + ' ' + (success ? "ok" : "fail"));
                return success;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        return false;
    }

    /**
     * Generate a radio modem reset. Used for device configuration by some CDMA operators.
     *
     * @param slotIndex - device slot.
     *
     * @return true on success; false on any failure
     */
    @Override
    public boolean rebootModem(int slotIndex) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone != null) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, phone.getSubId(), "rebootModem");

            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "rebootModem");

            final long identity = Binder.clearCallingIdentity();
            try {
                Boolean success = (Boolean) sendRequest(CMD_MODEM_REBOOT, null);
                if (DBG) log("rebootModem:" + ' ' + (success ? "ok" : "fail"));
                return success;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        return false;
    }

    /**
     * Toggle IMS disable and enable for the framework to reset it. See {@link #enableIms(int)} and
     * {@link #disableIms(int)}.
     * @param slotIndex device slot.
     */
    public void resetIms(int slotIndex) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_IMS, "resetIms");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the does not support IMS.
                return;
            }
            mImsResolver.resetIms(slotIndex);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Enables IMS for the framework. This will trigger IMS registration and ImsFeature capability
     * status updates, if not already enabled.
     */
    public void enableIms(int slotId) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return;
            }
            mImsResolver.enableIms(slotId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Disables IMS for the framework. This will trigger IMS de-registration and trigger ImsFeature
     * status updates to disabled.
     */
    public void disableIms(int slotId) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return;
            }
            mImsResolver.disableIms(slotId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Registers for updates to the MmTelFeature connection through the IImsServiceFeatureCallback
     * callback.
     */
    @Override
    public void registerMmTelFeatureCallback(int slotId, IImsServiceFeatureCallback callback) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                        "Device does not support IMS");
            }
            mImsResolver.listenForFeature(slotId, ImsFeature.FEATURE_MMTEL, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
    /**
     * Unregister a previously registered IImsServiceFeatureCallback associated with an ImsFeature.
     */
    @Override
    public void unregisterImsFeatureCallback(IImsServiceFeatureCallback callback) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) return;
            mImsResolver.unregisterImsFeatureCallback(callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the {@link IImsRegistration} structure associated with the slotId and feature
     * specified or null if IMS is not supported on the slot specified.
     */
    public IImsRegistration getImsRegistration(int slotId, int feature) throws RemoteException {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return null;
            }
            return mImsResolver.getImsRegistration(slotId, feature);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the {@link IImsConfig} structure associated with the slotId and feature
     * specified or null if IMS is not supported on the slot specified.
     */
    public IImsConfig getImsConfig(int slotId, int feature) throws RemoteException {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return null;
            }
            return mImsResolver.getImsConfig(slotId, feature);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the ImsService Package Name that Telephony will bind to.
     *
     * @param slotIndex the slot ID that the ImsService should bind for.
     * @param isCarrierService true if the ImsService is the carrier override, false if the
     *         ImsService is the device default ImsService.
     * @param featureTypes An integer array of feature types associated with a packageName.
     * @param packageName The name of the package that the current configuration will be replaced
     *                    with.
     * @return true if setting the ImsService to bind to succeeded, false if it did not.
     */
    public boolean setBoundImsServiceOverride(int slotIndex, boolean isCarrierService,
            int[] featureTypes, String packageName) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "setBoundImsServiceOverride");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.getSubscriptionId(slotIndex), "setBoundImsServiceOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return false;
            }
            Map<Integer, String> featureConfig = new HashMap<>();
            for (int featureType : featureTypes) {
                featureConfig.put(featureType, packageName);
            }
            return mImsResolver.overrideImsServiceConfiguration(slotIndex, isCarrierService,
                    featureConfig);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Clears any carrier ImsService overrides for the slot index specified that were previously
     * set with {@link #setBoundImsServiceOverride(int, boolean, int[], String)}.
     *
     * This should only be used for testing.
     *
     * @param slotIndex the slot ID that the ImsService should bind for.
     * @return true if clearing the carrier ImsService override succeeded or false if it did not.
     */
    @Override
    public boolean clearCarrierImsServiceOverride(int slotIndex) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "clearCarrierImsServiceOverride");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.getSubscriptionId(slotIndex), "clearCarrierImsServiceOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return false;
            }
            return mImsResolver.clearCarrierImsServiceConfiguration(slotIndex);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return the package name of the currently bound ImsService.
     *
     * @param slotId The slot that the ImsService is associated with.
     * @param isCarrierImsService true, if the ImsService is a carrier override, false if it is
     *         the device default.
     * @param featureType The feature associated with the queried configuration.
     * @return the package name of the ImsService configuration.
     */
    public String getBoundImsServicePackage(int slotId, boolean isCarrierImsService,
            @ImsFeature.FeatureType int featureType) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(mApp,
                        SubscriptionManager.getSubscriptionId(slotId), "getBoundImsServicePackage");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (mImsResolver == null) {
                // may happen if the device does not support IMS.
                return "";
            }
            // TODO: change API to query RCS separately.
            return mImsResolver.getImsServiceConfiguration(slotId, isCarrierImsService,
                    featureType);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the MmTelFeature state associated with the requested subscription id.
     * @param subId The subscription that the MmTelFeature is associated with.
     * @param callback A callback with an integer containing the
     * {@link android.telephony.ims.feature.ImsFeature.ImsState} associated with the MmTelFeature.
     */
    @Override
    public void getImsMmTelFeatureState(int subId, IIntegerConsumer callback) {
        enforceReadPrivilegedPermission("getImsMmTelFeatureState");
        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }
        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndex(subId);
            if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.w(LOG_TAG, "getImsMmTelFeatureState: called with an inactive subscription '"
                        + subId + "'");
                throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
            }
            verifyImsMmTelConfiguredOrThrow(slotId);
            ImsManager.getInstance(mApp, slotId).getImsServiceState(anInteger -> {
                try {
                    callback.accept(anInteger == null ? ImsFeature.STATE_UNAVAILABLE : anInteger);
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "getImsMmTelFeatureState: remote caller is no longer running. "
                            + "Ignore");
                }
            });
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Sets the ims registration state on all valid {@link Phone}s.
     */
    public void setImsRegistrationState(final boolean registered) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            // NOTE: Before S, this method only set the default phone.
            for (final Phone phone : PhoneFactory.getPhones()) {
                if (SubscriptionManager.isValidSubscriptionId(phone.getSubId())) {
                    phone.setImsRegistrationState(registered);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the network selection mode to automatic.
     *
     */
    @Override
    public void setNetworkSelectionModeAutomatic(int subId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setNetworkSelectionModeAutomatic");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "setNetworkSelectionModeAutomatic");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!isActiveSubscription(subId)) {
                return;
            }
            if (DBG) log("setNetworkSelectionModeAutomatic: subId " + subId);
            sendRequest(CMD_SET_NETWORK_SELECTION_MODE_AUTOMATIC, null, subId,
                    SET_NETWORK_SELECTION_MODE_AUTOMATIC_TIMEOUT_MS);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Ask the radio to connect to the input network and change selection mode to manual.
     *
     * @param subId the id of the subscription.
     * @param operatorInfo the operator information, included the PLMN, long name and short name of
     * the operator to attach to.
     * @param persistSelection whether the selection will persist until reboot. If true, only allows
     * attaching to the selected PLMN until reboot; otherwise, attach to the chosen PLMN and resume
     * normal network selection next time.
     * @return {@code true} on success; {@code true} on any failure.
     */
    @Override
    public boolean setNetworkSelectionModeManual(
            int subId, OperatorInfo operatorInfo, boolean persistSelection) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setNetworkSelectionModeManual");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "setNetworkSelectionModeManual");

        final long identity = Binder.clearCallingIdentity();
        if (!isActiveSubscription(subId)) {
            return false;
        }

        try {
            ManualNetworkSelectionArgument arg = new ManualNetworkSelectionArgument(operatorInfo,
                    persistSelection);
            if (DBG) {
                log("setNetworkSelectionModeManual: subId: " + subId
                        + " operator: " + operatorInfo);
            }
            return (Boolean) sendRequest(CMD_SET_NETWORK_SELECTION_MODE_MANUAL, arg, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
    /**
     * Get the manual network selection
     *
     * @param subId the id of the subscription.
     *
     * @return the previously saved user selected PLMN
     */
    @Override
    public String getManualNetworkSelectionPlmn(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "getManualNetworkSelectionPlmn");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getManualNetworkSelectionPlmn");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!isActiveSubscription(subId)) {
                throw new IllegalArgumentException("Invalid Subscription Id: " + subId);
            }

            final Phone phone = getPhone(subId);
            if (phone == null) {
                throw new IllegalArgumentException("Invalid Subscription Id: " + subId);
            }
            OperatorInfo networkSelection = phone.getSavedNetworkSelection();
            return TextUtils.isEmpty(networkSelection.getOperatorNumeric())
                    ? phone.getManualNetworkSelectionPlmn() : networkSelection.getOperatorNumeric();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Scans for available networks.
     */
    @Override
    public CellNetworkScanResult getCellNetworkScanResults(int subId, String callingPackage,
            String callingFeatureId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "getCellNetworkScanResults");
        LocationAccessPolicy.LocationPermissionResult locationResult =
                LocationAccessPolicy.checkLocationPermission(mApp,
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(callingPackage)
                                .setCallingFeatureId(callingFeatureId)
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("getCellNetworkScanResults")
                                .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.Q)
                                .setMinSdkVersionForEnforcement(Build.VERSION_CODES.Q)
                                .build());
        switch (locationResult) {
            case DENIED_HARD:
                throw new SecurityException("Not allowed to access scan results -- location");
            case DENIED_SOFT:
                return null;
        }

        long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("getCellNetworkScanResults: subId " + subId);
            return (CellNetworkScanResult) sendRequest(
                    CMD_PERFORM_NETWORK_SCAN, null, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the call forwarding info, given the call forwarding reason.
     */
    @Override
    public void getCallForwarding(int subId, int callForwardingReason,
            ICallForwardingInfoCallback callback) {
        enforceReadPrivilegedPermission("getCallForwarding");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getCallForwarding");

        long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) {
                log("getCallForwarding: subId " + subId
                        + " callForwardingReason" + callForwardingReason);
            }

            Phone phone = getPhone(subId);
            if (phone == null) {
                try {
                    callback.onError(
                            TelephonyManager.CallForwardingInfoCallback.RESULT_ERROR_UNKNOWN);
                } catch (RemoteException e) {
                    // ignore
                }
                return;
            }

            Pair<Integer, TelephonyManager.CallForwardingInfoCallback> argument = Pair.create(
                    callForwardingReason, new TelephonyManager.CallForwardingInfoCallback() {
                        @Override
                        public void onCallForwardingInfoAvailable(CallForwardingInfo info) {
                            try {
                                callback.onCallForwardingInfoAvailable(info);
                            } catch (RemoteException e) {
                                // ignore
                            }
                        }

                        @Override
                        public void onError(int error) {
                            try {
                                callback.onError(error);
                            } catch (RemoteException e) {
                                // ignore
                            }
                        }
                    });
            sendRequestAsync(CMD_GET_CALL_FORWARDING, argument, phone, null);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the voice call forwarding info including status (enable/disable), call forwarding
     * reason, the number to forward, and the timeout before the forwarding is attempted.
     */
    @Override
    public void setCallForwarding(int subId, CallForwardingInfo callForwardingInfo,
            IIntegerConsumer callback) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "setCallForwarding");

        long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) {
                log("setCallForwarding: subId " + subId
                        + " callForwardingInfo" + callForwardingInfo);
            }

            Phone phone = getPhone(subId);
            if (phone == null) {
                try {
                    callback.accept(
                            TelephonyManager.CallForwardingInfoCallback.RESULT_ERROR_UNKNOWN);
                } catch (RemoteException e) {
                    // ignore
                }
                return;
            }

            Pair<CallForwardingInfo, Consumer<Integer>> arguments = Pair.create(callForwardingInfo,
                    FunctionalUtils.ignoreRemoteException(callback::accept));

            sendRequestAsync(CMD_SET_CALL_FORWARDING, arguments, phone, null);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the call waiting status for a subId.
     */
    @Override
    public void getCallWaitingStatus(int subId, IIntegerConsumer callback) {
        enforceReadPrivilegedPermission("getCallWaitingStatus");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getCallWaitingStatus");

        long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) {
                try {
                    callback.accept(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
                } catch (RemoteException e) {
                    // ignore
                }
                return;
            }
            CarrierConfigManager configManager = new CarrierConfigManager(phone.getContext());
            PersistableBundle c = configManager.getConfigForSubId(subId);
            boolean requireUssd = c.getBoolean(
                    CarrierConfigManager.KEY_USE_CALL_WAITING_USSD_BOOL, false);

            if (DBG) log("getCallWaitingStatus: subId " + subId);
            if (requireUssd) {
                CarrierXmlParser carrierXmlParser = new CarrierXmlParser(phone.getContext(),
                        getSubscriptionCarrierId(subId));
                String newUssdCommand = "";
                try {
                    newUssdCommand = carrierXmlParser.getFeature(
                                    CarrierXmlParser.FEATURE_CALL_WAITING)
                            .makeCommand(CarrierXmlParser.SsEntry.SSAction.QUERY, null);
                } catch (NullPointerException e) {
                    loge("Failed to generate USSD number" + e);
                }
                ResultReceiver wrappedCallback = new CallWaitingUssdResultReceiver(
                        mMainThreadHandler, callback, carrierXmlParser,
                        CarrierXmlParser.SsEntry.SSAction.QUERY);
                final String ussdCommand = newUssdCommand;
                Executors.newSingleThreadExecutor().execute(() -> {
                    handleUssdRequest(subId, ussdCommand, wrappedCallback);
                });
            } else {
                Consumer<Integer> argument = FunctionalUtils.ignoreRemoteException(
                        callback::accept);
                sendRequestAsync(CMD_GET_CALL_WAITING, argument, phone, null);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets whether call waiting is enabled for a given subId.
     */
    @Override
    public void setCallWaitingStatus(int subId, boolean enable, IIntegerConsumer callback) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "setCallWaitingStatus");

        long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("setCallWaitingStatus: subId " + subId + " enable: " + enable);

            Phone phone = getPhone(subId);
            if (phone == null) {
                try {
                    callback.accept(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
                } catch (RemoteException e) {
                    // ignore
                }
                return;
            }

            CarrierConfigManager configManager = new CarrierConfigManager(phone.getContext());
            PersistableBundle c = configManager.getConfigForSubId(subId);
            boolean requireUssd = c.getBoolean(
                    CarrierConfigManager.KEY_USE_CALL_WAITING_USSD_BOOL, false);

            if (DBG) log("getCallWaitingStatus: subId " + subId);
            if (requireUssd) {
                CarrierXmlParser carrierXmlParser = new CarrierXmlParser(phone.getContext(),
                        getSubscriptionCarrierId(subId));
                CarrierXmlParser.SsEntry.SSAction ssAction =
                        enable ? CarrierXmlParser.SsEntry.SSAction.UPDATE_ACTIVATE
                                : CarrierXmlParser.SsEntry.SSAction.UPDATE_DEACTIVATE;
                String newUssdCommand = "";
                try {
                    newUssdCommand = carrierXmlParser.getFeature(
                                    CarrierXmlParser.FEATURE_CALL_WAITING)
                            .makeCommand(ssAction, null);
                } catch (NullPointerException e) {
                    loge("Failed to generate USSD number" + e);
                }
                ResultReceiver wrappedCallback = new CallWaitingUssdResultReceiver(
                        mMainThreadHandler, callback, carrierXmlParser, ssAction);
                final String ussdCommand = newUssdCommand;
                Executors.newSingleThreadExecutor().execute(() -> {
                    handleUssdRequest(subId, ussdCommand, wrappedCallback);
                });
            } else {
                Pair<Boolean, Consumer<Integer>> arguments = Pair.create(enable,
                        FunctionalUtils.ignoreRemoteException(callback::accept));

                sendRequestAsync(CMD_SET_CALL_WAITING, arguments, phone, null);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Starts a new network scan and returns the id of this scan.
     *
     * @param subId id of the subscription
     * @param renounceFineLocationAccess Set this to true if the caller would not like to receive
     * location related information which will be sent if the caller already possess
     * {@android.Manifest.permission.ACCESS_FINE_LOCATION} and do not renounce the permission
     * @param request contains the radio access networks with bands/channels to scan
     * @param messenger callback messenger for scan results or errors
     * @param binder for the purpose of auto clean when the user thread crashes
     * @return the id of the requested scan which can be used to stop the scan.
     */
    @Override
    public int requestNetworkScan(int subId, boolean renounceFineLocationAccess,
            NetworkScanRequest request, Messenger messenger,
            IBinder binder, String callingPackage, String callingFeatureId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "requestNetworkScan");
        LocationAccessPolicy.LocationPermissionResult locationResult =
                LocationAccessPolicy.LocationPermissionResult.DENIED_HARD;
        if (!renounceFineLocationAccess) {
            locationResult = LocationAccessPolicy.checkLocationPermission(mApp,
                    new LocationAccessPolicy.LocationPermissionQuery.Builder()
                            .setCallingPackage(callingPackage)
                            .setCallingFeatureId(callingFeatureId)
                            .setCallingPid(Binder.getCallingPid())
                            .setCallingUid(Binder.getCallingUid())
                            .setMethod("requestNetworkScan")
                            .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                            .setMinSdkVersionForCoarse(Build.VERSION_CODES.Q)
                            .setMinSdkVersionForEnforcement(Build.VERSION_CODES.Q)
                            .build());
        }
        if (locationResult != LocationAccessPolicy.LocationPermissionResult.ALLOWED) {
            SecurityException e = checkNetworkRequestForSanitizedLocationAccess(
                    request, subId, callingPackage);
            if (e != null) {
                if (locationResult == LocationAccessPolicy.LocationPermissionResult.DENIED_HARD) {
                    throw e;
                } else {
                    loge(e.getMessage());
                    return TelephonyScanManager.INVALID_SCAN_ID;
                }
            }
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "requestNetworkScan");

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        final long identity = Binder.clearCallingIdentity();
        try {
            return mNetworkScanRequestTracker.startNetworkScan(
                    renounceFineLocationAccess, request, messenger, binder,
                    getPhoneFromSubIdOrDefault(subId),
                    callingUid, callingPid, callingPackage);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private SecurityException checkNetworkRequestForSanitizedLocationAccess(
            NetworkScanRequest request, int subId, String callingPackage) {
        boolean hasCarrierPriv;
        final long identity = Binder.clearCallingIdentity();
        try {
            hasCarrierPriv = checkCarrierPrivilegesForPackage(subId, callingPackage)
                    == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        boolean hasNetworkScanPermission =
                mApp.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_SCAN)
                        == PERMISSION_GRANTED;

        if (!hasCarrierPriv && !hasNetworkScanPermission) {
            return new SecurityException("permission.NETWORK_SCAN or carrier privileges is needed"
                    + " for network scans without location access.");
        }

        if (request.getSpecifiers() != null && request.getSpecifiers().length > 0) {
            for (RadioAccessSpecifier ras : request.getSpecifiers()) {
                if (ras.getChannels() != null && ras.getChannels().length > 0) {
                    return new SecurityException("Specific channels must not be"
                            + " scanned without location access.");
                }
            }
        }

        return null;
    }

    /**
     * Stops an existing network scan with the given scanId.
     *
     * @param subId id of the subscription
     * @param scanId id of the scan that needs to be stopped
     */
    @Override
    public void stopNetworkScan(int subId, int scanId) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "stopNetworkScan");

        int callingUid = Binder.getCallingUid();
        final long identity = Binder.clearCallingIdentity();
        try {
            mNetworkScanRequestTracker.stopNetworkScan(scanId, callingUid);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the allowed network types bitmask.
     *
     * @return the allowed network types bitmask, defined in RILConstants.java.
     */
    @Override
    public int getAllowedNetworkTypesBitmask(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "getAllowedNetworkTypesBitmask");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getAllowedNetworkTypesBitmask");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DBG) log("getAllowedNetworkTypesBitmask");
            int[] result = (int[]) sendRequest(CMD_GET_ALLOWED_NETWORK_TYPES_BITMASK, null, subId);
            int networkTypesBitmask = (result != null ? result[0] : -1);
            if (DBG) log("getAllowedNetworkTypesBitmask: " + networkTypesBitmask);
            return networkTypesBitmask;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the allowed network types for certain reason.
     *
     * @param subId the id of the subscription.
     * @param reason the reason the allowed network type change is taking place
     * @return the allowed network types.
     */
    @Override
    public long getAllowedNetworkTypesForReason(int subId,
            @TelephonyManager.AllowedNetworkTypesReason int reason) {
        TelephonyPermissions.enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                mApp, subId, "getAllowedNetworkTypesForReason");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getAllowedNetworkTypesForReason");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getPhoneFromSubIdOrDefault(subId).getAllowedNetworkTypes(reason);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Enable/Disable E-UTRA-NR Dual Connectivity
     * @param subId subscription id of the sim card
     * @param nrDualConnectivityState expected NR dual connectivity state
     * This can be passed following states
     * <ol>
     * <li>Enable NR dual connectivity {@link TelephonyManager#NR_DUAL_CONNECTIVITY_ENABLE}
     * <li>Disable NR dual connectivity {@link TelephonyManager#NR_DUAL_CONNECTIVITY_DISABLE}
     * <li>Disable NR dual connectivity and force secondary cell to be released
     * {@link TelephonyManager#NR_DUAL_CONNECTIVITY_DISABLE_IMMEDIATE}
     * </ol>
     * @return operation result.
     */
    @Override
    public int setNrDualConnectivityState(int subId,
            @TelephonyManager.NrDualConnectivityState int nrDualConnectivityState) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "enableNRDualConnectivity");
        if (!isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE)) {
            return TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED;
        }

        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            int result = (int) sendRequest(CMD_ENABLE_NR_DUAL_CONNECTIVITY,
                    nrDualConnectivityState, subId,
                    workSource);
            if (DBG) log("enableNRDualConnectivity result: " + result);
            return result;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Is E-UTRA-NR Dual Connectivity enabled
     * @return true if dual connectivity is enabled else false
     */
    @Override
    public boolean isNrDualConnectivityEnabled(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "isNRDualConnectivityEnabled");
        if (!isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE)) {
            return false;
        }
        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            boolean isEnabled = (boolean) sendRequest(CMD_IS_NR_DUAL_CONNECTIVITY_ENABLED,
                    null, subId, workSource);
            if (DBG) log("isNRDualConnectivityEnabled: " + isEnabled);
            return isEnabled;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the allowed network types of the device and
     * provide the reason triggering the allowed network change.
     *
     * @param subId the id of the subscription.
     * @param reason the reason the allowed network type change is taking place
     * @param allowedNetworkTypes the allowed network types.
     * @return true on success; false on any failure.
     */
    @Override
    public boolean setAllowedNetworkTypesForReason(int subId,
            @TelephonyManager.AllowedNetworkTypesReason int reason,
            @TelephonyManager.NetworkTypeBitMask long allowedNetworkTypes) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setAllowedNetworkTypesForReason");
        // If the caller only has carrier privileges, then they should not be able to override
        // any network types which were set for security reasons.
        if (mApp.checkCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE)
                != PERMISSION_GRANTED
                && reason == TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G) {
            throw new SecurityException(
                    "setAllowedNetworkTypesForReason cannot be called with carrier privileges for"
                            + " reason " + reason);
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "setAllowedNetworkTypesForReason");

        if (!TelephonyManager.isValidAllowedNetworkTypesReason(reason)) {
            loge("setAllowedNetworkTypesForReason: Invalid allowed network type reason: " + reason);
            return false;
        }
        if (!SubscriptionManager.isUsableSubscriptionId(subId)) {
            loge("setAllowedNetworkTypesForReason: Invalid subscriptionId:" + subId);
            return false;
        }

        log("setAllowedNetworkTypesForReason: subId=" + subId + ", reason=" + reason + " value: "
                + TelephonyManager.convertNetworkTypeBitmaskToString(allowedNetworkTypes));

        Phone phone = getPhone(subId);
        if (phone == null) {
            return false;
        }

        if (allowedNetworkTypes == phone.getAllowedNetworkTypes(reason)) {
            log("setAllowedNetworkTypesForReason: " + reason + "does not change value");
            return true;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            Boolean success = (Boolean) sendRequest(
                    CMD_SET_ALLOWED_NETWORK_TYPES_FOR_REASON,
                    new Pair<Integer, Long>(reason, allowedNetworkTypes), subId);

            if (DBG) log("setAllowedNetworkTypesForReason: " + (success ? "ok" : "fail"));
            return success;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Check whether DUN APN is required for tethering with subId.
     *
     * @param subId the id of the subscription to require tethering.
     * @return {@code true} if DUN APN is required for tethering.
     * @hide
     */
    @Override
    public boolean isTetheringApnRequiredForSubscriber(int subId) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isTetheringApnRequiredForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        final Phone phone = getPhone(subId);
        try {
            if (phone != null) {
                return phone.hasMatchedTetherApnSetting();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the user enabled state of Mobile Data.
     *
     * TODO: remove and use isUserDataEnabled.
     * This can't be removed now because some vendor codes
     * calls through ITelephony directly while they should
     * use TelephonyManager.
     *
     * @return true on enabled
     */
    @Override
    public boolean getDataEnabled(int subId) {
        return isUserDataEnabled(subId);
    }

    /**
     * Get whether mobile data is enabled per user setting.
     *
     * There are other factors deciding whether mobile data is actually enabled, but they are
     * not considered here. See {@link #isDataEnabled(int)} for more details.
     *
     * Accepts either READ_BASIC_PHONE_STATE, ACCESS_NETWORK_STATE, MODIFY_PHONE_STATE
     * or carrier privileges.
     *
     * @return {@code true} if data is enabled else {@code false}
     */
    @Override
    public boolean isUserDataEnabled(int subId) {
        String functionName = "isUserDataEnabled";
        try {
            try {
                mApp.enforceCallingOrSelfPermission(permission.READ_BASIC_PHONE_STATE,
                        functionName);
            } catch (SecurityException e) {
                mApp.enforceCallingOrSelfPermission(permission.ACCESS_NETWORK_STATE, functionName);
            }
        } catch (SecurityException e) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, subId, functionName);

        }

        final long identity = Binder.clearCallingIdentity();
        try {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (DBG) log("isUserDataEnabled: subId=" + subId + " phoneId=" + phoneId);
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                boolean retVal = phone.isUserDataEnabled();
                if (DBG) log("isUserDataEnabled: subId=" + subId + " retVal=" + retVal);
                return retVal;
            } else {
                if (DBG) loge("isUserDataEnabled: no phone subId=" + subId + " retVal=false");
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Checks if the device is capable of mobile data by considering whether whether the
     * user has enabled mobile data, whether the carrier has enabled mobile data, and
     * whether the network policy allows data connections.
     *
     * @return {@code true} if the overall data connection is capable; {@code false} if not.
     */
    @Override
    public boolean isDataEnabled(int subId) {
        String functionName = "isDataEnabled";
        try {
            try {
                mApp.enforceCallingOrSelfPermission(
                        android.Manifest.permission.ACCESS_NETWORK_STATE,
                        functionName);
            } catch (SecurityException e) {
                try {
                    mApp.enforceCallingOrSelfPermission(
                            android.Manifest.permission.READ_PHONE_STATE,
                            functionName);
                } catch (SecurityException e2) {
                    mApp.enforceCallingOrSelfPermission(
                            permission.READ_BASIC_PHONE_STATE, functionName);
                }
            }
        } catch (SecurityException e) {
            enforceReadPrivilegedPermission(functionName);
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isDataEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                boolean retVal = phone.getDataSettingsManager().isDataEnabled();
                if (DBG) log("isDataEnabled: " + retVal + ", subId=" + subId);
                return retVal;
            } else {
                if (DBG) loge("isDataEnabled: no phone subId=" + subId + " retVal=false");
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Check if data is enabled for a specific reason
     * @param subId Subscription index
     * @param reason the reason the data enable change is taking place
     * @return {@code true} if the overall data is enabled; {@code false} if not.
     */
    @Override
    public boolean isDataEnabledForReason(int subId,
            @TelephonyManager.DataEnabledReason int reason) {
        String functionName = "isDataEnabledForReason";
        try {
            try {
                mApp.enforceCallingOrSelfPermission(
                        android.Manifest.permission.ACCESS_NETWORK_STATE,
                        functionName);
            } catch (SecurityException e) {
                mApp.enforceCallingOrSelfPermission(permission.READ_BASIC_PHONE_STATE,
                        functionName);
            }
        } catch (SecurityException e) {
            try {
                mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE,
                        functionName);
            } catch (SecurityException e2) {
                TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                        mApp, subId, functionName);
            }
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isDataEnabledForReason");

        final long identity = Binder.clearCallingIdentity();
        try {
            int phoneId = SubscriptionManager.getPhoneId(subId);
            if (DBG) {
                log("isDataEnabledForReason: subId=" + subId + " phoneId=" + phoneId
                        + " reason=" + reason);
            }
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone != null) {
                boolean retVal;
                retVal = phone.getDataSettingsManager().isDataEnabledForReason(reason);
                if (DBG) log("isDataEnabledForReason: retVal=" + retVal);
                return retVal;
            } else {
                if (DBG) {
                    loge("isDataEnabledForReason: no phone subId="
                            + subId + " retVal=false");
                }
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCarrierPrivilegeStatus(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getCarrierPrivilegeStatus");

        // No permission needed; this only lets the caller inspect their own status.
        return getCarrierPrivilegeStatusForUidWithPermission(subId, Binder.getCallingUid());
    }

    @Override
    public int getCarrierPrivilegeStatusForUid(int subId, int uid) {
        enforceReadPrivilegedPermission("getCarrierPrivilegeStatusForUid");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getCarrierPrivilegeStatusForUid");

        return getCarrierPrivilegeStatusForUidWithPermission(subId, uid);
    }

    private int getCarrierPrivilegeStatusForUidWithPermission(int subId, int uid) {
        Phone phone = getPhone(subId);
        if (phone == null) {
            loge("getCarrierPrivilegeStatusForUid: Invalid subId");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        }
        CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
        if (cpt == null) {
            loge("getCarrierPrivilegeStatusForUid: No CarrierPrivilegesTracker");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
        return cpt.getCarrierPrivilegeStatusForUid(uid);
    }

    @Override
    public int checkCarrierPrivilegesForPackage(int subId, String pkgName) {
        enforceReadPrivilegedPermission("checkCarrierPrivilegesForPackage");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "checkCarrierPrivilegesForPackage");

        if (TextUtils.isEmpty(pkgName)) {
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        }
        Phone phone = getPhone(subId);
        if (phone == null) {
            loge("checkCarrierPrivilegesForPackage: Invalid subId");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        }
        CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
        if (cpt == null) {
            loge("checkCarrierPrivilegesForPackage: No CarrierPrivilegesTracker");
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
        return cpt.getCarrierPrivilegeStatusForPackage(pkgName);
    }

    @Override
    public int checkCarrierPrivilegesForPackageAnyPhone(String pkgName) {
        enforceReadPrivilegedPermission("checkCarrierPrivilegesForPackageAnyPhone");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "checkCarrierPrivilegesForPackageAnyPhone");

        return checkCarrierPrivilegesForPackageAnyPhoneWithPermission(pkgName);
    }

    private int checkCarrierPrivilegesForPackageAnyPhoneWithPermission(String pkgName) {
        if (TextUtils.isEmpty(pkgName)) {
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        }
        int result = TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        for (int phoneId = 0; phoneId < TelephonyManager.getDefault().getPhoneCount(); phoneId++) {
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone == null) {
                continue;
            }
            CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
            if (cpt == null) {
                continue;
            }
            result = cpt.getCarrierPrivilegeStatusForPackage(pkgName);
            if (result == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
                break;
            }
        }
        return result;
    }

    @Override
    public List<String> getCarrierPackageNamesForIntentAndPhone(Intent intent, int phoneId) {
        enforceReadPrivilegedPermission("getCarrierPackageNamesForIntentAndPhone");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "getCarrierPackageNamesForIntentAndPhone");

        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return Collections.emptyList();
        }
        CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
        if (cpt == null) {
            return Collections.emptyList();
        }
        return cpt.getCarrierPackageNamesForIntent(intent);
    }

    @Override
    public List<String> getPackagesWithCarrierPrivileges(int phoneId) {
        enforceReadPrivilegedPermission("getPackagesWithCarrierPrivileges");
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return Collections.emptyList();
        }
        CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
        if (cpt == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(cpt.getPackagesWithCarrierPrivileges());
    }

    @Override
    public List<String> getPackagesWithCarrierPrivilegesForAllPhones() {
        enforceReadPrivilegedPermission("getPackagesWithCarrierPrivilegesForAllPhones");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "getPackagesWithCarrierPrivilegesForAllPhones");

        Set<String> privilegedPackages = new ArraySet<>();
        final long identity = Binder.clearCallingIdentity();
        try {
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                privilegedPackages.addAll(getPackagesWithCarrierPrivileges(i));
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return new ArrayList<>(privilegedPackages);
    }

    @Override
    public @Nullable String getCarrierServicePackageNameForLogicalSlot(int logicalSlotIndex) {
        enforceReadPrivilegedPermission("getCarrierServicePackageNameForLogicalSlot");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "getCarrierServicePackageNameForLogicalSlot");

        final Phone phone = PhoneFactory.getPhone(logicalSlotIndex);
        if (phone == null) {
            return null;
        }
        final CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
        if (cpt == null) {
            return null;
        }
        return cpt.getCarrierServicePackageName();
    }

    private String getIccId(int subId) {
        final Phone phone = getPhone(subId);
        UiccPort port = phone == null ? null : phone.getUiccPort();
        if (port == null) {
            return null;
        }
        String iccId = port.getIccId();
        if (TextUtils.isEmpty(iccId)) {
            return null;
        }
        return iccId;
    }

    @Override
    public void setCallComposerStatus(int subId, int status) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "setCallComposerStatus");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                Phone defaultPhone = phone.getImsPhone();
                if (defaultPhone != null && defaultPhone.getPhoneType() == PHONE_TYPE_IMS) {
                    ImsPhone imsPhone = (ImsPhone) defaultPhone;
                    imsPhone.setCallComposerStatus(status);
                    ImsManager.getInstance(mApp, getSlotIndexOrException(subId))
                            .updateImsServiceConfig();
                }
            }
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        }  finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCallComposerStatus(int subId) {
        enforceReadPrivilegedPermission("getCallComposerStatus");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getCallComposerStatus");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                Phone defaultPhone = phone.getImsPhone();
                if (defaultPhone != null && defaultPhone.getPhoneType() == PHONE_TYPE_IMS) {
                    ImsPhone imsPhone = (ImsPhone) defaultPhone;
                    return imsPhone.getCallComposerStatus();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return TelephonyManager.CALL_COMPOSER_STATUS_OFF;
    }

    @Override
    public boolean setLine1NumberForDisplayForSubscriber(int subId, String alphaTag,
            String number) {
        TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mApp,
                subId, "setLine1NumberForDisplayForSubscriber");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "setLine1NumberForDisplayForSubscriber");

        final long identity = Binder.clearCallingIdentity();
        try {
            final String iccId = getIccId(subId);
            final Phone phone = getPhone(subId);
            if (phone == null) {
                return false;
            }
            final String subscriberId = phone.getSubscriberId();

            if (DBG_MERGE) {
                Rlog.d(LOG_TAG, "Setting line number for ICC=" + iccId + ", subscriberId="
                        + subscriberId + " to " + number);
            }

            if (TextUtils.isEmpty(iccId)) {
                return false;
            }

            final SharedPreferences.Editor editor = mTelephonySharedPreferences.edit();

            final String alphaTagPrefKey = PREF_CARRIERS_ALPHATAG_PREFIX + iccId;
            if (alphaTag == null) {
                editor.remove(alphaTagPrefKey);
            } else {
                editor.putString(alphaTagPrefKey, alphaTag);
            }

            // Record both the line number and IMSI for this ICCID, since we need to
            // track all merged IMSIs based on line number
            final String numberPrefKey = PREF_CARRIERS_NUMBER_PREFIX + iccId;
            final String subscriberPrefKey = PREF_CARRIERS_SUBSCRIBER_PREFIX + iccId;
            if (number == null) {
                editor.remove(numberPrefKey);
                editor.remove(subscriberPrefKey);
            } else {
                editor.putString(numberPrefKey, number);
                editor.putString(subscriberPrefKey, subscriberId);
            }

            editor.commit();
            return true;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getLine1NumberForDisplay(int subId, String callingPackage,
            String callingFeatureId) {
        // This is open to apps with WRITE_SMS.
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneNumber(
                mApp, subId, callingPackage, callingFeatureId, "getLine1NumberForDisplay")) {
            if (DBG_MERGE) log("getLine1NumberForDisplay returning null due to permission");
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getLine1NumberForDisplay");

        final long identity = Binder.clearCallingIdentity();
        try {
            String iccId = getIccId(subId);
            if (iccId != null) {
                String numberPrefKey = PREF_CARRIERS_NUMBER_PREFIX + iccId;
                if (DBG_MERGE) {
                    log("getLine1NumberForDisplay returning "
                            + mTelephonySharedPreferences.getString(numberPrefKey, null));
                }
                return mTelephonySharedPreferences.getString(numberPrefKey, null);
            }
            if (DBG_MERGE) log("getLine1NumberForDisplay returning null as iccId is null");
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getLine1AlphaTagForDisplay(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "getLine1AlphaTagForDisplay")) {
            return null;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            String iccId = getIccId(subId);
            if (iccId != null) {
                String alphaTagPrefKey = PREF_CARRIERS_ALPHATAG_PREFIX + iccId;
                return mTelephonySharedPreferences.getString(alphaTagPrefKey, null);
            }
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String[] getMergedSubscriberIds(int subId, String callingPackage,
            String callingFeatureId) {
        // This API isn't public, so no need to provide a valid subscription ID - we're not worried
        // about carrier-privileged callers not having access.
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, SubscriptionManager.INVALID_SUBSCRIPTION_ID, callingPackage,
                callingFeatureId, "getMergedSubscriberIds")) {
            return null;
        }

        // Clear calling identity, when calling TelephonyManager, because callerUid must be
        // the process, where TelephonyManager was instantiated.
        // Otherwise AppOps check will fail.
        final long identity  = Binder.clearCallingIdentity();
        try {
            final Context context = mApp;
            final TelephonyManager tele = TelephonyManager.from(context);
            final SubscriptionManager sub = SubscriptionManager.from(context);

            // Figure out what subscribers are currently active
            final ArraySet<String> activeSubscriberIds = new ArraySet<>();

            // Only consider subs which match the current subId
            // This logic can be simplified. See b/131189269 for progress.
            if (isActiveSubscription(subId)) {
                activeSubscriberIds.add(tele.getSubscriberId(subId));
            }

            // First pass, find a number override for an active subscriber
            String mergeNumber = null;
            final Map<String, ?> prefs = mTelephonySharedPreferences.getAll();
            for (String key : prefs.keySet()) {
                if (key.startsWith(PREF_CARRIERS_SUBSCRIBER_PREFIX)) {
                    final String subscriberId = (String) prefs.get(key);
                    if (activeSubscriberIds.contains(subscriberId)) {
                        final String iccId = key.substring(
                                PREF_CARRIERS_SUBSCRIBER_PREFIX.length());
                        final String numberKey = PREF_CARRIERS_NUMBER_PREFIX + iccId;
                        mergeNumber = (String) prefs.get(numberKey);
                        if (DBG_MERGE) {
                            Rlog.d(LOG_TAG, "Found line number " + mergeNumber
                                    + " for active subscriber " + subscriberId);
                        }
                        if (!TextUtils.isEmpty(mergeNumber)) {
                            break;
                        }
                    }
                }
            }

            // Shortcut when no active merged subscribers
            if (TextUtils.isEmpty(mergeNumber)) {
                return null;
            }

            // Second pass, find all subscribers under that line override
            final ArraySet<String> result = new ArraySet<>();
            for (String key : prefs.keySet()) {
                if (key.startsWith(PREF_CARRIERS_NUMBER_PREFIX)) {
                    final String number = (String) prefs.get(key);
                    if (mergeNumber.equals(number)) {
                        final String iccId = key.substring(PREF_CARRIERS_NUMBER_PREFIX.length());
                        final String subscriberKey = PREF_CARRIERS_SUBSCRIBER_PREFIX + iccId;
                        final String subscriberId = (String) prefs.get(subscriberKey);
                        if (!TextUtils.isEmpty(subscriberId)) {
                            result.add(subscriberId);
                        }
                    }
                }
            }

            final String[] resultArray = result.toArray(new String[result.size()]);
            Arrays.sort(resultArray);
            if (DBG_MERGE) {
                Rlog.d(LOG_TAG,
                        "Found subscribers " + Arrays.toString(resultArray) + " after merge");
            }
            return resultArray;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String[] getMergedImsisFromGroup(int subId, String callingPackage) {
        enforceReadPrivilegedPermission("getMergedImsisFromGroup");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getMergedImsisFromGroup");

        final long identity = Binder.clearCallingIdentity();
        try {
            final TelephonyManager telephonyManager = mApp.getSystemService(
                    TelephonyManager.class);
            String subscriberId = telephonyManager.getSubscriberId(subId);
            if (subscriberId == null) {
                if (DBG) {
                    log("getMergedImsisFromGroup can't find subscriberId for subId "
                            + subId);
                }
                return null;
            }

            final SubscriptionInfo info = getSubscriptionManagerService()
                    .getSubscriptionInfo(subId);
            ParcelUuid groupUuid = info.getGroupUuid();
            // If it doesn't belong to any group, return just subscriberId of itself.
            if (groupUuid == null) {
                return new String[]{subscriberId};
            }

            // Get all subscriberIds from the group.
            final List<String> mergedSubscriberIds = new ArrayList<>();
            List<SubscriptionInfo> groupInfos = getSubscriptionManagerService()
                    .getSubscriptionsInGroup(groupUuid, mApp.getOpPackageName(),
                            mApp.getAttributionTag());
            for (SubscriptionInfo subInfo : groupInfos) {
                subscriberId = telephonyManager.getSubscriberId(subInfo.getSubscriptionId());
                if (subscriberId != null) {
                    mergedSubscriberIds.add(subscriberId);
                }
            }

            return mergedSubscriberIds.toArray(new String[mergedSubscriberIds.size()]);
        } finally {
            Binder.restoreCallingIdentity(identity);

        }
    }

    @Override
    public boolean setOperatorBrandOverride(int subId, String brand) {
        TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(mApp,
                subId, "setOperatorBrandOverride");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "setOperatorBrandOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            return phone == null ? false : phone.setOperatorBrandOverride(brand);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean setRoamingOverride(int subId, List<String> gsmRoamingList,
            List<String> gsmNonRoamingList, List<String> cdmaRoamingList,
            List<String> cdmaNonRoamingList) {
        TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(
                mApp, subId, "setRoamingOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null) {
                return false;
            }
            return phone.setRoamingOverride(gsmRoamingList, gsmNonRoamingList, cdmaRoamingList,
                    cdmaNonRoamingList);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    @Deprecated
    public int invokeOemRilRequestRaw(byte[] oemReq, byte[] oemResp) {
        enforceModifyPermission();

        int returnValue = 0;
        try {
            AsyncResult result = (AsyncResult) sendRequest(CMD_INVOKE_OEM_RIL_REQUEST_RAW, oemReq);
            if(result.exception == null) {
                if (result.result != null) {
                    byte[] responseData = (byte[])(result.result);
                    if(responseData.length > oemResp.length) {
                        Log.w(LOG_TAG, "Buffer to copy response too small: Response length is " +
                                responseData.length +  "bytes. Buffer Size is " +
                                oemResp.length + "bytes.");
                    }
                    System.arraycopy(responseData, 0, oemResp, 0, responseData.length);
                    returnValue = responseData.length;
                }
            } else {
                CommandException ex = (CommandException) result.exception;
                returnValue = ex.getCommandError().ordinal();
                if(returnValue > 0) returnValue *= -1;
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "sendOemRilRequestRaw: Runtime Exception");
            returnValue = (CommandException.Error.GENERIC_FAILURE.ordinal());
            if(returnValue > 0) returnValue *= -1;
        }

        return returnValue;
    }

    @Override
    public int getRadioAccessFamily(int phoneId, String callingPackage) {
        int raf = RadioAccessFamily.RAF_UNKNOWN;
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone == null) {
            return raf;
        }

        try {
            TelephonyPermissions
                    .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                            mApp, phone.getSubId(), "getRadioAccessFamily");
        } catch (SecurityException e) {
            EventLog.writeEvent(0x534e4554, "150857259", -1, "Missing Permission");
            throw e;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getRadioAccessFamily");

        final long identity = Binder.clearCallingIdentity();
        try {
            raf = ProxyController.getInstance().getRadioAccessFamily(phoneId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return raf;
    }

    @Override
    public void uploadCallComposerPicture(int subscriptionId, String callingPackage,
            String contentType, ParcelFileDescriptor fd, ResultReceiver callback) {
        try {
            if (!Objects.equals(mApp.getPackageManager().getPackageUid(callingPackage, 0),
                    Binder.getCallingUid())) {
                throw new SecurityException("Invalid package:" + callingPackage);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("Invalid package:" + callingPackage);
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "uploadCallComposerPicture");

        RoleManager rm = mApp.getSystemService(RoleManager.class);
        List<String> dialerRoleHolders = rm.getRoleHolders(RoleManager.ROLE_DIALER);
        if (!dialerRoleHolders.contains(callingPackage)) {
            throw new SecurityException("App must be the dialer role holder to"
                    + " upload a call composer pic");
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    (int) TelephonyManager.getMaximumCallComposerPictureSize());
            InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd);
            boolean readUntilEnd = false;
            int totalBytesRead = 0;
            byte[] buffer = new byte[16 * 1024];
            while (true) {
                int numRead;
                try {
                    numRead = input.read(buffer);
                } catch (IOException e) {
                    try {
                        fd.checkError();
                        callback.send(TelephonyManager.CallComposerException.ERROR_INPUT_CLOSED,
                                null);
                    } catch (IOException e1) {
                        // This means that the other side closed explicitly with an error. If this
                        // happens, log and ignore.
                        loge("Remote end of call composer picture pipe closed: " + e1);
                    }
                    break;
                }
                if (numRead == -1) {
                    readUntilEnd = true;
                    break;
                }
                totalBytesRead += numRead;
                if (totalBytesRead > TelephonyManager.getMaximumCallComposerPictureSize()) {
                    loge("Too many bytes read for call composer picture: " + totalBytesRead);
                    try {
                        input.close();
                    } catch (IOException e) {
                        // ignore
                    }
                    break;
                }
                output.write(buffer, 0, numRead);
            }
            // Generally, the remote end will close the file descriptors. The only case where we
            // close is above, where the picture size is too big.

            try {
                fd.checkError();
            } catch (IOException e) {
                loge("Remote end for call composer closed with an error: " + e);
                return;
            }

            if (!readUntilEnd) {
                loge("Did not finish reading entire image; aborting");
                return;
            }

            ImageData imageData = new ImageData(output.toByteArray(), contentType, null);
            CallComposerPictureManager.getInstance(mApp, subscriptionId).handleUploadToServer(
                    new CallComposerPictureTransfer.Factory() {},
                    imageData,
                    (result) -> {
                        if (result.first != null) {
                            ParcelUuid parcelUuid = new ParcelUuid(result.first);
                            Bundle outputResult = new Bundle();
                            outputResult.putParcelable(
                                    TelephonyManager.KEY_CALL_COMPOSER_PICTURE_HANDLE, parcelUuid);
                            callback.send(TelephonyManager.CallComposerException.SUCCESS,
                                    outputResult);
                        } else {
                            callback.send(result.second, null);
                        }
                    }
            );
        });
    }

    @Override
    public void enableVideoCalling(boolean enable) {
        final Phone defaultPhone = getDefaultPhone();
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_IMS, "enableVideoCalling");

        final long identity = Binder.clearCallingIdentity();
        try {
            ImsManager.getInstance(defaultPhone.getContext(),
                    defaultPhone.getPhoneId()).setVtSetting(enable);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isVideoCallingEnabled(String callingPackage, String callingFeatureId) {
        final Phone defaultPhone = getDefaultPhone();
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mApp, defaultPhone.getSubId(),
                callingPackage, callingFeatureId, "isVideoCallingEnabled")) {
            return false;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_IMS, "isVideoCallingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            // Check the user preference and the  system-level IMS setting. Even if the user has
            // enabled video calling, if IMS is disabled we aren't able to support video calling.
            // In the long run, we may instead need to check if there exists a connection service
            // which can support video calling.
            ImsManager imsManager =
                    ImsManager.getInstance(defaultPhone.getContext(), defaultPhone.getPhoneId());
            return imsManager.isVtEnabledByPlatform()
                    && imsManager.isEnhanced4gLteModeSettingEnabledByUser()
                    && imsManager.isVtEnabledByUser();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean canChangeDtmfToneLength(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "isVideoCallingEnabled")) {
            return false;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "canChangeDtmfToneLength");

        final long identity = Binder.clearCallingIdentity();
        try {
            CarrierConfigManager configManager =
                    (CarrierConfigManager) mApp.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            return configManager.getConfigForSubId(subId)
                    .getBoolean(CarrierConfigManager.KEY_DTMF_TYPE_ENABLED_BOOL);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isWorldPhone(int subId, String callingPackage, String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "isVideoCallingEnabled")) {
            return false;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY, "isWorldPhone");

        final long identity = Binder.clearCallingIdentity();
        try {
            CarrierConfigManager configManager =
                    (CarrierConfigManager) mApp.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            return configManager.getConfigForSubId(subId)
                    .getBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isTtyModeSupported() {
        TelecomManager telecomManager = mApp.getSystemService(TelecomManager.class);
        return telecomManager.isTtySupported();
    }

    @Override
    public boolean isHearingAidCompatibilitySupported() {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "isHearingAidCompatibilitySupported");

        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.getResources().getBoolean(R.bool.hac_enabled);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Determines whether the device currently supports RTT (Real-time text). Based both on carrier
     * support for the feature and device firmware support.
     *
     * @return {@code true} if the device and carrier both support RTT, {@code false} otherwise.
     */
    @Override
    public boolean isRttSupported(int subscriptionId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_IMS, "isRttSupported");

        final long identity = Binder.clearCallingIdentity();
        final Phone phone = getPhone(subscriptionId);
        if (phone == null) {
            loge("isRttSupported: no Phone found. Invalid subId:" + subscriptionId);
            return false;
        }
        try {
            boolean isCarrierSupported = mApp.getCarrierConfigForSubId(subscriptionId).getBoolean(
                    CarrierConfigManager.KEY_RTT_SUPPORTED_BOOL);
            boolean isDeviceSupported = (phone.getContext().getResources() != null)
                    ? phone.getContext().getResources().getBoolean(R.bool.config_support_rtt)
                    : false;
            return isCarrierSupported && isDeviceSupported;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Determines whether the user has turned on RTT. If the carrier wants to ignore the user-set
     * RTT setting, will return true if the device and carrier both support RTT.
     * Otherwise. only returns true if the device and carrier both also support RTT.
     */
    public boolean isRttEnabled(int subscriptionId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()) {
                if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS)) {
                    return false;
                }
            }

            boolean isRttSupported = isRttSupported(subscriptionId);
            boolean isUserRttSettingOn = Settings.Secure.getInt(
                    mApp.getContentResolver(), Settings.Secure.RTT_CALLING_MODE, 0) != 0;
            boolean shouldIgnoreUserRttSetting = mApp.getCarrierConfigForSubId(subscriptionId)
                    .getBoolean(CarrierConfigManager.KEY_IGNORE_RTT_MODE_SETTING_BOOL);
            return isRttSupported && (isUserRttSettingOn || shouldIgnoreUserRttSetting);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Deprecated
    @Override
    public String getDeviceId(String callingPackage) {
        return getDeviceIdWithFeature(callingPackage, null);
    }

    /**
     * Returns the unique device ID of phone, for example, the IMEI for
     * GSM and the MEID for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    @Override
    public String getDeviceIdWithFeature(String callingPackage, String callingFeatureId) {
        try {
            mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        } catch (SecurityException se) {
            EventLog.writeEvent(0x534e4554, "186530889", Binder.getCallingUid());
            throw new SecurityException("Package " + callingPackage + " does not belong to "
                    + Binder.getCallingUid());
        }
        final Phone phone = PhoneFactory.getPhone(0);
        if (phone == null) {
            return null;
        }
        int subId = phone.getSubId();
        if (!TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mApp, subId,
                callingPackage, callingFeatureId, "getDeviceId")) {
            return null;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return phone.getDeviceId();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * {@hide}
     * Returns the IMS Registration Status on a particular subid
     *
     * @param subId
     */
    public boolean isImsRegistered(int subId) {
        Phone phone = getPhone(subId);
        if (phone != null) {
            return phone.isImsRegistered();
        } else {
            return false;
        }
    }

    @Override
    public int getSubIdForPhoneAccountHandle(
            PhoneAccountHandle phoneAccountHandle, String callingPackage, String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mApp, getDefaultSubscription(),
                callingPackage, callingFeatureId, "getSubIdForPhoneAccountHandle")) {
            throw new SecurityException("Requires READ_PHONE_STATE permission.");
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            return PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccountHandle);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public @Nullable PhoneAccountHandle getPhoneAccountHandleForSubscriptionId(int subscriptionId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp,
                        subscriptionId,
                        "getPhoneAccountHandleForSubscriptionId, " + "subscriptionId: "
                                + subscriptionId);

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getPhoneAccountHandleForSubscriptionId");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subscriptionId);
            if (phone == null) {
                return null;
            }
            return PhoneUtils.makePstnPhoneAccountHandle(phone);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @return the VoWiFi calling availability.
     */
    public boolean isWifiCallingAvailable(int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.isWifiCallingEnabled();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @return the VT calling availability.
     */
    public boolean isVideoTelephonyAvailable(int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.isVideoEnabled();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @return the IMS registration technology for the MMTEL feature. Valid return values are
     * defined in {@link ImsRegistrationImplBase}.
     */
    public @ImsRegistrationImplBase.ImsRegistrationTech int getImsRegTechnologyForMmTel(int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                return phone.getImsRegistrationTech();
            } else {
                return ImsRegistrationImplBase.REGISTRATION_TECH_NONE;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void factoryReset(int subId, String callingPackage) {
        enforceSettingsPermission();

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY, "factoryReset");

        if (mUserManager.hasUserRestriction(UserManager.DISALLOW_NETWORK_RESET)) {
            return;
        }
        Phone defaultPhone = getDefaultPhone();
        if (defaultPhone != null) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, getDefaultPhone().getSubId(), "factoryReset");
        }
        final long identity = Binder.clearCallingIdentity();

        try {
            if (SubscriptionManager.isUsableSubIdValue(subId) && !mUserManager.hasUserRestriction(
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
                setDataEnabledForReason(subId, TelephonyManager.DATA_ENABLED_REASON_USER,
                        getDefaultDataEnabled(), callingPackage);
                setNetworkSelectionModeAutomatic(subId);
                Phone phone = getPhone(subId);
                cleanUpAllowedNetworkTypes(phone, subId);
                setDataRoamingEnabled(subId, phone == null ? false
                        : phone.getDataSettingsManager().isDefaultDataRoamingEnabled());
                getPhone(subId).resetCarrierKeysForImsiEncryption();
            }
            // There has been issues when Sms raw table somehow stores orphan
            // fragments. They lead to garbled message when new fragments come
            // in and combined with those stale ones. In case this happens again,
            // user can reset all network settings which will clean up this table.
            cleanUpSmsRawTable(getDefaultPhone().getContext());
            // Clean up IMS settings as well here.
            int slotId = getSlotIndex(subId);
            if (slotId > SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                ImsManager.getInstance(mApp, slotId).factoryReset();
            }

            if (defaultPhone == null) {
                return;
            }
            // Erase modem config if erase modem on network setting is enabled.
            String configValue = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_TELEPHONY,
                    RESET_NETWORK_ERASE_MODEM_CONFIG_ENABLED);
            if (configValue != null && Boolean.parseBoolean(configValue)) {
                sendEraseModemConfig(defaultPhone);
            }

            sendEraseDataInSharedPreferences(defaultPhone);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @VisibleForTesting
    void cleanUpAllowedNetworkTypes(Phone phone, int subId) {
        if (phone == null || !SubscriptionManager.isUsableSubscriptionId(subId)) {
            return;
        }
        long defaultNetworkType = RadioAccessFamily.getRafFromNetworkType(
                RILConstants.PREFERRED_NETWORK_MODE);
        SubscriptionManager.setSubscriptionProperty(subId,
                SubscriptionManager.ALLOWED_NETWORK_TYPES,
                "user=" + defaultNetworkType);
        phone.loadAllowedNetworksFromSubscriptionDatabase();
        phone.setAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                defaultNetworkType, null);
    }

    private void cleanUpSmsRawTable(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw/permanentDelete");
        resolver.delete(uri, null, null);
    }

    @Override
    public String getSimLocaleForSubscriber(int subId) {
        enforceReadPrivilegedPermission("getSimLocaleForSubscriber, subId: " + subId);

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getSimLocaleForSubscriber");

        final Phone phone = getPhone(subId);
        if (phone == null) {
            log("getSimLocaleForSubscriber, invalid subId");
            return null;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            SubscriptionInfo info = getSubscriptionManagerService().getActiveSubscriptionInfo(subId,
                    phone.getContext().getOpPackageName(),
                    phone.getContext().getAttributionTag());
            if (info == null) {
                log("getSimLocaleForSubscriber, inactive subId: " + subId);
                return null;
            }
            // Try and fetch the locale from the carrier properties or from the SIM language
            // preferences (EF-PL and EF-LI)...
            final int mcc = info.getMcc();
            String simLanguage = null;
            final Locale localeFromDefaultSim = phone.getLocaleFromSimAndCarrierPrefs();
            if (localeFromDefaultSim != null) {
                if (!localeFromDefaultSim.getCountry().isEmpty()) {
                    if (DBG) log("Using locale from subId: " + subId + " locale: "
                            + localeFromDefaultSim);
                    return matchLocaleFromSupportedLocaleList(phone, localeFromDefaultSim);
                } else {
                    simLanguage = localeFromDefaultSim.getLanguage();
                }
            }

            // The SIM language preferences only store a language (e.g. fr = French), not an
            // exact locale (e.g. fr_FR = French/France). So, if the locale returned from
            // the SIM and carrier preferences does not include a country we add the country
            // determined from the SIM MCC to provide an exact locale.
            final Locale mccLocale = LocaleUtils.getLocaleFromMcc(mApp, mcc, simLanguage);
            if (mccLocale != null) {
                if (DBG) log("No locale from SIM, using mcc locale:" + mccLocale);
                return matchLocaleFromSupportedLocaleList(phone, mccLocale);
            }

            if (DBG) log("No locale found - returning null");
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @VisibleForTesting
    String matchLocaleFromSupportedLocaleList(Phone phone, @NonNull Locale inputLocale) {
        String[] supportedLocale = com.android.internal.app.LocalePicker.getSupportedLocales(
                phone.getContext());
        for (String localeTag : supportedLocale) {
            if (LocaleList.matchesLanguageAndScript(inputLocale, Locale.forLanguageTag(localeTag))
                    && TextUtils.equals(inputLocale.getCountry(),
                    Locale.forLanguageTag(localeTag).getCountry())) {
                return localeTag;
            }
        }
        return inputLocale.toLanguageTag();
    }

    /**
     * NOTE: this method assumes permission checks are done and caller identity has been cleared.
     */
    private List<SubscriptionInfo> getActiveSubscriptionInfoListPrivileged() {
        return getSubscriptionManagerService().getActiveSubscriptionInfoList(
                mApp.getOpPackageName(), mApp.getAttributionTag(), true/*isForAllProfile*/);
    }

    private ActivityStatsTechSpecificInfo[] mLastModemActivitySpecificInfo = null;
    private ModemActivityInfo mLastModemActivityInfo = null;

    /**
     * Responds to the ResultReceiver with the {@link android.telephony.ModemActivityInfo} object
     * representing the state of the modem.
     *
     * NOTE: The underlying implementation clears the modem state, so there should only ever be one
     * caller to it. Everyone should call this class to get cumulative data.
     * @hide
     */
    @Override
    public void requestModemActivityInfo(ResultReceiver result) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY, "requestModemActivityInfo");

        WorkSource workSource = getWorkSource(Binder.getCallingUid());

        final long identity = Binder.clearCallingIdentity();
        try {
            sendRequestAsync(CMD_GET_MODEM_ACTIVITY_INFO, result, null, workSource);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    // Checks that ModemActivityInfo is valid. Sleep time and Idle time should be
    // less than total activity duration.
    private boolean isModemActivityInfoValid(ModemActivityInfo info) {
        if (info == null) {
            return false;
        }
        int activityDurationMs =
                (int) (info.getTimestampMillis() - mLastModemActivityInfo.getTimestampMillis());
        activityDurationMs += MODEM_ACTIVITY_TIME_OFFSET_CORRECTION_MS;

        int totalTxTimeMs = Arrays.stream(info.getTransmitTimeMillis()).sum();

        return (info.isValid()
                && (info.getSleepTimeMillis() <= activityDurationMs)
                && (info.getIdleTimeMillis() <= activityDurationMs));
    }

    private void updateLastModemActivityInfo(ModemActivityInfo info, int rat, int freq) {
        int[] mergedTxTimeMs = new int [ModemActivityInfo.getNumTxPowerLevels()];
        int[] txTimeMs = info.getTransmitTimeMillis(rat, freq);
        int[] lastModemTxTimeMs = mLastModemActivityInfo.getTransmitTimeMillis(rat, freq);

        for (int lvl = 0; lvl < mergedTxTimeMs.length; lvl++) {
            mergedTxTimeMs[lvl] = txTimeMs[lvl] + lastModemTxTimeMs[lvl];
        }

        mLastModemActivityInfo.setTransmitTimeMillis(rat, freq, mergedTxTimeMs);
        mLastModemActivityInfo.setReceiveTimeMillis(
                rat,
                freq,
                info.getReceiveTimeMillis(rat, freq)
                        + mLastModemActivityInfo.getReceiveTimeMillis(rat, freq));
    }

    private void updateLastModemActivityInfo(ModemActivityInfo info, int rat) {
        int[] mergedTxTimeMs = new int [ModemActivityInfo.getNumTxPowerLevels()];
        int[] txTimeMs = info.getTransmitTimeMillis(rat);
        int[] lastModemTxTimeMs = mLastModemActivityInfo.getTransmitTimeMillis(rat);

        for (int lvl = 0; lvl < mergedTxTimeMs.length; lvl++) {
            mergedTxTimeMs[lvl] = txTimeMs[lvl] + lastModemTxTimeMs[lvl];
        }
        mLastModemActivityInfo.setTransmitTimeMillis(rat, mergedTxTimeMs);
        mLastModemActivityInfo.setReceiveTimeMillis(
                rat,
                info.getReceiveTimeMillis(rat) + mLastModemActivityInfo.getReceiveTimeMillis(rat));
    }

    /**
     * Merge this ModemActivityInfo with mLastModemActivitySpecificInfo
     * @param info recent ModemActivityInfo
     */
    private void mergeModemActivityInfo(ModemActivityInfo info) {
        List<ActivityStatsTechSpecificInfo> merged = new ArrayList<>();
        ActivityStatsTechSpecificInfo deltaSpecificInfo;
        boolean matched;
        for (int i = 0; i < info.getSpecificInfoLength(); i++) {
            matched = false;
            int rat = info.getSpecificInfoRat(i);
            int freq = info.getSpecificInfoFrequencyRange(i);
            //Check each ActivityStatsTechSpecificInfo in this ModemActivityInfo for new rat returns
            //Add a new ActivityStatsTechSpecificInfo if is a new rat, and merge with the original
            //if it already exists
            for (int j = 0; j < mLastModemActivitySpecificInfo.length; j++) {
                if (rat == mLastModemActivityInfo.getSpecificInfoRat(j) && !matched) {
                    //Merged based on frequency range (MMWAVE vs SUB6) for 5G
                    if (rat == AccessNetworkConstants.AccessNetworkType.NGRAN) {
                        if (freq == mLastModemActivityInfo.getSpecificInfoFrequencyRange(j)) {
                            updateLastModemActivityInfo(info, rat, freq);
                            matched = true;
                        }
                    } else {
                        updateLastModemActivityInfo(info, rat);
                        matched = true;
                    }
                }
            }

            if (!matched) {
                deltaSpecificInfo =
                        new ActivityStatsTechSpecificInfo(
                                rat,
                                freq,
                                info.getTransmitTimeMillis(rat, freq),
                                (int) info.getReceiveTimeMillis(rat, freq));
                merged.addAll(Arrays.asList(deltaSpecificInfo));
            }
        }
        merged.addAll(Arrays.asList(mLastModemActivitySpecificInfo));
        mLastModemActivitySpecificInfo =
                new ActivityStatsTechSpecificInfo[merged.size()];
        merged.toArray(mLastModemActivitySpecificInfo);

        mLastModemActivityInfo.setTimestamp(info.getTimestampMillis());
        mLastModemActivityInfo.setSleepTimeMillis(
                info.getSleepTimeMillis()
                        + mLastModemActivityInfo.getSleepTimeMillis());
        mLastModemActivityInfo.setIdleTimeMillis(
                info.getIdleTimeMillis()
                        + mLastModemActivityInfo.getIdleTimeMillis());

        mLastModemActivityInfo =
                new ModemActivityInfo(
                        mLastModemActivityInfo.getTimestampMillis(),
                        mLastModemActivityInfo.getSleepTimeMillis(),
                        mLastModemActivityInfo.getIdleTimeMillis(),
                        mLastModemActivitySpecificInfo);
    }

    private ActivityStatsTechSpecificInfo[] deepCopyModemActivitySpecificInfo(
            ActivityStatsTechSpecificInfo[] info) {
        int infoSize = info.length;
        ActivityStatsTechSpecificInfo[] ret = new ActivityStatsTechSpecificInfo[infoSize];
        for (int i = 0; i < infoSize; i++) {
            ret[i] = new ActivityStatsTechSpecificInfo(
                    info[i].getRat(), info[i].getFrequencyRange(),
                    info[i].getTransmitTimeMillis(),
                    (int) info[i].getReceiveTimeMillis());
        }
        return ret;
    }

    /**
     * Returns the service state information on specified subscription.
     */
    @Override
    public ServiceState getServiceStateForSubscriber(int subId,
            boolean renounceFineLocationAccess, boolean renounceCoarseLocationAccess,
            String callingPackage, String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "getServiceStateForSubscriber")) {
            return null;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getServiceStateForSubscriber");

        boolean hasFinePermission = false;
        boolean hasCoarsePermission = false;
        if (!renounceFineLocationAccess) {
            LocationAccessPolicy.LocationPermissionResult fineLocationResult =
                    LocationAccessPolicy.checkLocationPermission(mApp,
                            new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                    .setCallingPackage(callingPackage)
                                    .setCallingFeatureId(callingFeatureId)
                                    .setCallingPid(Binder.getCallingPid())
                                    .setCallingUid(Binder.getCallingUid())
                                    .setMethod("getServiceStateForSubscriber")
                                    .setLogAsInfo(true)
                                    .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                    .setMinSdkVersionForCoarse(Build.VERSION_CODES.Q)
                                    .setMinSdkVersionForEnforcement(Build.VERSION_CODES.Q)
                                    .build());
            hasFinePermission =
                    fineLocationResult == LocationAccessPolicy.LocationPermissionResult.ALLOWED;
        }

        if (!renounceCoarseLocationAccess) {
            LocationAccessPolicy.LocationPermissionResult coarseLocationResult =
                    LocationAccessPolicy.checkLocationPermission(mApp,
                            new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                    .setCallingPackage(callingPackage)
                                    .setCallingFeatureId(callingFeatureId)
                                    .setCallingPid(Binder.getCallingPid())
                                    .setCallingUid(Binder.getCallingUid())
                                    .setMethod("getServiceStateForSubscriber")
                                    .setLogAsInfo(true)
                                    .setMinSdkVersionForCoarse(Build.VERSION_CODES.Q)
                                    .setMinSdkVersionForFine(Integer.MAX_VALUE)
                                    .setMinSdkVersionForEnforcement(Build.VERSION_CODES.Q)
                                    .build());
            hasCoarsePermission =
                    coarseLocationResult == LocationAccessPolicy.LocationPermissionResult.ALLOWED;
        }

        final Phone phone = getPhone(subId);
        if (phone == null) {
            return null;
        }

        final long identity = Binder.clearCallingIdentity();

        boolean isCallingPackageDataService = phone.getDataServicePackages()
                .contains(callingPackage);
        try {
            // isActiveSubId requires READ_PHONE_STATE, which we already check for above
            SubscriptionInfoInternal subInfo = getSubscriptionManagerService()
                    .getSubscriptionInfoInternal(subId);
            if (subInfo == null || !subInfo.isActive()) {
                Rlog.d(LOG_TAG, "getServiceStateForSubscriber returning null for inactive "
                        + "subId=" + subId);
                return null;
            }

            ServiceState ss = phone.getServiceState();

            // Scrub out the location info in ServiceState depending on what level of access
            // the caller has.
            if (hasFinePermission || isCallingPackageDataService) return ss;
            if (hasCoarsePermission) return ss.createLocationInfoSanitizedCopy(false);
            return ss.createLocationInfoSanitizedCopy(true);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the URI for the per-account voicemail ringtone set in Phone settings.
     *
     * @param accountHandle The handle for the {@link PhoneAccount} for which to retrieve the
     * voicemail ringtone.
     * @return The URI for the ringtone to play when receiving a voicemail from a specific
     * PhoneAccount.
     */
    @Override
    public Uri getVoicemailRingtoneUri(PhoneAccountHandle accountHandle) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getVoicemailRingtoneUri");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(accountHandle);
            if (phone == null) {
                phone = getDefaultPhone();
            }

            return VoicemailNotificationSettingsUtil.getRingtoneUri(phone.getContext());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the per-account voicemail ringtone.
     *
     * <p>Requires that the calling app is the default dialer, or has carrier privileges, or
     * has permission {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
     *
     * @param phoneAccountHandle The handle for the {@link PhoneAccount} for which to set the
     * voicemail ringtone.
     * @param uri The URI for the ringtone to play when receiving a voicemail from a specific
     * PhoneAccount.
     */
    @Override
    public void setVoicemailRingtoneUri(String callingPackage,
            PhoneAccountHandle phoneAccountHandle, Uri uri) {
        final Phone defaultPhone = getDefaultPhone();
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        TelecomManager tm = defaultPhone.getContext().getSystemService(TelecomManager.class);
        if (!TextUtils.equals(callingPackage, tm.getDefaultDialerPackage())) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccountHandle),
                    "setVoicemailRingtoneUri");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "setVoicemailRingtoneUri");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(phoneAccountHandle);
            if (phone == null) {
                phone = defaultPhone;
            }
            VoicemailNotificationSettingsUtil.setRingtoneUri(phone.getContext(), uri);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns whether vibration is set for voicemail notification in Phone settings.
     *
     * @param accountHandle The handle for the {@link PhoneAccount} for which to retrieve the
     * voicemail vibration setting.
     * @return {@code true} if the vibration is set for this PhoneAccount, {@code false} otherwise.
     */
    @Override
    public boolean isVoicemailVibrationEnabled(PhoneAccountHandle accountHandle) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "isVoicemailVibrationEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(accountHandle);
            if (phone == null) {
                phone = getDefaultPhone();
            }

            return VoicemailNotificationSettingsUtil.isVibrationEnabled(phone.getContext());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the per-account voicemail vibration.
     *
     * <p>Requires that the calling app is the default dialer, or has carrier privileges, or
     * has permission {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
     *
     * @param phoneAccountHandle The handle for the {@link PhoneAccount} for which to set the
     * voicemail vibration setting.
     * @param enabled Whether to enable or disable vibration for voicemail notifications from a
     * specific PhoneAccount.
     */
    @Override
    public void setVoicemailVibrationEnabled(String callingPackage,
            PhoneAccountHandle phoneAccountHandle, boolean enabled) {
        final Phone defaultPhone = getDefaultPhone();
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        TelecomManager tm = defaultPhone.getContext().getSystemService(TelecomManager.class);
        if (!TextUtils.equals(callingPackage, tm.getDefaultDialerPackage())) {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, PhoneUtils.getSubIdForPhoneAccountHandle(phoneAccountHandle),
                    "setVoicemailVibrationEnabled");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "setVoicemailVibrationEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = PhoneUtils.getPhoneForPhoneAccountHandle(phoneAccountHandle);
            if (phone == null) {
                phone = defaultPhone;
            }
            VoicemailNotificationSettingsUtil.setVibrationEnabled(phone.getContext(), enabled);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Make sure either called from same process as self (phone) or IPC caller has read privilege.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    @VisibleForTesting
    public void enforceReadPrivilegedPermission(String message) {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                message);
    }

    /**
     * Make sure either called from same process as self (phone) or IPC caller has send SMS
     * permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceSendSmsPermission() {
        mApp.enforceCallingOrSelfPermission(permission.SEND_SMS, null);
    }

    /**
     * Make sure either called from same process as self (phone) or IPC caller has interact across
     * users permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceInteractAcrossUsersPermission(String message) {
        mApp.enforceCallingOrSelfPermission(permission.INTERACT_ACROSS_USERS, message);
    }

    /**
     * Make sure called from the package in charge of visual voicemail.
     *
     * @throws SecurityException if the caller is not the visual voicemail package.
     */
    private void enforceVisualVoicemailPackage(String callingPackage, int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            ComponentName componentName =
                    RemoteVvmTaskManager.getRemotePackage(mApp, subId);
            if (componentName == null) {
                throw new SecurityException(
                        "Caller not current active visual voicemail package[null]");
            }
            String vvmPackage = componentName.getPackageName();
            if (!callingPackage.equals(vvmPackage)) {
                throw new SecurityException("Caller not current active visual voicemail package");
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return the application ID for the app type.
     *
     * @param subId the subscription ID that this request applies to.
     * @param appType the uicc app type.
     * @return Application ID for specificied app type, or null if no uicc.
     */
    @Override
    public String getAidForAppType(int subId, int appType) {
        enforceReadPrivilegedPermission("getAidForAppType");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getAidForAppType");

        Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone == null) {
                return null;
            }
            String aid = null;
            try {
                aid = UiccController.getInstance().getUiccPort(phone.getPhoneId())
                        .getApplicationByType(appType).getAid();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Not getting aid. Exception ex=" + e);
            }
            return aid;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return the Electronic Serial Number.
     *
     * @param subId the subscription ID that this request applies to.
     * @return ESN or null if error.
     */
    @Override
    public String getEsn(int subId) {
        enforceReadPrivilegedPermission("getEsn");
        Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone == null) {
                return null;
            }
            String esn = null;
            try {
                esn = phone.getEsn();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Not getting ESN. Exception ex=" + e);
            }
            return esn;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return the Preferred Roaming List Version.
     *
     * @param subId the subscription ID that this request applies to.
     * @return PRLVersion or null if error.
     */
    @Override
    public String getCdmaPrlVersion(int subId) {
        enforceReadPrivilegedPermission("getCdmaPrlVersion");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "getCdmaPrlVersion");

        Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone == null) {
                return null;
            }
            String cdmaPrlVersion = null;
            try {
                cdmaPrlVersion = phone.getCdmaPrlVersion();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Not getting PRLVersion", e);
            }
            return cdmaPrlVersion;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get snapshot of Telephony histograms
     * @return List of Telephony histograms
     * @hide
     */
    @Override
    public List<TelephonyHistogram> getTelephonyHistograms() {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, getDefaultSubscription(), "getTelephonyHistograms");

        final long identity = Binder.clearCallingIdentity();
        try {
            return RIL.getTelephonyRILTimingHistograms();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * {@hide}
     * Set the allowed carrier list and the excluded carrier list, indicating the priority between
     * the two lists.
     * Require system privileges. In the future we may add this to carrier APIs.
     *
     * @return Integer with the result of the operation, as defined in {@link TelephonyManager}.
     */
    @Override
    @TelephonyManager.SetCarrierRestrictionResult
    public int setAllowedCarriers(CarrierRestrictionRules carrierRestrictionRules) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CARRIERLOCK, "setAllowedCarriers");

        WorkSource workSource = getWorkSource(Binder.getCallingUid());

        if (carrierRestrictionRules == null) {
            throw new NullPointerException("carrier restriction cannot be null");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return (int) sendRequest(CMD_SET_ALLOWED_CARRIERS, carrierRestrictionRules,
                    workSource);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * {@hide}
     * Get the allowed carrier list and the excluded carrier list, including the priority between
     * the two lists.
     * Require system privileges. In the future we may add this to carrier APIs.
     *
     * @return {@link android.telephony.CarrierRestrictionRules}
     */
    @Override
    public CarrierRestrictionRules getAllowedCarriers() {
        enforceReadPrivilegedPermission("getAllowedCarriers");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CARRIERLOCK, "getAllowedCarriers");

        WorkSource workSource = getWorkSource(Binder.getCallingUid());

        final long identity = Binder.clearCallingIdentity();
        try {
            Object response = sendRequest(CMD_GET_ALLOWED_CARRIERS, null, workSource);
            if (response instanceof CarrierRestrictionRules) {
                return (CarrierRestrictionRules) response;
            }
            // Response is an Exception of some kind,
            // which is signalled to the user as a NULL retval
            return null;
        } catch (Exception e) {
            Log.e(LOG_TAG, "getAllowedCarriers. Exception ex=" + e);
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Fetches the carrier restriction status of the device and sends the status to the caller
     * through the callback.
     *
     * @param callback The callback that will be used to send the result.
     * @throws SecurityException if the caller does not have the required permission/privileges or
     *                           the caller is not allowlisted.
     */
    @Override
    public void getCarrierRestrictionStatus(IIntegerConsumer callback, String packageName) {
        enforceReadPermission("getCarrierRestrictionStatus");

        enforceTelephonyFeatureWithException(packageName,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getCarrierRestrictionStatus");

        int carrierId = validateCallerAndGetCarrierId(packageName);
        if (carrierId == CarrierAllowListInfo.INVALID_CARRIER_ID) {
            Rlog.e(LOG_TAG, "getCarrierRestrictionStatus: caller is not registered");
            throw new SecurityException("Not an authorized caller");
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            Consumer<Integer> consumer = FunctionalUtils.ignoreRemoteException(callback::accept);
            CallerCallbackInfo callbackInfo = new CallerCallbackInfo(consumer, carrierId);
            sendRequestAsync(CMD_GET_ALLOWED_CARRIERS, callbackInfo);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public List<String> getShaIdFromAllowList(String pkgName, int carrierId) {
        enforceReadPrivilegedPermission("checkCarrierRestrictionFileForNoChange");
        CarrierAllowListInfo allowListInfo = CarrierAllowListInfo.loadInstance(mApp);
        return allowListInfo.getShaIdList(pkgName, carrierId);
    }

    @VisibleForTesting
    public int validateCallerAndGetCarrierId(String packageName) {
        CarrierAllowListInfo allowListInfo = CarrierAllowListInfo.loadInstance(mApp);
        return allowListInfo.validateCallerAndGetCarrierId(packageName);
    }

    /**
     * Action set from carrier signalling broadcast receivers to enable/disable radio
     * @param subId the subscription ID that this action applies to.
     * @param enabled control enable or disable radio.
     * {@hide}
     */
    @Override
    public void carrierActionSetRadioEnabled(int subId, boolean enabled) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        if (phone == null) {
            loge("carrierAction: SetRadioEnabled fails with invalid sibId: " + subId);
            return;
        }
        try {
            phone.carrierActionSetRadioEnabled(enabled);
        } catch (Exception e) {
            Log.e(LOG_TAG, "carrierAction: SetRadioEnabled fails. Exception ex=" + e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Enable or disable Voice over NR (VoNR)
     * @param subId the subscription ID that this action applies to.
     * @param enabled enable or disable VoNR.
     * @return operation result.
     */
    @Override
    public int setVoNrEnabled(int subId, boolean enabled) {
        enforceModifyPermission();
        final Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        if (phone == null) {
            loge("setVoNrEnabled fails with no phone object for subId: " + subId);
            return TelephonyManager.ENABLE_VONR_RADIO_NOT_AVAILABLE;
        }

        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        try {
            int result = (int) sendRequest(CMD_ENABLE_VONR, enabled, subId,
                    workSource);
            if (DBG) log("setVoNrEnabled result: " + result);

            if (result == TelephonyManager.ENABLE_VONR_SUCCESS) {
                if (DBG) {
                    log("Set VoNR settings in siminfo db; subId=" + subId + ", value:" + enabled);
                }
                SubscriptionManager.setSubscriptionProperty(
                        subId, SubscriptionManager.NR_ADVANCED_CALLING_ENABLED,
                        (enabled ? "1" : "0"));
            }

            return result;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Is voice over NR enabled
     * @return true if VoNR is enabled else false
     */
    @Override
    public boolean isVoNrEnabled(int subId) {
        enforceReadPrivilegedPermission("isVoNrEnabled");
        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            boolean isEnabled = (boolean) sendRequest(CMD_IS_VONR_ENABLED,
                    null, subId, workSource);
            if (DBG) log("isVoNrEnabled: " + isEnabled);
            return isEnabled;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Action set from carrier signalling broadcast receivers to start/stop reporting the default
     * network status based on which carrier apps could apply actions accordingly,
     * enable/disable default url handler for example.
     *
     * @param subId the subscription ID that this action applies to.
     * @param report control start/stop reporting the default network status.
     * {@hide}
     */
    @Override
    public void carrierActionReportDefaultNetworkStatus(int subId, boolean report) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS,
                "carrierActionReportDefaultNetworkStatus");

        final Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        if (phone == null) {
            loge("carrierAction: ReportDefaultNetworkStatus fails with invalid sibId: " + subId);
            return;
        }
        try {
            phone.carrierActionReportDefaultNetworkStatus(report);
        } catch (Exception e) {
            Log.e(LOG_TAG, "carrierAction: ReportDefaultNetworkStatus fails. Exception ex=" + e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Action set from carrier signalling broadcast receivers to reset all carrier actions
     * @param subId the subscription ID that this action applies to.
     * {@hide}
     */
    @Override
    public void carrierActionResetAll(int subId) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "carrierActionResetAll");

        final Phone phone = getPhone(subId);
        if (phone == null) {
            loge("carrierAction: ResetAll fails with invalid sibId: " + subId);
            return;
        }
        try {
            phone.carrierActionResetAll();
        } catch (Exception e) {
            Log.e(LOG_TAG, "carrierAction: ResetAll fails. Exception ex=" + e);
        }
    }

    /**
     * Called when "adb shell dumpsys phone" is invoked. Dump is also automatically invoked when a
     * bug report is being generated.
     */
    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mApp.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump Phone from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + "without permission "
                    + android.Manifest.permission.DUMP);
            return;
        }
        DumpsysHandler.dump(mApp, fd, writer, args);
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return new TelephonyShellCommand(this, getDefaultPhone().getContext()).exec(
                this, in.getFileDescriptor(), out.getFileDescriptor(),
                err.getFileDescriptor(), args);
    }

    /**
     * Policy control of data connection with reason {@@TelephonyManager.DataEnabledReason}
     * @param subId Subscription index
     * @param reason The reason the data enable change is taking place.
     * @param enabled True if enabling the data, otherwise disabling.
     * @param callingPackage The package that changed the data enabled state.
     * @hide
     */
    @Override
    public void setDataEnabledForReason(int subId, @TelephonyManager.DataEnabledReason int reason,
            boolean enabled, String callingPackage) {
        if (reason == TelephonyManager.DATA_ENABLED_REASON_USER
                || reason == TelephonyManager.DATA_ENABLED_REASON_CARRIER) {
            try {
                TelephonyPermissions.enforceCallingOrSelfCarrierPrivilege(
                        mApp, subId, "setDataEnabledForReason");
            } catch (SecurityException se) {
                enforceModifyPermission();
            }
        } else {
            enforceModifyPermission();
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_DATA, "setDataEnabledForReason");

        int callingUid = Binder.getCallingUid();
        final long identity = Binder.clearCallingIdentity();
        try {
            if (reason == TelephonyManager.DATA_ENABLED_REASON_USER && enabled
                    && null != callingPackage && opEnableMobileDataByUser()) {
                mAppOps.noteOp(AppOpsManager.OPSTR_ENABLE_MOBILE_DATA_BY_USER,
                        callingUid, callingPackage, null, null);
            }
            Phone phone = getPhone(subId);
            if (phone != null) {
                if (reason == TelephonyManager.DATA_ENABLED_REASON_CARRIER) {
                    phone.carrierActionSetMeteredApnsEnabled(enabled);
                } else {
                    phone.getDataSettingsManager().setDataEnabled(
                            reason, enabled, callingPackage);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get Client request stats
     * @return List of Client Request Stats
     * @hide
     */
    @Override
    public List<ClientRequestStats> getClientRequestStats(String callingPackage,
            String callingFeatureId, int subId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "getClientRequestStats")) {
            return null;
        }
        Phone phone = getPhone(subId);

        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone != null) {
                return phone.getClientRequestStats();
            }

            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private WorkSource getWorkSource(int uid) {
        String packageName = mApp.getPackageManager().getNameForUid(uid);
        if (uid == Process.ROOT_UID && packageName == null) {
            // Downstream WorkSource attribution inside the RIL requires both a UID and package name
            // to be set for wakelock tracking, otherwise RIL requests fail with a runtime
            // exception. ROOT_UID seems not to have a valid package name returned by
            // PackageManager, so just fake it here to avoid issues when running telephony shell
            // commands that plumb through the RIL as root, like so:
            // $ adb root
            // $ adb shell cmd phone ...
            packageName = "root";
        }
        return new WorkSource(uid, packageName);
    }

    /**
     * Set SIM card power state.
     *
     * @param slotIndex SIM slot id.
     * @param state  State of SIM (power down, power up, pass through)
     * - {@link android.telephony.TelephonyManager#CARD_POWER_DOWN}
     * - {@link android.telephony.TelephonyManager#CARD_POWER_UP}
     * - {@link android.telephony.TelephonyManager#CARD_POWER_UP_PASS_THROUGH}
     *
     **/
    @Override
    public void setSimPowerStateForSlot(int slotIndex, int state) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "setSimPowerStateForSlot");

        Phone phone = PhoneFactory.getPhone(slotIndex);

        WorkSource workSource = getWorkSource(Binder.getCallingUid());

        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone != null) {
                phone.setSimPowerState(state, null, workSource);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set SIM card power state.
     *
     * @param slotIndex SIM slot id.
     * @param state  State of SIM (power down, power up, pass through)
     * @param callback  callback to trigger after success or failure
     * - {@link android.telephony.TelephonyManager#CARD_POWER_DOWN}
     * - {@link android.telephony.TelephonyManager#CARD_POWER_UP}
     * - {@link android.telephony.TelephonyManager#CARD_POWER_UP_PASS_THROUGH}
     *
     **/
    @Override
    public void setSimPowerStateForSlotWithCallback(int slotIndex, int state,
            IIntegerConsumer callback) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "setSimPowerStateForSlotWithCallback");

        Phone phone = PhoneFactory.getPhone(slotIndex);

        WorkSource workSource = getWorkSource(Binder.getCallingUid());

        final long identity = Binder.clearCallingIdentity();
        try {
            if (phone != null) {
                Pair<Integer, IIntegerConsumer> arguments = Pair.create(state, callback);
                sendRequestAsync(CMD_SET_SIM_POWER, arguments, phone, workSource);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isUssdApiAllowed(int subId) {
        CarrierConfigManager configManager =
                (CarrierConfigManager) mApp.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }
        PersistableBundle pb = configManager.getConfigForSubId(subId);
        if (pb == null) {
            return false;
        }
        return pb.getBoolean(
                CarrierConfigManager.KEY_ALLOW_USSD_REQUESTS_VIA_TELEPHONY_MANAGER_BOOL);
    }

    /**
     * Check if phone is in emergency callback mode.
     * @return true if phone is in emergency callback mode
     * @param subId sub Id, but the check is in fact irrlevant to sub Id.
     */
    @Override
    public boolean getEmergencyCallbackMode(int subId) {
        enforceReadPrivilegedPermission("getEmergencyCallbackMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getEmergencyCallbackMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getPhoneFromSubIdOrDefault(subId).isInEcm();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the current signal strength information for the given subscription.
     * Because this information is not updated when the device is in a low power state
     * it should not be relied-upon to be current.
     * @param subId Subscription index
     * @return the most recent cached signal strength info from the modem
     */
    @Override
    public SignalStrength getSignalStrength(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getSignalStrength");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone p = getPhone(subId);
            if (p == null) {
                return null;
            }

            return p.getSignalStrength();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the current modem radio state for the given slot.
     * @param slotIndex slot index.
     * @param callingPackage the name of the package making the call.
     * @param callingFeatureId The feature in the package.
     * @return the current radio power state from the modem
     */
    @Override
    public int getRadioPowerState(int slotIndex, String callingPackage, String callingFeatureId) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone != null) {
            if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mApp, phone.getSubId(),
                    callingPackage, callingFeatureId, "getRadioPowerState")) {
                return TelephonyManager.RADIO_POWER_UNAVAILABLE;
            }

            enforceTelephonyFeatureWithException(callingPackage,
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getRadioPowerState");

            final long identity = Binder.clearCallingIdentity();
            try {
                return phone.getRadioPowerState();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        return TelephonyManager.RADIO_POWER_UNAVAILABLE;
    }

    /**
     * Checks if data roaming is enabled on the subscription with id {@code subId}.
     *
     * <p>Requires one of the following permissions:
     * {@link android.Manifest.permission#ACCESS_NETWORK_STATE},
     * {@link android.Manifest.permission#READ_BASIC_PHONE_STATE},
     * {@link android.Manifest.permission#READ_PHONE_STATE} or that the calling app has carrier
     * privileges.
     *
     * @param subId subscription id
     * @return {@code true} if data roaming is enabled on this subscription, otherwise return
     * {@code false}.
     */
    @Override
    public boolean isDataRoamingEnabled(int subId) {
        String functionName = "isDataRoamingEnabled";
        try {
            try {
                mApp.enforceCallingOrSelfPermission(
                        android.Manifest.permission.ACCESS_NETWORK_STATE,
                        functionName);
            } catch (SecurityException e) {
                mApp.enforceCallingOrSelfPermission(
                        permission.READ_BASIC_PHONE_STATE, functionName);
            }
        } catch (SecurityException e) {
            TelephonyPermissions.enforceCallingOrSelfReadPhoneStatePermissionOrCarrierPrivilege(
                    mApp, subId, functionName);
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isDataRoamingEnabled");

        boolean isEnabled = false;
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            isEnabled =  phone != null ? phone.getDataRoamingEnabled() : false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return isEnabled;
    }


    /**
     * Enables/Disables the data roaming on the subscription with id {@code subId}.
     *
     * <p> Requires permission:
     * {@link android.Manifest.permission#MODIFY_PHONE_STATE} or that the calling app has carrier
     * privileges.
     *
     * @param subId subscription id
     * @param isEnabled {@code true} means enable, {@code false} means disable.
     */
    @Override
    public void setDataRoamingEnabled(int subId, boolean isEnabled) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setDataRoamingEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "setDataRoamingEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                phone.setDataRoamingEnabled(isEnabled);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isManualNetworkSelectionAllowed(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "isManualNetworkSelectionAllowed");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "isManualNetworkSelectionAllowed");

        boolean isAllowed = true;
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null) {
                isAllowed = phone.isCspPlmnEnabled();
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return isAllowed;
    }

    private boolean haveCarrierPrivilegeAccess(UiccPort port, String callingPackage) {
        UiccProfile profile = port.getUiccProfile();
        if (profile == null) {
            return false;
        }
        Phone phone = PhoneFactory.getPhone(profile.getPhoneId());
        if (phone == null) {
            return false;
        }
        CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
        return cpt != null && cpt.getCarrierPrivilegeStatusForPackage(callingPackage)
                == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
    }

    @Override
    public List<UiccCardInfo> getUiccCardsInfo(String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        boolean hasReadPermission = false;
        boolean isIccIdAccessRestricted = false;
        try {
            enforceReadPrivilegedPermission("getUiccCardsInfo");
            hasReadPermission = true;
        } catch (SecurityException e) {
            // even without READ_PRIVILEGED_PHONE_STATE, we allow the call to continue if the caller
            // has carrier privileges on an active UICC
            if (checkCarrierPrivilegesForPackageAnyPhoneWithPermission(callingPackage)
                    != TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
                throw new SecurityException("Caller does not have permission.");
            }
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getUiccCardsInfo");

        // checking compatibility, if calling app's target SDK is T and beyond.
        if (CompatChanges.isChangeEnabled(GET_API_SIGNATURES_FROM_UICC_PORT_INFO,
                Binder.getCallingUid())) {
            isIccIdAccessRestricted = true;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            UiccController uiccController = UiccController.getInstance();
            ArrayList<UiccCardInfo> cardInfos = uiccController.getAllUiccCardInfos();
            if (hasReadPermission) {
                return cardInfos;
            }

            // Remove private info if the caller doesn't have access
            ArrayList<UiccCardInfo> filteredInfos = new ArrayList<>();
            for (UiccCardInfo cardInfo : cardInfos) {
                //setting the value after compatibility check
                cardInfo.setIccIdAccessRestricted(isIccIdAccessRestricted);
                // For an inactive eUICC, the UiccCard will be null even though the UiccCardInfo
                // is available
                UiccCard card = uiccController.getUiccCardForSlot(cardInfo.getPhysicalSlotIndex());
                if (card == null) {
                    // assume no access if the card is unavailable
                    filteredInfos.add(getUiccCardInfoUnPrivileged(cardInfo));
                    continue;
                }
                Collection<UiccPortInfo> portInfos = cardInfo.getPorts();
                if (portInfos.isEmpty()) {
                    filteredInfos.add(getUiccCardInfoUnPrivileged(cardInfo));
                    continue;
                }
                List<UiccPortInfo> uiccPortInfos = new  ArrayList<>();
                for (UiccPortInfo portInfo : portInfos) {
                    UiccPort port = uiccController.getUiccPortForSlot(
                            cardInfo.getPhysicalSlotIndex(), portInfo.getPortIndex());
                    if (port == null) {
                        // assume no access if port is null
                        uiccPortInfos.add(getUiccPortInfoUnPrivileged(portInfo));
                        continue;
                    }
                    if (haveCarrierPrivilegeAccess(port, callingPackage)) {
                        uiccPortInfos.add(portInfo);
                    } else {
                        uiccPortInfos.add(getUiccPortInfoUnPrivileged(portInfo));
                    }
                }
                filteredInfos.add(new UiccCardInfo(
                        cardInfo.isEuicc(),
                        cardInfo.getCardId(),
                        null,
                        cardInfo.getPhysicalSlotIndex(),
                        cardInfo.isRemovable(),
                        cardInfo.isMultipleEnabledProfilesSupported(),
                        uiccPortInfos));
            }
            return filteredInfos;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns a copy of the UiccCardinfo with the EID and ICCID set to null. These values are
     * generally private and require carrier privileges to view.
     *
     * @hide
     */
    @NonNull
    public UiccCardInfo getUiccCardInfoUnPrivileged(UiccCardInfo cardInfo) {
        List<UiccPortInfo> portinfo = new  ArrayList<>();
        for (UiccPortInfo portinfos : cardInfo.getPorts()) {
            portinfo.add(getUiccPortInfoUnPrivileged(portinfos));
        }
        return new UiccCardInfo(
                cardInfo.isEuicc(),
                cardInfo.getCardId(),
                null,
                cardInfo.getPhysicalSlotIndex(),
                cardInfo.isRemovable(),
                cardInfo.isMultipleEnabledProfilesSupported(),
                portinfo
        );
    }

    /**
     * @hide
     * @return a copy of the UiccPortInfo with ICCID set to {@link UiccPortInfo#ICCID_REDACTED}.
     * These values are generally private and require carrier privileges to view.
     */
    @NonNull
    public UiccPortInfo getUiccPortInfoUnPrivileged(UiccPortInfo portInfo) {
        return new UiccPortInfo(
                UiccPortInfo.ICCID_REDACTED,
                portInfo.getPortIndex(),
                portInfo.getLogicalSlotIndex(),
                portInfo.isActive()
        );
    }
    @Override
    public UiccSlotInfo[] getUiccSlotsInfo(String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        boolean isLogicalSlotAccessRestricted = false;

        // This will make sure caller has the READ_PRIVILEGED_PHONE_STATE. Do not remove this as
        // we are reading iccId which is PII data.
        enforceReadPrivilegedPermission("getUiccSlotsInfo");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getUiccSlotsInfo");

        // checking compatibility, if calling app's target SDK is T and beyond.
        if (CompatChanges.isChangeEnabled(GET_API_SIGNATURES_FROM_UICC_PORT_INFO,
                Binder.getCallingUid())) {
            isLogicalSlotAccessRestricted  = true;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            UiccSlot[] slots = UiccController.getInstance().getUiccSlots();
            if (slots == null || slots.length == 0) {
                Rlog.i(LOG_TAG, "slots is null or empty.");
                return null;
            }
            UiccSlotInfo[] infos = new UiccSlotInfo[slots.length];
            for (int i = 0; i < slots.length; i++) {
                UiccSlot slot = slots[i];
                if (slot == null) {
                    continue;
                }

                String cardId;
                UiccCard card = slot.getUiccCard();
                if (card != null) {
                    cardId = card.getCardId();
                } else {
                    cardId = slot.getEid();
                    if (TextUtils.isEmpty(cardId)) {
                        // If cardId is null, use iccId of default port as cardId.
                        cardId = slot.getIccId(TelephonyManager.DEFAULT_PORT_INDEX);
                    }
                }

                if (cardId != null) {
                    // if cardId is an ICCID, strip off trailing Fs before exposing to user
                    // if cardId is an EID, it's all digits so this is fine
                    cardId = IccUtils.stripTrailingFs(cardId);
                }

                int cardState = 0;
                switch (slot.getCardState()) {
                    case CARDSTATE_ABSENT:
                        cardState = UiccSlotInfo.CARD_STATE_INFO_ABSENT;
                        break;
                    case CARDSTATE_PRESENT:
                        cardState = UiccSlotInfo.CARD_STATE_INFO_PRESENT;
                        break;
                    case CARDSTATE_ERROR:
                        cardState = UiccSlotInfo.CARD_STATE_INFO_ERROR;
                        break;
                    case CARDSTATE_RESTRICTED:
                        cardState = UiccSlotInfo.CARD_STATE_INFO_RESTRICTED;
                        break;
                    default:
                        break;

                }
                List<UiccPortInfo> portInfos = new ArrayList<>();
                int[] portIndexes = slot.getPortList();
                for (int portIdx : portIndexes) {
                    String iccId = IccUtils.stripTrailingFs(getIccId(slot, portIdx,
                            callingPackage, /* hasReadPermission= */ true));
                    portInfos.add(new UiccPortInfo(iccId, portIdx,
                            slot.getPhoneIdFromPortIndex(portIdx), slot.isPortActive(portIdx)));
                }
                infos[i] = new UiccSlotInfo(
                        slot.isEuicc(),
                        cardId,
                        cardState,
                        slot.isExtendedApduSupported(),
                        slot.isRemovable(), portInfos);
                //setting the value after compatibility check
                infos[i].setLogicalSlotAccessRestricted(isLogicalSlotAccessRestricted);
            }
            return infos;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /* Returns null if doesn't have read permission or carrier privilege access. */
    private String getIccId(UiccSlot slot, int portIndex, String callingPackage,
            boolean hasReadPermission) {
        String iccId = slot.getIccId(portIndex);
        if (hasReadPermission) { // if has read permission
            return iccId;
        } else {
            if (slot.getUiccCard() != null && slot.getUiccCard().getUiccPort(portIndex) != null) {
                UiccPort port = slot.getUiccCard().getUiccPort(portIndex);
                // if no read permission, checking carrier privilege access
                if (haveCarrierPrivilegeAccess(port, callingPackage)) {
                    return iccId;
                }
            }
        }
        // No read permission or carrier privilege access.
        return UiccPortInfo.ICCID_REDACTED;
    }

    @Override
    @Deprecated
    public boolean switchSlots(int[] physicalSlots) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "switchSlots");

        final long identity = Binder.clearCallingIdentity();
        try {
            List<UiccSlotMapping> slotMappings = new ArrayList<>();
            for (int i = 0; i < physicalSlots.length; i++) {
                // Deprecated API, hence MEP is not supported. Adding default portIndex 0.
                slotMappings.add(new UiccSlotMapping(TelephonyManager.DEFAULT_PORT_INDEX,
                        physicalSlots[i], i));
            }
            return (Boolean) sendRequest(CMD_SWITCH_SLOTS, slotMappings);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean setSimSlotMapping(@NonNull List<UiccSlotMapping> slotMapping) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "setSimSlotMapping");

        final long identity = Binder.clearCallingIdentity();
        try {
            return (Boolean) sendRequest(CMD_SWITCH_SLOTS, slotMapping);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCardIdForDefaultEuicc(int subId, String callingPackage) {
        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_EUICC, "getCardIdForDefaultEuicc");

        final long identity = Binder.clearCallingIdentity();
        try {
            return UiccController.getInstance().getCardIdForDefaultEuicc();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * A test API to reload the UICC profile.
     *
     * <p>Requires that the calling app has permission
     * {@link android.Manifest.permission#MODIFY_PHONE_STATE MODIFY_PHONE_STATE}.
     * @hide
     */
    @Override
    public void refreshUiccProfile(int subId) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) {
                return;
            }
            UiccPort uiccPort = phone.getUiccPort();
            if (uiccPort == null) {
                return;
            }
            UiccProfile uiccProfile = uiccPort.getUiccProfile();
            if (uiccProfile == null) {
                return;
            }
            uiccProfile.refresh();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns false if the mobile data is disabled by default, otherwise return true.
     */
    private boolean getDefaultDataEnabled() {
        return TelephonyProperties.mobile_data().orElse(true);
    }

    /**
     * Returns the default network type for the given {@code subId}, if the default network type is
     * not set, return {@link Phone#PREFERRED_NT_MODE}.
     */
    private int getDefaultNetworkType(int subId) {
        List<Integer> list = TelephonyProperties.default_network();
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (phoneId >= 0 && phoneId < list.size() && list.get(phoneId) != null) {
            return list.get(phoneId);
        }
        return Phone.PREFERRED_NT_MODE;
    }

    @Override
    public void setCarrierTestOverride(int subId, String mccmnc, String imsi, String iccid, String
            gid1, String gid2, String plmn, String spn, String carrierPrivilegeRules, String apn) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null) {
                loge("setCarrierTestOverride fails with invalid subId: " + subId);
                return;
            }
            CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
            if (cpt != null) {
                cpt.setTestOverrideCarrierPrivilegeRules(carrierPrivilegeRules);
            }
            // TODO(b/211796398): remove the legacy logic below once CPT migration is done.
            phone.setCarrierTestOverride(mccmnc, imsi, iccid, gid1, gid2, plmn, spn,
                    carrierPrivilegeRules, apn);
            if (carrierPrivilegeRules == null) {
                mCarrierPrivilegeTestOverrideSubIds.remove(subId);
            } else {
                mCarrierPrivilegeTestOverrideSubIds.add(subId);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setCarrierServicePackageOverride(
            int subId, String carrierServicePackage, String callingPackage) {
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setCarrierServicePackageOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null || phone.getSubId() != subId) {
                loge("setCarrierServicePackageOverride fails with invalid subId: " + subId);
                throw new IllegalArgumentException("No phone for subid");
            }
            CarrierPrivilegesTracker cpt = phone.getCarrierPrivilegesTracker();
            if (cpt == null) {
                loge("setCarrierServicePackageOverride failed with no CPT for phone");
                throw new IllegalStateException("No CPT for phone");
            }
            cpt.setTestOverrideCarrierServicePackage(carrierServicePackage);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCarrierIdListVersion(int subId) {
        enforceReadPrivilegedPermission("getCarrierIdListVersion");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null) {
                loge("getCarrierIdListVersion fails with invalid subId: " + subId);
                return TelephonyManager.UNKNOWN_CARRIER_ID_LIST_VERSION;
            }
            return phone.getCarrierIdListVersion();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getNumberOfModemsWithSimultaneousDataConnections(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "getNumberOfModemsWithSimultaneousDataConnections")) {
            return -1;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return mPhoneConfigurationManager.getNumberOfModemsWithSimultaneousDataConnections();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCdmaRoamingMode(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "getCdmaRoamingMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "getCdmaRoamingMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            return (int) sendRequest(CMD_GET_CDMA_ROAMING_MODE, null /* argument */, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean setCdmaRoamingMode(int subId, int mode) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setCdmaRoamingMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "setCdmaRoamingMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            return (boolean) sendRequest(CMD_SET_CDMA_ROAMING_MODE, mode, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getCdmaSubscriptionMode(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "getCdmaSubscriptionMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "getCdmaSubscriptionMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            return (int) sendRequest(CMD_GET_CDMA_SUBSCRIPTION_MODE, null /* argument */, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean setCdmaSubscriptionMode(int subId, int mode) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setCdmaSubscriptionMode");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CDMA, "setCdmaSubscriptionMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            return (boolean) sendRequest(CMD_SET_CDMA_SUBSCRIPTION_MODE, mode, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public Map<Integer, List<EmergencyNumber>> getEmergencyNumberList(
            String callingPackage, String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, getDefaultSubscription(), callingPackage, callingFeatureId,
                "getEmergencyNumberList")) {
            throw new SecurityException("Requires READ_PHONE_STATE permission.");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_CALLING, "getEmergencyNumberList");

        final long identity = Binder.clearCallingIdentity();
        try {
            Map<Integer, List<EmergencyNumber>> emergencyNumberListInternal = new HashMap<>();
            for (Phone phone: PhoneFactory.getPhones()) {
                if (phone.getEmergencyNumberTracker() != null
                        && phone.getEmergencyNumberTracker().getEmergencyNumberList() != null) {
                    emergencyNumberListInternal.put(
                            phone.getSubId(),
                            phone.getEmergencyNumberTracker().getEmergencyNumberList());
                }
            }
            return emergencyNumberListInternal;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isEmergencyNumber(String number, boolean exactMatch) {
        final Phone defaultPhone = getDefaultPhone();
        if (!exactMatch) {
            TelephonyPermissions
                    .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                            mApp, defaultPhone.getSubId(), "isEmergencyNumber(Potential)");
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "isEmergencyNumber");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone: PhoneFactory.getPhones()) {
                //Note: we ignore passed in param exactMatch. We can remove it once
                // TelephonyManager#isPotentialEmergencyNumber is removed completely
                if (phone.getEmergencyNumberTracker() != null
                        && phone.getEmergencyNumberTracker()
                        .isEmergencyNumber(number)) {
                    return true;
                }
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Start emergency callback mode for GsmCdmaPhone for testing.
     */
    @Override
    public void startEmergencyCallbackMode() {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "startEmergencyCallbackMode");
        enforceModifyPermission();
        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone : PhoneFactory.getPhones()) {
                Rlog.d(LOG_TAG, "startEmergencyCallbackMode phone type: " + phone.getPhoneType());
                if (phone != null && ((phone.getPhoneType() == PHONE_TYPE_GSM)
                        || (phone.getPhoneType() == PHONE_TYPE_CDMA))) {
                    GsmCdmaPhone gsmCdmaPhone = (GsmCdmaPhone) phone;
                    gsmCdmaPhone.obtainMessage(
                            GsmCdmaPhone.EVENT_EMERGENCY_CALLBACK_MODE_ENTER).sendToTarget();
                    Rlog.d(LOG_TAG, "startEmergencyCallbackMode: triggered");
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Update emergency number list for test mode.
     */
    @Override
    public void updateEmergencyNumberListTestMode(int action, EmergencyNumber num) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "updateEmergencyNumberListTestMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone: PhoneFactory.getPhones()) {
                EmergencyNumberTracker tracker = phone.getEmergencyNumberTracker();
                if (tracker != null) {
                    tracker.executeEmergencyNumberTestModeCommand(action, num);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the full emergency number list for test mode.
     */
    @Override
    public List<String> getEmergencyNumberListTestMode() {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "getEmergencyNumberListTestMode");

        final long identity = Binder.clearCallingIdentity();
        try {
            Set<String> emergencyNumbers = new HashSet<>();
            for (Phone phone: PhoneFactory.getPhones()) {
                EmergencyNumberTracker tracker = phone.getEmergencyNumberTracker();
                if (tracker != null) {
                    for (EmergencyNumber num : tracker.getEmergencyNumberList()) {
                        emergencyNumbers.add(num.getNumber());
                    }
                }
            }
            return new ArrayList<>(emergencyNumbers);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getEmergencyNumberDbVersion(int subId) {
        enforceReadPrivilegedPermission("getEmergencyNumberDbVersion");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "getEmergencyNumberDbVersion");

        final long identity = Binder.clearCallingIdentity();
        try {
            final Phone phone = getPhone(subId);
            if (phone == null) {
                loge("getEmergencyNumberDbVersion fails with invalid subId: " + subId);
                return TelephonyManager.INVALID_EMERGENCY_NUMBER_DB_VERSION;
            }
            return phone.getEmergencyNumberDbVersion();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void notifyOtaEmergencyNumberDbInstalled() {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "notifyOtaEmergencyNumberDbInstalled");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone: PhoneFactory.getPhones()) {
                EmergencyNumberTracker tracker = phone.getEmergencyNumberTracker();
                if (tracker != null) {
                    tracker.updateOtaEmergencyNumberDatabase();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void updateOtaEmergencyNumberDbFilePath(ParcelFileDescriptor otaParcelFileDescriptor) {
        enforceActiveEmergencySessionPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "updateOtaEmergencyNumberDbFilePath");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone: PhoneFactory.getPhones()) {
                EmergencyNumberTracker tracker = phone.getEmergencyNumberTracker();
                if (tracker != null) {
                    tracker.updateOtaEmergencyNumberDbFilePath(otaParcelFileDescriptor);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void resetOtaEmergencyNumberDbFilePath() {
        enforceActiveEmergencySessionPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CALLING, "resetOtaEmergencyNumberDbFilePath");

        final long identity = Binder.clearCallingIdentity();
        try {
            for (Phone phone: PhoneFactory.getPhones()) {
                EmergencyNumberTracker tracker = phone.getEmergencyNumberTracker();
                if (tracker != null) {
                    tracker.resetOtaEmergencyNumberDbFilePath();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public List<String> getCertsFromCarrierPrivilegeAccessRules(int subId) {
        enforceReadPrivilegedPermission("getCertsFromCarrierPrivilegeAccessRules");
        Phone phone = getPhone(subId);
        if (phone == null) {
            return null;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            UiccProfile profile = UiccController.getInstance()
                    .getUiccProfileForPhone(phone.getPhoneId());
            if (profile != null) {
                return profile.getCertsFromCarrierPrivilegeAccessRules();
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

    /**
     * Enable or disable a modem stack.
     */
    @Override
    public boolean enableModemForSlot(int slotIndex, boolean enable) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY, "enableModemForSlot");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = PhoneFactory.getPhone(slotIndex);
            if (phone == null) {
                return false;
            } else {
                return (Boolean) sendRequest(CMD_REQUEST_ENABLE_MODEM, enable, phone, null);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Whether a modem stack is enabled or not.
     */
    @Override
    public boolean isModemEnabledForSlot(int slotIndex, String callingPackage,
            String callingFeatureId) {
        Phone phone = PhoneFactory.getPhone(slotIndex);
        if (phone == null) return false;

        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, phone.getSubId(), callingPackage, callingFeatureId,
                "isModemEnabledForSlot")) {
            throw new SecurityException("Requires READ_PHONE_STATE permission.");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY, "isModemEnabledForSlot");

        final long identity = Binder.clearCallingIdentity();
        try {
            try {
                return mPhoneConfigurationManager.getPhoneStatusFromCache(phone.getPhoneId());
            } catch (NoSuchElementException ex) {
                return (Boolean) sendRequest(CMD_GET_MODEM_STATUS, null, phone, null);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setMultiSimCarrierRestriction(boolean isMultiSimCarrierRestricted) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_CARRIERLOCK, "setMultiSimCarrierRestriction");

        final long identity = Binder.clearCallingIdentity();
        try {
            mTelephonySharedPreferences.edit()
                    .putBoolean(PREF_MULTI_SIM_RESTRICTED, isMultiSimCarrierRestricted)
                    .commit();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    @TelephonyManager.IsMultiSimSupportedResult
    public int isMultiSimSupported(String callingPackage, String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mApp,
                getDefaultPhone().getSubId(), callingPackage, callingFeatureId,
                "isMultiSimSupported")) {
            return TelephonyManager.MULTISIM_NOT_SUPPORTED_BY_HARDWARE;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "isMultiSimSupported");

        final long identity = Binder.clearCallingIdentity();
        try {
            return isMultiSimSupportedInternal();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @TelephonyManager.IsMultiSimSupportedResult
    private int isMultiSimSupportedInternal() {
        // If the device has less than 2 SIM cards, indicate that multisim is restricted.
        int numPhysicalSlots = UiccController.getInstance().getUiccSlots().length;
        if (numPhysicalSlots < 2) {
            loge("isMultiSimSupportedInternal: requires at least 2 cards");
            return TelephonyManager.MULTISIM_NOT_SUPPORTED_BY_HARDWARE;
        }
        // Check if the hardware supports multisim functionality. If usage of multisim is not
        // supported by the modem, indicate that it is restricted.
        PhoneCapability staticCapability =
                mPhoneConfigurationManager.getStaticPhoneCapability();
        if (staticCapability == null) {
            loge("isMultiSimSupportedInternal: no static configuration available");
            return TelephonyManager.MULTISIM_NOT_SUPPORTED_BY_HARDWARE;
        }
        if (staticCapability.getLogicalModemList().size() < 2) {
            loge("isMultiSimSupportedInternal: maximum number of modem is < 2");
            return TelephonyManager.MULTISIM_NOT_SUPPORTED_BY_HARDWARE;
        }
        // Check if support of multiple SIMs is restricted by carrier
        if (mTelephonySharedPreferences.getBoolean(PREF_MULTI_SIM_RESTRICTED, false)) {
            return TelephonyManager.MULTISIM_NOT_SUPPORTED_BY_CARRIER;
        }

        return TelephonyManager.MULTISIM_ALLOWED;
    }

    /**
     * Switch configs to enable multi-sim or switch back to single-sim
     * Note: Switch from multi-sim to single-sim is only possible with MODIFY_PHONE_STATE
     * permission, but the other way around is possible with either MODIFY_PHONE_STATE
     * or carrier privileges
     * @param numOfSims number of active sims we want to switch to
     */
    @Override
    public void switchMultiSimConfig(int numOfSims) {
        if (numOfSims == 1) {
            enforceModifyPermission();
        } else {
            TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                    mApp, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, "switchMultiSimConfig");
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "switchMultiSimConfig");

        final long identity = Binder.clearCallingIdentity();

        try {
            //only proceed if multi-sim is not restricted
            if (isMultiSimSupportedInternal() != TelephonyManager.MULTISIM_ALLOWED) {
                loge("switchMultiSimConfig not possible. It is restricted or not supported.");
                return;
            }
            mPhoneConfigurationManager.switchMultiSimConfig(numOfSims);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isApplicationOnUicc(int subId, int appType) {
        enforceReadPrivilegedPermission("isApplicationOnUicc");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "isApplicationOnUicc");

        Phone phone = getPhone(subId);
        if (phone == null) {
            return false;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            UiccPort uiccPort = phone.getUiccPort();
            if (uiccPort == null) {
                return false;
            }
            UiccProfile uiccProfile = uiccPort.getUiccProfile();
            if (uiccProfile == null) {
                return false;
            }
            if (TelephonyManager.APPTYPE_SIM <= appType
                    && appType <= TelephonyManager.APPTYPE_ISIM) {
                return uiccProfile.isApplicationOnIcc(AppType.values()[appType]);
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get whether making changes to modem configurations will trigger reboot.
     * Return value defaults to true.
     */
    @Override
    public boolean doesSwitchMultiSimConfigTriggerReboot(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId,
                "doesSwitchMultiSimConfigTriggerReboot")) {
            return false;
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION,
                "doesSwitchMultiSimConfigTriggerReboot");

        final long identity = Binder.clearCallingIdentity();
        try {
            return mPhoneConfigurationManager.isRebootRequiredForModemConfigChange();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void updateModemStateMetrics() {
        TelephonyMetrics metrics = TelephonyMetrics.getInstance();
        // TODO: check the state for each modem if the api is ready.
        metrics.updateEnabledModemBitmap((1 << TelephonyManager.from(mApp).getPhoneCount()) - 1);
    }

    @Override
    public List<UiccSlotMapping> getSlotsMapping(String callingPackage) {
        enforceReadPrivilegedPermission("getSlotsMapping");
        // Verify that the callingPackage belongs to the calling UID
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), callingPackage);

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getSlotsMapping");

        final long identity = Binder.clearCallingIdentity();
        List<UiccSlotMapping> slotMap = new ArrayList<>();
        try {
            UiccSlotInfo[] slotInfos = getUiccSlotsInfo(mApp.getOpPackageName());
            if (slotInfos != null) {
                for (int i = 0; i < slotInfos.length; i++) {
                    for (UiccPortInfo portInfo : slotInfos[i].getPorts()) {
                        if (SubscriptionManager.isValidPhoneId(portInfo.getLogicalSlotIndex())) {
                            slotMap.add(new UiccSlotMapping(portInfo.getPortIndex(), i,
                                    portInfo.getLogicalSlotIndex()));
                        }
                    }
                }
            }
            return slotMap;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the IRadio HAL Version
     * @deprecated use getHalVersion instead
     */
    @Deprecated
    @Override
    public int getRadioHalVersion() {
        return getHalVersion(HAL_SERVICE_RADIO);
    }

    /**
     * Get the HAL Version of a specific service
     */
    @Override
    public int getHalVersion(int service) {
        Phone phone = getDefaultPhone();
        if (phone == null) return -1;
        HalVersion hv = phone.getHalVersion(service);
        if (hv.equals(HalVersion.UNKNOWN)) return -1;
        return hv.major * 100 + hv.minor;
    }

    /**
     * Get the current calling package name.
     *
     * @return the current calling package name, or null if there is no known package.
     */
    @Override
    public @Nullable String getCurrentPackageName() {
        PackageManager pm = mApp.getPackageManager();
        String[] packageNames = pm == null ? null : pm.getPackagesForUid(Binder.getCallingUid());
        return packageNames == null ? null : packageNames[0];
    }

    /**
     * Return whether data is enabled for certain APN type. This will tell if framework will accept
     * corresponding network requests on a subId.
     *
     *  Data is enabled if:
     *  1) user data is turned on, or
     *  2) APN is un-metered for this subscription, or
     *  3) APN type is whitelisted. E.g. MMS is whitelisted if
     *  {@link TelephonyManager#MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED} is enabled.
     *
     * @return whether data is allowed for a apn type.
     *
     * @hide
     */
    @Override
    public boolean isDataEnabledForApn(int apnType, int subId, String callingPackage) {
        enforceReadPrivilegedPermission("Needs READ_PRIVILEGED_PHONE_STATE for "
                + "isDataEnabledForApn");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_DATA, "isDataEnabledForApn");

        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) return false;

            boolean isMetered;
            boolean isDataEnabled;
            isMetered = phone.getDataNetworkController().getDataConfigManager()
                    .isMeteredCapability(DataUtils.apnTypeToNetworkCapability(apnType),
                            phone.getServiceState().getDataRoaming());
            isDataEnabled = phone.getDataSettingsManager().isDataEnabled(apnType);
            return !isMetered || isDataEnabled;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isApnMetered(@ApnType int apnType, int subId) {
        enforceReadPrivilegedPermission("isApnMetered");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isApnMetered");

        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) return true; // By default return true.
            return phone.getDataNetworkController().getDataConfigManager().isMeteredCapability(
                    DataUtils.apnTypeToNetworkCapability(apnType),
                    phone.getServiceState().getDataRoaming());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setSystemSelectionChannels(List<RadioAccessSpecifier> specifiers,
            int subscriptionId, IBooleanConsumer resultCallback) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "setSystemSelectionChannels");

        long token = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subscriptionId);
            if (phone == null) {
                try {
                    if (resultCallback != null) {
                        resultCallback.accept(false);
                    }
                } catch (RemoteException e) {
                    // ignore
                }
                return;
            }
            Pair<List<RadioAccessSpecifier>, Consumer<Boolean>> argument =
                    Pair.create(specifiers, (x) -> {
                        try {
                            if (resultCallback != null) {
                                resultCallback.accept(x);
                            }
                        } catch (RemoteException e) {
                            // ignore
                        }
                    });
            sendRequestAsync(CMD_SET_SYSTEM_SELECTION_CHANNELS, argument, phone, null);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public List<RadioAccessSpecifier> getSystemSelectionChannels(int subId) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, subId, "getSystemSelectionChannels");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "getSystemSelectionChannels");

        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();
        try {
            Object result = sendRequest(CMD_GET_SYSTEM_SELECTION_CHANNELS, null, subId, workSource);
            if (result instanceof IllegalStateException) {
                throw (IllegalStateException) result;
            }
            List<RadioAccessSpecifier> specifiers = (List<RadioAccessSpecifier>) result;
            if (DBG) log("getSystemSelectionChannels: " + specifiers);
            return specifiers;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isMvnoMatched(int slotIndex, int mvnoType, @NonNull String mvnoMatchData) {
        enforceReadPrivilegedPermission("isMvnoMatched");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "isMvnoMatched");

        return UiccController.getInstance().mvnoMatches(slotIndex, mvnoType, mvnoMatchData);
    }

    @Override
    public void enqueueSmsPickResult(String callingPackage, String callingAttributionTag,
            IIntegerConsumer pendingSubIdResult) {
        if (callingPackage == null) {
            callingPackage = getCurrentPackageName();
        }
        SmsPermissions permissions = new SmsPermissions(getDefaultPhone(), mApp,
                (AppOpsManager) mApp.getSystemService(Context.APP_OPS_SERVICE));
        if (!permissions.checkCallingCanSendSms(callingPackage, callingAttributionTag,
                "Sending message")) {
            throw new SecurityException("Requires SEND_SMS permission to perform this operation");
        }
        PickSmsSubscriptionActivity.addPendingResult(pendingSubIdResult);
        Intent intent = new Intent();
        intent.setClass(mApp, PickSmsSubscriptionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Bring up choose default SMS subscription dialog right now
        intent.putExtra(PickSmsSubscriptionActivity.DIALOG_TYPE_KEY,
                PickSmsSubscriptionActivity.SMS_PICK_FOR_MESSAGE);
        mApp.startActivity(intent);
    }

    @Override
    public void showSwitchToManagedProfileDialog() {
        enforceModifyPermission();
        try {
            // Note: This intent is constructed to ensure that the IntentForwarderActivity is
            // shown in accordance with the intent filters in DefaultCrossProfileIntentFilterUtils
            // for work telephony.
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mApp.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(LOG_TAG, "Unable to show intent forwarder, try showing error dialog instead");
            Intent intent = new Intent();
            intent.setClass(mApp, ErrorDialogActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mApp.startActivity(intent);
        }
    }

    @Override
    public String getMmsUAProfUrl(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_MESSAGING, "getMmsUAProfUrl");

        //TODO investigate if this API should require proper permission check in R b/133791609
        final long identity = Binder.clearCallingIdentity();
        try {
            String carrierUAProfUrl = mApp.getCarrierConfigForSubId(subId).getString(
                    CarrierConfigManager.KEY_MMS_UA_PROF_URL_STRING);
            if (!TextUtils.isEmpty(carrierUAProfUrl)) {
                return carrierUAProfUrl;
            }
            return SubscriptionManager.getResourcesForSubId(getDefaultPhone().getContext(), subId)
                    .getString(com.android.internal.R.string.config_mms_user_agent_profile_url);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public String getMmsUserAgent(int subId) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_MESSAGING, "getMmsUserAgent");

        //TODO investigate if this API should require proper permission check in R b/133791609
        final long identity = Binder.clearCallingIdentity();
        try {
            String carrierUserAgent = mApp.getCarrierConfigForSubId(subId).getString(
                    CarrierConfigManager.KEY_MMS_USER_AGENT_STRING);
            if (!TextUtils.isEmpty(carrierUserAgent)) {
                return carrierUserAgent;
            }
            return SubscriptionManager.getResourcesForSubId(getDefaultPhone().getContext(), subId)
                    .getString(com.android.internal.R.string.config_mms_user_agent);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isMobileDataPolicyEnabled(int subscriptionId, int policy) {
        enforceReadPrivilegedPermission("isMobileDataPolicyEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isMobileDataPolicyEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subscriptionId);
            if (phone == null) return false;

            return phone.getDataSettingsManager().isMobileDataPolicyEnabled(policy);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setMobileDataPolicyEnabled(int subscriptionId, int policy,
            boolean enabled) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "setMobileDataPolicyEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subscriptionId);
            if (phone == null) return;

            phone.getDataSettingsManager().setMobileDataPolicy(policy, enabled);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Updates whether conference event package handling is enabled.
     * @param isCepEnabled {@code true} if CEP handling is enabled (default), or {@code false}
     *                                 otherwise.
     */
    @Override
    public void setCepEnabled(boolean isCepEnabled) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "setCepEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Rlog.i(LOG_TAG, "setCepEnabled isCepEnabled=" + isCepEnabled);
            for (Phone phone : PhoneFactory.getPhones()) {
                Phone defaultPhone = phone.getImsPhone();
                if (defaultPhone != null && defaultPhone.getPhoneType() == PHONE_TYPE_IMS) {
                    ImsPhone imsPhone = (ImsPhone) defaultPhone;
                    ImsPhoneCallTracker imsPhoneCallTracker =
                            (ImsPhoneCallTracker) imsPhone.getCallTracker();
                    imsPhoneCallTracker.setConferenceEventPackageEnabled(isCepEnabled);
                    Rlog.i(LOG_TAG, "setCepEnabled isCepEnabled=" + isCepEnabled + ", for imsPhone "
                            + imsPhone.getMsisdn());
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Notify that an RCS autoconfiguration XML file has been received for provisioning.
     *
     * @param config       The XML file to be read. ASCII/UTF8 encoded text if not compressed.
     * @param isCompressed The XML file is compressed in gzip format and must be decompressed
     *                     before being read.
     */
    @Override
    public void notifyRcsAutoConfigurationReceived(int subId, @NonNull byte[] config, boolean
            isCompressed) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "notifyRcsAutoConfigurationReceived");
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }

        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, getCurrentPackageName(),
                Binder.getCallingUserHandle())) {
            if (!isImsAvailableOnDevice()) {
                // ProvisioningManager can not handle ServiceSpecificException.
                // Throw the IllegalStateException and annotate ProvisioningManager.
                throw new IllegalStateException("IMS not available on device.");
            }
        } else {
            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION,
                    "notifyRcsAutoConfigurationReceived");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            RcsProvisioningMonitor.getInstance().updateConfig(subId, config, isCompressed);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isIccLockEnabled(int subId) {
        enforceReadPrivilegedPermission("isIccLockEnabled");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "isIccLockEnabled");

        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone != null && phone.getIccCard() != null) {
                return phone.getIccCard().getIccLockEnabled();
            } else {
                return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the ICC pin lock enabled or disabled.
     *
     * @return an integer representing the status of IccLock enabled or disabled in the following
     * three cases:
     *   - {@link TelephonyManager#CHANGE_ICC_LOCK_SUCCESS} if enabled or disabled IccLock
     *   successfully.
     *   - Positive number and zero for remaining password attempts.
     *   - Negative number for other failure cases (such like enabling/disabling PIN failed).
     *
     */
    @Override
    public int setIccLockEnabled(int subId, boolean enabled, String password) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "setIccLockEnabled");

        Phone phone = getPhone(subId);
        if (phone == null) {
            return 0;
        }
        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            int attemptsRemaining = (int) sendRequest(CMD_SET_ICC_LOCK_ENABLED,
                    new Pair<Boolean, String>(enabled, password), phone, null);
            return attemptsRemaining;

        } catch (Exception e) {
            Log.e(LOG_TAG, "setIccLockEnabled. Exception e =" + e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return 0;
    }

    /**
     * Change the ICC password used in ICC pin lock.
     *
     * @return an integer representing the status of IccLock changed in the following three cases:
     *   - {@link TelephonyManager#CHANGE_ICC_LOCK_SUCCESS} if changed IccLock successfully.
     *   - Positive number and zero for remaining password attempts.
     *   - Negative number for other failure cases (such like enabling/disabling PIN failed).
     *
     */
    @Override
    public int changeIccLockPassword(int subId, String oldPassword, String newPassword) {
        enforceModifyPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "changeIccLockPassword");

        Phone phone = getPhone(subId);
        if (phone == null) {
            return 0;
        }
        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            int attemptsRemaining = (int) sendRequest(CMD_CHANGE_ICC_LOCK_PASSWORD,
                    new Pair<String, String>(oldPassword, newPassword), phone, null);
            return attemptsRemaining;

        } catch (Exception e) {
            Log.e(LOG_TAG, "changeIccLockPassword. Exception e =" + e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return 0;
    }

    /**
     * Request for receiving user activity notification
     */
    @Override
    public void requestUserActivityNotification() {
        if (!mNotifyUserActivity.get()
                && !mMainThreadHandler.hasMessages(MSG_NOTIFY_USER_ACTIVITY)) {
            mNotifyUserActivity.set(true);
        }
    }

    /**
     * Called when userActivity is signalled in the power manager.
     * This is safe to call from any thread, with any window manager locks held or not.
     */
    @Override
    public void userActivity() {
        // ***************************************
        // *  Inherited from PhoneWindowManager  *
        // ***************************************
        // THIS IS CALLED FROM DEEP IN THE POWER MANAGER
        // WITH ITS LOCKS HELD.
        //
        // This code must be VERY careful about the locks
        // it acquires.
        // In fact, the current code acquires way too many,
        // and probably has lurking deadlocks.

        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Only the OS may call notifyUserActivity()");
        }

        if (mNotifyUserActivity.getAndSet(false)) {
            mMainThreadHandler.sendEmptyMessageDelayed(MSG_NOTIFY_USER_ACTIVITY,
                    USER_ACTIVITY_NOTIFICATION_DELAY);
        }
    }

    @Override
    public boolean canConnectTo5GInDsdsMode() {
        return mApp.getResources().getBoolean(R.bool.config_5g_connection_in_dsds_mode);
    }

    @Override
    public @NonNull List<String> getEquivalentHomePlmns(int subId, String callingPackage,
            String callingFeatureId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(
                mApp, subId, callingPackage, callingFeatureId, "getEquivalentHomePlmns")) {
            throw new SecurityException("Requires READ_PHONE_STATE permission.");
        }

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getEquivalentHomePlmns");

        Phone phone = getPhone(subId);
        if (phone == null) {
            throw new RuntimeException("phone is not available");
        }
        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            return phone.getEquivalentHomePlmns();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean isRadioInterfaceCapabilitySupported(
            final @NonNull @TelephonyManager.RadioInterfaceCapability String capability) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS,
                "isRadioInterfaceCapabilitySupported");

        Set<String> radioInterfaceCapabilities =
                mRadioInterfaceCapabilities.getCapabilities();
        if (radioInterfaceCapabilities == null) {
            throw new RuntimeException("radio interface capabilities are not available");
        }
        return radioInterfaceCapabilities.contains(capability);
    }

    @Override
    public void bootstrapAuthenticationRequest(int subId, int appType, Uri nafUrl,
            UaSecurityProtocolIdentifier securityProtocol,
            boolean forceBootStrapping, IBootstrapAuthenticationCallback callback) {
        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(mApp, subId,
                Binder.getCallingUid(), "bootstrapAuthenticationRequest",
                Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION,
                Manifest.permission.MODIFY_PHONE_STATE);

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "bootstrapAuthenticationRequest");

        if (DBG) {
            log("bootstrapAuthenticationRequest, subId:" + subId + ", appType:"
                    + appType + ", NAF:" + nafUrl + ", sp:" + securityProtocol
                    + ", forceBootStrapping:" + forceBootStrapping + ", callback:" + callback);
        }

        if (!SubscriptionManager.isValidSubscriptionId(subId)
                || appType < TelephonyManager.APPTYPE_UNKNOWN
                || appType > TelephonyManager.APPTYPE_ISIM
                || nafUrl == null || securityProtocol == null || callback == null) {
            Log.d(LOG_TAG, "bootstrapAuthenticationRequest failed due to invalid parameters");
            if (callback != null) {
                try {
                    callback.onAuthenticationFailure(
                            0, TelephonyManager.GBA_FAILURE_REASON_FEATURE_NOT_SUPPORTED);
                } catch (RemoteException exception) {
                    log("Fail to notify onAuthenticationFailure due to " + exception);
                }
                return;
            }
        }

        final long token = Binder.clearCallingIdentity();
        try {
            getGbaManager(subId).bootstrapAuthenticationRequest(
                    new GbaAuthRequest(subId, appType, nafUrl, securityProtocol.toByteArray(),
                            forceBootStrapping, callback));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Attempts to set the radio power state for all phones for thermal reason.
     * This does not guarantee that the
     * requested radio power state will actually be set. See {@link
     * PhoneInternalInterface#setRadioPowerForReason} for more details.
     *
     * @param enable {@code true} if trying to turn radio on.
     * @return {@code true} if phone setRadioPowerForReason was called. Otherwise, returns {@code
     * false}.
     */
    private boolean setRadioPowerForThermal(boolean enable) {
        boolean isPhoneAvailable = false;
        for (int i = 0; i < TelephonyManager.getDefault().getActiveModemCount(); i++) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone != null) {
                phone.setRadioPowerForReason(enable, TelephonyManager.RADIO_POWER_REASON_THERMAL);
                isPhoneAvailable = true;
            }
        }

        // return true if successfully informed the phone object about the thermal radio power
        // request.
        return isPhoneAvailable;
    }

    private int handleDataThrottlingRequest(int subId,
            DataThrottlingRequest dataThrottlingRequest, String callingPackage) {
        boolean isDataThrottlingSupported = isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING);
        if (!isDataThrottlingSupported && dataThrottlingRequest.getDataThrottlingAction()
                != DataThrottlingRequest.DATA_THROTTLING_ACTION_NO_DATA_THROTTLING) {
            throw new IllegalArgumentException("modem does not support data throttling");
        }

        // Ensure that radio is on. If not able to power on due to phone being unavailable, return
        // THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE.
        if (!setRadioPowerForThermal(true)) {
            return TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
        }

        setDataEnabledForReason(
                subId, TelephonyManager.DATA_ENABLED_REASON_THERMAL, true, callingPackage);

        if (isDataThrottlingSupported) {
            int thermalMitigationResult =
                    (int) sendRequest(CMD_SET_DATA_THROTTLING, dataThrottlingRequest, subId);
            if (thermalMitigationResult == SET_DATA_THROTTLING_MODEM_THREW_INVALID_PARAMS) {
                throw new IllegalArgumentException("modem returned INVALID_ARGUMENTS");
            } else if (thermalMitigationResult
                    == MODEM_DOES_NOT_SUPPORT_DATA_THROTTLING_ERROR_CODE) {
                log("Modem likely does not support data throttling on secondary carrier. Data " +
                        "throttling action = " + dataThrottlingRequest.getDataThrottlingAction());
                return TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_ERROR;
            }
            return thermalMitigationResult;
        }

        return TelephonyManager.THERMAL_MITIGATION_RESULT_SUCCESS;
    }

    private static List<String> getThermalMitigationAllowlist(Context context) {
        if (sThermalMitigationAllowlistedPackages.isEmpty()) {
            for (String pckg : context.getResources()
                    .getStringArray(R.array.thermal_mitigation_allowlisted_packages)) {
                sThermalMitigationAllowlistedPackages.add(pckg);
            }
        }

        return sThermalMitigationAllowlistedPackages;
    }

    private boolean isAnyPhoneInEmergencyState() {
        TelecomManager tm = mApp.getSystemService(TelecomManager.class);
        if (tm.isInEmergencyCall()) {
            Log.e(LOG_TAG , "Phone state is not valid. One of the phones is in an emergency call");
            return true;
        }
        for (Phone phone : PhoneFactory.getPhones()) {
            if (phone.isInEmergencySmsMode() || phone.isInEcm()) {
                Log.e(LOG_TAG, "Phone state is not valid. isInEmergencySmsMode = "
                        + phone.isInEmergencySmsMode() + " isInEmergencyCallbackMode = "
                        + phone.isInEcm());
                return true;
            }
        }

        return false;
    }

    /**
     * Used by shell commands to add an authorized package name for thermal mitigation.
     * @param packageName name of package to be allowlisted
     * @param context
     */
    static void addPackageToThermalMitigationAllowlist(String packageName, Context context) {
        sThermalMitigationAllowlistedPackages = getThermalMitigationAllowlist(context);
        sThermalMitigationAllowlistedPackages.add(packageName);
    }

    /**
     * Used by shell commands to remove an authorized package name for thermal mitigation.
     * @param packageName name of package to remove from allowlist
     * @param context
     */
    static void removePackageFromThermalMitigationAllowlist(String packageName, Context context) {
        sThermalMitigationAllowlistedPackages = getThermalMitigationAllowlist(context);
        sThermalMitigationAllowlistedPackages.remove(packageName);
    }

    /**
     * Thermal mitigation request to control functionalities at modem.
     *
     * @param subId the id of the subscription.
     * @param thermalMitigationRequest holds all necessary information to be passed down to modem.
     * @param callingPackage the package name of the calling package.
     *
     * @return thermalMitigationResult enum as defined in android.telephony.Annotation.
     */
    @Override
    @ThermalMitigationResult
    public int sendThermalMitigationRequest(
            int subId,
            ThermalMitigationRequest thermalMitigationRequest,
            String callingPackage) throws IllegalArgumentException {
        enforceModifyPermission();

        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "sendThermalMitigationRequest");

        if (!getThermalMitigationAllowlist(getDefaultPhone().getContext())
                .contains(callingPackage)) {
            throw new SecurityException("Calling package must be configured in the device config. "
                    + "calling package: " + callingPackage);
        }

        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        final long identity = Binder.clearCallingIdentity();

        int thermalMitigationResult = TelephonyManager.THERMAL_MITIGATION_RESULT_UNKNOWN_ERROR;
        try {
            int thermalMitigationAction = thermalMitigationRequest.getThermalMitigationAction();
            switch (thermalMitigationAction) {
                case ThermalMitigationRequest.THERMAL_MITIGATION_ACTION_DATA_THROTTLING:
                    thermalMitigationResult =
                            handleDataThrottlingRequest(subId,
                                    thermalMitigationRequest.getDataThrottlingRequest(),
                                    callingPackage);
                    break;
                case ThermalMitigationRequest.THERMAL_MITIGATION_ACTION_VOICE_ONLY:
                    if (thermalMitigationRequest.getDataThrottlingRequest() != null) {
                        throw new IllegalArgumentException("dataThrottlingRequest must be null for "
                                + "ThermalMitigationRequest.THERMAL_MITIGATION_ACTION_VOICE_ONLY");
                    }

                    // Ensure that radio is on. If not able to power on due to phone being
                    // unavailable, return THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE.
                    if (!setRadioPowerForThermal(true)) {
                        thermalMitigationResult =
                                TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
                        break;
                    }

                    setDataEnabledForReason(subId, TelephonyManager.DATA_ENABLED_REASON_THERMAL,
                            false, callingPackage);
                    thermalMitigationResult = TelephonyManager.THERMAL_MITIGATION_RESULT_SUCCESS;
                    break;
                case ThermalMitigationRequest.THERMAL_MITIGATION_ACTION_RADIO_OFF:
                    if (thermalMitigationRequest.getDataThrottlingRequest() != null) {
                        throw new IllegalArgumentException("dataThrottlingRequest  must be null for"
                                + " ThermalMitigationRequest.THERMAL_MITIGATION_ACTION_RADIO_OFF");
                    }

                    TelecomAccountRegistry registry = TelecomAccountRegistry.getInstance(null);
                    if (registry != null) {
                        Phone phone = getPhone(subId);
                        if (phone == null) {
                            thermalMitigationResult =
                                    TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
                            break;
                        }

                        TelephonyConnectionService service =
                                registry.getTelephonyConnectionService();
                        if (service != null && service.isEmergencyCallPending()) {
                            Log.e(LOG_TAG, "An emergency call is pending");
                            thermalMitigationResult =
                                    TelephonyManager.THERMAL_MITIGATION_RESULT_INVALID_STATE;
                            break;
                        } else if (isAnyPhoneInEmergencyState()) {
                            thermalMitigationResult =
                                    TelephonyManager.THERMAL_MITIGATION_RESULT_INVALID_STATE;
                            break;
                        }
                    } else {
                        thermalMitigationResult =
                                TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
                        break;
                    }

                    // Turn radio off. If not able to power off due to phone being unavailable,
                    // return THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE.
                    if (!setRadioPowerForThermal(false)) {
                        thermalMitigationResult =
                                TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_NOT_AVAILABLE;
                        break;
                    }
                    thermalMitigationResult =
                            TelephonyManager.THERMAL_MITIGATION_RESULT_SUCCESS;
                    break;
                default:
                    throw new IllegalArgumentException("the requested thermalMitigationAction does "
                            + "not exist. Requested action: " + thermalMitigationAction);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            Log.e(LOG_TAG, "thermalMitigationRequest. Exception e =" + e);
            thermalMitigationResult = TelephonyManager.THERMAL_MITIGATION_RESULT_MODEM_ERROR;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }

        if (DBG) {
            log("thermalMitigationRequest returning with thermalMitigationResult: "
                    + thermalMitigationResult);
        }

        return thermalMitigationResult;
    }

    /**
     * Set the GbaService Package Name that Telephony will bind to.
     *
     * @param subId The sim that the GbaService is associated with.
     * @param packageName The name of the package to be replaced with.
     * @return true if setting the GbaService to bind to succeeded, false if it did not.
     */
    @Override
    public boolean setBoundGbaServiceOverride(int subId, String packageName) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            return getGbaManager(subId).overrideServicePackage(packageName);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return the package name of the currently bound GbaService.
     *
     * @param subId The sim that the GbaService is associated with.
     * @return the package name of the GbaService configuration, null if GBA is not supported.
     */
    @Override
    public String getBoundGbaService(int subId) {
        enforceReadPrivilegedPermission("getBoundGbaServicePackage");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getGbaManager(subId).getServicePackage();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the release time for telephony to unbind GbaService.
     *
     * @param subId The sim that the GbaService is associated with.
     * @param interval The release time to unbind GbaService by millisecond.
     * @return true if setting the GbaService to bind to succeeded, false if it did not.
     */
    @Override
    public boolean setGbaReleaseTimeOverride(int subId, int interval) {
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            return getGbaManager(subId).overrideReleaseTime(interval);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Return the release time for telephony to unbind GbaService.
     *
     * @param subId The sim that the GbaService is associated with.
     * @return The release time to unbind GbaService by millisecond.
     */
    @Override
    public int getGbaReleaseTime(int subId) {
        enforceReadPrivilegedPermission("getGbaReleaseTime");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getGbaManager(subId).getReleaseTime();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private GbaManager getGbaManager(int subId) {
        GbaManager instance = GbaManager.getInstance(subId);
        if (instance == null) {
            String packageName = mApp.getResources().getString(R.string.config_gba_package);
            int releaseTime = mApp.getResources().getInteger(R.integer.config_gba_release_time);
            instance = GbaManager.make(mApp, subId, packageName, releaseTime);
        }
        return instance;
    }

    /**
     * indicate whether the device and the carrier can support
     * RCS VoLTE single registration.
     */
    @Override
    public boolean isRcsVolteSingleRegistrationCapable(int subId) {
        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(mApp, subId,
                Binder.getCallingUid(), "isRcsVolteSingleRegistrationCapable",
                Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION,
                permission.READ_PRIVILEGED_PHONE_STATE);

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            RcsProvisioningMonitor rpm = RcsProvisioningMonitor.getInstance();
            if (rpm != null) {
                Boolean isCapable = rpm.isRcsVolteSingleRegistrationEnabled(subId);
                if (isCapable != null) {
                    return isCapable;
                }
            }
            throw new ServiceSpecificException(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE,
                    "service is temporarily unavailable.");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Register RCS provisioning callback.
     */
    @Override
    public void registerRcsProvisioningCallback(int subId,
            IRcsConfigCallback callback) {
        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(mApp, subId,
                Binder.getCallingUid(), "registerRcsProvisioningCallback",
                Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION,
                permission.READ_PRIVILEGED_PHONE_STATE);

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }
        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, getCurrentPackageName(),
                Binder.getCallingUserHandle())) {
            if (!isImsAvailableOnDevice()) {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                        "IMS not available on device.");
            }
        } else {
            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION, "registerRcsProvisioningCallback");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!RcsProvisioningMonitor.getInstance()
                    .registerRcsProvisioningCallback(subId, callback)) {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION,
                        "Active subscription not found.");
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Unregister RCS provisioning callback.
     */
    @Override
    public void unregisterRcsProvisioningCallback(int subId,
            IRcsConfigCallback callback) {
        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(mApp, subId,
                Binder.getCallingUid(), "unregisterRcsProvisioningCallback",
                Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION,
                permission.READ_PRIVILEGED_PHONE_STATE);

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }

        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, getCurrentPackageName(),
                Binder.getCallingUserHandle())) {
            if (!isImsAvailableOnDevice()) {
                // operation failed silently
                Rlog.w(LOG_TAG, "IMS not available on device.");
                return;
            }
        } else {
            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION,
                    "unregisterRcsProvisioningCallback");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            RcsProvisioningMonitor.getInstance()
                    .unregisterRcsProvisioningCallback(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * trigger RCS reconfiguration.
     */
    public void triggerRcsReconfiguration(int subId) {
        TelephonyPermissions.enforceAnyPermissionGranted(mApp, Binder.getCallingUid(),
                "triggerRcsReconfiguration",
                Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION);

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }
        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, getCurrentPackageName(),
                Binder.getCallingUserHandle())) {
            if (!isImsAvailableOnDevice()) {
                // ProvisioningManager can not handle ServiceSpecificException.
                // Throw the IllegalStateException and annotate ProvisioningManager.
                throw new IllegalStateException("IMS not available on device.");
            }
        } else {
            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION, "triggerRcsReconfiguration");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            RcsProvisioningMonitor.getInstance().requestReconfig(subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Provide the client configuration parameters of the RCS application.
     */
    public void setRcsClientConfiguration(int subId, RcsClientConfiguration rcc) {
        TelephonyPermissions.enforceAnyPermissionGranted(mApp, Binder.getCallingUid(),
                "setRcsClientConfiguration",
                Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION);

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid Subscription ID: " + subId);
        }
        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, getCurrentPackageName(),
                Binder.getCallingUserHandle())) {
            if (!isImsAvailableOnDevice()) {
                throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                        "IMS not available on device.");
            }
        } else {
            enforceTelephonyFeatureWithException(getCurrentPackageName(),
                    FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION, "setRcsClientConfiguration");
        }

        final long identity = Binder.clearCallingIdentity();

        try {
            IImsConfig configBinder = getImsConfig(getSlotIndex(subId), ImsFeature.FEATURE_RCS);
            if (configBinder == null) {
                Rlog.e(LOG_TAG, "null result for setRcsClientConfiguration");
                throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION,
                        "could not find the requested subscription");
            } else {
                configBinder.setRcsClientConfiguration(rcc);
            }

            RcsStats.getInstance().onRcsClientProvisioningStats(subId,
                    RCS_CLIENT_PROVISIONING_STATS__EVENT__CLIENT_PARAMS_SENT);
        } catch (RemoteException e) {
            Rlog.e(LOG_TAG, "fail to setRcsClientConfiguration " + e.getMessage());
            throw new ServiceSpecificException(ImsException.CODE_ERROR_SERVICE_UNAVAILABLE,
                    "service is temporarily unavailable.");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Enables or disables the test mode for RCS VoLTE single registration.
     */
    @Override
    public void setRcsSingleRegistrationTestModeEnabled(boolean enabled) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setRcsSingleRegistrationTestModeEnabled");

        RcsProvisioningMonitor.getInstance().setTestModeEnabled(enabled);
    }

    /**
     * Gets the test mode for RCS VoLTE single registration.
     */
    @Override
    public boolean getRcsSingleRegistrationTestModeEnabled() {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "getRcsSingleRegistrationTestModeEnabled");

        return RcsProvisioningMonitor.getInstance().getTestModeEnabled();
    }

    /**
     * Overrides the config of RCS VoLTE single registration enabled for the device.
     */
    @Override
    public void setDeviceSingleRegistrationEnabledOverride(String enabledStr) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setDeviceSingleRegistrationEnabledOverride");
        enforceModifyPermission();

        Boolean enabled = "NULL".equalsIgnoreCase(enabledStr) ? null
                : Boolean.parseBoolean(enabledStr);
        RcsProvisioningMonitor.getInstance().overrideDeviceSingleRegistrationEnabled(enabled);
        mApp.imsRcsController.setDeviceSingleRegistrationSupportOverride(enabled);
    }

    /**
     * Sends a device to device communication message.  Only usable via shell.
     * @param message message to send.
     * @param value message value.
     */
    @Override
    public void sendDeviceToDeviceMessage(int message, int value) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "sendDeviceToDeviceMessage");
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            TelephonyConnectionService service =
                    TelecomAccountRegistry.getInstance(null).getTelephonyConnectionService();
            if (service == null) {
                Rlog.e(LOG_TAG, "sendDeviceToDeviceMessage: not in a call.");
                return;
            }
            service.sendTestDeviceToDeviceMessage(message, value);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the specified device to device transport active.
     * @param transport The transport to set active.
     */
    @Override
    public void setActiveDeviceToDeviceTransport(@NonNull String transport) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setActiveDeviceToDeviceTransport");
        enforceModifyPermission();

        final long identity = Binder.clearCallingIdentity();
        try {
            TelephonyConnectionService service =
                    TelecomAccountRegistry.getInstance(null).getTelephonyConnectionService();
            if (service == null) {
                Rlog.e(LOG_TAG, "setActiveDeviceToDeviceTransport: not in a call.");
                return;
            }
            service.setActiveDeviceToDeviceTransport(transport);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setDeviceToDeviceForceEnabled(boolean isForceEnabled) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setDeviceToDeviceForceEnabled");

        final long identity = Binder.clearCallingIdentity();
        try {
            Arrays.stream(PhoneFactory.getPhones()).forEach(
                    p -> {
                        Phone thePhone = p.getImsPhone();
                        if (thePhone != null && thePhone instanceof ImsPhone) {
                            ImsPhone imsPhone = (ImsPhone) thePhone;
                            CallTracker tracker = imsPhone.getCallTracker();
                            if (tracker != null && tracker instanceof ImsPhoneCallTracker) {
                                ImsPhoneCallTracker imsPhoneCallTracker =
                                        (ImsPhoneCallTracker) tracker;
                                imsPhoneCallTracker.setDeviceToDeviceForceEnabled(isForceEnabled);
                            }
                        }
                    }
            );
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Gets the config of RCS VoLTE single registration enabled for the device.
     */
    @Override
    public boolean getDeviceSingleRegistrationEnabled() {
        enforceReadPrivilegedPermission("getDeviceSingleRegistrationEnabled");
        return RcsProvisioningMonitor.getInstance().getDeviceSingleRegistrationEnabled();
    }

    /**
     * Overrides the config of RCS VoLTE single registration enabled for the carrier/subscription.
     */
    @Override
    public boolean setCarrierSingleRegistrationEnabledOverride(int subId, String enabledStr) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setCarrierSingleRegistrationEnabledOverride");
        enforceModifyPermission();

        Boolean enabled = "NULL".equalsIgnoreCase(enabledStr) ? null
                : Boolean.parseBoolean(enabledStr);
        return RcsProvisioningMonitor.getInstance().overrideCarrierSingleRegistrationEnabled(
                subId, enabled);
    }

    /**
     * Gets the config of RCS VoLTE single registration enabled for the carrier/subscription.
     */
    @Override
    public boolean getCarrierSingleRegistrationEnabled(int subId) {
        enforceReadPrivilegedPermission("getCarrierSingleRegistrationEnabled");
        return RcsProvisioningMonitor.getInstance().getCarrierSingleRegistrationEnabled(subId);
    }

    /**
     * Overrides the ims feature validation result
     */
    @Override
    public boolean setImsFeatureValidationOverride(int subId, String enabledStr) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setImsFeatureValidationOverride");

        Boolean enabled = "NULL".equalsIgnoreCase(enabledStr) ? null
                : Boolean.parseBoolean(enabledStr);
        return RcsProvisioningMonitor.getInstance().overrideImsFeatureValidation(
                subId, enabled);
    }

    /**
     * Gets the ims feature validation override value
     */
    @Override
    public boolean getImsFeatureValidationOverride(int subId) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "getImsFeatureValidationOverride");
        return RcsProvisioningMonitor.getInstance().getImsFeatureValidationOverride(subId);
    }

    /**
     * Get the mobile provisioning url that is used to launch a browser to allow users to manage
     * their mobile plan.
     */
    @Override
    public String getMobileProvisioningUrl() {
        enforceReadPrivilegedPermission("getMobileProvisioningUrl");
        final long identity = Binder.clearCallingIdentity();
        try {
            return getDefaultPhone().getMobileProvisioningUrl();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the EAB contact from the EAB database.
     */
    @Override
    public String getContactFromEab(String contact) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "getContactFromEab");
        enforceModifyPermission();
        final long identity = Binder.clearCallingIdentity();
        try {
            return EabUtil.getContactFromEab(getDefaultPhone().getContext(), contact);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the EAB capability from the EAB database.
     */
    @Override
    public String getCapabilityFromEab(String contact) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "getCapabilityFromEab");
        enforceModifyPermission();
        final long identity = Binder.clearCallingIdentity();
        try {
            return EabUtil.getCapabilityFromEab(getDefaultPhone().getContext(), contact);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Remove the EAB contacts from the EAB database.
     */
    @Override
    public int removeContactFromEab(int subId, String contacts) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "removeCapabilitiesFromEab");
        enforceModifyPermission();
        final long identity = Binder.clearCallingIdentity();
        try {
            return EabUtil.removeContactFromEab(subId, contacts, getDefaultPhone().getContext());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public boolean getDeviceUceEnabled() {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "getDeviceUceEnabled");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.getDeviceUceEnabled();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setDeviceUceEnabled(boolean isEnabled) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "setDeviceUceEnabled");
        final long identity = Binder.clearCallingIdentity();
        try {
            mApp.setDeviceUceEnabled(isEnabled);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Add new feature tags to the Set used to calculate the capabilities in PUBLISH.
     * @return current RcsContactUceCapability instance that will be used for PUBLISH.
     */
    // Used for SHELL command only right now.
    @Override
    public RcsContactUceCapability addUceRegistrationOverrideShell(int subId,
            List<String> featureTags) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "addUceRegistrationOverrideShell");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.addUceRegistrationOverrideShell(subId,
                    new ArraySet<>(featureTags));
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Remove existing feature tags to the Set used to calculate the capabilities in PUBLISH.
     * @return current RcsContactUceCapability instance that will be used for PUBLISH.
     */
    // Used for SHELL command only right now.
    @Override
    public RcsContactUceCapability removeUceRegistrationOverrideShell(int subId,
            List<String> featureTags) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "removeUceRegistrationOverrideShell");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.removeUceRegistrationOverrideShell(subId,
                    new ArraySet<>(featureTags));
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Clear all overrides in the Set used to calculate the capabilities in PUBLISH.
     * @return current RcsContactUceCapability instance that will be used for PUBLISH.
     */
    // Used for SHELL command only right now.
    @Override
    public RcsContactUceCapability clearUceRegistrationOverrideShell(int subId) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "clearUceRegistrationOverrideShell");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.clearUceRegistrationOverrideShell(subId);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * @return current RcsContactUceCapability instance that will be used for PUBLISH.
     */
    // Used for SHELL command only right now.
    @Override
    public RcsContactUceCapability getLatestRcsContactUceCapabilityShell(int subId) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "getLatestRcsContactUceCapabilityShell");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.getLatestRcsContactUceCapabilityShell(subId);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the last PIDF XML sent to the network during the last PUBLISH or "none" if the
     * device does not have an active PUBLISH.
     */
    // Used for SHELL command only right now.
    @Override
    public String getLastUcePidfXmlShell(int subId) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "uceGetLastPidfXml");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.getLastUcePidfXmlShell(subId);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Remove UCE requests cannot be sent to the network status.
     */
    // Used for SHELL command only right now.
    @Override
    public boolean removeUceRequestDisallowedStatus(int subId) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "uceRemoveDisallowedStatus");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.removeUceRequestDisallowedStatus(subId);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Remove UCE requests cannot be sent to the network status.
     */
    // Used for SHELL command only.
    @Override
    public boolean setCapabilitiesRequestTimeout(int subId, long timeoutAfterMs) {
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "setCapRequestTimeout");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mApp.imsRcsController.setCapabilitiesRequestTimeout(subId, timeoutAfterMs);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode(), e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setSignalStrengthUpdateRequest(int subId, SignalStrengthUpdateRequest request,
            String callingPackage) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "setSignalStrengthUpdateRequest");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "setSignalStrengthUpdateRequest");

        final int callingUid = Binder.getCallingUid();
        // Verify that tha callingPackage belongs to the calling UID
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(callingUid, callingPackage);

        validateSignalStrengthUpdateRequest(mApp, request, callingUid);

        final long identity = Binder.clearCallingIdentity();
        try {
            Object result = sendRequest(CMD_SET_SIGNAL_STRENGTH_UPDATE_REQUEST,
                    new Pair<Integer, SignalStrengthUpdateRequest>(callingUid, request), subId);

            if (result instanceof IllegalStateException) {
                throw (IllegalStateException) result;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void clearSignalStrengthUpdateRequest(int subId, SignalStrengthUpdateRequest request,
            String callingPackage) {
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(
                mApp, subId, "clearSignalStrengthUpdateRequest");

        enforceTelephonyFeatureWithException(callingPackage,
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS, "clearSignalStrengthUpdateRequest");

        final int callingUid = Binder.getCallingUid();
        // Verify that tha callingPackage belongs to the calling UID
        mApp.getSystemService(AppOpsManager.class)
                .checkPackage(callingUid, callingPackage);

        final long identity = Binder.clearCallingIdentity();
        try {
            Object result = sendRequest(CMD_CLEAR_SIGNAL_STRENGTH_UPDATE_REQUEST,
                    new Pair<Integer, SignalStrengthUpdateRequest>(callingUid, request), subId);

            if (result instanceof IllegalStateException) {
                throw (IllegalStateException) result;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static void validateSignalStrengthUpdateRequest(Context context,
            SignalStrengthUpdateRequest request, int callingUid) {
        if (callingUid == Process.PHONE_UID || callingUid == Process.SYSTEM_UID) {
            // phone/system process do not have further restriction on request
            return;
        }

        // Applications has restrictions on how to use the request:
        // Non-system callers need permission to set mIsSystemThresholdReportingRequestedWhileIdle
        if (request.isSystemThresholdReportingRequestedWhileIdle()) {
            context.enforceCallingOrSelfPermission(
                    android.Manifest.permission.LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH,
                    "validateSignalStrengthUpdateRequest");
        }

        for (SignalThresholdInfo info : request.getSignalThresholdInfos()) {
            // Only system caller can set mHysteresisMs/mIsEnabled.
            if (info.getHysteresisMs() != SignalThresholdInfo.HYSTERESIS_MS_DISABLED
                    || info.isEnabled()) {
                throw new IllegalArgumentException(
                        "Only system can set hide fields in SignalThresholdInfo");
            }

            // Thresholds length for each RAN need in range. This has been validated in
            // SignalThresholdInfo#Builder#setThreshold. Here we prevent apps calling hide method
            // setThresholdUnlimited (e.g. through reflection) with too short or too long thresholds
            final int[] thresholds = info.getThresholds();
            Objects.requireNonNull(thresholds);
            if (thresholds.length < SignalThresholdInfo.getMinimumNumberOfThresholdsAllowed()
                    || thresholds.length
                    > SignalThresholdInfo.getMaximumNumberOfThresholdsAllowed()) {
                throw new IllegalArgumentException(
                        "thresholds length is out of range: " + thresholds.length);
            }
        }
    }

    /**
     * Gets the current phone capability.
     *
     * Requires carrier privileges or READ_PRECISE_PHONE_STATE permission.
     * @return the PhoneCapability which describes the data connection capability of modem.
     * It's used to evaluate possible phone config change, for example from single
     * SIM device to multi-SIM device.
     */
    @Override
    public PhoneCapability getPhoneCapability() {
        enforceReadPrivilegedPermission("getPhoneCapability");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY, "getPhoneCapability");

        final long identity = Binder.clearCallingIdentity();
        try {
            return mPhoneConfigurationManager.getCurrentPhoneCapability();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Prepare TelephonyManager for an unattended reboot. The reboot is
     * required to be done shortly after the API is invoked.
     */
    @Override
    @TelephonyManager.PrepareUnattendedRebootResult
    public int prepareForUnattendedReboot() {
        WorkSource workSource = getWorkSource(Binder.getCallingUid());
        enforceRebootPermission();

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "prepareForUnattendedReboot");

        final long identity = Binder.clearCallingIdentity();
        try {
            return (int) sendRequest(CMD_PREPARE_UNATTENDED_REBOOT, null, workSource);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Request to get the current slicing configuration including URSP rules and
     * NSSAIs (configured, allowed and rejected).
     *
     * Requires carrier privileges or READ_PRIVILEGED_PHONE_STATE permission.
     */
    @Override
    public void getSlicingConfig(ResultReceiver callback) {
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, SubscriptionManager.INVALID_SUBSCRIPTION_ID, "getSlicingConfig");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS,
                "getSlicingConfig");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getDefaultPhone();
            sendRequestAsync(CMD_GET_SLICING_CONFIG, callback, phone, null);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Check whether the given premium capability is available for purchase from the carrier.
     *
     * @param capability The premium capability to check.
     * @param subId The subId to check the premium capability for.
     *
     * @return Whether the given premium capability is available to purchase.
     */
    @Override
    public boolean isPremiumCapabilityAvailableForPurchase(int capability, int subId) {
        if (!TelephonyPermissions.checkCallingOrSelfReadNonDangerousPhoneStateNoThrow(
                mApp, "isPremiumCapabilityAvailableForPurchase")) {
            log("Premium capability "
                    + TelephonyManager.convertPremiumCapabilityToString(capability)
                    + " is not available for purchase due to missing permissions.");
            throw new SecurityException("isPremiumCapabilityAvailableForPurchase requires "
                    + "permission READ_BASIC_PHONE_STATE.");
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "isPremiumCapabilityAvailableForPurchase");

        Phone phone = getPhone(subId);
        if (phone == null) {
            loge("isPremiumCapabilityAvailableForPurchase: phone is null, subId=" + subId);
            return false;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            return SlicePurchaseController.getInstance(phone, mFeatureFlags)
                    .isPremiumCapabilityAvailableForPurchase(capability);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Purchase the given premium capability from the carrier.
     *
     * @param capability The premium capability to purchase.
     * @param callback The result of the purchase request.
     * @param subId The subId to purchase the premium capability for.
     */
    @Override
    public void purchasePremiumCapability(int capability, IIntegerConsumer callback, int subId) {
        log("purchasePremiumCapability: capability="
                + TelephonyManager.convertPremiumCapabilityToString(capability) + ", caller="
                + getCurrentPackageName());

        if (!TelephonyPermissions.checkCallingOrSelfReadNonDangerousPhoneStateNoThrow(
                mApp, "purchasePremiumCapability")) {
            log("purchasePremiumCapability "
                    + TelephonyManager.convertPremiumCapabilityToString(capability)
                    + " failed due to missing permissions.");
            throw new SecurityException("purchasePremiumCapability requires permission "
                    + "READ_BASIC_PHONE_STATE.");
        } else if (!TelephonyPermissions.checkInternetPermissionNoThrow(
                mApp, "purchasePremiumCapability")) {
            log("purchasePremiumCapability "
                    + TelephonyManager.convertPremiumCapabilityToString(capability)
                    + " failed due to missing permissions.");
            throw new SecurityException("purchasePremiumCapability requires permission INTERNET.");
        }

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_DATA, "purchasePremiumCapability");

        Phone phone = getPhone(subId);
        if (phone == null) {
            try {
                int result = TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_REQUEST_FAILED;
                callback.accept(result);
                loge("purchasePremiumCapability: phone is null, subId=" + subId);
            } catch (RemoteException e) {
                String logStr = "Purchase premium capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability)
                        + " failed due to RemoteException handling null phone: " + e;
                if (DBG) log(logStr);
                AnomalyReporter.reportAnomaly(
                        UUID.fromString(PURCHASE_PREMIUM_CAPABILITY_ERROR_UUID), logStr);
            }
            return;
        }

        String callingProcess;
        try {
            callingProcess = mApp.getPackageManager().getApplicationInfo(
                    getCurrentPackageName(), 0).processName;
        } catch (PackageManager.NameNotFoundException e) {
            callingProcess = getCurrentPackageName();
        }

        boolean isVisible = false;
        ActivityManager am = mApp.getSystemService(ActivityManager.class);
        if (am != null) {
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    log("purchasePremiumCapability: process " + process.processName
                            + " has importance " + process.importance);
                    if (process.processName.equals(callingProcess) && process.importance
                            <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        isVisible = true;
                        break;
                    }
                }
            }
        }

        if (!isVisible) {
            try {
                int result = TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_FOREGROUND;
                callback.accept(result);
                loge("purchasePremiumCapability: " + callingProcess + " is not in the foreground.");
            } catch (RemoteException e) {
                String logStr = "Purchase premium capability "
                        + TelephonyManager.convertPremiumCapabilityToString(capability)
                        + " failed due to RemoteException handling background application: " + e;
                if (DBG) log(logStr);
                AnomalyReporter.reportAnomaly(
                        UUID.fromString(PURCHASE_PREMIUM_CAPABILITY_ERROR_UUID), logStr);
            }
            return;
        }

        sendRequestAsync(CMD_PURCHASE_PREMIUM_CAPABILITY,
                new PurchasePremiumCapabilityArgument(capability, callback), phone, null);
    }

    /**
     * Register an IMS connection state callback
     */
    @Override
    public void registerImsStateCallback(int subId, int feature, IImsStateCallback cb,
            String callingPackage) {
        if (feature == ImsFeature.FEATURE_MMTEL) {
            // ImsMmTelManager
            // The following also checks READ_PRIVILEGED_PHONE_STATE.
            TelephonyPermissions
                    .enforceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                            mApp, subId, "registerImsStateCallback");
        } else if (feature == ImsFeature.FEATURE_RCS) {
            // ImsRcsManager or SipDelegateManager
            TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(mApp, subId,
                    Binder.getCallingUid(), "registerImsStateCallback",
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                    Manifest.permission.READ_PRECISE_PHONE_STATE,
                    Manifest.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE,
                    Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION);
        }

        if (!ImsManager.isImsSupportedOnDevice(mApp)) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }

        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_INVALID_SUBSCRIPTION);
        }

        ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
        if (controller == null) {
            throw new ServiceSpecificException(ImsException.CODE_ERROR_UNSUPPORTED_OPERATION,
                    "IMS not available on device.");
        }

        if (callingPackage == null) {
            callingPackage = getCurrentPackageName();
        }

        final long token = Binder.clearCallingIdentity();
        try {
            int slotId = getSlotIndexOrException(subId);
            controller.registerImsStateCallback(subId, feature, cb, callingPackage);
        } catch (ImsException e) {
            throw new ServiceSpecificException(e.getCode());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Unregister an IMS connection state callback
     */
    @Override
    public void unregisterImsStateCallback(IImsStateCallback cb) {
        final long token = Binder.clearCallingIdentity();
        ImsStateCallbackController controller = ImsStateCallbackController.getInstance();
        if (controller == null) {
            return;
        }
        try {
            controller.unregisterImsStateCallback(cb);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * @return {@CellIdentity} last known cell identity {@CellIdentity}.
     *
     * Require {@link android.Manifest.permission#ACCESS_FINE_LOCATION} and
     * {@link android.Manifest.permission#ACCESS_LAST_KNOWN_CELL_ID}, otherwise throws
     * SecurityException.
     *
     * If there is current registered network this value will be same as the registered cell
     * identity. If the device goes out of service the previous cell identity is cached and
     * will be returned. If the cache age of the Cell identity is more than 24 hours
     * it will be cleared and null will be returned.
     *
     */
    @Override
    public @Nullable CellIdentity getLastKnownCellIdentity(int subId, String callingPackage,
            String callingFeatureId) {
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        LocationAccessPolicy.LocationPermissionResult fineLocationResult =
                LocationAccessPolicy.checkLocationPermission(mApp,
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(callingPackage)
                                .setCallingFeatureId(callingFeatureId)
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("getLastKnownCellIdentity")
                                .setLogAsInfo(true)
                                .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.Q)
                                .setMinSdkVersionForEnforcement(Build.VERSION_CODES.Q)
                                .build());

        boolean hasFinePermission =
                fineLocationResult == LocationAccessPolicy.LocationPermissionResult.ALLOWED;
        if (!hasFinePermission
                || !TelephonyPermissions.checkLastKnownCellIdAccessPermission(mApp)) {
            throw new SecurityException("getLastKnownCellIdentity need ACCESS_FINE_LOCATION "
                    + "and ACCESS_LAST_KNOWN_CELL_ID permission.");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            ServiceStateTracker sst = getPhoneFromSubIdOrDefault(subId).getServiceStateTracker();
            if (sst == null) return null;
            return sst.getLastKnownCellIdentity();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Sets the modem service class Name that Telephony will bind to.
     *
     * @param serviceName The class name of the modem service.
     * @return true if the operation is succeed, otherwise false.
     */
    public boolean setModemService(String serviceName) {
        Log.d(LOG_TAG, "setModemService - " + serviceName);
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "setModemService");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setModemService");
        return mPhoneConfigurationManager.setModemService(serviceName);
    }

    /**
     * Return the class name of the currently bounded modem service.
     *
     * @return the class name of the modem service.
     */
    public String getModemService() {
        String result;
        Log.d(LOG_TAG, "getModemService");
        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(), "getModemService");
        TelephonyPermissions
                .enforceCallingOrSelfReadPrivilegedPhoneStatePermissionOrCarrierPrivilege(
                        mApp, SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                        "getModemService");
        result = mPhoneConfigurationManager.getModemService();
        Log.d(LOG_TAG, "result = " + result);
        return result;
    }

    /**
     * Get the aggregated satellite plmn list. This API collects plmn data from multiple sources,
     * including carrier config, entitlement server, and config update.
     *
     * @param subId subId The subscription ID of the carrier.
     *
     * @return List of plmns for carrier satellite service. If no plmn is available, empty list will
     * be returned.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @NonNull public List<String> getSatellitePlmnsForCarrier(int subId) {
        enforceSatelliteCommunicationPermission("getSatellitePlmnsForCarrier");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mSatelliteController.getSatellitePlmnsForCarrier(subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void setVoiceServiceStateOverride(int subId, boolean hasService, String callingPackage) {
        // Only telecom (and shell, for CTS purposes) is allowed to call this method.
        mApp.enforceCallingOrSelfPermission(
                permission.BIND_TELECOM_CONNECTION_SERVICE, "setVoiceServiceStateOverride");
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhone(subId);
            if (phone == null) return;
            Log.i(LOG_TAG, "setVoiceServiceStateOverride: subId=" + subId + ", phone=" + phone
                    + ", hasService=" + hasService + ", callingPackage=" + callingPackage);
            phone.setVoiceServiceStateOverride(hasService);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * set removable eSIM as default eUICC.
     *
     * @hide
     */
    @Override
    public void setRemovableEsimAsDefaultEuicc(boolean isDefault, String callingPackage) {
        enforceModifyPermission();
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        final long identity = Binder.clearCallingIdentity();
        try {
            UiccController.getInstance().setRemovableEsimAsDefaultEuicc(isDefault);
        }  finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns whether the removable eSIM is default eUICC or not.
     *
     * @hide
     */
    @Override
    public boolean isRemovableEsimDefaultEuicc(String callingPackage) {
        enforceReadPrivilegedPermission("isRemovableEsimDefaultEuicc");
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);

        final long identity = Binder.clearCallingIdentity();
        try {
            return UiccController.getInstance().isRemovableEsimDefaultEuicc();
        }  finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the component name of the default app to direct respond-via-message intent for the
     * user associated with this subscription, update the cache if there is no respond-via-message
     * application currently configured for this user.
     * @return component name of the app and class to direct Respond Via Message intent to, or
     * {@code null} if the functionality is not supported.
     * @hide
     */
    @Override
    public @Nullable ComponentName getDefaultRespondViaMessageApplication(int subId,
            boolean updateIfNeeded) {
        enforceInteractAcrossUsersPermission("getDefaultRespondViaMessageApplication");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_MESSAGING,
                "getDefaultRespondViaMessageApplication");

        Context context = getPhoneFromSubIdOrDefault(subId).getContext();

        UserHandle userHandle = null;
        final long identity = Binder.clearCallingIdentity();
        try {
            userHandle = TelephonyUtils.getSubscriptionUserHandle(context, subId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return SmsApplication.getDefaultRespondViaMessageApplicationAsUser(context,
                updateIfNeeded, userHandle);
    }

    /**
     * Set whether the device is able to connect with null ciphering or integrity
     * algorithms. This is a global setting and will apply to all active subscriptions
     * and all new subscriptions after this.
     *
     * @param enabled when true, null  cipher and integrity algorithms are allowed.
     * @hide
     */
    @Override
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public void setNullCipherAndIntegrityEnabled(boolean enabled) {
        enforceModifyPermission();
        checkForNullCipherAndIntegritySupport();

        // Persist the state of our preference. Each GsmCdmaPhone instance is responsible
        // for listening to these preference changes and applying them immediately.
        SharedPreferences.Editor editor = mTelephonySharedPreferences.edit();
        editor.putBoolean(Phone.PREF_NULL_CIPHER_AND_INTEGRITY_ENABLED, enabled);
        editor.apply();

        for (Phone phone: PhoneFactory.getPhones()) {
            phone.handleNullCipherEnabledChange();
        }
    }


    /**
     * Get whether the device is able to connect with null ciphering or integrity
     * algorithms. Note that this retrieves the phone-global preference and not
     * the state of the radio.
     *
     * @throws SecurityException if {@link permission#MODIFY_PHONE_STATE} is not satisfied
     * @throws UnsupportedOperationException if the device does not support the minimum HAL
     * version for this feature.
     * @hide
     */
    @Override
    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    public boolean isNullCipherAndIntegrityPreferenceEnabled() {
        enforceReadPermission();
        checkForNullCipherAndIntegritySupport();
        return getDefaultPhone().getNullCipherAndIntegrityEnabledPreference();
    }

    private void checkForNullCipherAndIntegritySupport() {
        if (getHalVersion(HAL_SERVICE_NETWORK) < MIN_NULL_CIPHER_AND_INTEGRITY_VERSION) {
            throw new UnsupportedOperationException(
                    "Null cipher and integrity operations require HAL 2.1 or above");
        }
        if (!getDefaultPhone().isNullCipherAndIntegritySupported()) {
            throw new UnsupportedOperationException(
                    "Null cipher and integrity operations unsupported by modem");
        }
    }

    private void checkForIdentifierDisclosureNotificationSupport() {
        if (getHalVersion(HAL_SERVICE_NETWORK) < MIN_IDENTIFIER_DISCLOSURE_VERSION) {
            throw new UnsupportedOperationException(
                    "Cellular identifier disclosure transparency operations require HAL 2.2 or "
                            + "above");
        }
        if (!getDefaultPhone().isIdentifierDisclosureTransparencySupported()) {
            throw new UnsupportedOperationException(
                    "Cellular identifier disclosure transparency operations unsupported by modem");
        }
    }

    private void checkForNullCipherNotificationSupport() {
        if (getHalVersion(HAL_SERVICE_NETWORK) < MIN_NULL_CIPHER_NOTIFICATION_VERSION) {
            throw new UnsupportedOperationException(
                    "Null cipher notification operations require HAL 2.2 or above");
        }
        if (!getDefaultPhone().isNullCipherNotificationSupported()) {
            throw new UnsupportedOperationException(
                    "Null cipher notification operations unsupported by modem");
        }
    }

    /**
     * Get the SIM state for the slot index.
     * For Remote-SIMs, this method returns {@link IccCardConstants.State#UNKNOWN}
     *
     * @return SIM state as the ordinal of {@link IccCardConstants.State}
     */
    @Override
    @SimState
    public int getSimStateForSlotIndex(int slotIndex) {
        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION, "getSimStateForSlotIndex");

        IccCardConstants.State simState;
        if (slotIndex < 0) {
            simState = IccCardConstants.State.UNKNOWN;
        } else {
            Phone phone = null;
            try {
                phone = PhoneFactory.getPhone(slotIndex);
            } catch (IllegalStateException e) {
                // ignore
            }
            if (phone == null) {
                simState = IccCardConstants.State.UNKNOWN;
            } else {
                IccCard icc = phone.getIccCard();
                if (icc == null) {
                    simState = IccCardConstants.State.UNKNOWN;
                } else {
                    simState = icc.getState();
                }
            }
        }
        return simState.ordinal();
    }

    private void persistEmergencyCallDiagnosticDataInternal(@NonNull String dropboxTag,
            boolean enableLogcat,
            long logcatStartTimestampMillis, boolean enableTelecomDump,
            boolean enableTelephonyDump) {
        DropBoxManager db = mApp.getSystemService(DropBoxManager.class);
        TelephonyManager.EmergencyCallDiagnosticData.Builder ecdDataBuilder =
                new TelephonyManager.EmergencyCallDiagnosticData.Builder();
        ecdDataBuilder
                .setTelecomDumpsysCollectionEnabled(enableTelecomDump)
                .setTelephonyDumpsysCollectionEnabled(enableTelephonyDump);
        if (enableLogcat) {
            ecdDataBuilder.setLogcatCollectionStartTimeMillis(logcatStartTimestampMillis);
        }
        TelephonyManager.EmergencyCallDiagnosticData ecdData = ecdDataBuilder.build();
        Log.d(LOG_TAG, "persisting with Params " + ecdData.toString());
        DiagnosticDataCollector ddc = new DiagnosticDataCollector(Runtime.getRuntime(),
                Executors.newCachedThreadPool(), db,
                mApp.getSystemService(ActivityManager.class).isLowRamDevice());
        ddc.persistEmergencyDianosticData(new DataCollectorConfig.Adapter(), ecdData, dropboxTag);
    }

    /**
     * Request telephony to persist state for debugging emergency call failures.
     *
     * @param dropboxTag                 Tag to use when persisting data to dropbox service.
     * @param enableLogcat               whether to collect logcat output
     * @param logcatStartTimestampMillis timestamp from when logcat buffers would be persisted
     * @param enableTelecomDump          whether to collect telecom dumpsys
     * @param enableTelephonyDump        whether to collect telephony dumpsys
     */
    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    public void persistEmergencyCallDiagnosticData(@NonNull String dropboxTag, boolean enableLogcat,
            long logcatStartTimestampMillis, boolean enableTelecomDump,
            boolean enableTelephonyDump) {
        mApp.enforceCallingPermission(android.Manifest.permission.DUMP,
                "persistEmergencyCallDiagnosticData");
        final long identity = Binder.clearCallingIdentity();
        try {
            persistEmergencyCallDiagnosticDataInternal(dropboxTag, enableLogcat,
                    logcatStartTimestampMillis, enableTelecomDump, enableTelephonyDump);

        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get current cell broadcast ranges.
     */
    @Override
    @RequiresPermission(android.Manifest.permission.MODIFY_CELL_BROADCASTS)
    public List<CellBroadcastIdRange> getCellBroadcastIdRanges(int subId) {
        mApp.enforceCallingPermission(android.Manifest.permission.MODIFY_CELL_BROADCASTS,
                "getCellBroadcastIdRanges");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_MESSAGING, "getCellBroadcastIdRanges");

        final long identity = Binder.clearCallingIdentity();
        try {
            return getPhone(subId).getCellBroadcastIdRanges();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set reception of cell broadcast messages with the list of the given ranges
     *
     * @param ranges the list of {@link CellBroadcastIdRange} to be enabled
     */
    @Override
    @RequiresPermission(android.Manifest.permission.MODIFY_CELL_BROADCASTS)
    public void setCellBroadcastIdRanges(int subId, @NonNull List<CellBroadcastIdRange> ranges,
            @Nullable IIntegerConsumer callback) {
        mApp.enforceCallingPermission(android.Manifest.permission.MODIFY_CELL_BROADCASTS,
                "setCellBroadcastIdRanges");

        enforceTelephonyFeatureWithException(getCurrentPackageName(),
                PackageManager.FEATURE_TELEPHONY_MESSAGING, "setCellBroadcastIdRanges");

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = getPhoneFromSubId(subId);
            if (DBG) {
                log("setCellBroadcastIdRanges for subId :" + subId + ", phone:" + phone);
            }
            phone.setCellBroadcastIdRanges(ranges, result -> {
                if (callback != null) {
                    try {
                        callback.accept(result);
                    } catch (RemoteException e) {
                        Log.w(LOG_TAG, "setCellBroadcastIdRanges: callback not available.");
                    }
                }
            });
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns whether the device supports the domain selection service.
     *
     * @return {@code true} if the device supports the domain selection service.
     */
    @Override
    public boolean isDomainSelectionSupported() {
        mApp.enforceCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                "isDomainSelectionSupported");

        final long identity = Binder.clearCallingIdentity();
        try {
            return DomainSelectionResolver.getInstance().isDomainSelectionSupported();
        }  finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Request to enable or disable the satellite modem and demo mode. If the satellite modem is
     * enabled, this may also disable the cellular modem, and if the satellite modem is disabled,
     * this may also re-enable the cellular modem.
     *
     * @param subId The subId of the subscription to set satellite enabled for.
     * @param enableSatellite {@code true} to enable the satellite modem and
     *                        {@code false} to disable.
     * @param enableDemoMode {@code true} to enable demo mode and {@code false} to disable.
     * @param isEmergency {@code true} to enable emergency mode, {@code false} otherwise.
     * @param callback The callback to get the result of the request.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestSatelliteEnabled(int subId, boolean enableSatellite, boolean enableDemoMode,
            boolean isEmergency, @NonNull IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("requestSatelliteEnabled");
        if (enableSatellite) {
            ResultReceiver resultReceiver = new ResultReceiver(mMainThreadHandler) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    Log.d(LOG_TAG, "Satellite access restriction resultCode=" + resultCode
                            + ", resultData=" + resultData);
                    boolean isAllowed = false;
                    Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(
                            callback::accept);
                    if (resultCode == SATELLITE_RESULT_SUCCESS) {
                        if (resultData != null
                                && resultData.containsKey(KEY_SATELLITE_COMMUNICATION_ALLOWED)) {
                            isAllowed = resultData.getBoolean(KEY_SATELLITE_COMMUNICATION_ALLOWED);
                        } else {
                            loge("KEY_SATELLITE_COMMUNICATION_ALLOWED does not exist.");
                        }
                    } else {
                        result.accept(resultCode);
                        return;
                    }
                    if (isAllowed) {
                        mSatelliteController.requestSatelliteEnabled(
                                subId, enableSatellite, enableDemoMode, callback);
                    } else {
                        result.accept(SATELLITE_RESULT_ACCESS_BARRED);
                    }
                }
            };
            mSatelliteAccessController.requestIsCommunicationAllowedForCurrentLocation(
                    subId, resultReceiver);
        } else {
            // No need to check if satellite is allowed at current location when disabling satellite
            mSatelliteController.requestSatelliteEnabled(
                    subId, enableSatellite, enableDemoMode, callback);
        }
    }

    /**
     * Request to get whether the satellite modem is enabled.
     *
     * @param subId The subId of the subscription to check whether satellite is enabled for.
     * @param result The result receiver that returns whether the satellite modem is enabled
     *               if the request is successful or an error code if the request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestIsSatelliteEnabled(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestIsSatelliteEnabled");
        mSatelliteController.requestIsSatelliteEnabled(subId, result);
    }

    /**
     * Request to get whether the satellite service demo mode is enabled.
     *
     * @param subId The subId of the subscription to check whether the satellite demo mode
     *              is enabled for.
     * @param result The result receiver that returns whether the satellite demo mode is enabled
     *               if the request is successful or an error code if the request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestIsDemoModeEnabled(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestIsDemoModeEnabled");
        mSatelliteController.requestIsDemoModeEnabled(subId, result);
    }

    /**
     * Request to get whether the satellite service is enabled with emergency mode.
     *
     * @param subId The subId of the subscription to check whether the satellite demo mode
     *              is enabled for.
     * @param result The result receiver that returns whether the satellite emergency mode is
     *               enabled if the request is successful or an error code if the request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestIsEmergencyModeEnabled(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestIsEmergencyModeEnabled");
        result.send(SATELLITE_RESULT_REQUEST_NOT_SUPPORTED, null);
    }

    /**
     * Request to get whether the satellite service is supported on the device.
     *
     * @param subId The subId of the subscription to check satellite service support for.
     * @param result The result receiver that returns whether the satellite service is supported on
     *               the device if the request is successful or an error code if the request failed.
     */
    @Override
    public void requestIsSatelliteSupported(int subId, @NonNull ResultReceiver result) {
        mSatelliteController.requestIsSatelliteSupported(subId, result);
    }

    /**
     * Request to get the {@link SatelliteCapabilities} of the satellite service.
     *
     * @param subId The subId of the subscription to get the satellite capabilities for.
     * @param result The result receiver that returns the {@link SatelliteCapabilities}
     *               if the request is successful or an error code if the request failed.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    @Override
    public void requestSatelliteCapabilities(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestSatelliteCapabilities");
        mSatelliteController.requestSatelliteCapabilities(subId, result);
    }

    /**
     * Start receiving satellite transmission updates.
     * This can be called by the pointing UI when the user starts pointing to the satellite.
     * Modem should continue to report the pointing input as the device or satellite moves.
     *
     * @param subId The subId of the subscription to start satellite transmission updates for.
     * @param resultCallback The callback to get the result of the request.
     * @param callback The callback to notify of satellite transmission updates.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void startSatelliteTransmissionUpdates(int subId,
            @NonNull IIntegerConsumer resultCallback,
            @NonNull ISatelliteTransmissionUpdateCallback callback) {
        enforceSatelliteCommunicationPermission("startSatelliteTransmissionUpdates");
        mSatelliteController.startSatelliteTransmissionUpdates(subId, resultCallback, callback);
    }

    /**
     * Stop receiving satellite transmission updates.
     * This can be called by the pointing UI when the user stops pointing to the satellite.
     *
     * @param subId The subId of the subscription to stop satellite transmission updates for.
     * @param resultCallback The callback to get the result of the request.
     * @param callback The callback that was passed to {@link #startSatelliteTransmissionUpdates(
     *                 int, IIntegerConsumer, ISatelliteTransmissionUpdateCallback)}.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void stopSatelliteTransmissionUpdates(int subId,
            @NonNull IIntegerConsumer resultCallback,
            @NonNull ISatelliteTransmissionUpdateCallback callback) {
        enforceSatelliteCommunicationPermission("stopSatelliteTransmissionUpdates");
        mSatelliteController.stopSatelliteTransmissionUpdates(subId, resultCallback, callback);
    }

    /**
     * Register the subscription with a satellite provider.
     * This is needed to register the subscription if the provider allows dynamic registration.
     *
     * @param subId The subId of the subscription to be provisioned.
     * @param token The token to be used as a unique identifier for provisioning with satellite
     *              gateway.
     * @param provisionData Data from the provisioning app that can be used by provisioning server
     * @param callback The callback to get the result of the request.
     *
     * @return The signal transport used by the caller to cancel the provision request,
     *         or {@code null} if the request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    @Nullable public ICancellationSignal provisionSatelliteService(int subId,
            @NonNull String token, @NonNull byte[] provisionData,
            @NonNull IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("provisionSatelliteService");
        return mSatelliteController.provisionSatelliteService(subId, token, provisionData,
                callback);
    }

    /**
     * Unregister the device/subscription with the satellite provider.
     * This is needed if the provider allows dynamic registration. Once deprovisioned,
     * {@link SatelliteProvisionStateCallback#onSatelliteProvisionStateChanged(boolean)}
     * should report as deprovisioned.
     *
     * @param subId The subId of the subscription to be deprovisioned.
     * @param token The token of the device/subscription to be deprovisioned.
     * @param callback The callback to get the result of the request.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void deprovisionSatelliteService(int subId,
            @NonNull String token, @NonNull IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("deprovisionSatelliteService");
        mSatelliteController.deprovisionSatelliteService(subId, token, callback);
    }

    /**
     * Registers for the satellite provision state changed.
     *
     * @param subId The subId of the subscription to register for provision state changed.
     * @param callback The callback to handle the satellite provision state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    @SatelliteManager.SatelliteResult public int registerForSatelliteProvisionStateChanged(
            int subId, @NonNull ISatelliteProvisionStateCallback callback) {
        enforceSatelliteCommunicationPermission("registerForSatelliteProvisionStateChanged");
        return mSatelliteController.registerForSatelliteProvisionStateChanged(subId, callback);
    }

    /**
     * Unregisters for the satellite provision state changed.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for provision state changed.
     * @param callback The callback that was passed to
     * {@link #registerForSatelliteProvisionStateChanged(int, ISatelliteProvisionStateCallback)}.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void unregisterForSatelliteProvisionStateChanged(
            int subId, @NonNull ISatelliteProvisionStateCallback callback) {
        enforceSatelliteCommunicationPermission("unregisterForSatelliteProvisionStateChanged");
        mSatelliteController.unregisterForSatelliteProvisionStateChanged(subId, callback);
    }

    /**
     * Request to get whether the device is provisioned with a satellite provider.
     *
     * @param subId The subId of the subscription to get whether the device is provisioned for.
     * @param result The result receiver that returns whether the device is provisioned with a
     *               satellite provider if the request is successful or an error code if the
     *               request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestIsSatelliteProvisioned(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestIsSatelliteProvisioned");
        mSatelliteController.requestIsSatelliteProvisioned(subId, result);
    }

    /**
     * Registers for modem state changed from satellite modem.
     *
     * @param subId The subId of the subscription to register for satellite modem state changed.
     * @param callback The callback to handle the satellite modem state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    @SatelliteManager.SatelliteResult public int registerForSatelliteModemStateChanged(int subId,
            @NonNull ISatelliteModemStateCallback callback) {
        enforceSatelliteCommunicationPermission("registerForSatelliteModemStateChanged");
        return mSatelliteController.registerForSatelliteModemStateChanged(subId, callback);
    }

    /**
     * Unregisters for modem state changed from satellite modem.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for satellite modem state changed.
     * @param callback The callback that was passed to
     * {@link #registerForModemStateChanged(int, ISatelliteModemStateCallback)}.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void unregisterForModemStateChanged(int subId,
            @NonNull ISatelliteModemStateCallback callback) {
        enforceSatelliteCommunicationPermission("unregisterForModemStateChanged");
        mSatelliteController.unregisterForModemStateChanged(subId, callback);
    }

    /**
     * Register to receive incoming datagrams over satellite.
     *
     * @param subId The subId of the subscription to register for incoming satellite datagrams.
     * @param callback The callback to handle incoming datagrams over satellite.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    @SatelliteManager.SatelliteResult public int registerForIncomingDatagram(int subId,
            @NonNull ISatelliteDatagramCallback callback) {
        enforceSatelliteCommunicationPermission("registerForIncomingDatagram");
        return mSatelliteController.registerForIncomingDatagram(subId, callback);
    }

    /**
     * Unregister to stop receiving incoming datagrams over satellite.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for incoming satellite datagrams.
     * @param callback The callback that was passed to
     *                 {@link #registerForIncomingDatagram(int, ISatelliteDatagramCallback)}.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void unregisterForIncomingDatagram(int subId,
            @NonNull ISatelliteDatagramCallback callback) {
        enforceSatelliteCommunicationPermission("unregisterForIncomingDatagram");
        mSatelliteController.unregisterForIncomingDatagram(subId, callback);
    }

    /**
     * Poll pending satellite datagrams over satellite.
     *
     * This method requests modem to check if there are any pending datagrams to be received over
     * satellite. If there are any incoming datagrams, they will be received via
     * {@link SatelliteDatagramCallback#onSatelliteDatagramReceived(long, SatelliteDatagram, int, Consumer)})}
     *
     * @param subId The subId of the subscription used for receiving datagrams.
     * @param callback The callback to get {@link SatelliteManager.SatelliteResult} of the request.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    public void pollPendingDatagrams(int subId, IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("pollPendingDatagrams");
        mSatelliteController.pollPendingDatagrams(subId, callback);
    }

    /**
     * Send datagram over satellite.
     *
     * Gateway encodes SOS message or location sharing message into a datagram and passes it as
     * input to this method. Datagram received here will be passed down to modem without any
     * encoding or encryption.
     *
     * @param subId The subId of the subscription to send satellite datagrams for.
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param datagram encoded gateway datagram which is encrypted by the caller.
     *                 Datagram will be passed down to modem without any encoding or encryption.
     * @param needFullScreenPointingUI this is used to indicate pointingUI app to open in
     *                                 full screen mode.
     * @param callback The callback to get {@link SatelliteManager.SatelliteResult} of the request.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    @Override
    public void sendDatagram(int subId, @SatelliteManager.DatagramType int datagramType,
            @NonNull SatelliteDatagram datagram, boolean needFullScreenPointingUI,
            @NonNull IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("sendDatagram");
        mSatelliteController.sendDatagram(subId, datagramType, datagram, needFullScreenPointingUI,
                callback);
    }

    /**
     * Request to get whether satellite communication is allowed for the current location.
     *
     * @param subId The subId of the subscription to check whether satellite communication is
     *              allowed for the current location for.
     * @param result The result receiver that returns whether satellite communication is allowed
     *               for the current location if the request is successful or an error code
     *               if the request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestIsCommunicationAllowedForCurrentLocation(int subId,
            @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestIsCommunicationAllowedForCurrentLocation");
        mSatelliteAccessController.requestIsCommunicationAllowedForCurrentLocation(subId,
                result);
    }

    /**
     * Request to get the time after which the satellite will be visible.
     *
     * @param subId The subId to get the time after which the satellite will be visible for.
     * @param result The result receiver that returns the time after which the satellite will
     *               be visible if the request is successful or an error code if the request failed.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void requestTimeForNextSatelliteVisibility(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestTimeForNextSatelliteVisibility");
        mSatelliteController.requestTimeForNextSatelliteVisibility(subId, result);
    }

    /**
     * Inform that Device is aligned to satellite for demo mode.
     *
     * @param subId The subId to get the time after which the satellite will be visible for.
     * @param isAligned {@code true} Device is aligned with the satellite for demo mode
     *                  {@code false} Device fails to align with the satellite for demo mode.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)

    public void setDeviceAlignedWithSatellite(int subId, @NonNull boolean isAligned) {
        enforceSatelliteCommunicationPermission("informDeviceAlignedToSatellite");
        mSatelliteController.setDeviceAlignedWithSatellite(subId, isAligned);
    }

    /**
     * Add a restriction reason for disallowing carrier supported satellite plmn scan and attach
     * by modem.
     *
     * @param subId The subId of the subscription to request for.
     * @param reason Reason for disallowing satellite communication for carrier.
     * @param callback Listener for the {@link SatelliteManager.SatelliteError} result of the
     * operation.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    public void addAttachRestrictionForCarrier(int subId,
            @SatelliteManager.SatelliteCommunicationRestrictionReason int reason,
            @NonNull IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("addAttachRestrictionForCarrier");
        final long identity = Binder.clearCallingIdentity();
        try {
            mSatelliteController.addAttachRestrictionForCarrier(subId, reason, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Remove a restriction reason for disallowing carrier supported satellite plmn scan and attach
     * by modem.
     *
     * @param subId The subId of the subscription to request for.
     * @param reason Reason for disallowing satellite communication.
     * @param callback Listener for the {@link SatelliteManager.SatelliteError} result of the
     * operation.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    public void removeAttachRestrictionForCarrier(int subId,
            @SatelliteManager.SatelliteCommunicationRestrictionReason int reason,
            @NonNull IIntegerConsumer callback) {
        enforceSatelliteCommunicationPermission("removeAttachRestrictionForCarrier");
        final long identity = Binder.clearCallingIdentity();
        try {
            mSatelliteController.removeAttachRestrictionForCarrier(subId, reason, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get reasons for disallowing satellite communication, as requested by
     * {@link #addSatelliteAttachRestrictionForCarrier(int, int, IIntegerConsumer)}.
     *
     * @param subId The subId of the subscription to request for.
     *
     * @return Integer array of reasons for disallowing satellite communication.
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    public @NonNull int[] getAttachRestrictionReasonsForCarrier(
            int subId) {
        enforceSatelliteCommunicationPermission("getAttachRestrictionReasonsForCarrier");
        final long identity = Binder.clearCallingIdentity();
        try {
            Set<Integer> reasonSet =
                    mSatelliteController.getAttachRestrictionReasonsForCarrier(subId);
            return reasonSet.stream().mapToInt(i->i).toArray();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Request to get the signal strength of the satellite connection.
     *
     * @param subId The subId of the subscription to request for.
     * @param result Result receiver to get the error code of the request and the current signal
     * strength of the satellite connection.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    @Override
    public void requestNtnSignalStrength(int subId, @NonNull ResultReceiver result) {
        enforceSatelliteCommunicationPermission("requestNtnSignalStrength");
        final long identity = Binder.clearCallingIdentity();
        try {
            mSatelliteController.requestNtnSignalStrength(subId, result);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Registers for NTN signal strength changed from satellite modem. If the registration operation
     * is not successful, a {@link ServiceSpecificException} that contains
     * {@link SatelliteManager.SatelliteResult} will be thrown.
     *
     * @param subId The subId of the subscription to request for.
     * @param callback The callback to handle the NTN signal strength changed event. If the
     * operation is successful, {@link NtnSignalStrengthCallback#onNtnSignalStrengthChanged(
     * NtnSignalStrength)} will return an instance of {@link NtnSignalStrength} with a value of
     * {@link NtnSignalStrength.NtnSignalStrengthLevel} when the signal strength of non-terrestrial
     * network has changed.
     *
     * @throws SecurityException If the caller doesn't have the required permission.
     * @throws ServiceSpecificException If the callback registration operation fails.
     */
    @Override
    public void registerForNtnSignalStrengthChanged(int subId,
            @NonNull INtnSignalStrengthCallback callback) throws RemoteException {
        enforceSatelliteCommunicationPermission("registerForNtnSignalStrengthChanged");
        final long identity = Binder.clearCallingIdentity();
        try {
            mSatelliteController.registerForNtnSignalStrengthChanged(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Unregisters for NTN signal strength changed from satellite modem.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for listening NTN signal strength
     * changed event.
     * @param callback The callback that was passed to
     * {@link #registerForNtnSignalStrengthChanged(int, INtnSignalStrengthCallback)}
     *
     * @throws SecurityException if the caller doesn't have the required permission.
     */
    @Override
    public void unregisterForNtnSignalStrengthChanged(
            int subId, @NonNull INtnSignalStrengthCallback callback) {
        enforceSatelliteCommunicationPermission("unregisterForNtnSignalStrengthChanged");
        final long identity = Binder.clearCallingIdentity();
        try {
            mSatelliteController.unregisterForNtnSignalStrengthChanged(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Registers for satellite capabilities change event from the satellite service.
     *
     * @param subId The subId of the subscription to request for.
     * @param callback The callback to handle the satellite capabilities changed event.
     *
     * @return The {@link SatelliteManager.SatelliteResult} result of the operation.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    @Override
    @SatelliteManager.SatelliteResult public int registerForCapabilitiesChanged(
            int subId, @NonNull ISatelliteCapabilitiesCallback callback) {
        enforceSatelliteCommunicationPermission("registerForCapabilitiesChanged");
        final long identity = Binder.clearCallingIdentity();
        try {
            return mSatelliteController.registerForCapabilitiesChanged(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Unregisters for satellite capabilities change event from the satellite service.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for satellite capabilities change.
     * @param callback The callback that was passed to.
     * {@link #registerForCapabilitiesChanged(int, ISatelliteCapabilitiesCallback)}.
     *
     * @throws SecurityException if the caller doesn't have required permission.
     */
    @Override
    public void unregisterForCapabilitiesChanged(int subId,
            @NonNull ISatelliteCapabilitiesCallback callback) {
        enforceSatelliteCommunicationPermission("unregisterForCapabilitiesChanged");
        final long identity = Binder.clearCallingIdentity();
        try {
            mSatelliteController.unregisterForCapabilitiesChanged(subId, callback);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * This API can be used by only CTS to update satellite vendor service package name.
     *
     * @param servicePackageName The package name of the satellite vendor service.
     * @return {@code true} if the satellite vendor service is set successfully,
     * {@code false} otherwise.
     */
    public boolean setSatelliteServicePackageName(String servicePackageName) {
        Log.d(LOG_TAG, "setSatelliteServicePackageName - " + servicePackageName);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setSatelliteServicePackageName");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setSatelliteServicePackageName");
        return mSatelliteController.setSatelliteServicePackageName(servicePackageName);
    }

    /**
     * This API can be used by only CTS to update satellite gateway service package name.
     *
     * @param servicePackageName The package name of the satellite gateway service.
     * @return {@code true} if the satellite gateway service is set successfully,
     * {@code false} otherwise.
     */
    public boolean setSatelliteGatewayServicePackageName(@Nullable String servicePackageName) {
        Log.d(LOG_TAG, "setSatelliteGatewayServicePackageName - " + servicePackageName);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setSatelliteGatewayServicePackageName");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setSatelliteGatewayServicePackageName");
        return mSatelliteController.setSatelliteGatewayServicePackageName(servicePackageName);
    }

    /**
     * This API can be used by only CTS to update satellite pointing UI app package and class names.
     *
     * @param packageName The package name of the satellite pointing UI app.
     * @param className The class name of the satellite pointing UI app.
     * @return {@code true} if the satellite pointing UI app package and class is set successfully,
     * {@code false} otherwise.
     */
    public boolean setSatellitePointingUiClassName(
            @Nullable String packageName, @Nullable String className) {
        Log.d(LOG_TAG, "setSatellitePointingUiClassName: packageName=" + packageName
                + ", className=" + className);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setSatellitePointingUiClassName");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setSatelliteGatewayServicePackageName");
        return mSatelliteController.setSatellitePointingUiClassName(packageName, className);
    }

    /**
     * This API can be used by only CTS to update the timeout duration in milliseconds that
     * satellite should stay at listening mode to wait for the next incoming page before disabling
     * listening mode.
     *
     * @param timeoutMillis The timeout duration in millisecond.
     * @return {@code true} if the timeout duration is set successfully, {@code false} otherwise.
     */
    public boolean setSatelliteListeningTimeoutDuration(long timeoutMillis) {
        Log.d(LOG_TAG, "setSatelliteListeningTimeoutDuration - " + timeoutMillis);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setSatelliteListeningTimeoutDuration");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setSatelliteListeningTimeoutDuration");
        return mSatelliteController.setSatelliteListeningTimeoutDuration(timeoutMillis);
    }

    /**
     * This API can be used by only CTS to override the timeout durations used by the
     * DatagramController module.
     *
     * @param timeoutMillis The timeout duration in millisecond.
     * @return {@code true} if the timeout duration is set successfully, {@code false} otherwise.
     */
    public boolean setDatagramControllerTimeoutDuration(
            boolean reset, int timeoutType, long timeoutMillis) {
        Log.d(LOG_TAG, "setDatagramControllerTimeoutDuration - " + timeoutMillis + ", reset="
                + reset + ", timeoutMillis=" + timeoutMillis);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setDatagramControllerTimeoutDuration");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setDatagramControllerTimeoutDuration");
        return mSatelliteController.setDatagramControllerTimeoutDuration(
                reset, timeoutType, timeoutMillis);
    }

    /**
     * This API can be used by only CTS to override the timeout durations used by the
     * SatelliteController module.
     *
     * @param timeoutMillis The timeout duration in millisecond.
     * @return {@code true} if the timeout duration is set successfully, {@code false} otherwise.
     */
    public boolean setSatelliteControllerTimeoutDuration(
            boolean reset, int timeoutType, long timeoutMillis) {
        Log.d(LOG_TAG, "setSatelliteControllerTimeoutDuration - " + timeoutMillis + ", reset="
                + reset + ", timeoutMillis=" + timeoutMillis);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setSatelliteControllerTimeoutDuration");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setSatelliteControllerTimeoutDuration");
        return mSatelliteController.setSatelliteControllerTimeoutDuration(
                reset, timeoutType, timeoutMillis);
    }

    /**
     * This API can be used in only testing to override connectivity status in monitoring emergency
     * calls and sending EVENT_DISPLAY_EMERGENCY_MESSAGE to Dialer.
     *
     * @param handoverType The type of handover from emergency call to satellite messaging. Use one
     *                     of the following values to enable the override:
     *                     0 - EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS
     *                     1 - EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911
     *                     To disable the override, use -1 for handoverType.
     * @param delaySeconds The event EVENT_DISPLAY_EMERGENCY_MESSAGE will be sent to Dialer
     *                     delaySeconds after the emergency call starts.
     * @return {@code true} if the handover type is set successfully, {@code false} otherwise.
     */
    public boolean setEmergencyCallToSatelliteHandoverType(int handoverType, int delaySeconds) {
        Log.d(LOG_TAG, "setEmergencyCallToSatelliteHandoverType - " + handoverType);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setEmergencyCallToSatelliteHandoverType");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setEmergencyCallToSatelliteHandoverType");
        return mSatelliteController.setEmergencyCallToSatelliteHandoverType(
                handoverType, delaySeconds);
    }

    /**
     * This API can be used in only testing to override oem-enabled satellite provision status.
     *
     * @param reset {@code true} mean the overriding status should not be used, {@code false}
     *              otherwise.
     * @param isProvisioned The overriding provision status.
     * @return {@code true} if the provision status is set successfully, {@code false} otherwise.
     */
    public boolean setOemEnabledSatelliteProvisionStatus(boolean reset, boolean isProvisioned) {
        Log.d(LOG_TAG, "setOemEnabledSatelliteProvisionStatus - reset=" + reset
                + ", isProvisioned=" + isProvisioned);
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setOemEnabledSatelliteProvisionStatus");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setOemEnabledSatelliteProvisionStatus");
        return mSatelliteController.setOemEnabledSatelliteProvisionStatus(reset, isProvisioned);
    }

    /**
     * This API should be used by only CTS tests to forcefully set telephony country codes.
     *
     * @return {@code true} if the country code is set successfully, {@code false} otherwise.
     */
    public boolean setCountryCodes(boolean reset, List<String> currentNetworkCountryCodes,
            Map cachedNetworkCountryCodes, String locationCountryCode,
            long locationCountryCodeTimestampNanos) {
        Log.d(LOG_TAG, "setCountryCodes: currentNetworkCountryCodes="
                + String.join(", ", currentNetworkCountryCodes)
                + ", locationCountryCode=" + locationCountryCode
                + ", locationCountryCodeTimestampNanos" + locationCountryCodeTimestampNanos
                + ", reset=" + reset + ", cachedNetworkCountryCodes="
                + String.join(", ", cachedNetworkCountryCodes.keySet()));
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setCachedLocationCountryCode");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setCachedLocationCountryCode");
        return TelephonyCountryDetector.getInstance(getDefaultPhone().getContext()).setCountryCodes(
                reset, currentNetworkCountryCodes, cachedNetworkCountryCodes, locationCountryCode,
                locationCountryCodeTimestampNanos);
    }

    /**
     * This API should be used by only CTS tests to override the overlay configs of satellite
     * access controller.
     *
     * @param reset {@code true} mean the overridden configs should not be used, {@code false}
     *              otherwise.
     * @return {@code true} if the overlay configs are set successfully, {@code false} otherwise.
     */
    public boolean setSatelliteAccessControlOverlayConfigs(boolean reset, boolean isAllowed,
            String s2CellFile, long locationFreshDurationNanos,
            List<String> satelliteCountryCodes) {
        Log.d(LOG_TAG, "setSatelliteAccessControlOverlayConfigs: reset=" + reset
                + ", isAllowed" + isAllowed + ", s2CellFile=" + s2CellFile
                + ", locationFreshDurationNanos=" + locationFreshDurationNanos
                + ", satelliteCountryCodes=" + ((satelliteCountryCodes != null)
                ? String.join(", ", satelliteCountryCodes) : null));
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setSatelliteAccessControlOverlayConfigs");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setSatelliteAccessControlOverlayConfigs");
        return mSatelliteAccessController.setSatelliteAccessControlOverlayConfigs(reset, isAllowed,
                s2CellFile, locationFreshDurationNanos, satelliteCountryCodes);
    }

    /**
     * This API can be used by only CTS to override the cached value for the device overlay config
     * value : config_send_satellite_datagram_to_modem_in_demo_mode, which determines whether
     * outgoing satellite datagrams should be sent to modem in demo mode.
     *
     * @param shouldSendToModemInDemoMode Whether send datagram in demo mode should be sent to
     * satellite modem or not.
     *
     * @return {@code true} if the operation is successful, {@code false} otherwise.
     */
    public boolean setShouldSendDatagramToModemInDemoMode(boolean shouldSendToModemInDemoMode) {
        if (!mFeatureFlags.oemEnabledSatelliteFlag()) {
            Log.d(LOG_TAG, "shouldSendDatagramToModemInDemoMode: oemEnabledSatelliteFlag is "
                    + "disabled");
            return false;
        }
        Log.d(LOG_TAG, "setShouldSendDatagramToModemInDemoMode");
        TelephonyPermissions.enforceShellOnly(
                Binder.getCallingUid(), "setShouldSendDatagramToModemInDemoMode");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                "setShouldSendDatagramToModemInDemoMode");
        return mSatelliteController.setShouldSendDatagramToModemInDemoMode(
                shouldSendToModemInDemoMode);
    }

    /**
     * Sets the service defined in ComponentName to be bound.
     *
     * This should only be used for testing.
     * @return {@code true} if the DomainSelectionService to bind to was set,
     *         {@code false} otherwise.
     */
    @Override
    public boolean setDomainSelectionServiceOverride(ComponentName componentName) {
        Log.i(LOG_TAG, "setDomainSelectionServiceOverride component=" + componentName);

        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "setDomainSelectionServiceOverride");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                getDefaultSubscription(), "setDomainSelectionServiceOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DomainSelectionResolver.getInstance().isDomainSelectionSupported()) {
                return DomainSelectionResolver.getInstance()
                        .setDomainSelectionServiceOverride(componentName);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return false;
    }

    /**
     * Clears the DomainSelectionService override.
     *
     * This should only be used for testing.
     * @return {@code true} if the DomainSelectionService override was cleared,
     *         {@code false} otherwise.
     */
    @Override
    public boolean clearDomainSelectionServiceOverride() {
        Log.i(LOG_TAG, "clearDomainSelectionServiceOverride");

        TelephonyPermissions.enforceShellOnly(Binder.getCallingUid(),
                "clearDomainSelectionServiceOverride");
        TelephonyPermissions.enforceCallingOrSelfModifyPermissionOrCarrierPrivilege(mApp,
                getDefaultSubscription(), "clearDomainSelectionServiceOverride");

        final long identity = Binder.clearCallingIdentity();
        try {
            if (DomainSelectionResolver.getInstance().isDomainSelectionSupported()) {
                return DomainSelectionResolver.getInstance()
                        .clearDomainSelectionServiceOverride();
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return false;
    }

    /**
     * Enable or disable notifications sent for cellular identifier disclosure events.
     *
     * Disclosure events are defined as instances where a device has sent a cellular identifier
     * on the Non-access stratum (NAS) before a security context is established. As a result the
     * identifier is sent in the clear, which has privacy implications for the user.
     *
     * @param enable if notifications about disclosure events should be enabled
     * @throws SecurityException             if the caller does not have the required privileges
     * @throws UnsupportedOperationException if the modem does not support this feature.
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setEnableCellularIdentifierDisclosureNotifications(boolean enable) {
        enforceModifyPermission();
        checkForIdentifierDisclosureNotificationSupport();

        SharedPreferences.Editor editor = mTelephonySharedPreferences.edit();
        editor.putBoolean(Phone.PREF_IDENTIFIER_DISCLOSURE_NOTIFICATIONS_ENABLED, enable);
        editor.apply();

        // Each phone instance is responsible for updating its respective modem immediately
        // after we've made a preference change.
        for (Phone phone : PhoneFactory.getPhones()) {
            phone.handleIdentifierDisclosureNotificationPreferenceChange();
        }
    }

    /**
     * Get whether or not cellular identifier disclosure notifications are enabled.
     *
     * @throws SecurityException             if the caller does not have the required privileges
     * @throws UnsupportedOperationException if the modem does not support this feature.
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isCellularIdentifierDisclosureNotificationsEnabled() {
        enforceReadPrivilegedPermission("isCellularIdentifierDisclosureNotificationEnabled");
        checkForIdentifierDisclosureNotificationSupport();
        return getDefaultPhone().getIdentifierDisclosureNotificationsPreferenceEnabled();
    }

    /**
     * Enables or disables notifications sent when cellular null cipher or integrity algorithms
     * are in use by the cellular modem.
     *
     * @throws IllegalStateException if the Telephony process is not currently available
     * @throws SecurityException if the caller does not have the required privileges
     * @throws UnsupportedOperationException if the modem does not support reporting on ciphering
     * and integrity algorithms in use
     * @hide
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setNullCipherNotificationsEnabled(boolean enable) {
        enforceModifyPermission();
        checkForNullCipherNotificationSupport();

        SharedPreferences.Editor editor = mTelephonySharedPreferences.edit();
        editor.putBoolean(Phone.PREF_NULL_CIPHER_NOTIFICATIONS_ENABLED, enable);
        editor.apply();

        // Each phone instance is responsible for updating its respective modem immediately
        // after a preference change.
        for (Phone phone : PhoneFactory.getPhones()) {
            phone.handleNullCipherNotificationPreferenceChanged();
        }
    }

    /**
     * Get whether notifications are enabled for null cipher or integrity algorithms in use by the
     * cellular modem.
     *
     * @throws IllegalStateException if the Telephony process is not currently available
     * @throws SecurityException if the caller does not have the required privileges
     * @throws UnsupportedOperationException if the modem does not support reporting on ciphering
     * and integrity algorithms in use
     * @hide
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isNullCipherNotificationsEnabled() {
        enforceReadPrivilegedPermission("isNullCipherNotificationsEnabled");
        checkForNullCipherNotificationSupport();
        return getDefaultPhone().getNullCipherNotificationsPreferenceEnabled();
    }

    /**
     * Check whether the caller (or self, if not processing an IPC) can read device identifiers.
     *
     * <p>This method behaves in one of the following ways:
     * <ul>
     *     <li>return true : if the calling package has the appop permission {@link
     *     Manifest.permission#USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER} in the manifest </>
     *     <li>return true : if any one subscription has the READ_PRIVILEGED_PHONE_STATE
     *     permission, the calling package passes a DevicePolicyManager Device Owner / Profile
     *     Owner device identifier access check, or the calling package has carrier privileges</>
     *     <li>throw SecurityException: if the caller does not meet any of the requirements.
     * </ul>
     */
    private static boolean checkCallingOrSelfReadDeviceIdentifiersForAnySub(Context context,
            String callingPackage, @Nullable String callingFeatureId, String message) {
        for (Phone phone : PhoneFactory.getPhones()) {
            if (TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(context,
                    phone.getSubId(), callingPackage, callingFeatureId, message)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The subscription manager service instance.
     */
    public SubscriptionManagerService getSubscriptionManagerService() {
        return SubscriptionManagerService.getInstance();
    }

    /**
     * Class binds the consumer[callback] and carrierId.
     */
    private static class CallerCallbackInfo {
        private final Consumer<Integer> mConsumer;
        private final int mCarrierId;

        public CallerCallbackInfo(Consumer<Integer> consumer, int carrierId) {
            mConsumer = consumer;
            mCarrierId = carrierId;
        }

        public Consumer<Integer> getConsumer() {
            return mConsumer;
        }

        public int getCarrierId() {
            return mCarrierId;
        }
    }

    /*
    * PhoneInterfaceManager is a singleton. Unit test calls the init() with context.
    * But the context that is passed in is unused if the phone app is already alive.
    * In this case PackageManager object is different in PhoneInterfaceManager and Unit test.
    */
    @VisibleForTesting
    public void setPackageManager(PackageManager packageManager) {
        mPackageManager = packageManager;
    }

    /*
     * PhoneInterfaceManager is a singleton. Unit test calls the init() with FeatureFlags.
     * But the FeatureFlags that is passed in is unused if the phone app is already alive.
     * In this case FeatureFlags object is different in PhoneInterfaceManager and Unit test.
     */
    @VisibleForTesting
    public void setFeatureFlags(FeatureFlags featureFlags) {
        mFeatureFlags = featureFlags;
    }

    /**
     * Make sure the device has required telephony feature
     *
     * @throws UnsupportedOperationException if the device does not have required telephony feature
     */
    private void enforceTelephonyFeatureWithException(@Nullable String callingPackage,
            @NonNull String telephonyFeature, @NonNull String methodName) {
        if (callingPackage == null || mPackageManager == null) {
            return;
        }

        if (!mFeatureFlags.enforceTelephonyFeatureMappingForPublicApis()
                || !CompatChanges.isChangeEnabled(ENABLE_FEATURE_MAPPING, callingPackage,
                Binder.getCallingUserHandle())) {
            return;
        }

        if (!mPackageManager.hasSystemFeature(telephonyFeature)) {
            throw new UnsupportedOperationException(
                    methodName + " is unsupported without " + telephonyFeature);
        }
    }
}
