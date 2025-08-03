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

import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.homescreen.audio.dialer.DialerCardPresenter;
import com.android.car.carlauncher.homescreen.audio.media.MediaCardPresenter;

/**
 * The Audio card module. This class initializes the necessary components to present an audio card
 * as a home module.
 */
public class AudioCardModule implements HomeCardModule {
    private AudioCardPresenter mAudioCardPresenter;
    private AudioCardFragment mAudioCardView;
    private ViewModelProvider mViewModelProvider;
    @Override
    public void setViewModelProvider(ViewModelProvider viewModelProvider) {
        if (mViewModelProvider != null) {
            throw new IllegalStateException("Cannot reset the view model provider");
        }
        mViewModelProvider = viewModelProvider;

        mAudioCardPresenter = new AudioCardPresenter(
                new DialerCardPresenter(), new MediaCardPresenter());
        mAudioCardPresenter.setModel(new AudioCardModel(mViewModelProvider));
        mAudioCardView = new AudioCardFragment();
        mAudioCardPresenter.setView(mAudioCardView);
    }

    @Override
    public int getCardResId() {
        return R.id.bottom_card;
    }

    @Override
    public HomeCardInterface.Presenter getCardPresenter() {
        return mAudioCardPresenter;
    }

    @Override
    public HomeCardInterface.View getCardView() {
        return mAudioCardView;
    }
}
