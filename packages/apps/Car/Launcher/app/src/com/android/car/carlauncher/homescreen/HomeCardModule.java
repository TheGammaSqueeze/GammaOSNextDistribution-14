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

package com.android.car.carlauncher.homescreen;

import androidx.annotation.IdRes;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.homescreen.HomeCardInterface.Presenter;
import com.android.car.carlauncher.homescreen.HomeCardInterface.View;

import java.util.List;

/**
 * A HomeCardModule creates and provides the Model-View-Presenter structure that underlies cards
 * on the home screen.
 *
 * The class should construct the {@link HomeCardInterface} View, Presenter, and Model(s) needed
 * for the card and call their appropriate set methods. For the View: {@link
 * View#setPresenter(Presenter)}. For the Presenter:
 * {@link Presenter#setView(HomeCardInterface.View)},
 * {@link Presenter#setModels(List)}
 */
public interface HomeCardModule {

    /**
     * Sets the {@link ViewModelProvider}. which provides {@link androidx.lifecycle.ViewModel}s for
     * the scope of the {@link androidx.lifecycle.ViewModelStoreOwner} specified when constructed.
     */
    void setViewModelProvider(ViewModelProvider viewModelProvider);

    /**
     * Returns the id of the container view that will hold this card.
     */
    @IdRes
    int getCardResId();

    /**
     * Returns the card's {@link Presenter}
     */
    HomeCardInterface.Presenter getCardPresenter();


    /**
     * Returns the card's {@link View}
     */
    HomeCardInterface.View getCardView();

}
