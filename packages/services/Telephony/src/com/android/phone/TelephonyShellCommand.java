/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.internal.telephony.d2d.Communicator.MESSAGE_CALL_AUDIO_CODEC;
import static com.android.internal.telephony.d2d.Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE;
import static com.android.internal.telephony.d2d.Communicator.MESSAGE_DEVICE_BATTERY_STATE;
import static com.android.internal.telephony.d2d.Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE;

import static java.util.Map.entry;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.provider.BlockedNumberContract;
import android.telephony.BarringInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.feature.ImsFeature;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.rcs.uce.util.FeatureTags;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.d2d.Communicator;
import com.android.internal.telephony.emergency.EmergencyNumberTracker;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.phone.callcomposer.CallComposerPictureManager;
import com.android.phone.euicc.EuiccUiDispatcherActivity;
import com.android.phone.utils.CarrierAllowListInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Takes actions based on the adb commands given by "adb shell cmd phone ...". Be careful, no
 * permission checks have been done before onCommand was called. Make sure any commands processed
 * here also contain the appropriate permissions checks.
 */

public class TelephonyShellCommand extends BasicShellCommandHandler {

    private static final String LOG_TAG = "TelephonyShellCommand";
    // Don't commit with this true.
    private static final boolean VDBG = true;
    private static final int DEFAULT_PHONE_ID = 0;

    private static final String CALL_COMPOSER_SUBCOMMAND = "callcomposer";
    private static final String IMS_SUBCOMMAND = "ims";
    private static final String NUMBER_VERIFICATION_SUBCOMMAND = "numverify";
    private static final String EMERGENCY_CALLBACK_MODE = "emergency-callback-mode";
    private static final String EMERGENCY_NUMBER_TEST_MODE = "emergency-number-test-mode";
    private static final String END_BLOCK_SUPPRESSION = "end-block-suppression";
    private static final String RESTART_MODEM = "restart-modem";
    private static final String UNATTENDED_REBOOT = "unattended-reboot";
    private static final String CARRIER_CONFIG_SUBCOMMAND = "cc";
    private static final String DATA_TEST_MODE = "data";
    private static final String ENABLE = "enable";
    private static final String DISABLE = "disable";
    private static final String QUERY = "query";
    private static final String CARRIER_RESTRICTION_STATUS_TEST = "carrier_restriction_status_test";
    private static final String SET_CARRIER_SERVICE_PACKAGE_OVERRIDE =
            "set-carrier-service-package-override";
    private static final String CLEAR_CARRIER_SERVICE_PACKAGE_OVERRIDE =
            "clear-carrier-service-package-override";
    private final String QUOTES = "\"";

    private static final String CALL_COMPOSER_TEST_MODE = "test-mode";
    private static final String CALL_COMPOSER_SIMULATE_CALL = "simulate-outgoing-call";
    private static final String CALL_COMPOSER_USER_SETTING = "user-setting";

    private static final String IMS_SET_IMS_SERVICE = "set-ims-service";
    private static final String IMS_GET_IMS_SERVICE = "get-ims-service";
    private static final String IMS_CLEAR_SERVICE_OVERRIDE = "clear-ims-service-override";
    // Used to disable or enable processing of conference event package data from the network.
    // This is handy for testing scenarios where CEP data does not exist on a network which does
    // support CEP data.
    private static final String IMS_CEP = "conference-event-package";

    private static final String NUMBER_VERIFICATION_OVERRIDE_PACKAGE = "override-package";
    private static final String NUMBER_VERIFICATION_FAKE_CALL = "fake-call";

    private static final String CC_GET_VALUE = "get-value";
    private static final String CC_SET_VALUE = "set-value";
    private static final String CC_SET_VALUES_FROM_XML = "set-values-from-xml";
    private static final String CC_CLEAR_VALUES = "clear-values";

    private static final String EUICC_SUBCOMMAND = "euicc";
    private static final String EUICC_SET_UI_COMPONENT = "set-euicc-uicomponent";

    private static final String GBA_SUBCOMMAND = "gba";
    private static final String GBA_SET_SERVICE = "set-service";
    private static final String GBA_GET_SERVICE = "get-service";
    private static final String GBA_SET_RELEASE_TIME = "set-release";
    private static final String GBA_GET_RELEASE_TIME = "get-release";

    private static final String SINGLE_REGISTATION_CONFIG = "src";
    private static final String SRC_SET_DEVICE_ENABLED = "set-device-enabled";
    private static final String SRC_GET_DEVICE_ENABLED = "get-device-enabled";
    private static final String SRC_SET_CARRIER_ENABLED = "set-carrier-enabled";
    private static final String SRC_GET_CARRIER_ENABLED = "get-carrier-enabled";
    private static final String SRC_SET_TEST_ENABLED = "set-test-enabled";
    private static final String SRC_GET_TEST_ENABLED = "get-test-enabled";
    private static final String SRC_SET_FEATURE_ENABLED = "set-feature-validation";
    private static final String SRC_GET_FEATURE_ENABLED = "get-feature-validation";

    private static final String D2D_SUBCOMMAND = "d2d";
    private static final String D2D_SEND = "send";
    private static final String D2D_TRANSPORT = "transport";
    private static final String D2D_SET_DEVICE_SUPPORT = "set-device-support";

    private static final String BARRING_SUBCOMMAND = "barring";
    private static final String BARRING_SEND_INFO = "send";

    private static final String RCS_UCE_COMMAND = "uce";
    private static final String UCE_GET_EAB_CONTACT = "get-eab-contact";
    private static final String UCE_GET_EAB_CAPABILITY = "get-eab-capability";
    private static final String UCE_REMOVE_EAB_CONTACT = "remove-eab-contact";
    private static final String UCE_GET_DEVICE_ENABLED = "get-device-enabled";
    private static final String UCE_SET_DEVICE_ENABLED = "set-device-enabled";
    private static final String UCE_OVERRIDE_PUBLISH_CAPS = "override-published-caps";
    private static final String UCE_GET_LAST_PIDF_XML = "get-last-publish-pidf";
    private static final String UCE_REMOVE_REQUEST_DISALLOWED_STATUS =
            "remove-request-disallowed-status";
    private static final String UCE_SET_CAPABILITY_REQUEST_TIMEOUT =
            "set-capabilities-request-timeout";

    private static final String RADIO_SUBCOMMAND = "radio";
    private static final String RADIO_SET_MODEM_SERVICE = "set-modem-service";
    private static final String RADIO_GET_MODEM_SERVICE = "get-modem-service";

    // Check if a package has carrier privileges on any SIM, regardless of subId/phoneId.
    private static final String HAS_CARRIER_PRIVILEGES_COMMAND = "has-carrier-privileges";

    private static final String DISABLE_PHYSICAL_SUBSCRIPTION = "disable-physical-subscription";
    private static final String ENABLE_PHYSICAL_SUBSCRIPTION = "enable-physical-subscription";

    private static final String THERMAL_MITIGATION_COMMAND = "thermal-mitigation";
    private static final String ALLOW_THERMAL_MITIGATION_PACKAGE_SUBCOMMAND = "allow-package";
    private static final String DISALLOW_THERMAL_MITIGATION_PACKAGE_SUBCOMMAND = "disallow-package";
    private static final String SET_SATELLITE_SERVICE_PACKAGE_NAME =
            "set-satellite-service-package-name";
    private static final String SET_SATELLITE_GATEWAY_SERVICE_PACKAGE_NAME =
            "set-satellite-gateway-service-package-name";
    private static final String SET_SATELLITE_LISTENING_TIMEOUT_DURATION =
            "set-satellite-listening-timeout-duration";
    private static final String SET_SATELLITE_POINTING_UI_CLASS_NAME =
            "set-satellite-pointing-ui-class-name";
    private static final String SET_DATAGRAM_CONTROLLER_TIMEOUT_DURATION =
            "set-datagram-controller-timeout-duration";

    private static final String SET_SATELLITE_CONTROLLER_TIMEOUT_DURATION =
            "set-satellite-controller-timeout-duration";
    private static final String SET_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE =
            "set-emergency-call-to-satellite-handover-type";
    private static final String SET_COUNTRY_CODES = "set-country-codes";
    private static final String SET_SATELLITE_ACCESS_CONTROL_OVERLAY_CONFIGS =
            "set-satellite-access-control-overlay-configs";
    private static final String SET_OEM_ENABLED_SATELLITE_PROVISION_STATUS =
            "set-oem-enabled-satellite-provision-status";
    private static final String SET_SHOULD_SEND_DATAGRAM_TO_MODEM_IN_DEMO_MODE =
            "set-should-send-datagram-to-modem-in-demo-mode";

    private static final String DOMAIN_SELECTION_SUBCOMMAND = "domainselection";
    private static final String DOMAIN_SELECTION_SET_SERVICE_OVERRIDE = "set-dss-override";
    private static final String DOMAIN_SELECTION_CLEAR_SERVICE_OVERRIDE = "clear-dss-override";

    private static final String INVALID_ENTRY_ERROR = "An emergency number (only allow '0'-'9', "
            + "'*', '#' or '+') needs to be specified after -a in the command ";

    private static final int[] ROUTING_TYPES = {EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN,
            EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY,
            EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL};

    private static final String GET_ALLOWED_NETWORK_TYPES_FOR_USER =
            "get-allowed-network-types-for-users";
    private static final String SET_ALLOWED_NETWORK_TYPES_FOR_USER =
            "set-allowed-network-types-for-users";
    private static final String GET_IMEI = "get-imei";
    private static final String GET_SIM_SLOTS_MAPPING = "get-sim-slots-mapping";
    // Take advantage of existing methods that already contain permissions checks when possible.
    private final ITelephony mInterface;

    private SubscriptionManager mSubscriptionManager;
    private CarrierConfigManager mCarrierConfigManager;
    private TelephonyRegistryManager mTelephonyRegistryManager;
    private Context mContext;

    private enum CcType {
        BOOLEAN, DOUBLE, DOUBLE_ARRAY, INT, INT_ARRAY, LONG, LONG_ARRAY, STRING,
                STRING_ARRAY, PERSISTABLE_BUNDLE, UNKNOWN
    }

    private class CcOptionParseResult {
        public int mSubId;
        public boolean mPersistent;
    }

    // Maps carrier config keys to type. It is possible to infer the type for most carrier config
    // keys by looking at the end of the string which usually tells the type.
    // For instance: "xxxx_string", "xxxx_string_array", etc.
    // The carrier config keys in this map does not follow this convention. It is therefore not
    // possible to infer the type for these keys by looking at the string.
    private static final Map<String, CcType> CC_TYPE_MAP = Map.ofEntries(
            entry(CarrierConfigManager.Gps.KEY_A_GLONASS_POS_PROTOCOL_SELECT_STRING,
                    CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_GPS_LOCK_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_LPP_PROFILE_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_NFW_PROXY_APPS_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_SUPL_ES_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_SUPL_HOST_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_SUPL_MODE_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_SUPL_PORT_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_SUPL_VER_STRING, CcType.STRING),
            entry(CarrierConfigManager.Gps.KEY_USE_EMERGENCY_PDN_FOR_EMERGENCY_SUPL_STRING,
                    CcType.STRING),
            entry(CarrierConfigManager.KEY_CARRIER_APP_NO_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                    CcType.STRING_ARRAY),
            entry(CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                    CcType.STRING_ARRAY),
            entry(CarrierConfigManager.KEY_CARRIER_CALL_SCREENING_APP_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_MMS_EMAIL_GATEWAY_NUMBER_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_MMS_HTTP_PARAMS_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_MMS_NAI_SUFFIX_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_MMS_UA_PROF_TAG_NAME_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_MMS_UA_PROF_URL_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_MMS_USER_AGENT_STRING, CcType.STRING),
            entry(CarrierConfigManager.KEY_RATCHET_RAT_FAMILIES, CcType.STRING_ARRAY));

    /**
     * Map from a shorthand string to the feature tags required in registration required in order
     * for the RCS feature to be considered "capable".
     */
    private static final Map<String, Set<String>> TEST_FEATURE_TAG_MAP;
    static {
        ArrayMap<String, Set<String>> map = new ArrayMap<>(18);
        map.put("chat_v1", Collections.singleton(FeatureTags.FEATURE_TAG_CHAT_IM));
        map.put("chat_v2", Collections.singleton(FeatureTags.FEATURE_TAG_CHAT_SESSION));
        map.put("ft", Collections.singleton(FeatureTags.FEATURE_TAG_FILE_TRANSFER));
        map.put("ft_sms", Collections.singleton(FeatureTags.FEATURE_TAG_FILE_TRANSFER_VIA_SMS));
        map.put("mmtel", Collections.singleton(FeatureTags.FEATURE_TAG_MMTEL));
        map.put("mmtel_vt", new ArraySet<>(Arrays.asList(FeatureTags.FEATURE_TAG_MMTEL,
                FeatureTags.FEATURE_TAG_VIDEO)));
        map.put("geo_push", Collections.singleton(FeatureTags.FEATURE_TAG_GEO_PUSH));
        map.put("geo_push_sms", Collections.singleton(FeatureTags.FEATURE_TAG_GEO_PUSH_VIA_SMS));
        map.put("call_comp",
                Collections.singleton(FeatureTags.FEATURE_TAG_CALL_COMPOSER_ENRICHED_CALLING));
        map.put("call_comp_mmtel",
                Collections.singleton(FeatureTags.FEATURE_TAG_CALL_COMPOSER_VIA_TELEPHONY));
        map.put("call_post", Collections.singleton(FeatureTags.FEATURE_TAG_POST_CALL));
        map.put("map", Collections.singleton(FeatureTags.FEATURE_TAG_SHARED_MAP));
        map.put("sketch", Collections.singleton(FeatureTags.FEATURE_TAG_SHARED_SKETCH));
        // Feature tags defined twice for chatbot session because we want v1 and v2 based on bot
        // version
        map.put("chatbot", new ArraySet<>(Arrays.asList(
                FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_SESSION,
                FeatureTags.FEATURE_TAG_CHATBOT_VERSION_SUPPORTED)));
        map.put("chatbot_v2", new ArraySet<>(Arrays.asList(
                FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_SESSION,
                FeatureTags.FEATURE_TAG_CHATBOT_VERSION_V2_SUPPORTED)));
        map.put("chatbot_sa", new ArraySet<>(Arrays.asList(
                FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_STANDALONE_MSG,
                FeatureTags.FEATURE_TAG_CHATBOT_VERSION_SUPPORTED)));
        map.put("chatbot_sa_v2", new ArraySet<>(Arrays.asList(
                FeatureTags.FEATURE_TAG_CHATBOT_COMMUNICATION_USING_STANDALONE_MSG,
                FeatureTags.FEATURE_TAG_CHATBOT_VERSION_V2_SUPPORTED)));
        map.put("chatbot_role", Collections.singleton(FeatureTags.FEATURE_TAG_CHATBOT_ROLE));
        TEST_FEATURE_TAG_MAP = Collections.unmodifiableMap(map);
    }


    public TelephonyShellCommand(ITelephony binder, Context context) {
        mInterface = binder;
        mCarrierConfigManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        mSubscriptionManager = (SubscriptionManager)
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mTelephonyRegistryManager = (TelephonyRegistryManager)
                context.getSystemService(Context.TELEPHONY_REGISTRY_SERVICE);
        mContext = context;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(null);
        }

        switch (cmd) {
            case IMS_SUBCOMMAND: {
                return handleImsCommand();
            }
            case RCS_UCE_COMMAND:
                return handleRcsUceCommand();
            case NUMBER_VERIFICATION_SUBCOMMAND:
                return handleNumberVerificationCommand();
            case EMERGENCY_CALLBACK_MODE:
                return handleEmergencyCallbackModeCommand();
            case EMERGENCY_NUMBER_TEST_MODE:
                return handleEmergencyNumberTestModeCommand();
            case CARRIER_CONFIG_SUBCOMMAND: {
                return handleCcCommand();
            }
            case DATA_TEST_MODE:
                return handleDataTestModeCommand();
            case END_BLOCK_SUPPRESSION:
                return handleEndBlockSuppressionCommand();
            case EUICC_SUBCOMMAND:
                return handleEuiccCommand();
            case GBA_SUBCOMMAND:
                return handleGbaCommand();
            case D2D_SUBCOMMAND:
                return handleD2dCommand();
            case BARRING_SUBCOMMAND:
                return handleBarringCommand();
            case SINGLE_REGISTATION_CONFIG:
                return handleSingleRegistrationConfigCommand();
            case RESTART_MODEM:
                return handleRestartModemCommand();
            case CALL_COMPOSER_SUBCOMMAND:
                return handleCallComposerCommand();
            case UNATTENDED_REBOOT:
                return handleUnattendedReboot();
            case HAS_CARRIER_PRIVILEGES_COMMAND:
                return handleHasCarrierPrivilegesCommand();
            case THERMAL_MITIGATION_COMMAND:
                return handleThermalMitigationCommand();
            case DISABLE_PHYSICAL_SUBSCRIPTION:
                return handleEnablePhysicalSubscription(false);
            case ENABLE_PHYSICAL_SUBSCRIPTION:
                return handleEnablePhysicalSubscription(true);
            case GET_ALLOWED_NETWORK_TYPES_FOR_USER:
            case SET_ALLOWED_NETWORK_TYPES_FOR_USER:
                return handleAllowedNetworkTypesCommand(cmd);
            case GET_IMEI:
                return handleGetImei();
            case GET_SIM_SLOTS_MAPPING:
                return handleGetSimSlotsMapping();
            case RADIO_SUBCOMMAND:
                return handleRadioCommand();
            case CARRIER_RESTRICTION_STATUS_TEST:
                return handleCarrierRestrictionStatusCommand();
            case SET_CARRIER_SERVICE_PACKAGE_OVERRIDE:
                return setCarrierServicePackageOverride();
            case CLEAR_CARRIER_SERVICE_PACKAGE_OVERRIDE:
                return clearCarrierServicePackageOverride();
            case DOMAIN_SELECTION_SUBCOMMAND:
                return handleDomainSelectionCommand();
            case SET_SATELLITE_SERVICE_PACKAGE_NAME:
                return handleSetSatelliteServicePackageNameCommand();
            case SET_SATELLITE_GATEWAY_SERVICE_PACKAGE_NAME:
                return handleSetSatelliteGatewayServicePackageNameCommand();
            case SET_SATELLITE_LISTENING_TIMEOUT_DURATION:
                return handleSetSatelliteListeningTimeoutDuration();
            case SET_SATELLITE_POINTING_UI_CLASS_NAME:
                return handleSetSatellitePointingUiClassNameCommand();
            case SET_DATAGRAM_CONTROLLER_TIMEOUT_DURATION:
                return handleSetDatagramControllerTimeoutDuration();
            case SET_SATELLITE_CONTROLLER_TIMEOUT_DURATION:
                return handleSetSatelliteControllerTimeoutDuration();
            case SET_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE:
                return handleSetEmergencyCallToSatelliteHandoverType();
            case SET_SHOULD_SEND_DATAGRAM_TO_MODEM_IN_DEMO_MODE:
                return handleSetShouldSendDatagramToModemInDemoMode();
            case SET_SATELLITE_ACCESS_CONTROL_OVERLAY_CONFIGS:
                return handleSetSatelliteAccessControlOverlayConfigs();
            case SET_COUNTRY_CODES:
                return handleSetCountryCodes();
            case SET_OEM_ENABLED_SATELLITE_PROVISION_STATUS:
                return handleSetOemEnabledSatelliteProvisionStatus();
            default: {
                return handleDefaultCommands(cmd);
            }
        }
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Telephony Commands:");
        pw.println("  help");
        pw.println("    Print this help text.");
        pw.println("  ims");
        pw.println("    IMS Commands.");
        pw.println("  uce");
        pw.println("    RCS User Capability Exchange Commands.");
        pw.println("  emergency-number-test-mode");
        pw.println("    Emergency Number Test Mode Commands.");
        pw.println("  end-block-suppression");
        pw.println("    End Block Suppression command.");
        pw.println("  data");
        pw.println("    Data Test Mode Commands.");
        pw.println("  cc");
        pw.println("    Carrier Config Commands.");
        pw.println("  gba");
        pw.println("    GBA Commands.");
        pw.println("  src");
        pw.println("    RCS VoLTE Single Registration Config Commands.");
        pw.println("  restart-modem");
        pw.println("    Restart modem command.");
        pw.println("  unattended-reboot");
        pw.println("    Prepare for unattended reboot.");
        pw.println("  has-carrier-privileges [package]");
        pw.println("    Query carrier privilege status for a package. Prints true or false.");
        pw.println("  get-allowed-network-types-for-users");
        pw.println("    Get the Allowed Network Types.");
        pw.println("  set-allowed-network-types-for-users");
        pw.println("    Set the Allowed Network Types.");
        pw.println("  radio");
        pw.println("    Radio Commands.");
        onHelpIms();
        onHelpUce();
        onHelpEmergencyNumber();
        onHelpEndBlockSupperssion();
        onHelpDataTestMode();
        onHelpCc();
        onHelpGba();
        onHelpSrc();
        onHelpD2D();
        onHelpDisableOrEnablePhysicalSubscription();
        onHelpAllowedNetworkTypes();
        onHelpRadio();
        onHelpImei();
        onHelpSatellite();
        onHelpDomainSelection();
    }

    private void onHelpD2D() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("D2D Comms Commands:");
        pw.println("  d2d send TYPE VALUE");
        pw.println("    Sends a D2D message of specified type and value.");
        pw.println("    Type: " + MESSAGE_CALL_RADIO_ACCESS_TYPE + " - "
                + Communicator.messageToString(MESSAGE_CALL_RADIO_ACCESS_TYPE));
        pw.println("    Type: " + MESSAGE_CALL_AUDIO_CODEC + " - " + Communicator.messageToString(
                MESSAGE_CALL_AUDIO_CODEC));
        pw.println("    Type: " + MESSAGE_DEVICE_BATTERY_STATE + " - "
                        + Communicator.messageToString(
                        MESSAGE_DEVICE_BATTERY_STATE));
        pw.println("    Type: " + MESSAGE_DEVICE_NETWORK_COVERAGE + " - "
                + Communicator.messageToString(MESSAGE_DEVICE_NETWORK_COVERAGE));
        pw.println("  d2d transport TYPE");
        pw.println("    Forces the specified D2D transport TYPE to be active.  Use the");
        pw.println("    short class name of the transport; i.e. DtmfTransport or RtpTransport.");
        pw.println("  d2d set-device-support true/default");
        pw.println("    true - forces device support to be enabled for D2D.");
        pw.println("    default - clear any previously set force-enable of D2D, reverting to ");
        pw.println("    the current device's configuration.");
    }

    private void onHelpBarring() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Barring Commands:");
        pw.println("  barring send -s SLOT_ID -b BARRING_TYPE -c IS_CONDITIONALLY_BARRED"
                + " -t CONDITIONAL_BARRING_TIME_SECS");
        pw.println("    Notifies of a barring info change for the specified slot id.");
        pw.println("    BARRING_TYPE: 0 for BARRING_TYPE_NONE");
        pw.println("    BARRING_TYPE: 1 for BARRING_TYPE_UNCONDITIONAL");
        pw.println("    BARRING_TYPE: 2 for BARRING_TYPE_CONDITIONAL");
        pw.println("    BARRING_TYPE: -1 for BARRING_TYPE_UNKNOWN");
    }

    private void onHelpIms() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("IMS Commands:");
        pw.println("  ims set-ims-service [-s SLOT_ID] (-c | -d | -f) PACKAGE_NAME");
        pw.println("    Sets the ImsService defined in PACKAGE_NAME to to be the bound");
        pw.println("    ImsService. Options are:");
        pw.println("      -s: the slot ID that the ImsService should be bound for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      -c: Override the ImsService defined in the carrier configuration.");
        pw.println("      -d: Override the ImsService defined in the device overlay.");
        pw.println("      -f: Set the feature that this override if for, if no option is");
        pw.println("          specified, the new package name will be used for all features.");
        pw.println("  ims get-ims-service [-s SLOT_ID] [-c | -d]");
        pw.println("    Gets the package name of the currently defined ImsService.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID for the registered ImsService. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      -c: The ImsService defined as the carrier configured ImsService.");
        pw.println("      -d: The ImsService defined as the device default ImsService.");
        pw.println("      -f: The feature type that the query will be requested for. If none is");
        pw.println("          specified, the returned package name will correspond to MMTEL.");
        pw.println("  ims clear-ims-service-override [-s SLOT_ID]");
        pw.println("    Clear all carrier ImsService overrides. This does not work for device ");
        pw.println("    configuration overrides. Options are:");
        pw.println("      -s: The SIM slot ID for the registered ImsService. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  ims enable [-s SLOT_ID]");
        pw.println("    enables IMS for the SIM slot specified, or for the default voice SIM slot");
        pw.println("    if none is specified.");
        pw.println("  ims disable [-s SLOT_ID]");
        pw.println("    disables IMS for the SIM slot specified, or for the default voice SIM");
        pw.println("    slot if none is specified.");
        pw.println("  ims conference-event-package [enable/disable]");
        pw.println("    enables or disables handling or network conference event package data.");
    }

    private void onHelpUce() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("User Capability Exchange Commands:");
        pw.println("  uce get-eab-contact [PHONE_NUMBER]");
        pw.println("    Get the EAB contacts from the EAB database.");
        pw.println("    Options are:");
        pw.println("      PHONE_NUMBER: The phone numbers to be removed from the EAB databases");
        pw.println("    Expected output format :");
        pw.println("      [PHONE_NUMBER],[RAW_CONTACT_ID],[CONTACT_ID],[DATA_ID]");
        pw.println("  uce remove-eab-contact [-s SLOT_ID] [PHONE_NUMBER]");
        pw.println("    Remove the EAB contacts from the EAB database.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      PHONE_NUMBER: The phone numbers to be removed from the EAB databases");
        pw.println("  uce get-device-enabled");
        pw.println("    Get the config to check whether the device supports RCS UCE or not.");
        pw.println("  uce set-device-enabled true|false");
        pw.println("    Set the device config for RCS User Capability Exchange to the value.");
        pw.println("    The value could be true, false.");
        pw.println("  uce override-published-caps [-s SLOT_ID] add|remove|clear [CAPABILITIES]");
        pw.println("    Override the existing SIP PUBLISH with different capabilities.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      add [CAPABILITY]: add a new capability");
        pw.println("      remove [CAPABILITY]: remove a capability");
        pw.println("      clear: clear all capability overrides");
        pw.println("      CAPABILITY: \":\" separated list of capabilities.");
        pw.println("          Valid options are: [mmtel(_vt), chat_v1, chat_v2, ft, ft_sms,");
        pw.println("          geo_push, geo_push_sms, call_comp, call_post, map, sketch, chatbot,");
        pw.println("          chatbot_sa, chatbot_role] as well as full length");
        pw.println("          featureTag=\"featureValue\" feature tags that are not defined here.");
        pw.println("  uce get-last-publish-pidf [-s SLOT_ID]");
        pw.println("    Get the PIDF XML included in the last SIP PUBLISH, or \"none\" if no ");
        pw.println("    PUBLISH is active");
        pw.println("  uce remove-request-disallowed-status [-s SLOT_ID]");
        pw.println("    Remove the UCE is disallowed to execute UCE requests status");
        pw.println("  uce set-capabilities-request-timeout [-s SLOT_ID] [REQUEST_TIMEOUT_MS]");
        pw.println("    Set the timeout for contact capabilities request.");
    }

    private void onHelpNumberVerification() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Number verification commands");
        pw.println("  numverify override-package PACKAGE_NAME;");
        pw.println("    Set the authorized package for number verification.");
        pw.println("    Leave the package name blank to reset.");
        pw.println("  numverify fake-call NUMBER;");
        pw.println("    Fake an incoming call from NUMBER. This is for testing. Output will be");
        pw.println("    1 if the call would have been intercepted, 0 otherwise.");
    }

    private void onHelpThermalMitigation() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Thermal mitigation commands");
        pw.println("  thermal-mitigation allow-package PACKAGE_NAME");
        pw.println("    Set the package as one of authorized packages for thermal mitigation.");
        pw.println("  thermal-mitigation disallow-package PACKAGE_NAME");
        pw.println("    Remove the package from one of the authorized packages for thermal "
                + "mitigation.");
    }

    private void onHelpDisableOrEnablePhysicalSubscription() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Disable or enable a physical subscription");
        pw.println("  disable-physical-subscription SUB_ID");
        pw.println("    Disable the physical subscription with the provided subId, if allowed.");
        pw.println("  enable-physical-subscription SUB_ID");
        pw.println("    Enable the physical subscription with the provided subId, if allowed.");
    }

    private void onHelpDataTestMode() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Mobile Data Test Mode Commands:");
        pw.println("  data enable: enable mobile data connectivity");
        pw.println("  data disable: disable mobile data connectivity");
    }

    private void onHelpEmergencyNumber() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Emergency Number Test Mode Commands:");
        pw.println("  emergency-number-test-mode ");
        pw.println("    Add(-a), Clear(-c), Print (-p) or Remove(-r) the emergency number list in"
                + " the test mode");
        pw.println("      -a <emergency number address>: add an emergency number address for the"
                + " test mode, only allows '0'-'9', '*', '#' or '+'.");
        pw.println("      -c: clear the emergency number list in the test mode.");
        pw.println("      -r <emergency number address>: remove an existing emergency number"
                + " address added by the test mode, only allows '0'-'9', '*', '#' or '+'.");
        pw.println("      -p: get the full emergency number list in the test mode.");
    }

    private void onHelpEndBlockSupperssion() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("End Block Suppression command:");
        pw.println("  end-block-suppression: disable suppressing blocking by contact");
        pw.println("                         with emergency services.");
    }

    private void onHelpCc() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Carrier Config Commands:");
        pw.println("  cc get-value [-s SLOT_ID] [KEY]");
        pw.println("    Print carrier config values.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("    KEY: The key to the carrier config value to print. All values are printed");
        pw.println("         if KEY is not specified.");
        pw.println("  cc set-value [-s SLOT_ID] [-p] KEY [NEW_VALUE]");
        pw.println("    Set carrier config KEY to NEW_VALUE.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to set carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      -p: Value will be stored persistent");
        pw.println("    NEW_VALUE specifies the new value for carrier config KEY. Null will be");
        pw.println("      used if NEW_VALUE is not set. Strings should be encapsulated with");
        pw.println("      quotation marks. Spaces needs to be escaped. Example: \"Hello\\ World\"");
        pw.println("      Separate items in arrays with space . Example: \"item1\" \"item2\"");
        pw.println("  cc set-values-from-xml [-s SLOT_ID] [-p] < XML_FILE_PATH");
        pw.println("    Set carrier config based on the contents of the XML_FILE. File must be");
        pw.println("    provided through standard input and follow CarrierConfig XML format.");
        pw.println("    Example: packages/apps/CarrierConfig/assets/*.xml");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to set carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("      -p: Value will be stored persistent");
        pw.println("  cc clear-values [-s SLOT_ID]");
        pw.println("    Clear all carrier override values that has previously been set");
        pw.println("    with set-value or set-values-from-xml");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to clear carrier config values for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
    }

    private void onHelpEuicc() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Euicc Commands:");
        pw.println("  euicc set-euicc-uicomponent COMPONENT_NAME PACKAGE_NAME");
        pw.println("  Sets the Euicc Ui-Component which handles EuiccService Actions.");
        pw.println("  COMPONENT_NAME: The component name which handles UI Actions.");
        pw.println("  PACKAGE_NAME: THe package name in which ui component belongs.");
    }

    private void onHelpGba() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Gba Commands:");
        pw.println("  gba set-service [-s SLOT_ID] PACKAGE_NAME");
        pw.println("    Sets the GbaService defined in PACKAGE_NAME to to be the bound.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  gba get-service [-s SLOT_ID]");
        pw.println("    Gets the package name of the currently defined GbaService.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  gba set-release [-s SLOT_ID] n");
        pw.println("    Sets the time to release/unbind GbaService in n milli-second.");
        pw.println("    Do not release/unbind if n is -1.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  gba get-release [-s SLOT_ID]");
        pw.println("    Gets the time to release/unbind GbaService in n milli-sencond.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read carrier config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
    }

    private void onHelpSrc() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("RCS VoLTE Single Registration Config Commands:");
        pw.println("  src set-test-enabled true|false");
        pw.println("    Sets the test mode enabled for RCS VoLTE single registration.");
        pw.println("    The value could be true, false, or null(undefined).");
        pw.println("  src get-test-enabled");
        pw.println("    Gets the test mode for RCS VoLTE single registration.");
        pw.println("  src set-device-enabled true|false|null");
        pw.println("    Sets the device config for RCS VoLTE single registration to the value.");
        pw.println("    The value could be true, false, or null(undefined).");
        pw.println("  src get-device-enabled");
        pw.println("    Gets the device config for RCS VoLTE single registration.");
        pw.println("  src set-carrier-enabled [-s SLOT_ID] true|false|null");
        pw.println("    Sets the carrier config for RCS VoLTE single registration to the value.");
        pw.println("    The value could be true, false, or null(undefined).");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to set the config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  src get-carrier-enabled [-s SLOT_ID]");
        pw.println("    Gets the carrier config for RCS VoLTE single registration.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read the config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  src set-feature-validation [-s SLOT_ID] true|false|null");
        pw.println("    Sets ims feature validation result.");
        pw.println("    The value could be true, false, or null(undefined).");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to set the config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
        pw.println("  src get-feature-validation [-s SLOT_ID]");
        pw.println("    Gets ims feature validation override value.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read the config value for. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
    }

    private void onHelpAllowedNetworkTypes() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Allowed Network Types Commands:");
        pw.println("  get-allowed-network-types-for-users [-s SLOT_ID]");
        pw.println("    Print allowed network types value.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to read allowed network types value for. If no");
        pw.println("          option is specified, it will choose the default voice SIM slot.");
        pw.println("  set-allowed-network-types-for-users [-s SLOT_ID] [NETWORK_TYPES_BITMASK]");
        pw.println("    Sets allowed network types to NETWORK_TYPES_BITMASK.");
        pw.println("    Options are:");
        pw.println("      -s: The SIM slot ID to set allowed network types value for. If no");
        pw.println("          option is specified, it will choose the default voice SIM slot.");
        pw.println("    NETWORK_TYPES_BITMASK specifies the new network types value and this type");
        pw.println("      is bitmask in binary format. Reference the NetworkTypeBitMask");
        pw.println("      at TelephonyManager.java");
        pw.println("      For example:");
        pw.println("        NR only                    : 10000000000000000000");
        pw.println("        NR|LTE                     : 11000001000000000000");
        pw.println("        NR|LTE|CDMA|EVDO|GSM|WCDMA : 11001111101111111111");
        pw.println("        LTE|CDMA|EVDO|GSM|WCDMA    : 01001111101111111111");
        pw.println("        LTE only                   : 01000001000000000000");
    }

    private void onHelpRadio() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Radio Commands:");
        pw.println("  radio set-modem-service [-s SERVICE_NAME]");
        pw.println("    Sets the class name of modem service defined in SERVICE_NAME");
        pw.println("    to be the bound. Options are:");
        pw.println("      -s: the service name that the modem service should be bound for.");
        pw.println("          If no option is specified, it will bind to the default.");
        pw.println("  radio get-modem-service");
        pw.println("    Gets the service name of the currently defined modem service.");
        pw.println("    If it is binding to default, 'default' returns.");
        pw.println("    If it doesn't bind to any modem service for some reasons,");
        pw.println("    the result would be 'unknown'.");
    }

    private void onHelpSatellite() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Satellite Commands:");
        pw.println("  set-satellite-service-package-name [-s SERVICE_PACKAGE_NAME]");
        pw.println("    Sets the package name of satellite service defined in");
        pw.println("    SERVICE_PACKAGE_NAME to be bound. Options are:");
        pw.println("      -s: the satellite service package name that Telephony will bind to.");
        pw.println("          If no option is specified, it will bind to the default.");
        pw.println("  set-satellite-gateway-service-package-name [-s SERVICE_PACKAGE_NAME]");
        pw.println("    Sets the package name of satellite gateway service defined in");
        pw.println("    SERVICE_PACKAGE_NAME to be bound. Options are:");
        pw.println("      -s: the satellite gateway service package name that Telephony will bind");
        pw.println("           to. If no option is specified, it will bind to the default.");
        pw.println("  set-satellite-listening-timeout-duration [-t TIMEOUT_MILLIS]");
        pw.println("    Sets the timeout duration in millis that satellite will stay at listening");
        pw.println("    mode. Options are:");
        pw.println("      -t: the timeout duration in milliseconds.");
        pw.println("          If no option is specified, it will use the default values.");
        pw.println("  set-satellite-pointing-ui-class-name [-p PACKAGE_NAME -c CLASS_NAME]");
        pw.println("    Sets the package and class name of satellite pointing UI app defined in");
        pw.println("    PACKAGE_NAME and CLASS_NAME to be launched. Options are:");
        pw.println("      -p: the satellite pointing UI app package name that Telephony will");
        pw.println("           launch. If no option is specified, it will launch the default.");
        pw.println("      -c: the satellite pointing UI app class name that Telephony will");
        pw.println("           launch.");
        pw.println("  set-emergency-call-to-satellite-handover-type [-t HANDOVER_TYPE ");
        pw.println("    -d DELAY_SECONDS] Override connectivity status in monitoring emergency ");
        pw.println("    call and sending EVENT_DISPLAY_EMERGENCY_MESSAGE to Dialer.");
        pw.println("    Options are:");
        pw.println("      -t: the emergency call to satellite handover type.");
        pw.println("          If no option is specified, override is disabled.");
        pw.println("      -d: the delay in seconds in sending EVENT_DISPLAY_EMERGENCY_MESSAGE.");
        pw.println("          If no option is specified, there is no delay in sending the event.");
        pw.println("  set-satellite-access-control-overlay-configs [-r -a -f SATELLITE_S2_FILE ");
        pw.println("    -d LOCATION_FRESH_DURATION_NANOS -c COUNTRY_CODES] Override the overlay");
        pw.println("    configs of satellite access controller.");
        pw.println("    Options are:");
        pw.println("      -r: clear the overriding. Absent means enable overriding.");
        pw.println("      -a: the country codes is an allowed list. Absent means disallowed.");
        pw.println("      -f: the satellite s2 file.");
        pw.println("      -d: the location fresh duration nanos.");
        pw.println("      -c: the list of satellite country codes separated by comma.");
        pw.println("  set-country-codes [-r -n CURRENT_NETWORK_COUNTRY_CODES -c");
        pw.println("    CACHED_NETWORK_COUNTRY_CODES -l LOCATION_COUNTRY_CODE -t");
        pw.println("    LOCATION_COUNTRY_CODE_TIMESTAMP] ");
        pw.println("    Override the cached location country code and its update timestamp. ");
        pw.println("    Options are:");
        pw.println("      -r: clear the overriding. Absent means enable overriding.");
        pw.println("      -n: the current network country code ISOs.");
        pw.println("      -c: the cached network country code ISOs.");
        pw.println("      -l: the location country code ISO.");
        pw.println("      -t: the update timestamp nanos of the location country code.");
        pw.println("  set-oem-enabled-satellite-provision-status [-p true/false]");
        pw.println("    Sets the OEM-enabled satellite provision status. Options are:");
        pw.println("      -p: the overriding satellite provision status. If no option is ");
        pw.println("          specified, reset the overridden provision status.");
    }

    private void onHelpImei() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("IMEI Commands:");
        pw.println("  get-imei [-s SLOT_ID]");
        pw.println("    Gets the device IMEI. Options are:");
        pw.println("      -s: the slot ID to get the IMEI. If no option");
        pw.println("          is specified, it will choose the default voice SIM slot.");
    }

    private void onHelpDomainSelection() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Domain Selection Commands:");
        pw.println("  domainselection set-dss-override COMPONENT_NAME");
        pw.println("    Sets the service defined in COMPONENT_NAME to be bound");
        pw.println("  domainselection clear-dss-override");
        pw.println("    Clears DomainSelectionService override.");
    }

    private int handleImsCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpIms();
            return 0;
        }

        switch (arg) {
            case IMS_SET_IMS_SERVICE: {
                return handleImsSetServiceCommand();
            }
            case IMS_GET_IMS_SERVICE: {
                return handleImsGetServiceCommand();
            }
            case IMS_CLEAR_SERVICE_OVERRIDE: {
                return handleImsClearCarrierServiceCommand();
            }
            case ENABLE: {
                return handleEnableIms();
            }
            case DISABLE: {
                return handleDisableIms();
            }
            case IMS_CEP: {
                return handleCepChange();
            }
        }

        return -1;
    }

    private int handleDataTestModeCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String arg = getNextArgRequired();
        if (arg == null) {
            onHelpDataTestMode();
            return 0;
        }
        switch (arg) {
            case ENABLE: {
                try {
                    mInterface.enableDataConnectivity(mContext.getOpPackageName());
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "data enable, error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            case DISABLE: {
                try {
                    mInterface.disableDataConnectivity(mContext.getOpPackageName());
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "data disable, error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            default:
                onHelpDataTestMode();
                break;
        }
        return 0;
    }

    private int handleEmergencyCallbackModeCommand() {
        PrintWriter errPw = getErrPrintWriter();
        try {
            mInterface.startEmergencyCallbackMode();
            Log.d(LOG_TAG, "handleEmergencyCallbackModeCommand: triggered");
        } catch (RemoteException ex) {
            Log.w(LOG_TAG, "emergency-callback-mode error: " + ex.getMessage());
            errPw.println("Exception: " + ex.getMessage());
            return -1;
        }
        return 0;
    }

    private void removeEmergencyNumberTestMode(String emergencyNumber) {
        PrintWriter errPw = getErrPrintWriter();
        for (int routingType : ROUTING_TYPES) {
            try {
                mInterface.updateEmergencyNumberListTestMode(
                        EmergencyNumberTracker.REMOVE_EMERGENCY_NUMBER_TEST_MODE,
                        new EmergencyNumber(emergencyNumber, "", "",
                                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                                new ArrayList<String>(),
                                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_TEST,
                                routingType));
            } catch (RemoteException ex) {
                Log.w(LOG_TAG, "emergency-number-test-mode " + "error " + ex.getMessage());
                errPw.println("Exception: " + ex.getMessage());
            }
        }
    }

    private int handleEmergencyNumberTestModeCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String opt = getNextOption();
        if (opt == null) {
            onHelpEmergencyNumber();
            return 0;
        }
        switch (opt) {
            case "-a": {
                String emergencyNumberCmd = getNextArgRequired();
                if (emergencyNumberCmd == null){
                    errPw.println(INVALID_ENTRY_ERROR);
                    return -1;
                }
                String[] params = emergencyNumberCmd.split(":");
                String emergencyNumber;
                if (params[0] == null ||
                        !EmergencyNumber.validateEmergencyNumberAddress(params[0])){
                    errPw.println(INVALID_ENTRY_ERROR);
                    return -1;
                } else {
                    emergencyNumber = params[0];
                }
                removeEmergencyNumberTestMode(emergencyNumber);
                int emergencyCallRouting = EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN;
                if (params.length > 1) {
                    switch (params[1].toLowerCase(Locale.ROOT)) {
                        case "emergency":
                            emergencyCallRouting = EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY;
                            break;
                        case "normal":
                            emergencyCallRouting = EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL;
                            break;
                        case "unknown":
                            break;
                        default:
                            errPw.println("\"" + params[1] + "\" is not a valid specification for "
                                    + "emergency call routing. Please enter either \"normal\", "
                                    + "\"unknown\", or \"emergency\" for call routing. "
                                    + "(-a 1234:normal)");
                            return -1;
                    }
                }
                try {
                    mInterface.updateEmergencyNumberListTestMode(
                            EmergencyNumberTracker.ADD_EMERGENCY_NUMBER_TEST_MODE,
                            new EmergencyNumber(emergencyNumber, "", "",
                                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                                    new ArrayList<String>(),
                                    EmergencyNumber.EMERGENCY_NUMBER_SOURCE_TEST,
                                    emergencyCallRouting));
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -a " + emergencyNumber
                            + ", error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            case "-c": {
                try {
                    mInterface.updateEmergencyNumberListTestMode(
                            EmergencyNumberTracker.RESET_EMERGENCY_NUMBER_TEST_MODE, null);
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -c " + "error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            case "-r": {
                String emergencyNumberCmd = getNextArgRequired();
                if (emergencyNumberCmd == null
                        || !EmergencyNumber.validateEmergencyNumberAddress(emergencyNumberCmd)) {
                    errPw.println("An emergency number (only allow '0'-'9', '*', '#' or '+') needs"
                            + " to be specified after -r in the command ");
                    return -1;
                }
                removeEmergencyNumberTestMode(emergencyNumberCmd);
                break;
            }
            case "-p": {
                try {
                    getOutPrintWriter().println(mInterface.getEmergencyNumberListTestMode());
                } catch (RemoteException ex) {
                    Log.w(LOG_TAG, "emergency-number-test-mode -p " + "error " + ex.getMessage());
                    errPw.println("Exception: " + ex.getMessage());
                    return -1;
                }
                break;
            }
            default:
                onHelpEmergencyNumber();
                break;
        }
        return 0;
    }

    private int handleNumberVerificationCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpNumberVerification();
            return 0;
        }

        if (!checkShellUid()) {
            return -1;
        }

        switch (arg) {
            case NUMBER_VERIFICATION_OVERRIDE_PACKAGE: {
                NumberVerificationManager.overrideAuthorizedPackage(getNextArg());
                return 0;
            }
            case NUMBER_VERIFICATION_FAKE_CALL: {
                boolean val = NumberVerificationManager.getInstance()
                        .checkIncomingCall(getNextArg());
                getOutPrintWriter().println(val ? "1" : "0");
                return 0;
            }
        }

        return -1;
    }

    private boolean subIsEsim(int subId) {
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (info != null) {
            return info.isEmbedded();
        }
        return false;
    }

    private int handleEnablePhysicalSubscription(boolean enable) {
        PrintWriter errPw = getErrPrintWriter();
        int subId = 0;
        try {
            subId = Integer.parseInt(getNextArgRequired());
        } catch (NumberFormatException e) {
            errPw.println((enable ? "enable" : "disable")
                    + "-physical-subscription requires an integer as a subId.");
            return -1;
        }
        // Verify that the user is allowed to run the command. Only allowed in rooted device in a
        // non user build.
        if (Binder.getCallingUid() != Process.ROOT_UID || TelephonyUtils.IS_USER) {
            errPw.println("cc: Permission denied.");
            return -1;
        }
        // Verify that the subId represents a physical sub
        if (subIsEsim(subId)) {
            errPw.println("SubId " + subId + " is not for a physical subscription");
            return -1;
        }
        Log.d(LOG_TAG, (enable ? "Enabling" : "Disabling")
                + " physical subscription with subId=" + subId);
        mSubscriptionManager.setUiccApplicationsEnabled(subId, enable);
        return 0;
    }

    private int handleThermalMitigationCommand() {
        String arg = getNextArg();
        String packageName = getNextArg();
        if (arg == null || packageName == null) {
            onHelpThermalMitigation();
            return 0;
        }

        if (!checkShellUid()) {
            return -1;
        }

        switch (arg) {
            case ALLOW_THERMAL_MITIGATION_PACKAGE_SUBCOMMAND: {
                PhoneInterfaceManager.addPackageToThermalMitigationAllowlist(packageName, mContext);
                return 0;
            }
            case DISALLOW_THERMAL_MITIGATION_PACKAGE_SUBCOMMAND: {
                PhoneInterfaceManager.removePackageFromThermalMitigationAllowlist(packageName,
                        mContext);
                return 0;
            }
            default:
                onHelpThermalMitigation();
        }

        return -1;

    }

    private int handleD2dCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpD2D();
            return 0;
        }

        switch (arg) {
            case D2D_SEND: {
                return handleD2dSendCommand();
            }
            case D2D_TRANSPORT: {
                return handleD2dTransportCommand();
            }
            case D2D_SET_DEVICE_SUPPORT: {
                return handleD2dDeviceSupportedCommand();
            }
        }

        return -1;
    }

    private int handleD2dSendCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int messageType = -1;
        int messageValue = -1;

        String arg = getNextArg();
        if (arg == null) {
            onHelpD2D();
            return 0;
        }
        try {
            messageType = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            errPw.println("message type must be a valid integer");
            return -1;
        }

        arg = getNextArg();
        if (arg == null) {
            onHelpD2D();
            return 0;
        }
        try {
            messageValue = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            errPw.println("message value must be a valid integer");
            return -1;
        }

        try {
            mInterface.sendDeviceToDeviceMessage(messageType, messageValue);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "d2d send error: " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }

        return 0;
    }

    private int handleD2dTransportCommand() {
        PrintWriter errPw = getErrPrintWriter();

        String arg = getNextArg();
        if (arg == null) {
            onHelpD2D();
            return 0;
        }

        try {
            mInterface.setActiveDeviceToDeviceTransport(arg);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "d2d transport error: " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }
    private int handleBarringCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpBarring();
            return 0;
        }

        switch (arg) {
            case BARRING_SEND_INFO: {
                return handleBarringSendCommand();
            }
        }
        return -1;
    }

    private int handleBarringSendCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int slotId = getDefaultSlot();
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        @BarringInfo.BarringServiceInfo.BarringType int barringType =
                BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL;
        boolean isConditionallyBarred = false;
        int conditionalBarringTimeSeconds = 0;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                        subId = SubscriptionManager.getSubscriptionId(slotId);
                    } catch (NumberFormatException e) {
                        errPw.println("barring send requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
                case "-b": {
                    try {
                        barringType = Integer.parseInt(getNextArgRequired());
                        if (barringType < -1 || barringType > 2) {
                            throw new NumberFormatException();
                        }

                    } catch (NumberFormatException e) {
                        errPw.println("barring send requires an integer in range [-1,2] as "
                                + "a BARRING_TYPE.");
                        return -1;
                    }
                    break;
                }
                case "-c": {
                    try {
                        isConditionallyBarred = Boolean.parseBoolean(getNextArgRequired());
                    } catch (Exception e) {
                        errPw.println("barring send requires a boolean after -c indicating"
                                + " conditional barring");
                        return -1;
                    }
                    break;
                }
                case "-t": {
                    try {
                        conditionalBarringTimeSeconds = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("barring send requires an integer for time of barring"
                                + " in seconds after -t for conditional barring");
                        return -1;
                    }
                    break;
                }
            }
        }
        SparseArray<BarringInfo.BarringServiceInfo> barringServiceInfos = new SparseArray<>();
        BarringInfo.BarringServiceInfo bsi = new BarringInfo.BarringServiceInfo(
                barringType, isConditionallyBarred, 0, conditionalBarringTimeSeconds);
        barringServiceInfos.append(0, bsi);
        BarringInfo barringInfo = new BarringInfo(null, barringServiceInfos);
        try {
            mTelephonyRegistryManager.notifyBarringInfoChanged(slotId, subId, barringInfo);
        } catch (Exception e) {
            Log.w(LOG_TAG, "barring send error: " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleD2dDeviceSupportedCommand() {
        PrintWriter errPw = getErrPrintWriter();

        String arg = getNextArg();
        if (arg == null) {
            onHelpD2D();
            return 0;
        }

        boolean isEnabled = "true".equals(arg.toLowerCase(Locale.ROOT));
        try {
            mInterface.setDeviceToDeviceForceEnabled(isEnabled);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Error forcing D2D enabled: " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    // ims set-ims-service
    private int handleImsSetServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int slotId = getDefaultSlot();
        Boolean isCarrierService = null;
        List<Integer> featuresList = new ArrayList<>();

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("ims set-ims-service requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
                case "-c": {
                    isCarrierService = true;
                    break;
                }
                case "-d": {
                    isCarrierService = false;
                    break;
                }
                case "-f": {
                    String featureString = getNextArgRequired();
                    String[] features = featureString.split(",");
                    for (int i = 0; i < features.length; i++) {
                        try {
                            Integer result = Integer.parseInt(features[i]);
                            if (result < ImsFeature.FEATURE_EMERGENCY_MMTEL
                                    || result >= ImsFeature.FEATURE_MAX) {
                                errPw.println("ims set-ims-service -f " + result
                                        + " is an invalid feature.");
                                return -1;
                            }
                            featuresList.add(result);
                        } catch (NumberFormatException e) {
                            errPw.println("ims set-ims-service -f tried to parse " + features[i]
                                            + " as an integer.");
                            return -1;
                        }
                    }
                }
            }
        }
        // Mandatory param, either -c or -d
        if (isCarrierService == null) {
            errPw.println("ims set-ims-service requires either \"-c\" or \"-d\" to be set.");
            return -1;
        }

        String packageName = getNextArg();

        try {
            if (packageName == null) {
                packageName = "";
            }
            int[] featureArray = new int[featuresList.size()];
            for (int i = 0; i < featuresList.size(); i++) {
                featureArray[i] = featuresList.get(i);
            }
            boolean result = mInterface.setBoundImsServiceOverride(slotId, isCarrierService,
                    featureArray, packageName);
            if (VDBG) {
                Log.v(LOG_TAG, "ims set-ims-service -s " + slotId + " "
                        + (isCarrierService ? "-c " : "-d ")
                        + "-f " + featuresList + " "
                        + packageName + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "ims set-ims-service -s " + slotId + " "
                    + (isCarrierService ? "-c " : "-d ")
                    + "-f " + featuresList + " "
                    + packageName + ", error" + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    // ims clear-ims-service-override
    private int handleImsClearCarrierServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int slotId = getDefaultSlot();

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("ims set-ims-service requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
            }
        }

        try {
            boolean result = mInterface.clearCarrierImsServiceOverride(slotId);
            if (VDBG) {
                Log.v(LOG_TAG, "ims clear-ims-service-override -s " + slotId
                        + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "ims clear-ims-service-override -s " + slotId
                    + ", error" + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    // ims get-ims-service
    private int handleImsGetServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        int slotId = getDefaultSlot();
        Boolean isCarrierService = null;
        Integer featureType = ImsFeature.FEATURE_MMTEL;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("ims set-ims-service requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
                case "-c": {
                    isCarrierService = true;
                    break;
                }
                case "-d": {
                    isCarrierService = false;
                    break;
                }
                case "-f": {
                    try {
                        featureType = Integer.parseInt(getNextArg());
                    } catch (NumberFormatException e) {
                        errPw.println("ims get-ims-service -f requires valid integer as feature.");
                        return -1;
                    }
                    if (featureType < ImsFeature.FEATURE_EMERGENCY_MMTEL
                            || featureType >= ImsFeature.FEATURE_MAX) {
                        errPw.println("ims get-ims-service -f invalid feature.");
                        return -1;
                    }
                }
            }
        }
        // Mandatory param, either -c or -d
        if (isCarrierService == null) {
            errPw.println("ims get-ims-service requires either \"-c\" or \"-d\" to be set.");
            return -1;
        }

        String result;
        try {
            result = mInterface.getBoundImsServicePackage(slotId, isCarrierService, featureType);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "ims get-ims-service -s " + slotId + " "
                    + (isCarrierService ? "-c " : "-d ")
                    + (featureType != null ? ("-f " + featureType) : "") + " , returned: "
                    + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleEnableIms() {
        int slotId = getDefaultSlot();
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println("ims enable requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
            }
        }
        try {
            mInterface.enableIms(slotId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "ims enable -s " + slotId);
        }
        return 0;
    }

    private int handleDisableIms() {
        int slotId = getDefaultSlot();
        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        slotId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        getErrPrintWriter().println(
                                "ims disable requires an integer as a SLOT_ID.");
                        return -1;
                    }
                    break;
                }
            }
        }
        try {
            mInterface.disableIms(slotId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "ims disable -s " + slotId);
        }
        return 0;
    }

    private int handleCepChange() {
        Log.i(LOG_TAG, "handleCepChange");
        String opt = getNextArg();
        if (opt == null) {
            return -1;
        }
        boolean isCepEnabled = opt.equals("enable");

        try {
            mInterface.setCepEnabled(isCepEnabled);
        } catch (RemoteException e) {
            return -1;
        }
        return 0;
    }

    private int getDefaultSlot() {
        int slotId = SubscriptionManager.getDefaultVoicePhoneId();
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX
                || slotId == SubscriptionManager.DEFAULT_PHONE_INDEX) {
            // If there is no default, default to slot 0.
            slotId = DEFAULT_PHONE_ID;
        }
        return slotId;
    }

    // Parse options related to Carrier Config Commands.
    private CcOptionParseResult parseCcOptions(String tag, boolean allowOptionPersistent) {
        PrintWriter errPw = getErrPrintWriter();
        CcOptionParseResult result = new CcOptionParseResult();
        result.mSubId = SubscriptionManager.getDefaultSubscriptionId();
        result.mPersistent = false;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    try {
                        result.mSubId = slotStringToSubId(tag, getNextArgRequired());
                        if (!SubscriptionManager.isValidSubscriptionId(result.mSubId)) {
                            errPw.println(tag + "No valid subscription found.");
                            return null;
                        }

                    } catch (IllegalArgumentException e) {
                        // Missing slot id
                        errPw.println(tag + "SLOT_ID expected after -s.");
                        return null;
                    }
                    break;
                }
                case "-p": {
                    if (allowOptionPersistent) {
                        result.mPersistent = true;
                    } else {
                        errPw.println(tag + "Unexpected option " + opt);
                        return null;
                    }
                    break;
                }
                default: {
                    errPw.println(tag + "Unknown option " + opt);
                    return null;
                }
            }
        }
        return result;
    }

    private int slotStringToSubId(String tag, String slotString) {
        int slotId = -1;
        try {
            slotId = Integer.parseInt(slotString);
        } catch (NumberFormatException e) {
            getErrPrintWriter().println(tag + slotString + " is not a valid number for SLOT_ID.");
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        if (!SubscriptionManager.isValidPhoneId(slotId)) {
            getErrPrintWriter().println(tag + slotString + " is not a valid SLOT_ID.");
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        Phone phone = PhoneFactory.getPhone(slotId);
        if (phone == null) {
            getErrPrintWriter().println(tag + "No subscription found in slot " + slotId + ".");
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return phone.getSubId();
    }

    private boolean checkShellUid() {
        // adb can run as root or as shell, depending on whether the device is rooted.
        return Binder.getCallingUid() == Process.SHELL_UID
                || Binder.getCallingUid() == Process.ROOT_UID;
    }

    private int handleCcCommand() {
        // Verify that the user is allowed to run the command. Only allowed in rooted device in a
        // non user build.
        if (Binder.getCallingUid() != Process.ROOT_UID || TelephonyUtils.IS_USER) {
            getErrPrintWriter().println("cc: Permission denied.");
            return -1;
        }

        String arg = getNextArg();
        if (arg == null) {
            onHelpCc();
            return 0;
        }

        switch (arg) {
            case CC_GET_VALUE: {
                return handleCcGetValue();
            }
            case CC_SET_VALUE: {
                return handleCcSetValue();
            }
            case CC_SET_VALUES_FROM_XML: {
                return handleCcSetValuesFromXml();
            }
            case CC_CLEAR_VALUES: {
                return handleCcClearValues();
            }
            default: {
                getErrPrintWriter().println("cc: Unknown argument: " + arg);
            }
        }
        return -1;
    }

    // cc get-value
    private int handleCcGetValue() {
        PrintWriter errPw = getErrPrintWriter();
        String tag = CARRIER_CONFIG_SUBCOMMAND + " " + CC_GET_VALUE + ": ";
        String key = null;

        // Parse all options
        CcOptionParseResult options = parseCcOptions(tag, false);
        if (options == null) {
            return -1;
        }

        // Get bundle containing all carrier configuration values.
        PersistableBundle bundle = mCarrierConfigManager.getConfigForSubId(options.mSubId);
        if (bundle == null) {
            errPw.println(tag + "No carrier config values found for subId " + options.mSubId + ".");
            return -1;
        }

        // Get the key.
        key = getNextArg();
        if (key != null) {
            // A key was provided. Verify if it is a valid key
            if (!bundle.containsKey(key)) {
                errPw.println(tag + key + " is not a valid key.");
                return -1;
            }

            // Print the carrier config value for key.
            getOutPrintWriter().println(ccValueToString(key, getType(tag, key, bundle), bundle));
        } else {
            // No key provided. Show all values.
            // Iterate over a sorted list of all carrier config keys and print them.
            TreeSet<String> sortedSet = new TreeSet<String>(bundle.keySet());
            for (String k : sortedSet) {
                getOutPrintWriter().println(ccValueToString(k, getType(tag, k, bundle), bundle));
            }
        }
        return 0;
    }

    // cc set-value
    private int handleCcSetValue() {
        PrintWriter errPw = getErrPrintWriter();
        String tag = CARRIER_CONFIG_SUBCOMMAND + " " + CC_SET_VALUE + ": ";

        // Parse all options
        CcOptionParseResult options = parseCcOptions(tag, true);
        if (options == null) {
            return -1;
        }

        // Get bundle containing all current carrier configuration values.
        PersistableBundle originalValues = mCarrierConfigManager.getConfigForSubId(options.mSubId);
        if (originalValues == null) {
            errPw.println(tag + "No carrier config values found for subId " + options.mSubId + ".");
            return -1;
        }

        // Get the key.
        String key = getNextArg();
        if (key == null || key.equals("")) {
            errPw.println(tag + "KEY is missing");
            return -1;
        }

        // Verify if the key is valid
        if (!originalValues.containsKey(key)) {
            errPw.println(tag + key + " is not a valid key.");
            return -1;
        }

        // Remaining arguments is a list of new values. Add them all into an ArrayList.
        ArrayList<String> valueList = new ArrayList<String>();
        while (peekNextArg() != null) {
            valueList.add(getNextArg());
        }

        // Find the type of the carrier config value
        CcType type = getType(tag, key, originalValues);
        if (type == CcType.UNKNOWN) {
            errPw.println(tag + "ERROR: Not possible to override key with unknown type.");
            return -1;
        }
        if (type == CcType.PERSISTABLE_BUNDLE) {
            errPw.println(tag + "ERROR: Overriding of persistable bundle type is not supported. "
                    + "Use set-values-from-xml instead.");
            return -1;
        }

        // Create an override bundle containing the key and value that should be overriden.
        PersistableBundle overrideBundle = getOverrideBundle(tag, type, key, valueList);
        if (overrideBundle == null) {
            return -1;
        }

        // Override the value
        mCarrierConfigManager.overrideConfig(options.mSubId, overrideBundle, options.mPersistent);

        // Find bundle containing all new carrier configuration values after the override.
        PersistableBundle newValues = mCarrierConfigManager.getConfigForSubId(options.mSubId);
        if (newValues == null) {
            errPw.println(tag + "No carrier config values found for subId " + options.mSubId + ".");
            return -1;
        }

        // Print the original and new value.
        String originalValueString = ccValueToString(key, type, originalValues);
        String newValueString = ccValueToString(key, type, newValues);
        getOutPrintWriter().println("Previous value: \n" + originalValueString);
        getOutPrintWriter().println("New value: \n" + newValueString);

        return 0;
    }

    // cc set-values-from-xml
    private int handleCcSetValuesFromXml() {
        PrintWriter errPw = getErrPrintWriter();
        String tag = CARRIER_CONFIG_SUBCOMMAND + " " + CC_SET_VALUES_FROM_XML + ": ";

        // Parse all options
        CcOptionParseResult options = parseCcOptions(tag, true);
        if (options == null) {
            return -1;
        }

        // Get bundle containing all current carrier configuration values.
        PersistableBundle originalValues = mCarrierConfigManager.getConfigForSubId(options.mSubId);
        if (originalValues == null) {
            errPw.println(tag + "No carrier config values found for subId " + options.mSubId + ".");
            return -1;
        }

        PersistableBundle overrideBundle = readPersistableBundleFromXml(tag);
        if (overrideBundle == null) {
            return -1;
        }

        // Verify all values are valid types
        for (String key : overrideBundle.keySet()) {
            CcType type = getType(tag, key, originalValues);
            if (type == CcType.UNKNOWN) {
                errPw.println(tag + "ERROR: Not possible to override key with unknown type.");
                return -1;
            }
        }

        // Override the value
        mCarrierConfigManager.overrideConfig(options.mSubId, overrideBundle, options.mPersistent);

        // Find bundle containing all new carrier configuration values after the override.
        PersistableBundle newValues = mCarrierConfigManager.getConfigForSubId(options.mSubId);
        if (newValues == null) {
            errPw.println(tag + "No carrier config values found for subId " + options.mSubId + ".");
            return -1;
        }

        // Print the original and new values
        overrideBundle.keySet().forEach(key -> {
            CcType type = getType(tag, key, originalValues);
            String originalValueString = ccValueToString(key, type, originalValues);
            String newValueString = ccValueToString(key, type, newValues);
            getOutPrintWriter().println("Previous value: \n" + originalValueString);
            getOutPrintWriter().println("New value: \n" + newValueString);
        });

        return 0;
    }

    private PersistableBundle readPersistableBundleFromXml(String tag) {
        PersistableBundle subIdBundles;
        try {
            subIdBundles = PersistableBundle.readFromStream(getRawInputStream());
        } catch (IOException | RuntimeException e) {
            PrintWriter errPw = getErrPrintWriter();
            errPw.println(tag + e);
            return null;
        }

        return subIdBundles;
    }

    // cc clear-values
    private int handleCcClearValues() {
        String tag = CARRIER_CONFIG_SUBCOMMAND + " " + CC_CLEAR_VALUES + ": ";

        // Parse all options
        CcOptionParseResult options = parseCcOptions(tag, false);
        if (options == null) {
            return -1;
        }

        // Clear all values that has previously been set.
        mCarrierConfigManager.overrideConfig(options.mSubId, null, true);
        getOutPrintWriter()
                .println("All previously set carrier config override values has been cleared");
        return 0;
    }

    private CcType getType(String tag, String key, PersistableBundle bundle) {
        // Find the type by checking the type of the current value stored in the bundle.
        Object value = bundle.get(key);

        if (CC_TYPE_MAP.containsKey(key)) {
            return CC_TYPE_MAP.get(key);
        } else if (value != null) {
            if (value instanceof Boolean) {
                return CcType.BOOLEAN;
            }
            if (value instanceof Double) {
                return CcType.DOUBLE;
            }
            if (value instanceof double[]) {
                return CcType.DOUBLE_ARRAY;
            }
            if (value instanceof Integer) {
                return CcType.INT;
            }
            if (value instanceof int[]) {
                return CcType.INT_ARRAY;
            }
            if (value instanceof Long) {
                return CcType.LONG;
            }
            if (value instanceof long[]) {
                return CcType.LONG_ARRAY;
            }
            if (value instanceof String) {
                return CcType.STRING;
            }
            if (value instanceof String[]) {
                return CcType.STRING_ARRAY;
            }
            if (value instanceof PersistableBundle) {
                return CcType.PERSISTABLE_BUNDLE;
            }
        } else {
            // Current value was null and can therefore not be used in order to find the type.
            // Check the name of the key to infer the type. This check is not needed for primitive
            // data types (boolean, double, int and long), since they can not be null.
            if (key.endsWith("double_array")) {
                return CcType.DOUBLE_ARRAY;
            }
            if (key.endsWith("int_array")) {
                return CcType.INT_ARRAY;
            }
            if (key.endsWith("long_array")) {
                return CcType.LONG_ARRAY;
            }
            if (key.endsWith("string")) {
                return CcType.STRING;
            }
            if (key.endsWith("string_array") || key.endsWith("strings")) {
                return CcType.STRING_ARRAY;
            }
            if (key.endsWith("bundle")) {
                return CcType.PERSISTABLE_BUNDLE;
            }
        }

        // Not possible to infer the type by looking at the current value or the key.
        PrintWriter errPw = getErrPrintWriter();
        errPw.println(tag + "ERROR: " + key + " has unknown type.");
        return CcType.UNKNOWN;
    }

    private String ccValueToString(String key, CcType type, PersistableBundle bundle) {
        String result;
        StringBuilder valueString = new StringBuilder();
        String typeString = type.toString();
        Object value = bundle.get(key);

        if (value == null) {
            valueString.append("null");
        } else {
            switch (type) {
                case DOUBLE_ARRAY: {
                    // Format the string representation of the int array as value1 value2......
                    double[] valueArray = (double[]) value;
                    for (int i = 0; i < valueArray.length; i++) {
                        if (i != 0) {
                            valueString.append(" ");
                        }
                        valueString.append(valueArray[i]);
                    }
                    break;
                }
                case INT_ARRAY: {
                    // Format the string representation of the int array as value1 value2......
                    int[] valueArray = (int[]) value;
                    for (int i = 0; i < valueArray.length; i++) {
                        if (i != 0) {
                            valueString.append(" ");
                        }
                        valueString.append(valueArray[i]);
                    }
                    break;
                }
                case LONG_ARRAY: {
                    // Format the string representation of the int array as value1 value2......
                    long[] valueArray = (long[]) value;
                    for (int i = 0; i < valueArray.length; i++) {
                        if (i != 0) {
                            valueString.append(" ");
                        }
                        valueString.append(valueArray[i]);
                    }
                    break;
                }
                case STRING: {
                    valueString.append("\"" + value.toString() + "\"");
                    break;
                }
                case STRING_ARRAY: {
                    // Format the string representation of the string array as "value1" "value2"....
                    String[] valueArray = (String[]) value;
                    for (int i = 0; i < valueArray.length; i++) {
                        if (i != 0) {
                            valueString.append(" ");
                        }
                        if (valueArray[i] != null) {
                            valueString.append("\"" + valueArray[i] + "\"");
                        } else {
                            valueString.append("null");
                        }
                    }
                    break;
                }
                default: {
                    valueString.append(value.toString());
                }
            }
        }
        return String.format("%-70s %-15s %s", key, typeString, valueString);
    }

    private PersistableBundle getOverrideBundle(String tag, CcType type, String key,
            ArrayList<String> valueList) {
        PrintWriter errPw = getErrPrintWriter();
        PersistableBundle bundle = new PersistableBundle();

        // First verify that a valid number of values has been provided for the type.
        switch (type) {
            case BOOLEAN:
            case DOUBLE:
            case INT:
            case LONG: {
                if (valueList.size() != 1) {
                    errPw.println(tag + "Expected 1 value for type " + type
                            + ". Found: " + valueList.size());
                    return null;
                }
                break;
            }
            case STRING: {
                if (valueList.size() > 1) {
                    errPw.println(tag + "Expected 0 or 1 values for type " + type
                            + ". Found: " + valueList.size());
                    return null;
                }
                break;
            }
        }

        // Parse the value according to type and add it to the Bundle.
        switch (type) {
            case BOOLEAN: {
                if ("true".equalsIgnoreCase(valueList.get(0))) {
                    bundle.putBoolean(key, true);
                } else if ("false".equalsIgnoreCase(valueList.get(0))) {
                    bundle.putBoolean(key, false);
                } else {
                    errPw.println(tag + "Unable to parse " + valueList.get(0) + " as a " + type);
                    return null;
                }
                break;
            }
            case DOUBLE: {
                try {
                    bundle.putDouble(key, Double.parseDouble(valueList.get(0)));
                } catch (NumberFormatException nfe) {
                    // Not a valid double
                    errPw.println(tag + "Unable to parse " + valueList.get(0) + " as a " + type);
                    return null;
                }
                break;
            }
            case DOUBLE_ARRAY: {
                double[] valueDoubleArray = null;
                if (valueList.size() > 0) {
                    valueDoubleArray = new double[valueList.size()];
                    for (int i = 0; i < valueList.size(); i++) {
                        try {
                            valueDoubleArray[i] = Double.parseDouble(valueList.get(i));
                        } catch (NumberFormatException nfe) {
                            // Not a valid double
                            errPw.println(
                                    tag + "Unable to parse " + valueList.get(i) + " as a double.");
                            return null;
                        }
                    }
                }
                bundle.putDoubleArray(key, valueDoubleArray);
                break;
            }
            case INT: {
                try {
                    bundle.putInt(key, Integer.parseInt(valueList.get(0)));
                } catch (NumberFormatException nfe) {
                    // Not a valid integer
                    errPw.println(tag + "Unable to parse " + valueList.get(0) + " as an " + type);
                    return null;
                }
                break;
            }
            case INT_ARRAY: {
                int[] valueIntArray = null;
                if (valueList.size() > 0) {
                    valueIntArray = new int[valueList.size()];
                    for (int i = 0; i < valueList.size(); i++) {
                        try {
                            valueIntArray[i] = Integer.parseInt(valueList.get(i));
                        } catch (NumberFormatException nfe) {
                            // Not a valid integer
                            errPw.println(tag
                                    + "Unable to parse " + valueList.get(i) + " as an integer.");
                            return null;
                        }
                    }
                }
                bundle.putIntArray(key, valueIntArray);
                break;
            }
            case LONG: {
                try {
                    bundle.putLong(key, Long.parseLong(valueList.get(0)));
                } catch (NumberFormatException nfe) {
                    // Not a valid long
                    errPw.println(tag + "Unable to parse " + valueList.get(0) + " as a " + type);
                    return null;
                }
                break;
            }
            case LONG_ARRAY: {
                long[] valueLongArray = null;
                if (valueList.size() > 0) {
                    valueLongArray = new long[valueList.size()];
                    for (int i = 0; i < valueList.size(); i++) {
                        try {
                            valueLongArray[i] = Long.parseLong(valueList.get(i));
                        } catch (NumberFormatException nfe) {
                            // Not a valid long
                            errPw.println(
                                    tag + "Unable to parse " + valueList.get(i) + " as a long");
                            return null;
                        }
                    }
                }
                bundle.putLongArray(key, valueLongArray);
                break;
            }
            case STRING: {
                String value = null;
                if (valueList.size() > 0) {
                    value = valueList.get(0);
                }
                bundle.putString(key, value);
                break;
            }
            case STRING_ARRAY: {
                String[] valueStringArray = null;
                if (valueList.size() > 0) {
                    valueStringArray = new String[valueList.size()];
                    valueList.toArray(valueStringArray);
                }
                bundle.putStringArray(key, valueStringArray);
                break;
            }
        }
        return bundle;
    }

    private int handleEndBlockSuppressionCommand() {
        if (!checkShellUid()) {
            return -1;
        }

        if (BlockedNumberContract.SystemContract.getBlockSuppressionStatus(mContext).isSuppressed) {
            BlockedNumberContract.SystemContract.endBlockSuppression(mContext);
        }
        return 0;
    }

    private int handleEuiccCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpEuicc();
            return 0;
        }

        switch (arg) {
            case EUICC_SET_UI_COMPONENT: {
                return handleEuiccServiceCommand();
            }
        }
        return -1;
    }

    private int handleEuiccServiceCommand() {
        String uiComponent = getNextArg();
        String packageName = getNextArg();
        if (packageName == null || uiComponent == null) {
            return -1;
        }
        EuiccUiDispatcherActivity.setTestEuiccUiComponent(packageName, uiComponent);
        if (VDBG) {
            Log.v(LOG_TAG, "euicc set-euicc-uicomponent " + uiComponent +" "
                    + packageName);
        }
        return 0;
    }

    private int handleRestartModemCommand() {
        // Verify that the user is allowed to run the command. Only allowed in rooted device in a
        // non user build.
        if (Binder.getCallingUid() != Process.ROOT_UID || TelephonyUtils.IS_USER) {
            getErrPrintWriter().println("RestartModem: Permission denied.");
            return -1;
        }

        boolean result = TelephonyManager.getDefault().rebootRadio();
        getOutPrintWriter().println(result);

        return result ? 0 : -1;
    }

    private int handleGetImei() {
        // Verify that the user is allowed to run the command. Only allowed in rooted device in a
        // non user build.
        if (Binder.getCallingUid() != Process.ROOT_UID || TelephonyUtils.IS_USER) {
            getErrPrintWriter().println("Device IMEI: Permission denied.");
            return -1;
        }

        final long identity = Binder.clearCallingIdentity();

        String imei = null;
        String arg = getNextArg();
        if (arg != null) {
            try {
                int specifiedSlotIndex = Integer.parseInt(arg);
                imei = TelephonyManager.from(mContext).getImei(specifiedSlotIndex);
            } catch (NumberFormatException exception) {
                PrintWriter errPw = getErrPrintWriter();
                errPw.println("-s requires an integer as slot index.");
                return -1;
            }

        } else {
            imei = TelephonyManager.from(mContext).getImei();
        }
        getOutPrintWriter().println("Device IMEI: " + imei);

        Binder.restoreCallingIdentity(identity);
        return 0;
    }

    private int handleUnattendedReboot() {
        // Verify that the user is allowed to run the command. Only allowed in rooted device in a
        // non user build.
        if (Binder.getCallingUid() != Process.ROOT_UID || TelephonyUtils.IS_USER) {
            getErrPrintWriter().println("UnattendedReboot: Permission denied.");
            return -1;
        }

        int result = TelephonyManager.getDefault().prepareForUnattendedReboot();
        getOutPrintWriter().println("result: " + result);

        return result != TelephonyManager.PREPARE_UNATTENDED_REBOOT_ERROR ? 0 : -1;
    }

    private int handleGetSimSlotsMapping() {
        // Verify that the user is allowed to run the command. Only allowed in rooted device in a
        // non user build.
        if (Binder.getCallingUid() != Process.ROOT_UID || TelephonyUtils.IS_USER) {
            getErrPrintWriter().println("GetSimSlotsMapping: Permission denied.");
            return -1;
        }
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        String result = telephonyManager.getSimSlotMapping().toString();
        getOutPrintWriter().println("simSlotsMapping: " + result);

        return 0;
    }

    private int handleGbaCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpGba();
            return 0;
        }

        switch (arg) {
            case GBA_SET_SERVICE: {
                return handleGbaSetServiceCommand();
            }
            case GBA_GET_SERVICE: {
                return handleGbaGetServiceCommand();
            }
            case GBA_SET_RELEASE_TIME: {
                return handleGbaSetReleaseCommand();
            }
            case GBA_GET_RELEASE_TIME: {
                return handleGbaGetReleaseCommand();
            }
        }

        return -1;
    }

    private int getSubId(String cmd) {
        int slotId = getDefaultSlot();
        String opt = getNextOption();
        if (opt != null && opt.equals("-s")) {
            try {
                slotId = Integer.parseInt(getNextArgRequired());
            } catch (NumberFormatException e) {
                getErrPrintWriter().println(cmd + " requires an integer as a SLOT_ID.");
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            }
        }
        return SubscriptionManager.getSubscriptionId(slotId);
    }

    private int handleGbaSetServiceCommand() {
        int subId = getSubId("gba set-service");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        String packageName = getNextArg();
        try {
            if (packageName == null) {
                packageName = "";
            }
            boolean result = mInterface.setBoundGbaServiceOverride(subId, packageName);
            if (VDBG) {
                Log.v(LOG_TAG, "gba set-service -s " + subId + " "
                        + packageName + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "gba set-service " + subId + " "
                    + packageName + ", error" + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleGbaGetServiceCommand() {
        String result;

        int subId = getSubId("gba get-service");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        try {
            result = mInterface.getBoundGbaService(subId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "gba get-service -s " + subId + ", returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleGbaSetReleaseCommand() {
        //the release time value could be -1
        int subId = getRemainingArgsCount() > 1 ? getSubId("gba set-release")
                : SubscriptionManager.getDefaultSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        String intervalStr = getNextArg();
        if (intervalStr == null) {
            return -1;
        }

        try {
            int interval = Integer.parseInt(intervalStr);
            boolean result = mInterface.setGbaReleaseTimeOverride(subId, interval);
            if (VDBG) {
                Log.v(LOG_TAG, "gba set-release -s " + subId + " "
                        + intervalStr + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (NumberFormatException | RemoteException e) {
            Log.w(LOG_TAG, "gba set-release -s " + subId + " "
                    + intervalStr + ", error" + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleGbaGetReleaseCommand() {
        int subId = getSubId("gba get-release");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        int result = 0;
        try {
            result = mInterface.getGbaReleaseTime(subId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "gba get-release -s " + subId + ", returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleSingleRegistrationConfigCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpSrc();
            return 0;
        }

        switch (arg) {
            case SRC_SET_TEST_ENABLED: {
                return handleSrcSetTestEnabledCommand();
            }
            case SRC_GET_TEST_ENABLED: {
                return handleSrcGetTestEnabledCommand();
            }
            case SRC_SET_DEVICE_ENABLED: {
                return handleSrcSetDeviceEnabledCommand();
            }
            case SRC_GET_DEVICE_ENABLED: {
                return handleSrcGetDeviceEnabledCommand();
            }
            case SRC_SET_CARRIER_ENABLED: {
                return handleSrcSetCarrierEnabledCommand();
            }
            case SRC_GET_CARRIER_ENABLED: {
                return handleSrcGetCarrierEnabledCommand();
            }
            case SRC_SET_FEATURE_ENABLED: {
                return handleSrcSetFeatureValidationCommand();
            }
            case SRC_GET_FEATURE_ENABLED: {
                return handleSrcGetFeatureValidationCommand();
            }
        }

        return -1;
    }

    private int handleRcsUceCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpUce();
            return 0;
        }

        switch (arg) {
            case UCE_REMOVE_EAB_CONTACT:
                return handleRemovingEabContactCommand();
            case UCE_GET_EAB_CONTACT:
                return handleGettingEabContactCommand();
            case UCE_GET_EAB_CAPABILITY:
                return handleGettingEabCapabilityCommand();
            case UCE_GET_DEVICE_ENABLED:
                return handleUceGetDeviceEnabledCommand();
            case UCE_SET_DEVICE_ENABLED:
                return handleUceSetDeviceEnabledCommand();
            case UCE_OVERRIDE_PUBLISH_CAPS:
                return handleUceOverridePublishCaps();
            case UCE_GET_LAST_PIDF_XML:
                return handleUceGetPidfXml();
            case UCE_REMOVE_REQUEST_DISALLOWED_STATUS:
                return handleUceRemoveRequestDisallowedStatus();
            case UCE_SET_CAPABILITY_REQUEST_TIMEOUT:
                return handleUceSetCapRequestTimeout();
        }
        return -1;
    }

    private int handleRemovingEabContactCommand() {
        int subId = getSubId("uce remove-eab-contact");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        String phoneNumber = getNextArgRequired();
        if (TextUtils.isEmpty(phoneNumber)) {
            return -1;
        }
        int result = 0;
        try {
            result = mInterface.removeContactFromEab(subId, phoneNumber);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce remove-eab-contact -s " + subId + ", error " + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }

        if (VDBG) {
            Log.v(LOG_TAG, "uce remove-eab-contact -s " + subId + ", result: " + result);
        }
        return 0;
    }

    private int handleGettingEabContactCommand() {
        String phoneNumber = getNextArgRequired();
        if (TextUtils.isEmpty(phoneNumber)) {
            return -1;
        }
        String result = "";
        try {
            result = mInterface.getContactFromEab(phoneNumber);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce get-eab-contact, error " + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }

        if (VDBG) {
            Log.v(LOG_TAG, "uce get-eab-contact, result: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleGettingEabCapabilityCommand() {
        String phoneNumber = getNextArgRequired();
        if (TextUtils.isEmpty(phoneNumber)) {
            return -1;
        }
        String result = "";
        try {
            result = mInterface.getCapabilityFromEab(phoneNumber);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce get-eab-capability, error " + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }

        if (VDBG) {
            Log.v(LOG_TAG, "uce get-eab-capability, result: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleUceGetDeviceEnabledCommand() {
        boolean result = false;
        try {
            result = mInterface.getDeviceUceEnabled();
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce get-device-enabled, error " + e.getMessage());
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "uce get-device-enabled, returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleUceSetDeviceEnabledCommand() {
        String enabledStr = getNextArg();
        if (TextUtils.isEmpty(enabledStr)) {
            return -1;
        }

        try {
            boolean isEnabled = Boolean.parseBoolean(enabledStr);
            mInterface.setDeviceUceEnabled(isEnabled);
            if (VDBG) {
                Log.v(LOG_TAG, "uce set-device-enabled " + enabledStr + ", done");
            }
        } catch (NumberFormatException | RemoteException e) {
            Log.w(LOG_TAG, "uce set-device-enabled " + enabledStr + ", error " + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleUceRemoveRequestDisallowedStatus() {
        int subId = getSubId("uce remove-request-disallowed-status");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(LOG_TAG, "uce remove-request-disallowed-status, Invalid subscription ID");
            return -1;
        }
        boolean result;
        try {
            result = mInterface.removeUceRequestDisallowedStatus(subId);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce remove-request-disallowed-status, error " + e.getMessage());
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "uce remove-request-disallowed-status, returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleUceSetCapRequestTimeout() {
        int subId = getSubId("uce set-capabilities-request-timeout");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(LOG_TAG, "uce set-capabilities-request-timeout, Invalid subscription ID");
            return -1;
        }
        long timeoutAfterMs = Long.valueOf(getNextArg());
        boolean result;
        try {
            result = mInterface.setCapabilitiesRequestTimeout(subId, timeoutAfterMs);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce set-capabilities-request-timeout, error " + e.getMessage());
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "uce set-capabilities-request-timeout, returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleSrcSetTestEnabledCommand() {
        String enabledStr = getNextArg();
        if (enabledStr == null) {
            return -1;
        }

        try {
            mInterface.setRcsSingleRegistrationTestModeEnabled(Boolean.parseBoolean(enabledStr));
            if (VDBG) {
                Log.v(LOG_TAG, "src set-test-enabled " + enabledStr + ", done");
            }
            getOutPrintWriter().println("Done");
        } catch (NumberFormatException | RemoteException e) {
            Log.w(LOG_TAG, "src set-test-enabled " + enabledStr + ", error" + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSrcGetTestEnabledCommand() {
        boolean result = false;
        try {
            result = mInterface.getRcsSingleRegistrationTestModeEnabled();
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "src get-test-enabled, returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleUceOverridePublishCaps() {
        int subId = getSubId("uce override-published-caps");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }
        //uce override-published-caps [-s SLOT_ID] add|remove|clear|list [CAPABILITIES]
        String operation = getNextArgRequired();
        String caps = getNextArg();
        if (!"add".equals(operation) && !"remove".equals(operation) && !"clear".equals(operation)
                && !"list".equals(operation)) {
            getErrPrintWriter().println("Invalid operation: " + operation);
            return -1;
        }

        // add/remove requires capabilities to be specified.
        if ((!"clear".equals(operation) && !"list".equals(operation)) && TextUtils.isEmpty(caps)) {
            getErrPrintWriter().println("\"" + operation + "\" requires capabilities to be "
                    + "specified");
            return -1;
        }

        ArraySet<String> capSet = new ArraySet<>();
        if (!TextUtils.isEmpty(caps)) {
            String[] capArray = caps.split(":");
            for (String cap : capArray) {
                // Allow unknown tags to be passed in as well.
                capSet.addAll(TEST_FEATURE_TAG_MAP.getOrDefault(cap, Collections.singleton(cap)));
            }
        }

        RcsContactUceCapability result = null;
        try {
            switch (operation) {
                case "add":
                    result = mInterface.addUceRegistrationOverrideShell(subId,
                            new ArrayList<>(capSet));
                    break;
                case "remove":
                    result = mInterface.removeUceRegistrationOverrideShell(subId,
                            new ArrayList<>(capSet));
                    break;
                case "clear":
                    result = mInterface.clearUceRegistrationOverrideShell(subId);
                    break;
                case "list":
                    result = mInterface.getLatestRcsContactUceCapabilityShell(subId);
                    break;
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce override-published-caps, error " + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        } catch (ServiceSpecificException sse) {
            // Reconstruct ImsException
            ImsException imsException = new ImsException(sse.getMessage(), sse.errorCode);
            Log.w(LOG_TAG, "uce override-published-caps, error " + imsException);
            getErrPrintWriter().println("Exception: " + imsException);
            return -1;
        }
        if (result == null) {
            getErrPrintWriter().println("Service not available");
            return -1;
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleUceGetPidfXml() {
        int subId = getSubId("uce get-last-publish-pidf");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        String result;
        try {
            result = mInterface.getLastUcePidfXmlShell(subId);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "uce get-last-publish-pidf, error " + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        } catch (ServiceSpecificException sse) {
            // Reconstruct ImsException
            ImsException imsException = new ImsException(sse.getMessage(), sse.errorCode);
            Log.w(LOG_TAG, "uce get-last-publish-pidf error " + imsException);
            getErrPrintWriter().println("Exception: " + imsException);
            return -1;
        }
        if (result == null) {
            getErrPrintWriter().println("Service not available");
            return -1;
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleSrcSetDeviceEnabledCommand() {
        String enabledStr = getNextArg();
        if (enabledStr == null) {
            return -1;
        }

        try {
            mInterface.setDeviceSingleRegistrationEnabledOverride(enabledStr);
            if (VDBG) {
                Log.v(LOG_TAG, "src set-device-enabled " + enabledStr + ", done");
            }
            getOutPrintWriter().println("Done");
        } catch (NumberFormatException | RemoteException e) {
            Log.w(LOG_TAG, "src set-device-enabled " + enabledStr + ", error" + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSrcGetDeviceEnabledCommand() {
        boolean result = false;
        try {
            result = mInterface.getDeviceSingleRegistrationEnabled();
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "src get-device-enabled, returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleSrcSetCarrierEnabledCommand() {
        //the release time value could be -1
        int subId = getRemainingArgsCount() > 1 ? getSubId("src set-carrier-enabled")
                : SubscriptionManager.getDefaultSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        String enabledStr = getNextArg();
        if (enabledStr == null) {
            return -1;
        }

        try {
            boolean result =
                    mInterface.setCarrierSingleRegistrationEnabledOverride(subId, enabledStr);
            if (VDBG) {
                Log.v(LOG_TAG, "src set-carrier-enabled -s " + subId + " "
                        + enabledStr + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (NumberFormatException | RemoteException e) {
            Log.w(LOG_TAG, "src set-carrier-enabled -s " + subId + " "
                    + enabledStr + ", error" + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSrcGetCarrierEnabledCommand() {
        int subId = getSubId("src get-carrier-enabled");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        boolean result = false;
        try {
            result = mInterface.getCarrierSingleRegistrationEnabled(subId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "src get-carrier-enabled -s " + subId + ", returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }

    private int handleSrcSetFeatureValidationCommand() {
        //the release time value could be -1
        int subId = getRemainingArgsCount() > 1 ? getSubId("src set-feature-validation")
                : SubscriptionManager.getDefaultSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        String enabledStr = getNextArg();
        if (enabledStr == null) {
            return -1;
        }

        try {
            boolean result =
                    mInterface.setImsFeatureValidationOverride(subId, enabledStr);
            if (VDBG) {
                Log.v(LOG_TAG, "src set-feature-validation -s " + subId + " "
                        + enabledStr + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (NumberFormatException | RemoteException e) {
            Log.w(LOG_TAG, "src set-feature-validation -s " + subId + " "
                    + enabledStr + ", error" + e.getMessage());
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSrcGetFeatureValidationCommand() {
        int subId = getSubId("src get-feature-validation");
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return -1;
        }

        Boolean result = false;
        try {
            result = mInterface.getImsFeatureValidationOverride(subId);
        } catch (RemoteException e) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "src get-feature-validation -s " + subId + ", returned: " + result);
        }
        getOutPrintWriter().println(result);
        return 0;
    }


    private void onHelpCallComposer() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Call composer commands");
        pw.println("  callcomposer test-mode enable|disable|query");
        pw.println("    Enables or disables test mode for call composer. In test mode, picture");
        pw.println("    upload/download from carrier servers is disabled, and operations are");
        pw.println("    performed using emulated local files instead.");
        pw.println("  callcomposer simulate-outgoing-call [subId] [UUID]");
        pw.println("    Simulates an outgoing call being placed with the picture ID as");
        pw.println("    the provided UUID. This triggers storage to the call log.");
        pw.println("  callcomposer user-setting [subId] enable|disable|query");
        pw.println("    Enables or disables the user setting for call composer, as set by");
        pw.println("    TelephonyManager#setCallComposerStatus.");
    }

    private int handleCallComposerCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpCallComposer();
            return 0;
        }

        mContext.enforceCallingPermission(Manifest.permission.MODIFY_PHONE_STATE,
                "MODIFY_PHONE_STATE required for call composer shell cmds");
        switch (arg) {
            case CALL_COMPOSER_TEST_MODE: {
                String enabledStr = getNextArg();
                if (ENABLE.equals(enabledStr)) {
                    CallComposerPictureManager.sTestMode = true;
                } else if (DISABLE.equals(enabledStr)) {
                    CallComposerPictureManager.sTestMode = false;
                } else if (QUERY.equals(enabledStr)) {
                    getOutPrintWriter().println(CallComposerPictureManager.sTestMode);
                } else {
                    onHelpCallComposer();
                    return 1;
                }
                break;
            }
            case CALL_COMPOSER_SIMULATE_CALL: {
                int subscriptionId = Integer.valueOf(getNextArg());
                String uuidString = getNextArg();
                UUID uuid = UUID.fromString(uuidString);
                CompletableFuture<Uri> storageUriFuture = new CompletableFuture<>();
                Binder.withCleanCallingIdentity(() -> {
                    CallComposerPictureManager.getInstance(mContext, subscriptionId)
                            .storeUploadedPictureToCallLog(uuid, storageUriFuture::complete);
                });
                try {
                    Uri uri = storageUriFuture.get();
                    getOutPrintWriter().println(String.valueOf(uri));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            case CALL_COMPOSER_USER_SETTING: {
                try {
                    int subscriptionId = Integer.valueOf(getNextArg());
                    String enabledStr = getNextArg();
                    if (ENABLE.equals(enabledStr)) {
                        mInterface.setCallComposerStatus(subscriptionId,
                                TelephonyManager.CALL_COMPOSER_STATUS_ON);
                    } else if (DISABLE.equals(enabledStr)) {
                        mInterface.setCallComposerStatus(subscriptionId,
                                TelephonyManager.CALL_COMPOSER_STATUS_OFF);
                    } else if (QUERY.equals(enabledStr)) {
                        getOutPrintWriter().println(mInterface.getCallComposerStatus(subscriptionId)
                                == TelephonyManager.CALL_COMPOSER_STATUS_ON);
                    } else {
                        onHelpCallComposer();
                        return 1;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace(getOutPrintWriter());
                    return 1;
                }
                break;
            }
        }
        return 0;
    }

    private int handleHasCarrierPrivilegesCommand() {
        String packageName = getNextArgRequired();

        boolean hasCarrierPrivileges;
        final long token = Binder.clearCallingIdentity();
        try {
            hasCarrierPrivileges =
                    mInterface.checkCarrierPrivilegesForPackageAnyPhone(packageName)
                            == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
        } catch (RemoteException e) {
            Log.w(LOG_TAG, HAS_CARRIER_PRIVILEGES_COMMAND + " exception", e);
            getErrPrintWriter().println("Exception: " + e.getMessage());
            return -1;
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        getOutPrintWriter().println(hasCarrierPrivileges);
        return 0;
    }

    private int handleAllowedNetworkTypesCommand(String command) {
        if (!checkShellUid()) {
            return -1;
        }

        PrintWriter errPw = getErrPrintWriter();
        String tag = command + ": ";
        String opt;
        int subId = -1;
        Log.v(LOG_TAG, command + " start");

        while ((opt = getNextOption()) != null) {
            if (opt.equals("-s")) {
                try {
                    subId = slotStringToSubId(tag, getNextArgRequired());
                    if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                        errPw.println(tag + "No valid subscription found.");
                        return -1;
                    }
                } catch (IllegalArgumentException e) {
                    // Missing slot id
                    errPw.println(tag + "SLOT_ID expected after -s.");
                    return -1;
                }
            } else {
                errPw.println(tag + "Unknown option " + opt);
                return -1;
            }
        }

        if (GET_ALLOWED_NETWORK_TYPES_FOR_USER.equals(command)) {
            return handleGetAllowedNetworkTypesCommand(subId);
        }
        if (SET_ALLOWED_NETWORK_TYPES_FOR_USER.equals(command)) {
            return handleSetAllowedNetworkTypesCommand(subId);
        }
        return -1;
    }

    private int handleGetAllowedNetworkTypesCommand(int subId) {
        PrintWriter errPw = getErrPrintWriter();

        long result = -1;
        try {
            if (mInterface != null) {
                result = mInterface.getAllowedNetworkTypesForReason(subId,
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
            } else {
                throw new IllegalStateException("telephony service is null.");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getAllowedNetworkTypesForReason RemoteException" + e);
            errPw.println(GET_ALLOWED_NETWORK_TYPES_FOR_USER + "RemoteException " + e);
            return -1;
        }

        getOutPrintWriter().println(TelephonyManager.convertNetworkTypeBitmaskToString(result));
        return 0;
    }

    private int handleSetAllowedNetworkTypesCommand(int subId) {
        PrintWriter errPw = getErrPrintWriter();

        String bitmaskString = getNextArg();
        if (TextUtils.isEmpty(bitmaskString)) {
            errPw.println(SET_ALLOWED_NETWORK_TYPES_FOR_USER + " No NETWORK_TYPES_BITMASK");
            return -1;
        }
        long allowedNetworkTypes = convertNetworkTypeBitmaskFromStringToLong(bitmaskString);
        if (allowedNetworkTypes < 0) {
            errPw.println(SET_ALLOWED_NETWORK_TYPES_FOR_USER + " No valid NETWORK_TYPES_BITMASK");
            return -1;
        }
        boolean result = false;
        try {
            if (mInterface != null) {
                result = mInterface.setAllowedNetworkTypesForReason(subId,
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, allowedNetworkTypes);
            } else {
                throw new IllegalStateException("telephony service is null.");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "setAllowedNetworkTypesForReason RemoteException" + e);
            errPw.println(SET_ALLOWED_NETWORK_TYPES_FOR_USER + " RemoteException " + e);
            return -1;
        }

        String resultMessage = SET_ALLOWED_NETWORK_TYPES_FOR_USER + " failed";
        if (result) {
            resultMessage = SET_ALLOWED_NETWORK_TYPES_FOR_USER + " completed";
        }
        getOutPrintWriter().println(resultMessage);
        return 0;
    }

    private long convertNetworkTypeBitmaskFromStringToLong(String bitmaskString) {
        if (TextUtils.isEmpty(bitmaskString)) {
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "AllowedNetworkTypes:" + bitmaskString
                            + ", length: " + bitmaskString.length());
        }
        try {
            return Long.parseLong(bitmaskString, 2);
        } catch (NumberFormatException e) {
            Log.e(LOG_TAG, "AllowedNetworkTypes: " + e);
            return -1;
        }
    }

    private int handleRadioSetModemServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String serviceName = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    serviceName = getNextArgRequired();
                    break;
                }
            }
        }

        try {
            boolean result = mInterface.setModemService(serviceName);
            if (VDBG) {
                Log.v(LOG_TAG,
                        "RadioSetModemService " + serviceName + ", result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG,
                    "RadioSetModemService: " + serviceName + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleRadioGetModemServiceCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String result;

        try {
            result = mInterface.getModemService();
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        if (VDBG) {
            Log.v(LOG_TAG, "RadioGetModemService, result = " + result);
        }
        return 0;
    }

    private int handleRadioCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpRadio();
            return 0;
        }

        switch (arg) {
            case RADIO_SET_MODEM_SERVICE:
                return handleRadioSetModemServiceCommand();

            case RADIO_GET_MODEM_SERVICE:
                return handleRadioGetModemServiceCommand();
        }

        return -1;
    }

    private int handleSetSatelliteServicePackageNameCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String serviceName = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    serviceName = getNextArgRequired();
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "handleSetSatelliteServicePackageNameCommand: serviceName="
                + serviceName);

        try {
            boolean result = mInterface.setSatelliteServicePackageName(serviceName);
            if (VDBG) {
                Log.v(LOG_TAG, "SetSatelliteServicePackageName " + serviceName
                        + ", result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "SetSatelliteServicePackageName: " + serviceName
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetSatelliteGatewayServicePackageNameCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String serviceName = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s": {
                    serviceName = getNextArgRequired();
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "handleSetSatelliteGatewayServicePackageNameCommand: serviceName="
                + serviceName);

        try {
            boolean result = mInterface.setSatelliteGatewayServicePackageName(serviceName);
            if (VDBG) {
                Log.v(LOG_TAG, "setSatelliteGatewayServicePackageName " + serviceName
                        + ", result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "setSatelliteGatewayServicePackageName: " + serviceName
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetSatellitePointingUiClassNameCommand() {
        PrintWriter errPw = getErrPrintWriter();
        String packageName = null;
        String className = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-p": {
                    packageName = getNextArgRequired();
                    break;
                }
                case "-c": {
                    className = getNextArgRequired();
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "handleSetSatellitePointingUiClassNameCommand: packageName="
                + packageName + ", className=" + className);

        try {
            boolean result = mInterface.setSatellitePointingUiClassName(packageName, className);
            if (VDBG) {
                Log.v(LOG_TAG, "setSatellitePointingUiClassName result =" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setSatellitePointingUiClassName: " + packageName
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetEmergencyCallToSatelliteHandoverType() {
        PrintWriter errPw = getErrPrintWriter();
        int handoverType = -1;
        int delaySeconds = 0;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-t": {
                    try {
                        handoverType = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("SetEmergencyCallToSatelliteHandoverType: require an integer"
                                + " for handoverType");
                        return -1;
                    }
                    break;
                }
                case "-d": {
                    try {
                        delaySeconds = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println("SetEmergencyCallToSatelliteHandoverType: require an integer"
                                + " for delaySeconds");
                        return -1;
                    }
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "handleSetEmergencyCallToSatelliteHandoverType: handoverType="
                + handoverType + ", delaySeconds=" + delaySeconds);

        try {
            boolean result =
                    mInterface.setEmergencyCallToSatelliteHandoverType(handoverType, delaySeconds);
            if (VDBG) {
                Log.v(LOG_TAG, "setEmergencyCallToSatelliteHandoverType result =" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setEmergencyCallToSatelliteHandoverType: " + handoverType
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetSatelliteListeningTimeoutDuration() {
        PrintWriter errPw = getErrPrintWriter();
        long timeoutMillis = 0;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-t": {
                    timeoutMillis = Long.parseLong(getNextArgRequired());
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "handleSetSatelliteListeningTimeoutDuration: timeoutMillis="
                + timeoutMillis);

        try {
            boolean result = mInterface.setSatelliteListeningTimeoutDuration(timeoutMillis);
            if (VDBG) {
                Log.v(LOG_TAG, "setSatelliteListeningTimeoutDuration " + timeoutMillis
                        + ", result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "setSatelliteListeningTimeoutDuration: " + timeoutMillis
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetDatagramControllerTimeoutDuration() {
        PrintWriter errPw = getErrPrintWriter();
        boolean reset = false;
        int timeoutType = 0;
        long timeoutMillis = 0;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-d": {
                    timeoutMillis = Long.parseLong(getNextArgRequired());
                    break;
                }
                case "-r": {
                    reset = true;
                    break;
                }
                case "-t": {
                    timeoutType = Integer.parseInt(getNextArgRequired());
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "setDatagramControllerTimeoutDuration: timeoutMillis="
                + timeoutMillis + ", reset=" + reset + ", timeoutType=" + timeoutType);

        try {
            boolean result = mInterface.setDatagramControllerTimeoutDuration(
                    reset, timeoutType, timeoutMillis);
            if (VDBG) {
                Log.v(LOG_TAG, "setDatagramControllerTimeoutDuration " + timeoutMillis
                        + ", result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "setDatagramControllerTimeoutDuration: " + timeoutMillis
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetSatelliteControllerTimeoutDuration() {
        PrintWriter errPw = getErrPrintWriter();
        boolean reset = false;
        int timeoutType = 0;
        long timeoutMillis = 0;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-d": {
                    timeoutMillis = Long.parseLong(getNextArgRequired());
                    break;
                }
                case "-r": {
                    reset = true;
                    break;
                }
                case "-t": {
                    timeoutType = Integer.parseInt(getNextArgRequired());
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "setSatelliteControllerTimeoutDuration: timeoutMillis="
                + timeoutMillis + ", reset=" + reset + ", timeoutType=" + timeoutType);

        try {
            boolean result = mInterface.setSatelliteControllerTimeoutDuration(
                    reset, timeoutType, timeoutMillis);
            if (VDBG) {
                Log.v(LOG_TAG, "setSatelliteControllerTimeoutDuration " + timeoutMillis
                        + ", result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "setSatelliteControllerTimeoutDuration: " + timeoutMillis
                    + ", error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetShouldSendDatagramToModemInDemoMode() {
        PrintWriter errPw = getErrPrintWriter();
        String opt;
        boolean shouldSendToDemoMode;

        if ((opt = getNextArg()) == null) {
            errPw.println(
                    "adb shell cmd phone set-should-send-datagram-to-modem-in-demo-mode :"
                            + " Invalid Argument");
            return -1;
        } else {
            switch (opt) {
                case "true": {
                    shouldSendToDemoMode = true;
                    break;
                }
                case "false": {
                    shouldSendToDemoMode = false;
                    break;
                }
                default:
                    errPw.println(
                            "adb shell cmd phone set-should-send-datagram-to-modem-in-demo-mode :"
                                    + " Invalid Argument");
                    return -1;
            }
        }

        Log.d(LOG_TAG,
                "handleSetShouldSendDatagramToModemInDemoMode(" + shouldSendToDemoMode + ")");

        try {
            boolean result = mInterface.setShouldSendDatagramToModemInDemoMode(
                    shouldSendToDemoMode);
            if (VDBG) {
                Log.v(LOG_TAG, "handleSetShouldSendDatagramToModemInDemoMode returns: "
                        + result);
            }
            getOutPrintWriter().println(false);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "setShouldSendDatagramToModemInDemoMode(" + shouldSendToDemoMode
                    + "), error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetSatelliteAccessControlOverlayConfigs() {
        PrintWriter errPw = getErrPrintWriter();
        boolean reset = false;
        boolean isAllowed = false;
        String s2CellFile = null;
        long locationFreshDurationNanos = 0;
        List<String> satelliteCountryCodes = null;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-r": {
                    reset = true;
                    break;
                }
                case "-a": {
                    isAllowed = true;
                    break;
                }
                case "-f": {
                    s2CellFile = getNextArgRequired();
                    break;
                }
                case "-d": {
                    locationFreshDurationNanos = Long.parseLong(getNextArgRequired());
                    break;
                }
                case "-c": {
                    String countryCodeStr = getNextArgRequired();
                    satelliteCountryCodes = Arrays.asList(countryCodeStr.split(","));
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "handleSetSatelliteAccessControlOverlayConfigs: reset=" + reset
                + ", isAllowed=" + isAllowed + ", s2CellFile=" + s2CellFile
                + ", locationFreshDurationNanos=" + locationFreshDurationNanos
                + ", satelliteCountryCodes=" + satelliteCountryCodes);

        try {
            boolean result = mInterface.setSatelliteAccessControlOverlayConfigs(reset, isAllowed,
                    s2CellFile, locationFreshDurationNanos, satelliteCountryCodes);
            if (VDBG) {
                Log.v(LOG_TAG, "setSatelliteAccessControlOverlayConfigs result =" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setSatelliteAccessControlOverlayConfigs: ex=" + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetCountryCodes() {
        PrintWriter errPw = getErrPrintWriter();
        List<String> currentNetworkCountryCodes = new ArrayList<>();
        String locationCountryCode = null;
        long locationCountryCodeTimestampNanos = 0;
        Map<String, Long> cachedNetworkCountryCodes = new HashMap<>();
        boolean reset = false;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-r": {
                    reset = true;
                    break;
                }
                case "-n": {
                    String countryCodeStr = getNextArgRequired();
                    currentNetworkCountryCodes = Arrays.asList(countryCodeStr.split(","));
                    break;
                }
                case "-c": {
                    String cachedNetworkCountryCodeStr = getNextArgRequired();
                    cachedNetworkCountryCodes = parseStringLongMap(cachedNetworkCountryCodeStr);
                    break;
                }
                case "-l": {
                    locationCountryCode = getNextArgRequired();
                    break;
                }
                case "-t": {
                    locationCountryCodeTimestampNanos = Long.parseLong(getNextArgRequired());
                    break;
                }
            }
        }
        Log.d(LOG_TAG, "setCountryCodes: locationCountryCode="
                + locationCountryCode + ", locationCountryCodeTimestampNanos="
                + locationCountryCodeTimestampNanos + ", currentNetworkCountryCodes="
                + currentNetworkCountryCodes);

        try {
            boolean result = mInterface.setCountryCodes(reset, currentNetworkCountryCodes,
                    cachedNetworkCountryCodes, locationCountryCode,
                    locationCountryCodeTimestampNanos);
            if (VDBG) {
                Log.v(LOG_TAG, "setCountryCodes result =" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "setCountryCodes: ex=" + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleSetOemEnabledSatelliteProvisionStatus() {
        PrintWriter errPw = getErrPrintWriter();
        boolean isProvisioned = false;
        boolean reset = true;

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-p": {
                    try {
                        isProvisioned = Boolean.parseBoolean(getNextArgRequired());
                        reset = false;
                    } catch (Exception e) {
                        errPw.println("setOemEnabledSatelliteProvisionStatus requires a boolean "
                                + "after -p indicating provision status");
                        return -1;
                    }
                }
            }
        }
        Log.d(LOG_TAG, "setOemEnabledSatelliteProvisionStatus: reset=" + reset
                + ", isProvisioned=" + isProvisioned);

        try {
            boolean result = mInterface.setOemEnabledSatelliteProvisionStatus(reset, isProvisioned);
            if (VDBG) {
                Log.v(LOG_TAG, "setOemEnabledSatelliteProvisionStatus result = " + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "setOemEnabledSatelliteProvisionStatus: error = " + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    /**
     * Sample inputStr = "US,UK,CA;2,1,3"
     * Sample output: {[US,2], [UK,1], [CA,3]}
     */
    @NonNull private Map<String, Long> parseStringLongMap(@Nullable String inputStr) {
        Map<String, Long> result = new HashMap<>();
        if (!TextUtils.isEmpty(inputStr)) {
            String[] stringLongArr = inputStr.split(";");
            if (stringLongArr.length != 2) {
                Log.e(LOG_TAG, "parseStringLongMap: invalid inputStr=" + inputStr);
                return result;
            }

            String[] stringArr = stringLongArr[0].split(",");
            String[] longArr = stringLongArr[1].split(",");
            if (stringArr.length != longArr.length) {
                Log.e(LOG_TAG, "parseStringLongMap: invalid inputStr=" + inputStr);
                return result;
            }

            for (int i = 0; i < stringArr.length; i++) {
                try {
                    result.put(stringArr[i], Long.parseLong(longArr[i]));
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "parseStringLongMap: invalid inputStr=" + inputStr
                            + ", ex=" + ex);
                    return result;
                }
            }
        }
        return result;
    }

    private int handleCarrierRestrictionStatusCommand() {
        try {
            String MOCK_MODEM_SERVICE_NAME = "android.telephony.mockmodem.MockModemService";
            if (!(checkShellUid() && MOCK_MODEM_SERVICE_NAME.equalsIgnoreCase(
                    mInterface.getModemService()))) {
                Log.v(LOG_TAG,
                        "handleCarrierRestrictionStatusCommand, MockModem service check fails or "
                                + " checkShellUid fails");
                return -1;
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
        String callerInfo = getNextOption();
        CarrierAllowListInfo allowListInfo = CarrierAllowListInfo.loadInstance(mContext);
        if (TextUtils.isEmpty(callerInfo)) {
            // reset the Json content after testing
            allowListInfo.updateJsonForTest(null);
            return 0;
        }
        if (callerInfo.startsWith("--")) {
            callerInfo = callerInfo.replace("--", "");
        }
        String params[] = callerInfo.split(",");
        StringBuffer jsonStrBuffer = new StringBuffer();
        String tokens;
        for (int index = 0; index < params.length; index++) {
            tokens = convertToJsonString(index, params[index]);
            if (TextUtils.isEmpty(tokens)) {
                // received wrong format from CTS
                if (VDBG) {
                    Log.v(LOG_TAG,
                            "handleCarrierRestrictionStatusCommand, Shell command parsing error");
                }
                return -1;
            }
            jsonStrBuffer.append(tokens);
        }
        int result = allowListInfo.updateJsonForTest(jsonStrBuffer.toString());
        return result;
    }

    // set-carrier-service-package-override
    private int setCarrierServicePackageOverride() {
        PrintWriter errPw = getErrPrintWriter();
        int subId = SubscriptionManager.getDefaultSubscriptionId();

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s":
                    try {
                        subId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println(
                                "set-carrier-service-package-override requires an integer as a"
                                        + " subscription ID.");
                        return -1;
                    }
                    break;
            }
        }

        String packageName = getNextArg();
        if (packageName == null) {
            errPw.println("set-carrier-service-package-override requires a override package name.");
            return -1;
        }

        try {
            mInterface.setCarrierServicePackageOverride(
                    subId, packageName, mContext.getOpPackageName());

            if (VDBG) {
                Log.v(
                        LOG_TAG,
                        "set-carrier-service-package-override -s " + subId + " " + packageName);
            }
        } catch (RemoteException | IllegalArgumentException | IllegalStateException e) {
            Log.w(
                    LOG_TAG,
                    "set-carrier-service-package-override -s "
                            + subId
                            + " "
                            + packageName
                            + ", error"
                            + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    // clear-carrier-service-package-override
    private int clearCarrierServicePackageOverride() {
        PrintWriter errPw = getErrPrintWriter();
        int subId = SubscriptionManager.getDefaultSubscriptionId();

        String opt;
        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-s":
                    try {
                        subId = Integer.parseInt(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        errPw.println(
                                "clear-carrier-service-package-override requires an integer as a"
                                        + " subscription ID.");
                        return -1;
                    }
                    break;
            }
        }

        try {
            mInterface.setCarrierServicePackageOverride(subId, null, mContext.getOpPackageName());

            if (VDBG) {
                Log.v(LOG_TAG, "clear-carrier-service-package-override -s " + subId);
            }
        } catch (RemoteException | IllegalArgumentException | IllegalStateException e) {
            Log.w(
                    LOG_TAG,
                    "clear-carrier-service-package-override -s "
                            + subId
                            + ", error"
                            + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    private int handleDomainSelectionCommand() {
        String arg = getNextArg();
        if (arg == null) {
            onHelpDomainSelection();
            return 0;
        }

        switch (arg) {
            case DOMAIN_SELECTION_SET_SERVICE_OVERRIDE: {
                return handleDomainSelectionSetServiceOverrideCommand();
            }
            case DOMAIN_SELECTION_CLEAR_SERVICE_OVERRIDE: {
                return handleDomainSelectionClearServiceOverrideCommand();
            }
        }

        return -1;
    }

    // domainselection set-dss-override
    private int handleDomainSelectionSetServiceOverrideCommand() {
        PrintWriter errPw = getErrPrintWriter();

        String componentName = getNextArg();

        try {
            boolean result = mInterface.setDomainSelectionServiceOverride(
                    ComponentName.unflattenFromString(componentName));
            if (VDBG) {
                Log.v(LOG_TAG, "domainselection set-dss-override "
                        + componentName + ", result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (Exception e) {
            Log.w(LOG_TAG, "domainselection set-dss-override "
                    + componentName + ", error=" + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    // domainselection clear-dss-override
    private int handleDomainSelectionClearServiceOverrideCommand() {
        PrintWriter errPw = getErrPrintWriter();

        try {
            boolean result = mInterface.clearDomainSelectionServiceOverride();
            if (VDBG) {
                Log.v(LOG_TAG, "domainselection clear-dss-override result=" + result);
            }
            getOutPrintWriter().println(result);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "domainselection clear-dss-override error=" + e.getMessage());
            errPw.println("Exception: " + e.getMessage());
            return -1;
        }
        return 0;
    }

    /**
     * Building the string that can be used to build the JsonObject which supports to stub the data
     * in CarrierAllowListInfo for CTS testing. sample format is like
     * {"com.android.example":{"carrierId":"10000","callerSHA1Id":["XXXXXXXXXXXXXX"]}}
     */
    private String convertToJsonString(int index, String param) {

        String token[] = param.split(":");
        String jSonString;
        switch (index) {
            case 0:
                jSonString = "{" + QUOTES + token[1] + QUOTES + ":";
                break;
            case 1:
                jSonString =
                        "{" + QUOTES + token[0] + QUOTES + ":" + QUOTES + token[1] + QUOTES + ",";
                break;
            case 2:
                jSonString =
                        QUOTES + token[0] + QUOTES + ":" + "[" + QUOTES + token[1] + QUOTES + "]}}";
                break;
            default:
                jSonString = null;
        }
        return jSonString;
    }
}
