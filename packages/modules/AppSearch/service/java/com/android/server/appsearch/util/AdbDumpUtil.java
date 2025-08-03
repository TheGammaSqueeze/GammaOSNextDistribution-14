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

package com.android.server.appsearch.util;

import android.annotation.NonNull;
import android.util.Log;

import com.google.android.icing.proto.DebugInfoProto;
import com.google.android.icing.proto.DocumentDebugInfoProto;
import com.google.android.icing.proto.DocumentStorageInfoProto;
import com.google.android.icing.proto.NamespaceStorageInfoProto;
import com.google.android.icing.proto.PropertyConfigProto;
import com.google.android.icing.proto.SchemaDebugInfoProto;
import com.google.android.icing.proto.SchemaProto;
import com.google.android.icing.proto.SchemaTypeConfigProto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * A utility class for helper methods to process {@link DebugInfoProto}.
 */
public class AdbDumpUtil {
    private static final String TAG = "AppSearchAdbDumpUtil";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Generate MD5 hash to help anonymize some string fields in {@link DebugInfoProto}.
     *
     * @param str The original string.
     * @return The hash value of str in hex format.
     */
    @NonNull
    public static String generateFingerprintMd5(@NonNull String str) {
        Objects.requireNonNull(str);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();
            char[] hexChars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xFF;
                hexChars[i * 2] = HEX_ARRAY[v >>> 4];
                hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to generate fingerprint");
        }
        return "";
    }

    /**
     * Anonymize some privacy-sensitive string fields in {@link DebugInfoProto}.
     *
     * @param debugInfo The original {@link DebugInfoProto} to be desensitized.
     * @return The desensitized {@link DebugInfoProto}.
     */
    @NonNull
    public static DebugInfoProto desensitizeDebugInfo(@NonNull DebugInfoProto debugInfo) {
        Objects.requireNonNull(debugInfo);

        DebugInfoProto.Builder debugInfoBuilder = debugInfo.toBuilder();
        DocumentDebugInfoProto.Builder documentInfoBuilder =
                debugInfoBuilder.getDocumentInfo().toBuilder();

        for (int i = 0; i < documentInfoBuilder.getCorpusInfoCount(); ++i) {
            DocumentDebugInfoProto.CorpusInfo.Builder corpusInfoBuilder =
                    documentInfoBuilder.getCorpusInfo(i).toBuilder();
            corpusInfoBuilder.setNamespace(
                    generateFingerprintMd5(corpusInfoBuilder.getNamespace()));
            corpusInfoBuilder.setSchema(generateFingerprintMd5(corpusInfoBuilder.getSchema()));
            documentInfoBuilder.setCorpusInfo(i, corpusInfoBuilder);
        }

        DocumentStorageInfoProto.Builder documentStorageInfoBuilder =
                documentInfoBuilder.getDocumentStorageInfo().toBuilder();
        for (int i = 0; i < documentStorageInfoBuilder.getNamespaceStorageInfoCount(); ++i) {
            NamespaceStorageInfoProto.Builder namespaceStorageInfoBuilder =
                    documentStorageInfoBuilder.getNamespaceStorageInfo(i).toBuilder();
            namespaceStorageInfoBuilder.setNamespace(
                    generateFingerprintMd5(namespaceStorageInfoBuilder.getNamespace()));
            documentStorageInfoBuilder.setNamespaceStorageInfo(i, namespaceStorageInfoBuilder);
        }
        documentInfoBuilder.setDocumentStorageInfo(documentStorageInfoBuilder);

        debugInfoBuilder.setDocumentInfo(documentInfoBuilder);

        SchemaDebugInfoProto.Builder schemaInfoBuilder =
                debugInfoBuilder.getSchemaInfo().toBuilder();
        SchemaProto.Builder schemaBuilder = schemaInfoBuilder.getSchema().toBuilder();
        for (int i = 0; i < schemaBuilder.getTypesCount(); ++i) {
            SchemaTypeConfigProto.Builder typeBuilder = schemaBuilder.getTypes(i).toBuilder();
            typeBuilder.setSchemaType(generateFingerprintMd5(typeBuilder.getSchemaType()));
            for (int j = 0; j < typeBuilder.getPropertiesCount(); ++j) {
                PropertyConfigProto property = typeBuilder.getProperties(j);
                if (property.getDataType() == PropertyConfigProto.DataType.Code.DOCUMENT) {
                    PropertyConfigProto.Builder propertyBuilder = property.toBuilder();
                    propertyBuilder.setSchemaType(
                            generateFingerprintMd5(propertyBuilder.getSchemaType()));
                    typeBuilder.setProperties(j, propertyBuilder);
                }
            }
            schemaBuilder.setTypes(i, typeBuilder);
        }
        schemaInfoBuilder.setSchema(schemaBuilder);
        debugInfoBuilder.setSchemaInfo(schemaInfoBuilder);
        return debugInfoBuilder.build();
    }
}
