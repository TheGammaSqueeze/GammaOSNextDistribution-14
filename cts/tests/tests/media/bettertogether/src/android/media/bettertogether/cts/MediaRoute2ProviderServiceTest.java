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

import static android.media.bettertogether.cts.MediaRouter2Test.releaseControllers;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.FEATURE_SPECIAL;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID4_TO_SELECT_AND_DESELECT;
import static android.media.bettertogether.cts.StubMediaRoute2ProviderService.ROUTE_ID5_TO_TRANSFER_TO;

import static androidx.test.ext.truth.os.BundleSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ControllerCallback;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2.TransferCallback;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.media.bettertogether.cts.StubMediaRoute2ProviderService.Proxy;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to StubMediaRoute2ProviderService")
@LargeTest
@NonMainlineTest
public class MediaRoute2ProviderServiceTest {
    private static final String TAG = "MR2ProviderServiceTest";
    Context mContext;
    private MediaRouter2 mRouter2;
    private Executor mExecutor;
    private RouteCallback mRouterDummyCallback = new RouteCallback(){};
    private StubMediaRoute2ProviderService mService;

    private static final int TIMEOUT_MS = 5000;

    private static final String SESSION_ID_1 = "SESSION_ID_1";
    private static final String SESSION_ID_2 = "SESSION_ID_2";

    // TODO: Merge these TEST_KEY / TEST_VALUE in all files
    public static final String TEST_KEY = "test_key";
    public static final String TEST_VALUE = "test_value";

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mRouter2 = MediaRouter2.getInstance(mContext);
        mExecutor = Executors.newSingleThreadExecutor();

        MediaRouter2TestActivity.startActivity(mContext);

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
    }

    @After
    public void tearDown() throws Exception {
        mRouter2.unregisterRouteCallback(mRouterDummyCallback);
        MediaRouter2TestActivity.finishActivity();
        if (mService != null) {
            mService.clear();
            mService = null;
        }
    }

    @Test
    public void testGetSessionInfoAndGetAllSessionInfo() {
        assertThat(mService.getAllSessionInfo()).isEmpty();

        // Add a session
        RoutingSessionInfo sessionInfo1 = new RoutingSessionInfo.Builder(
                SESSION_ID_1, "" /* clientPackageName */)
                .addSelectedRoute(ROUTE_ID1)
                .build();
        mService.notifySessionCreated(MediaRoute2ProviderService.REQUEST_ID_NONE, sessionInfo1);
        assertThat(mService.getAllSessionInfo()).containsExactly(sessionInfo1);
        assertThat(mService.getSessionInfo(SESSION_ID_1)).isEqualTo(sessionInfo1);

        // Add another session
        RoutingSessionInfo sessionInfo2 = new RoutingSessionInfo.Builder(
                SESSION_ID_2, "" /* clientPackageName */)
                .addSelectedRoute(ROUTE_ID2)
                .build();
        mService.notifySessionCreated(
                MediaRoute2ProviderService.REQUEST_ID_NONE, sessionInfo2);
        assertThat(mService.getAllSessionInfo()).hasSize(2);
        assertThat(mService.getSessionInfo(SESSION_ID_2)).isEqualTo(sessionInfo2);

        // Remove the first session
        mService.notifySessionReleased(SESSION_ID_1);
        assertThat(mService.getSessionInfo(SESSION_ID_1)).isNull();
        assertThat(mService.getAllSessionInfo()).containsExactly(sessionInfo2);
        assertThat(mService.getSessionInfo(SESSION_ID_2)).isEqualTo(sessionInfo2);

        // Remove the remaining session
        mService.notifySessionReleased(SESSION_ID_2);
        assertThat(mService.getAllSessionInfo()).isEmpty();
        assertThat(mService.getSessionInfo(SESSION_ID_2)).isNull();
    }

    @Test
    @ApiTest(apis = {"android.media.MediaRoute2ProviderService#notifyRoutes",
            "android.media.MediaRouter2.RouteCallback#onRoutesAdded",
            "android.media.MediaRouter2.RouteCallback#onRoutesChanged",
            "android.media.MediaRouter2.RouteCallback#onRoutesRemoved"})
    public void testNotifyRoutesInvokesMediaRouter2DeprecatedRouteCallback() throws Exception {
        final String routeId0 = "routeId0";
        final String routeName0 = "routeName0";
        final String routeId1 = "routeId1";
        final String routeName1 = "routeName1";
        final List<String> features = Collections.singletonList("customFeature");

        final List<MediaRoute2Info> routes = new ArrayList<>();
        routes.add(new MediaRoute2Info.Builder(routeId0, routeName0)
                .addFeatures(features)
                .build());
        routes.add(new MediaRoute2Info.Builder(routeId1, routeName1)
                .addFeatures(features)
                .build());

        final int newConnectionState = MediaRoute2Info.CONNECTION_STATE_CONNECTED;
        CountDownLatch onRoutesAddedLatch = new CountDownLatch(1);
        CountDownLatch onRoutesChangedLatch = new CountDownLatch(1);
        CountDownLatch onRoutesRemovedLatch = new CountDownLatch(1);
        RouteCallback routeCallback = new RouteCallback() {
            @Override
            public void onRoutesAdded(List<MediaRoute2Info> routes) {
                if (!features.equals(routes.get(0).getFeatures())) {
                    return;
                }
                assertThat(routes).hasSize(2);

                MediaRoute2Info route0;
                MediaRoute2Info route1;
                if (routeId0.equals(routes.get(0).getOriginalId())) {
                    route0 = routes.get(0);
                    route1 = routes.get(1);
                } else {
                    route0 = routes.get(1);
                    route1 = routes.get(0);
                }

                assertThat(route0).isNotNull();
                assertThat(route0.getOriginalId()).isEqualTo(routeId0);
                assertThat(route0.getName()).isEqualTo(routeName0);
                assertThat(route0.getFeatures()).isEqualTo(features);

                assertThat(route1).isNotNull();
                assertThat(route1.getOriginalId()).isEqualTo(routeId1);
                assertThat(route1.getName()).isEqualTo(routeName1);
                assertThat(route1.getFeatures()).isEqualTo(features);

                onRoutesAddedLatch.countDown();
            }

            @Override
            public void onRoutesChanged(List<MediaRoute2Info> routes) {
                if (!features.equals(routes.get(0).getFeatures())) {
                    return;
                }
                assertThat(routes).hasSize(1);
                assertThat(routes.get(0).getOriginalId()).isEqualTo(routeId1);
                assertThat(routes.get(0).getConnectionState()).isEqualTo(newConnectionState);
                onRoutesChangedLatch.countDown();
            }

            @Override
            public void onRoutesRemoved(List<MediaRoute2Info> routes) {
                if (!features.equals(routes.get(0).getFeatures())) {
                    return;
                }
                assertThat(routes).hasSize(2);

                MediaRoute2Info route0;
                MediaRoute2Info route1;
                if (routeId0.equals(routes.get(0).getOriginalId())) {
                    route0 = routes.get(0);
                    route1 = routes.get(1);
                } else {
                    route0 = routes.get(1);
                    route1 = routes.get(0);
                }

                assertThat(route0).isNotNull();
                assertThat(route0.getOriginalId()).isEqualTo(routeId0);
                assertThat(route0.getName()).isEqualTo(routeName0);
                assertThat(route0.getFeatures()).isEqualTo(features);

                assertThat(route1).isNotNull();
                assertThat(route1.getOriginalId()).isEqualTo(routeId1);
                assertThat(route1.getName()).isEqualTo(routeName1);
                assertThat(route1.getFeatures()).isEqualTo(features);

                onRoutesRemovedLatch.countDown();
            }
        };

        mRouter2.registerRouteCallback(mExecutor, routeCallback,
                new RouteDiscoveryPreference.Builder(features, true).build());
        try {
            mService.notifyRoutes(routes);
            assertThat(onRoutesAddedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // Change the connection state of route2 in order to invoke onRoutesChanged()
            MediaRoute2Info newRoute2 = new MediaRoute2Info.Builder(routes.get(1))
                    .setConnectionState(newConnectionState)
                    .build();
            routes.set(1, newRoute2);
            mService.notifyRoutes(routes);
            assertThat(onRoutesChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // Now remove all the routes
            routes.clear();
            mService.notifyRoutes(routes);
            assertThat(onRoutesRemovedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    @Test
    @ApiTest(apis = {"android.media.MediaRoute2ProviderService#notifyRoutes",
            "android.media.MediaRouter2.RouteCallback#onRoutesUpdated"})
    public void testNotifyRoutesInvokesMediaRouter2RouteCallback() throws Exception {
        final String routeId0 = "routeId0";
        final String routeName0 = "routeName0";
        final String routeId1 = "routeId1";
        final String routeName1 = "routeName1";
        final List<String> features = Collections.singletonList("customFeature");

        final List<MediaRoute2Info> routes = new ArrayList<>();
        routes.add(new MediaRoute2Info.Builder(routeId0, routeName0).addFeatures(features).build());
        routes.add(new MediaRoute2Info.Builder(routeId1, routeName1).addFeatures(features).build());

        final List<MediaRoute2Info> testRoutes = new ArrayList<>();
        testRoutes.add(new MediaRoute2Info.Builder(routes.get(0)).build());
        testRoutes.add(new MediaRoute2Info.Builder(routes.get(1)).build());

        final int newConnectionState = MediaRoute2Info.CONNECTION_STATE_CONNECTED;
        CountDownLatch routesAddedLatch = new CountDownLatch(1);
        CountDownLatch routesChangedLatch = new CountDownLatch(1);
        CountDownLatch routesRemovedLatch = new CountDownLatch(1);
        RouteCallback routeCallback =
                new RouteCallback() {
                    @Override
                    public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                        if (routesAddedLatch.getCount() == 1) {
                            assertContainsExpectedRoutes(routes, testRoutes);
                            routesAddedLatch.countDown();
                            return;
                        }

                        if (routesChangedLatch.getCount() == 1) {
                            assertContainsExpectedRoutes(routes, testRoutes);
                            routesChangedLatch.countDown();
                            return;
                        }

                        if (routesRemovedLatch.getCount() == 1) {
                            assertNotContainsExpectedRoutes(routes, testRoutes);
                            routesRemovedLatch.countDown();
                            return;
                        }
                    }
                };

        mRouter2.registerRouteCallback(
                mExecutor,
                routeCallback,
                new RouteDiscoveryPreference.Builder(features, true).build());
        try {
            mService.notifyRoutes(routes);
            assertThat(routesAddedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // Change the connection state of route2 in order to invoke onRoutesUpdated()
            MediaRoute2Info newRoute2 =
                    new MediaRoute2Info.Builder(routes.get(1))
                            .setConnectionState(newConnectionState)
                            .build();
            routes.set(1, newRoute2);
            testRoutes.set(1, newRoute2);
            mService.notifyRoutes(routes);
            assertThat(routesChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            // Now remove all the routes
            routes.clear();
            mService.notifyRoutes(routes);
            assertThat(routesRemovedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    /**
     * Asserts {@code routes} contains all items in {@code expectedRoutes}. Assertion will fail if
     * id, name, or features do not match.
     * @param routes
     * @param expectedRoutes
     */
    private static void assertContainsExpectedRoutes(
            List<MediaRoute2Info> routes, List<MediaRoute2Info> expectedRoutes) {
        for (MediaRoute2Info testRoute : expectedRoutes) {
            MediaRoute2Info matchingRoute =
                    routes.stream()
                            .filter(r -> r.getOriginalId().equals(testRoute.getOriginalId()))
                            .findFirst()
                            .orElse(null);

            assertWithMessage("No route found.").that(matchingRoute).isNotNull();
            assertThat(matchingRoute.getName()).isEqualTo(testRoute.getName());
            assertThat(matchingRoute.getFeatures()).isEqualTo(testRoute.getFeatures());
        }
    }

    /**
     * Asserts {@code routes} does <i><b>not</b></i> contain all items in {@code expectedRoutes}.
     * @param routes
     * @param expectedRoutes
     */
    private static void assertNotContainsExpectedRoutes(@NonNull List<MediaRoute2Info> routes,
            @NonNull List<MediaRoute2Info> expectedRoutes) {
        for (MediaRoute2Info removedRoute : expectedRoutes) {
            MediaRoute2Info matchingRoute =
                    routes.stream()
                            .filter(r -> r.getOriginalId().equals(removedRoute.getOriginalId()))
                            .findFirst()
                            .orElse(null);
            assertThat(matchingRoute).isNull();
        }
    }

    @Test
    public void testSessionRelatedCallbacks() throws Exception {
        mService.initializeRoutes();
        mService.publishRoutes();

        List<String> featuresSample = Collections.singletonList(FEATURE_SAMPLE);
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(featuresSample);
        MediaRoute2Info routeToCreateSession = routes.get(ROUTE_ID1);
        assertThat(routeToCreateSession).isNotNull();

        Bundle sessionHints = new Bundle();
        sessionHints.putString(TEST_KEY, TEST_VALUE);
        mRouter2.setOnGetControllerHintsListener(route -> sessionHints);

        CountDownLatch onCreateSessionLatch = new CountDownLatch(1);
        CountDownLatch onReleaseSessionLatch = new CountDownLatch(1);
        CountDownLatch onSelectRouteLatch = new CountDownLatch(1);
        CountDownLatch onDeselectRouteLatch = new CountDownLatch(1);
        CountDownLatch onTransferToRouteLatch = new CountDownLatch(1);

        // Now test all session-related callbacks.
        setProxy(new Proxy() {
            @Override
            public void onCreateSession(long requestId, String packageName, String routeId,
                    Bundle sessionHints) {
                assertThat(packageName).isEqualTo(mContext.getPackageName());
                assertThat(routeId).isEqualTo(ROUTE_ID1);
                assertThat(sessionHints).isNotNull();
                assertThat(sessionHints).containsKey(TEST_KEY);
                assertThat(sessionHints).string(TEST_KEY).isEqualTo(TEST_VALUE);

                RoutingSessionInfo info = new RoutingSessionInfo.Builder(
                        SESSION_ID_1, mContext.getPackageName())
                        .addSelectedRoute(ROUTE_ID1)
                        .addSelectableRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .addTransferableRoute(ROUTE_ID5_TO_TRANSFER_TO)
                        .build();
                mService.notifySessionCreated(requestId, info);
                onCreateSessionLatch.countDown();
            }

            @Override
            public void onSelectRoute(long requestId, String sessionId, String routeId) {
                assertThat(sessionId).isEqualTo(SESSION_ID_1);
                assertThat(routeId).isEqualTo(ROUTE_ID4_TO_SELECT_AND_DESELECT);

                RoutingSessionInfo oldInfo = mService.getSessionInfo(SESSION_ID_1);
                RoutingSessionInfo newInfo = new RoutingSessionInfo.Builder(oldInfo)
                        .addSelectedRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .removeSelectableRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .addDeselectableRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .build();
                mService.notifySessionUpdated(newInfo);
                onSelectRouteLatch.countDown();
            }

            @Override
            public void onDeselectRoute(long requestId, String sessionId, String routeId) {
                assertThat(sessionId).isEqualTo(SESSION_ID_1);
                assertThat(routeId).isEqualTo(ROUTE_ID4_TO_SELECT_AND_DESELECT);

                RoutingSessionInfo oldInfo = mService.getSessionInfo(SESSION_ID_1);
                RoutingSessionInfo newInfo = new RoutingSessionInfo.Builder(oldInfo)
                        .removeSelectedRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .addSelectableRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .removeDeselectableRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .build();
                mService.notifySessionUpdated(newInfo);
                onDeselectRouteLatch.countDown();
            }

            @Override
            public void onTransferToRoute(long requestId, String sessionId, String routeId) {
                assertThat(sessionId).isEqualTo(SESSION_ID_1);
                assertThat(routeId).isEqualTo(ROUTE_ID5_TO_TRANSFER_TO);

                RoutingSessionInfo oldInfo = mService.getSessionInfo(SESSION_ID_1);
                RoutingSessionInfo newInfo = new RoutingSessionInfo.Builder(oldInfo)
                        .clearDeselectableRoutes()
                        .clearSelectedRoutes()
                        .clearDeselectableRoutes()
                        .addSelectedRoute(ROUTE_ID5_TO_TRANSFER_TO)
                        .build();
                mService.notifySessionUpdated(newInfo);
                onTransferToRouteLatch.countDown();
            }

            @Override
            public void onReleaseSession(long requestId, String sessionId) {
                assertThat(sessionId).isEqualTo(SESSION_ID_1);
                mService.notifySessionReleased(sessionId);
                onReleaseSessionLatch.countDown();
            }
        });

        CountDownLatch onTransferredLatch = new CountDownLatch(1);
        CountDownLatch onControllerUpdatedForSelectLatch = new CountDownLatch(1);
        CountDownLatch onControllerUpdatedForDeselectLatch = new CountDownLatch(1);
        CountDownLatch onControllerUpdatedForTransferLatch = new CountDownLatch(1);
        List<RoutingController> controllers = new ArrayList<>();

        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                if (SESSION_ID_1.equals(newController.getOriginalId())) {
                    assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                    controllers.add(newController);
                    onTransferredLatch.countDown();
                }
            }
        };

        ControllerCallback controllerCallback = new ControllerCallback() {
            @Override
            public void onControllerUpdated(RoutingController controller) {
                List<MediaRoute2Info> selectedRoutes = controller.getSelectedRoutes();
                if (onControllerUpdatedForSelectLatch.getCount() > 0) {
                    if (selectedRoutes.size() == 2
                            && ROUTE_ID4_TO_SELECT_AND_DESELECT.equals(
                            selectedRoutes.get(1).getOriginalId())) {
                        onControllerUpdatedForSelectLatch.countDown();
                    }
                } else if (onControllerUpdatedForDeselectLatch.getCount() > 0) {
                    if (selectedRoutes.size() == 1
                            && ROUTE_ID1.equals(selectedRoutes.get(0).getOriginalId())) {
                        onControllerUpdatedForDeselectLatch.countDown();
                    }
                } else if (onControllerUpdatedForTransferLatch.getCount() > 0) {
                    if (selectedRoutes.size() == 1
                            && ROUTE_ID5_TO_TRANSFER_TO.equals(
                            selectedRoutes.get(0).getOriginalId())) {
                        onControllerUpdatedForTransferLatch.countDown();
                    }
                }
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the mService.
        RouteCallback dummyCallback = new RouteCallback() {};
        try {
            mRouter2.registerRouteCallback(mExecutor, dummyCallback,
                    new RouteDiscoveryPreference.Builder(new ArrayList<>(), true).build());
            mRouter2.registerTransferCallback(mExecutor, transferCallback);
            mRouter2.registerControllerCallback(mExecutor, controllerCallback);

            mRouter2.transferTo(routeToCreateSession);
            assertThat(onCreateSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(onTransferredLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(controllers.isEmpty()).isFalse();

            RoutingController controller = controllers.get(0);

            controller.selectRoute(routes.get(ROUTE_ID4_TO_SELECT_AND_DESELECT));
            assertThat(onSelectRouteLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(onControllerUpdatedForSelectLatch.await(TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)).isTrue();

            controller.deselectRoute(routes.get(ROUTE_ID4_TO_SELECT_AND_DESELECT));
            assertThat(onDeselectRouteLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(onControllerUpdatedForDeselectLatch.await(
                    TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            mRouter2.transferTo(routes.get(ROUTE_ID5_TO_TRANSFER_TO));
            assertThat(onTransferToRouteLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(onControllerUpdatedForTransferLatch.await(
                    TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

            controller.release();
            assertThat(onReleaseSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteCallback(dummyCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
            mRouter2.unregisterControllerCallback(controllerCallback);
            mRouter2.setOnGetControllerHintsListener(null);
            releaseControllers(mRouter2.getControllers());
        }
    }

    @Test
    public void testNotifySessionReleased() throws Exception {
        mService.initializeRoutes();
        mService.publishRoutes();

        List<String> featuresSample = Collections.singletonList(FEATURE_SAMPLE);
        Map<String, MediaRoute2Info> routes = waitAndGetRoutes(featuresSample);
        MediaRoute2Info routeToCreateSession = routes.get(ROUTE_ID1);
        assertThat(routeToCreateSession).isNotNull();

        CountDownLatch onCreateSessionLatch = new CountDownLatch(1);
        setProxy(new Proxy() {
            @Override
            public void onCreateSession(long requestId, String packageName, String routeId,
                    Bundle sessionHints) {
                assertThat(packageName).isEqualTo(mContext.getPackageName());
                assertThat(routeId).isEqualTo(ROUTE_ID1);
                assertThat(sessionHints).isNull();

                RoutingSessionInfo info = new RoutingSessionInfo.Builder(
                        SESSION_ID_1, mContext.getPackageName())
                        .addSelectedRoute(ROUTE_ID1)
                        .addSelectableRoute(ROUTE_ID4_TO_SELECT_AND_DESELECT)
                        .addTransferableRoute(ROUTE_ID5_TO_TRANSFER_TO)
                        .build();
                mService.notifySessionCreated(requestId, info);
                onCreateSessionLatch.countDown();
            }
        });


        CountDownLatch onTransferredLatch = new CountDownLatch(1);
        CountDownLatch onStoppedLatch = new CountDownLatch(1);
        List<RoutingController> controllers = new ArrayList<>();

        TransferCallback transferCallback = new TransferCallback() {
            @Override
            public void onTransfer(RoutingController oldController,
                    RoutingController newController) {
                if (SESSION_ID_1.equals(newController.getOriginalId())) {
                    assertThat(oldController).isEqualTo(mRouter2.getSystemController());
                    controllers.add(newController);
                    onTransferredLatch.countDown();
                }
            }
            @Override
            public void onStop(RoutingController controller) {
                if (SESSION_ID_1.equals(controller.getOriginalId())) {
                    assertThat(controller.isReleased()).isTrue();
                    onStoppedLatch.countDown();
                }
            }
        };

        // TODO: Remove this once the MediaRouter2 becomes always connected to the mService.
        RouteCallback dummyCallback = new RouteCallback() {};
        try {
            mRouter2.registerRouteCallback(mExecutor, dummyCallback,
                    new RouteDiscoveryPreference.Builder(new ArrayList<>(), true).build());
            mRouter2.registerTransferCallback(mExecutor, transferCallback);

            mRouter2.transferTo(routeToCreateSession);
            assertThat(onCreateSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(onTransferredLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(controllers.isEmpty()).isFalse();

            mService.notifySessionReleased(SESSION_ID_1);
            assertThat(onStoppedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteCallback(dummyCallback);
            mRouter2.unregisterTransferCallback(transferCallback);
            releaseControllers(mRouter2.getControllers());
        }
    }


    @Test
    public void testOnDiscoveryPreferenceChanged() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        RouteCallback routeCallback = new RouteCallback() {};
        RouteCallback routeCallback2 = new RouteCallback() {};

        List<String> featuresSample = Collections.singletonList(FEATURE_SAMPLE);
        List<String> featuresSpecial = Collections.singletonList(FEATURE_SPECIAL);

        setProxy(new Proxy() {
            @Override
            public void onDiscoveryPreferenceChanged(RouteDiscoveryPreference preference) {
                List<String> features = preference.getPreferredFeatures();
                if (features.contains(FEATURE_SAMPLE) && features.contains(FEATURE_SPECIAL)
                        && preference.shouldPerformActiveScan()) {
                    latch.countDown();
                }
                if (latch.getCount() == 0 && !features.contains(FEATURE_SAMPLE)
                        && features.contains(FEATURE_SPECIAL)) {
                    latch2.countDown();
                }
            }
        });

        mRouter2.registerRouteCallback(mExecutor, routeCallback,
                new RouteDiscoveryPreference.Builder(featuresSample, true).build());
        mRouter2.registerRouteCallback(mExecutor, routeCallback2,
                new RouteDiscoveryPreference.Builder(featuresSpecial, true).build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            mRouter2.unregisterRouteCallback(routeCallback);
            assertThat(latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback2);
        }
    }

    void setProxy(StubMediaRoute2ProviderService.Proxy proxy) {
        StubMediaRoute2ProviderService service = mService;
        if (service != null) {
            service.setProxy(proxy);
        }
    }

    Map<String, MediaRoute2Info> waitAndGetRoutes(List<String> features)
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

        mRouter2.registerRouteCallback(mExecutor, routeCallback,
                new RouteDiscoveryPreference.Builder(features, true).build());
        try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return createRouteMap(mRouter2.getRoutes());
        } finally {
            mRouter2.unregisterRouteCallback(routeCallback);
        }
    }

    // Helper for getting routes easily. Uses original ID as a key
    static Map<String, MediaRoute2Info> createRouteMap(List<MediaRoute2Info> routes) {
        Map<String, MediaRoute2Info> routeMap = new HashMap<>();
        for (MediaRoute2Info route : routes) {
            routeMap.put(route.getOriginalId(), route);
        }
        return routeMap;
    }

}
