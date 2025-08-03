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

import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_SMS;
import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSchema.PropertyConfig;
import android.app.appsearch.AppSearchSchema.StringPropertyConfig;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.JoinSpec;
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.ReportSystemUsageRequest;
import android.app.appsearch.ReportUsageRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.testutil.AppSearchEmail;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.app.appsearch.testutil.SystemUtil;
import android.app.appsearch.testutil.TestObserverCallback;
import android.app.appsearch.util.DocumentIdUtil;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import com.android.cts.appsearch.ICommandReceiver;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This doesn't extend {@link android.app.appsearch.cts.app.GlobalSearchSessionCtsTestBase} since
 * these test cases can't be run in a non-platform environment.
 */
@AppModeFull(reason = "Can't bind to helper apps from instant mode")
public class GlobalSearchSessionPlatformCtsTest {

    private static final long TIMEOUT_BIND_SERVICE_SEC = 10;

    private static final String TAG = "GlobalSearchSessionPlatformCtsTest";

    private static final String PKG_A = "com.android.cts.appsearch.helper.a";

    // To generate, run `apksigner` on the build APK. e.g.
    //   ./apksigner verify --print-certs \
    //   ~/sc-dev/out/soong/.intermediates/cts/tests/appsearch/CtsAppSearchTestHelperA/\
    //   android_common/CtsAppSearchTestHelperA.apk`
    // to get the SHA-256 digest. All characters need to be uppercase.
    //
    // Note: May need to switch the "sdk_version" of the test app from "test_current" to "30" before
    // building the apk and running apksigner
    private static final byte[] PKG_A_CERT_SHA256 =
            BaseEncoding.base16()
                    .decode("A90B80BD307B71BB4029674C5C4FE18066994E352EAC933B7B68266210CAFB53");

    private static final String PKG_B = "com.android.cts.appsearch.helper.b";

    // To generate, run `apksigner` on the build APK. e.g.
    //   ./apksigner verify --print-certs \
    //   ~/sc-dev/out/soong/.intermediates/cts/tests/appsearch/CtsAppSearchTestHelperB/\
    //   android_common/CtsAppSearchTestHelperB.apk`
    // to get the SHA-256 digest. All characters need to be uppercase.
    //
    // Note: May need to switch the "sdk_version" of the test app from "test_current" to "30" before
    // building the apk and running apksigner
    private static final byte[] PKG_B_CERT_SHA256 =
            BaseEncoding.base16()
                    .decode("88C0B41A31943D13226C3F22A86A6B4F300315575A6BC533CBF16C4EF3CFAA37");

    private static final String HELPER_SERVICE =
            "com.android.cts.appsearch.helper.AppSearchTestService";

    private static final String TEXT = "foo";

    private static final String NAMESPACE_NAME = "namespace";

    private static final AppSearchEmail EMAIL_DOCUMENT =
            new AppSearchEmail.Builder(NAMESPACE_NAME, "id1")
                    .setFrom("from@example.com")
                    .setTo("to1@example.com", "to2@example.com")
                    .setSubject(TEXT)
                    .setBody("this is the body of the email")
                    .build();

    private static final String DB_NAME = "database";


    private GlobalSearchSessionShim mGlobalSearchSession;
    private AppSearchSessionShim mDb;

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mDb =
                AppSearchSessionShimImpl.createSearchSessionAsync(
                                new AppSearchManager.SearchContext.Builder(DB_NAME).build())
                        .get();
        cleanup();
    }

    @After
    public void tearDown() throws Exception {
        // Cleanup whatever documents may still exist in these databases.
        cleanup();
    }

    private void cleanup() throws Exception {
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();

        clearData(PKG_A, DB_NAME);
        clearData(PKG_B, DB_NAME);
    }

    // This does not have a fixed certificate, so we need to get it live
    private byte[] getCtsPackageSha256Cert() {
        try {
            PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                    mContext.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signingInfo = packageInfo.signingInfo;

            if (signingInfo != null) {
                MessageDigest md = MessageDigest.getInstance("SHA256");
                md.update(signingInfo.getSigningCertificateHistory()[0].toByteArray());
                return md.digest();
            }
        } catch (Exception e) {
            // Invalid certificate or test setup failure, continue to return null
        }
        return null;
    }

    // We could add a third package, PKG_C, that the cts package cannot query. However, this does
    // not allow us to bind to PKG_C, meaning we can't index documents in PKG_C, making it useless.
    // Instead, we will take advantage of the fact that the cts package can query both PKG_A and
    // PKG_B, while PKG_A and PKG_B cannot query each other. We'll focus on the fact that PKG_A
    // cannot query PKG_B, but can query the cts package.
    //
    // So we'll index two schemas, one with a publiclyVisibleTargetPackage of the cts package, and
    // another with a publiclyVisibleTargetPackage of PKG_B. We can't index these in PKG_A, as
    // packages always have visibility to schemas they hold. We'll index them both in PKG_B,
    // ensuring that the "cts schema" is still visible, as well as the cts package (this one),
    // ensuring that the "PKG_B schema" is not visible.
    //
    // In summary:
    // Doc in accessible package, accessible publiclyVisibleTargetPackage: searchable
    // Doc in inaccessible package, accessible publiclyVisibleTargetPackage: searchable
    // Doc in accessible package, inaccessible publiclyVisibleTargetPackage: not searchable
    // Doc in inaccessible package, inaccessible publiclyVisibleTargetPackage: not searchable
    @SdkSuppress(minSdkVersion = 34)
    // TODO(b/275592563): Figure out why canPackageQuery works different on T
    @Test
    public void testPublicVisibility_accessibleTestPackage() throws Exception {
        // Ensure manifest files are set up correctly.
        String ctsPackageName = mContext.getPackageName();
        String ctsSchemaName = ctsPackageName + "Schema";

        assertThat(mContext.getPackageManager().canPackageQuery(PKG_A, ctsPackageName)).isTrue();
        assertThat(mContext.getPackageManager().canPackageQuery(PKG_A, PKG_B)).isFalse();

        AppSearchSchema ctsSchema = new AppSearchSchema.Builder(ctsSchemaName)
                .addProperty(new StringPropertyConfig.Builder("searchable")
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES).build())
                .build();
        // pkg b schema
        AppSearchSchema bSchema = new AppSearchSchema.Builder("BSchema")
                .addProperty(new StringPropertyConfig.Builder("searchable")
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES).build())
                .build();

        // Index schemas in the cts package
        mDb.setSchemaAsync(new SetSchemaRequest.Builder()
                .addSchemas(ctsSchema, bSchema)
                .setPubliclyVisibleSchema(ctsSchema.getSchemaType(),
                        new PackageIdentifier(ctsPackageName, getCtsPackageSha256Cert()))
                .setPubliclyVisibleSchema(bSchema.getSchemaType(),
                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256))
                .build()).get();
        GenericDocument ctsDoc =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id1", ctsSchemaName)
                        .setPropertyString("searchable", "pineapple from com.android.cts.appsearch")
                        .build();
        GenericDocument bDoc =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id2", "BSchema")
                        .setPropertyString("searchable",
                                "pineapple from com.android.cts.appserach.helper.b")
                        .build();
        checkIsBatchResultSuccess(mDb.putAsync(new PutDocumentsRequest.Builder()
                .addGenericDocuments(ctsDoc, bDoc).build()));

        // Assert that this package can search for 2
        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();

        SearchResultsShim results = mDb.search("pineapple", searchSpec);
        List<SearchResult> page = results.getNextPageAsync().get();
        assertThat(page).hasSize(2);

        // Assert PKG_A can see cts
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(PKG_A);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> packageBResults = commandReceiver.globalSearch("pineapple");
            // Only the cts doc
            assertThat(packageBResults).hasSize(1);
            assertThat(packageBResults).containsExactly(ctsDoc.toString());
        } finally {
            serviceConnection.unbind();
        }
    }

    @SdkSuppress(minSdkVersion = 34)
    // TODO(b/275592563): Figure out why canPackageQuery works different on T
    @Test
    public void testPublicVisibility_inaccessibleHelperApp() throws Exception {
        String ctsPackageName = mContext.getPackageName();
        String ctsSchemaName = ctsPackageName + "Schema";
        GenericDocument ctsDoc =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id1", ctsSchemaName)
                        .setPropertyString("searchable", "pineapple from com.android.cts.appsearch")
                        .build();

        assertThat(mContext.getPackageManager().canPackageQuery(PKG_A, ctsPackageName)).isTrue();
        assertThat(mContext.getPackageManager().canPackageQuery(PKG_A, PKG_B)).isFalse();

        // Index schemas in the B package
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(PKG_B);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            commandReceiver.setUpPubliclyVisibleDocuments(
                    ctsPackageName, getCtsPackageSha256Cert(), PKG_B, PKG_B_CERT_SHA256);

            // Assert that B sees two documents
            List<String> results = commandReceiver.globalSearch("pineapple");
            assertThat(results).hasSize(2);
        } finally {
            serviceConnection.unbind();
        }

        // Assert that A just sees the cts document
        serviceConnection = bindToHelperService(PKG_A);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> results = commandReceiver.globalSearch("pineapple");
            // Only the cts doc
            assertThat(results).hasSize(1);
            String resultWithoutTimestamp =
                    results.get(0).replaceAll("creationTimestampMillis: [0-9]+",
                            "creationTimestampMillis: " + ctsDoc.getCreationTimestampMillis());
            assertThat(resultWithoutTimestamp).isEqualTo(ctsDoc.toString());

        } finally {
            serviceConnection.unbind();
        }
    }

    @SdkSuppress(minSdkVersion = 34)
    // TODO(b/275592563): Figure out why canPackageQuery works different on T
    @Test
    public void testPublicVisibility_invalidCertificate() throws Exception {
        // Ensure manifest files are set up correctly.
        String ctsPackageName = mContext.getPackageName();
        String ctsSchemaName = ctsPackageName + "Schema";

        // This test will index a publicly accessible document in both PKG_B and the cts package,
        // but configured with an invalid sha 256 cert. Both should be inaccessible. We can also
        // check that PKG_A can't access publicly visible documents with the publicly visible target
        // package set to PKG_A itself if the certificate doesn't match.

        assertThat(mContext.getPackageManager().canPackageQuery(PKG_A, ctsPackageName)).isTrue();
        assertThat(mContext.getPackageManager().canPackageQuery(PKG_A, PKG_B)).isFalse();

        AppSearchSchema ctsSchema = new AppSearchSchema.Builder(ctsSchemaName)
                .addProperty(new StringPropertyConfig.Builder("searchable")
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES).build())
                .build();
        // pkg a schema
        AppSearchSchema aSchema = new AppSearchSchema.Builder("ASchema")
                .addProperty(new StringPropertyConfig.Builder("searchable")
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES).build())
                .build();

        byte[] invalidCert = new byte[32];
        invalidCert[16] = 48;

        // Index schemas in the cts package
        mDb.setSchemaAsync(new SetSchemaRequest.Builder()
                .addSchemas(ctsSchema, aSchema)
                .setPubliclyVisibleSchema(ctsSchema.getSchemaType(),
                        new PackageIdentifier(ctsPackageName, invalidCert))
                .setPubliclyVisibleSchema(aSchema.getSchemaType(),
                        new PackageIdentifier(PKG_A, invalidCert))
                .build()).get();
        GenericDocument ctsDoc =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id1", ctsSchemaName)
                        .setPropertyString("searchable", "pineapple from com.android.cts.appsearch")
                        .build();
        GenericDocument aDoc =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id2", "ASchema")
                        .setPropertyString("searchable",
                                "pineapple from com.android.cts.appserach.helper.a")
                        .build();
        checkIsBatchResultSuccess(mDb.putAsync(new PutDocumentsRequest.Builder()
                .addGenericDocuments(ctsDoc, aDoc).build()));

        // Check that this package can search for 2
        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build();
        SearchResultsShim results = mDb.search("pineapple", searchSpec);
        List<SearchResult> page = results.getNextPageAsync().get();
        assertThat(page).hasSize(2);

        // Assert PKG_A can't see the documents
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(PKG_A);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> ctsPackageResults = commandReceiver.globalSearch("pineapple");
            assertThat(ctsPackageResults).isEmpty();
        } finally {
            serviceConnection.unbind();
        }

        // Index schemas in the B package
        serviceConnection = bindToHelperService(PKG_B);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            commandReceiver.setUpPubliclyVisibleDocuments(
                    ctsPackageName, invalidCert, PKG_A, invalidCert);

            // Assert that B sees two documents
            List<String> bResults = commandReceiver.globalSearch("pineapple");
            assertThat(bResults).hasSize(2);
        } finally {
            serviceConnection.unbind();
        }

        // Assert that A just sees the cts document
        serviceConnection = bindToHelperService(PKG_A);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> aResults = commandReceiver.globalSearch("pineapple");
            assertThat(aResults).isEmpty();
        } finally {
            serviceConnection.unbind();
        }
    }

    @Test
    public void testNoPackageAccess_default() throws Exception {
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        // No package has access by default
        assertPackageCannotAccess(PKG_A);
        assertPackageCannotAccess(PKG_B);
    }

    @Test
    public void testNoPackageAccess_wrongPackageName() throws Exception {
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(
                                                "some.other.package", PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCannotAccess(PKG_A);
    }

    @Test
    public void testNoPackageAccess_wrongCertificate() throws Exception {
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, new byte[] {10}))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCannotAccess(PKG_A);
    }

    @Test
    public void testAllowPackageAccess() throws Exception {
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);
        assertPackageCannotAccess(PKG_B);
    }

    @Test
    public void testRemoveVisibilitySetting_noRemainingSettings() throws Exception {
        // Set schema and allow PKG_A to access.
        mDb.setSchemaAsync(
                new SetSchemaRequest.Builder()
                        .addSchemas(AppSearchEmail.SCHEMA)
                        .setSchemaTypeVisibilityForPackage(
                                AppSearchEmail.SCHEMA_TYPE,
                                /*visible=*/ true,
                                new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                        .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        // PKG_A can access.
        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);
        assertPackageCannotAccess(PKG_B);

        // Remove the schema.
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();

        // Add the schema back with default visibility setting.
        mDb.setSchemaAsync(new SetSchemaRequest.Builder()
                .addSchemas(AppSearchEmail.SCHEMA).build()).get();

        // No pcakage can access.
        assertPackageCannotAccess(PKG_A);
        assertPackageCannotAccess(PKG_B);
    }

    @Test
    public void testAllowMultiplePackageAccess() throws Exception {
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);
        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_B);
    }

    @Test
    public void testNoPackageAccess_revoked() throws Exception {
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));
        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);

        // Set the schema again, but package access as false.
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ false,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));
        assertPackageCannotAccess(PKG_A);

        // Set the schema again, but with default (i.e. no) access
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ false,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));
        assertPackageCannotAccess(PKG_A);
    }

    @Test
    public void testAllowPermissionAccess() throws Exception {
        // index a global searchable document in pkg_A and set it needs READ_SMS to read it.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1",
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)));

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS);
    }

    //TODO(b/202194495) add test for READ_HOME_APP_SEARCH_DATA and READ_ASSISTANT_APP_SEARCH_DATA
    // once they are available in Shell.
    @Test
    public void testRequireAllPermissionsOfAnyCombinationToAccess() throws Exception {
        // index a global searchable document in pkg_A and set it needs both READ_SMS and
        // READ_CALENDAR or READ_HOME_APP_SEARCH_DATA only or READ_ASSISTANT_APP_SEARCH_DATA
        // only to read it.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1",
                ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest.READ_CONTACTS),
                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)));

        // Has READ_SMS only cannot access the document.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can't get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);

        // Has READ_SMS and READ_CALENDAR can access the document.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS, READ_CALENDAR);

        // Has READ_CONTACTS can access the document also.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_CONTACTS);

        // Has READ_EXTERNAL_STORAGE can access the document.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_EXTERNAL_STORAGE);
    }

    @Test
    public void testAllowPermissions_sameError() throws Exception {
        // Try to get document before we put them, this is not found error.
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        AppSearchBatchResult<String, GenericDocument> nonExistentResult = mGlobalSearchSession
                .getByDocumentIdAsync(PKG_A, "database",
                        new GetByDocumentIdRequest.Builder("namespace")
                                .addIds("id1")
                                .build()).get();
        assertThat(nonExistentResult.isSuccess()).isFalse();
        assertThat(nonExistentResult.getSuccesses()).isEmpty();
        assertThat(nonExistentResult.getFailures()).containsKey("id1");

        // Index a global searchable document in pkg_A and set it needs READ_SMS to read it.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1",
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)));

        // Try to get document w/o permission, this is unAuthority error.
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        AppSearchBatchResult<String, GenericDocument> unAuthResult = mGlobalSearchSession
                .getByDocumentIdAsync(PKG_A, "database",
                        new GetByDocumentIdRequest.Builder("namespace")
                                .addIds("id1")
                                .build()).get();
        assertThat(unAuthResult.isSuccess()).isFalse();
        assertThat(unAuthResult.getSuccesses()).isEmpty();
        assertThat(unAuthResult.getFailures()).containsKey("id1");

        // The error messages must be same.
        assertThat(unAuthResult.getFailures().get("id1").getErrorMessage())
                .isEqualTo(nonExistentResult.getFailures().get("id1").getErrorMessage());
    }

    @Test
    public void testGlobalGetById_withAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();

                    assertThat(result.getSuccesses()).hasSize(1);

                    // Can't get non existent document
                    AppSearchBatchResult<String, GenericDocument> nonExistent = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id2")
                                            .build()).get();

                    assertThat(nonExistent.isSuccess()).isFalse();
                    assertThat(nonExistent.getSuccesses()).isEmpty();
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalGetById_withoutAccess() throws Exception {
        indexNotGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can't get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.isSuccess()).isFalse();
                    assertThat(result.getSuccesses()).isEmpty();
                    assertThat(result.getFailures()).containsKey("id1");
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalGetById_sameErrorMessages() throws Exception {
        AtomicReference<String> errorMessageNonExistent = new AtomicReference<>();
        AtomicReference<String> errorMessageUnauth = new AtomicReference<>();

        // Can't get the document because we haven't added it yet
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    AppSearchBatchResult<String, GenericDocument> nonExistentResult =
                            mGlobalSearchSession.getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(nonExistentResult.isSuccess()).isFalse();
                    assertThat(nonExistentResult.getSuccesses()).isEmpty();
                    assertThat(nonExistentResult.getFailures()).containsKey("id1");
                    errorMessageNonExistent.set(
                            nonExistentResult.getFailures().get("id1").getErrorMessage());
                },
                READ_GLOBAL_APP_SEARCH_DATA);

        indexNotGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        // Can't get the document because the document is not globally searchable
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    AppSearchBatchResult<String, GenericDocument> unAuthResult =
                            mGlobalSearchSession.getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(unAuthResult.isSuccess()).isFalse();
                    assertThat(unAuthResult.getSuccesses()).isEmpty();
                    assertThat(unAuthResult.getFailures()).containsKey("id1");
                    errorMessageUnauth.set(
                            unAuthResult.getFailures().get("id1").getErrorMessage());
                },
                READ_GLOBAL_APP_SEARCH_DATA);

        // try adding a global doc here to make sure non-global querier can't get it
        // and same error message
        clearData(PKG_A, DB_NAME);
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        // Can't get the document because we don't have global permissions
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        AppSearchBatchResult<String, GenericDocument> noGlobalResult = mGlobalSearchSession
                .getByDocumentIdAsync(PKG_A, DB_NAME,
                        new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                .addIds("id1")
                                .build()).get();
        assertThat(noGlobalResult.isSuccess()).isFalse();
        assertThat(noGlobalResult.getSuccesses()).isEmpty();
        assertThat(noGlobalResult.getFailures()).containsKey("id1");

        // compare error messages
        assertThat(errorMessageNonExistent.get()).isEqualTo(errorMessageUnauth.get());
        assertThat(errorMessageNonExistent.get())
                .isEqualTo(noGlobalResult.getFailures().get("id1").getErrorMessage());
    }

    @Test
    public void testGlobalGetById_requireAllVisibleToConfig_pkgAndPermission() throws Exception {
        String ctsPackageName = mContext.getPackageName();
        PackageIdentifier ctsPackage =
                new PackageIdentifier(ctsPackageName, getCtsPackageSha256Cert());
        PackageIdentifier otherPackage =
                new PackageIdentifier("com.android.other", new byte[32]);
        // index global searchable document in pkg_A and set it requires READ_SMS and accessible
        // to cts package
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                ImmutableSet.of(ctsPackage, otherPackage),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                /*publicAclPackage=*/null);

        // Can get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document "id1".
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS);

        // Cann't get document with READ_CALENDAR permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();

                    // Can't get the document "id2", even if we are while list package, but we don't
                    // hold required permission.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_CALENDAR);

        // Update the schema visibility setting and index global searchable document in pkg_A
        // set it requires READ_SMS and NOT accessible to cts package
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                ImmutableSet.of(otherPackage),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                /*publicAclPackage=*/null);
        // Cann't get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();
                    // Can't get the document "id2", even if we hold the required permission but
                    // we are not in the while list packages.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);
    }

    @Test
    public void testGlobalGetById_requireAllVisibleToConfig_publicAclAndPermission()
            throws Exception {
        String ctsPackageName = mContext.getPackageName();
        assertThat(mContext.getPackageManager().canPackageQuery(ctsPackageName, PKG_A)).isTrue();
        PackageIdentifier packageA = new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256);
        // index global searchable document in pkg_A and set it requires READ_SMS and is public to
        // packages that can query other package
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                /*visibleToPackages=*/ImmutableSet.of(),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                packageA);

        // Can get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS);

        // Cann't get document with READ_CALENDAR permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();

                    // Can't get the document, even if we are while list package, but we don't
                    // hold required permission.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_CALENDAR);

        // Update the schema visibility setting and index global searchable document in pkg_A
        // set it requires READ_SMS and is public to packages that can query otherPackage
        PackageIdentifier otherPackage =
                new PackageIdentifier("com.android.other", new byte[32]);
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                /*visibleToPackages=*/ImmutableSet.of(),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                otherPackage);
        // Cann't get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();
                    // Can't get the document "id2", even if we hold the required permission but
                    // we are not in the while list packages.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);
    }

    @Test
    public void testGlobalGetById_requireAllVisibleToConfig_all3Settings()
            throws Exception {
        String ctsPackageName = mContext.getPackageName();
        assertThat(mContext.getPackageManager().canPackageQuery(ctsPackageName, PKG_A)).isTrue();
        PackageIdentifier ctsPackage =
                new PackageIdentifier(ctsPackageName, getCtsPackageSha256Cert());
        PackageIdentifier packageA = new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256);
        PackageIdentifier otherPackage =
                new PackageIdentifier("com.android.other", new byte[32]);
        // index global searchable document in pkg_A and set it requires READ_SMS and accessible
        // to cts package and is public to packages that can query other package
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                ImmutableSet.of(ctsPackage, otherPackage),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                packageA);

        // Can get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS);

        // Cann't get document with READ_CALENDAR permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();

                    // Can't get the document "id2", even if we are while list package, but we don't
                    // hold required permission.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id1")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_CALENDAR);

        // Update the schema visibility setting and index global searchable document in pkg_A.
        // set it NOT accessible to cts package
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                ImmutableSet.of(otherPackage),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                packageA);

        // Cann't get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();
                    // Can't get the document "id2", even if we hold the required permission but
                    // we are not in the while list packages.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);

        // Update the schema visibility setting and index global searchable document in pkg_A.
        // set it requires READ_SMS and accessible to cts package and require can query to target
        // the otherPackage.
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                ImmutableSet.of(ctsPackage, otherPackage),
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)),
                /*publicAclPackage=*/otherPackage);

        // Cann't get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                        GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                            .get();
                    // Can't get the document "id2", even if we hold the required permission but
                    // we are not in the while list packages.
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                new GetByDocumentIdRequest.Builder("namespace")
                                    .addIds("id")
                                    .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);
    }

    @Test
    public void testGlobalGetById_requireAllVisibleToConfig_emptyConfig()
            throws Exception {
        // index global searchable document in pkg_A and set it requires empty config. This
        // shouldn't accessible by anyone.
        indexGloballySearchableDocumentVisibleToConfig(PKG_A, DB_NAME, NAMESPACE_NAME, "id",
                /*visibleToPackages=*/ImmutableSet.of(),
                /*visibleToPermissions=*/ImmutableSet.of(),
                /*publicAclPackage=*/null);

        // Cann't get document with READ_SMS permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id")
                                            .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);
    }

    @Test
    public void testGlobalSearch_withAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .addFilterPackageNames(PKG_A, PKG_B)
                                            .build());
                    List<SearchResult> page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(2);

                    ImmutableSet<String> actualPackageNames =
                            ImmutableSet.of(
                                    page.get(0).getPackageName(), page.get(1).getPackageName());
                    assertThat(actualPackageNames).containsExactly(PKG_A, PKG_B);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withPartialAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexNotGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .addFilterPackageNames(PKG_A, PKG_B)
                                            .build());
                    List<SearchResult> page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(1);

                    assertThat(page.get(0).getPackageName()).isEqualTo(PKG_A);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withPackageFilters() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .addFilterPackageNames(PKG_B)
                                            .build());
                    List<SearchResult> page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(1);

                    assertThat(page.get(0).getPackageName()).isEqualTo(PKG_B);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withoutAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        SearchResultsShim searchResults =
                mGlobalSearchSession.search(
                        /*queryExpression=*/ "",
                        new SearchSpec.Builder()
                                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                .addFilterPackageNames(PKG_A, PKG_B)
                                .build());
        List<SearchResult> page = searchResults.getNextPageAsync().get();
        assertThat(page).isEmpty();
    }

    @Test
    public void testGlobalSearch_withJoin_packageFiltering() throws Exception {
        // This test ensures that we can place documents in separate packages, then join them
        // together while specifying package filters.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "ida");

        // In pkg B, we report an action on the document in pkg A. When we retrieve the pkg A
        // document using a joinspec, the action in pkg B should be joined.
        String qualifiedId =
                DocumentIdUtil.createQualifiedId(
                        PKG_A, DB_NAME, NAMESPACE_NAME, "ida");

        indexActionDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "actionId1", qualifiedId,
                /*globallySearchable*/true);

        AppSearchSchema actionSchema =
                new AppSearchSchema.Builder("PlayAction")
                        .addProperty(
                                new StringPropertyConfig.Builder("songId")
                                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                        .setJoinableValueType(StringPropertyConfig
                                                .JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .build())
                        .build();
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(actionSchema).build()).get();

        GenericDocument join =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "actionId2", "PlayAction")
                        .setPropertyString("songId", qualifiedId)
                        .build();

        // In the test package, index an action on that document
        checkIsBatchResultSuccess(mDb.putAsync(
                new PutDocumentsRequest.Builder().addGenericDocuments(join).build()));

        SystemUtil.runWithShellPermissionIdentity(
                () -> {

                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchSpec nestedSearchSpec = new SearchSpec.Builder()
                            .addFilterPackageNames(PKG_B)
                            .build();
                    JoinSpec js = new JoinSpec.Builder("songId")
                            .setNestedSearch("", nestedSearchSpec)
                            .build();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .addFilterPackageNames(PKG_A)
                                            .addFilterSchemas(AppSearchEmail.SCHEMA_TYPE)
                                            .setJoinSpec(js)
                                            .build());

                    List<SearchResult> page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(1);

                    SearchResult searchResult = page.get(0);

                    assertThat(searchResult.getGenericDocument().getId()).isEqualTo("ida");

                    assertThat(searchResult.getJoinedResults()).hasSize(1);
                    SearchResult joined = page.get(0).getJoinedResults().get(0);
                    assertThat(joined.getGenericDocument().getId()).isEqualTo("actionId1");
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withJoin() throws Exception {
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "idb");
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "ida");

        // In pkg B, we report an action on the document in pkg A. When we retrieve the pkg A
        // document using a joinspec, the action in pkg B should be joined.
        String qualifiedId =
                DocumentIdUtil.createQualifiedId(
                        PKG_A, DB_NAME, NAMESPACE_NAME, "ida");

        indexActionDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "actionId", qualifiedId,
                /*globallySearchable*/true);

        SystemUtil.runWithShellPermissionIdentity(
                () -> {

                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchSpec nestedSearchSpec = new SearchSpec.Builder()
                            .addFilterPackageNames(PKG_A, PKG_B)
                            .build();
                    JoinSpec js = new JoinSpec.Builder("songId")
                            .setNestedSearch("", nestedSearchSpec)
                            .build();

                    // Here, we rank based on document creation timestamp. Since it's ascending,
                    // the document in package B should be first.
                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .setRankingStrategy(SearchSpec
                                                    .RANKING_STRATEGY_CREATION_TIMESTAMP)
                                            .setOrder(SearchSpec.ORDER_ASCENDING)
                                            .addFilterPackageNames(PKG_A, PKG_B)
                                            .addFilterSchemas(AppSearchEmail.SCHEMA_TYPE)
                                            .setJoinSpec(js)
                                            .build());

                    List<SearchResult> page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(2);

                    assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("idb");

                    assertThat(page.get(1).getJoinedResults()).hasSize(1);
                    assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("ida");

                    SearchResult joined = page.get(1).getJoinedResults().get(0);
                    assertThat(joined.getGenericDocument().getId()).isEqualTo("actionId");

                    // Here, we rank based on the number of joined documents a parent document has.
                    // The results should be in a different order this time.
                    js = new JoinSpec.Builder("songId")
                            .setNestedSearch("", nestedSearchSpec)
                            .setAggregationScoringStrategy(JoinSpec
                                    .AGGREGATION_SCORING_RESULT_COUNT)
                            .build();

                    searchResults = mGlobalSearchSession.search(
                            /*queryExpression=*/ "",
                            new SearchSpec.Builder()
                                    .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                    .setRankingStrategy(SearchSpec
                                            .RANKING_STRATEGY_JOIN_AGGREGATE_SCORE)
                                    .addFilterPackageNames(PKG_A, PKG_B)
                                    .addFilterSchemas(AppSearchEmail.SCHEMA_TYPE)
                                    .setJoinSpec(js)
                                    .build());

                    page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(2);

                    assertThat(page.get(0).getJoinedResults()).hasSize(1);
                    assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("ida");

                    joined = page.get(0).getJoinedResults().get(0);
                    assertThat(joined.getGenericDocument().getId()).isEqualTo("actionId");

                    assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("idb");
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withJoin_partialAccess() throws Exception {

        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "ida");

        String qualifiedId =
                DocumentIdUtil.createQualifiedId(
                        PKG_A, DB_NAME, NAMESPACE_NAME, "ida");
        // Reporting an action on a globally searchable document, but the action itself isn't
        // globally searchable. Searching with joinspec shouldn't return the action document.

        // In pkg B, we report an action on the document in pkg A. When we retrieve the pkg A
        // document using a joinspec, the action in pkg B should be joined.

        // here, we index an action document inaccessible from the querier
        indexActionDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "actionId", qualifiedId,
                /*globallySearchable*/false);

        SystemUtil.runWithShellPermissionIdentity(
                () -> {

                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchSpec nestedSearchSpec = new SearchSpec.Builder()
                            .addFilterPackageNames(PKG_A, PKG_B)
                            .build();
                    JoinSpec js =
                            new JoinSpec.Builder("songId")
                                    .setNestedSearch("", nestedSearchSpec)
                                    .setAggregationScoringStrategy(JoinSpec
                                            .AGGREGATION_SCORING_RESULT_COUNT)
                                    .build();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .setRankingStrategy(SearchSpec
                                                    .RANKING_STRATEGY_JOIN_AGGREGATE_SCORE)
                                            .addFilterPackageNames(PKG_A, PKG_B)
                                            .addFilterSchemas(AppSearchEmail.SCHEMA_TYPE)
                                            .setJoinSpec(js)
                                            .build());

                    List<SearchResult> page = searchResults.getNextPageAsync().get();
                    assertThat(page).hasSize(1);

                    assertThat(page.get(0).getJoinedResults()).hasSize(0);
                    assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("ida");
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withJoin_withoutAccess() throws Exception {

        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                        .get();

        // Insert schema
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();

        // Insert two docs
        GenericDocument document1 =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id1", AppSearchEmail.SCHEMA_TYPE)
                        .build();
        mDb.putAsync(
                        new PutDocumentsRequest.Builder().addGenericDocuments(document1).build())
                .get();

        String qualifiedId =
                DocumentIdUtil.createQualifiedId(
                        PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexActionDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "actionId", qualifiedId,
                /*globallySearchable*/true);

        // Query the data
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        SearchSpec nestedSearchSpec = new SearchSpec.Builder()
                .addFilterPackageNames(PKG_A, PKG_B)
                .build();
        JoinSpec js =
                new JoinSpec.Builder("songId")
                        .setNestedSearch("", nestedSearchSpec)
                        .setAggregationScoringStrategy(JoinSpec
                                .AGGREGATION_SCORING_RESULT_COUNT)
                        .build();

        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .setRankingStrategy(SearchSpec
                        .RANKING_STRATEGY_JOIN_AGGREGATE_SCORE)
                .addFilterSchemas(AppSearchEmail.SCHEMA_TYPE)
                .setJoinSpec(js)
                .build();

        SearchResultsShim results = mDb.search("", searchSpec);
        List<SearchResult> page = results.getNextPageAsync().get();
        assertThat(page).hasSize(1);

        assertThat(page.get(0).getJoinedResults()).hasSize(0);
        assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id1");
    }

    @Test
    public void testGlobalGetSchema_packageAccess_defaultAccess() throws Exception {
        // 1. Create a schema in the test with default (no) access.
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .build())
                .get();

        // 2. Neither PKG_A nor PKG_B should be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).isNull();

        schemaStrings = getSchemaAsPackage(PKG_B);
        assertThat(schemaStrings).isNull();
    }

    @Test
    public void testGlobalGetSchema_packageAccess_singleAccess() throws Exception {
        // 1. Create a schema in the test with access granted to PKG_A, but not PKG_B.
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();

        // 2. Only PKG_A should be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).containsExactly(AppSearchEmail.SCHEMA.toString());

        schemaStrings = getSchemaAsPackage(PKG_B);
        assertThat(schemaStrings).isNull();
    }

    @Test
    public void testGlobalGetSchema_packageAccess_multiAccess() throws Exception {
        // 1. Create a schema in the test with access granted to PKG_A and PKG_B.
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256))
                                .build())
                .get();

        // 2. Both packages should be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).containsExactly(AppSearchEmail.SCHEMA.toString());

        schemaStrings = getSchemaAsPackage(PKG_B);
        assertThat(schemaStrings).containsExactly(AppSearchEmail.SCHEMA.toString());
    }

    @Test
    public void testGlobalGetSchema_packageAccess_revokeAccess() throws Exception {
        // 1. Create a schema in the test with access granted to PKG_A.
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();

        // 2. Now revoke that access.
        mDb.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ false,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();

        // 3. PKG_A should NOT be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).isNull();
    }

    @Test
    public void testGlobalGetSchema_globalAccess_singleAccess() throws Exception {
        // 1. Index documents for PKG_A and PKG_B. This will set the schema for each with the
        // corresponding access set.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexNotGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(
                                    mContext).get();

                    // 2. The schema for PKG_A should be retrievable, but PKG_B should not be.
                    GetSchemaResponse response =
                            mGlobalSearchSession.getSchemaAsync(PKG_A, DB_NAME).get();
                    assertThat(response.getSchemas()).hasSize(1);

                    response = mGlobalSearchSession.getSchemaAsync(PKG_B, DB_NAME).get();
                    assertThat(response.getSchemas()).isEmpty();
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalGetSchema_globalAccess_multiAccess() throws Exception {
        // 1. Index documents for PKG_A and PKG_B. This will set the schema for each with the
        // corresponding access set.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(
                                    mContext).get();

                    // 2. The schema for both PKG_A and PKG_B should be retrievable.
                    GetSchemaResponse response =
                            mGlobalSearchSession.getSchemaAsync(PKG_A, DB_NAME).get();
                    assertThat(response.getSchemas()).hasSize(1);

                    response = mGlobalSearchSession.getSchemaAsync(PKG_B, DB_NAME).get();
                    assertThat(response.getSchemas()).hasSize(1);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }


    @Test
    public void testReportSystemUsage() throws Exception {
        // Insert schema
        mDb.setSchemaAsync(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();

        // Insert two docs
        GenericDocument document1 =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id1", AppSearchEmail.SCHEMA_TYPE)
                        .build();
        GenericDocument document2 =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id2", AppSearchEmail.SCHEMA_TYPE)
                        .build();
        mDb.putAsync(
                new PutDocumentsRequest.Builder().addGenericDocuments(document1, document2).build())
                .get();

        // Report some usages. id1 has 2 app and 1 system usage, id2 has 1 app and 2 system usage.
        mDb.reportUsageAsync(
                        new ReportUsageRequest.Builder(NAMESPACE_NAME, "id1")
                                .setUsageTimestampMillis(10)
                                .build())
                .get();
        mDb.reportUsageAsync(
                        new ReportUsageRequest.Builder(NAMESPACE_NAME, "id1")
                                .setUsageTimestampMillis(20)
                                .build())
                .get();
        mDb.reportUsageAsync(
                        new ReportUsageRequest.Builder(NAMESPACE_NAME, "id2")
                                .setUsageTimestampMillis(100)
                                .build())
                .get();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mGlobalSearchSession =
                    GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
            mGlobalSearchSession
                    .reportSystemUsageAsync(
                            new ReportSystemUsageRequest.Builder(
                                    mContext.getPackageName(), DB_NAME, NAMESPACE_NAME, "id1")
                                    .setUsageTimestampMillis(1000)
                                    .build())
                    .get();
            mGlobalSearchSession
                    .reportSystemUsageAsync(
                            new ReportSystemUsageRequest.Builder(
                                    mContext.getPackageName(), DB_NAME, NAMESPACE_NAME, "id2")
                                    .setUsageTimestampMillis(200)
                                    .build())
                    .get();
            mGlobalSearchSession
                    .reportSystemUsageAsync(
                            new ReportSystemUsageRequest.Builder(
                                    mContext.getPackageName(), DB_NAME, NAMESPACE_NAME, "id2")
                                    .setUsageTimestampMillis(150)
                                    .build())
                    .get();
        }, READ_GLOBAL_APP_SEARCH_DATA);

        // Query the data
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        // Sort by app usage count: id1 should win
        try (SearchResultsShim results = mDb.search(
                "",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_COUNT)
                        .build())) {
            List<SearchResult> page = results.getNextPageAsync().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id1");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id2");
        }

        // Sort by app usage timestamp: id2 should win
        try (SearchResultsShim results = mDb.search(
                "",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP)
                        .build())) {
            List<SearchResult> page = results.getNextPageAsync().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id2");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id1");
        }

        // Sort by system usage count: id2 should win
        try (SearchResultsShim results = mDb.search(
                "",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setRankingStrategy(
                                SearchSpec.RANKING_STRATEGY_SYSTEM_USAGE_COUNT)
                        .build())) {
            List<SearchResult> page = results.getNextPageAsync().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id2");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id1");
        }

        // Sort by system usage timestamp: id1 should win
        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_SYSTEM_USAGE_LAST_USED_TIMESTAMP)
                .build();
        try (SearchResultsShim results = mDb.search("", searchSpec)) {
            List<SearchResult> page = results.getNextPageAsync().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id1");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id2");
        }
    }

    @Test
    public void testRemoveObserver_otherPackagesNotRemoved() throws Exception {
        final String fakePackage = "com.android.appsearch.fake.package";
        TestObserverCallback observer = new TestObserverCallback();

        // Set up schema
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        mDb.setSchemaAsync(new SetSchemaRequest.Builder()
                .addSchemas(AppSearchEmail.SCHEMA).build()).get();

        // Register this observer twice, on different packages.
        Executor executor = MoreExecutors.directExecutor();
        mGlobalSearchSession.registerObserverCallback(
                mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas(AppSearchEmail.SCHEMA_TYPE).build(),
                executor,
                observer);
        mGlobalSearchSession.registerObserverCallback(
                /*observedPackage=*/fakePackage,
                new ObserverSpec.Builder().addFilterSchemas("Gift").build(),
                executor,
                observer);

        // Make sure everything is empty
        assertThat(observer.getSchemaChanges()).isEmpty();
        assertThat(observer.getDocumentChanges()).isEmpty();

        // Index some documents
        AppSearchEmail email1 = new AppSearchEmail.Builder(NAMESPACE_NAME, "id1").build();
        AppSearchEmail email2 =
                new AppSearchEmail.Builder(NAMESPACE_NAME, "id2").setBody("caterpillar").build();
        AppSearchEmail email3 =
                new AppSearchEmail.Builder(NAMESPACE_NAME, "id3").setBody("foo").build();

        checkIsBatchResultSuccess(
                mDb.putAsync(new PutDocumentsRequest.Builder()
                        .addGenericDocuments(email1).build()));

        // Make sure the notifications were received.
        observer.waitForNotificationCount(1);

        assertThat(observer.getSchemaChanges()).isEmpty();
        assertThat(observer.getDocumentChanges()).containsExactly(
                new DocumentChangeInfo(
                        mContext.getPackageName(),
                        DB_NAME,
                        NAMESPACE_NAME,
                        AppSearchEmail.SCHEMA_TYPE,
                        ImmutableSet.of("id1")));
        observer.clear();

        // Unregister observer from com.example.package
        mGlobalSearchSession.unregisterObserverCallback("com.example.package", observer);

        // Index some more documents
        assertThat(observer.getDocumentChanges()).isEmpty();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder().addGenericDocuments(email2).build()));

        // Make sure data was still received
        observer.waitForNotificationCount(1);
        assertThat(observer.getDocumentChanges()).containsExactly(
                new DocumentChangeInfo(
                        mContext.getPackageName(),
                        DB_NAME,
                        NAMESPACE_NAME,
                        AppSearchEmail.SCHEMA_TYPE,
                        ImmutableSet.of("id2")));
        observer.clear();

        // Unregister the final observer
        mGlobalSearchSession.unregisterObserverCallback(mContext.getPackageName(), observer);

        // Index some more documents
        assertThat(observer.getDocumentChanges()).isEmpty();
        checkIsBatchResultSuccess(
                mDb.putAsync(
                        new PutDocumentsRequest.Builder().addGenericDocuments(email3).build()));

        // Make sure there have been no further notifications
        assertThat(observer.getDocumentChanges()).isEmpty();
    }

    private List<String> getSchemaAsPackage(String pkg) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            return commandReceiver.globalGetSchema(mContext.getPackageName(), DB_NAME);
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPackageCannotAccess(String pkg) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> results = commandReceiver.globalSearch(TEXT);
            assertThat(results).isEmpty();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPackageCanAccess(GenericDocument expectedDocument, String pkg)
            throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> results = commandReceiver.globalSearch(TEXT);
            assertThat(results).containsExactly(expectedDocument.toString());
        } finally {
            serviceConnection.unbind();
        }
    }

    private void indexGloballySearchableDocument(String pkg, String databaseName, String namespace,
            String id) throws Exception {
        indexGloballySearchableDocument(pkg, databaseName, namespace, id, ImmutableSet.of());
    }

    private void indexGloballySearchableDocument(String pkg, String databaseName, String namespace,
            String id, Set<Set<Integer>> visibleToPermissions) throws Exception {
        // binder won't accept Set or Integer, we need to convert to List<Bundle>.
        List<Bundle> permissionBundles = new ArrayList<>(visibleToPermissions.size());
        for (Set<Integer> allRequiredPermissions : visibleToPermissions) {
            Bundle permissionBundle = new Bundle();
            permissionBundle.putIntegerArrayList("permission",
                    new ArrayList<>(allRequiredPermissions));
            permissionBundles.add(permissionBundle);
        }
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver.indexGloballySearchableDocument(
                    databaseName, namespace, id, permissionBundles)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void indexGloballySearchableDocumentVisibleToConfig(String pkg, String databaseName,
            String namespace, String id, Set<PackageIdentifier> visibleToPackages,
            Set<Set<Integer>> visibleToPermissions, PackageIdentifier publicAclPackage)
            throws Exception {
        // PackageIdentifierParcel is hidden, we need to use bundle to pass PackageIdentifier.
        List<Bundle> packagesBundles = new ArrayList<>(visibleToPackages.size());
        for (PackageIdentifier visibleToPackage: visibleToPackages) {
            Bundle packageBundle = new Bundle();
            packageBundle.putString("packageName", visibleToPackage.getPackageName());
            packageBundle.putByteArray("sha256Cert", visibleToPackage.getSha256Certificate());
            packagesBundles.add(packageBundle);
        }

        // binder won't accept Set or Integer, we need to convert to List<Bundle>.
        List<Bundle> permissionBundles = new ArrayList<>(visibleToPermissions.size());
        for (Set<Integer> allRequiredPermissions : visibleToPermissions) {
            Bundle permissionBundle = new Bundle();
            permissionBundle.putIntegerArrayList("permission",
                    new ArrayList<>(allRequiredPermissions));
            permissionBundles.add(permissionBundle);
        }

        Bundle publicAclBundle = null;
        if (publicAclPackage != null) {
            publicAclBundle = new Bundle();
            publicAclBundle.putString("packageName", publicAclPackage.getPackageName());
            publicAclBundle.putByteArray("sha256Cert", publicAclPackage.getSha256Certificate());
        }

        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver.indexGloballySearchableDocumentVisibleToConfig(databaseName,
                    namespace, id, packagesBundles, permissionBundles, publicAclBundle)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void indexNotGloballySearchableDocument(
            String pkg, String databaseName, String namespace, String id) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver
                    .indexNotGloballySearchableDocument(databaseName, namespace, id)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void indexActionDocument(
            String pkg, String databaseName, String namespace, String id, String entityId,
            boolean globallySearchable)
            throws Exception {

        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver
                    .indexAction(databaseName, namespace, id, entityId, globallySearchable))
                    .isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void clearData(String pkg, String databaseName) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver.clearData(databaseName)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private GlobalSearchSessionPlatformCtsTest.TestServiceConnection bindToHelperService(
            String pkg) {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                new GlobalSearchSessionPlatformCtsTest.TestServiceConnection(mContext);
        Intent intent = new Intent().setComponent(new ComponentName(pkg, HELPER_SERVICE));
        mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return serviceConnection;
    }

    private class TestServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();
        private ICommandReceiver mCommandReceiver;

        TestServiceConnection(Context context) {
            mContext = context;
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service got connected: " + componentName);
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "Service got disconnected: " + componentName);
        }

        private IBinder getService() throws Exception {
            return mBlockingQueue.poll(TIMEOUT_BIND_SERVICE_SEC, TimeUnit.SECONDS);
        }

        public ICommandReceiver getCommandReceiver() throws Exception {
            if (mCommandReceiver == null) {
                mCommandReceiver = ICommandReceiver.Stub.asInterface(getService());
            }
            if (mCommandReceiver == null) {
                Log.e(TAG, "Cannot bind to a service in " + TIMEOUT_BIND_SERVICE_SEC + " second.");
            }
            return mCommandReceiver;
        }

        public void unbind() {
            mCommandReceiver = null;
            Log.i(TAG, "Service got unbinded.");
            mContext.unbindService(this);
        }
    }
}
