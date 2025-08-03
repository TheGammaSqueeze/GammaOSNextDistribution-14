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

package com.android.server.nearby.managers;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.NonNull;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.nearby.managers.registration.BinderListenerRegistration;
import com.android.server.nearby.managers.registration.BinderListenerRegistration.ListenerOperation;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/**
 * A simplified class based on {@link com.android.server.location.listeners.ListenerMultiplexer}.
 * It is a base class to multiplex broadcast and discovery events to multiple listener
 * registrations. Every listener is represented by a registration object which stores all required
 * state for a listener.
 * Registrations will be merged to one request for the service to operate.
 *
 * @param <TListener>           callback type for clients
 * @param <TRegistration>       child of {@link BinderListenerRegistration}
 * @param <TMergedRegistration> merged registration type
 */
public abstract class ListenerMultiplexer<TListener,
        TRegistration extends BinderListenerRegistration<TListener>, TMergedRegistration> {

    /**
     * The lock object used by the multiplexer. Acquiring this lock allows for multiple operations
     * on the multiplexer to be completed atomically. Otherwise, it is not required to hold this
     * lock. This lock is held while invoking all lifecycle callbacks on both the multiplexer and
     * any registrations.
     */
    public final Object mMultiplexerLock = new Object();

    @GuardedBy("mMultiplexerLock")
    final ArrayMap<IBinder, TRegistration> mRegistrations = new ArrayMap<>();

    // this is really @NonNull in many ways, but we explicitly null this out to allow for GC when
    // not
    // in use, so we can't annotate with @NonNull
    @GuardedBy("mMultiplexerLock")
    public TMergedRegistration mMerged;

    /**
     * Invoked when the multiplexer goes from having no registrations to having some registrations.
     * This is a convenient entry point for registering listeners, etc, which only need to be
     * present
     * while there are any registrations. Invoked while holding the multiplexer's internal lock.
     */
    @GuardedBy("mMultiplexerLock")
    public void onRegister() {
        Log.v(TAG, "ListenerMultiplexer registered.");
    }

    /**
     * Invoked when the multiplexer goes from having some registrations to having no registrations.
     * This is a convenient entry point for unregistering listeners, etc, which only need to be
     * present while there are any registrations. Invoked while holding the multiplexer's internal
     * lock.
     */
    @GuardedBy("mMultiplexerLock")
    public void onUnregister() {
        Log.v(TAG, "ListenerMultiplexer unregistered.");
    }

    /**
     * Puts a new registration with the given key, replacing any previous registration under the
     * same key. This method cannot be called to put a registration re-entrantly.
     */
    public final void putRegistration(@NonNull IBinder key, @NonNull TRegistration registration) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(registration);
        synchronized (mMultiplexerLock) {
            boolean wasEmpty = mRegistrations.isEmpty();

            int index = mRegistrations.indexOfKey(key);
            if (index > 0) {
                BinderListenerRegistration<TListener> oldRegistration = mRegistrations.valueAt(
                        index);
                oldRegistration.onUnregister();
                mRegistrations.setValueAt(index, registration);
            } else {
                mRegistrations.put(key, registration);
            }

            registration.onRegister();
            onRegistrationsUpdated();
            if (wasEmpty) {
                onRegister();
            }
        }
    }

    /**
     * Removes the registration with the given key.
     */
    public final void removeRegistration(IBinder key) {
        synchronized (mMultiplexerLock) {
            int index = mRegistrations.indexOfKey(key);
            if (index < 0) {
                return;
            }

            removeRegistration(index);
        }
    }

    @GuardedBy("mMultiplexerLock")
    private void removeRegistration(int index) {
        TRegistration registration = mRegistrations.valueAt(index);

        registration.onUnregister();
        mRegistrations.removeAt(index);

        onRegistrationsUpdated();

        if (mRegistrations.isEmpty()) {
            onUnregister();
        }
    }

    /**
     * Invoked when a registration is added, removed, or replaced. Invoked while holding the
     * multiplexer's internal lock.
     */
    @GuardedBy("mMultiplexerLock")
    public final void onRegistrationsUpdated() {
        TMergedRegistration newMerged = mergeRegistrations(mRegistrations.values());
        if (newMerged.equals(mMerged)) {
            return;
        }
        mMerged = newMerged;
        onMergedRegistrationsUpdated();
    }

    /**
     * Called in order to generate a merged registration from the given set of active registrations.
     * The list of registrations will never be empty. If the resulting merged registration is equal
     * to the currently registered merged registration, nothing further will happen. If the merged
     * registration differs,{@link #onMergedRegistrationsUpdated()} will be invoked with the new
     * merged registration so that the backing service can be updated.
     */
    @GuardedBy("mMultiplexerLock")
    public abstract TMergedRegistration mergeRegistrations(
            @NonNull Collection<TRegistration> registrations);

    /**
     * The operation that the manager wants to handle when there is an update for the merged
     * registration.
     */
    @GuardedBy("mMultiplexerLock")
    public abstract void onMergedRegistrationsUpdated();

    protected final void deliverToListeners(
            Function<TRegistration, ListenerOperation<TListener>> function) {
        synchronized (mMultiplexerLock) {
            final int size = mRegistrations.size();
            for (int i = 0; i < size; i++) {
                TRegistration registration = mRegistrations.valueAt(i);
                BinderListenerRegistration.ListenerOperation<TListener> operation = function.apply(
                        registration);
                if (operation != null) {
                    registration.executeOperation(operation);
                }
            }
        }
    }
}
