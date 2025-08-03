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

package android.app.appsearch.cts.app;

import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;
import static android.app.appsearch.testutil.AppSearchTestUtils.doGet;
import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchEmail;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AppSearchSessionPlatformCtsTest {
    private static final String DB_NAME = "testDb";

    private AppSearchSessionShim mDb;

    @Before
    public void setUp() throws Exception {
        mDb =
                AppSearchSessionShimImpl.createSearchSessionAsync(
                                new AppSearchManager.SearchContext.Builder(DB_NAME).build())
                        .get();

        // Cleanup whatever documents may still exist in these databases. This is needed in
        // addition to tearDown in case a test exited without completing properly.
        cleanup();
    }

    @After
    public void tearDown() throws Exception {
        // Cleanup whatever documents may still exist in these databases.
        cleanup();
    }

    private void cleanup() throws Exception {
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
    }

    @Test
    public void testStorageAttributedToSelf_withDocument() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        StorageStatsManager storageStatsManager =
                context.getSystemService(StorageStatsManager.class);
        String packageName = context.getPackageName();
        UserHandle user = context.getUser();
        int uid = android.os.Process.myUid();
        StorageStats beforeStatsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats beforeStatsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);

        // Schema registration.
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();
        AppSearchEmail email =
                new AppSearchEmail.Builder("namespace", "uri1")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        // Put 30 document. Needed to spot the storage size increase.
        List<AppSearchEmail> emails = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            emails.add(email);
        }
        mDb.putAsync(new PutDocumentsRequest.Builder().addGenericDocuments(emails).build()).get();

        StorageStats afterStatsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats afterStatsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);

        // Verify the storage size increase.
        assertThat(afterStatsForPkg.getDataBytes()).isGreaterThan(beforeStatsForPkg.getDataBytes());
        assertThat(afterStatsForUid.getDataBytes()).isGreaterThan(beforeStatsForUid.getDataBytes());
    }

    @Test
    public void testStorageAttributedToSelf_withoutDocument() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        StorageStatsManager storageStatsManager =
                context.getSystemService(StorageStatsManager.class);
        String packageName = context.getPackageName();
        UserHandle user = context.getUser();
        int uid = android.os.Process.myUid();
        StorageStats beforeStatsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats beforeStatsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);

        // Schema registration.
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();

        StorageStats afterStatsForPkg =
                storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, user);
        StorageStats afterStatsForUid = storageStatsManager.queryStatsForUid(UUID_DEFAULT, uid);

        // Verify that storage size doesn't change.
        assertThat(afterStatsForPkg.getDataBytes()).isEqualTo(beforeStatsForPkg.getDataBytes());
        assertThat(afterStatsForUid.getDataBytes()).isEqualTo(beforeStatsForUid.getDataBytes());
    }

    @Test
    public void testPutDocuments_emptyBytesAndDocuments() throws Exception {
        // Schema registration
        AppSearchSchema schema = new AppSearchSchema.Builder("testSchema")
                .addProperty(new AppSearchSchema.BytesPropertyConfig.Builder("bytes")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .build())
                .addProperty(new AppSearchSchema.DocumentPropertyConfig.Builder(
                        "document", AppSearchEmail.SCHEMA_TYPE)
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                        .setShouldIndexNestedProperties(true)
                        .build())
                .build();
        mDb.setSchemaAsync(new SetSchemaRequest.Builder()
                .addSchemas(schema, AppSearchEmail.SCHEMA).build()).get();

        // Index a document
        GenericDocument document = new GenericDocument.Builder<>("namespace", "id1", "testSchema")
                .setPropertyBytes("bytes")
                .setPropertyDocument("document")
                .build();

        AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(mDb.putAsync(
                new PutDocumentsRequest.Builder().addGenericDocuments(document).build()));
        assertThat(result.getSuccesses()).containsExactly("id1", null);
        assertThat(result.getFailures()).isEmpty();

        GetByDocumentIdRequest request = new GetByDocumentIdRequest.Builder("namespace")
                .addIds("id1")
                .build();
        List<GenericDocument> outDocuments = doGet(mDb, request);
        assertThat(outDocuments).hasSize(1);
        GenericDocument outDocument = outDocuments.get(0);
        assertThat(outDocument.getPropertyBytesArray("bytes")).isEmpty();
        assertThat(outDocument.getPropertyDocumentArray("document")).isEmpty();
    }
}
