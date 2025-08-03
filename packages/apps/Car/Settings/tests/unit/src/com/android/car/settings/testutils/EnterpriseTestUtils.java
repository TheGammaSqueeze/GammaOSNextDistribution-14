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

package com.android.car.settings.testutils;

import android.os.UserManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Collection of helper methods for PreferenceController with restricted access.
 */
public class EnterpriseTestUtils {
    public static void mockUserRestrictionSetByUm(UserManager um, String restriction,
            boolean restricted) {
        when(um.hasBaseUserRestriction(eq(restriction), any()))
                .thenReturn(restricted);
        if (restricted) {
            // In this test infra, setting base user restriction does not automatically set
            // user restriction
            when(um.hasUserRestriction(restriction)).thenReturn(restricted);
        }
    }

    public static void mockUserRestrictionSetByDpm(UserManager um, String restriction,
            boolean restricted) {
        mockUserRestrictionSetByUm(um, restriction, false);
        when(um.hasUserRestriction(restriction)).thenReturn(restricted);
    }

    private EnterpriseTestUtils() {
        throw new AssertionError();
    }
}
