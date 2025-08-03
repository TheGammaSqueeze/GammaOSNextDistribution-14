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


import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationState;

import org.junit.Test;

@SmallTest
public class ProvisioningViewModelTest {
    private final ProvisioningViewModel mViewModel = new ProvisioningViewModel();

    @Test
    public void setTransitionAnimationState_works() {
        TransitionAnimationState originalState = createTransitionState();
        mViewModel.saveTransitionAnimationState(originalState);

        TransitionAnimationState restoredState = mViewModel.restoreTransitionAnimationState();

        assertThat(restoredState).isEqualTo(originalState);
    }

    @Test
    public void restoreTransitionAnimationState_nullByDefault() {
        TransitionAnimationState restoredState = mViewModel.restoreTransitionAnimationState();

        assertThat(restoredState).isNull();
    }

    private TransitionAnimationState createTransitionState() {
        return new TransitionAnimationState(/* animationIndex= */ 2, /* progress= */
                0.5f, /* lastTransitionTimestamp= */ 1234);
    }
}
