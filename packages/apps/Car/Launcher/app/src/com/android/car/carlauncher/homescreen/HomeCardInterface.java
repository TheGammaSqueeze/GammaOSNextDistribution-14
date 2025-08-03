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

import android.content.Context;

import androidx.fragment.app.Fragment;

import java.util.List;

/**
 * Defines the interfaces for a card on the home app.
 * The cards follow a Model-View-Presenter architectural design pattern to separate functionality
 * into discrete components to be extensible and easily testable.
 *
 * The layout of a card is distinguished into two parts:
 * (1) Header - the source of the card's data. This is the source app's name and app icon.
 * (2) Content - the data itself. This could include text, an image, etc.
 */
public interface HomeCardInterface {

    /**
     * The View is what the user interacts with.
     *
     * The methods that the View exposes will be called by its Presenter. The View should
     * only be responsible for providing a UI; the logic for determining the card's layout and
     * content is handled by the presenter.
     */
    interface View {

        /**
         * Called by the Presenter to remove the entire card from view if there is no data to
         * display.
         */
        void hideCard();

        /**
         * Returns the {@link Fragment} with which the View is associated.
         */
        Fragment getFragment();
    }

    /**
     * The Presenter connects the View to the Model.
     *
     * It accesses and formats the data from a Model and updates the View to display the data
     */
    interface Presenter {

        /**
         * Sets the {@link View}, which is the card's UI that the Presenter will update.
         */
        void setView(View view);

        /**
         * Sets the list of {@link Model} that the Presenter will use as sources of content.
         */
        void setModels(List<Model> models);
    }

    /**
     * The Model defines the data to be displayed in a card on the home screen.
     *
     * The card's header is distinguished from the body as the body may update more frequently.
     * For example, as a user listens to media from a single app, the header (source app)
     * remains the same while the body (song title) changes.
     */
    interface Model {

        /**
         * Interface definition for a callback to be invoked for when model has updates.
         */
        interface OnModelUpdateListener {
            /**
             * Called when model is updated.
             */
            void onModelUpdate(HomeCardInterface.Model model);
        }

        /**
         * Registers OnModelUpdateListener on the model.
         */
        void setOnModelUpdateListener(OnModelUpdateListener onModelUpdateListener);

        /**
         * Called by the Presenter to create the Model when the View is created.
         * Should be called after the Model's Presenter has been set with setPresenter
         */
        default void onCreate(Context context) {
        }

        /**
         * Called by the Presenter to destroy the Model when the View is destroyed
         */
        default void onDestroy(Context context) {
        }
    }
}
