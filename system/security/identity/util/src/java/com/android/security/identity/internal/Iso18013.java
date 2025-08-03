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

package com.android.security.identity.internal;

import static com.android.security.identity.internal.Util.CBOR_SEMANTIC_TAG_ENCODED_CBOR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

/**
 * Various utilities for working with the ISO mobile driving license (mDL)
 * application specification (ISO 18013-5).
 */
public class Iso18013 {
    /**
     * Each version of the spec is namespaced, and all namespace-specific constants
     * are thus collected into a namespace-specific nested class.
     */
    public static class V1 {
        public static final String NAMESPACE = "org.iso.18013.5.1";
        public static final String DOC_TYPE = "org.iso.18013.5.1.mdl";

        public static final String FAMILY_NAME = "family_name";
        public static final String GIVEN_NAME = "given_name";
        public static final String BIRTH_DATE = "birth_date";
        public static final String ISSUE_DATE = "issue_date";
        public static final String EXPIRY = "expiry_date";
        public static final String ISSUING_COUNTRY = "issuing_country";
        public static final String ISSUING_AUTHORITY = "issuing_authority";
        public static final String DOCUMENT_NUMBER = "document_number";
        public static final String PORTRAIT = "portrait";
        public static final String DRIVING_PRIVILEGES = "driving_privileges";
        public static final String UN_DISTINGUISHING_SIGN = "un_distinguishing_sign";
        public static final String HEIGHT = "height";
        public static final String BIO_FACE = "biometric_template_face";

        public static String ageOver(int age) {
            if (age < 0 || age > 99) {
                throw new InvalidParameterException("age must be between 0 and 99, inclusive");
            }
            return String.format("age_over_%02d", age);
        }
    }

    public static byte[] buildDeviceAuthenticationCbor(String docType,
            byte[] encodedSessionTranscript,
            byte[] deviceNameSpacesBytes) {
        ByteArrayOutputStream daBaos = new ByteArrayOutputStream();
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(encodedSessionTranscript);
            List<DataItem> dataItems = null;
            dataItems = new CborDecoder(bais).decode();
            DataItem sessionTranscript = dataItems.get(0);
            ByteString deviceNameSpacesBytesItem = new ByteString(deviceNameSpacesBytes);
            deviceNameSpacesBytesItem.setTag(CBOR_SEMANTIC_TAG_ENCODED_CBOR);
            new CborEncoder(daBaos).encode(new CborBuilder()
                    .addArray()
                    .add("DeviceAuthentication")
                    .add(sessionTranscript)
                    .add(docType)
                    .add(deviceNameSpacesBytesItem)
                    .end()
                    .build());
        } catch (CborException e) {
            throw new RuntimeException("Error encoding DeviceAuthentication", e);
        }
        return daBaos.toByteArray();
    }

    public static byte[] buildReaderAuthenticationBytesCbor(
            byte[] encodedSessionTranscript,
            byte[] requestMessageBytes) {

        ByteArrayOutputStream daBaos = new ByteArrayOutputStream();
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(encodedSessionTranscript);
            List<DataItem> dataItems = null;
            dataItems = new CborDecoder(bais).decode();
            DataItem sessionTranscript = dataItems.get(0);
            ByteString requestMessageBytesItem = new ByteString(requestMessageBytes);
            requestMessageBytesItem.setTag(CBOR_SEMANTIC_TAG_ENCODED_CBOR);
            new CborEncoder(daBaos).encode(new CborBuilder()
                    .addArray()
                    .add("ReaderAuthentication")
                    .add(sessionTranscript)
                    .add(requestMessageBytesItem)
                    .end()
                    .build());
        } catch (CborException e) {
            throw new RuntimeException("Error encoding ReaderAuthentication", e);
        }
        byte[] readerAuthentication = daBaos.toByteArray();
        return Util.prependSemanticTagForEncodedCbor(readerAuthentication);
    }

    // This returns a SessionTranscript which satisfy the requirement
    // that the uncompressed X and Y coordinates of the public key for the
    // mDL's ephemeral key-pair appear somewhere in the encoded
    // DeviceEngagement.
    public static byte[] buildSessionTranscript(KeyPair ephemeralKeyPair) {
        // Make the coordinates appear in an already encoded bstr - this
        // mimics how the mDL COSE_Key appear as encoded data inside the
        // encoded DeviceEngagement
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(new byte[]{41});

            ECPoint w = ((ECPublicKey) ephemeralKeyPair.getPublic()).getW();
            baos.write(Util.convertP256PublicKeyToDERFormat(w));

            baos.write(new byte[]{42, 44});
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        byte[] blobWithCoords = baos.toByteArray();

        baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                    .add(blobWithCoords)
                    .end()
                    .build());
        } catch (CborException e) {
            e.printStackTrace();
            return null;
        }
        ByteString encodedDeviceEngagementItem = new ByteString(baos.toByteArray());
        ByteString encodedEReaderKeyItem = new ByteString(Util.cborEncodeString("doesn't matter"));
        encodedDeviceEngagementItem.setTag(CBOR_SEMANTIC_TAG_ENCODED_CBOR);
        encodedEReaderKeyItem.setTag(CBOR_SEMANTIC_TAG_ENCODED_CBOR);

        baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                    .add(encodedDeviceEngagementItem)
                    .add(encodedEReaderKeyItem)
                    .end()
                    .build());
        } catch (CborException e) {
            e.printStackTrace();
            return null;
        }
        return baos.toByteArray();
    }

    /*
     * Helper function to create a CBOR data for requesting data items. The IntentToRetain
     * value will be set to false for all elements.
     *
     * <p>The returned CBOR data conforms to the following CDDL schema:</p>
     *
     * <pre>
     *   ItemsRequest = {
     *     ? "docType" : DocType,
     *     "nameSpaces" : NameSpaces,
     *     ? "RequestInfo" : {* tstr => any} ; Additional info the reader wants to provide
     *   }
     *
     *   NameSpaces = {
     *     + NameSpace => DataElements     ; Requested data elements for each NameSpace
     *   }
     *
     *   DataElements = {
     *     + DataElement => IntentToRetain
     *   }
     *
     *   DocType = tstr
     *
     *   DataElement = tstr
     *   IntentToRetain = bool
     *   NameSpace = tstr
     * </pre>
     *
     * @param entriesToRequest       The entries to request, organized as a map of namespace
     *                               names with each value being a collection of data elements
     *                               in the given namespace.
     * @param docType                  The document type or {@code null} if there is no document
     *                                 type.
     * @return CBOR data conforming to the CDDL mentioned above.
     */
    public static @NonNull
    byte[] createItemsRequest(
            @NonNull Map<String, Collection<String>> entriesToRequest,
            @Nullable String docType) {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = builder.addMap();
        if (docType != null) {
            mapBuilder.put("docType", docType);
        }

        MapBuilder<MapBuilder<CborBuilder>> nsMapBuilder = mapBuilder.putMap("nameSpaces");
        for (String namespaceName : entriesToRequest.keySet()) {
            Collection<String> entryNames = entriesToRequest.get(namespaceName);
            MapBuilder<MapBuilder<MapBuilder<CborBuilder>>> entryNameMapBuilder =
                    nsMapBuilder.putMap(namespaceName);
            for (String entryName : entryNames) {
                entryNameMapBuilder.put(entryName, false);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborEncoder encoder = new CborEncoder(baos);
        try {
            encoder.encode(builder.build());
        } catch (CborException e) {
            throw new RuntimeException("Error encoding CBOR", e);
        }
        return baos.toByteArray();
    }

    public static SecretKey calcEMacKeyForReader(PublicKey authenticationPublicKey,
            PrivateKey ephemeralReaderPrivateKey,
            byte[] encodedSessionTranscript) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ephemeralReaderPrivateKey);
            ka.doPhase(authenticationPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            byte[] sessionTranscriptBytes =
                    Util.cborEncode(Util.buildCborTaggedByteString(encodedSessionTranscript));

            byte[] salt = MessageDigest.getInstance("SHA-256").digest(sessionTranscriptBytes);
            byte[] info = new byte[]{'E', 'M', 'a', 'c', 'K', 'e', 'y'};
            byte[] derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);

            SecretKey secretKey = new SecretKeySpec(derivedKey, "");
            return secretKey;
        } catch (InvalidKeyException
                | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error performing key agreement", e);
        }
    }
}
