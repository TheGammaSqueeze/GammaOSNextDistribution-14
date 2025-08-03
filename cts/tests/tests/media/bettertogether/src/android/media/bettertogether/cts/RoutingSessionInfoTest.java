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

import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_FIXED;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;

import static androidx.test.ext.truth.os.BundleSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.res.Resources;
import android.media.MediaRoute2Info;
import android.media.RoutingSessionInfo;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests {@link RoutingSessionInfo} and its {@link RoutingSessionInfo.Builder builder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@NonMainlineTest
public class RoutingSessionInfoTest {
    public static final String TEST_ID = "test_id";
    public static final String TEST_CLIENT_PACKAGE_NAME = "com.test.client.package.name";
    public static final String TEST_NAME = "test_name";
    public static final String TEST_OTHER_NAME = "test_other_name";

    public static final String TEST_ROUTE_ID_0 = "test_route_type_0";
    public static final String TEST_ROUTE_ID_1 = "test_route_type_1";
    public static final String TEST_ROUTE_ID_2 = "test_route_type_2";
    public static final String TEST_ROUTE_ID_3 = "test_route_type_3";
    public static final String TEST_ROUTE_ID_4 = "test_route_type_4";
    public static final String TEST_ROUTE_ID_5 = "test_route_type_5";
    public static final String TEST_ROUTE_ID_6 = "test_route_type_6";
    public static final String TEST_ROUTE_ID_7 = "test_route_type_7";

    public static final String TEST_KEY = "test_key";
    public static final String TEST_VALUE = "test_value";

    public static final int TEST_VOLUME_MAX = 100;
    public static final int TEST_VOLUME = 65;
    @Test
    public void testBuilderConstructorWithInvalidValues() {
        final String nullId = null;
        final String nullClientPackageName = null;

        final String emptyId = "";
        // Note: An empty string as client package name is valid.

        final String validId = TEST_ID;
        final String validClientPackageName = TEST_CLIENT_PACKAGE_NAME;

        // ID is invalid
        assertThrows(IllegalArgumentException.class, () -> new RoutingSessionInfo.Builder(
                nullId, validClientPackageName));
        assertThrows(IllegalArgumentException.class, () -> new RoutingSessionInfo.Builder(
                emptyId, validClientPackageName));

        // client package name is invalid (null)
        assertThrows(NullPointerException.class, () -> new RoutingSessionInfo.Builder(
                validId, nullClientPackageName));

        // Both are invalid
        assertThrows(IllegalArgumentException.class, () -> new RoutingSessionInfo.Builder(
                nullId, nullClientPackageName));
        assertThrows(IllegalArgumentException.class, () -> new RoutingSessionInfo.Builder(
                emptyId, nullClientPackageName));
    }

    @Test
    public void testBuilderCopyConstructorWithNull() {
        // Null RouteInfo (1-argument constructor)
        final RoutingSessionInfo nullRoutingSessionInfo = null;
        assertThrows(NullPointerException.class,
                () -> new RoutingSessionInfo.Builder(nullRoutingSessionInfo));
    }

    @Test
    public void testBuilderConstructorWithEmptyClientPackageName() {
        // An empty string for client package name is valid. (for unknown cases)
        // Creating builder with it should not throw any exception.
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, "" /* clientPackageName*/);
    }

    @Test
    public void testBuilderBuildWithEmptySelectedRoutesThrowsIAE() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME);
        // Note: Calling build() without adding any selected routes.
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuilderAddRouteMethodsWithIllegalArgumentsThrowsIAE() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME);

        final String nullRouteId = null;
        final String emptyRouteId = "";

        assertThrows(IllegalArgumentException.class,
                () -> builder.addSelectedRoute(nullRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.addSelectableRoute(nullRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.addDeselectableRoute(nullRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.addTransferableRoute(nullRouteId));

        assertThrows(IllegalArgumentException.class,
                () -> builder.addSelectedRoute(emptyRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.addSelectableRoute(emptyRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.addDeselectableRoute(emptyRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.addTransferableRoute(emptyRouteId));
    }

    @Test
    public void testBuilderRemoveRouteMethodsWithIllegalArgumentsThrowsIAE() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME);

        final String nullRouteId = null;
        final String emptyRouteId = "";

        assertThrows(IllegalArgumentException.class,
                () -> builder.removeSelectedRoute(nullRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.removeSelectableRoute(nullRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.removeDeselectableRoute(nullRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.removeTransferableRoute(nullRouteId));

        assertThrows(IllegalArgumentException.class,
                () -> builder.removeSelectedRoute(emptyRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.removeSelectableRoute(emptyRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.removeDeselectableRoute(emptyRouteId));
        assertThrows(IllegalArgumentException.class,
                () -> builder.removeTransferableRoute(emptyRouteId));
    }

    @Test
    public void testBuilderAndGettersOfRoutingSessionInfo() {
        Bundle controlHints = new Bundle();
        controlHints.putString(TEST_KEY, TEST_VALUE);

        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .setVolumeMax(TEST_VOLUME_MAX)
                .setVolume(TEST_VOLUME)
                .setControlHints(controlHints)
                .build();

        assertThat(sessionInfo.getId()).isEqualTo(TEST_ID);
        assertThat(sessionInfo.getClientPackageName()).isEqualTo(TEST_CLIENT_PACKAGE_NAME);
        assertThat(sessionInfo.getName()).isEqualTo(TEST_NAME);

        assertThat(sessionInfo.getSelectedRoutes())
                .containsExactly(TEST_ROUTE_ID_0, TEST_ROUTE_ID_1).inOrder();

        assertThat(sessionInfo.getSelectableRoutes())
                .containsExactly(TEST_ROUTE_ID_2, TEST_ROUTE_ID_3).inOrder();

        assertThat(sessionInfo.getDeselectableRoutes())
                .containsExactly(TEST_ROUTE_ID_4, TEST_ROUTE_ID_5).inOrder();

        assertThat(sessionInfo.getTransferableRoutes())
                .containsExactly(TEST_ROUTE_ID_6, TEST_ROUTE_ID_7).inOrder();

        //Note: Individual tests for volume handling were added below, as its value depends on
        // config_volumeAdjustmentForRemoteGroupSessions. See b/228021646 for more details.
        assertThat(sessionInfo.getVolumeMax()).isEqualTo(TEST_VOLUME_MAX);
        assertThat(sessionInfo.getVolume()).isEqualTo(TEST_VOLUME);

        Bundle controlHintsOut = sessionInfo.getControlHints();
        assertThat(controlHintsOut).isNotNull();
        assertThat(controlHintsOut).containsKey(TEST_KEY);
        assertThat(controlHintsOut).string(TEST_KEY).isEqualTo(TEST_VALUE);
    }

    @Test
    public void testBuilderAddRouteMethodsWithBuilderCopyConstructor() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .build();

        RoutingSessionInfo newSessionInfo = new RoutingSessionInfo.Builder(sessionInfo)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .build();

        assertThat(newSessionInfo.getSelectedRoutes())
                .containsExactly(TEST_ROUTE_ID_0, TEST_ROUTE_ID_1).inOrder();

        assertThat(newSessionInfo.getSelectableRoutes())
                .containsExactly(TEST_ROUTE_ID_2, TEST_ROUTE_ID_3).inOrder();

        assertThat(newSessionInfo.getDeselectableRoutes())
                .containsExactly(TEST_ROUTE_ID_4, TEST_ROUTE_ID_5).inOrder();

        assertThat(newSessionInfo.getTransferableRoutes())
                .containsExactly(TEST_ROUTE_ID_6, TEST_ROUTE_ID_7).inOrder();
    }

    @Test
    public void testBuilderRemoveRouteMethods() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .removeSelectedRoute(TEST_ROUTE_ID_1)

                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .removeSelectableRoute(TEST_ROUTE_ID_3)

                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .removeDeselectableRoute(TEST_ROUTE_ID_5)

                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .removeTransferableRoute(TEST_ROUTE_ID_7)

                .build();

        assertThat(sessionInfo.getSelectedRoutes()).containsExactly(TEST_ROUTE_ID_0);

        assertThat(sessionInfo.getSelectableRoutes()).containsExactly(TEST_ROUTE_ID_2);

        assertThat(sessionInfo.getDeselectableRoutes()).containsExactly(TEST_ROUTE_ID_4);

        assertThat(sessionInfo.getTransferableRoutes()).containsExactly(TEST_ROUTE_ID_6);
    }

    @Test
    public void testBuilderRemoveRouteMethodsWithBuilderCopyConstructor() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .build();

        RoutingSessionInfo newSessionInfo = new RoutingSessionInfo.Builder(sessionInfo)
                .removeSelectedRoute(TEST_ROUTE_ID_1)
                .removeSelectableRoute(TEST_ROUTE_ID_3)
                .removeDeselectableRoute(TEST_ROUTE_ID_5)
                .removeTransferableRoute(TEST_ROUTE_ID_7)
                .build();

        assertThat(newSessionInfo.getSelectedRoutes()).containsExactly(TEST_ROUTE_ID_0);

        assertThat(newSessionInfo.getSelectableRoutes()).containsExactly(TEST_ROUTE_ID_2);

        assertThat(newSessionInfo.getDeselectableRoutes()).containsExactly(TEST_ROUTE_ID_4);

        assertThat(newSessionInfo.getTransferableRoutes()).containsExactly(TEST_ROUTE_ID_6);
    }

    @Test
    public void testBuilderClearRouteMethods() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .clearSelectedRoutes()

                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .clearSelectableRoutes()

                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .clearDeselectableRoutes()

                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .clearTransferableRoutes()

                // SelectedRoutes must not be empty
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .build();

        assertThat(sessionInfo.getSelectedRoutes()).containsExactly(TEST_ROUTE_ID_0);

        assertThat(sessionInfo.getSelectableRoutes().isEmpty()).isTrue();
        assertThat(sessionInfo.getDeselectableRoutes().isEmpty()).isTrue();
        assertThat(sessionInfo.getTransferableRoutes().isEmpty()).isTrue();
    }

    @Test
    public void testBuilderClearRouteMethodsWithBuilderCopyConstructor() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .build();

        RoutingSessionInfo newSessionInfo = new RoutingSessionInfo.Builder(sessionInfo)
                .clearSelectedRoutes()
                .clearSelectableRoutes()
                .clearDeselectableRoutes()
                .clearTransferableRoutes()
                // SelectedRoutes must not be empty
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .build();

        assertThat(newSessionInfo.getSelectedRoutes()).containsExactly(TEST_ROUTE_ID_0);

        assertThat(newSessionInfo.getSelectableRoutes().isEmpty()).isTrue();
        assertThat(newSessionInfo.getDeselectableRoutes().isEmpty()).isTrue();
        assertThat(newSessionInfo.getTransferableRoutes().isEmpty()).isTrue();
    }

    @Test
    public void testEqualsCreatedWithSameArguments() {
        Bundle controlHints = new Bundle();
        controlHints.putString(TEST_KEY, TEST_VALUE);

        RoutingSessionInfo sessionInfo1 = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(TEST_VOLUME_MAX)
                .setVolume(TEST_VOLUME)
                .setControlHints(controlHints)
                .build();

        RoutingSessionInfo sessionInfo2 = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(TEST_VOLUME_MAX)
                .setVolume(TEST_VOLUME)
                .setControlHints(controlHints)
                .build();

        assertThat(sessionInfo2).isEqualTo(sessionInfo1);
        assertThat(sessionInfo2.hashCode()).isEqualTo(sessionInfo1.hashCode());
    }

    @Test
    public void testEqualsCreatedWithBuilderCopyConstructor() {
        Bundle controlHints = new Bundle();
        controlHints.putString(TEST_KEY, TEST_VALUE);

        RoutingSessionInfo sessionInfo1 = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(TEST_VOLUME_MAX)
                .setVolume(TEST_VOLUME)
                .setControlHints(controlHints)
                .build();

        RoutingSessionInfo sessionInfo2 = new RoutingSessionInfo.Builder(sessionInfo1).build();

        assertThat(sessionInfo2).isEqualTo(sessionInfo1);
        assertThat(sessionInfo2.hashCode()).isEqualTo(sessionInfo1.hashCode());
    }

    @Test
    public void testEqualsReturnFalse() {
        Bundle controlHints = new Bundle();
        controlHints.putString(TEST_KEY, TEST_VALUE);

        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(TEST_VOLUME_MAX)
                .setVolume(TEST_VOLUME)
                .setControlHints(controlHints)
                .build();

        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .setName(TEST_OTHER_NAME).build()).isNotEqualTo(sessionInfo);

        // Now, we will use copy constructor
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .addSelectedRoute("randomRoute")
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .addSelectableRoute("randomRoute")
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .addDeselectableRoute("randomRoute")
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .addTransferableRoute("randomRoute")
                .build()).isNotEqualTo(sessionInfo);

        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .removeSelectedRoute(TEST_ROUTE_ID_1)
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .removeSelectableRoute(TEST_ROUTE_ID_3)
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .removeDeselectableRoute(TEST_ROUTE_ID_5)
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .removeTransferableRoute(TEST_ROUTE_ID_7)
                .build()).isNotEqualTo(sessionInfo);

        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .clearSelectedRoutes()
                // Note: Calling build() with empty selected routes will throw IAE.
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .clearSelectableRoutes()
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .clearDeselectableRoutes()
                .build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .clearTransferableRoutes()
                .build()).isNotEqualTo(sessionInfo);

        /*
        Note: Using session with only one selected route, as volume handling of group sessions
        depends config_volumeAdjustmentForRemoteGroupSessions. See b/228021646.
        */
        RoutingSessionInfo.Builder oneRouteSession = new RoutingSessionInfo.Builder(sessionInfo)
                .clearSelectedRoutes()
                .setVolumeHandling(PLAYBACK_VOLUME_FIXED)
                .addSelectedRoute(TEST_ROUTE_ID_0);

        assertThat(oneRouteSession.build()).isNotEqualTo(
                oneRouteSession
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .build());

        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .setVolumeMax(TEST_VOLUME_MAX + 1).build()).isNotEqualTo(sessionInfo);
        assertThat(new RoutingSessionInfo.Builder(sessionInfo)
                .setVolume(TEST_VOLUME + 1).build()).isNotEqualTo(sessionInfo);

        // Note: ControlHints will not affect the equals.
    }

    @Test
    public void testParcelingAndUnParceling() {
        Bundle controlHints = new Bundle();
        controlHints.putString(TEST_KEY, TEST_VALUE);

        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addSelectableRoute(TEST_ROUTE_ID_3)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addDeselectableRoute(TEST_ROUTE_ID_5)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .addTransferableRoute(TEST_ROUTE_ID_7)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(TEST_VOLUME_MAX)
                .setVolume(TEST_VOLUME)
                .setControlHints(controlHints)
                .build();

        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(sessionInfo, 0);
        parcel.setDataPosition(0);

        RoutingSessionInfo sessionInfoFromParcel = parcel.readParcelable(null);
        assertThat(sessionInfoFromParcel).isEqualTo(sessionInfo);
        assertThat(sessionInfoFromParcel.hashCode()).isEqualTo(sessionInfo.hashCode());

        // Check controlHints
        Bundle controlHintsOut = sessionInfoFromParcel.getControlHints();
        assertThat(controlHintsOut).isNotNull();
        assertThat(controlHintsOut).containsKey(TEST_KEY);
        assertThat(controlHintsOut).string(TEST_KEY).isEqualTo(TEST_VALUE);
        parcel.recycle();

        // In order to mark writeToParcel as tested, we let's just call it directly.
        Parcel dummyParcel = Parcel.obtain();
        sessionInfo.writeToParcel(dummyParcel, 0);
        dummyParcel.recycle();
    }

    @Test
    public void testDescribeContents() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .addDeselectableRoute(TEST_ROUTE_ID_4)
                .addTransferableRoute(TEST_ROUTE_ID_6)
                .build();
        assertThat(sessionInfo.describeContents()).isEqualTo(0);
    }

    @Test
    public void testGroupVolumeHandling() {
        //Note: Volume handling for group sessions depends on
        // config_volumeAdjustmentForRemoteGroupSessions. See b/228021646 for details.

        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectedRoute(TEST_ROUTE_ID_1)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .build();

        // Resources.getSystem().getIdentifier() is necessary to avoid the inlining of the resource
        // id int, which is not guaranteed to match across Android builds on which CTS runs. See
        // b/288602351 for more context.
        int volumeAdjustmentForRemoteGroupSessionsResourceId =
                Resources.getSystem()
                        .getIdentifier(
                                "config_volumeAdjustmentForRemoteGroupSessions",
                                "bool",
                                /* defPackage= */ "android");
        boolean volumeAdjustmentForRemoteGroupSessions =
                Resources.getSystem().getBoolean(volumeAdjustmentForRemoteGroupSessionsResourceId);

        int expectedVolumeHandling = volumeAdjustmentForRemoteGroupSessions
                ? PLAYBACK_VOLUME_VARIABLE : PLAYBACK_VOLUME_FIXED;

        assertThat(sessionInfo.getVolumeHandling()).isEqualTo(expectedVolumeHandling);
    }

    @Test
    public void testGroupVolumeHandlingForSystemSession() {
        RoutingSessionInfo sessionInfo =
                new RoutingSessionInfo.Builder(TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                        .setName(TEST_NAME)
                        .addSelectedRoute(TEST_ROUTE_ID_0)
                        .addSelectedRoute(TEST_ROUTE_ID_1)
                        .setSystemSession(true)
                        .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                        .build();

        assertThat(sessionInfo.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);
    }

    @Test
    public void testSingleRouteVolumeHandling() {
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                .build();

        assertThat(sessionInfo.getVolumeHandling()).isEqualTo(PLAYBACK_VOLUME_VARIABLE);
    }

    @Test
    public void selectedRoutesListIsImmutable() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        RoutingSessionInfo sessionInfo = builder.build();
        assertThat(sessionInfo.getSelectedRoutes()).containsExactly(TEST_ROUTE_ID_0);
        builder.clearSelectedRoutes();
        assertThat(sessionInfo.getSelectedRoutes()).containsExactly(TEST_ROUTE_ID_0);
    }

    @Test
    public void selectableRoutesListIsImmutable() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addSelectableRoute(TEST_ROUTE_ID_2)
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        RoutingSessionInfo sessionInfo = builder.build();
        assertThat(sessionInfo.getSelectableRoutes()).containsExactly(TEST_ROUTE_ID_2);
        builder.clearSelectableRoutes();
        assertThat(sessionInfo.getSelectableRoutes()).containsExactly(TEST_ROUTE_ID_2);
    }

    @Test
    public void deselectableRoutesListIsImmutable() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addDeselectableRoute(TEST_ROUTE_ID_2)
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        RoutingSessionInfo sessionInfo = builder.build();
        assertThat(sessionInfo.getDeselectableRoutes()).containsExactly(TEST_ROUTE_ID_2);
        builder.clearDeselectableRoutes();
        assertThat(sessionInfo.getDeselectableRoutes()).containsExactly(TEST_ROUTE_ID_2);
    }

    @Test
    public void transferableRoutesListIsImmutable() {
        RoutingSessionInfo.Builder builder = new RoutingSessionInfo.Builder(
                TEST_ID, TEST_CLIENT_PACKAGE_NAME)
                .setName(TEST_NAME)
                .addSelectedRoute(TEST_ROUTE_ID_0)
                .addTransferableRoute(TEST_ROUTE_ID_2)
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        RoutingSessionInfo sessionInfo = builder.build();
        assertThat(sessionInfo.getTransferableRoutes()).containsExactly(TEST_ROUTE_ID_2);
        builder.clearTransferableRoutes();
        assertThat(sessionInfo.getTransferableRoutes()).containsExactly(TEST_ROUTE_ID_2);
    }
}
