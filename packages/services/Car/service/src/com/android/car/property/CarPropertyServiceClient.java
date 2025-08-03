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

package com.android.car.property;

import android.car.builtin.util.Slogf;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.CarPropertyService;
import com.android.car.internal.property.CarPropertyEventController;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/** Client class to keep track of listeners to {@link CarPropertyService}. */
public final class CarPropertyServiceClient extends CarPropertyEventController
        implements IBinder.DeathRecipient {
    private static final String TAG = CarLog.tagFor(CarPropertyServiceClient.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final ICarPropertyEventListener mListener;
    private final IBinder mListenerBinder;
    private final UnregisterCallback mUnregisterCallback;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private boolean mIsDead;

    public CarPropertyServiceClient(ICarPropertyEventListener listener,
            UnregisterCallback unregisterCallback) {
        super(/* useSystemLogger= */ true);
        mListener = listener;
        mListenerBinder = listener.asBinder();
        mUnregisterCallback = unregisterCallback;

        try {
            mListenerBinder.linkToDeath(this, /* flags= */ 0);
        } catch (RemoteException e) {
            Slogf.w(TAG, "IBinder=%s is already dead", mListenerBinder);
            mIsDead = true;
        }
    }

    /**
     * Returns whether this client is already dead.
     *
     * Caller should not assume this client is alive after getting True response because the
     * binder might die after this function checks the status. Caller should only use this
     * function to fail early.
     */
    public boolean isDead() {
        synchronized (mLock) {
            return mIsDead;
        }
    }

    /**
     * Store a continuous property ID and area IDs with the update rate in hz that this
     * client is subscribed at.
     */
    @Override
    public void addContinuousProperty(int propertyId, int[] areaIds, float updateRateHz,
            boolean enableVur, float resolution) {
        synchronized (mLock) {
            if (mIsDead) {
                Slogf.w(TAG, "addContinuousProperty: The client is already dead, ignore");
                return;
            }

            super.addContinuousProperty(propertyId, areaIds, updateRateHz, enableVur, resolution);
        }
    }

    /**
     * Stores a newly subscribed on-change property and area IDs.
     */
    @Override
    public void addOnChangeProperty(int propertyId, int[] areaIds) {
        synchronized (mLock) {
            if (mIsDead) {
                Slogf.w(TAG, "addOnChangeProperty: The client is already dead, ignore");
                return;
            }

            super.addOnChangeProperty(propertyId, areaIds);
        }
    }

    /**
     * Remove property IDs that this client is subscribed to.
     *
     * Once all property IDs are removed, this client instance must be removed because
     * {@link #mListenerBinder} will no longer be receiving death notifications.
     *
     * @return {@code true} if there are no properties registered to this client
     */
    @Override
    public boolean remove(ArraySet<Integer> propertyIds) {
        synchronized (mLock) {
            boolean empty = super.remove(propertyIds);
            if (empty) {
                mListenerBinder.unlinkToDeath(this, /* flags= */ 0);
            }
            return empty;
        }
    }

    /**
     * Handler to be called when client died.
     *
     * Remove the listener from HAL service and unregister if this is the last client.
     */
    @Override
    public void binderDied() {
        List<Integer> propertyIds = new ArrayList<>();
        synchronized (mLock) {
            mIsDead = true;

            if (DBG) {
                Slogf.d(TAG, "binderDied %s", mListenerBinder);
            }

            // Because we set mIsDead to true here, we are sure subscribeProperties will not
            // have new elements. The property IDs here cover all the properties that we need to
            // unregister.
            for (int propertyId : getSubscribedProperties()) {
                propertyIds.add(propertyId);
            }
        }
        try {
            mUnregisterCallback.onUnregister(propertyIds, mListenerBinder);
        } catch (Exception e) {
            Slogf.e(TAG, "registerCallback.onUnregister failed", e);
        }
    }

    /**
     * Calls onEvent function on the listener if the binder is alive.
     *
     * The property events will be filtered based on timestamp and whether VUR is on.
     *
     * There is still chance when onEvent might fail because binderDied is not called before
     * this function.
     */
    public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
        List<CarPropertyEvent> filteredEvents = new ArrayList<>();
        synchronized (mLock) {
            if (mIsDead) {
                return;
            }
            for (int i = 0; i < events.size(); i++) {
                CarPropertyEvent event = events.get(i);
                CarPropertyValue<?> carPropertyValue = getCarPropertyValueIfCallbackRequired(event);
                if (carPropertyValue == null) {
                    continue;
                }
                if (!carPropertyValue.equals(event.getCarPropertyValue())) {
                    CarPropertyEvent updatedEvent =
                            new CarPropertyEvent(event.getEventType(), carPropertyValue,
                                    event.getErrorCode());
                    filteredEvents.add(updatedEvent);
                } else {
                    filteredEvents.add(event);
                }
            }
        }
        onFilteredEvents(filteredEvents);
    }

    /**
     * Calls onEvent function on the listener if the binder is alive with no filtering.
     *
     * There is still chance when onEvent might fail because binderDied is not called before
     * this function.
     */
    public void onFilteredEvents(List<CarPropertyEvent> events) throws RemoteException {
        if (!events.isEmpty()) {
            mListener.onEvent(events);
        }
    }

    /**
     * Interface that receives updates when unregistering properties with {@link
     * CarPropertyService}.
     */
    public interface UnregisterCallback {

        /**
         * Callback from {@link CarPropertyService} when unregistering properties.
         *
         * @param propertyIds the properties to be unregistered
         * @param listenerBinder the client to be removed
         */
        void onUnregister(List<Integer> propertyIds, IBinder listenerBinder);
    }
}
