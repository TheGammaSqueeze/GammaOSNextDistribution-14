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

package com.android.cts.splitapp;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertNotSame;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Environment;
import android.system.Os;
import android.system.StructStat;
import android.test.MoreAsserts;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.splitapp.TestThemeHelper.ThemeColors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class SplitAppTest {
    private static final String TAG = "SplitAppTest";
    private static final String PKG = "com.android.cts.splitapp";
    private static final String NORESTART_PKG = "com.android.cts.norestart";

    private static final long MB_IN_BYTES = 1 * 1024 * 1024;

    public static boolean sFeatureTouched = false;
    public static String sFeatureValue = null;

    private static final String BASE_THEME_ACTIVITY = ".ThemeActivity";
    private static final String WARM_THEME_ACTIVITY = ".WarmThemeActivity";
    private static final String ROSE_THEME_ACTIVITY = ".RoseThemeActivity";

    private static final ComponentName FEATURE_WARM_EMPTY_PROVIDER_NAME =
            ComponentName.createRelative(PKG, ".feature.warm.EmptyProvider");
    private static final ComponentName FEATURE_WARM_EMPTY_SERVICE_NAME =
            ComponentName.createRelative(PKG, ".feature.warm.EmptyService");

    private static final Uri INSTANT_APP_NORESTART_URI = Uri.parse(
            "https://cts.android.com/norestart");

    @Rule
    public ActivityTestRule<Activity> mActivityRule =
            new ActivityTestRule<>(Activity.class, true /*initialTouchMode*/,
                    false /*launchActivity*/);

    @Before
    public void setUp() {
        setAppLinksUserSelection(NORESTART_PKG, INSTANT_APP_NORESTART_URI.getHost(),
                true /*enabled*/);
    }

    @After
    public void tearDown() {
        setAppLinksUserSelection(NORESTART_PKG, INSTANT_APP_NORESTART_URI.getHost(),
                false /*enabled*/);
    }

    @Test
    public void testNothing() throws Exception {
    }

    @Test
    public void testSingleBase() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Should have untouched resources from base
        assertEquals(false, r.getBoolean(R.bool.my_receiver_enabled));

        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        assertEquals(0xff00ff00, r.getColor(R.color.my_color));
        assertEquals(123, r.getInteger(R.integer.my_integer));

        assertEquals("base", getXmlTestValue(r.getXml(R.xml.my_activity_meta)));

        // We know about drawable IDs, but they're stripped from base
        try {
            r.getDrawable(R.drawable.image);
            fail("Unexpected drawable in base");
        } catch (Resources.NotFoundException expected) {
        }

        // Should have base assets
        assertAssetContents(r, "file1.txt", "FILE1");
        assertAssetContents(r, "dir/dirfile1.txt", "DIRFILE1");

        try {
            assertAssetContents(r, "file2.txt", null);
            fail("Unexpected asset file2");
        } catch (IOException expected) {
        }

        // Should only have base manifest items
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(PKG);

        List<ResolveInfo> result = pm.queryIntentActivities(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.MyActivity", result.get(0).activityInfo.name);

        // Activity with split name `feature_warm` cannot be found.
        intent = new Intent("com.android.cts.splitapp.intent.SPLIT_NAME_TEST");
        intent.setPackage(PKG);
        assertThat(pm.queryIntentActivities(intent, 0).stream().noneMatch(
                info -> info.activityInfo.name.equals(
                        "com.android.cts.splitapp.feature.warm.EmptyActivity"))).isTrue();

        // Receiver disabled by default in base
        intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);

        result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(0, result.size());

        // We shouldn't have any native code in base
        try {
            Native.add(2, 4);
            fail("Unexpected native code in base");
        } catch (UnsatisfiedLinkError expected) {
        }
    }

    @Test
    public void testDensitySingle() throws Exception {
        final Resources r = getContext().getResources();

        // We should still have base resources
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        // Now we know about drawables, but only mdpi
        final Drawable d = r.getDrawable(R.drawable.image);
        assertEquals(0xff7e00ff, getDrawableColor(d));
    }

    @Test
    public void testDensityAll() throws Exception {
        final Resources r = getContext().getResources();

        // We should still have base resources
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        // Pretend that we're at each density
        updateDpi(r, DisplayMetrics.DENSITY_MEDIUM);
        assertEquals(0xff7e00ff, getDrawableColor(r.getDrawable(R.drawable.image)));

        updateDpi(r, DisplayMetrics.DENSITY_HIGH);
        assertEquals(0xff00fcff, getDrawableColor(r.getDrawable(R.drawable.image)));

        updateDpi(r, DisplayMetrics.DENSITY_XHIGH);
        assertEquals(0xff80ff00, getDrawableColor(r.getDrawable(R.drawable.image)));

        updateDpi(r, DisplayMetrics.DENSITY_XXHIGH);
        assertEquals(0xffff0000, getDrawableColor(r.getDrawable(R.drawable.image)));
    }

    @Test
    public void testDensityBest1() throws Exception {
        final Resources r = getContext().getResources();

        // Pretend that we're really high density, but we only have mdpi installed
        updateDpi(r, DisplayMetrics.DENSITY_XXHIGH);
        assertEquals(0xff7e00ff, getDrawableColor(r.getDrawable(R.drawable.image)));
    }

    @Test
    public void testDensityBest2() throws Exception {
        final Resources r = getContext().getResources();

        // Pretend that we're really high density, and now we have better match
        updateDpi(r, DisplayMetrics.DENSITY_XXHIGH);
        assertEquals(0xffff0000, getDrawableColor(r.getDrawable(R.drawable.image)));
    }

    @Test
    public void testApi() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // We should have updated boolean, different from base
        assertEquals(true, r.getBoolean(R.bool.my_receiver_enabled));

        // Receiver should be enabled now
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);

        List<ResolveInfo> result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.MyReceiver", result.get(0).activityInfo.name);
    }

    @Test
    public void testLocale() throws Exception {
        final Resources r = getContext().getResources();

        updateLocale(r, Locale.ENGLISH);
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        updateLocale(r, Locale.GERMAN);
        assertEquals("blau", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        updateLocale(r, Locale.FRENCH);
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("pourpre", r.getString(R.string.my_string2));
    }

    @Test
    public void testNative() throws Exception {
        Log.d(TAG, "testNative() thinks it's using ABI " + Native.arch());

        // Make sure we can do the maths
        assertEquals(11642, Native.add(4933, 6709));
    }

    @Test
    public void testNativeRevision_sub_shouldImplementBadly() throws Exception {
        assertNotSame(1, Native.sub(0, -1));
    }

    @Test
    public void testNativeRevision_sub_shouldImplementWell() throws Exception {
        assertEquals(1, Native.sub(0, -1));
    }

    @Test
    public void testNative64Bit() throws Exception {
        Log.d(TAG, "The device supports 32Bit ABIs \""
                + Arrays.deepToString(Build.SUPPORTED_32_BIT_ABIS) + "\" and 64Bit ABIs \""
                + Arrays.deepToString(Build.SUPPORTED_64_BIT_ABIS) + "\"");

        assertThat(Native.getAbiBitness()).isEqualTo(64);
    }

    @Test
    public void testNative32Bit() throws Exception {
        Log.d(TAG, "The device supports 32Bit ABIs \""
                + Arrays.deepToString(Build.SUPPORTED_32_BIT_ABIS) + "\" and 64Bit ABIs \""
                + Arrays.deepToString(Build.SUPPORTED_64_BIT_ABIS) + "\"");

        assertThat(Native.getAbiBitness()).isEqualTo(32);
    }

    @Test
    public void testNative_getNumberADirectly_shouldBeSeven() throws Exception {
        assertThat(Native.getNumberADirectly()).isEqualTo(7);
    }

    @Test
    public void testNative_getNumberAViaProxy_shouldBeSeven() throws Exception {
        assertThat(Native.getNumberAViaProxy()).isEqualTo(7);
    }

    @Test
    public void testNative_getNumberBDirectly_shouldBeEleven() throws Exception {
        assertThat(Native.getNumberBDirectly()).isEqualTo(11);
    }

    @Test
    public void testNative_getNumberBViaProxy_shouldBeEleven() throws Exception {
        assertThat(Native.getNumberBViaProxy()).isEqualTo(11);
    }

    @Test
    public void testFeatureWarmBase() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Should have untouched resources from base
        assertEquals(false, r.getBoolean(R.bool.my_receiver_enabled));

        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        assertEquals(0xff00ff00, r.getColor(R.color.my_color));
        assertEquals(123, r.getInteger(R.integer.my_integer));

        assertEquals("base", getXmlTestValue(r.getXml(R.xml.my_activity_meta)));

        // And that we can access resources from feature
        assertEquals("red", r.getString(r.getIdentifier(
                "com.android.cts.splitapp.feature_warm:feature_string", "string", PKG)));
        assertEquals(123, r.getInteger(r.getIdentifier(
                "com.android.cts.splitapp.feature_warm:feature_integer", "integer", PKG)));

        final Class<?> featR = Class.forName("com.android.cts.splitapp.FeatureR");
        final int boolId = (int) featR.getDeclaredField("feature_receiver_enabled").get(null);
        final int intId = (int) featR.getDeclaredField("feature_integer").get(null);
        final int stringId = (int) featR.getDeclaredField("feature_string").get(null);
        assertEquals(true, r.getBoolean(boolId));
        assertEquals(123, r.getInteger(intId));
        assertEquals("red", r.getString(stringId));

        // Should have both base and feature assets
        assertAssetContents(r, "file1.txt", "FILE1");
        assertAssetContents(r, "file2.txt", "FILE2");
        assertAssetContents(r, "dir/dirfile1.txt", "DIRFILE1");
        assertAssetContents(r, "dir/dirfile2.txt", "DIRFILE2");

        // Should have both base and feature components
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(PKG);
        List<ResolveInfo> result = pm.queryIntentActivities(intent, 0);
        assertEquals(2, result.size());
        assertEquals("com.android.cts.splitapp.MyActivity", result.get(0).activityInfo.name);
        assertEquals("com.android.cts.splitapp.FeatureActivity", result.get(1).activityInfo.name);

        // Receiver only enabled in feature
        intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);
        result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.FeatureReceiver", result.get(0).activityInfo.name);

        // And we should have a service
        intent = new Intent("com.android.cts.splitapp.service");
        intent.setPackage(PKG);
        result = pm.queryIntentServices(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.FeatureService", result.get(0).serviceInfo.name);

        // And a provider too
        ProviderInfo info = pm.resolveContentProvider("com.android.cts.splitapp.provider", 0);
        assertEquals("com.android.cts.splitapp.FeatureProvider", info.name);

        // And assert that we spun up the provider in this process
        final Class<?> provider = Class.forName("com.android.cts.splitapp.FeatureProvider");
        final Field field = provider.getDeclaredField("sCreated");
        assertTrue("Expected provider to have been created", (boolean) field.get(null));
        assertTrue("Expected provider to have touched us", sFeatureTouched);
        assertEquals(r.getString(R.string.my_string1), sFeatureValue);

        // Finally ensure that we can execute some code from split
        final Class<?> logic = Class.forName("com.android.cts.splitapp.FeatureLogic");
        final Method method = logic.getDeclaredMethod("mult", new Class[] {
                Integer.TYPE, Integer.TYPE });
        assertEquals(72, (int) method.invoke(null, 12, 6));

        // Make sure we didn't get an extra flag from feature split
        assertTrue("Someone parsed application flag!",
                (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) == 0);

        // Make sure we have permission from base APK
        getContext().enforceCallingOrSelfPermission(android.Manifest.permission.CAMERA, null);

        try {
            // But no new permissions from the feature APK
            getContext().enforceCallingOrSelfPermission(android.Manifest.permission.INTERNET, null);
            fail("Whaaa, we somehow gained permission from feature?");
        } catch (SecurityException expected) {
        }

        // Assert that activity declared in the base can be found after feature_warm installed
        intent = new Intent("com.android.cts.splitapp.intent.SPLIT_NAME_TEST");
        intent.setPackage(PKG);
        assertThat(pm.queryIntentActivities(intent, 0).stream().anyMatch(
                resolveInfo -> resolveInfo.activityInfo.name.equals(
                        "com.android.cts.splitapp.feature.warm.EmptyActivity"))).isTrue();
    }

    private Intent createLaunchIntent() {
        final boolean isInstant = Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("is_instant", "false"));
        if (isInstant) {
            final Intent i = new Intent(Intent.ACTION_VIEW);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.setData(INSTANT_APP_NORESTART_URI);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        } else {
            final Intent i = new Intent("com.android.cts.norestart.START");
            i.addCategory(Intent.CATEGORY_DEFAULT);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        }
    }

    @Test
    public void testBaseInstalled() throws Exception {
        final ConditionVariable cv = new ConditionVariable();
        final BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                assertEquals(1, intent.getIntExtra("CREATE_COUNT", -1));
                assertEquals(0, intent.getIntExtra("NEW_INTENT_COUNT", -1));
                assertNull(intent.getStringExtra("RESOURCE_CONTENT"));
                cv.open();
            }
        };
        final IntentFilter filter = new IntentFilter("com.android.cts.norestart.BROADCAST");
        getContext().registerReceiver(r, filter,
                Context.RECEIVER_EXPORTED | Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        final Intent i = createLaunchIntent();
        getContext().startActivity(i);
        assertTrue(cv.block(2000L));
        getContext().unregisterReceiver(r);
    }

    /**
     * Tests a running activity remains active while a new feature split is installed.
     * <p>
     * Prior to running this test, the activity must be started. That is currently
     * done in {@link #testBaseInstalled()}.
     */
    @Test
    public void testFeatureInstalled() throws Exception {
        final ConditionVariable cv = new ConditionVariable();
        final BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                assertEquals(1, intent.getIntExtra("CREATE_COUNT", -1));
                assertEquals(1, intent.getIntExtra("NEW_INTENT_COUNT", -1));
                assertEquals("Hello feature!", intent.getStringExtra("RESOURCE_CONTENT"));
                cv.open();
            }
        };
        final IntentFilter filter = new IntentFilter("com.android.cts.norestart.BROADCAST");
        getContext().registerReceiver(r, filter,
                Context.RECEIVER_EXPORTED | Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        final Intent i = createLaunchIntent();
        getContext().startActivity(i);
        assertTrue(cv.block(2000L));
        getContext().unregisterReceiver(r);
    }

    @Test
    public void testFeatureWarmApi() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Should have untouched resources from base
        assertEquals(false, r.getBoolean(R.bool.my_receiver_enabled));

        // And that we can access resources from feature
        assertEquals(321, r.getInteger(r.getIdentifier(
                "com.android.cts.splitapp.feature_warm:feature_integer", "integer", PKG)));

        final Class<?> featR = Class.forName("com.android.cts.splitapp.FeatureR");
        final int boolId = (int) featR.getDeclaredField("feature_receiver_enabled").get(null);
        final int intId = (int) featR.getDeclaredField("feature_integer").get(null);
        final int stringId = (int) featR.getDeclaredField("feature_string").get(null);
        assertEquals(false, r.getBoolean(boolId));
        assertEquals(321, r.getInteger(intId));
        assertEquals("red", r.getString(stringId));

        // And now both receivers should be disabled
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);
        List<ResolveInfo> result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(0, result.size());
    }

    @Test
    public void testInheritUpdatedBase_withRevisionA() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Resources should have been updated
        assertEquals(true, r.getBoolean(R.bool.my_receiver_enabled));

        assertEquals("blue-revision", r.getString(R.string.my_string1));
        assertEquals("purple-revision", r.getString(R.string.my_string2));

        assertEquals(0xff00ffff, r.getColor(R.color.my_color));
        assertEquals(456, r.getInteger(R.integer.my_integer));

        // Also, new resources could be found
        assertEquals("new string", r.getString(r.getIdentifier(
                "my_new_string", "string", PKG)));

        assertAssetContents(r, "fileA.txt", "FILEA");
        assertAssetContents(r, "dir/dirfileA.txt", "DIRFILEA");

        // Activity of ACTION_MAIN should have been updated to .revision_a.MyActivity
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(PKG);
        final List<String> activityNames = pm.queryIntentActivities(intent, 0).stream()
                .map(info -> info.activityInfo.name).collect(Collectors.toList());
        assertThat(activityNames).contains("com.android.cts.splitapp.revision_a.MyActivity");

        // Receiver of DATE_CHANGED should have been updated to .revision_a.MyReceiver
        intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);
        final List<String> receiverNames = pm.queryBroadcastReceivers(intent, 0).stream()
                .map(info -> info.activityInfo.name).collect(Collectors.toList());
        assertThat(receiverNames).contains("com.android.cts.splitapp.revision_a.MyReceiver");

        // Provider should have been updated to .revision_a.MyProvider
        final ProviderInfo info = pm.resolveContentProvider("com.android.cts.splitapp", 0);
        assertEquals("com.android.cts.splitapp.revision_a.MyProvider", info.name);

        // And assert that we spun up the provider in this process
        final Class<?> provider = Class.forName("com.android.cts.splitapp.revision_a.MyProvider");
        final Field field = provider.getDeclaredField("sCreated");
        assertTrue("Expected provider to have been created", (boolean) field.get(null));

        // Camera permission has been removed
        try {
            getContext().enforceCallingOrSelfPermission(android.Manifest.permission.CAMERA, null);
            fail("Camera permission should not be granted");
        } catch (SecurityException expected) {
        }

        // New Vibrate permission should be granted
        getContext().enforceCallingOrSelfPermission(android.Manifest.permission.VIBRATE, null);
    }

    @Test
    public void testInheritUpdatedSplit_withRevisionA() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Resources should have been updated
        assertEquals("red-revision", r.getString(r.getIdentifier(
                "com.android.cts.splitapp.feature_warm:feature_string", "string", PKG)));
        assertEquals(456, r.getInteger(r.getIdentifier(
                "com.android.cts.splitapp.feature_warm:feature_integer", "integer", PKG)));

        // Also, new resources could be found
        assertEquals("feature new string", r.getString(r.getIdentifier(
                "com.android.cts.splitapp.feature_warm:feature_new_string", "string", PKG)));

        assertAssetContents(r, "fileFA.txt", "FILE_FA");
        assertAssetContents(r, "dir/dirfileFA.txt", "DIRFILE_FA");

        // Activity of ACTION_MAIN should have been updated to .revision_a.FeatureActivity
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(PKG);
        final List<String> activityNames = pm.queryIntentActivities(intent, 0).stream()
                .map(info -> info.activityInfo.name).collect(Collectors.toList());
        assertThat(activityNames).contains("com.android.cts.splitapp.revision_a.FeatureActivity");

        // Receiver of DATE_CHANGED could not be found
        intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);
        final List<String> receiverNames = pm.queryBroadcastReceivers(intent, 0).stream()
                .map(info -> info.activityInfo.name).collect(Collectors.toList());
        assertThat(receiverNames).doesNotContain("com.android.cts.splitapp.FeatureReceiver");

        // Service of splitapp should have been updated to .revision_a.FeatureService
        intent = new Intent("com.android.cts.splitapp.service");
        intent.setPackage(PKG);
        final List<String> serviceNames = pm.queryIntentServices(intent, 0).stream()
                .map(info -> info.serviceInfo.name).collect(Collectors.toList());
        assertThat(serviceNames).contains("com.android.cts.splitapp.revision_a.FeatureService");

        // Provider should have been updated to .revision_a.FeatureProvider
        final ProviderInfo info = pm.resolveContentProvider(
                "com.android.cts.splitapp.provider", 0);
        assertEquals("com.android.cts.splitapp.revision_a.FeatureProvider", info.name);

        // And assert that we spun up the provider in this process
        final Class<?> provider = Class.forName(
                "com.android.cts.splitapp.revision_a.FeatureProvider");
        final Field field = provider.getDeclaredField("sCreated");
        assertTrue("Expected provider to have been created", (boolean) field.get(null));
    }

    /**
     * Write app data in a number of locations that expect to remain intact over
     * long periods of time, such as across app moves.
     */
    @Test
    public void testDataWrite() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        writeString(getContext().getFileStreamPath("my_int"), token);

        final SQLiteDatabase db = getContext().openOrCreateDatabase("my_db",
                Context.MODE_PRIVATE, null);
        try {
            db.execSQL("DROP TABLE IF EXISTS my_table");
            db.execSQL("CREATE TABLE my_table(value INTEGER)");
            db.execSQL("INSERT INTO my_table VALUES (101), (102), (103)");
        } finally {
            db.close();
        }
    }

    /**
     * Verify that data written by {@link #testDataWrite()} is still intact.
     */
    @Test
    public void testDataRead() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        assertEquals(token, readString(getContext().getFileStreamPath("my_int")));

        final SQLiteDatabase db = getContext().openOrCreateDatabase("my_db",
                Context.MODE_PRIVATE, null);
        try {
            final Cursor cursor = db.query("my_table", null, null, null, null, null, "value ASC");
            try {
                assertEquals(3, cursor.getCount());
                assertTrue(cursor.moveToPosition(0));
                assertEquals(101, cursor.getInt(0));
                assertTrue(cursor.moveToPosition(1));
                assertEquals(102, cursor.getInt(0));
                assertTrue(cursor.moveToPosition(2));
                assertEquals(103, cursor.getInt(0));
            } finally {
                cursor.close();
            }
        } finally {
            db.close();
        }
    }

    /**
     * Verify that app is installed on internal storage.
     */
    @Test
    public void testDataInternal() throws Exception {
        final StructStat internal = Os.stat(Environment.getDataDirectory().getAbsolutePath());
        final StructStat actual = Os.stat(getContext().getFilesDir().getAbsolutePath());
        assertEquals(internal.st_dev, actual.st_dev);
    }

    /**
     * Verify that app is not installed on internal storage.
     */
    @Test
    public void testDataNotInternal() throws Exception {
        final StructStat internal = Os.stat(Environment.getDataDirectory().getAbsolutePath());
        final StructStat actual = Os.stat(getContext().getFilesDir().getAbsolutePath());
        MoreAsserts.assertNotEqual(internal.st_dev, actual.st_dev);
    }

    @Test
    public void testPrimaryDataWrite() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        writeString(new File(getContext().getExternalFilesDir(null), "my_ext"), token);
    }

    @Test
    public void testPrimaryDataRead() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        assertEquals(token, readString(new File(getContext().getExternalFilesDir(null), "my_ext")));
    }

    /**
     * Verify shared storage behavior when on internal storage.
     */
    @Test
    public void testPrimaryInternal() throws Exception {
        assertTrue("emulated", Environment.isExternalStorageEmulated());
        assertFalse("removable", Environment.isExternalStorageRemovable());
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify shared storage behavior when on physical storage.
     */
    @Test
    public void testPrimaryPhysical() throws Exception {
        assertFalse("emulated", Environment.isExternalStorageEmulated());
        assertTrue("removable", Environment.isExternalStorageRemovable());
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify shared storage behavior when on adopted storage.
     */
    @Test
    public void testPrimaryAdopted() throws Exception {
        assertTrue("emulated", Environment.isExternalStorageEmulated());
        assertTrue("removable", Environment.isExternalStorageRemovable());
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify that shared storage is unmounted.
     */
    @Test
    public void testPrimaryUnmounted() throws Exception {
        MoreAsserts.assertNotEqual(Environment.MEDIA_MOUNTED,
                Environment.getExternalStorageState());
    }

    /**
     * Verify that shared storage lives on same volume as app.
     */
    @Test
    public void testPrimaryOnSameVolume() throws Exception {
        final File current = getContext().getFilesDir();
        final File primary = Environment.getExternalStorageDirectory();

        // Shared storage may jump through another filesystem for permission
        // enforcement, so we verify that total/free space are identical.
        final long totalDelta = Math.abs(current.getTotalSpace() - primary.getTotalSpace());
        final long freeDelta = Math.abs(current.getFreeSpace() - primary.getFreeSpace());
        if (totalDelta > MB_IN_BYTES * 300 || freeDelta > MB_IN_BYTES * 300) {
            fail("Expected primary storage to be on same volume as app");
        }
    }

    @Test
    public void testCodeCacheWrite() throws Exception {
        assertTrue(new File(getContext().getFilesDir(), "normal.raw").createNewFile());
        assertTrue(new File(getContext().getCodeCacheDir(), "cache.raw").createNewFile());
    }

    @Test
    public void testCodeCacheRead() throws Exception {
        assertTrue(new File(getContext().getFilesDir(), "normal.raw").exists());
        assertFalse(new File(getContext().getCodeCacheDir(), "cache.raw").exists());
    }

    @Test
    public void testRevision0_0() throws Exception {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo(getContext().getPackageName(), 0);
        assertEquals(0, info.baseRevisionCode);
        assertEquals(1, info.splitRevisionCodes.length);
        assertEquals(0, info.splitRevisionCodes[0]);
    }

    @Test
    public void testRevision12_0() throws Exception {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo(getContext().getPackageName(), 0);
        assertEquals(12, info.baseRevisionCode);
        assertEquals(1, info.splitRevisionCodes.length);
        assertEquals(0, info.splitRevisionCodes[0]);
    }

    @Test
    public void testRevision0_12() throws Exception {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo(getContext().getPackageName(), 0);
        assertEquals(0, info.baseRevisionCode);
        assertEquals(1, info.splitRevisionCodes.length);
        assertEquals(12, info.splitRevisionCodes[0]);
    }

    @Test
    public void testComponentWithSplitName_singleBase() {
        final PackageManager pm = getContext().getPackageManager();
        final Intent intent = new Intent("com.android.cts.splitapp.intent.SPLIT_NAME_TEST");
        intent.setPackage(PKG);

        // Service with split name `feature_warm` cannot be found
        List<ResolveInfo> resolveInfoList = pm.queryIntentServices(intent, 0);
        assertThat(resolveInfoList.stream().noneMatch(resolveInfo -> getComponentName(resolveInfo)
                .equals(FEATURE_WARM_EMPTY_SERVICE_NAME))).isTrue();

        // Provider with split name `feature_warm` cannot be found
        resolveInfoList = pm.queryIntentContentProviders(intent, 0);
        assertThat(resolveInfoList.stream().noneMatch(resolveInfo -> getComponentName(resolveInfo)
                .equals(FEATURE_WARM_EMPTY_PROVIDER_NAME))).isTrue();
    }

    @Test
    public void testComponentWithSplitName_featureWarmInstalled() throws Exception {
        final PackageManager pm = getContext().getPackageManager();
        final Intent intent = new Intent("com.android.cts.splitapp.intent.SPLIT_NAME_TEST");
        intent.setPackage(PKG);

        // Service with split name `feature_warm` could be found
        List<ResolveInfo> resolveInfoList = pm.queryIntentServices(intent, 0);
        assertThat(resolveInfoList.stream().anyMatch(resolveInfo -> getComponentName(resolveInfo)
                .equals(FEATURE_WARM_EMPTY_SERVICE_NAME))).isTrue();

        // Provider with split name `feature_warm` could be found
        resolveInfoList = pm.queryIntentContentProviders(intent, 0);
        assertThat(resolveInfoList.stream().anyMatch(resolveInfo -> getComponentName(resolveInfo)
                .equals(FEATURE_WARM_EMPTY_PROVIDER_NAME))).isTrue();

        // And assert that we spun up the provider in this process
        final Class<?> provider = Class.forName(FEATURE_WARM_EMPTY_PROVIDER_NAME.getClassName());
        final Field field = provider.getDeclaredField("sCreated");
        assertThat((boolean) field.get(null)).isTrue();
    }

    @Test
    public void launchBaseActivity_withThemeBase_baseApplied() {
        assertActivityLaunchedAndThemeApplied(BASE_THEME_ACTIVITY, R.style.Theme_Base,
                ThemeColors.BASE);
    }

    @Test
    public void launchBaseActivity_withThemeBaseLt_baseLtApplied() {
        assertActivityLaunchedAndThemeApplied(BASE_THEME_ACTIVITY, R.style.Theme_Base,
                ThemeColors.BASE_LT);
    }

    @Test
    public void launchBaseActivity_withThemeWarm_warmApplied() {
        assertActivityLaunchedAndThemeApplied(BASE_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_WARM), ThemeColors.WARM);
    }

    @Test
    public void launchBaseActivity_withThemeWarmLt_warmLtApplied() {
        assertActivityLaunchedAndThemeApplied(BASE_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_WARM), ThemeColors.WARM_LT);
    }

    @Test
    public void launchWarmActivity_withThemeBase_baseApplied() {
        assertActivityLaunchedAndThemeApplied(WARM_THEME_ACTIVITY, R.style.Theme_Base,
                ThemeColors.BASE);
    }

    @Test
    public void launchWarmActivity_withThemeBaseLt_baseLtApplied() {
        assertActivityLaunchedAndThemeApplied(WARM_THEME_ACTIVITY, R.style.Theme_Base,
                ThemeColors.BASE_LT);
    }

    @Test
    public void launchWarmActivity_withThemeWarm_warmApplied() {
        assertActivityLaunchedAndThemeApplied(WARM_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_WARM), ThemeColors.WARM);
    }

    @Test
    public void launchWarmActivity_withThemeWarmLt_warmLtApplied() {
        assertActivityLaunchedAndThemeApplied(WARM_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_WARM), ThemeColors.WARM_LT);
    }

    @Test
    public void launchWarmActivity_withThemeRose_roseApplied() {
        assertActivityLaunchedAndThemeApplied(WARM_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_ROSE), ThemeColors.ROSE);
    }

    @Test
    public void launchWarmActivity_withThemeRoseLt_roseLtApplied() {
        assertActivityLaunchedAndThemeApplied(WARM_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_ROSE), ThemeColors.ROSE_LT);
    }

    @Test
    public void launchRoseActivity_withThemeWarm_warmApplied() {
        assertActivityLaunchedAndThemeApplied(ROSE_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_WARM), ThemeColors.WARM);
    }

    @Test
    public void launchRoseActivity_withThemeWarmLt_warmLtApplied() {
        assertActivityLaunchedAndThemeApplied(ROSE_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_WARM), ThemeColors.WARM_LT);
    }

    @Test
    public void launchRoseActivity_withThemeRose_roseApplied() {
        assertActivityLaunchedAndThemeApplied(ROSE_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_ROSE), ThemeColors.ROSE);
    }

    @Test
    public void launchRoseActivity_withThemeRoseLt_roseLtApplied() {
        assertActivityLaunchedAndThemeApplied(ROSE_THEME_ACTIVITY,
                resolveResourceId(TestThemeHelper.THEME_ROSE), ThemeColors.ROSE_LT);
    }

    private void assertActivityLaunchedAndThemeApplied(String activityName, int themeResId,
            ThemeColors themeColors) {
        final Activity activity = mActivityRule.launchActivity(
                getTestThemeIntent(activityName, themeResId));
        final TestThemeHelper expected = new TestThemeHelper(activity, themeResId);
        expected.assertThemeValues(themeColors);
        expected.assertThemeApplied(activity);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static void updateDpi(Resources r, int densityDpi) {
        final Configuration c = new Configuration(r.getConfiguration());
        c.densityDpi = densityDpi;
        r.updateConfiguration(c, r.getDisplayMetrics());
    }

    private static void updateLocale(Resources r, Locale locale) {
        final Configuration c = new Configuration(r.getConfiguration());
        c.locale = locale;
        r.updateConfiguration(c, r.getDisplayMetrics());
    }

    private static int getDrawableColor(Drawable d) {
        final Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        d.draw(canvas);
        return bitmap.getPixel(0, 0);
    }

    private static String getXmlTestValue(XmlPullParser in) throws XmlPullParserException,
            IOException {
        int type;
        while ((type = in.next()) != END_DOCUMENT) {
            if (type == START_TAG) {
                final String tag = in.getName();
                if ("tag".equals(tag)) {
                    return in.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }

    private static void assertAssetContents(Resources r, String path, String expected)
            throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(r.getAssets().open(path)));
            assertEquals(expected, in.readLine());
        } finally {
            if (in != null) in.close();
        }
    }

    private static void writeString(File file, String value) throws IOException {
        final DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
        try {
            os.writeUTF(value);
        } finally {
            os.close();
        }
    }

    private static String readString(File file) throws IOException {
        final DataInputStream is = new DataInputStream(new FileInputStream(file));
        try {
            return is.readUTF();
        } finally {
            is.close();
        }
    }

    private int resolveResourceId(String nameOfIdentifier) {
        final int resId = getContext().getResources().getIdentifier(nameOfIdentifier, null, null);
        assertTrue("Resource not found: " + nameOfIdentifier, resId != 0);
        return resId;
    }

    private static Intent getTestThemeIntent(String activityName, int themeResId) {
        final Intent intent = new Intent(ThemeActivity.INTENT_THEME_TEST);
        intent.setComponent(ComponentName.createRelative(PKG, activityName));
        intent.putExtra(ThemeActivity.EXTRAS_THEME_RES_ID, themeResId);
        return intent;
    }

    private static ComponentName getComponentName(ResolveInfo resolveInfo) {
        final ComponentInfo componentInfo = resolveInfo.activityInfo != null
                ? resolveInfo.activityInfo : resolveInfo.serviceInfo != null
                ? resolveInfo.serviceInfo : resolveInfo.providerInfo;
        if (componentInfo == null) {
            throw new AssertionError("Missing ComponentInfo in the ResolveInfo!");
        }
        return new ComponentName(componentInfo.packageName, componentInfo.name);
    }

    private static void setAppLinksUserSelection(String packageName, String uriHostName,
            boolean enabled) {
        final String cmd = String.format("pm set-app-links-user-selection --user cur --package "
                + "%s %b %s", packageName, enabled, uriHostName);
        SystemUtil.runShellCommand(cmd);
    }
}
