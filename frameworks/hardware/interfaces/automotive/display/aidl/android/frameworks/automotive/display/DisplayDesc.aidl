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

package android.frameworks.automotive.display;

import android.frameworks.automotive.display.Rotation;

/**
 * Structure describing the basic properties of an EVS display
 *
 * The HAL is responsible for filling out this structure to describe
 * the EVS display. As an implementation detail, this may be a physical
 * display or a virtual display that is overlaid or mixed with another
 * presentation device.
 */
@VintfStability
parcelable DisplayDesc {
    /**
     * The width of the display in pixels
     */
    int width;
    /**
     * The height of the display in pixels
     */
    int height;
    /**
     * Z-ordered group of layers (the layer stack) currently on this display,
     * from ::android::ui::DisplayState
     */
    long layer;
    /**
     * Counterclock-wise orientation of the display
     */
    Rotation orientation;
}
