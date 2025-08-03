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

package android.telephony.cts;

import static android.app.AppOpsManager.OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER;
import static android.telephony.DataSpecificRegistrationInfo.LTE_ATTACH_EXTRA_INFO_CSFB_NOT_PREFERRED;
import static android.telephony.DataSpecificRegistrationInfo.LTE_ATTACH_EXTRA_INFO_NONE;
import static android.telephony.DataSpecificRegistrationInfo.LTE_ATTACH_EXTRA_INFO_SMS_ONLY;
import static android.telephony.DataSpecificRegistrationInfo.LTE_ATTACH_TYPE_COMBINED;
import static android.telephony.DataSpecificRegistrationInfo.LTE_ATTACH_TYPE_EPS_ONLY;
import static android.telephony.DataSpecificRegistrationInfo.LTE_ATTACH_TYPE_UNKNOWN;
import static android.telephony.PhoneCapability.DEVICE_NR_CAPABILITY_NSA;
import static android.telephony.PhoneCapability.DEVICE_NR_CAPABILITY_SA;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.TestUtils;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.RadioPowerState;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CallAttributes;
import android.telephony.CallForwardingInfo;
import android.telephony.CallQuality;
import android.telephony.CarrierConfigManager;
import android.telephony.CellBroadcastIdRange;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.DataThrottlingRequest;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneCapability;
import android.telephony.PhoneStateListener;
import android.telephony.PinResult;
import android.telephony.PreciseCallState;
import android.telephony.RadioAccessFamily;
import android.telephony.RadioAccessSpecifier;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ThermalMitigationRequest;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.UiccSlotInfo;
import android.telephony.UiccSlotMapping;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.data.ApnSetting;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.emergency.EmergencyNumber;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CarrierPrivilegeUtils;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestThread;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.uicc.IccUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Build, install and run the tests by running the commands below:
 *  make cts -j64
 *  cts-tradefed run cts -m CtsTelephonyTestCases --test android.telephony.cts.TelephonyManagerTest
 */
public class TelephonyManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private PackageManager mPackageManager;
    private boolean mOnCellLocationChangedCalled = false;
    private boolean mOnCellInfoChanged = false;
    private boolean mOnSignalStrengthsChanged = false;
    private boolean mServiceStateChangedCalled = false;
    private boolean mRadioRebootTriggered = false;
    private boolean mHasRadioPowerOff = false;
    private ServiceState mServiceState;
    private PhoneCapability mPhoneCapability;
    private boolean mOnPhoneCapabilityChanged = false;
    private final Object mLock = new Object();

    private CarrierConfigManager mCarrierConfigManager;
    private String mSelfPackageName;
    private String mSelfCertHash;

    private static final int WAIT_FOR_CONDITION = 3000;
    private static final int TOLERANCE = 1000;
    private static final int TIMEOUT_FOR_NETWORK_OPS = TOLERANCE * 180;

    private static final int TIMEOUT_FOR_CARRIER_STATUS_FILE_CHECK = TOLERANCE * 180;
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private PhoneStateListener mListener;
    private static ConnectivityManager mCm;
    private static final String TAG = "TelephonyManagerTest";
    private static final List<Integer> ROAMING_TYPES = Arrays.asList(
            ServiceState.ROAMING_TYPE_DOMESTIC,
            ServiceState.ROAMING_TYPE_INTERNATIONAL,
            ServiceState.ROAMING_TYPE_NOT_ROAMING,
            ServiceState.ROAMING_TYPE_UNKNOWN);
    private static final List<Integer> NETWORK_TYPES = Arrays.asList(
            TelephonyManager.NETWORK_TYPE_UNKNOWN,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_IWLAN,
            TelephonyManager.NETWORK_TYPE_LTE_CA,
            TelephonyManager.NETWORK_TYPE_NR);

    private static final int EMERGENCY_NUMBER_SOURCE_RIL_ECCLIST = 0;
    private static final Set<Integer> EMERGENCY_NUMBER_SOURCE_SET;

    private static final String PLMN_A = "123456";
    private static final String PLMN_B = "78901";
    private static final List<String> FPLMN_TEST = Arrays.asList(PLMN_A, PLMN_B);
    private static final int MAX_FPLMN_NUM = 1000;
    private static final int MIN_FPLMN_NUM = 3;

    private static final String THERMAL_MITIGATION_COMMAND_BASE = "cmd phone thermal-mitigation ";
    private static final String ALLOW_PACKAGE_SUBCOMMAND = "allow-package ";
    private static final String DISALLOW_PACKAGE_SUBCOMMAND = "disallow-package ";
    private static final String TELEPHONY_CTS_PACKAGE = "android.telephony.cts";

    private static final String TEST_FORWARD_NUMBER = "54321";
    private static final String TESTING_PLMN = "12345";

    private static final String BAD_IMSI_CERT_URL = "https:badurl.badurl:8080";
    private static final String IMSI_CERT_STRING_EPDG = "-----BEGIN CERTIFICATE-----"
            + "\nMIIDkzCCAnugAwIBAgIEJ4MVZDANBgkqhkiG9w0BAQsFADB6MQswCQYDVQQGEwJV"
            + "\nUzEOMAwGA1UECBMFVGV4YXMxDzANBgNVBAcTBklydmluZzEiMCAGA1UEChMZVmVy"
            + "\naXpvbiBEYXRhIFNlcnZpY2VzIExMQzEMMAoGA1UECxMDTk5PMRgwFgYDVQQDEw9F"
            + "\nQVAtSURFLlZaVy5DT00wHhcNMTcxMTEzMTkxMTA1WhcNMjcxMTExMTkxMTA1WjB6"
            + "\nMQswCQYDVQQGEwJVUzEOMAwGA1UECBMFVGV4YXMxDzANBgNVBAcTBklydmluZzEi"
            + "\nMCAGA1UEChMZVmVyaXpvbiBEYXRhIFNlcnZpY2VzIExMQzEMMAoGA1UECxMDTk5P"
            + "\nMRgwFgYDVQQDEw9FQVAtSURFLlZaVy5DT00wggEiMA0GCSqGSIb3DQEBAQUAA4IB"
            + "\nDwAwggEKAoIBAQCrQ28TvN0uUV/vK4YUS7+zcYMKAe5IYtDa3Wa0r64iyBSz6Eau"
            + "\nT+YHNNzCV4xMqURM5mIY6796LnmWR5jViUgrHyw0d06mLE54uUET/drn2pwhaobK"
            + "\nNVvbYzpm5W3dvext+klEgIhpRW4fR/uNUmD0O9n/5ofpg++wbvMNWEIjeTVUGPRT"
            + "\nCeVblH3tK8bKdCKjp48HtuciY7gE8LMoHhMHA1cob9VktSYTy2ABa+rKAPAaqVz4"
            + "\nL0Arlbi9INHSDNFlLvy1xE5dyYIqhRMicM2i4LCMwJnwf0tz8m7DmDxfdmC4HY2Q"
            + "\nz4VpbQOu10oRhXXrhZFkZEmqp6RYQmDRDDDtAgMBAAGjITAfMB0GA1UdDgQWBBSg"
            + "\nFA6liox07smzfITrvjSlgWkMMTANBgkqhkiG9w0BAQsFAAOCAQEAIoFKLgLfS9f1"
            + "\n0UG85rb+noaeXY0YofSY0dxFIW3rA5zjRD0kus9iyw9CfADDD305hefJ4Kq/NLAF"
            + "\n0odR4MOTan5KhXTlD9/8mZjSSeEktgCX3BbmMqKoKcaV6Oo9C0RfwGccDms6D+Dw"
            + "\n3GkgsvKJEB8LjApzQSmDwCV9BVJsC60041cndqBxMr3RMxCkO6/sQRKyAuzx5f91"
            + "\nWn5cpYxvl4//TatSc9oeU+ootlxfXszdRPM5xqCodm6gWmxRkK6DePlhpaZ1sKdw"
            + "\nCQg/mA35Eh5ZgOpZT2YG+a8BbDRCF5gj/pu1tPt8VfApPHq6lAoitlrx1cEdJWx6"
            + "\n5JXaFrs0UA=="
            + "\n-----END CERTIFICATE-----";
    private static final String IMSI_CERT_STRING_WLAN = "-----BEGIN CERTIFICATE-----"
            + "\nMIIFbzCCBFegAwIBAgIUAz8I/cK3fILeJ9PSbi7MkN8yZBkwDQYJKoZIhvcNAQEL"
            + "\nBQAwgY0xCzAJBgNVBAYTAk5MMRIwEAYDVQQHEwlBbXN0ZXJkYW0xJTAjBgNVBAoT"
            + "\nHFZlcml6b24gRW50ZXJwcmlzZSBTb2x1dGlvbnMxEzARBgNVBAsTCkN5YmVydHJ1"
            + "\nc3QxLjAsBgNVBAMTJVZlcml6b24gUHVibGljIFN1cmVTZXJ2ZXIgQ0EgRzE0LVNI"
            + "\nQTIwHhcNMTcxMTE2MTU1NjMzWhcNMTkxMTE2MTU1NjMzWjB6MQswCQYDVQQGEwJV"
            + "\nUzEOMAwGA1UECBMFVGV4YXMxDzANBgNVBAcTBklydmluZzEiMCAGA1UEChMZVmVy"
            + "\naXpvbiBEYXRhIFNlcnZpY2VzIExMQzEMMAoGA1UECxMDTk5PMRgwFgYDVQQDEw9F"
            + "\nQVAtSURFLlZaVy5DT00wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCr"
            + "\nQ28TvN0uUV/vK4YUS7+zcYMKAe5IYtDa3Wa0r64iyBSz6EauT+YHNNzCV4xMqURM"
            + "\n5mIY6796LnmWR5jViUgrHyw0d06mLE54uUET/drn2pwhaobKNVvbYzpm5W3dvext"
            + "\n+klEgIhpRW4fR/uNUmD0O9n/5ofpg++wbvMNWEIjeTVUGPRTCeVblH3tK8bKdCKj"
            + "\np48HtuciY7gE8LMoHhMHA1cob9VktSYTy2ABa+rKAPAaqVz4L0Arlbi9INHSDNFl"
            + "\nLvy1xE5dyYIqhRMicM2i4LCMwJnwf0tz8m7DmDxfdmC4HY2Qz4VpbQOu10oRhXXr"
            + "\nhZFkZEmqp6RYQmDRDDDtAgMBAAGjggHXMIIB0zAMBgNVHRMBAf8EAjAAMEwGA1Ud"
            + "\nIARFMEMwQQYJKwYBBAGxPgEyMDQwMgYIKwYBBQUHAgEWJmh0dHBzOi8vc2VjdXJl"
            + "\nLm9tbmlyb290LmNvbS9yZXBvc2l0b3J5MIGpBggrBgEFBQcBAQSBnDCBmTAtBggr"
            + "\nBgEFBQcwAYYhaHR0cDovL3Zwc3NnMTQyLm9jc3Aub21uaXJvb3QuY29tMDMGCCsG"
            + "\nAQUFBzAChidodHRwOi8vY2FjZXJ0Lm9tbmlyb290LmNvbS92cHNzZzE0Mi5jcnQw"
            + "\nMwYIKwYBBQUHMAKGJ2h0dHA6Ly9jYWNlcnQub21uaXJvb3QuY29tL3Zwc3NnMTQy"
            + "\nLmRlcjAaBgNVHREEEzARgg9FQVAtSURFLlZaVy5DT00wDgYDVR0PAQH/BAQDAgWg"
            + "\nMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAfBgNVHSMEGDAWgBTkLbuR"
            + "\nAWUmH7R6P6MVJaTOjEQzOzA+BgNVHR8ENzA1MDOgMaAvhi1odHRwOi8vdnBzc2cx"
            + "\nNDIuY3JsLm9tbmlyb290LmNvbS92cHNzZzE0Mi5jcmwwHQYDVR0OBBYEFKAUDqWK"
            + "\njHTuybN8hOu+NKWBaQwxMA0GCSqGSIb3DQEBCwUAA4IBAQAbSrvVrdxRPLnVu6vc"
            + "\n4BiFT2gWDhZ63EyV4f877sC1iMJRFlfwWQQfHVyhGTFa8JnhbEhhTxCP+L00Q8rX"
            + "\nKbOw9ei5g2yp7OjStwhHz5T20UejjKkl7hKtMduZXxFToqhVwIpqG58Tzl/35FX4"
            + "\nu+YDPgwTX5gbpbJxpbncn9voxWGWu3AbHVvzaskfBgZfWAuJnbgq0WTEt7bGOfiI"
            + "\nelIIQe7XL6beFcdAM9C7DlgOLqpR/31LncrMC46cPA5HmfV4mnpeK/9uq0mMbUJK"
            + "\nx2vNRWONSm2UGwdb00tLsTloxeqCOMpbkBiqi/RhOlIKIOWMPojukA5+xryh2FVs"
            + "\n7bdw"
            + "\n-----END CERTIFICATE-----";

    private static final int RADIO_HAL_VERSION_1_5 = makeRadioVersion(1, 5);
    private static final int RADIO_HAL_VERSION_1_6 = makeRadioVersion(1, 6);
    private static final int RADIO_HAL_VERSION_2_0 = makeRadioVersion(2, 0);
    private static final int RADIO_HAL_VERSION_2_1 = makeRadioVersion(2, 1);
    private static final int RADIO_HAL_VERSION_2_2 = makeRadioVersion(2, 2);

    static {
        EMERGENCY_NUMBER_SOURCE_SET = new HashSet<Integer>();
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG);
        EMERGENCY_NUMBER_SOURCE_SET.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT);
    }

    private static final Set<Integer> EMERGENCY_SERVICE_CATEGORY_SET;
    static {
        EMERGENCY_SERVICE_CATEGORY_SET = new HashSet<Integer>();
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD);
        EMERGENCY_SERVICE_CATEGORY_SET.add(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC);
        EMERGENCY_SERVICE_CATEGORY_SET.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC);
    }

    private static final Map<Class<? extends CellIdentity>, List<Integer>> sNetworkTypes;
    static {
        sNetworkTypes = new ArrayMap<>();
        sNetworkTypes.put(CellIdentityGsm.class,
                Arrays.asList(new Integer[]{
                    TelephonyManager.NETWORK_TYPE_GSM,
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE}));
        sNetworkTypes.put(CellIdentityWcdma.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSPAP));
        sNetworkTypes.put(CellIdentityCdma.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyManager.NETWORK_TYPE_EVDO_0,
                        TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_EVDO_B,
                        TelephonyManager.NETWORK_TYPE_EHRPD));
        sNetworkTypes.put(CellIdentityLte.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_LTE));
        sNetworkTypes.put(CellIdentityNr.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_NR));
        sNetworkTypes.put(CellIdentityTdscdma.class,
                Arrays.asList(TelephonyManager.NETWORK_TYPE_TD_SCDMA));
    }

    /**
     * Emergency call diagnostic data configs
     */
    private static final String DROPBOX_TAG = "ecall_diagnostic_data";
    private static final String TELECOM_DUMPSYS_COMMAND = "dumpsys telecom";
    private static final String TELEPHONY_DUMPSYS_COMMAND = "dumpsys telephony.registry";
    private static final String LOGCAT_BINARY = "/system/bin/logcat";
    private static final String DIAG_ERROR_MSG = "DiagnosticDataCollector error executing cmd";
    public static final int MAX_LINES_TO_VERIFY_IN_DUMPSYS_OUTPUT = 20;
    private static final int MAX_READ_BYTES_PER_DROP_BOX_ENTRY = 5000;
    private static final int DROP_BOX_LATCH_TIMEOUT = 3000;
    private CountDownLatch mLatchForDropBox;
    private IntentFilter mDropBoxIntentFilter;

    private int mTestSub;
    private int mNetworkHalVersion;
    private int mModemHalVersion;
    private int mConfigHalVersion;
    private boolean mIsAllowedNetworkTypeChanged;
    private Map<Integer, Long> mAllowedNetworkTypesList = new HashMap<>();

    private static final String CARRIER_RESTRICTION_OPERATOR_DETAILS = "{\"com.vzw.hss"
            + ".myverizon\":{\"carrierId\":1839,"
    + "\"callerSHA1Id\":[\"C58EE7871896786F8BF70EBDB137DE10074043E9\","
    + "\"AE23A03436DF07B0CD70FE881CDA2EC1D21215D7B7B0CC68E67B67F5DF89526A\"]}}";

    private class CarrierPrivilegeChangeMonitor implements AutoCloseable {
        // CarrierPrivilegesCallback will be triggered upon registration. Filter the first callback
        // here since we really care of the *change* of carrier privileges instead of the content
        private boolean mHasSentPrivilegeChangeCallback = false;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private final TelephonyManager.CarrierPrivilegesCallback mCarrierPrivilegesCallback;

        CarrierPrivilegeChangeMonitor() {
            mCarrierPrivilegesCallback = (privilegedPackageNames, privilegedUids) -> {
                // Ignore the first callback which is triggered upon registration
                if (!mHasSentPrivilegeChangeCallback) {
                    mHasSentPrivilegeChangeCallback = true;
                    return;
                }
                mLatch.countDown();
            };

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.registerCarrierPrivilegesCallback(
                            SubscriptionManager.getSlotIndex(mTestSub),
                            getContext().getMainExecutor(),
                            mCarrierPrivilegesCallback));
        }

        public void waitForCarrierPrivilegeChanged() throws Exception {
            if (!mLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to update carrier privileges");
            }
        }

        @Override
        public void close() throws Exception {
            if(mTelephonyManager != null) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        (tm) -> tm.unregisterCarrierPrivilegesCallback(
                                mCarrierPrivilegesCallback));
            }
        }
    }

    private static class CountryChangedReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);

        @Nullable
        private Bundle mBundle;

        @Nullable
        public Bundle getExtras() {
            return mBundle;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED.equals(intent.getAction())) {
                Log.d(TAG, "testLastKnownCountryIso received ACTION_NETWORK_COUNTRY_CHANGED");
                mBundle = intent.getExtras();
                mLatch.countDown();
            }
        }

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForIntent() throws Exception {
            // Extend to wait up to 10 seconds to receive CountryChanged Intent.
            mLatch.await(10000, TimeUnit.MILLISECONDS);
        }
    }

    private static class DataEnabledListenerTest extends TelephonyCallback implements
            TelephonyCallback.DataEnabledListener {

        private final Semaphore mThermalDataOnSemaphore = new Semaphore(0);
        private final Semaphore mThermalDataOffSemaphore = new Semaphore(0);
        private boolean mEnabled = false;
        @TelephonyManager.DataEnabledChangedReason
        private int mReason = TelephonyManager.DATA_ENABLED_REASON_UNKNOWN;

        @Override
        public void onDataEnabledChanged(boolean enabled,
                @TelephonyManager.DataEnabledChangedReason int reason) {
            Log.d(TAG, "onDataEnabledChanged: enabled=" + enabled + " reason=" + reason);
            mEnabled = enabled;
            mReason = reason;

            if (mReason == TelephonyManager.DATA_ENABLED_REASON_THERMAL) {
                releaseThermalDataSemaphores();
            }
        }

        public void releaseThermalDataSemaphores() {
            if (mEnabled) {
                try {
                    mThermalDataOnSemaphore.release();
                } catch (Exception e) {
                    Log.e(TAG, "releaseThermalDataSemaphores: Got Exception, ex=" + e);
                }
            } else {
                try {
                    mThermalDataOffSemaphore.release();
                } catch (Exception e) {
                    Log.e(TAG, "releaseThermalDataSemaphores: Got Exception, ex=" + e);
                }
            }
        }

        public boolean waitForThermalDataOn() {
            Log.d(TAG, "waitForThermalDataOn()");
            if (mReason == TelephonyManager.DATA_ENABLED_REASON_THERMAL && mEnabled) {
                return true;
            }

            try {
                if (!mThermalDataOnSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout to receive onDataEnabledChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "DataEnabledListenerTest waitForThermalDataOn: "
                        + "Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitForThermalDataOff() {
            Log.d(TAG, "waitForThermalDataOff()");
            if (mReason == TelephonyManager.DATA_ENABLED_REASON_THERMAL && !mEnabled) {
                return true;
            }

            try {
                if (!mThermalDataOffSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout to receive onDataEnabledChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "DataEnabledListenerTest waitForThermalDataOff: "
                        + "Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void clearDataEnableChanges() {
            Log.d(TAG, "clearDataEnableChanges()");
            mThermalDataOnSemaphore.drainPermits();
            mThermalDataOffSemaphore.drainPermits();
        }
    }

    @Before
    public void setUp() throws Exception {
        mCm = getContext().getSystemService(ConnectivityManager.class);
        mPackageManager = getContext().getPackageManager();
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
        mCarrierConfigManager = getContext().getSystemService(CarrierConfigManager.class);
        mSelfPackageName = getContext().getPackageName();
        mSelfCertHash = getCertHash(mSelfPackageName);
        mTestSub = SubscriptionManager.getDefaultSubscriptionId();
        // If the test subscription is invalid, TelephonyManager APIs may return null
        assumeTrue("Skipping tests because default subscription ID is invalid",
                mTestSub != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mTestSub);
        try {
            mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping tests because Telephony service is null", e);
        }
        Pair<Integer, Integer> networkHalVersion =
                mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK);
        mNetworkHalVersion = makeRadioVersion(networkHalVersion.first, networkHalVersion.second);
        Pair<Integer, Integer> modemHalVersion =
                mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_MODEM);
        mModemHalVersion = makeRadioVersion(modemHalVersion.first, modemHalVersion.second);
        Pair<Integer, Integer> simHalVersion =
                mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        mConfigHalVersion = makeRadioVersion(simHalVersion.first, simHalVersion.second);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.READ_PHONE_STATE);
        saveAllowedNetworkTypesForAllReasons();
        mLatchForDropBox = new CountDownLatch(1);
        mDropBoxIntentFilter = new IntentFilter();
        mDropBoxIntentFilter.addAction(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED);
        // Wait previously queued broadcasts to complete before starting the test
        AmUtils.waitForBroadcastBarrier();
    }

    @After
    public void tearDown() throws Exception {
        if (mListener != null) {
            // unregister the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }
        if (mIsAllowedNetworkTypeChanged) {
            recoverAllowedNetworkType();
        }

        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(THERMAL_MITIGATION_COMMAND_BASE).append(DISALLOW_PACKAGE_SUBCOMMAND)
                .append(TELEPHONY_CTS_PACKAGE);
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());
    }

    private void saveAllowedNetworkTypesForAllReasons() {
        mIsAllowedNetworkTypeChanged = false;
        if (mAllowedNetworkTypesList == null) {
            mAllowedNetworkTypesList = new HashMap<>();
        }
        long allowedNetworkTypesUser = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
        );
        long allowedNetworkTypesPower = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER)
        );
        long allowedNetworkTypesCarrier = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER)
        );
        long allowedNetworkTypesEnable2g = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G)
        );
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                allowedNetworkTypesUser);
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                allowedNetworkTypesPower);
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                allowedNetworkTypesCarrier);
        mAllowedNetworkTypesList.put(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G,
                allowedNetworkTypesEnable2g);
    }

    private void recoverAllowedNetworkType() {
        if (mAllowedNetworkTypesList == null) {
            return;
        }
        for (Integer key : mAllowedNetworkTypesList.keySet()) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            key,
                            mAllowedNetworkTypesList.get(key)));
        }
    }

    private String getCertHash(String pkgName) throws Exception {
        try {
            PackageInfo pInfo = mPackageManager.getPackageInfo(pkgName,
                    PackageManager.GET_SIGNATURES
                            | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return IccUtils.bytesToHexString(md.digest(pInfo.signatures[0].toByteArray()));
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, pkgName + " not found", ex);
            throw ex;
        } catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, "Algorithm SHA1 is not found.");
            throw ex;
        }
    }

    /** Checks whether the telephony feature is supported. */
    private boolean hasFeature(String feature) {
        return mPackageManager.hasSystemFeature(feature);
    }

    @Test
    public void testHasCarrierPrivilegesViaCarrierConfigs() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mTestSub);

        try {
            assertNotNull("CarrierConfigManager#getConfigForSubId() returned null",
                    carrierConfig);
            assertFalse("CarrierConfigManager#getConfigForSubId() returned empty bundle",
                    carrierConfig.isEmpty());

            // purge the certs in carrierConfigs first
            carrierConfig.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY, new String[]{});
            changeCarrierPrivileges(false, carrierConfig);
            // verify we don't have privilege through carrierConfigs or Uicc
            assertFalse(mTelephonyManager.hasCarrierPrivileges());

            carrierConfig.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY,
                    new String[]{mSelfCertHash});

            // verify we now have privilege after adding certificate to carrierConfigs
            changeCarrierPrivileges(true, carrierConfig);
            assertTrue(mTelephonyManager.hasCarrierPrivileges());
        } finally {
            // purge the newly added certificate
            carrierConfig.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY, new String[]{});
            changeCarrierPrivileges(false, carrierConfig);
            // verify we no longer have privilege after removing certificate
            assertFalse(mTelephonyManager.hasCarrierPrivileges());
        }
    }

    private void changeCarrierPrivileges(boolean gain, PersistableBundle carrierConfig)
            throws Exception {
        if (mTelephonyManager.hasCarrierPrivileges() == gain) {
            Log.w(TAG, "Carrier privileges already " + (gain ? "granted" : "revoked"));
            return;
        }

        try(CarrierPrivilegeChangeMonitor monitor = new CarrierPrivilegeChangeMonitor()) {
            overrideCarrierConfig(carrierConfig);
            monitor.waitForCarrierPrivilegeChanged();
        }
    }

    private void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mCarrierConfigManager,
                (cm) -> cm.overrideConfig(mTestSub, bundle));
    }

    public static void grantLocationPermissions() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String packageName = getContext().getPackageName();
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_COARSE_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_FINE_LOCATION);
        uiAutomation.grantRuntimePermission(packageName, permission.ACCESS_BACKGROUND_LOCATION);
    }

    @Test
    public void testDevicePolicyApn() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // These methods aren't accessible to anything except system and phone by design, so we just
        // look for security exceptions here.
        try {
            List<ApnSetting> apns = mTelephonyManager.getDevicePolicyOverrideApns(getContext());
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }

        try {
            ApnSetting.Builder builder = new ApnSetting.Builder();

            ApnSetting setting = builder
                    .setEntryName("asdf")
                    .setApnName("asdf")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                    .build();
            int id = mTelephonyManager.addDevicePolicyOverrideApn(getContext(), setting);
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }

        try {
            ApnSetting.Builder builder = new ApnSetting.Builder();

            ApnSetting setting = builder
                    .setEntryName("asdf")
                    .setApnName("asdf")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                    .build();
            boolean success = mTelephonyManager.modifyDevicePolicyOverrideApn(
                    getContext(), 0, setting);
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testListen() throws Throwable {
        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // TODO: temp workaround, need to adjust test to for CDMA
            return;
        }

        grantLocationPermissions();

        TestThread t = new TestThread(() -> {
            Looper.prepare();
            mListener = new PhoneStateListener() {
                @Override
                public void onCellLocationChanged(CellLocation location) {
                    if (!mOnCellLocationChangedCalled) {
                        synchronized (mLock) {
                            mOnCellLocationChangedCalled = true;
                            mLock.notify();
                        }
                    }
                }
            };

            synchronized (mLock) {
                mLock.notify(); // mListener is ready
            }

            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE); // wait for mListener
        }

        // Test register
        synchronized (mLock) {
            // .listen generates an onCellLocationChanged event
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_CELL_LOCATION);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                    mOnCellLocationChangedCalled);
        }

        synchronized (mLock) {
            mOnCellLocationChangedCalled = false;
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            // Starting with Android S, this API will silently drop all requests from apps
            // targeting Android S due to unfixable limitations with the API.
            assertFalse("Test register, mOnCellLocationChangedCalled should be false.",
                    mOnCellLocationChangedCalled);
        }

        // unregister the listener
        mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        Thread.sleep(TOLERANCE);

        // Test unregister
        synchronized (mLock) {
            mOnCellLocationChangedCalled = false;
            // unregister again, to make sure doing so does not call the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            assertFalse("Test unregister, mOnCellLocationChangedCalled should be false.",
                    mOnCellLocationChangedCalled);
        }
    }

    /**
     * The getter methods here are all related to the information about the telephony.
     * These getters are related to concrete location, phone, service provider company, so
     * it's no need to get details of these information, just make sure they are in right
     * condition(>0 or not null).
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS)
    public void testTelephonyManager() {
        assertTrue(mTelephonyManager.getNetworkType() >= TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertTrue(mTelephonyManager.getPhoneType() >= TelephonyManager.PHONE_TYPE_NONE);
        assertTrue(mTelephonyManager.getSimState() >= TelephonyManager.SIM_STATE_UNKNOWN);
        assertTrue(mTelephonyManager.getDataActivity() >= TelephonyManager.DATA_ACTIVITY_NONE);
        assertTrue(mTelephonyManager.getDataState() >= TelephonyManager.DATA_DISCONNECTED);
        assertTrue(mTelephonyManager.getCallState() >= TelephonyManager.CALL_STATE_IDLE);

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); ++i) {
            assertTrue(mTelephonyManager.getSimState(i) >= TelephonyManager.SIM_STATE_UNKNOWN);
        }

        // Make sure devices without MMS service won't fail on this
        if (InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE)) {
            assertFalse(mTelephonyManager.getMmsUserAgent().isEmpty());
            assertFalse(mTelephonyManager.getMmsUAProfUrl().isEmpty());
        }

        // The following methods may return any value depending on the state of the device. Simply
        // call them to make sure they do not throw any exceptions.
        mTelephonyManager.getVoiceMailNumber();
        mTelephonyManager.getSimOperatorName();
        mTelephonyManager.getNetworkCountryIso();
        mTelephonyManager.getCellLocation();
        mTelephonyManager.getSimCarrierId();
        mTelephonyManager.getSimCarrierIdName();
        mTelephonyManager.getSimSpecificCarrierId();
        mTelephonyManager.getSimSpecificCarrierIdName();
        mTelephonyManager.getCarrierIdFromSimMccMnc();
        mTelephonyManager.isDataRoamingEnabled();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSimSerialNumber());
        mTelephonyManager.getSimOperator();
        mTelephonyManager.getSignalStrength();
        mTelephonyManager.getNetworkOperatorName();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSubscriberId());
        mTelephonyManager.getLine1Number();
        mTelephonyManager.getNetworkOperator();

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            mTelephonyManager.getPhoneAccountHandle();
        } catch (SecurityException e) {
            fail("TelephonyManager#getPhoneAccountHandle requires READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        mTelephonyManager.getSimCountryIso();
        mTelephonyManager.getVoiceMailAlphaTag();
        mTelephonyManager.isNetworkRoaming();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        mTelephonyManager.getDeviceSoftwareVersion();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceSoftwareVersion(mTelephonyManager.getSlotIndex()));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());
        if (mModemHalVersion >= RADIO_HAL_VERSION_2_1) {
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getPrimaryImei());
        }
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(mTelephonyManager.getSlotIndex()));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isManualNetworkSelectionAllowed());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getManualNetworkSelectionPlmn());

        mTelephonyManager.getPhoneCount();
        mTelephonyManager.getDataEnabled();
        mTelephonyManager.getNetworkSpecifier();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager, (tm) -> tm.getNai());
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle defaultAccount = telecomManager
                .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        mTelephonyManager.getVoicemailRingtoneUri(defaultAccount);
        mTelephonyManager.isVoicemailVibrationEnabled(defaultAccount);
        mTelephonyManager.getSubscriptionId(defaultAccount);
        mTelephonyManager.getCarrierConfig();
        mTelephonyManager.isVoiceCapable();
        mTelephonyManager.isSmsCapable();
        if (Flags.dataOnlyCellularService()) {
            mTelephonyManager.isDeviceVoiceCapable();
            mTelephonyManager.isDeviceSmsCapable();
        }
        mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isDataConnectionAllowed());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isAnyRadioPoweredOn());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.resetIms(tm.getSlotIndex()));

        // Verify TelephonyManager.getCarrierPrivilegeStatus
        List<Integer> validCarrierPrivilegeStatus = new ArrayList<>();
        validCarrierPrivilegeStatus.add(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        validCarrierPrivilegeStatus.add(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        validCarrierPrivilegeStatus.add(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED);
        validCarrierPrivilegeStatus.add(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_ERROR_LOADING_RULES);
        int carrierPrivilegeStatusResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getCarrierPrivilegeStatus(Process.myUid()));
        assertTrue(validCarrierPrivilegeStatus.contains(carrierPrivilegeStatusResult));

        // Verify TelephonyManager.getCarrierPrivilegedPackagesForAllActiveSubscriptions
        List<String> resultForGetCarrierPrivilegedApis =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        (tm) -> tm.getCarrierPrivilegedPackagesForAllActiveSubscriptions());
        assertNotNull(resultForGetCarrierPrivilegedApis);
        for (String result : resultForGetCarrierPrivilegedApis) {
            assertFalse(TextUtils.isEmpty(result));
        }

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getDefaultRespondViaMessageApplication);
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getAndUpdateDefaultRespondViaMessageApplication);

        // Verify getImei/getSubscriberId/getIccAuthentication:
        // With app ops permision USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
        // SecurityException.
        try {
            setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

            mTelephonyManager.getImei();
            if (mModemHalVersion >= RADIO_HAL_VERSION_2_1) {
                mTelephonyManager.getPrimaryImei();
            }
            mTelephonyManager.getSubscriberId();
            mTelephonyManager.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA, "");
        } finally {
            setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
        }

        // Verify getIccAuthentication:
        // With app ops permission USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
        // SecurityException.
        try {
            setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

            mTelephonyManager.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_GBA_BOOTSTRAP, "");
        } finally {
            setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
        }

        // Verify getIccAuthentication:
        // With app ops permission USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
        // SecurityException.
        try {
            setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

            mTelephonyManager.getIccAuthentication(
                    TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_GBA_NAF_KEY_EXTERNAL,
                    "");
        } finally {
            setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
        }
    }

    /**
     * The getter methods here are all related to the information about the telephony.
     * These getters are related to concrete location, phone, service provider company, so
     * it's no need to get details of these information, just make sure they are in right
     * condition(>0 or not null).
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS)
    public void testTelephonyManagerWithFeatureMapping() {

        // Telephony feature not required.
        assertTrue(mTelephonyManager.getNetworkType() >= TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertTrue(mTelephonyManager.getPhoneType() >= TelephonyManager.PHONE_TYPE_NONE);
        assertTrue(mTelephonyManager.getCallState() >= TelephonyManager.CALL_STATE_IDLE);

        // The following methods may return any value depending on the state of the device. Simply
        // call them to make sure they do not throw any exceptions.
        mTelephonyManager.getCellLocation();
        mTelephonyManager.getLine1Number();
        mTelephonyManager.getPhoneCount();
        mTelephonyManager.isVoiceCapable();
        mTelephonyManager.isSmsCapable();
        mTelephonyManager.getDeviceSoftwareVersion();

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceSoftwareVersion(mTelephonyManager.getSlotIndex()));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getPhoneAccountHandle());

        // FEATURE_TELEPHONY_DATA required.
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_DATA)) {
            assertTrue(mTelephonyManager.getDataActivity() >= TelephonyManager.DATA_ACTIVITY_NONE);
            assertTrue(mTelephonyManager.getDataState() >= TelephonyManager.DATA_DISCONNECTED);
            mTelephonyManager.isDataRoamingEnabled();
            mTelephonyManager.getDataEnabled();
            mTelephonyManager.getNetworkSpecifier();
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.isDataConnectionAllowed());
        }

        //FEATURE_TELEPHONY_RADIO_ACCESS required.
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)) {
            mTelephonyManager.getNetworkCountryIso();
            mTelephonyManager.getSignalStrength();
            mTelephonyManager.getNetworkOperatorName();
            mTelephonyManager.getNetworkOperator();
            mTelephonyManager.isNetworkRoaming();
            mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled();
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.isManualNetworkSelectionAllowed());
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getManualNetworkSelectionPlmn());
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.isAnyRadioPoweredOn());
        }

        //FEATURE_TELEPHONY_MESSAGING
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            // Make sure devices without MMS service won't fail on this
            if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE) {
                assertFalse(mTelephonyManager.getMmsUserAgent().isEmpty());
                assertFalse(mTelephonyManager.getMmsUAProfUrl().isEmpty());
            }
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    TelephonyManager::getDefaultRespondViaMessageApplication);
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    TelephonyManager::getAndUpdateDefaultRespondViaMessageApplication);
        }

        //FEATURE_TELEPHONY_CALLING required
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING)) {
            mTelephonyManager.getVoiceMailNumber();
            mTelephonyManager.getVoiceMailAlphaTag();
        }

        //FEATURE_TELEPHONY_IMS required
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_IMS)) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.resetIms(tm.getSlotIndex()));
        }

        //FEATURE_TELEPHONY_GSM required
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_GSM)) {
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getImei());
            if (mModemHalVersion >= RADIO_HAL_VERSION_2_1) {
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        (tm) -> tm.getPrimaryImei());
            }
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getImei(mTelephonyManager.getSlotIndex()));
        }

        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle defaultAccount = telecomManager
                .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING)) {
            mTelephonyManager.getVoicemailRingtoneUri(defaultAccount);
            mTelephonyManager.isVoicemailVibrationEnabled(defaultAccount);
        }
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            mTelephonyManager.getSubscriptionId(defaultAccount);
        }

        // FEATURE_TELEPHONY_SUBSCRIPTION required.
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            assertTrue(mTelephonyManager.getSimState() >= TelephonyManager.SIM_STATE_UNKNOWN);

            for (int i = 0; i < mTelephonyManager.getPhoneCount(); ++i) {
                assertTrue(mTelephonyManager.getSimState(i) >= TelephonyManager.SIM_STATE_UNKNOWN);
            }

            mTelephonyManager.getSimOperatorName();
            mTelephonyManager.getSimCarrierId();
            mTelephonyManager.getSimCarrierIdName();
            mTelephonyManager.getSimSpecificCarrierId();
            mTelephonyManager.getSimSpecificCarrierIdName();
            mTelephonyManager.getCarrierIdFromSimMccMnc();
            mTelephonyManager.getSimCountryIso();
            mTelephonyManager.getCarrierConfig();
            mTelephonyManager.getSimOperator();

            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getSimSerialNumber());
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getSubscriberId());
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getNai());

            // Verify TelephonyManager.getCarrierPrivilegeStatus
            List<Integer> validCarrierPrivilegeStatus = new ArrayList<>();
            validCarrierPrivilegeStatus.add(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
            validCarrierPrivilegeStatus.add(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
            validCarrierPrivilegeStatus.add(
                    TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED);
            validCarrierPrivilegeStatus.add(
                    TelephonyManager.CARRIER_PRIVILEGE_STATUS_ERROR_LOADING_RULES);
            int carrierPrivilegeStatusResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getCarrierPrivilegeStatus(Process.myUid()));
            assertTrue(validCarrierPrivilegeStatus.contains(carrierPrivilegeStatusResult));

            // Verify TelephonyManager.getCarrierPrivilegedPackagesForAllActiveSubscriptions
            List<String> resultForGetCarrierPrivilegedApis =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                            (tm) -> tm.getCarrierPrivilegedPackagesForAllActiveSubscriptions());
            assertNotNull(resultForGetCarrierPrivilegedApis);
            for (String result : resultForGetCarrierPrivilegedApis) {
                assertFalse(TextUtils.isEmpty(result));
            }

            // Verify getImei/getSubscriberId/getIccAuthentication:
            // With app ops permission USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
            // SecurityException.
            try {
                setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

                if (hasFeature(PackageManager.FEATURE_TELEPHONY_GSM)) {
                    mTelephonyManager.getImei();
                    if (mModemHalVersion >= RADIO_HAL_VERSION_2_1) {
                        mTelephonyManager.getPrimaryImei();
                    }
                }

                mTelephonyManager.getSubscriberId();
                mTelephonyManager.getIccAuthentication(
                        TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_EAP_AKA, "");
            } finally {
                setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
            }

            // Verify getIccAuthentication:
            // With app ops permission USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
            // SecurityException.
            try {
                setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

                mTelephonyManager.getIccAuthentication(
                        TelephonyManager.APPTYPE_USIM, TelephonyManager.AUTHTYPE_GBA_BOOTSTRAP, "");
            } finally {
                setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
            }

            // Verify getIccAuthentication:
            // With app ops permission USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, should not throw
            // SecurityException.
            try {
                setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

                mTelephonyManager.getIccAuthentication(
                        TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_GBA_NAF_KEY_EXTERNAL,
                        "");
            } finally {
                setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
            }
        }
    }

    @Test
    public void testGetCallForwarding() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        List<Integer> callForwardingReasons = new ArrayList<>();
        callForwardingReasons.add(CallForwardingInfo.REASON_UNCONDITIONAL);
        callForwardingReasons.add(CallForwardingInfo.REASON_BUSY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NO_REPLY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NOT_REACHABLE);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL_CONDITIONAL);

        Set<Integer> callForwardingErrors = new HashSet<Integer>();
        callForwardingErrors.add(TelephonyManager.CallForwardingInfoCallback
                .RESULT_ERROR_FDN_CHECK_FAILURE);
        callForwardingErrors.add(TelephonyManager.CallForwardingInfoCallback.RESULT_ERROR_UNKNOWN);
        callForwardingErrors.add(TelephonyManager.CallForwardingInfoCallback
                .RESULT_ERROR_NOT_SUPPORTED);

        for (int callForwardingReasonToGet : callForwardingReasons) {
            Log.d(TAG, "[testGetCallForwarding] callForwardingReasonToGet: "
                    + callForwardingReasonToGet);
            AtomicReference<CallForwardingInfo> receivedForwardingInfo = new AtomicReference<>();
            AtomicReference<Integer> receivedErrorCode = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            TelephonyManager.CallForwardingInfoCallback callback =
                    new TelephonyManager.CallForwardingInfoCallback() {
                        @Override
                        public void onCallForwardingInfoAvailable(CallForwardingInfo info) {
                            receivedForwardingInfo.set(info);
                            latch.countDown();
                        }

                        @Override
                        public void onError(int error) {
                            receivedErrorCode.set(error);
                            latch.countDown();
                        }
            };
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.getCallForwarding(callForwardingReasonToGet,
                            getContext().getMainExecutor(), callback));

            assertTrue(latch.await(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS));
            // Make sure only one of the callbacks gets invoked
            assertTrue((receivedForwardingInfo.get() != null) ^ (receivedErrorCode.get() != null));
            if (receivedForwardingInfo.get() != null) {
                CallForwardingInfo info = receivedForwardingInfo.get();
                assertTrue("Got reason not in expected set:" + info.getReason(),
                        callForwardingReasons.contains(info.getReason()));
                if (info.isEnabled()) {
                    assertNotNull(info.getNumber());
                    assertTrue("Got negative timeoutSeconds=" + info.getTimeoutSeconds(),
                            info.getTimeoutSeconds() >= 0);
                }
            }

            if (receivedErrorCode.get() != null) {
                assertTrue("Got code not in expected set:" + receivedErrorCode.get(),
                        callForwardingErrors.contains(receivedErrorCode.get()));
            }
        }
    }

    @Test
    public void testSetCallForwarding() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        List<Integer> callForwardingReasons = new ArrayList<>();
        callForwardingReasons.add(CallForwardingInfo.REASON_UNCONDITIONAL);
        callForwardingReasons.add(CallForwardingInfo.REASON_BUSY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NO_REPLY);
        callForwardingReasons.add(CallForwardingInfo.REASON_NOT_REACHABLE);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL);
        callForwardingReasons.add(CallForwardingInfo.REASON_ALL_CONDITIONAL);

        // Enable Call Forwarding
        for (int callForwardingReasonToEnable : callForwardingReasons) {
            CountDownLatch latch = new CountDownLatch(1);
            // Disregard success or failure; just make sure it reports back.
            Consumer<Integer> ignoringResultListener = (x) -> latch.countDown();

            final CallForwardingInfo callForwardingInfoToEnable = new CallForwardingInfo(
                    true,
                    callForwardingReasonToEnable,
                    TEST_FORWARD_NUMBER,
                    // time seconds
                    1);
            Log.d(TAG, "[testSetCallForwarding] Enable Call Forwarding. Reason: "
                    + callForwardingReasonToEnable + " Number: " + TEST_FORWARD_NUMBER
                    + " Time Seconds: 1");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallForwarding(callForwardingInfoToEnable,
                            getContext().getMainExecutor(), ignoringResultListener));
            // TODO: this takes way too long on a real network (upwards of 40s).
            // assertTrue("No response for forwarding for reason " + callForwardingReasonToEnable,
            //        latch.await(TIMEOUT_FOR_NETWORK_OPS * 3, TimeUnit.MILLISECONDS));
        }

        // Disable Call Forwarding
        for (int callForwardingReasonToDisable : callForwardingReasons) {
            CountDownLatch latch = new CountDownLatch(1);
            // Disregard success or failure; just make sure it reports back.
            Consumer<Integer> ignoringResultListener = (x) -> latch.countDown();

            final CallForwardingInfo callForwardingInfoToDisable = new CallForwardingInfo(
                    false,
                    callForwardingReasonToDisable,
                    TEST_FORWARD_NUMBER,
                    // time seconds
                    1);
            Log.d(TAG, "[testSetCallForwarding] Disable Call Forwarding. Reason: "
                    + callForwardingReasonToDisable + " Number: " + TEST_FORWARD_NUMBER
                    + " Time Seconds: 1");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallForwarding(callForwardingInfoToDisable,
                            getContext().getMainExecutor(), ignoringResultListener));
            // TODO: this takes way too long on a real network (upwards of 40s).
            //assertTrue("No response for forwarding for reason " + callForwardingReasonToDisable,
            //        latch.await(TIMEOUT_FOR_NETWORK_OPS * 3, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testGetCallWaitingStatus() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING)) {
                Log.d(TAG, "skipping test on device without FEATURE_TELEPHONY_CALLING present");
                return;
            }
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                Log.d(TAG, "skipping test on device without FEATURE_TELEPHONY present");
                return;
            }
        }

        Set<Integer> validCallWaitingStatuses = new HashSet<Integer>();
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_ENABLED);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_DISABLED);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED);
        validCallWaitingStatuses.add(TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE);

        LinkedBlockingQueue<Integer> callWaitingStatusResult = new LinkedBlockingQueue<>(1);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.getCallWaitingStatus(getContext().getMainExecutor(),
                        callWaitingStatusResult::offer));
        assertTrue(validCallWaitingStatuses.contains(
                callWaitingStatusResult.poll(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS)));
    }

    @Test
    public void testSetCallWaitingStatus() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        Set<Integer> validCallWaitingErrors = new HashSet<Integer>();
        validCallWaitingErrors.add(TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR);
        validCallWaitingErrors.add(TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED);
        validCallWaitingErrors.add(TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE);
        Executor executor = getContext().getMainExecutor();
        {
            LinkedBlockingQueue<Integer> callWaitingResult = new LinkedBlockingQueue<>(1);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallWaitingEnabled(true, executor, callWaitingResult::offer));
            Integer result = callWaitingResult.poll(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS);
            assertNotNull("Never got callback from set call waiting", result);
            if (result != TelephonyManager.CALL_WAITING_STATUS_ENABLED) {
                assertTrue("Call waiting callback got an invalid value: " + result,
                        validCallWaitingErrors.contains(result));
            }
        }

        {
            LinkedBlockingQueue<Integer> callWaitingResult = new LinkedBlockingQueue<>(1);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setCallWaitingEnabled(false, executor, callWaitingResult::offer));
            Integer result = callWaitingResult.poll(TIMEOUT_FOR_NETWORK_OPS, TimeUnit.MILLISECONDS);
            assertNotNull("Never got callback from set call waiting", result);
            if (result != TelephonyManager.CALL_WAITING_STATUS_DISABLED) {
                assertTrue("Call waiting callback got an invalid value: " + result,
                        validCallWaitingErrors.contains(result));
            }
        }
    }

    @Test
    public void testGetHalVersion() {
        Pair<Integer, Integer> halversion;
        for (int i = TelephonyManager.HAL_SERVICE_DATA;
                i <= TelephonyManager.HAL_SERVICE_VOICE; i++) {
            halversion = mTelephonyManager.getHalVersion(i);

            // The version must be valid, and the versions start with 1.0
            assertFalse("Invalid HAL Version (" + halversion + ") of service (" + i + ")",
                    halversion.first < 1 || halversion.second < 0);
        }
    }

    @Test
    public void testCreateForPhoneAccountHandle() {
        if (!mTelephonyManager.isVoiceCapable()) {
            Log.d(TAG, "Skipping test that requires config_voice_capable is true");
            return;
        }
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "Skipping test that requires DefaultDataSubscriptionId setting");
            return;
        }

        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle handle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        TelephonyManager telephonyManager = mTelephonyManager.createForPhoneAccountHandle(handle);
        String globalSubscriberId = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getSubscriberId());
        String localSubscriberId = ShellIdentityUtils.invokeMethodWithShellPermissions(
                telephonyManager, (tm) -> tm.getSubscriberId());
        assertEquals(globalSubscriberId, localSubscriberId);
    }

    @Test
    public void testCreateForPhoneAccountHandle_InvalidHandle(){
        PhoneAccountHandle handle =
                new PhoneAccountHandle(new ComponentName("com.example.foo", "bar"), "baz");
        assertNull(mTelephonyManager.createForPhoneAccountHandle(handle));
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getPhoneAccountHandle"})
    public void testGetPhoneAccountHandle() {
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        List<PhoneAccountHandle> callCapableAccounts = telecomManager
                .getCallCapablePhoneAccounts();
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            PhoneAccountHandle phoneAccountHandle = mTelephonyManager.getPhoneAccountHandle();
            assertTrue(callCapableAccounts.contains(phoneAccountHandle));
        } catch (SecurityException e) {
            fail("TelephonyManager#getPhoneAccountHandle requires READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * Tests that the phone count returned is valid.
     */
    @Test
    public void testGetPhoneCount() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        int phoneType = mTelephonyManager.getPhoneType();
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM:
            case TelephonyManager.PHONE_TYPE_CDMA:
                assertTrue("Phone count should be > 0", phoneCount > 0);
                break;
            case TelephonyManager.PHONE_TYPE_NONE:
                assertTrue("Phone count should be >= 0", phoneCount >= 0);
                break;
            default:
                throw new IllegalArgumentException("Did you add a new phone type? " + phoneType);
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI, MEID/ESN, or a valid MAC address
     * if only a WiFi device. At least one of them must be valid.
     */
    @Test
    public void testGetDeviceId() {
        String deviceId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        verifyDeviceId(deviceId);
    }

    /**
     * Tests the max number of active SIMs method
     */
    @Test
    public void testGetMaxNumberOfSimultaneouslyActiveSims() {
        int maxNum = mTelephonyManager.getMaxNumberOfSimultaneouslyActiveSims();
        assertTrue(maxNum >= 1);
    }

    /**
     * Tests that the device properly reports either a valid IMEI, MEID/ESN, or a valid MAC address
     * if only a WiFi device. At least one of them must be valid.
     */
    @Test
    public void testGetDeviceIdForSlot() {
        String deviceId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        verifyDeviceId(deviceId);
        // Also verify that no exception is thrown for any slot index (including invalid ones)
        for (int i = -1; i <= mTelephonyManager.getPhoneCount(); i++) {
            // The compiler error 'local variables referenced from a lambda expression must be final
            // or effectively final' is reported when using i, so assign it to a final variable.
            final int currI = i;
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getDeviceId(currI));
        }
    }

    private void verifyDeviceId(String deviceId) {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            // Either IMEI or MEID need to be valid.
            try {
                assertImei(deviceId);
            } catch (AssertionError e) {
                assertMeidEsn(deviceId);
            }
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            assertSerialNumber();
            assertMacAddress(getWifiMacAddress());
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            assertSerialNumber();
            assertMacAddress(getBluetoothMacAddress());
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_ETHERNET)) {
            assertTrue(mCm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET) != null);
        }
    }

    private static void assertImei(String id) {
        assertFalse("Imei should not be empty or null", TextUtils.isEmpty(id));
        // IMEI must have 15 digits.
        String imeiPattern = "[0-9]{15}";
        String invalidPattern = "[0]{15}";
        assertTrue("IMEI " + id + " does not match pattern " + imeiPattern,
                Pattern.matches(imeiPattern, id));
        assertFalse("IMEI " + id + " must not be a zero sequence" + invalidPattern,
                Pattern.matches(invalidPattern, id));
        // 15th digit must be a check digit.
        assertImeiCheckDigit(id);
    }

    private static void assertImeiCheckDigit(String deviceId) {
        int expectedCheckDigit = getLuhnCheckDigit(deviceId.substring(0, 14));
        int actualCheckDigit = Character.digit(deviceId.charAt(14), 10);
        assertEquals("Incorrect check digit for " + deviceId, expectedCheckDigit, actualCheckDigit);
    }

    /**
     * Use decimal value (0-9) to index into array to get sum of its digits
     * needed by Lunh check.
     *
     * Example: DOUBLE_DIGIT_SUM[6] = 3 because 6 * 2 = 12 => 1 + 2 = 3
     */
    private static final int[] DOUBLE_DIGIT_SUM = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Calculate the check digit by starting from the right, doubling every
     * each digit, summing all the digits including the doubled ones, and
     * finding a number to make the sum divisible by 10.
     *
     * @param deviceId not including the check digit
     * @return the check digit
     */
    private static int getLuhnCheckDigit(String deviceId) {
        int sum = 0;
        int dontDoubleModulus = deviceId.length() % 2;
        for (int i = deviceId.length() - 1; i >= 0; --i) {
            int digit = Character.digit(deviceId.charAt(i), 10);
            if (i % 2 == dontDoubleModulus) {
                sum += digit;
            } else {
                sum += DOUBLE_DIGIT_SUM[digit];
            }
        }
        sum %= 10;
        return sum == 0 ? 0 : 10 - sum;
    }

    private static void assertMeidEsn(String id) {
        // CDMA device IDs may either be a 14-hex-digit MEID or an
        // 8-hex-digit ESN.  If it's an ESN, it may not be a
        // pseudo-ESN.
        assertFalse("Meid ESN should not be empty or null", TextUtils.isEmpty(id));
        if (id.length() == 14) {
            assertMeidFormat(id);
        } else if (id.length() == 8) {
            assertHexadecimalEsnFormat(id);
        } else {
            fail("device id on CDMA must be 14-digit hex MEID or 8-digit hex ESN.");
        }
    }

    private static void assertHexadecimalEsnFormat(String deviceId) {
        String esnPattern = "[0-9a-fA-F]{8}";
        String invalidPattern = "[0]{8}";
        assertTrue("ESN hex device id " + deviceId + " does not match pattern " + esnPattern,
                Pattern.matches(esnPattern, deviceId));
        assertFalse("ESN hex device id " + deviceId + " must not be a pseudo-ESN",
                "80".equals(deviceId.substring(0, 2)));
        assertFalse("ESN hex device id " + deviceId + "must not be a zero sequence",
                Pattern.matches(invalidPattern, deviceId));
    }

    private static void assertMeidFormat(String deviceId) {
        // MEID must NOT include the check digit.
        String meidPattern = "[0-9a-fA-F]{14}";
        String invalidPattern = "[0]{14}";
        assertTrue("MEID device id " + deviceId + " does not match pattern "
                + meidPattern, Pattern.matches(meidPattern, deviceId));
        assertFalse("MEID device id " + deviceId + "must not be a zero sequence",
                Pattern.matches(invalidPattern, deviceId));
    }

    private void assertSerialNumber() {
        String serial = ShellIdentityUtils.invokeStaticMethodWithShellPermissions(
                Build::getSerial);
        assertNotNull("Non-telephony devices must have a Build.getSerial() number.",
                serial);
        assertTrue("Hardware id must be alphanumeric.",
                Pattern.matches("[0-9A-Za-z.,_-]+", serial));
    }

    private void assertMacAddress(String macAddress) {
        String macPattern = "([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}";
        assertTrue("MAC Address " + macAddress + " does not match pattern " + macPattern,
                Pattern.matches(macPattern, macAddress));
    }

    /** @return mac address which requires the WiFi system to be enabled */
    private String getWifiMacAddress() {
        WifiManager wifiManager = getContext().getSystemService(WifiManager.class);

        if (wifiManager.isWifiEnabled()) {
            return wifiManager.getConnectionInfo().getMacAddress();
        } else {
            try {
                runWithShellPermissionIdentity(() -> wifiManager.setWifiEnabled(true));

                return wifiManager.getConnectionInfo().getMacAddress();

            } finally {
                runWithShellPermissionIdentity(() -> wifiManager.setWifiEnabled(false));
            }
        }
    }

    private String getBluetoothMacAddress() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return "";
        }

        return adapter.getAddress();
    }

    private static final String ISO_COUNTRY_CODE_PATTERN = "[a-z]{2}";

    @Test
    @ApiTest(apis = "android.telephony.TelephonyManager#getNetworkCountryIso")
    public void testGetNetworkCountryIso() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        String countryCode = mTelephonyManager.getNetworkCountryIso();
        ServiceState serviceState = mTelephonyManager.getServiceState();
        if (serviceState != null && (serviceState.getState()
                == ServiceState.STATE_IN_SERVICE || serviceState.getState()
                == ServiceState.STATE_EMERGENCY_ONLY)) {
            assertTrue("Country code '" + countryCode + "' did not match "
                    + ISO_COUNTRY_CODE_PATTERN,
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
        } else {
            assertTrue("Country code could be empty when out of service",
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode)
                    || TextUtils.isEmpty(countryCode));
        }

        int[] allSubs = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (sm) -> sm.getActiveSubscriptionIdList());
        for (int i : allSubs) {
            countryCode = mTelephonyManager.getNetworkCountryIso(
                    SubscriptionManager.getSlotIndex(i));
            serviceState = mTelephonyManager.createForSubscriptionId(i).getServiceState();

            if (serviceState != null && (serviceState.getState()
                    == ServiceState.STATE_IN_SERVICE || serviceState.getState()
                    == ServiceState.STATE_EMERGENCY_ONLY)) {
                assertTrue("Country code '" + countryCode + "' did not match "
                        + ISO_COUNTRY_CODE_PATTERN + " for slot " + i,
                        Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
            } else {
                assertTrue("Country code could be empty when out of service",
                        Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode)
                        || TextUtils.isEmpty(countryCode));
            }
        }

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); i++) {
            countryCode = mTelephonyManager.getNetworkCountryIso(i);
            assertTrue("Country code must match " + ISO_COUNTRY_CODE_PATTERN + "or empty",
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode)
                    || TextUtils.isEmpty(countryCode));
        }
    }

    @Test
    public void testSetSystemSelectionChannels() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Get initial list of system selection channels if the API is available
        List<RadioAccessSpecifier> initialSpecifiers = tryGetSystemSelectionChannels();
        // TODO (b/189255895): Don't allow empty or null channels once API is enforced in U.
        boolean getAvailable = initialSpecifiers != null && !initialSpecifiers.isEmpty();
        Log.d(TAG, "getSystemSelectionChannels is " + (getAvailable ? "" : "not ") + "available.");

        List<RadioAccessSpecifier> validSpecifiers = new ArrayList<>();
        List<RadioAccessSpecifier> specifiers;
        for (int accessNetworkType : TelephonyUtils.ALL_BANDS.keySet()) {
            List<Integer> validBands = new ArrayList<>();
            for (int band : TelephonyUtils.ALL_BANDS.get(accessNetworkType)) {
                // Set each band to see which ones are supported by the modem
                RadioAccessSpecifier specifier = new RadioAccessSpecifier(
                        accessNetworkType, new int[]{band}, new int[]{});
                boolean success = trySetSystemSelectionChannels(
                        Collections.singletonList(specifier), true);
                if (success) {
                    validBands.add(band);

                    // Try calling the API that doesn't provide feedback.
                    // We have no way of knowing if it succeeds, so just make sure nothing crashes.
                    trySetSystemSelectionChannels(Collections.singletonList(specifier), false);

                    if (getAvailable) {
                        // Assert that we get back the value we set.
                        specifiers = tryGetSystemSelectionChannels();
                        assertNotNull(specifiers);
                        assertEquals(1, specifiers.size());
                        assertEquals(specifier, specifiers.get(0));
                    }
                }
            }
            if (!validBands.isEmpty()) {
                validSpecifiers.add(new RadioAccessSpecifier(accessNetworkType,
                        validBands.stream().mapToInt(i -> i).toArray(), new int[]{}));
            }
        }

        // Call setSystemSelectionChannels with an empty list and verify no error
        if (!trySetSystemSelectionChannels(Collections.emptyList(), true)) {
            // TODO (b/189255895): Reset initial system selection channels on failure
            fail("Failed to call setSystemSelectionChannels with an empty list.");
        }

        // Verify that getSystemSelectionChannels returns all valid specifiers
        specifiers = tryGetSystemSelectionChannels();
        // TODO (b/189255895): Uncomment in U after getSystemSelectionChannels is enforced
        //assertNotNull(specifiers);
        //assertEquals(specifiers.size(), validSpecifiers.size());
        //assertTrue(specifiers.containsAll(validSpecifiers));

        // Call setSystemSelectionChannels with all valid specifiers to test batch operations
        if (!trySetSystemSelectionChannels(validSpecifiers, true)) {
            // TODO (b/189255895): Reset initial system selection channels on failure
            // TODO (b/189255895): Fail once setSystemSelectionChannels is enforced properly
            Log.e(TAG, "Failed to call setSystemSelectionChannels with all valid specifiers.");
        }

        // Reset the values back to the original.
        if (getAvailable) {
            trySetSystemSelectionChannels(initialSpecifiers, true);
        }
    }

    private List<RadioAccessSpecifier> tryGetSystemSelectionChannels() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        List<RadioAccessSpecifier> channels = null;
        try {
            channels = mTelephonyManager.getSystemSelectionChannels();
        } catch (IllegalStateException ignored) {
            // TODO (b/189255895): Reset and fail in U after getSystemSelectionChannels is enforced
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return channels;
    }

    private boolean trySetSystemSelectionChannels(List<RadioAccessSpecifier> specifiers,
            boolean useCallback) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        boolean success = false;
        try {
            if (useCallback) {
                LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
                // This is a oneway binder call, meaning we may return before the permission check
                // happens. Hold shell permissions until we get a response.
                mTelephonyManager.setSystemSelectionChannels(
                        specifiers, getContext().getMainExecutor(), queue::offer);
                Boolean result = queue.poll(2000, TimeUnit.MILLISECONDS);

                // Ensure we get a result
                assertNotNull(result);
                success = result;
            } else {
                mTelephonyManager.setSystemSelectionChannels(specifiers);
                success = true;
            }
        } catch (InterruptedException e) {
            // TODO (b/189255895): Reset initial system selection channels on failure
            fail("setSystemSelectionChannels interrupted.");
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return success;
    }

    @Test
    public void testGetSimCountryIso() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        String countryCode = mTelephonyManager.getSimCountryIso();
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                !countryCode.isEmpty()) {
            assertTrue("Country code '" + countryCode + "' did not match "
                            + ISO_COUNTRY_CODE_PATTERN,
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
        } else {
            // Non-telephony may still have the property defined if it has a SIM.
        }
    }

    @Test
    public void testResetSettings() throws Exception {
        UserManager userManager = getContext().getSystemService(UserManager.class);

        boolean canChangeMobileNetworkSettings = userManager != null
                && !userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        assertTrue("Primary user must be able to configure mobile networks to pass this test",
                canChangeMobileNetworkSettings);
        boolean initialDataSetting = isDataEnabled();

        //First check permissions are correct
        try {
            mTelephonyManager.resetSettings();
            fail("TelephonyManager#resetSettings requires the"
                    + " android.Manifest.permission.NETWORK_SETTINGS permission");
        } catch (SecurityException e) {
            //expected
        }
        // and then do a reset to move data to default.
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    TelephonyManager::resetSettings,
                    android.Manifest.permission.NETWORK_SETTINGS,
                    android.Manifest.permission.MODIFY_PHONE_STATE);
        } catch (SecurityException e) {
            e.printStackTrace();
            fail(e.toString());
        }
        // This may timeout because the default is equal to the initial data setting, but there is
        // no way to definitively check what the default should be, so assume the default will be
        // set within TOLERANCE time.
        TelephonyUtils.pollUntilTrue(() -> initialDataSetting != isDataEnabled(), 5 /*times*/,
                TOLERANCE/5 /*timeout per poll*/);

        boolean defaultDataSetting = isDataEnabled();

        // set data to not the default!
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setDataEnabled(!defaultDataSetting));
        assertTrue("Data enable change didn't work",
                TelephonyUtils.pollUntilTrue(() -> defaultDataSetting != isDataEnabled(),
                        5 /*times*/, TOLERANCE/5 /*timeout per poll*/));

        // and then do a reset to move data to default again.
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    TelephonyManager::resetSettings,
                    android.Manifest.permission.NETWORK_SETTINGS,
                    android.Manifest.permission.MODIFY_PHONE_STATE);
        } catch (SecurityException e) {
            e.printStackTrace();
            fail(e.toString());
        }

        assertTrue("resetSettings did not reset default data",
                TelephonyUtils.pollUntilTrue(() -> defaultDataSetting == isDataEnabled(),
                        5 /*times*/, TOLERANCE/5 /*timeout per poll*/));
    }

    @Test
    public void testNetworkTypeMatchesDataNetworkType() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertEquals(mTelephonyManager.getDataNetworkType(),
                mTelephonyManager.getNetworkType());
    }

    @Test
    public void testNetworkTypeMatchesCellIdentity() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        ServiceState ss = mTelephonyManager.getServiceState();
        assertNotNull(ss);
        for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoList()) {
            final int networkType = nri.getAccessNetworkTechnology();
            final CellIdentity cid = nri.getCellIdentity();
            if (nri.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                assertTrue("NetworkType for WLAN transport must be IWLAN if registered or"
                        + " UNKNOWN if unregistered",
                    networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN
                            || networkType == TelephonyManager.NETWORK_TYPE_IWLAN);
                assertNull("There is no valid cell type for WLAN", cid);
                continue;
            }
            if (!nri.isRegistered() && !nri.isEmergencyEnabled()) {
                assertEquals(
                        "Network type cannot be known unless it is providing some service",
                        TelephonyManager.NETWORK_TYPE_UNKNOWN, networkType);
                assertNull(cid);
                continue;
            }

            assertEquals(nri.getTransportType(), AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri.isRegistered() || (nri.isEmergencyEnabled() && !nri.isSearching())) {
                assertNotEquals("Network type must be known if it is providing some service",
                        TelephonyManager.NETWORK_TYPE_UNKNOWN, networkType);
                assertNotNull("The cid must be known for a cell providing service", cid);
                // The network type must roughly match the CellIdentity type
                assertTrue("The network type must be valid for the current cell",
                        sNetworkTypes.get(cid.getClass()).contains(networkType));
            }
        }
    }

    @Test
    public void testGetServiceState() throws InterruptedException {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(() -> {
            Looper.prepare();

            mListener = new PhoneStateListener() {
                @Override
                public void onServiceStateChanged(ServiceState serviceState) {
                    synchronized (mLock) {
                        mServiceState = serviceState;
                        mLock.notify();
                    }
                }
            };
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE);
            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE);
        }

        // Service state changes frequently and there can be a mismatch between the current service
        // state from TelephonyManager and the slightly delayed one from the listener.
        // Retry all assertions multiple times to prevent flaky test failures.
        int retries = 5;
        for (int i = 0; i < retries; i++) {
            try {
                assertEquals(mServiceState, mTelephonyManager.getServiceState());
                // Exit if the assertion passes without an exception
                break;
            } catch (AssertionError e) {
                if (i == retries - 1) {
                    throw(e);
                }
            }
            waitForMs(100);
        }
        for (int i = 0; i < retries; i++) {
            try {
                assertServiceStateSanitization(mServiceState,
                        mTelephonyManager.getServiceState(
                                TelephonyManager.INCLUDE_LOCATION_DATA_NONE));
                // Exit if the assertion passes without an exception
                break;
            } catch (AssertionError e) {
                if (i == retries - 1) {
                    throw(e);
                }
            }
            waitForMs(100);
        }
        for (int i = 0; i < retries; i++) {
            try {
                assertServiceStateFineLocationSanitization(mServiceState,
                        mTelephonyManager.getServiceState(
                                TelephonyManager.INCLUDE_LOCATION_DATA_COARSE));
                // Exit if the assertion passes without an exception
                break;
            } catch (AssertionError e) {
                if (i == retries - 1) {
                    throw(e);
                }
            }
            waitForMs(100);
        }
        for (int i = 0; i < retries; i++) {
            try {
                assertEquals(mServiceState,
                        mTelephonyManager.getServiceState(
                                TelephonyManager.INCLUDE_LOCATION_DATA_FINE));
                // Exit if the assertion passes without an exception
                break;
            } catch (AssertionError e) {
                if (i == retries - 1) {
                    throw(e);
                }
            }
            waitForMs(100);
        }

        NetworkRegistrationInfo regInfo = mServiceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (regInfo != null) {
            DataSpecificRegistrationInfo dsri = regInfo.getDataSpecificInfo();
            if (dsri != null) {
                int lteAttachType = dsri.getLteAttachResultType();
                assertTrue(lteAttachType == LTE_ATTACH_TYPE_UNKNOWN
                        || lteAttachType == LTE_ATTACH_TYPE_EPS_ONLY
                        || lteAttachType == LTE_ATTACH_TYPE_COMBINED);

                int lteAttachExtraInfo = dsri.getLteAttachExtraInfo();
                assertTrue(lteAttachExtraInfo == LTE_ATTACH_EXTRA_INFO_NONE
                        || lteAttachExtraInfo == LTE_ATTACH_EXTRA_INFO_SMS_ONLY
                        || lteAttachExtraInfo == LTE_ATTACH_EXTRA_INFO_CSFB_NOT_PREFERRED
                        || lteAttachExtraInfo == (LTE_ATTACH_EXTRA_INFO_SMS_ONLY
                                                 | LTE_ATTACH_EXTRA_INFO_CSFB_NOT_PREFERRED));
            }
        }
    }

    private void assertServiceStateSanitization(ServiceState expectedServiceState,
            ServiceState receivedServiceState) {
        assertNotEquals(null, receivedServiceState);
        assertServiceStateFineLocationSanitization(expectedServiceState, receivedServiceState);

        assertTrue(TextUtils.isEmpty(receivedServiceState.getOperatorAlphaLong()));
        assertTrue(TextUtils.isEmpty(receivedServiceState.getOperatorAlphaShort()));
        assertTrue(TextUtils.isEmpty(receivedServiceState.getOperatorNumeric()));
    }

    private void assertServiceStateFineLocationSanitization(ServiceState expectedServiceState,
            ServiceState receivedServiceState) {
        assertNotEquals(null, receivedServiceState);

        assertEquals(expectedServiceState.getVoiceRegState(),
                receivedServiceState.getVoiceRegState());
        assertEquals(expectedServiceState.getDataRegState(),
                receivedServiceState.getDataRegState());
        assertEquals(expectedServiceState.getDataNetworkType(),
                receivedServiceState.getDataNetworkType());
        assertEquals(expectedServiceState.getDataRoaming(),
                receivedServiceState.getDataRoaming());
        assertEquals(expectedServiceState.getRilVoiceRadioTechnology(),
                receivedServiceState.getRilVoiceRadioTechnology());

        if (receivedServiceState.getNetworkRegistrationInfoList() != null) {
            for (NetworkRegistrationInfo nrs : receivedServiceState
                    .getNetworkRegistrationInfoList()) {
                assertNull(nrs.getCellIdentity());
            }
        }
    }

    @Test
    public void testGetServiceStateForInactiveSub() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        int[] allSubs  = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (sm) ->sm.getActiveSubscriptionIdList());
        // generate a subscription that is valid (>0) but inactive (not part of active subId list)
        // A simple way to do this is sum the active subIds and add 1
        int inactiveValidSub = 1;
        for (int sub : allSubs) {
            inactiveValidSub += sub;
        }

        assertNull(mTelephonyManager.createForSubscriptionId(inactiveValidSub).getServiceState());
    }

    // This test is to ensure the RAT IWLAN is not reported on WWAN transport if the device is
    // operated in AP-assisted mode.
    @Test
    @CddTest(requirement = "7.4.1/C-4-1")
    public void testIWlanServiceState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }
        String mode = SystemProperties.get("ro.telephony.iwlan_operation_mode");
        if (!mode.equals("legacy")) {
            ServiceState ss = mTelephonyManager.getServiceState();
            if (ss != null) {
                for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoList()) {
                    if (nri.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                        assertNotEquals(TelephonyManager.NETWORK_TYPE_IWLAN,
                                nri.getAccessNetworkTechnology());
                    }
                }
            }
        }
    }

    private MockPhoneCapabilityListener mMockPhoneCapabilityListener;

    private class MockPhoneCapabilityListener extends TelephonyCallback
            implements TelephonyCallback.PhoneCapabilityListener {
        @Override
        public void onPhoneCapabilityChanged(PhoneCapability capability) {
            synchronized (mLock) {
                mPhoneCapability = capability;
                mOnPhoneCapabilityChanged = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testGetPhoneCapabilityAndVerify() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY));
        }

        boolean is5gStandalone = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_telephony5gStandalone", "bool",
                        "android"));
        boolean is5gNonStandalone = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_telephony5gNonStandalone", "bool",
                        "android"));
        int[] deviceNrCapabilities = new int[0];
        if (is5gStandalone || is5gNonStandalone) {
            List<Integer> list = new ArrayList<>();
            if (is5gNonStandalone) {
                list.add(DEVICE_NR_CAPABILITY_NSA);
            }
            if (is5gStandalone) {
                list.add(DEVICE_NR_CAPABILITY_SA);
            }
            deviceNrCapabilities = list.stream().mapToInt(Integer::valueOf).toArray();
        }

        PhoneCapability phoneCapability = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getPhoneCapability());

        assertArrayEquals(deviceNrCapabilities, phoneCapability.getDeviceNrCapabilities());
    }

    @Test
    public void testGetSimLocale() throws InterruptedException {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                Log.d(TAG, "skipping test that requires Telephony");
                return;
            }
        }

        if (SubscriptionManager.getDefaultSubscriptionId()
                == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            fail("Expected SIM inserted");
        }
        Locale locale = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSimLocale());
        Log.d(TAG, "testGetSimLocale: " + locale);
        assertNotNull(locale);
    }

    /**
     * Tests that a GSM device properly reports either the correct TAC (type allocation code) or
     * null.
     * The TAC should match the first 8 digits of the IMEI.
     */
    @Test
    public void testGetTac() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_GSM));
        }

        String tac = mTelephonyManager.getTypeAllocationCode();
        String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());

        if (tac == null || imei == null) {
            return;
        }

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                assertEquals(imei.substring(0, 8), tac);
            }
        }
    }

    /**
     * Tests that a CDMA device properly reports either the correct MC (manufacturer code) or null.
     * The MC should match the first 8 digits of the MEID.
     */
    @Test
    public void testGetMc() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));
        }

        String mc = mTelephonyManager.getManufacturerCode();
        String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid());

        if (mc == null || meid == null) {
            return;
        }

        // mc and meid should either be null or supported. empty string is not expected even if
        // the device does not support mc/meid.
        assertNotEquals("", mc);
        assertNotEquals("", meid);

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                assertEquals(meid.substring(0, 8), mc);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI or null.
     */
    @Test
    public void testGetImei() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_GSM));
        }

        String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                assertImei(imei);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid Primary Imei.
     */
    @Test
    public void testGetPrimaryImei() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_GSM));
        }

        // make sure the modem supports primaryImei feature
        assumeTrue(mModemHalVersion >= RADIO_HAL_VERSION_2_1);
        String primaryImei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getPrimaryImei());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                assertImei(primaryImei);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI or null.
     */
    @Test
    public void testGetImeiForSlot() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_GSM));
        }

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); i++) {
            // The compiler error 'local variables referenced from a lambda expression must be final
            // or effectively final' is reported when using i, so assign it to a final variable.
            final int currI = i;
            String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getImei(currI));
            if (!TextUtils.isEmpty(imei)) {
                assertImei(imei);
            }
        }

        // Also verify that no exception is thrown for any slot index (including invalid ones)
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(-1));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(mTelephonyManager.getPhoneCount()));
    }

    /**
     * Verifies that {@link TelephonyManager#getRadioPowerState()} does not throw any exception
     * and returns radio on.
     */
    @Test
    public void testGetRadioPowerState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Also verify that no exception is thrown.
        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
    }

    /**
     * Verifies that {@link TelephonyManager#setCarrierDataEnabled(boolean)} does not throw any
     * exception. TODO enhance later if we have an API to get data enabled state.
     */
    @Test
    public void testSetCarrierDataEnabled() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Also verify that no exception is thrown.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCarrierDataEnabled(false));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCarrierDataEnabled(true));
    }

    /**
     * Verifies that {@link TelephonyManager#rebootModem()} does not throw any exception
     * and final radio state is radio power on.
     */
    @Test
    public void testRebootRadio() throws Throwable {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mModemHalVersion <= RADIO_HAL_VERSION_2_2) {
            Log.d(TAG,
                    "Skipping test since rebootModem is not supported/enforced until IRadio 2.3.");
            return;
        }

        TestThread t = new TestThread(() -> {
            Looper.prepare();

            mListener = new PhoneStateListener() {
                @Override
                public void onRadioPowerStateChanged(@RadioPowerState int state) {
                    synchronized (mLock) {
                        if (state == TelephonyManager.RADIO_POWER_ON && mHasRadioPowerOff) {
                            mRadioRebootTriggered = true;
                            mLock.notify();
                        } else if (state == TelephonyManager.RADIO_POWER_OFF) {
                            // reboot must go to power off
                            mHasRadioPowerOff = true;
                        }
                    }
                }
            };
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.listen(mListener,
                            PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED));
            Looper.loop();
        });

        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
        assertThat(mRadioRebootTriggered).isFalse();
        assertThat(mHasRadioPowerOff).isFalse();
        t.start();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    TelephonyManager::rebootModem);
        } catch (Exception ex) {
            //skip this test if not supported or unsuccessful (success=false)
            return;
        }

        synchronized (mLock) {
            // reboot takes longer time
            if (!mRadioRebootTriggered) {
                mLock.wait(20000);
            }
        }
        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
        assertThat(mRadioRebootTriggered).isTrue();

        if (mListener != null) {
            // unregister the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }

        // note, other telephony states might not resumes properly at this point. e.g, service state
        // might still in the transition from OOS to In service. Thus we need to wait for in
        // service state before running next tests.
        t = new TestThread(() -> {
            Looper.prepare();

            mListener = new PhoneStateListener() {
                @Override
                public void onServiceStateChanged(ServiceState serviceState) {
                    synchronized (mLock) {
                        if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                            mServiceStateChangedCalled = true;
                            mLock.notify();
                        }
                    }
                }
            };
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE));
            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            if (!mServiceStateChangedCalled) {
                mLock.wait(60000);
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.READ_PHONE_STATE);
        assertThat(mTelephonyManager.getServiceState().getState()).isEqualTo(
                ServiceState.STATE_IN_SERVICE);
    }

    /**
     * Verifies that {@link TelephonyManager#getAidForAppType(int)} does not throw any exception
     * for all supported subscription app type.
     */
    @Test
    public void testGetAidForAppType() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_SIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_CSIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_RUIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_ISIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_USIM));
    }

    /**
     * Verifies that {@link TelephonyManager#getIsimDomain()} does not throw any exception
     */
    @Test
    public void testGetIsimDomain() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getIsimDomain());
    }

    /**
     * Verifies that {@link TelephonyManager#getIsimImpu()} does not throw any exception when called
     * and has the correct permissions.
     */
    @Ignore("API moved back to @hide for Android R.")
    @Test
    public void testGetIsimImpu() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getIsimImpu);
        // Try without the correct permissions and ensure it fails.
        try {
            mTelephonyManager.getIsimImpu();
            fail();
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#getRegisteredPlmn()} provides valid
     * information.
     */
    @Test
    public void testNetworkRegistrationInfoRegisteredPlmn() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object
        ServiceState ss = mTelephonyManager.getServiceState();
        assertNotNull(ss);

        boolean hasRegistered = false;
        for (NetworkRegistrationInfo nwReg : ss.getNetworkRegistrationInfoList()) {
            if (nwReg.isRegistered()
                        && nwReg.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                hasRegistered = true;
                String plmnId = nwReg.getRegisteredPlmn();
                // CDMA doesn't have PLMN IDs. Rather than put CID|NID here, instead it will be
                // empty. It's a case that's becoming less important over time, but for now a
                // device that's only registered on CDMA needs to pass this test.
                if (nwReg.getCellIdentity() instanceof android.telephony.CellIdentityCdma) {
                    assertTrue(TextUtils.isEmpty(plmnId));
                } else {
                    assertFalse(TextUtils.isEmpty(plmnId));
                    assertTrue("PlmnId() out of range [00000 - 999999], PLMN ID=" + plmnId,
                            plmnId.matches("^[0-9]{5,6}$"));
                }
            }
        }
        assertTrue(hasRegistered);
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#isRoaming()} does not throw any
     * exception.
     */
    @Test
    public void testNetworkRegistrationInfoIsRoaming() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object
        NetworkRegistrationInfo nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        nwReg.isRoaming();
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#getRoamingType()} does not throw any
     * exception and returns valid result
     * @see ServiceState.RoamingType
     */
    @Test
    public void testNetworkRegistrationInfoGetRoamingType() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object for voice
        NetworkRegistrationInfo nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertNotNull(nwReg);
        assertThat(nwReg.getRoamingType()).isIn(ROAMING_TYPES);

        // getNetworkRegistration object for data
        // get NetworkRegistration object for voice
        nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getRoamingType()).isIn(ROAMING_TYPES);
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationInfo#getAccessNetworkTechnology()} not
     * throw any exception and returns valid result
     * @see android.telephony.Annotation.NetworkType
     */
    @Test
    public void testNetworkRegistationStateGetAccessNetworkTechnology() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // get NetworkRegistration object for voice
        NetworkRegistrationInfo nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getAccessNetworkTechnology()).isIn(NETWORK_TYPES);

        // get NetworkRegistation object for data
        nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getAccessNetworkTechnology()).isIn(NETWORK_TYPES);
    }


    /**
     * Tests that the device properly reports either a valid MEID or null.
     */
    @Test
    public void testGetMeid() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));

        String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                assertMeidEsn(meid);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid MEID or null.
     */
    @Test
    public void testGetMeidForSlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));

        SubscriptionManager sm = SubscriptionManager.from(getContext());
        List<SubscriptionInfo> subInfos = sm.getActiveSubscriptionInfoList();

        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                int slotIndex = subInfo.getSimSlotIndex();
                int subId = subInfo.getSubscriptionId();
                TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
                if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                    String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager,
                            (telephonyManager) -> telephonyManager.getMeid(slotIndex));

                    if (!TextUtils.isEmpty(meid)) {
                        assertMeidEsn(meid);
                    }
                }
            }
        }

        // Also verify that no exception is thrown for any slot index (including invalid ones)
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid(-1));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid(mTelephonyManager.getPhoneCount()));
    }

    /**
     * Tests sendDialerSpecialCode API.
     * Expects a security exception since the caller does not have carrier privileges or is not the
     * current default dialer app.
     */
    @Test
    public void testSendDialerSpecialCode() {
        try {
            mTelephonyManager.sendDialerSpecialCode("4636");
            fail("Expected SecurityException. App does not have carrier privileges or is not the "
                    + "default dialer app");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly reports the contents of EF_FPLMN or null
     */
    @Test
    public void testGetForbiddenPlmns() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        String[] plmns = mTelephonyManager.getForbiddenPlmns();

        int phoneType = mTelephonyManager.getPhoneType();
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM:
                assertNotNull("Forbidden PLMNs must be valid or an empty list!", plmns);
            case TelephonyManager.PHONE_TYPE_CDMA:
            case TelephonyManager.PHONE_TYPE_NONE:
                if (plmns == null) {
                    return;
                }
        }

        for(String plmn : plmns) {
            assertTrue(
                    "Invalid Length for PLMN-ID, must be 5 or 6! plmn=" + plmn,
                    plmn.length() >= 5 && plmn.length() <= 6);
            assertTrue(
                    "PLMNs must be strings of digits 0-9! plmn=" + plmn,
                    android.text.TextUtils.isDigitsOnly(plmn));
        }
    }

    /**
     * Tests that the device properly sets and pads the contents of EF_FPLMN
     */
    @Test
    public void testSetForbiddenPlmns() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            int numFplmnsSet = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(FPLMN_TEST));
            String[] writtenFplmns = mTelephonyManager.getForbiddenPlmns();
            assertEquals("Wrong return value for setFplmns with less than required fplmns: "
                    + numFplmnsSet, FPLMN_TEST.size(), numFplmnsSet);
            assertEquals("Wrong Fplmns content written", FPLMN_TEST, Arrays.asList(writtenFplmns));
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }

    /**
     * Tests that the device properly truncates the contents of EF_FPLMN when provided size
     * is too big.
     */
    @Test
    public void testSetForbiddenPlmnsTruncate() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            List<String> targetFplmns = new ArrayList<>();
            for (int i = 0; i < MIN_FPLMN_NUM; i++) {
                targetFplmns.add(PLMN_A);
            }
            for (int i = MIN_FPLMN_NUM; i < MAX_FPLMN_NUM; i++) {
                targetFplmns.add(PLMN_B);
            }
            int numFplmnsSet = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(targetFplmns));
            String[] writtenFplmns = mTelephonyManager.getForbiddenPlmns();
            assertTrue("Wrong return value for setFplmns with overflowing fplmns: " + numFplmnsSet,
                    numFplmnsSet < MAX_FPLMN_NUM);
            assertEquals("Number of Fplmns set does not equal number of Fplmns available",
                    numFplmnsSet, writtenFplmns.length);
            assertEquals("Wrong Fplmns content written", targetFplmns.subList(0, numFplmnsSet),
                    Arrays.asList(writtenFplmns));
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }

    /**
     * Tests that the device properly deletes the contents of EF_FPLMN
     */
    @Test
    public void testSetForbiddenPlmnsDelete() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            // Support test for empty SIM
            List<String> targetDummyFplmns = new ArrayList<>();
            for (int i = 0; i < MIN_FPLMN_NUM; i++) {
                targetDummyFplmns.add(PLMN_A);
            }
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(targetDummyFplmns));
            String[] writtenDummyFplmns = mTelephonyManager.getForbiddenPlmns();
            assertEquals(targetDummyFplmns, Arrays.asList(writtenDummyFplmns));

            List<String> targetFplmns = new ArrayList<>();
            int numFplmnsSet = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(targetFplmns));
            String[] writtenFplmns = mTelephonyManager.getForbiddenPlmns();
            assertEquals("Wrong return value for setFplmns with empty list", 0, numFplmnsSet);
            assertEquals("Wrong number of Fplmns written", 0, writtenFplmns.length);
            // TODO wait for 10 minutes or so for the FPLMNS list to grow back
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }


    /**
     * Tests that setForbiddenPlmns properly handles null input
     */
    @Test
    public void testSetForbiddenPlmnsVoid() {
        assumeTrue(supportSetFplmn());

        String[] originalFplmns = mTelephonyManager.getForbiddenPlmns();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(null));
            fail("Expected IllegalArgumentException. Null input is not allowed");
        } catch (IllegalArgumentException expected) {
        } finally {
            // Restore
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setForbiddenPlmns(Arrays.asList(originalFplmns)));
        }
    }

    @Test
    public void testGetEquivalentHomePlmns() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        List<String> plmns = mTelephonyManager.getEquivalentHomePlmns();

        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            assertEquals(0, plmns.size());
        } else {
            for (String plmn : plmns) {
                assertTrue(
                        "Invalid Length for PLMN-ID, must be 5 or 6! plmn=" + plmn,
                        plmn.length() >= 5 && plmn.length() <= 6);
                assertTrue(
                        "PLMNs must be strings of digits 0-9! plmn=" + plmn,
                        android.text.TextUtils.isDigitsOnly(plmn));
            }
        }
    }

    /**
     * Tests that the device properly reports the contents of ManualNetworkSelectionPlmn
     * The setting is not persisted selection
     */
    @Test
    public void testGetManualNetworkSelectionPlmnNonPersisted() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) return;

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeManual(
                     TESTING_PLMN/* operatorNumeric */, false /* persistSelection */));
            String plmn = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                     (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals(TESTING_PLMN, plmn);
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeAutomatic());
        }
    }

    /**
     * Tests that the device properly reports the contents of ManualNetworkSelectionPlmn
     * The setting is persisted selection
     */
    @Test
    public void testGetManualNetworkSelectionPlmnPersisted() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) return;

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeManual(
                     TESTING_PLMN/* operatorNumeric */, true /* persistSelection */));
            String plmn = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                     (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals(TESTING_PLMN, plmn);
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeAutomatic());
        }
    }

    /**
     * Verify that TelephonyManager.getCardIdForDefaultEuicc returns a positive value or either
     * UNINITIALIZED_CARD_ID or UNSUPPORTED_CARD_ID.
     */
    @Test
    public void testGetCardIdForDefaultEuicc() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_EUICC));

        int cardId = mTelephonyManager.getCardIdForDefaultEuicc();
        assertTrue("Card ID for default EUICC is not a valid value",
                cardId == TelephonyManager.UNSUPPORTED_CARD_ID
                        || cardId == TelephonyManager.UNINITIALIZED_CARD_ID
                        || cardId >= 0);
    }

    /**
     * Tests that a SecurityException is thrown when trying to access UiccCardsInfo.
     */
    @Test
    public void testGetUiccCardsInfoException() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            // Requires READ_PRIVILEGED_PHONE_STATE or carrier privileges
            List<UiccCardInfo> infos = mTelephonyManager.getUiccCardsInfo();
            fail("Expected SecurityException. App does not have carrier privileges");
        } catch (SecurityException e) {
        }
    }

    @Test
    public void testSetLine1NumberUpdatesSubscriptionInfo() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // get a random 10 digit number
        final String randomLine1Number = String.format(
                "%010d",
                (long) (10_000_000_000L * Math.random()));

        ThrowingRunnable r = () -> {
            String originalLine1Number = mTelephonyManager.getLine1Number();
            String originalAlpha = mTelephonyManager.getLine1AlphaTag();

            try {
                // Check to see whether the original Line1Number was overridden
                mTelephonyManager.setLine1NumberForDisplay(null, null);
                if (Objects.equals(originalLine1Number, mTelephonyManager.getLine1Number())) {
                    // Number wasn't overridden
                    originalLine1Number = null;
                }
                if (Objects.equals(originalAlpha, mTelephonyManager.getLine1AlphaTag())) {
                    // Alpha tag wasn't overridden
                    originalAlpha = null;
                }

                mTelephonyManager.setLine1NumberForDisplay(originalAlpha, randomLine1Number);
                final SubscriptionInfo subInfo =
                        mSubscriptionManager.getActiveSubscriptionInfo(mTestSub);
                assertEquals(randomLine1Number, subInfo.getNumber());
            } finally {
                mTelephonyManager.setLine1NumberForDisplay(originalAlpha, originalLine1Number);
            }
        };

        CarrierPrivilegeUtils.withCarrierPrivileges(
                getContext(),
                SubscriptionManager.getDefaultSubscriptionId(),
                r);
    }

    /**
     * Tests that UiccCardsInfo methods don't crash.
     */
    @Test
    public void testGetUiccCardsInfo() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // The API requires either READ_PRIVILEGED_PHONE_STATE or carrier privileges
        try {
            mTelephonyManager.getUiccCardsInfo();
            fail("Telephony#getUiccCardsInfo should throw SecurityException without "
                    + "READ_PRIVILEGED_PHONE_STATE nor carrier privileges");
        } catch (SecurityException expected) {
        }

        // With READ_PRIVILEGED_PHONE_STATE only, it should work
        List<UiccCardInfo> infos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getUiccCardsInfo());
        // test that these methods don't crash
        if (infos.size() > 0) {
            UiccCardInfo info = infos.get(0);
            info.getEid();
            info.isRemovable();
            info.isEuicc();
            info.getCardId();
            info.getPorts();
            info.getPhysicalSlotIndex();
            info.isRemovable();
        }

        // With carrier privileges only, it should also work
        try {
            CarrierPrivilegeUtils.withCarrierPrivileges(
                    getContext(),
                    SubscriptionManager.getDefaultSubscriptionId(),
                    () -> mTelephonyManager.getUiccCardsInfo());
        } catch (SecurityException se) {
            fail("TelephonyManager.getUiccCardsInfo should not throw SecurityException with "
                    + "carrier privileges");
        } catch (Exception e) {
            fail("Exception thrown when try to get carrier privileges.");
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    /**
     * Tests that the device properly reports the contents of NetworkSelectionMode
     */
    @Test
    public void testGetNetworkSelectionMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setNetworkSelectionModeAutomatic());
        } catch (Exception e) {
        }

        int networkMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getNetworkSelectionMode());

        assertEquals(TelephonyManager.NETWORK_SELECTION_MODE_AUTO, networkMode);
    }

    /**
     * Tests that the device properly sets the network selection mode to automatic.
     * Expects a security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeAutomatic() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        try {
            mTelephonyManager.setNetworkSelectionModeAutomatic();
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly asks the radio to connect to the input network and change
     * selection mode to manual.
     * Expects a security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeManual() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        try {
            mTelephonyManager.setNetworkSelectionModeManual(
                    "" /* operatorNumeric */, false /* persistSelection */);
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly check whether selection mode was manual.
     */
    @Test
    public void testIsManualNetworkSelectionAllowed() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) return;

        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isManualNetworkSelectionAllowed()));
    }

    /**
     * Tests that the device properly sets the VoNr
     */
    @Test
    public void testIsVoNrEnabled() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return;
            }
        }

        try {
            int result = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setVoNrEnabled(true));
            if (result ==  TelephonyManager.ENABLE_VONR_REQUEST_NOT_SUPPORTED) {
                return;
            }
        } catch (Exception e) {
        }

        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isVoNrEnabled()));
    }

    /**
     * Tests that a SecurityException is thrown when trying to set VoNR
     */
    @Test
    public void testSetVoNrEnabledException() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
                return;
            }
        }
        try {
            mTelephonyManager.setVoNrEnabled(true);
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Construct a CallAttributes object and test getters.
     */
    @Test
    public void testCallAttributes() {
        CallQuality cq = new CallQuality();
        PreciseCallState pcs = new PreciseCallState();
        CallAttributes ca = new CallAttributes(pcs, TelephonyManager.NETWORK_TYPE_UNKNOWN, cq);
        assertEquals(pcs, ca.getPreciseCallState());
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, ca.getNetworkType());
        assertEquals(cq, ca.getCallQuality());
    }

    /**
     * Checks that a zeroed-out default CallQuality object can be created
     */
    @Test
    public void testCallQuality() {
        CallQuality cq = new CallQuality();
        assertEquals(0, cq.getDownlinkCallQualityLevel());
        assertEquals(0, cq.getUplinkCallQualityLevel());
        assertEquals(0, cq.getCallDuration());
        assertEquals(0, cq.getNumRtpPacketsTransmitted());
        assertEquals(0, cq.getNumRtpPacketsReceived());
        assertEquals(0, cq.getNumRtpPacketsTransmittedLost());
        assertEquals(0, cq.getNumRtpPacketsNotReceived());
        assertEquals(0, cq.getAverageRelativeJitter());
        assertEquals(0, cq.getMaxRelativeJitter());
        assertEquals(0, cq.getAverageRoundTripTime());
        assertEquals(0, cq.getCodecType());
        assertEquals(false, cq.isRtpInactivityDetected());
        assertEquals(false, cq.isIncomingSilenceDetectedAtCallSetup());
        assertEquals(false, cq.isOutgoingSilenceDetectedAtCallSetup());
        assertEquals(0, cq.getNumVoiceFrames());
        assertEquals(0, cq.getNumNoDataFrames());
        assertEquals(0, cq.getNumDroppedRtpPackets());
        assertEquals(0, cq.getMinPlayoutDelayMillis());
        assertEquals(0, cq.getMaxPlayoutDelayMillis());
        assertEquals(0, cq.getNumRtpSidPacketsReceived());
        assertEquals(0, cq.getNumRtpDuplicatePackets());
    }

    /**
     * Validate CallQuality Parcel
     */
    @Test
    public void testCallQualityParcel() {
        CallQuality cq = new CallQuality.Builder()
                .setDownlinkCallQualityLevel(CallQuality.CALL_QUALITY_NOT_AVAILABLE)
                .setUplinkCallQualityLevel(CallQuality.CALL_QUALITY_NOT_AVAILABLE)
                .setCallDurationMillis(20000)
                .setNumRtpPacketsTransmitted(550)
                .setNumRtpPacketsReceived(450)
                .setNumRtpPacketsTransmittedLost(4)
                .setNumRtpPacketsNotReceived(6)
                .setAverageRelativeJitter(20)
                .setMaxRelativeJitter(30)
                .setAverageRoundTripTimeMillis(150)
                .setCodecType(0)
                .setRtpInactivityDetected(false)
                .setIncomingSilenceDetectedAtCallSetup(false)
                .setOutgoingSilenceDetectedAtCallSetup(false)
                .setNumVoiceFrames(300)
                .setNumNoDataFrames(300)
                .setNumDroppedRtpPackets(5)
                .setMinPlayoutDelayMillis(500)
                .setMaxPlayoutDelayMillis(1000)
                .setNumRtpSidPacketsReceived(300)
                .setNumRtpDuplicatePackets(0)
                .build();

        Parcel stateParcel = Parcel.obtain();
        cq.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        CallQuality parcelCq = CallQuality.CREATOR.createFromParcel(stateParcel);
        assertThat(cq).isEqualTo(parcelCq);

    }

    // Reference: packages/services/Telephony/ecc/input/eccdata.txt
    private static final Map<String, String> EMERGENCY_NUMBERS_FOR_COUNTRIES = Map.of(
            "au", "000",
            "ca", "911",
            "de", "112",
            "gb", "999",
            "in", "112",
            "jp", "110",
            "sg", "999",
            "tw", "110",
            "us", "911");

    /**
     * Tests TelephonyManager.getEmergencyNumberList.
     *
     * Also enforce country-specific emergency number in CTS.
     */
    @Test
    public void testGetEmergencyNumberList() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        Map<Integer, List<EmergencyNumber>> emergencyNumberList =
                mTelephonyManager.getEmergencyNumberList();

        assertFalse(emergencyNumberList == null);

        checkEmergencyNumberFormat(emergencyNumberList);

        int defaultSubId = mSubscriptionManager.getDefaultSubscriptionId();
        for (Map.Entry<String, String> entry : EMERGENCY_NUMBERS_FOR_COUNTRIES.entrySet()) {
            if (mTelephonyManager.getNetworkCountryIso().equals(entry.getKey())) {
                assertTrue(checkIfEmergencyNumberListHasSpecificAddress(
                        emergencyNumberList.get(defaultSubId), entry.getValue()));
            }
        }
    }

    /**
     * Tests TelephonyManager.getEmergencyNumberList(@EmergencyServiceCategories int categories).
     *
     */
    @Test
    public void testGetEmergencyNumberListForCategories() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        Map<Integer, List<EmergencyNumber>> emergencyNumberList =
                mTelephonyManager.getEmergencyNumberList(
                        EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);

        assertFalse(emergencyNumberList == null);

        checkEmergencyNumberFormat(emergencyNumberList);

        int defaultSubId = mSubscriptionManager.getDefaultSubscriptionId();
        final String country_us = "us";
        final String country_us_police_number = "911";
        if (mTelephonyManager.getNetworkCountryIso().equals(country_us)) {
            assertTrue(checkIfEmergencyNumberListHasSpecificAddress(
                    emergencyNumberList.get(defaultSubId), country_us_police_number));
        }
        for (EmergencyNumber num : emergencyNumberList.get(defaultSubId)) {
            assertTrue(num.isInEmergencyServiceCategories(
                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        }
    }

    /**
     * Tests TelephonyManager.isEmergencyNumber.
     *
     * Also enforce country-specific emergency number in CTS.
     */
    @Test
    public void testIsEmergencyNumber() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        for (Map.Entry<String, String> entry : EMERGENCY_NUMBERS_FOR_COUNTRIES.entrySet()) {
            if (mTelephonyManager.getNetworkCountryIso().equals(entry.getKey())) {
                assertTrue(mTelephonyManager.isEmergencyNumber(entry.getValue()));
            }
        }
    }

    /**
     * Tests TelephonyManager.isPotentialEmergencyNumber.
     */
    @Test
    public void testIsPotentialEmergencyNumber() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        //NOTE: TelephonyManager#isPotentialEmergencyNumber is a hidden
        //and now deprecated API (from Android-U). This test is updated to make sure we never
        //do a "potential" match, but always use "exact" matching since it can cause issues
        //in countries where regular numbers can end up being treated as emergency numbers.
        String countryIso = mTelephonyManager.getNetworkCountryIso();
        String potentialEmergencyAddress = "91112345";
        assertFalse(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isPotentialEmergencyNumber(potentialEmergencyAddress)));
    }

    /**
     * Tests TelephonyManager.setCallComposerStatus and TelephonyManager.getCallComposerStatus.
     */
    @Test
    public void testSetGetCallComposerStatus() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (hasFeature(PackageManager.FEATURE_TELEPHONY_IMS)) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_OFF));
            int status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_OFF);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_ON));
            status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_ON);
        } else {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_OFF));
            int status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_OFF);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(TelephonyManager.CALL_COMPOSER_STATUS_ON));
            status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_OFF);
        }
    }

    /**
     * Tests TelephonyManager.setCallComposerStatus and TelephonyManager.getCallComposerStatus with
     * the TelephonyManager.CALL_COMPOSER_STATUS_BUSINESS_ONLY value.
     */
    @RequiresFlagsEnabled(com.android.server.telecom.flags.Flags.FLAG_BUSINESS_CALL_COMPOSER)
    @Test
    public void testBusinessOnlyCallComposerStatus() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_IMS)) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setCallComposerStatus(
                            TelephonyManager.CALL_COMPOSER_STATUS_BUSINESS_ONLY));
            int status = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    tm -> tm.getCallComposerStatus());
            assertThat(status).isEqualTo(TelephonyManager.CALL_COMPOSER_STATUS_BUSINESS_ONLY);
        }
    }

    /**
     * Tests {@link TelephonyManager#getSupportedRadioAccessFamily()}
     */
    @Test
    public void testGetRadioAccessFamily() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        long raf = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSupportedRadioAccessFamily());
        assertThat(raf).isNotEqualTo(TelephonyManager.NETWORK_TYPE_BITMASK_UNKNOWN);
    }

    private static void assertSetOpportunisticSubSuccess(int value) {
        assertThat(value).isEqualTo(TelephonyManager.SET_OPPORTUNISTIC_SUB_SUCCESS);
    }

    private static void assertSetOpportunisticNoOpportunisticSub(int value) {
        assertThat(value).isEqualTo(
                TelephonyManager.SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE);
    }

    /**
     * Tests {@link TelephonyManager#setPreferredOpportunisticDataSubscription} and
     * {@link TelephonyManager#getPreferredOpportunisticDataSubscription}
     */
    @Test
    public void testPreferredOpportunisticDataSubscription() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        int randomSubId = 1;
        int activeSubscriptionInfoCount = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (tm) -> tm.getActiveSubscriptionInfoCount());
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 1) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 2 && activeSubscriptionInfoCount != 2) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setPreferredOpportunisticDataSubscription(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false,
                        null, null));
        // wait for the data change to take effect
        waitForMs(500);
        int subId =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        (tm) -> tm.getPreferredOpportunisticDataSubscription());
        assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSubscriptionManager,
                        (tm) -> tm.getOpportunisticSubscriptions());
        Consumer<Integer> callbackSuccess = TelephonyManagerTest::assertSetOpportunisticSubSuccess;
        Consumer<Integer> callbackNoOpSub =
                TelephonyManagerTest::assertSetOpportunisticNoOpportunisticSub;
        if (subscriptionInfoList == null || subscriptionInfoList.size() == 0) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setPreferredOpportunisticDataSubscription(randomSubId, false,
                            AsyncTask.SERIAL_EXECUTOR, callbackNoOpSub));
            // wait for the data change to take effect
            waitForMs(500);
            subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getPreferredOpportunisticDataSubscription());
            assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        } else {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setPreferredOpportunisticDataSubscription(
                            subscriptionInfoList.get(0).getSubscriptionId(), false,
                            AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
            // wait for the data change to take effect
            waitForMs(500);
            subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getPreferredOpportunisticDataSubscription());
            assertThat(subId).isEqualTo(subscriptionInfoList.get(0).getSubscriptionId());
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setPreferredOpportunisticDataSubscription(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false,
                        AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
        // wait for the data change to take effect
        waitForMs(500);
        subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getPreferredOpportunisticDataSubscription());
        assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    private static void assertUpdateAvailableNetworkSuccess(int value) {
        assertThat(value).isEqualTo(TelephonyManager.UPDATE_AVAILABLE_NETWORKS_SUCCESS);
    }

    private static void assertUpdateAvailableNetworkNoOpportunisticSub(int value) {
        assertThat(value).isEqualTo(
                TelephonyManager.UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE);
    }

    private static boolean checkIfEmergencyNumberListHasSpecificAddress(
            List<EmergencyNumber> emergencyNumberList, String address) {
        for (EmergencyNumber emergencyNumber : emergencyNumberList) {
            if (address.equals(emergencyNumber.getNumber())) {
                return true;
            }
        }
        return false;
    }

    private static void checkEmergencyNumberFormat(
            Map<Integer, List<EmergencyNumber>> emergencyNumberLists) {
        for (List<EmergencyNumber> emergencyNumberList : emergencyNumberLists.values()) {
            for (EmergencyNumber emergencyNumber : emergencyNumberList) {

                // Validate Emergency number address
                assertTrue(validateEmergencyNumberAddress(emergencyNumber.getNumber()));

                // Validate Emergency number country Iso
                assertTrue(validateEmergencyNumberCountryIso(emergencyNumber.getCountryIso()));

                // Validate Emergency number mnc
                assertTrue(validateEmergencyNumberMnc(emergencyNumber.getMnc()));

                // Validate Emergency service category list
                assertTrue(validateEmergencyServiceCategoryList(
                        emergencyNumber.getEmergencyServiceCategories()));

                // Validate Emergency number source list
                assertTrue(validateEmergencyNumberSourceList(
                        emergencyNumber.getEmergencyNumberSources()));

                // Validate Emergency URN list
                // (just verify it is not null, because the support of this field is optional)
                assertTrue(emergencyNumber.getEmergencyUrns() != null);

                // Validat Emergency call routing
                assertTrue(validateEmergencyCallRouting(
                        emergencyNumber.getEmergencyCallRouting()));

                // Valid the emergency number should be at least in a valid source.
                assertTrue(validateEmergencyNumberFromAnySource(emergencyNumber));

                // Valid the emergency number should be at least in a valid category.
                assertTrue(validateEmergencyNumberInAnyCategory(emergencyNumber));
            }

            // Validate compareTo
            assertTrue(validateEmergencyNumberCompareTo(emergencyNumberList));
        }
    }

    /**
     * Tests {@link TelephonyManager#updateAvailableNetworks}
     */
    @Test
    public void testUpdateAvailableNetworks() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        int randomSubId = 1;
        int activeSubscriptionInfoCount = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (tm) -> tm.getActiveSubscriptionInfoCount());
        boolean isOpportunisticNetworkEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isOpportunisticNetworkEnabled());

        if (!isOpportunisticNetworkEnabled) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 1) {
            return;
        }
        if (mTelephonyManager.getPhoneCount() == 2 && activeSubscriptionInfoCount != 2) {
            return;
        }

        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSubscriptionManager,
                        (tm) -> tm.getOpportunisticSubscriptions());
        List<String> mccMncs = new ArrayList<String>();
        List<Integer> bands = new ArrayList<Integer>();
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        Consumer<Integer> callbackSuccess =
                TelephonyManagerTest::assertUpdateAvailableNetworkSuccess;
        Consumer<Integer> callbackNoOpSub =
                TelephonyManagerTest::assertUpdateAvailableNetworkNoOpportunisticSub;
        if (subscriptionInfoList == null || subscriptionInfoList.size() == 0
                || !mSubscriptionManager.isActiveSubscriptionId(
                subscriptionInfoList.get(0).getSubscriptionId())) {
            AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(randomSubId,
                    AvailableNetworkInfo.PRIORITY_HIGH, mccMncs, bands);
            availableNetworkInfos.add(availableNetworkInfo);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackNoOpSub));
            // wait for the data change to take effect
            waitForMs(500);
            // clear all the operations at the end of test.
            availableNetworkInfos.clear();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackNoOpSub));
        } else {
            AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(
                    subscriptionInfoList.get(0).getSubscriptionId(),
                    AvailableNetworkInfo.PRIORITY_HIGH, mccMncs, bands);
            availableNetworkInfos.add(availableNetworkInfo);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
            // wait for the data change to take effect
            waitForMs(500);
            // clear all the operations at the end of test.
            availableNetworkInfos.clear();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos,
                            AsyncTask.SERIAL_EXECUTOR, callbackSuccess));
        }
    }

    @Test
    public void testSwitchMultiSimConfig() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.switchMultiSimConfig(mTelephonyManager.getActiveModemCount());
            fail("TelephonyManager#switchMultiSimConfig should require the MODIFY_PHONE_STATE"
                    + " permission to access.");
        } catch (SecurityException e) {
            // expected
        }
        try {
            // This should result in no-op.
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.switchMultiSimConfig(mTelephonyManager.getActiveModemCount()),
                    SecurityException.class, android.Manifest.permission.MODIFY_PHONE_STATE);
        } catch (SecurityException e) {
            fail("TelephonyManager#switchMultiSimConfig should require MODIFY_PHONE_STATE"
                    + "permission to access.");
        }
    }

    @Test
    public void testIccOpenLogicalChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccOpenLogicalChannelBySlot(0, null, 0));
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }

    @Test
    public void testIccOpenLogicalChannelBySlotAndPort() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return;
            }
        }
        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccOpenLogicalChannelByPort(0, 0, null, 0));
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccCloseLogicalChannelByPort: SecurityException not expected");
        }
    }

    @Test
    public void testIccCloseLogicalChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelBySlot(0, 0));
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }
    @Test
    public void testIccCloseLogicalChannelBySlotAndPort() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return;
            }
        }

        int slotIndex = getValidSlotIndexAndPort().getKey();
        int portIndex = getValidSlotIndexAndPort().getValue();
        // just verify no crash
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelByPort(
                            slotIndex, portIndex, 0));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalArgumentException and IllegalStateException is okay, just not
            // SecurityException
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccCloseLogicalChannelByPort: SecurityException not expected");
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelByPort(slotIndex, -1, 0));
            fail("Expected IllegalArgumentException, invalid PortIndex");
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is expected
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.iccCloseLogicalChannelByPort(
                            slotIndex, portIndex, -1));
            fail("Expected IllegalArgumentException, invalid channel");
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException is expected
        }
    }

    @Test
    public void testIccTransmitApduLogicalChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        int slotIndex = getValidSlotIndexAndPort().getKey();
        String result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.iccTransmitApduLogicalChannelBySlot(
                        slotIndex,
                        0 /* channel */,
                        0 /* cla */,
                        0 /* instruction */,
                        0 /* p1 */,
                        0 /* p2 */,
                        0 /* p3 */,
                        null /* data */));
        assertTrue(TextUtils.isEmpty(result));
    }

    @Test
    public void testIccTransmitApduLogicalChannelBySlotAndPort() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return;
            }
        }

        int slotIndex = getValidSlotIndexAndPort().getKey();
        int portIndex = getValidSlotIndexAndPort().getValue();
        try {
            String result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccTransmitApduLogicalChannelByPort(
                            slotIndex,
                            portIndex /* portIndex */,
                            0 /* channel */,
                            0 /* cla */,
                            0 /* instruction */,
                            0 /* p1 */,
                            0 /* p2 */,
                            0 /* p3 */,
                            null /* data */));
            assertTrue(TextUtils.isEmpty(result));
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccTransmitApduLogicalChannelByPort: SecurityException not expected");
        }
    }
    @Test
    public void testIccTransmitApduBasicChannelBySlot() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // just verify no crash
        int slotIndex = getValidSlotIndexAndPort().getKey();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccTransmitApduBasicChannelBySlot(
                            slotIndex,
                            0 /* cla */,
                            0 /* instruction */,
                            0 /* p1 */,
                            0 /* p2 */,
                            0 /* p3 */,
                            null /* data */));
        } catch (IllegalArgumentException e ) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }

    @Test
    public void testIccTransmitApduBasicChannelBySlotAndPort() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return;
            }
        }

        // just verify no crash
        int slotIndex = getValidSlotIndexAndPort().getKey();
        int portIndex = getValidSlotIndexAndPort().getValue();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.iccTransmitApduBasicChannelByPort(
                            slotIndex,
                            portIndex /*portIndex */,
                            0 /* cla */,
                            0 /* instruction */,
                            0 /* p1 */,
                            0 /* p2 */,
                            0 /* p3 */,
                            null /* data */));
        } catch (SecurityException e) {
            // IllegalArgumentException is okay, just not SecurityException
            fail("iccTransmitApduBasicChannelByPort: SecurityException not expected");
        }
    }

    @Test
    public void testIsIccLockEnabled() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // verify SecurityException
        try {
            mTelephonyManager.isIccLockEnabled();
            fail("testIsIccLockEnabled: Expected SecurityException on isIccLockEnabled");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isIccLockEnabled());
        } catch (SecurityException se) {
            fail("testIsIccLockEnabled: SecurityException not expected");
        }
    }

    @Test
    public void testIsDataEnabledForApn() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // verify SecurityException
        try {
            mTelephonyManager.isDataEnabledForApn(ApnSetting.TYPE_MMS);
            fail("testIsDataEnabledForApn: Expected SecurityException on isDataEnabledForApn");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isDataEnabledForApn(ApnSetting.TYPE_MMS));
        } catch (SecurityException se) {
            fail("testIsDataEnabledForApn: SecurityException not expected");
        }
    }

    @Test
    public void testIsTetheringApnRequired() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // verify SecurityException
        try {
            mTelephonyManager.isTetheringApnRequired();
            fail("testIsTetheringApnRequired: Expected SecurityException on "
                    + "isTetheringApnRequired");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isTetheringApnRequired());
        } catch (SecurityException se) {
            fail("testIsIccLockEnabled: SecurityException not expected");
        }
    }

    @Test
    public void testGetCarrierInfoForImsiEncryption() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // test without permission: verify SecurityException
        try {
            mTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_EPDG);
            fail("testGetCarrierInfoForImsiEncryption: "
                    + "SecurityException expected on getCarrierInfoForImsiEncryption");
        } catch (SecurityException se) {
            // expected
        }
        try {
            mTelephonyManager.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN);
            fail("testGetCarrierInfoForImsiEncryption: "
                    + "SecurityException expected on getCarrierInfoForImsiEncryption");
        } catch (SecurityException se) {
            // expected
        }
        // test with permission
        PublicKey epdgKey = null;
        PublicKey wlanKey = null;
        try {
            PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mTestSub);

            assertNotNull("CarrierConfigManager#getConfigForSubId() returned null",
                    carrierConfig);
            assertFalse("CarrierConfigManager#getConfigForSubId() returned empty bundle",
                    carrierConfig.isEmpty());

            // purge the certs in carrierConfigs first
            carrierConfig.putInt(
                    CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
            carrierConfig.putString(
                    CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, BAD_IMSI_CERT_URL);
            carrierConfig.putString(
                    CarrierConfigManager.IMSI_CARRIER_PUBLIC_KEY_EPDG_STRING,
                    IMSI_CERT_STRING_EPDG);
            carrierConfig.putString(
                    CarrierConfigManager.IMSI_CARRIER_PUBLIC_KEY_WLAN_STRING,
                    IMSI_CERT_STRING_WLAN);
            overrideCarrierConfig(carrierConfig);
        } catch (Exception e) {
            fail("Could not override carrier config. e=" + e.toString());
        }

        try {
            // It appears that the two certs actually have the same public key. Ideally we would
            // want these to be different for testing, but it's challenging to create a valid
            // certificate string for testing and these are the only two examples available
            InputStream inStream = new ByteArrayInputStream(IMSI_CERT_STRING_WLAN.getBytes());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
            wlanKey = cert.getPublicKey();

            inStream = new ByteArrayInputStream(IMSI_CERT_STRING_EPDG.getBytes());
            cert = (X509Certificate) cf.generateCertificate(inStream);
            epdgKey = cert.getPublicKey();
        } catch (CertificateException e) {
            fail("Could not create certs. e=" + e.toString());
        }

        try {
            ImsiEncryptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager,
                    (tm) -> {
                        return tm.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_EPDG);
                    });
            assertNotNull("Encryption info returned null", info);
            assertEquals(epdgKey, info.getPublicKey());
            assertEquals(TelephonyManager.KEY_TYPE_EPDG, info.getKeyType());
        } catch (SecurityException se) {
            fail("testGetCarrierInfoForImsiEncryption: SecurityException not expected");
        } catch (IllegalArgumentException iae) {
            // IllegalArgumentException is okay, just not SecurityException
        }
        try {
            ImsiEncryptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager,
                    (tm) -> {
                        return tm.getCarrierInfoForImsiEncryption(TelephonyManager.KEY_TYPE_WLAN);
                    });
            assertNotNull("Encryption info returned null", info);
            assertEquals(wlanKey, info.getPublicKey());
            assertEquals(TelephonyManager.KEY_TYPE_WLAN, info.getKeyType());
        } catch (SecurityException se) {
            fail("testGetCarrierInfoForImsiEncryption: SecurityException not expected");
        } catch (IllegalArgumentException iae) {
            // IllegalArgumentException is okay, just not SecurityException
        }
    }

    @Test
    public void testResetCarrierKeysForImsiEncryption() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // test without permission: verify SecurityException
        try {
            mTelephonyManager.resetCarrierKeysForImsiEncryption();
            fail("testResetCarrierKeysForImsiEncryption: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }
        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.resetCarrierKeysForImsiEncryption());
        } catch (SecurityException se) {
            fail("testResetCarrierKeysForImsiEncryption: SecurityException not expected");
        }
    }

    @Test
    public void testIsInEmergencySmsMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING));

        // test without permission: verify SecurityException
        try {
            mTelephonyManager.isInEmergencySmsMode();
            fail("testIsInEmergencySmsMode: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }
        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.isInEmergencySmsMode());
        } catch (SecurityException se) {
            fail("testIsInEmergencySmsMode: SecurityException not expected");
        }
    }

    @Test
    public void testGetSubscriptionId() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        TelephonyManager tm = mTelephonyManager.createForSubscriptionId(1);
        int subId = tm.getSubscriptionId();
        assertEquals(1, subId);
    }

    @Test
    public void testSetAllowedNetworkTypes() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // test without permission: verify SecurityException
        long allowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        try {
            mTelephonyManager.setAllowedNetworkTypes(allowedNetworkTypes);
            fail("testSetAllowedNetworkTypes: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypes(allowedNetworkTypes));

            long deviceAllowedNetworkTypes = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypes();
                    }
            );
            assertEquals(allowedNetworkTypes, deviceAllowedNetworkTypes);
        } catch (SecurityException se) {
            fail("testSetAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testDisAllowedNetworkTypes() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        long allowedNetworkTypes = ~TelephonyManager.NETWORK_TYPE_BITMASK_NR;
        long networkTypeBitmask = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA;

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                            allowedNetworkTypes));

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                            networkTypeBitmask));

            long modemNetworkTypeBitmask = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesBitmask();
                    }
            );
            long radioAccessFamily = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getSupportedRadioAccessFamily();
                    }
            );

            // RadioAccessFamily won't include all bits of RAFs group, so transfer to preferred
            // network type instead of using bitmask directly
            int modemPreferredNetworkType = RadioAccessFamily.getNetworkTypeFromRaf(
                    (int) modemNetworkTypeBitmask);
            int preferredNetworkType = RadioAccessFamily.getNetworkTypeFromRaf(
                    (int) (networkTypeBitmask & allowedNetworkTypes & radioAccessFamily));
            assertEquals(preferredNetworkType, modemPreferredNetworkType);
        } catch (SecurityException se) {
            fail("testDisAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testSetAllowedNetworkTypesForReason() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // test without permission: verify SecurityException
        long allowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        try {
            mIsAllowedNetworkTypeChanged = true;
            mTelephonyManager.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER, allowedNetworkTypes);
            fail("testSetAllowedNetworkTypesForReason: SecurityException expected");
        } catch (SecurityException se) {
            // expected
        }

        // test with permission
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                            allowedNetworkTypes));

            long deviceAllowedNetworkTypes = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER);
                    }
            );
            assertEquals(allowedNetworkTypes, deviceAllowedNetworkTypes);
        } catch (SecurityException se) {
            fail("testSetAllowedNetworkTypes: SecurityException not expected");
        }
    }

    @Test
    public void testSetAllowedNetworkTypesForReason_carrierPrivileges() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        try {
            CarrierPrivilegeUtils.withCarrierPrivileges(getContext(),
                    SubscriptionManager.getDefaultSubscriptionId(),
                    () -> {
                        mTelephonyManager.setAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                                TelephonyManager.NETWORK_TYPE_BITMASK_LTE);
                    }
            );
        } catch (SecurityException se) {
            fail("setAllowedNetworkTypesForReason: SecurityException not expected: "
                    + se.getMessage());
        } catch (Exception e) {
            // withCarrierPrivileges declares a checked Exception, so we must handle it. We don't
            // expect any exceptions so this is still a failure.
            Log.e(TAG, "Exception not expected. failing test.", e);
            fail("CarrierPrivilegeUtils.withCarrierPrivileges: Exception not expected. "
                    + "See error log.");
        }

        assertThrows(SecurityException.class, () -> {
            CarrierPrivilegeUtils.withCarrierPrivileges(getContext(),
                    SubscriptionManager.getDefaultSubscriptionId(),
                    () -> {
                        mTelephonyManager.setAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G,
                                TelephonyManager.NETWORK_TYPE_BITMASK_LTE);
                    }
            );
        });
    }

    private class AllowedNetworkTypesListener extends TelephonyCallback
            implements TelephonyCallback.AllowedNetworkTypesListener {
        @Override
        public void onAllowedNetworkTypesChanged(int reason, long allowedNetworkType) {
            try {
                Log.d(TAG, "onAllowedNetworkTypesChanged");
                if (mExpectedReason == reason
                        && mExpectedAllowedNetworkType == allowedNetworkType) {
                    verifyExpectedGetAllowedNetworkType(reason);
                }
            } catch (SecurityException se) {
                fail("testSetAllowedNetworkTypes: SecurityException not expected");
            }
        }

        private CountDownLatch mLatch;
        private int mExpectedReason;
        private long mExpectedAllowedNetworkType;
        public void setExpectedAllowedNetworkType(
                int expectedReason, long expectedAllowedNetworkType, int expectedLatchcount) {
            mExpectedReason = expectedReason;
            mExpectedAllowedNetworkType = expectedAllowedNetworkType;
            mLatch = new CountDownLatch(expectedLatchcount);
        }

        public void verifyExpectedGetAllowedNetworkType(int reason) {
            long allowedNetworkType = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager,
                    (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(reason);
                    },
                    "android.permission.READ_PRIVILEGED_PHONE_STATE"
            );
            if (mExpectedAllowedNetworkType == allowedNetworkType) {
                mLatch.countDown();
            }
        }
    }

    private void verifySetAndGetAllowedNetworkTypesForReason(AllowedNetworkTypesListener listener,
            int reason, long allowedNetworkTypes) throws Exception {

        // Try the test three times, and stop test when get success once during the test.
        int retries = 3;
        listener.setExpectedAllowedNetworkType(reason, allowedNetworkTypes, 1);
        for (int count = 0; count < retries; count++) {
            // setAllowedNetworkTypesForReason
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(reason, allowedNetworkTypes),
                    "android.permission.MODIFY_PHONE_STATE");

            // test getAllowedNetworkTypesForReason.
            // Since this is testing the getAllowedNetworkTypesForReason it helps to speed up the
            // test by doing the get here first rather than waiting for the event change.
            listener.verifyExpectedGetAllowedNetworkType(reason);

            // if getAllowedNetworkTypesForReason return not-expected value, then wait for a while
            // by listening onAllowedNetworkTypesChanged
            if (listener.mLatch.await(TOLERANCE, TimeUnit.MILLISECONDS)) {
                break;
            }
        }

        // Check to see if the result is expected at least once.
        assertEquals(0, listener.mLatch.getCount());
    }

    @Test
    public void testSetAllowedNetworkTypesForReason_moreReason() throws Exception {

        // Register telephony callback for AllowedNetworkTypesListener
        AllowedNetworkTypesListener listener = new AllowedNetworkTypesListener();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.registerTelephonyCallback(mSimpleExecutor, listener));

        // test without permission: verify SecurityException
        long allowedNetworkTypes1 = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS;
        long allowedNetworkTypes2 = TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        long allowedNetworkTypes3 = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_HSPA
                | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS;
        long allowedNetworkTypes4 = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_HSPA;

        // test all allowedNetworkTypes
        verifySetAndGetAllowedNetworkTypesForReason(listener,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER, allowedNetworkTypes1);
        verifySetAndGetAllowedNetworkTypesForReason(listener,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, allowedNetworkTypes2);
        verifySetAndGetAllowedNetworkTypesForReason(listener,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER, allowedNetworkTypes3);
        verifySetAndGetAllowedNetworkTypesForReason(listener,
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G, allowedNetworkTypes4);

        // Unregister telephony callback
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.unregisterTelephonyCallback(listener));
    }

    @Test
    public void testIsApplicationOnUicc() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // Expect a security exception without permission.
        try {
            mTelephonyManager.isApplicationOnUicc(TelephonyManager.APPTYPE_SIM);
            fail("Expected security exception");
        } catch (SecurityException se1) {
            // Expected
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        try {
            mTelephonyManager.isApplicationOnUicc(TelephonyManager.APPTYPE_SIM);
        } catch (SecurityException se) {
            fail("Caller with READ_PRIVILEGED_PHONE_STATE should be able to call API");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#requestModemActivityInfo"})
    public void testRequestModemActivityInfo() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.MODIFY_PHONE_STATE);
        try {
            // Get one instance of activity info and make sure it's valid
            CompletableFuture<ModemActivityInfo> future1 = new CompletableFuture<>();
            mTelephonyManager.requestModemActivityInfo(getContext().getMainExecutor(),
                    future1::complete);
            ModemActivityInfo activityInfo1 = future1.get(TOLERANCE, TimeUnit.MILLISECONDS);
            assertNotNull(activityInfo1);
            assertTrue("first activity info is" + activityInfo1, activityInfo1.isValid());

            // Wait a bit, then get another instance to make sure that some info has accumulated
            waitForMs(5000);
            CompletableFuture<ModemActivityInfo> future2 = new CompletableFuture<>();
            mTelephonyManager.requestModemActivityInfo(getContext().getMainExecutor(),
                    future2::complete);
            ModemActivityInfo activityInfo2 = future2.get(TOLERANCE, TimeUnit.MILLISECONDS);
            assertNotNull(activityInfo2);
            assertTrue("second activity info is" + activityInfo2, activityInfo2.isValid());

            ModemActivityInfo diff = activityInfo1.getDelta(activityInfo2);
            assertNotNull(diff);
            assertTrue("two activityInfo are identical", !activityInfo1.equals(activityInfo2));
            assertTrue("diff is" + diff, diff.isValid() || diff.isEmpty());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testModemActivityInfoException() {
        TelephonyManager.ModemActivityInfoException exception =
                new TelephonyManager.ModemActivityInfoException(
                        TelephonyManager.ModemActivityInfoException.ERROR_PHONE_NOT_AVAILABLE);
        assertEquals(TelephonyManager.ModemActivityInfoException.ERROR_PHONE_NOT_AVAILABLE,
                exception.getErrorCode());
    }

    @Test
    public void testGetSupportedModemCount() {
        int supportedModemCount = mTelephonyManager.getSupportedModemCount();
        int activeModemCount = mTelephonyManager.getActiveModemCount();
        assertTrue(activeModemCount >= 0);
        assertTrue(supportedModemCount >= activeModemCount);
    }

    @Test
    public void testGetAllNetworkTypes() {
        Set<Integer> expectedNetworkTypes = new HashSet<>(Arrays.asList(
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_GSM,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                TelephonyManager.NETWORK_TYPE_LTE_CA,
                TelephonyManager.NETWORK_TYPE_NR
        ));

        Set<Integer> actualNetworkTypes = IntStream.of(TelephonyManager.getAllNetworkTypes())
                .boxed().collect(Collectors.toSet());
        assertTrue(expectedNetworkTypes.containsAll(actualNetworkTypes));
        assertTrue(actualNetworkTypes.containsAll(expectedNetworkTypes));
    }

    @Test
    public void testIsModemEnabledForSlot() {
        int activeModemCount = mTelephonyManager.getActiveModemCount();
        for (int i = 0; i < activeModemCount; i++) {
            // Call isModemEnabledForSlot for each slot and verify no crash.
            mTelephonyManager.isModemEnabledForSlot(i);
        }
    }

    @Test
    public void testOpportunisticNetworkState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
                && !mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH));

        boolean isEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                tm -> tm.isOpportunisticNetworkEnabled());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setOpportunisticNetworkState(true));
        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                tm -> tm.isOpportunisticNetworkEnabled()));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setOpportunisticNetworkState(false));
        assertFalse(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                tm -> tm.isOpportunisticNetworkEnabled()));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setOpportunisticNetworkState(isEnabled));
    }

    @Test
    public void testGetSimApplicationState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        int simApplicationState = mTelephonyManager.getSimApplicationState();
        assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                TelephonyManager.SIM_STATE_NOT_READY,
                TelephonyManager.SIM_STATE_PERM_DISABLED,
                TelephonyManager.SIM_STATE_LOADED).contains(simApplicationState));

        for (int i = 0; i <= mTelephonyManager.getPhoneCount(); i++) {
            final int slotId = i;
            simApplicationState = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getSimApplicationState(slotId));
            assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                    TelephonyManager.SIM_STATE_PIN_REQUIRED,
                    TelephonyManager.SIM_STATE_PUK_REQUIRED,
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                    TelephonyManager.SIM_STATE_NOT_READY,
                    TelephonyManager.SIM_STATE_PERM_DISABLED,
                    TelephonyManager.SIM_STATE_LOADED).contains(simApplicationState));
        }
    }

    @Test
    public void testGetSimApplicationStateWithPhysicalSlotIndexAndPortIndex() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            List<UiccCardInfo> cardInfoList =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                            (tm) -> tm.getUiccCardsInfo());
            for (UiccCardInfo cardInfo : cardInfoList) {
                int physicalSlotIndex = cardInfo.getPhysicalSlotIndex();
                List<UiccPortInfo> portInfoList = (List<UiccPortInfo>) cardInfo.getPorts();
                for (UiccPortInfo uiccPortInfo : portInfoList) {
                    int portIndex = uiccPortInfo.getPortIndex();
                    int simApplicationState =
                            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                                    (tm) -> tm.getSimApplicationState(physicalSlotIndex,
                                            portIndex));
                    assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                            TelephonyManager.SIM_STATE_PIN_REQUIRED,
                            TelephonyManager.SIM_STATE_PUK_REQUIRED,
                            TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                            TelephonyManager.SIM_STATE_NOT_READY,
                            TelephonyManager.SIM_STATE_PERM_DISABLED,
                            TelephonyManager.SIM_STATE_LOADED).contains(simApplicationState));
                }
            }
        } catch (SecurityException e) {
            fail("Caller with READ_PRIVILEGED_PHONE_STATE should be able to call API");
        }
    }

    @Test
    public void testGetSimCardState() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        int simCardState = mTelephonyManager.getSimCardState();
        assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                TelephonyManager.SIM_STATE_ABSENT,
                TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                TelephonyManager.SIM_STATE_CARD_RESTRICTED,
                TelephonyManager.SIM_STATE_PRESENT).contains(simCardState));
    }
    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getUiccCardsInfo",
            "android.telephony.TelephonyManager#getSimCardState"})
    public void getSimCardStateTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        List<UiccCardInfo> cardsInfo = mTelephonyManager.getUiccCardsInfo();
        for (UiccCardInfo cardInfo : cardsInfo) {
            for (UiccPortInfo portInfo : cardInfo.getPorts()) {
                int simCardState = mTelephonyManager.getSimCardState(cardInfo
                        .getPhysicalSlotIndex(), portInfo.getPortIndex());
                assertTrue(Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN,
                        TelephonyManager.SIM_STATE_ABSENT,
                        TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                        TelephonyManager.SIM_STATE_CARD_RESTRICTED,
                        TelephonyManager.SIM_STATE_PRESENT).contains(simCardState));
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testMultipleEnabledProfiles() {
        if (hasFeature(PackageManager.FEATURE_TELEPHONY_EUICC_MEP)) {
            List<UiccCardInfo> cardInfos =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                            (tm) -> tm.getUiccCardsInfo());
            for (UiccCardInfo cardInfo : cardInfos) {
                // This test suppose there is no use case that OEMs will have multiple esim
                // chipset with different MEP capabilities.
                if (cardInfo.isEuicc()) {
                    assertTrue(cardInfo.isMultipleEnabledProfilesSupported());
                    List<UiccPortInfo> uiccPortInfos = (List<UiccPortInfo>)
                            ShellIdentityUtils.invokeMethodWithShellPermissions(cardInfo,
                                    (card) -> card.getPorts());
                    assertTrue(uiccPortInfos.size() > 1);
                }
            }
        }
    }

    private boolean isDataEnabled() {
        return ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::isDataEnabled);
    }

    @Test
    public void testThermalDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Perform this test on default data subscription.
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        // Register data enabled listener
        DataEnabledListenerTest dataEnabledListener = new DataEnabledListenerTest();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, dataEnabledListener));

        dataEnabledListener.clearDataEnableChanges();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_THERMAL,
                        false));

        assertTrue(dataEnabledListener.waitForThermalDataOff());
        boolean isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_THERMAL));
        assertFalse(isDataEnabledForReason);

        boolean isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertFalse(isDataConnectionAvailable);

        dataEnabledListener.clearDataEnableChanges();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_THERMAL,
                        true));

        assertTrue(dataEnabledListener.waitForThermalDataOn());
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_THERMAL));
        assertTrue(isDataEnabledForReason);

        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);

        // Unregister data enabled listener
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.unregisterTelephonyCallback(dataEnabledListener));
    }

    @Test
    public void testPolicyDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Perform this test on default data subscription.
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());

        int retry = 0;
        boolean isDataEnabledForReason = true;
        boolean isDataConnectionAvailable = true;
        // NPMS will set policy data to true after tests set it to false,
        // so retry disabling policy data to prevent flaky test failures.
        // TODO: Set empty policies once we can suppress default policies.
        while ((isDataEnabledForReason || isDataConnectionAvailable) && retry < 30) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_POLICY,
                            false));
            isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                            TelephonyManager.DATA_ENABLED_REASON_POLICY));
            isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
            retry++;
            waitForMs(500);
        }
        assertFalse(isDataEnabledForReason);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_POLICY,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_POLICY));
        assertTrue(isDataEnabledForReason);

        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testCarrierDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        // Perform this test on default data subscription.
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_CARRIER,
                        false));

        waitForMs(1000);
        boolean isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_CARRIER));
        assertFalse(isDataEnabledForReason);

        boolean isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_CARRIER,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_CARRIER));
        assertTrue(isDataEnabledForReason);
        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testUserDataEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER,
                        false));

        waitForMs(1000);
        boolean isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_USER));
        assertFalse(isDataEnabledForReason);

        boolean isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertFalse(isDataConnectionAvailable);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER,
                        true));

        waitForMs(1000);
        isDataEnabledForReason = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_USER));
        assertTrue(isDataEnabledForReason);
        isDataConnectionAvailable = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDataConnectionAllowed);
        assertTrue(isDataConnectionAvailable);
    }

    @Test
    public void testDataDuringVoiceCallPolicy() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.ShellPermissionMethodHelper<Boolean, TelephonyManager> getPolicyHelper =
                (tm) -> tm.isMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL);

        boolean allowDataDuringVoiceCall = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, getPolicyHelper);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL,
                        !allowDataDuringVoiceCall));

        waitForMs(500);
        assertNotEquals(allowDataDuringVoiceCall,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL,
                        allowDataDuringVoiceCall));

        waitForMs(500);
        assertEquals(allowDataDuringVoiceCall,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));
    }

    private interface Condition {
        Object expected();
        Object actual();
    }

    private void waitUntilConditionIsTrueOrTimeout(
            Condition condition, long timeout, String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            waitForMs(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    private void waitForDataPolicySetting(ShellIdentityUtils.ShellPermissionMethodHelper<Boolean,
            TelephonyManager> getPolicyHelper, boolean mmsAlwaysAllowed) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return mmsAlwaysAllowed;
                    }

                    @Override
                    public Object actual() {
                        Log.d(TAG, "invokeMethodWithShellPermissions : " + mmsAlwaysAllowed);
                        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                          mTelephonyManager, getPolicyHelper);
                    }
                }, WAIT_FOR_CONDITION, "Policy returned");
    }

    @Test
    public void testAlwaysAllowMmsDataPolicy() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.ShellPermissionMethodHelper<Boolean, TelephonyManager> getPolicyHelper =
                (tm) -> tm.isMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED);

        boolean mmsAlwaysAllowed = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, getPolicyHelper);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED,
                        !mmsAlwaysAllowed));

        waitForDataPolicySetting(getPolicyHelper, !mmsAlwaysAllowed);
        assertNotEquals(mmsAlwaysAllowed,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_MMS_ALWAYS_ALLOWED,
                        mmsAlwaysAllowed));

        waitForDataPolicySetting(getPolicyHelper, mmsAlwaysAllowed);
        assertEquals(mmsAlwaysAllowed,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));
    }

    @Test
    public void testAutoDataSwitchPolicy() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_DATA));

        ShellIdentityUtils.ShellPermissionMethodHelper<Boolean, TelephonyManager> getPolicyHelper =
                (tm) -> tm.isMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_AUTO_DATA_SWITCH);

        boolean autoDatSwitchAllowed = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, getPolicyHelper);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_AUTO_DATA_SWITCH,
                        !autoDatSwitchAllowed));

        waitForMs(1000);
        assertNotEquals(autoDatSwitchAllowed,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setMobileDataPolicyEnabled(
                        TelephonyManager.MOBILE_DATA_POLICY_AUTO_DATA_SWITCH,
                        autoDatSwitchAllowed));

        waitForMs(1000);
        assertEquals(autoDatSwitchAllowed,
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, getPolicyHelper));
    }

    @Test
    public void testGetCdmaEnhancedRoamingIndicatorDisplayNumber() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA));

        int index = mTelephonyManager.getCdmaEnhancedRoamingIndicatorDisplayNumber();
        int phoneType = mTelephonyManager.getPhoneType();
        if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            assertTrue(index >= 0 && index <= 255);
        } else {
            assertEquals(-1, index);
        }
    }

    private int disableNrDualConnectivity() {
        if (!ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager
                                .CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE))) {
            return TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED;
        }

        int result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager,
                (tm) -> tm.setNrDualConnectivityState(
                        TelephonyManager.NR_DUAL_CONNECTIVITY_DISABLE));

        boolean isNrDualConnectivityEnabled =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, (tm) -> tm.isNrDualConnectivityEnabled());
        // Only verify the result for supported devices on IRadio 1.6+
        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_6
                && result != TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED) {
            assertFalse(isNrDualConnectivityEnabled);
        }

        return result;
    }

    @Test
    public void testNrDualConnectivityEnable() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        if (!ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager
                                .CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE))) {
            return;
        }

        boolean isInitiallyEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isNrDualConnectivityEnabled());
        boolean isNrDualConnectivityEnabled;
        int result;
        if (isInitiallyEnabled) {
            result = disableNrDualConnectivity();
            if (result == TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED) {
                return;
            }
        }


        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager,
                (tm) -> tm.setNrDualConnectivityState(
                        TelephonyManager.NR_DUAL_CONNECTIVITY_ENABLE));

        if (result == TelephonyManager.ENABLE_NR_DUAL_CONNECTIVITY_NOT_SUPPORTED) {
            return;
        }

        isNrDualConnectivityEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isNrDualConnectivityEnabled());
        // Only verify the result for supported devices on IRadio 1.6+
        if (mNetworkHalVersion >= RADIO_HAL_VERSION_1_6) {
            assertTrue(isNrDualConnectivityEnabled);
        }

        if (!isInitiallyEnabled) {
            disableNrDualConnectivity();
        }
    }

    @Test
    public void testCdmaRoamingMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA)
                && mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA);

        // Save state
        int cdmaRoamingMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                TelephonyManager::getCdmaRoamingMode);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaRoamingMode(TelephonyManager.CDMA_ROAMING_MODE_HOME));
        assertEquals(TelephonyManager.CDMA_ROAMING_MODE_HOME,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaRoamingMode));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaRoamingMode(TelephonyManager.CDMA_ROAMING_MODE_AFFILIATED));
        assertEquals(TelephonyManager.CDMA_ROAMING_MODE_AFFILIATED,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaRoamingMode));

        // Reset state
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaRoamingMode(cdmaRoamingMode));
    }

    @Test
    public void testCdmaSubscriptionMode() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CDMA)
                && mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA);

        // Save state
        int cdmaSubscriptionMode = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getCdmaSubscriptionMode);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaSubscriptionMode(TelephonyManager.CDMA_SUBSCRIPTION_NV));
        assertEquals(TelephonyManager.CDMA_SUBSCRIPTION_NV,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaSubscriptionMode));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaSubscriptionMode(TelephonyManager.CDMA_SUBSCRIPTION_RUIM_SIM));
        assertEquals(TelephonyManager.CDMA_SUBSCRIPTION_RUIM_SIM,
                (int) ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        TelephonyManager::getCdmaSubscriptionMode));

        // Reset state
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setCdmaSubscriptionMode(cdmaSubscriptionMode));
    }

    @Test
    public void testPinResult() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        final String empty_pin = ""; // For getting current remaining pin attempt.
        final String pin = "fake_pin";
        final String puk = "fake_puk";
        final String newPin = "fake_new_pin";

        //Refer GSM 02.17 5.6 PIN Management
        //To avoid that sim may enter PUK state,
        //TC should be allowed when current Pin attempt count is reset with 3.
        boolean isEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isIccLockEnabled);
        PinResult result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.supplyIccLockPin(empty_pin));
        if (result.getAttemptsRemaining() < 3) {
            Log.d(TAG, "Skipping test and requires that reboot device and unlock pin successfully");
            return;
        }

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.setIccLockEnabled(!isEnabled, pin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);
        assertEquals(isEnabled, ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isIccLockEnabled));

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.changeIccLockPin(pin, newPin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.supplyIccLockPin(pin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);

        result = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.supplyIccLockPuk(puk, pin));
        assertTrue(result.getResult() == PinResult.PIN_RESULT_TYPE_INCORRECT
                || result.getResult() == PinResult.PIN_RESULT_TYPE_FAILURE);
        assertTrue(result.getAttemptsRemaining() >= -1);
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_nullRequest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify NPE throws if set request with null object
        try {
            mTelephonyManager.setSignalStrengthUpdateRequest(null);
            fail("NullPointerException expected when setSignalStrengthUpdateRequest with null");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_noPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        final SignalStrengthUpdateRequest normalRequest =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        // Verify SE throws for apps without carrier privilege or MODIFY_PHONE_STATE permission
        try {
            mTelephonyManager.setSignalStrengthUpdateRequest(normalRequest);
            fail("SecurityException expected when setSignalStrengthUpdateRequest without "
                    + "carrier privilege or MODIFY_PHONE_STATE permission");
        } catch (SecurityException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(normalRequest));
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_systemThresholdReportingRequestedWhileIdle() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify system privileged app with permission LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH can
        // set systemThresholdReportingRequestedWhileIdle to true with empty thresholdInfos
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(Collections.EMPTY_LIST)
                .setSystemThresholdReportingRequestedWhileIdle(true)
                .build();

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.setSignalStrengthUpdateRequest(request));
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(request));
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_hysteresisDbSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify SE throws for app when set hysteresisDb in the SignalThresholdInfo
        SignalStrengthUpdateRequest requestWithHysteresisDbSet =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .setHysteresisDb(10)
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithHysteresisDbSet));
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(requestWithHysteresisDbSet));
        }
    }


    @Test
    public void testSetSignalStrengthUpdateRequest_hysteresisMsSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify SE throws for app when set hysteresisMs in the SignalThresholdInfo
        SignalStrengthUpdateRequest requestWithHysteresisMsSet =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .setHysteresisMs(1000) //allowed for system caller only
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithHysteresisMsSet));
            fail("IllegalArgumentException expected when set hysteresisMs in SignalThresholdInfo "
                    + "to true");
        } catch (IllegalArgumentException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(requestWithHysteresisMsSet));
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_isEnabledSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify SE throws for app when set isEnabled in the SignalThresholdInfo
        SignalStrengthUpdateRequest requestWithThresholdIsEnabledSet =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97})
                                        .setIsEnabled(true) //allowed for system caller only
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithThresholdIsEnabledSet));
            fail("IllegalArgumentException expected when set isEnabled in SignalThresholdInfo "
                    + "with true");
        } catch (IllegalArgumentException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(requestWithThresholdIsEnabledSet));
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_tooShortThresholds() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // verify SE throws if app set too short thresholds
        SignalStrengthUpdateRequest requestWithTooShortThresholds =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{}, true /*isSystem*/)
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithTooShortThresholds));
            fail("IllegalArgumentException expected when set thresholds that is too short");
        } catch (IllegalArgumentException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(requestWithTooShortThresholds));
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_tooLongThresholds() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // verify SE throws if app set too long thresholds
        SignalStrengthUpdateRequest requestWithTooLongThresholds =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -61, -51},
                                            true /*isSystem*/)
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(requestWithTooLongThresholds));
            fail("IllegalArgumentException expected when set thresholds that is too long");
        } catch (IllegalArgumentException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(requestWithTooLongThresholds));
        }
    }

    @Test
    public void testSetSignalStrengthUpdateRequest_duplicatedRequest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        final SignalStrengthUpdateRequest normalRequest =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        // Verify IllegalStateException should throw when set the same request twice
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setSignalStrengthUpdateRequest(normalRequest));
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.setSignalStrengthUpdateRequest(normalRequest));
            fail("IllegalStateException expected when setSignalStrengthUpdateRequest twice with "
                    + "same request object");
        } catch (IllegalStateException expected) {
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.clearSignalStrengthUpdateRequest(normalRequest));
        }
    }

    @Test
    public void testClearSignalStrengthUpdateRequest_nullRequest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Verify NPE should throw if clear request with null object
        try {
            mTelephonyManager.clearSignalStrengthUpdateRequest(null);
            fail("NullPointerException expected when clearSignalStrengthUpdateRequest with null");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testClearSignalStrengthUpdateRequest_noPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        final SignalStrengthUpdateRequest normalRequest =
                new SignalStrengthUpdateRequest.Builder()
                        .setSignalThresholdInfos(List.of(
                                new SignalThresholdInfo.Builder()
                                        .setRadioAccessNetworkType(
                                                AccessNetworkConstants.AccessNetworkType.GERAN)
                                        .setSignalMeasurementType(
                                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                                        .setThresholds(new int[]{-113, -103, -97, -51})
                                        .build()))
                        .setReportingRequestedWhileIdle(true)
                        .build();

        // Verify SE throws for apps without carrier privilege or MODIFY_PHONE_STATE permission
        try {
            mTelephonyManager.clearSignalStrengthUpdateRequest(normalRequest);
            fail("SecurityException expected when clearSignalStrengthUpdateRequest without "
                    + "carrier privilege or MODIFY_PHONE_STATE permission");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testClearSignalStrengthUpdateRequest_clearWithNoSet() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        SignalStrengthUpdateRequest requestNeverSetBefore = new SignalStrengthUpdateRequest
                .Builder()
                .setSignalThresholdInfos(List.of(new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                        .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                        .setThresholds(new int[]{-113, -103, -97, -51})
                        .build()))
                .setReportingRequestedWhileIdle(true)
                .build();

        // Verify clearSignalStrengthUpdateRequest is no-op when clear request that was not set
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.clearSignalStrengthUpdateRequest(requestNeverSetBefore));
    }

    @Test
    public void testSendThermalMitigationRequest() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(THERMAL_MITIGATION_COMMAND_BASE).append(ALLOW_PACKAGE_SUBCOMMAND)
                .append(TELEPHONY_CTS_PACKAGE);
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                cmdBuilder.toString());

        long arbitraryCompletionWindowMillis = 60000L;

        boolean isDataThrottlingSupported = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                        TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING));

        int thermalMitigationResult = -1;
        if (isDataThrottlingSupported) {
            // Test a proper data throttling thermal mitigation request.
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                        new ThermalMitigationRequest.Builder()
                                .setThermalMitigationAction(ThermalMitigationRequest
                                        .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                        .setDataThrottlingAction(DataThrottlingRequest
                                                .DATA_THROTTLING_ACTION_THROTTLE_SECONDARY_CARRIER)
                                        .setCompletionDurationMillis(arbitraryCompletionWindowMillis)
                                        .build())
                                .build()));

            assertEquals(thermalMitigationResult,
                    TelephonyManager.THERMAL_MITIGATION_RESULT_SUCCESS);
        }
        // Test negative completionDurationSecs is an invalid parameter.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(ThermalMitigationRequest
                                            .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(DataThrottlingRequest
                                                    .DATA_THROTTLING_ACTION_THROTTLE_PRIMARY_CARRIER
                                            )
                                            .setCompletionDurationMillis(-1)
                                            .build())
                                    .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test non-zero completionDurationSecs is an invalid parameter for data throttling hold.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(ThermalMitigationRequest
                                            .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(
                                                    DataThrottlingRequest
                                                            .DATA_THROTTLING_ACTION_HOLD)
                                            .setCompletionDurationMillis(
                                                    arbitraryCompletionWindowMillis)
                                            .build())
                                    .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test null DataThrottlingParams is an invalid parameter for data throttling request.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(ThermalMitigationRequest
                                            .THERMAL_MITIGATION_ACTION_DATA_THROTTLING)
                                    .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test non-null DataThrottlingParams is an invalid parameter for voice only request.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(
                                            ThermalMitigationRequest
                                                    .THERMAL_MITIGATION_ACTION_VOICE_ONLY)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(
                                                    DataThrottlingRequest
                                                    .DATA_THROTTLING_ACTION_THROTTLE_PRIMARY_CARRIER
                                            )
                                            .setCompletionDurationMillis(-1)
                                            .build())
                            .build()));
        } catch (IllegalArgumentException e) {
        }

        // Test non-null DataThrottlingParams is an invalid parameter for radio off request.
        try {
            thermalMitigationResult = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.sendThermalMitigationRequest(
                            new ThermalMitigationRequest.Builder()
                                    .setThermalMitigationAction(
                                            ThermalMitigationRequest
                                                    .THERMAL_MITIGATION_ACTION_RADIO_OFF)
                                    .setDataThrottlingRequest(new DataThrottlingRequest.Builder()
                                            .setDataThrottlingAction(DataThrottlingRequest
                                                    .DATA_THROTTLING_ACTION_THROTTLE_PRIMARY_CARRIER
                                            )
                                            .setCompletionDurationMillis(-1)
                                            .build())
                            .build()));
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testIsRadioInterfaceCapabilitySupported() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        assertFalse(mTelephonyManager.isRadioInterfaceCapabilitySupported("empty"));
        assertFalse(mTelephonyManager.isRadioInterfaceCapabilitySupported(null));
        assertFalse(mTelephonyManager.isRadioInterfaceCapabilitySupported(""));
    }

    private Set<CellIdentity> getRegisteredCellIdentities() {
        ServiceState ss = mTelephonyManager.getServiceState();
        Set<CellIdentity> cidSet = new ArraySet<>(2);
        for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoListForTransportType(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN)) {
            if (nri.isRegistered()) cidSet.add(nri.getCellIdentity());
        }
        return cidSet;
    }

    private boolean hasMultipleRegisteredSubscriptions() {
        final int[] activeSubIds = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSubscriptionManager, (sm) ->sm.getActiveSubscriptionIdList());
        int registeredSubscriptions = 0;
        for (int subId : activeSubIds) {
            ServiceState ss = mTelephonyManager.createForSubscriptionId(subId).getServiceState();
            for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoListForTransportType(
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN)) {
                if (nri.isRegistered()) {
                    registeredSubscriptions++;
                    break;
                }
            }
        }
        return registeredSubscriptions > 1;
    }

    @Test
    public void testGetAllCellInfo() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        // For INetworkRadio <1.5, just verify that calling the method doesn't throw an error.
        if (mNetworkHalVersion < RADIO_HAL_VERSION_1_5) {
            mTelephonyManager.getAllCellInfo();
            return;
        }

        List<CellInfo> allCellInfo = mTelephonyManager.getAllCellInfo();
        assertTrue(!allCellInfo.isEmpty());
        for (CellInfo cellInfo : allCellInfo) {
            CellIdentity cellIdentity = cellInfo.getCellIdentity();
            int[] bands;
            if (cellIdentity instanceof CellIdentityLte) {
                bands = ((CellIdentityLte) cellIdentity).getBands();
                if (cellInfo.isRegistered()) assertTrue(bands.length > 0);
                for (int band : bands) {
                    assertTrue(band >= AccessNetworkConstants.EutranBand.BAND_1
                            && band <= AccessNetworkConstants.EutranBand.BAND_88);
                }
            } else if (cellIdentity instanceof CellIdentityNr) {
                bands = ((CellIdentityNr) cellIdentity).getBands();
                if (cellInfo.isRegistered()) assertTrue(bands.length > 0);
                for (int band : bands) {
                    assertTrue((band >= AccessNetworkConstants.NgranBands.BAND_1
                            && band <= AccessNetworkConstants.NgranBands.BAND_95)
                            || (band >= AccessNetworkConstants.NgranBands.BAND_257
                            && band <= AccessNetworkConstants.NgranBands.BAND_261));
                }
            }

            // TODO(229311863): This can theoretically break on a DSDS device where both SIMs are
            // registered because CellInfo returns data for both modems and this code only cross
            // checks against the default subscription.
            if (hasMultipleRegisteredSubscriptions()) continue;

            boolean isSameCell = false;
            if (cellInfo.isRegistered()) {
                for (CellIdentity cid : getRegisteredCellIdentities()) {
                    if (cellIdentity.isSameCell(cid)) isSameCell = true;
                }
                assertTrue(sNetworkTypes.get(cellIdentity.getClass()).contains(
                            mTelephonyManager.getDataNetworkType())
                                    || sNetworkTypes.get(cellIdentity.getClass()).contains(
                                            mTelephonyManager.getVoiceNetworkType()));
                assertTrue(
                        "Registered CellInfo#CellIdentity not found in ServiceState",
                        isSameCell);
            }
        }

    }

    @Test
    @ApiTest(apis = {"android.telephony.CarrierConfigManager#KEY_CARRIER_METERED_APN_TYPES_STRINGS",
            "android.telephony.CarrierConfigManager#KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS",
            "android.telephony.TelephonyManager#isApnMetered"})
    public void testIsApnMetered() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] {ApnSetting.TYPE_MMS_STRING});
        carrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[] {ApnSetting.TYPE_MMS_STRING});
        overrideCarrierConfig(carrierConfig);

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            PollingCheck.waitFor(5000, () -> !mTelephonyManager.isApnMetered(ApnSetting.TYPE_DUN),
                    "Timeout when waiting for DUN APN to become unmetered");

            assertTrue(mTelephonyManager.isApnMetered(ApnSetting.TYPE_MMS));
            assertFalse(mTelephonyManager.isApnMetered(ApnSetting.TYPE_DUN));
        } finally {
            // Restore the original carrier config
            overrideCarrierConfig(null);
            // Revoke the permission READ_PRIVILEGED_PHONE_STATE
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        carrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[] {ApnSetting.TYPE_DUN_STRING});
        carrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[] {ApnSetting.TYPE_DUN_STRING});
        overrideCarrierConfig(carrierConfig);
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            PollingCheck.waitFor(5000, () -> mTelephonyManager.isApnMetered(ApnSetting.TYPE_DUN),
                    "Timeout when waiting for DUN APN to become metered");

            assertFalse(mTelephonyManager.isApnMetered(ApnSetting.TYPE_MMS));
            assertTrue(mTelephonyManager.isApnMetered(ApnSetting.TYPE_DUN));
        } finally {
            overrideCarrierConfig(null);
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * Validate Emergency Number address that only contains the dialable character.
     *
     * @param address Emergency number address to validate
     * @return {@code true} if the address is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberAddress(String address) {
        if (address == null) {
            return false;
        }
        for (char c : address.toCharArray()) {
            if (!isDialable(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Emergency Number country Iso
     *
     * @param countryIso Emergency number country iso to validate
     * @return {@code true} if the country iso is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberCountryIso(String countryIso) {
        if (countryIso == null) {
            return false;
        }
        int length = countryIso.length();
        return length >= 0 && length <= 2;
    }

    /**
     * Validate Emergency Number MNC
     *
     * @param mnc Emergency number MNC to validate
     * @return {@code true} if the MNC is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberMnc(String mnc) {
        if (mnc == null) {
            return false;
        }
        int length = mnc.length();
        return length >= 0 && length <= 3;
    }

    /**
     * Validate Emergency service category list
     *
     * @param categories Emergency service category list to validate
     * @return {@code true} if the category list is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyServiceCategoryList(List<Integer> categories) {
        if (categories == null) {
            return false;
        }
        if (categories.contains(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED)) {
            return categories.size() == 1;
        }
        for (int category : categories) {
            if (!EMERGENCY_SERVICE_CATEGORY_SET.contains(category)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Emergency number source list
     *
     * @param categories Emergency number source list to validate
     * @return {@code true} if the source list is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberSourceList(List<Integer> sources) {
        if (sources == null) {
            return false;
        }
        for (int source : sources) {
            if (!EMERGENCY_NUMBER_SOURCE_SET.contains(source)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate Emergency call routing.
     *
     * @param routing Emergency call routing to validate
     * @return {@code true} if the emergency call routing is valid; {@code false} otherwise.
     */
    private static boolean validateEmergencyCallRouting(int routing) {
        return routing >= EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN
                && routing <= (EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY
                | EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
    }

    /**
     * Valid the emergency number should be at least from a valid source.
     *
     * @param emergencyNumber Emergency number to verify
     * @return {@code true} if the emergency number is from any source; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberFromAnySource(EmergencyNumber emergencyNumber) {
        boolean isFromAnySource = false;
        for (int possibleSourceValue = EMERGENCY_NUMBER_SOURCE_RIL_ECCLIST;
                possibleSourceValue <= (EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT);
                possibleSourceValue++) {
            if (emergencyNumber.isFromSources(possibleSourceValue)) {
                isFromAnySource = true;
                break;
            }
        }
        return isFromAnySource;
    }

    /**
     * Valid the emergency number should be at least in a valid category.
     *
     * @param emergencyNumber Emergency number to verify
     * @return {@code true} if it is in any category; {@code false} otherwise.
     */
    private static boolean validateEmergencyNumberInAnyCategory(EmergencyNumber emergencyNumber) {
        boolean isInAnyCategory = false;
        for (int possibleCategoryValue = EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
                possibleCategoryValue <= (EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC);
                possibleCategoryValue++) {
            if (emergencyNumber.isInEmergencyServiceCategories(possibleCategoryValue)) {
                isInAnyCategory = true;
                break;
            }
        }
        return isInAnyCategory;
    }

    @SuppressWarnings("SelfComparison") // TODO: Fix me
    private static boolean validateEmergencyNumberCompareTo(
            List<EmergencyNumber> emergencyNumberList) {
        if (emergencyNumberList == null) {
            return false;
        }
        if (emergencyNumberList.size() > 0) {
            EmergencyNumber emergencyNumber = emergencyNumberList.get(0);
            if (emergencyNumber.compareTo(emergencyNumber) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+' || c == 'N';
    }

    private Map.Entry<Integer, Integer> getValidSlotIndexAndPort() {
        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> {

                    List<UiccCardInfo> cardInfos = mTelephonyManager.getUiccCardsInfo();
                    Set<String> presentCards = Arrays.stream(mTelephonyManager.getUiccSlotsInfo())
                            .filter(Objects::nonNull)
                            .filter(port -> port.getPorts().stream().anyMatch(portInfo ->
                                    portInfo.isActive()))
                            .map(UiccSlotInfo::getCardId)
                            .filter(Objects::nonNull)
                            // hack around getUiccSlotsInfo not stripping trailing F
                            .map(s -> s.endsWith("F") ? s.substring(0, s.length() - 1) : s)
                            .collect(Collectors.toSet());
                    int slotIndex = -1;
                    int portIndex = -1;
                    for (UiccCardInfo cardInfo : cardInfos) {
                        for (UiccPortInfo portInfo : cardInfo.getPorts()) {
                            if (presentCards.contains(portInfo.getIccId())
                                    || presentCards.contains(cardInfo.getEid())) {
                                slotIndex = cardInfo.getPhysicalSlotIndex();
                                portIndex = portInfo.getPortIndex();
                                Log.d(TAG, "SlotIndex : " + slotIndex + " and portIndex :"
                                        + portIndex);
                                break;
                            }
                        }
                    }
                    if (slotIndex < 0) {
                        fail("Test must be run with SIM card inserted, presentCards = "
                                + presentCards + "cardinfos = " + cardInfos);
                    }
                    return Map.entry(slotIndex, portIndex);
                });
    }

    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException while waiting: " + e);
        }
    }

    /**
     * Verify that the phone is supporting the action of setForbiddenPlmn.
     *
     * @return whether to proceed the test
     */
    private boolean supportSetFplmn() {
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            return false;
        }
        return mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
    }

    /**
     * Verify that the phone is supporting the action of setForbiddenPlmn.
     *
     * @return whether to proceed the test
     */
    private boolean test() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return false;
        }
        return mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
    }

    private static int makeRadioVersion(int major, int minor) {
        if (major < 0 || minor < 0) return 0;
        return major * 100 + minor;
    }

    private Executor mSimpleExecutor = Runnable::run;

    private static MockSignalStrengthsTelephonyCallback mMockSignalStrengthsTelephonyCallback;

    private class MockSignalStrengthsTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (!mOnSignalStrengthsChanged) {
                synchronized (mLock) {
                    mOnSignalStrengthsChanged = true;
                    mLock.notify();
                }
            }
        }
    }

    @Test
    public void testRegisterTelephonyCallbackWithNonLooper() throws Throwable {
        mMockSignalStrengthsTelephonyCallback = new MockSignalStrengthsTelephonyCallback();

        // Test register, generates an mOnSignalStrengthsChanged event
        mTelephonyManager.registerTelephonyCallback(mSimpleExecutor,
                mMockSignalStrengthsTelephonyCallback);

        synchronized (mLock) {
            if (!mOnSignalStrengthsChanged) {
                mLock.wait(TOLERANCE);
            }
        }
        assertTrue("Test register, mOnSignalStrengthsChanged should be true.",
                mOnSignalStrengthsChanged);

        // Test unregister
        mOnSignalStrengthsChanged = false;
        // unregister again, to make sure doing so does not call the listener
        mTelephonyManager.unregisterTelephonyCallback(mMockSignalStrengthsTelephonyCallback);

        assertFalse("Test unregister, mOnSignalStrengthsChanged should be false.",
                mOnSignalStrengthsChanged);
    }

    private static MockCellInfoListener mMockCellInfoListener;

    private class MockCellInfoListener extends TelephonyCallback
            implements TelephonyCallback.CellInfoListener {
        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            if (!mOnCellInfoChanged) {
                synchronized (mLock) {
                    mOnCellInfoChanged = true;
                    mLock.notify();
                }
            }
        }
    }

    @Test
    public void testRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        } else {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                // TODO: temp workaround, need to adjust test to for CDMA
                return;
            }
        }

        grantLocationPermissions();

        TestThread t = new TestThread(() -> {
            Looper.prepare();
            mMockCellInfoListener = new MockCellInfoListener();
            synchronized (mLock) {
                mLock.notify(); // listener is ready
            }

            Looper.loop();
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE); // wait for listener
        }

        // Test register
        synchronized (mLock) {
            // .registerTelephonyCallback generates an onCellLocationChanged event
            mTelephonyManager.registerTelephonyCallback(mSimpleExecutor, mMockCellInfoListener);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                    mOnCellInfoChanged);
        }

        synchronized (mLock) {
            mOnCellInfoChanged = false;

            CellInfoResultsCallback resultsCallback = new CellInfoResultsCallback();
            mTelephonyManager.requestCellInfoUpdate(mSimpleExecutor, resultsCallback);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                    mOnCellInfoChanged);
        }

        // unregister the listener
        mTelephonyManager.unregisterTelephonyCallback(mMockCellInfoListener);
        Thread.sleep(TOLERANCE);

        // Test unregister
        synchronized (mLock) {
            mOnCellInfoChanged = false;
            // unregister again, to make sure doing so does not call the listener
            mTelephonyManager.unregisterTelephonyCallback(mMockCellInfoListener);
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            assertFalse("Test unregister, mOnCellLocationChangedCalled should be false.",
                    mOnCellInfoChanged);
        }
    }

    private class CellInfoResultsCallback extends TelephonyManager.CellInfoCallback {
        public List<CellInfo> cellInfo;

        @Override
        public synchronized void onCellInfo(List<CellInfo> cellInfo) {
            this.cellInfo = cellInfo;
            notifyAll();
        }

        public synchronized void wait(int millis) throws InterruptedException {
            if (cellInfo == null) {
                super.wait(millis);
            }
        }
    }

    private void setAppOpsPermissionAllowed(boolean allowed, String op) {
        AppOpsManager appOpsManager = getContext().getSystemService(AppOpsManager.class);
        int mode = allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.opToDefaultMode(op);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                appOpsManager, (appOps) -> appOps.setUidMode(op, Process.myUid(), mode));
    }

    /**
     * Verifies that {@link TelephonyManager#getNetworkSlicingConfiguration()} does not throw any
     * exception
     */
    @Test
    public void testGetNetworkSlicingConfiguration() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        CompletableFuture<NetworkSlicingConfig> resultFuture = new CompletableFuture<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.getNetworkSlicingConfiguration(mSimpleExecutor, resultFuture::complete));
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#checkCarrierPrivilegesForPackage"})
    public void testCheckCarrierPrivilegesForPackageEnforcesReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            mTelephonyManager.checkCarrierPrivilegesForPackage(mSelfPackageName);
        } catch (SecurityException e) {
            fail("TelephonyManager#checkCarrierPrivilegesForPackage requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testCheckCarrierPrivilegesForPackageThrowsExceptionWithoutReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.checkCarrierPrivilegesForPackage(mSelfPackageName);
            fail("TelephonyManager#checkCarrierPrivilegesForPackage must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#checkCarrierPrivilegesForPackageAnyPhone"})
    public void testCheckCarrierPrivilegesForPackageAnyPhone() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(mSelfPackageName);
            fail("TelephonyManager#checkCarrierPrivilegesForPackageAnyPhone must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException expected) {
        }

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(mSelfPackageName);
        } catch (SecurityException e) {
            fail("TelephonyManager#checkCarrierPrivilegesForPackageAnyPhone should not throw "
                    + "SecurityException with READ_PRIVILEGED_PHONE_STATE permission");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getCarrierPackageNamesForIntentAndPhone"})
    public void testGetCarrierPackageNamesForIntentAndPhoneEnforcesReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            Intent intent = new Intent();
            int phoneId = 1;
            mTelephonyManager.getCarrierPackageNamesForIntentAndPhone(intent, phoneId);
        } catch (SecurityException e) {
            fail("TelephonyManager#getCarrierPackageNamesForIntentAndPhone requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetCarrierPackageNamesForIntentAndPhoneThrowsExceptionWithoutReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            Intent intent = new Intent();
            int phoneId = 1;
            mTelephonyManager.getCarrierPackageNamesForIntentAndPhone(intent, phoneId);
            fail("TelephonyManager#getCarrierPackageNamesForIntentAndPhone must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getPackagesWithCarrierPrivileges"})
    public void testGetPackagesWithCarrierPrivilegesEnforcesReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            mTelephonyManager.getPackagesWithCarrierPrivileges();
        } catch (SecurityException e) {
            fail("TelephonyManager#getPackagesWithCarrierPrivileges requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetPackagesWithCarrierPrivilegesThrowsExceptionWithoutReadPrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            mTelephonyManager.getPackagesWithCarrierPrivileges();
            fail("TelephonyManager#getPackagesWithCarrierPrivileges must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getSimSlotMapping",
            "android.telephony.TelephonyManager#setSimSlotMapping"})
    public void testSimSlotMapping() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        Collection<UiccSlotMapping> simSlotMapping = mTelephonyManager.getSimSlotMapping();
        // passing slotMapping combination
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.MODIFY_PHONE_STATE);
        try {
            mTelephonyManager.setSimSlotMapping(simSlotMapping);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // if HAL version is less than 2.0, vendors may not have implemented API,
            // skipping the failure.
            if (mConfigHalVersion >= RADIO_HAL_VERSION_2_0) {
                fail("Not Expected Fail, Error in setSimSlotMapping :" + e);
            }
        }

        List<UiccSlotMapping> slotMappingList = new ArrayList<>();
        // invalid logicalSlotIndex - Fail
        UiccSlotMapping slotMapping1 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                SubscriptionManager.INVALID_PHONE_INDEX /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping2 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                0, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping1);
        slotMappingList.add(slotMapping2);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalStateException, invalid UiccSlotMapping data found");
        } catch (IllegalStateException e) {
            //expected
        }
        slotMappingList.clear();

        // Duplicate logicalSlotIndex - Fail
        UiccSlotMapping slotMapping3 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping4 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                0, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping3);
        slotMappingList.add(slotMapping4);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } catch (IllegalArgumentException e) {
            //expected
        }
        slotMappingList.clear();

        // Duplicate {portIndex+physicalSlotIndex} - Fail
        UiccSlotMapping slotMapping5 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping6 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                1 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping5);
        slotMappingList.add(slotMapping6);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } catch (IllegalArgumentException e) {
            //expected
        }
        slotMappingList.clear();

        // Duplicate {portIndex+physicalSlotIndex+logicalSlotIndex} - Fail
        UiccSlotMapping slotMapping7 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        UiccSlotMapping slotMapping8 = new UiccSlotMapping(
                TelephonyManager.DEFAULT_PORT_INDEX, /*portIndex*/
                1, /*physicalSlotIndex*/
                0 /*logicalSlotIndex*/);
        slotMappingList.add(slotMapping7);
        slotMappingList.add(slotMapping8);
        try {
            mTelephonyManager.setSimSlotMapping(slotMappingList);
            fail("Expected IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } catch (IllegalArgumentException e) {
            //expected
        }
        slotMappingList.clear();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();

    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getUiccSlotsInfo"})
    public void getUiccSlotInfoTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        UiccSlotInfo[] slotInfos = mTelephonyManager.getUiccSlotsInfo();

        if (slotInfos == null) {
            return;
        }

        // Call below methods to make sure it doesn't crash.
        for (UiccSlotInfo slotInfo : slotInfos) {
            slotInfo.getIsEuicc();
            slotInfo.getCardId();
            slotInfo.getCardStateInfo();
            slotInfo.getIsExtendedApduSupported();
            slotInfo.isRemovable();
            for (UiccPortInfo portInfo :slotInfo.getPorts()) {
                portInfo.isActive();
                portInfo.getIccId();
                portInfo.getLogicalSlotIndex();
                portInfo.getPortIndex();
            }
        }

        for (UiccSlotInfo slotInfo : slotInfos) {
            // Make sure portIndex value is less than the number of ports available.
            int count = slotInfo.getPorts().stream().filter(portInfo
                    -> portInfo.getPortIndex() >= slotInfo.getPorts().size()).toList().size();
            if (count > 0) {
                fail("port index should be less than the total number of ports available");
            }
            // Make sure both port indexes are unique.
            for (int index = 0; index < slotInfo.getPorts().size(); index++) {
                final int portIndex = index;
                assertEquals(1, slotInfo.getPorts().stream().filter(
                        portInfo -> portInfo.getPortIndex() == portIndex).toList().size());
            }
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testGetUiccSlotInfosFailsWithoutReadPhoneStatePrivilege() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            mTelephonyManager.getUiccSlotsInfo();
            fail("TelephonyManager#getUiccSlotsInfo must be protected "
                    + "with READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void getSimSlotMappingTestReadPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        try {
            Collection<UiccSlotMapping> simSlotMapping = mTelephonyManager.getSimSlotMapping();
            fail("Expected SecurityException, no READ_PRIVILEGED_PHONE_STATE permission");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testSetAllowedNetworkTypesForReason_ignoreInvalidNetworkType() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // NETWORK_TYPE_BITMASK_LTE_CA is invalid, should be converted into NETWORK_TYPE_BITMASK_LTE
        long invalidAllowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                | TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA;
        long expectedAllowedNetworkTypes = TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        try {
            mIsAllowedNetworkTypeChanged = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager,
                    (tm) -> tm.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER,
                            invalidAllowedNetworkTypes));

            long deviceAllowedNetworkTypes = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> {
                        return tm.getAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER);
                    }
            );
            assertEquals(expectedAllowedNetworkTypes, deviceAllowedNetworkTypes);
        } catch (SecurityException se) {
            fail("testIgnoreInvalidNetworkType: SecurityException not expected");
        }
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#getSimSlotMapping"})
    public void getSimSlotMappingTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        try {
            Collection<UiccSlotMapping> simSlotMapping = mTelephonyManager.getSimSlotMapping();
            assertTrue(isSlotMappingValid(simSlotMapping));
        } catch (IllegalArgumentException e) {
            fail("IllegalArgumentException, Duplicate UiccSlotMapping data found");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
    private static boolean isSlotMappingValid(@NonNull Collection<UiccSlotMapping> slotMapping) {
        // Grouping the collection by logicalSlotIndex, finding different entries mapping to the
        // same logical slot
        Map<Integer, List<UiccSlotMapping>> slotMappingInfo = slotMapping.stream().collect(
                Collectors.groupingBy(UiccSlotMapping::getLogicalSlotIndex));
        for (Map.Entry<Integer, List<UiccSlotMapping>> entry : slotMappingInfo.entrySet()) {
            List<UiccSlotMapping> logicalSlotMap = entry.getValue();
            if (logicalSlotMap.size() > 1) {
                // duplicate logicalSlotIndex found
                return false;
            }
        }
        return true;
    }

    public static class ServiceStateRadioStateListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener,
            TelephonyCallback.RadioPowerStateListener {
        private static final long TIMEOUT_TO_WAIT_FOR_DESIRED_STATE =
                TimeUnit.SECONDS.toMillis(20);
        private final Object mPowerStateLock = new Object();
        private final Object mServiceStateLock = new Object();
        ServiceState mServiceState;
        int mDesireServiceState;

        int mRadioPowerState;
        int mDesireRadioPowerState;

        public ServiceStateRadioStateListener(ServiceState serviceState, int radioPowerState) {
            mServiceState = serviceState;
            mRadioPowerState = radioPowerState;
            mDesireRadioPowerState = radioPowerState;
        }

        @Override
        public void onServiceStateChanged(ServiceState ss) {
            Log.d(TAG, "onServiceStateChanged to " + ss);
            synchronized (mServiceStateLock) {
                mServiceState = ss;
                if (ss.getState() == mDesireServiceState) {
                    mServiceStateLock.notify();
                }
            }
        }

        @Override
        public void onRadioPowerStateChanged(int radioState) {
            Log.d(TAG, "onRadioPowerStateChanged to " + radioState);
            synchronized (mPowerStateLock) {
                mRadioPowerState = radioState;
                if (radioState == mDesireRadioPowerState) {
                    mPowerStateLock.notify();
                }
            }
        }

        public void waitForRadioStateIntent(int desiredRadioState) {
            Log.d(TAG, "waitForRadioStateIntent: desiredRadioState=" + desiredRadioState);
            synchronized (mPowerStateLock) {
                mDesireRadioPowerState = desiredRadioState;
                /**
                 * Since SST sets waiting time up to 10 seconds for the power off radio, the
                 * RadioStateIntent timer extends the wait time up to 20 seconds here as well.
                 */
                waitForDesiredState(mPowerStateLock, desiredRadioState,
                        () -> mRadioPowerState, true);
            }
        }

        public void waitForServiceStateIntent(int desiredServiceState, boolean failOnTimeOut) {
            Log.d(TAG, "waitForServiceStateIntent: desiredServiceState=" + desiredServiceState);
            synchronized (mServiceStateLock) {
                mDesireServiceState = desiredServiceState;
                waitForDesiredState(mServiceStateLock, desiredServiceState,
                        () -> mServiceState.getState(), failOnTimeOut);
            }
        }

        private void waitForDesiredState(@NonNull Object lock, int desiredState,
                @NonNull IntSupplier currentStateSupplier, boolean failOnTimeOut) {
            synchronized (lock) {
                long now = SystemClock.elapsedRealtime();
                long deadline = now + TIMEOUT_TO_WAIT_FOR_DESIRED_STATE;
                while (currentStateSupplier.getAsInt() != desiredState && now < deadline) {
                    try {
                        lock.wait(TIMEOUT_TO_WAIT_FOR_DESIRED_STATE);
                    } catch (Exception e) {
                        if (failOnTimeOut) {
                            fail(e.getMessage());
                        } else {
                            Log.w(TAG, "waitForDesiredState: e=" + e);
                        }
                    }
                    now = SystemClock.elapsedRealtime();
                }
            }
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#setVoiceServiceStateOverride"})
    @RequiresFlagsEnabled(
            com.android.server.telecom.flags.Flags.FLAG_TELECOM_RESOLVE_HIDDEN_DEPENDENCIES)
    public void testSetVoiceServiceStateOverride() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                mTelephonyManager.getServiceState(), mTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));

        boolean turnedRadioOff = false;
        boolean setServiceStateOverride = false;
        try {
            if (mTelephonyManager.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
                Log.i(TAG, "testSetVoiceServiceStateOverride: turning radio off to force OOS");
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setRadioPower(false), permission.MODIFY_PHONE_STATE);
                callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
                callback.waitForServiceStateIntent(ServiceState.STATE_POWER_OFF, true);
                assertEquals(TelephonyManager.RADIO_POWER_OFF, callback.mRadioPowerState);
                assertEquals(ServiceState.STATE_POWER_OFF, callback.mServiceState.getState());
                turnedRadioOff = true;
            }
            // This could be OUT_OF_SERVICE or POWER_OFF, it doesn't really matter for this test as
            // long as it's not IN_SERVICE
            ServiceState serviceState = mTelephonyManager.getServiceState();
            int retry = 0;
            while ((serviceState == null
                    || serviceState.getState() == ServiceState.STATE_IN_SERVICE) && retry < 3) {
                serviceState = mTelephonyManager.getServiceState();
                retry++;
                // wait up to 3s for radio power off/out of service
                waitForMs(1000);
            }
            int originalServiceState = serviceState != null ? serviceState.getState()
                    : callback.mServiceState.getState();
            Log.i(TAG, "testSetVoiceServiceStateOverride: originalSS = " + originalServiceState);
            assertNotEquals(ServiceState.STATE_IN_SERVICE, originalServiceState);

            // Telecom will sometimes remove the override after radio reboots.
            // Retry setting the override to prevent flaky test failures.
            int listenerState = callback.mServiceState.getState();
            int telephonyManagerState = originalServiceState;
            retry = 0;
            while ((listenerState != ServiceState.STATE_IN_SERVICE
                    || telephonyManagerState != ServiceState.STATE_IN_SERVICE) && retry < 3) {
                // We should see the override in both ServiceStateListener and getServiceState
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setVoiceServiceStateOverride(true),
                        permission.BIND_TELECOM_CONNECTION_SERVICE);
                callback.waitForServiceStateIntent(ServiceState.STATE_IN_SERVICE, false);
                setServiceStateOverride = true;

                serviceState = mTelephonyManager.getServiceState();
                if (serviceState != null) {
                    telephonyManagerState = serviceState.getState();
                }
                listenerState = callback.mServiceState.getState();
                retry++;
            }
            assertEquals(ServiceState.STATE_IN_SERVICE, listenerState);
            assertEquals(ServiceState.STATE_IN_SERVICE, telephonyManagerState);

            // When we take away the override, things flip back to the original state since there
            // were no other material changes made to the device that would impact ServiceState
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    tm -> tm.setVoiceServiceStateOverride(false),
                    permission.BIND_TELECOM_CONNECTION_SERVICE);
            callback.waitForServiceStateIntent(originalServiceState, true);
            assertEquals(originalServiceState, callback.mServiceState.getState());
            assertEquals(originalServiceState, mTelephonyManager.getServiceState().getState());
        } finally {
            if (setServiceStateOverride) {
                // No harm in calling this again if we already did, but call just in case we failed
                // an assertion related to setOverride(true)
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setVoiceServiceStateOverride(false),
                        permission.BIND_TELECOM_CONNECTION_SERVICE);
            }
            if (turnedRadioOff) {
                // Turn the radio back on and wait for ServiceState to become stable again, so we
                // don't cause flakes in other tests
                Log.i(TAG, "testSetVoiceServiceStateOverride: turning radio back on");
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        tm -> tm.setRadioPower(true), permission.MODIFY_PHONE_STATE);
                callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
                callback.waitForServiceStateIntent(ServiceState.STATE_IN_SERVICE, true);
                assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);
                assertEquals(ServiceState.STATE_IN_SERVICE, callback.mServiceState.getState());
            }
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#requestRadioPowerOffForReason",
            "android.telephony.TelephonyManager#clearRadioPowerOffForReason",
            "android.telephony.TelephonyManager#getRadioPowerOffReasons"})
    public void testSetRadioPowerForReasonNearbyDevice() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                mTelephonyManager.getServiceState(), mTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));

        boolean turnedRadioOn = false;
        if (mTelephonyManager.getRadioPowerState() == TelephonyManager.RADIO_POWER_OFF) {
            Log.i(TAG, "testSetRadioPowerForReasonNearbyDevice:"
                    + "turning on radio since it is off");
            turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);
            turnedRadioOn = true;
        }

        Log.i(TAG, "testSetRadioPowerForReasonNearbyDevice:"
                + "turning radio off due to nearby device ...");
        turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);
        assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);

        Log.i(TAG, "testSetRadioPowerForReasonNearbyDevice: turning on airplane mode ...");
        turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_USER);
        assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_USER);

        Log.i(TAG, "testSetRadioPowerForReasonNearbyDevice: turning off airplane mode ...");
        turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_USER);
        assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);

        Log.i(TAG, "testSetRadioPowerForReasonNearbyDevice:"
                + " turning on radio due to nearby device...");
        turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);
        assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);

        if (turnedRadioOn) {
            Log.i(TAG, "testSetRadioPowerForReasonNearbyDevice: turning radio back off");
            turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_USER);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#requestRadioPowerOffForReason",
            "android.telephony.TelephonyManager#clearRadioPowerOffForReason",
            "android.telephony.TelephonyManager#getRadioPowerOffReasons",
            "android.telephony.TelephonyManager#setRadioEnabled"})
    public void testSetRadioPowerForReasonCarrier() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                mTelephonyManager.getServiceState(), mTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));

        boolean turnedRadioOn = false;
        if (mTelephonyManager.getRadioPowerState() == TelephonyManager.RADIO_POWER_OFF) {
            Log.i(TAG, "testSetRadioPowerForReasonCarrier: turning on radio since it is off");
            turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);
            turnedRadioOn = true;
        }

        Log.i(TAG, "testSetRadioPowerForReasonCarrier: turning radio off due to carrier ...");
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setRadioEnabled(false), permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
        assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_CARRIER);

        Log.i(TAG, "testSetRadioPowerForReasonCarrier: turning on airplane mode ...");
        turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_USER);
        assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_CARRIER);

        Log.i(TAG, "testSetRadioPowerForReasonCarrier: turning off airplane mode ...");
        turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_USER);
        assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_CARRIER);

        Log.i(TAG, "testSetRadioPowerForReasonCarrier: turning on radio due to carrier...");
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.setRadioEnabled(true), permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
        assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);

        if (turnedRadioOn) {
            Log.i(TAG, "testSetRadioPowerForReasonCarrier: turning radio back off");
            turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            assertRadioOffWithReason(callback, TelephonyManager.RADIO_POWER_REASON_USER);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#getCellBroadcastIdRanges",
            "android.telephony.TelephonyManager#setCellBroadcastIdRanges"})
    public void testSetCellBroadcastIdRanges() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING));
        }

        final List<CellBroadcastIdRange> ranges = new ArrayList<>();
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true));

        // Permission check
        assertThrows(SecurityException.class, () ->
                mTelephonyManager.getCellBroadcastIdRanges());

        assertThrows(SecurityException.class, () ->
                mTelephonyManager.setCellBroadcastIdRanges(ranges,
                        AsyncTask.SERIAL_EXECUTOR, (result) -> {}));

        final List<Integer> resultsExpected = new ArrayList<>();
        resultsExpected.add(TelephonyManager.CELL_BROADCAST_RESULT_UNKNOWN);
        resultsExpected.add(TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS);
        resultsExpected.add(TelephonyManager.CELL_BROADCAST_RESULT_UNSUPPORTED);
        resultsExpected.add(TelephonyManager.CELL_BROADCAST_RESULT_FAIL_CONFIG);
        resultsExpected.add(TelephonyManager.CELL_BROADCAST_RESULT_FAIL_ACTIVATION);

        final List<CellBroadcastIdRange> rangesExpected = ShellIdentityUtils
                .invokeMethodWithShellPermissions(
                        mTelephonyManager, (tm) -> tm.getCellBroadcastIdRanges());
        CountDownLatch latch = new CountDownLatch(1);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCellBroadcastIdRanges(ranges, AsyncTask.SERIAL_EXECUTOR,
                        (result) -> {
                            latch.countDown();
                            // The result must be a valid value
                            assertTrue("Got " + result + " not in expected set",
                                    resultsExpected.contains(result));
                            // The range will be updated when result is success
                            if (result == TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS) {
                                rangesExpected.clear();
                                rangesExpected.addAll(ranges);
                            }
                        }));
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        List<CellBroadcastIdRange> ranges2 = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getCellBroadcastIdRanges());

        assertEquals(rangesExpected, ranges2);

        ranges.add(new CellBroadcastIdRange(999, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, false));
        assertThrows(IllegalArgumentException.class, () ->
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        (tm) -> tm.setCellBroadcastIdRanges(ranges, null, null)));
    }

    private void turnRadioOn(ServiceStateRadioStateListener callback, int reason) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.clearRadioPowerOffForReason(reason), permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
    }

    private void turnRadioOff(ServiceStateRadioStateListener callback, int reason) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.requestRadioPowerOffForReason(reason), permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
    }

    private void assertRadioOffWithReason(ServiceStateRadioStateListener callback, int reason) {
        assertEquals(TelephonyManager.RADIO_POWER_OFF, callback.mRadioPowerState);

        Set<Integer> radioPowerOffReasons = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager,
                tm -> tm.getRadioPowerOffReasons(), permission.READ_PRIVILEGED_PHONE_STATE);
        assertTrue(radioPowerOffReasons.contains(reason));
    }

    private void assertRadioOffWithReason(TelephonyManager telephonyManager,
            ServiceStateRadioStateListener callback, int reason) {
        assertEquals(TelephonyManager.RADIO_POWER_OFF, callback.mRadioPowerState);

        Set<Integer> radioPowerOffReasons = ShellIdentityUtils.invokeMethodWithShellPermissions(
                telephonyManager,
                tm -> tm.getRadioPowerOffReasons(), permission.READ_PRIVILEGED_PHONE_STATE);
        assertTrue(radioPowerOffReasons.contains(reason));
    }

    /**
     * Verifies that {@link TelephonyManager#getImsPrivateUserIdentity()} does not throw any
     * exception when called and has the correct permissions.
     */
    @Ignore("TelephonyManager#getImsPrivateUserIdentity()" + " is hidden. Internal use only.")
    @Test
    public void getImsPrivateUserIdentity() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        // make sure not to face any permission problem while calling the API
        try {
            setAppOpsPermissionAllowed(true, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
            mTelephonyManager.getImsPrivateUserIdentity();
        } catch (IllegalStateException e) {
            // expected in case SIM do not support ISIM
        } catch (SecurityException secExp) {
            fail();
        } finally {
            setAppOpsPermissionAllowed(false, OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);
        }
    }

    /**
     * Verifies that {@link TelephonyManager#getImsPrivateUserIdentity()} does throw
     * SecurityException when required permissions are not granted.
     */
    @Ignore("TelephonyManager#getImsPrivateUserIdentity()" + " is hidden. Internal use only.")
    @Test
    public void getImsPrivateUserIdentity_NoPermissionGranted() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        try {
            mTelephonyManager.getImsPrivateUserIdentity();
            fail(); // if no SecurityException then it fails()
        } catch (IllegalStateException e) {
            // expected in case SIM do not support ISIM
        } catch (SecurityException secExp) {
            // expected as API has no permission to fetch ISIM
        }
    }

    /**
     * Verifies that {@link TelephonyManager#getImsPublicUserIdentities()} does not throw any
     * exception when granted with READ_PRIVILEGED_PHONE_STATE permission.
     */
    @Ignore("TelephonyManager#getImsPublicUserIdentities()" + " is hidden. Internal use only.")
    @Test
    public void getImsPublicUserIdentities_ReadPrivilegedPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        // make sure not to face any permission problem while calling the API
        try {
            List<Uri> impuList = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, tm -> tm.getImsPublicUserIdentities(),
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            assertNotNull(impuList);
            for (Uri impu : impuList) {
                assertTrue(impu.getScheme().equalsIgnoreCase("sip"));
            }
        } catch (IllegalStateException e) {
            // expected in case SIM do not support ISIM
            fail();
        }
    }

    /**
     * Verifies that {@link TelephonyManager#getImsPublicUserIdentities()} does not throw any
     * exception when granted with READ_PHONE_NUMBERS permission.
     */
    @Ignore("TelephonyManager#getImsPublicUserIdentities()" + " is hidden. Internal use only.")
    @Test
    public void getImsPublicUserIdentities_ReadPhoneNumberPermission() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        // make sure not to face any permission problem while calling the API
        try {
            List<Uri> impuList = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, tm -> tm.getImsPublicUserIdentities(),
                    Manifest.permission.READ_PHONE_NUMBERS);
            assertNotNull(impuList);
            for (Uri impu : impuList) {
                assertTrue(impu.getScheme().equalsIgnoreCase("sip"));
            }
        } catch (IllegalStateException e) {
            // expected in case SIM do not support ISIM
        }
    }

    /**
     * Verifies that {@link TelephonyManager#getImsPublicUserIdentities()} does throw
     * SecurityException when called with out any permissions granted.
     */
    @Ignore("TelephonyManager#getImsPublicUserIdentities()" + " is hidden. Internal use only.")
    @Test
    public void getImsPublicUserIdentities_NoPermissionGranted() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        try {
            if (hasReadContactsPermission(mSelfPackageName)) {
                InstrumentationRegistry.getInstrumentation().getUiAutomation().
                        revokeRuntimePermission(mSelfPackageName,
                                "android.permission.READ_PHONE_NUMBERS");
            }
            List<Uri> impuList = mTelephonyManager.getImsPublicUserIdentities();
            fail(); // if no SecurityException then it fails()
        } catch (IllegalStateException e) {
            // expected in case SIM do not support ISIM
        } catch (SecurityException secExp) {
            // expected as caller is not granted with required permissions
        }
    }

    private boolean hasReadContactsPermission(String pkgName) {
        return mPackageManager.checkPermission(Manifest.permission.READ_CONTACTS, pkgName)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Test
    public void testLastKnownCountryIso() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        CountryChangedReceiver countryChangedReceiver = new CountryChangedReceiver();

        getContext().registerReceiver(countryChangedReceiver,
                new IntentFilter(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED),
                Context.RECEIVER_EXPORTED);

        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                mTelephonyManager.getServiceState(), mTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));

        int initialRadioState = mTelephonyManager.getRadioPowerState();
        try {
            if (initialRadioState == TelephonyManager.RADIO_POWER_OFF) {
                Log.i(TAG, "testLastKnownCountryIso:"
                        + "turning on radio since it is off");
                turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_USER);
                assertEquals(TelephonyManager.RADIO_POWER_ON, callback.mRadioPowerState);
            }

            String countryCode = mTelephonyManager.getNetworkCountryIso();
            if (TextUtils.isEmpty(countryCode)) {
                Log.i(TAG, "testLastKnownCountryIso: country iso is already known. Not testable.");
                // Not testable.
                return;
            }

            Log.i(TAG, "testLastKnownCountryIso:"
                    + "turning radio off due to testing last known country ...");
            turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            try {
                countryChangedReceiver.waitForIntent();
                assertThat(countryChangedReceiver.getExtras().getString(
                        TelephonyManager.EXTRA_NETWORK_COUNTRY)).isEmpty();
                assertThat(countryChangedReceiver.getExtras().getString(
                        TelephonyManager.EXTRA_LAST_KNOWN_NETWORK_COUNTRY)).isEqualTo(countryCode);
                Log.i(TAG, "testLastKnownCountryIso: country code \"" + countryCode
                        + "\" matched.");
            } catch (Exception e) {
                fail(e.getMessage());
            }
        } finally {
            if (initialRadioState == TelephonyManager.RADIO_POWER_OFF
                    && mTelephonyManager.getRadioPowerState() != TelephonyManager.RADIO_POWER_OFF) {
                Log.i(TAG, "testLastKnownCountryIso: turning radio back off");
                turnRadioOff(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            } else if (initialRadioState == TelephonyManager.RADIO_POWER_ON
                    && mTelephonyManager.getRadioPowerState() != TelephonyManager.RADIO_POWER_ON) {
                Log.i(TAG, "testLastKnownCountryIso: turning radio back on");
                turnRadioOn(callback, TelephonyManager.RADIO_POWER_REASON_USER);
            }
            getContext().unregisterReceiver(countryChangedReceiver);
        }
    }

    private static class CarrierInfo {
        final private int mCallerCarrierId;
        final private List<String> mSHAIdList;

        public CarrierInfo(int carrierId, List<String> SHAIds) {
            mCallerCarrierId = carrierId;
            mSHAIdList = SHAIds;
        }

        public int getCallerCarrierId() {
            return mCallerCarrierId;
        }

        public List<String> getSHAIdList() {
            return mSHAIdList;
        }
    }

    private static final String CALLER_SHA_1_ID = "callerSHA1Id";
    private static final String CALLER_CARRIER_ID = "carrierId";
    private CarrierInfo parseJsonForCallerInfo(String callerPackage, JSONObject dataJson) {
        try {
            if (dataJson != null && callerPackage != null) {
                JSONObject callerJSON = dataJson.getJSONObject(callerPackage.trim());
                JSONArray callerJSONArray = callerJSON.getJSONArray(CALLER_SHA_1_ID);
                int carrierId = callerJSON.getInt(CALLER_CARRIER_ID);
                List<String> appSignatures = new ArrayList<>();
                for (int index = 0; index < callerJSONArray.length(); index++) {
                    appSignatures.add((String) callerJSONArray.get(index));
                }
                return new CarrierInfo(carrierId, appSignatures);
            }
        } catch (JSONException ex) {
            Log.e(TAG, "getCallerSignatureInfo: JSONException = " + ex);
        }
        return null;
    }

    @Test
    public void testCarrierRestrictionStatusAllowList() throws JSONException {
        JSONObject testJson = new JSONObject(CARRIER_RESTRICTION_OPERATOR_DETAILS);
        Set<String> testPkgSet = testJson.keySet();
        testPkgSet.remove("_comment");
        for (String srcPkg : testPkgSet) {
            final CarrierInfo testCarrierInfo = parseJsonForCallerInfo(srcPkg, testJson);
            List<String> shaIdList = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getShaIdFromAllowList(srcPkg,
                            testCarrierInfo.mCallerCarrierId));

            if (shaIdList == null || shaIdList.isEmpty()) {
                Log.d(TAG, "shaIdList is empty");
                fail();
            }
            assertTrue(shaIdList.equals(testCarrierInfo.getSHAIdList()));
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#requestRadioPowerOffForReason",
            "android.telephony.TelephonyManager#clearRadioPowerOffForReason",
            "android.telephony.TelephonyManager#getRadioPowerOffReasons"})
    public void testSetRadioPowerForMultiSimDevice() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mTelephonyManager.isMultiSimSupported() != TelephonyManager.MULTISIM_ALLOWED) {
            Log.d(TAG, "testSetRadioPowerForMultiSimDevice: Multi SIM is not supported");
            return;
        }
        Integer secondTestSubId = getSecondTestSubId();
        if (secondTestSubId == null) {
            Log.d(TAG, "Need at least 2 active subscriptions to run this test");
            return;
        }
        Log.d(TAG, "testSetRadioPowerForMultiSimDevice: secondTestSubId=" + secondTestSubId);

        TelephonyManager secondTelephonyManager = getContext().getSystemService(
                TelephonyManager.class).createForSubscriptionId(secondTestSubId);
        ServiceStateRadioStateListener callbackForFirstSub = new ServiceStateRadioStateListener(
                mTelephonyManager.getServiceState(), mTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callbackForFirstSub));
        ServiceStateRadioStateListener callbackForSecondSub =
                new ServiceStateRadioStateListener(secondTelephonyManager.getServiceState(),
                        secondTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(secondTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callbackForSecondSub));

        boolean turnedRadioOn = false;
        if (mTelephonyManager.getRadioPowerState() == TelephonyManager.RADIO_POWER_OFF) {
            Log.i(TAG, "testSetRadioPowerForMultiSimDevice:"
                    + "turning on radio since it is off");
            turnRadioOn(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_USER);
            assertEquals(TelephonyManager.RADIO_POWER_ON, callbackForFirstSub.mRadioPowerState);
            callbackForSecondSub.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
            assertEquals(TelephonyManager.RADIO_POWER_ON, callbackForSecondSub.mRadioPowerState);
            turnedRadioOn = true;
        }

        Log.i(TAG, "testSetRadioPowerForMultiSimDevice:"
                + "turning radio off due to nearby device ...");
        turnRadioOff(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);
        assertRadioOffWithReason(callbackForFirstSub,
                TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);
        callbackForSecondSub.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
        assertRadioOffWithReason(secondTelephonyManager, callbackForSecondSub,
                TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);

        Log.i(TAG, "testSetRadioPowerForMultiSimDevice: turning on airplane mode ...");
        turnRadioOff(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_USER);
        assertRadioOffWithReason(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_USER);
        callbackForSecondSub.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
        assertRadioOffWithReason(secondTelephonyManager, callbackForSecondSub,
                TelephonyManager.RADIO_POWER_REASON_USER);

        Log.i(TAG, "testSetRadioPowerForMultiSimDevice: turning off airplane mode ...");
        turnRadioOn(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_USER);
        assertRadioOffWithReason(callbackForFirstSub,
                TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);
        assertRadioOffWithReason(secondTelephonyManager, callbackForSecondSub,
                TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);

        Log.i(TAG, "testSetRadioPowerForMultiSimDevice:"
                + " turning on radio due to nearby device...");
        turnRadioOn(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_NEARBY_DEVICE);
        assertEquals(TelephonyManager.RADIO_POWER_ON, callbackForFirstSub.mRadioPowerState);
        callbackForSecondSub.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
        assertEquals(TelephonyManager.RADIO_POWER_ON, callbackForSecondSub.mRadioPowerState);

        if (turnedRadioOn) {
            Log.i(TAG, "testSetRadioPowerForMultiSimDevice: turning radio back off");
            turnRadioOff(callbackForFirstSub, TelephonyManager.RADIO_POWER_REASON_USER);
            callbackForSecondSub.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#isCellularIdentifierDisclosureNotificationsEnabled",
            "android.telephony.TelephonyManager#setEnableCellularIdentifierDisclosureNotifications"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_IDENTIFIER_DISCLOSURE_TRANSPARENCY)
    public void testSetEnableCellularIdentifierDisclosureNotifications() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mNetworkHalVersion < RADIO_HAL_VERSION_2_2) {
            Log.d(TAG,
                    "Skipping test since modem does not support IRadioNetwork HAL v2.2");
            return;
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setEnableCellularIdentifierDisclosureNotifications(true));
        boolean enabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isCellularIdentifierDisclosureNotificationsEnabled());
        assertTrue(enabled);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setEnableCellularIdentifierDisclosureNotifications(false));
        enabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isCellularIdentifierDisclosureNotificationsEnabled());
        assertFalse(enabled);
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#isCellularIdentifierDisclosureNotificationsEnabled",
            "android.telephony.TelephonyManager#setEnableCellularIdentifierDisclosureNotifications"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_IDENTIFIER_DISCLOSURE_TRANSPARENCY)
    public void testCellularIdentifierDisclosureNotificationsPermissions() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mNetworkHalVersion < RADIO_HAL_VERSION_2_2) {
            Log.d(TAG,
                    "Skipping test since modem does not support IRadioNetwork HAL v2.2");
            return;
        }

        assertThrows(SecurityException.class, () -> {
                    mTelephonyManager.setEnableCellularIdentifierDisclosureNotifications(true);
                }
        );

        assertThrows(SecurityException.class, () -> {
                    mTelephonyManager.isCellularIdentifierDisclosureNotificationsEnabled();
                }
        );
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#getLastKnownCellIdentity"})
    @RequiresFlagsEnabled(
            com.android.server.telecom.flags.Flags.FLAG_GET_LAST_KNOWN_CELL_IDENTITY)
    public void testGetLastKnownCellIdentity() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        // Revoking ACCESS_FINE_LOCATION will cause test to crash. Verify that security exception
        // is still thrown if com.android.phone.permission.ACCESS_LAST_KNOWN_CELL_ID is
        // not granted.
        try {
            mTelephonyManager.getLastKnownCellIdentity();
            fail("TelephonyManager#resetSettings requires the"
                    + " permission.ACCESS_LAST_KNOWN_CELL_ID.");
        } catch (SecurityException e) {
            //expected
        }

        // Obtain the primary cell identity from the NetworkRegistration info list.
        ServiceState ss = mTelephonyManager.getServiceState();
        List<NetworkRegistrationInfo> regInfos = ss != null
                ? ss.getNetworkRegistrationInfoList()
                : new ArrayList();

        Optional<CellIdentity> primaryCellIdentity = regInfos.stream()
                .filter(nri -> nri.getCellIdentity() != null)
                .filter(nri -> nri.getTransportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .sorted(Comparator.comparing(NetworkRegistrationInfo::isRegistered)
                        .thenComparing((nri) -> nri.getDomain() & NetworkRegistrationInfo.DOMAIN_CS)
                        .reversed())
                .map(nri -> nri.getCellIdentity())
                .distinct()
                .findFirst();

        try {
            CellIdentity cellIdentity = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getLastKnownCellIdentity(),
                    permission.ACCESS_LAST_KNOWN_CELL_ID,
                    permission.ACCESS_FINE_LOCATION);
            assertEquals(
                    cellIdentity,
                    primaryCellIdentity.isPresent() ? primaryCellIdentity.get() : null);
        } catch (SecurityException e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#isNullCipherNotificationsEnabled",
            "android.telephony.TelephonyManager#setNullCipherNotificationsEnabled"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MODEM_CIPHER_TRANSPARENCY)
    public void testsetNullCipherNotificationsEnabled() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mNetworkHalVersion < RADIO_HAL_VERSION_2_2) {
            Log.d(TAG,
                    "Skipping test since modem does not support IRadioNetwork HAL v2.2");
            return;
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setNullCipherNotificationsEnabled(true));
        boolean enabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isNullCipherNotificationsEnabled());
        assertTrue(enabled);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setNullCipherNotificationsEnabled(false));
        enabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isNullCipherNotificationsEnabled());
        assertFalse(enabled);
    }

    @Test
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#isNullCipherNotificationsEnabled",
            "android.telephony.TelephonyManager#setNullCipherNotificationsEnabled"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MODEM_CIPHER_TRANSPARENCY)
    public void testNullCipherNotificationsPermissions() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        if (mNetworkHalVersion < RADIO_HAL_VERSION_2_2) {
            Log.d(TAG,
                    "Skipping test since modem does not support IRadioNetwork HAL v2.2");
            return;
        }

        assertThrows(SecurityException.class, () -> {
                    mTelephonyManager.setNullCipherNotificationsEnabled(true);
                }
        );

        assertThrows(SecurityException.class, () -> {
                    mTelephonyManager.isNullCipherNotificationsEnabled();
                }
        );
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#EmergencyCallDiagnosticData"})
    @RequiresFlagsEnabled(
            com.android.server.telecom.flags.Flags.FLAG_TELECOM_RESOLVE_HIDDEN_DEPENDENCIES)
    public void testEmergencyCallDiagnosticData() {
        long startTime = SystemClock.elapsedRealtime();
        TelephonyManager.EmergencyCallDiagnosticData.Builder callDiagnosticBuilder =
                new TelephonyManager.EmergencyCallDiagnosticData.Builder();
        TelephonyManager.EmergencyCallDiagnosticData data = callDiagnosticBuilder
                .setTelecomDumpsysCollectionEnabled(true)
                .setTelephonyDumpsysCollectionEnabled(true)
                .setLogcatCollectionStartTimeMillis(startTime)
                .build();
        assertTrue(data.isTelecomDumpsysCollectionEnabled());
        assertTrue(data.isTelephonyDumpsysCollectionEnabled());
        assertTrue(data.isLogcatCollectionEnabled());
        assertEquals(startTime, data.getLogcatCollectionStartTimeMillis());

        data = callDiagnosticBuilder
                .setTelecomDumpsysCollectionEnabled(false)
                .setTelephonyDumpsysCollectionEnabled(false)
                .setLogcatCollectionStartTimeMillis(-1L)
                .build();
        assertFalse(data.isTelecomDumpsysCollectionEnabled());
        assertFalse(data.isTelephonyDumpsysCollectionEnabled());
        assertFalse(data.isLogcatCollectionEnabled());
    }

    @Test
    @ApiTest(apis = {"android.telephony.TelephonyManager#persistEmergencyCallDiagnosticData"})
    @RequiresFlagsEnabled(
            com.android.server.telecom.flags.Flags.FLAG_TELECOM_RESOLVE_HIDDEN_DEPENDENCIES)
    public void testPersistEmergencyCallDiagnosticData() throws Exception {
        long startTime = SystemClock.elapsedRealtime();
        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String tag =
                        intent.getStringExtra(DropBoxManager.EXTRA_TAG);
                if (tag.equals(DROPBOX_TAG)) {
                    Log.d(TAG, "entry added to dropbox");
                    mLatchForDropBox.countDown();
                }
            }
        }, mDropBoxIntentFilter);

        TelephonyManager.EmergencyCallDiagnosticData.Builder callDiagnosticBuilder =
                new TelephonyManager.EmergencyCallDiagnosticData.Builder();
        persistCallDiagnostics(callDiagnosticBuilder, true /* setTelecomDump */,
                false /* setTelephonyDump */, false /* setLogcatDump */);
        String telecomDumpOutput = TestUtils.executeShellCommand(getInstrumentation(),
                TELECOM_DUMPSYS_COMMAND);
        long nextEntryTime = verifyEmergencyDropBoxEntriesCreatedAndDumped(
                startTime, telecomDumpOutput, false);

        persistCallDiagnostics(callDiagnosticBuilder, false /* setTelecomDump */,
                true /* setTelephonyDump */, false /* setLogcatDump */);
        String telephonyDumpOutput = TestUtils.executeShellCommand(getInstrumentation(),
                TELEPHONY_DUMPSYS_COMMAND);
        nextEntryTime = verifyEmergencyDropBoxEntriesCreatedAndDumped(
                nextEntryTime, telephonyDumpOutput, false);

        String logcatSystemRadioCmd = LOGCAT_BINARY + " -t " + startTime
                + " -b system,radio";
        persistCallDiagnostics(callDiagnosticBuilder, false /* setTelecomDump */,
                false /* setTelephonyDump */, true /* setLogcatDump */);
        String logcatDumpOutput = TestUtils.executeShellCommand(getInstrumentation(),
                logcatSystemRadioCmd);
        verifyEmergencyDropBoxEntriesCreatedAndDumped(nextEntryTime, logcatDumpOutput, true);
    }

    private void persistCallDiagnostics(
            TelephonyManager.EmergencyCallDiagnosticData.Builder callDiagnosticBuilder,
            boolean setTelecomDump, boolean setTelephonyDump, boolean setLogcatDump)
            throws InterruptedException {
        TelephonyManager.EmergencyCallDiagnosticData data = callDiagnosticBuilder
                .setTelecomDumpsysCollectionEnabled(setTelecomDump)
                .setTelephonyDumpsysCollectionEnabled(setTelephonyDump)
                .setLogcatCollectionStartTimeMillis(
                        setLogcatDump ? SystemClock.elapsedRealtime() : -1L)
                .build();

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                    (tm) -> tm.persistEmergencyCallDiagnosticData(DROPBOX_TAG, data),
                    permission.DUMP);
        } catch (SecurityException e) {
            e.printStackTrace();
            fail(e.toString());
        }
        assertTrue(mLatchForDropBox.await(DROP_BOX_LATCH_TIMEOUT, TimeUnit.MILLISECONDS));
        mLatchForDropBox = new CountDownLatch(1);
    }

    private long verifyEmergencyDropBoxEntriesCreatedAndDumped(
            long entriesAfterTime, String dumpsysOutput, boolean allowSkipDumpsysVerification
    ) {
        DropBoxManager dm = getContext().getSystemService(DropBoxManager.class);
        DropBoxManager.Entry entry;

        entry = dm.getNextEntry(DROPBOX_TAG, entriesAfterTime);
        if (allowSkipDumpsysVerification && entry == null) {
            return -1L;
        }

        assertNotNull("No emergency diagnostic dropbox entries found", entry);
        long entryTime = entry.getTimeMillis();
        String [] content = entry.getText(MAX_READ_BYTES_PER_DROP_BOX_ENTRY).split(
                System.lineSeparator());
        assertNotNull("Dropbox entry content is null", content);
        int lineCount = 1;
        int foundCount = 0;
        for (String line : content) {
            assertFalse(line.contains(DIAG_ERROR_MSG));
            //verify that corresponding dumpsys output also has this data
            if (lineCount++ < MAX_LINES_TO_VERIFY_IN_DUMPSYS_OUTPUT) {
                // Only perform half verification in order to make the verification less prone to
                // timing issues between when the dumpsys is taken v.s. when the entry is recorded.
                if (dumpsysOutput.contains(line)) {
                    foundCount++;
                }
            }
        }
        assertTrue("Should have found ~50% of expected lines in dropbox",
                foundCount >= MAX_LINES_TO_VERIFY_IN_DUMPSYS_OUTPUT / 2);
        entry.close();
        return entryTime;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_ONLY_CELLULAR_SERVICE)
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#isDeviceVoiceCapable",
            "android.telephony.TelephonyManager#isVoiceCapable"})
    public void testIsDeviceVoiceCapable_isIdenticalToIsVoiceCapable() {
        assertEquals(mTelephonyManager.isDeviceVoiceCapable(), mTelephonyManager.isVoiceCapable());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_ONLY_CELLULAR_SERVICE)
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#isDeviceSmsCapable",
            "android.telephony.TelephonyManager#isSmsCapable"})
    public void testIsDeviceSmsCapable_isIdenticalToIsSmsCapable() {
        assertEquals(mTelephonyManager.isDeviceSmsCapable(), mTelephonyManager.isSmsCapable());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RESET_MOBILE_NETWORK_SETTINGS)
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#ACTION_RESET_MOBILE_NETWORK_SETTINGS"})
    public void testActionResetMobileNetworkSettings_shouldBeSupported() {
        PackageManager packageManager = getContext().getPackageManager();
        Intent intent = new Intent(TelephonyManager.ACTION_RESET_MOBILE_NETWORK_SETTINGS);

        List<ResolveInfo> resolvedActivities = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        final String errorMessage =
                "TelephonyManager.ACTION_RESET_MOBILE_NETWORK_SETTINGS should be supported to "
                        + "launch setting to reset mobile networks!";
        assertTrue(errorMessage, !resolvedActivities.isEmpty());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RESET_MOBILE_NETWORK_SETTINGS)
    @ApiTest(apis = {
            "android.telephony.TelephonyManager#ACTION_RESET_MOBILE_NETWORK_SETTINGS"})
    public void testActionResetMobileNetworkSettings_requiresNoPermission() {
        // Try to startActivity with the action and make sure no exceptions are thrown.
        // Exceptions may include:
        // 1. SecurityException if additional permission are required for the action
        // 2. ActivityNotFoundException if the action is not supported
        Intent intent = new Intent(TelephonyManager.ACTION_RESET_MOBILE_NETWORK_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_GET_EMERGENCY_ROLE_HOLDER_API_ENABLED)
    @ApiTest(apis = {"android.telephony.TelephonyManager#getEmergencyAssistancePackageName"})
    public void testGetEmergencyAssistancePackageName() {
        if (mTelephonyManager.isVoiceCapable()
            && ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.isEmergencyAssistanceEnabled())) {
            assertNotNull(ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                            (tm) -> tm.getEmergencyAssistancePackageName()));
        } else {
            assertThrows(IllegalStateException.class, () ->
                    ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                            (tm) -> tm.getEmergencyAssistancePackageName()));
        }
    }

    private Integer getSecondTestSubId() {
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            for (int subId : mSubscriptionManager.getActiveSubscriptionIdList()) {
                if (subId != mTestSub) {
                    return subId;
                }
            }
        } catch (SecurityException e) {
            fail("SubscriptionManager#getActiveSubscriptionIdList requires "
                    + "READ_PRIVILEGED_PHONE_STATE");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        return null;
    }
}
