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

package android.media.bettertogether.cts;

import static android.content.Context.AUDIO_SERVICE;
import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;
import static android.media.MediaRoute2ProviderService.REASON_REJECTED;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID3_SESSION_CREATION_FAILED;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID4_TO_SELECT_AND_DESELECT;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID6_REJECT_SET_VOLUME;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID7_STATIC_GROUP;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID_VARIABLE_VOLUME;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_NAME2;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.STATIC_GROUP_SELECTED_ROUTES_IDS;

import static com.android.media.flags.Flags.FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ControllerCallback;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2.TransferCallback;
import android.media.MediaRouter2Manager;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.RoutingSessionInfo;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.media.flags.Flags;

import com.google.common.truth.Correspondence;
import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to StubMediaRoute2ProviderService")
@LargeTest
@NonMainlineTest
public class SystemMediaRouter2Test {
    private static final String TAG = "SystemMR2Test";

    @Rule public final Expect expect = Expect.create();
    @Rule public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    UiAutomation mUiAutomation;
    Context mContext;
    private MediaRouter2 mSystemRouter2ForCts;
    private MediaRouter2 mAppRouter2;

    private Executor mExecutor;
    private AudioManager mAudioManager;
    private StubMediaRoute2ProviderService mService;

    private static final int TIMEOUT_MS = 5000;
    private static final int WAIT_MS = 2000;
    private static final String EXTRA_ROUTE_ID = "EXTRA_ROUTE_ID";
    private static final String EXTRA_ROUTE_NAME = "EXTRA_ROUTE_NAME";
    private static final Correspondence<MediaRoute2Info, String> ROUTE_HAS_ORIGINAL_ID =
            Correspondence.transforming(MediaRoute2Info::getOriginalId, "has original id");

    private RouteCallback mAppRouterPlaceHolderCallback = new RouteCallback() {};

    private final List<RouteCallback> mRouteCallbacks = new ArrayList<>();
    private final List<TransferCallback> mTransferCallbacks = new ArrayList<>();

    public static final List<String> FEATURES_ALL = new ArrayList();
    public static final List<String> FEATURES_SPECIAL = new ArrayList();

    static {
        FEATURES_ALL.add(FEATURE_SAMPLE);
        FEATURES_ALL.add(FEATURE_SPECIAL);
        FEATURES_ALL.add(FEATURE_LIVE_AUDIO);

        FEATURES_SPECIAL.add(FEATURE_SPECIAL);
    }

    private static final MediaRoute2Info EXTRA_ROUTE = new MediaRoute2Info.Builder(EXTRA_ROUTE_ID,
            EXTRA_ROUTE_NAME).addFeature(FEATURE_SAMPLE).build();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.MODIFY_AUDIO_ROUTING,
                Manifest.permission.QUERY_AUDIO_STATE);

        mExecutor = Executors.newSingleThreadExecutor();
        mAudioManager = (AudioManager) mContext.getSystemService(AUDIO_SERVICE);
        MediaRouter2TestActivity.startActivity(mContext);

        mSystemRouter2ForCts = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        mSystemRouter2ForCts.startScan();

        mAppRouter2 = MediaRouter2.getInstance(mContext);
        // In order to make the system bind to the test service,
        // set a non-empty discovery preference.
        List<String> features = new ArrayList<>();
        features.add("A test feature");
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, false).build();
        mRouteCallbacks.add(mAppRouterPlaceHolderCallback);
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback, preference);

        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                StubMediaRoute2ProviderService service =
                        StubMediaRoute2ProviderService.getInstance();
                if (service != null) {
                    mService = service;
                    return true;
                }
                return false;
            }
        }.run();
        mService.initializeRoutes();
        mService.publishRoutes();
    }

    @After
    public void tearDown() throws Exception {
        mSystemRouter2ForCts.stopScan();

        MediaRouter2TestActivity.finishActivity();
        if (mService != null) {
            mService.clear();
            mService = null;
        }

        // order matters (callbacks should be cleared at the last)
        releaseAllSessions();
        // unregister callbacks
        clearCallbacks();

        // Clearing RouteListingPreference.
        mAppRouter2.setRouteListingPreference(null);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testGetInstanceWithInvalidPackageName() {
        assertThat(MediaRouter2.getInstance(mContext, "com.non.existent.package.name")).isNull();
    }

    @Test
    public void testGetInstanceReturnsSameInstance() {
        assertThat(MediaRouter2.getInstance(mContext, mContext.getPackageName()))
                .isSameInstanceAs(mSystemRouter2ForCts);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_withDifferentOverrides_returnsSameInstance() {
        assertThat(MediaRouter2.getInstance(mContext, mContext.getPackageName()))
                .isSameInstanceAs(
                        MediaRouter2.getInstance(
                                mContext,
                                mContext.getPackageName(),
                                mContext.getUser()));
    }

    @Test
    public void testGetClientPackageName() {
        assertThat(mSystemRouter2ForCts.getClientPackageName())
                .isEqualTo(mContext.getPackageName());
    }

    @Test
    public void testGetSystemController() {
        RoutingController controller = mSystemRouter2ForCts.getSystemController();
        assertThat(controller).isNotNull();
        // getSystemController() should always return the same instance.
        assertThat(controller).isSameInstanceAs(mSystemRouter2ForCts.getSystemController());
    }

    @Test
    public void testGetControllerReturnsNullForUnknownId() {
        assertThat(mSystemRouter2ForCts.getController("nonExistentControllerId")).isNull();
    }

    @Test
    public void testGetController() {
        String systemControllerId = mSystemRouter2ForCts.getSystemController().getId();
        RoutingController controllerById = mSystemRouter2ForCts.getController(systemControllerId);
        assertThat(controllerById).isNotNull();
        assertThat(controllerById.getId()).isEqualTo(systemControllerId);
    }

    @Test
    public void testGetAllRoutes() throws Exception {
        waitAndGetRoutes(FEATURE_SPECIAL);

        // Regardless of whether the app router registered its preference,
        // getAllRoutes() will return all the routes.
        boolean routeFound = false;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getAllRoutes()) {
            if (route.getFeatures().contains(FEATURE_SPECIAL)) {
                routeFound = true;
                break;
            }
        }
        assertThat(routeFound).isTrue();
    }

    @Test
    public void testGetRoutes() throws Exception {
        // Since the app router haven't registered any preference yet,
        // only the system routes will come out after creation.
        assertThat(mSystemRouter2ForCts.getRoutes().isEmpty()).isTrue();

        waitAndGetRoutes(FEATURE_SPECIAL);

        boolean routeFound = false;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getRoutes()) {
            if (route.getFeatures().contains(FEATURE_SPECIAL)) {
                routeFound = true;
                break;
            }
        }
        assertThat(routeFound).isTrue();
    }

    @Test
    public void testRouteCallbackOnRoutesAdded() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        CountDownLatch addedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(EXTRA_ROUTE.getOriginalId())
                            && route.getName().equals(EXTRA_ROUTE.getName())) {
                        addedLatch.countDown();
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mService.addRoute(EXTRA_ROUTE);
        assertThat(addedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void addRoute_callsOnRoutesUpdated() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        CountDownLatch addedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(EXTRA_ROUTE.getOriginalId())
                            && route.getName().equals(EXTRA_ROUTE.getName())) {
                        addedLatch.countDown();
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mService.addRoute(EXTRA_ROUTE);
        assertThat(addedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRouteCallbackOnRoutesRemoved() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        CountDownLatch removedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesRemoved(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(ROUTE_ID2)
                            && route.getName().equals(ROUTE_NAME2)) {
                        removedLatch.countDown();
                        break;
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mService.removeRoute(ROUTE_ID2);
        assertThat(removedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void removeRoute_callsOnRoutesUpdated() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(Collections.singletonList(FEATURE_SAMPLE),
                        true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        CountDownLatch removedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                boolean routeFound = routes.stream().anyMatch(
                        route -> TextUtils.equals(route.getOriginalId(), ROUTE_ID2));
                if (!routeFound) {
                    removedLatch.countDown();
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mService.removeRoute(ROUTE_ID2);
        assertThat(removedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void setRouteListingPreference_onPrivilegedInstance_withNull_throws() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mSystemRouter2ForCts.setRouteListingPreference(null));
    }

    @Test
    public void setRouteListingPreference_onPrivilegedInstance_withRLP_throws() {
        RouteListingPreference rlp = new RouteListingPreference.Builder().build();
        assertThrows(
                UnsupportedOperationException.class,
                () -> mSystemRouter2ForCts.setRouteListingPreference(rlp));
    }

    @Test
    public void showSystemOutputSwitcher_onPrivilegedInstance_throws() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mSystemRouter2ForCts.showSystemOutputSwitcher());
    }

    @Test
    public void setRouteListingPreference_callsOnRouteListingPreferenceChanged()
            throws InterruptedException {
        RouteListingPreference.Item item =
                new RouteListingPreference.Item.Builder(ROUTE_ID1).build();
        RouteListingPreference testPreference =
                new RouteListingPreference.Builder()
                        .setItems(List.of(item))
                        .build();

        assertThat(mAppRouter2.getRouteListingPreference()).isNull();
        assertThat(mSystemRouter2ForCts.getRouteListingPreference()).isNull();

        CountDownLatch preferenceChangedLatch = new CountDownLatch(1);
        Consumer<RouteListingPreference> callback =
                (preference) -> {
                    if (Objects.equals(preference, testPreference)) {
                        preferenceChangedLatch.countDown();
                    }
                };

        try {
            mSystemRouter2ForCts.registerRouteListingPreferenceUpdatedCallback(mExecutor, callback);

            mAppRouter2.setRouteListingPreference(testPreference);
            assertThat(preferenceChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mSystemRouter2ForCts.unregisterRouteListingPreferenceUpdatedCallback(callback);
        }
    }

    @Test
    public void getRouteListingPreference_returnsLastSetPreference() throws InterruptedException {
        RouteListingPreference.Item item =
                new RouteListingPreference.Item.Builder(ROUTE_ID1).build();
        RouteListingPreference testPreference =
                new RouteListingPreference.Builder()
                        .setItems(Collections.singletonList(item))
                        .build();

        assertThat(mAppRouter2.getRouteListingPreference()).isNull();
        assertThat(mSystemRouter2ForCts.getRouteListingPreference()).isNull();

        CountDownLatch preferenceChangedLatch = new CountDownLatch(1);
        Consumer<RouteListingPreference> routeListingPreferenceCallback =
                (preference) -> {
                    if (Objects.equals(preference, testPreference)) {
                        preferenceChangedLatch.countDown();
                    }
                };

        try {
            mSystemRouter2ForCts.registerRouteListingPreferenceUpdatedCallback(
                    mExecutor, routeListingPreferenceCallback);

            mAppRouter2.setRouteListingPreference(testPreference);
            expect.that(preferenceChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(mSystemRouter2ForCts.getRouteListingPreference()).isEqualTo(testPreference);
        } finally {
            mSystemRouter2ForCts.unregisterRouteListingPreferenceUpdatedCallback(
                    routeListingPreferenceCallback);
        }
    }

    @Test
    public void testRouteCallbackOnRoutesChanged() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        MediaRoute2Info routeToChangeVolume = null;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getAllRoutes()) {
            if (TextUtils.equals(ROUTE_ID_VARIABLE_VOLUME, route.getOriginalId())) {
                routeToChangeVolume = route;
                break;
            }
        }
        assertThat(routeToChangeVolume).isNotNull();

        int targetVolume = routeToChangeVolume.getVolume() + 1;
        CountDownLatch changedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesChanged(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(ROUTE_ID_VARIABLE_VOLUME)
                            && route.getVolume() == targetVolume) {
                        changedLatch.countDown();
                        break;
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mSystemRouter2ForCts.setRouteVolume(routeToChangeVolume, targetVolume);
        assertThat(changedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void setRouteVolume_withSpecialRoute_rejectsRequest() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        MediaRoute2Info routeToChangeVolume = null;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getAllRoutes()) {
            if (TextUtils.equals(ROUTE_ID6_REJECT_SET_VOLUME, route.getOriginalId())) {
                routeToChangeVolume = route;
                break;
            }
        }
        assertThat(routeToChangeVolume).isNotNull();

        int targetVolume = routeToChangeVolume.getVolume() + 1;
        CountDownLatch changedLatch = new CountDownLatch(1);
        int expectedFailureReason = REASON_REJECTED;
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onRequestFailed(int reason) {
                if (reason == expectedFailureReason) {
                    changedLatch.countDown();
                }
            }
        };

        mTransferCallbacks.add(transferCallback);
        mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);

        mSystemRouter2ForCts.setRouteVolume(routeToChangeVolume, targetVolume);
        assertThat(changedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        // Assert the route volume did not change.
        MediaRoute2Info updatedTargetRoute = mSystemRouter2ForCts.getAllRoutes().stream()
                .filter(r -> TextUtils.equals(r.getOriginalId(), ROUTE_ID6_REJECT_SET_VOLUME))
                .findAny()
                .orElse(null);
        assertThat(updatedTargetRoute).isNotNull();
        assertThat(updatedTargetRoute.getVolume()).isEqualTo(routeToChangeVolume.getVolume());
    }

    @Test
    public void volumeChange_callsOnRoutesUpdated() throws Exception {
        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(FEATURES_ALL, true).build());

        waitAndGetRoutes(FEATURE_SAMPLE);

        MediaRoute2Info routeToChangeVolume = null;
        for (MediaRoute2Info route : mSystemRouter2ForCts.getAllRoutes()) {
            if (TextUtils.equals(ROUTE_ID_VARIABLE_VOLUME, route.getOriginalId())) {
                routeToChangeVolume = route;
                break;
            }
        }
        assertThat(routeToChangeVolume).isNotNull();

        int targetVolume = routeToChangeVolume.getVolume() + 1;
        CountDownLatch changedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getOriginalId().equals(ROUTE_ID_VARIABLE_VOLUME)
                            && route.getVolume() == targetVolume) {
                        changedLatch.countDown();
                        break;
                    }
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mSystemRouter2ForCts.setRouteVolume(routeToChangeVolume, targetVolume);
        assertThat(changedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }


    @Test
    public void testRouteCallbackOnRoutesChanged_whenLocalVolumeChanged() throws Exception {
        if (mAudioManager.isVolumeFixed() || mAudioManager.isFullVolumeDevice()) {
            return;
        }

        waitAndGetRoutes(FEATURE_LIVE_AUDIO);

        final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int minVolume = mAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        MediaRoute2Info selectedSystemRoute =
                mSystemRouter2ForCts.getSystemController().getSelectedRoutes().get(0);

        assertThat(selectedSystemRoute.getVolumeMax()).isEqualTo(maxVolume);
        assertThat(selectedSystemRoute.getVolume()).isEqualTo(originalVolume);
        assertThat(selectedSystemRoute.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);

        final int targetVolume = originalVolume == minVolume
                ? originalVolume + 1 : originalVolume - 1;
        final CountDownLatch latch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesChanged(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getId().equals(selectedSystemRoute.getId())
                            && route.getVolume() == targetVolume) {
                        latch.countDown();
                        break;
                    }
                }
            }
        };

        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        try {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mSystemRouter2ForCts.unregisterRouteCallback(routeCallback);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        }
    }

    @Test
    public void adjustSelectedRouteVolume_invokesOnControllerUpdated() throws Exception {
        if (mAudioManager.isVolumeFixed() || mAudioManager.isFullVolumeDevice()) {
            return;
        }

        waitAndGetRoutes(FEATURE_LIVE_AUDIO);

        final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int minVolume = mAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        MediaRoute2Info selectedSystemRoute =
                mSystemRouter2ForCts.getSystemController().getSelectedRoutes().get(0);

        assertThat(selectedSystemRoute.getVolumeMax()).isEqualTo(maxVolume);
        assertThat(selectedSystemRoute.getVolume()).isEqualTo(originalVolume);
        assertThat(selectedSystemRoute.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);

        final int targetVolume =
                originalVolume == minVolume ? originalVolume + 1 : originalVolume - 1;
        final CountDownLatch latch = new CountDownLatch(1);
        ControllerCallback controllerCallback =
                new ControllerCallback() {
                    @Override
                    public void onControllerUpdated(@NonNull RoutingController controller) {
                        if (!TextUtils.equals(
                                controller.getId(),
                                mSystemRouter2ForCts.getSystemController().getId())) {
                            return;
                        }
                        MediaRoute2Info controllerSelectedRoute =
                                controller.getSelectedRoutes().get(0);

                        if (controllerSelectedRoute.getVolume() == targetVolume) {
                            latch.countDown();
                        }
                    }
                };

        mSystemRouter2ForCts.registerControllerCallback(mExecutor, controllerCallback);

        try {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mSystemRouter2ForCts.unregisterControllerCallback(controllerCallback);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        }
    }

    @Test
    public void testRouteCallbackOnPreferredFeaturesChanged() throws Exception {
        String testFeature = "testFeature";
        List<String> testFeatures = new ArrayList<>();
        testFeatures.add(testFeature);

        CountDownLatch featuresChangedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onPreferredFeaturesChanged(List<String> preferredFeatures) {
                if (preferredFeatures.contains(testFeature)) {
                    featuresChangedLatch.countDown();
                }
            }
        };
        mRouteCallbacks.add(routeCallback);
        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(testFeatures, true).build());
        assertThat(featuresChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testTransferTo_succeeds_onTransferCalled() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mSystemRouter2ForCts.getSystemController());
                assertThat(createRouteMap(newController.getSelectedRoutes()).containsKey(
                        ROUTE_ID1)).isTrue();
                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            List<RoutingController> controllersFromGetControllers =
                    mSystemRouter2ForCts.getControllers();
            assertThat(controllersFromGetControllers).hasSize(2);
            assertThat(createRouteMap(controllersFromGetControllers.get(1).getSelectedRoutes()))
                    .containsKey(ROUTE_ID1);

            // onSessionCreationFailed should not be called.
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void getSelectedRoutes_returnsNonMatchingFeatureRoutes() throws Exception {
        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SPECIAL);
        MediaRoute2Info route = routes.get(ROUTE_ID7_STATIC_GROUP);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();
        TransferCallback transferCallback =
                new TransferCallback() {
                    @Override
                    public void onTransfer(
                            RoutingController oldController, RoutingController newController) {
                        if (newController != null) {
                            controllers.add(newController);
                            successLatch.countDown();
                        }
                    }
                };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            RoutingController newController = controllers.get(0);
            assertThat(newController.getSelectedRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactlyElementsIn(STATIC_GROUP_SELECTED_ROUTES_IDS);

            // Make sure ROUTE_ID1 does not match FEATURE_SPECIAL.
            assertThat(mAppRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .doesNotContain(ROUTE_ID1);

        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void getSelectableRoutes_returnsNonMatchingFeatureRoutes() throws Exception {
        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SPECIAL);
        MediaRoute2Info route = routes.get(ROUTE_ID7_STATIC_GROUP);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();
        TransferCallback transferCallback =
                new TransferCallback() {
                    @Override
                    public void onTransfer(
                            RoutingController oldController, RoutingController newController) {
                        if (newController != null) {
                            controllers.add(newController);
                            successLatch.countDown();
                        }
                    }
                };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            RoutingController newController = controllers.get(0);
            assertThat(newController.getSelectableRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactly(ROUTE_ID4_TO_SELECT_AND_DESELECT);

            // Make sure ROUTE_ID4_TO_SELECT_AND_DESELECT does not match FEATURE_SPECIAL.
            assertThat(mAppRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .doesNotContain(ROUTE_ID4_TO_SELECT_AND_DESELECT);

        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void getDeselectableRoutes_returnsNonMatchingFeatureRoutes() throws Exception {
        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SPECIAL);
        MediaRoute2Info route = routes.get(ROUTE_ID7_STATIC_GROUP);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();
        TransferCallback transferCallback =
                new TransferCallback() {
                    @Override
                    public void onTransfer(
                            RoutingController oldController, RoutingController newController) {
                        if (newController != null) {
                            controllers.add(newController);
                            successLatch.countDown();
                        }
                    }
                };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            RoutingController newController = controllers.get(0);
            assertThat(newController.getDeselectableRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactly(ROUTE_ID1);

            // Make sure ROUTE_ID1 does not match FEATURE_SPECIAL.
            assertThat(mAppRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .doesNotContain(ROUTE_ID1);

        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testTransferTo_fails_onTransferFailureCalled() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID3_SESSION_CREATION_FAILED);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                assertThat(requestedRoute).isEqualTo(route);
                failureLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(failureLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // onTransfer should not be called.
            assertThat(successLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testTransferToTwice() throws Exception {
        final CountDownLatch successLatch1 = new CountDownLatch(1);
        final CountDownLatch successLatch2 = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final CountDownLatch stopLatch = new CountDownLatch(1);
        final CountDownLatch onReleaseSessionLatch = new CountDownLatch(1);

        final List<RoutingController> createdControllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                createdControllers.add(newController);
                if (successLatch1.getCount() > 0) {
                    successLatch1.countDown();
                } else {
                    successLatch2.countDown();
                }
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }

            @Override
            public void onStop(RoutingController controller) {
                stopLatch.countDown();
            }
        };

        StubMediaRoute2ProviderService service = mService;
        if (service != null) {
            service.setProxy(new StubMediaRoute2ProviderService.Proxy() {
                @Override
                public void onReleaseSession(long requestId, String sessionId) {
                    onReleaseSessionLatch.countDown();
                }
            });
        }

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route1 = routes.get(ROUTE_ID1);
        MediaRoute2Info route2 = routes.get(ROUTE_ID2);
        assertThat(route1).isNotNull();
        assertThat(route2).isNotNull();

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route1);
            assertThat(successLatch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            mSystemRouter2ForCts.transferTo(route2);
            assertThat(successLatch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // onTransferFailure/onStop should not be called.
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(stopLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();

            // Created controllers should have proper info
            assertThat(createdControllers).hasSize(2);
            RoutingController controller1 = createdControllers.get(0);
            RoutingController controller2 = createdControllers.get(1);

            assertThat(controller2.getId()).isNotEqualTo(controller1.getId());
            assertThat(createRouteMap(controller1.getSelectedRoutes()).containsKey(
                    ROUTE_ID1)).isTrue();
            assertThat(createRouteMap(controller2.getSelectedRoutes()).containsKey(
                    ROUTE_ID2)).isTrue();

            // Should be able to release transferred controllers.
            controller1.release();
            assertThat(onReleaseSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(createdControllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    // Same test with testTransferTo_succeeds_onTransferCalled,
    // but with MediaRouter2#transfer(controller, route) instead of transferTo(route).
    @Test
    public void testTransfer_succeeds_onTransferCalled() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mSystemRouter2ForCts.getSystemController());
                assertThat(createRouteMap(newController.getSelectedRoutes())
                        .containsKey(ROUTE_ID1)).isTrue();
                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transfer(mSystemRouter2ForCts.getSystemController(), route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            List<RoutingController> controllersFromGetControllers =
                    mSystemRouter2ForCts.getControllers();
            assertThat(controllersFromGetControllers).hasSize(2);
            assertThat(createRouteMap(controllersFromGetControllers.get(1).getSelectedRoutes())
                    .containsKey(ROUTE_ID1)).isTrue();

            // onSessionCreationFailed should not be called.
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testStop() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        final CountDownLatch onTransferLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final CountDownLatch onStopLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mSystemRouter2ForCts.getSystemController());
                assertThat(createRouteMap(newController.getSelectedRoutes())
                        .containsKey(ROUTE_ID1)).isTrue();
                controllers.add(newController);
                onTransferLatch.countDown();
            }
            @Override
            public void onStop(RoutingController controller) {
                if (onTransferLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }
                onStopLatch.countDown();
            }
        };

        ControllerCallback controllerCallback =
                new ControllerCallback() {
                    @Override
                    public void onControllerUpdated(RoutingController controller) {
                        if (onTransferLatch.getCount() != 0
                                || !TextUtils.equals(
                                        controllers.get(0).getId(), controller.getId())) {
                            return;
                        }
                        if (!TextUtils.equals(
                                controller.getSelectedRoutes().get(0).getOriginalId(),
                                route.getOriginalId())) {
                            onControllerUpdatedLatch.countDown();
                        }
                    }
                };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.registerControllerCallback(mExecutor, controllerCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);

            mSystemRouter2ForCts.stop();

            // Select ROUTE_ID4_TO_SELECT_AND_DESELECT
            MediaRoute2Info routeToSelect = routes.get(ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertThat(routeToSelect).isNotNull();

            // This call should be ignored.
            // The onControllerUpdated() shouldn't be called.
            controller.selectRoute(routeToSelect);
            assertThat(onControllerUpdatedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();

            // onStop should be called.
            assertThat(onStopLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterControllerCallback(controllerCallback);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testRoutingControllerSelectAndDeselectRoute() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info routeToBegin = routes.get(ROUTE_ID1);
        assertThat(routeToBegin).isNotNull();

        final CountDownLatch onTransferLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatchForSelect = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatchForDeselect = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with ROUTE_ID1
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mSystemRouter2ForCts.getSystemController());
                assertThat(createRouteMap(newController.getSelectedRoutes())
                        .containsKey(ROUTE_ID1)).isTrue();
                controllers.add(newController);
                onTransferLatch.countDown();
            }
        };

        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(RoutingController controller) {
                if (onTransferLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }

                if (onControllerUpdatedLatchForSelect.getCount() != 0) {
                    assertThat(controller.getSelectedRoutes()).hasSize(2);
                    assertThat(createRouteMap(controller.getSelectedRoutes())
                            .containsKey(ROUTE_ID1)).isTrue();
                    assertThat(createRouteMap(controller.getSelectedRoutes())
                            .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isTrue();
                    assertThat(createRouteMap(controller.getSelectableRoutes())
                            .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isFalse();
                    assertThat(createRouteMap(controller.getDeselectableRoutes())
                            .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isTrue();

                    controllers.add(controller);
                    onControllerUpdatedLatchForSelect.countDown();
                } else {
                    assertThat(controller.getSelectedRoutes()).hasSize(1);
                    assertThat(createRouteMap(controller.getSelectedRoutes())
                            .containsKey(ROUTE_ID1)).isTrue();
                    assertThat(createRouteMap(controller.getSelectedRoutes())
                            .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isFalse();
                    assertThat(createRouteMap(controller.getSelectableRoutes())
                            .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isTrue();
                    assertThat(createRouteMap(controller.getDeselectableRoutes())
                            .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isFalse();

                    onControllerUpdatedLatchForDeselect.countDown();
                }
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.registerControllerCallback(mExecutor, controllerCallback);
            mSystemRouter2ForCts.transferTo(routeToBegin);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);
            assertThat(createRouteMap(controller.getSelectableRoutes())
                    .containsKey(ROUTE_ID4_TO_SELECT_AND_DESELECT)).isTrue();

            // Select ROUTE_ID4_TO_SELECT_AND_DESELECT
            MediaRoute2Info routeToSelectAndDeselect = routes.get(
                    ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertThat(routeToSelectAndDeselect).isNotNull();

            controller.selectRoute(routeToSelectAndDeselect);
            assertThat(onControllerUpdatedLatchForSelect.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue();

            // Note that the updated controller is a different instance.
            assertThat(controllers).hasSize(2);
            assertThat(controllers.get(0).getId()).isEqualTo(controllers.get(1).getId());
            RoutingController updatedController = controllers.get(1);
            updatedController.deselectRoute(routeToSelectAndDeselect);
            assertThat(onControllerUpdatedLatchForDeselect.await(
                    TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
            mSystemRouter2ForCts.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testRoutingControllerTransferToRoute() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info routeToBegin = routes.get(ROUTE_ID1);
        assertThat(routeToBegin).isNotNull();

        final CountDownLatch onTransferLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with ROUTE_ID1
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mSystemRouter2ForCts.getSystemController());
                assertThat(createRouteMap(newController.getSelectedRoutes())
                        .containsKey(ROUTE_ID1)).isTrue();
                controllers.add(newController);
                onTransferLatch.countDown();
            }
        };

        ControllerCallback controllerCallback =
                new ControllerCallback() {
                    @Override
                    public void onControllerUpdated(RoutingController controller) {
                        if (onTransferLatch.getCount() != 0
                                || !TextUtils.equals(
                                        controllers.get(0).getId(), controller.getId())) {
                            return;
                        }
                        if (createRouteMap(controller.getSelectedRoutes())
                                .containsKey(ROUTE_ID5_TO_TRANSFER_TO)) {
                            assertThat(controller.getSelectedRoutes()).hasSize(1);
                            assertThat(
                                            createRouteMap(controller.getSelectedRoutes())
                                                    .containsKey(ROUTE_ID1))
                                    .isFalse();
                            onControllerUpdatedLatch.countDown();
                        }
                    }
                };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.registerControllerCallback(mExecutor, controllerCallback);
            mSystemRouter2ForCts.transferTo(routeToBegin);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);

            // Transfer to ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToTransferTo = routes.get(ROUTE_ID5_TO_TRANSFER_TO);
            assertThat(routeToTransferTo).isNotNull();

            mSystemRouter2ForCts.transferTo(routeToTransferTo);
            assertThat(onControllerUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterControllerCallback(controllerCallback);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testRoutingControllerSetSessionVolume() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        CountDownLatch successLatch = new CountDownLatch(1);
        CountDownLatch volumeChangedLatch = new CountDownLatch(1);

        List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                controllers.add(newController);
                successLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.transferTo(route);

            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }

        assertThat(controllers).hasSize(1);

        // test setSessionVolume
        RoutingController targetController = controllers.get(0);
        assertThat(targetController.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);
        int currentVolume = targetController.getVolume();
        int maxVolume = targetController.getVolumeMax();
        int targetVolume = (currentVolume == maxVolume) ? currentVolume - 1 : (currentVolume + 1);

        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(MediaRouter2.RoutingController controller) {
                if (!TextUtils.equals(targetController.getId(), controller.getId())) {
                    return;
                }
                if (controller.getVolume() == targetVolume) {
                    volumeChangedLatch.countDown();
                }
            }
        };

        try {
            mSystemRouter2ForCts.registerControllerCallback(mExecutor, controllerCallback);
            targetController.setVolume(targetVolume);
            assertThat(volumeChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES)
    public void testBuiltinSpeakerSuitabilityStatusReflectsXmlConfigValue() {
        int builtInSpeakerSuitabilityResourceId =
                Resources.getSystem()
                        .getIdentifier(
                                "config_mediaRouter_builtInSpeakerSuitability",
                                "integer",
                                /* defPackage= */ "android");
        int builtInSpeakerSuitability =
                Resources.getSystem().getInteger(builtInSpeakerSuitabilityResourceId);

        List<MediaRoute2Info> selectedRoutes = mAppRouter2.getSystemController()
                .getSelectedRoutes();

        assertThat(selectedRoutes).isNotEmpty();

        MediaRoute2Info deviceRoute = selectedRoutes.get(0);
        assertThat(deviceRoute.getType()).isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        assertThat(deviceRoute.getSuitabilityStatus()).isEqualTo(builtInSpeakerSuitability);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES)
    public void testLocalRouterTransferChangesTransferReasonToTransferredFromApp()
            throws InterruptedException {
        clearPossibleTransferReason();

        CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(RoutingController controller) {
                onControllerUpdatedLatch.countDown();
            }
        };

        mAppRouter2.registerControllerCallback(mExecutor, controllerCallback);

        List<MediaRoute2Info> selectedRoutes = mAppRouter2.getSystemController()
                .getSelectedRoutes();
        assertThat(selectedRoutes).isNotEmpty();
        MediaRoute2Info deviceRoute = selectedRoutes.get(0);

        mAppRouter2.transferTo(deviceRoute);
        assertThat(onControllerUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        mAppRouter2.unregisterControllerCallback(controllerCallback);

        RoutingController controller = mAppRouter2.getSystemController();
        RoutingSessionInfo currentSessionInfo = controller.getRoutingSessionInfo();

        assertThat(controller.wasTransferInitiatedBySelf()).isTrue();
        assertThat(currentSessionInfo.getTransferReason()).isEqualTo(
                RoutingSessionInfo.TRANSFER_REASON_APP);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES)
    public void testProxyRouterTransferChangesTransferReasonToTransferredAsSystemRequest()
            throws InterruptedException {
        clearPossibleTransferReason();

        CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(RoutingController controller) {
                onControllerUpdatedLatch.countDown();
            }
        };

        mAppRouter2.registerControllerCallback(mExecutor, controllerCallback);

        List<MediaRoute2Info> selectedRoutes = mAppRouter2.getSystemController()
                .getSelectedRoutes();
        assertThat(selectedRoutes).isNotEmpty();
        MediaRoute2Info deviceRoute = selectedRoutes.get(0);

        mSystemRouter2ForCts.transferTo(deviceRoute);
        assertThat(onControllerUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        mAppRouter2.unregisterControllerCallback(controllerCallback);

        RoutingController controller = mAppRouter2.getSystemController();
        RoutingSessionInfo currentSessionInfo = controller.getRoutingSessionInfo();

        assertThat(controller.wasTransferInitiatedBySelf()).isTrue();
        assertThat(currentSessionInfo.getTransferReason()).isEqualTo(
                RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST);
    }

    /**
     * To be able to receive {@link ControllerCallback#onControllerUpdated(RoutingController)} call
     * we need to leave the system in some random state in which we, unlikely, end up in the tests.
     * For this reason, we need to transfer the system in a random state.
     */
    private void clearPossibleTransferReason() throws InterruptedException {
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(RoutingController controller) {
                super.onControllerUpdated(controller);
                onControllerUpdatedLatch.countDown();
            }
        };

        mAppRouter2.registerControllerCallback(mExecutor, controllerCallback);

        RoutingController controller = mAppRouter2.getSystemController();
        List<MediaRoute2Info> selectedRoutes = controller.getSelectedRoutes();
        assertThat(selectedRoutes).isNotEmpty();
        MediaRoute2Info deviceRoute = selectedRoutes.get(0);

        mSystemRouter2ForCts.transfer(controller, deviceRoute, UserHandle.SYSTEM,
                "some_random_package_name");

        assertThat(onControllerUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        RoutingController routingController = mAppRouter2.getSystemController();
        RoutingSessionInfo sessionInfo = routingController.getRoutingSessionInfo();

        assertThat(sessionInfo.getTransferReason()).isEqualTo(
                RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST);
        assertThat(routingController.wasTransferInitiatedBySelf()).isFalse();

        mAppRouter2.unregisterControllerCallback(controllerCallback);
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testRoutingControllerRelease() throws Exception {
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURE_SAMPLE);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        final CountDownLatch onTransferLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final CountDownLatch onStopLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mSystemRouter2ForCts.getSystemController());
                assertThat(createRouteMap(newController.getSelectedRoutes())
                        .containsKey(ROUTE_ID1)).isTrue();
                controllers.add(newController);
                onTransferLatch.countDown();
            }
            @Override
            public void onStop(RoutingController controller) {
                if (onTransferLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }
                onStopLatch.countDown();
            }
        };

        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(RoutingController controller) {
                if (onTransferLatch.getCount() != 0
                        || !TextUtils.equals(controllers.get(0).getId(), controller.getId())) {
                    return;
                }
                onControllerUpdatedLatch.countDown();
            }
        };

        try {
            mSystemRouter2ForCts.registerTransferCallback(mExecutor, transferCallback);
            mSystemRouter2ForCts.registerControllerCallback(mExecutor, controllerCallback);
            mSystemRouter2ForCts.transferTo(route);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);

            // Release controller. Future calls should be ignored.
            controller.release();

            // Select ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToSelect = routes.get(ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertThat(routeToSelect).isNotNull();

            // This call should be ignored.
            // The onControllerUpdated() shouldn't be called.
            controller.selectRoute(routeToSelect);
            assertThat(onControllerUpdatedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();

            // onStop should be called.
            assertThat(onStopLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mSystemRouter2ForCts.unregisterControllerCallback(controllerCallback);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
    }

    private Map<String, MediaRoute2Info> waitAndGetRoutes(String feature) throws Exception {
        List<String> features = new ArrayList<>();
        features.add(feature);

        mAppRouter2.registerRouteCallback(mExecutor, mAppRouterPlaceHolderCallback,
                new RouteDiscoveryPreference.Builder(features, true).build());

        CountDownLatch latch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (route.getFeatures().contains(feature)) {
                        latch.countDown();
                        break;
                    }
                }
            }
        };

        mSystemRouter2ForCts.registerRouteCallback(mExecutor, routeCallback,
                RouteDiscoveryPreference.EMPTY);

        try {
            // Note: The routes can be added before registering the callback,
            // therefore no assertThat.isTrue() here.
            latch.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mSystemRouter2ForCts.getRoutes());
        } finally {
            mSystemRouter2ForCts.unregisterRouteCallback(routeCallback);
        }
    }

    // Helper for getting routes easily. Uses original ID as a key
    private static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            routeMap.put(route.getOriginalId(), route);
        }
        return routeMap;
    }

    private void releaseAllSessions() {
        MediaRouter2Manager manager = MediaRouter2Manager.getInstance(mContext);
        for (RoutingSessionInfo session : manager.getRemoteSessions()) {
            manager.releaseSession(session);
        }
    }

    private void clearCallbacks() {
        for (RouteCallback routeCallback : mRouteCallbacks) {
            mAppRouter2.unregisterRouteCallback(routeCallback);
            mSystemRouter2ForCts.unregisterRouteCallback(routeCallback);
        }
        mRouteCallbacks.clear();

        for (TransferCallback transferCallback : mTransferCallbacks) {
            mAppRouter2.unregisterTransferCallback(transferCallback);
            mSystemRouter2ForCts.unregisterTransferCallback(transferCallback);
        }
        mTransferCallbacks.clear();
    }

    static void releaseControllers(List<RoutingController> controllers) {
        for (RoutingController controller : controllers) {
            controller.release();
        }
    }
}
