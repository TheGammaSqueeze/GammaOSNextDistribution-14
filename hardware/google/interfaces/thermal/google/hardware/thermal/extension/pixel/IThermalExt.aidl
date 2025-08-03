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

package google.hardware.thermal.extension.pixel;

/* @hide */
@VintfStability
interface IThermalExt {
    /**
     * setThermalMode() is called to enable/disable a thermal mode. It will update
     * the mode for multiple related temperature types together.
     *
     * A particular platform may choose to ignore any mode hint.
     *
     * @param mode which is to be enable/disable.
     * @param enabled true to enable, false to disable the mode.
     */
    oneway void setThermalMode(in @utf8InCpp String mode, in boolean enabled);

    /**
     * isThermalModeSupported() checks whether a thermal mode is supported by vendor.
     *
     * @return true if the hint passed is supported on this platform.
     *         If false, setting the mode will have no effect.
     * @param mode to be queried
     */
    boolean isThermalModeSupported(in @utf8InCpp String mode);
}
