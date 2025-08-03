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

package android.car.builtin.annotation;

import android.annotation.SystemApi;

/**
 * Platform version values to be used by {@code android.car.builtin} and
 * {@code car-frameworks-service}
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
@SuppressWarnings("Enum")
public enum PlatformVersion {
    TIRAMISU_0,
    TIRAMISU_1,
    TIRAMISU_2,
    TIRAMISU_3,
    UPSIDE_DOWN_CAKE_0,
    UPSIDE_DOWN_CAKE_1,
    // DO NOT ADD minor UPSIDE_DOWN_CAKE version until lint tool is working. (b/275125924)
    VANILLA_ICE_CREAM_0,
}
