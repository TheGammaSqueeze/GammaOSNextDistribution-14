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

package com.android.bedstead.harrier;


import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.annotations.BeforeClass;

/**
 * This is a parent class used for testing test inheritance.
 *
 * <p>Test inheritance is not recommended when using Bedstead but should be supported.
 */
public class DeviceStateTestParent extends DeviceStateTestGrandparent {

    public static boolean sParentBeforeClassHasRun = false;

    @BeforeClass
    public static void parentBeforeClass() {
        assertThat(sShadowedGrandparentBeforeClassHasRun).isFalse();
        assertThat(sShadowedGrandparentWithoutBeforeClassHasRun).isTrue();
        assertThat(sGrandparentBeforeClassHasRun).isTrue();

        sParentBeforeClassHasRun = true;
    }
}
