/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.normalapp;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.PatternMatcher;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.util.TestResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ClientTest {
    /** Action to start normal test activities */
    private static final String ACTION_START_NORMAL_ACTIVITY =
            "com.android.cts.ephemeraltest.START_NORMAL";
    /** Action to start normal, exposed test activities */
    private static final String ACTION_START_EXPOSED_ACTIVITY =
            "com.android.cts.ephemeraltest.START_EXPOSED";
    /** Action to start ephemeral test activities */
    private static final String ACTION_START_EPHEMERAL_ACTIVITY =
            "com.android.cts.ephemeraltest.START_EPHEMERAL";
    /** Action to start ephemeral test activities */
    private static final String ACTION_START_OTHER_EPHEMERAL_ACTIVITY =
            "com.android.cts.ephemeraltest.START_OTHER_EPHEMERAL";
    /** Action to query for test activities */
    private static final String ACTION_QUERY =
            "com.android.cts.ephemeraltest.QUERY";
    private static final String EXTRA_ACTIVITY_NAME =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_NAME";
    private static final String EXTRA_ACTIVITY_RESULT =
            "com.android.cts.ephemeraltest.EXTRA_ACTIVITY_RESULT";

    private static final String EPHEMERAL_1_PKG = "com.android.cts.ephemeralapp1";
    private static final String EPHEMERAL_2_PKG = "com.android.cts.ephemeralapp2";
    private static final String INSTALLED_INSTANT_APP_MIN_CACHE_PERIOD =
            "installed_instant_app_min_cache_period";

    private BroadcastReceiver mReceiver;
    private final SynchronousQueue<TestResult> mResultQueue = new SynchronousQueue<>();

    @Before
    public void setUp() throws Exception {
        final IntentFilter filter =
                new IntentFilter("com.android.cts.ephemeraltest.START_ACTIVITY");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        mReceiver = new ActivityBroadcastReceiver(mResultQueue);
        InstrumentationRegistry.getContext().registerReceiver(mReceiver, filter,
                Context.RECEIVER_VISIBLE_TO_INSTANT_APPS | Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getContext().unregisterReceiver(mReceiver);
    }

    @Test
    public void testQuery() throws Exception {
        // query activities without flags
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).activityInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(0).activityInfo.name,
                    is("com.android.cts.normalapp.ExposedActivity"));
            assertThat(resolveInfo.get(0).isInstantAppAvailable,
                    is(false));
            assertThat(resolveInfo.get(1).activityInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).activityInfo.name,
                    is("com.android.cts.normalapp.NormalActivity"));
            assertThat(resolveInfo.get(1).isInstantAppAvailable,
                    is(false));
        }

        // query activities asking for ephemeral apps [we should only get normal apps]
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final int MATCH_EPHEMERAL = 0x00800000;

            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentActivities(queryIntent, MATCH_EPHEMERAL);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).activityInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(0).activityInfo.name,
                    is("com.android.cts.normalapp.ExposedActivity"));
            assertThat(resolveInfo.get(0).isInstantAppAvailable,
                    is(false));
            assertThat(resolveInfo.get(1).activityInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).activityInfo.name,
                    is("com.android.cts.normalapp.NormalActivity"));
            assertThat(resolveInfo.get(1).isInstantAppAvailable,
                    is(false));
        }

        // query activities; directed package
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setPackage(EPHEMERAL_1_PKG);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
            assertThat(resolveInfo.size(), is(0));
        }

        // query activities; directed component
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setComponent(
                    new ComponentName(EPHEMERAL_1_PKG,
                            "com.android.cts.ephemeralapp1.EphemeralActivity"));
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
            assertThat(resolveInfo.size(), is(0));
        }

        // query services without flags
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentServices(queryIntent, 0 /*flags*/);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).serviceInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(0).serviceInfo.name,
                    is("com.android.cts.normalapp.ExposedService"));
            assertThat(resolveInfo.get(0).isInstantAppAvailable,
                    is(false));
            assertThat(resolveInfo.get(1).serviceInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).serviceInfo.name,
                    is("com.android.cts.normalapp.NormalService"));
            assertThat(resolveInfo.get(1).isInstantAppAvailable,
                    is(false));
        }

        // query services asking for ephemeral apps [we should only get normal apps]
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final int MATCH_EPHEMERAL = 0x00800000;

            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentServices(queryIntent, MATCH_EPHEMERAL);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).serviceInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(0).serviceInfo.name,
                    is("com.android.cts.normalapp.ExposedService"));
            assertThat(resolveInfo.get(0).isInstantAppAvailable,
                    is(false));
            assertThat(resolveInfo.get(1).serviceInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).serviceInfo.name,
                    is("com.android.cts.normalapp.NormalService"));
            assertThat(resolveInfo.get(1).isInstantAppAvailable,
                    is(false));
        }

        // query services; directed package
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setPackage(EPHEMERAL_1_PKG);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentServices(queryIntent, 0 /*flags*/);
            assertThat(resolveInfo.size(), is(0));
        }

        // query services; directed component
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setComponent(
                    new ComponentName(EPHEMERAL_1_PKG,
                            "com.android.cts.ephemeralapp1.EphemeralService"));
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentServices(queryIntent, 0 /*flags*/);
            assertThat(resolveInfo.size(), is(0));
        }

        // query content providers without flags
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry
                    .getContext().getPackageManager().queryIntentContentProviders(
                            queryIntent, 0 /*flags*/);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).providerInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(0).providerInfo.name,
                    is("com.android.cts.normalapp.ExposedProvider"));
            assertThat(resolveInfo.get(1).providerInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).providerInfo.name,
                    is("com.android.cts.normalapp.NormalProvider"));
            assertThat(resolveInfo.get(1).isInstantAppAvailable,
                    is(false));
        }

        // query content providers asking for ephemeral apps [we should only get normal apps]
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            final int MATCH_EPHEMERAL = 0x00800000;

            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentContentProviders(
                            queryIntent, MATCH_EPHEMERAL);
            if (resolveInfo == null || resolveInfo.size() == 0) {
                fail("didn't resolve any intents");
            }
            assertThat(resolveInfo.size(), is(2));
            assertThat(resolveInfo.get(0).providerInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(0).providerInfo.name,
                    is("com.android.cts.normalapp.ExposedProvider"));
            assertThat(resolveInfo.get(1).providerInfo.packageName,
                    is("com.android.cts.normalapp"));
            assertThat(resolveInfo.get(1).providerInfo.name,
                    is("com.android.cts.normalapp.NormalProvider"));
            assertThat(resolveInfo.get(1).isInstantAppAvailable,
                    is(false));
        }

        // query content providers; directed package
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setPackage(EPHEMERAL_1_PKG);
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentContentProviders(queryIntent, 0 /*flags*/);
            assertThat(resolveInfo.size(), is(0));
        }

        // query content providers; directed component
        {
            final Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setComponent(
                    new ComponentName(EPHEMERAL_1_PKG,
                            "com.android.cts.ephemeralapp1.EphemeralProvider"));
            final List<ResolveInfo> resolveInfo = InstrumentationRegistry.getContext()
                    .getPackageManager().queryIntentContentProviders(queryIntent, 0 /*flags*/);
            assertThat(resolveInfo.size(), is(0));
        }
    }

    @Test
    public void testStartNormal() throws Exception {
        // start the normal activity
        {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            InstrumentationRegistry.getContext().startActivity(startNormalIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("NormalActivity"));
            assertThat(testResult.getException(),
                    is("android.content.pm.PackageManager$NameNotFoundException"));
        }

        // start the normal activity; directed package
        {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startNormalIntent.setPackage("com.android.cts.normalapp");
            InstrumentationRegistry.getContext().startActivity(startNormalIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("NormalActivity"));
            assertThat(testResult.getException(),
                    is("android.content.pm.PackageManager$NameNotFoundException"));
        }

        // start the normal activity; directed component
        {
            final Intent startNormalIntent = new Intent(ACTION_START_NORMAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startNormalIntent.setComponent(new ComponentName(
                    "com.android.cts.normalapp", "com.android.cts.normalapp.NormalActivity"));
            InstrumentationRegistry.getContext().startActivity(startNormalIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(testResult.getPackageName(),
                    is("com.android.cts.normalapp"));
            assertThat(testResult.getComponentName(),
                    is("NormalActivity"));
            assertThat(testResult.getException(),
                    is("android.content.pm.PackageManager$NameNotFoundException"));
        }

        // connect to the normal provider
        {
            final String provider = "content://com.android.cts.normalapp.provider/table";
            final Cursor testCursor = InstrumentationRegistry
                    .getContext().getContentResolver().query(
                            Uri.parse(provider),
                            null /*projection*/,
                            null /*selection*/,
                            null /*selectionArgs*/,
                            null /*sortOrder*/);
            assertThat(testCursor, is(notNullValue()));
            assertThat(testCursor.getCount(), is(1));
            assertThat(testCursor.getColumnCount(), is(2));
            assertThat(testCursor.moveToFirst(), is(true));
            assertThat(testCursor.getInt(0), is(1));
            assertThat(testCursor.getString(1), is("NormalProvider"));
        }
    }

    @Test
    public void testStartEphemeral() throws Exception {
        // start the ephemeral activity; no EXTERNAL flag
        try {
            final Intent startEphemeralIntent = new Intent(ACTION_START_OTHER_EPHEMERAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            InstrumentationRegistry.getContext().startActivity(
                    startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the ephemeral activity; EXTERNAL flag
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_OTHER_EPHEMERAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MATCH_EXTERNAL);
            InstrumentationRegistry.getContext().startActivity(
                    startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(EPHEMERAL_2_PKG, is(testResult.getPackageName()));
            assertThat(ACTION_START_OTHER_EPHEMERAL_ACTIVITY, is(testResult.getIntent().getAction()));
        }


        // start the ephemeral activity; directed package, no EXTERNAL flag
        try {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startEphemeralIntent.setPackage(EPHEMERAL_1_PKG);
            InstrumentationRegistry.getContext().startActivity(
                    startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the ephemeral activity; directed package, includes EXTERNAL flag
        {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MATCH_EXTERNAL);
            startEphemeralIntent.setPackage(EPHEMERAL_1_PKG);
            InstrumentationRegistry.getContext().startActivity(
                    startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(EPHEMERAL_1_PKG, is(testResult.getPackageName()));
            assertThat(ACTION_START_EPHEMERAL_ACTIVITY, is(testResult.getIntent().getAction()));
        }

        // start the ephemeral activity; directed component
        try {
            final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startEphemeralIntent.setComponent(
                    new ComponentName(EPHEMERAL_1_PKG,
                            "com.android.cts.ephemeralapp1.EphemeralActivity"));
            InstrumentationRegistry.getContext().startActivity(
                    startEphemeralIntent, null /*options*/);
            final TestResult testResult = getResult();
            fail();
        } catch (ActivityNotFoundException expected) {
        }

        // start the ephemeral activity; using VIEW/BROWSABLE
        {
            final Intent startViewIntent = new Intent(Intent.ACTION_VIEW)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startViewIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            startViewIntent.setData(Uri.parse("https://cts.google.com/ephemeral"));
            InstrumentationRegistry.getContext().startActivity(
                    startViewIntent, null /*options*/);
            final TestResult testResult = getResult();
            assertThat(EPHEMERAL_1_PKG, is(testResult.getPackageName()));
            assertThat("EphemeralActivity", is(testResult.getComponentName()));
            assertThat(Intent.ACTION_VIEW, is(testResult.getIntent().getAction()));
            assertThat(testResult.getIntent().getCategories(), hasItems(Intent.CATEGORY_BROWSABLE));
            assertThat("https://cts.google.com/ephemeral",
                    is(testResult.getIntent().getData().toString()));
        }

        final ContentResolver contentResolver =
                InstrumentationRegistry.getContext().getContentResolver();
	// TODO(b/120026065): Re-enable this when we fine a more reliable way to toggle the setting
	/*
        final int originalSetting = Secure.getInt(contentResolver, Secure.INSTANT_APPS_ENABLED, 1);
        Secure.putInt(contentResolver, Secure.INSTANT_APPS_ENABLED, 0);
        try {
            // start the ephemeral activity; using VIEW/BROWSABLE with setting disabled
            try {
                final Intent startViewIntent = new Intent(Intent.ACTION_VIEW)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startViewIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                startViewIntent.setData(Uri.parse("https://cts.google.com/ephemeral"));
                InstrumentationRegistry.getContext().startActivity(
                        startViewIntent, null);
                final TestResult testResult = getResult();
                fail();
            } catch (TestResultNotFoundException expected) {
                // we shouldn't resolve to our instant app
            }

            // start the ephemeral activity; EXTERNAL flag with setting disabled
            {
                final Intent startEphemeralIntent = new Intent(ACTION_START_EPHEMERAL_ACTIVITY)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_MATCH_EXTERNAL);
                InstrumentationRegistry.getContext().startActivity(
                        startEphemeralIntent, null);
                final TestResult testResult = getResult();
                assertThat("com.android.cts.ephemeralapp1", is(testResult.getPackageName()));
                assertThat(ACTION_START_EPHEMERAL_ACTIVITY, is(testResult.getIntent().getAction()));
            }

        } finally {
            Secure.putInt(contentResolver, Secure.INSTANT_APPS_ENABLED, originalSetting);
        }
	*/

        // connect to the instant app provider
        {
            final String provider = "content://com.android.cts.ephemeralapp1.provider/table";
            final Cursor testCursor = contentResolver.query(
                            Uri.parse(provider),
                            null /*projection*/,
                            null /*selection*/,
                            null /*selectionArgs*/,
                            null /*sortOrder*/);
            assertThat(testCursor, is(nullValue()));
        }
    }

    /** Tests getting changed packages for instant app. */
    @Test
    public void testGetChangedPackages() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        // Query changed packages without permission, and we should only get normal apps.
        final ChangedPackages changedPackages = pm.getChangedPackages(0);
        assertThat(changedPackages.getPackageNames()).doesNotContain(EPHEMERAL_1_PKG);

        // Query changed packages with permission, and we should be able to get ephemeral apps.
        runWithShellPermissionIdentity(() -> {
            final ChangedPackages changesInstantApp = pm.getChangedPackages(0);
            assertThat(changesInstantApp.getPackageNames()).contains(EPHEMERAL_1_PKG);
        }, Manifest.permission.ACCESS_INSTANT_APPS);
    }

    @Test
    public void uninstall_userInstalledApp_shouldBeUserInitiated() {
        runWithShellPermissionIdentity(() -> {
            final boolean userInitiated = uninstallAndWaitForExtraUserInitiated(
                    InstrumentationRegistry.getContext(), EPHEMERAL_1_PKG);

            assertThat(userInitiated).isTrue();
        }, Manifest.permission.DELETE_PACKAGES, Manifest.permission.ACCESS_INSTANT_APPS);
    }

    @Test
    public void uninstall_pruneInstantApp_shouldNotBeUserInitiated() {
        runWithShellPermissionIdentity(() -> {
            final boolean userInitiated = pruneInstantAppAndWaitForExtraUserInitiated(
                    InstrumentationRegistry.getContext(), EPHEMERAL_1_PKG);

            assertThat(userInitiated).isFalse();
        }, Manifest.permission.WRITE_SECURE_SETTINGS, Manifest.permission.ACCESS_INSTANT_APPS);
    }

    /**
     * Uninstall the package and wait for the package removed intent.
     *
     * @return The value of {@link Intent#EXTRA_USER_INITIATED} associated with the intent.
     */
    private boolean uninstallAndWaitForExtraUserInitiated(Context context, String packageName) {
        final Runnable uninstall = () -> {
            final PackageInstaller packageInstaller = context.getPackageManager()
                    .getPackageInstaller();
            packageInstaller.uninstall(packageName, null);
        };

        final Intent packageRemoved = executeAndWaitForPackageRemoved(
                context, packageName, uninstall);
        return packageRemoved.getBooleanExtra(Intent.EXTRA_USER_INITIATED, false);
    }

    /**
     * Runs the shell command {@code pm trim-caches} to invoke system to prune instant applications.
     * Waits for the package removed intent and returns the extra filed.
     *
     * @return The value of {@link Intent#EXTRA_USER_INITIATED} associated with the intent.
     */
    private boolean pruneInstantAppAndWaitForExtraUserInitiated(Context context,
            String packageName) {
        final String defaultPeriod = Settings.Global.getString(context.getContentResolver(),
                INSTALLED_INSTANT_APP_MIN_CACHE_PERIOD);
        final Runnable trimCaches = () -> {
            // Updates installed instant app minimum cache period to zero to ensure that system
            // could uninstall instant apps when trim-caches is invoked.
            Settings.Global.putInt(context.getContentResolver(),
                    INSTALLED_INSTANT_APP_MIN_CACHE_PERIOD, 0);
            SystemUtil.runShellCommand("pm trim-caches " + Long.MAX_VALUE + " internal");
        };

        try {
            final Intent packageRemoved = executeAndWaitForPackageRemoved(
                    context, packageName, trimCaches);
            return packageRemoved.getBooleanExtra(Intent.EXTRA_USER_INITIATED, false);
        } finally {
            Settings.Global.putString(context.getContentResolver(),
                    INSTALLED_INSTANT_APP_MIN_CACHE_PERIOD, defaultPeriod);
        }
    }

    /**
     * Executes a command and waits for the package removed intent.
     *
     * @return The {@link Intent#ACTION_PACKAGE_REMOVED} associated with the given package name.
     */
    private Intent executeAndWaitForPackageRemoved(Context context, String packageName,
            Runnable command) {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL);
        final BlockingQueue<Intent> intentQueue = new LinkedBlockingQueue<>();
        final BroadcastReceiver removedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    intentQueue.put(intent);
                } catch (InterruptedException e) {
                    fail("Cannot add intent to intent blocking queue!");
                }
            }
        };
        context.registerReceiver(removedReceiver, filter);
        try {
            command.run();
            final Intent intent = intentQueue.poll(60 /* timeout */, TimeUnit.SECONDS);
            assertNotNull("Timed out to wait for package removed intent", intent);
            return intent;
        } catch (InterruptedException e) {
            fail("Failed to get package removed intent: " + e.getMessage());
        } finally {
            context.unregisterReceiver(removedReceiver);
        }
        return null;
    }

    private TestResult getResult() {
        final TestResult result;
        try {
            result = mResultQueue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (result == null) {
            throw new TestResultNotFoundException(
                    "Activity didn't receive a Result in 5 seconds");
        }
        return result;
    }

    private static class TestResultNotFoundException extends IllegalStateException {
        public TestResultNotFoundException(String description) {
            super(description);
        }
    }

    private static class ActivityBroadcastReceiver extends BroadcastReceiver {
        private final SynchronousQueue<TestResult> mQueue;
        public ActivityBroadcastReceiver(SynchronousQueue<TestResult> queue) {
            mQueue = queue;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                mQueue.offer(intent.getParcelableExtra(TestResult.EXTRA_TEST_RESULT),
                        5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
