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

package android.media.router.cts.modifyaudioroutingapp;

import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.MediaRoute2Info.FEATURE_LIVE_VIDEO;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.platform.test.annotations.LargeTest;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.media.flags.Flags;

import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Executors;

/** Device-side test for {@link MediaRouter2} functionality. */
@LargeTest
public class MediaRouter2DeviceTestWithModifyAudioRouting {

    // TODO: b/316864909 - Stop relying on route ids once we can control system routing in CTS.
    private static final String ROUTE_ID_BUILTIN_SPEAKER =
            Flags.enableAudioPoliciesDeviceAndBluetoothController()
                    ? "ROUTE_ID_BUILTIN_SPEAKER"
                    : MediaRoute2Info.ROUTE_ID_DEVICE;

    /** {@link RouteDiscoveryPreference} for system routes only. */
    private static final RouteDiscoveryPreference SYSTEM_ROUTE_DISCOVERY_PREFERENCE =
            new RouteDiscoveryPreference.Builder(
                            List.of(FEATURE_LIVE_AUDIO, FEATURE_LIVE_VIDEO),
                            /* activeScan= */ false)
                    .build();

    private static final Correspondence<MediaRoute2Info, String> ROUTE_HAS_ORIGINAL_ID =
            Correspondence.transforming(
                    MediaRoute2Info::getOriginalId, /* description */ "has original id of");

    private final MediaRouter2.RouteCallback mPlaceholderRouteCallback =
            new MediaRouter2.RouteCallback() {};
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
    }

    @Test
    public void getSystemController_withModifyAudioRouting_returnsDeviceRoute() {
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MODIFY_AUDIO_ROUTING))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        MediaRouter2 mediaRouter2 = MediaRouter2.getInstance(mContext);

        MediaRouter2.RoutingController systemController = mediaRouter2.getSystemController();
        assertThat(systemController.getSelectedRoutes())
                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                .containsExactly(ROUTE_ID_BUILTIN_SPEAKER);
    }

    @Test
    public void getRoutes_withModifyAudioRouting_returnsDeviceRoute() {
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MODIFY_AUDIO_ROUTING))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        MediaRouter2 mediaRouter2 = MediaRouter2.getInstance(mContext);

        try {
            mediaRouter2.registerRouteCallback(
                    Executors.newSingleThreadExecutor(),
                    mPlaceholderRouteCallback,
                    SYSTEM_ROUTE_DISCOVERY_PREFERENCE);

            assertThat(mediaRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactly(ROUTE_ID_BUILTIN_SPEAKER);
        } finally {
            mediaRouter2.unregisterRouteCallback(mPlaceholderRouteCallback);
        }
    }
}
