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
#ifndef EXYNOS_HWC3_TYPES_H_
#define EXYNOS_HWC3_TYPES_H_

enum class HwcMountOrientation {
    ROT_0 = 0,
    ROT_90,
    ROT_180,
    ROT_270,
};

enum class HwcDimmingStage {
    DIMMING_NONE = 0,
    DIMMING_LINEAR,
    DIMMING_OETF,
};

#endif  // EXYNOS_HWC3_TYPES_H_
