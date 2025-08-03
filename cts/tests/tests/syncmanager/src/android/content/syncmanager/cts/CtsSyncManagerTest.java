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
package android.content.syncmanager.cts;

import static android.content.syncmanager.cts.common.Values.ACCOUNT_1_A;
import static android.content.syncmanager.cts.common.Values.APP1_AUTHORITY;
import static android.content.syncmanager.cts.common.Values.APP1_PACKAGE;

import static com.android.compatibility.common.util.BundleUtils.makeBundle;
import static com.android.compatibility.common.util.ConnectivityUtils.assertNetworkConnected;
import static com.android.compatibility.common.util.SystemUtil.runCommandAndPrintOnLogcat;
import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static junit.framework.TestCase.assertEquals;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.accounts.Account;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Request.AddAccount;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Request.ClearSyncInvocations;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Request.GetSyncInvocations;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Request.RemoveAllAccounts;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Request.SetResult;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Request.SetResult.Result;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.Response;
import android.content.syncmanager.cts.SyncManagerCtsProto.Payload.SyncInvocation;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.OnFailureRule;
import com.android.compatibility.common.util.ParcelUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserSettings;
import com.android.compatibility.common.util.UserSettings.Namespace;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class CtsSyncManagerTest {
    private static final String TAG = "CtsSyncManagerTest";

    public static final int DEFAULT_TIMEOUT_SECONDS = 10 * 60;

    public static final boolean DEBUG = false;

    private static final int STANDBY_BUCKET_NEVER = 50;

    @Rule
    public final OnFailureRule mDumpOnFailureRule = new OnFailureRule(TAG) {
        @Override
        protected void onTestFailure(Statement base, Description description, Throwable t) {
            runCommandAndPrintOnLogcat(TAG, "dumpsys content");
            runCommandAndPrintOnLogcat(TAG, "dumpsys jobscheduler");
        }
    };

    protected final BroadcastRpc mRpc = new BroadcastRpc();

    Context mContext;
    ContentResolver mContentResolver;

    @Before
    public void setUp() throws Exception {
        assertNetworkConnected(InstrumentationRegistry.getContext());

        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableAdaptiveBatterySaver(false);
        // Don't wait so tests can also run for devices without battery saver.
        BatteryUtils.enableBatterySaver(false, false);

        AmUtils.setStandbyBucket(APP1_PACKAGE, UsageStatsManager.STANDBY_BUCKET_ACTIVE);

        mContext = InstrumentationRegistry.getContext();
        mContentResolver = mContext.getContentResolver();

        ContentResolver.setMasterSyncAutomatically(true);

        mRpc.invoke(APP1_PACKAGE, rb ->
                rb.setSetResult(SetResult.newBuilder().setResult(Result.OK)));

        Thread.sleep(1000); // Don't make the system too busy...
    }

    @After
    public void tearDown() throws Exception {
        resetSyncConfig();
        setDozeState(false);
        BatteryUtils.runDumpsysBatteryReset();
    }

    private static final UserSettings sGlobalSettings = new UserSettings(Namespace.GLOBAL);

    private static void resetSyncConfig() {
        sGlobalSettings.set("sync_manager_constants", "null");
    }

    private static void writeSyncConfig(
            int initialSyncRetryTimeInSeconds,
            float retryTimeIncreaseFactor,
            int maxSyncRetryTimeInSeconds,
            int maxRetriesWithAppStandbyExemption) {
        sGlobalSettings.set("sync_manager_constants",
                "initial_sync_retry_time_in_seconds=" + initialSyncRetryTimeInSeconds + "," +
                "retry_time_increase_factor=" + retryTimeIncreaseFactor + "," +
                "max_sync_retry_time_in_seconds=" + maxSyncRetryTimeInSeconds + "," +
                "max_retries_with_app_standby_exemption=" + maxRetriesWithAppStandbyExemption);
    }

    /** Return the part of "dumpsys content" that's relevant to the current sync status. */
    private String getSyncDumpsys() {
        final String out = SystemUtil.runCommandAndExtractSection("dumpsys content",
                "^Active Syncs:.*", false,
                "^Sync Statistics", false);
        return out;
    }

    private void removeAllAccounts() throws Exception {
        mRpc.invoke(APP1_PACKAGE,
                rb -> rb.setRemoveAllAccounts(RemoveAllAccounts.newBuilder()));

        Thread.sleep(1000);

        AmUtils.waitForBroadcastIdle();

        waitUntil("Dumpsys still mentions " + ACCOUNT_1_A, DEFAULT_TIMEOUT_SECONDS,
                () -> !getSyncDumpsys().contains(ACCOUNT_1_A.name));

        Thread.sleep(1000);
    }

    private void clearSyncInvocations(String packageName) throws Exception {
        mRpc.invoke(packageName,
                rb -> rb.setClearSyncInvocations(ClearSyncInvocations.newBuilder()));
    }

    private void addAccountAndLetInitialSyncRun(Account account, String authority)
            throws Exception {
        // Add the first account, which will trigger an initial sync.
        mRpc.invoke(APP1_PACKAGE,
                rb -> rb.setAddAccount(AddAccount.newBuilder().setName(account.name)));

        waitUntil("Syncable isn't initialized", DEFAULT_TIMEOUT_SECONDS,
                () -> ContentResolver.getIsSyncable(account, authority) == 1);

        waitUntil("Periodic sync should set up", DEFAULT_TIMEOUT_SECONDS,
                () -> ContentResolver.getPeriodicSyncs(account, authority).size() == 1);
        assertEquals("Periodic should be 24h",
                24 * 60 * 60, ContentResolver.getPeriodicSyncs(account, authority).get(0).period);
    }

    @Test
    public void testInitialSync() throws Exception {
        removeAllAccounts();

        mRpc.invoke(APP1_PACKAGE, rb -> rb.setClearSyncInvocations(
                ClearSyncInvocations.newBuilder()));

        // Add the first account, which will trigger an initial sync.
        addAccountAndLetInitialSyncRun(ACCOUNT_1_A, APP1_AUTHORITY);

        // Check the sync request parameters.

        Response res = mRpc.invoke(APP1_PACKAGE,
                rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
        assertEquals(1, res.getSyncInvocations().getSyncInvocationsCount());

        SyncInvocation si = res.getSyncInvocations().getSyncInvocations(0);

        assertEquals(ACCOUNT_1_A.name, si.getAccountName());
        assertEquals(ACCOUNT_1_A.type, si.getAccountType());
        assertEquals(APP1_AUTHORITY, si.getAuthority());

        Bundle extras = ParcelUtils.fromBytes(si.getExtras().toByteArray());
        assertTrue(extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE));
    }

    @Test
    @FlakyTest
    public void testSoftErrorRetriesActiveApp() throws Exception {
        removeAllAccounts();

        // Let the initial sync happen.
        addAccountAndLetInitialSyncRun(ACCOUNT_1_A, APP1_AUTHORITY);

        writeSyncConfig(2, 1, 2, 3);

        clearSyncInvocations(APP1_PACKAGE);

        AmUtils.setStandbyBucket(APP1_PACKAGE, UsageStatsManager.STANDBY_BUCKET_ACTIVE);

        // Set soft error.
        mRpc.invoke(APP1_PACKAGE, rb ->
                rb.setSetResult(SetResult.newBuilder().setResult(Result.SOFT_ERROR)));

        Bundle b = makeBundle(
                "testSoftErrorRetriesActiveApp", true,
                ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);

        ContentResolver.requestSync(ACCOUNT_1_A, APP1_AUTHORITY, b);

        // First sync + 3 retries == 4, so should be called more than 4 times.
        // But it's active, so it should retry more than that.
        waitUntil("Should retry more than 3 times.", DEFAULT_TIMEOUT_SECONDS, () -> {
            final Response res = mRpc.invoke(APP1_PACKAGE,
                    rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
            final int calls = res.getSyncInvocations().getSyncInvocationsCount();
            Log.i(TAG, "NumSyncInvocations=" + calls);
            return calls > 4; // Arbitrarily bigger than 4.
        });
    }

    @Test
    public void testExpeditedJobSync() throws Exception {
        setDozeState(false);
        removeAllAccounts();

        // Let the initial sync happen.
        addAccountAndLetInitialSyncRun(ACCOUNT_1_A, APP1_AUTHORITY);

        writeSyncConfig(2, 1, 2, 3);

        clearSyncInvocations(APP1_PACKAGE);

        AmUtils.setStandbyBucket(APP1_PACKAGE, UsageStatsManager.STANDBY_BUCKET_RARE);

        Bundle b = makeBundle(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB, true,
                ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);

        ContentResolver.requestSync(ACCOUNT_1_A, APP1_AUTHORITY, b);

        waitUntil("Expedited job sync didn't run in Doze", 30, () -> {
            final Response res = mRpc.invoke(APP1_PACKAGE,
                    rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
            final int calls = res.getSyncInvocations().getSyncInvocationsCount();
            Log.i(TAG, "NumSyncInvocations=" + calls);
            return calls == 1;
        });
    }

    @Test
    public void testExpeditedJobSync_InDoze() throws Exception {
        assumeTrue(isDozeFeatureEnabled());

        setDozeState(false);
        removeAllAccounts();

        // Let the initial sync happen.
        addAccountAndLetInitialSyncRun(ACCOUNT_1_A, APP1_AUTHORITY);

        writeSyncConfig(2, 1, 2, 3);

        clearSyncInvocations(APP1_PACKAGE);

        AmUtils.setStandbyBucket(APP1_PACKAGE, UsageStatsManager.STANDBY_BUCKET_RARE);

        setDozeState(true);
        Bundle b = makeBundle(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB, true,
                ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);

        ContentResolver.requestSync(ACCOUNT_1_A, APP1_AUTHORITY, b);

        waitUntil("Expedited job sync should still run in Doze", 30, () -> {
            final Response res = mRpc.invoke(APP1_PACKAGE,
                    rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
            final int calls = res.getSyncInvocations().getSyncInvocationsCount();
            Log.i(TAG, "NumSyncInvocations=" + calls);
            return calls == 1;
        });
    }

    @Test
    public void testInitialSyncInNeverBucket() throws Exception {
        removeAllAccounts();

        AmUtils.setStandbyBucket(APP1_PACKAGE, STANDBY_BUCKET_NEVER);

        mRpc.invoke(APP1_PACKAGE, rb -> rb.setClearSyncInvocations(
                ClearSyncInvocations.newBuilder()));

        addAccountAndLetInitialSyncRun(ACCOUNT_1_A, APP1_AUTHORITY);

        // App should be brought out of the NEVER bucket to handle the sync
        assertTrue("Standby bucket should be WORKING_SET or better",
                AmUtils.getStandbyBucket(APP1_PACKAGE)
                        <= UsageStatsManager.STANDBY_BUCKET_WORKING_SET);

        // Check the sync request parameters.
        Response res = mRpc.invoke(APP1_PACKAGE,
                rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
        assertEquals(1, res.getSyncInvocations().getSyncInvocationsCount());

        SyncInvocation si = res.getSyncInvocations().getSyncInvocations(0);

        assertEquals(ACCOUNT_1_A.name, si.getAccountName());
        assertEquals(ACCOUNT_1_A.type, si.getAccountType());
        assertEquals(APP1_AUTHORITY, si.getAuthority());

        Bundle extras = ParcelUtils.fromBytes(si.getExtras().toByteArray());
        assertTrue(extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE));
    }

    private static boolean isDozeFeatureEnabled() {
        final String output = ShellUtils.runShellCommand("cmd deviceidle enabled deep").trim();
        return Integer.parseInt(output) != 0;
    }

    private void setDozeState(final boolean on) throws Exception {
        ShellUtils.runShellCommand("cmd deviceidle " + (on ? "force-idle" : "unforce"));
        if (!on) {
            // Make sure the device doesn't stay idle, even after unforcing.
            ShellUtils.runShellCommand("cmd deviceidle motion");
        }
        final PowerManager powerManager =
                InstrumentationRegistry.getContext().getSystemService(PowerManager.class);
        waitUntil("Doze mode didn't change to " + (on ? "on" : "off"), 10,
                () -> powerManager.isDeviceIdleMode() == on);
    }

    // WIP This test doesn't work yet.
//    @Test
//    public void testSoftErrorRetriesFrequentApp() throws Exception {
//        runTest(() -> {
//            removeAllAccounts();
//
//            // Let the initial sync happen.
//            addAccountAndLetInitialSyncRun(ACCOUNT_1_A, APP1_AUTHORITY);
//
//            writeSyncConfig(2, 1, 2, 3);
//
//            clearSyncInvocations(APP1_PACKAGE);
//
//            AmUtils.setStandbyBucket(APP1_PACKAGE, UsageStatsManager.STANDBY_BUCKET_FREQUENT);
//
//            // Set soft error.
//            mRpc.invoke(APP1_PACKAGE, rb ->
//                    rb.setSetResult(SetResult.newBuilder().setResult(Result.SOFT_ERROR)));
//
//            Bundle b = makeBundle(
//                    "testSoftErrorRetriesFrequentApp", true,
//                    ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
//
//            ContentResolver.requestSync(ACCOUNT_1_A, APP1_AUTHORITY, b);
//
//            waitUntil("Should retry more than 3 times.", () -> {
//                final Response res = mRpc.invoke(APP1_PACKAGE,
//                        rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
//                final int calls =  res.getSyncInvocations().getSyncInvocationsCount();
//                Log.i(TAG, "NumSyncInvocations=" + calls);
//                return calls >= 4; // First sync + 3 retries == 4, so at least 4 times.
//            });
//
//            Thread.sleep(10_000);
//
//            // One more retry is okay because of how the job scheduler throttle jobs, but no further.
//            final Response res = mRpc.invoke(APP1_PACKAGE,
//                    rb -> rb.setGetSyncInvocations(GetSyncInvocations.newBuilder()));
//            final int calls =  res.getSyncInvocations().getSyncInvocationsCount();
//            assertTrue("# of syncs must be equal or less than 5, but was " + calls, calls <= 5);
//        });
//    }
}
