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
import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURES_SPECIAL;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL_ROUTE_IDS;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID3_SESSION_CREATION_FAILED;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID4_TO_SELECT_AND_DESELECT;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID7_STATIC_GROUP;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.STATIC_GROUP_SELECTED_ROUTES_IDS;

import static androidx.test.ext.truth.os.BundleSubject.assertThat;

import static com.android.media.flags.Flags.FLAG_ENABLE_GET_TRANSFERABLE_ROUTES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ControllerCallback;
import android.media.MediaRouter2.OnGetControllerHintsListener;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2.TransferCallback;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.RoutingSessionInfo;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.PollingCheck;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
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
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to StubMediaRoute2ProviderService")
@LargeTest
@NonMainlineTest
public class MediaRouter2Test {
    private static final String TAG = "MR2Test";
    private static final String DEFAULT_ROUTE_ID = "DEFAULT_ROUTE";
    private static final int SAMPLE_CURRENT_VOLUME = 10;
    private static final int SAMPLE_MAX_VOLUME = 12;

    // Required by Bedstead.
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    Context mContext;
    private MediaRouter2 mRouter2;
    private Executor mExecutor;
    private AudioManager mAudioManager;
    private RouteCallback mRouterDummyCallback = new RouteCallback(){};
    private StubMediaRoute2ProviderService mService;

    private static final int TIMEOUT_MS = 5000;
    private static final int WAIT_MS = 2000;

    private static final String TEST_KEY = "test_key";
    private static final String TEST_VALUE = "test_value";
    private static final RouteDiscoveryPreference EMPTY_DISCOVERY_PREFERENCE =
            new RouteDiscoveryPreference.Builder(Collections.emptyList(), false).build();
    private static final RouteDiscoveryPreference LIVE_AUDIO_DISCOVERY_PREFERENCE =
            new RouteDiscoveryPreference.Builder(
                    Collections.singletonList(FEATURE_LIVE_AUDIO), false).build();
    private static final Correspondence<MediaRoute2Info, String> ROUTE_HAS_ORIGINAL_ID =
            Correspondence.transforming(MediaRoute2Info::getOriginalId, "has original id");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mAudioManager = (AudioManager) mContext.getSystemService(AUDIO_SERVICE);

        mRouter2 = MediaRouter2.getInstance(mContext);
        MediaRouter2TestActivity.startActivity(mContext);
    }

    private void setUpStubProvider() {
        // In order to make the system bind to the test service,
        // set a non-empty discovery preference while app is in foreground.
        List<String> features = new ArrayList<>();
        features.add("A test feature");
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, false).build();
        mRouter2.registerRouteCallback(mExecutor, mRouterDummyCallback, preference);

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
    public void tearDown() {
        mRouter2.unregisterRouteCallback(mRouterDummyCallback);
        // Clearing RouteListingPreference.
        mRouter2.setRouteListingPreference(null);
        MediaRouter2TestActivity.finishActivity();
        if (mService != null) {
            mService.clear();
            mService = null;
        }
    }

    @Test
    public void testMediaRouter2ReturnsRoutingSessionInfoWithNonEmptyClientName() {
        RoutingController routingController = mRouter2.getSystemController();
        RoutingSessionInfo sessionInfo = routingController.getRoutingSessionInfo();
        assertThat(sessionInfo.getClientPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void testGetRoutesAfterCreation() {
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, LIVE_AUDIO_DISCOVERY_PREFERENCE);
        try {
            List<MediaRoute2Info> initialRoutes = mRouter2.getRoutes();
            assertThat(initialRoutes.isEmpty()).isFalse();
            for (MediaRoute2Info route : initialRoutes) {
                assertThat(route.getFeatures().contains(FEATURE_LIVE_AUDIO)).isTrue();
            }
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    /**
     * Tests if we get proper routes for an application that requests a special route type.
     *
     * <p>Runs on both the primary user and a work profile, as per {@link UserTest}.
     */
    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @UserTest({UserType.PRIMARY_USER, UserType.WORK_PROFILE})
    @Test
    public void testGetRoutes() throws Exception {
        setUpStubProvider();

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(FEATURES_SPECIAL);
        List<MediaRoute2Info> nonSystemRoutes =
                routes.values().stream()
                        .filter(route -> !route.isSystemRoute())
                        .collect(Collectors.toList());

        assertThat(nonSystemRoutes)
                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                .containsExactlyElementsIn(FEATURE_SPECIAL_ROUTE_IDS);
    }

    @Test
    public void testRegisterTransferCallbackWithInvalidArguments() {
        Executor executor = mExecutor;
        TransferCallback callback = new TransferCallback() {};

        // Tests null executor
        assertThrows(NullPointerException.class,
                () -> mRouter2.registerTransferCallback(null, callback));

        // Tests null callback
        assertThrows(NullPointerException.class,
                () -> mRouter2.registerTransferCallback(executor, null));
    }

    @Test
    public void testUnregisterTransferCallbackWithNullCallback() {
        // Tests null callback
        assertThrows(NullPointerException.class,
                () -> mRouter2.unregisterTransferCallback(null));
    }

    @Test
    public void activeScanRouteDiscoveryPreference_scansOnSelfScanProvider() {
        RouteDiscoveryPreference activeScanRouteDiscoveryPreference =
                new RouteDiscoveryPreference.Builder(
                                List.of("placeholder_feature"), /* activeScan= */ true)
                        .build();
        RouteCallback routeCallback = new RouteCallback() {};
        ConditionVariable conditionVariable = new ConditionVariable();
        PlaceholderService.setOnBindCallback(
                action -> {
                    if (MediaRoute2ProviderService.SERVICE_INTERFACE.equals(action)) {
                        conditionVariable.open();
                    }
                });
        try {
            mRouter2.registerRouteCallback(
                    Runnable::run, routeCallback, activeScanRouteDiscoveryPreference);
            assertThat(conditionVariable.block(WAIT_MS)).isTrue();
        } finally {
            PlaceholderService.setOnBindCallback(action -> {});
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    @Test
    public void testTransferToSuccess() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback controllerCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mRouter2.getSystemController());
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

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, controllerCallback);
            mRouter2.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // onSessionCreationFailed should not be called.
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(controllerCallback);
        }
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testTransferToFailure() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
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

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.transferTo(route);
            assertThat(failureLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // onTransfer should not be called.
            assertThat(successLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testTransferToTwice() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

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

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info route1 = routes.get(ROUTE_ID1);
        MediaRoute2Info route2 = routes.get(ROUTE_ID2);
        assertThat(route1).isNotNull();
        assertThat(route2).isNotNull();

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.transferTo(route1);
            assertThat(successLatch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            mRouter2.transferTo(route2);
            assertThat(successLatch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // onTransferFailure/onStop should not be called.
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(stopLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();

            // Created controllers should have proper info
            assertThat(createdControllers).hasSize(2);
            RoutingController controller1 = createdControllers.get(0);
            RoutingController controller2 = createdControllers.get(1);

            assertThat(controller1.getId()).isNotEqualTo(controller2.getId());
            assertThat(createRouteMap(controller1.getSelectedRoutes()).containsKey(
                    ROUTE_ID1)).isTrue();
            assertThat(createRouteMap(controller2.getSelectedRoutes()).containsKey(
                    ROUTE_ID2)).isTrue();

            // Transferred controllers shouldn't be obtainable.
            assertThat(mRouter2.getControllers().contains(controller1)).isFalse();
            assertThat(mRouter2.getControllers().contains(controller2)).isTrue();

            // Should be able to release transferred controllers.
            controller1.release();
            assertThat(onReleaseSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(createdControllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testSetOnGetControllerHintsListener() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        final Bundle controllerHints = new Bundle();
        controllerHints.putString(TEST_KEY, TEST_VALUE);
        final OnGetControllerHintsListener listener = route1 -> controllerHints;

        final CountDownLatch successLatch = new CountDownLatch(1);
        final CountDownLatch failureLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        // Create session with this route
        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(createRouteMap(newController.getSelectedRoutes())
                        .containsKey(ROUTE_ID1)).isTrue();

                // The StubMediaRoute2ProviderService is supposed to set control hints
                // with the given controllerHints.
                Bundle controlHints = newController.getControlHints();
                assertThat(controlHints).isNotNull();
                assertThat(controlHints).containsKey(TEST_KEY);
                assertThat(controlHints).string(TEST_KEY).isEqualTo(TEST_VALUE);

                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);

            // The StubMediaRoute2ProviderService supposed to set control hints
            // with the given creationSessionHints.
            mRouter2.setOnGetControllerHintsListener(listener);
            mRouter2.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // onSessionCreationFailed should not be called.
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testSetSessionVolume() throws Exception {
        setUpStubProvider();

        List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
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

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};

        try {
            mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.transferTo(route);

            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterRouteCallback(routeCallback);
        }

        assertThat(controllers).hasSize(1);
        // test requestSetSessionVolume

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
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
            targetController.setVolume(targetVolume);
            assertThat(volumeChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @Test
    public void testVolumeAdjustmentEventsReachOnlyExistingActiveRemoteSession() throws Exception {
        // TODO (b/308994839): Remove this once MediaRouter2 supports multiuser interaction.
        // Change MediaSessionRecord check from MediaRouter2Manager to MediaRouter2.
        assumeFalse("Shouldn't run in HSUM mode", UserManager.isHeadlessSystemUserMode());

        setUpStubProvider();

        List<String> sampleRouteFeature = new ArrayList<>();
        sampleRouteFeature.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteFeature);
        MediaRoute2Info route = routes.get(ROUTE_ID1);
        assertThat(route).isNotNull();

        CountDownLatch onTransferLatch = new CountDownLatch(1);
        List<RoutingController> controllers = new ArrayList<>();

        TransferCallback controllerCallback =
                new TransferCallback() {
                    @Override
                    public void onTransfer(
                            RoutingController oldController, RoutingController newController) {
                        assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                        assertThat(
                                        createRouteMap(newController.getSelectedRoutes())
                                                .containsKey(ROUTE_ID1))
                                .isTrue();
                        controllers.add(newController);
                        onTransferLatch.countDown();
                    }
                };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
        Handler handler = new Handler(Looper.getMainLooper());

        try {
            mRouter2.registerTransferCallback(mExecutor, controllerCallback);

            // This call is needed to create a routing session with the same package name as the
            // media session so that volume key events are sent to mSession. Some devices block
            // volume messages remote media sessions with no associated routing session.
            // See b/228021646 for details.
            mRouter2.transferTo(route);

            // Make sure the transfer succeeded.
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // Define the media session
            MediaSession mSession = new MediaSession(mContext, /* tag= */ "sample_session");

            CountDownLatch volumeAdjustedLatch = new CountDownLatch(1);
            VolumeProvider volumeProvider =
                    new VolumeProvider(
                            VolumeProvider.VOLUME_CONTROL_RELATIVE,
                            SAMPLE_MAX_VOLUME,
                            SAMPLE_CURRENT_VOLUME,
                            /* volumeControlId= */ "sample_volume_control_id") {
                        @Override
                        public void onAdjustVolume(int direction) {
                            super.onAdjustVolume(direction);
                            setCurrentVolume(getCurrentVolume() + direction);
                            volumeAdjustedLatch.countDown();
                        }

                        @Override
                        public void onSetVolumeTo(int volume) {
                            super.onSetVolumeTo(volume);
                            setCurrentVolume(volume);
                            volumeAdjustedLatch.countDown();
                        }
                    };
            mSession.setPlaybackToRemote(volumeProvider);

            assertThat(volumeProvider.getCurrentVolume()).isEqualTo(SAMPLE_CURRENT_VOLUME);

            PlaybackState playbackState =
                    new PlaybackState.Builder()
                            .setState(
                                    PlaybackState.STATE_PLAYING,
                                    /* position= */ 0L,
                                    /* playbackSpeed= */ 0.0f)
                            .build();
            mSession.setActive(true);
            mSession.setPlaybackState(playbackState);

            // Set a callback for volume provider to receive the volume updates.
            mSession.setCallback(new MediaSession.Callback() {}, handler);

            long downTime = System.currentTimeMillis();

            // Simulate the volume key event
            MediaSessionManager mSessionManager =
                    mContext.getSystemService(MediaSessionManager.class);
            mSessionManager.dispatchVolumeKeyEvent(
                    new KeyEvent(
                            downTime,
                            downTime,
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_VOLUME_DOWN,
                            0),
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    false);
            mSessionManager.dispatchVolumeKeyEvent(
                    new KeyEvent(
                            downTime,
                            System.currentTimeMillis(),
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_VOLUME_DOWN,
                            0),
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    false);

            assertThat(volumeAdjustedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(volumeProvider.getCurrentVolume()).isEqualTo(SAMPLE_CURRENT_VOLUME - 1);
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(controllerCallback);
        }
    }

    @Test
    public void testTransferCallbackIsNotCalledAfterUnregistered() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
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
                controllers.add(newController);
                successLatch.countDown();
            }

            @Override
            public void onTransferFailure(MediaRoute2Info requestedRoute) {
                failureLatch.countDown();
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            // Unregisters transfer callback
            mRouter2.unregisterTransferCallback(transferCallback);

            mRouter2.transferTo(route);

            // No transfer callback methods should be called.
            assertThat(successLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(failureLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    // TODO: Add tests for illegal inputs if needed (e.g. selecting already selected route)
    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testRoutingControllerSelectAndDeselectRoute() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
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
                assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                assertThat(getOriginalRouteIds(newController.getSelectedRoutes()).contains(
                        ROUTE_ID1)).isTrue();
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
                    assertThat(getOriginalRouteIds(controller.getSelectedRoutes()))
                            .contains(ROUTE_ID1);
                    assertThat(getOriginalRouteIds(controller.getSelectedRoutes()))
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT);
                    assertThat(getOriginalRouteIds(controller.getSelectableRoutes()))
                            .doesNotContain(ROUTE_ID4_TO_SELECT_AND_DESELECT);
                    assertThat(getOriginalRouteIds(controller.getDeselectableRoutes()))
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT);

                    onControllerUpdatedLatchForSelect.countDown();
                } else {
                    assertThat(controller.getSelectedRoutes()).hasSize(1);
                    assertThat(getOriginalRouteIds(controller.getSelectedRoutes()))
                            .contains(ROUTE_ID1);
                    assertThat(getOriginalRouteIds(controller.getSelectedRoutes()))
                            .doesNotContain(ROUTE_ID4_TO_SELECT_AND_DESELECT);
                    assertThat(getOriginalRouteIds(controller.getSelectableRoutes()))
                            .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT);
                    assertThat(getOriginalRouteIds(controller.getDeselectableRoutes()))
                            .doesNotContain(ROUTE_ID4_TO_SELECT_AND_DESELECT);

                    onControllerUpdatedLatchForDeselect.countDown();
                }
            }
        };


        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.transferTo(routeToBegin);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);
            assertThat(getOriginalRouteIds(controller.getSelectableRoutes()))
                    .contains(ROUTE_ID4_TO_SELECT_AND_DESELECT);

            // Select ROUTE_ID4_TO_SELECT_AND_DESELECT
            MediaRoute2Info routeToSelectAndDeselect = routes.get(
                    ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertThat(routeToSelectAndDeselect).isNotNull();

            controller.selectRoute(routeToSelectAndDeselect);
            assertThat(onControllerUpdatedLatchForSelect.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue();

            controller.deselectRoute(routeToSelectAndDeselect);
            assertThat(onControllerUpdatedLatchForDeselect.await(
                    TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
        }
    }

    @RequiresFlagsEnabled(FLAG_ENABLE_GET_TRANSFERABLE_ROUTES)
    @ApiTest(apis = "android.media.MediaRouter2.RoutingController#getTransferableRoutes")
    @Test
    public void routingController_getTransferableRoutes_returnsNonFeatureMatchingRoutes()
            throws Exception {
        setUpStubProvider();

        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(List.of(FEATURE_SPECIAL));
        MediaRoute2Info route = routes.get(ROUTE_ID7_STATIC_GROUP);
        assertThat(route).isNotNull();

        final CountDownLatch successLatch = new CountDownLatch(1);
        TransferCallback transferCallback =
                new TransferCallback() {
                    @Override
                    public void onTransfer(
                            @NonNull RoutingController oldController,
                            @NonNull RoutingController newController) {
                        assertThat(newController.getTransferableRoutes())
                                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                                .containsExactly(ROUTE_ID5_TO_TRANSFER_TO);

                        // Make sure ROUTE_ID5_TO_TRANSFER_TO does not match FEATURE_SPECIAL.
                        assertThat(mRouter2.getRoutes())
                                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                                .doesNotContain(ROUTE_ID5_TO_TRANSFER_TO);

                        successLatch.countDown();
                    }
                };
        // We need to keep this callback to unregister the RouteCallback.
        RouteCallback placeholderRouteCallback = new RouteCallback() {};

        try {
            RouteDiscoveryPreference preference =
                    new RouteDiscoveryPreference.Builder(
                                    List.of(FEATURE_SPECIAL), /* isActiveScan */ true)
                            .build();
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerRouteCallback(mExecutor, placeholderRouteCallback, preference);
            mRouter2.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.stop();
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterRouteCallback(placeholderRouteCallback);
        }
    }

    @Test
    public void routingController_getSelectedRoutes_returnsNonFeatureMatchingRoutes()
            throws Exception {
        setUpStubProvider();

        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(List.of(FEATURE_SPECIAL));
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
        RouteCallback placeholderRouteCallback = new RouteCallback() {};

        try {
            RouteDiscoveryPreference preference =
                    new RouteDiscoveryPreference.Builder(List.of(FEATURE_SPECIAL), true).build();
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerRouteCallback(mExecutor, placeholderRouteCallback, preference);
            mRouter2.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            RoutingController newController = controllers.get(0);
            assertThat(newController.getSelectedRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactlyElementsIn(STATIC_GROUP_SELECTED_ROUTES_IDS);

            // Make sure ROUTE_ID1 does not match FEATURE_SPECIAL.
            assertThat(mRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .doesNotContain(ROUTE_ID1);

        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterRouteCallback(placeholderRouteCallback);
        }
    }

    @Test
    public void routingController_getSelectableRoutes_returnsNonFeatureMatchingRoutes()
            throws Exception {
        setUpStubProvider();

        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(List.of(FEATURE_SPECIAL));
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
        RouteCallback placeholderRouteCallback = new RouteCallback() {};

        try {
            RouteDiscoveryPreference preference =
                    new RouteDiscoveryPreference.Builder(List.of(FEATURE_SPECIAL), true).build();
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerRouteCallback(mExecutor, placeholderRouteCallback, preference);
            mRouter2.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            RoutingController newController = controllers.get(0);
            assertThat(newController.getSelectableRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactly(ROUTE_ID4_TO_SELECT_AND_DESELECT);

            // Make sure ROUTE_ID4_TO_SELECT_AND_DESELECT does not match FEATURE_SPECIAL.
            assertThat(mRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .doesNotContain(ROUTE_ID4_TO_SELECT_AND_DESELECT);

        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterRouteCallback(placeholderRouteCallback);
        }
    }

    @Test
    public void routingController_getDeselectableRoutes_returnsNonFeatureMatchingRoutes()
            throws Exception {
        setUpStubProvider();

        // Set discovery preference to FEATURE_SPECIAL.
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(List.of(FEATURE_SPECIAL));
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
        RouteCallback placeholderRouteCallback = new RouteCallback() {};

        try {
            RouteDiscoveryPreference preference =
                    new RouteDiscoveryPreference.Builder(List.of(FEATURE_SPECIAL), true).build();
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerRouteCallback(mExecutor, placeholderRouteCallback, preference);
            mRouter2.transferTo(route);
            assertThat(successLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            RoutingController newController = controllers.get(0);
            assertThat(newController.getDeselectableRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .containsExactly(ROUTE_ID1);

            // Make sure ROUTE_ID1 does not match FEATURE_SPECIAL.
            assertThat(mRouter2.getRoutes())
                    .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                    .doesNotContain(ROUTE_ID1);

        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterRouteCallback(placeholderRouteCallback);
        }
    }

    @Test
    public void testRoutingControllerTransferToRoute() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
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
                assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                assertThat(getOriginalRouteIds(newController.getSelectedRoutes()).contains(
                        ROUTE_ID1)).isTrue();
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

                        if (getOriginalRouteIds(controller.getSelectedRoutes())
                                .contains(ROUTE_ID5_TO_TRANSFER_TO)) {
                            assertThat(controller.getSelectedRoutes()).hasSize(1);
                            assertThat(getOriginalRouteIds(controller.getSelectedRoutes()))
                                    .doesNotContain(ROUTE_ID1);
                            onControllerUpdatedLatch.countDown();
                        }
                    }
                };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.transferTo(routeToBegin);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);

            // Transfer to ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToTransferTo = routes.get(ROUTE_ID5_TO_TRANSFER_TO);
            assertThat(routeToTransferTo).isNotNull();

            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.transferTo(routeToTransferTo);
            assertThat(onControllerUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    @Test
    public void testControllerCallbackUnregister() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
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
                assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                assertThat(getOriginalRouteIds(newController.getSelectedRoutes()).contains(
                        ROUTE_ID1)).isTrue();
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
                                        mRouter2.getSystemController().getId(),
                                        controller.getId())) {
                            return;
                        }

                        if (getOriginalRouteIds(controller.getSelectedRoutes())
                                .contains(ROUTE_ID5_TO_TRANSFER_TO)) {
                            assertThat(controller.getSelectedRoutes()).hasSize(1);
                            assertThat(getOriginalRouteIds(controller.getSelectedRoutes()))
                                    .doesNotContain(ROUTE_ID1);
                            onControllerUpdatedLatch.countDown();
                        }
                    }
                };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.transferTo(routeToBegin);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);

            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            // Transfer to ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToTransferTo = routes.get(ROUTE_ID5_TO_TRANSFER_TO);
            assertThat(routeToTransferTo).isNotNull();

            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.transferTo(routeToTransferTo);
            assertThat(onControllerUpdatedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    // TODO: Add tests for onStop() when provider releases the session.
    @Test
    public void testStop() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info routeTransferFrom = routes.get(ROUTE_ID1);
        assertThat(routeTransferFrom).isNotNull();

        final CountDownLatch onTransferLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final CountDownLatch onStopLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                assertThat(getOriginalRouteIds(newController.getSelectedRoutes()).contains(
                        ROUTE_ID1)).isTrue();
                controllers.add(newController);
                onTransferLatch.countDown();
            }
            @Override
            public void onStop(RoutingController controller) {
                if (onTransferLatch.getCount() != 0
                        || !TextUtils.equals(
                        controllers.get(0).getId(), controller.getId())) {
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

        // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.transferTo(routeTransferFrom);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);

            // Registering the callback here to avoid unrelated calls related to the transfer above.
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.stop();

            // Select ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToSelect = routes.get(ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertThat(routeToSelect).isNotNull();

            // This call should be ignored.
            // The onSessionInfoChanged() shouldn't be called.
            controller.selectRoute(routeToSelect);
            assertThat(onControllerUpdatedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();

            // onStop should be called.
            assertThat(onStopLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    @Ignore // TODO(b/291800179): Diagnose flakiness and re-enable.
    @Test
    public void testRoutingControllerRelease() throws Exception {
        setUpStubProvider();

        final List<String> sampleRouteType = new ArrayList<>();
        sampleRouteType.add(FEATURE_SAMPLE);

        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(sampleRouteType);
        MediaRoute2Info routeTransferFrom = routes.get(ROUTE_ID1);
        assertThat(routeTransferFrom).isNotNull();

        final CountDownLatch onTransferLatch = new CountDownLatch(1);
        final CountDownLatch onControllerUpdatedLatch = new CountDownLatch(1);
        final CountDownLatch onStopLatch = new CountDownLatch(1);
        final List<RoutingController> controllers = new ArrayList<>();

        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                assertThat(getOriginalRouteIds(newController.getSelectedRoutes()).contains(
                        ROUTE_ID1)).isTrue();
                controllers.add(newController);
                onTransferLatch.countDown();
            }
            @Override
            public void onStop(RoutingController controller) {
                if (onTransferLatch.getCount() != 0
                        || !TextUtils.equals(
                                controllers.get(0).getId(), controller.getId())) {
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

       // TODO: Remove this once the MediaRouter2 becomes always connected to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        try {
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);
            mRouter2.transferTo(routeTransferFrom);
            assertThat(onTransferLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(controllers).hasSize(1);
            RoutingController controller = controllers.get(0);

            // Release controller. Future calls should be ignored.
            controller.release();

            // Select ROUTE_ID5_TO_TRANSFER_TO
            MediaRoute2Info routeToSelect = routes.get(ROUTE_ID4_TO_SELECT_AND_DESELECT);
            assertThat(routeToSelect).isNotNull();

            // This call should be ignored.
            // The onSessionInfoChanged() shouldn't be called.
            controller.selectRoute(routeToSelect);
            assertThat(onControllerUpdatedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isFalse();

            // onStop should be called.
            assertThat(onStopLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseControllers(controllers);
            mRouter2.unregisterRouteCallback(routeCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
        }
    }

    // TODO: Consider adding tests with bluetooth connection/disconnection.
    @Test
    public void testGetSystemController() {
        final RoutingController systemController = mRouter2.getSystemController();
        assertThat(systemController).isNotNull();
        assertThat(systemController.isReleased()).isFalse();

        for (MediaRoute2Info route : systemController.getSelectedRoutes()) {
            assertThat(route.isSystemRoute()).isTrue();
        }
    }

    @Test
    public void getInstance_withoutSystemRoutingPermissions_fetchesOnlyDefaultSystemRoute() {
        // MR2 needs a LIVE_AUDIO discovery preference to not filter out system routes.
        mRouter2.registerRouteCallback(
                mExecutor, mRouterDummyCallback, LIVE_AUDIO_DISCOVERY_PREFERENCE);
        assertThat(mRouter2.getRoutes())
                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                .containsExactly(DEFAULT_ROUTE_ID);
    }

    @Test
    public void testGetControllers() {
        List<RoutingController> controllers = mRouter2.getControllers();
        assertThat(controllers).isNotNull();
        assertThat(controllers.isEmpty()).isFalse();
        assertThat(controllers.get(0)).isSameInstanceAs(mRouter2.getSystemController());
    }

    @Test
    public void testGetController() {
        String systemControllerId = mRouter2.getSystemController().getId();
        RoutingController controllerById = mRouter2.getController(systemControllerId);
        assertThat(controllerById).isNotNull();
        assertThat(controllerById.getId()).isEqualTo(systemControllerId);
    }

    @Test
    public void testVolumeHandlingWhenVolumeFixed() {
        if (!mAudioManager.isVolumeFixed()) {
            return;
        }
        MediaRoute2Info selectedSystemRoute =
                mRouter2.getSystemController().getSelectedRoutes().get(0);
        assertThat(selectedSystemRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_FIXED);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaRouter2.RouteCallback#onRoutesUpdated"})
    public void testCallbacksAreCalledWhenVolumeChanged() throws Exception {
        if (mAudioManager.isVolumeFixed()) {
            return;
        }

        final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int minVolume = mAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        MediaRoute2Info selectedSystemRoute =
                mRouter2.getSystemController().getSelectedRoutes().get(0);

        assertThat(selectedSystemRoute.getVolumeMax()).isEqualTo(maxVolume);
        assertThat(selectedSystemRoute.getVolume()).isEqualTo(originalVolume);
        assertThat(selectedSystemRoute.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);

        final int targetVolume = originalVolume == minVolume
                ? originalVolume + 1 : originalVolume - 1;
        final CountDownLatch volumeUpdatedLatch = new CountDownLatch(1);
        RouteCallback routeCallback =
                new RouteCallback() {
                    @Override
                    public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                        for (MediaRoute2Info route : routes) {
                            if (route.getId().equals(selectedSystemRoute.getId())
                                    && route.getVolume() == targetVolume) {
                                volumeUpdatedLatch.countDown();
                                break;
                            }
                        }
                    }
                };

        mRouter2.registerRouteCallback(mExecutor, routeCallback, LIVE_AUDIO_DISCOVERY_PREFERENCE);
        try {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            assertThat(volumeUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        }
    }

    @Test
    public void adjustSelectedRouteVolume_callsOnControlledUpdated() throws InterruptedException {
        if (mAudioManager.isVolumeFixed()) {
            return;
        }

        final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int minVolume = mAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        MediaRoute2Info selectedSystemRoute =
                mRouter2.getSystemController().getSelectedRoutes().get(0);

        assertThat(selectedSystemRoute.getVolumeMax()).isEqualTo(maxVolume);
        assertThat(selectedSystemRoute.getVolume()).isEqualTo(originalVolume);
        assertThat(selectedSystemRoute.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);

        final int targetVolume =
                originalVolume == minVolume ? originalVolume + 1 : originalVolume - 1;
        final CountDownLatch volumeUpdatedLatch = new CountDownLatch(1);

        ControllerCallback callback =
                new ControllerCallback() {
                    @Override
                    public void onControllerUpdated(@NonNull RoutingController controller) {
                        if (!TextUtils.equals(
                                controller.getId(), mRouter2.getSystemController().getId())) {
                            return;
                        }
                        MediaRoute2Info controllerSelectedRoute =
                                controller.getSelectedRoutes().get(0);

                        if (controllerSelectedRoute.getVolume() == targetVolume) {
                            volumeUpdatedLatch.countDown();
                        }
                    }
                };

        // Register a route callback for the router to connect to the service.
        RouteCallback routeCallback = new RouteCallback() {};
        mRouter2.registerRouteCallback(mExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);

        mRouter2.registerControllerCallback(mExecutor, callback);
        try {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            assertThat(volumeUpdatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterControllerCallback(callback);
            mRouter2.unregisterRouteCallback(routeCallback);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
        }
    }

    @Test
    public void testGettingSystemMediaRouter2WithoutPermissionThrowsSecurityException() {
        // Make sure that the permission is not given.
        assertThat(mContext.checkSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isNotEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThat(mContext.checkSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isNotEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThrows(SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, mContext.getPackageName()));
    }

    @Test
    public void markCallbacksAsTested() {
        // Due to CTS coverage tool's bug, it doesn't count the callback methods as tested even if
        // we have tests for them. This method just directly calls those methods so that the tool
        // can recognize the callback methods as tested.

        RouteCallback routeCallback = new RouteCallback() {};
        routeCallback.onRoutesAdded(null);
        routeCallback.onRoutesChanged(null);
        routeCallback.onRoutesRemoved(null);
        routeCallback.onRoutesUpdated(null);

        TransferCallback transferCallback = new TransferCallback() {};
        transferCallback.onTransfer(null, null);
        transferCallback.onTransferFailure(null);

        ControllerCallback controllerCallback = new ControllerCallback() {};
        controllerCallback.onControllerUpdated(null);

        OnGetControllerHintsListener listener = route -> null;
        listener.onGetControllerHints(null);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaRouter2.RoutingController#getRoutingSessionInfo"})
    public void getRoutingSessionInfo_returnsNonNullSession() {
        // System controller is always available regardless of MediaRouter2 state.
        RoutingController controller = mRouter2.getSystemController();
        RoutingSessionInfo sessionInfo = controller.getRoutingSessionInfo();
        assertThat(sessionInfo).isNotNull();
    }

    @Test
    public void testShowSystemOutputSwitcherReturnsTrue() throws InterruptedException {
        boolean isDialogShown = mRouter2.showSystemOutputSwitcher();

        // Wait for the dialog to show before dismissing it.
        Thread.sleep(WAIT_MS);

        // Dismiss the system output switcher dialog in order to clean up, leaving the device in
        // the same state as it was when the test started.
        InstrumentationRegistry.getInstrumentation().getContext().sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));

        assertThat(isDialogShown).isTrue();
    }

    @Test
    public void setRouteListingPreference_withIllegalPackageName_throws() {
        // Package name does not belong to caller.
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        setRouteListingPreferenceWithComponentName(
                                new ComponentName(
                                        /* package= */ "android",
                                        /* class= */ getClass().getCanonicalName())));
    }

    @Test
    public void setRouteListingPreference_withInvalidClassName_throws() {
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        setRouteListingPreferenceWithComponentName(
                                new ComponentName(mContext.getPackageName(), "invalidClassName")));
    }

    @Test
    public void setRouteListingPreference_withServiceComponentName_throws() {
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        setRouteListingPreferenceWithComponentName(
                                new ComponentName(mContext, PlaceholderService.class)));
    }

    @Test
    public void setRouteListingPreference_callsOnRouteListingPreferenceChanged()
            throws InterruptedException {
        assertThat(mRouter2.getRouteListingPreference()).isNull();

        RouteListingPreference testPreference = new RouteListingPreference.Builder().build();
        CountDownLatch preferenceChangedLatch = new CountDownLatch(1);
        Consumer<RouteListingPreference> callback =
                (preference) -> {
                    if (Objects.equals(preference, testPreference)) {
                        preferenceChangedLatch.countDown();
                    }
                };

        try {
            mRouter2.registerRouteListingPreferenceUpdatedCallback(mExecutor, callback);

            mRouter2.setRouteListingPreference(testPreference);
            assertThat(preferenceChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteListingPreferenceUpdatedCallback(callback);
        }
    }

    @Test
    public void getRouteListingPreference_returnsLastSetPreference() {
        RouteListingPreference testPreference = new RouteListingPreference.Builder().build();

        mRouter2.setRouteListingPreference(testPreference);
        assertThat(mRouter2.getRouteListingPreference()).isEqualTo(testPreference);
    }

    @Test
    public void getRouteListingPreference_withNoSetPreference_returnsNull() {
        assertThat(mRouter2.getRouteListingPreference()).isNull();
    }

    private void setRouteListingPreferenceWithComponentName(ComponentName componentName) {
        mRouter2.setRouteListingPreference(
                new RouteListingPreference.Builder()
                        .setLinkedItemComponentName(componentName)
                        .build());
    }

    // Helper for getting routes easily. Uses original ID as a key
    private static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            routeMap.put(route.getOriginalId(), route);
        }
        return routeMap;
    }

    private Map<String, MediaRoute2Info> waitAndGetRoutes(List<String> routeTypes)
            throws Exception {
        return waitAndGetRoutes(new RouteDiscoveryPreference.Builder(routeTypes, true).build());
    }

    private Map<String, MediaRoute2Info> waitAndGetRoutes(RouteDiscoveryPreference preference)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                for (MediaRoute2Info route : routes) {
                    if (!route.isSystemRoute()) {
                        latch.countDown();
                    }
                }
            }
        };

        mRouter2.registerRouteCallback(mExecutor, routeCallback, preference);
        try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mRouter2.getRoutes());
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    static void releaseControllers(@NonNull List<RoutingController> controllers) {
        for (RoutingController controller : controllers) {
            controller.release();
        }
    }

    /**
     * Returns a list of original route IDs of the given route list.
     */
    private List<String> getOriginalRouteIds(@NonNull List<MediaRoute2Info> routes) {
        List<String> result = new ArrayList<>();
        for (MediaRoute2Info route : routes) {
            result.add(route.getOriginalId());
        }
        return result;
    }
}
