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

import static android.car.VehiclePropertyIds.CLUSTER_DISPLAY_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_SWITCH_UI;

import static com.android.car.hal.ClusterHalService.DISPLAY_ON;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import android.app.ActivityOptions;
import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.ClusterState;
import android.car.hardware.property.VehicleHalStatusCode;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.display.DisplayManager;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.DisplayAddress;

import com.android.car.CarLocalServices;
import com.android.car.ICarImpl;
import com.android.car.MockedCarTestBase;
import com.android.car.am.FixedActivityService;
import com.android.car.hal.test.AidlMockedVehicleHal;
import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.annotations.GuardedBy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ClusterHomeManagerTest extends MockedCarTestBase {
    private static final String TAG = ClusterHomeManagerTest.class.getSimpleName();

    private static final int CONFIG_CLUSTER_HOME_SERVICE_FULL_MODE = 0;
    private static final long TEST_TIMEOUT_MS = 5_000;

    private static final int UI_TYPE_1 = 1;
    private static final int UI_TYPE_2 = 2;
    private static final byte[] UI_AVAILABILITY = new byte[]{(byte) 1, (byte) 0, (byte) 1};

    private static final int BOUNDS_LEFT = 0;
    private static final int BOUNDS_TOP = 1;
    private static final int BOUNDS_RIGHT = 800;
    private static final int BOUNDS_BOTTOM = 601;
    private static final int INSET_LEFT = 20;
    private static final int INSET_TOP = 10;
    private static final int INSET_RIGHT = 780;
    private static final int INSET_BOTTOM = 590;

    // We use DEFAULT_DISPLAY for Cluster testing.
    private static final int CLUSTER_DISPLAY_ID = Display.DEFAULT_DISPLAY;
    private static final String[] ENABLED_OPTIONAL_FEATURES = {
            Car.CLUSTER_HOME_SERVICE
    };

    private ClusterHomeManager mClusterHomeManager;
    private final ClusterPropertyHandler mPropertyHandler = new ClusterPropertyHandler();
    private final CountDownLatch mPropertySetReady = new CountDownLatch(1);
    private final CountDownLatch mClusterStateListenerCalled = new CountDownLatch(1);
    private final CountDownLatch mClusterNavigationStateListenerCalled = new CountDownLatch(1);

    private ClusterStateListenerImpl mClusterStateListener = new ClusterStateListenerImpl();
    private ClusterNavigationStateListenerImpl mClusterNavigationStateListener =
            new ClusterNavigationStateListenerImpl();
    private ClusterState mState;
    private int mChanges = 0;

    private FixedActivityService mFixedActivityService;
    @Captor private ArgumentCaptor<Intent> mIntentCaptor;
    @Captor private ArgumentCaptor<ActivityOptions> mActivityOptionsCaptor;
    @Captor private ArgumentCaptor<Integer> mDisplayIdCaptor;
    @Captor private ArgumentCaptor<Integer> mUserIdCaptor;

    @Rule
    public final TestName mTestName = new TestName();

    private boolean isNoHalPropertyTest() {
        return mTestName.getMethodName().contains("testNoHalProperty");
    }

    @Override
    protected void configureMockedHal() {
        if (isNoHalPropertyTest()) return;

        addAidlProperty(VehicleProperty.CLUSTER_SWITCH_UI)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addAidlProperty(VehicleProperty.CLUSTER_DISPLAY_STATE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addAidlProperty(VehicleProperty.CLUSTER_REPORT_STATE, mPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addAidlProperty(VehicleProperty.CLUSTER_REQUEST_DISPLAY, mPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addAidlProperty(VehicleProperty.CLUSTER_NAVIGATION_STATE, mPropertyHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
    }

    @Override
    protected void configureResourceOverrides(MockedCarTestBase.MockResources resources) {
        super.configureResourceOverrides(resources);
        StringBuilder occupantDisplayMapping = new StringBuilder();
        occupantDisplayMapping.append("displayPort=");
        occupantDisplayMapping.append(getClusterDisplayPort());
        occupantDisplayMapping.append(",displayType=INSTRUMENT_CLUSTER,occupantZoneId=0");
        resources.overrideResource(com.android.car.R.array.config_occupant_display_mapping,
                new String[]{occupantDisplayMapping.toString()});
        resources.overrideResource(com.android.car.R.array.config_allowed_optional_car_features,
                ENABLED_OPTIONAL_FEATURES);
        resources.overrideResource(com.android.car.R.integer.config_clusterHomeServiceMode,
                CONFIG_CLUSTER_HOME_SERVICE_FULL_MODE);
    }

    private int getClusterDisplayPort() {
        DisplayManager displayManager = getTestContext().getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(CLUSTER_DISPLAY_ID);
        DisplayAddress address = (DisplayAddress.Physical) defaultDisplay.getAddress();
        if (!(address instanceof DisplayAddress.Physical)) {
            throw new IllegalStateException("Default display is not a physical display");
        }
        DisplayAddress.Physical physicalAddress = (DisplayAddress.Physical) address;
        return physicalAddress.getPort();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mClusterHomeManager = (ClusterHomeManager) getCar().getCarManager(Car.CLUSTER_HOME_SERVICE);
        if (!isNoHalPropertyTest() && mClusterHomeManager != null) {
            mClusterHomeManager.registerClusterStateListener(
                    getContext().getMainExecutor(), mClusterStateListener);

            mClusterHomeManager.registerClusterNavigationStateListener(
                    getContext().getMainExecutor(), mClusterNavigationStateListener);
        }
    }

    @Override
    protected void spyOnBeforeCarImplInit(ICarImpl carImpl) {
        mFixedActivityService = CarLocalServices.getService(FixedActivityService.class);
        ExtendedMockito.spyOn(mFixedActivityService);

        doReturn(true).when(mFixedActivityService).startFixedActivityModeForDisplayAndUser(
                mIntentCaptor.capture(), mActivityOptionsCaptor.capture(),
                mDisplayIdCaptor.capture(), mUserIdCaptor.capture());
    }

    @Override
    public void tearDown() throws Exception {
        if (!isNoHalPropertyTest() && mClusterHomeManager != null) {
            mClusterHomeManager.unregisterClusterStateListener(mClusterStateListener);
            mClusterHomeManager
                    .unregisterClusterNavigationStateListener(mClusterNavigationStateListener);
        }
        super.tearDown();
    }

    @Test
    public void testClusterSwitchUi() throws InterruptedException {
        getAidlMockedVehicleHal().injectEvent(createSwitchUiEvent(UI_TYPE_2));
        mClusterStateListenerCalled.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(mState).isNotNull();
        assertThat(mState.uiType).isEqualTo(UI_TYPE_2);
        assertThat(mChanges).isEqualTo(ClusterHomeManager.CONFIG_UI_TYPE);
    }

    @Test
    public void testClusterSwitchUi_InvalidProp() throws InterruptedException {
        VehiclePropValue event = new VehiclePropValue();
        event.prop = CLUSTER_SWITCH_UI;
        event.value = new RawPropValues();
        event.value.floatValues = new float[]{0f};
        getAidlMockedVehicleHal().injectEvent(event);
        assertThat(mClusterStateListenerCalled.getCount()).isEqualTo(1);
    }

    @Test
    public void testClusterState() throws InterruptedException {
        getAidlMockedVehicleHal().injectEvent(createDisplayStateEvent(
                DISPLAY_ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM));
        mClusterStateListenerCalled.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(mState).isNotNull();
        assertThat(mState.on).isEqualTo(true);
        assertThat(mState.bounds.left).isEqualTo(BOUNDS_LEFT);
        assertThat(mState.bounds.top).isEqualTo(BOUNDS_TOP);
        assertThat(mState.bounds.right).isEqualTo(BOUNDS_RIGHT);
        assertThat(mState.bounds.bottom).isEqualTo(BOUNDS_BOTTOM);
        assertThat(mState.insets.left).isEqualTo(INSET_LEFT);
        assertThat(mState.insets.top).isEqualTo(INSET_TOP);
        assertThat(mState.insets.right).isEqualTo(INSET_RIGHT);
        assertThat(mState.insets.bottom).isEqualTo(INSET_BOTTOM);
        assertThat(mChanges).isEqualTo(ClusterHomeManager.CONFIG_DISPLAY_ON_OFF
                | ClusterHomeManager.CONFIG_DISPLAY_BOUNDS
                | ClusterHomeManager.CONFIG_DISPLAY_INSETS);

        ClusterState state2 = mClusterHomeManager.getClusterState();
        assertThat(state2).isNotNull();
        // The class generated from aidl doesn't have the proper equals() method yet.
        assertThat(state2.on).isEqualTo(mState.on);
        assertThat(state2.bounds).isEqualTo(mState.bounds);
        assertThat(state2.insets).isEqualTo(mState.insets);
    }

    @Test
    public void testClusterState_InvalidProp() throws InterruptedException {
        VehiclePropValue event = new VehiclePropValue();
        event.prop = CLUSTER_DISPLAY_STATE;
        event.value = new RawPropValues();
        // Only one int value is set while 9 is expected. This should be handled properly.
        event.value.int32Values = new int[]{DISPLAY_ON};
        getAidlMockedVehicleHal().injectEvent(event);
        assertThat(mClusterStateListenerCalled.getCount()).isEqualTo(1);

        event = createDisplayStateEvent(
                DISPLAY_ON, BOUNDS_LEFT, BOUNDS_TOP, BOUNDS_RIGHT, BOUNDS_BOTTOM,
                INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM);
        // Remove the last value so we have one value missing.
        int[] newValues = new int[8];
        for (int i = 0; i < 8; i++) {
            newValues[i] = event.value.int32Values[i];
        }
        event.value.int32Values = newValues;
        getAidlMockedVehicleHal().injectEvent(event);
        assertThat(mClusterStateListenerCalled.getCount()).isEqualTo(1);
    }

    @Test
    public void testClusterReportState() throws InterruptedException {
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        mPropertySetReady.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        VehiclePropValue value = mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE);
        assertThat(value.prop).isEqualTo(VehicleProperty.CLUSTER_REPORT_STATE);
        assertThat(value.value.int32Values[9]).isEqualTo(UI_TYPE_1);
        assertThat(value.value.int32Values[10]).isEqualTo(UI_TYPE_2);
        assertThat(value.value.byteValues).isEqualTo(new byte[]{
                (byte) UI_AVAILABILITY[0], (byte) UI_AVAILABILITY[1], (byte) UI_AVAILABILITY[2]});
    }

    @Test
    public void testClusterRequestDisplay() throws InterruptedException {
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        mPropertySetReady.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        VehiclePropValue value = mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY);
        assertThat(value.prop).isEqualTo(VehicleProperty.CLUSTER_REQUEST_DISPLAY);
        assertThat(value.value.int32Values).isEqualTo(new int[]{UI_TYPE_1});
    }

    @Test
    public void testNoHalProperty_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.requestDisplay(UI_TYPE_1));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.registerClusterStateListener(
                        getContext().getMainExecutor(), mClusterStateListener));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.unregisterClusterStateListener(mClusterStateListener));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager.registerClusterNavigationStateListener(
                        getContext().getMainExecutor(), mClusterNavigationStateListener));
        assertThrows(IllegalStateException.class,
                () -> mClusterHomeManager
                        .unregisterClusterNavigationStateListener(mClusterNavigationStateListener));
    }

    @Test
    public void testClusterRequestDisplayErrorStatusFromHal() {
        // All exceptions are handled in ClusterHalService.
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REQUEST_DISPLAY,
                VehicleHalStatusCode.STATUS_TRY_AGAIN);
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REQUEST_DISPLAY,
                VehicleHalStatusCode.STATUS_INVALID_ARG);
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REQUEST_DISPLAY,
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE);
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REQUEST_DISPLAY,
                VehicleHalStatusCode.STATUS_ACCESS_DENIED);
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REQUEST_DISPLAY,
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        mClusterHomeManager.requestDisplay(UI_TYPE_1);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REQUEST_DISPLAY)).isNull();

        // Clear the status.
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REQUEST_DISPLAY,
                VehicleHalStatusCode.STATUS_OK);
    }

    @Test
    public void testClusterReportStateErrorStatusFromHal() {
        // All exceptions are handled in ClusterHalService.
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REPORT_STATE,
                VehicleHalStatusCode.STATUS_TRY_AGAIN);
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REPORT_STATE,
                VehicleHalStatusCode.STATUS_INVALID_ARG);
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REPORT_STATE,
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE);
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REPORT_STATE,
                VehicleHalStatusCode.STATUS_ACCESS_DENIED);
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE)).isNull();
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REPORT_STATE,
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        mClusterHomeManager.reportState(UI_TYPE_1, UI_TYPE_2, UI_AVAILABILITY);
        assertThat(mPropertyHandler.peek(VehicleProperty.CLUSTER_REPORT_STATE)).isNull();

        // Clear the status.
        mPropertyHandler.setStatus(
                VehicleProperty.CLUSTER_REPORT_STATE,
                VehicleHalStatusCode.STATUS_OK);
    }

    @Test
    public void testStartFixedActivityModeAsUser() {
        ComponentName testActivity = ComponentName.createRelative("testPkg", "testActivity");
        Intent intent = Intent.makeMainActivity(testActivity);
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        int userId = 99;

        assertThat(mClusterHomeManager.startFixedActivityModeAsUser(
                intent, activityOptions.toBundle(), userId)).isTrue();

        assertThat(mIntentCaptor.getValue().getComponent()).isEqualTo(testActivity);
        assertThat(mActivityOptionsCaptor.getValue().getLaunchDisplayId())
                .isEqualTo(CLUSTER_DISPLAY_ID);
        assertThat(mDisplayIdCaptor.getValue()).isEqualTo(CLUSTER_DISPLAY_ID);
        assertThat(mUserIdCaptor.getValue()).isEqualTo(userId);
    }

    @Test
    public void testStopFixedActivityMode() {
        doNothing().when(mFixedActivityService).stopFixedActivityMode(mDisplayIdCaptor.capture());

        mClusterHomeManager.stopFixedActivityMode();

        assertThat(mDisplayIdCaptor.getValue()).isEqualTo(CLUSTER_DISPLAY_ID);
    }

    private class ClusterPropertyHandler implements AidlMockedVehicleHal.VehicleHalPropertyHandler {
        SparseArray<VehiclePropValue> mPropValueMap = new SparseArray<>();

        @GuardedBy("mLock")
        SparseIntArray mPropStatusMap = new SparseIntArray();

        private final Object mLock = new Object();

        @Override
        public void onPropertySet(VehiclePropValue value) {
            synchronized (mLock) {
                int status = mPropStatusMap.get(value.prop, VehicleHalStatusCode.STATUS_OK);
                if (status != VehicleHalStatusCode.STATUS_OK) {
                    // This would turn into returned status code.
                    throw new ServiceSpecificException(mPropStatusMap.get(value.prop));
                }
                mPropValueMap.put(value.prop, value);
            }
            mPropertySetReady.countDown();
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            VehiclePropValue currentValue = null;
            synchronized (mLock) {
                int status = mPropStatusMap.get(value.prop, VehicleHalStatusCode.STATUS_OK);
                if (status != VehicleHalStatusCode.STATUS_OK) {
                    // This would turn into returned status code.
                    throw new ServiceSpecificException(mPropStatusMap.get(value.prop));
                }
                currentValue = mPropValueMap.get(value.prop);
            }
            // VNS will call get method when subscribe is called, just return empty value.
            return currentValue != null ? currentValue : value;
        }

        public VehiclePropValue peek(int property) {
            synchronized (mLock) {
                return mPropValueMap.get(property);
            }
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property " + property + " sampleRate " + sampleRate);
            if (mPropValueMap.get(property) == null) {
                synchronized (mLock) {
                    Log.d(TAG, "onPropertySubscribe add placeholder property: " + property);
                    VehiclePropValue placeholderValue =
                            AidlVehiclePropValueBuilder.newBuilder(property)
                                    .setAreaId(0)
                                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                                    .addIntValues(1)
                                    .addFloatValues(1)
                                    .build();
                    mPropValueMap.put(property, placeholderValue);
                }
            }
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }

        public void setStatus(int prop, int status) {
            synchronized (mLock) {
                mPropStatusMap.put(prop, status);
            }
        }
    }

    private class ClusterStateListenerImpl implements ClusterHomeManager.ClusterStateListener {
        public void onClusterStateChanged(ClusterState state, int changes) {
            mState = state;
            mChanges = changes;
            mClusterStateListenerCalled.countDown();
        }
    }

    private class ClusterNavigationStateListenerImpl implements
            ClusterHomeManager.ClusterNavigationStateListener {
        @Override
        public void onNavigationStateChanged(byte[] unusedNavigationState) {
            mClusterNavigationStateListenerCalled.countDown();
        }
    }

    private static VehiclePropValue createSwitchUiEvent(int uiType) {
        VehiclePropValue event = new VehiclePropValue();
        event.prop = CLUSTER_SWITCH_UI;
        event.value = new RawPropValues();
        event.value.int32Values = new int[]{uiType};
        return event;
    }

    private static VehiclePropValue createDisplayStateEvent(int onOff,
            int boundsLeft, int boundsTop, int boundsRight, int boundsBottom,
            int insetsLeft, int insetsTop, int insetSRight, int insetSBottom) {
        VehiclePropValue event = new VehiclePropValue();
        event.prop = CLUSTER_DISPLAY_STATE;
        event.value = new RawPropValues();
        event.value.int32Values = new int[]{onOff, boundsLeft, boundsTop, boundsRight,
                boundsBottom, insetsLeft, insetsTop, insetSRight, insetSBottom};
        return event;
    }
}
