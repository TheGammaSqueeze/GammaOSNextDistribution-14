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

package com.android.car.carlauncher.calmmode;

import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.R;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackViewModel;

public final class CalmModeFragment extends Fragment {
    private static final String TAG = CalmModeFragment.class.getSimpleName();
    private static final boolean DEBUG = Build.isDebuggable();
    private View mContainerView;
    private Group mNavGroup;
    private Group mTemperatureGroup;
    private TextView mMediaTitleView;
    private TextView mNavStateView;
    private TextView mTemperatureView;
    private TextView mClockView;
    private TextView mDateView;
    private ViewModelProvider mViewModelProvider;
    private TemperatureViewModel mTemperatureViewModel;
    private LiveData<TemperatureData> mTemperatureData;
    private PlaybackViewModel mPlaybackViewModel;
    @Nullable
    private NavigationStateViewModel mNavigationStateViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (DEBUG) {
            Log.v(TAG, "onCreateView");
        }

        View rootView = inflater.inflate(R.layout.calm_mode_fragment, container, false);
        mViewModelProvider = new ViewModelProvider(getViewModelStore(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()));

        mContainerView = rootView.findViewById(R.id.calm_mode_container);
        mClockView = rootView.findViewById(R.id.clock);
        mDateView = rootView.findViewById(R.id.date);
        mNavGroup = rootView.findViewById(R.id.nav_group);
        mNavStateView = rootView.findViewById(R.id.nav_state);
        mTemperatureGroup = rootView.findViewById(R.id.temperature_group);
        mTemperatureView = rootView.findViewById(R.id.temperature);
        mMediaTitleView = rootView.findViewById(R.id.media_title);

        initExitOnClick();
        initClock();
        initDate();
        initNavState();
        initTemperature();
        initMediaTitle();

        return rootView;
    }

    private void initMediaTitle() {
        if (DEBUG) {
            Log.v(TAG, "initMediaTitle()");
        }
        if (shouldShowMedia()) {
            mPlaybackViewModel = PlaybackViewModel.get(requireActivity().getApplication(),
                    MEDIA_SOURCE_MODE_PLAYBACK);
            mPlaybackViewModel.getMetadata().observe(this, this::updateMediaTitle);
        }
    }

    private void initTemperature() {
        if (DEBUG) {
            Log.v(TAG, "initTemperature()");
        }
        if (shouldShowTemperature()) {
            mTemperatureViewModel = mViewModelProvider.get(TemperatureViewModel.class);
            mTemperatureData = mTemperatureViewModel.getTemperatureData();
            mTemperatureData.observe(getViewLifecycleOwner(), this::updateTemperatureData);
        }
    }

    private void initDate() {
        if (DEBUG) {
            Log.v(TAG, "initDate()");
        }
        if (shouldShowDate()) {
            mDateView.setVisibility(View.VISIBLE);
        }
    }

    private void initClock() {
        if (DEBUG) {
            Log.v(TAG, "initClock()");
        }
        if (shouldShowClock()) {
            mClockView.setVisibility(View.VISIBLE);
        }
    }

    private void initExitOnClick() {
        if (DEBUG) {
            Log.v(TAG, "initExitOnTouch()");
        }
        mContainerView.setOnClickListener((view) -> {
            if (DEBUG) {
                Log.v(TAG, "Detected touch, exiting Calm mode");
            }
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    private void initNavState() {
        if (DEBUG) {
            Log.v(TAG, "initNavState()");
        }

        if (shouldShowNavigation()) {
            mNavigationStateViewModel = mViewModelProvider.get(NavigationStateViewModel.class);
            mNavigationStateViewModel
                    .getNavigationState()
                    .observe(this, this::updateNavigationState);
        }
    }

    private boolean shouldShowMedia() {
        return getResources().getBoolean(R.bool.config_calmMode_showMedia);
    }

    private boolean shouldShowTemperature() {
        return getResources().getBoolean(R.bool.config_calmMode_showTemperature);
    }

    private boolean shouldShowClock() {
        return getResources().getBoolean(R.bool.config_calmMode_showClock);
    }

    private boolean shouldShowDate() {
        return getResources().getBoolean(R.bool.config_calmMode_showDate);
    }

    private boolean shouldShowNavigation() {
        return getResources().getBoolean(R.bool.config_calmMode_showNavigation);
    }

    private void updateTemperatureData(@Nullable TemperatureData temperatureData) {
        if (temperatureData == null) {
            mTemperatureGroup.setVisibility(View.GONE);
            mTemperatureView.setText("");
            return;
        }
        mTemperatureGroup.setVisibility(View.VISIBLE);
        mTemperatureView.setText(
                TemperatureData.buildTemperatureString(
                        temperatureData, getResources().getConfiguration().getLocales().get(0),
                        /* showUnit = */ false));
    }

    private void updateNavigationState(NavigationStateData navState) {
        if (DEBUG) {
            Log.v(TAG, "updateNavigationState navState = " + navState);
        }

        if (navState == null) {
            mNavGroup.setVisibility(View.GONE);
            mNavStateView.setText("");
            return;
        }

        mNavGroup.setVisibility(View.VISIBLE);
        mNavStateView.setText(
                NavigationStateData.buildTripStatusString(navState,
                        getResources().getConfiguration().getLocales().get(0),
                        getResources().getString(R.string.calm_mode_separator)));
    }

    @VisibleForTesting
    void updateMediaTitle(MediaItemMetadata mediaItemMetadata) {
        if (DEBUG) {
            Log.v(TAG, "updateMediaTitle mediaItemMetadata = " + mediaItemMetadata);
        }
        if (mediaItemMetadata == null || mediaItemMetadata.getTitle() == null
                || mediaItemMetadata.getTitle().length() == 0) {
            mMediaTitleView.setVisibility(View.GONE);
            mClockView.setTranslationY(0f);
            mMediaTitleView.setText("");
            return;
        }

        StringBuilder medaTitleBuilder = new StringBuilder();
        medaTitleBuilder.append(mediaItemMetadata.getTitle());

        if (mediaItemMetadata.getSubtitle() != null
                && mediaItemMetadata.getSubtitle().length() > 0) {
            medaTitleBuilder.append(getResources().getString(R.string.calm_mode_separator));
            medaTitleBuilder.append(mediaItemMetadata.getSubtitle());
        }

        mMediaTitleView.setVisibility(View.VISIBLE);
        mClockView.setTranslationY(
                -getResources().getDimension(R.dimen.calm_mode_clock_translationY));
        mContainerView.requestLayout();
        mMediaTitleView.setText(medaTitleBuilder.toString());
    }

    @Override
    public void onStart() {
        super.onStart();
        int launchType = requireActivity().getIntent().getIntExtra(
                CalmModeStatsLogHelper.INTENT_EXTRA_CALM_MODE_LAUNCH_TYPE,
                CalmModeStatsLogHelper.CalmModeLaunchType.UNSPECIFIED_LAUNCH_TYPE);
        CalmModeStatsLogHelper.getInstance().logSessionStarted(launchType);
    }

    @Override
    public void onStop() {
        super.onStop();
        CalmModeStatsLogHelper.getInstance().logSessionFinished();
    }

}
