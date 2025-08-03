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

package android.tools.traces.surfaceflinger

enum class HwcCompositionType(val value: Int) {
    HWC_TYPE_UNSPECIFIED(0),
    HWC_TYPE_CLIENT(1),
    HWC_TYPE_DEVICE(2),
    HWC_TYPE_SOLID_COLOR(3),
    HWC_TYPE_CURSOR(4),
    HWC_TYPE_SIDEBAND(5),
    HWC_TYPE_UNRECOGNIZED(-1)
}
