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

package com.android.adservices.data.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public final class BooleanFileDatastoreTest {
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String FILENAME = "BooleanFileDatastoreTest.xml";
    private static final int DATASTORE_VERSION = 1;

    private final BooleanFileDatastore mDatastore =
            new BooleanFileDatastore(PPAPI_CONTEXT, FILENAME, DATASTORE_VERSION);

    @Before
    public void initializeDatastore() throws IOException {
        mDatastore.initialize();
    }

    @After
    public void cleanupDatastore() {
        mDatastore.tearDownForTesting();
    }

    @Test
    public void testGetVersionKey() {
        assertWithMessage("getVersionKey()")
                .that(mDatastore.getVersionKey())
                .isEqualTo(BooleanFileDatastore.VERSION_KEY);
    }

    @Test
    public void testConstructor_emptyOrNullArgs() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new BooleanFileDatastore(
                                /* adServicesContext= */ null, FILENAME, DATASTORE_VERSION));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BooleanFileDatastore(
                                PPAPI_CONTEXT, /* filename= */ null, DATASTORE_VERSION));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BooleanFileDatastore(
                                PPAPI_CONTEXT, /* filename= */ "", DATASTORE_VERSION));
    }
}
