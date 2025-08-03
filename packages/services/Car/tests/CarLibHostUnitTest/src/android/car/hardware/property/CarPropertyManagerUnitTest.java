/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.hardware.property;

import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
import static android.car.VehiclePropertyIds.FUEL_DOOR_OPEN;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;
import static android.car.VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE;
import static android.car.VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION;
import static android.car.VehiclePropertyIds.INITIAL_USER_INFO;
import static android.car.VehiclePropertyIds.INVALID;
import static android.car.hardware.property.CarPropertyManager.GetPropertyResult;
import static android.car.hardware.property.CarPropertyManager.PropertyAsyncError;
import static android.car.hardware.property.CarPropertyManager.SENSOR_RATE_ONCHANGE;
import static android.car.hardware.property.CarPropertyManager.SetPropertyRequest;
import static android.car.hardware.property.CarPropertyManager.SetPropertyResult;

import static com.android.car.internal.property.CarPropertyHelper.SYNC_OP_LIMIT_TRY_AGAIN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.feature.FeatureFlags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.internal.ICarBase;
import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.AsyncPropertyServiceRequestList;
import com.android.car.internal.property.CarPropertyConfigList;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetPropertyConfigListResult;
import com.android.car.internal.property.GetSetValueResult;
import com.android.car.internal.property.GetSetValueResultList;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.car.internal.util.IntArray;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * <p>This class contains unit tests for the {@link CarPropertyManager}.
 */
public final class CarPropertyManagerUnitTest {
    // Required to set the process ID and set the "main" thread for this test, otherwise
    // getMainLooper will return null.
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder()
            .setProcessApp()
            .setProvideMainThread(true)
            .build();

    // Defined as vendor property.
    private static final int VENDOR_CONTINUOUS_PROPERTY = 0x21100111;
    private static final int VENDOR_ON_CHANGE_PROPERTY = 0x21100222;
    private static final int VENDOR_STATIC_PROPERTY = 0x21100333;

    private static final int BOOLEAN_PROP = FUEL_DOOR_OPEN;
    private static final int INT32_PROP = INFO_FUEL_DOOR_LOCATION;
    private static final int INT32_VEC_PROP = INFO_EV_CONNECTOR_TYPE;
    private static final int FLOAT_PROP = HVAC_TEMPERATURE_SET;

    private static final float MIN_UPDATE_RATE_HZ = 10;
    private static final float MAX_UPDATE_RATE_HZ = 100;
    private static final float FIRST_UPDATE_RATE_HZ = 50;
    private static final float LARGER_UPDATE_RATE_HZ = 50.1f;
    private static final float SMALLER_UPDATE_RATE_HZ = 49.9f;

    private static final int VENDOR_ERROR_CODE = 0x2;
    private static final int VENDOR_ERROR_CODE_SHIFT = 16;
    private static final int UNKNOWN_ERROR = -101;

    private static final long TEST_TIMESTAMP = 1234;

    private Handler mMainHandler;

    private CarPropertyConfig mContinuousCarPropertyConfig;
    private CarPropertyConfig mOnChangeCarPropertyConfig;
    private CarPropertyConfig mStaticCarPropertyConfig;
    private final SparseArray<CarPropertyConfig> mCarPropertyConfigsById = new SparseArray<>();
    private final ArraySet<Integer> mUnsupportedPropIds = new ArraySet<>();
    private final ArraySet<Integer> mMissingPermissionPropIds = new ArraySet<>();

    @Mock
    private ICarBase mCar;
    @Mock
    private ApplicationInfo mApplicationInfo;
    @Mock
    private ICarProperty mICarProperty;
    @Mock
    private Context mContext;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback2;
    @Mock
    private CarPropertyManager.GetPropertyCallback mGetPropertyCallback;
    @Mock
    private CarPropertyManager.SetPropertyCallback mSetPropertyCallback;
    @Mock
    private Executor mMockExecutor1;
    @Mock
    private Executor mMockExecutor2;
    @Mock
    private FeatureFlags mFeatureFlags;

    @Captor
    private ArgumentCaptor<Integer> mPropertyIdCaptor;
    @Captor
    private ArgumentCaptor<List> mCarSubscriptionCaptor;
    @Captor
    private ArgumentCaptor<AsyncPropertyServiceRequestList> mAsyncPropertyServiceRequestCaptor;
    @Captor
    private ArgumentCaptor<PropertyAsyncError> mPropertyAsyncErrorCaptor;
    @Captor
    private ArgumentCaptor<GetPropertyResult<?>> mGetPropertyResultCaptor;
    @Captor
    private ArgumentCaptor<SetPropertyResult> mSetPropertyResultCaptor;
    private CarPropertyManager mCarPropertyManager;

    private static int combineErrors(int systemError, int vendorError) {
        return vendorError << VENDOR_ERROR_CODE_SHIFT | systemError;
    }

    private static List<CarPropertyEvent> createErrorCarPropertyEventList() {
        CarPropertyValue<Integer> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_AVAILABLE, 0, -1);
        CarPropertyEvent carPropertyEvent = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_ERROR, value,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        return List.of(carPropertyEvent);
    }

    private static List<CarPropertyEvent> createCarPropertyEventList() {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);
        CarPropertyEvent carPropertyEvent = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, value);
        return List.of(carPropertyEvent);
    }

    private static CarSubscription createCarSubscriptionOption(int propertyId,
            int[] areaId, float updateRateHz) {
        return createCarSubscriptionOption(propertyId, areaId, updateRateHz,
                /* enableVur= */ false);
    }

    private static CarSubscription createCarSubscriptionOption(int propertyId,
            int[] areaId, float updateRateHz, boolean enableVur) {
        CarSubscription options = new CarSubscription();
        options.propertyId = propertyId;
        options.areaIds = areaId;
        options.updateRateHz = updateRateHz;
        options.enableVariableUpdateRate = enableVur;
        return options;
    }

    private void addCarPropertyConfig(CarPropertyConfig config) {
        mCarPropertyConfigsById.put(config.getPropertyId(), config);
    }

    private void setPropIdWithoutPermission(int propId) {
        mCarPropertyConfigsById.remove(propId);
        mMissingPermissionPropIds.add(propId);
    }

    private void setPropIdWithoutConfig(int propId) {
        mCarPropertyConfigsById.remove(propId);
        mUnsupportedPropIds.add(propId);
    }

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);

        mMainHandler = new Handler(Looper.getMainLooper());
        when(mCar.getContext()).thenReturn(mContext);
        when(mCar.getEventHandler()).thenReturn(mMainHandler);
        when(mCar.handleRemoteExceptionFromCarService(any(RemoteException.class), any()))
                .thenAnswer((inv) -> {
                    return inv.getArgument(1);
                });

        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);

        mContinuousCarPropertyConfig = CarPropertyConfig.newBuilder(Integer.class,
                VENDOR_CONTINUOUS_PROPERTY, VEHICLE_AREA_TYPE_GLOBAL)
                .setChangeMode(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                .setMinSampleRate(MIN_UPDATE_RATE_HZ)
                .setMaxSampleRate(MAX_UPDATE_RATE_HZ)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(0)
                        .setSupportVariableUpdateRate(true).build())
                .build();
        mOnChangeCarPropertyConfig = CarPropertyConfig.newBuilder(Integer.class,
                VENDOR_ON_CHANGE_PROPERTY, VEHICLE_AREA_TYPE_GLOBAL)
                .setChangeMode(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(0).build())
                .build();
        mStaticCarPropertyConfig = CarPropertyConfig.newBuilder(Integer.class,
                VENDOR_STATIC_PROPERTY, VEHICLE_AREA_TYPE_GLOBAL)
                .setChangeMode(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(0).build())
                .build();

        when(mICarProperty.getPropertyConfigList(any())).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            int[] propIds = (int[]) args[0];
            GetPropertyConfigListResult result = new GetPropertyConfigListResult();
            IntArray unsupportedPropIds = new IntArray();
            IntArray missingPermissionPropIds = new IntArray();
            List<CarPropertyConfig> configs = new ArrayList<>();
            for (int propId : propIds) {
                if (mUnsupportedPropIds.contains(propId)) {
                    unsupportedPropIds.add(propId);
                    continue;
                }
                if (mMissingPermissionPropIds.contains(propId)) {
                    missingPermissionPropIds.add(propId);
                    continue;
                }
                var config = mCarPropertyConfigsById.get(propId);
                if (config == null) {
                    unsupportedPropIds.add(propId);
                    continue;
                }
                configs.add(config);
            }

            result.carPropertyConfigList = new CarPropertyConfigList(configs);
            result.unsupportedPropIds = unsupportedPropIds.toArray();
            result.missingPermissionPropIds = missingPermissionPropIds.toArray();
            return result;
        });

        addCarPropertyConfig(mContinuousCarPropertyConfig);
        addCarPropertyConfig(mOnChangeCarPropertyConfig);
        addCarPropertyConfig(mStaticCarPropertyConfig);
        when(mICarProperty.getSupportedNoReadPermPropIds(any())).thenReturn(new int[0]);
        mCarPropertyManager = new CarPropertyManager(mCar, mICarProperty);
        // Enable the features.
        when(mFeatureFlags.variableUpdateRate()).thenReturn(true);
        mCarPropertyManager.setFeatureFlags(mFeatureFlags);
    }

    @Test
    public void testGetProperty_returnsValue() throws RemoteException {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0)).isEqualTo(value);
    }

    @Test
    public void testGetProperty_unsupportedProperty_exceptionAtU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getProperty(INVALID, 0));
    }

    @Test
    public void testGetProperty_unsupportedProperty_nullBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThat(mCarPropertyManager.getProperty(INVALID, 0)).isNull();
    }

    @Test
    public void testGetProperty_unsupportedPropertyInSvc_exceptionAtU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                        new IllegalArgumentException());

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_unsupportedPropertyInSvc_nullBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                        new IllegalArgumentException());

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0)).isNull();
    }

    @Test
    public void testGetProperty_syncOpTryAgain() throws RemoteException {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0)).isEqualTo(value);
        verify(mICarProperty, times(2)).getProperty(HVAC_TEMPERATURE_SET, 0);
    }

    @Test
    public void testGetProperty_syncOpTryAgain_exceedRetryCountLimit() throws RemoteException {
        // Car service will throw CarInternalException with version >= R.
        setAppTargetSdk(Build.VERSION_CODES.R);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN));

        assertThrows(CarInternalErrorException.class, () ->
                mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
        verify(mICarProperty, times(10)).getProperty(HVAC_TEMPERATURE_SET, 0);
    }

    private void setAppTargetSdk(int appTargetSdk) {
        mApplicationInfo.targetSdkVersion = appTargetSdk;
        mCarPropertyManager = new CarPropertyManager(mCar, mICarProperty);
    }

    @Test
    public void testGetProperty_notAvailableBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_notAvailableEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE));

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_tryAgainBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0)).isNull();
    }

    @Test
    public void testGetProperty_tryAgainEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_accessDeniedBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_ACCESS_DENIED));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_accessDeniedEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_ACCESS_DENIED));

        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_internalErrorBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_internalErrorEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR));

        assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_internalErrorEqualAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(combineErrors(
                        VehicleHalStatusCode.STATUS_INTERNAL_ERROR, VENDOR_ERROR_CODE)));

        CarInternalErrorException exception = assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));

        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testGetProperty_unknownErrorBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(UNKNOWN_ERROR));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_unknownErrorEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(UNKNOWN_ERROR));

        assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_returnsValueWithUnavailableStatusBeforeU() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_UNAVAILABLE, TEST_TIMESTAMP, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, /*areaId=*/ 0)).isEqualTo(
                new CarPropertyValue<>(HVAC_TEMPERATURE_SET, /*areaId=*/0,
                        CarPropertyValue.STATUS_UNAVAILABLE, TEST_TIMESTAMP, 17.0f));
    }

    @Test
    public void testGetProperty_valueWithUnavailableStatusThrowsAfterU() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_UNAVAILABLE, TEST_TIMESTAMP, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_returnsValueWithErrorStatusBeforeU() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_ERROR, TEST_TIMESTAMP, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, /*areaId=*/ 0)).isEqualTo(
                new CarPropertyValue<>(HVAC_TEMPERATURE_SET, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, TEST_TIMESTAMP, 17.0f));
    }

    @Test
    public void testGetProperty_valueWithErrorStatusThrowsAfterU() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_ERROR, TEST_TIMESTAMP, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_returnsValueWithUnknownStatusBeforeU() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                /*unknown status=*/999, TEST_TIMESTAMP, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, /*areaId=*/ 0)).isEqualTo(
                new CarPropertyValue<>(HVAC_TEMPERATURE_SET, /*areaId=*/0, /*status=*/999,
                        TEST_TIMESTAMP, 17.0f));
    }

    @Test
    public void testGetProperty_valueWithUnknownStatusThrowsAfterU() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                /*unknown status=*/999, TEST_TIMESTAMP, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetPropertyWithClass() throws Exception {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(Float.class, HVAC_TEMPERATURE_SET, 0).getValue())
                .isEqualTo(17.0f);
    }

    @Test
    public void testGetPropertyWithClass_mismatchClass() throws Exception {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getProperty(Integer.class, HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetPropertyWithClass_tryAgainBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.getProperty(Float.class, HVAC_TEMPERATURE_SET, 0))
                .isNull();
    }

    @Test
    public void testGetPropertyWithClass_tryAgainEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mCarPropertyManager.getProperty(Float.class, HVAC_TEMPERATURE_SET, 0));
    }


    @Test
    public void testGetBooleanProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        CarPropertyValue<Boolean> value = new CarPropertyValue<>(BOOLEAN_PROP, 0, true);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0)).isTrue();
    }

    @Test
    public void testGetBooleanProperty_notAvailableBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_notAvailableEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE));

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetProperty_notAvailableEqualAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(combineErrors(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE, VENDOR_ERROR_CODE)));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));

        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testGetProperty_notAvailableDisabledBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED));

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_notAvailableDisabledAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED);
    }

    @Test
    public void testGetProperty_notAvailableDisabledAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(combineErrors(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED, VENDOR_ERROR_CODE)));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));

        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testGetProperty_notAvailableSafetyBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY));

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_notAvailableSafetyAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY);
    }

    @Test
    public void testGetProperty_notAvailableSafetyAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(combineErrors(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY, VENDOR_ERROR_CODE)));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));

        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testGetProperty_notAvailableSpeedHighBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH));

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_notAvailableSpeedHighAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH);
    }

    @Test
    public void testGetProperty_notAvailableSpeedHighAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(combineErrors(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH, VENDOR_ERROR_CODE)));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));

        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testGetProperty_notAvailableSpeedLowBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW));

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetProperty_notAvailableSpeedLowAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW);
    }

    @Test
    public void testGetProperty_notAvailableSpeedLowAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(combineErrors(
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW, VENDOR_ERROR_CODE)));

        PropertyNotAvailableException exception = assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));

        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testGetBooleanProperty_tryAgainBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0)).isFalse();
    }

    @Test
    public void testGetBooleanProperty_tryAgainEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_accessDeniedBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_ACCESS_DENIED));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_accessDeniedEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_ACCESS_DENIED));

        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_internalErrorBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_internalErrorEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR));

        assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_unknownErrorBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(UNKNOWN_ERROR));

        assertThrows(IllegalStateException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetBooleanProperty_unknownErrorEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(BOOLEAN_PROP, 0)).thenThrow(
                new ServiceSpecificException(UNKNOWN_ERROR));

        assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.getBooleanProperty(BOOLEAN_PROP, 0));
    }

    @Test
    public void testGetIntProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        CarPropertyValue<Integer> value = new CarPropertyValue<>(INT32_PROP, 0, 1);
        when(mICarProperty.getProperty(INT32_PROP, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getIntProperty(INT32_PROP, 0)).isEqualTo(1);
    }

    @Test
    public void testGetIntProperty_tryAgainBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(INT32_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.getIntProperty(INT32_PROP, 0)).isEqualTo(0);
    }

    @Test
    public void testGetIntProperty_tryAgainEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(INT32_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mCarPropertyManager.getIntProperty(INT32_PROP, 0));
    }

    @Test
    public void testGetIntProperty_wrongType() throws Exception {
        CarPropertyValue<Integer> value = new CarPropertyValue<>(INT32_PROP, 0, 1);
        when(mICarProperty.getProperty(INT32_PROP, 0)).thenReturn(value);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getFloatProperty(INT32_PROP, 0));
    }

    @Test
    public void testGetIntArrayProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        CarPropertyValue<Float> value = new CarPropertyValue<>(FLOAT_PROP, 0, 1f);
        when(mICarProperty.getProperty(FLOAT_PROP, 0)).thenReturn(value);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getIntArrayProperty(FLOAT_PROP, 0));
    }

    @Test
    public void testGetIntArrayProperty_tryAgainBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(INT32_VEC_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.getIntArrayProperty(INT32_VEC_PROP, 0)).isEqualTo(
                new int[0]);
    }

    @Test
    public void testGetIntArrayProperty_tryAgainEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(INT32_VEC_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mCarPropertyManager.getIntArrayProperty(INT32_VEC_PROP, 0));
    }

    @Test
    public void testGetFloatProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        CarPropertyValue<Float> value = new CarPropertyValue<>(FLOAT_PROP, 0, 1.f);
        when(mICarProperty.getProperty(FLOAT_PROP, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getFloatProperty(FLOAT_PROP, 0)).isEqualTo(1.f);
    }

    @Test
    public void testGetFloatProperty_tryAgainBeforeR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.Q);
        when(mICarProperty.getProperty(FLOAT_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.getFloatProperty(FLOAT_PROP, 0)).isEqualTo(0.f);
    }

    @Test
    public void testGetFloatProperty_tryAgainEqualAfterR() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.R);
        when(mICarProperty.getProperty(FLOAT_PROP, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mCarPropertyManager.getFloatProperty(FLOAT_PROP, 0));
    }

    @Test
    public void testGetFloatProperty_wrongType() throws Exception {
        CarPropertyValue<Integer> value = new CarPropertyValue<>(INT32_PROP, 0, 1);
        when(mICarProperty.getProperty(INT32_PROP, 0)).thenReturn(value);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getFloatProperty(INT32_PROP, 0));
    }

    private CarPropertyManager.GetPropertyRequest createGetPropertyRequest() {
        return mCarPropertyManager.generateGetPropertyRequest(HVAC_TEMPERATURE_SET, 0);
    }

    @Test
    public void testGetPropertiesAsync() throws RemoteException {
        mCarPropertyManager.getPropertiesAsync(List.of(createGetPropertyRequest()), null, null,
                mGetPropertyCallback);

        ArgumentCaptor<AsyncPropertyServiceRequestList> argumentCaptor = ArgumentCaptor.forClass(
                AsyncPropertyServiceRequestList.class);
        verify(mICarProperty).getPropertiesAsync(argumentCaptor.capture(), any(), anyLong());
        assertThat(argumentCaptor.getValue().getList().get(0).getRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().getList().get(0).getPropertyId())
                .isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(argumentCaptor.getValue().getList().get(0).getAreaId()).isEqualTo(0);
    }

    @Test
    public void testGetPropertiesAsyncWithTimeout() throws RemoteException {
        mCarPropertyManager.getPropertiesAsync(List.of(createGetPropertyRequest()),
                /* timeoutInMs= */ 1000, null, null, mGetPropertyCallback);

        ArgumentCaptor<AsyncPropertyServiceRequestList> argumentCaptor = ArgumentCaptor.forClass(
                AsyncPropertyServiceRequestList.class);
        verify(mICarProperty).getPropertiesAsync(argumentCaptor.capture(), any(), eq(1000L));
        assertThat(argumentCaptor.getValue().getList().get(0).getRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().getList().get(0).getPropertyId())
                .isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(argumentCaptor.getValue().getList().get(0).getAreaId()).isEqualTo(0);
    }

    @Test
    public void testGetPropertiesAsync_illegalArgumentException() throws RemoteException {
        IllegalArgumentException exception = new IllegalArgumentException();
        doThrow(exception).when(mICarProperty).getPropertiesAsync(any(
                AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getPropertiesAsync(
                        List.of(createGetPropertyRequest()), null, null, mGetPropertyCallback));
    }

    @Test
    public void testGetPropertiesAsync_SecurityException() throws RemoteException {
        SecurityException exception = new SecurityException();
        doThrow(exception).when(mICarProperty).getPropertiesAsync(any(
                AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        assertThrows(SecurityException.class,
                () -> mCarPropertyManager.getPropertiesAsync(
                        List.of(createGetPropertyRequest()), null, null, mGetPropertyCallback));
    }

    @Test
    public void tsetGetPropertiesAsync_unsupportedProperty() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getPropertiesAsync(
                        List.of(mCarPropertyManager.generateGetPropertyRequest(INVALID, 0)), null,
                        null, mGetPropertyCallback));
    }

    @Test
    public void testGetPropertiesAsync_remoteException() throws RemoteException {
        RemoteException remoteException = new RemoteException();
        doThrow(remoteException).when(mICarProperty).getPropertiesAsync(any(
                AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        mCarPropertyManager.getPropertiesAsync(List.of(createGetPropertyRequest()), null, null,
                mGetPropertyCallback);

        verify(mCar).handleRemoteExceptionFromCarService(any(RemoteException.class));
    }

    @Test
    public void testGetPropertiesAsync_clearRequestIdAfterFailed() throws RemoteException {
        CarPropertyManager.GetPropertyRequest getPropertyRequest = createGetPropertyRequest();
        IllegalArgumentException exception = new IllegalArgumentException();
        doThrow(exception).when(mICarProperty).getPropertiesAsync(any(
                AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getPropertiesAsync(List.of(getPropertyRequest), null,
                        null, mGetPropertyCallback));

        clearInvocations(mICarProperty);
        doNothing().when(mICarProperty).getPropertiesAsync(any(
                AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());
        ArgumentCaptor<AsyncPropertyServiceRequestList> argumentCaptor = ArgumentCaptor.forClass(
                AsyncPropertyServiceRequestList.class);

        mCarPropertyManager.getPropertiesAsync(List.of(getPropertyRequest), null, null,
                mGetPropertyCallback);

        verify(mICarProperty).getPropertiesAsync(argumentCaptor.capture(), any(), anyLong());
        assertThat(argumentCaptor.getValue().getList().get(0).getRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().getList().get(0).getPropertyId())
                .isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(argumentCaptor.getValue().getList().get(0).getAreaId()).isEqualTo(0);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = android.os.CancellationSignal.class)
    public void testGetPropertiesAsync_cancellationSignalCancelRequests() throws Exception {
        CarPropertyManager.GetPropertyRequest getPropertyRequest = createGetPropertyRequest();
        CancellationSignal cancellationSignal = new CancellationSignal();
        List<IAsyncPropertyResultCallback> callbackWrapper = new ArrayList<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            callbackWrapper.add((IAsyncPropertyResultCallback) args[1]);
            return null;
        }).when(mICarProperty).getPropertiesAsync(any(), any(), anyLong());

        mCarPropertyManager.getPropertiesAsync(List.of(getPropertyRequest), cancellationSignal,
                /* callbackExecutor= */ null, mGetPropertyCallback);

        // Cancel the pending request.
        cancellationSignal.cancel();

        verify(mICarProperty).cancelRequests(new int[]{0});

        // Call the manager callback after the request is already cancelled.
        GetSetValueResult getValueResult = GetSetValueResult.newErrorResult(
                0,
                new CarPropertyErrorCodes(
                        CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                        /* vendorErrorCode= */ 0,
                        /* systemErrorCode= */ 0));
        assertThat(callbackWrapper.size()).isEqualTo(1);
        callbackWrapper.get(0).onGetValueResults(
                new GetSetValueResultList(List.of(getValueResult)));

        // No client callbacks should be called.
        verify(mGetPropertyCallback, never()).onFailure(any());
        verify(mGetPropertyCallback, never()).onSuccess(any());
    }

    @Test
    public void testOnGetValueResult_onSuccess() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            AsyncPropertyServiceRequestList asyncGetPropertyServiceRequestList =
                    (AsyncPropertyServiceRequestList) args[0];
            List getPropertyServiceList = asyncGetPropertyServiceRequestList.getList();
            AsyncPropertyServiceRequest getPropertyServiceRequest =
                    (AsyncPropertyServiceRequest) getPropertyServiceList.get(0);
            IAsyncPropertyResultCallback getAsyncPropertyResultCallback =
                    (IAsyncPropertyResultCallback) args[1];

            assertThat(getPropertyServiceRequest.getRequestId()).isEqualTo(0);
            assertThat(getPropertyServiceRequest.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);

            CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);
            GetSetValueResult getValueResult = GetSetValueResult.newGetValueResult(0, value);

            getAsyncPropertyResultCallback.onGetValueResults(
                    new GetSetValueResultList(List.of(getValueResult)));
            return null;
        }).when(mICarProperty).getPropertiesAsync(any(), any(), anyLong());

        mCarPropertyManager.getPropertiesAsync(List.of(createGetPropertyRequest()), null, null,
                mGetPropertyCallback);

        verify(mGetPropertyCallback, timeout(1000)).onSuccess(
                mGetPropertyResultCaptor.capture());
        GetPropertyResult<Float> gotResult = (GetPropertyResult<Float>)
                mGetPropertyResultCaptor.getValue();
        assertThat(gotResult.getRequestId()).isEqualTo(0);
        assertThat(gotResult.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotResult.getAreaId()).isEqualTo(0);
        assertThat(gotResult.getValue()).isEqualTo(17.0f);
    }

    @Test
    public void testOnGetValueResult_onSuccessMultipleRequests() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            AsyncPropertyServiceRequestList getPropertyServiceRequest =
                    (AsyncPropertyServiceRequestList) args[0];
            List<AsyncPropertyServiceRequest> getPropertyServiceRequests =
                    getPropertyServiceRequest.getList();
            IAsyncPropertyResultCallback getAsyncPropertyResultCallback =
                    (IAsyncPropertyResultCallback) args[1];

            assertThat(getPropertyServiceRequests.size()).isEqualTo(2);
            assertThat(getPropertyServiceRequests.get(0).getRequestId()).isEqualTo(0);
            assertThat(getPropertyServiceRequests.get(0).getPropertyId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);
            assertThat(getPropertyServiceRequests.get(1).getRequestId()).isEqualTo(1);
            assertThat(getPropertyServiceRequests.get(1).getPropertyId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);

            CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);
            List<GetSetValueResult> getValueResults = List.of(
                    GetSetValueResult.newGetValueResult(0, value),
                    GetSetValueResult.newGetValueResult(1, value));

            getAsyncPropertyResultCallback.onGetValueResults(
                    new GetSetValueResultList(getValueResults));
            return null;
        }).when(mICarProperty).getPropertiesAsync(any(), any(), anyLong());

        List<CarPropertyManager.GetPropertyRequest> getPropertyRequests = new ArrayList<>();
        getPropertyRequests.add(createGetPropertyRequest());
        getPropertyRequests.add(createGetPropertyRequest());

        mCarPropertyManager.getPropertiesAsync(getPropertyRequests, null, null,
                mGetPropertyCallback);

        verify(mGetPropertyCallback, timeout(1000).times(2)).onSuccess(
                    mGetPropertyResultCaptor.capture());
        List<GetPropertyResult<?>> gotPropertyResults = mGetPropertyResultCaptor.getAllValues();
        assertThat(gotPropertyResults.get(0).getRequestId()).isEqualTo(0);
        assertThat(gotPropertyResults.get(0).getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotPropertyResults.get(0).getAreaId()).isEqualTo(0);
        assertThat(gotPropertyResults.get(0).getValue()).isEqualTo(17.0f);
        assertThat(gotPropertyResults.get(1).getRequestId()).isEqualTo(1);
        assertThat(gotPropertyResults.get(1).getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotPropertyResults.get(1).getAreaId()).isEqualTo(0);
        assertThat(gotPropertyResults.get(1).getValue()).isEqualTo(17.0f);
    }

    @Test
    public void testOnGetValueResult_onFailure() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            AsyncPropertyServiceRequestList asyncPropertyServiceREquestList =
                    (AsyncPropertyServiceRequestList) args[0];
            List getPropertyServiceList = asyncPropertyServiceREquestList.getList();
            AsyncPropertyServiceRequest getPropertyServiceRequest =
                    (AsyncPropertyServiceRequest) getPropertyServiceList.get(0);
            IAsyncPropertyResultCallback getAsyncPropertyResultCallback =
                    (IAsyncPropertyResultCallback) args[1];

            assertThat(getPropertyServiceRequest.getRequestId()).isEqualTo(0);
            assertThat(getPropertyServiceRequest.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);

            GetSetValueResult getValueResult = GetSetValueResult.newErrorResult(
                    0,
                    new CarPropertyErrorCodes(
                            CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR, VENDOR_ERROR_CODE, 0));

            getAsyncPropertyResultCallback.onGetValueResults(
                    new GetSetValueResultList(List.of(getValueResult)));
            return null;
        }).when(mICarProperty).getPropertiesAsync(any(), any(), anyLong());


        mCarPropertyManager.getPropertiesAsync(List.of(createGetPropertyRequest()), null, null,
                mGetPropertyCallback);

        verify(mGetPropertyCallback, timeout(1000)).onFailure(mPropertyAsyncErrorCaptor.capture());
        PropertyAsyncError error = mPropertyAsyncErrorCaptor.getValue();
        assertThat(error.getRequestId()).isEqualTo(0);
        assertThat(error.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(error.getAreaId()).isEqualTo(0);
        assertThat(error.getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(error.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testSetProperty_setsValue() throws RemoteException {
        mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f);

        ArgumentCaptor<CarPropertyValue> value = ArgumentCaptor.forClass(CarPropertyValue.class);

        verify(mICarProperty).setProperty(value.capture(), any());
        assertThat(value.getValue().getValue()).isEqualTo(17.0f);
    }

    @Test
    public void testSetProperty_syncOpTryAgain() throws RemoteException {
        doThrow(new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN)).doNothing()
                .when(mICarProperty).setProperty(any(), any());

        mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f);

        verify(mICarProperty, times(2)).setProperty(any(), any());
    }

    @Test
    public void testSetProperty_unsupportedProperty() throws RemoteException {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.setProperty(Float.class, INVALID, 0, 17.0f));
    }

    private SetPropertyRequest<Float> createSetPropertyRequest() {
        return mCarPropertyManager.generateSetPropertyRequest(HVAC_TEMPERATURE_SET, 0,
                Float.valueOf(17.0f));
    }

    @Test
    public void testSetPropertiesAsync() throws RemoteException {
        SetPropertyRequest<Float> setPropertyRequest = createSetPropertyRequest();
        setPropertyRequest.setUpdateRateHz(10.1f);
        setPropertyRequest.setWaitForPropertyUpdate(false);
        mCarPropertyManager.setPropertiesAsync(List.of(setPropertyRequest), null, null,
                mSetPropertyCallback);

        verify(mICarProperty).setPropertiesAsync(mAsyncPropertyServiceRequestCaptor.capture(),
                any(), anyLong());

        AsyncPropertyServiceRequest request = mAsyncPropertyServiceRequestCaptor.getValue()
                .getList().get(0);

        assertThat(request.getRequestId()).isEqualTo(0);
        assertThat(request.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(request.getAreaId()).isEqualTo(0);
        assertThat(request.isWaitForPropertyUpdate()).isFalse();
        assertThat(request.getUpdateRateHz()).isEqualTo(10.1f);
        CarPropertyValue requestValue = request.getCarPropertyValue();
        assertThat(requestValue).isNotNull();
        assertThat(requestValue.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(requestValue.getAreaId()).isEqualTo(0);
        assertThat(requestValue.getValue()).isEqualTo(17.0f);
    }

    @Test
    public void testSetPropertiesAsync_nullRequests() throws RemoteException {
        assertThrows(NullPointerException.class,
                () -> mCarPropertyManager.setPropertiesAsync(
                        null, null, null, mSetPropertyCallback));
    }

    @Test
    public void testSetPropertiesAsync_nullCallback() throws RemoteException {
        assertThrows(NullPointerException.class,
                () -> mCarPropertyManager.setPropertiesAsync(
                        List.of(createSetPropertyRequest()), null, null, null));
    }

    @Test
    public void testSetPropertiesAsyncWithTimeout() throws RemoteException {
        mCarPropertyManager.setPropertiesAsync(List.of(createSetPropertyRequest()),
                /* timeoutInMs= */ 1000, /* cancellationSignal= */ null,
                /* callbackExecutor= */ null, mSetPropertyCallback);

        verify(mICarProperty).setPropertiesAsync(mAsyncPropertyServiceRequestCaptor.capture(),
                any(), eq(1000L));
        AsyncPropertyServiceRequest request = mAsyncPropertyServiceRequestCaptor.getValue()
                .getList().get(0);
        assertThat(request.getRequestId()).isEqualTo(0);
        assertThat(request.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(request.getAreaId()).isEqualTo(0);
        CarPropertyValue requestValue = request.getCarPropertyValue();
        assertThat(requestValue).isNotNull();
        assertThat(requestValue.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(requestValue.getAreaId()).isEqualTo(0);
        assertThat(requestValue.getValue()).isEqualTo(17.0f);
    }

    @Test
    public void testSetPropertiesAsync_illegalArgumentException() throws RemoteException {
        doThrow(new IllegalArgumentException()).when(mICarProperty).setPropertiesAsync(
                any(AsyncPropertyServiceRequestList.class), any(IAsyncPropertyResultCallback.class),
                anyLong());

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.setPropertiesAsync(
                        List.of(createSetPropertyRequest()), null, null, mSetPropertyCallback));
    }

    @Test
    public void testSetPropertiesAsync_SecurityException() throws RemoteException {
        doThrow(new SecurityException()).when(mICarProperty).setPropertiesAsync(
                any(AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        assertThrows(SecurityException.class,
                () -> mCarPropertyManager.setPropertiesAsync(
                        List.of(createSetPropertyRequest()), null, null, mSetPropertyCallback));
    }

    @Test
    public void testSetPropertiesAsync_unsupportedProperty() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.setPropertiesAsync(
                        List.of(mCarPropertyManager.generateSetPropertyRequest(
                                INVALID, 0, Integer.valueOf(0))),
                        null, null, mSetPropertyCallback));
    }

    @Test
    public void testSetPropertiesAsync_remoteException() throws RemoteException {
        doThrow(new RemoteException()).when(mICarProperty).setPropertiesAsync(
                any(AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        mCarPropertyManager.setPropertiesAsync(List.of(createSetPropertyRequest()), null, null,
                mSetPropertyCallback);

        verify(mCar).handleRemoteExceptionFromCarService(any(RemoteException.class));
    }

    @Test
    public void testSetPropertiesAsync_duplicateRequestId() throws RemoteException {
        SetPropertyRequest request = createSetPropertyRequest();

        mCarPropertyManager.setPropertiesAsync(List.of(request), null, null,
                mSetPropertyCallback);

        // Send the same request again with the same request ID is not allowed.
        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.setPropertiesAsync(List.of(request), null, null,
                        mSetPropertyCallback));
    }

    @Test
    public void testSetPropertiesAsync_clearRequestIdAfterFailed() throws RemoteException {
        SetPropertyRequest setPropertyRequest = createSetPropertyRequest();
        IllegalArgumentException exception = new IllegalArgumentException();
        doThrow(exception).when(mICarProperty).setPropertiesAsync(
                any(AsyncPropertyServiceRequestList.class), any(IAsyncPropertyResultCallback.class),
                anyLong());

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.setPropertiesAsync(List.of(setPropertyRequest), null,
                        null, mSetPropertyCallback));

        clearInvocations(mICarProperty);
        doNothing().when(mICarProperty).setPropertiesAsync(
                any(AsyncPropertyServiceRequestList.class),
                any(IAsyncPropertyResultCallback.class), anyLong());

        // After the first request failed, the request ID map should be cleared so we can use the
        // same request ID again.
        mCarPropertyManager.setPropertiesAsync(List.of(setPropertyRequest), null, null,
                mSetPropertyCallback);

        verify(mICarProperty).setPropertiesAsync(any(), any(), anyLong());
    }

    @Test
    public void testSetProperty_notAvailableAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(combineErrors(
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE, VENDOR_ERROR_CODE))).when(mICarProperty)
                .setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testSetProperty_internalErrorAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(combineErrors(
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR, VENDOR_ERROR_CODE))).when(mICarProperty)
                .setProperty(eq(carPropertyValue), any());

        CarInternalErrorException exception =  assertThrows(CarInternalErrorException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testSetProperty_notAvailableDisabledBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
    }

    @Test
    public void testSetProperty_notAvailableDisabledAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED);
    }

    @Test
    public void testSetProperty_notAvailableDisabledAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(combineErrors(
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED, VENDOR_ERROR_CODE)))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testSetProperty_notAvailableSafetyBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
    }

    @Test
    public void testSetProperty_notAvailableSafetyAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY);
    }

    @Test
    public void testSetProperty_notAvailableSafetyAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(combineErrors(
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY, VENDOR_ERROR_CODE)))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testSetProperty_notAvailableSpeedHighBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
    }

    @Test
    public void testSetProperty_notAvailableSpeedHighAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH);
    }

    @Test
    public void testSetProperty_notAvailableSpeedHighAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(combineErrors(
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH, VENDOR_ERROR_CODE)))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testSetProperty_notAvailableSpeedLowBeforeU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
    }

    @Test
    public void testSetProperty_notAvailableSpeedLowAfterU() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW);
    }

    @Test
    public void testSetProperty_notAvailableSpeedLowAfterU_withVendorErrorCode() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CarPropertyValue<Float> carPropertyValue = new CarPropertyValue<>(
                HVAC_TEMPERATURE_SET, 0, 17.0f);
        doThrow(new ServiceSpecificException(combineErrors(
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW, VENDOR_ERROR_CODE)))
                .when(mICarProperty).setProperty(eq(carPropertyValue), any());

        PropertyNotAvailableException exception =  assertThrows(PropertyNotAvailableException.class,
                () -> mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f));
        assertThat(exception.getDetailedErrorCode())
                .isEqualTo(PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW);
        assertThat(exception.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testOnSetValueResult_onSuccess() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            AsyncPropertyServiceRequestList setPropertyRequest =
                    (AsyncPropertyServiceRequestList) args[0];
            List setPropertyServiceList = setPropertyRequest.getList();
            AsyncPropertyServiceRequest setPropertyServiceRequest =
                    (AsyncPropertyServiceRequest) setPropertyServiceList.get(0);
            IAsyncPropertyResultCallback setAsyncPropertyResultCallback =
                    (IAsyncPropertyResultCallback) args[1];

            assertThat(setPropertyServiceRequest.getRequestId()).isEqualTo(0);
            assertThat(setPropertyServiceRequest.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
            assertThat(setPropertyServiceRequest.getCarPropertyValue().getValue()).isEqualTo(
                    17.0f);

            GetSetValueResult setValueResult = GetSetValueResult.newSetValueResult(0,
                        /* updateTimestampNanos= */ TEST_TIMESTAMP);

            setAsyncPropertyResultCallback.onSetValueResults(
                    new GetSetValueResultList(List.of(setValueResult)));
            return null;
        }).when(mICarProperty).setPropertiesAsync(any(), any(), anyLong());


        mCarPropertyManager.setPropertiesAsync(List.of(createSetPropertyRequest()), null, null,
                mSetPropertyCallback);

        verify(mSetPropertyCallback, timeout(1000)).onSuccess(mSetPropertyResultCaptor.capture());
        SetPropertyResult gotResult = mSetPropertyResultCaptor.getValue();
        assertThat(gotResult.getRequestId()).isEqualTo(0);
        assertThat(gotResult.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotResult.getAreaId()).isEqualTo(0);
        assertThat(gotResult.getUpdateTimestampNanos()).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    public void testOnSetValueResult_onSuccessMultipleRequests() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            AsyncPropertyServiceRequestList setPropertyServiceRequest =
                    (AsyncPropertyServiceRequestList) args[0];
            List<AsyncPropertyServiceRequest> setPropertyServiceRequests =
                    setPropertyServiceRequest.getList();
            IAsyncPropertyResultCallback setAsyncPropertyResultCallback =
                    (IAsyncPropertyResultCallback) args[1];

            assertThat(setPropertyServiceRequests.size()).isEqualTo(2);
            assertThat(setPropertyServiceRequests.get(0).getRequestId()).isEqualTo(0);
            assertThat(setPropertyServiceRequests.get(0).getPropertyId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);
            assertThat(setPropertyServiceRequests.get(1).getRequestId()).isEqualTo(1);
            assertThat(setPropertyServiceRequests.get(1).getPropertyId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);

            List<GetSetValueResult> setValueResults = List.of(
                    GetSetValueResult.newSetValueResult(0,
                            /* updateTimestampNanos= */ TEST_TIMESTAMP),
                    GetSetValueResult.newSetValueResult(1,
                            /* updateTimestampNanos= */ TEST_TIMESTAMP));

            setAsyncPropertyResultCallback.onSetValueResults(
                    new GetSetValueResultList(setValueResults));
            return null;
        }).when(mICarProperty).setPropertiesAsync(any(), any(), anyLong());

        List<SetPropertyRequest<?>> setPropertyRequests = new ArrayList<>();
        setPropertyRequests.add(createSetPropertyRequest());
        setPropertyRequests.add(createSetPropertyRequest());

        mCarPropertyManager.setPropertiesAsync(setPropertyRequests, null, null,
                mSetPropertyCallback);

        verify(mSetPropertyCallback, timeout(1000).times(2)).onSuccess(
                mSetPropertyResultCaptor.capture());
        List<SetPropertyResult> gotPropertyResults = mSetPropertyResultCaptor.getAllValues();
        assertThat(gotPropertyResults.get(0).getRequestId()).isEqualTo(0);
        assertThat(gotPropertyResults.get(0).getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotPropertyResults.get(0).getAreaId()).isEqualTo(0);
        assertThat(gotPropertyResults.get(0).getUpdateTimestampNanos()).isEqualTo(TEST_TIMESTAMP);
        assertThat(gotPropertyResults.get(1).getRequestId()).isEqualTo(1);
        assertThat(gotPropertyResults.get(1).getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotPropertyResults.get(1).getAreaId()).isEqualTo(0);
        assertThat(gotPropertyResults.get(1).getUpdateTimestampNanos()).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    public void testOnSetValueResult_onFailure() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            AsyncPropertyServiceRequestList setPropertyService =
                    (AsyncPropertyServiceRequestList) args[0];
            List setPropertyServiceList = setPropertyService.getList();
            AsyncPropertyServiceRequest setPropertyServiceRequest =
                    (AsyncPropertyServiceRequest) setPropertyServiceList.get(0);
            IAsyncPropertyResultCallback setAsyncPropertyResultCallback =
                    (IAsyncPropertyResultCallback) args[1];

            assertThat(setPropertyServiceRequest.getRequestId()).isEqualTo(0);
            assertThat(setPropertyServiceRequest.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);

            GetSetValueResult setValueResult = GetSetValueResult.newErrorSetValueResult(
                    0,
                    new CarPropertyErrorCodes(
                            CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR, VENDOR_ERROR_CODE, 0));

            setAsyncPropertyResultCallback.onSetValueResults(
                    new GetSetValueResultList(List.of(setValueResult)));
            return null;
        }).when(mICarProperty).setPropertiesAsync(any(), any(), anyLong());

        mCarPropertyManager.setPropertiesAsync(List.of(createSetPropertyRequest()), null, null,
                mSetPropertyCallback);

        verify(mSetPropertyCallback, timeout(1000)).onFailure(mPropertyAsyncErrorCaptor.capture());
        PropertyAsyncError error = mPropertyAsyncErrorCaptor.getValue();
        assertThat(error.getRequestId()).isEqualTo(0);
        assertThat(error.getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(error.getAreaId()).isEqualTo(0);
        assertThat(error.getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(error.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
    }

    @Test
    public void testRegisterCallback_returnsFalseIfPropertyIdNotSupportedInVehicle()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VehiclePropertyIds.INVALID, FIRST_UPDATE_RATE_HZ)).isFalse();
        verify(mICarProperty, never()).registerListener(any(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersWithServiceOnFirstCallback() throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        FIRST_UPDATE_RATE_HZ))), any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersWithMaxUpdateRateOnFirstCallback()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, MAX_UPDATE_RATE_HZ + 1)).isTrue();
        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        MAX_UPDATE_RATE_HZ))), any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersWithMinUpdateRateOnFirstCallback()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, MIN_UPDATE_RATE_HZ - 1)).isTrue();
        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        MIN_UPDATE_RATE_HZ))), any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersWithOnChangeRateForOnChangeProperty()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[]{0},
                        CarPropertyManager.SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersWithOnChangeRateForStaticProperty()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_STATIC_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(VENDOR_STATIC_PROPERTY, new int[]{0},
                        CarPropertyManager.SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_returnsFalseForRemoteException() throws RemoteException {
        RemoteException remoteException = new RemoteException();
        doThrow(remoteException).when(mICarProperty).registerListener(
                eq(List.of(createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[] {0},
                        CarPropertyManager.SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
    }

    @Test
    public void testRegisterCallback_recoversAfterFirstRemoteException() throws RemoteException {
        RemoteException remoteException = new RemoteException();
        doThrow(remoteException).doNothing().when(mICarProperty)
                .registerListener(eq(List.of(
                        createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[] {0},
                        SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        verify(mICarProperty, times(2)).registerListener(
                eq(List.of(createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[] {0},
                        CarPropertyManager.SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_returnsFalseForIllegalArgumentException()
            throws RemoteException {
        doThrow(IllegalArgumentException.class).when(mICarProperty)
                .registerListener(eq(List.of(createCarSubscriptionOption(
                        VENDOR_ON_CHANGE_PROPERTY, new int[] {0}, SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
    }

    @Test
    public void testRegisterCallback_registersTwiceWithHigherRateCallback() throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                VENDOR_CONTINUOUS_PROPERTY, LARGER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty, times(2)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        LARGER_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        FIRST_UPDATE_RATE_HZ)));
    }

    @Test
    public void testRegisterCallback_registersOnSecondHigherRateWithSameCallback()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                VENDOR_CONTINUOUS_PROPERTY, LARGER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty, times(2)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        LARGER_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        FIRST_UPDATE_RATE_HZ)));
    }

    @Test
    public void testRegisterCallback_registersOnSecondLowerRateWithSameCallback()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, SMALLER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty, times(2)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        FIRST_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        SMALLER_UPDATE_RATE_HZ)));
    }

    @Test
    public void testRegisterCallback_doesNotRegistersOnSecondLowerRateCallback()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                VENDOR_CONTINUOUS_PROPERTY, SMALLER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        FIRST_UPDATE_RATE_HZ))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersTwiceForDifferentProperties() throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        verify(mICarProperty, times(2)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        FIRST_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[] {0},
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)));
    }

    @Test
    public void testRegisterCallback_registerInvalidProp() throws Exception {
        assertThat(mCarPropertyManager.registerCallback(
                mCarPropertyEventCallback, /* propertyId= */ -1, FIRST_UPDATE_RATE_HZ)).isFalse();
    }

    // We annotate the update rate to be within 0 and 100, however, in the actual implementation,
    // we will fit update rate outside this range to the min and max update rate.
    @Test
    public void testRegisterCallback_updateRateOutsideFloatRangeMustNotThrow_tooLarge()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(
                mCarPropertyEventCallback, VENDOR_CONTINUOUS_PROPERTY, /* updateRateHz= */ 101.f))
                .isTrue();

        verify(mICarProperty).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getValue()).isEqualTo(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        MAX_UPDATE_RATE_HZ)));
    }

    @Test
    public void testRegisterCallback_updateRateOutsideFloatRangeMustNotThrow_tooSmall()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(
                mCarPropertyEventCallback, VENDOR_CONTINUOUS_PROPERTY, /* updateRateHz= */ -1.f))
                .isTrue();

        verify(mICarProperty).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getValue()).isEqualTo(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        MIN_UPDATE_RATE_HZ)));
    }

    @Test
    public void testRegisterCallback_isSupportedAndHasWritePermissionOnly() throws Exception {
        int propId = VENDOR_CONTINUOUS_PROPERTY;
        when(mICarProperty.isSupportedAndHasWritePermissionOnly(propId)).thenReturn(true);

        assertThrows(SecurityException.class, () -> mCarPropertyManager.registerCallback(
                mCarPropertyEventCallback, VENDOR_CONTINUOUS_PROPERTY, 0));
    }

    @Test
    public void testRegisterCallback_isSupportedAndHasWritePermissionOnly_remoteException()
            throws Exception {
        int propId = VENDOR_CONTINUOUS_PROPERTY;
        when(mICarProperty.isSupportedAndHasWritePermissionOnly(propId)).thenThrow(
                new RemoteException());

        assertThat(mCarPropertyManager.registerCallback(
                mCarPropertyEventCallback, VENDOR_CONTINUOUS_PROPERTY, 0)).isFalse();
    }

    @Test
    public void testSubscribePropertyEvents_registerMultipleEvents() throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                        .setUpdateRateFast().build(),
                new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        verify(mICarProperty).registerListener(
                eq(List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        CarPropertyManager.SENSOR_RATE_FAST, /* enableVur= */ true),
                        createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[]{0},
                                CarPropertyManager.SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_sanitizeSampleRate() throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                        .setUpdateRateHz(0.f).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback))
                .isTrue();

        verify(mICarProperty).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getValue()).isEqualTo(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[] {0},
                        MIN_UPDATE_RATE_HZ, /* enableVur= */ true)));
    }

    @Test
    public void testSubscribePropertyEvents_registerMultipleEventsSameProperty_throws()
            throws Exception {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.subscribePropertyEvents(List.of(
                                new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                        .addAreaId(0).setUpdateRateFast().build(),
                                new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                        .addAreaId(0).build()),
                        /* callbackExecutor= */ null, mCarPropertyEventCallback));

        assertWithMessage("Overlapping areaIds").that(thrown).hasMessageThat()
                .contains("Subscribe options contain overlapping propertyId");
    }

    @Test
    public void testSubscribePropertyEvents_registerMultipleEvents_unsubscribe() throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFast().build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0)
                                .build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_registerMultipleEventsWithSameProperty_unsubscribe()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFastest().build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0)
                                .build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFast().build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback2)).isTrue();
        clearInvocations(mICarProperty);
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);

        verify(mICarProperty).registerListener(eq(List.of(
                        createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                                CarPropertyManager.SENSOR_RATE_FAST, /* enableVur= */ true))),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_withPropertyIdCallback() throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(
                VENDOR_CONTINUOUS_PROPERTY, mCarPropertyEventCallback)).isTrue();

        verify(mICarProperty).registerListener(eq(List.of(
                        createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                                MIN_UPDATE_RATE_HZ, /* enableVur= */ true))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_withPropertyIdUpdateRateCallback() throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(
                VENDOR_CONTINUOUS_PROPERTY, CarPropertyManager.SENSOR_RATE_FAST,
                mCarPropertyEventCallback)).isTrue();

        verify(mICarProperty).registerListener(eq(List.of(
                        createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                                CarPropertyManager.SENSOR_RATE_FAST, /* enableVur= */ true))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_withPropertyIdAreaIdCallback() throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(
                VENDOR_CONTINUOUS_PROPERTY, /* areaId= */ 0,
                mCarPropertyEventCallback)).isTrue();

        verify(mICarProperty).registerListener(eq(List.of(
                        createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                                MIN_UPDATE_RATE_HZ, /* enableVur= */ true))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_withPropertyIdAreaIdUpdateRateCallback()
                throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(
                VENDOR_CONTINUOUS_PROPERTY, /* areaId= */ 0,
                CarPropertyManager.SENSOR_RATE_FAST,
                mCarPropertyEventCallback)).isTrue();

        verify(mICarProperty).registerListener(eq(List.of(
                        createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                                CarPropertyManager.SENSOR_RATE_FAST, /* enableVur= */ false))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testSubscribePropertyEvents_noReadPermission() throws Exception {
        int propId = VENDOR_CONTINUOUS_PROPERTY;
        when(mICarProperty.getSupportedNoReadPermPropIds(any()))
                .thenReturn(new int[] {propId});

        assertThrows(SecurityException.class, () -> mCarPropertyManager.subscribePropertyEvents(
                propId, mCarPropertyEventCallback));
    }

    @Test
    public void testSubscribePropertyEvents_getSupportedNoReadPermPropIds_remoteException()
            throws Exception {
        int propId = VENDOR_CONTINUOUS_PROPERTY;
        when(mICarProperty.getSupportedNoReadPermPropIds(any()))
                .thenThrow(new RemoteException());

        assertThat(mCarPropertyManager.subscribePropertyEvents(
                propId, mCarPropertyEventCallback)).isFalse();
    }

    @Test
    public void testRegisterCallback_registersAgainWithDifferentExecutor_throws() throws Exception {
        mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFast().build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0)
                                .build()),
                mMockExecutor1, mCarPropertyEventCallback);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFast().build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0)
                                .build()),
                mMockExecutor2, mCarPropertyEventCallback));
        assertWithMessage("Two executor subscription").that(thrown).hasMessageThat()
                .contains("A different executor is already associated with this callback,"
                        + " please use the same executor");
    }

    @Test
    public void testRegisterCallback_registersAgainWithSameExecutor() throws Exception {
        mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFast().build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0)
                                .build()),
                mMockExecutor1, mCarPropertyEventCallback);
        mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY).addAreaId(0)
                                .setUpdateRateFast().build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).addAreaId(0)
                                .build()),
                mMockExecutor1, mCarPropertyEventCallback);

        verify(mICarProperty).registerListener(
                eq(List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        CarPropertyManager.SENSOR_RATE_FAST, /* enableVur= */ true),
                        createCarSubscriptionOption(VENDOR_ON_CHANGE_PROPERTY, new int[]{0},
                                CarPropertyManager.SENSOR_RATE_ONCHANGE))),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testRegisterCallback_registersAgainIfTheFirstCallbackReturnsFalse()
            throws RemoteException {
        doThrow(IllegalArgumentException.class).doNothing().when(mICarProperty)
                .registerListener(eq(List.of(
                                createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY,
                                        new int[]{0}, FIRST_UPDATE_RATE_HZ))),
                any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isFalse();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
    }

    @Test
    public void testRegisterCallback_restoresOriginalRateHzIfTheSecondCallbackReturnsFalse()
            throws RemoteException {
        Float smallerUpdate = 1F;
        Float largerUpdate = 2F;
        doThrow(IllegalArgumentException.class).when(mICarProperty)
                .registerListener(eq(List.of(createCarSubscriptionOption(
                        HVAC_TEMPERATURE_SET, new int[]{0}, largerUpdate))),
                any(ICarPropertyEventListener.class));

        CarPropertyValue<Float> goodValue = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                Duration.ofSeconds(1).toNanos(), 17.0f);
        CarPropertyValue<Float> almostFreshValue = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                Duration.ofMillis(1899).toNanos(), 18.0f);
        List<CarPropertyEvent> eventList = List.of(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, goodValue),
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        almostFreshValue)
        );
        CarPropertyConfig config = CarPropertyConfig.newBuilder(Float.class, HVAC_TEMPERATURE_SET,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Float>(0).build())
                .setChangeMode(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                .setMinSampleRate(0f)
                .setMaxSampleRate(10f).build();
        addCarPropertyConfig(config);
        ICarPropertyEventListener listener = getCarPropertyEventListener();
        ArgumentCaptor<CarPropertyValue> valueCaptor =
                ArgumentCaptor.forClass(CarPropertyValue.class);

        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                HVAC_TEMPERATURE_SET, smallerUpdate)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                HVAC_TEMPERATURE_SET, largerUpdate)).isFalse();
        verify(mICarProperty, times(3)).registerListener(
                any(), any(ICarPropertyEventListener.class));

        listener.onEvent(eventList);
        verify(mCarPropertyEventCallback, timeout(5000)).onChangeEvent(valueCaptor.capture());
        assertThat(valueCaptor.getAllValues()).containsExactly(goodValue);
    }

    @Test
    public void testUnregisterCallback_doesNothingIfNothingRegistered() throws Exception {
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, VENDOR_STATIC_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_doesNothingIfNothingRegistered() throws Exception {
        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_STATIC_PROPERTY,
                mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_doesNothingIfPropertyIsNotRegisteredForCallback()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, VENDOR_STATIC_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_doesNothingIfNothingIsNotRegisteredForCallback()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                        .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_STATIC_PROPERTY,
                mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_doesNothingIfCallbackIsNotRegisteredForProperty()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback2,
                VENDOR_CONTINUOUS_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_doesNothingIfCallbackIsNotRegisteredForProperty()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_CONTINUOUS_PROPERTY,
                mCarPropertyEventCallback2);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_doesNothingIfCarPropertyConfigNull() throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();

        setPropIdWithoutConfig(VENDOR_CONTINUOUS_PROPERTY);

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_doesNothingIfNoPermission() throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();

        setPropIdWithoutPermission(VENDOR_CONTINUOUS_PROPERTY);

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_doesNothingIfCarPropertyConfigNull()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        setPropIdWithoutConfig(VENDOR_CONTINUOUS_PROPERTY);

        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_CONTINUOUS_PROPERTY,
                mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_doesNothingIfNoPermission()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        setPropIdWithoutPermission(VENDOR_CONTINUOUS_PROPERTY);

        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_CONTINUOUS_PROPERTY,
                mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_unregistersCallbackForSingleProperty()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_unsubscribeCallbackForSingleProperty()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_unregistersCallbackForSpecificProperty()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY);
        verify(mICarProperty).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_ignoreUserHalProp()
            throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        mCarPropertyManager.unsubscribePropertyEvents(INITIAL_USER_INFO,
                mCarPropertyEventCallback);

        verify(mICarProperty, never()).unregisterListener(anyInt(), any());
    }

    @Test
    public void testUnregisterCallback_ignoreUserHalProp_beforeU()
            throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, INITIAL_USER_INFO);

        verify(mICarProperty, never()).unregisterListener(anyInt(), any());
    }

    @Test
    public void testUnregisterCallback_ignoreUserHalProp_afterU()
            throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, INITIAL_USER_INFO);

        verify(mICarProperty, never()).unregisterListener(anyInt(), any());
    }

    @Test
    public void testUnsubscribePropertyEvents_unsubscribeCallbackForSpecificProperty()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY)
                                .build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty, never()).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_unregistersCallbackForBothProperties()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty, times(2)).unregisterListener(mPropertyIdCaptor.capture(),
                any(ICarPropertyEventListener.class));
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(VENDOR_CONTINUOUS_PROPERTY,
                VENDOR_ON_CHANGE_PROPERTY);
    }

    @Test
    public void testUnsubscribePropertyEvents_unsubscribeCallbackForBothProperties()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build(),
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY)
                                .setUpdateRateNormal().build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_CONTINUOUS_PROPERTY,
                mCarPropertyEventCallback);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_unregistersAllCallbackForSingleProperty()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
            VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
            VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_unsubscribeAllCallbackForSingleProperty()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback2)).isTrue();

        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_CONTINUOUS_PROPERTY,
                mCarPropertyEventCallback);
        verify(mICarProperty).unregisterListener(eq(VENDOR_CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty, never()).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_registerCalledIfBiggerRateRemoved() throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                VENDOR_CONTINUOUS_PROPERTY, LARGER_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback2);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty, times(3)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        FIRST_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        LARGER_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        FIRST_UPDATE_RATE_HZ))
        );
    }

    @Test
    public void testUnsubscribePropertyEvents_registerCalledIfBiggerRateRemoved()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(LARGER_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback2)).isTrue();
        clearInvocations(mICarProperty);

        mCarPropertyManager.unsubscribePropertyEvents(mCarPropertyEventCallback2);

        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty, times(1)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        FIRST_UPDATE_RATE_HZ, /* enableVur= */ true))
        );
    }

    @Test
    public void testUnregisterCallback_registerNotCalledIfSmallerRateRemoved()
            throws Exception {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_CONTINUOUS_PROPERTY, FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                VENDOR_CONTINUOUS_PROPERTY, LARGER_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty, times(2)).registerListener(
                mCarSubscriptionCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mCarSubscriptionCaptor.getAllValues()).containsExactly(
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        FIRST_UPDATE_RATE_HZ)),
                List.of(createCarSubscriptionOption(VENDOR_CONTINUOUS_PROPERTY, new int[]{0},
                        LARGER_UPDATE_RATE_HZ)));
    }

    @Test
    public void testUnsubscribePropertyEvents_subscribeNotCalledIfSmallerRateremoved()
            throws Exception {
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(FIRST_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_CONTINUOUS_PROPERTY)
                                .setUpdateRateHz(LARGER_UPDATE_RATE_HZ).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback2)).isTrue();
        clearInvocations(mICarProperty);

        mCarPropertyManager.unsubscribePropertyEvents(mCarPropertyEventCallback2);

        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_doesNothingWithPropertyIdIfNothingRegistered()
            throws Exception {
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_doesNothingWithPropertyIdIfNothingRegistered()
            throws Exception {
        mCarPropertyManager.unsubscribePropertyEvents(mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_returnsVoidForIllegalArgumentException()
            throws RemoteException {
        doThrow(IllegalArgumentException.class).when(mICarProperty).unregisterListener(
                eq(VENDOR_ON_CHANGE_PROPERTY), any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY);

        verify(mICarProperty).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_returnsVoidForIllegalArgumentException()
            throws Exception {
        doThrow(IllegalArgumentException.class).when(mICarProperty).unregisterListener(
                eq(VENDOR_ON_CHANGE_PROPERTY), any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY);

        verify(mICarProperty).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnregisterCallback_SecurityException() throws Exception {
        doThrow(SecurityException.class).when(mICarProperty).unregisterListener(
                eq(VENDOR_ON_CHANGE_PROPERTY), any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        assertThrows(SecurityException.class,
                () -> mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                        VENDOR_ON_CHANGE_PROPERTY));
    }

    @Test
    public void testUnsubscribePropertyEvents_SecurityException() throws Exception {
        doThrow(SecurityException.class).when(mICarProperty).unregisterListener(
                eq(VENDOR_ON_CHANGE_PROPERTY), any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        assertThrows(SecurityException.class,
                () -> mCarPropertyManager.unsubscribePropertyEvents(VENDOR_ON_CHANGE_PROPERTY,
                        mCarPropertyEventCallback));
    }

    @Test
    public void testUnregisterCallback_recoversAfterSecurityException() throws Exception {
        doThrow(SecurityException.class).doNothing().when(mICarProperty).unregisterListener(
                eq(VENDOR_ON_CHANGE_PROPERTY), any(ICarPropertyEventListener.class));

        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        assertThrows(SecurityException.class,
                () -> mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                        VENDOR_ON_CHANGE_PROPERTY));

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback,
                VENDOR_ON_CHANGE_PROPERTY);

        verify(mICarProperty, times(2)).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testUnsubscribePropertyEvents_recoversAfterSecurityException() throws Exception {
        doThrow(SecurityException.class).doNothing().when(mICarProperty).unregisterListener(
                eq(VENDOR_ON_CHANGE_PROPERTY), any(ICarPropertyEventListener.class));
        assertThat(mCarPropertyManager.subscribePropertyEvents(List.of(
                        new Subscription.Builder(VENDOR_ON_CHANGE_PROPERTY).build()),
                /* callbackExecutor= */ null, mCarPropertyEventCallback)).isTrue();

        assertThrows(SecurityException.class,
                () -> mCarPropertyManager.unsubscribePropertyEvents(VENDOR_ON_CHANGE_PROPERTY,
                        mCarPropertyEventCallback));
        mCarPropertyManager.unsubscribePropertyEvents(VENDOR_ON_CHANGE_PROPERTY,
                mCarPropertyEventCallback);

        verify(mICarProperty, times(2)).unregisterListener(eq(VENDOR_ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void testOnErrorEvent_callbackIsCalledWithErrorEvent() throws RemoteException {
        List<CarPropertyEvent> eventList = createErrorCarPropertyEventList();
        CarPropertyConfig config = CarPropertyConfig.newBuilder(Float.class, HVAC_TEMPERATURE_SET,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Float>(0).build()).build();
        addCarPropertyConfig(config);
        ICarPropertyEventListener listener = getCarPropertyEventListener();

        listener.onEvent(eventList);

        // Wait until we get the on error event for the initial value.
        verify(mCarPropertyEventCallback, timeout(5000)).onErrorEvent(HVAC_TEMPERATURE_SET, 0,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
    }

    @Test
    public void testOnChangeEvent_callbackIsCalledWithEvent() throws RemoteException {
        List<CarPropertyEvent> eventList = createCarPropertyEventList();
        CarPropertyConfig config = CarPropertyConfig.newBuilder(Float.class, HVAC_TEMPERATURE_SET,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Float>(0).build()).build();
        addCarPropertyConfig(config);
        ICarPropertyEventListener listener = getCarPropertyEventListener();
        ArgumentCaptor<CarPropertyValue> value = ArgumentCaptor.forClass(CarPropertyValue.class);

        listener.onEvent(eventList);

        // Wait until we get the on property change event for the initial value.
        verify(mCarPropertyEventCallback, timeout(5000)).onChangeEvent(value.capture());
        assertThat(value.getValue().getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(value.getValue().getValue()).isEqualTo(17.0f);
    }

    private ICarPropertyEventListener getCarPropertyEventListener() throws RemoteException {
        ArgumentCaptor<ICarPropertyEventListener> carPropertyEventListenerArgumentCaptor =
                ArgumentCaptor.forClass(ICarPropertyEventListener.class);
        mCarPropertyManager.registerCallback(mCarPropertyEventCallback, HVAC_TEMPERATURE_SET,
                SENSOR_RATE_ONCHANGE);

        verify(mICarProperty).registerListener(eq(List.of(
                createCarSubscriptionOption(HVAC_TEMPERATURE_SET, new int[]{0},
                        SENSOR_RATE_ONCHANGE))),
                carPropertyEventListenerArgumentCaptor.capture());

        return carPropertyEventListenerArgumentCaptor.getValue();
    }

    @Test
    public void testGetPropertyList() throws Exception {
        List<CarPropertyConfig> expectedConfigs = mock(List.class);
        when(mICarProperty.getPropertyList())
                .thenReturn(new CarPropertyConfigList(expectedConfigs));


        assertThat(mCarPropertyManager.getPropertyList()).isEqualTo(expectedConfigs);
    }

    @Test
    public void testGetPropertyList_withPropertyIds() throws Exception {
        ArraySet<Integer> requestedPropertyIds = new ArraySet<>(Set.of(
                VENDOR_CONTINUOUS_PROPERTY, HVAC_TEMPERATURE_SET));
        CarPropertyConfig config1 = CarPropertyConfig.newBuilder(Integer.class,
                VENDOR_CONTINUOUS_PROPERTY,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(0).build()).build();
        CarPropertyConfig config2 = CarPropertyConfig.newBuilder(Float.class,
                HVAC_TEMPERATURE_SET,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Float>(0).build()).build();
        addCarPropertyConfig(config1);
        addCarPropertyConfig(config2);

        assertThat(mCarPropertyManager.getPropertyList(requestedPropertyIds))
                .containsExactly(config1, config2);
    }

    @Test
    public void testGetPropertyList_filterUnsupportedPropertyIds() throws Exception {
        ArraySet<Integer> requestedPropertyIds = new ArraySet<>(Set.of(
                0, 1, VENDOR_CONTINUOUS_PROPERTY, HVAC_TEMPERATURE_SET));
        CarPropertyConfig config1 = CarPropertyConfig.newBuilder(Integer.class,
                VENDOR_CONTINUOUS_PROPERTY,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(0).build()).build();
        CarPropertyConfig config2 = CarPropertyConfig.newBuilder(Float.class,
                HVAC_TEMPERATURE_SET,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)
                .addAreaIdConfig(new AreaIdConfig.Builder<Float>(0).build()).build();
        addCarPropertyConfig(config1);
        addCarPropertyConfig(config2);

        assertThat(mCarPropertyManager.getPropertyList(requestedPropertyIds))
                .containsExactly(config1, config2);
    }

    @Test
    public void testGetCarPropertyConfig() throws Exception {
        assertThat(mCarPropertyManager.getCarPropertyConfig(VENDOR_ON_CHANGE_PROPERTY))
                .isEqualTo(mOnChangeCarPropertyConfig);
    }

    @Test
    public void testGetCarPropertyConfig_noConfigReturned_notSupported() throws Exception {
        setPropIdWithoutConfig(HVAC_TEMPERATURE_SET);

        assertThat(mCarPropertyManager.getCarPropertyConfig(HVAC_TEMPERATURE_SET)).isNull();
    }

    @Test
    public void testGetCarPropertyConfig_noConfigReturned_noPermission() throws Exception {
        setPropIdWithoutPermission(HVAC_TEMPERATURE_SET);

        assertThat(mCarPropertyManager.getCarPropertyConfig(HVAC_TEMPERATURE_SET)).isNull();
    }

    @Test
    public void testGetCarPropertyConfig_unsupported() throws Exception {
        assertThat(mCarPropertyManager.getCarPropertyConfig(/* propId= */ 0)).isNull();
    }

    @Test
    public void testGetAreaId_global() throws Exception {
        assertThat(mCarPropertyManager.getAreaId(VENDOR_ON_CHANGE_PROPERTY, 0)).isEqualTo(0);
    }

    @Test
    public void testGetAreaId_withArea() throws Exception {
        int areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT | VehicleAreaSeat.SEAT_ROW_1_CENTER;
        CarPropertyConfig config1 = CarPropertyConfig.newBuilder(Integer.class,
                HVAC_TEMPERATURE_SET,
                VehicleAreaType.VEHICLE_AREA_TYPE_SEAT)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(areaId)
                .build()).build();
        addCarPropertyConfig(config1);

        assertThat(mCarPropertyManager.getAreaId(
                HVAC_TEMPERATURE_SET, VehicleAreaSeat.SEAT_ROW_1_LEFT)).isEqualTo(areaId);
        assertThat(mCarPropertyManager.getAreaId(
                HVAC_TEMPERATURE_SET, VehicleAreaSeat.SEAT_ROW_1_CENTER)).isEqualTo(areaId);
    }

    @Test
    public void testGetAreaId_areaNotSupported() throws Exception {
        int areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT | VehicleAreaSeat.SEAT_ROW_1_CENTER;
        CarPropertyConfig config1 = CarPropertyConfig.newBuilder(Integer.class,
                HVAC_TEMPERATURE_SET,
                VehicleAreaType.VEHICLE_AREA_TYPE_SEAT)
                .addAreaIdConfig(new AreaIdConfig.Builder<Integer>(areaId)
                .build()).build();
        addCarPropertyConfig(config1);

        assertThrows(IllegalArgumentException.class, () -> mCarPropertyManager.getAreaId(
                HVAC_TEMPERATURE_SET, VehicleAreaSeat.SEAT_ROW_1_RIGHT));
    }

    @Test
    public void testGetAreaId_propertyNotSupported() throws Exception {
        setPropIdWithoutConfig(HVAC_TEMPERATURE_SET);

        assertThrows(IllegalArgumentException.class, () -> mCarPropertyManager.getAreaId(
                HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetAreaId_noPermissionToProperty() throws Exception {
        setPropIdWithoutPermission(HVAC_TEMPERATURE_SET);

        assertThrows(IllegalArgumentException.class, () -> mCarPropertyManager.getAreaId(
                HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testGetAreaId_remoteExceptionFromCarService() throws Exception {
        when(mICarProperty.getPropertyConfigList(new int[]{HVAC_TEMPERATURE_SET}))
                .thenThrow(new RemoteException());

        assertThrows(IllegalArgumentException.class, () -> mCarPropertyManager.getAreaId(
                HVAC_TEMPERATURE_SET, 0));
    }

    @Test
    public void testIsPropertyAvailable_withValueWithNotAvailableStatus() throws Exception {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_ERROR, TEST_TIMESTAMP, 17.0f);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isFalse();
    }

    @Test
    public void testIsPropertyAvailable() throws Exception {
        CarPropertyValue<Integer> expectedValue = new CarPropertyValue<>(HVAC_TEMPERATURE_SET,
                /* areaId= */ 0, CarPropertyValue.STATUS_AVAILABLE, /* timestamp= */ 0,
                /* value= */ 1);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(expectedValue);

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isTrue();
    }

    @Test
    public void testIsPropertyAvailable_syncOpTryAgain() throws Exception {
        CarPropertyValue<Integer> expectedValue = new CarPropertyValue<>(HVAC_TEMPERATURE_SET,
                /* areaId= */ 0, CarPropertyValue.STATUS_AVAILABLE, /* timestamp= */ 0,
                /* value= */ 1);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN)).thenReturn(expectedValue);

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isTrue();
        verify(mICarProperty, times(2)).getProperty(HVAC_TEMPERATURE_SET, 0);
    }

    @Test
    public void testIsPropertyAvailable_notAvailable() throws Exception {
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_NOT_AVAILABLE));

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isFalse();
    }

    @Test
    public void testIsPropertyAvailable_tryAgain() throws Exception {
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(VehicleHalStatusCode.STATUS_TRY_AGAIN));

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isFalse();
    }


    @Test
    public void testIsPropertyAvailable_RemoteException() throws Exception {
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new RemoteException());

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isFalse();
    }

    @Test
    public void testIsPropertyAvailable_unsupported() throws Exception {
        assertThat(mCarPropertyManager.isPropertyAvailable(/* propId= */ 0, /* areaId= */ 0))
                .isFalse();
    }

    @Test
    public void testGetCarPropertyConfig_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getCarPropertyConfig(INITIAL_USER_INFO));
    }

    @Test
    public void testGetPropertyList_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getPropertyList(
                        new ArraySet<Integer>(Set.of(HVAC_TEMPERATURE_SET, INITIAL_USER_INFO))));
    }

    @Test
    public void testGetAreaId_userHalProperty()  throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getAreaId(INITIAL_USER_INFO, /* area= */ 0));
    }

    @Test
    public void testGetReadPermission_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getReadPermission(INITIAL_USER_INFO));
    }

    @Test
    public void testGetWritePermission_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getWritePermission(INITIAL_USER_INFO));
    }

    @Test
    public void testIsPropertyAvailable_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.isPropertyAvailable(INITIAL_USER_INFO, /* areaId= */ 0));
    }

    @Test
    public void testGetProperty_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getProperty(INITIAL_USER_INFO, /* areaId= */ 0));
    }

    @Test
    public void testGetBooleanProperty_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.getBooleanProperty(INITIAL_USER_INFO, /* areaId= */ 0));
    }

    @Test
    public void testSetBooleanProperty_userHalProperty() throws Exception {
        setAppTargetSdk(Build.VERSION_CODES.TIRAMISU);

        assertThrows(IllegalArgumentException.class,
                () -> mCarPropertyManager.setBooleanProperty(INITIAL_USER_INFO, /* areaId= */ 0,
                        true));
    }
}
