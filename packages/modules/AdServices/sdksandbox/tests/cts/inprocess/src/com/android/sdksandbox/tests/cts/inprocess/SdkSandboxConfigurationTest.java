/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sdksandbox.tests.cts.inprocess;

import static android.content.Context.MODE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.DeviceSupportUtils;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Process;
import android.os.SELinux;
import android.webkit.WebView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.AdServicesCommon;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests to check some basic properties of the Sdk Sandbox processes.
 */
@RunWith(JUnit4.class)
public class SdkSandboxConfigurationTest {

    private static final String TEST_PKG = "com.android.sdksandbox.tests.cts.inprocesstests";
    private static final String CURRENT_USER_ID =
            String.valueOf(Process.myUserHandle().getUserId(Process.myUid()));
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();

    @Before
    public void setUp() {
        assumeTrue(
                DeviceSupportUtils.isSdkSandboxSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    /** Tests that uid belongs to the sdk sandbox processes uid range. */
    @Test
    public void testUidBelongsToSdkSandboxRange() {
        int myUid = Process.myUid();
        assertWithMessage(myUid + " is not a SdkSandbox uid").that(Process.isSdkSandbox()).isTrue();
    }

    /**
     * Tests that sdk sandbox processes are running under the {@code sdk_sandbox} selinux domain.
     */
    @Test
    public void testCorrectSelinuxDomain() {
        final String selinuxContext = SELinux.getContext();
        assertThat(selinuxContext).contains("u:r:sdk_sandbox");
    }

    /** Tests that sdk sandbox SDK minimum and target versions are correct. */
    @Test
    public void testCorrectSdkVersion() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final PackageInfo info =
                pm.getPackageInfo(mContext.getPackageName(), PackageInfoFlags.of(0));

        int minSdkVersion = info.applicationInfo.minSdkVersion;
        assertThat(minSdkVersion).isEqualTo(33);

        int targetSdkVersion = info.applicationInfo.targetSdkVersion;
        assertThat(targetSdkVersion).isAtLeast(33);
    }

    /**
     * Tests that client app is visible to the sdk sandbox.
     */
    @Test
    public void testClientAppIsVisibleToSdkSandbox() throws Exception {
        final PackageInfo info = mPackageManager.getPackageInfo(TEST_PKG, PackageInfoFlags.of(0));
        assertThat(info.applicationInfo.uid).isEqualTo(
                Process.getAppUidForSdkSandboxUid(Process.myUid()));
    }

    /**
     * Tests that {@link Context#getDataDir()} returns correct value for the CE storage of the sak
     * sandbox.
     */
    @Test
    public void testGetDataDir_CE() {
        final File dir = mContext.getDataDir();
        assertThat(dir.getAbsolutePath())
                .isEqualTo(
                        "/data/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/" + TEST_PKG + "/shared");
    }

    /**
     * Tests that {@link Context#getDataDir()} returns correct value for the DE storage of the sak
     * sandbox.
     */
    @Test
    public void testGetDataDir_DE() throws Exception {
        final Context ctx = mContext.createDeviceProtectedStorageContext();
        final File dir = ctx.getDataDir();
        assertThat(dir.getAbsolutePath())
                .isEqualTo(
                        "/data/misc_de/" + CURRENT_USER_ID + "/sdksandbox/" + TEST_PKG + "/shared");
    }

    /** Tests that sdk sandbox process can write to it's CE storage. */
    @Test
    public void testCanWriteToDataDir_CE() throws Exception {
        try (OutputStreamWriter writer =
                new OutputStreamWriter(mContext.openFileOutput("random_ce_file", MODE_PRIVATE))) {
            writer.write("I am an sdk sandbox");
        }
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(mContext.openFileInput("random_ce_file")))) {
            String line = reader.readLine();
            assertThat(line).isEqualTo("I am an sdk sandbox");
        }
    }

    /** Tests that sdk sandbox process can write to it's DE storage. */
    @Test
    public void testCanWriteToDataDir_DE() throws Exception {
        final Context ctx = mContext.createDeviceProtectedStorageContext();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                ctx.openFileOutput("random_de_file", MODE_PRIVATE))) {
            writer.write("I am also an sdk sandbox");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ctx.openFileInput("random_de_file")))) {
            String line = reader.readLine();
            assertThat(line).isEqualTo("I am also an sdk sandbox");
        }
    }

    /** Tests that sdk sandbox process can resolve the package that provides AdServices APIs. */
    @Test
    @Ignore("b/243146745")
    public void testCanResolveAndBindToAdServicesApiPackage() throws Exception {
        // Only run this test if sdk sandbox is enabled.
        assumeThat(
                SdkSandboxManager.getSdkSandboxState(),
                equalTo(SdkSandboxManager.SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION));

        // First check that we can resolve the adservices apk
        final Intent resolveIntent = new Intent(AdServicesCommon.ACTION_TOPICS_SERVICE);
        final List<ResolveInfo> services =
                mPackageManager.queryIntentServices(
                        resolveIntent,
                        PackageManager.ResolveInfoFlags.of(
                                PackageManager.GET_SERVICES
                                        | PackageManager.MATCH_SYSTEM_ONLY
                                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE));
        assertThat(services).hasSize(1);
        final ServiceInfo serviceInfo = services.get(0).serviceInfo;

        // Now check that we can bind to the adservices api process.
        final Intent serviceIntent =
                new Intent()
                        .setComponent(new ComponentName(serviceInfo.packageName, serviceInfo.name));
        final CountDownLatch latch = new CountDownLatch(1);
        final ServiceConnection conn =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        final boolean ret = mContext.bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE);

        try {
            assertThat(ret).isTrue();
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            mContext.unbindService(conn);
        }
    }

    /**
     * Tests that after sdk sandbox has requested a current WebView provider, then the provider is
     * visible to this sdk sandbox.
     */
    @Test
    public void testCurrentWebViewProviderIsVisibleToSdkSandbox() throws Exception {
        // This call will force a current webview provider to become visible to this sdk sandbox
        // process.
        final PackageInfo info = WebView.getCurrentWebViewPackage();
        assertThat(info).isNotNull();

        // Now time to query the current WebView provider through PackageManager, this is used to
        // check if this sdk sandbox process can see the WebView.
        final PackageInfo webViewProviderInfo =
                mPackageManager.getPackageInfo(info.packageName, PackageInfoFlags.of(0));
        assertThat(webViewProviderInfo).isNotNull();
    }

    @Test
    public void testCanAccessGyroscope() {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE));

        SensorManager sensorManager = mContext.getSystemService(SensorManager.class);
        assertThat(sensorManager).isNotNull();
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        assertThat(gyroscope).isNotNull();
    }

    @Test
    public void testCanAccessVolume() {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT));

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
        int minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_SYSTEM);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        assertThat(currentVolume).isAtMost(maxVolume);
        assertThat(currentVolume).isAtLeast(minVolume);
    }
}
