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

import android.annotation.Nullable;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.hal.fakevhal.FakeVehicleStub;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.CarPropertyErrorCodes.CarPropMgrErrorCode;

import java.io.FileDescriptor;
import java.util.List;

/**
 * VehicleStub represents an IVehicle service interface in either AIDL or legacy HIDL version. It
 * exposes common interface so that the client does not need to care about which version the
 * underlying IVehicle service is in.
 */
public abstract class VehicleStub {

    /**
     * SubscriptionClient represents a client that could subscribe/unsubscribe to properties.
     */
    public interface SubscriptionClient {
        /**
         * Subscribes to a property.
         *
         * @param options The list of subscribe options.
         * @throws RemoteException if the remote operation fails.
         * @throws ServiceSpecificException if VHAL returns service specific error.
         */
        void subscribe(SubscribeOptions[] options) throws RemoteException, ServiceSpecificException;

        /**
         * Unsubscribes from a property.
         *
         * @param prop The ID for the property to unsubscribe.
         * @throws RemoteException if the remote operation fails.
         * @throws ServiceSpecificException if VHAL returns service specific error.
         */
        void unsubscribe(int prop) throws RemoteException, ServiceSpecificException;
    }

    /**
     * A request for {@link VehicleStub#getAsync} or {@link VehicleStub#setAsync}.
     */
    public static class AsyncGetSetRequest {
        private final int mServiceRequestId;
        private final HalPropValue mHalPropValue;
        private final long mTimeoutUptimeMs;

        public int getServiceRequestId() {
            return mServiceRequestId;
        }

        public HalPropValue getHalPropValue() {
            return mHalPropValue;
        }

        public long getTimeoutUptimeMs() {
            return mTimeoutUptimeMs;
        }

        /**
         * Get an instance for AsyncGetSetRequest.
         */
        public AsyncGetSetRequest(int serviceRequestId, HalPropValue halPropValue,
                long timeoutUptimeMs) {
            mServiceRequestId = serviceRequestId;
            mHalPropValue = halPropValue;
            mTimeoutUptimeMs = timeoutUptimeMs;
        }
    }

    /**
     * A result for {@link VehicleStub#getAsync}.
     */
    public static final class GetVehicleStubAsyncResult {
        private final int mServiceRequestId;
        @Nullable
        private final HalPropValue mHalPropValue;
        private final CarPropertyErrorCodes mCarPropertyErrorCodes;

        public int getServiceRequestId() {
            return mServiceRequestId;
        }

        @Nullable
        public HalPropValue getHalPropValue() {
            return mHalPropValue;
        }

        @CarPropMgrErrorCode
        public int getErrorCode() {
            return mCarPropertyErrorCodes.getCarPropertyManagerErrorCode();
        }

        public int getVendorErrorCode() {
            return mCarPropertyErrorCodes.getVendorErrorCode();
        }

        public int getSystemErrorCode() {
            return mCarPropertyErrorCodes.getSystemErrorCode();
        }

        public CarPropertyErrorCodes getCarPropertyErrorCodes() {
            return mCarPropertyErrorCodes;
        }

        /**
         * Constructs an instance for GetVehicleStubAsyncResult when result returned successfully.
         */
        public GetVehicleStubAsyncResult(int serviceRequestId, HalPropValue halPropValue) {
            mServiceRequestId = serviceRequestId;
            mHalPropValue = halPropValue;
            mCarPropertyErrorCodes = CarPropertyErrorCodes.STATUS_OK_NO_ERROR;
        }

        /**
         * Constructs an instance for GetVehicleStubAsyncResult when errors.
         */
        public GetVehicleStubAsyncResult(int serviceRequestId, CarPropertyErrorCodes errorCodes) {
            mServiceRequestId = serviceRequestId;
            mHalPropValue = null;
            mCarPropertyErrorCodes = errorCodes;
        }
    }

    /**
     * A result for {@link VehicleStub#setAsync}.
     */
    public static final class SetVehicleStubAsyncResult {
        private final int mServiceRequestId;
        @CarPropMgrErrorCode
        private final CarPropertyErrorCodes mCarPropertyErrorCodes;

        public int getServiceRequestId() {
            return mServiceRequestId;
        }

        @CarPropMgrErrorCode
        public int getErrorCode() {
            return mCarPropertyErrorCodes.getCarPropertyManagerErrorCode();
        }

        public int getVendorErrorCode() {
            return mCarPropertyErrorCodes.getVendorErrorCode();
        }

        public int getSystemErrorCode() {
            return mCarPropertyErrorCodes.getSystemErrorCode();
        }

        public CarPropertyErrorCodes getCarPropertyErrorCodes() {
            return mCarPropertyErrorCodes;
        }

        /**
         * Constructs an success result.
         */
        public SetVehicleStubAsyncResult(int serviceRequestId) {
            mServiceRequestId = serviceRequestId;
            mCarPropertyErrorCodes = CarPropertyErrorCodes.STATUS_OK_NO_ERROR;
        }

        /**
         * Constructs an instance for SetVehicleStubAsyncResult when errors.
         */
        public SetVehicleStubAsyncResult(int serviceRequestId, CarPropertyErrorCodes errorCodes) {
            mServiceRequestId = serviceRequestId;
            mCarPropertyErrorCodes = errorCodes;
        }
    }

    /**
     * A callback for asynchronous operations.
     */
    public abstract static class VehicleStubCallbackInterface {
        /**
         * Method called when {@link getAsync} returns results.
         */
        public abstract void onGetAsyncResults(
                List<GetVehicleStubAsyncResult> getVehicleStubAsyncResults);

        /**
         * Method called when {@link setAsync} returns results.
         */
        public abstract void onSetAsyncResults(
                List<SetVehicleStubAsyncResult> setVehicleStubAsyncResults);

        /**
         * Register a callback that will be called when the callback binder died.
         */
        public abstract void linkToDeath(DeathRecipient recipient) throws RemoteException;

        /**
         * Method called when async requests timed-out.
         *
         * If the callback's binder is already dead, this function will not be called.
         */
        public abstract void onRequestsTimeout(List<Integer> serviceRequestIds);
    }

    /**
     * Gets a property asynchronously.
     */
    public abstract void getAsync(List<AsyncGetSetRequest> getVehicleStubAsyncRequests,
            VehicleStubCallbackInterface getVehicleStubAsyncCallback);

    /**
     * Sets a property asynchronously.
     */
    public abstract void setAsync(List<AsyncGetSetRequest> setVehicleStubAsyncRequests,
            VehicleStubCallbackInterface setVehicleStubAsyncCallback);

    /**
     * Checks whether we are connected to AIDL VHAL: {@code true} or HIDL VHAL: {@code false}.
     */
    public abstract boolean isAidlVhal();

    /**
     * Creates a new VehicleStub to connect to Vehicle HAL.
     *
     * Create a new VehicleStub to connect to Vehicle HAL according to which backend (AIDL or HIDL)
     * is available. This function will throw {@link IllegalStateException} if no vehicle HAL is
     * available.
     *
     * @return a vehicle stub to connect to Vehicle HAL.
     */
    public static VehicleStub newVehicleStub() throws IllegalStateException {
        VehicleStub stub = new AidlVehicleStub();
        if (stub.isValid()) {
            if ((BuildHelper.isUserDebugBuild() || BuildHelper.isEngBuild())
                    && FakeVehicleStub.doesEnableFileExist()) {
                try {
                    return new FakeVehicleStub(stub);
                } catch (Exception e) {
                    Slogf.e(CarLog.TAG_SERVICE, e, "Failed to create FakeVehicleStub. "
                            + "Fallback to using real VehicleStub.");
                }
            }
            return stub;
        }

        Slogf.i(CarLog.TAG_SERVICE, "No AIDL vehicle HAL found, fall back to HIDL version");

        stub = new HidlVehicleStub();

        if (!stub.isValid()) {
            throw new IllegalStateException("Vehicle HAL service is not available.");
        }

        return stub;
    }

    /**
     * Gets a HalPropValueBuilder that could be used to build a HalPropValue.
     *
     * @return a builder to build HalPropValue.
     */
    public abstract HalPropValueBuilder getHalPropValueBuilder();

    /**
     * Returns whether this vehicle stub is connecting to a valid vehicle HAL.
     *
     * @return Whether this vehicle stub is connecting to a valid vehicle HAL.
     */
    public abstract boolean isValid();

    /**
     * Gets the interface descriptor for the connecting vehicle HAL.
     *
     * @return the interface descriptor.
     * @throws IllegalStateException If unable to get the descriptor.
     */
    public abstract String getInterfaceDescriptor() throws IllegalStateException;

    /**
     * Registers a death recipient that would be called when vehicle HAL died.
     *
     * @param recipient A death recipient.
     * @throws IllegalStateException If unable to register the death recipient.
     */
    public abstract void linkToDeath(IVehicleDeathRecipient recipient) throws IllegalStateException;

    /**
     * Unlinks a previously linked death recipient.
     *
     * @param recipient A previously linked death recipient.
     */
    public abstract void unlinkToDeath(IVehicleDeathRecipient recipient);

    /**
     * Gets all property configs.
     *
     * @return All the property configs.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract HalPropConfig[] getAllPropConfigs()
            throws RemoteException, ServiceSpecificException;

    /**
     * Gets a new {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     *
     * Caller MUST unsubscribe all subscribed properties before discarding the client to prevent
     * resource leak.
     *
     * @param callback A callback that could be used to receive events.
     * @return a {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     */
    public abstract SubscriptionClient newSubscriptionClient(VehicleHalCallback callback);

    /**
     * Gets a property.
     *
     * @param requestedPropValue The property to get.
     * @return The vehicle property value.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Nullable
    public abstract HalPropValue get(HalPropValue requestedPropValue)
            throws RemoteException, ServiceSpecificException;

    /**
     * Sets a property.
     *
     * @param propValue The property to set.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void set(HalPropValue propValue)
            throws RemoteException, ServiceSpecificException;

    /**
     * Dumps VHAL debug information.
     *
     * Additional arguments could also be provided through {@link args} to debug VHAL.
     *
     * @param fd The file descriptor to print output.
     * @param args Optional additional arguments for the debug command. Can be empty.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void dump(FileDescriptor fd, List<String> args)
            throws RemoteException, ServiceSpecificException;

    /**
     * Checks if fake VHAL is enabled.
     *
     * @return {@code true} if a FakeVehicleStub instance is created.
     */
    public boolean isFakeModeEnabled() {
        return false;
    }

    /**
     * Cancels all the on-going async requests with the given request IDs.
     *
     * @param requestIds a list of async get/set request IDs.
     */
    public void cancelRequests(List<Integer> requestIds) {}
}
