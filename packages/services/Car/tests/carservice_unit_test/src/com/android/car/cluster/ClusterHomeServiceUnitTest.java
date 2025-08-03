/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.cluster;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER;
import static android.car.cluster.ClusterHomeManager.UI_TYPE_CLUSTER_HOME;
import static android.car.cluster.ClusterHomeManager.UI_TYPE_CLUSTER_NONE;
import static android.car.navigation.CarNavigationInstrumentCluster.CLUSTER_TYPE_IMAGE_CODES_ONLY;

import static com.android.car.internal.common.CommonConstants.EMPTY_BYTE_ARRAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityOptions;
import android.car.CarOccupantZoneManager;
import android.car.ICarOccupantZoneCallback;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.ClusterState;
import android.car.cluster.IClusterNavigationStateListener;
import android.car.cluster.IClusterStateListener;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Display;

import com.android.car.CarOccupantZoneService;
import com.android.car.R;
import com.android.car.am.FixedActivityService;
import com.android.car.cluster.ClusterNavigationService.ContextOwner;
import com.android.car.hal.ClusterHalService;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClusterHomeServiceUnitTest {
    private static final int CLUSTER_DISPLAY_ID = 99;
    private static final int NEW_CLUSTER_DISPLAY_ID = CLUSTER_DISPLAY_ID + 1;
    private static final int CLUSTER_WIDTH = 1024;
    private static final int CLUSTER_HEIGHT = 600;
    private static final int UI_TYPE_CLUSTER_MAPS = UI_TYPE_CLUSTER_HOME + 1;
    private static final String NAV_STATE_PROTO_BUNDLE_KEY = "navstate2";
    private static final int USER_ID = 111;

    private ClusterHomeService mClusterHomeService;

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private ClusterHalService mClusterHalService;
    @Mock
    private ClusterNavigationService mNavigationService;
    @Mock
    private CarOccupantZoneService mOccupantZoneService;
    @Mock
    private FixedActivityService mFixedActivityService;
    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private Display mClusterDisplay;

    private ClusterState mClusterState;
    private int mClusterStateChanges;
    private byte[] mNavigationState;
    private ICarOccupantZoneCallback mOccupantZoneCallback;

    private IClusterStateListener mClusterStateListener;
    private IClusterNavigationStateListener mClusterNavigationStateListener;

    private class IClusterStateListenerImpl extends IClusterStateListener.Stub {
        @Override
        public void onClusterStateChanged(ClusterState state, int changes) {
            mClusterState = state;
            mClusterStateChanges = changes;
        }
    }

    private class IClusterNavigationStateListenerImpl extends IClusterNavigationStateListener.Stub {
        @Override
        public void onNavigationStateChanged(byte[] navigationState) {
            mNavigationState = navigationState;
        }
    }

    private ComponentName mClusterHomeActivity = new ComponentName("clusterhome.pkg", "activity");

    @Before
    public void setUp() {
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getString(R.string.config_clusterHomeActivity))
                .thenReturn(mClusterHomeActivity.flattenToString());
        when(mResources.getFraction(R.fraction.config_clusterHomeVisibility_minAlpha, 1, 1))
                .thenReturn(1.0f);
        when(mResources.getFraction(R.fraction.config_clusterHomeVisibility_minRendered, 1, 1))
                .thenReturn(0.9f);
        when(mResources.getInteger(R.integer.config_clusterHomeVisibility_stabilityMs))
                .thenReturn(100);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);

        when(mOccupantZoneService.getDisplayIdForDriver(DISPLAY_TYPE_INSTRUMENT_CLUSTER))
                .thenReturn(CLUSTER_DISPLAY_ID)
                .thenReturn(NEW_CLUSTER_DISPLAY_ID);
        doAnswer(invocation -> {
            mOccupantZoneCallback = invocation.getArgument(0);
            assertThat(mOccupantZoneCallback).isNotNull();
            return null;
        }).when(mOccupantZoneService).registerCallback(any(ICarOccupantZoneCallback.class));
        when(mClusterHalService.isServiceEnabled()).thenReturn(true);
        when(mClusterHalService.isLightMode()).thenReturn(false);
        when(mClusterHalService.isNavigationStateSupported()).thenReturn(true);
        when(mClusterHalService.isHeartbeatSupported()).thenReturn(true);
        when(mDisplayManager.getDisplay(CLUSTER_DISPLAY_ID)).thenReturn(mClusterDisplay);
        doAnswer(invocation -> {
            Point size = (Point) invocation.getArgument(0);
            size.set(CLUSTER_WIDTH, CLUSTER_HEIGHT);
            return null;
        }).when(mClusterDisplay).getRealSize(any(Point.class));

        mClusterHomeService = new ClusterHomeService(mContext, mClusterHalService,
                mNavigationService, mOccupantZoneService, mFixedActivityService);
        mClusterHomeService.init();
    }

    private void registerClusterHomeCallbacks() {
        mClusterStateListener = new IClusterStateListenerImpl();
        mClusterNavigationStateListener = new IClusterNavigationStateListenerImpl();
        mClusterHomeService.registerClusterStateListener(mClusterStateListener);
        mClusterHomeService.registerClusterNavigationStateListener(mClusterNavigationStateListener);
    }

    @After
    public void tearDown() throws Exception {
        if (mClusterStateListener != null) {
            mClusterHomeService.unregisterClusterStateListener(mClusterStateListener);
        }
        if (mClusterNavigationStateListener != null) {
            mClusterHomeService.unregisterClusterNavigationStateListener(
                    mClusterNavigationStateListener);
        }
        mClusterHomeService.release();
    }

    @Test
    public void initStartsClusterHomeActivity() {
        // ClusterHomeService.init() was called in setUp().

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ActivityOptions> activityOptionsCaptor = ArgumentCaptor.forClass(
                ActivityOptions.class);
        verify(mFixedActivityService).startFixedActivityModeForDisplayAndUser(
                intentCaptor.capture(), activityOptionsCaptor.capture(),
                eq(CLUSTER_DISPLAY_ID), eq(UserHandle.USER_SYSTEM));
        assertThat(intentCaptor.getValue().getComponent()).isEqualTo(mClusterHomeActivity);
        assertThat(activityOptionsCaptor.getValue().getLaunchDisplayId())
                .isEqualTo(CLUSTER_DISPLAY_ID);
    }

    @Test
    public void init_lightMode_setsDisplayOn() {
        when(mClusterHalService.isLightMode()).thenReturn(true);

        mClusterHomeService.init();

        assertThat(mClusterHomeService.getClusterState().on).isEqualTo(true);
    }

    @Test
    public void occupantZoneConfigChangeRestartsFixedActivityInNewDisplay() throws Exception {
        mOccupantZoneCallback.onOccupantZoneConfigChanged(
                CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ActivityOptions> activityOptionsCaptor = ArgumentCaptor.forClass(
                ActivityOptions.class);
        verify(mFixedActivityService).startFixedActivityModeForDisplayAndUser(
                intentCaptor.capture(), activityOptionsCaptor.capture(),
                eq(NEW_CLUSTER_DISPLAY_ID), eq(UserHandle.USER_SYSTEM));
        assertThat(intentCaptor.getValue().getComponent()).isEqualTo(mClusterHomeActivity);
        assertThat(activityOptionsCaptor.getValue().getLaunchDisplayId())
                .isEqualTo(NEW_CLUSTER_DISPLAY_ID);
    }

    @Test
    public void getClusterStateReturnsClusterState() {
        ClusterState clusterState = mClusterHomeService.getClusterState();

        assertThat(clusterState.on).isFalse();
        assertThat(clusterState.bounds).isEqualTo(new Rect(0, 0, CLUSTER_WIDTH, CLUSTER_HEIGHT));
        assertThat(clusterState.insets).isEqualTo(Insets.NONE);
        assertThat(clusterState.uiType).isEqualTo(UI_TYPE_CLUSTER_HOME);
        assertThat(clusterState.displayId).isEqualTo(CLUSTER_DISPLAY_ID);
    }

    @Test
    public void onSwitchUiSendsDisplayState() {
        registerClusterHomeCallbacks();

        mClusterHomeService.onSwitchUi(UI_TYPE_CLUSTER_MAPS);

        // mClusterState and mClusterStateChanges are updated through mClusterHomeCallback.
        assertThat(mClusterStateChanges).isEqualTo(ClusterHomeManager.CONFIG_UI_TYPE);
        assertThat(mClusterState.uiType).isEqualTo(UI_TYPE_CLUSTER_MAPS);
        assertThat(mClusterHomeService.getClusterState().uiType).isEqualTo(UI_TYPE_CLUSTER_MAPS);
    }

    @Test
    public void displayOnSendsDisplayState() {
        registerClusterHomeCallbacks();

        mClusterHomeService.onDisplayState(ClusterHalService.DISPLAY_ON,
                /* bounds= */ null, /* insets= */ null);

        // mClusterState and mClusterStateChanges are updated through mClusterHomeCallback.
        assertThat(mClusterStateChanges).isEqualTo(ClusterHomeManager.CONFIG_DISPLAY_ON_OFF);
        assertThat(mClusterState.on).isTrue();
        assertThat(mClusterHomeService.getClusterState().on).isTrue();
    }

    @Test
    public void displayBoundsSendsDisplayState() {
        registerClusterHomeCallbacks();

        Rect newBounds = new Rect(10, 10, CLUSTER_WIDTH - 10, CLUSTER_HEIGHT - 10);
        mClusterHomeService.onDisplayState(ClusterHalService.DONT_CARE,
                newBounds, /* insets= */ null);

        // mClusterState and mClusterStateChanges are updated through mClusterHomeCallback.
        assertThat(mClusterStateChanges).isEqualTo(ClusterHomeManager.CONFIG_DISPLAY_BOUNDS);
        assertThat(mClusterState.bounds).isEqualTo(newBounds);
        assertThat(mClusterHomeService.getClusterState().bounds).isEqualTo(newBounds);
    }

    @Test
    public void displayInsetsSendsDisplayState() {
        registerClusterHomeCallbacks();

        Insets newInsets = Insets.of(10, 10, 10, 10);
        mClusterHomeService.onDisplayState(ClusterHalService.DONT_CARE, /* bounds= */ null,
                newInsets);

        // mClusterState and mClusterStateChanges are updated through mClusterHomeCallback.
        assertThat(mClusterStateChanges).isEqualTo(ClusterHomeManager.CONFIG_DISPLAY_INSETS);
        assertThat(mClusterState.insets).isEqualTo(newInsets);
        assertThat(mClusterHomeService.getClusterState().insets).isEqualTo(newInsets);
    }

    @Test
    public void onNavigationStateChangedSendsNavigationState() {
        registerClusterHomeCallbacks();

        Bundle bundle = new Bundle();
        byte[] newNavState = new byte[] {(byte) 1, (byte) 2, (byte) 3};
        bundle.putByteArray(NAV_STATE_PROTO_BUNDLE_KEY, newNavState);
        mClusterHomeService.onNavigationStateChanged(bundle);

        // mNavigationState is updated through mClusterHomeCallback.
        assertThat(mNavigationState).isEqualTo(newNavState);

        ArgumentCaptor<byte[]> navStateCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mClusterHalService).sendNavigationState(navStateCaptor.capture());
        assertThat(navStateCaptor.getValue()).isEqualTo(newNavState);
    }

    @Test
    public void getInstrumentClusterInfoReturnsClusterInfo() {
        CarNavigationInstrumentCluster clusterInfo = mClusterHomeService.getInstrumentClusterInfo();

        assertThat(clusterInfo.getType()).isEqualTo(CLUSTER_TYPE_IMAGE_CODES_ONLY);
    }

    @Test
    public void notifyNavContextOwnerChangedSendsNavigationState() throws
            InvalidProtocolBufferException {
        ContextOwner owner = new ContextOwner(/* uid= */ 123, /* pid= */ 456);
        mClusterHomeService.notifyNavContextOwnerChanged(owner);

        ArgumentCaptor<byte[]> navStateCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mClusterHalService).sendNavigationState(navStateCaptor.capture());
        NavigationStateProto navState = NavigationStateProto.parseFrom(navStateCaptor.getValue());
        assertThat(navState.getServiceStatus())
                .isEqualTo(NavigationStateProto.ServiceStatus.NORMAL);
    }

    @Test
    public void reportStateInvokesHal() {
        byte[] uiAvailability = new byte[] {(byte) 1, (byte) 1, (byte) 0, (byte) 1};
        mClusterHomeService.reportState(UI_TYPE_CLUSTER_MAPS, UI_TYPE_CLUSTER_NONE, uiAvailability);

        ArgumentCaptor<byte[]> uiAvailabilityCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mClusterHalService).reportState(/* onOff= */ anyInt(), /* bounds= */ any(Rect.class),
                any(Insets.class), eq(UI_TYPE_CLUSTER_MAPS), eq(UI_TYPE_CLUSTER_NONE),
                uiAvailabilityCaptor.capture());
        assertThat(uiAvailabilityCaptor.getValue()).isEqualTo(uiAvailability);
    }

    @Test
    public void requestDisplayInvokesHal() {
        mClusterHomeService.requestDisplay(UI_TYPE_CLUSTER_MAPS);

        verify(mClusterHalService).requestDisplay(eq(UI_TYPE_CLUSTER_MAPS));
    }

    @Test
    public void startFixedActivityModeAsUserInvokesFixedActivityService() {
        Intent intent = Intent.makeMainActivity(
                ComponentName.createRelative("test.pkg", "testClusterActivity"));
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        mClusterHomeService.startFixedActivityModeAsUser(
                intent, activityOptions.toBundle(), USER_ID);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ActivityOptions> activityOptionsCaptor = ArgumentCaptor.forClass(
                ActivityOptions.class);
        verify(mFixedActivityService).startFixedActivityModeForDisplayAndUser(
                intentCaptor.capture(), activityOptionsCaptor.capture(),
                eq(CLUSTER_DISPLAY_ID), eq(USER_ID));
        assertThat(intentCaptor.getValue()).isEqualTo(intent);
        assertThat(activityOptionsCaptor.getValue().getLaunchDisplayId()).isEqualTo(
                CLUSTER_DISPLAY_ID);
    }

    @Test
    public void stopFixedActivityModeInvokesFixedActivityService() {
        mClusterHomeService.stopFixedActivityMode();

        verify(mFixedActivityService).stopFixedActivityMode(eq(CLUSTER_DISPLAY_ID));
    }

    @Test
    public void sendHeartbeat_SimpleCase() {
        byte[] appMetadata = null;
        long epochTimeNs = 123456789;

        mClusterHomeService.sendHeartbeat(epochTimeNs, appMetadata);

        verify(mClusterHalService).sendHeartbeat(
                eq(epochTimeNs), eq(/* visibility= */ 0L), eq(EMPTY_BYTE_ARRAY));
    }
}
