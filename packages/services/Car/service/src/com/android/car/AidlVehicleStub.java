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

import static com.android.car.internal.property.CarPropertyErrorCodes.convertVhalStatusCodeToCarPropertyManagerErrorCodes;

import android.annotation.Nullable;
import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.hardware.property.CarPropertyManager;
import android.car.util.concurrent.AndroidFuture;
import android.hardware.automotive.vehicle.GetValueRequest;
import android.hardware.automotive.vehicle.GetValueRequests;
import android.hardware.automotive.vehicle.GetValueResult;
import android.hardware.automotive.vehicle.GetValueResults;
import android.hardware.automotive.vehicle.IVehicle;
import android.hardware.automotive.vehicle.IVehicleCallback;
import android.hardware.automotive.vehicle.SetValueRequest;
import android.hardware.automotive.vehicle.SetValueRequests;
import android.hardware.automotive.vehicle.SetValueResult;
import android.hardware.automotive.vehicle.SetValueResults;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropConfigs;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehiclePropErrors;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropValues;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LongSparseArray;

import com.android.car.hal.AidlHalPropConfig;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.internal.LargeParcelable;
import com.android.car.internal.LongPendingRequestPool;
import com.android.car.internal.LongPendingRequestPool.TimeoutCallback;
import com.android.car.internal.LongRequestIdWithTimeout;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.expresslog.Histogram;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class AidlVehicleStub extends VehicleStub {
    private static final Histogram sVehicleHalGetSyncLatencyHistogram = new Histogram(
            "automotive_os.value_sync_hal_get_property_latency",
            new Histogram.ScaledRangeOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* firstBinWidth= */ 2, /* scaleFactor= */ 1.5f));

    private static final Histogram sVehicleHalSetSyncLatencyHistogram = new Histogram(
            "automotive_os.value_sync_hal_set_property_latency",
            new Histogram.ScaledRangeOptions(/* binCount= */ 20, /* minValue= */ 0,
                    /* firstBinWidth= */ 2, /* scaleFactor= */ 1.5f));

    private static final String AIDL_VHAL_SERVICE =
            "android.hardware.automotive.vehicle.IVehicle/default";
    // default timeout: 10s
    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    private static final String TAG = CarLog.tagFor(AidlVehicleStub.class);
    private static final long TRACE_TAG = TraceHelper.TRACE_TAG_CAR_SERVICE;

    private final IVehicle mAidlVehicle;
    private final HalPropValueBuilder mPropValueBuilder;
    private final GetSetValuesCallback mGetSetValuesCallback;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final AtomicLong mRequestId = new AtomicLong(0);
    private final Object mLock = new Object();
    // PendingSyncRequestPool is thread-safe.
    private final PendingSyncRequestPool<GetValueResult> mPendingSyncGetValueRequestPool =
            new PendingSyncRequestPool<>();
    private final PendingSyncRequestPool<SetValueResult> mPendingSyncSetValueRequestPool =
            new PendingSyncRequestPool<>();
    // PendingAsyncRequestPool is thread-safe.
    private final PendingAsyncRequestPool mPendingAsyncRequestPool;

    // This might be modifed during tests.
    private long mSyncOpTimeoutInMs = DEFAULT_TIMEOUT_MS;

    private static class AsyncRequestInfo implements LongRequestIdWithTimeout {
        private final int mServiceRequestId;
        private final VehicleStubCallbackInterface mClientCallback;
        private final long mTimeoutUptimeMs;
        private final long mVhalRequestId;

        private AsyncRequestInfo(
                long vhalRequestId,
                int serviceRequestId,
                VehicleStubCallbackInterface clientCallback,
                long timeoutUptimeMs) {
            mVhalRequestId = vhalRequestId;
            mServiceRequestId = serviceRequestId;
            mClientCallback = clientCallback;
            mTimeoutUptimeMs = timeoutUptimeMs;
        }

        @Override
        public long getRequestId() {
            return mVhalRequestId;
        }

        @Override
        public long getTimeoutUptimeMs() {
            return mTimeoutUptimeMs;
        }

        public int getServiceRequestId() {
            return mServiceRequestId;
        }

        public VehicleStubCallbackInterface getClientCallback() {
            return mClientCallback;
        }
    }

    AidlVehicleStub() {
        this(getAidlVehicle());
    }

    @VisibleForTesting
    AidlVehicleStub(IVehicle aidlVehicle) {
        this(aidlVehicle,
                CarServiceUtils.getHandlerThread(AidlVehicleStub.class.getSimpleName()));
    }

    @VisibleForTesting
    AidlVehicleStub(IVehicle aidlVehicle, HandlerThread handlerThread) {
        mAidlVehicle = aidlVehicle;
        mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);
        mHandlerThread = handlerThread;
        mHandler = new Handler(mHandlerThread.getLooper());
        mGetSetValuesCallback = new GetSetValuesCallback();
        mPendingAsyncRequestPool = new PendingAsyncRequestPool(mHandler.getLooper());
    }

    /**
     * Sets the timeout for getValue/setValue requests in milliseconds.
     */
    @VisibleForTesting
    void setSyncOpTimeoutInMs(long timeoutMs) {
        mSyncOpTimeoutInMs = timeoutMs;
    }

    @VisibleForTesting
    int countPendingRequests() {
        synchronized (mLock) {
            return mPendingAsyncRequestPool.size()
                    + mPendingSyncGetValueRequestPool.size()
                    + mPendingSyncSetValueRequestPool.size();
        }
    }

    /**
     * Checks whether we are connected to AIDL VHAL: {@code true} or HIDL VHAL: {@code false}.
     */
    @Override
    public boolean isAidlVhal() {
        return true;
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
        return mAidlVehicle != null;
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
            return mAidlVehicle.asBinder().getInterfaceDescriptor();
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
            mAidlVehicle.asBinder().linkToDeath(recipient, /*flag=*/ 0);
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
        mAidlVehicle.asBinder().unlinkToDeath(recipient, /*flag=*/ 0);
    }

    /**
     * Gets all property configs.
     *
     * @return All the property configs.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    public HalPropConfig[] getAllPropConfigs()
            throws RemoteException, ServiceSpecificException {
        VehiclePropConfigs propConfigs = (VehiclePropConfigs)
                LargeParcelable.reconstructStableAIDLParcelable(
                        mAidlVehicle.getAllPropConfigs(), /* keepSharedMemory= */ false);
        VehiclePropConfig[] payloads = propConfigs.payloads;
        int size = payloads.length;
        HalPropConfig[] configs = new HalPropConfig[size];
        for (int i = 0; i < size; i++) {
            configs[i] = new AidlHalPropConfig(payloads[i]);
        }
        return configs;
    }

    /**
     * Gets a new {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     *
     * @param callback A callback that could be used to receive events.
     * @return a {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     */
    @Override
    public SubscriptionClient newSubscriptionClient(VehicleHalCallback callback) {
        return new AidlSubscriptionClient(callback, mPropValueBuilder);
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
        long currentTime = System.currentTimeMillis();
        HalPropValue halPropValue = getOrSetSync(requestedPropValue,
                mPendingSyncGetValueRequestPool, new AsyncGetRequestsHandler(),
                (result) -> {
                    if (result.status != StatusCode.OK) {
                        throw new ServiceSpecificException(result.status,
                                "failed to get value for " + printPropIdAreaId(requestedPropValue));
                    }
                    if (result.prop == null) {
                        return null;
                    }
                    return mPropValueBuilder.build(result.prop);
                });
        sVehicleHalGetSyncLatencyHistogram.logSample((float)
                (System.currentTimeMillis() - currentTime));
        return halPropValue;
    }

    /**
     * Sets a property.
     *
     * @param requestedPropValue The property to set.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Override
    public void set(HalPropValue requestedPropValue) throws RemoteException,
            ServiceSpecificException {
        long currentTime = System.currentTimeMillis();
        getOrSetSync(requestedPropValue, mPendingSyncSetValueRequestPool,
                new AsyncSetRequestsHandler(),
                (result) -> {
                    if (result.status != StatusCode.OK) {
                        throw new ServiceSpecificException(result.status,
                                "failed to set value for " + printPropIdAreaId(requestedPropValue));
                    }
                    return null;
                });
        sVehicleHalSetSyncLatencyHistogram.logSample((float)
                (System.currentTimeMillis() - currentTime));
    }

    @Override
    public void getAsync(List<AsyncGetSetRequest> getVehicleStubAsyncRequests,
            VehicleStubCallbackInterface getCallback) {
        getOrSetAsync(getVehicleStubAsyncRequests, getCallback, new AsyncGetRequestsHandler(),
                new AsyncGetResultsHandler(mPropValueBuilder));
    }

    @Override
    public void setAsync(List<AsyncGetSetRequest> setVehicleStubAsyncRequests,
            VehicleStubCallbackInterface setCallback) {
        getOrSetAsync(setVehicleStubAsyncRequests, setCallback, new AsyncSetRequestsHandler(),
                new AsyncSetResultsHandler());
    }

    @Override
    public void dump(FileDescriptor fd, List<String> args) throws RemoteException {
        mAidlVehicle.asBinder().dump(fd, args.toArray(new String[args.size()]));
    }

    // Get all the VHAL request IDs according to the service request IDs and remove them from
    // pending requests map.
    @Override
    public void cancelRequests(List<Integer> serviceRequestIds) {
        mPendingAsyncRequestPool.cancelRequests(serviceRequestIds);
    }

    /**
     * A thread-safe pending sync request pool.
     */
    private static final class PendingSyncRequestPool<VhalResultType> {
        private final Object mSyncRequestPoolLock = new Object();
        @GuardedBy("mSyncRequestPoolLock")
        private final LongSparseArray<AndroidFuture<VhalResultType>>
                mPendingRequestsByVhalRequestId = new LongSparseArray();

        AndroidFuture<VhalResultType> addRequest(long vhalRequestId) {
            synchronized (mSyncRequestPoolLock) {
                AndroidFuture<VhalResultType> resultFuture = new AndroidFuture();
                mPendingRequestsByVhalRequestId.put(vhalRequestId, resultFuture);
                return resultFuture;
            }
        }

        @Nullable AndroidFuture<VhalResultType> finishRequestIfFound(long vhalRequestId) {
            synchronized (mSyncRequestPoolLock) {
                AndroidFuture<VhalResultType> pendingRequest =
                        mPendingRequestsByVhalRequestId.get(vhalRequestId);
                mPendingRequestsByVhalRequestId.remove(vhalRequestId);
                return pendingRequest;
            }
        }

        int size() {
            synchronized (mSyncRequestPoolLock) {
                return mPendingRequestsByVhalRequestId.size();
            }
        }
    }

    /**
     * A thread-safe pending async request pool.
     */
    private static final class PendingAsyncRequestPool {
        private final Object mAsyncRequestPoolLock = new Object();
        private final TimeoutCallback mTimeoutCallback = new AsyncRequestTimeoutCallback();
        private final Looper mLooper;
        @GuardedBy("mAsyncRequestPoolLock")
        private final LongPendingRequestPool<AsyncRequestInfo> mPendingRequestPool;

        PendingAsyncRequestPool(Looper looper) {
            mLooper = looper;
            mPendingRequestPool = new LongPendingRequestPool<>(mLooper, mTimeoutCallback);
        }

        private class AsyncRequestTimeoutCallback implements TimeoutCallback {
            @Override
            public void onRequestsTimeout(List<Long> vhalRequestIds) {
                ArrayMap<VehicleStubCallbackInterface, List<Integer>> serviceRequestIdsByCallback =
                        new ArrayMap<>();
                for (int i = 0; i < vhalRequestIds.size(); i++) {
                    long vhalRequestId = vhalRequestIds.get(i);
                    AsyncRequestInfo requestInfo = finishRequestIfFound(vhalRequestId);
                    if (requestInfo == null) {
                        // We already finished the request or the callback is already dead, ignore.
                        Slogf.w(TAG, "onRequestsTimeout: the request for VHAL request ID: %d is "
                                + "already finished or the callback is already dead, ignore",
                                vhalRequestId);
                        continue;
                    }
                    VehicleStubCallbackInterface getAsyncCallback = requestInfo.getClientCallback();
                    if (serviceRequestIdsByCallback.get(getAsyncCallback) == null) {
                        serviceRequestIdsByCallback.put(getAsyncCallback, new ArrayList<>());
                    }
                    serviceRequestIdsByCallback.get(getAsyncCallback).add(
                            requestInfo.getServiceRequestId());
                }

                for (int i = 0; i < serviceRequestIdsByCallback.size(); i++) {
                    serviceRequestIdsByCallback.keyAt(i).onRequestsTimeout(
                            serviceRequestIdsByCallback.valueAt(i));
                }
            }
        }


        void addRequests(List<AsyncRequestInfo> requestInfo) {
            synchronized (mAsyncRequestPoolLock) {
                mPendingRequestPool.addPendingRequests(requestInfo);
            }
        }

        @Nullable AsyncRequestInfo finishRequestIfFound(long vhalRequestId) {
            synchronized (mAsyncRequestPoolLock) {
                AsyncRequestInfo requestInfo = mPendingRequestPool.getRequestIfFound(vhalRequestId);
                mPendingRequestPool.removeRequest(vhalRequestId);
                return requestInfo;
            }
        }

        int size() {
            synchronized (mAsyncRequestPoolLock) {
                return mPendingRequestPool.size();
            }
        }

        boolean contains(long vhalRequestId) {
            synchronized (mAsyncRequestPoolLock) {
                return mPendingRequestPool.getRequestIfFound(vhalRequestId) != null;
            }
        }

        void cancelRequests(List<Integer> serviceRequestIds) {
            Set<Integer> serviceRequestIdsSet = new ArraySet<>(serviceRequestIds);
            List<Long> vhalRequestIdsToCancel = new ArrayList<>();
            synchronized (mAsyncRequestPoolLock) {
                for (int i = 0; i < mPendingRequestPool.size(); i++) {
                    int serviceRequestId = mPendingRequestPool.valueAt(i)
                            .getServiceRequestId();
                    if (serviceRequestIdsSet.contains(serviceRequestId)) {
                        vhalRequestIdsToCancel.add(mPendingRequestPool.keyAt(i));
                    }
                }
                for (int i = 0; i < vhalRequestIdsToCancel.size(); i++) {
                    long vhalRequestIdToCancel = vhalRequestIdsToCancel.get(i);
                    Slogf.w(TAG, "the request for VHAL request ID: %d is cancelled",
                            vhalRequestIdToCancel);
                    mPendingRequestPool.removeRequest(vhalRequestIdToCancel);
                }
            }
        }

        void removeRequestsForCallback(VehicleStubCallbackInterface callback) {
            synchronized (mAsyncRequestPoolLock) {
                List<Long> requestIdsToRemove = new ArrayList<>();

                for (int i = 0; i < mPendingRequestPool.size(); i++) {
                    if (mPendingRequestPool.valueAt(i).getClientCallback() == callback) {
                        requestIdsToRemove.add(mPendingRequestPool.keyAt(i));
                    }
                }

                for (int i = 0; i < requestIdsToRemove.size(); i++) {
                    mPendingRequestPool.removeRequest(requestIdsToRemove.get(i));
                }
            }
        }
    }

    /**
     * An abstract interface for handling async get/set value requests from vehicle stub.
     */
    private abstract static class AsyncRequestsHandler<VhalRequestType, VhalRequestsType> {
        protected LongSparseArray<List<Long>> mVhalRequestIdsByTimeoutInMs =
                new LongSparseArray<>();

        /**
         * Preallocsate size array for storing VHAL requests.
         */
        abstract void allocateVhalRequestSize(int size);

        /**
         * Add a vhal request to be sent later.
         */
        abstract void addVhalRequest(long vhalRequestId, HalPropValue halPropValue);

        /**
         * Get the list of stored request items.
         */
        abstract VhalRequestType[] getRequestItems();

        /**
         * Send the prepared requests to VHAL.
         */
        abstract void sendRequestsToVhal(IVehicle iVehicle, GetSetValuesCallback callbackForVhal)
                throws RemoteException, ServiceSpecificException;

        /**
         * Get the request ID for the request.
         */
        abstract long getVhalRequestId(VhalRequestType vhalRequest);
    }

    /**
     * An abstract class to handle async get/set value results from VHAL.
     */
    private abstract static class AsyncResultsHandler<VhalResultType, VehicleStubResultType> {
        protected Map<VehicleStubCallbackInterface, List<VehicleStubResultType>> mCallbackToResults;

        /**
         * Add an error result to be sent to vehicleStub through the callback later.
         */
        abstract void addErrorResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                CarPropertyErrorCodes errorCodes);
        /**
         * Add a VHAL result to be sent to vehicleStub through the callback later.
         */
        abstract void addVhalResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                VhalResultType result);
        /**
         * Send all the stored results to vehicleStub.
         */
        abstract void callVehicleStubCallback();

        /**
         * Get the request ID for the result.
         */
        abstract long getVhalRequestId(VhalResultType vhalRequest);

        protected void addVehicleStubResult(VehicleStubCallbackInterface callback,
                VehicleStubResultType vehicleStubResult) {
            if (mCallbackToResults.get(callback) == null) {
                mCallbackToResults.put(callback, new ArrayList<>());
            }
            mCallbackToResults.get(callback).add(vehicleStubResult);
        }
    }

    @Nullable
    private static IVehicle getAidlVehicle() {
        try {
            return IVehicle.Stub.asInterface(
                    ServiceManagerHelper.waitForDeclaredService(AIDL_VHAL_SERVICE));
        } catch (RuntimeException e) {
            Slogf.w(TAG, "Failed to get \"" + AIDL_VHAL_SERVICE + "\" service", e);
        }
        return null;
    }

    private class AidlSubscriptionClient extends IVehicleCallback.Stub
            implements SubscriptionClient {
        private final VehicleHalCallback mCallback;
        private final HalPropValueBuilder mBuilder;

        AidlSubscriptionClient(VehicleHalCallback callback, HalPropValueBuilder builder) {
            mCallback = callback;
            mBuilder = builder;
        }

        @Override
        public void onGetValues(GetValueResults responses) throws RemoteException {
            // We use GetSetValuesCallback for getValues and setValues operation.
            throw new UnsupportedOperationException(
                    "onGetValues should never be called on AidlSubscriptionClient");
        }

        @Override
        public void onSetValues(SetValueResults responses) throws RemoteException {
            // We use GetSetValuesCallback for getValues and setValues operation.
            throw new UnsupportedOperationException(
                    "onSetValues should never be called on AidlSubscriptionClient");
        }

        @Override
        public void onPropertyEvent(VehiclePropValues propValues, int sharedMemoryFileCount)
                throws RemoteException {
            VehiclePropValues origPropValues = (VehiclePropValues)
                    LargeParcelable.reconstructStableAIDLParcelable(propValues,
                            /* keepSharedMemory= */ false);
            ArrayList<HalPropValue> values = new ArrayList<>(origPropValues.payloads.length);
            for (VehiclePropValue value : origPropValues.payloads) {
                values.add(mBuilder.build(value));
            }
            mCallback.onPropertyEvent(values);
        }

        @Override
        public void onPropertySetError(VehiclePropErrors errors) throws RemoteException {
            VehiclePropErrors origErrors = (VehiclePropErrors)
                    LargeParcelable.reconstructStableAIDLParcelable(errors,
                            /* keepSharedMemory= */ false);
            ArrayList<VehiclePropError> errorList = new ArrayList<>(origErrors.payloads.length);
            for (VehiclePropError error : origErrors.payloads) {
                errorList.add(error);
            }
            mCallback.onPropertySetError(errorList);
        }

        @Override
        public void subscribe(SubscribeOptions[] options)
                throws RemoteException, ServiceSpecificException {
            mAidlVehicle.subscribe(this, options, /* maxSharedMemoryFileCount= */ 2);
        }

        @Override
        public void unsubscribe(int prop) throws RemoteException, ServiceSpecificException {
            mAidlVehicle.unsubscribe(this, new int[]{prop});
        }

        @Override
        public String getInterfaceHash() {
            return IVehicleCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IVehicleCallback.VERSION;
        }
    }

    private void onGetValues(GetValueResults responses) {
        Trace.traceBegin(TRACE_TAG, "AidlVehicleStub#onGetValues");
        GetValueResults origResponses = (GetValueResults)
                LargeParcelable.reconstructStableAIDLParcelable(responses,
                        /* keepSharedMemory= */ false);
        onGetSetValues(origResponses.payloads, new AsyncGetResultsHandler(mPropValueBuilder),
                mPendingSyncGetValueRequestPool);
        Trace.traceEnd(TRACE_TAG);
    }

    private void onSetValues(SetValueResults responses) {
        Trace.traceBegin(TRACE_TAG, "AidlVehicleStub#onSetValues");
        SetValueResults origResponses = (SetValueResults)
                LargeParcelable.reconstructStableAIDLParcelable(responses,
                        /* keepSharedMemory= */ false);
        onGetSetValues(origResponses.payloads, new AsyncSetResultsHandler(),
                mPendingSyncSetValueRequestPool);
        Trace.traceEnd(TRACE_TAG);
    }

    /**
     * A generic function for {@link onGetValues} / {@link onSetValues}.
     */
    private <VhalResultType> void onGetSetValues(VhalResultType[] vhalResults,
            AsyncResultsHandler asyncResultsHandler,
            PendingSyncRequestPool<VhalResultType> pendingSyncRequestPool) {
        synchronized (mLock) {
            for (VhalResultType result : vhalResults) {
                long vhalRequestId = asyncResultsHandler.getVhalRequestId(result);
                if (!mPendingAsyncRequestPool.contains(vhalRequestId)) {
                    // If we cannot find the request Id in the async map, we assume it is for a
                    // sync request.
                    completePendingSyncRequestLocked(pendingSyncRequestPool, vhalRequestId, result);
                    continue;
                }

                AsyncRequestInfo requestInfo = mPendingAsyncRequestPool.finishRequestIfFound(
                        vhalRequestId);
                if (requestInfo == null) {
                    Slogf.w(TAG,
                            "No pending request for ID: %s, possibly already timed out, "
                            + "or cancelled, or the client already died", vhalRequestId);
                    continue;
                }
                asyncResultsHandler.addVhalResult(requestInfo.getClientCallback(),
                        requestInfo.getServiceRequestId(), result);
            }
        }
        Trace.traceBegin(TRACE_TAG, "AidlVehicleStub call async result callback");
        asyncResultsHandler.callVehicleStubCallback();
        Trace.traceEnd(TRACE_TAG);
    }

    private static String printPropIdAreaId(HalPropValue value) {
        return "propID: " + value.getPropId() + ", areaID: " + value.getAreaId();
    }

    private final class GetSetValuesCallback extends IVehicleCallback.Stub {

        @Override
        public void onGetValues(GetValueResults responses) throws RemoteException {
            AidlVehicleStub.this.onGetValues(responses);
        }

        @Override
        public void onSetValues(SetValueResults responses) throws RemoteException {
            AidlVehicleStub.this.onSetValues(responses);
        }

        @Override
        public void onPropertyEvent(VehiclePropValues propValues, int sharedMemoryFileCount)
                throws RemoteException {
            throw new UnsupportedOperationException(
                    "GetSetValuesCallback only support onGetValues or onSetValues");
        }

        @Override
        public void onPropertySetError(VehiclePropErrors errors) throws RemoteException {
            throw new UnsupportedOperationException(
                    "GetSetValuesCallback only support onGetValues or onSetValues");
        }

        @Override
        public String getInterfaceHash() {
            return IVehicleCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IVehicleCallback.VERSION;
        }
    }

    /**
     * Mark a pending sync get/set property request as complete and deliver the result.
     */
    @GuardedBy("mLock")
    private <VhalResultType> void completePendingSyncRequestLocked(
            PendingSyncRequestPool<VhalResultType> pendingSyncRequestPool, long vhalRequestId,
            VhalResultType result) {
        Trace.traceBegin(TRACE_TAG, "AidlVehicleStub#completePendingSyncRequestLocked");
        AndroidFuture<VhalResultType> pendingRequest =
                pendingSyncRequestPool.finishRequestIfFound(vhalRequestId);
        if (pendingRequest == null) {
            Slogf.w(TAG, "No pending request for ID: " + vhalRequestId
                    + ", possibly already timed out");
            return;
        }

        Trace.traceBegin(TRACE_TAG, "AidlVehicleStub#complete pending request");
        // This might fail if the request already timed out.
        pendingRequest.complete(result);
        Trace.traceEnd(TRACE_TAG);
        Trace.traceEnd(TRACE_TAG);
    }

    private static final class AsyncGetRequestsHandler
            extends AsyncRequestsHandler<GetValueRequest, GetValueRequests> {
        private GetValueRequest[] mVhalRequestItems;
        private int mIndex;

        @Override
        public void allocateVhalRequestSize(int size) {
            mVhalRequestItems = new GetValueRequest[size];
        }

        @Override
        public void addVhalRequest(long vhalRequestId, HalPropValue halPropValue) {
            mVhalRequestItems[mIndex] = new GetValueRequest();
            mVhalRequestItems[mIndex].requestId = vhalRequestId;
            mVhalRequestItems[mIndex].prop = (VehiclePropValue) halPropValue.toVehiclePropValue();
            mIndex++;
        }

        @Override
        public GetValueRequest[] getRequestItems() {
            return mVhalRequestItems;
        }

        @Override
        public void sendRequestsToVhal(IVehicle iVehicle, GetSetValuesCallback callbackForVhal)
                throws RemoteException, ServiceSpecificException {
            Trace.traceBegin(TRACE_TAG, "Prepare LargeParcelable");
            GetValueRequests largeParcelableRequest = new GetValueRequests();
            largeParcelableRequest.payloads = mVhalRequestItems;

            // TODO(b/269669729): Don't try to use large parcelable if the request size is too
            // small.
            largeParcelableRequest = (GetValueRequests) LargeParcelable.toLargeParcelable(
                    largeParcelableRequest, () -> {
                        GetValueRequests newRequests = new GetValueRequests();
                        newRequests.payloads = new GetValueRequest[0];
                        return newRequests;
            });
            Trace.traceEnd(TRACE_TAG);
            Trace.traceBegin(TRACE_TAG, "IVehicle#getValues");
            iVehicle.getValues(callbackForVhal, largeParcelableRequest);
            Trace.traceEnd(TRACE_TAG);
        }

        @Override
        public long getVhalRequestId(GetValueRequest request) {
            return request.requestId;
        }
    }

    private static final class AsyncSetRequestsHandler
            extends AsyncRequestsHandler<SetValueRequest, SetValueRequests> {
        private SetValueRequest[] mVhalRequestItems;
        private int mIndex;

        @Override
        public void allocateVhalRequestSize(int size) {
            mVhalRequestItems = new SetValueRequest[size];
        }

        @Override
        public void addVhalRequest(long vhalRequestId, HalPropValue halPropValue) {
            mVhalRequestItems[mIndex] = new SetValueRequest();
            mVhalRequestItems[mIndex].requestId = vhalRequestId;
            mVhalRequestItems[mIndex].value = (VehiclePropValue) halPropValue.toVehiclePropValue();
            mIndex++;
        }

        @Override
        public SetValueRequest[] getRequestItems() {
            return mVhalRequestItems;
        }

        @Override
        public void sendRequestsToVhal(IVehicle iVehicle, GetSetValuesCallback callbackForVhal)
                throws RemoteException, ServiceSpecificException {
            SetValueRequests largeParcelableRequest = new SetValueRequests();
            largeParcelableRequest.payloads = mVhalRequestItems;
            largeParcelableRequest = (SetValueRequests) LargeParcelable.toLargeParcelable(
                    largeParcelableRequest, () -> {
                        SetValueRequests newRequests = new SetValueRequests();
                        newRequests.payloads = new SetValueRequest[0];
                        return newRequests;
            });
            iVehicle.setValues(callbackForVhal, largeParcelableRequest);
        }

        @Override
        public long getVhalRequestId(SetValueRequest request) {
            return request.requestId;
        }
    }

    private static final class AsyncGetResultsHandler extends
            AsyncResultsHandler<GetValueResult, GetVehicleStubAsyncResult> {
        private HalPropValueBuilder mPropValueBuilder;

        AsyncGetResultsHandler(HalPropValueBuilder propValueBuilder) {
            mPropValueBuilder = propValueBuilder;
            mCallbackToResults = new ArrayMap<VehicleStubCallbackInterface,
                    List<GetVehicleStubAsyncResult>>();
        }

        @Override
        void addErrorResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                CarPropertyErrorCodes errorCodes) {
            addVehicleStubResult(callback, new GetVehicleStubAsyncResult(serviceRequestId,
                    errorCodes));
        }

        @Override
        void addVhalResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                GetValueResult result) {
            addVehicleStubResult(callback, toVehicleStubResult(serviceRequestId, result));
        }

        @Override
        void callVehicleStubCallback() {
            for (Map.Entry<VehicleStubCallbackInterface, List<GetVehicleStubAsyncResult>> entry :
                    mCallbackToResults.entrySet()) {
                entry.getKey().onGetAsyncResults(entry.getValue());
            }
        }

        @Override
        long getVhalRequestId(GetValueResult result) {
            return result.requestId;
        }

        private GetVehicleStubAsyncResult toVehicleStubResult(int serviceRequestId,
                GetValueResult vhalResult) {
            if (vhalResult.status != StatusCode.OK) {
                CarPropertyErrorCodes carPropertyErrorCodes =
                        convertVhalStatusCodeToCarPropertyManagerErrorCodes(vhalResult.status);
                return new GetVehicleStubAsyncResult(serviceRequestId, carPropertyErrorCodes);
            } else if (vhalResult.prop == null) {
                // If status is OKAY but no property is returned, treat it as not_available.
                return new GetVehicleStubAsyncResult(serviceRequestId,
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0));
            }
            return new GetVehicleStubAsyncResult(serviceRequestId,
                    mPropValueBuilder.build(vhalResult.prop));
        }
    }

    private static final class AsyncSetResultsHandler extends
            AsyncResultsHandler<SetValueResult, SetVehicleStubAsyncResult> {
        AsyncSetResultsHandler() {
            mCallbackToResults = new ArrayMap<VehicleStubCallbackInterface,
                    List<SetVehicleStubAsyncResult>>();
        }

        @Override
        void addErrorResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                CarPropertyErrorCodes errorCodes) {
            addVehicleStubResult(callback,
                    new SetVehicleStubAsyncResult(serviceRequestId, errorCodes));
        }

        @Override
        void addVhalResult(VehicleStubCallbackInterface callback, int serviceRequestId,
                SetValueResult result) {
            addVehicleStubResult(callback, toVehicleStubResult(serviceRequestId, result));

        }

        @Override
        void callVehicleStubCallback() {
            for (Map.Entry<VehicleStubCallbackInterface, List<SetVehicleStubAsyncResult>> entry :
                    mCallbackToResults.entrySet()) {
                entry.getKey().onSetAsyncResults(entry.getValue());
            }
        }

        @Override
        long getVhalRequestId(SetValueResult result) {
            return result.requestId;
        }

        private SetVehicleStubAsyncResult toVehicleStubResult(int serviceRequestId,
                SetValueResult vhalResult) {
            if (vhalResult.status != StatusCode.OK) {
                CarPropertyErrorCodes carPropertyErrorCodes =
                        convertVhalStatusCodeToCarPropertyManagerErrorCodes(vhalResult.status);
                return new SetVehicleStubAsyncResult(serviceRequestId, carPropertyErrorCodes);
            }
            return new SetVehicleStubAsyncResult(serviceRequestId);
        }
    }

    /**
     * Generic function for {@link get} or {@link set}.
     */
    private <VhalResultType> HalPropValue getOrSetSync(
            HalPropValue requestedPropValue,
            PendingSyncRequestPool<VhalResultType> pendingSyncRequestPool,
            AsyncRequestsHandler requestsHandler,
            Function<VhalResultType, HalPropValue> resultHandler)
            throws RemoteException, ServiceSpecificException {
        Trace.traceBegin(TRACE_TAG, "AidlVehicleStub#getOrSetSync");
        long vhalRequestId = mRequestId.getAndIncrement();

        AndroidFuture<VhalResultType> resultFuture = pendingSyncRequestPool.addRequest(
                vhalRequestId);

        requestsHandler.allocateVhalRequestSize(1);
        requestsHandler.addVhalRequest(vhalRequestId, requestedPropValue);
        requestsHandler.sendRequestsToVhal(mAidlVehicle, mGetSetValuesCallback);

        boolean gotResult = false;

        try {
            Trace.traceBegin(TRACE_TAG, "AidlVehicleStub#waitingForSyncResult");
            VhalResultType result = resultFuture.get(mSyncOpTimeoutInMs,
                    TimeUnit.MILLISECONDS);
            gotResult = true;
            return resultHandler.apply(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "thread interrupted, possibly exiting the thread");
        } catch (ExecutionException e) {
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "failed to resolve future, error: " + e);
        } catch (TimeoutException e) {
            throw new ServiceSpecificException(StatusCode.INTERNAL_ERROR,
                    "get/set value request timeout for: " + printPropIdAreaId(requestedPropValue));
        } finally {
            Trace.traceEnd(TRACE_TAG);
            if (!gotResult) {
                resultFuture = pendingSyncRequestPool.finishRequestIfFound(vhalRequestId);
                // Something wrong happened, the future is guaranteed not to be used again.
                resultFuture.cancel(/* mayInterruptIfRunning= */ false);
            }
            Trace.traceEnd(TRACE_TAG);
        }
    }

    /**
     * Generic function for {@link getAsync} or {@link setAsync}.
     */
    private <VhalRequestType, VhalRequestsType> void getOrSetAsync(
            List<AsyncGetSetRequest> vehicleStubAsyncRequests,
            VehicleStubCallbackInterface vehicleStubCallback,
            AsyncRequestsHandler<VhalRequestType, VhalRequestsType> asyncRequestsHandler,
            AsyncResultsHandler asyncResultsHandler) {
        prepareAndConvertAsyncRequests(vehicleStubAsyncRequests, vehicleStubCallback,
                asyncRequestsHandler);

        try {
            asyncRequestsHandler.sendRequestsToVhal(mAidlVehicle, mGetSetValuesCallback);
        } catch (RemoteException e) {
            handleAsyncExceptionFromVhal(
                    asyncRequestsHandler,
                    vehicleStubCallback,
                    new CarPropertyErrorCodes(
                            CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                            /* vendorErrorCode= */ 0,
                            /* systemErrorCode= */ 0),
                    asyncResultsHandler);
            return;
        } catch (ServiceSpecificException e) {
            CarPropertyErrorCodes carPropertyErrorCodes =
                    convertVhalStatusCodeToCarPropertyManagerErrorCodes(e.errorCode);
            handleAsyncExceptionFromVhal(asyncRequestsHandler, vehicleStubCallback,
                    carPropertyErrorCodes, asyncResultsHandler);
            return;
        }
    }

    /**
     * Prepare an async get/set request from client and convert it to vhal requests.
     *
     * <p> It does the following things:
     * <ul>
     * <li> Add a client callback death listener which will clear the pending requests when client
     * died
     * <li> Store the async requests to a pending request map.
     * <li> For each client request, generate a unique VHAL request ID and convert the request to
     * VHAL request type.
     * <li> Stores the time-out information for each request into a map so that we can register
     * timeout handlers later.
     * <li> Convert the vhal request items to a single large parcelable class.
     */
    private <VhalRequestType, VhalRequestsType> void prepareAndConvertAsyncRequests(
                    List<AsyncGetSetRequest> vehicleStubRequests,
                    VehicleStubCallbackInterface clientCallback,
                    AsyncRequestsHandler<VhalRequestType, VhalRequestsType> asyncRequestsHandler) {
        asyncRequestsHandler.allocateVhalRequestSize(vehicleStubRequests.size());
        synchronized (mLock) {
            // Add the death recipient so that all client info for a dead callback will be cleaned
            // up. Note that this must be in the same critical section as the following code to
            // store the client info into the map. This makes sure that even if the client is
            // died half way while adding the client info, it will wait until all the clients are
            // added and then remove them all.
            try {
                clientCallback.linkToDeath(() -> {
                    // This function will be invoked from a different thread. It needs to be
                    // guarded by a lock so that the whole 'prepareAndConvertAsyncRequests' finishes
                    // before we remove the callback.
                    synchronized (mLock) {
                        mPendingAsyncRequestPool.removeRequestsForCallback(clientCallback);
                    }
                });
            } catch (RemoteException e) {
                // The binder is already died.
                throw new IllegalStateException("Failed to link callback to death recipient, the "
                        + "client maybe already died");
            }

            List<AsyncRequestInfo> requestInfoList = new ArrayList<>();
            for (int i = 0; i < vehicleStubRequests.size(); i++) {
                AsyncGetSetRequest vehicleStubRequest = vehicleStubRequests.get(i);
                long vhalRequestId = mRequestId.getAndIncrement();
                asyncRequestsHandler.addVhalRequest(vhalRequestId,
                        vehicleStubRequest.getHalPropValue());
                requestInfoList.add(new AsyncRequestInfo(
                        vhalRequestId, vehicleStubRequest.getServiceRequestId(), clientCallback,
                        vehicleStubRequest.getTimeoutUptimeMs()));
            }
            mPendingAsyncRequestPool.addRequests(requestInfoList);
        }

    }

    /**
     * Callback to deliver async get/set error results back to the client.
     *
     * <p>When an exception is received, the callback delivers the error results on the same thread
     * where the caller is.
     */
    private <VhalRequestType, VhalRequestsType> void handleAsyncExceptionFromVhal(
            AsyncRequestsHandler<VhalRequestType, VhalRequestsType> asyncRequestsHandler,
            VehicleStubCallbackInterface vehicleStubCallback, CarPropertyErrorCodes errorCodes,
            AsyncResultsHandler asyncResultsHandler) {
        Slogf.w(TAG,
                "Received RemoteException or ServiceSpecificException from VHAL. VHAL is likely "
                        + "dead, system error code: %d, vendor error code: %d",
                errorCodes.getCarPropertyManagerErrorCode(), errorCodes.getVendorErrorCode());
        synchronized (mLock) {
            VhalRequestType[] requests = asyncRequestsHandler.getRequestItems();
            for (int i = 0; i < requests.length; i++) {
                long vhalRequestId = asyncRequestsHandler.getVhalRequestId(requests[i]);
                AsyncRequestInfo requestInfo = mPendingAsyncRequestPool.finishRequestIfFound(
                        vhalRequestId);
                if (requestInfo == null) {
                    Slogf.w(TAG,
                            "No pending request for ID: %s, possibly already timed out or "
                            + "the client already died", vhalRequestId);
                    continue;
                }
                asyncResultsHandler.addErrorResult(
                        vehicleStubCallback, requestInfo.getServiceRequestId(), errorCodes);
            }
        }
        asyncResultsHandler.callVehicleStubCallback();
    }

}
