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

package com.android.server.adservices.rollback;

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.server.adservices.rollback.RollbackHandlingManager.DUMP_PREFIX;
import static com.android.server.adservices.rollback.RollbackHandlingManager.STORAGE_XML_IDENTIFIER;
import static com.android.server.adservices.rollback.RollbackHandlingManager.VERSION_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.adservices.AdServicesManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.storage.BooleanFileDatastore;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;

public class RollbackHandlingManagerTest {

    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();

    private static final int DATASTORE_VERSION = 339900900;

    private BooleanFileDatastore mDatastore;

    @Before
    public void setup() {
        mDatastore =
                new BooleanFileDatastore(
                        PPAPI_CONTEXT.getFilesDir().getAbsolutePath(),
                        STORAGE_XML_IDENTIFIER,
                        DATASTORE_VERSION,
                        VERSION_KEY);
    }

    @After
    public void tearDown() {
        mDatastore.tearDownForTesting();
    }

    @Test
    public void testGetRollbackHandlingDataStoreDir() throws IOException {
        // The Datastore is in the directory with the following format.
        // /data/system/adservices/user_id/rollback/

        MockitoSession staticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(Files.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        ExtendedMockito.doReturn(true).when(() -> Files.exists(any()));

        assertThat(
                        RollbackHandlingDatastoreLocationHelper
                                .getRollbackHandlingDataStoreDirAndCreateDir(
                                        /* baseDir */ "/data/system/adservices",
                                        /* userIdentifier */ 0))
                .isEqualTo("/data/system/adservices/0/rollback");

        assertThat(
                        RollbackHandlingDatastoreLocationHelper
                                .getRollbackHandlingDataStoreDirAndCreateDir(
                                        /* baseDir */ "/data/system/adservices",
                                        /* userIdentifier */ 1))
                .isEqualTo("/data/system/adservices/1/rollback");

        assertThrows(
                NullPointerException.class,
                () ->
                        RollbackHandlingDatastoreLocationHelper
                                .getRollbackHandlingDataStoreDirAndCreateDir(null, 0));

        staticMockSession.finishMocking();
    }

    @Test
    public void testGetOrCreateBooleanFileDatastore() throws IOException {
        RollbackHandlingManager rollbackHandlingManager =
                RollbackHandlingManager.createRollbackHandlingManager(
                        BASE_DIR, /* userIdentifier */ 0, DATASTORE_VERSION);
        BooleanFileDatastore datastore =
                rollbackHandlingManager.getOrCreateBooleanFileDatastore(
                        AdServicesManager.MEASUREMENT_DELETION);

        // Assert that the DataStore is created.
        assertThat(datastore).isNotNull();
    }

    @Test
    public void testRecordMeasurementDeletionOccurred() throws IOException {
        RollbackHandlingManager rollbackHandlingManager =
                RollbackHandlingManager.createRollbackHandlingManager(
                        BASE_DIR, /* userIdentifier */ 0, DATASTORE_VERSION);

        // By default, the bit is false.
        assertThat(
                        rollbackHandlingManager.wasAdServicesDataDeleted(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();

        // Set the record bit and verify it is true.
        rollbackHandlingManager.recordAdServicesDataDeletion(
                AdServicesManager.MEASUREMENT_DELETION);
        assertThat(
                        rollbackHandlingManager.wasAdServicesDataDeleted(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isTrue();

        // Reset the record bit and verify it is false.
        rollbackHandlingManager.resetAdServicesDataDeletion(AdServicesManager.MEASUREMENT_DELETION);
        assertThat(
                        rollbackHandlingManager.wasAdServicesDataDeleted(
                                AdServicesManager.MEASUREMENT_DELETION))
                .isFalse();
    }

    @Test
    public void testDump() throws Exception {
        RollbackHandlingManager rollbackHandlingManager =
                RollbackHandlingManager.createRollbackHandlingManager(
                        BASE_DIR, /* userIdentifier= */ 0, DATASTORE_VERSION);
        BooleanFileDatastore datastore =
                rollbackHandlingManager.getOrCreateBooleanFileDatastore(
                        AdServicesManager.MEASUREMENT_DELETION);
        String prefix = "_";

        String dump = dump(pw -> rollbackHandlingManager.dump(pw, prefix));

        assertWithMessage("content of dump()")
                .that(dump)
                .startsWith(prefix + "RollbackHandlingManager:");
        assertDumpHasPrefix(dump, prefix);
        assertWithMessage("content of dump()")
                .that(dump)
                .startsWith(prefix + "RollbackHandlingManager:");
        assertWithMessage("content of dump() (BASE_DIR)").that(dump).contains(BASE_DIR);
        assertWithMessage("content of dump() (DATASTORE_VERSION)")
                .that(dump)
                .contains(Integer.toString(DATASTORE_VERSION));
        assertWithMessage("content of dump() (# datastores)").that(dump).contains("1 datastores");

        String datastoreDump =
                dump(pw -> datastore.dump(pw, prefix + DUMP_PREFIX + DUMP_PREFIX + DUMP_PREFIX));
        assertWithMessage("content of dump() (datastore)").that(dump).contains(datastoreDump);
    }
}
