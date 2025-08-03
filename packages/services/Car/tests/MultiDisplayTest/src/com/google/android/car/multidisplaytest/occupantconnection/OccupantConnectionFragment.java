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

package com.google.android.car.multidisplaytest.occupantconnection;

import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_INSTALLED;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_IN_FOREGROUND;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_RUNNING;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_SAME_LONG_VERSION;
import static android.car.CarRemoteDeviceManager.FLAG_CLIENT_SAME_SIGNATURE;
import static android.car.CarRemoteDeviceManager.FLAG_OCCUPANT_ZONE_CONNECTION_READY;
import static android.car.CarRemoteDeviceManager.FLAG_OCCUPANT_ZONE_POWER_ON;
import static android.car.CarRemoteDeviceManager.FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.car.CarRemoteDeviceManager.StateCallback;
import android.graphics.Color;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.car.multidisplaytest.R;

import java.util.ArrayList;
import java.util.List;

public class OccupantConnectionFragment extends Fragment {

    private static final int INITIAL_ZONE_STATE = 0;
    private static final int INITIAL_APP_STATE = 0;

    // The member variables are accessed by the main thread only, so there is no multi-thread issue.
    private final ArrayMap<OccupantZoneInfo, Integer> mOccupantZoneStateMap = new ArrayMap<>();
    private final ArrayMap<OccupantZoneInfo, Integer> mAppStateMap = new ArrayMap<>();
    private final List<OccupantZoneInfo> mAddedPeerZones = new ArrayList<>();

    private String mTag;
    private CarRemoteDeviceManager mRemoteDeviceManager;
    private FragmentActivity mActivity;
    private ViewGroup mPeerZonesLayout;
    private boolean mIsDiscovering;

    private final Car.CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        if (!ready) {
            Slog.e(mTag, "Disconnect from Car Service");
            mRemoteDeviceManager = null;
            return;
        }
        Slog.v(mTag, "Connected to Car Service");
        mRemoteDeviceManager = car.getCarManager(CarRemoteDeviceManager.class);
    };

    private final StateCallback mStateCallback = new StateCallback() {
        @Override
        public void onOccupantZoneStateChanged(
                @androidx.annotation.NonNull OccupantZoneInfo occupantZone,
                int occupantZoneStates) {
            Slog.d(mTag, "onOccupantZoneStateChanged: occupantZone=" + occupantZone
                            + ", occupantZoneStates=" + occupantZoneStates);
            mOccupantZoneStateMap.put(occupantZone, occupantZoneStates);
            if (mAddedPeerZones.contains(occupantZone)) {
                updateOccupantZoneState(occupantZone, occupantZoneStates);
                return;
            }
            addPeerOccupantZoneItem(occupantZone, occupantZoneStates, INITIAL_ZONE_STATE);
            mAddedPeerZones.add(occupantZone);
        }

        @Override
        public void onAppStateChanged(
                @androidx.annotation.NonNull OccupantZoneInfo occupantZone,
                int appStates) {
            Slog.d(mTag, "onAppStateChanged: occupantZone=" + occupantZone
                    + ", appStates=" + appStates);
            mAppStateMap.put(occupantZone, appStates);

            if (!mAddedPeerZones.contains(occupantZone)) {
                // onOccupantZoneStateChanged() is guaranteed to be invoked before
                // onAppStateChanged() for a given occupant zone, so this must be a bug when it
                // happens.
                throw new IllegalStateException("onOccupantZoneStateChanged() should have been "
                        + "invoked for " + occupantZone + " already");
            }
            updateAppState(occupantZone, appStates);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Car.createCar(getContext(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mCarServiceLifecycleListener);
        mActivity = getActivity();
        mTag = "OccupantConnection##" + mActivity.getUserId();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(
                R.layout.occupant_connection_fragment, container, /* attachToRoot= */false);
        mPeerZonesLayout = root.findViewById(R.id.peer_zones);

        Button registerStateCallbackButton = root.findViewById(R.id.register_state_callback);
        registerStateCallbackButton.setOnClickListener(v -> {
            if (mIsDiscovering) {
                Toast.makeText(mActivity, "It is discovering already", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isRemoteDeviceManagerReady()) return;
            mRemoteDeviceManager.registerStateCallback(mActivity.getMainExecutor(), mStateCallback);
            Slog.d(mTag, "Start discovery");
            mIsDiscovering = true;
        });

        Button unregisterStateCallbackButton = root.findViewById(R.id.unregister_state_callback);
        unregisterStateCallbackButton.setOnClickListener(v -> {
            if (!mIsDiscovering) {
                Toast.makeText(mActivity, "It is not discovering", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isRemoteDeviceManagerReady()) return;
            stopDiscovery();
        });

        Button textReceiverButton = root.findViewById(R.id.text_receiver);
        textReceiverButton.setOnClickListener(v -> {
            Fragment textReceiverFragment = new TextReceiverFragment();
            mActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.menu_content, textReceiverFragment)
                    .commit();
        });

        Button seekBarReceiverButton = root.findViewById(R.id.progress_bar_receiver);
        seekBarReceiverButton.setOnClickListener(v -> {
            Fragment seekBarReceiverFragment = new SeekBarReceiverFragment();
            mActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.menu_content, seekBarReceiverFragment)
                    .commit();
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        stopDiscovery();
        super.onDestroyView();
    }

    private void addPeerOccupantZoneItem(OccupantZoneInfo zone, int occupantZoneStates,
            int appStates) {
        Button powerOnButton = new Button(mActivity);
        powerOnButton.setText("Power on");
        powerOnButton.setOnClickListener(v -> {
            if (!isRemoteDeviceManagerReady()) return;
            if (mRemoteDeviceManager.isOccupantZonePowerOn(zone)) {
                Toast.makeText(mActivity, "The occupant zone is already powered on",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mRemoteDeviceManager.setOccupantZonePower(zone, true);
            Slog.d(mTag, "Power on " + zone);
        });

        Button powerOffButton = new Button(mActivity);
        powerOffButton.setText("Power off");
        powerOffButton.setOnClickListener(v -> {
            if (!isRemoteDeviceManagerReady()) return;
            if (!mRemoteDeviceManager.isOccupantZonePowerOn(zone)) {
                Toast.makeText(mActivity, "The occupant zone is already powered off",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mRemoteDeviceManager.setOccupantZonePower(zone, false);
            Slog.d(mTag, "Power off " + zone);
        });

        Button connectButton = new Button(mActivity);
        connectButton.setText("Connect");
        connectButton.setOnClickListener(v -> {
            int currentZoneState = mOccupantZoneStateMap.getOrDefault(zone, INITIAL_ZONE_STATE);
            int currentAppState = mAppStateMap.getOrDefault(zone, INITIAL_APP_STATE);
            if (checkConnectionError(currentZoneState, currentAppState)) {
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.KEY_OCCUPANT_ZONE, zone);
            Fragment senderFragment = new SenderFragment();
            senderFragment.setArguments(bundle);
            mActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.menu_content, senderFragment)
                    .commit();
        });

        LinearLayout buttonParent = new LinearLayout(mActivity);
        buttonParent.addView(powerOnButton);
        buttonParent.addView(powerOffButton);
        buttonParent.addView(connectButton);

        TextView stateView = new TextView(mActivity);
        setStateView(stateView, zone, occupantZoneStates, appStates);

        LinearLayout zoneLayout = new LinearLayout(mActivity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 20, 0, 20);
        zoneLayout.setLayoutParams(params);
        zoneLayout.setBackgroundColor(Color.BLACK);
        zoneLayout.setOrientation(LinearLayout.VERTICAL);
        zoneLayout.addView(buttonParent);
        zoneLayout.addView(stateView);
        zoneLayout.setTag(zone.zoneId);

        mPeerZonesLayout.addView(zoneLayout);
    }

    private void updateOccupantZoneState(OccupantZoneInfo zone, int occupantZoneStates) {
        TextView stateView = getStateViewForOccupantZone(zone);
        int appStates = mAppStateMap.getOrDefault(zone, INITIAL_APP_STATE);
        setStateView(stateView, zone, occupantZoneStates, appStates);
    }

    private void updateAppState(OccupantZoneInfo zone, int appStates) {
        TextView stateView = getStateViewForOccupantZone(zone);
        int zoneStates = mOccupantZoneStateMap.getOrDefault(zone, INITIAL_ZONE_STATE);
        setStateView(stateView, zone, zoneStates, appStates);
    }

    private TextView getStateViewForOccupantZone(OccupantZoneInfo zone) {
        for (int i = 0; i < mPeerZonesLayout.getChildCount(); i++) {
            ViewGroup zoneLayout = (ViewGroup) mPeerZonesLayout.getChildAt(i);
            Integer zoneId = (Integer) zoneLayout.getTag();
            if (zoneId.intValue() == zone.zoneId) {
                return (TextView) zoneLayout.getChildAt(1);
            }
        }
        throw new IllegalArgumentException("The TextView for " + zone + " was not added before");
    }

    private static void setStateView(TextView textView, OccupantZoneInfo zone,
            int occupantZoneStates, int appStates) {
        StringBuilder builder = new StringBuilder()
                .append(zone)
                .append((occupantZoneStates & FLAG_OCCUPANT_ZONE_POWER_ON) != 0
                        ? ":\nscreens on, " : ":\nscreens off, ")
                .append((occupantZoneStates & FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED) != 0
                        ? "main screen unlocked, " : "main screen locked, ")
                .append((occupantZoneStates & FLAG_OCCUPANT_ZONE_CONNECTION_READY) != 0
                        ? "connection ready\n" : "connection not ready\n");

        if ((occupantZoneStates & FLAG_OCCUPANT_ZONE_CONNECTION_READY) == 0) {
            builder.append("app state unknown");
        } else if ((appStates & FLAG_CLIENT_INSTALLED) == 0) {
            builder.append("app not installed");
        } else {
            builder.append((appStates & FLAG_CLIENT_SAME_LONG_VERSION) != 0
                    ? "same version " : "different long version ");
            builder.append((appStates & FLAG_CLIENT_SAME_SIGNATURE) != 0
                    ? "same signature app " : "different signature app ");
            if ((appStates & FLAG_CLIENT_RUNNING) == 0) {
                builder.append("installed but not running");
            } else {
                builder.append((appStates & FLAG_CLIENT_IN_FOREGROUND) != 0
                        ? "running in foreground" : "running in background");
            }
        }
        String text = builder.toString();
        textView.setText(text);
    }

    /** Checks connection error and returns true if there is connection error. */
    private boolean checkConnectionError(int occupantZoneStates, int appStates) {
        String connectionError = getConnectionError(occupantZoneStates, appStates);
        if (connectionError != null) {
            Toast.makeText(mActivity,
                    "Can't connect to the occupant zone because " + connectionError,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private static String getConnectionError(int occupantZoneStates, int appStates) {
        if ((occupantZoneStates & FLAG_OCCUPANT_ZONE_POWER_ON) == 0) {
            // Note: if you don't need user confirmation to establish the connection, just skip this
            // block and next block, and change ReceiverService#NEEDS_USER_APPROVAL to be false.
            return "it is not powered on";
        }
        if ((occupantZoneStates & FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED) == 0) {
            // TODO(b/277234550): ignore screen locked state for now since it is not implemented
            //  yet.
            // Note: if you don't need user confirmation to establish the connection, just skip this
            // block and the previous block, and change ReceiverService#NEEDS_USER_APPROVAL to be
            // false.
            // return "its main screen is locked";
        }
        if ((occupantZoneStates & FLAG_OCCUPANT_ZONE_CONNECTION_READY) == 0) {
            return "it is not ready for connection";
        }
        if ((appStates & FLAG_CLIENT_INSTALLED) == 0) {
            return "the peer app is not installed";
        }
        if ((appStates & FLAG_CLIENT_SAME_LONG_VERSION) == 0) {
            return "the peer app has a different long version";
        }
        if ((appStates & FLAG_CLIENT_SAME_SIGNATURE) == 0) {
            return "the peer app has a different signature";
        }
        if ((appStates & FLAG_CLIENT_RUNNING) == 0) {
            // Note: if you want the sender to connect to the receiver even if the receiver is not
            // running, just skip this block.
            return "the peer app is not running";
        }
        if ((appStates & FLAG_CLIENT_IN_FOREGROUND) == 0) {
            // Note: if you want the sender to connect to the receiver even if the receiver is not
            // running in the foreground, just skip this block.
            return "the peer app is running in background";
        }
        return null;
    }

    private boolean isRemoteDeviceManagerReady() {
        if (mRemoteDeviceManager != null) return true;
        Toast.makeText(mActivity, "No CarRemoteDeviceManager", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void stopDiscovery() {
        if (mIsDiscovering) {
            mRemoteDeviceManager.unregisterStateCallback();
            Slog.d(mTag, "Discovery has stopped");
            mIsDiscovering = false;
        }
    }
}
