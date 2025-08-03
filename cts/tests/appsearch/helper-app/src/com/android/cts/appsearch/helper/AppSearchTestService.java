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
package com.android.cts.appsearch.helper;

import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;
import static android.app.appsearch.testutil.AppSearchTestUtils.convertSearchResultsToDocuments;

import android.app.Service;
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
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SchemaVisibilityConfig;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchEmail;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArraySet;
import android.util.Log;

import com.android.cts.appsearch.ICommandReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class AppSearchTestService extends Service {

    private static final String TAG = "AppSearchTestService";
    private GlobalSearchSessionShim mGlobalSearchSessionShim;

    @Override
    public void onCreate() {
        try {
            // We call this here so we can pass in a context. If we try to create the session in the
            // stub, it'll try to grab the context from ApplicationProvider. But that will fail
            // since this isn't instrumented.
            mGlobalSearchSessionShim =
                    GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(this).get();

        } catch (Exception e) {
            Log.e(TAG, "Error starting service.", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new CommandReceiver();
    }

    private class CommandReceiver extends ICommandReceiver.Stub {

        @Override
        public List<String> globalSearch(String queryExpression) {
            try {
                final SearchSpec searchSpec =
                        new SearchSpec.Builder()
                                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                .build();
                SearchResultsShim searchResults =
                        mGlobalSearchSessionShim.search(queryExpression, searchSpec);
                List<GenericDocument> results = convertSearchResultsToDocuments(searchResults);

                List<String> resultStrings = new ArrayList<>();
                for (GenericDocument doc : results) {
                    resultStrings.add(doc.toString());
                }

                return resultStrings;
            } catch (Exception e) {
                Log.e(TAG, "Error issuing global search.", e);
                return Collections.emptyList();
            }
        }

        @Override
        public List<String> globalGet(
                String packageName, String databaseName, String namespace, String id) {
            try {
                AppSearchBatchResult<String, GenericDocument> getResult =
                        mGlobalSearchSessionShim.getByDocumentIdAsync(
                                packageName,
                                databaseName,
                                new GetByDocumentIdRequest.Builder(namespace)
                                        .addIds(id)
                                        .build())
                                .get();

                List<String> resultStrings = new ArrayList<>();
                for (String docKey : getResult.getSuccesses().keySet()) {
                    resultStrings.add(getResult.getSuccesses().get(docKey).toString());
                }

                return resultStrings;
            } catch (Exception e) {
                Log.e(TAG, "Error issuing global get.", e);
                return Collections.emptyList();
            }
        }

        public List<String> globalGetSchema(String packageName, String databaseName) {
            try {
                GetSchemaResponse response =
                        mGlobalSearchSessionShim.getSchemaAsync(packageName, databaseName).get();
                if (response == null || response.getSchemas().isEmpty()) {
                    return null;
                }
                List<String> schemas = new ArrayList(response.getSchemas().size());
                for (AppSearchSchema schema : response.getSchemas()) {
                    schemas.add(schema.toString());
                }
                return schemas;
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving global schema.", e);
                return null;
            }
        }

        @Override
        public boolean indexGloballySearchableDocument(
                String databaseName, String namespace, String id, List<Bundle> permissionBundles) {
            try {
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                                .get();

                // By default, schemas/documents are globally searchable. We don't purposely set
                // setSchemaTypeDisplayedBySystem(false) for this schema
                SetSchemaRequest.Builder setSchemaRequestBuilder =
                        new SetSchemaRequest.Builder()
                                .setForceOverride(true)
                                .addSchemas(AppSearchEmail.SCHEMA);
                for (int i = 0; i < permissionBundles.size(); i++) {
                    setSchemaRequestBuilder.addRequiredPermissionsForSchemaTypeVisibility(
                            AppSearchEmail.SCHEMA_TYPE,
                            new ArraySet<>(permissionBundles.get(i)
                                    .getIntegerArrayList("permission")));
                }
                db.setSchemaAsync(setSchemaRequestBuilder.build()).get();

                AppSearchEmail emailDocument =
                        new AppSearchEmail.Builder(namespace, id)
                                .setFrom("from@example.com")
                                .setTo("to1@example.com", "to2@example.com")
                                .setSubject("subject")
                                .setBody("this is the body of the email")
                                .build();
                checkIsBatchResultSuccess(
                        db.putAsync(
                                new PutDocumentsRequest.Builder()
                                        .addGenericDocuments(emailDocument)
                                        .build()));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index globally searchable document.", e);
            }
            return false;
        }

        /**
         * Set A schema and index a document with specific visible to config setting to the
         * given database.
         *
         * @param databaseName       The name of database to set the schema.
         * @param namespace          The namespace of the indexed document
         * @param id                 The id of the indexed document
         * @param packageBundles     The VisibleToPackage settings in VisibleToConfig
         * @param permissionBundles  The VisibleToPermission settings in VisibleToConfig
         * @param publicAclPackage   The target public acl settings in VisibleToConfig
         * @return whether this operation is successful.
         */
        @Override
        public boolean indexGloballySearchableDocumentVisibleToConfig(
                String databaseName, String namespace, String id, List<Bundle> packageBundles,
                List<Bundle> permissionBundles, Bundle publicAclPackage) {
            try {
                AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                        AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                        .get();

                // By default, schemas/documents are globally searchable. We don't purposely set
                // setSchemaTypeDisplayedBySystem(false) for this schema
                SchemaVisibilityConfig.Builder configBuilder = new SchemaVisibilityConfig.Builder();
                for (int i = 0; i < packageBundles.size(); i++) {
                    configBuilder.addAllowedPackage(
                            new PackageIdentifier(
                                    packageBundles.get(i).getString("packageName"),
                                    packageBundles.get(i).getByteArray("sha256Cert")));
                }
                for (int i = 0; i < permissionBundles.size(); i++) {
                    configBuilder.addRequiredPermissions(
                            new ArraySet<>(permissionBundles.get(i)
                                    .getIntegerArrayList("permission")));
                }
                if (publicAclPackage != null) {
                    configBuilder.setPubliclyVisibleTargetPackage(
                            new PackageIdentifier(
                                    publicAclPackage.getString("packageName"),
                                    publicAclPackage.getByteArray("sha256Cert")));
                }
                db.setSchemaAsync(new SetSchemaRequest.Builder()
                        .setForceOverride(true)
                        .addSchemas(AppSearchEmail.SCHEMA)
                        .addSchemaTypeVisibleToConfig(AppSearchEmail.SCHEMA_TYPE,
                                configBuilder.build())
                        .build()).get();

                AppSearchEmail emailDocument =
                        new AppSearchEmail.Builder(namespace, id)
                                .setFrom("from@example.com")
                                .setTo("to1@example.com", "to2@example.com")
                                .setSubject("subject")
                                .setBody("this is the body of the email")
                                .build();
                checkIsBatchResultSuccess(
                        db.putAsync(
                                new PutDocumentsRequest.Builder()
                                        .addGenericDocuments(emailDocument)
                                        .build()));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index globally searchable document.", e);
            }
            return false;
        }

        @Override
        public boolean indexNotGloballySearchableDocument(
                String databaseName, String namespace, String id) {
            try {
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                                .get();

                db.setSchemaAsync(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setForceOverride(true)
                                .setSchemaTypeDisplayedBySystem(
                                        AppSearchEmail.SCHEMA_TYPE, /*displayed=*/ false)
                                .build())
                        .get();

                AppSearchEmail emailDocument =
                        new AppSearchEmail.Builder(namespace, id)
                                .setFrom("from@example.com")
                                .setTo("to1@example.com", "to2@example.com")
                                .setSubject("subject")
                                .setBody("this is the body of the email")
                                .build();
                checkIsBatchResultSuccess(
                        db.putAsync(
                                new PutDocumentsRequest.Builder()
                                        .addGenericDocuments(emailDocument)
                                        .build()));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index not-globally searchable document.", e);
            }
            return false;
        }

        @Override
        public boolean indexAction(
                String databaseName, String namespace, String id, String entityId,
                boolean globallySearchable) {
            try {
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                        AppSearchTestService.this,
                                        new AppSearchManager.SearchContext.Builder(databaseName)
                                                .build(),
                                        Executors.newCachedThreadPool())
                                .get();

                AppSearchSchema actionSchema =
                        new AppSearchSchema.Builder("PlayAction")
                                .addProperty(
                                        new StringPropertyConfig.Builder("songId")
                                                .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                                .setJoinableValueType(StringPropertyConfig
                                                        .JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                                .build())
                                .build();

                // By default, schemas/documents are globally searchable. We purposely don't set
                // setSchemaTypeDisplayedBySystem(false) for this schema
                SetSchemaRequest.Builder setSchemaRequest =
                        new SetSchemaRequest.Builder()
                                .setForceOverride(true)
                                .addSchemas(AppSearchEmail.SCHEMA, actionSchema);

                if (!globallySearchable) {
                    setSchemaRequest.setSchemaTypeDisplayedBySystem(
                                    AppSearchEmail.SCHEMA_TYPE, false)
                            .setSchemaTypeDisplayedBySystem(
                                    actionSchema.getSchemaType(), false);
                }

                db.setSchemaAsync(setSchemaRequest.build()).get();

                GenericDocument join =
                        new GenericDocument.Builder<>(namespace, id, "PlayAction")
                                .setPropertyString("songId", entityId)
                                .build();

                checkIsBatchResultSuccess(
                        db.putAsync(
                                new PutDocumentsRequest.Builder()
                                        .addGenericDocuments(join)
                                        .build()));

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index " + (globallySearchable ? "" : "non-")
                        + "globally searchable action document.", e);
            }
            return false;
        }

        @Override
        public boolean setUpPubliclyVisibleDocuments(String targetPackageNameA,
                byte[] targetPackageCertA, String targetPackageNameB, byte[] targetPackageCertB) {
            // We need two schemas, with two different target packages. This way we can test public
            // visibility.

            try {
                AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder("database").build(),
                                Executors.newCachedThreadPool())
                        .get();

                String schemaNameA = targetPackageNameA + "Schema";
                String schemaNameB = targetPackageNameB + "Schema";

                AppSearchSchema schemaA = new AppSearchSchema.Builder(schemaNameA)
                        .addProperty(new StringPropertyConfig.Builder("searchable")
                                .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                                .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                                .build()).build();

                AppSearchSchema schemaB = new AppSearchSchema.Builder(schemaNameB)
                        .addProperty(new StringPropertyConfig.Builder("searchable")
                                .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                                .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                                .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                                .build()).build();

                // Index schemas in the cts package
                db.setSchemaAsync(new SetSchemaRequest.Builder()
                        .addSchemas(schemaA, schemaB)
                        .setForceOverride(true)
                        .setPubliclyVisibleSchema(schemaNameA,
                                new PackageIdentifier(targetPackageNameA, targetPackageCertA))
                        .setPubliclyVisibleSchema(schemaNameB,
                                new PackageIdentifier(targetPackageNameB, targetPackageCertB))
                        .build()).get();

                GenericDocument docA =
                        new GenericDocument.Builder<>("namespace", "id1", schemaNameA)
                                .setCreationTimestampMillis(0L)
                                .setPropertyString("searchable",
                                        "pineapple from " + targetPackageNameA).build();
                GenericDocument docB =
                        new GenericDocument.Builder<>("namespace", "id2", schemaNameB)
                                .setCreationTimestampMillis(0L)
                                .setPropertyString("searchable",
                                        "pineapple from " + targetPackageNameB).build();
                checkIsBatchResultSuccess(db.putAsync(new PutDocumentsRequest.Builder()
                        .addGenericDocuments(docA, docB).build()));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index publicly searchable document.", e);
            }
            return false;
        }

        public boolean clearData(String databaseName) {
            try {
                // Force override with empty schema will clear all previous schemas and their
                // documents.
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                                .get();

                db.setSchemaAsync(
                        new SetSchemaRequest.Builder().setForceOverride(true).build()).get();

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear data.", e);
            }
            return false;
        }
    }
}
