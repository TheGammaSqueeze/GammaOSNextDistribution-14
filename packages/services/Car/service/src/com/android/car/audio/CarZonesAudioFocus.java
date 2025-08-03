/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.audio;

import static com.android.car.audio.FocusInteraction.AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.oem.CarAudioFeaturesInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneFocusProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.oem.CarOemProxyService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implements {@link AudioPolicy.AudioPolicyFocusListener}
 *
 * <p><b>Note:</b> Manages audio focus on a per zone basis.
 */
final class CarZonesAudioFocus extends AudioPolicy.AudioPolicyFocusListener {
    private static final String TAG = CarLog.tagFor(CarZonesAudioFocus.class);

    private final CarFocusCallback mCarFocusCallback;
    private CarAudioService mCarAudioService; // Dynamically assigned just after construction
    private AudioPolicy mAudioPolicy; // Dynamically assigned just after construction

    private final SparseArray<CarAudioFocus> mFocusZones;

    public static CarZonesAudioFocus createCarZonesAudioFocus(AudioManager audioManager,
            PackageManager packageManager, SparseArray<CarAudioZone> carAudioZones,
            CarAudioSettings carAudioSettings, CarFocusCallback carFocusCallback,
            CarVolumeInfoWrapper carVolumeInfoWrapper, @Nullable CarAudioFeaturesInfo features) {
        Objects.requireNonNull(audioManager, "Audio manager cannot be null");
        Objects.requireNonNull(packageManager, "Package manager cannot be null");
        Objects.requireNonNull(carAudioZones, "Car audio zones cannot be null");
        Preconditions.checkArgument(carAudioZones.size() != 0,
                "There must be a minimum of one audio zone");
        Objects.requireNonNull(carAudioSettings, "Car audio settings cannot be null");
        Objects.requireNonNull(carVolumeInfoWrapper, "Car volume info cannot be null");

        SparseArray<CarAudioFocus> audioFocusPerZone = new SparseArray<>();

        ContentObserverFactory observerFactory = new ContentObserverFactory(
                AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI);
        //Create focus for all the zones
        for (int i = 0; i < carAudioZones.size(); i++) {
            CarAudioZone audioZone = carAudioZones.valueAt(i);
            int audioZoneId = audioZone.getId();
            Slogf.d(TAG, "Adding new zone %d", audioZoneId);
            FocusInteraction interaction = new FocusInteraction(carAudioSettings, observerFactory);
            CarAudioFocus zoneFocusListener = new CarAudioFocus(audioManager, packageManager,
                    interaction, audioZone, carVolumeInfoWrapper, features);
            audioFocusPerZone.put(audioZoneId, zoneFocusListener);
        }
        return new CarZonesAudioFocus(audioFocusPerZone, carFocusCallback);
    }

    @VisibleForTesting
    CarZonesAudioFocus(SparseArray<CarAudioFocus> focusZones, CarFocusCallback carFocusCallback) {
        mFocusZones = focusZones;
        mCarFocusCallback = carFocusCallback;
    }

    /**
     * Query the current list of focus loser in zoneId for uid
     * @param uid uid to query for current focus losers
     * @param zoneId zone id to query for info
     * @return list of current focus losers for uid
     */
    ArrayList<AudioFocusInfo> getAudioFocusLosersForUid(int uid, int zoneId) {
        CarAudioFocus focus = mFocusZones.get(zoneId);
        return focus.getAudioFocusLosersForUid(uid);
    }

    /**
     * Query the current list of focus holders in zoneId for uid
     * @param uid uid to query for current focus holders
     * @param zoneId zone id to query
     * @return list of current focus holders that for uid
     */
    ArrayList<AudioFocusInfo> getAudioFocusHoldersForUid(int uid, int zoneId) {
        CarAudioFocus focus = mFocusZones.get(zoneId);
        return focus.getAudioFocusHoldersForUid(uid);
    }


    /**
     * For the zone queried, transiently lose all active focus entries
     * @param zoneId zone id where all focus entries should be lost
     * @return focus entries lost in the zone.
     */
    List<AudioFocusInfo> transientlyLoseAllFocusHoldersInZone(int zoneId) {
        CarAudioFocus focus = mFocusZones.get(zoneId);
        List<AudioFocusInfo> activeFocusInfos = focus.getAudioFocusHolders();
        if (!activeFocusInfos.isEmpty()) {
            transientlyLoseInFocusInZone(activeFocusInfos, zoneId);
        }
        return activeFocusInfos;
    }

    /**
     * For each entry in list, transiently lose focus
     * @param afiList list of audio focus entries
     * @param zoneId zone id where focus should be lost
     */
    void transientlyLoseInFocusInZone(List<AudioFocusInfo> afiList, int zoneId) {
        CarAudioFocus focus = mFocusZones.get(zoneId);

        transientlyLoseInFocusInZone(afiList, focus);
    }

    private void transientlyLoseInFocusInZone(List<AudioFocusInfo> audioFocusInfos,
            CarAudioFocus focus) {
        for (int index = 0; index < audioFocusInfos.size(); index++) {
            AudioFocusInfo info = audioFocusInfos.get(index);
            focus.removeAudioFocusInfoAndTransientlyLoseFocus(info);
        }
    }

    /**
     * For each entry in list, reevaluate and regain focus and notify focus listener of its zone
     *
     * @param audioFocusInfos list of audio focus entries to reevaluate and regain
     * @return list of results for regaining focus
     */
    List<Integer> reevaluateAndRegainAudioFocusList(List<AudioFocusInfo> audioFocusInfos) {
        List<Integer> res = new ArrayList<>(audioFocusInfos.size());
        ArraySet<Integer> zoneIds = new ArraySet<>();
        for (int index = 0; index < audioFocusInfos.size(); index++) {
            AudioFocusInfo afi = audioFocusInfos.get(index);
            int zoneId = getAudioZoneIdForAudioFocusInfo(afi);
            res.add(getCarAudioFocusForZoneId(zoneId).reevaluateAndRegainAudioFocus(afi));
            zoneIds.add(zoneId);
        }
        int[] zoneIdArray = new int[zoneIds.size()];
        for (int zoneIdIndex = 0; zoneIdIndex < zoneIds.size(); zoneIdIndex++) {
            zoneIdArray[zoneIdIndex] = zoneIds.valueAt(zoneIdIndex);
        }
        notifyFocusListeners(zoneIdArray);
        return res;
    }

    int reevaluateAndRegainAudioFocus(AudioFocusInfo afi) {
        int zoneId = getAudioZoneIdForAudioFocusInfo(afi);
        return getCarAudioFocusForZoneId(zoneId).reevaluateAndRegainAudioFocus(afi);
    }

    /**
     * Sets the owning policy of the audio focus
     *
     * <p><b>Note:</b> This has to happen after the construction to avoid a chicken and egg
     * problem when setting up the AudioPolicy which must depend on this object.

     * @param carAudioService owning car audio service
     * @param parentPolicy owning parent car audio policy
     */
    void setOwningPolicy(CarAudioService carAudioService, AudioPolicy parentPolicy) {
        mAudioPolicy = parentPolicy;
        mCarAudioService = carAudioService;

        for (int i = 0; i < mFocusZones.size(); i++) {
            mFocusZones.valueAt(i).setOwningPolicy(mAudioPolicy);
        }
    }

    void setRestrictFocus(boolean isFocusRestricted) {
        int[] zoneIds = new int[mFocusZones.size()];
        for (int i = 0; i < mFocusZones.size(); i++) {
            zoneIds[i] = mFocusZones.keyAt(i);
            mFocusZones.valueAt(i).setRestrictFocus(isFocusRestricted);
        }
        notifyFocusListeners(zoneIds);
    }

    @Override
    public void onAudioFocusRequest(AudioFocusInfo afi, int requestResult) {
        int zoneId = getAudioZoneIdForAudioFocusInfo(afi);
        getCarAudioFocusForZoneId(zoneId).onAudioFocusRequest(afi, requestResult);
        notifyFocusListeners(new int[]{zoneId});
    }

    /**
     * @see AudioManager#abandonAudioFocus(AudioManager.OnAudioFocusChangeListener, AudioAttributes)
     * Note that we'll get this call for a focus holder that dies while in the focus stack, so
     * we don't need to watch for death notifications directly.
     */
    @Override
    public void onAudioFocusAbandon(AudioFocusInfo afi) {
        int zoneId = getAudioZoneIdForAudioFocusInfo(afi);
        getCarAudioFocusForZoneId(zoneId).onAudioFocusAbandon(afi);
        notifyFocusListeners(new int[]{zoneId});
    }

    private CarAudioFocus getCarAudioFocusForZoneId(int zoneId) {
        return mFocusZones.get(zoneId);
    }

    private int getAudioZoneIdForAudioFocusInfo(AudioFocusInfo afi) {
        int zoneId = mCarAudioService.getZoneIdForAudioFocusInfo(afi);

        // If the bundle attribute for AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID has been assigned
        // Use zone id from that instead.
        Bundle bundle = afi.getAttributes().getBundle();

        if (bundle != null) {
            int bundleZoneId =
                    bundle.getInt(CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID,
                            -1);
            // check if the zone id is within current zones bounds
            if (mCarAudioService.isAudioZoneIdValid(bundleZoneId)) {
                Slogf.d(TAG, "getFocusForAudioFocusInfo valid zoneId %d with bundle request for"
                        + " client %s", bundleZoneId, afi.getClientId());
                zoneId = bundleZoneId;
            } else {
                Slogf.w(TAG, "getFocusForAudioFocusInfo invalid zoneId %d with bundle request for "
                                + "client %s, dispatching focus request to zoneId %d", bundleZoneId,
                        afi.getClientId(), zoneId);
            }
        }

        return zoneId;
    }

    private void notifyFocusListeners(int[] zoneIds) {
        SparseArray<List<AudioFocusInfo>> focusHoldersByZoneId = new SparseArray<>(zoneIds.length);
        for (int i = 0; i < zoneIds.length; i++) {
            int zoneId = zoneIds[i];
            focusHoldersByZoneId.put(zoneId, mFocusZones.get(zoneId).getAudioFocusHolders());
            sendFocusChangeToOemService(getCarAudioFocusForZoneId(zoneId), zoneId);
        }

        if (mCarFocusCallback == null) {
            return;
        }
        mCarFocusCallback.onFocusChange(zoneIds, focusHoldersByZoneId);
    }

    private void sendFocusChangeToOemService(CarAudioFocus carAudioFocus, int zoneId) {
        CarOemProxyService proxy = CarLocalServices.getService(CarOemProxyService.class);
        if (!proxy.isOemServiceEnabled() || !proxy.isOemServiceReady()
                || proxy.getCarOemAudioFocusService() == null) {
            return;
        }

        proxy.getCarOemAudioFocusService().notifyAudioFocusChange(
                carAudioFocus.getAudioFocusHolders(), carAudioFocus.getAudioFocusLosers(), zoneId);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.println("*CarZonesAudioFocus*");
        writer.increaseIndent();
        writer.printf("Has Focus Callback: %b\n", mCarFocusCallback != null);
        writer.println("Car Zones Audio Focus Listeners:");
        writer.increaseIndent();
        for (int i = 0; i < mFocusZones.size(); i++) {
            writer.printf("Zone Id: %d\n", mFocusZones.keyAt(i));
            writer.increaseIndent();
            mFocusZones.valueAt(i).dump(writer);
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        long focusHandlerToken = proto.start(CarAudioDumpProto.FOCUS_HANDLER);
        proto.write(CarAudioZoneFocusProto.HAS_FOCUS_CALLBACK, mCarFocusCallback != null);
        for (int i = 0; i < mFocusZones.size(); i++) {
            mFocusZones.valueAt(i).dumpProto(proto);
        }
        proto.end(focusHandlerToken);
    }

    public void updateUserForZoneId(int audioZoneId, @UserIdInt int userId) {
        Preconditions.checkArgument(mCarAudioService.isAudioZoneIdValid(audioZoneId),
                "Invalid zoneId %d", audioZoneId);
        mFocusZones.get(audioZoneId).getFocusInteraction().setUserIdForSettings(userId);
    }

    AudioFocusStack transientlyLoseMediaAudioFocusForUser(int userId, int zoneId) {
        AudioAttributes audioAttributes =
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();
        CarAudioFocus carAudioFocus = mFocusZones.get(zoneId);
        List<AudioFocusInfo> activeFocusInfos = carAudioFocus
                .getActiveAudioFocusForUserAndAudioAttributes(audioAttributes, userId);
        List<AudioFocusInfo> inactiveFocusInfos = carAudioFocus
                .getInactiveAudioFocusForUserAndAudioAttributes(audioAttributes, userId);

        return transientlyLoserFocusForFocusStack(carAudioFocus, activeFocusInfos,
                inactiveFocusInfos);
    }

    AudioFocusStack transientlyLoseAudioFocusForZone(int zoneId) {
        CarAudioFocus carAudioFocus = mFocusZones.get(zoneId);
        List<AudioFocusInfo> activeFocusInfos = carAudioFocus.getAudioFocusHolders();
        List<AudioFocusInfo> inactiveFocusInfos = carAudioFocus.getAudioFocusLosers();

        return transientlyLoserFocusForFocusStack(carAudioFocus, activeFocusInfos,
                inactiveFocusInfos);
    }

    private AudioFocusStack transientlyLoserFocusForFocusStack(CarAudioFocus carAudioFocus,
            List<AudioFocusInfo> activeFocusInfos, List<AudioFocusInfo> inactiveFocusInfos) {
        // Order matters here: Remove the focus losers first
        // then do the current holder to prevent loser from popping up while
        // the focus is being removed for current holders
        // Remove focus for current focus losers
        if (!inactiveFocusInfos.isEmpty()) {
            transientlyLoseInFocusInZone(inactiveFocusInfos, carAudioFocus);
        }

        if (!activeFocusInfos.isEmpty()) {
            transientlyLoseInFocusInZone(activeFocusInfos, carAudioFocus);
        }

        return new AudioFocusStack(activeFocusInfos, inactiveFocusInfos);
    }

    void regainMediaAudioFocusInZone(AudioFocusStack mediaFocusStack, int zoneId) {
        CarAudioFocus carAudioFocus = mFocusZones.get(zoneId);

        // Order matters here: Regain focus for
        // previously lost focus holders then regain
        // focus for holders that had it last.
        // Regain focus for the focus losers from previous zone.
        if (!mediaFocusStack.getInactiveFocusList().isEmpty()) {
            regainMediaAudioFocusInZone(mediaFocusStack.getInactiveFocusList(), carAudioFocus);
        }

        if (!mediaFocusStack.getActiveFocusList().isEmpty()) {
            regainMediaAudioFocusInZone(mediaFocusStack.getActiveFocusList(), carAudioFocus);
        }
    }

    private void regainMediaAudioFocusInZone(List<AudioFocusInfo> focusInfos,
            CarAudioFocus carAudioFocus) {
        for (int index = 0; index < focusInfos.size(); index++) {
            carAudioFocus.reevaluateAndRegainAudioFocus(focusInfos.get(index));
        }
    }

    /**
     * Callback to get notified of the active focus holders after any focus request or abandon  call
     */
    public interface CarFocusCallback {
        /**
         * Called after a focus request or abandon call is handled.
         *
         * @param audioZoneIds IDs of the zones where the changes took place
         * @param focusHoldersByZoneId sparse array by zone ID, where each value is a list of
         * {@link AudioFocusInfo}s holding focus in specified audio zone
         */
        void onFocusChange(int[] audioZoneIds,
                @NonNull SparseArray<List<AudioFocusInfo>> focusHoldersByZoneId);
    }
}
