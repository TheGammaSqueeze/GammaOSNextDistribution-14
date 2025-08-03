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
package com.android.adservices.shared.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public final class ApplicationContextSingletonTest {

    @Rule public final MockitoRule mRule = MockitoJUnit.rule();

    @Mock private Context mContext;
    @Mock private Context mOtherContext;
    @Mock private Context mAppContext;
    @Mock private Context mOtherAppContext;

    @Before
    @After
    public void resetState() {
        ApplicationContextSingleton.setForTests(/* context= */ null);
    }

    @Test
    public void testGet_notSet() {
        assertThrows(IllegalStateException.class, () -> ApplicationContextSingleton.get());
        assertWithMessage("getForTests()").that(ApplicationContextSingleton.getForTests()).isNull();
    }

    @Test
    public void testSet_nullContext() {
        assertThrows(NullPointerException.class, () -> ApplicationContextSingleton.set(null));
    }

    @Test
    public void testSet_nullAppContext() {
        mockAppContext(mContext, /* appContext= */ null);

        assertThrows(
                IllegalArgumentException.class, () -> ApplicationContextSingleton.set(mContext));
    }

    @Test
    public void testSet_once() {
        mockAppContext(mContext, mAppContext);

        ApplicationContextSingleton.set(mContext);

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mAppContext);
    }

    @Test
    public void testSet_twiceSameAppContext() {
        mockAppContext(mContext, mAppContext);
        mockAppContext(mOtherContext, mAppContext);

        ApplicationContextSingleton.set(mContext);
        ApplicationContextSingleton.set(mOtherContext);

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mAppContext);
    }

    @Test
    public void testSet_twiceDifferentAppContexts() {
        mockAppContext(mContext, mAppContext);
        mockAppContext(mOtherContext, mOtherAppContext);

        ApplicationContextSingleton.set(mContext);
        assertThrows(
                IllegalStateException.class, () -> ApplicationContextSingleton.set(mOtherContext));

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mAppContext);
    }

    @Test
    public void testSetForTests() {
        ApplicationContextSingleton.setForTests(mContext);

        assertWithMessage("get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mContext);
    }

    static void mockAppContext(Context context, Context appContext) {
        when(context.getApplicationContext()).thenReturn(appContext);
    }
}
