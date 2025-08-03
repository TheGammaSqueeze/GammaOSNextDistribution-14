/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.kitchensink.touch;

import static android.view.WindowManager.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class InjectMotionTestFragment extends Fragment {
    private static final String TAG = InjectMotionTestFragment.class.getSimpleName();

    private static final String SHELL_CMD = "sh";
    private static final String PREFIX_INJECTING_MOTION_CMD = "cmd car_service inject-motion";
    private static final String OPTION_SEAT = " -s ";
    private static final String OPTION_ACTION = " -a ";
    private static final String OPTION_COUNT = " -c ";
    private static final String OPTION_POINTER_ID = " -p";

    // Only half of {@link MotionEvent#ACTION_MOVE} events are sampled to reduce latency.
    // Events can be delayed with shell commands using {@link Runtime#exec()}. Depending on hardware
    // specifications, on some devices sampling only half of the events may still cause a delay.
    private static final int TOUCH_SAMPLING_RATE = 2; // 50%

    private java.lang.Process mShellProcess;
    private DataOutputStream mOutStreamForShell;

    private DisplayManager mDisplayManager;
    private Display mCurrentDisplay;
    private Display mTargetDisplay;

    private boolean mIsSelected;

    private Spinner mDisplaySpinner;
    private Spinner mSamplingRateSpinner;
    private CarOccupantZoneManager mOccupantZoneManager;
    private WindowManager mWindowManager;
    private ViewGroup mTouchPointView;
    private int mCountOfMoveEvent = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisplayManager = getContext().getSystemService(DisplayManager.class);
        mCurrentDisplay = getContext().getDisplay();

        final Runnable r = () -> {
            mOccupantZoneManager = ((KitchenSinkActivity) getActivity()).getOccupantZoneManager();
        };
        ((KitchenSinkActivity) getActivity()).requestRefreshManager(r,
                new Handler(getContext().getMainLooper()));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.injecting_touch_fragment, container, false);
        mDisplaySpinner = view.findViewById(R.id.display_select_spinner);
        mTouchPointView = (ViewGroup) LayoutInflater.from(getContext())
                .inflate(R.layout.injecting_touch_point_view, /* root= */ null);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mDisplaySpinner.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, getDisplays()));
        view.findViewById(R.id.select_start_button).setOnClickListener((v) -> startInjecting());
        view.findViewById(R.id.select_stop_button).setOnClickListener((v) -> stopInjecting());
        view.findViewById(R.id.touch_point_view).setOnTouchListener(this::onTouchView);
        updateUi();
    }

    public boolean onTouchView(View v, MotionEvent event) {
        if (shouldDropEvent(event)) {
            return true;
        }

        return injectMotionByShell(event);
    }

    @Override
    public void onDestroyView() {
        removeTouchPointView();
        mTouchPointView = null;
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        if (mIsSelected) {
            stopInjecting();
        }
        super.onPause();
    }

    private boolean shouldDropEvent(MotionEvent event) {
        if (!mIsSelected) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCountOfMoveEvent = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                mCountOfMoveEvent++;
                if (mCountOfMoveEvent == Integer.MAX_VALUE) {
                    mCountOfMoveEvent = 1;
                }
                if (mCountOfMoveEvent % TOUCH_SAMPLING_RATE != 0) {
                    return true;
                }
                break;
            default:
                break;
        }

        return false;
    }

    private boolean injectMotionByShell(MotionEvent event) {
        if (!mIsSelected) {
            return false;
        }
        if (mTargetDisplay == null) {
            return false;
        }
        if (mShellProcess == null || mOutStreamForShell == null) {
            return false;
        }

        OccupantZoneInfo zone = getOccupantZoneForDisplayId(
                mTargetDisplay.getDisplayId());
        if (zone != null) {
            // use raw screen X and Y coordinates instead of window coordinates.
            float deltaX = event.getRawX() - event.getX();
            float deltaY = event.getRawY() - event.getY();

            // generate a command message
            StringBuilder sb = new StringBuilder()
                    .append(PREFIX_INJECTING_MOTION_CMD)
                    .append(OPTION_SEAT)
                    .append(zone.seat)
                    .append(OPTION_ACTION)
                    .append(event.getAction())
                    .append(OPTION_COUNT)
                    .append(event.getPointerCount());
            sb.append(OPTION_POINTER_ID);
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointerId = event.getPointerId(i);
                sb.append(' ');
                sb.append(pointerId);
            }
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointerId = event.getPointerId(i);
                int pointerIndex = event.findPointerIndex(pointerId);
                float x = event.getX(pointerIndex) + deltaX;
                float y = event.getY(pointerIndex) + deltaY;
                sb.append(' ');
                sb.append(x);
                sb.append(' ');
                sb.append(y);
            }
            sb.append('\n');

            try {
                // send the command to shell
                mOutStreamForShell.writeBytes(sb.toString());
                mOutStreamForShell.flush();
            } catch (Exception e) {
                Log.e(TAG, "Cannot flush", e);
            }
        }
        return true;
    }

    private void openShellSession() {
        try {
            mShellProcess = Runtime.getRuntime().exec(SHELL_CMD);
            mOutStreamForShell = new DataOutputStream(mShellProcess.getOutputStream());
        } catch (Exception e) {
            Log.e(TAG, "Cannot execute shell", e);
            mShellProcess = null;
            mOutStreamForShell = null;
        }
    }

    private void closeShellSession() {
        if (mShellProcess == null) {
            return;
        }

        try {
            mShellProcess.getErrorStream().close();
            mShellProcess.getInputStream().close();
            mShellProcess.getOutputStream().close();
            mShellProcess.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Cannot close streams", e);
        } finally {
            mShellProcess = null;
            mOutStreamForShell = null;
        }
    }

    // It is used to find a seat for the display id.
    private OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        List<OccupantZoneInfo> zones = mOccupantZoneManager.getAllOccupantZones();
        for (OccupantZoneInfo zone : zones) {
            List<Display> displays = mOccupantZoneManager.getAllDisplaysForOccupant(zone);
            for (Display disp : displays) {
                if (disp.getDisplayId() == displayId) {
                    return zone;
                }
            }
        }
        return null;
    }

    /**
     * Start selecting the target display for test.
     */
    private void startInjecting() {
        int selectedDisplayId = (Integer) mDisplaySpinner.getSelectedItem();
        mTargetDisplay = mDisplayManager.getDisplay(selectedDisplayId);
        showTouchPointView(mTargetDisplay);
        mIsSelected = true;
        openShellSession();
        updateUi();
    }

    /**
     * Stop selecting  the target display for test.
     */
    private void stopInjecting() {
        removeTouchPointView();
        mTargetDisplay = null;
        mIsSelected = false;
        closeShellSession();
        updateUi();
    }

    /**
     * Update a touch point view and control buttons UI.
     */
    private void updateUi() {
        getView().findViewById(R.id.touch_point_view).setVisibility(
                mIsSelected ? View.VISIBLE : View.INVISIBLE);
        getView().findViewById(R.id.select_start_button).setEnabled(!mIsSelected);
        mDisplaySpinner.setEnabled(!mIsSelected);
        getView().findViewById(R.id.select_stop_button).setEnabled(mIsSelected);
    }

    /**
     * Shows a touch point view on the specified display.
     */
    private void showTouchPointView(Display display) {
        final int displayId = display.getDisplayId();
        if (mWindowManager != null) {
            Log.w(TAG, "Active window exists on Display #" + displayId + ".");
            return;
        }
        mWindowManager = getContext().createWindowContext(display, TYPE_APPLICATION_OVERLAY,
                /* options= */ null).getSystemService(WindowManager.class);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT,
                TYPE_APPLICATION_OVERLAY,
                /* flags= */ 0,
                PixelFormat.RGBA_8888);
        mWindowManager.addView(mTouchPointView, lp);
    }

    /**
     * Removes a touch point view on the specified display.
     */
    private void removeTouchPointView() {
        if (mWindowManager == null) {
            return;
        }
        mWindowManager.removeView(mTouchPointView);
        mWindowManager = null;
    }

    private ArrayList<Integer> getDisplays() {
        ArrayList<Integer> displayIds = new ArrayList<>();
        Display[] displays = mDisplayManager.getDisplays();
        int uidSelf = Process.myUid();
        for (Display disp : displays) {
            if (!disp.hasAccess(uidSelf)) {
                Log.d(TAG, "Cannot access the display: displayId=" + disp.getDisplayId());
                continue;
            }
            if (mCurrentDisplay != null && disp.getDisplayId() == mCurrentDisplay.getDisplayId()) {
                // skip the current display
                continue;
            }
            displayIds.add(disp.getDisplayId());
        }
        return displayIds;
    }
}
