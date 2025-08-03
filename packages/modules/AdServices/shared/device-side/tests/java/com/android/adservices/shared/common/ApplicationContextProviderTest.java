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

import static com.android.adservices.shared.common.ApplicationContextSingletonTest.mockAppContext;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.pm.ProviderInfo;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public final class ApplicationContextProviderTest {

    @Rule public final MockitoRule mRule = MockitoJUnit.rule();

    private final ApplicationContextProvider mProvider = new ApplicationContextProvider();

    @Mock private Context mContext;
    @Mock private Context mAppContext;

    @Test
    public void testContextProviderApiMethods() {
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mProvider.query(
                                /* uri= */ null,
                                /* projection= */ null,
                                /* selection= */ null,
                                /* selectionArgs= */ null,
                                /* sortOrder= */ null));
        assertThrows(UnsupportedOperationException.class, () -> mProvider.getType(/* uri= */ null));
        assertThrows(
                UnsupportedOperationException.class,
                () -> mProvider.insert(/* uri= */ null, /* values= */ null));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mProvider.delete(
                                /* uri= */ null, /* selection= */ null, /* selectionArgs= */ null));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mProvider.update(
                                /* uri= */ null,
                                /* values= */ null,
                                /* selection= */ null,
                                /* selectionArgs= */ null));
    }

    @Test
    public void testOnCreate_setsSingleton() {
        ApplicationContextSingleton.setForTests(/* context= */ null);
        mockAppContext(mContext, mAppContext);
        mProvider.attachInfo(mContext, new ProviderInfo());

        assertWithMessage("result of onCreate()").that(mProvider.onCreate()).isTrue();

        assertWithMessage("ApplicationContextSingleton.get()")
                .that(ApplicationContextSingleton.get())
                .isSameInstanceAs(mAppContext);
    }
}
