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

package com.android.car.hal;

import static android.car.VehiclePropertyIds.CLUSTER_DISPLAY_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_HEARTBEAT;
import static android.car.VehiclePropertyIds.CLUSTER_NAVIGATION_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_REPORT_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_REQUEST_DISPLAY;
import static android.car.VehiclePropertyIds.CLUSTER_SWITCH_UI;

import static com.android.car.hal.ClusterHalService.DONT_CARE;
import static com.android.car.hal.VehicleHalTestingHelper.newSubscribableConfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;

import com.android.car.R;
import com.android.car.hal.ClusterHalService.ClusterHalEventCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ClusterHalServiceTest {
    private static final int NOT_ASSIGNED = -1;

    private static final int ON = 1;
    private static final int UI_TYPE_1 = 1;
    private static final int UI_TYPE_2 = 2;
    private static final byte[] UI_AVAILABILITY = new byte[] {(byte) 1, (byte) 1, (byte) 0};

    private static final int BOUNDS_LEFT = 0;
    private static final int BOUNDS_TOP = 1;
    private static final int BOUNDS_RIGHT = 800;
    private static final int BOUNDS_BOTTOM = 601;
    private static final int INSET_LEFT = 20;
    private static final int INSET_TOP = 10;
    private static final int INSET_RIGHT = 780;
    private static final int INSET_BOTTOM = 590;

    private static final HalPropValueBuilder PROP_VALUE_BUILDER = new HalPropValueBuilder(
            /*isAidl=*/true);

    @Mock
    Context mMockContext;
    @Mock
    Resources mMockResources;
    @Mock
    VehicleHal mVehicleHal;
    @Captor
    ArgumentCaptor<HalPropValue> mPropCaptor;

    private ClusterHalService mClusterHalService;

    int mUiType = NOT_ASSIGNED;
    int mOnOff = NOT_ASSIGNED;
    Rect mBounds = null;
    Insets mInsets = null;

    private final ClusterHalEventCallback mHalEventListener = new ClusterHalEventCallback() {
        public void onSwitchUi(int uiType) {
            mUiType = uiType;
        }

        public void onDisplayState(int onOff, Rect bounds, Insets insets) {
            mOnOff = onOff;
            mBounds = bounds;
            mInsets = insets;
        }
    };

    private ArrayList<Integer> getIntValues(HalPropValue value) {
        ArrayList<Integer> intValues = new ArrayList<Integer>();
        for (int i = 0; i < value.getInt32ValuesSize(); i++) {
            intValues.add(value.getInt32Value(i));
        }
        return intValues;
    }

    private static List<HalPropConfig> getFullProperties() {
        return Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE),
                newSubscribableConfig(CLUSTER_REQUEST_DISPLAY),
                newSubscribableConfig(CLUSTER_NAVIGATION_STATE));
    }

    private static List<HalPropConfig> getCoreProperties() {
        return Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE),
                newSubscribableConfig(CLUSTER_REQUEST_DISPLAY));
    }

    private ClusterHalService createLightModeServiceWithProperties(List<HalPropConfig> properties) {
        when(mMockResources.getInteger(R.integer.config_clusterHomeServiceMode)).thenReturn(1);
        ClusterHalService lightModeService = new ClusterHalService(mMockContext, mVehicleHal);
        lightModeService.takeProperties(properties);
        lightModeService.setCallback(mHalEventListener);

        return lightModeService;
    }

    @Before
    public void setUp() {
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(R.integer.config_clusterHomeServiceMode)).thenReturn(0);
        when(mVehicleHal.getHalPropValueBuilder()).thenReturn(PROP_VALUE_BUILDER);
        mClusterHalService = new ClusterHalService(mMockContext, mVehicleHal);
        mClusterHalService.setCallback(mHalEventListener);
    }

    @After
    public void tearDown() {
        mClusterHalService.release();
        mClusterHalService = null;
    }

    @Test
    public void testIsServiceEnabled_fullMode_coreProperties() {
        mClusterHalService.takeProperties(getCoreProperties());

        assertThat(mClusterHalService.isServiceEnabled()).isTrue();
    }

    @Test
    public void testIsServiceEnabled_fullMode_partialProperties() {
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_DISPLAY_STATE)));

        assertThat(mClusterHalService.isServiceEnabled()).isFalse();
    }

    @Test
    public void testIsServiceEnabled_fullMode_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        assertThat(mClusterHalService.isServiceEnabled()).isFalse();
    }

    @Test
    public void testIsServiceEnabled_lightMode_partialProperties() {
        ClusterHalService lightModeService = createLightModeServiceWithProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_DISPLAY_STATE)));

        assertThat(lightModeService.isServiceEnabled()).isTrue();
    }

    @Test
    public void testIsServiceEnabled_lightMode_noProperties() {
        ClusterHalService lightModeService = createLightModeServiceWithProperties(Arrays.asList());

        assertThat(lightModeService.isServiceEnabled()).isTrue();
    }

    @Test
    public void testInit_subscribePropertySafe_coreProperties() {
        mClusterHalService.takeProperties(getCoreProperties());

        mClusterHalService.init();

        verify(mVehicleHal).subscribePropertySafe(mClusterHalService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal).subscribePropertySafe(mClusterHalService, CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testInit_subscribePropertySafe_lightMode_coreProperties() {
        ClusterHalService lightModeService =
                createLightModeServiceWithProperties(getCoreProperties());

        lightModeService.init();

        verify(mVehicleHal, times(0)).subscribePropertySafe(lightModeService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal, times(0)).subscribePropertySafe(lightModeService,
                CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testInit_subscribePropertySafe_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.init();

        verify(mVehicleHal, times(0)).subscribePropertySafe(mClusterHalService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal, times(0)).subscribePropertySafe(mClusterHalService,
                CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testInit_lightMode_subscribePropertySafe_noProperties() {
        ClusterHalService lightModeService = createLightModeServiceWithProperties(Arrays.asList());

        lightModeService.init();

        verify(mVehicleHal, times(0)).subscribePropertySafe(lightModeService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal, times(0)).subscribePropertySafe(lightModeService,
                CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testInit_subscribePropertySafe_partialProperties() {
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_DISPLAY_STATE)));

        mClusterHalService.init();

        verify(mVehicleHal, times(0)).subscribePropertySafe(mClusterHalService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal, times(0)).subscribePropertySafe(mClusterHalService,
                CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testInit_lightMode_subscribePropertySafe_partialProperties() {
        ClusterHalService lightModeService = createLightModeServiceWithProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_DISPLAY_STATE)));

        lightModeService.init();

        verify(mVehicleHal, times(0)).subscribePropertySafe(lightModeService, CLUSTER_SWITCH_UI);
        verify(mVehicleHal, times(0)).subscribePropertySafe(lightModeService,
                CLUSTER_DISPLAY_STATE);
    }

    @Test
    public void testTakeProperties_fullMode_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        assertThat(mClusterHalService.isFullModeEnabled()).isFalse();
        assertThat(mClusterHalService.isServiceEnabled()).isFalse();
        assertThat(mClusterHalService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_lightMode_noProperties() {
        ClusterHalService lightModeService = createLightModeServiceWithProperties(Arrays.asList());

        assertThat(lightModeService.isFullModeEnabled()).isFalse();
        assertThat(lightModeService.isServiceEnabled()).isTrue();
        assertThat(lightModeService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_fullMode_partialProperties() {
        mClusterHalService.takeProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE)));

        assertThat(mClusterHalService.isFullModeEnabled()).isFalse();
        assertThat(mClusterHalService.isServiceEnabled()).isFalse();
        assertThat(mClusterHalService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_lightMode_partialProperties() {
        ClusterHalService lightModeService = createLightModeServiceWithProperties(Arrays.asList(
                newSubscribableConfig(CLUSTER_SWITCH_UI),
                newSubscribableConfig(CLUSTER_DISPLAY_STATE),
                newSubscribableConfig(CLUSTER_REPORT_STATE)));

        assertThat(lightModeService.isFullModeEnabled()).isFalse();
        assertThat(lightModeService.isServiceEnabled()).isTrue();
        assertThat(lightModeService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_fullMode_coreProperties() {
        mClusterHalService.takeProperties(getCoreProperties());

        assertThat(mClusterHalService.isFullModeEnabled()).isTrue();
        assertThat(mClusterHalService.isServiceEnabled()).isTrue();
        assertThat(mClusterHalService.isNavigationStateSupported()).isFalse();
    }

    @Test
    public void testTakeProperties_fullMode_fullProperties() {
        mClusterHalService.takeProperties(getFullProperties());

        assertThat(mClusterHalService.isFullModeEnabled()).isTrue();
        assertThat(mClusterHalService.isServiceEnabled()).isTrue();
        assertThat(mClusterHalService.isNavigationStateSupported()).isTrue();
    }

    @Test
    public void testTakeProperties_lightMode_fullProperties() {
        ClusterHalService lightModeService =
                createLightModeServiceWithProperties(getFullProperties());

        assertThat(lightModeService.isFullModeEnabled()).isFalse();
        assertThat(lightModeService.isServiceEnabled()).isTrue();
        assertThat(lightModeService.isNavigationStateSupported()).isTrue();
    }

    private static HalPropValue createSwitchUiEvent(int uiType) {
        HalPropValue event = PROP_VALUE_BUILDER.build(CLUSTER_SWITCH_UI, /*areaId=*/0, uiType);
        return event;
    }

    private static HalPropValue createDisplayStateEvent(int onOff,
            int boundsLeft, int boundsTop, int boundsRight, int boundsBottom,
            int insetsLeft, int insetsTop, int insetSRight, int insetSBottom) {
        HalPropValue event = PROP_VALUE_BUILDER.build(CLUSTER_DISPLAY_STATE, /*areaId=*/0,
                new int[]{onOff, boundsLeft, boundsTop, boundsRight, boundsBottom, insetsLeft,
                        insetsTop, insetSRight, insetSBottom});
        return event;
    }

    @Test
    public void testOnSwitchUi() {
        mClusterHalService.takeProperties(getCoreProperties());

        mClusterHalService.onHalEvents(Arrays.asList(createSwitchUiEvent(UI_TYPE_1)));

        assertThat(mUiType).isEqualTo(UI_TYPE_1);
    }

    @Test
    public void testOnSwitchUi_noListener() {
        mClusterHalService.takeProperties(getCoreProperties());
        mClusterHalService.setCallback(null);

        mClusterHalService.onHalEvents(Arrays.asList(createSwitchUiEvent(UI_TYPE_1)));

        assertThat(mUiType).isEqualTo(NOT_ASSIGNED);
    }

    @Test
    public void testOnSwitchUi_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.onHalEvents(Arrays.asList(createSwitchUiEvent(UI_TYPE_1)));

        assertThat(mUiType).isEqualTo(NOT_ASSIGNED);
    }

    @Test
    public void testOnDisplayState() {
        mClusterHalService.takeProperties(getCoreProperties());

        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(ON);
        assertThat(mBounds.left).isEqualTo(BOUNDS_LEFT);
        assertThat(mBounds.top).isEqualTo(BOUNDS_TOP);
        assertThat(mBounds.right).isEqualTo(BOUNDS_RIGHT);
        assertThat(mBounds.bottom).isEqualTo(BOUNDS_BOTTOM);
        assertThat(mInsets.left).isEqualTo(INSET_LEFT);
        assertThat(mInsets.top).isEqualTo(INSET_TOP);
        assertThat(mInsets.right).isEqualTo(INSET_RIGHT);
        assertThat(mInsets.bottom).isEqualTo(INSET_BOTTOM);
    }

    @Test
    public void testOnDisplayState_DontAcceptPartialDontCare_Bounds() {
        mClusterHalService.takeProperties(getCoreProperties());
        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, DONT_CARE,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(ON);
        assertThat(mBounds).isNull();
        assertThat(mInsets.left).isEqualTo(INSET_LEFT);
        assertThat(mInsets.top).isEqualTo(INSET_TOP);
        assertThat(mInsets.right).isEqualTo(INSET_RIGHT);
        assertThat(mInsets.bottom).isEqualTo(INSET_BOTTOM);
    }

    @Test
    public void testOnDisplayState_DontAcceptPartialDontCare_Inset() {
        mClusterHalService.takeProperties(getCoreProperties());
        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, DONT_CARE)));

        assertThat(mOnOff).isEqualTo(ON);
        assertThat(mBounds.left).isEqualTo(BOUNDS_LEFT);
        assertThat(mBounds.top).isEqualTo(BOUNDS_TOP);
        assertThat(mBounds.right).isEqualTo(BOUNDS_RIGHT);
        assertThat(mBounds.bottom).isEqualTo(BOUNDS_BOTTOM);
        assertThat(mInsets).isNull();
    }

    @Test
    public void testOnDisplayState_noListener() {
        mClusterHalService.takeProperties(getCoreProperties());
        mClusterHalService.setCallback(null);

        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(NOT_ASSIGNED);
        assertThat(mBounds).isNull();
        assertThat(mInsets).isNull();
    }

    @Test
    public void testOnDisplayState_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.onHalEvents(Arrays.asList(
                createDisplayStateEvent(ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                        INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM)));

        assertThat(mOnOff).isEqualTo(NOT_ASSIGNED);
        assertThat(mBounds).isNull();
        assertThat(mInsets).isNull();
    }

    @Test
    public void testReportState() {
        mClusterHalService.takeProperties(getCoreProperties());
        mClusterHalService.reportState(
                ON, new Rect(BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM),
                Insets.of(INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM),
                UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);

        verify(mVehicleHal).set(mPropCaptor.capture());
        HalPropValue prop = mPropCaptor.getValue();
        assertThat(prop.getPropId()).isEqualTo(CLUSTER_REPORT_STATE);
        assertThat(getIntValues(prop)).containsExactly(
                ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM, UI_TYPE_1, UI_TYPE_2);
        assertThat(prop.getByteArray()).asList().containsExactly(
                (Byte) UI_AVAILABILITY[0], (Byte) UI_AVAILABILITY[1], (Byte) UI_AVAILABILITY[2]);
    }

    @Test
    public void testReportState_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        assertThrows(IllegalStateException.class,
                () -> mClusterHalService.reportState(
                        ON, new Rect(BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM),
                        Insets.of(INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM),
                        UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY));
    }

    @Test
    public void testReportState_lightMode_fullProperties() {
        ClusterHalService lightModeService =
                createLightModeServiceWithProperties(getFullProperties());

        assertThrows(IllegalStateException.class,
                () -> lightModeService.reportState(
                        ON, new Rect(BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM),
                        Insets.of(INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM),
                        UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY));
    }

    @Test
    public void testRequestDisplay() {
        mClusterHalService.takeProperties(getCoreProperties());
        mClusterHalService.requestDisplay(UI_TYPE_2);

        verify(mVehicleHal).set(mPropCaptor.capture());
        HalPropValue prop = mPropCaptor.getValue();
        assertThat(prop.getPropId()).isEqualTo(CLUSTER_REQUEST_DISPLAY);
        assertThat(getIntValues(prop)).containsExactly(UI_TYPE_2);
    }

    @Test
    public void testRequestDisplay_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        assertThrows(IllegalStateException.class,
                () -> mClusterHalService.requestDisplay(UI_TYPE_2));
    }

    @Test
    public void testRequestDisplay_lightMode_fullProperties() {
        ClusterHalService lightModeService =
                createLightModeServiceWithProperties(getFullProperties());

        assertThrows(IllegalStateException.class,
                () -> lightModeService.requestDisplay(UI_TYPE_2));
    }

    @Test
    public void testSendNavigationState() {
        mClusterHalService.takeProperties(getFullProperties());
        mClusterHalService.sendNavigationState(new byte[]{1, 2, 3, 4});

        verify(mVehicleHal).set(mPropCaptor.capture());
        HalPropValue prop = mPropCaptor.getValue();
        assertThat(prop.getPropId()).isEqualTo(CLUSTER_NAVIGATION_STATE);
        assertThat(prop.getByteArray()).asList()
                .containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    public void testSendNavigationState_noProperties() {
        mClusterHalService.takeProperties(Arrays.asList());

        mClusterHalService.sendNavigationState(new byte[]{1, 2, 3, 4});

        verify(mVehicleHal, times(0)).set(mPropCaptor.capture());
    }

    @Test
    public void testSendHeartbeat() {
        long epochTimeNs = 123456789;
        long visibility = 1;
        byte[] appMetadata = new byte[]{3, 2, 0, 1};
        mClusterHalService.sendHeartbeat(epochTimeNs, visibility, appMetadata);

        verify(mVehicleHal).set(mPropCaptor.capture());
        HalPropValue prop = mPropCaptor.getValue();
        assertThat(prop.getPropId()).isEqualTo(CLUSTER_HEARTBEAT);
        assertThat(prop.getInt64ContainerArray()).asList()
                .containsExactly((long) epochTimeNs, (long) visibility);
        assertThat(prop.getByteArray()).asList()
                .containsExactly((byte) 3, (byte) 2, (byte) 0, (byte) 1);
    }

}
