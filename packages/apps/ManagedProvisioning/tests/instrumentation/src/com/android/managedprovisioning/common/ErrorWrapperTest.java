/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ErrorWrapperTest {
    private static final int DIALOG_TITLE_ID = 1;
    private static final int ERROR_MESSAGE_ID = 2;
    private static final boolean IS_FACTORY_RESET_REQUIRED = true;

    @Test
    public void constructor_works() {
        ErrorWrapper errorWrapper = new ErrorWrapper(
                DIALOG_TITLE_ID, ERROR_MESSAGE_ID, IS_FACTORY_RESET_REQUIRED);

        assertThat(errorWrapper.dialogTitleId).isEqualTo(DIALOG_TITLE_ID);
        assertThat(errorWrapper.errorMessageResId).isEqualTo(ERROR_MESSAGE_ID);
        assertThat(errorWrapper.factoryResetRequired).isEqualTo(IS_FACTORY_RESET_REQUIRED);
    }
}
