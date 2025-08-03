/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlauncher.homescreen.audio;

import com.android.car.carlauncher.homescreen.CardPresenter;
import com.android.car.carlauncher.homescreen.HomeCardFragment;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.audio.dialer.DialerCardPresenter;
import com.android.car.carlauncher.homescreen.audio.media.MediaCardPresenter;

import java.util.List;

/**
 * Presenter used to coordinate the binding between the audio card model and presentation
 */
public class AudioCardPresenter extends CardPresenter {

    // Presenter for the dialer card
    private final DialerCardPresenter mDialerPresenter;

    // Presenter for the media card
    private final MediaCardPresenter mMediaPresenter;

    // The fragment controlled by this presenter.
    private AudioCardFragment mFragment;

    private final HomeCardFragment.OnViewLifecycleChangeListener mOnViewLifecycleChangeListener =
            new HomeCardFragment.OnViewLifecycleChangeListener() {
                @Override
                public void onViewCreated() {
                    mDialerPresenter.setView(mFragment.getInCallFragment());
                    mMediaPresenter.setView(mFragment.getMediaFragment());
                }

                @Override
                public void onViewDestroyed() {
                }
            };

    public AudioCardPresenter(DialerCardPresenter dialerPresenter,
            MediaCardPresenter mediaPresenter) {
        mDialerPresenter = dialerPresenter;
        mMediaPresenter = mediaPresenter;

        mDialerPresenter.setOnInCallStateChangeListener(hasActiveCall -> {
            if (hasActiveCall) {
                mMediaPresenter.setShowMedia(false);
                mFragment.showInCallCard();
            } else {
                mMediaPresenter.setShowMedia(true);
                mFragment.showMediaCard();
            }
        });
    }


    // Deprecated. Use setModel instead.
    @Override
    public void setModels(List<HomeCardInterface.Model> models) {
        // No-op
    }

    /** Sets the model for this presenter. */
    public void setModel(AudioCardModel viewModel) {
        mDialerPresenter.setModel(viewModel.getInCallViewModel());
        mMediaPresenter.setModel(viewModel.getMediaViewModel());
    }

    @Override
    public void setView(HomeCardInterface.View view) {
        super.setView(view);
        mFragment = (AudioCardFragment) view;
        mFragment.setOnViewLifecycleChangeListener(mOnViewLifecycleChangeListener);
    }
}
