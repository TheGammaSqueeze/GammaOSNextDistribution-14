/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.router.cts.proxymediacontentcontrolapp;

import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.TARGET_USER_ID_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ScanRequest;
import android.media.RouteDiscoveryPreference;
import android.media.cts.app.common.ScreenOnActivity;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Device-side test for {@link MediaRouter2} functionality. */
@LargeTest
public class MediaRouter2DeviceTest {
    private static final int TIMEOUT_MS = 5000;
    private Context mContext;
    private Executor mExecutor;
    private Activity mScreenOnActivity;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);
    }

    private void loadScreenOnActivity() {
        // Launch ScreenOnActivity while tests are running for scanning to work. MediaRouter2 blocks
        // app scan requests while the screen is off for resource saving.
        Intent intent = new Intent(/* context= */ mContext, ScreenOnActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mScreenOnActivity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();

        // mScreenOnActivity may be null if we failed to launch the activity. The NPE would not
        // change the outcome of the test, but it would misdirect attention, away from the root
        // cause of the failure.
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void requestScan_withScreenOnScanning_triggersScanning() throws InterruptedException {
        loadScreenOnActivity();

        MediaRouter2 localInstance = MediaRouter2.getInstance(mContext);
        MediaRouter2.RouteCallback placeholderCallback = new MediaRouter2.RouteCallback() {};
        localInstance.registerRouteCallback(
                mExecutor,
                placeholderCallback,
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* isActiveScan */ false)
                        .build());

        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        assertThat(instance).isNotNull();
        CountDownLatch latch = new CountDownLatch(1);

        MediaRouter2.RouteCallback onRoutesUpdated =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        instance.registerRouteCallback(mExecutor, onRoutesUpdated, RouteDiscoveryPreference.EMPTY);

        MediaRouter2.ScanToken token = instance.requestScan(new ScanRequest.Builder().build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            instance.cancelScanRequest(token);
            localInstance.unregisterRouteCallback(placeholderCallback);
            instance.unregisterRouteCallback(onRoutesUpdated);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING)
    @Test
    public void requestScan_screenOff_withoutMediaRoutingControl_throwsSecurityException() {
        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        assertThat(instance).isNotNull();
        assertThrows(
                SecurityException.class,
                () ->
                        instance.requestScan(
                                new ScanRequest.Builder().setScreenOffScan(true).build()));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING)
    @Test
    public void cancelScanRequest_callTwice_throwsIllegalArgumentException() {
        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        assertThat(instance).isNotNull();
        MediaRouter2.ScanToken token = instance.requestScan(new ScanRequest.Builder().build());
        instance.cancelScanRequest(token);
        assertThrows(IllegalArgumentException.class, () -> instance.cancelScanRequest(token));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_acrossUsers_withInteractAcrossUsersFull_returnsInstance() {
        Bundle args = InstrumentationRegistry.getArguments();
        assertThat(args.containsKey(TARGET_USER_ID_KEY)).isTrue();
        int targetUserId = Integer.parseInt(args.getString(TARGET_USER_ID_KEY));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                        Manifest.permission.MEDIA_CONTENT_CONTROL);

        assertThat(mContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(mContext.checkSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThat(
                        MediaRouter2.getInstance(
                                mContext,
                                MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE,
                                UserHandle.of(targetUserId)))
                .isNotNull();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_acrossUsers_withoutInteractAcrossUsersFull_throwsSecurityException() {
        Bundle args = InstrumentationRegistry.getArguments();
        assertThat(args.containsKey(TARGET_USER_ID_KEY)).isTrue();
        int targetUserId = Integer.parseInt(args.getString(TARGET_USER_ID_KEY));

        assertThat(mContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThrows(
                SecurityException.class,
                () ->
                        MediaRouter2.getInstance(
                                mContext,
                                MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE,
                                UserHandle.of(targetUserId)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @SuppressLint("MissingPermission")
    @Test
    public void getInstance_acrossUsers_withFakePackageName_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouter2.getInstance(
                                mContext,
                                /* clientPackageName */ "FAKE_PACKAGE_NAME",
                                mContext.getUser()));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_withinUser_returnsInstance() {
        assertThat(
                        MediaRouter2.getInstance(
                                mContext,
                                mContext.getPackageName(),
                                mContext.getUser()))
                .isNotNull();
    }

    @Test
    public void getAllRoutes_returnsAtLeastOneSystemRoute() {
        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        List<MediaRoute2Info> allRoutes = instance.getAllRoutes();
        assertThat(allRoutes).isNotEmpty();
        assertThat(allRoutes.stream().filter(MediaRoute2Info::isSystemRoute).findAny()).isNotNull();
    }
}
