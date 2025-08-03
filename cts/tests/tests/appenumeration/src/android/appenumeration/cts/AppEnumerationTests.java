/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.appenumeration.cts;

import static android.Manifest.permission.SET_PREFERRED_APPLICATIONS;
import static android.appenumeration.cts.Constants.ACTION_APP_ENUMERATION_PREFERRED_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTION_BIND_SERVICE;
import static android.appenumeration.cts.Constants.ACTION_CAN_PACKAGE_QUERIES;
import static android.appenumeration.cts.Constants.ACTION_CAN_PACKAGE_QUERY;
import static android.appenumeration.cts.Constants.ACTION_CHECK_PACKAGE;
import static android.appenumeration.cts.Constants.ACTION_CHECK_SIGNATURES;
import static android.appenumeration.cts.Constants.ACTION_CHECK_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTION_CHECK_CONTENT_URI_PERMISSION_FULL;
import static android.appenumeration.cts.Constants.ACTION_GET_CONTENT_PROVIDER_MIME_TYPE;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_APPWIDGET_PROVIDERS;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_NAMES_FOR_UIDS;
import static android.appenumeration.cts.Constants.ACTION_GET_NAME_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGES_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGE_INFO;
import static android.appenumeration.cts.Constants.ACTION_GET_PREFERRED_ACTIVITIES;
import static android.appenumeration.cts.Constants.ACTION_GET_SHAREDLIBRARY_DEPENDENT_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GRANT_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTION_HAS_SIGNING_CERTIFICATE;
import static android.appenumeration.cts.Constants.ACTION_JUST_FINISH;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_PROVIDER;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_SERVICE;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_UNEXPORTED_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTION_PENDING_INTENT_GET_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTION_PENDING_INTENT_GET_CREATOR_PACKAGE;
import static android.appenumeration.cts.Constants.ACTION_QUERY_ACTIVITIES;
import static android.appenumeration.cts.Constants.ACTION_QUERY_PROVIDERS;
import static android.appenumeration.cts.Constants.ACTION_QUERY_RESOLVER;
import static android.appenumeration.cts.Constants.ACTION_QUERY_SERVICES;
import static android.appenumeration.cts.Constants.ACTION_REVOKE_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTION_SEND_RESULT;
import static android.appenumeration.cts.Constants.ACTION_SET_INSTALLER_PACKAGE_NAME;
import static android.appenumeration.cts.Constants.ACTION_START_DIRECTLY;
import static android.appenumeration.cts.Constants.ACTION_START_FOR_RESULT;
import static android.appenumeration.cts.Constants.ACTION_TAKE_PERSISTABLE_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_DUMMY_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_NOT_EXPORTED;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_PERMISSION_PROTECTED;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_TEST;
import static android.appenumeration.cts.Constants.AUTHORITY_SUFFIX;
import static android.appenumeration.cts.Constants.EXTRA_AUTHORITY;
import static android.appenumeration.cts.Constants.EXTRA_CERT;
import static android.appenumeration.cts.Constants.EXTRA_DATA;
import static android.appenumeration.cts.Constants.EXTRA_FLAGS;
import static android.appenumeration.cts.Constants.EXTRA_PENDING_INTENT;
import static android.appenumeration.cts.Constants.QUERIES_ACTIVITY_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PERM;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PROVIDER;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PROVIDER_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_Q;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERM_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERM_URI_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_URI_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SEES_INSTALLER;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SEES_INSTALLER_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SHARED_USER;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_USES_LIBRARY;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_USES_OPTIONAL_LIBRARY;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE_PROVIDER;
import static android.appenumeration.cts.Constants.QUERIES_PROVIDER_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_PROVIDER_AUTH;
import static android.appenumeration.cts.Constants.QUERIES_SERVICE_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_ACTIVITY_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_PROVIDER_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_PROVIDER_AUTH;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_SERVICE_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_BROWSABLE;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_BROWSER;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_CONTACTS;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_EDITOR;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_SHARE;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_WEB;
import static android.appenumeration.cts.Constants.SERVICE_CLASS_SELF_VISIBILITY_SERVICE;
import static android.appenumeration.cts.Constants.SPLIT_BASE_APK;
import static android.appenumeration.cts.Constants.SPLIT_FEATURE_APK;
import static android.appenumeration.cts.Constants.SPLIT_PKG;
import static android.appenumeration.cts.Constants.TARGET_APPWIDGETPROVIDER;
import static android.appenumeration.cts.Constants.TARGET_APPWIDGETPROVIDER_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_BROWSER;
import static android.appenumeration.cts.Constants.TARGET_BROWSER_WILDCARD;
import static android.appenumeration.cts.Constants.TARGET_CONTACTS;
import static android.appenumeration.cts.Constants.TARGET_EDITOR;
import static android.appenumeration.cts.Constants.TARGET_FILTERS;
import static android.appenumeration.cts.Constants.TARGET_FILTERS_APK;
import static android.appenumeration.cts.Constants.TARGET_FORCEQUERYABLE;
import static android.appenumeration.cts.Constants.TARGET_FORCEQUERYABLE_NORMAL;
import static android.appenumeration.cts.Constants.TARGET_NO_API;
import static android.appenumeration.cts.Constants.TARGET_PREFERRED_ACTIVITY;
import static android.appenumeration.cts.Constants.TARGET_PREFIX_WILDCARD_WEB;
import static android.appenumeration.cts.Constants.TARGET_SHARE;
import static android.appenumeration.cts.Constants.TARGET_SHARED_LIBRARY_PACKAGE;
import static android.appenumeration.cts.Constants.TARGET_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER;
import static android.appenumeration.cts.Constants.TARGET_WEB;
import static android.appenumeration.cts.Constants.TEST_NONEXISTENT_PACKAGE_NAME_1;
import static android.appenumeration.cts.Constants.TEST_NONEXISTENT_PACKAGE_NAME_2;
import static android.appenumeration.cts.Constants.TEST_SHARED_LIB_NAME;
import static android.appenumeration.cts.Utils.Result;
import static android.appenumeration.cts.Utils.ThrowingBiFunction;
import static android.appenumeration.cts.Utils.allowTestApiAccess;
import static android.appenumeration.cts.Utils.clearAppDataForUser;
import static android.appenumeration.cts.Utils.ensurePackageIsInstalled;
import static android.appenumeration.cts.Utils.ensurePackageIsNotInstalled;
import static android.appenumeration.cts.Utils.forceStopPackage;
import static android.appenumeration.cts.Utils.installPackage;
import static android.appenumeration.cts.Utils.resetTestApiAccess;
import static android.appenumeration.cts.Utils.suspendPackages;
import static android.appenumeration.cts.Utils.uninstallPackage;
import static android.content.Intent.EXTRA_PACKAGES;
import static android.content.Intent.EXTRA_UID;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.SIGNATURE_MATCH;
import static android.content.pm.PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
import static android.os.Process.INVALID_UID;
import static android.os.Process.ROOT_UID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.LargeTest;

import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class AppEnumerationTests extends AppEnumerationTestsBase {

    private static final String SKIP_APP_FILTER_CACHE = "0";
    private static final String USE_APP_FILTER_CACHE = "1";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameterized.Parameter
    public String mUseAppFilterCache;

    @Parameterized.Parameters
    public static Iterable<Object> initParameters() {
        return Arrays.asList(SKIP_APP_FILTER_CACHE, USE_APP_FILTER_CACHE);
    }

    @Before
    public void onBefore() throws Exception {
        setSystemProperty("debug.pm.use_app_filter_cache", mUseAppFilterCache);
    }

    @After
    public void onAfter() throws Exception {
        setSystemProperty("debug.pm.use_app_filter_cache", "invalid");
    }

    @Test
    public void systemPackagesQueryable_notEnabled() throws Exception {
        final Resources resources = Resources.getSystem();
        assertFalse(
                "config_forceSystemPackagesQueryable must not be true.",
                resources.getBoolean(resources.getIdentifier(
                        "config_forceSystemPackagesQueryable", "bool", "android")));

        // now let's assert that the actual set of system apps is limited
        assertThat("Not all system apps should be visible.",
                getInstalledPackages(QUERIES_NOTHING_PERM, MATCH_SYSTEM_ONLY).length,
                greaterThan(getInstalledPackages(QUERIES_NOTHING, MATCH_SYSTEM_ONLY).length));
    }

    @Test
    public void all_canSeeForceQueryable() throws Exception {
        assertVisible(QUERIES_NOTHING, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PACKAGE, TARGET_FORCEQUERYABLE);
    }

    @Test
    public void all_cannotSeeForceQueryableInstalledNormally() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_ACTIVITY_ACTION, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_SERVICE_ACTION, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_PROVIDER_AUTH, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_PACKAGE, TARGET_FORCEQUERYABLE_NORMAL);
    }

    @Test
    public void startExplicitly_canStartNonVisible() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
        startExplicitIntentViaComponent(QUERIES_NOTHING, TARGET_FILTERS);
        startExplicitIntentViaPackageName(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void startExplicitly_cannotStartNonVisibleNonExported() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
        Assert.assertThrows("Start of non-exported activity should act as if app not installed",
                ActivityNotFoundException.class,
                () -> startExplicitIntentNotExportedViaComponent(
                        QUERIES_NOTHING, TARGET_FILTERS));
    }

    @Test
    public void startExplicitly_cannotStartVisibleNonExported() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
        Assert.assertThrows("Start of non-exported activity should fail with SecurityException",
                SecurityException.class,
                () -> startExplicitIntentNotExportedViaComponent(
                        QUERIES_ACTIVITY_ACTION, TARGET_FILTERS));
    }

    @Test
    public void startExplicitly_canStartVisible() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
        startExplicitIntentViaComponent(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
        startExplicitIntentViaPackageName(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void startExplicitly_activityPermissionProtected_canSeeTarget() {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        Assert.assertThrows(SecurityException.class,
                () -> startExplicitPermissionProtectedIntentViaComponent(
                        QUERIES_ACTIVITY_ACTION, TARGET_STUB));
        Assert.assertThrows(SecurityException.class,
                () -> startExplicitIntentViaPackageName(QUERIES_ACTIVITY_ACTION, TARGET_STUB));
    }

    @Test
    public void startExplicitly_activityPermissionProtected_cannotSeeTarget() {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        Assert.assertThrows(ActivityNotFoundException.class,
                () -> startExplicitPermissionProtectedIntentViaComponent(
                        QUERIES_NOTHING, TARGET_STUB));
        Assert.assertThrows(ActivityNotFoundException.class,
                () -> startExplicitIntentViaPackageName(QUERIES_NOTHING, TARGET_STUB));
    }

    @Test
    public void startImplicitly_canStartNonVisible() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
        startImplicitIntent(QUERIES_NOTHING);
    }

    @Test
    public void startActivityWithNoPermissionUri_canSeeProvider() throws Exception {
        uninstallPackage(QUERIES_NOTHING_RECEIVES_URI);
        installPackage(QUERIES_NOTHING_RECEIVES_URI_APK);

        assertNotVisible(QUERIES_NOTHING_RECEIVES_URI, QUERIES_NOTHING_PERM);

        // send with uri but no grant flags; shouldn't be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "/test")));
        assertNotVisible(QUERIES_NOTHING_RECEIVES_URI, QUERIES_NOTHING_PERM);

        // send again with uri bug grant flags now set; should be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "/test"))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_URI, QUERIES_NOTHING_PERM);
    }

    @Test
    public void startActivityWithUri_canSeePermissionProtectedProvider() throws Exception {
        uninstallPackage(QUERIES_NOTHING_RECEIVES_PERM_URI);
        installPackage(QUERIES_NOTHING_RECEIVES_PERM_URI_APK);

        assertNotVisible(QUERIES_NOTHING_RECEIVES_PERM_URI, QUERIES_NOTHING_PERM);

        // send with uri but no grant flags; shouldn't be visible
        // Setting a dummy type to mitigate test failure. Bug: b/273465805
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_PERM_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setDataAndType(
                                Uri.parse("content://" + QUERIES_NOTHING_PERM + "2/test"),
                                "null"));

        assertNotVisible(QUERIES_NOTHING_RECEIVES_PERM_URI, QUERIES_NOTHING_PERM);

        // send again with uri bug grant flags now set; should be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_PERM_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setDataAndType(
                                Uri.parse("content://" + QUERIES_NOTHING_PERM + "2/test"),
                                "null")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_PERM_URI, QUERIES_NOTHING_PERM);
    }

    @Test
    public void startActivityWithUriGrant_cannotSeeProviderAfterUpdated() throws Exception {
        assertNotVisible(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // send with uri grant flags; should be visible
        // Setting a dummy type to mitigate test failure. Bug: b/273465805
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM,
                QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setDataAndType(
                                Uri.parse("content://" + QUERIES_NOTHING_PERM + "3/test"),
                                "null")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // update the package; shouldn't be visible
        installPackage(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI_APK);
        // Wait until the updating is done
        AmUtils.waitForBroadcastBarrier();
        assertNotVisible(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI, QUERIES_NOTHING_PERM);
    }

    @Test
    public void startActivityWithPersistableUriGrant_canSeeProviderAfterUpdated() throws Exception {
        uninstallPackage(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI);
        installPackage(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI_APK);

        assertNotVisible(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // send with persistable uri grant flags; should be visible
        // Setting a dummy type to mitigate test failure. Bug: b/273465805
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM,
                QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI,
                new Intent(ACTION_TAKE_PERSISTABLE_URI_PERMISSION)
                        .setDataAndType(
                                Uri.parse("content://" + QUERIES_NOTHING_PERM + "3/test"),
                                "null")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // update the package; should be still visible
        installPackage(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI_APK);
        // Wait until the updating is done
        AmUtils.waitForBroadcastBarrier();
        assertVisible(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI, QUERIES_NOTHING_PERM);
    }

    private void startExplicitActivityWithIntent(
            String sourcePackageName, String targetPackageName, Intent intent) throws Exception {
        sendCommandBlocking(sourcePackageName, targetPackageName,
                intent.setClassName(targetPackageName, ACTIVITY_CLASS_TEST),
                ACTION_START_DIRECTLY);
    }

    @Test
    public void queriesNothing_cannotSeeNonForceQueryable() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_NO_API);
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingTargetsQ_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_Q, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_Q, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_Q, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingHasPermission_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS);
    }

    @Test
    public void queriesNothing_cannotSeeFilters() throws Exception {
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                ACTION_MANIFEST_ACTIVITY, this::queryIntentActivities);
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                ACTION_MANIFEST_SERVICE, this::queryIntentServices);
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
    }

    @Test
    public void queriesActivityAction_canSeeFilters() throws Exception {
        assertQueryable(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                ACTION_MANIFEST_ACTIVITY, this::queryIntentActivities);
        assertQueryable(QUERIES_SERVICE_ACTION, TARGET_FILTERS,
                ACTION_MANIFEST_SERVICE, this::queryIntentServices);
        assertQueryable(QUERIES_PROVIDER_AUTH, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
        assertQueryable(QUERIES_PROVIDER_ACTION, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
    }

    @Test
    public void queriesNothingHasPermission_canSeeFilters() throws Exception {
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                ACTION_MANIFEST_ACTIVITY, this::queryIntentActivities);
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                ACTION_MANIFEST_SERVICE, this::queryIntentServices);
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
    }

    @Test
    public void queriesSomething_cannotSeeNoApi() throws Exception {
        assertNotVisible(QUERIES_ACTIVITY_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_SERVICE_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_PROVIDER_AUTH, TARGET_NO_API);
        assertNotVisible(QUERIES_PROVIDER_ACTION, TARGET_NO_API);
    }

    @Test
    public void queriesActivityAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesWildcardAction_canSeeTargets() throws Exception {
        assertVisible(QUERIES_WILDCARD_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PROVIDER_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesActivityAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_PROVIDER_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesPackage_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_NO_API);
    }

    @Test
    public void queriesNothing_canSeeInstaller() throws Exception {
        uninstallPackage(QUERIES_NOTHING_SEES_INSTALLER);
        installPackage(QUERIES_NOTHING_SEES_INSTALLER_APK, TARGET_NO_API);
        try {
            assertVisible(QUERIES_NOTHING_SEES_INSTALLER, TARGET_NO_API);
        } finally {
            uninstallPackage(QUERIES_NOTHING_SEES_INSTALLER);
        }
    }

    @Test
    public void whenStarted_canSeeCaller() throws Exception {
        uninstallPackage(QUERIES_NOTHING);
        installPackage(QUERIES_NOTHING_APK);

        // let's first make sure that the target cannot see the caller.
        assertNotVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
        // now let's start the target and make sure that it can see the caller as part of that call
        PackageInfo packageInfo = startForResult(QUERIES_NOTHING_PERM, QUERIES_NOTHING);
        assertThat(packageInfo, IsNull.notNullValue());
        assertThat(packageInfo.packageName, is(QUERIES_NOTHING_PERM));
        // and finally let's re-run the last check to make sure that the target can still see the
        // caller
        assertVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
    }

    @Test
    public void whenStartedViaIntentSender_canSeeCaller() throws Exception {
        uninstallPackage(QUERIES_NOTHING);
        installPackage(QUERIES_NOTHING_APK);

        // let's first make sure that the target cannot see the caller.
        assertNotVisible(QUERIES_NOTHING, QUERIES_NOTHING_Q);
        // now let's start the target via pending intent and make sure that it can see the caller
        // as part of that call
        PackageInfo packageInfo = startSenderForResult(QUERIES_NOTHING_Q, QUERIES_NOTHING);
        assertThat(packageInfo, IsNull.notNullValue());
        assertThat(packageInfo.packageName, is(QUERIES_NOTHING_Q));
        // and finally let's re-run the last check to make sure that the target can still see the
        // caller
        assertVisible(QUERIES_NOTHING, QUERIES_NOTHING_Q);
    }

    @Test
    public void queriesNothing_cannotSeeLibraryPackage() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothingUsesLibrary_canSeeLibraryPackage() throws Exception {
        assertVisible(QUERIES_NOTHING_USES_LIBRARY, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothing_cannotSeeOptionalLibraryPackage() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothingUsesOptionalLibrary_canSeeLibraryPackage() throws Exception {
        assertVisible(QUERIES_NOTHING_USES_OPTIONAL_LIBRARY, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothing_getPackagesForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
        Assert.assertNull(getPackagesForUid(QUERIES_NOTHING, targetSharedUid));
        Assert.assertNull(getPackagesForUid(QUERIES_NOTHING, targetUid));
    }

    @Test
    public void queriesNothingHasPermission_getPackagesForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
        Assert.assertNotNull(getPackagesForUid(QUERIES_NOTHING_PERM, targetSharedUid));
        Assert.assertNotNull(getPackagesForUid(QUERIES_NOTHING_PERM, targetUid));
    }

    @Test
    public void queriesNothing_getNameForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
        Assert.assertNull(getNameForUid(QUERIES_NOTHING, targetSharedUid));
        Assert.assertNull(getNameForUid(QUERIES_NOTHING, targetUid));
    }

    @Test
    public void queriesNothingHasPermission_getNameForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
        Assert.assertNotNull(getNameForUid(QUERIES_NOTHING_PERM, targetSharedUid));
        Assert.assertNotNull(getNameForUid(QUERIES_NOTHING_PERM, targetUid));
    }

    @Test
    public void queriesNothing_getNamesForUids_consistentVisibility()
            throws Exception {
        try {
            allowTestApiAccess(QUERIES_NOTHING);

            final int targetSharedUid =
                    sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
            final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
            Assert.assertNull(getNamesForUids(QUERIES_NOTHING, targetSharedUid)[0]);
            Assert.assertNull(getNamesForUids(QUERIES_NOTHING, targetUid)[0]);
        } finally {
            resetTestApiAccess(QUERIES_NOTHING);
        }
    }

    @Test
    public void queriesNothingHasPermission_getNamesForUids_consistentVisibility()
            throws Exception {
        try {
            allowTestApiAccess(QUERIES_NOTHING_PERM);

            final int targetSharedUid =
                    sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
            final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
            Assert.assertNotNull(getNamesForUids(QUERIES_NOTHING_PERM, targetSharedUid)[0]);
            Assert.assertNotNull(getNamesForUids(QUERIES_NOTHING_PERM, targetUid)[0]);
        } finally {
            resetTestApiAccess(QUERIES_NOTHING_PERM);
        }
    }

    @Test
    public void queriesNothing_checkSignatures_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
        Assert.assertEquals(SIGNATURE_UNKNOWN_PACKAGE,
                checkSignatures(QUERIES_NOTHING, targetSharedUid));
        Assert.assertEquals(SIGNATURE_UNKNOWN_PACKAGE,
                checkSignatures(QUERIES_NOTHING, targetUid));
    }

    @Test
    public void queriesNothingHasPermission_checkSignatures_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, PackageInfoFlags.of(0));
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0));
        Assert.assertEquals(SIGNATURE_MATCH,
                checkSignatures(QUERIES_NOTHING_PERM, targetSharedUid));
        Assert.assertEquals(SIGNATURE_MATCH, checkSignatures(QUERIES_NOTHING_PERM, targetUid));
    }

    @Test
    public void queriesNothing_hasSigningCertificate_consistentVisibility() throws Exception {
        final PackageInfo targetSharedUidInfo = sPm.getPackageInfo(TARGET_SHARED_USER,
                PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final PackageInfo targetUidInfo = sPm.getPackageInfo(TARGET_FILTERS,
                PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final byte[] targetSharedCert = convertSignaturesToCertificates(
                targetSharedUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();
        final byte[] targetCert = convertSignaturesToCertificates(
                targetUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();

        Assert.assertFalse(
                hasSigningCertificate(QUERIES_NOTHING, targetSharedUidInfo.applicationInfo.uid,
                        targetSharedCert));
        Assert.assertFalse(
                hasSigningCertificate(QUERIES_NOTHING, targetUidInfo.applicationInfo.uid,
                        targetCert));
    }

    @Test
    public void queriesNothingHasPermission_hasSigningCertificate_consistentVisibility()
            throws Exception {
        final PackageInfo targetSharedUidInfo = sPm.getPackageInfo(TARGET_SHARED_USER,
                PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final PackageInfo targetUidInfo = sPm.getPackageInfo(TARGET_FILTERS,
                PackageInfoFlags.of(GET_SIGNING_CERTIFICATES));
        final byte[] targetSharedCert = convertSignaturesToCertificates(
                targetSharedUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();
        final byte[] targetCert = convertSignaturesToCertificates(
                targetUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();

        Assert.assertTrue(
                hasSigningCertificate(QUERIES_NOTHING_PERM, targetSharedUidInfo.applicationInfo.uid,
                        targetSharedCert));
        Assert.assertTrue(
                hasSigningCertificate(QUERIES_NOTHING_PERM, targetUidInfo.applicationInfo.uid,
                        targetCert));
    }

    @Test
    public void sharedUserMember_canSeeOtherMember() throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SHARED_USER);
    }

    @Test
    public void queriesPackage_canSeeAllSharedUserMembers() throws Exception {
        // explicitly queries target via manifest
        assertVisible(QUERIES_PACKAGE, TARGET_SHARED_USER);
        // implicitly granted visibility to other member of shared user
        assertVisible(QUERIES_PACKAGE, QUERIES_NOTHING_SHARED_USER);
    }

    @Test
    public void queriesWildcardContacts() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_CONTACTS);
        assertVisible(QUERIES_WILDCARD_CONTACTS, TARGET_CONTACTS);
    }

    @Test
    public void queriesWildcardWeb() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_WEB);
        assertVisible(QUERIES_WILDCARD_BROWSABLE, TARGET_WEB);
        assertVisible(QUERIES_WILDCARD_WEB, TARGET_WEB);
    }

    @Test
    public void queriesWildcardBrowser() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_BROWSER);
        assertNotVisible(QUERIES_WILDCARD_BROWSER, TARGET_WEB);
        assertVisible(QUERIES_WILDCARD_BROWSER, TARGET_BROWSER);
        assertVisible(QUERIES_WILDCARD_BROWSER, TARGET_BROWSER_WILDCARD);
    }

    @Test
    public void queriesWildcardWeb_canSeePrefixWildcardWeb() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_PREFIX_WILDCARD_WEB);
        assertVisible(QUERIES_WILDCARD_BROWSABLE, TARGET_PREFIX_WILDCARD_WEB);
        assertVisible(QUERIES_WILDCARD_WEB, TARGET_PREFIX_WILDCARD_WEB);
    }

    @Test
    public void queriesWildcardBrowser_cannotSeePrefixWildcardWeb() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_PREFIX_WILDCARD_WEB);
        assertNotVisible(QUERIES_WILDCARD_BROWSER, TARGET_PREFIX_WILDCARD_WEB);
    }

    @Test
    public void queriesWildcardEditor() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_EDITOR);
        assertVisible(QUERIES_WILDCARD_EDITOR, TARGET_EDITOR);
    }

    @Test
    public void queriesWildcardShareSheet() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARE);
        assertVisible(QUERIES_WILDCARD_SHARE, TARGET_SHARE);
    }

    @Test
    public void queriesNothing_cannotSeeA11yService() throws Exception {
        try {
            allowTestApiAccess(QUERIES_NOTHING);

            assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS,
                    this::getInstalledAccessibilityServices);
        } finally {
            resetTestApiAccess(QUERIES_NOTHING);
        }
    }

    @Test
    public void queriesNothingHasPermission_canSeeA11yService() throws Exception {
        try {
            allowTestApiAccess(QUERIES_NOTHING_PERM);

            assertVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                    this::getInstalledAccessibilityServices);
        } finally {
            resetTestApiAccess(QUERIES_NOTHING_PERM);
        }
    }

    @LargeTest
    @Test
    public void broadcastAdded_notVisibleDoesNotReceive() throws Exception {
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_ADDED, /* waitForReady */ true);
        installPackage(TARGET_FILTERS_APK);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void broadcastAdded_visibleReceives() throws Exception {
        final Result result = sendCommand(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_ADDED, /* waitForReady */ true);
        installPackage(TARGET_FILTERS_APK);
        try {
            Assert.assertEquals(TARGET_FILTERS,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail(e.getMessage());
        }
    }

    @LargeTest
    @Test
    public void reinstallTarget_broadcastRemoved_notVisibleDoesNotReceive() throws Exception {
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        installPackage(TARGET_FILTERS_APK);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void reinstallTarget_broadcastRemoved_visibleReceives() throws Exception {
        final Result result = sendCommand(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        installPackage(TARGET_FILTERS_APK);
        try {
            Assert.assertEquals(TARGET_FILTERS,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail(e.getMessage());
        }
    }

    @LargeTest
    @Test
    public void uninstallTarget_broadcastRemoved_notVisibleDoesNotReceive() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        uninstallPackage(TARGET_STUB);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void uninstallTarget_broadcastRemoved_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING_PERM, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        uninstallPackage(TARGET_STUB);
        try {
            Assert.assertEquals(TARGET_STUB,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @LargeTest
    @Test
    public void uninstallTarget_broadcastFullyRemoved_notVisibleDoesNotReceive() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_FULLY_REMOVED, /* waitForReady */ true);
        uninstallPackage(TARGET_STUB);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void uninstallTarget_broadcastFullyRemoved_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING_PERM, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_FULLY_REMOVED, /* waitForReady */ true);
        uninstallPackage(TARGET_STUB);
        try {
            Assert.assertEquals(TARGET_STUB,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @LargeTest
    @Test
    public void clearTargetData_broadcastDataCleared_notVisibleDoesNotReceive() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_DATA_CLEARED, /* waitForReady */ true);
        clearAppDataForUser(TARGET_STUB, UserReference.of(sContext.getUser()));
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void clearTargetData_broadcastDataCleared_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING_PERM, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_DATA_CLEARED, /* waitForReady */ true);
        clearAppDataForUser(TARGET_STUB, UserReference.of(sContext.getUser()));
        try {
            Assert.assertEquals(TARGET_STUB,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @Test
    public void broadcastRestarted_visibleReceives() throws Exception {
        assertBroadcastRestartedVisible(QUERIES_PACKAGE, TARGET_NO_API, TARGET_NO_API);
    }

    @LargeTest
    @Test
    public void broadcastRestarted_notVisibleDoesNotReceive() throws Exception {
        assertBroadcastRestartedVisible(QUERIES_NOTHING, /* expectedPackage */ null, TARGET_NO_API);
    }

    @Test
    public void broadcastSuspended_visibleReceives() throws Exception {
        assertBroadcastSuspendedVisible(QUERIES_PACKAGE,
                Arrays.asList(TARGET_NO_API, TARGET_SYNCADAPTER),
                Arrays.asList(TARGET_NO_API, TARGET_SYNCADAPTER));
    }

    @LargeTest
    @Test
    public void broadcastSuspended_notVisibleDoesNotReceive() throws Exception {
        assertBroadcastSuspendedVisible(QUERIES_NOTHING,
                Arrays.asList(),
                Arrays.asList(TARGET_NO_API, TARGET_SYNCADAPTER));
    }

    @LargeTest
    @Test
    public void broadcastSuspended_visibleReceivesAndNotVisibleDoesNotReceive() throws Exception {
        assertBroadcastSuspendedVisible(QUERIES_ACTIVITY_ACTION,
                Arrays.asList(TARGET_FILTERS),
                Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
    }

    @Test
    public void queriesResolver_grantsVisibilityToProvider() throws Exception {
        uninstallPackage(QUERIES_NOTHING_PROVIDER);
        installPackage(QUERIES_NOTHING_PROVIDER_APK);

        assertNotVisible(QUERIES_NOTHING_PROVIDER, QUERIES_NOTHING_PERM);

        String[] result = sendCommandBlocking(
                QUERIES_NOTHING_PERM, QUERIES_NOTHING_PROVIDER, null, ACTION_QUERY_RESOLVER)
                .getStringArray(Intent.EXTRA_RETURN_RESULT);
        Arrays.sort(result);
        assertThat(QUERIES_NOTHING_PERM + " not visible to " + QUERIES_NOTHING_PROVIDER
                        + " during resolver interaction",
                Arrays.binarySearch(result, QUERIES_NOTHING_PERM),
                greaterThanOrEqualTo(0));

        assertVisible(QUERIES_NOTHING_PROVIDER, QUERIES_NOTHING_PERM);
    }

    @Test
    public void bindService_consistentVisibility() throws Exception {
        // Ensure package visibility isn't impacted by optimization or cached result.
        // Target service shouldn't be visible to app without query permission even if
        // another app with query permission is binding it.
        assertServiceVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS);
        assertServiceNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesPackage_canSeeAppWidgetProviderTarget() throws Exception {
        assumeTrue(sPm.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS));

        assertVisible(QUERIES_PACKAGE, TARGET_APPWIDGETPROVIDER,
                this::getInstalledAppWidgetProviders);
    }

    @Test
    public void queriesNothing_cannotSeeAppWidgetProviderTarget() throws Exception {
        assumeTrue(sPm.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS));

        assertNotVisible(QUERIES_NOTHING, TARGET_APPWIDGETPROVIDER,
                this::getInstalledAppWidgetProviders);
        assertNotVisible(QUERIES_NOTHING, TARGET_APPWIDGETPROVIDER_SHARED_USER,
                this::getInstalledAppWidgetProviders);
    }

    @Test
    public void queriesNothingSharedUser_canSeeAppWidgetProviderSharedUserTarget()
            throws Exception {
        assumeTrue(sPm.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS));

        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_APPWIDGETPROVIDER_SHARED_USER,
                this::getInstalledAppWidgetProviders);
    }

    @Test
    public void queriesNothing_cannotSeeSharedLibraryDependentPackages() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_NO_API, this::getSharedLibraryDependentPackages);
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS, this::getSharedLibraryDependentPackages);
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARED_USER,
                this::getSharedLibraryDependentPackages);

    }

    @Test
    public void queriesPackage_canSeeSharedLibraryDependentPackages() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_NO_API, this::getSharedLibraryDependentPackages);
        assertVisible(QUERIES_PACKAGE, TARGET_SHARED_USER, this::getSharedLibraryDependentPackages);
    }

    @Test
    public void queriesNothingSharedUser_canSeeSharedUserInSharedLibraryDependentPackages()
            throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SHARED_USER,
                this::getSharedLibraryDependentPackages);
    }

    @Test
    public void queriesNothing_cannotSeePreferredActivityTarget() throws Exception {
        addPreferredActivity();
        try {
            assertNotVisible(QUERIES_NOTHING, TARGET_PREFERRED_ACTIVITY,
                    this::getPreferredActivities);
        } finally {
            clearPreferredActivity();
        }
    }

    @Test
    public void queriesPackage_canSeePreferredActivityTarget() throws Exception {
        addPreferredActivity();
        try {
            assertVisible(QUERIES_PACKAGE, TARGET_PREFERRED_ACTIVITY,
                    this::getPreferredActivities);
        } finally {
            clearPreferredActivity();
        }
    }

    @Test
    public void queriesNothing_setInstallerPackageName_targetIsNoApi_throwsException() {
        final Exception ex = assertThrows(IllegalArgumentException.class,
                () -> setInstallerPackageName(QUERIES_NOTHING, TARGET_NO_API, QUERIES_NOTHING));
        assertThat(ex.getMessage(), containsString(TARGET_NO_API));
    }

    @Test
    public void queriesNothing_setInstallerPackageName_installerIsNoApi_throwsException() {
        final Exception ex = assertThrows(IllegalArgumentException.class,
                () -> setInstallerPackageName(QUERIES_NOTHING, QUERIES_NOTHING, TARGET_NO_API));
        assertThat(ex.getMessage(), containsString(TARGET_NO_API));
    }

    @Test
    public void queriesPackageHasProvider_checkUriPermission_canSeeNoApi() throws Exception {
        final int permissionResult = checkUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_NO_API);
        assertThat(permissionResult, is(PackageManager.PERMISSION_GRANTED));
    }

    @Test
    public void queriesPackageHasProvider_checkUriPermission_cannotSeeFilters() throws Exception {
        final int permissionResult = checkUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_FILTERS);
        assertThat(permissionResult, is(PackageManager.PERMISSION_DENIED));
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void queriesPackageHasProvider_checkContentUriPermissionFull_canSeeNoApi()
            throws Exception {
        final int permissionResult = checkContentUriPermissionFull(QUERIES_PACKAGE_PROVIDER,
                TARGET_NO_API);
        assertThat(permissionResult, is(PackageManager.PERMISSION_GRANTED));
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void queriesPackageHasProvider_checkContentUriPermissionFull_cannotSeeFilters()
            throws Exception {
        final int permissionResult = checkContentUriPermissionFull(QUERIES_PACKAGE_PROVIDER,
                TARGET_FILTERS);
        assertThat(permissionResult, is(PackageManager.PERMISSION_DENIED));
    }

    @Test
    public void queriesPackageHasProvider_grantUriPermission_canSeeNoApi() throws Exception {
        try {
            grantUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_NO_API);
            assertThat(sContext.checkUriPermission(
                            Uri.parse("content://" + QUERIES_PACKAGE_PROVIDER),
                            0 /* pid */,
                            sPm.getPackageUid(TARGET_NO_API, PackageInfoFlags.of(0)),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    is(PackageManager.PERMISSION_GRANTED));
        } finally {
            revokeUriPermission(QUERIES_PACKAGE_PROVIDER);
        }
    }

    @Test
    public void queriesPackageHasProvider_grantUriPermission_cannotSeeFilters() throws Exception {
        try {
            grantUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_FILTERS);
            assertThat(sContext.checkUriPermission(
                                    Uri.parse("content://" + QUERIES_PACKAGE_PROVIDER),
                                    0 /* pid */,
                                    sPm.getPackageUid(TARGET_FILTERS, PackageInfoFlags.of(0)),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    is(PackageManager.PERMISSION_DENIED));
        } finally {
            revokeUriPermission(QUERIES_PACKAGE_PROVIDER);
        }
    }

    @Test
    public void queriesPackage_getType_canGetMimeType() throws Exception {
        assertThat(getContentProviderMimeType(QUERIES_PACKAGE, TARGET_SYNCADAPTER),
                is("got/theMIME"));
    }

    @Test
    public void queriesNothing_getType_cannotGetMimeType() throws Exception {
        assertThat(getContentProviderMimeType(QUERIES_NOTHING, TARGET_SYNCADAPTER),
                IsNull.nullValue());
    }

    @Test
    public void setAutoRevokeWhitelisted_targetIsNotExisting_setFailed() throws Exception {
        final boolean result = SystemUtil.callWithShellPermissionIdentity(
                () -> sPm.setAutoRevokeWhitelisted(TEST_NONEXISTENT_PACKAGE_NAME_1,
                        false /* whitelisted */),
                Manifest.permission.WHITELIST_AUTO_REVOKE_PERMISSIONS);
        assertThat(result, is(false));
    }

    @Test
    public void setAutoRevokeWhitelisted_cannotSeeTarget_setFailed() throws Exception {
        final boolean result = SystemUtil.callWithShellPermissionIdentity(
                () -> sPm.setAutoRevokeWhitelisted(QUERIES_PACKAGE, false /* whitelisted */),
                Manifest.permission.WHITELIST_AUTO_REVOKE_PERMISSIONS);
        assertThat(result, is(false));
    }

    @Test
    public void setAutoRevokeWhitelisted_canSeeTarget_setSuccessful() throws Exception {
        final boolean result = SystemUtil.callWithShellPermissionIdentity(
                () -> sPm.setAutoRevokeWhitelisted(QUERIES_NOTHING, false /* whitelisted */),
                Manifest.permission.WHITELIST_AUTO_REVOKE_PERMISSIONS);
        assertThat(result, is(true));
    }

    @Test
    public void setAutoRevokeWhitelisted_withoutPermission_throwsException() throws Exception {
        assertThrows(SecurityException.class,
                () -> sPm.setAutoRevokeWhitelisted(QUERIES_NOTHING, false /* whitelisted */));
    }

    @Test
    public void canPackageQuery_queriesActivityAction_canSeeFilters() throws Exception {
        assertThat(sPm.canPackageQuery(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS),
                is(true));
    }

    @Test
    public void canPackageQuery_queriesNothing_cannotSeeFilters() throws Exception {
        assertThat(sPm.canPackageQuery(QUERIES_NOTHING, TARGET_FILTERS),
                is(false));
    }

    @Test
    public void canPackageQuery_withNonexistentPackages() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(
                        TEST_NONEXISTENT_PACKAGE_NAME_1, TEST_NONEXISTENT_PACKAGE_NAME_2));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(
                        QUERIES_NOTHING_PERM, TEST_NONEXISTENT_PACKAGE_NAME_2));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(
                        TEST_NONEXISTENT_PACKAGE_NAME_1, TARGET_FILTERS));
    }

    @Test
    public void canPackageQuery_callerHasNoPackageVisibility() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(
                        QUERIES_NOTHING, QUERIES_ACTIVITY_ACTION, TARGET_FILTERS));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(
                        QUERIES_NOTHING_SHARED_USER, QUERIES_PACKAGE, TARGET_SHARED_USER));
    }

    @Test
    public void canPackageQuery_cannotDetectPackageExistence() {
        ensurePackageIsNotInstalled(TARGET_STUB);
        final Exception ex1 = assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(QUERIES_NOTHING, TARGET_STUB, ""));
        final StringWriter stackTrace1 = new StringWriter();
        ex1.printStackTrace(new PrintWriter(stackTrace1));

        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);

        final Exception ex2 = assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(QUERIES_NOTHING, TARGET_STUB, ""));
        final StringWriter stackTrace2 = new StringWriter();
        ex1.printStackTrace(new PrintWriter(stackTrace2));

        assertThat(ex1.getMessage(), is(ex2.getMessage()));
        assertThat(stackTrace1.toString(), is(stackTrace2.toString()));
    }

    @Test
    public void canPackageQueries_queriesPackage_canSeeDeclaredPackages_cannotSeeOthers()
            throws Exception {
        assertThat(canPackageQueries(QUERIES_NOTHING_PERM, QUERIES_PACKAGE,
                        new String[]{
                                TARGET_NO_API,
                                TARGET_BROWSER,
                                TARGET_SHARED_USER,
                                TARGET_WEB,
                                TARGET_SYNCADAPTER}),
                allOf(arrayWithSize(5), arrayContaining(true, false, true, false, true)));
    }

    @Test
    public void canPackageQueries_callerHasNoPackageVisibility() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQueries(QUERIES_PACKAGE, TARGET_NO_API,
                        new String[]{TARGET_SHARED_USER, TARGET_WEB}));
    }

    @Test
    public void checkPackage_queriesNothing_validateFailed() {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThrows(SecurityException.class,
                () -> checkPackage(QUERIES_NOTHING, TARGET_FILTERS, ROOT_UID));
    }

    @Test
    public void checkPackage_queriesNothing_targetIsNotExisting_validateFailed() {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThrows(SecurityException.class,
                () -> checkPackage(QUERIES_NOTHING, TEST_NONEXISTENT_PACKAGE_NAME_1, ROOT_UID));
    }

    @Test
    public void checkPackage_queriesNothingHasPerm_validateSuccessful() throws Exception {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThat(checkPackage(QUERIES_NOTHING_PERM, TARGET_FILTERS, ROOT_UID), is(true));
    }

    @Test
    public void checkPackage_queriesNothingHasPerm_targetIsNotExisting_validateFailed()
            throws Exception {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThrows(SecurityException.class,
                () -> checkPackage(QUERIES_NOTHING_PERM, TEST_NONEXISTENT_PACKAGE_NAME_1,
                        ROOT_UID));
    }

    @Test
    public void pendingIntent_getCreatorPackage_queriesPackage_canSeeNoApi()
            throws Exception {
        final PendingIntent pendingIntent = getPendingIntentActivity(TARGET_NO_API);
        assertThat(getPendingIntentCreatorPackage(QUERIES_PACKAGE, pendingIntent),
                is(TARGET_NO_API));
    }

    @Test
    public void pendingIntent_getCreatorPackage_queriesNothing_cannotSeeNoApi()
            throws Exception {
        final PendingIntent pendingIntent = getPendingIntentActivity(TARGET_NO_API);
        assertThat(getPendingIntentCreatorPackage(QUERIES_NOTHING, pendingIntent),
                is(emptyOrNullString()));
    }

    @Test
    public void makeUidVisible_throwsException() throws Exception {
        final int recipientUid = sPm.getPackageUid(
                QUERIES_NOTHING, PackageManager.PackageInfoFlags.of(0));
        final int visibleUid = sPm.getPackageUid(
                TARGET_NO_API, PackageManager.PackageInfoFlags.of(0));
        assertThrows(SecurityException.class,
                () -> sPm.makeUidVisible(recipientUid, visibleUid));
    }

    @Test
    public void makeUidVisible_queriesNothing_canSeeStub() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        try {
            assertNotVisible(QUERIES_NOTHING, TARGET_STUB);

            final int recipientUid = sPm.getPackageUid(
                    QUERIES_NOTHING, PackageManager.PackageInfoFlags.of(0));
            final int visibleUid = sPm.getPackageUid(
                    TARGET_STUB, PackageManager.PackageInfoFlags.of(0));
            SystemUtil.runWithShellPermissionIdentity(
                    () -> sPm.makeUidVisible(recipientUid, visibleUid),
                            Manifest.permission.MAKE_UID_VISIBLE);

            assertVisible(QUERIES_NOTHING, TARGET_STUB);
        } finally {
            ensurePackageIsNotInstalled(TARGET_STUB);
        }
    }

    @Test
    public void testSelfVisibility() throws Exception {
        final ServiceInfo serviceInfo = sPm.getServiceInfo(
                new ComponentName(sContext, SERVICE_CLASS_SELF_VISIBILITY_SERVICE),
                PackageManager.GET_META_DATA);
        assertNotNull(serviceInfo);
    }

    @Test
    public void grantImplicitAccessByUriGrant_canSeeProviderAfterUpdatedWithDontKill()
            throws Exception {
        try {
            new InstallMultiple().addApk(SPLIT_BASE_APK).run();
            assertThat(sPm.canPackageQuery(SPLIT_PKG, QUERIES_PACKAGE_PROVIDER),
                    is(false));
            grantUriPermission(QUERIES_PACKAGE_PROVIDER, SPLIT_PKG);
            assertThat(sPm.canPackageQuery(SPLIT_PKG, QUERIES_PACKAGE_PROVIDER),
                    is(true));

            new InstallMultiple().addApk(SPLIT_FEATURE_APK)
                    .inheritFrom(SPLIT_PKG).dontKill().run();

            assertThat(sPm.canPackageQuery(SPLIT_PKG, QUERIES_PACKAGE_PROVIDER),
                    is(true));
        } finally {
            revokeUriPermission(QUERIES_PACKAGE_PROVIDER);
            uninstallPackage(SPLIT_PKG);
        }
    }

    private void assertVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        Assert.assertNotNull(sourcePackageName + " should be able to see " + targetPackageName,
                getPackageInfo(sourcePackageName, targetPackageName));
    }

    private void assertNotVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        try {
            getPackageInfo(sourcePackageName, targetPackageName);
            fail(sourcePackageName + " should not be able to see " + targetPackageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private void assertServiceVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        assertTrue(bindService(sourcePackageName, targetPackageName));
    }

    private void assertServiceNotVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        assertFalse(bindService(sourcePackageName, targetPackageName));
    }

    private void assertNotQueryable(String sourcePackageName, String targetPackageName,
            String intentAction, ThrowingBiFunction<String, Intent, String[]> commandMethod)
            throws Exception {
        Intent intent = new Intent(intentAction);
        String[] queryablePackageNames = commandMethod.apply(sourcePackageName, intent);
        for (String packageName : queryablePackageNames) {
            if (packageName.contentEquals(targetPackageName)) {
                fail(sourcePackageName + " should not be able to query " + targetPackageName +
                        " via " + intentAction);
            }
        }
    }

    private void assertQueryable(String sourcePackageName, String targetPackageName,
            String intentAction, ThrowingBiFunction<String, Intent, String[]> commandMethod)
            throws Exception {
        Intent intent = new Intent(intentAction);
        String[] queryablePackageNames = commandMethod.apply(sourcePackageName, intent);
        for (String packageName : queryablePackageNames) {
            if (packageName.contentEquals(targetPackageName)) {
                return;
            }
        }
        fail(sourcePackageName + " should be able to query " + targetPackageName + " via "
                + intentAction);
    }

    private void assertBroadcastRestartedVisible(String sourcePackageName,
            String expectedPackage, String packageToRestart) throws Exception {
        final Bundle extras = new Bundle();
        extras.putString(Intent.EXTRA_PACKAGE_NAME, packageToRestart);
        final Result result = sendCommand(sourcePackageName, /* targetPackageName */ null,
                /* targetUid */ INVALID_UID, extras, Constants.ACTION_AWAIT_PACKAGE_RESTARTED,
                /* waitForReady */ true);
        forceStopPackage(packageToRestart);
        final String restartedPackages = result.await().getString(Intent.EXTRA_PACKAGE_NAME,
                null /* defaultValue */);
        assertThat(restartedPackages, expectedPackage == null ? IsNull.nullValue()
                : equalTo(expectedPackage));
    }

    private void assertBroadcastSuspendedVisible(String sourcePackageName,
            List<String> expectedVisiblePackages, List<String> packagesToSuspend)
            throws Exception {
        final Bundle extras = new Bundle();
        extras.putStringArray(EXTRA_PACKAGES, packagesToSuspend.toArray(new String[] {}));
        final Result result = sendCommand(sourcePackageName, /* targetPackageName */ null,
                /* targetUid */ INVALID_UID, extras, Constants.ACTION_AWAIT_PACKAGES_SUSPENDED,
                /* waitForReady */ true);
        try {
            suspendPackages(true /* suspend */, packagesToSuspend);
            final String[] suspendedPackages = result.await().getStringArray(EXTRA_PACKAGES);
            assertThat(suspendedPackages, arrayContainingInAnyOrder(
                    expectedVisiblePackages.toArray()));
        } finally {
            suspendPackages(false /* suspend */, packagesToSuspend);
        }
    }

    private String[] getInstalledAccessibilityServices (String sourcePackageName)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, null /*targetPackageName*/,
                null /*queryIntent*/, ACTION_GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo getPackageInfo(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                null /*queryIntent*/, ACTION_GET_PACKAGE_INFO);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT, PackageInfo.class);
    }

    private String[] getPackagesForUid(String sourcePackageName, int targetUid)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_GET_PACKAGES_FOR_UID);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String getNameForUid(String sourcePackageName, int targetUid) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_GET_NAME_FOR_UID);
        return response.getString(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getNamesForUids(String sourcePackageName, int targetUid) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_GET_NAMES_FOR_UIDS);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private int checkSignatures(String sourcePackageName, int targetUid) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_CHECK_SIGNATURES);
        return response.getInt(Intent.EXTRA_RETURN_RESULT);
    }

    private boolean hasSigningCertificate(String sourcePackageName, int targetUid, byte[] cert)
            throws Exception {
        final Bundle extra = new Bundle();
        extra.putByteArray(EXTRA_CERT, cert);
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid, extra,
                ACTION_HAS_SIGNING_CERTIFICATE);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private List<Certificate> convertSignaturesToCertificates(Signature[] signatures)
            throws Exception {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ArrayList<Certificate> certs = new ArrayList<>(signatures.length);
        for (Signature signature : signatures) {
            final InputStream is = new ByteArrayInputStream(signature.toByteArray());
            final X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            certs.add(cert);
        }
        return certs;
    }

    private boolean checkPackage(String sourcePackageName, String targetPackageName, int targetUid)
            throws Exception {
        final Bundle extra = new Bundle();
        extra.putInt(EXTRA_UID, targetUid);
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName, extra,
                ACTION_CHECK_PACKAGE);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo startForResult(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                null /*queryIntent*/, ACTION_START_FOR_RESULT);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT, PackageInfo.class);
    }

    private PackageInfo startSenderForResult(String sourcePackageName, String targetPackageName)
            throws Exception {
        PendingIntent pendingIntent = PendingIntent.getActivity(sContext, 100 /* requestCode */,
                new Intent(ACTION_SEND_RESULT).setComponent(
                        new ComponentName(targetPackageName, ACTIVITY_CLASS_TEST)),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                pendingIntent /*queryIntent*/, Constants.ACTION_START_SENDER_FOR_RESULT);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT, PackageInfo.class);
    }

    private String[] queryIntentActivities(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response =
                sendCommandBlocking(sourcePackageName, null, queryIntent, ACTION_QUERY_ACTIVITIES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentServices(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, null, queryIntent,
                ACTION_QUERY_SERVICES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentProviders(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, null, queryIntent,
                ACTION_QUERY_PROVIDERS);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getInstalledPackages(String sourcePackageNames, int flags) throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageNames, null,
                new Intent().putExtra(EXTRA_FLAGS, flags), ACTION_GET_INSTALLED_PACKAGES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private void startExplicitIntentViaComponent(String sourcePackage, String targetPackage)
            throws Exception {
        sendCommandBlocking(sourcePackage, targetPackage,
                new Intent(ACTION_MANIFEST_ACTIVITY)
                        .setClassName(targetPackage, ACTIVITY_CLASS_DUMMY_ACTIVITY),
                ACTION_START_DIRECTLY);
    }
    private void startExplicitIntentNotExportedViaComponent(
            String sourcePackage, String targetPackage) throws Exception {
        sendCommandBlocking(sourcePackage, targetPackage,
                new Intent(ACTION_MANIFEST_UNEXPORTED_ACTIVITY)
                        .setClassName(targetPackage, ACTIVITY_CLASS_NOT_EXPORTED),
                ACTION_START_DIRECTLY);
    }
    private void startExplicitIntentViaPackageName(String sourcePackage, String targetPackage)
            throws Exception {
        sendCommandBlocking(sourcePackage, targetPackage,
                new Intent(ACTION_MANIFEST_ACTIVITY).setPackage(targetPackage),
                ACTION_START_DIRECTLY);
    }

    private void startExplicitPermissionProtectedIntentViaComponent(
            String sourcePackage, String targetPackage) throws Exception {
        sendCommandBlocking(sourcePackage, targetPackage,
                new Intent(ACTION_MANIFEST_ACTIVITY)
                        .setClassName(targetPackage, ACTIVITY_CLASS_PERMISSION_PROTECTED),
                ACTION_START_DIRECTLY);
    }

    private void startImplicitIntent(String sourcePackage) throws Exception {
        sendCommandBlocking(sourcePackage, TARGET_FILTERS, new Intent(ACTION_MANIFEST_ACTIVITY),
                ACTION_START_DIRECTLY);
    }

    private boolean bindService(String sourcePackageName, String targetPackageName)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                /* intentExtra */ null, ACTION_BIND_SERVICE);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getInstalledAppWidgetProviders(String sourcePackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                /* intentExtra */ null, ACTION_GET_INSTALLED_APPWIDGET_PROVIDERS);
        final List<AppWidgetProviderInfo> infos = response.getParcelableArrayList(
                Intent.EXTRA_RETURN_RESULT, AppWidgetProviderInfo.class);
        return infos.stream()
                .map(info -> info.provider.getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] getSharedLibraryDependentPackages(String sourcePackageName) throws Exception {
        final Bundle extraData = new Bundle();
        final Bundle response = sendCommandBlocking(sourcePackageName, TEST_SHARED_LIB_NAME,
                extraData, ACTION_GET_SHAREDLIBRARY_DEPENDENT_PACKAGES);
        return response.getStringArray(Intent.EXTRA_PACKAGES);
    }

    private String[] getPreferredActivities(String sourcePackageName) throws Exception {
        final Bundle extraData = new Bundle();
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_PREFERRED_ACTIVITIES);
        return response.getStringArray(Intent.EXTRA_PACKAGES);
    }

    private void setInstallerPackageName(String sourcePackageName, String targetPackageName,
            String installerPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(Intent.EXTRA_INSTALLER_PACKAGE_NAME, installerPackageName);
        sendCommandBlocking(sourcePackageName, targetPackageName,
                extraData, ACTION_SET_INSTALLER_PACKAGE_NAME);
    }

    private int checkUriPermission(String sourcePackageName, String targetPackageName)
            throws Exception {
        return internalCheckUriPermissionOrContentUriPermissionFull(sourcePackageName,
                targetPackageName, ACTION_CHECK_URI_PERMISSION);
    }

    private int checkContentUriPermissionFull(String sourcePackageName, String targetPackageName)
            throws Exception {
        return internalCheckUriPermissionOrContentUriPermissionFull(sourcePackageName,
                targetPackageName, ACTION_CHECK_CONTENT_URI_PERMISSION_FULL);
    }

    private int internalCheckUriPermissionOrContentUriPermissionFull(String sourcePackageName,
            String targetPackageName, String action) throws Exception {
        final int targetUid = sPm.getPackageUid(targetPackageName, PackageInfoFlags.of(0));
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, sourcePackageName);
        final Result result = sendCommand(sourcePackageName, targetPackageName, targetUid,
                extraData, action, /* waitForReady */ false);
        final Bundle response = result.await();
        return response.getInt(Intent.EXTRA_RETURN_RESULT);
    }

    private void grantUriPermission(String providerPackageName, String targetPackageName)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, providerPackageName);
        sendCommandBlocking(providerPackageName, targetPackageName, extraData,
                ACTION_GRANT_URI_PERMISSION);
    }

    private void revokeUriPermission(String providerPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, providerPackageName);
        sendCommandBlocking(providerPackageName, null /* targetPackageName */, extraData,
                ACTION_REVOKE_URI_PERMISSION);
    }

    private String getContentProviderMimeType(String sourcePackageName, String targetPackageName)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, targetPackageName + AUTHORITY_SUFFIX);
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                extraData, ACTION_GET_CONTENT_PROVIDER_MIME_TYPE);
        return response.getString(Intent.EXTRA_RETURN_RESULT);
    }

    private boolean canPackageQuery(String callerPackageName, String sourcePackageName,
            String targetPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        final Bundle response = sendCommandBlocking(callerPackageName, sourcePackageName,
                extraData, ACTION_CAN_PACKAGE_QUERY);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private Boolean[] canPackageQueries(String callerPackageName, String sourcePackageName,
            String[] targetPackageNames) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putStringArray(EXTRA_PACKAGES, targetPackageNames);
        final Bundle response = sendCommandBlocking(callerPackageName, sourcePackageName,
                extraData, ACTION_CAN_PACKAGE_QUERIES);
        return toBooleanArray(response.getBooleanArray(Intent.EXTRA_RETURN_RESULT));
    }

    private PendingIntent getPendingIntentActivity(String sourcePackageName) throws Exception  {
        final Bundle bundle = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                null /* intentExtra */, ACTION_PENDING_INTENT_GET_ACTIVITY);
        return bundle.getParcelable(EXTRA_PENDING_INTENT, PendingIntent.class);
    }

    private String getPendingIntentCreatorPackage(String sourcePackageName,
            PendingIntent pendingIntent) throws Exception  {
        final Bundle bundle = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                pendingIntent, ACTION_PENDING_INTENT_GET_CREATOR_PACKAGE);
        return bundle.getString(Intent.EXTRA_PACKAGE_NAME);
    }

    private void addPreferredActivity() {
        final IntentFilter filter = new IntentFilter(
                ACTION_APP_ENUMERATION_PREFERRED_ACTIVITY);
        final ComponentName[] candidates = {new ComponentName(TARGET_PREFERRED_ACTIVITY,
                ACTIVITY_CLASS_DUMMY_ACTIVITY)};
        SystemUtil.runWithShellPermissionIdentity(() -> {
            sPm.addPreferredActivity(filter, IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                    candidates, candidates[0]);
        }, SET_PREFERRED_APPLICATIONS);
    }

    private void clearPreferredActivity() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            sPm.clearPackagePreferredActivities(TARGET_PREFERRED_ACTIVITY);
        }, SET_PREFERRED_APPLICATIONS);
    }

    private void setSystemProperty(String name, String value) throws Exception {
        assertEquals("", SystemUtil.runShellCommand("setprop " + name + " " + value));
    }

    private static Boolean[] toBooleanArray(boolean[] from) {
        final Boolean[] copies = new Boolean[from.length];
        for (int i = 0; i < from.length; i++) {
            copies[i] = from[i];
        }
        return copies;
    }
}
