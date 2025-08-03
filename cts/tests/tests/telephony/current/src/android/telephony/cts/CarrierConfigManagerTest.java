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

package android.telephony.cts;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_READ_PHONE_STATE;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_STRING;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_FORCE_HOME_NETWORK_BOOL;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_OVERRIDE_WFC_ROAMING_MODE_WHILE_USING_NTN_BOOL;
import static android.telephony.ServiceState.STATE_IN_SERVICE;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.AppOpsUtils.setOpMode;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.os.PersistableBundle;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestThread;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// TODO(b/221323753): replace remain junit asserts with Truth assert
public class CarrierConfigManagerTest {

    private static final long TIMEOUT_MILLIS = 5000;

    private static final int TEST_SLOT_INDEX = 0;
    private static final int TEST_SUB_ID = 1;
    private static final int TEST_CARRIER_ID = 99;
    private static final int TEST_PRECISE_CARRIER_ID = 100;

    private static final String CARRIER_NAME_OVERRIDE = "carrier_a";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private CarrierConfigManager mConfigManager;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    // Use a long timeout to accommodate devices with lower amounts of memory, as it will take
    // longer for these devices to receive the broadcast (b/161963269). It is expected that all
    // devices can receive the broadcast in under 5s (most should receive it well before then).
    private static final int BROADCAST_TIMEOUT_MILLIS = 5000;
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);

    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        mTelephonyManager = (TelephonyManager)
                getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mConfigManager = (CarrierConfigManager)
                getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        mSubscriptionManager =
                (SubscriptionManager)
                        getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mUiAutomation = getInstrumentation().getUiAutomation();
    }

    @After
    public void tearDown() throws Exception {
        try {
            setOpMode("--uid android.telephony.cts", OPSTR_READ_PHONE_STATE, MODE_ALLOWED);
        } catch (IOException e) {
            fail();
        }
    }

    /**
     * Checks whether the telephony stack should be running on this device.
     *
     * Note: "Telephony" means only SMS/MMS and voice calls in some contexts, but we also care if
     * the device supports cellular data.
     */
    private boolean hasTelephony() {
        return mTelephonyManager.isDataCapable();
    }

    private boolean isSimCardPresent() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_UNKNOWN &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    private boolean isSimCardAbsent() {
        return mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_ABSENT;
    }

    private void checkConfig(PersistableBundle config) {
        if (config == null) {
            assertFalse(
                    "Config should only be null when telephony is not running.", hasTelephony());
            return;
        }
        assertNotNull("CarrierConfigManager should not return null config", config);
        if (isSimCardAbsent()) {
            // Static default in CarrierConfigManager will be returned when no sim card present.
            assertEquals("Config doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_ADDITIONAL_CALL_SETTING_BOOL), true);

            assertEquals("KEY_VVM_DESTINATION_NUMBER_STRING doesn't match static default.",
                config.getString(CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING), "");
            assertEquals("KEY_VVM_PORT_NUMBER_INT doesn't match static default.",
                config.getInt(CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT), 0);
            assertEquals("KEY_VVM_TYPE_STRING doesn't match static default.",
                config.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING), "");
            assertEquals("KEY_VVM_CELLULAR_DATA_REQUIRED_BOOLEAN doesn't match static default.",
                config.getBoolean(CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL),
                false);
            assertEquals("KEY_VVM_PREFETCH_BOOLEAN doesn't match static default.",
                config.getBoolean(CarrierConfigManager.KEY_VVM_PREFETCH_BOOL), true);
            assertEquals("KEY_CARRIER_VVM_PACKAGE_NAME_STRING doesn't match static default.",
                config.getString(CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING), "");
            assertFalse(CarrierConfigManager.isConfigForIdentifiedCarrier(config));

            // Check default value matching
            assertEquals("KEY_DATA_LIMIT_NOTIFICATION_BOOL doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_DATA_LIMIT_NOTIFICATION_BOOL),
                            true);
            assertEquals("KEY_DATA_RAPID_NOTIFICATION_BOOL doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_DATA_RAPID_NOTIFICATION_BOOL),
                            true);
            assertEquals("KEY_DATA_WARNING_NOTIFICATION_BOOL doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_DATA_WARNING_NOTIFICATION_BOOL),
                            true);
            assertEquals("Gps.KEY_PERSIST_LPP_MODE_BOOL doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.Gps.KEY_PERSIST_LPP_MODE_BOOL),
                            true);
            assertEquals("KEY_MONTHLY_DATA_CYCLE_DAY_INT doesn't match static default.",
                    config.getInt(CarrierConfigManager.KEY_MONTHLY_DATA_CYCLE_DAY_INT),
                            CarrierConfigManager.DATA_CYCLE_USE_PLATFORM_DEFAULT);
            assertEquals("KEY_SUPPORT_ADHOC_CONFERENCE_CALLS_BOOL doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_SUPPORT_ADHOC_CONFERENCE_CALLS_BOOL),
                    false);
            assertEquals("KEY_SUPPORTS_CALL_COMPOSER_BOOL doesn't match static default.",
                    config.getBoolean(CarrierConfigManager.KEY_SUPPORTS_CALL_COMPOSER_BOOL),
                            false);
            assertEquals("KEY_CALL_COMPOSER_PICTURE_SERVER_URL_STRING doesn't match static"
                    + " default.", config.getString(
                            CarrierConfigManager.KEY_CALL_COMPOSER_PICTURE_SERVER_URL_STRING), "");
            assertEquals("KEY_CARRIER_USSD_METHOD_INT doesn't match static default.",
                    config.getInt(CarrierConfigManager.KEY_CARRIER_USSD_METHOD_INT),
                            CarrierConfigManager.USSD_OVER_CS_PREFERRED);
            assertEquals("KEY_USAGE_SETTING_INT doesn't match static default.",
                    config.getInt(CarrierConfigManager.KEY_CELLULAR_USAGE_SETTING_INT),
                            SubscriptionManager.USAGE_SETTING_UNKNOWN);
            assertEquals("KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL"
                    + " doesn't match static default.",
                    config.getBoolean(
                      CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL),
                      false);
            assertEquals(
                    "KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE"
                            + " doesn't match static default.",
                    config.getPersistableBundle(CarrierConfigManager
                            .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE),
                    PersistableBundle.EMPTY);
            assertEquals("KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT "
                            + "doesn't match static default.",
                    config.getInt(
                            KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT),
                    300);
            assertTrue("KEY_OVERRIDE_WFC_ROAMING_MODE_WHILE_USING_NTN_BOOL "
                            + "doesn't match static default.",
                    config.getBoolean(KEY_OVERRIDE_WFC_ROAMING_MODE_WHILE_USING_NTN_BOOL));

            assertArrayEquals("KEY_CAPABILITIES_EXEMPT_FROM_SINGLE_DC_CHECK_INT_ARRAY"
                            + " doesn't match static default.",
                    config.getIntArray(CarrierConfigManager
                            .KEY_CAPABILITIES_EXEMPT_FROM_SINGLE_DC_CHECK_INT_ARRAY),
                    new int[] {NetworkCapabilities.NET_CAPABILITY_IMS});
        }

        // These key should return default values if not customized.
        assertNotNull(config.getIntArray(
                CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY));
        assertNotNull(config.getIntArray(
                CarrierConfigManager.KEY_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY));
        assertNotNull(config.getIntArray(
                CarrierConfigManager.KEY_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY));
        assertNotNull(config.getIntArray(
                CarrierConfigManager.KEY_LTE_RSRQ_THRESHOLDS_INT_ARRAY));
        assertNotNull(config.getIntArray(
                CarrierConfigManager.KEY_LTE_RSSNR_THRESHOLDS_INT_ARRAY));

        // Check the GPS key prefix
        assertTrue("Gps.KEY_PREFIX doesn't match the prefix of the name of "
                + "Gps.KEY_PERSIST_LPP_MODE_BOOL",
                        CarrierConfigManager.Gps.KEY_PERSIST_LPP_MODE_BOOL.startsWith(
                                CarrierConfigManager.Gps.KEY_PREFIX));
    }

    private void checkConfigSubset(PersistableBundle configSubset, PersistableBundle allConfigs,
            String key) {
        assertThat(configSubset).isNotNull();
        assertThat(allConfigs).isNotNull();

        // KEY_CARRIER_CONFIG_VERSION_STRING and KEY_CARRIER_CONFIG_APPLIED_BOOL should always
        // be included
        assertThat(configSubset.containsKey(
                CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING)).isTrue();
        assertThat(configSubset.containsKey(
                CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL)).isTrue();

        Object value = allConfigs.get(key);
        if (value instanceof PersistableBundle) {
            assertThat(isEqual((PersistableBundle) configSubset.get(key),
                    (PersistableBundle) value)).isTrue();
        } else {
            // value may be array types, compare with value equality instead of object equality
            assertThat(configSubset.get(key)).isEqualTo(value);
        }
    }

    // Checks for PersistableBundle equality
    // Copied from com.android.server.vcn.util.PersistableBundleUtils
    // TODO: move to a CTS common lib if other CTS cases also need PersistableBundle equality check
    private static boolean isEqual(PersistableBundle left, PersistableBundle right) {
        // Check for pointer equality & null equality
        if (Objects.equals(left, right)) {
            return true;
        }

        // If only one of the two is null, but not the other, not equal by definition.
        if (Objects.isNull(left) != Objects.isNull(right)) {
            return false;
        }

        if (!left.keySet().equals(right.keySet())) {
            return false;
        }

        for (String key : left.keySet()) {
            Object leftVal = left.get(key);
            Object rightVal = right.get(key);

            // Check for equality
            if (Objects.equals(leftVal, rightVal)) {
                continue;
            } else if (Objects.isNull(leftVal) != Objects.isNull(rightVal)) {
                // If only one of the two is null, but not the other, not equal by definition.
                return false;
            } else if (!Objects.equals(leftVal.getClass(), rightVal.getClass())) {
                // If classes are different, not equal by definition.
                return false;
            }
            if (leftVal instanceof PersistableBundle) {
                if (!isEqual((PersistableBundle) leftVal, (PersistableBundle) rightVal)) {
                    return false;
                }
            } else if (leftVal.getClass().isArray()) {
                if (leftVal instanceof boolean[]) {
                    if (!Arrays.equals((boolean[]) leftVal, (boolean[]) rightVal)) {
                        return false;
                    }
                } else if (leftVal instanceof double[]) {
                    if (!Arrays.equals((double[]) leftVal, (double[]) rightVal)) {
                        return false;
                    }
                } else if (leftVal instanceof int[]) {
                    if (!Arrays.equals((int[]) leftVal, (int[]) rightVal)) {
                        return false;
                    }
                } else if (leftVal instanceof long[]) {
                    if (!Arrays.equals((long[]) leftVal, (long[]) rightVal)) {
                        return false;
                    }
                } else if (!Arrays.equals((Object[]) leftVal, (Object[]) rightVal)) {
                    return false;
                }
            } else {
                if (!Objects.equals(leftVal, rightVal)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Test
    public void testGetConfig() {
        PersistableBundle config = mConfigManager.getConfig();
        checkConfig(config);
    }

    @Test
    public void testGetConfig_withNullKeys() {
        try {
            mConfigManager.getConfig(null);
            fail("getConfig with null keys should throw NullPointerException");
        } catch (NullPointerException expected) {
        }

        try {
            mConfigManager.getConfig(CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING, null);
            fail("getConfig with null keys should throw NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testGetConfig_withValidKeys() {
        PersistableBundle allConfigs = mConfigManager.getConfig();
        Set<String> allKeys = allConfigs.keySet();
        assertThat(allKeys).isNotNull();

        for (String key : allKeys) {
            PersistableBundle configSubset = mConfigManager.getConfig(key);
            checkConfigSubset(configSubset, allConfigs, key);
        }
    }

    @Test
    public void testGetConfig_keyWithoutDefaultValue() {
        String keyWithDefaultValue = CarrierConfigManager.KEY_CARRIER_SUPPORTS_TETHERING_BOOL;
        String keyWithoutDefaultValue = "random_key_for_testing";

        PersistableBundle configSubset = mConfigManager.getConfig(keyWithoutDefaultValue);
        assertThat(configSubset.isEmpty()).isFalse();
        assertThat(configSubset.keySet()).doesNotContain(keyWithoutDefaultValue);

        configSubset = mConfigManager.getConfig(keyWithDefaultValue, keyWithoutDefaultValue);
        assertThat(configSubset.isEmpty()).isFalse();
        assertThat(configSubset.keySet()).contains(keyWithDefaultValue);
        assertThat(configSubset.keySet()).doesNotContain(keyWithoutDefaultValue);
    }

    @Test
    @AsbSecurityTest(cveBugId = 73136824)
    public void testRevokePermission() {
        PersistableBundle config;

        try {
            setOpMode("--uid android.telephony.cts", OPSTR_READ_PHONE_STATE, MODE_IGNORED);
        } catch (IOException e) {
            fail();
        }

        config = mConfigManager.getConfig();
        assertTrue(config.isEmptyParcel());

        try {
            setOpMode("--uid android.telephony.cts", OPSTR_READ_PHONE_STATE, MODE_ALLOWED);
        } catch (IOException e) {
            fail();
        }

        config = mConfigManager.getConfig();
        checkConfig(config);
    }

    @Test
    public void testGetConfigForSubId() {
        PersistableBundle config =
                mConfigManager.getConfigForSubId(SubscriptionManager.getDefaultSubscriptionId());
        checkConfig(config);
    }

    @Test
    public void testGetConfigForSubId_withNullKeys() {
        try {
            mConfigManager.getConfigForSubId(SubscriptionManager.getDefaultSubscriptionId(), null);
            fail("getConfigForSubId with null keys should throw NullPointerException");
        } catch (NullPointerException expected) {
        }

        try {
            mConfigManager.getConfigForSubId(SubscriptionManager.getDefaultSubscriptionId(),
                    CarrierConfigManager.KEY_CARRIER_CONFIG_VERSION_STRING, null);
            fail("getConfigForSubId with null keys should throw NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testGetConfigForSubId_withValidSingleKey() {
        final int defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        PersistableBundle allConfigs = mConfigManager.getConfigForSubId(defaultSubId);
        Set<String> allKeys = allConfigs.keySet();
        assertThat(allKeys).isNotNull();

        for (String key : allKeys) {
            PersistableBundle configSubset = mConfigManager.getConfigForSubId(defaultSubId, key);
            checkConfigSubset(configSubset, allConfigs, key);
        }
    }

    @Test
    public void testGetConfigForSubId_withValidMultipleKeys() {
        final int defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        PersistableBundle allConfigs = mConfigManager.getConfigForSubId(defaultSubId);
        Set<String> allKeys = allConfigs.keySet();
        assertThat(allKeys).isNotNull();

        // Just cover size in 2..10 to cover majority of cases while keeping this case quick
        for (int size = 2; size <= 10; size++) {
            Collection<List<String>> subsets = partitionSetWithSize(allKeys, size);
            for (List<String> subset : subsets) {
                String[] keyArray = new String[subset.size()];
                keyArray = subset.toArray(keyArray);

                PersistableBundle configSubset = mConfigManager.getConfigForSubId(defaultSubId,
                        keyArray);
                for (String key : keyArray) {
                    checkConfigSubset(configSubset, allConfigs, key);
                }
            }
        }
    }

    private Collection<List<String>> partitionSetWithSize(Set<String> keySet, int size) {
        final AtomicInteger counter = new AtomicInteger(0);
        return keySet.stream().collect(Collectors.groupingBy(s -> counter.getAndIncrement()))
                .values();
    }

    /**
     * Tests the CarrierConfigManager.notifyConfigChangedForSubId() API. This makes a call to
     * notifyConfigChangedForSubId() API and expects a SecurityException since the test apk is not signed
     * by certificate on the SIM.
     */
    @Test
    public void testNotifyConfigChangedForSubId() {
        try {
            if (isSimCardPresent()) {
                mConfigManager.notifyConfigChangedForSubId(
                        SubscriptionManager.getDefaultSubscriptionId());
                fail("Expected SecurityException. App doesn't have carrier privileges.");
            }
        } catch (SecurityException expected) {
        }
    }

    /**
     * The following methods may return any value depending on the state of the device. Simply
     * call them to make sure they do not throw any exceptions.
     */
    @Test
    public void testCarrierConfigManagerResultDependentApi() {
        assertNotNull(ShellIdentityUtils.invokeMethodWithShellPermissions(mConfigManager,
                (cm) -> cm.getDefaultCarrierServicePackageName()));
    }

    /**
     * This checks that {@link CarrierConfigManager#overrideConfig(int, PersistableBundle)}
     * correctly overrides the Carrier Name (SPN) string.
     */
    @Test
    public void testCarrierConfigNameOverride() throws Exception {
        if (!isSimCardPresent()
                || mTelephonyManager.getServiceState().getState() != STATE_IN_SERVICE) {
            return;
        }

        // Adopt shell permission so the required permission (android.permission.MODIFY_PHONE_STATE)
        // is granted.
        UiAutomation ui = getInstrumentation().getUiAutomation();
        ui.adoptShellPermissionIdentity();

        int subId = SubscriptionManager.getDefaultSubscriptionId();
        TestThread t = new TestThread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                OnSubscriptionsChangedListener listener =
                        new OnSubscriptionsChangedListener() {
                            @Override
                            public void onSubscriptionsChanged() {
                                if (CARRIER_NAME_OVERRIDE.equals(
                                        mTelephonyManager.getSimOperatorName())) {
                                    COUNT_DOWN_LATCH.countDown();
                                }
                            }
                        };
                mSubscriptionManager.addOnSubscriptionsChangedListener(listener);

                PersistableBundle carrierNameOverride = new PersistableBundle(3);
                carrierNameOverride.putBoolean(KEY_CARRIER_NAME_OVERRIDE_BOOL, true);
                carrierNameOverride.putBoolean(KEY_FORCE_HOME_NETWORK_BOOL, true);
                carrierNameOverride.putString(KEY_CARRIER_NAME_STRING, CARRIER_NAME_OVERRIDE);
                mConfigManager.overrideConfig(subId, carrierNameOverride);

                Looper.loop();
            }
        });

        try {
            t.start();
            boolean didCarrierNameUpdate =
                    COUNT_DOWN_LATCH.await(BROADCAST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!didCarrierNameUpdate) {
                fail("CarrierName not overridden in " + BROADCAST_TIMEOUT_MILLIS + " ms");
            }
        } finally {
            mConfigManager.overrideConfig(subId, null);
            ui.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testExtraRebroadcastOnUnlock() throws Throwable {
        if (!hasTelephony()) {
            return;
        }

        BlockingQueue<Boolean> queue = new ArrayBlockingQueue<Boolean>(5);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                    queue.add(new Boolean(true));
                    // verify that REBROADCAST_ON_UNLOCK is populated
                    assertFalse(
                            intent.getBooleanExtra(CarrierConfigManager.EXTRA_REBROADCAST_ON_UNLOCK,
                            true));
                }
            }
        };

        try {
            final IntentFilter filter =
                    new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
            getContext().registerReceiver(receiver, filter);

            // verify that carrier config is received
            int subId = SubscriptionManager.getDefaultSubscriptionId();
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
            mConfigManager.notifyConfigChangedForSubId(subId);

            Boolean broadcastReceived = queue.poll(BROADCAST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(broadcastReceived);
            assertTrue(broadcastReceived);
        } finally {
            // unregister receiver
            getContext().unregisterReceiver(receiver);
            receiver = null;
        }
    }

    @Test
    public void testGetConfigByComponentForSubId() {
        PersistableBundle config =
                mConfigManager.getConfigByComponentForSubId(
                        CarrierConfigManager.Wifi.KEY_PREFIX,
                        SubscriptionManager.getDefaultSubscriptionId());
        if (config != null) {
            assertTrue(config.containsKey(CarrierConfigManager.Wifi.KEY_HOTSPOT_MAX_CLIENT_COUNT));
            assertFalse(config.containsKey(KEY_CARRIER_VOLTE_PROVISIONED_BOOL));
            assertFalse(config.containsKey(CarrierConfigManager.Gps.KEY_SUPL_ES_STRING));
        }

        config = mConfigManager.getConfigByComponentForSubId(
                CarrierConfigManager.ImsVoice.KEY_PREFIX,
                SubscriptionManager.getDefaultSubscriptionId());
        if (config != null) {
            assertTrue(config.containsKey(
                    CarrierConfigManager.ImsVoice.KEY_AMRWB_PAYLOAD_DESCRIPTION_BUNDLE));
        }
    }

    @Test
    public void testRegisterCarrierConfigChangeListener_withNullExecutor() throws Exception {
        // non-null listener
        CarrierConfigManager.CarrierConfigChangeListener listener = (a, b, c, d) -> {
        };
        try {
            mConfigManager.registerCarrierConfigChangeListener(null, listener);
            fail("NullPointerException expected when register with null executor");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testRegisterCarrierConfigChangeListener_withNullListener() throws Exception {
        // non-null executor
        Executor executor = Runnable::run;
        try {
            mConfigManager.registerCarrierConfigChangeListener(executor, null);
            fail("NullPointerException expected when register with null listener");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testUnregisterCarrierConfigChangeListener_withNullListener() throws Exception {
        try {
            mConfigManager.unregisterCarrierConfigChangeListener(null);
            fail("NullPointerException expected when unregister with null listener");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testCarrierConfigChangeListener() throws Exception {
        LinkedBlockingQueue<CarrierConfigChangeParams> queue = new LinkedBlockingQueue<>(1);

        CarrierConfigManager.CarrierConfigChangeListener listener =
                (slotIndex, subId, carrierId, preciseCarrierId) -> queue.offer(
                        new CarrierConfigChangeParams(slotIndex, subId, carrierId,
                                preciseCarrierId));

        try {
            mConfigManager.registerCarrierConfigChangeListener(Runnable::run, listener);

            TelephonyRegistryManager telephonyRegistryManager = getContext().getSystemService(
                    TelephonyRegistryManager.class);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyRegistryManager,
                    (trm) -> trm.notifyCarrierConfigChanged(TEST_SLOT_INDEX, TEST_SUB_ID,
                            TEST_CARRIER_ID, TEST_PRECISE_CARRIER_ID));
            CarrierConfigChangeParams result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            assertEquals(TEST_SLOT_INDEX, result.mSlotIndex);
            assertEquals(TEST_SUB_ID, result.mSubId);
            assertEquals(TEST_CARRIER_ID, result.mCarrierId);
            assertEquals(TEST_PRECISE_CARRIER_ID, result.mPreciseCarrierId);
        } finally {
            mConfigManager.unregisterCarrierConfigChangeListener(listener);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_ONLY_CELLULAR_SERVICE)
    @ApiTest(apis = {"android.telephony"
            + ".CarrierConfigManager#KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY",
            "android.telephony.SubscriptionInfo#getServiceCapabilities"})
    public void testCellularServiceCapabilitiesOverride() throws Exception {
        if (!isSimCardPresent()
                || mTelephonyManager.getServiceState().getState() != STATE_IN_SERVICE) {
            return;
        }

        final int subId = SubscriptionManager.getDefaultSubscriptionId();
        final boolean wasDataOnlyService = isDataOnlyService(subId);
        final OnSubscriptionsChangedListener listener =
                new OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        boolean isDataOnlyService = isDataOnlyService(subId);
                        if (wasDataOnlyService != isDataOnlyService) {
                            COUNT_DOWN_LATCH.countDown();
                        }
                    }
                };

        try {
            // Adopt shell permission to require android.permission.MODIFY_PHONE_STATE
            mUiAutomation.adoptShellPermissionIdentity();
            mSubscriptionManager.addOnSubscriptionsChangedListener(Runnable::run, listener);

            // Override with full services if it was data only, or data only if it was full
            int[] overriddenCapabilities;
            if (!wasDataOnlyService) {
                overriddenCapabilities = new int[]{SubscriptionManager.SERVICE_CAPABILITY_DATA};
            } else {
                final int numberOfServices =
                        SubscriptionManager.SERVICE_CAPABILITY_MAX
                                - SubscriptionManager.SERVICE_CAPABILITY_VOICE + 1;
                overriddenCapabilities = new int[numberOfServices];
                int service = SubscriptionManager.SERVICE_CAPABILITY_VOICE;
                int i = 0;
                while (i < numberOfServices) {
                    overriddenCapabilities[i++] = service++;
                }
            }
            overrideCellularServiceCapabilities(subId, overriddenCapabilities);

            boolean awaitResult =
                    COUNT_DOWN_LATCH.await(BROADCAST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!awaitResult) {
                fail("Cellular services was not updated in " + BROADCAST_TIMEOUT_MILLIS + " ms");
            }
        } finally {
            mConfigManager.overrideConfig(subId, null);
            mSubscriptionManager.removeOnSubscriptionsChangedListener(listener);
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private boolean isDataOnlyService(int subId) {
        final SubscriptionInfo subInfo =
                mSubscriptionManager.getActiveSubscriptionInfo(subId);
        final Set<Integer> cellularServices = subInfo.getServiceCapabilities();
        return cellularServices.size() == 1
                && cellularServices.contains(SubscriptionManager.SERVICE_CAPABILITY_DATA);
    }

    private void overrideCellularServiceCapabilities(int subId, int[] newCapabilities) {
        PersistableBundle overrideBundle = new PersistableBundle(1);
        overrideBundle.putIntArray(KEY_CELLULAR_SERVICE_CAPABILITIES_INT_ARRAY, newCapabilities);
        mConfigManager.overrideConfig(subId, overrideBundle);
    }

    // A data value class to wrap the parameters of carrier config change
    private class CarrierConfigChangeParams {
        final int mSlotIndex;
        final int mSubId;
        final int mCarrierId;
        final int mPreciseCarrierId;

        CarrierConfigChangeParams(int slotIndex, int subId, int carrierId, int preciseCarrierId) {
            mSlotIndex = slotIndex;
            mSubId = subId;
            mCarrierId = carrierId;
            mPreciseCarrierId = preciseCarrierId;
        }
    }
}
