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

package com.android.car.carlauncher.homescreen.audio.dialer;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.view.Display;

import androidx.annotation.VisibleForTesting;

import com.android.car.carlauncher.homescreen.CardPresenter;
import com.android.car.carlauncher.homescreen.HomeCardFragment.OnViewClickListener;
import com.android.car.carlauncher.homescreen.HomeCardFragment.OnViewLifecycleChangeListener;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.audio.InCallModel;

import java.util.List;

/**
 * A presenter for the in-call controls.
 */
public class DialerCardPresenter extends CardPresenter {

    /** A listener to notify when an in-call state changes. */
    public interface OnInCallStateChangeListener {

        /** Notifies when an in-call state changes. */
        void onInCallStateChanged(boolean hasActiveCall);
    }

    private InCallModel mViewModel;
    private DialerCardFragment mFragment;

    @VisibleForTesting
    boolean mHasActiveCall;

    public void setOnInCallStateChangeListener(
            OnInCallStateChangeListener onInCallStateChangeListener) {
        mOnInCallStateChangeListener = onInCallStateChangeListener;
    }

    OnInCallStateChangeListener mOnInCallStateChangeListener;

    private final OnViewClickListener mOnViewClickListener =
            new OnViewClickListener() {
                @Override
                public void onViewClicked() {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                    Intent intent = mViewModel.getIntent();
                    Context context = mFragment.getContext();
                    if (context != null) {
                        context.startActivity(intent, options.toBundle());
                    }
                }
            };
    @VisibleForTesting
    final HomeCardInterface.Model.OnModelUpdateListener mOnInCallModelUpdateListener =
            new HomeCardInterface.Model.OnModelUpdateListener() {
                @Override
                public void onModelUpdate(HomeCardInterface.Model model) {
                    DialerCardModel dialerCardModel = (DialerCardModel) model;
                    if (dialerCardModel.getCardHeader() != null) {
                        mFragment.updateHeaderView(dialerCardModel.getCardHeader());
                    }
                    if (dialerCardModel.getCardContent() != null) {
                        mFragment.updateContentView(dialerCardModel.getCardContent());
                    }
                    boolean hasActiveCall = dialerCardModel.hasActiveCall();
                    if (mHasActiveCall != hasActiveCall) {
                        mHasActiveCall = hasActiveCall;
                        mOnInCallStateChangeListener.onInCallStateChanged(hasActiveCall);
                    }
                }
            };

    private final OnViewLifecycleChangeListener mOnInCallViewLifecycleChangeListener =
            new OnViewLifecycleChangeListener() {
                @Override
                public void onViewCreated() {
                    mViewModel.setOnModelUpdateListener(mOnInCallModelUpdateListener);
                    mViewModel.onCreate(mFragment.requireContext());
                }

                @Override
                public void onViewDestroyed() {
                    mViewModel.onDestroy(getFragment().requireContext());
                }
            };

    // Deprecated. Use setModel instead.
    @Override
    public void setModels(List<HomeCardInterface.Model> models) {
        // No-op
    }

    public void setModel(InCallModel viewModel) {
        mViewModel = viewModel;
    }

    @Override
    public void setView(HomeCardInterface.View view) {
        super.setView(view);
        mFragment = (DialerCardFragment) view;
        mFragment.setOnViewLifecycleChangeListener(mOnInCallViewLifecycleChangeListener);
        mFragment.setOnViewClickListener(mOnViewClickListener);
    }
}
