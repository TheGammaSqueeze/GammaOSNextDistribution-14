/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.vms;

import android.annotation.NonNull;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Internal utility for computing subscription updates.
 *
 * @hide
 */
public final class VmsSubscriptionHelper {
    private final Consumer<Set<VmsAssociatedLayer>> mUpdateHandler;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArraySet<VmsLayer> mLayerSubscriptions = new ArraySet<>();

    @GuardedBy("mLock")
    private final ArrayMap<VmsLayer, SparseBooleanArray> mPublisherSubscriptions = new ArrayMap<>();

    @GuardedBy("mLock")
    private boolean mPendingUpdate;

    /**
     * Constructor for subscription helper.
     *
     * @param updateHandler Consumer of subscription updates.
     */
    public VmsSubscriptionHelper(@NonNull Consumer<Set<VmsAssociatedLayer>> updateHandler) {
        mUpdateHandler = Objects.requireNonNull(updateHandler, "updateHandler cannot be null");
    }

    /**
     * Adds a subscription to a layer.
     */
    public void subscribe(@NonNull VmsLayer layer) {
        Objects.requireNonNull(layer, "layer cannot be null");
        synchronized (mLock) {
            if (mLayerSubscriptions.add(layer)) {
                mPendingUpdate = true;
            }
            publishSubscriptionUpdate();
        }
    }

    /**
     * Adds a subscription to a specific provider of a layer.
     */
    public void subscribe(@NonNull VmsLayer layer, int providerId) {
        Objects.requireNonNull(layer, "layer cannot be null");
        synchronized (mLock) {
            SparseBooleanArray providerIds = mPublisherSubscriptions.computeIfAbsent(layer,
                    ignored -> new SparseBooleanArray());
            if (!providerIds.get(providerId)) {
                providerIds.put(providerId, true);
                mPendingUpdate = true;
            }
            publishSubscriptionUpdate();
        }
    }

    /**
     * Removes a subscription to a layer.
     */
    public void unsubscribe(@NonNull VmsLayer layer) {
        Objects.requireNonNull(layer, "layer cannot be null");
        synchronized (mLock) {
            if (mLayerSubscriptions.remove(layer)) {
                mPendingUpdate = true;
            }
            publishSubscriptionUpdate();
        }
    }

    /**
     * Removes a subscription to the specific provider of a layer.
     */
    public void unsubscribe(@NonNull VmsLayer layer, int providerId) {
        Objects.requireNonNull(layer, "layer cannot be null");
        synchronized (mLock) {
            SparseBooleanArray providerIds = mPublisherSubscriptions.get(layer);
            if (providerIds != null && providerIds.get(providerId)) {
                providerIds.delete(providerId);
                if (providerIds.size() == 0) {
                    mPublisherSubscriptions.remove(layer);
                }
                mPendingUpdate = true;
            }
            publishSubscriptionUpdate();
        }
    }

    /**
     * Gets the current set of subscriptions.
     */
    @NonNull
    @GuardedBy("mLock")
    public Set<VmsAssociatedLayer> getSubscriptions() {
        Set<VmsAssociatedLayer> vmsAssociatedLayerSet = new ArraySet<>();
        for (int i = 0; i < mLayerSubscriptions.size(); i++) {
            VmsLayer layer = mLayerSubscriptions.valueAt(i);
            vmsAssociatedLayerSet.add(new VmsAssociatedLayer(layer, Collections.emptySet()));
        }

        for (int i = 0; i < mPublisherSubscriptions.size(); i++) {
            VmsLayer layer = mPublisherSubscriptions.keyAt(i);
            if (!mLayerSubscriptions.contains(layer)) {
                vmsAssociatedLayerSet.add(
                        toAssociatedLayer(layer, mPublisherSubscriptions.valueAt(i)));
            }
        }
        return vmsAssociatedLayerSet;
    }

    private void publishSubscriptionUpdate() {
        synchronized (mLock) {
            if (mPendingUpdate) {
                mUpdateHandler.accept(getSubscriptions());
            }
            mPendingUpdate = false;
        }
    }

    private static VmsAssociatedLayer toAssociatedLayer(VmsLayer layer,
            SparseBooleanArray providerIdArray) {
        Set<Integer> providerIds = new ArraySet<>(providerIdArray.size());
        for (int i = 0; i < providerIdArray.size(); i++) {
            providerIds.add(providerIdArray.keyAt(i));
        }
        return new VmsAssociatedLayer(layer, providerIds);
    }
}
