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

package com.android.car.carlauncher.homescreen.assistive;

import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.CardPresenter;
import com.android.car.carlauncher.homescreen.HomeCardFragment;
import com.android.car.carlauncher.homescreen.HomeCardModule;

import java.util.Arrays;
import java.util.Collections;

/**
 * Home screen card that displays general assistive content including projection status and
 * static weather data.
 */
public class AssistiveCardModule implements HomeCardModule {

    private ViewModelProvider mViewModelProvider;
    private AssistiveCardPresenter mAssistiveCardPresenter;
    private HomeCardFragment mAssistiveCardView;

    @Override
    public void setViewModelProvider(ViewModelProvider viewModelProvider) {
        mViewModelProvider = viewModelProvider;
    }

    @Override
    public int getCardResId() {
        return R.id.top_card;
    }

    @Override
    public CardPresenter getCardPresenter() {
        if (mAssistiveCardPresenter == null) {
            mAssistiveCardPresenter = new AssistiveCardPresenter();
            mAssistiveCardPresenter.setModels(
                    Collections.unmodifiableList(
                            Arrays.asList(new ProjectionModel(), new FakeWeatherModel())));
        }
        return mAssistiveCardPresenter;
    }

    @Override
    public HomeCardFragment getCardView() {
        if (mAssistiveCardView == null) {
            mAssistiveCardView = new HomeCardFragment();
            getCardPresenter().setView(mAssistiveCardView);
        }
        return mAssistiveCardView;
    }
}
