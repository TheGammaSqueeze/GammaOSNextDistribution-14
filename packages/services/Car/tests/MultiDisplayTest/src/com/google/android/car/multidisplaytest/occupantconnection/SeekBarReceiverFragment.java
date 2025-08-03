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

import static com.google.android.car.multidisplaytest.occupantconnection.Constants.SEEK_BAR_RECEIVER_ID;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.occupantconnection.CarOccupantConnectionManager;
import android.car.occupantconnection.CarOccupantConnectionManager.PayloadCallback;
import android.os.Bundle;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.car.multidisplaytest.R;
import com.google.android.car.multidisplaytest.occupantconnection.PayloadProto.Data;

public class SeekBarReceiverFragment extends Fragment {

    private String mTag;
    private CarOccupantConnectionManager mOccupantConnectionManager;
    private FragmentActivity mActivity;
    private View mRoot;
    private Button mBackButton;
    private SeekBar mSeekBar;

    private final Car.CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        if (!ready) {
            Slog.e(mTag, "Disconnect from Car Service");
            mOccupantConnectionManager = null;
            return;
        }
        Slog.v(mTag, "Connected to Car Service");
        mOccupantConnectionManager = car.getCarManager(CarOccupantConnectionManager.class);
    };

    private final PayloadCallback mPayloadCallback = (senderZone, payload) -> {
        Data data = PayloadUtils.parseData(payload);
        payload.close();
        int progress = data.getProgress();
        Slog.d(mTag, "Received progress " + progress + " from " + senderZone);
        mSeekBar.setProgress(progress);
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
        mRoot = inflater.inflate(R.layout.seek_bar_receiver_fragment, container,
                /* attachToRoot= */false);
        mBackButton = mRoot.findViewById(R.id.back);
        mSeekBar = mRoot.findViewById(R.id.seek_bar);
        init();
        return mRoot;
    }

    @Override
    public void onDestroy() {
        if (mOccupantConnectionManager != null) {
            Slog.d(mTag, "unregisterReceiver(" + SEEK_BAR_RECEIVER_ID + ")");
            mOccupantConnectionManager.unregisterReceiver(SEEK_BAR_RECEIVER_ID);
        }
        super.onDestroy();
    }

    private void init() {
        mBackButton.setOnClickListener(v -> {
            Fragment occupantConnectionFragment = new OccupantConnectionFragment();
            mActivity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.menu_content, occupantConnectionFragment)
                    .commit();
        });

        if (mOccupantConnectionManager == null) {
            Toast.makeText(mActivity, "No CarOccupantConnectionManager",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Slog.d(mTag, "registerReceiver(" + SEEK_BAR_RECEIVER_ID + ")");
        mOccupantConnectionManager.registerReceiver(SEEK_BAR_RECEIVER_ID,
                mActivity.getMainExecutor(), mPayloadCallback);
    }
}
