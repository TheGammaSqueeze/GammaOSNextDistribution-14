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

package android.appsearch.app.helper_a;

import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;
import static android.app.appsearch.testutil.AppSearchTestUtils.doGet;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.Manifest;
import android.app.UiAutomation;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.io.BaseEncoding;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public class AppSearchDeviceTest {

    private static final String DB_NAME = "";
    private static final String NAMESPACE = "namespace";
    private static final String ID = "id";
    private static final String USER_ID_KEY = "userId";
    private static final AppSearchSchema SCHEMA = new AppSearchSchema.Builder("testSchema")
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            .build();
    private static final GenericDocument DOCUMENT =
            new GenericDocument.Builder<>(NAMESPACE, ID, SCHEMA.getSchemaType())
                    .setPropertyString("subject", "testPut example1")
                    .setCreationTimestampMillis(12345L)
                    .build();

    private static final String PKG_B = "android.appsearch.app.helper_b";

    // To generate, run `apksigner` on the build APK. e.g.
    //   ./apksigner verify --print-certs \
    //   ~/sc-dev/out/soong/.intermediates/cts/tests/appsearch/CtsAppSearchTestHelperA/\
    //   android_common/CtsAppSearchTestHelperA.apk`
    // to get the SHA-256 digest. All characters need to be uppercase.
    //
    // Note: May need to switch the "sdk_version" of the test app from "test_current" to "30" before
    // building the apk and running apksigner
    private static final byte[] PKG_B_CERT_SHA256 = BaseEncoding.base16().decode(
            "3D7A1AAE7AE8B9949BE93E071F3702AA38695B0F99B5FC4B2E8B364AC78FFDB2");

    private AppSearchSessionShim mDb;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mDb = AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder(DB_NAME).build()).get();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Test
    public void testPutDocuments() throws Exception {
        // Schema registration
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(SCHEMA)
                .setSchemaTypeVisibilityForPackage(SCHEMA.getSchemaType(), /*visible=*/ true,
                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256)).build()).get();

        // Index a document
        AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder().addGenericDocuments(DOCUMENT).build()));
        assertThat(result.getSuccesses()).containsExactly(ID, /*v0=*/null);
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    public void testPutDocumentsAsAnotherUser() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));

            // Initialize as other user
            AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                    new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                    userId).get();

            // Schema registration
            db.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(SCHEMA)
                    .setSchemaTypeVisibilityForPackage(SCHEMA.getSchemaType(), /*visible=*/ true,
                            new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256)).build()).get();

            // Index a document
            AppSearchBatchResult<String, Void> result = checkIsBatchResultSuccess(
                    db.putAsync(new PutDocumentsRequest.Builder().addGenericDocuments(DOCUMENT)
                            .build()));
            assertThat(result.getSuccesses()).containsExactly(ID, /*v0=*/null);
            assertThat(result.getFailures()).isEmpty();

        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetDocuments_exist() throws Exception {
        List<GenericDocument> outDocuments = doGet(mDb, NAMESPACE, ID);
        assertThat(outDocuments).containsExactly(DOCUMENT);
    }

    @Test
    public void testGetDocumentsAsAnotherUser_exist() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));

            // Initialize as other user
            AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                    new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                    userId).get();

            // Get documents
            List<GenericDocument> outDocuments = doGet(db, NAMESPACE, ID);
            assertThat(outDocuments).containsExactly(DOCUMENT);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void closeAndFlush() {
        mDb.close();
    }

    @Test
    public void testGetDocuments_nonexist() throws Exception {
        AppSearchBatchResult<String, GenericDocument> getResult = mDb.getByDocumentIdAsync(
                new GetByDocumentIdRequest.Builder(NAMESPACE).addIds(ID).build()).get();
        assertThat(getResult.getFailures().get(ID).getResultCode())
                .isEqualTo(AppSearchResult.RESULT_NOT_FOUND);
    }

    /**
     * Clear generated data during the test.
     *
     * <p>Device side tests will be a part of host side test. We should clear the test data in the
     * host side tearDown only. Otherwise, it will wipe the data in the middle of a host side test.
     */
    @Test
    public void clearTestData() throws Exception {
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
    }

    @Test
    public void createSessionInStoppedUser() {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));
            ExecutionException exception = expectThrows(ExecutionException.class, () ->
                    AppSearchSessionShimImpl.createSearchSessionAsync(
                            new AppSearchManager.SearchContext.Builder(DB_NAME).build(),
                            userId).get());
            assertThat(exception.getMessage()).contains("is locked or not running.");
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }
}
