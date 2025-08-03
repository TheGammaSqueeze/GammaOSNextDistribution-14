/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.apitest;

import static android.hardware.automotive.vehicle.TestVendorProperty.VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyManager;
import android.car.test.CarTestManager;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.util.ArraySet;
import android.util.Log;

import com.android.car.test.TestPropertyAsyncCallback;
import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public final class CarPropertyManagerTest extends CarApiTestBase {
    private static final int EXPECTED_VENDOR_ERROR_CODE = 0x00ab;
    private static final int NUMBER_OF_TEST_CODES = 0x2000;
    // 557862912
    private static final int STARTING_TEST_CODES = 0x3000 | VehiclePropertyGroup.VENDOR
            | VehicleArea.GLOBAL | VehiclePropertyType.INT32;
    private static final int END_TEST_CODES = 0x3000 + NUMBER_OF_TEST_CODES
            | VehiclePropertyGroup.VENDOR | VehicleArea.GLOBAL | VehiclePropertyType.INT32;
    private static final String TAG = CarPropertyManagerTest.class.getSimpleName();

    private final HandlerThread mHandlerThread = new HandlerThread(getClass().getSimpleName());

    private CarPropertyManager mCarPropertyManager;
    private CarTestManager mTestManager;
    private Binder mToken;
    private Handler mHandler;

    @Before
    public void setUp() throws Exception {
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCarPropertyManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        assertThat(mCarPropertyManager).isNotNull();
        mTestManager = getCarService(Car.TEST_SERVICE);
        mToken = new Binder("stop_car_service");
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
    }

    @ApiTest(apis = {"android.car.hardware.property.CarInternalErrorException#getVendorErrorCode"})
    @Test
    public void testGetProperty_withVendorPropertyId_throws() {
        List<CarPropertyConfig> carPropertyConfigList = mCarPropertyManager.getPropertyList(
                new ArraySet(List.of(VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING)));
        assumeTrue("VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING is supported",
                !carPropertyConfigList.isEmpty());
        CarInternalErrorException thrown = assertThrows(CarInternalErrorException.class, () ->
                mCarPropertyManager.getProperty(VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING,
                        /* areaId = */ 0));

        assertThat(thrown.getVendorErrorCode()).isEqualTo(EXPECTED_VENDOR_ERROR_CODE);
    }

    @ApiTest(apis = {"android.car.hardware.property.CarInternalErrorException#getVendorErrorCode"})
    @Test
    public void testSetProperty_withVendorPropertyId_throws() {
        List<CarPropertyConfig> carPropertyConfigList = mCarPropertyManager.getPropertyList(
                new ArraySet(List.of(VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING)));
        assumeTrue("VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING is supported",
                !carPropertyConfigList.isEmpty());
        CarInternalErrorException thrown = assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.setProperty(Integer.class,
                        VENDOR_PROPERTY_FOR_ERROR_CODE_TESTING, /* areaId = */ 0, /* val= */ 0));

        assertThat(thrown.getVendorErrorCode()).isEqualTo(EXPECTED_VENDOR_ERROR_CODE);
    }

    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getPropertyList()"})
    @Test
    public void testGetPropertyListWithLargeNumberOfConfigs() throws Exception {
        List<CarPropertyConfig> carPropertyConfigsListBeforeGenerating =
                mCarPropertyManager.getPropertyList();
        Set<Integer> resultSet = new ArraySet<>();
        for (int i = 0; i < carPropertyConfigsListBeforeGenerating.size(); i++) {
            resultSet.add(carPropertyConfigsListBeforeGenerating.get(i).getPropertyId());
        }
        try {
            for (int i = STARTING_TEST_CODES; i < END_TEST_CODES; i++) {
                assertThat(i).isNotIn(resultSet);
            }
            Log.d(TAG, "Stopping car service for test");
            mTestManager.stopCarService(mToken);
            String result = CarApiTestBase.executeShellCommand(
                    "dumpsys car_service gen-test-vendor-configs");
            Log.d(TAG, "Starting car service for test");
            mTestManager.startCarService(mToken);

            List<CarPropertyConfig> carPropertyConfigsList = mCarPropertyManager.getPropertyList();
            resultSet = new ArraySet<>();
            for (int i = 0; i < carPropertyConfigsList.size(); i++) {
                resultSet.add(carPropertyConfigsList.get(i).getPropertyId());
            }
            assumeTrue("successfully generated vendor configs, VHAL gen-test-vendor-configs "
                            + "debug command is supported",
                    result.equals("successfully generated vendor configs\n"));
            for (int i = STARTING_TEST_CODES; i < END_TEST_CODES; i++) {
                assertThat(i).isIn(resultSet);
            }
        } finally {
            restoreCarService();
        }
    }

    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#setPropertiesAsync"
            + "(List, long, CancellationSignal, Executor, SetPropertyCallback)"})
    @Test
    public void testSetPropertiesAsyncWithLargeNumberRequests() throws Exception {
        try {
            Log.d(TAG, "Stopping car service for test");
            mTestManager.stopCarService(mToken);
            String result = CarApiTestBase.executeShellCommand(
                    "dumpsys car_service gen-test-vendor-configs");
            Log.d(TAG, "Starting car service for test");
            mTestManager.startCarService(mToken);
            assumeTrue("successfully generated vendor configs, VHAL gen-test-vendor-configs "
                            + "debug command is supported",
                    result.equals("successfully generated vendor configs\n"));
            Executor callbackExecutor = new HandlerExecutor(mHandler);
            Set<Integer> setPropertyIds = new ArraySet<>();
            List<CarPropertyManager.SetPropertyRequest<?>> setPropertyRequests = new ArrayList<>();

            for (int i = STARTING_TEST_CODES; i < END_TEST_CODES; i++) {
                CarPropertyManager.SetPropertyRequest setRequest =
                        mCarPropertyManager.generateSetPropertyRequest(
                                i, /* areaId= */ 0, /* value= */ 10);
                setRequest.setWaitForPropertyUpdate(false);
                setPropertyRequests.add(setRequest);
                setPropertyIds.add(setRequest.getRequestId());
            }
            TestPropertyAsyncCallback callback = new TestPropertyAsyncCallback(
                    setPropertyIds);
            mCarPropertyManager.setPropertiesAsync(setPropertyRequests, /* timeoutInMs= */ 1000,
                    /* cancellationSignal= */ null, callbackExecutor, callback);

            callback.waitAndFinish(/* timeoutInMs= */ 3000);
            assertThat(callback.getTestErrors()).isEmpty();
            List<CarPropertyManager.SetPropertyResult> results = callback.getSetResultList();
            assertThat(results).hasSize(NUMBER_OF_TEST_CODES);
            assertThat(callback.getErrorList().size()).isEqualTo(0);
        } finally {
            Log.d(TAG, "restoring car service");
            restoreCarService();
        }
    }

    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getPropertiesAsync"
            + "(List, long, CancellationSignal, Executor, GetPropertyCallback)"})
    @Test
    public void testGetPropertiesAsyncWithLargeNumberRequests() throws Exception {
        try {
            Log.d(TAG, "Stopping car service for test");
            mTestManager.stopCarService(mToken);
            String result = CarApiTestBase.executeShellCommand(
                    "dumpsys car_service gen-test-vendor-configs");
            Log.d(TAG, "Starting car service for test");
            mTestManager.startCarService(mToken);
            assumeTrue("successfully generated vendor configs, VHAL gen-test-vendor-configs "
                            + "debug command is supported",
                    result.equals("successfully generated vendor configs\n"));

            Executor callbackExecutor = new HandlerExecutor(mHandler);
            Set<Integer> getPropertyIds = new ArraySet<>();
            List<CarPropertyManager.GetPropertyRequest> getPropertyRequests = new ArrayList<>();

            for (int i = STARTING_TEST_CODES; i < END_TEST_CODES; i++) {
                CarPropertyManager.GetPropertyRequest getRequest =
                        mCarPropertyManager.generateGetPropertyRequest(
                                i, /* areaId= */ 0);
                getPropertyRequests.add(getRequest);
                getPropertyIds.add(getRequest.getRequestId());
            }
            TestPropertyAsyncCallback callback = new TestPropertyAsyncCallback(
                    getPropertyIds);
            mCarPropertyManager.getPropertiesAsync(getPropertyRequests, /* timeoutInMs= */ 1000,
                    /* cancellationSignal= */ null, callbackExecutor, callback);

            callback.waitAndFinish(/* timeoutInMs= */ 3000);
            assertThat(callback.getTestErrors()).isEmpty();
            List<CarPropertyManager.GetPropertyResult<?>> results = callback.getGetResultList();
            assertThat(results.size()).isEqualTo(NUMBER_OF_TEST_CODES);
            assertThat(callback.getErrorList()).isEmpty();
        } finally {
            Log.d(TAG, "restoring car service");
            restoreCarService();
        }
    }

    private void restoreCarService() throws Exception {
        Log.d(TAG, "Stopping car service for test");
        mTestManager.stopCarService(mToken);
        String result = CarApiTestBase.executeShellCommand(
                "dumpsys car_service restore-vendor-configs");
        assertThat(result.equals("successfully restored vendor configs\n")).isTrue();
        Log.d(TAG, "Starting car service for test");
        mTestManager.startCarService(mToken);
    }
}
