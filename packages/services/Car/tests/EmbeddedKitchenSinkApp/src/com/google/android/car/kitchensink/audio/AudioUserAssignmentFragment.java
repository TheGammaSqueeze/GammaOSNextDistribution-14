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
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_AUDIO_MIRRORING;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.media.CarAudioManager;
import android.car.media.MediaAudioRequestStatusCallback;
import android.car.media.PrimaryZoneMediaAudioRequestCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.List;

public final class AudioUserAssignmentFragment extends Fragment {

    public static final String FRAGMENT_NAME = "Play User Audio In Primary Zone";
    private static final String TAG = AudioUserAssignmentFragment.class.getSimpleName();

    private final ArrayMap<Long, OccupantZoneInfo> mRequestIdToOccupantZone =
            new ArrayMap<>();
    private Context mContext;
    private CarAudioManager mCarAudioManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private Spinner mUserSpinner;
    private ArrayAdapter<Integer> mUserAdapter;
    private Button mToggleUserAssignButton;

    private PrimaryZoneMediaAudioRequestCallback mPrimaryZoneMediaAudioRequestCallback =
            new PrimaryZoneMediaAudioRequestCallback() {
        @Override
        public void onRequestMediaOnPrimaryZone(OccupantZoneInfo info, long requestId) {
            handleRequestMediaOnPrimaryZone(info, requestId);
        }

        @Override
        public void onMediaAudioRequestStatusChanged(OccupantZoneInfo info, long requestId,
                @CarAudioManager.MediaAudioRequestStatus int status) {
            if (status == CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED) {
                handleResetMediaOnPrimaryZone(info, requestId);
            }
        }
    };

    private MediaAudioRequestStatusCallback mCallback = (info, requestId, status) -> {
        Log.d(TAG, "onMediaAudioRequestStatusChanged request " + requestId
                + " for occupant " + info + " status " + status);
        if (status == CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED) {
            handleRequestAccepted(info);
            return;
        }

        handleRequestRejected(info);
        mRequestIdToOccupantZone.remove(requestId);
    };
    private AcceptAudioDialog mAcceptDialog;
    private RadioGroup mAssignAudioRadioGroup;
    private boolean mIsPrimaryZoneMediaCallbackSet;

    private void handleResetMediaOnPrimaryZone(OccupantZoneInfo info,
            long requestId) {
        Log.v(TAG, "handleResetMediaOnPrimaryZone request " + requestId + " for occupant "
                + info + " reset");
        if (mAcceptDialog == null) {
            return;
        }
        mAcceptDialog.dismiss();
        mAcceptDialog = null;
    }

    private void handleRequestMediaOnPrimaryZone(OccupantZoneInfo info,
            long requestId) {
        Log.v(TAG, "handleRequestMediaOnPrimaryZone request " + requestId + " allowed to play");

        int currentSelectionRule = mAssignAudioRadioGroup.getCheckedRadioButtonId();
        switch (currentSelectionRule) {
            case R.id.assign_audio_reject_audio_button:
            case R.id.assign_audio_accept_audio_button:
                boolean allow = currentSelectionRule == R.id.assign_audio_accept_audio_button;
                handleAllowAudioInPrimaryZone(requestId, allow);
                return;
            case R.id.assign_audio_ask_user_button:
            default:
                // Fall through to ask user
        }

        mAcceptDialog = AcceptAudioDialog.newInstance(
                (allowed) -> handleAllowAudioInPrimaryZone(requestId, allowed), getUserName(info));
        mAcceptDialog.show(getActivity().getSupportFragmentManager(),
                "fragment_accept_audio_playback");
    }

    private String getUserName(OccupantZoneInfo info) {
        int userId = mCarOccupantZoneManager.getUserForOccupant(info);
        Context userContext = getContext()
                .createContextAsUser(UserHandle.of(userId), /* flags= */ 0);

        UserManager userManager = userContext.getSystemService(UserManager.class);
        return userManager.getUserName();
    }

    private void handleAllowAudioInPrimaryZone(long requestId, boolean allowed) {
        Log.v(TAG, "handleAllowAudioInPrimaryZone request allowed " + allowed);
        mCarAudioManager.allowMediaAudioOnPrimaryZone(requestId, allowed);
        mAcceptDialog = null;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        View view = inflater
                .inflate(R.layout.assign_user_to_primary_audio_zone, container,
                        /* attachToRoot= */ false);
        mUserSpinner = view.findViewById(R.id.user_spinner);
        mUserSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleUserSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mToggleUserAssignButton = view.findViewById(R.id.user_assign_to_main_zone_button);
        mToggleUserAssignButton.setOnClickListener(v -> handleToggleAssignUserAudio());

        Button resetUser  = view.findViewById(R.id.user_reset_from_main_zone_button);
        resetUser.setOnClickListener(v -> handleResetUserAudio());

        mAssignAudioRadioGroup = view.findViewById(R.id.assign_audio_radio_group);

        connectCar();

        return view;
    }

    private void handleResetUserAudio() {
        Log.d(TAG, "handleResetUserAudio");
        int position = mUserSpinner.getSelectedItemPosition();
        int userId = mUserAdapter.getItem(position);
        OccupantZoneInfo info = getOccupantZoneForUser(userId);

        mCarAudioManager.resetMediaAudioOnPrimaryZone(info);
    }

    private void handleToggleAssignUserAudio() {
        Log.d(TAG, "handleToggleAssignUserAudio");
        int position = mUserSpinner.getSelectedItemPosition();
        int userId = mUserAdapter.getItem(position);
        OccupantZoneInfo info = getOccupantZoneForUser(userId);

        if (mRequestIdToOccupantZone.containsValue(info)) {
            handleCancelMediaAudioOnPrimaryZone(info);
            return;
        }

        handleRequestUserToPlayInMainCabin(userId);
    }

    private void handleRequestUserToPlayInMainCabin(int userId) {
        Log.d(TAG, "requestUserToPlayInMainCabin");
        OccupantZoneInfo info = getOccupantZoneForUser(userId);
        if (info == null) {
            Log.e(TAG, "Can not find occupant zone info for user" + userId);
            showToast("User " + userId + " is not currently assigned to any occupant zone");
            return;
        }

        if (mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info)) {
            showToast("User " + userId + " is already allowed to play in primary zone");
            return;
        }

        int carAudioZoneId = mCarOccupantZoneManager.getAudioZoneIdForOccupant(info);

        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_AUDIO_MIRRORING)
                && !mCarAudioManager.getMirrorAudioZonesForAudioZone(carAudioZoneId).isEmpty()) {
            showToast("Can not enable primary zone playback as user " + userId
                    + " is currently mirroring with another zone");
            return;
        }

        requestToPlayAudioInPrimaryZone(info);
    }

    private void requestToPlayAudioInPrimaryZone(OccupantZoneInfo info) {
        Log.d(TAG, "requestUserToPlayInMainCabin occupant " + info);
        long requestId;
        try {
            requestId = mCarAudioManager.requestMediaAudioOnPrimaryZone(info,
                    ContextCompat.getMainExecutor(getActivity().getApplicationContext()),
                    mCallback);
        } catch (Exception e) {
            showToast("Error while requesting media playback: " + e.getMessage());
            return;
        }
        if (requestId == INVALID_REQUEST_ID) {
            handleRequestRejected(info);
            return;
        }

        mRequestIdToOccupantZone.put(requestId, info);
        mToggleUserAssignButton.setText(R.string.cancel_request);
    }

    private void handleRequestRejected(OccupantZoneInfo info) {
        if (!mRequestIdToOccupantZone.containsValue(info)) {
            Log.d(TAG, "handleRequestRejected info " + info + " has no pending request");
            return;
        }
        mToggleUserAssignButton.setText(R.string.assign_user);

        AudioPlaybackRequestResults dialog =
                AudioPlaybackRequestResults.newInstance(getUserName(info), /* allowed= */ false);
        dialog.show(getActivity().getSupportFragmentManager(), "rejected_results");

    }

    private void handleRequestAccepted(OccupantZoneInfo info) {
        AudioPlaybackRequestResults dialog =
                AudioPlaybackRequestResults.newInstance(getUserName(info), /* allowed= */ true);
        mToggleUserAssignButton.setText(R.string.unassign_user);
        dialog.show(getActivity().getSupportFragmentManager(), "allowed_results");
    }

    private OccupantZoneInfo getOccupantZoneForUser(int userId) {
        List<OccupantZoneInfo> occupants =
                mCarOccupantZoneManager.getAllOccupantZones();

        for (int index = 0; index < occupants.size(); index++) {
            OccupantZoneInfo info = occupants.get(index);
            int occupantZoneUser = mCarOccupantZoneManager.getUserForOccupant(info);
            if (occupantZoneUser == userId) {
                return info;
            }
        }

        return null;
    }

    private void handleCancelMediaAudioOnPrimaryZone(OccupantZoneInfo info) {
        Log.d(TAG, "handleCancelMediaAudioOnPrimaryZone");
        int index = mRequestIdToOccupantZone.indexOfValue(info);
        if (index < 0) {
            showToast("Occupant " + info + " not currently assign to play media in primary zone");
            return;
        }

        long requestId = mRequestIdToOccupantZone.keyAt(index);

        mRequestIdToOccupantZone.remove(requestId);
        boolean cancelled;
        try {
            cancelled = mCarAudioManager.cancelMediaAudioOnPrimaryZone(requestId);
        } catch (Exception e) {
            showToast("Could not cancel media on primary zone: " + e.getMessage());
            return;
        }
        if (!cancelled) {
            showToast("Could not unassigned request " + requestId + " for occupant " + info);
            return;
        }
        showToast("Unassigned request " + requestId + " for occupant " + info);
        mToggleUserAssignButton.setText(R.string.assign_user);
    }

    private void handleUserSelection() {
        Log.d(TAG, "handleUserSelection");
        int position = mUserSpinner.getSelectedItemPosition();
        int userId = mUserAdapter.getItem(position);
        Log.d(TAG, String.format("User Selected: %d", userId));

        boolean isUserAssigned =
                mCarAudioManager.isMediaAudioAllowedInPrimaryZone(getOccupantZoneForUser(userId));
        Log.d(TAG, String.format("User Selected: %d is assigned %b", userId, isUserAssigned));
        mToggleUserAssignButton.setText(isUserAssigned
                ? R.string.unassign_user : R.string.assign_user);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mIsPrimaryZoneMediaCallbackSet) {
            mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();
        }
        mIsPrimaryZoneMediaCallbackSet = false;
        Log.i(TAG, "onDestroyView");
    }

    private void connectCar() {
        mContext = getContext();
        Car.createCar(mContext, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car,
                ready) -> onCarReady(car, ready));
    }

    private void onCarReady(Car car, boolean ready) {
        Log.i(TAG, String.format("connectCar ready %b", ready));
        if (!ready) {
            showToast("Car service not ready!");
            return;
        }

        mCarAudioManager = car.getCarManager(CarAudioManager.class);
        mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);

        setUserInfo();
        setDriverOnlyAbilities();
    }

    private void setDriverOnlyAbilities() {
        if (mCarOccupantZoneManager.getMyOccupantZone().occupantType != OCCUPANT_TYPE_DRIVER) {
            mAssignAudioRadioGroup.setVisibility(View.INVISIBLE);
            return;
        }
        mIsPrimaryZoneMediaCallbackSet = mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(
                ContextCompat.getMainExecutor(getActivity().getApplicationContext()),
                mPrimaryZoneMediaAudioRequestCallback);
    }

    private void setUserInfo() {
        List<OccupantZoneInfo> occupantZones =
                mCarOccupantZoneManager.getAllOccupantZones();

        OccupantZoneInfo myInfo =
                mCarOccupantZoneManager.getMyOccupantZone();

        Log.i(TAG, String.format("setUserInfo occupant zones %d", occupantZones.size()));

        List<Integer> userList = new ArrayList<>();
        int myIndex = 0;
        int counter = 0;
        for (OccupantZoneInfo occupantZoneInfo : occupantZones) {
            int userId = mCarOccupantZoneManager.getUserForOccupant(occupantZoneInfo);
            if (userId == CarOccupantZoneManager.INVALID_USER_ID) {
                Log.i(TAG, String.format("setUserInfo occupant zone %s has invalid user",
                        occupantZoneInfo));
                continue;
            }

            // Do not include driver in the list as driver already owns the primary zone
            if (occupantZoneInfo.occupantType == OCCUPANT_TYPE_DRIVER) {
                continue;
            }
            Log.i(TAG, String.format("setUserInfo occupant zone %s has user %d",
                    occupantZoneInfo, userId));
            userList.add(userId);
            if (myInfo.equals(occupantZoneInfo)) {
                myIndex = counter;
            }
            counter++;
        }

        if (userList.isEmpty()) {
            showToast("Audio playback to primary zone is not supported on this device");
            return;
        }

        Integer[] userArray = userList.toArray(Integer[]::new);
        mUserAdapter = new ArrayAdapter<>(mContext, simple_spinner_item, userArray);
        mUserAdapter.setDropDownViewResource(simple_spinner_dropdown_item);
        mUserSpinner.setAdapter(mUserAdapter);
        mUserSpinner.setEnabled(true);
        mUserSpinner.setSelection(myIndex);
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
        Log.d(TAG, "Showed toast message: " + message);
    }
}
