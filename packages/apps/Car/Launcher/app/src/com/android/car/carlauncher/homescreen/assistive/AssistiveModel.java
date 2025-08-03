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

package com.android.car.carlauncher.homescreen.assistive;

import android.content.Intent;

import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.CardHeader;

/**
 * An extension of {@link HomeCardInterface.Model} for assistive models.
 */
public interface AssistiveModel extends HomeCardInterface.Model {

    /**
     * Gets the {@link CardHeader} to display for the model.
     * If there is no content to display, this returns null.
     */
    CardHeader getCardHeader();

    /**
     * Gets the {@link CardContent} to display for the model
     */
    CardContent getCardContent();
    /**
     * Called by the Presenter to getIntent when the View is clicked
     */
    default Intent getIntent() {
        return null;
    }
}
