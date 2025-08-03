/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.telephony.SubscriptionManager.TRANSFER_STATUS_TRANSFERRED_OUT;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.RcsUceAdapter;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CarrierPrivilegeUtils;
import com.android.compatibility.common.util.PropertyUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestThread;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.util.ArrayUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SubscriptionManagerTest {
    private static final String TAG = "SubscriptionManagerTest";
    private static final String MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE";
    private static final String READ_PRIVILEGED_PHONE_STATE =
            "android.permission.READ_PRIVILEGED_PHONE_STATE";
    private static final List<Uri> CONTACTS = new ArrayList<>();
    static {
        CONTACTS.add(Uri.fromParts("tel", "+16505551212", null));
        CONTACTS.add(Uri.fromParts("tel", "+16505552323", null));
    }

    // time to wait when testing APIs which enable or disable subscriptions. The time waiting
    // to enable is longer because enabling a subscription can take longer than disabling
    private static final int SUBSCRIPTION_DISABLE_WAIT_MS = 5000;
    private static final int SUBSCRIPTION_ENABLE_WAIT_MS = 50000;

    // time to wait for subscription plans to expire
    private static final int SUBSCRIPTION_PLAN_EXPIRY_MS = 50;
    private static final int SUBSCRIPTION_PLAN_CLEAR_WAIT_MS = 5000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private int mSubId;
    private int mDefaultVoiceSubId;
    private String mPackageName;
    private SubscriptionManager mSm;
    private SubscriptionManagerTest.CarrierConfigReceiver mReceiver;

    private static class CarrierConfigReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForCarrierConfigChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private void overrideCarrierConfig(PersistableBundle bundle, int subId) throws Exception {
        mReceiver = new CarrierConfigReceiver(subId);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        InstrumentationRegistry.getContext().registerReceiver(mReceiver, filter);
        try {
            mReceiver.clearQueue();

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    InstrumentationRegistry.getContext().getSystemService(
                            CarrierConfigManager.class),
                    (cm) -> cm.overrideConfig(subId, bundle));
            mReceiver.waitForCarrierConfigChanged();
        } finally {
            InstrumentationRegistry.getContext().unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    /**
     * Callback used in testRegisterNetworkCallback that allows caller to block on
     * {@code onAvailable}.
     */
    private static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final CountDownLatch mAvailableLatch = new CountDownLatch(1);

        public void waitForAvailable() throws InterruptedException {
            assertTrue("Cellular network did not come up after 5 seconds",
                    mAvailableLatch.await(5, TimeUnit.SECONDS));
        }

        @Override
        public void onAvailable(Network network) {
            mAvailableLatch.countDown();
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        if (!isSupported()) return;

        final TestNetworkCallback callback = new TestNetworkCallback();
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        cm.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build(), callback);
        try {
            // Wait to get callback for availability of internet
            callback.waitForAvailable();
        } catch (InterruptedException e) {
            fail("NetworkCallback wait was interrupted.");
        } finally {
            cm.unregisterNetworkCallback(callback);
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!isSupported()) return;
        TelephonyUtils.flushTelephonyMetrics(InstrumentationRegistry.getInstrumentation());
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(isSupported());

        mSm = InstrumentationRegistry.getContext().getSystemService(SubscriptionManager.class);
        mSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        mDefaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        mPackageName = InstrumentationRegistry.getContext().getPackageName();

        setIdentifierAccess(false);
    }

    @After
    public void tearDown() throws Exception {
        if (mReceiver != null) {
            InstrumentationRegistry.getContext().unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    /**
     * Correctness check that both {@link PackageManager#FEATURE_TELEPHONY} and
     * {@link NetworkCapabilities#TRANSPORT_CELLULAR} network must both be
     * either defined or undefined; you can't cross the streams.
     */
    @Test
    public void testCorrectness() throws Exception {
        final boolean hasCellular = findCellularNetwork() != null;
        if (!hasCellular) {
            fail("Device claims to support " + PackageManager.FEATURE_TELEPHONY
                    + " but has no active cellular network, which is required for validation");
        }

        if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            fail("Device must have a valid default data subId for validation");
        }
    }

    @Test
    public void testGetActiveSubscriptionInfoCount() throws Exception {
        assertTrue(mSm.getActiveSubscriptionInfoCount() <=
                mSm.getActiveSubscriptionInfoCountMax());
    }

    @Test
    public void testGetActiveSubscriptionInfoForIcc() throws Exception {
        SubscriptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(mSubId));
        assertNotNull(ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfoForIcc(info.getIccId())));
    }

    @Test
    public void testGetAllSubscriptionInfoList() throws Exception {
        List<SubscriptionInfo> allSubInfoList = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mSm, SubscriptionManager::getAllSubscriptionInfoList);
        SubscriptionInfo subInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(mSubId));
        assertThat(allSubInfoList).contains(subInfo);
    }

    @Test
    public void testIsActiveSubscriptionId() throws Exception {
        assertTrue(mSm.isActiveSubscriptionId(mSubId));
    }

    @Test
    public void testGetSubscriptionIds() throws Exception {
        int slotId = SubscriptionManager.getSlotIndex(mSubId);
        int[] subIds = mSm.getSubscriptionIds(slotId);
        assertNotNull(subIds);
        assertTrue(ArrayUtils.contains(subIds, mSubId));
    }

    @Test
    public void testGetSubscriptionId() throws Exception {
        int slotId = SubscriptionManager.getSlotIndex(mSubId);
        assertThat(SubscriptionManager.getSubscriptionId(slotId)).isEqualTo(mSubId);
    }

    @Test
    public void testGetResourcesForSubId() {
        Resources r = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getResourcesForSubId(InstrumentationRegistry.getContext(), mSubId));
        // this is an old method which returns mcc/mnc as ints, so use the old SM.getMcc/Mnc methods
        // because they also use ints
        assertEquals(mSm.getActiveSubscriptionInfo(mSubId).getMcc(), r.getConfiguration().mcc);
        assertEquals(mSm.getActiveSubscriptionInfo(mSubId).getMnc(), r.getConfiguration().mnc);
    }

    @Test
    public void testIsUsableSubscriptionId() throws Exception {
        assertTrue(SubscriptionManager.isUsableSubscriptionId(mSubId));
    }

    @Test
    public void testActiveSubscriptions() throws Exception {
        List<SubscriptionInfo> subList = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfoList());
        int[] idList = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionIdList());
        // Assert when there is no sim card present or detected
        assertNotNull("Active subscriber required", subList);
        assertNotNull("Active subscriber required", idList);
        assertFalse("Active subscriber required", subList.isEmpty());
        assertNotEquals("Active subscriber required", 0, idList.length);
        for (int i = 0; i < subList.size(); i++) {
            assertTrue(subList.get(i).getSubscriptionId() >= 0);
            assertTrue(subList.get(i).getSimSlotIndex() >= 0);
            assertTrue(ArrayUtils.contains(idList, subList.get(i).getSubscriptionId()));
            if (i >= 1) {
                assertTrue(subList.get(i - 1).getSimSlotIndex()
                        <= subList.get(i).getSimSlotIndex());
                assertTrue(subList.get(i - 1).getSimSlotIndex() < subList.get(i).getSimSlotIndex()
                        || subList.get(i - 1).getSubscriptionId()
                        < subList.get(i).getSubscriptionId());
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_SUBSCRIPTION_USER_FILTER)
    public void testForAllProfilesSubscriptionManager() {
        SubscriptionManager allProfileSm = InstrumentationRegistry.getContext()
                .getSystemService(SubscriptionManager.class).createForAllUserProfiles();

        List<SubscriptionInfo> specificProfileSubList = ShellIdentityUtils
                .invokeMethodWithShellPermissions(mSm,
                        SubscriptionManager::getActiveSubscriptionInfoList);
        // Assert when there is no sim card present or detected
        assertNotNull("Active subscriber required", specificProfileSubList);

        List<SubscriptionInfo> allProfileSubList = ShellIdentityUtils
                .invokeMethodWithShellPermissions(allProfileSm,
                        SubscriptionManager::getActiveSubscriptionInfoList);

        assertTrue(allProfileSubList.size() >= specificProfileSubList.size());
    }

    @Test
    public void testSubscriptionPlans() throws Exception {
        // Make ourselves the owner
        setSubPlanOwner(mSubId, mPackageName);

        // Push empty list and we get empty back
        mSm.setSubscriptionPlans(mSubId, Arrays.asList());
        assertEquals(Arrays.asList(), mSm.getSubscriptionPlans(mSubId));

        // Push simple plan and get it back
        final SubscriptionPlan plan = buildValidSubscriptionPlan(System.currentTimeMillis());
        mSm.setSubscriptionPlans(mSubId, Arrays.asList(plan));
        assertEquals(Arrays.asList(plan), mSm.getSubscriptionPlans(mSubId));

        // Push plan with expiration time and verify that it expired
        mSm.setSubscriptionPlans(mSubId, Arrays.asList(plan), SUBSCRIPTION_PLAN_EXPIRY_MS);
        Thread.sleep(SUBSCRIPTION_PLAN_EXPIRY_MS);
        Thread.sleep(SUBSCRIPTION_PLAN_CLEAR_WAIT_MS);
        assertTrue(mSm.getSubscriptionPlans(mSubId).isEmpty());

        // Now revoke our access
        setSubPlanOwner(mSubId, null);
        try {
            mSm.setSubscriptionPlans(mSubId, Arrays.asList());
            fail();
        } catch (SecurityException expected) {
        }
        try {
            mSm.getSubscriptionPlans(mSubId);
            fail();
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testSubscriptionPlansOverrideCongested() throws Exception {
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        final Network net = findCellularNetwork();
        assertNotNull("Active cellular network required", net);

        // Make ourselves the owner
        setSubPlanOwner(mSubId, mPackageName);

        // Missing plans means no overrides
        mSm.setSubscriptionPlans(mSubId, Arrays.asList());
        try {
            mSm.setSubscriptionOverrideCongested(mSubId, true, 0);
            fail();
        } catch (SecurityException | IllegalStateException expected) {
        }

        // Defining plans means we get to override
        mSm.setSubscriptionPlans(mSubId,
                Arrays.asList(buildValidSubscriptionPlan(System.currentTimeMillis())));

        // Cellular is uncongested by default
        assertTrue(cm.getNetworkCapabilities(net).hasCapability(NET_CAPABILITY_NOT_CONGESTED));

        // Override should make it go congested
        {
            final CountDownLatch latch = waitForNetworkCapabilities(net, caps -> {
                return !caps.hasCapability(NET_CAPABILITY_NOT_CONGESTED);
            });
            mSm.setSubscriptionOverrideCongested(
                    mSubId, true, TelephonyManager.getAllNetworkTypes(), 0);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }

        // Clearing override should make it go uncongested
        {
            final CountDownLatch latch = waitForNetworkCapabilities(net, caps -> {
                return caps.hasCapability(NET_CAPABILITY_NOT_CONGESTED);
            });
            mSm.setSubscriptionOverrideCongested(mSubId, false, 0);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }

        // Now revoke our access
        setSubPlanOwner(mSubId, null);
        try {
            mSm.setSubscriptionOverrideCongested(
                    mSubId, true, TelephonyManager.getAllNetworkTypes(), 0);
            fail();
        } catch (SecurityException | IllegalStateException expected) {
        }
    }

    @Test
    public void testSubscriptionInfoRecord() {
        if (!isAutomotive()) return;

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        final String uniqueId = "00:01:02:03:04:05";
        final String displayName = "device_name";
        uiAutomation.adoptShellPermissionIdentity();
        try {
            mSm.addSubscriptionInfoRecord(uniqueId, displayName, 0,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            assertNotNull(mSm.getActiveSubscriptionInfoForIcc(uniqueId));
            mSm.removeSubscriptionInfoRecord(uniqueId,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            assertNull(mSm.getActiveSubscriptionInfoForIcc(uniqueId));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

        // Testing permission fail
        try {
            mSm.addSubscriptionInfoRecord(uniqueId, displayName, 0,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            mSm.removeSubscriptionInfoRecord(uniqueId,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            fail("SecurityException should be thrown without MODIFY_PHONE_STATE");
        } catch (SecurityException expected) {
            // expected
        }
    }

    @Test
    public void testSetDefaultVoiceSubId() {
        // Only make sense to set default sub if the device supports more than 1 modem.
        final TelephonyManager tm = InstrumentationRegistry.getContext()
                .getSystemService(TelephonyManager.class).createForSubscriptionId(mSubId);
        assumeTrue(tm.getSupportedModemCount() > 1);

        int oldSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();

        final String uniqueId = "00:01:02:03:04:05";
        final String displayName = "device_name";
        try {
            // Insert a second SIM
            mSm.addSubscriptionInfoRecord(uniqueId, displayName, 1,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            mSm.getActiveSubscriptionInfoForIcc(uniqueId);

            mSm.setDefaultVoiceSubscriptionId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    SubscriptionManager.getDefaultVoiceSubscriptionId());
            mSm.setDefaultVoiceSubscriptionId(oldSubId);
            assertEquals(oldSubId, SubscriptionManager.getDefaultVoiceSubscriptionId());

            // Remove the second SIM
            mSm.removeSubscriptionInfoRecord(uniqueId,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
            assertNull(mSm.getActiveSubscriptionInfoForIcc(uniqueId));
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSubscriptionPlansOverrideUnmetered() throws Exception {
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        final Network net = findCellularNetwork();
        assertNotNull("Active cellular network required", net);

        // TODO: Remove this check after b/176119724 is fixed.
        if (!isUnmetered5GSupported()) return;

        // Cellular is metered by default
        assertFalse(cm.getNetworkCapabilities(net).hasCapability(
                NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        // Override should make it go temporarily unmetered
        {
            final CountDownLatch latch = waitForNetworkCapabilities(net, caps -> {
                return caps.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
            });
            mSm.setSubscriptionOverrideUnmetered(
                    mSubId, true, TelephonyManager.getAllNetworkTypes(), 0);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }

        // Clearing override should make it go metered
        {
            final CountDownLatch latch = waitForNetworkCapabilities(net, caps -> {
                return !caps.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
            });
            mSm.setSubscriptionOverrideUnmetered(
                    mSubId, false, TelephonyManager.getAllNetworkTypes(), 0);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testSubscriptionPlansUnmetered() throws Exception {
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        final Network net = findCellularNetwork();
        assertNotNull("Active cellular network required", net);

        // TODO: Remove this check after b/176119724 is fixed.
        if (!isUnmetered5GSupported()) return;

        // Make ourselves the owner and define some plans
        setSubPlanOwner(mSubId, mPackageName);
        mSm.setSubscriptionPlans(mSubId,
                Arrays.asList(buildValidSubscriptionPlan(System.currentTimeMillis())));

        // Cellular is metered by default
        assertFalse(cm.getNetworkCapabilities(net).hasCapability(
                NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        SubscriptionPlan unmeteredPlan = SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                        SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                .build();

        // Unmetered plan should make it go unmetered
        {
            final CountDownLatch latch = waitForNetworkCapabilities(net, caps -> {
                return caps.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
            });
            mSm.setSubscriptionPlans(mSubId, Arrays.asList(unmeteredPlan));
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }

        // Metered plan should make it go metered
        {
            final CountDownLatch latch = waitForNetworkCapabilities(net, caps -> {
                return !caps.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED);
            });
            mSm.setSubscriptionPlans(mSubId,
                    Arrays.asList(buildValidSubscriptionPlan(System.currentTimeMillis())));
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testSubscriptionPlansInvalid() throws Exception {
        // Make ourselves the owner
        setSubPlanOwner(mSubId, mPackageName);

        // Empty plans can't override
        assertOverrideFails();

        // Nonrecurring plan in the past can't override
        assertOverrideFails(SubscriptionPlan.Builder
                .createNonrecurring(ZonedDateTime.now().minusDays(14),
                        ZonedDateTime.now().minusDays(7))
                .setTitle("CTS")
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .build());

        // Plan with undefined limit can't override
        assertOverrideFails(SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .build());

        // We can override when there is an active plan somewhere
        final SubscriptionPlan older = SubscriptionPlan.Builder
                .createNonrecurring(ZonedDateTime.now().minusDays(14),
                        ZonedDateTime.now().minusDays(7))
                .setTitle("CTS")
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .build();
        final SubscriptionPlan newer = SubscriptionPlan.Builder
                .createNonrecurring(ZonedDateTime.now().minusDays(7),
                        ZonedDateTime.now().plusDays(7))
                .setTitle("CTS")
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .build();
        assertOverrideSuccess(older, newer);
    }

    @Test
    public void testSubscriptionPlansNetworkTypeValidation() throws Exception {
        // Make ourselves the owner
        setSubPlanOwner(mSubId, mPackageName);

        // Error when adding 2 plans with the same network type
        List<SubscriptionPlan> plans = new ArrayList<>();
        plans.add(buildValidSubscriptionPlan(System.currentTimeMillis()));
        plans.add(SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .setNetworkTypes(new int[] {TelephonyManager.NETWORK_TYPE_LTE})
                .build());
        plans.add(SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .setNetworkTypes(new int[] {TelephonyManager.NETWORK_TYPE_LTE})
                .build());
        try {
            mSm.setSubscriptionPlans(mSubId, plans);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        // Error when there is no general plan
        plans.clear();
        plans.add(SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .setNetworkTypes(new int[] {TelephonyManager.NETWORK_TYPE_LTE})
                .build());
        try {
            mSm.setSubscriptionPlans(mSubId, plans);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSubscriptionPlanResetNetworkTypes() {
        long time = System.currentTimeMillis();
        SubscriptionPlan plan = SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .setNetworkTypes(new int[] {TelephonyManager.NETWORK_TYPE_LTE})
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .setDataUsage(500_000_000, time)
                .resetNetworkTypes()
                .build();
        assertEquals(plan, buildValidSubscriptionPlan(time));
    }

    @Test
    public void testSubscriptionGrouping() throws Exception {
        // Set subscription group with current sub Id. This should fail
        // because we don't have MODIFY_PHONE_STATE or carrier privilege permission.
        List<Integer> subGroup = new ArrayList();
        subGroup.add(mSubId);
        try {
            mSm.createSubscriptionGroup(subGroup);
            fail();
        } catch (SecurityException expected) {
        }

        // Getting subscriptions in group should return null as setSubscriptionGroup
        // should fail.
        SubscriptionInfo info = mSm.getActiveSubscriptionInfo(mSubId);
        assertNull(info.getGroupUuid());

        // Remove from subscription group with current sub Id. This should fail
        // because we don't have MODIFY_PHONE_STATE or carrier privilege permission.
        try {
            mSm.addSubscriptionsIntoGroup(subGroup, null);
            fail();
        } catch (NullPointerException expected) {
        }

        // Add into subscription group that doesn't exist. This should fail
        // because we don't have MODIFY_PHONE_STATE or carrier privilege permission.
        try {
            ParcelUuid groupUuid = new ParcelUuid(UUID.randomUUID());
            mSm.addSubscriptionsIntoGroup(subGroup, groupUuid);
            fail();
        } catch (SecurityException expected) {
        }

        // Remove from subscription group with current sub Id. This should fail
        // because we don't have MODIFY_PHONE_STATE or carrier privilege permission.
        try {
            mSm.removeSubscriptionsFromGroup(subGroup, null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    @ApiTest(apis = "android.telephony.SubscriptionManager#getSubscriptionsInGroup")
    public void testSubscriptionGroupingWithPermission() throws Exception {
        // Set subscription group with current sub Id.
        List<Integer> subGroup = new ArrayList();
        subGroup.add(mSubId);
        ParcelUuid uuid = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.createSubscriptionGroup(subGroup));

        // Getting subscriptions in group.
        List<SubscriptionInfo> infoList;
        try {
            mSm.getSubscriptionsInGroup(uuid);
            fail("SecurityException should be thrown without device identifiers");
        } catch (SecurityException ex) {
            // Expected
        }

        // has the READ_PRIVILEGED_PHONE_STATE permission
        setIdentifierAccess(true);
        try {
            infoList = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getSubscriptionsInGroup(uuid), READ_PRIVILEGED_PHONE_STATE);
        } finally {
            setIdentifierAccess(false);
        }
        assertNotNull(infoList);
        assertEquals(1, infoList.size());
        assertEquals(uuid, infoList.get(0).getGroupUuid());

        infoList = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getSubscriptionsInGroup(uuid));
        assertNotNull(infoList);
        assertEquals(1, infoList.size());
        assertEquals(uuid, infoList.get(0).getGroupUuid());

        List<SubscriptionInfo> availableInfoList;
        try {
            mSm.getAvailableSubscriptionInfoList();
            fail("SecurityException should be thrown without READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException ex) {
            // Ignore
        }
        availableInfoList = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getAvailableSubscriptionInfoList());
        // has the OPSTR_READ_DEVICE_IDENTIFIERS permission
        setIdentifierAccess(true);
        try {
            if (availableInfoList.size() > 1) {
                List<Integer> availableSubGroup = availableInfoList.stream()
                        .map(info -> info.getSubscriptionId())
                        .filter(subId -> subId != mSubId)
                        .collect(Collectors.toList());

                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                        (sm) -> sm.addSubscriptionsIntoGroup(availableSubGroup, uuid));

                infoList = mSm.getSubscriptionsInGroup(uuid);
                assertNotNull(infoList);
                assertEquals(availableInfoList.size(), infoList.size());

                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                        (sm) -> sm.removeSubscriptionsFromGroup(availableSubGroup, uuid));
            }

            // Remove from subscription group with current sub Id.
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.removeSubscriptionsFromGroup(subGroup, uuid));

            infoList = mSm.getSubscriptionsInGroup(uuid);
            assertNotNull(infoList);
            assertTrue(infoList.isEmpty());
        } finally {
            setIdentifierAccess(false);
        }
    }

    @Test
    @ApiTest(apis = "android.telephony.SubscriptionManager#getSubscriptionsInGroup")
    public void testAddSubscriptionIntoNewGroupWithPermission() throws Exception {
        // Set subscription group with current sub Id.
        List<Integer> subGroup = new ArrayList();
        subGroup.add(mSubId);
        ParcelUuid uuid = new ParcelUuid(UUID.randomUUID());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                (sm) -> sm.addSubscriptionsIntoGroup(subGroup, uuid));

        List<SubscriptionInfo> infoList;
        try {
            mSm.getSubscriptionsInGroup(uuid);
            fail("SecurityException should be thrown without device identifiers");
        } catch (SecurityException ex) {
            // Expected
        }

        // Getting subscriptions in group.
        try {
            setIdentifierAccess(true);
            infoList = mSm.getSubscriptionsInGroup(uuid);
            assertNotNull(infoList);
            assertEquals(1, infoList.size());
            assertEquals(uuid, infoList.get(0).getGroupUuid());

            // Remove from subscription group with current sub Id.
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.removeSubscriptionsFromGroup(subGroup, uuid));

            infoList = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                    (sm) -> sm.getSubscriptionsInGroup(uuid));
            assertNotNull(infoList);
            assertTrue(infoList.isEmpty());
        } finally {
            setIdentifierAccess(false);
        }
    }

    @Test
    @ApiTest(apis = "android.telephony.SubscriptionManager#setOpportunistic")
    public void testSettingOpportunisticSubscription() throws Exception {
        // Set subscription to be opportunistic. This should fail
        // because we don't have MODIFY_PHONE_STATE or carrier privilege permission.
        try {
            mSm.setOpportunistic(true, mSubId);
            fail();
        } catch (SecurityException expected) {
        }

        // Shouldn't crash.
        SubscriptionInfo info = mSm.getActiveSubscriptionInfo(mSubId);
        info.isOpportunistic();
    }

    @Test
    public void testMccMncString() {
        SubscriptionInfo info = mSm.getActiveSubscriptionInfo(mSubId);
        String mcc = info.getMccString();
        String mnc = info.getMncString();
        assertTrue(mcc == null || mcc.length() <= 3);
        assertTrue(mnc == null || mnc.length() <= 3);
    }

    @Test
    public void testSetUiccApplicationsEnabled() throws Exception {
        boolean canDisable = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.canDisablePhysicalSubscription());
        if (canDisable) {
            Object lock = new Object();
            AtomicBoolean functionCallCompleted = new AtomicBoolean(false);
            // enabled starts off as true
            AtomicBoolean valueToWaitFor = new AtomicBoolean(false);
            TestThread t = new TestThread(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();

                    SubscriptionManager.OnSubscriptionsChangedListener listener =
                            new SubscriptionManager.OnSubscriptionsChangedListener() {
                                @Override
                                public void onSubscriptionsChanged() {
                                    if (valueToWaitFor.get() == mSm.getActiveSubscriptionInfo(
                                            mSubId).areUiccApplicationsEnabled()) {
                                        synchronized (lock) {
                                            functionCallCompleted.set(true);
                                            lock.notifyAll();
                                        }
                                    }
                                }
                            };
                    mSm.addOnSubscriptionsChangedListener(listener);

                    Looper.loop();
                }
            });

            // Disable the UICC application and wait until we detect the subscription change to
            // verify
            t.start();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.setUiccApplicationsEnabled(mSubId, false));

            synchronized (lock) {
                if (!functionCallCompleted.get()) {
                    lock.wait(SUBSCRIPTION_DISABLE_WAIT_MS);
                }
            }
            if (!functionCallCompleted.get()) {
                fail("testSetUiccApplicationsEnabled was not able to disable the UICC app on time");
            }

            // Enable the UICC application and wait again
            functionCallCompleted.set(false);
            valueToWaitFor.set(true);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.setUiccApplicationsEnabled(mSubId, true));

            synchronized (lock) {
                if (!functionCallCompleted.get()) {
                    lock.wait(SUBSCRIPTION_ENABLE_WAIT_MS);
                }
            }
            if (!functionCallCompleted.get()) {
                fail("testSetUiccApplicationsEnabled was not able to enable to UICC app on time");
            }

            // Reset default data and voice subId as it may have been changed as part of the
            // calls above
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.setDefaultDataSubId(mSubId));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.setDefaultVoiceSubscriptionId(mDefaultVoiceSubId));

            // Other tests also expect that cellular data must be available if telephony is
            // supported. Wait for that before returning.
            final CountDownLatch latch = waitForCellularNetwork();
            latch.await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testSubscriptionInfoCarrierId() {
        SubscriptionInfo info = mSm.getActiveSubscriptionInfo(mSubId);
        int carrierId = info.getCarrierId();
        assertTrue(carrierId >= TelephonyManager.UNKNOWN_CARRIER_ID);
    }

    @Test
    public void testGetOpportunisticSubscriptions() throws Exception {
        List<SubscriptionInfo> infoList = mSm.getOpportunisticSubscriptions();

        for (SubscriptionInfo info : infoList) {
            assertTrue(info.isOpportunistic());
        }
    }

    @Test
    public void testGetEnabledSubscriptionId() {
        int slotId = SubscriptionManager.getSlotIndex(mSubId);
        if (!SubscriptionManager.isValidSlotIndex(slotId)) {
            fail("Invalid slot id " + slotId + " for subscription id " + mSubId);
        }
        int enabledSubId = executeWithShellPermissionAndDefault(-1, mSm,
                (sm) -> sm.getEnabledSubscriptionId(slotId));
        assertEquals(mSubId, enabledSubId);
    }

    @Test
    public void testGetActiveDataSubscriptionId() {
        int activeDataSubIdCurrent = executeWithShellPermissionAndDefault(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, mSm,
                (sm) -> sm.getActiveDataSubscriptionId());

        if (activeDataSubIdCurrent != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            List<SubscriptionInfo> subscriptionInfos = mSm.getCompleteActiveSubscriptionInfoList();
            boolean foundSub = subscriptionInfos.stream()
                    .anyMatch(x -> x.getSubscriptionId() == activeDataSubIdCurrent);
            assertTrue(foundSub);
        }
    }

    @Test
    public void testSetPreferredDataSubscriptionId() {
        int preferredSubId = executeWithShellPermissionAndDefault(-1, mSm,
                (sm) -> sm.getPreferredDataSubscriptionId());
        if (preferredSubId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            // Make sure to switch back to primary/default data sub first.
            setPreferredDataSubId(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        }

        List<SubscriptionInfo> subscriptionInfos = mSm.getCompleteActiveSubscriptionInfoList();

        for (SubscriptionInfo subInfo : subscriptionInfos) {
            // Only test on opportunistic subscriptions.
            if (!subInfo.isOpportunistic()) continue;
            setPreferredDataSubId(subInfo.getSubscriptionId());
        }

        // Switch data back to previous preferredSubId.
        setPreferredDataSubId(preferredSubId);
    }

    @Test
    public void testRestoreAllSimSpecificSettingsFromBackup() throws Throwable {
        int activeDataSubId = SubscriptionManager.getActiveDataSubscriptionId();
        assertNotEquals(activeDataSubId, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        SubscriptionInfo activeSubInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(activeDataSubId));
        String isoCountryCode = activeSubInfo.getCountryIso();

        byte[] backupData = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                SubscriptionManager::getAllSimSpecificSettingsForBackup);
        assertTrue(backupData.length > 0);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);

        overrideCarrierConfig(bundle, activeDataSubId);
        try {
            // Get the original ims values.
            ImsManager imsManager = InstrumentationRegistry.getContext().getSystemService(
                    ImsManager.class);
            ImsMmTelManager mMmTelManager = imsManager.getImsMmTelManager(activeDataSubId);
            boolean isVolteVtEnabledOriginal = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mMmTelManager, ImsMmTelManager::isAdvancedCallingSettingEnabled);
            boolean isVtImsEnabledOriginal = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mMmTelManager, ImsMmTelManager::isVtSettingEnabled);
            boolean isVoWiFiSettingEnabledOriginal =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mMmTelManager, ImsMmTelManager::isVoWiFiSettingEnabled);
            int voWifiModeOriginal = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mMmTelManager, ImsMmTelManager::getVoWiFiModeSetting);
            int voWiFiRoamingModeOriginal = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mMmTelManager, ImsMmTelManager::getVoWiFiRoamingModeSetting);

            // Get the original RcsUce values.
            ImsRcsManager imsRcsManager = imsManager.getImsRcsManager(activeDataSubId);
            RcsUceAdapter rcsUceAdapter = imsRcsManager.getUceAdapter();
            boolean isImsRcsUceEnabledOriginal =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                    rcsUceAdapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                    android.Manifest.permission.READ_PHONE_STATE);

            //Change values in DB.
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setAdvancedCallingSettingEnabled(!isVolteVtEnabledOriginal));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setVtSettingEnabled(!isVtImsEnabledOriginal));
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(
                    rcsUceAdapter, (a) -> a.setUceSettingEnabled(!isImsRcsUceEnabledOriginal),
                    ImsException.class);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setVoWiFiSettingEnabled(!isVoWiFiSettingEnabledOriginal));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setVoWiFiModeSetting((voWifiModeOriginal + 1) % 3));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setVoWiFiRoamingModeSetting((voWiFiRoamingModeOriginal + 1) % 3));

            // Restore back to original values.
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                    (sm) -> sm.restoreAllSimSpecificSettingsFromBackup(backupData));

            final long maxWaitMillis = 5000;
            try {
                waitForSubscriptionCondition(
                        () -> isVolteVtEnabledOriginal == isAdvancedCallingSettingEnabled(mSubId),
                        maxWaitMillis);
                assertEquals(isVolteVtEnabledOriginal, isAdvancedCallingSettingEnabled(mSubId));
            } finally {
                overrideCarrierConfig(null, mSubId);
            }

            // Get ims values to verify with.
            boolean isVolteVtEnabledAfterRestore =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mMmTelManager, ImsMmTelManager::isAdvancedCallingSettingEnabled);
            boolean isVtImsEnabledAfterRestore =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mMmTelManager, ImsMmTelManager::isVtSettingEnabled);
            boolean isVoWiFiSettingEnabledAfterRestore =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mMmTelManager, ImsMmTelManager::isVoWiFiSettingEnabled);
            int voWifiModeAfterRestore = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mMmTelManager, ImsMmTelManager::getVoWiFiModeSetting);
            int voWiFiRoamingModeAfterRestore = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mMmTelManager, ImsMmTelManager::getVoWiFiRoamingModeSetting);
            // Get RcsUce values to verify with.
            boolean isImsRcsUceEnabledAfterRestore =
                    ShellIdentityUtils.invokeThrowableMethodWithShellPermissions(
                            rcsUceAdapter, RcsUceAdapter::isUceSettingEnabled, ImsException.class,
                            android.Manifest.permission.READ_PHONE_STATE);

            assertEquals(isVolteVtEnabledOriginal, isVolteVtEnabledAfterRestore);
            if (isoCountryCode == null
                    || isoCountryCode.equals("us")
                    || isoCountryCode.equals("ca")) {
                assertEquals(!isVoWiFiSettingEnabledOriginal, isVoWiFiSettingEnabledAfterRestore);
            } else {
                assertEquals(isVoWiFiSettingEnabledOriginal, isVoWiFiSettingEnabledAfterRestore);
            }
            assertEquals(voWifiModeOriginal, voWifiModeAfterRestore);
            assertEquals(voWiFiRoamingModeOriginal, voWiFiRoamingModeAfterRestore);
            assertEquals(isVtImsEnabledOriginal, isVtImsEnabledAfterRestore);
            assertEquals(isImsRcsUceEnabledOriginal, isImsRcsUceEnabledAfterRestore);
        } finally {
            // restore original carrier config.
            overrideCarrierConfig(null, activeDataSubId);
        }


        try {
            // Check api call will fail without proper permissions.
            mSm.restoreAllSimSpecificSettingsFromBackup(backupData);
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testSetAndGetD2DStatusSharing() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MODIFY_PHONE_STATE);
        try {
            int originalD2DStatusSharing = mSm.getDeviceToDeviceStatusSharingPreference(mSubId);
            mSm.setDeviceToDeviceStatusSharingPreference(mSubId,
                    SubscriptionManager.D2D_SHARING_ALL_CONTACTS);
            assertEquals(SubscriptionManager.D2D_SHARING_ALL_CONTACTS,
                    mSm.getDeviceToDeviceStatusSharingPreference(mSubId));
            mSm.setDeviceToDeviceStatusSharingPreference(
                    mSubId, SubscriptionManager.D2D_SHARING_ALL);
            assertEquals(SubscriptionManager.D2D_SHARING_ALL,
                    mSm.getDeviceToDeviceStatusSharingPreference(mSubId));
            mSm.setDeviceToDeviceStatusSharingPreference(mSubId, originalD2DStatusSharing);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSetAndGetD2DSharingContacts() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MODIFY_PHONE_STATE);
        try {
            List<Uri> originalD2DSharingContacts =
                    mSm.getDeviceToDeviceStatusSharingContacts(mSubId);
            mSm.setDeviceToDeviceStatusSharingContacts(mSubId, CONTACTS);
            try {
                assertEquals(CONTACTS, mSm.getDeviceToDeviceStatusSharingContacts(mSubId));
            } finally {
                mSm.setDeviceToDeviceStatusSharingContacts(mSubId, originalD2DSharingContacts);
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void tetsSetAndGetPhoneNumber() throws Exception {
        // The phone number may be anything depends on the state of SIM and device.
        // Simply call the getter and make sure no exception.

        // Getters accessiable with READ_PRIVILEGED_PHONE_STATE
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(READ_PRIVILEGED_PHONE_STATE);
        try {
            mSm.getPhoneNumber(mSubId);
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_UICC);
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER);
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        // Getters accessiable with READ_PHONE_NUMBERS
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.READ_PHONE_NUMBERS);
        try {
            mSm.getPhoneNumber(mSubId);
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_UICC);
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER);
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        // Getters and the setter accessiable with carrier privilege
        final String carrierNumber = "1234567890";
        CarrierPrivilegeUtils.withCarrierPrivileges(
                InstrumentationRegistry.getContext(),
                mSubId,
                () -> {
                    mSm.getPhoneNumber(mSubId);
                    mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_UICC);
                    mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);

                    mSm.setCarrierPhoneNumber(mSubId, carrierNumber);
                    assertEquals(
                            carrierNumber,
                            mSm.getPhoneNumber(
                                    mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER));
                });

        // Otherwise, getter and setter will hit SecurityException
        try {
            mSm.getPhoneNumber(mSubId);
            fail("Expect SecurityException from getPhoneNumber()");
        } catch (SecurityException e) {
            // expected
        }
        try {
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_UICC);
            fail("Expect SecurityException from getPhoneNumber()");
        } catch (SecurityException e) {
            // expected
        }
        try {
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_IMS);
            fail("Expect SecurityException from getPhoneNumber()");
        } catch (SecurityException e) {
            // expected
        }
        try {
            mSm.getPhoneNumber(mSubId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER);
            fail("Expect SecurityException from getPhoneNumber()");
        } catch (SecurityException e) {
            // expected
        }
        try {
            mSm.setCarrierPhoneNumber(mSubId, "987");
            fail("Expect SecurityException from setCarrierPhoneNumber()");
        } catch (SecurityException e) {
            // expected
        }
    }

    private Set<Integer> getSupportedUsageSettings() throws Exception {
        final Set<Integer> supportedUsageSettings = new HashSet();
        final Context context = InstrumentationRegistry.getContext();

        // Vendors can add supported usage settings by adding resources.
        try {
            int[] usageSettingsFromResource = context.getResources().getIntArray(
                Resources.getSystem().getIdentifier("config_supported_cellular_usage_settings","array","android"));

            for (int setting : usageSettingsFromResource) {
                supportedUsageSettings.add(setting);
            }

        } catch (Resources.NotFoundException ignore) {
        }

        // For devices shipping with Radio HAL 2.0 and/or non-HAL devices launching with T,
        // the usage settings are required to be supported if the rest of the telephony stack
        // has support for that mode of operation.
        if (PropertyUtil.isVendorApiLevelAtLeast(android.os.Build.VERSION_CODES.TIRAMISU)) {
            final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA)) {
                supportedUsageSettings.add(SubscriptionManager.USAGE_SETTING_DATA_CENTRIC);
            }
            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING)) {
                supportedUsageSettings.add(SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC);
            }
        }

        return supportedUsageSettings;
    }

    private int getUsageSetting() throws Exception {
        SubscriptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(mSubId));
        return info.getUsageSetting();
    }

    private void checkUsageSetting(int inputSetting, boolean isSupported) throws Exception {
        final int initialSetting = getUsageSetting();

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.KEY_CELLULAR_USAGE_SETTING_INT, inputSetting);
        overrideCarrierConfig(bundle, mSubId);

        final int newSetting = getUsageSetting();
        assertEquals(isSupported ? inputSetting : initialSetting, newSetting);
    }

    @Test
    public void testCellularUsageSetting() throws Exception {
        Set<Integer> supportedUsageSettings = getSupportedUsageSettings();

        // If any setting works, default must be allowed.
        if (supportedUsageSettings.size() > 0) {
            supportedUsageSettings.add(SubscriptionManager.USAGE_SETTING_DEFAULT);
        }

        final int[] allUsageSettings = new int[]{
                SubscriptionManager.USAGE_SETTING_UNKNOWN,
                SubscriptionManager.USAGE_SETTING_DEFAULT,
                SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC,
                SubscriptionManager.USAGE_SETTING_DATA_CENTRIC,
                3 /* undefined value */};

        try {
            for (int setting : allUsageSettings) {
                checkUsageSetting(setting, supportedUsageSettings.contains(setting));
            }
        } finally {
            overrideCarrierConfig(null, mSubId);
        }
    }

    @Test
    public void testCreateSubscriptionChangedListenerWithoutLooper() throws Throwable {
        CompletableFuture<Throwable> futureResult = new CompletableFuture<>();

        Thread t = new Thread(() -> {
            SubscriptionManager.OnSubscriptionsChangedListener oscl =
                    new SubscriptionManager.OnSubscriptionsChangedListener();
            try {
                mSm.addOnSubscriptionsChangedListener((r) -> {}, oscl);
                mSm.removeOnSubscriptionsChangedListener(oscl);
                futureResult.complete(null);
            } catch (Exception e) {
                futureResult.complete(e);
                try {
                    mSm.removeOnSubscriptionsChangedListener(oscl);
                } catch (Exception likely) {
                    // nothing to do
                }
            }

            try {
                mSm.addOnSubscriptionsChangedListener(oscl);
                futureResult.complete(
                        new Exception("Looper wasn't required as expected"));
            } catch (Exception expected) {
                futureResult.complete(null);
            } finally {
                mSm.removeOnSubscriptionsChangedListener(oscl);
            }
        });

        t.start();
        t.join(5000 /*millis*/);

        if (futureResult.get() != null) throw futureResult.get();
    }

    private String getSubscriptionIso(int subId) {
        SubscriptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(subId));
        return info.getCountryIso();
    }

    private boolean isAdvancedCallingSettingEnabled(int subId) {
        ImsManager imsManager = InstrumentationRegistry.getContext().getSystemService(
                ImsManager.class);
        ImsMmTelManager mMmTelManager = imsManager.getImsMmTelManager(subId);
        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                mMmTelManager, ImsMmTelManager::isAdvancedCallingSettingEnabled);
    }

    /**
     * Monitor the onSubscriptionsChangedListener until a condition is satisfied.
     */
    private void waitForSubscriptionCondition(
            BooleanSupplier condition, long maxWaitMillis) throws Throwable {
        final Object lock = new Object();

        TestThread t = new TestThread(() -> {
            Looper.prepare();

            SubscriptionManager.OnSubscriptionsChangedListener listener =
                    new SubscriptionManager.OnSubscriptionsChangedListener() {
                        @Override
                        public void onSubscriptionsChanged() {
                            synchronized (lock) {
                                if (condition.getAsBoolean()) {
                                    lock.notifyAll();
                                    Looper.myLooper().quitSafely();
                                }
                            }
                        }
                    };
            mSm.addOnSubscriptionsChangedListener(listener);
            try {
                synchronized (lock) {
                    if (condition.getAsBoolean()) lock.notifyAll();
                }
                if (!condition.getAsBoolean()) Looper.loop();
            } finally {
                mSm.removeOnSubscriptionsChangedListener(listener);
            }
        });

        synchronized (lock) {
            if (condition.getAsBoolean()) return;
            t.start();
            lock.wait(maxWaitMillis);
            t.joinAndCheck(5000);
        }
    }

    @Test
    public void testCountryIso() throws Throwable {
        final String liechtensteinIso = "li";
        final String faroeIslandsIso = "fo";

        final TelephonyManager tm = InstrumentationRegistry.getContext()
                .getSystemService(TelephonyManager.class).createForSubscriptionId(mSubId);

        final long maxWaitMillis = 5000;
        final String isoUT = liechtensteinIso.equals(getSubscriptionIso(mSubId))
                ? faroeIslandsIso : liechtensteinIso;

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, isoUT);
        overrideCarrierConfig(bundle, mSubId);
        try {
            waitForSubscriptionCondition(
                    () -> isoUT.equals(getSubscriptionIso(mSubId)),
                    maxWaitMillis);

            assertEquals(isoUT, getSubscriptionIso(mSubId));
            assertEquals(isoUT, tm.getSimCountryIso());
        } finally {
            overrideCarrierConfig(null, mSubId);
        }
    }

    @Test
    public void testIsNtn_enableFlag() throws Exception {
        if (!Flags.oemEnabledSatelliteFlag()) {
            return;
        }

        SubscriptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(mSubId));
        assertThat(info.isOnlyNonTerrestrialNetwork()).isNotNull();
    }

    @Test
    public void testIsNtn_disableFlag() throws Exception {
        if (Flags.oemEnabledSatelliteFlag()) {
            return;
        }

        SubscriptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(mSubId));
        assertThat(info.isOnlyNonTerrestrialNetwork()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_ONLY_CELLULAR_SERVICE)
    @ApiTest(apis = {"android.telephony.SubscriptionInfo#getServiceCapabilities"})
    public void testSubscriptionInfo_getServiceCapabilities() throws Exception {
        final List<SubscriptionInfo> allSubInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                        (sm) -> sm.getAllSubscriptionInfoList());
        for (SubscriptionInfo subInfo : allSubInfos) {
            final Set<Integer> capabilities = subInfo.getServiceCapabilities();

            assertThat(capabilities).isNotNull();
            for (int capability : capabilities) {
                assertTrue(isValidServiceCapability(capability));
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_PSIM_TO_ESIM_CONVERSION)
    public void testUpdateSubscription_transferStatus() throws Exception {
        // Testing permission fail
        try {
            mSm.setTransferStatus(mSubId, TRANSFER_STATUS_TRANSFERRED_OUT);
        } catch (SecurityException expected) {
            fail();
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                (sm) -> sm.setTransferStatus(mSubId, TRANSFER_STATUS_TRANSFERRED_OUT));
        SubscriptionInfo info = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getActiveSubscriptionInfo(mSubId));
        assertEquals(info.getTransferStatus(), TRANSFER_STATUS_TRANSFERRED_OUT);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUBSCRIPTION_USER_ASSOCIATION_QUERY)
    public void testIsSubscriptionAssociatedWithUser() throws Exception {

        UserHandle oldAssociatedUser = ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.getSubscriptionUserHandle(mSubId));

        // Testing with the current context user.
        UserHandle currentUserHandle = InstrumentationRegistry.getContext().getUser();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                (sm) -> sm.setSubscriptionUserHandle(mSubId, currentUserHandle));

        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.isSubscriptionAssociatedWithUser(mSubId)));

        // Testing with any random user which is not the current context user.
        UserHandle nonCurrentUserHandle = UserHandle.of(currentUserHandle.getIdentifier() + 1);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                (sm) -> sm.setSubscriptionUserHandle(mSubId, nonCurrentUserHandle));

        assertFalse(ShellIdentityUtils.invokeMethodWithShellPermissions(mSm,
                (sm) -> sm.isSubscriptionAssociatedWithUser(mSubId)));

        // Resetting it to the state before test.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                (sm) -> sm.setSubscriptionUserHandle(mSubId, oldAssociatedUser));

    }

    private boolean isValidServiceCapability(int capability) {
        return capability >= SubscriptionManager.SERVICE_CAPABILITY_VOICE
                && capability <= SubscriptionManager.SERVICE_CAPABILITY_MAX;
    }

    @Nullable
    private PersistableBundle getBundleFromBackupData(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            return PersistableBundle.readFromStream(bis);
        } catch (IOException e) {
            return null;
        }
    }

    private void setPreferredDataSubId(int subId) {
        final LinkedBlockingQueue<Integer> resultQueue = new LinkedBlockingQueue<>(1);
        Executor executor = (command)-> command.run();
        Consumer<Integer> consumer = (res)-> {
            if (res == null) {
                resultQueue.offer(-1);
            } else {
                resultQueue.offer(res);
            }
        };

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mSm,
                (sm) -> sm.setPreferredDataSubscriptionId(subId, false,
                        executor, consumer));
        int res = -1;
        try {
            res = resultQueue.poll(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Cannot get the modem result in time");
        }

        assertEquals(SET_OPPORTUNISTIC_SUB_SUCCESS, res);
        int getValue = executeWithShellPermissionAndDefault(-1, mSm,
                (sm) -> sm.getPreferredDataSubscriptionId());
        assertEquals(subId, getValue);
    }

    private <T, U> T executeWithShellPermissionAndDefault(T defaultValue, U targetObject,
            ShellIdentityUtils.ShellPermissionMethodHelper<T, U> helper) {
        try {
            return ShellIdentityUtils.invokeMethodWithShellPermissions(targetObject, helper);
        } catch (Exception e) {
            // do nothing, return default
        }
        return defaultValue;
    }

    private void assertOverrideSuccess(SubscriptionPlan... plans) {
        mSm.setSubscriptionPlans(mSubId, Arrays.asList(plans));
        mSm.setSubscriptionOverrideCongested(mSubId, false, 0);
    }

    private void assertOverrideFails(SubscriptionPlan... plans) {
        mSm.setSubscriptionPlans(mSubId, Arrays.asList(plans));
        try {
            mSm.setSubscriptionOverrideCongested(mSubId, false, 0);
            fail();
        } catch (SecurityException | IllegalStateException expected) {
        }
    }

    public static CountDownLatch waitForNetworkCapabilities(Network network,
            Predicate<NetworkCapabilities> predicate) {
        final CountDownLatch latch = new CountDownLatch(1);
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        cm.registerNetworkCallback(new NetworkRequest.Builder().build(),
                new NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network net, NetworkCapabilities caps) {
                        if (net.equals(network) && predicate.test(caps)) {
                            latch.countDown();
                            cm.unregisterNetworkCallback(this);
                        }
                    }
                });
        return latch;
    }

    /**
     * Corresponding to findCellularNetwork()
     */
    private static CountDownLatch waitForCellularNetwork() {
        final CountDownLatch latch = new CountDownLatch(1);
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        cm.registerNetworkCallback(new NetworkRequest.Builder().build(),
                new NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network net, NetworkCapabilities caps) {
                        if (caps.hasTransport(TRANSPORT_CELLULAR)
                                && caps.hasCapability(NET_CAPABILITY_INTERNET)
                                && caps.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
                            latch.countDown();
                            cm.unregisterNetworkCallback(this);
                        }
                    }
                });
        return latch;
    }

    private static SubscriptionPlan buildValidSubscriptionPlan(long dataUsageTime) {
        return SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setTitle("CTS")
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .setDataUsage(500_000_000, dataUsageTime)
                .build();
    }

    private static @Nullable Network findCellularNetwork() {
        final ConnectivityManager cm = InstrumentationRegistry.getContext()
                .getSystemService(ConnectivityManager.class);
        for (Network net : cm.getAllNetworks()) {
            final NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps != null && caps.hasTransport(TRANSPORT_CELLULAR)
                    && caps.hasCapability(NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
                return net;
            }
        }
        return null;
    }

    private static boolean isSupported() {
        return InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION);
    }

    private static boolean isAutomotive() {
        return InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private static boolean isDSDS() {
        TelephonyManager tm = InstrumentationRegistry.getContext()
                .getSystemService(TelephonyManager.class);
        return tm != null && tm.getPhoneCount() > 1;
    }

    private static void setSubPlanOwner(int subId, String packageName) throws Exception {
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy set sub-plan-owner " + subId + " " + packageName);
    }

    private boolean isUnmetered5GSupported() {
        final CarrierConfigManager ccm = InstrumentationRegistry.getContext()
                .getSystemService(CarrierConfigManager.class);
        PersistableBundle carrierConfig = ccm.getConfigForSubId(mSubId);

        final TelephonyManager tm = InstrumentationRegistry.getContext()
                .getSystemService(TelephonyManager.class);

        int dataNetworkType;
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            if (InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)) {
                dataNetworkType = tm.getDataNetworkType(mSubId);
            } else {
                dataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } else {
            dataNetworkType = tm.getDataNetworkType(mSubId);
        }
        long supportedRats = ShellIdentityUtils.invokeMethodWithShellPermissions(tm,
                TelephonyManager::getSupportedRadioAccessFamily);

        boolean validCarrier = carrierConfig.getBoolean(
                CarrierConfigManager.KEY_NETWORK_TEMP_NOT_METERED_SUPPORTED_BOOL);
        boolean validCapabilities = (supportedRats & TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0;
        // TODO: need to check for TelephonyDisplayInfo override for NR NSA
        boolean validNetworkType = dataNetworkType == TelephonyManager.NETWORK_TYPE_NR;

        return validCarrier && validNetworkType && validCapabilities;
    }

    private void setIdentifierAccess(boolean allowed) {
        CountDownLatch changeLatch = new CountDownLatch(1);
        String op = AppOpsManager.OPSTR_READ_DEVICE_IDENTIFIERS;
        AppOpsManager appOpsManager = InstrumentationRegistry.getContext().getSystemService(
                AppOpsManager.class);
        int mode = allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.opToDefaultMode(op);
        if (appOpsManager.unsafeCheckOpNoThrow(op, Process.myUid(),
                    InstrumentationRegistry.getContext().getOpPackageName()) == mode) {
            return;
        }
        AppOpsManager.OnOpChangedListener opListener =
                (String appOp, String packageName) -> changeLatch.countDown();
        appOpsManager.startWatchingMode(op, InstrumentationRegistry.getContext().getOpPackageName(),
                opListener);
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    appOpsManager, (appOps) -> appOps.setUidMode(op, Process.myUid(), mode));
            changeLatch.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            fail("Never received appOp change for Identifier Access");
        } finally {
            appOpsManager.stopWatchingMode(opListener);
        }
    }
}
