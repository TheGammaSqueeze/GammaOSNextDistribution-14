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
package com.android.cts.appsearch;

import android.os.Bundle;
import java.util.List;

interface ICommandReceiver {
    List<String> globalSearch(in String queryExpression);

    List<String> globalGet(in String packageName, in String databaseName, in String namespace,
        in String id);

    List<String> globalGetSchema(String packageName, String databaseName);

    boolean indexGloballySearchableDocument(in String databaseName, in String namespace,
        in String id, in List<Bundle> permissionBundles);

    boolean indexGloballySearchableDocumentVisibleToConfig(in String databaseName,
        in String namespace, in String id, in List<Bundle> packageBundles,
        in List<Bundle> permissionBundles, in Bundle publicAclPackage);

    boolean indexNotGloballySearchableDocument(in String databaseName, in String namespace,
        in String id);

    boolean indexAction(in String databaseName, in String namespace, in String id,
        in String entityId, in boolean globallySearchable);

    boolean setUpPubliclyVisibleDocuments(in String targetPackageNameA,
        in byte[] targetPackageCertA, in String targetPackageNameB, in byte[] targetPackageCertB);

    boolean clearData(in String databaseName);
}
