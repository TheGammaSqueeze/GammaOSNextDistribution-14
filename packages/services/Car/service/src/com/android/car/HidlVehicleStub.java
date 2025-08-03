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

package com.android.car;

import static com.android.car.CarServiceUtils.subscribeOptionsToHidl;
import static com.android.car.internal.property.CarPropertyErrorCodes.convertVhalStatusCodeToCarPropertyManagerErrorCodes;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.hardware.property.CarPropertyManager;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.os.NativeHandle;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;

import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.HidlHalPropConfig;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class HidlVehicleStub extends VehicleStub {

    private static final String TAG = CarLog.tagFor(HidlVehicleStub.class);

    // The property ID for "SUPPORTED_PROPRETY_IDS". This is the same as SUPPORTED_PROPERTY_IDS as
    // defined in
    // {@code platform/hardware/interfaces/automotive/vehicle/aidl/android/hardware/automotive/vehicle/VehicleProperty.aidl}.
    private static final int VHAL_PROP_SUPPORTED_PROPERTY_IDS = 0x11410F48;

    private final IVehicle mHidlVehicle;
    private final HalPropValueBuilder mPropValueBuilder;
    private final Executor mExecutor = Executors.newFixedThreadPool(5);

    HidlVehicleStub() {
        this(getHidlVehicle());
    }

    @VisibleForTesting
    HidlVehicleStub(IVehicle hidlVehicle) {
        mHidlVehicle = hidlVehicle;
        mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/false);
    }

    /**
     * Checks whether we are connected to AIDL VHAL: {@code true} or HIDL VHAL: {@code false}.
     */
    @Override
    public boolean isAidlVhal() {
        return false;
    }

    /**
     * Gets a HalPropValueBuilder that could be used to build a HalPropValue.
     *
     * @return a builder to build HalPropValue.
     */
    @Override
    public HalPropValueBuilder getHalPropValueBuilder() {
        return mPropValueBuilder;
    }

    /**
     * Returns whether this vehicle stub is connecting to a valid vehicle HAL.
     *
     * @return Whether this vehicle stub is connecting to a valid vehicle HAL.
     */
    @Override
    public boolean isValid() {
        return mHidlVehicle != null;
    }

    /**
     * Gets the interface descriptor for the connecting vehicle HAL.
     *
     * @return the interface descriptor.
     * @throws IllegalStateException If unable to get the descriptor.
     */
    @Override
    public String getInterfaceDescriptor() throws IllegalStateException {
        try {
            return mHidlVehicle.interfaceDescriptor();
        } catch (RemoteException e) {
            throw new IllegalStateException("Unable to get Vehicle HAL interface descriptor", e);
        }
    }

    /**
     * Registers a death recipient that would be called when vehicle HAL died.
     *
     * @param recipient A death recipient.
     * @throws IllegalStateException If unable to register the death recipient.
     */
    @Override
    public void linkToDeath(IVehicleDeathRecipient recipient) throws IllegalStateException {
        try {
            mHidlVehicle.linkToDeath(recipient, /*flag=*/ 0);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to linkToDeath Vehicle HAL");
        }
    }

    /**
     * Unlinks a previously linked death recipient.
     *
     * @param recipient A previously linked death recipient.
     */
    @Override
    public void unlinkToDeath(IVehicleDeathRecipient recipient) {
        try {
            mHidlVehicle.unlinkToDeath(recipient);
        } catch (RemoteException ignored) {
            // Ignore errors on shutdown path.
        }
    }

    /**
     * Gets all property configs.
     *
     * @return All the property configs.
     */
    @Override
    public HalPropConfig[] getAllPropConfigs() throws RemoteException {
        ArrayList<VehiclePropConfig> configForSupportedProps;
        try {
            configForSupportedProps = getPropConfigs(new ArrayList<>(
                    List.of(VHAL_PROP_SUPPORTED_PROPERTY_IDS)));
        } catch (Exception e) {
            Slogf.d(TAG, "Use getAllPropConfigs to fetch all property configs");

            // If the VHAL_PROP_SUPPORTED_PROPERTY_IDS is not supported, fallback to normal API.
            return vehiclePropConfigsToHalPropConfigs(mHidlVehicle.getAllPropConfigs());
        }

        if (configForSupportedProps.size() == 0) {
            Slogf.w(TAG, "getPropConfigs[VHAL_PROP_SUPPORTED_IDS] returns 0 config"
                    + "assume it is not supported, fall back to getAllPropConfigs.");
            return vehiclePropConfigsToHalPropConfigs(mHidlVehicle.getAllPropConfigs());
        }

        // If the VHAL_PROP_SUPPORTED_PROPERTY_IDS is supported, VHAL has
        // too many property configs that cannot be returned in getAllPropConfigs() in one binder
        // transaction.
        // We need to get the property list and then divide the list into smaller requests.
        Slogf.d(TAG, "VHAL_PROP_SUPPORTED_PROPERTY_IDS is supported, "
                + "use multiple getPropConfigs to fetch all property configs");

        return getAllPropConfigsThroughMultipleRequests(configForSupportedProps.get(0));
    }

    /**
     * Gets a new {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     *
     * @param callback A callback that could be used to receive events.
     * @return a {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     */
    @Override
    public SubscriptionClient newSubscriptionClient(VehicleHalCallback callback) {
        return new HidlSubscriptionClient(callback, mPropValueBuilder);
    }

    private static class GetValueResult {
        public int status;
        public VehiclePropValue value;
    }

    /**
     * Gets a property.
     *
     * @param requestedPropValue The property to get.
     * @return The vehicle property value.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    @Nullable
    public HalPropValue get(HalPropValue requestedPropValue)
            throws RemoteException, ServiceSpecificException {
        VehiclePropValue hidlPropValue = (VehiclePropValue) requestedPropValue.toVehiclePropValue();
        GetValueResult result = new GetValueResult();
        mHidlVehicle.get(
                hidlPropValue,
                (s, p) -> {
                    result.status = s;
                    result.value = p;
                });

        if (result.status != android.hardware.automotive.vehicle.V2_0.StatusCode.OK) {
            throw new ServiceSpecificException(
                    result.status,
                    "failed to get value for property: " + Integer.toString(hidlPropValue.prop));
        }

        if (result.value == null) {
            return null;
        }

        return getHalPropValueBuilder().build(result.value);
    }

    @Override
    public void getAsync(List<AsyncGetSetRequest> getVehicleStubAsyncRequests,
            VehicleStubCallbackInterface getVehicleStubAsyncCallback) {
        mExecutor.execute(() -> {
            for (int i = 0; i < getVehicleStubAsyncRequests.size(); i++) {
                AsyncGetSetRequest getVehicleStubAsyncRequest = getVehicleStubAsyncRequests.get(i);
                int serviceRequestId = getVehicleStubAsyncRequest.getServiceRequestId();
                HalPropValue halPropValue;
                try {
                    halPropValue = get(getVehicleStubAsyncRequest.getHalPropValue());
                } catch (ServiceSpecificException e) {
                    CarPropertyErrorCodes carPropertyErrorCodes =
                            convertVhalStatusCodeToCarPropertyManagerErrorCodes(e.errorCode);
                    callGetAsyncErrorCallback(carPropertyErrorCodes, serviceRequestId,
                            getVehicleStubAsyncCallback);
                    continue;
                } catch (RemoteException e) {
                    Slogf.w(CarLog.TAG_SERVICE,
                            "Received RemoteException from VHAL. VHAL is likely dead.");
                    callGetAsyncErrorCallback(
                            new CarPropertyErrorCodes(
                                    CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                                    /* vendorErrorCode= */ 0,
                                    /* systemErrorCode= */ 0),
                            serviceRequestId, getVehicleStubAsyncCallback);
                    continue;
                }

                if (halPropValue == null) {
                    callGetAsyncErrorCallback(
                            new CarPropertyErrorCodes(
                                    CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE,
                                    /* vendorErrorCode= */ 0,
                                    /* systemErrorCode= */ 0),
                            serviceRequestId, getVehicleStubAsyncCallback);
                    continue;
                }

                getVehicleStubAsyncCallback.onGetAsyncResults(
                        List.of(new GetVehicleStubAsyncResult(serviceRequestId, halPropValue)));
            }
        });
    }

    @Override
    public void setAsync(List<AsyncGetSetRequest> setVehicleStubAsyncRequests,
            VehicleStubCallbackInterface setVehicleStubAsyncCallback) {
        mExecutor.execute(() -> {
            for (int i = 0; i < setVehicleStubAsyncRequests.size(); i++) {
                AsyncGetSetRequest setVehicleStubAsyncRequest = setVehicleStubAsyncRequests.get(i);
                int serviceRequestId = setVehicleStubAsyncRequest.getServiceRequestId();
                try {
                    set(setVehicleStubAsyncRequest.getHalPropValue());
                    setVehicleStubAsyncCallback.onSetAsyncResults(
                            List.of(new SetVehicleStubAsyncResult(serviceRequestId)));
                } catch (ServiceSpecificException e) {
                    CarPropertyErrorCodes carPropertyErrorCodes =
                            convertVhalStatusCodeToCarPropertyManagerErrorCodes(e.errorCode);
                    callSetAsyncErrorCallback(
                            carPropertyErrorCodes,
                            serviceRequestId,
                            setVehicleStubAsyncCallback);
                } catch (RemoteException e) {
                    Slogf.w(CarLog.TAG_SERVICE,
                            "Received RemoteException from VHAL. VHAL is likely dead.");
                    callSetAsyncErrorCallback(
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0),
                            serviceRequestId, setVehicleStubAsyncCallback);
                }
            }
        });
    }

    private void callGetAsyncErrorCallback(CarPropertyErrorCodes errorCodes, int serviceRequestId,
            VehicleStubCallbackInterface callback) {
        callback.onGetAsyncResults(
                List.of(new GetVehicleStubAsyncResult(serviceRequestId, errorCodes)));
    }

    private void callSetAsyncErrorCallback(CarPropertyErrorCodes errorCodes, int serviceRequestId,
            VehicleStubCallbackInterface callback) {
        callback.onSetAsyncResults(
                List.of(new SetVehicleStubAsyncResult(serviceRequestId, errorCodes)));
    }

    /**
     * Sets a property.
     *
     * @param propValue The property to set.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    public void set(HalPropValue propValue) throws RemoteException {
        VehiclePropValue hidlPropValue = (VehiclePropValue) propValue.toVehiclePropValue();
        int status = mHidlVehicle.set(hidlPropValue);
        if (status != StatusCode.OK) {
            throw new ServiceSpecificException(status, "failed to set value for property: "
                    + Integer.toString(hidlPropValue.prop));
        }
    }

    @Override
    public void dump(FileDescriptor fd, List<String> args) throws RemoteException {
        mHidlVehicle.debug(new NativeHandle(fd, /* own= */ false), new ArrayList<String>(args));
    }

    @Nullable
    private static IVehicle getHidlVehicle() {
        String instanceName = SystemProperties.get("ro.vehicle.hal", "default");

        try {
            // Wait for HIDL VHAL to be ready if it is declared.
            return IVehicle.getService(instanceName, /* retry= */ true);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "Failed to get IVehicle/" + instanceName + " service");
        } catch (NoSuchElementException e) {
            Slogf.e(TAG, "IVehicle/" + instanceName + " service not registered yet");
        }
        return null;
    }

    private class HidlSubscriptionClient extends IVehicleCallback.Stub
            implements SubscriptionClient {
        private final VehicleHalCallback mCallback;
        private final HalPropValueBuilder mBuilder;

        HidlSubscriptionClient(VehicleHalCallback callback, HalPropValueBuilder builder) {
            mCallback = callback;
            mBuilder = builder;
        }

        @Override
        public void onPropertyEvent(ArrayList<VehiclePropValue> propValues) {
            ArrayList<HalPropValue> values = new ArrayList<>();
            for (VehiclePropValue value : propValues) {
                values.add(mBuilder.build(value));
            }
            mCallback.onPropertyEvent(values);
        }

        @Override
        public void onPropertySet(VehiclePropValue propValue) {
            // Deprecated, do nothing.
        }

        @Override
        public void onPropertySetError(int errorCode, int propId, int areaId) {
            VehiclePropError error = new VehiclePropError();
            error.propId = propId;
            error.areaId = areaId;
            error.errorCode = errorCode;
            ArrayList<VehiclePropError> errors = new ArrayList<VehiclePropError>();
            errors.add(error);
            mCallback.onPropertySetError(errors);
        }

        @Override
        public void subscribe(SubscribeOptions[] options) throws RemoteException {
            ArrayList<android.hardware.automotive.vehicle.V2_0.SubscribeOptions> hidlOptions =
                    new ArrayList<>();
            for (SubscribeOptions option : options) {
                hidlOptions.add(subscribeOptionsToHidl(option));
            }
            mHidlVehicle.subscribe(this, hidlOptions);
        }

        @Override
        public void unsubscribe(int prop) throws RemoteException {
            mHidlVehicle.unsubscribe(this, prop);
        }
    }

    private static HalPropConfig[] vehiclePropConfigsToHalPropConfigs(
            List<VehiclePropConfig> hidlConfigs) {
        int configSize = hidlConfigs.size();
        HalPropConfig[] configs = new HalPropConfig[configSize];
        for (int i = 0; i < configSize; i++) {
            configs[i] = new HidlHalPropConfig(hidlConfigs.get(i));
        }
        return configs;
    }

    private static final class GetPropConfigsResult {
        public int status;
        public ArrayList<VehiclePropConfig> propConfigs;
    }

    private HalPropConfig[] getAllPropConfigsThroughMultipleRequests(
            VehiclePropConfig configForSupportedProps)
            throws RemoteException, ServiceSpecificException {
        if (configForSupportedProps.configArray.size() < 1) {
            throw new IllegalArgumentException(
                    "VHAL Property: SUPPORTED_PROPERTY_IDS must have one element: "
                    + "[num_of_configs_per_request] in the config array");
        }

        int numConfigsPerRequest = configForSupportedProps.configArray.get(0);
        if (numConfigsPerRequest <= 0) {
            throw new IllegalArgumentException("Number of configs per request must be > 0");
        }
        HalPropValue propIdsRequestValue = mPropValueBuilder.build(
                VHAL_PROP_SUPPORTED_PROPERTY_IDS, /* areaId= */ 0);
        HalPropValue propIdsResultValue;
        try {
            propIdsResultValue = get(propIdsRequestValue);
        } catch (Exception e) {
            Slogf.e(TAG, e, "failed to get SUPPORTED_PROPRETY_IDS");
            throw e;
        }
        int status = propIdsResultValue.getStatus();
        if (status != VehiclePropertyStatus.AVAILABLE) {
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "got non-okay status: "  + StatusCode.toString(status)
                    + " for SUPPORTED_PROPERTY_IDS");
        }
        int propCount = propIdsResultValue.getInt32ValuesSize();
        ArrayList<VehiclePropConfig> allConfigs = new ArrayList<>();
        ArrayList<Integer> requestPropIds = new ArrayList<Integer>();
        for (int i = 0; i < propCount; i++) {
            requestPropIds.add(propIdsResultValue.getInt32Value(i));
            if (requestPropIds.size() == numConfigsPerRequest || (i + 1) == propCount) {
                ArrayList<VehiclePropConfig> subConfigs = getPropConfigs(requestPropIds);
                allConfigs.addAll(subConfigs);
                requestPropIds.clear();
            }
        }
        return vehiclePropConfigsToHalPropConfigs(allConfigs);
    }

    private ArrayList<VehiclePropConfig> getPropConfigs(ArrayList<Integer> propIds)
            throws RemoteException {
        GetPropConfigsResult result = new GetPropConfigsResult();
        mHidlVehicle.getPropConfigs(propIds,
                (status, propConfigs) -> {
                    result.status = status;
                    result.propConfigs = propConfigs;
                });
        if (result.status != StatusCode.OK) {
            throw new IllegalArgumentException("Part of the property IDs: " + propIds
                    + " is not supported");
        }
        return result.propConfigs;
    }
}
