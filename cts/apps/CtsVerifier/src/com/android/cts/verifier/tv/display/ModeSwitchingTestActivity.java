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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.cts.MediaCodecTunneledPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.android.compatibility.common.util.DisplayUtil;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TestStepBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test activity to verify that
 *   1. switching display modes during video playback doesn't cause crashes or major regressions
 *      in video quality
 *   2. switching between modes is seamless if and only if the modes have alternative refresh rates
 *          (see {@link Display.Mode#getAlternativeRefreshRates})
 */
public class ModeSwitchingTestActivity extends PassFailButtons.Activity {
    private static final String TAG = ModeSwitchingTestActivity.class.getSimpleName();
    private static final float REFRESH_RATE_TOLERANCE = 0.01f;

    private SurfaceHolder mHolder;
    private int mAudioSessionId = 0;
    private MediaCodecTunneledPlayer mPlayer;

    private ViewGroup mItemList;
    private List<TestStepBase> mSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_instructions_and_video);
        mItemList = findViewById(R.id.test_items);

        DisplayManager displayManager = getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        List<Display.Mode> modeList = findTestModes(display);

        Display.Mode lastMode = display.getMode();
        mSteps = new ArrayList<>();
        for (Display.Mode mode : modeList) {
            boolean isSeamlessSwitch = DisplayUtil.isModeSwitchSeamless(lastMode, mode);
            mSteps.addAll(createTestStepsForTransition(lastMode, mode, isSeamlessSwitch));
            lastMode = mode;
        }

        if (!mSteps.isEmpty()) {
            initTestStep(0);
        }

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        SurfaceView surfaceView = findViewById(R.id.surface);
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                playVideo();
            }

            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });

        AudioManager am = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        mAudioSessionId = am.generateAudioSessionId();

        mPlayer = new MediaCodecTunneledPlayer(this, mHolder, true, mAudioSessionId);

        Uri mediaUri = getAsResourceUri(R.raw.bunny_1280x720_24fps_VP9);
        mPlayer.setVideoDataSource(mediaUri, null);
        mPlayer.setAudioDataSource(mediaUri, null);
    }

    private Uri getAsResourceUri(int resId) {
        Resources res = getResources();
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(res.getResourcePackageName(resId))
                .appendPath(res.getResourceTypeName(resId))
                .appendPath(res.getResourceEntryName(resId))
                .build();
    }

    private List<Display.Mode> findTestModes(Display display) {
        Display.Mode activeMode = display.getMode();

        List<Display.Mode> modeList = new ArrayList<>();
        // Find a mode to test refresh rate switch - the mode with smallest refresh rate and
        // the same resolution as the active mode
        Arrays.stream(display.getSupportedModes())
                .filter(mode -> !mode.equals(activeMode))
                .filter(mode -> isResolutionEqual(mode, activeMode))
                .min(Comparator.comparingDouble(Display.Mode::getRefreshRate))
                .ifPresent(modeList::add);

        // Find a mode to test seamless mode switch.
        if (modeList.size() > 0 && !DisplayUtil.isModeSwitchSeamless(activeMode, modeList.get(0))
                && activeMode.getAlternativeRefreshRates().length > 0) {
            Arrays.stream(display.getSupportedModes())
                    .filter(mode -> !mode.equals(activeMode))
                    .filter(mode -> DisplayUtil.isModeSwitchSeamless(activeMode, mode))
                    .findFirst()
                    .ifPresent(modeList::add);
        }

        // Find a mode to test resolution switch - the first 16:9 mode with resolution less
        // than the active mode.
        Arrays.stream(display.getSupportedModes())
                .filter(mode -> !mode.equals(activeMode))
                .filter(mode -> !isResolutionEqual(mode, activeMode))
                .filter(this::is16to9)
                .max(Comparator.comparing(Display.Mode::getPhysicalHeight))
                .ifPresent(modeList::add);

        return modeList;
    }

    private void initTestStep(int stepIndex) {
        final TestStepBase currStep = mSteps.get(stepIndex);
        currStep.createUiElements()
                .forEach(view -> mItemList.addView(view));

        currStep.setOnDoneListener(() -> {
            // Delay so the user can see the passing state of the finished
            // step, before replacing it with the next one
            mItemList.postDelayed(() -> {
                int nextStepIndex = stepIndex + 1;
                if (!currStep.hasPassed() || nextStepIndex >= mSteps.size()) {
                    onAllStepsDone();
                    return;
                }

                mItemList.removeAllViews();
                initTestStep(nextStepIndex);
            }, 500);
        });

        currStep.enableInteractivity();
    }

    private void onAllStepsDone() {
        // The sequence passes if all containing test steps pass.
        boolean allTestStepsPass = mSteps.stream().allMatch(step -> step.hasPassed());
        getPassButton().setEnabled(allTestStepsPass);
    }

    private List<TestStepBase> createTestStepsForTransition(
            Display.Mode currMode, Display.Mode nextMode, boolean isSeamless) {
        List<TestStepBase> steps = new ArrayList<>();
        steps.add(new ModeSwitchStep(this,
                currMode,
                nextMode
                ));

        String blackScreenQuestion =
                getResources().getString(R.string.tv_mode_switching_test_black_screen_question);
        if (isSeamless) {
            steps.add(new YesNoTestStep(this, blackScreenQuestion,
                    /* passingButton */ R.string.tv_no, /* failingButton */ R.string.tv_yes));
        } else {
            steps.add(new YesNoTestStep(this, blackScreenQuestion,
                    /* passingButton */ R.string.tv_yes, /* failingButton */ R.string.tv_no));
        }

        steps.add(new YesNoTestStep(this,
                getResources().getString(R.string.tv_mode_switching_test_is_video_ok),
                R.string.tv_yes, R.string.tv_no));
        return steps;

    }

    private boolean isResolutionEqual(Display.Mode left, Display.Mode right) {
        return left.getPhysicalHeight() == right.getPhysicalHeight()
                && left.getPhysicalWidth() == right.getPhysicalWidth();
    }

    private boolean is16to9(Display.Mode mode) {
        return mode.getPhysicalHeight() * 16 == mode.getPhysicalWidth() * 9;
    }

    private void playVideo() {
        try {
            mPlayer.prepare();
            mPlayer.setLoopEnabled(true);
            mPlayer.startCodec();
            mPlayer.play();
        } catch (Exception e) {
            Log.d(TAG, "Could not play video", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPlayer != null) {
            mPlayer.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mPlayer.reset();
            mPlayer = null;
        }
    }

    @Override
    public String getTestDetails() {
        return mSteps.stream()
                .filter(step -> !step.hasPassed())
                .map(step -> step.getFailureDetails())
                .collect(Collectors.joining("\n"));
    }

    // Test step which shows instruction text to the user and performs a mode switch. This
    // test step will always pass successfully.
    private static class ModeSwitchStep extends SyncTestStep {
        private Display.Mode mNextMode;

        ModeSwitchStep(Activity context, Display.Mode currMode, Display.Mode nextMode) {
            super(context, R.string.tv_mode_switching_test_mode_switch_step,
                    getInstructionText(context, currMode, nextMode),
                    R.string.tv_start_test);
            this.mNextMode = nextMode;
        }

        private static String getInstructionText(Context context, Display.Mode currMode,
                Display.Mode nextMode) {
            return context.getResources().getString(
                    R.string.tv_mode_switching_test_step_instruction,
                    formatMode(currMode),
                    formatMode(nextMode));
        }

        private static String formatMode(Display.Mode mode) {
            return String.format("%dx%d %.2fHz",
                    mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());
        }

        @Override
        public void runTest() {
            Window window = mContext.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredDisplayModeId = mNextMode.getModeId();
            window.setAttributes(params);
        }
    }
}
