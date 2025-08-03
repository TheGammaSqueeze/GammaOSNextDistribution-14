/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.emergency.action;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.emergency.action.broadcast.EmergencyActionBroadcastReceiver;
import com.android.emergency.action.sensoryfeedback.EmergencyActionAlarmHelper;
import com.android.emergency.action.service.EmergencyActionForegroundService;
import com.android.emergency.widgets.countdown.CountDownAnimationView;
import com.android.emergency.widgets.slider.OnSlideCompleteListener;
import com.android.emergency.widgets.slider.SliderView;
import com.android.settingslib.emergencynumber.EmergencyNumberUtils;

import java.time.Duration;

public class EmergencyActionFragment extends Fragment implements OnSlideCompleteListener {

    private static final String TAG = "EmergencyActionFrag";
    private static final String STATE_MILLIS_LEFT = "STATE_MILLIS_LEFT";

    private EmergencyActionAlarmHelper mEmergencyActionAlarmHelper;
    private CountDownTimer mCountDownTimer;
    private EmergencyNumberUtils mEmergencyNumberUtils;
    private long mCountDownMillisLeft;

    private boolean mCountdownCancelled;
    private boolean mCountdownFinished;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EmergencyActionForegroundService.stopService(context);
        mEmergencyActionAlarmHelper = new EmergencyActionAlarmHelper(context);
        mEmergencyNumberUtils = new EmergencyNumberUtils(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        // Ignore the larger font scale if users set it in general system settings since we already
        // have relatively large font size on this page, and we need to display all content on one
        // page without scrolling.
        Configuration configuration = getResources().getConfiguration();
        if (configuration.fontScale > 1) {
            configuration.fontScale = (float) 1;

            DisplayMetrics metrics = new DisplayMetrics();
            metrics.scaledDensity = configuration.fontScale * metrics.density;
            configuration.densityDpi = (int) getResources().getDisplayMetrics().xdpi;
        }

        View view = inflater.cloneInContext(getContext().createConfigurationContext(configuration))
                .inflate(R.layout.emergency_action_fragment, container, false);

        TextView subtitleView = view.findViewById(R.id.subtitle);
        subtitleView.setText(getString(R.string.emergency_action_subtitle,
                mEmergencyNumberUtils.getPoliceNumber()));

        SliderView cancelButton = view.findViewById(R.id.btn_cancel);
        cancelButton.setSlideCompleteListener(this);

        if (savedInstanceState != null) {
            mCountDownMillisLeft = savedInstanceState.getLong(STATE_MILLIS_LEFT);
        } else {
            Activity activity = getActivity();
            Intent intent = null;
            if (activity != null) {
                intent = activity.getIntent();
            }
            if (intent != null) {
                mCountDownMillisLeft = intent.getLongExtra(STATE_MILLIS_LEFT,
                        getResources().getInteger(R.integer.emergency_action_count_down_millis));
            } else {
                mCountDownMillisLeft =
                        getResources().getInteger(R.integer.emergency_action_count_down_millis);
            }
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        startTimer();
        mEmergencyActionAlarmHelper.playWarningSound();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_MILLIS_LEFT, mCountDownMillisLeft);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mCountDownTimer != null) {
            CountDownAnimationView countDownAnimationView =
                    getView().findViewById(R.id.count_down_view);
            countDownAnimationView.stop();
            mCountDownTimer.cancel();
        }

        mEmergencyActionAlarmHelper.stopWarningSound();
        if (!mCountdownCancelled && !mCountdownFinished) {
            Log.d(TAG,
                    "Emergency countdown UI dismissed without being cancelled/finished, "
                            + "continuing countdown in background");

            Context context = getContext();
            context.startService(
                    EmergencyActionForegroundService.newStartCountdownIntent(
                            context,
                            mCountDownMillisLeft,
                            mEmergencyActionAlarmHelper.getUserSetAlarmVolume()));
        }
    }

    @Override
    public void onSlideComplete() {
        mCountdownCancelled = true;
        EmergencyActionForegroundService.stopService(getActivity());
        getActivity().finish();
    }

    private void startTimer() {
        CountDownAnimationView countDownAnimationView =
                getView().findViewById(R.id.count_down_view);

        if (mCountDownTimer != null) {
            countDownAnimationView.stop();
            mCountDownTimer.cancel();
        }

        mCountDownTimer =
                new CountDownTimer(
                        mCountDownMillisLeft,
                        getResources().getInteger(R.integer.emergency_action_count_down_interval)) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        CountDownAnimationView countDownAnimationView =
                                getView().findViewById(R.id.count_down_view);
                        if (countDownAnimationView != null) {
                            countDownAnimationView.setCountDownLeft(
                                    Duration.ofMillis(millisUntilFinished));
                        }

                        mCountDownMillisLeft = millisUntilFinished;
                    }

                    @Override
                    public void onFinish() {
                        mCountdownFinished = true;
                        Intent broadcast =
                                EmergencyActionBroadcastReceiver.newCallEmergencyIntent(
                                        getContext());
                        Activity activity = getActivity();
                        activity.sendBroadcast(broadcast);
                        activity.finish();
                    }
                };

        mCountDownTimer.start();

        countDownAnimationView.start(Duration.ofMillis(mCountDownMillisLeft));
        countDownAnimationView.showCountDown();
    }
}
