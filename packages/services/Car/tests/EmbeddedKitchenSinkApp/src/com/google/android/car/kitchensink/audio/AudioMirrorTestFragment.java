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

package com.google.android.car.kitchensink.audio;

import static android.R.layout.simple_spinner_dropdown_item;
import static android.R.layout.simple_spinner_item;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_AUDIO_MIRRORING;
import static android.car.media.CarAudioManager.AUDIO_MIRROR_CAN_ENABLE;
import static android.car.media.CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED;
import static android.car.media.CarAudioManager.INVALID_AUDIO_ZONE;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.media.AudioZonesMirrorStatusCallback;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.List;

public final class AudioMirrorTestFragment extends Fragment {

    public static final String FRAGMENT_NAME = "Mirror Audio Zones";
    private static final String TAG = AudioMirrorTestFragment.class.getSimpleName();

    private final SparseArray<Long> mZoneIdToMirrorId = new SparseArray<>();
    private final List<Long> mMirrorRequestIds = new ArrayList<>();
    private Context mContext;
    private CarAudioManager mCarAudioManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private Spinner mZoneOneSpinner;
    private Spinner mZoneTwoSpinner;
    private Spinner mMirrorIdSpinner;
    private ArrayAdapter<Integer> mZoneOneAdapter;
    private ArrayAdapter<Integer> mZoneTwoAdapter;
    private ArrayAdapter<Long> mMirrorIdAdapter;
    private boolean mIsMirrorCallbackSet;

    private AudioZonesMirrorStatusCallback mMirrorCallback = (mirroredAudioZones, status) -> {
        long requestId = mZoneIdToMirrorId.get(mirroredAudioZones.get(0), INVALID_REQUEST_ID);
        if (requestId == INVALID_REQUEST_ID) {
            showToast("Mirror enabled from outside app for zones " + mirroredAudioZones);
            return;
        }
        if (status == AUDIO_REQUEST_STATUS_APPROVED) {
            mMirrorRequestIds.add(requestId);
            updateMirrorIds();
            showToast("Mirror request " + requestId + " approved");
            return;
        }

        showToast("Mirror request " + requestId + " status changed: " + status);
        for (int audioZoneId : mirroredAudioZones) {
            mZoneIdToMirrorId.remove(audioZoneId);
        }

        mMirrorRequestIds.remove(requestId);
        updateMirrorIds();
    };

    private void handleResetMirrorForAudioZones(long requestId) {
        Log.v(TAG, "handleResetMirrorForAudioZones mirror id " + requestId);
        List<Integer> mirroringZones = mCarAudioManager
                .getMirrorAudioZonesForMirrorRequest(requestId);
        if (mirroringZones.size() == 0) {
            showToast("Request " + requestId + " is no longer valid");
            return;
        }

        showToast("Disabling mirror request " + requestId);
        mCarAudioManager.disableAudioMirror(requestId);
    }

    private void handleEnableMirroringForZones(int zoneOne, int zoneTwo) {
        if (zoneOne == zoneTwo) {
            showToast("Must select two distinct zones to mirror");
            return;
        }
        if (isZoneCurrentlyMirroringOrCastingToPrimaryZone(zoneOne)
                || isZoneCurrentlyMirroringOrCastingToPrimaryZone(zoneTwo)) {
            return;
        }

        int status;
        try {
            status = mCarAudioManager.canEnableAudioMirror();
        } catch (Exception e) {
            showToast("Error while enabling mirror: " + e.getMessage());
            return;
        }

        if (status != AUDIO_MIRROR_CAN_ENABLE) {
            showToast("Can not enable any more zones for mirroring, status " + status);
            return;
        }

        List<Integer> zonesToMirror = List.of(zoneOne, zoneTwo);
        long requestId = mCarAudioManager.enableMirrorForAudioZones(zonesToMirror);

        if (requestId == INVALID_REQUEST_ID) {
            showToast("Could not enable mirroring for zones [" + zoneOne + ", " + zoneTwo + "]");
            return;
        }

        for (int zoneId : zonesToMirror) {
            mZoneIdToMirrorId.put(zoneId, requestId);
        }
        showToast("Requested mirroring for audio zones [" + zoneOne + ", " + zoneTwo + "]");
    }

    private boolean isZoneCurrentlyMirroringOrCastingToPrimaryZone(int audioZoneId) {
        List<Integer> mirroringZones = mCarAudioManager.getMirrorAudioZonesForAudioZone(
                audioZoneId);
        if (mirroringZones.size() != 0) {
            showToast("Zone " + audioZoneId + " is already mirroring");
            return true;
        }

        OccupantZoneInfo info = mCarOccupantZoneManager.getOccupantForAudioZoneId(audioZoneId);
        if (mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info)) {
            showToast("Can not enable mirror, occupant in audio zone " + audioZoneId
                    + " is currently playing audio in primary zone");
            return true;
        }

        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        View view = inflater
                .inflate(R.layout.allow_audio_mirror, container, /* attachToRoot= */ false);

        mZoneOneSpinner = view.findViewById(R.id.zone_one_spinner);
        mZoneTwoSpinner = view.findViewById(R.id.zone_two_spinner);
        mMirrorIdSpinner = view.findViewById(R.id.mirror_id_spinner);

        Button audioMirrorButton = view.findViewById(R.id.enable_audio_mirror_button);
        audioMirrorButton.setOnClickListener(v -> handleEnableAudioMirror());
        Button resetMirrorButton  = view.findViewById(R.id.disable_audio_mirror_button);
        resetMirrorButton.setOnClickListener(v -> handleResetAudioMirror());
        Button updateZones  = view.findViewById(R.id.update_zones_button);
        updateZones.setOnClickListener(v -> setupAudioZones());

        connectCar();

        return view;
    }

    private void handleResetAudioMirror() {
        long requestId = mMirrorIdAdapter.getItem(mMirrorIdSpinner.getSelectedItemPosition());
        handleResetMirrorForAudioZones(requestId);
    }

    private void handleEnableAudioMirror() {
        int zoneOne = mZoneOneAdapter.getItem(mZoneOneSpinner.getSelectedItemPosition());
        int zoneTwo = mZoneTwoAdapter.getItem(mZoneTwoSpinner.getSelectedItemPosition());
        handleEnableMirroringForZones(zoneOne, zoneTwo);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mIsMirrorCallbackSet) {
            mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();
        }
        mIsMirrorCallbackSet = false;
        Log.i(TAG, "onDestroyView");
    }

    private void connectCar() {
        mContext = requireContext();
        Car.createCar(mContext, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car,
                ready) -> onCarReady(car, ready));
    }

    private void onCarReady(Car car, boolean ready) {
        Log.i(TAG, String.format("connectCar ready %b", ready));
        if (!ready) {
            return;
        }

        mCarAudioManager = car.getCarManager(CarAudioManager.class);
        mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);

        if (!mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_AUDIO_MIRRORING)) {
            showToast("Audio mirror is not enable on this device");
            return;
        }

        mCarAudioManager.setAudioZoneMirrorStatusCallback(ContextCompat.getMainExecutor(
                getActivity().getApplicationContext()), mMirrorCallback);
        mIsMirrorCallbackSet = true;

        setupAudioZones();
    }

    private void setupAudioZones() {
        List<OccupantZoneInfo> occupantZones = mCarOccupantZoneManager.getAllOccupantZones();

        ArraySet<Integer> audioZones = new ArraySet<>();
        for (OccupantZoneInfo info : occupantZones) {
            int userId = mCarOccupantZoneManager.getUserForOccupant(info);
            if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
                Log.i(TAG, "setupAudioZones occupant zone " + info + " has invalid user");
                continue;
            }
            int audioZoneId = mCarOccupantZoneManager.getAudioZoneIdForOccupant(info);
            if (audioZoneId == INVALID_AUDIO_ZONE || audioZoneId == PRIMARY_AUDIO_ZONE) {
                Log.i(TAG, "setupAudioZones audio zone " + audioZoneId
                                + " for user " + userId + " not supported for audio mirror");
                continue;
            }
            audioZones.add(audioZoneId);
        }

        Integer[] zones = audioZones.toArray(Integer[]::new);
        mZoneOneAdapter = new ArrayAdapter<>(mContext, simple_spinner_item, zones);
        mZoneOneAdapter.setDropDownViewResource(simple_spinner_dropdown_item);
        mZoneOneSpinner.setAdapter(mZoneOneAdapter);
        mZoneOneSpinner.setEnabled(true);

        mZoneTwoAdapter = new ArrayAdapter<>(mContext, simple_spinner_item, zones);
        mZoneTwoAdapter.setDropDownViewResource(simple_spinner_dropdown_item);
        mZoneTwoSpinner.setAdapter(mZoneTwoAdapter);
        mZoneTwoSpinner.setEnabled(true);
    }

    private void updateMirrorIds() {
        if (mMirrorRequestIds.isEmpty()) {
            mMirrorIdSpinner.setEnabled(false);
            return;
        }

        mMirrorIdAdapter = new ArrayAdapter<>(mContext, simple_spinner_item,
                mMirrorRequestIds.toArray(Long[]::new));
        mMirrorIdAdapter.setDropDownViewResource(simple_spinner_dropdown_item);
        mMirrorIdSpinner.setAdapter(mMirrorIdAdapter);
        mMirrorIdSpinner.setEnabled(true);
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
        Log.v(TAG, "Showed toast message: " + message);
    }
}
