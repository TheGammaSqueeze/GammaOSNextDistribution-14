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

package com.android.managedprovisioning.provisioning;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationState;

/**
 * An {@link ViewModel} which maintains data related to provisioning.
 */
public class ProvisioningViewModel extends ViewModel {

    private TransitionAnimationState mTransitionAnimationState;

    /**
     * Saves the state of the transition animation.
     *
     * @see #restoreTransitionAnimationState()
     */
    public void saveTransitionAnimationState(TransitionAnimationState state) {
        mTransitionAnimationState = state;
    }

    /**
     * Returns the state that was saved via {@link
     * #saveTransitionAnimationState(TransitionAnimationState)}.
     */
    public TransitionAnimationState restoreTransitionAnimationState() {
        return mTransitionAnimationState;
    }

    static class ProvisioningViewModelFactory implements ViewModelProvider.Factory {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (!ProvisioningViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException(
                        "Invalid class for creating a " + "PreProvisioningViewModel: "
                                + modelClass);
            }
            return (T) new ProvisioningViewModel();
        }
    }
}
