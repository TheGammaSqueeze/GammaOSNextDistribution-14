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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import android.car.evs.CarEvsManager;
import android.hardware.automotive.vehicle.EvsServiceState;
import android.hardware.automotive.vehicle.EvsServiceType;
import android.hardware.automotive.vehicle.VehicleProperty;

import com.android.car.hal.test.AidlVehiclePropConfigBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@RunWith(MockitoJUnitRunner.class)
public class EvsHalServiceTest {
    @Mock VehicleHal mVehicleHal;
    @Mock EvsHalService.EvsHalEventListener mListener;

    private static final HalPropConfig EVS_SERVICE_REQUEST =
            new AidlHalPropConfig(
                    AidlVehiclePropConfigBuilder.newBuilder(VehicleProperty.EVS_SERVICE_REQUEST)
                            .build());

    private EvsHalService mEvsHalService;

    private static final int TRUE = 1;
    private static final int FALSE = 0;

    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);

    @Before
    public void setUp() {
        mEvsHalService = new EvsHalService(mVehicleHal);
        mEvsHalService.init();
    }

    @After
    public void tearDown() {
        mEvsHalService.release();
        mEvsHalService = null;
    }

    @Test
    public void takesEvsServiceRequestProperty() {
        Set<HalPropConfig> offeredProps = ImmutableSet.of(
                EVS_SERVICE_REQUEST);

        mEvsHalService.takeProperties(offeredProps);

        assertThat(mEvsHalService.isEvsServiceRequestSupported()).isTrue();
    }

    @Test
    public void requestToStartRearviewViaEvsServiceRequest() {
        subscribeListener(ImmutableSet.of(EVS_SERVICE_REQUEST));

        List<Integer> events = new ArrayList<>();
        doAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                events.add(invocation.getArgument(1) ? TRUE : FALSE);
                return null;
        }).when(mListener).onEvent(anyInt(), anyBoolean());

        dispatchEvsServiceRequest(EvsServiceType.REARVIEW, EvsServiceState.ON);

        assertThat(events.get(0)).isEqualTo(CarEvsManager.SERVICE_TYPE_REARVIEW);
        assertThat(events.get(1)).isEqualTo(TRUE);
    }

    @Test
    public void requestToStopRearviewViaEvsServiceRequest() {
        subscribeListener(ImmutableSet.of(EVS_SERVICE_REQUEST));

        List<Integer> events = new ArrayList<>();
        doAnswer(invocation -> {
                events.add(invocation.getArgument(0));
                events.add(invocation.getArgument(1) ? TRUE : FALSE);
                return null;
        }).when(mListener).onEvent(anyInt(), anyBoolean());

        dispatchEvsServiceRequest(EvsServiceType.REARVIEW, EvsServiceState.OFF);

        assertThat(events.get(0)).isEqualTo(CarEvsManager.SERVICE_TYPE_REARVIEW);
        assertThat(events.get(1)).isEqualTo(FALSE);
    }

    @Test
    public void handleInvalidHalEvents() {
        subscribeListener(ImmutableSet.of(EVS_SERVICE_REQUEST));
        HalPropValue v = mPropValueBuilder.build(VehicleProperty.EVS_SERVICE_REQUEST, /*areaId=*/0);

        // Not type, no state.
        mEvsHalService.onHalEvents(ImmutableList.of(v));
        verify(mListener, never()).onEvent(anyInt(), anyBoolean());

        // Not state.
        v = mPropValueBuilder.build(VehicleProperty.EVS_SERVICE_REQUEST, /*areaId=*/0,
                EvsServiceType.REARVIEW);
        mEvsHalService.onHalEvents(ImmutableList.of(v));
        verify(mListener, never()).onEvent(anyInt(), anyBoolean());
    }

    @Test
    public void getAllSupportedProperties() {
        subscribeListener(ImmutableSet.of(EVS_SERVICE_REQUEST));

        int[] supported = mEvsHalService.getAllSupportedProperties();
        assertThat(supported.length > 0).isTrue();
        assertThat(IntStream.of(supported).anyMatch(x -> x == VehicleProperty.EVS_SERVICE_REQUEST))
                .isTrue();
    }

    // TODO(b/179029031): Adds more tests to verify the surround view service integration.

    private void dispatchEvsServiceRequest(int type, int state) {
        mEvsHalService.onHalEvents(ImmutableList.of(buildEvsServiceRequestProp(type, state)));
    }

    private HalPropValue buildEvsServiceRequestProp(int type, int state) {
        return mPropValueBuilder.build(VehicleProperty.EVS_SERVICE_REQUEST, /*areaId=*/0,
                new int[]{type, state});
    }

    private void subscribeListener(Collection<HalPropConfig> properties) {
        reset(mListener);
        mEvsHalService.takeProperties(properties);
        mEvsHalService.setListener(mListener);

        for (HalPropConfig config : properties) {
            verify(mVehicleHal).subscribePropertySafe(mEvsHalService, config.getPropId());
        }
    }
}
