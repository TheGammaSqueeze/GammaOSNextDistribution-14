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

package com.android.car.internal.property;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.property.CarPropertyHelper.propertyIdsToString;
import static com.android.car.internal.util.ArrayUtils.convertToIntArray;

import android.annotation.Nullable;
import android.car.VehiclePropertyIds;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.PairSparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class manages [{propertyId, areaId} -> RateInfoForClients] map and maintains two states:
 * a current state and a staged state. The staged state represents the proposed changes. After
 * the changes are applied to the lower layer, caller either uses {@link #commit} to replace
 * the curren state with the staged state, or uses {@link #dropCommit} to drop the staged state.
 *
 * A common pattern is
 *
 * ```
 * synchronized (mLock) {
 *   mSubscriptionManager.stageNewOptions(...);
 *   // Optionally stage some other options.
 *   mSubscriptionManager.stageNewOptions(...);
 *   // Optionally stage unregistration.
 *   mSubscriptionManager.stageUnregister(...);
 *
 *   mSubscriptionManager.diffBetweenCurrentAndStage(...);
 *   try {
 *     // Apply the diff.
 *   } catch (Exception e) {
 *     mSubscriptionManager.dropCommit();
 *     throw e;
 *   }
 *   mSubscriptionManager.commit();
 * }
 * ```
 *
 * This class is not thread-safe.
 *
 * @param <ClientType> A class representing a client.
 *
 * @hide
 */
public final class SubscriptionManager<ClientType> {
    private static final String TAG = SubscriptionManager.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final class RateInfo {
        public final float updateRateHz;
        public final boolean enableVariableUpdateRate;
        public final float resolution;

        RateInfo(float updateRateHz, boolean enableVariableUpdateRate, float resolution) {
            this.updateRateHz = updateRateHz;
            this.enableVariableUpdateRate = enableVariableUpdateRate;
            this.resolution = resolution;
        }

        @Override
        public String toString() {
            return String.format(
                    "RateInfo{updateRateHz: %f, enableVur: %b, resolution: %f}", updateRateHz,
                    enableVariableUpdateRate, resolution);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RateInfo)) {
                return false;
            }
            RateInfo that = (RateInfo) other;
            return updateRateHz == that.updateRateHz
                    && enableVariableUpdateRate == that.enableVariableUpdateRate
                    && resolution == that.resolution;
        }

        @Override
        public int hashCode() {
            return Objects.hash(updateRateHz, enableVariableUpdateRate, resolution);
        }
    }

    /**
     * This class provides an abstraction for all the clients and their subscribed rate for a
     * specific {propertyId, areaId} pair.
     */
    private static final class RateInfoForClients<ClientType> {
        private final ArrayMap<ClientType, RateInfo> mRateInfoByClient;
        // An ordered set for all update rates to provide efficient add, remove and get max update
        // rate.
        private final TreeSet<Float> mUpdateRatesHz;
        private final ArrayMap<Float, Integer> mClientCountByUpdateRateHz;
        private final TreeSet<Float> mResolutions;
        private final ArrayMap<Float, Integer> mClientCountByResolution;
        // How many clients has enabled variable update rate. We can only enable variable update
        // rate in the underlying layer if all clients enable VUR.
        private int mEnableVariableUpdateRateCount;

        RateInfoForClients() {
            mRateInfoByClient = new ArrayMap<>();
            mUpdateRatesHz = new TreeSet<>();
            mClientCountByUpdateRateHz = new ArrayMap<>();
            mResolutions = new TreeSet<>();
            mClientCountByResolution = new ArrayMap<>();
        }

        RateInfoForClients(RateInfoForClients other) {
            mRateInfoByClient = new ArrayMap<>(other.mRateInfoByClient);
            mUpdateRatesHz = new TreeSet<>(other.mUpdateRatesHz);
            mClientCountByUpdateRateHz = new ArrayMap<>(other.mClientCountByUpdateRateHz);
            mResolutions = new TreeSet<>(other.mResolutions);
            mClientCountByResolution = new ArrayMap<>(other.mClientCountByResolution);
            mEnableVariableUpdateRateCount = other.mEnableVariableUpdateRateCount;
        }

        /**
         * Gets the max update rate for this {propertyId, areaId}.
         */
        private float getMaxUpdateRateHz() {
            return mUpdateRatesHz.last();
        }

        /**
         * Gets the min required resolution for this {propertyId, areaId}.
         */
        private float getMinRequiredResolution() {
            return mResolutions.first();
        }

        private boolean isVariableUpdateRateEnabledForAllClients() {
            return mEnableVariableUpdateRateCount == mRateInfoByClient.size();
        }

        /**
         * Gets the combined rate info for all clients.
         *
         * We use the max update rate, min required resolution, and only enable VUR if all clients
         * enable.
         */
        RateInfo getCombinedRateInfo() {
            return new RateInfo(getMaxUpdateRateHz(), isVariableUpdateRateEnabledForAllClients(),
                    getMinRequiredResolution());
        }

        Set<ClientType> getClients() {
            return mRateInfoByClient.keySet();
        }

        float getUpdateRateHz(ClientType client) {
            return mRateInfoByClient.get(client).updateRateHz;
        }

        boolean isVariableUpdateRateEnabled(ClientType client) {
            return mRateInfoByClient.get(client).enableVariableUpdateRate;
        }

        float getResolution(ClientType client) {
            return mRateInfoByClient.get(client).resolution;
        }

        /**
         * Adds a new client for this {propertyId, areaId}.
         */
        void add(ClientType client, float updateRateHz, boolean enableVariableUpdateRate,
                 float resolution) {
            // Clear the existing updateRateHz for the client if exists.
            remove(client);
            // Store the new rate info.
            mRateInfoByClient.put(client,
                    new RateInfo(updateRateHz, enableVariableUpdateRate, resolution));

            if (enableVariableUpdateRate) {
                mEnableVariableUpdateRateCount++;
            }

            if (!mClientCountByUpdateRateHz.containsKey(updateRateHz)) {
                mUpdateRatesHz.add(updateRateHz);
                mClientCountByUpdateRateHz.put(updateRateHz, 1);
            } else {
                mClientCountByUpdateRateHz.put(updateRateHz,
                        mClientCountByUpdateRateHz.get(updateRateHz) + 1);
            }

            if (!mClientCountByResolution.containsKey(resolution)) {
                mResolutions.add(resolution);
                mClientCountByResolution.put(resolution, 1);
            } else {
                mClientCountByResolution.put(resolution,
                        mClientCountByResolution.get(resolution) + 1);
            }
        }

        void remove(ClientType client) {
            if (!mRateInfoByClient.containsKey(client)) {
                return;
            }
            RateInfo rateInfo = mRateInfoByClient.get(client);
            if (rateInfo.enableVariableUpdateRate) {
                mEnableVariableUpdateRateCount--;
            }
            float updateRateHz = rateInfo.updateRateHz;
            if (mClientCountByUpdateRateHz.containsKey(updateRateHz)) {
                int newCount = mClientCountByUpdateRateHz.get(updateRateHz) - 1;
                if (newCount == 0) {
                    mClientCountByUpdateRateHz.remove(updateRateHz);
                    mUpdateRatesHz.remove(updateRateHz);
                } else {
                    mClientCountByUpdateRateHz.put(updateRateHz, newCount);
                }
            }
            float resolution = rateInfo.resolution;
            if (mClientCountByResolution.containsKey(resolution)) {
                int newCount = mClientCountByResolution.get(resolution) - 1;
                if (newCount == 0) {
                    mClientCountByResolution.remove(resolution);
                    mResolutions.remove(resolution);
                } else {
                    mClientCountByResolution.put(resolution, newCount);
                }
            }

            mRateInfoByClient.remove(client);
        }

        boolean isEmpty() {
            return mRateInfoByClient.isEmpty();
        }
    }

    PairSparseArray<RateInfoForClients<ClientType>> mCurrentRateInfoByClientByPropIdAreaId =
            new PairSparseArray<>();
    PairSparseArray<RateInfoForClients<ClientType>> mStagedRateInfoByClientByPropIdAreaId =
            new PairSparseArray<>();
    ArraySet<int[]> mStagedAffectedPropIdAreaIds = new ArraySet<>();

    /**
     * Prepares new subscriptions.
     *
     * This apply the new subscribe options in the staging area without actually committing them.
     * Client should call {@link #diffBetweenCurrentAndStage} to get the difference between current
     * and the staging state. Apply them to the lower layer, and either commit the change after
     * the operation succeeds or drop the change after the operation failed.
     */
    public void stageNewOptions(ClientType client, List<CarSubscription> options) {
        if (DBG) {
            Log.d(TAG, "stageNewOptions: options: " + options);
        }

        cloneCurrentToStageIfClean();

        for (int i = 0; i < options.size(); i++) {
            CarSubscription option = options.get(i);
            int propertyId = option.propertyId;
            for (int areaId : option.areaIds) {
                mStagedAffectedPropIdAreaIds.add(new int[]{propertyId, areaId});
                if (mStagedRateInfoByClientByPropIdAreaId.get(propertyId, areaId) == null) {
                    mStagedRateInfoByClientByPropIdAreaId.put(propertyId, areaId,
                            new RateInfoForClients<>());
                }
                mStagedRateInfoByClientByPropIdAreaId.get(propertyId, areaId).add(
                        client, option.updateRateHz, option.enableVariableUpdateRate,
                        option.resolution);
            }
        }
    }

    /**
     * Prepares unregistration for list of property IDs.
     *
     * This apply the unregistration in the staging area without actually committing them.
     */
    public void stageUnregister(ClientType client, ArraySet<Integer> propertyIdsToUnregister) {
        if (DBG) {
            Log.d(TAG, "stageUnregister: propertyIdsToUnregister: " + propertyIdsToString(
                    propertyIdsToUnregister));
        }

        cloneCurrentToStageIfClean();

        for (int i = 0; i < propertyIdsToUnregister.size(); i++) {
            int propertyId = propertyIdsToUnregister.valueAt(i);
            ArraySet<Integer> areaIds =
                    mStagedRateInfoByClientByPropIdAreaId.getSecondKeysForFirstKey(propertyId);
            for (int j = 0; j < areaIds.size(); j++) {
                int areaId = areaIds.valueAt(j);
                mStagedAffectedPropIdAreaIds.add(new int[]{propertyId, areaId});
                RateInfoForClients<ClientType> rateInfoForClients =
                        mStagedRateInfoByClientByPropIdAreaId.get(propertyId, areaId);
                if (rateInfoForClients == null) {
                    Log.e(TAG, "The property: " + VehiclePropertyIds.toString(propertyId)
                            + ", area ID: " + areaId + " was not registered, do nothing");
                    continue;
                }
                rateInfoForClients.remove(client);
                if (rateInfoForClients.isEmpty()) {
                    mStagedRateInfoByClientByPropIdAreaId.remove(propertyId, areaId);
                }
            }
        }
    }

    /**
     * Commit the staged changes.
     *
     * This will replace the current state with the staged state. This should be called after the
     * changes are applied successfully to the lower layer.
     */
    public void commit() {
        if (mStagedAffectedPropIdAreaIds.isEmpty()) {
            if (DBG) {
                Log.d(TAG, "No changes has been staged, nothing to commit");
            }
            return;
        }
        // Drop the current state.
        mCurrentRateInfoByClientByPropIdAreaId = mStagedRateInfoByClientByPropIdAreaId;
        mStagedAffectedPropIdAreaIds.clear();
    }

    /**
     * Drop the staged changes.
     *
     * This should be called after the changes failed to apply to the lower layer.
     */
    public void dropCommit() {
        if (mStagedAffectedPropIdAreaIds.isEmpty()) {
            if (DBG) {
                Log.d(TAG, "No changes has been staged, nothing to drop");
            }
            return;
        }
        // Drop the staged state.
        mStagedRateInfoByClientByPropIdAreaId = mCurrentRateInfoByClientByPropIdAreaId;
        mStagedAffectedPropIdAreaIds.clear();
    }

    public ArraySet<Integer> getCurrentSubscribedPropIds() {
        return new ArraySet<Integer>(mCurrentRateInfoByClientByPropIdAreaId.getFirstKeys());
    }

    /**
     * Clear both the current state and staged state.
     */
    public void clear() {
        mStagedRateInfoByClientByPropIdAreaId.clear();
        mCurrentRateInfoByClientByPropIdAreaId.clear();
        mStagedAffectedPropIdAreaIds.clear();
    }

    /**
     * Gets all the subscription clients for the given propertyID, area ID pair.
     *
     * This uses the current state.
     */
    public @Nullable Set<ClientType> getClients(int propertyId, int areaId) {
        if (!mCurrentRateInfoByClientByPropIdAreaId.contains(propertyId, areaId)) {
            return null;
        }
        return mCurrentRateInfoByClientByPropIdAreaId.get(propertyId, areaId).getClients();
    }

    /**
     * Dumps the state.
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("Current subscription states:");
        dumpStates(writer, mCurrentRateInfoByClientByPropIdAreaId);
        writer.println("Staged subscription states:");
        dumpStates(writer, mStagedRateInfoByClientByPropIdAreaId);
    }

    /**
     * Calculates the difference between the staged state and current state.
     *
     * @param outDiffSubscriptions The output subscriptions that has changed. This includes
     *      both new subscriptions and updated subscriptions with a new update rate.
     * @param outPropertyIdsToUnsubscribe The output property IDs that need to be unsubscribed.
     */
    public void diffBetweenCurrentAndStage(List<CarSubscription> outDiffSubscriptions,
            List<Integer> outPropertyIdsToUnsubscribe) {
        if (mStagedAffectedPropIdAreaIds.isEmpty()) {
            if (DBG) {
                Log.d(TAG, "No changes has been staged, no diff");
            }
            return;
        }
        ArraySet<Integer> possiblePropIdsToUnsubscribe = new ArraySet<>();
        PairSparseArray<RateInfo> diffRateInfoByPropIdAreaId = new PairSparseArray<>();
        for (int i = 0; i < mStagedAffectedPropIdAreaIds.size(); i++) {
            int[] propIdAreaId = mStagedAffectedPropIdAreaIds.valueAt(i);
            int propertyId = propIdAreaId[0];
            int areaId = propIdAreaId[1];

            if (!mStagedRateInfoByClientByPropIdAreaId.contains(propertyId, areaId)) {
                // The [PropertyId, areaId] is no longer subscribed.
                if (DBG) {
                    Log.d(TAG, String.format("The property: %s, areaId: %d is no longer subscribed",
                            VehiclePropertyIds.toString(propertyId), areaId));
                }
                possiblePropIdsToUnsubscribe.add(propertyId);
                continue;
            }

            RateInfo newCombinedRateInfo = mStagedRateInfoByClientByPropIdAreaId
                    .get(propertyId, areaId).getCombinedRateInfo();

            if (!mCurrentRateInfoByClientByPropIdAreaId.contains(propertyId, areaId)
                    || !(mCurrentRateInfoByClientByPropIdAreaId
                            .get(propertyId, areaId).getCombinedRateInfo()
                            .equals(newCombinedRateInfo))) {
                if (DBG) {
                    Log.d(TAG, String.format(
                            "New combined subscription rate info for property: %s, areaId: %d, %s",
                            VehiclePropertyIds.toString(propertyId), areaId, newCombinedRateInfo));
                }
                diffRateInfoByPropIdAreaId.put(propertyId, areaId, newCombinedRateInfo);
                continue;
            }
        }
        outDiffSubscriptions.addAll(getCarSubscription(diffRateInfoByPropIdAreaId));
        for (int i = 0; i < possiblePropIdsToUnsubscribe.size(); i++) {
            int possiblePropIdToUnsubscribe = possiblePropIdsToUnsubscribe.valueAt(i);
            if (mStagedRateInfoByClientByPropIdAreaId.getSecondKeysForFirstKey(
                    possiblePropIdToUnsubscribe).isEmpty()) {
                // We should only unsubscribe the property if all area IDs are unsubscribed.
                if (DBG) {
                    Log.d(TAG, String.format(
                            "All areas for the property: %s are no longer subscribed, "
                            + "unsubscribe it", VehiclePropertyIds.toString(
                                    possiblePropIdToUnsubscribe)));
                }
                outPropertyIdsToUnsubscribe.add(possiblePropIdToUnsubscribe);
            }
        }
    }

    /**
     * Generates the {@code CarSubscription} instances.
     *
     * Converts [[propId, areaId] -> updateRateHz] map to
     * [propId -> [updateRateHz -> list of areaIds]] and then generates subscribe option for each
     * updateRateHz for each propId.
     *
     * @param diffRateInfoByPropIdAreaId A [[propId, areaId] -> updateRateHz] map.
     */
    private static List<CarSubscription> getCarSubscription(
            PairSparseArray<RateInfo> diffRateInfoByPropIdAreaId) {
        List<CarSubscription> carSubscriptions = new ArrayList<>();
        ArraySet<Integer> propertyIds = diffRateInfoByPropIdAreaId.getFirstKeys();
        for (int propertyIdIndex = 0; propertyIdIndex < propertyIds.size(); propertyIdIndex++) {
            int propertyId = propertyIds.valueAt(propertyIdIndex);
            ArraySet<Integer> areaIds = diffRateInfoByPropIdAreaId.getSecondKeysForFirstKey(
                    propertyId);

            // Group the areaIds by RateInfo.
            ArrayMap<RateInfo, List<Integer>> areaIdsByRateInfo = new ArrayMap<>();
            for (int i = 0; i < areaIds.size(); i++) {
                int areaId = areaIds.valueAt(i);
                RateInfo rateInfo = diffRateInfoByPropIdAreaId.get(propertyId, areaId);
                if (!areaIdsByRateInfo.containsKey(rateInfo)) {
                    areaIdsByRateInfo.put(rateInfo, new ArrayList<>());
                }
                areaIdsByRateInfo.get(rateInfo).add(areaId);
            }

            // Convert each update rate to a new CarSubscription.
            for (int i = 0; i < areaIdsByRateInfo.size(); i++) {
                CarSubscription option = new CarSubscription();
                option.propertyId = propertyId;
                option.areaIds = convertToIntArray(areaIdsByRateInfo.valueAt(i));
                option.updateRateHz = areaIdsByRateInfo.keyAt(i).updateRateHz;
                option.enableVariableUpdateRate =
                        areaIdsByRateInfo.keyAt(i).enableVariableUpdateRate;
                option.resolution = areaIdsByRateInfo.keyAt(i).resolution;
                carSubscriptions.add(option);
            }
        }

        return carSubscriptions;
    }

    private void cloneCurrentToStageIfClean() {
        if (!mStagedAffectedPropIdAreaIds.isEmpty()) {
            // The current state is not clean, we already cloned once. We allow staging multiple
            // commits before final commit/drop.
            return;
        }

        mStagedRateInfoByClientByPropIdAreaId = new PairSparseArray<>();
        for (int i = 0; i < mCurrentRateInfoByClientByPropIdAreaId.size(); i++) {
            int[] keyPair = mCurrentRateInfoByClientByPropIdAreaId.keyPairAt(i);
            mStagedRateInfoByClientByPropIdAreaId.put(keyPair[0], keyPair[1],
                    new RateInfoForClients<>(
                            mCurrentRateInfoByClientByPropIdAreaId.valueAt(i)));
        }
    }

    private static <ClientType> void dumpStates(IndentingPrintWriter writer,
            PairSparseArray<RateInfoForClients<ClientType>> states) {
        for (int i = 0; i < states.size(); i++) {
            int[] propIdAreaId = states.keyPairAt(i);
            RateInfoForClients<ClientType> rateInfoForClients = states.valueAt(i);
            int propertyId = propIdAreaId[0];
            int areaId = propIdAreaId[1];
            Set<ClientType> clients = states.get(propertyId, areaId).getClients();
            writer.println("property: " + VehiclePropertyIds.toString(propertyId)
                    + ", area ID: " + areaId + " is registered by " + clients.size()
                    + " client(s).");
            writer.increaseIndent();
            for (ClientType client : clients) {
                writer.println("Client " + client + ": Subscribed at "
                        + rateInfoForClients.getUpdateRateHz(client) + " hz"
                        + ", enableVur: "
                        + rateInfoForClients.isVariableUpdateRateEnabled(client)
                        + ", resoliution: " + rateInfoForClients.getResolution(client));
            }
            writer.decreaseIndent();
        }
    }
}
