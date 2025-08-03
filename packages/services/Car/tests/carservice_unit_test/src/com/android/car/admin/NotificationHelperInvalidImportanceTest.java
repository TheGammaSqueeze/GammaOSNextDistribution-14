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
package com.android.car.admin;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_MAX;
import static android.app.NotificationManager.IMPORTANCE_MIN;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationManager.IMPORTANCE_UNSPECIFIED;

import static com.android.car.admin.NotificationHelper.newNotificationBuilder;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class NotificationHelperInvalidImportanceTest {

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private Context mContext;

    private final int mImportance;

    public NotificationHelperInvalidImportanceTest(int importance) {
        mImportance = importance;
    }

    @Test
    public void testNewNotificationBuilder() {
        assertThrows(IllegalArgumentException.class,
                () -> newNotificationBuilder(mContext, mImportance));
    }

    @Parameterized.Parameters
    public static Collection<Integer> provideParams() {
        return Arrays.asList(
                IMPORTANCE_LOW,
                IMPORTANCE_MAX,
                IMPORTANCE_MIN,
                IMPORTANCE_NONE,
                IMPORTANCE_UNSPECIFIED,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);
    }
}
