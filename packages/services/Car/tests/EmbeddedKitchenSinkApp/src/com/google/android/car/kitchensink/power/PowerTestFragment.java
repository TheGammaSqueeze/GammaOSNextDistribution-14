/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.car.kitchensink.power;

import android.car.hardware.power.CarPowerManager;
import android.car.settings.CarSettings;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.ArrayList;

public class PowerTestFragment extends Fragment {
    private static final boolean DBG = false;
    private static final String TAG = "PowerTestFragment";

    private CarPowerManager mCarPowerManager;
    private DisplayManager mDisplayManager;
    private Spinner mDisplaySpinner;
    private ViewGroup mPowerModeViewGroup;
    private SparseArray<RadioGroup> mRadioGroupList;

    private static final int MODE_OFF = 0;
    private static final int MODE_ON = 1;
    private static final int MODE_ALWAYS_ON = 2;

    private final CarPowerManager.CarPowerStateListener mPowerListener =
            (state) -> {
                Log.i(TAG, "onStateChanged() state = " + state);
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        final Runnable r = () -> {
            mCarPowerManager = ((KitchenSinkActivity) getActivity()).getPowerManager();
            try {
                mCarPowerManager.setListener(getContext().getMainExecutor(), mPowerListener);
            } catch (IllegalStateException e) {
                Log.e(TAG, "CarPowerManager listener was not cleared");
            }
        };
        ((KitchenSinkActivity) getActivity()).requestRefreshManager(r,
                new Handler(getContext().getMainLooper()));
        super.onCreate(savedInstanceState);
        mDisplayManager = getContext().getSystemService(DisplayManager.class);
        mRadioGroupList = new SparseArray<>();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCarPowerManager.clearListener();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View v = inflater.inflate(R.layout.power_test, container, false);

        Button b = v.findViewById(R.id.btnPwrRequestShutdown);
        b.setOnClickListener(this::requestShutdownBtn);

        b = v.findViewById(R.id.btnPwrShutdown);
        b.setOnClickListener(this::shutdownBtn);

        b = v.findViewById(R.id.btnPwrSleep);
        b.setOnClickListener(this::sleepBtn);

        b = v.findViewById(R.id.btnDisplayOn);
        b.setOnClickListener(this::displayOnBtn);

        b = v.findViewById(R.id.btnDisplayOff);
        b.setOnClickListener(this::displayOffBtn);

        mDisplaySpinner = v.findViewById(R.id.display_spinner);
        mPowerModeViewGroup = v.findViewById(R.id.power_mode_layout);

        if(DBG) {
            Log.d(TAG, "Starting PowerTestFragment");
        }

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mDisplaySpinner.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, getDisplays()));

        // Display power mode for each passenger display is set to {@code PowerTestFragment.MODE_ON}
        // whenever this fragment is created.
        updateRadioGroups();
        updateDisplayModeSetting();
    }

    private void requestShutdownBtn(View v) {
        mCarPowerManager.requestShutdownOnNextSuspend();
    }

    private void shutdownBtn(View v) {
        if(DBG) {
            Log.d(TAG, "Calling shutdown method");
        }
        PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        pm.shutdown(/* confirm */ false, /* reason */ null, /* wait */ false);
        Log.d(TAG, "shutdown called!");
    }

    private void sleepBtn(View v) {
        if(DBG) {
            Log.d(TAG, "Calling sleep method");
        }
        // NOTE:  This doesn't really work to sleep the device.  Actual sleep is implemented via
        //  SystemInterface via libsuspend::force_suspend()
        PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN,
                     PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
    }

    private void displayOnBtn(View v) {
        if (DBG) {
            Log.d(TAG, "Calling display on method");
        }
        int selectedDisplayId = (Integer) mDisplaySpinner.getSelectedItem();
        mCarPowerManager.setDisplayPowerState(selectedDisplayId, /* enable */ true);
    }

    private void displayOffBtn(View v) {
        if (DBG) {
            Log.d(TAG, "Calling display off method");
        }
        int selectedDisplayId = (Integer) mDisplaySpinner.getSelectedItem();
        mCarPowerManager.setDisplayPowerState(selectedDisplayId, /* enable */ false);
    }

    private void updateRadioGroups() {
        mPowerModeViewGroup.removeAllViews();
        for (Display display : mDisplayManager.getDisplays()) {
            int displayId = display.getDisplayId();
            if (!getDisplays().contains(displayId)) {
                continue;
            }
            RadioButton butnOff = new RadioButton(getContext());
            butnOff.setText("OFF");
            RadioButton btnOn = new RadioButton(getContext());
            btnOn.setText("ON");
            RadioButton btnAlwaysOn = new RadioButton(getContext());
            btnAlwaysOn.setText("ALWAYS ON");

            RadioGroup group = new RadioGroup(getContext());
            group.addView(butnOff, MODE_OFF);
            group.addView(btnOn, MODE_ON);
            group.addView(btnAlwaysOn, MODE_ALWAYS_ON);
            group.check(btnOn.getId());
            group.setOnCheckedChangeListener(mListener);

            TextView tv = new TextView(getContext());
            tv.setText("Display: " + displayId);
            mPowerModeViewGroup.addView(tv);
            mPowerModeViewGroup.addView(group);
            mRadioGroupList.put(displayId, group);
        }
    }

    private void updateDisplayModeSetting() {
        StringBuilder sb = new StringBuilder();
        int displayPort = getDisplayPort(Display.DEFAULT_DISPLAY);
        sb.append(displayPort).append(":").append(MODE_ALWAYS_ON);
        for (int i = 0; i < mRadioGroupList.size(); i++) {
            if (sb.length() != 0) {
                sb.append(",");
            }
            int displayId = mRadioGroupList.keyAt(i);
            RadioGroup group = mRadioGroupList.get(displayId);
            RadioButton btnMode = group.findViewById(group.getCheckedRadioButtonId());
            int mode = textToValue(btnMode.getText().toString());

            displayPort = getDisplayPort(displayId);
            sb.append(displayPort).append(":").append(mode);
        }
        String value = sb.toString();
        if (DBG) {
            Log.d(TAG, "Setting value to " + value);
        }
        Settings.Global.putString(getContext().getContentResolver(),
                CarSettings.Global.DISPLAY_POWER_MODE, value);
    }

    private int getDisplayPort(int displayId) {
        Display display = mDisplayManager.getDisplay(displayId);
        if (display != null) {
            DisplayAddress address = display.getAddress();
            if (address instanceof DisplayAddress.Physical) {
                DisplayAddress.Physical physicalAddress = (DisplayAddress.Physical) address;
                return physicalAddress.getPort();
            }
        }
        return Display.INVALID_DISPLAY;
    }

    private OnCheckedChangeListener mListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            updateDisplayModeSetting();
        }
    };

    DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            updateRadioGroups();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            updateRadioGroups();
        }

        @Override
        public void onDisplayChanged(int displayId) {
            // do nothing
        }
    };

    private static int textToValue(String mode) {
        switch (mode) {
            case "OFF":
                return MODE_OFF;
            case "ON":
                return MODE_ON;
            case "ALWAYS ON":
            default:
                return MODE_ALWAYS_ON;
        }
    }

    private ArrayList<Integer> getDisplays() {
        ArrayList<Integer> displayIds = new ArrayList<>();
        Display[] displays = mDisplayManager.getDisplays();
        int uidSelf = Process.myUid();
        for (Display disp : displays) {
            if (!disp.hasAccess(uidSelf)) {
                continue;
            }
            if (disp.getDisplayId() == Display.DEFAULT_DISPLAY) {
                continue;
            }
            displayIds.add(disp.getDisplayId());
        }
        return displayIds;
    }
}
