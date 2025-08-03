/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.tv.display;

import android.annotation.StringRes;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.View;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TestSequence;
import com.android.cts.verifier.tv.TestStepBase;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Test when the HDMI cable on HDMI source devices (e.g. set-top boxes and TV sticks) is plugged out
 * and in {@link android.hardware.display.DisplayManager.DisplayListener#onDisplayChanged}. The
 * test also verifies that the display capabilities when the HDMI cable is unplugged contain only
 * one display mode and no HDR capabilities.
 */
public class HotplugTestActivity extends TvAppVerifierActivity {
    private static final float REFRESH_RATE_TOLERANCE = 0.01f;
    private TestSequence mTestSequence;

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_hotplug_test, R.string.tv_hotplug_test_info, -1);
    }

    @Override
    protected void createTestItems() {
        List<TestStepBase> testSteps = new ArrayList<>();
        testSteps.add(new NoDisplayTestStep(this));
        mTestSequence = new TestSequence(this, testSteps);
        mTestSequence.init();
    }

    @Override
    public String getTestDetails() {
        return mTestSequence.getFailureDetails();
    }

    private static class DisplayCapabilities {
        public Display.Mode[] modes;
        public Display.HdrCapabilities hdrCapabilities;

        DisplayCapabilities(Display display) {
            this.modes = display.getSupportedModes();
            this.hdrCapabilities = display.getHdrCapabilities();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DisplayCapabilities)) return false;
            DisplayCapabilities that = (DisplayCapabilities) o;
            return modesAreEqual(modes, that.modes)
                    && Objects.equals(hdrCapabilities, that.hdrCapabilities);
        }

        private boolean modesAreEqual(Display.Mode[] left, Display.Mode[] right) {
            if (left.length != right.length) {
                return false;
            }

            for (int i = 0; i < left.length; i++) {
                if (!modesAreEqual(left[i], right[i])) {
                    return false;
                }
            }

            return  true;
        }

        private boolean modesAreEqual(Display.Mode left, Display.Mode right) {
            // Compare all properties except the ID.
            return left.getPhysicalHeight() == right.getPhysicalHeight()
                    && left.getPhysicalWidth() == right.getPhysicalWidth()
                    && Math.abs(left.getRefreshRate() - right.getRefreshRate())
                            < REFRESH_RATE_TOLERANCE
                    && Arrays.equals(
                            left.getAlternativeRefreshRates(), right.getAlternativeRefreshRates());
        }

        @Override
        public String toString() {
            String modesStr = Arrays.stream(modes)
                    .map(Display.Mode::toString)
                    .collect(Collectors.joining("\n\t"));
            return "DisplayCapabilities{"
                    + "modes=" + modesStr
                    + ", hdrCapabilities=" + hdrCapabilities + '}';
        }
    }

    private static class NoDisplayTestStep extends AsyncTestStep implements
            DisplayManager.DisplayListener {
        // All recorded states of the display capabilities. The first element are the initial
        // display capabilities; each subsequent element is the state after a received
        // onDisplayChanged().
        private List<DisplayCapabilities> mRecordedCapabilities = new ArrayList<>();

        private int mNumOnDisplayChanged = 0;
        private boolean mDisconnectDetected = false;
        private boolean mConnectDetected = false;

        private View mDoneButtonView;
        private DisplayManager mDisplayManager;

        NoDisplayTestStep(TvAppVerifierActivity context) {
            super(
                    context,
                    R.string.tv_hotplug_test,
                    getInstructionText(context),
                    getButtonStringId());
        }

        private static String getInstructionText(Context context) {
            return context.getString(
                    R.string.tv_hotplug_disconnect_display,
                    context.getString(getButtonStringId()));
        }

        @StringRes
        private static int getButtonStringId() {
            return R.string.tv_start_test;
        }

        @Override
        public List<View> createUiElements() {
            List<View> list = super.createUiElements();
            mDoneButtonView = TvAppVerifierActivity.createButtonItem(
                    mContext.getLayoutInflater(),
                    null,
                    R.string.tv_done,
                    (View view) -> recordTestStateAndFinish());
            list.add(mDoneButtonView);
            return list;
        }

        @Override
        public void runTestAsync() {
            setButtonEnabled(mDoneButtonView, true);

            mDisplayManager = mContext.getSystemService(DisplayManager.class);
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            mRecordedCapabilities.add(new DisplayCapabilities(display));
            mDisplayManager.registerDisplayListener(this, null);
        }

        @Override
        public void onDisplayAdded(int displayId) {
            getAsserter().withMessage("onDisplayAdded() is not expected").fail();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            getAsserter().withMessage("onDisplayRemoved() is not expected").fail();
        }

        @Override
        public void onDisplayChanged(int displayId) {
            getAsserter().that(displayId).isEqualTo(Display.DEFAULT_DISPLAY);

            mNumOnDisplayChanged++;
            DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            DisplayCapabilities currCapabilities = new DisplayCapabilities(display);
            mRecordedCapabilities.add(currCapabilities);

            // Some TVs may send multiple onDisplayChanged() events, for that reason we
            // test that in the sequence of states after each onDisplayChanged() there's
            // a state which looks like a disconnected display, followed by a state which looks
            // like the initial state.
            if (!mDisconnectDetected) {
                boolean isDisconnectedDisplay =
                        display.getHdrCapabilities().getSupportedHdrTypes().length == 0
                                && display.getSupportedModes().length == 1;

                if (isDisconnectedDisplay) {
                    mDisconnectDetected = true;
                }
            } else if (!mConnectDetected) {
                DisplayCapabilities initialCapabilities = mRecordedCapabilities.get(0);
                if (currCapabilities.equals(initialCapabilities)) {
                    mConnectDetected = true;
                    recordTestStateAndFinish();
                }
            }
        }

        private void recordTestStateAndFinish() {
            setButtonEnabled(mDoneButtonView, false);
            mDisplayManager.unregisterDisplayListener(this);
            if (!mConnectDetected || !mDisconnectDetected) {
                String recordedCapabilitiesStr = mRecordedCapabilities.stream()
                        .map(DisplayCapabilities::toString)
                        .collect(Collectors.joining("\n"));
                String message = "Number of onDisplayChanged() events = " + mNumOnDisplayChanged
                        + "\n" + "Disconnect detected = " + mDisconnectDetected + "\n"
                        + "Recorded states = " + recordedCapabilitiesStr;
                getAsserter().withMessage(message).fail();
            }
            done();
        }
    }
}
