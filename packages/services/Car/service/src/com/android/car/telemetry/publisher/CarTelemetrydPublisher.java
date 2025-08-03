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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;
import android.automotive.telemetry.internal.CarDataInternal;
import android.automotive.telemetry.internal.ICarDataListener;
import android.automotive.telemetry.internal.ICarTelemetryInternal;
import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.telemetry.TelemetryProto;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.automotive.telemetry.CarDataProto;
import com.android.car.CarLog;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Publisher for cartelemtryd service (aka ICarTelemetry).
 *
 * <p>When a subscriber is added, the publisher binds to ICarTelemetryInternal and starts listening
 * for incoming CarData. The matching CarData will be pushed to the subscriber. It unbinds itself
 * from ICarTelemetryInternal if there are no subscribers.
 *
 * <p>See {@code packages/services/Car/cpp/telemetry/cartelemetryd} to learn more about the service.
 */
public class CarTelemetrydPublisher extends AbstractPublisher {
    private static final boolean DEBUG = false;  // STOPSHIP if true
    private static final String SERVICE_NAME = ICarTelemetryInternal.DESCRIPTOR + "/default";
    private static final int BINDER_FLAGS = 0;

    private final SparseArray<ArrayList<DataSubscriber>> mCarIdSubscriberLookUp =
            new SparseArray<>();
    // All the methods in this class are expected to be called on this handler's thread.
    private final Handler mTelemetryHandler;
    private final SessionController mSessionController;
    private final ICarDataListener mListener = new ICarDataListener.Stub() {
        @Override
        public void onCarDataReceived(
                @NonNull final CarDataInternal[] dataList) throws RemoteException {
            if (DEBUG) {
                Slogf.d(CarLog.TAG_TELEMETRY,
                        "Received " + dataList.length + " CarData from cartelemetryd");
            }
            // TODO(b/189142577): Create custom Handler and post message to improve performance
            mTelemetryHandler.post(() -> onCarDataListReceived(dataList));
        }

        @Override
        public String getInterfaceHash() {
            return ICarDataListener.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarDataListener.VERSION;
        }
    };
    private final IBinder.DeathRecipient mDeathRecipient = this::onBinderDied;

    private ICarTelemetryInternal mCarTelemetryInternal;

    CarTelemetrydPublisher(
            @NonNull PublisherListener listener, @NonNull Handler telemetryHandler,
            @NonNull SessionController sessionController) {
        super(listener);
        this.mTelemetryHandler = telemetryHandler;
        this.mSessionController = sessionController;
    }

    /** Called when binder for ICarTelemetry service is died. */
    private void onBinderDied() {
        // TODO(b/189142577): Create custom Handler and post message to improve performance
        mTelemetryHandler.post(() -> {
            if (mCarTelemetryInternal != null) {
                mCarTelemetryInternal.asBinder().unlinkToDeath(mDeathRecipient, BINDER_FLAGS);
                mCarTelemetryInternal = null;
            }
            // TODO(b/241441036): Revisit actions taken when the binder dies.
            onPublisherFailure(
                    getMetricsConfigs(),
                    new IllegalStateException("ICarTelemetryInternal binder died"));
        });
    }

    /**
     * Connects to ICarTelemetryInternal service and starts listening for CarData.
     *
     * @return true for success or if cartelemetryd is already connected, false otherwise.
     */
    private boolean connectToCarTelemetryd() {
        if (mCarTelemetryInternal != null) {
            return true;
        }
        IBinder binder = ServiceManagerHelper.checkService(SERVICE_NAME);
        if (binder == null) {
            onPublisherFailure(
                    getMetricsConfigs(),
                    new IllegalStateException(
                            "Failed to connect to the ICarTelemetryInternal: service is not "
                                    + "ready"));
            return false;
        }
        try {
            binder.linkToDeath(mDeathRecipient, BINDER_FLAGS);
        } catch (RemoteException e) {
            onPublisherFailure(
                    getMetricsConfigs(),
                    new IllegalStateException(
                            "Failed to connect to the ICarTelemetryInternal: linkToDeath failed",
                            e));
            return false;
        }
        mCarTelemetryInternal = ICarTelemetryInternal.Stub.asInterface(binder);
        try {
            mCarTelemetryInternal.setListener(mListener);
        } catch (RemoteException e) {
            binder.unlinkToDeath(mDeathRecipient, BINDER_FLAGS);
            mCarTelemetryInternal = null;
            onPublisherFailure(
                    getMetricsConfigs(),
                    new IllegalStateException(
                            "Failed to connect to the ICarTelemetryInternal: Cannot set CarData "
                                    + "listener", e));
            return false;
        }
        return true;
    }

    @NonNull
    private ArrayList<TelemetryProto.MetricsConfig> getMetricsConfigs() {
        ArraySet<TelemetryProto.MetricsConfig> uniqueConfigs =
                new ArraySet<TelemetryProto.MetricsConfig>();
        for (int i = 0; i < mCarIdSubscriberLookUp.size(); i++) {
            ArrayList<DataSubscriber> subscribers = mCarIdSubscriberLookUp.valueAt(i);
            for (int j = 0; j < subscribers.size(); j++) {
                uniqueConfigs.add(subscribers.get(j).getMetricsConfig());
            }
        }
        ArrayList<TelemetryProto.MetricsConfig> allConfigs =
                new ArrayList<TelemetryProto.MetricsConfig>();
        Iterator<TelemetryProto.MetricsConfig> iterator = uniqueConfigs.iterator();
        while (iterator.hasNext()) {
            allConfigs.add(iterator.next());
        }
        return allConfigs;
    }

    /**
     * Disconnects from ICarTelemetryInternal service.
     *
     * @throws IllegalStateException if fails to clear the listener.
     */
    private void disconnectFromCarTelemetryd() {
        if (mCarTelemetryInternal == null) {
            return;  // already disconnected
        }
        try {
            mCarTelemetryInternal.clearListener();
        } catch (RemoteException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to remove ICarTelemetryInternal listener", e);
        }
        mCarTelemetryInternal.asBinder().unlinkToDeath(mDeathRecipient, BINDER_FLAGS);
        mCarTelemetryInternal = null;
    }

    @VisibleForTesting
    boolean isConnectedToCarTelemetryd() {
        return mCarTelemetryInternal != null;
    }

    @Override
    public void addDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        Preconditions.checkArgument(
                publisherParam.getPublisherCase()
                        == TelemetryProto.Publisher.PublisherCase.CARTELEMETRYD,
                "Subscribers only with CarTelemetryd publisher are supported by this class.");
        int carDataId = publisherParam.getCartelemetryd().getId();
        CarDataProto.CarData.PushedCase carDataCase =
                CarDataProto.CarData.PushedCase.forNumber(carDataId);
        // TODO(b/241249252): Revise the check to accommodate data in OEM ID range, 10K-20K.
        Preconditions.checkArgument(
                carDataCase != null
                        && carDataCase != CarDataProto.CarData.PushedCase.PUSHED_NOT_SET,
                "Invalid CarData ID " + carDataId
                        + ". Please see CarData.proto for the list of available IDs.");

        ArrayList<DataSubscriber> currentSubscribers = mCarIdSubscriberLookUp.get(carDataId);
        if (currentSubscribers == null) {
            currentSubscribers = new ArrayList<>();
            mCarIdSubscriberLookUp.put(carDataId, currentSubscribers);
        }
        currentSubscribers.add(subscriber);

        if (!connectToCarTelemetryd()) {
            // logging is done in connectToCarTelemetryd, do not double log here
            return;
        }
        Slogf.d(CarLog.TAG_TELEMETRY, "Subscribing to CarData.id=%d", carDataId);
        // No need to make a binder call if the given CarDataId is already subscribed to.
        if (currentSubscribers.size() > 1) {
            return;
        }

        try {
            mCarTelemetryInternal.addCarDataIds(new int[]{carDataId});
        } catch (RemoteException e) {
            onPublisherFailure(
                    getMetricsConfigs(),
                    new IllegalStateException(
                            "Failed to make addCarDataIds binder call to ICarTelemetryInternal "
                                    + "for CarDataID = "
                                    + carDataId, e));
            return;
        }
    }

    @Override
    public void removeDataSubscriber(@NonNull DataSubscriber subscriber) {
        int idToRemove = subscriber.getPublisherParam().getCartelemetryd().getId();
        // TODO(b/241251062): Revise to consider throwing IllegalArgumentException and checking
        //  for subscriber type like some other publisher implementations do.
        ArrayList<DataSubscriber> currentSubscribers = mCarIdSubscriberLookUp.get(idToRemove);
        if (currentSubscribers == null) {
            // No subscribers were found for a given id.
            Slogf.e(CarLog.TAG_TELEMETRY,
                    "Subscriber for CarData.id=%d is not present among subscriptions. This is not"
                            + " expected.",
                    idToRemove);
            return;
        }
        currentSubscribers.remove(subscriber);
        if (currentSubscribers.isEmpty()) {
            mCarIdSubscriberLookUp.remove(idToRemove);
            try {
                mCarTelemetryInternal.removeCarDataIds(new int[]{idToRemove});
            } catch (RemoteException e) {
                Slogf.e(CarLog.TAG_TELEMETRY,
                        "removeCarDataIds binder call failed for CarData.id=%d", idToRemove);
            }
        }
        if (mCarIdSubscriberLookUp.size() == 0) {
            disconnectFromCarTelemetryd();
        }
    }

    @Override
    public void removeAllDataSubscribers() {
        int[] idsToRemove = new int[mCarIdSubscriberLookUp.size()];
        for (int index = 0; index < mCarIdSubscriberLookUp.size(); index++) {
            idsToRemove[index] = mCarIdSubscriberLookUp.keyAt(index);
        }
        try {
            mCarTelemetryInternal.removeCarDataIds(idsToRemove);
        } catch (RemoteException e) {
            Slogf.e(CarLog.TAG_TELEMETRY,
                    "removeCarDataIds binder call failed while unsubscribing from all data.");
        }
        mCarIdSubscriberLookUp.clear();

        disconnectFromCarTelemetryd();
    }

    @Override
    public boolean hasDataSubscriber(@NonNull DataSubscriber subscriber) {
        int id = subscriber.getPublisherParam().getCartelemetryd().getId();
        return mCarIdSubscriberLookUp.contains(id) && mCarIdSubscriberLookUp.get(id).contains(
                subscriber);
    }

    /**
     * Called when publisher receives new car data list. It's executed on the telemetry thread.
     */
    private void onCarDataListReceived(@NonNull CarDataInternal[] dataList) {
        for (CarDataInternal data : dataList) {
            processCarData(data);
        }
    }

    private void processCarData(@NonNull CarDataInternal dataItem) {
        ArrayList<DataSubscriber> currentSubscribers = mCarIdSubscriberLookUp.get(dataItem.id);
        if (currentSubscribers == null) {
            // It is possible the carId is no longer subscribed to while data is in-flight.
            return;
        }
        String content = new String(dataItem.content, StandardCharsets.UTF_8);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID, dataItem.id);
        bundle.putString(Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT, content);
        SessionAnnotation sessionAnnotation = mSessionController.getSessionAnnotation();
        sessionAnnotation.addAnnotationsToBundle(bundle);
        for (int i = 0; i < currentSubscribers.size(); i++) {
            currentSubscribers.get(i).push(bundle,
                    content.length() > DataSubscriber.SCRIPT_INPUT_SIZE_THRESHOLD_BYTES);
        }
    }

    @Override
    protected void handleSessionStateChange(SessionAnnotation annotation) {
        // We don't handle session state changes. We make synchronous calls to SessionController
        // as soon as new data arrives to retrieve the current session annotations.
        // Make sure to invoke sessionController.registerCallback(this::handleSessionStateChange)
        // in the constructor once this method is implemented.
    }


}
