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

package android.media.router.cts.proxyroutingapp;

import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Device-side test for privileged {@link MediaRouter2} functionality. */
@LargeTest
public class MediaRouter2DeviceTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int TIMEOUT_MS = 5000;

    private Instrumentation mInstrumentation;
    private Context mContext;
    private Executor mExecutor;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @SuppressLint("MissingPermission")
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL)
    public void getInstance_withMediaRoutingControl_flagDisabled_throwsSecurityException() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        try {
            assertThrows(
                    SecurityException.class,
                    () -> MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE));
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @SuppressLint("MissingPermission")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL)
    public void getInstance_withMediaRoutingControl_flagEnabled_doesNotThrow() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        try {
            MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @SuppressLint("MissingPermission")
    @Test
    public void getInstance_withoutMediaRoutingControl_throwsSecurityException() {
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThrows(
                SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE));
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void getInstance_withinUser_withMediaRoutingControl_flagEnabled_returnsInstance() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        try {
            assertThat(
                            MediaRouter2.getInstance(
                                    mContext,
                                    mContext.getPackageName(),
                                    mContext.getUser()))
                    .isNotNull();
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void getInstance_withinUser_withoutMediaRoutingControl_throwsSecurityException() {
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThrows(
                SecurityException.class,
                () ->
                        MediaRouter2.getInstance(
                                mContext,
                                mContext.getPackageName(),
                                mContext.getUser()));
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void requestScan_withScreenOff_triggersScanning() throws InterruptedException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        MediaRouter2 localInstance = MediaRouter2.getInstance(mContext);
        MediaRouter2.RouteCallback placeholderCallback = new MediaRouter2.RouteCallback() {};
        localInstance.registerRouteCallback(
                mExecutor,
                placeholderCallback,
                new RouteDiscoveryPreference.Builder(List.of(FEATURE_SAMPLE), false).build());

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

        MediaRouter2.ScanToken token =
                instance.requestScan(
                        new MediaRouter2.ScanRequest.Builder().setScreenOffScan(true).build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            instance.cancelScanRequest(token);
            localInstance.unregisterRouteCallback(placeholderCallback);
            instance.unregisterRouteCallback(onRoutesUpdated);
        }
    }
}
