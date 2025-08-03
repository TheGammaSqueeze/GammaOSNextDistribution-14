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

package com.android.server.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.testutil.FakeAppSearchConfig;
import com.android.server.appsearch.external.localstorage.AppSearchImpl;
import com.android.server.appsearch.icing.proto.DebugInfoProto;
import com.android.server.appsearch.icing.proto.DebugInfoVerbosity;
import com.android.server.appsearch.icing.proto.DocumentDebugInfoProto;
import com.android.server.appsearch.icing.proto.DocumentStorageInfoProto;
import com.android.server.appsearch.icing.proto.NamespaceStorageInfoProto;
import com.android.server.appsearch.icing.proto.PropertyConfigProto;
import com.android.server.appsearch.icing.proto.SchemaDebugInfoProto;
import com.android.server.appsearch.icing.proto.SchemaProto;
import com.android.server.appsearch.icing.proto.SchemaTypeConfigProto;
import com.android.server.appsearch.util.AdbDumpUtil;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AdbDumpUtilTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private static final String NAMESPACE_1 = "namespace1";
    private static final String NAMESPACE_2 = "namespace2";
    private static final String NAMESPACE_1_MD5 = "612897F0683DD83BE850731B14801182";
    private static final String NAMESPACE_2_MD5 = "6FDE1BA4470CACA933058264DBFDEA2E";

    private static final String EMAIL_TYPE = "email";
    private static final String PERSON_TYPE = "person";
    private static final String EMAIL_TYPE_MD5 = "0C83F57C786A0B4A39EFAB23731C7EBC";
    private static final String PERSON_TYPE_MD5 = "8B0A44048F58988B486BDD0D245B22A8";

    @Test
    public void testDesensitizeDebugInfo() {
        DocumentDebugInfoProto.Builder originalDocumentInfoBuilder =
                DocumentDebugInfoProto.newBuilder().addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_1).setSchema(EMAIL_TYPE)).addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_1).setSchema(PERSON_TYPE)).addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_2).setSchema(EMAIL_TYPE)).addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_2).setSchema(PERSON_TYPE)).setDocumentStorageInfo(
                        DocumentStorageInfoProto.newBuilder().addNamespaceStorageInfo(
                                NamespaceStorageInfoProto.newBuilder().setNamespace(
                                        NAMESPACE_1)).addNamespaceStorageInfo(
                                NamespaceStorageInfoProto.newBuilder().setNamespace(NAMESPACE_2)));
        DocumentDebugInfoProto.Builder desensitizedDocumentInfoBuilder =
                DocumentDebugInfoProto.newBuilder().addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_1_MD5).setSchema(EMAIL_TYPE_MD5)).addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_1_MD5).setSchema(PERSON_TYPE_MD5)).addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_2_MD5).setSchema(EMAIL_TYPE_MD5)).addCorpusInfo(
                        DocumentDebugInfoProto.CorpusInfo.newBuilder().setNamespace(
                                NAMESPACE_2_MD5).setSchema(PERSON_TYPE_MD5)).setDocumentStorageInfo(
                        DocumentStorageInfoProto.newBuilder().addNamespaceStorageInfo(
                                NamespaceStorageInfoProto.newBuilder().setNamespace(
                                        NAMESPACE_1_MD5)).addNamespaceStorageInfo(
                                NamespaceStorageInfoProto.newBuilder().setNamespace(
                                        NAMESPACE_2_MD5)));

        SchemaDebugInfoProto.Builder originalSchemaInfoBuilder =
                SchemaDebugInfoProto.newBuilder().setSchema(SchemaProto.newBuilder().addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType(EMAIL_TYPE).addProperties(
                                PropertyConfigProto.newBuilder().setDataType(
                                        PropertyConfigProto.DataType.Code.DOCUMENT).setSchemaType(
                                        PERSON_TYPE).setPropertyName("sender")).addProperties(
                                PropertyConfigProto.newBuilder().setDataType(
                                        PropertyConfigProto.DataType.Code.STRING).setPropertyName(
                                        "subject"))).addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType(PERSON_TYPE).addProperties(
                                PropertyConfigProto.newBuilder().setDataType(
                                        PropertyConfigProto.DataType.Code.STRING).setPropertyName(
                                        "name"))));
        SchemaDebugInfoProto.Builder desensitizedSchemaInfoBuilder =
                SchemaDebugInfoProto.newBuilder().setSchema(SchemaProto.newBuilder().addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType(
                                EMAIL_TYPE_MD5).addProperties(
                                PropertyConfigProto.newBuilder().setDataType(
                                        PropertyConfigProto.DataType.Code.DOCUMENT).setSchemaType(
                                        PERSON_TYPE_MD5).setPropertyName("sender")).addProperties(
                                PropertyConfigProto.newBuilder().setDataType(
                                        PropertyConfigProto.DataType.Code.STRING).setPropertyName(
                                        "subject"))).addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType(
                                PERSON_TYPE_MD5).addProperties(
                                PropertyConfigProto.newBuilder().setDataType(
                                        PropertyConfigProto.DataType.Code.STRING).setPropertyName(
                                        "name"))));

        DebugInfoProto originalDebugInfoProto = DebugInfoProto.newBuilder().setDocumentInfo(
                originalDocumentInfoBuilder).setSchemaInfo(originalSchemaInfoBuilder).build();
        DebugInfoProto desensitizedDebugInfoProto = DebugInfoProto.newBuilder().setDocumentInfo(
                desensitizedDocumentInfoBuilder).setSchemaInfo(
                desensitizedSchemaInfoBuilder).build();

        assertThat(AdbDumpUtil.desensitizeDebugInfo(originalDebugInfoProto)).isEqualTo(
                desensitizedDebugInfoProto);
    }

    @Test
    public void testDesensitizeRealDebugInfo() throws Exception {
        AppSearchImpl appSearchImpl = AppSearchImpl.create(mTemporaryFolder.newFolder(),
                new FakeAppSearchConfig(),
                /*initStatsBuilder=*/ null, /*visibilityChecker=*/ null,
                optimizeInfo -> true);
        List<AppSearchSchema> schemas = Collections.singletonList(new AppSearchSchema.Builder(
                PERSON_TYPE).addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                "name").setIndexingType(
                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES).setTokenizerType(
                AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN).build()).build());
        appSearchImpl.setSchema("adbdump_package", "adbdump_database", schemas,
                /*visibilityDocuments=*/ Collections.emptyList(),
                /*forceOverride=*/ false,
                /*version=*/ 0,
                /* setSchemaStatsBuilder= */ null);
        GenericDocument person = new GenericDocument.Builder<>("adbdump_namespace",
                "adbdump_doc_id", PERSON_TYPE).setPropertyString("name",
                "adbdump test person").build();
        appSearchImpl.putDocument("adbdump_package", "adbdump_database", person,
                /*sendChangeNotifications=*/ false, /*logger=*/ null);

        DebugInfoProto originalDebugInfoProto = appSearchImpl.getRawDebugInfoProto(
                DebugInfoVerbosity.Code.DETAILED);
        String originalDebugString = originalDebugInfoProto.toString();
        assertThat(originalDebugString).contains("adbdump_namespace");
        assertThat(originalDebugString).contains("adbdump_package");
        assertThat(originalDebugString).contains("adbdump_database");
        assertThat(originalDebugString).doesNotContain("adbdump_doc_id");

        DebugInfoProto desensitizedDebugInfoProto = AdbDumpUtil.desensitizeDebugInfo(
                originalDebugInfoProto);
        String desensitizedDebugString = desensitizedDebugInfoProto.toString();
        assertThat(desensitizedDebugString).doesNotContain("adbdump_namespace");
        assertThat(desensitizedDebugString).doesNotContain("adbdump_package");
        assertThat(desensitizedDebugString).doesNotContain("adbdump_database");
        assertThat(desensitizedDebugString).doesNotContain("adbdump_doc_id");
    }
}
