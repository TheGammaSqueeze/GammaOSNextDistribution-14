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

package com.android.adservices.service.measurement.aggregation;

import static com.android.adservices.service.measurement.PrivacyParams.AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE;
import static com.android.adservices.service.measurement.PrivacyParams.AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE;

import android.annotation.Nullable;

import androidx.annotation.NonNull;

import com.android.adservices.HpkeJni;
import com.android.adservices.LoggerFactory;
import com.android.adservices.service.exception.CryptoException;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

/** Encryption converter for aggregate data */
public class AggregateCryptoConverter {

    private static final Base64.Encoder sBase64Encoder = Base64.getEncoder();
    private static final Base64.Decoder sBase64Decoder = Base64.getDecoder();

    /**
     * Aggregate payload encryption. The payload is encrypted with the following steps: 1. Extracts
     * Histogram Contributions 2. Encode with CBOR 3. Retrieve public key for encryption 4. Encrypt
     * with HPKE 5. Encode with Base64
     *
     * @param payload json string of histogram data. Example: {operation: histogram, data: [bucket:
     *     1, value: 2]}
     * @param sharedInfo plain value that will be shared with receiver
     * @throws CryptoException if any exception is encountered
     */
    public static String encrypt(
            @NonNull String publicKeyBase64Encoded,
            @NonNull String payload,
            @Nullable String sharedInfo)
            throws CryptoException {
        try {
            Objects.requireNonNull(payload);
            Objects.requireNonNull(publicKeyBase64Encoded);

            // Extract Histogram
            final List<AggregateHistogramContribution> contributions = convert(payload);
            if (contributions.isEmpty()) {
                throw new CryptoException("No histogram found");
            }

            // Encode with Cbor
            final byte[] payloadCborEncoded = encodeWithCbor(contributions);

            // Get public key
            final byte[] publicKey = sBase64Decoder.decode(publicKeyBase64Encoded);

            final byte[] contextInfo;
            if (sharedInfo == null) {
                contextInfo = "aggregation_service".getBytes();
            } else {
                contextInfo = ("aggregation_service" + sharedInfo).getBytes();
            }

            // Encrypt with HPKE
            final byte[] payloadEncrypted =
                    encryptWithHpke(publicKey, payloadCborEncoded, contextInfo);
            if (payloadEncrypted == null) {
                throw new CryptoException("Payload not hpke encrypted");
            }

            // Encode with Base 64
            return encodeWithBase64(payloadEncrypted);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().e(e, "Encryption error");
            throw new CryptoException("Encryption error", e);
        }
    }

    /**
     * Same as {@link AggregateCryptoConverter#encrypt(String, String, String)}, but without hpke
     * encryption
     */
    public static String encode(@NonNull String payload) {
        try {
            Objects.requireNonNull(payload);

            // Extract Histogram
            final List<AggregateHistogramContribution> contributions = convert(payload);
            if (contributions.isEmpty()) {
                throw new CryptoException("No histogram found");
            }

            // Encode with Cbor
            final byte[] payloadCborEncoded = encodeWithCbor(contributions);

            // Encode with Base 64
            return encodeWithBase64(payloadCborEncoded);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().e(e, "Encoding error");
            throw new CryptoException("Encoding error", e);
        }
    }

    @VisibleForTesting
    static List<AggregateHistogramContribution> convert(String payload) {
        final List<AggregateHistogramContribution> contributions = new ArrayList<>();
        try {
            final JSONObject jsonObject = new JSONObject(payload);
            final JSONArray jsonArray = jsonObject.getJSONArray("data");
            if (null == jsonArray || jsonArray.length() == 0) {
                LoggerFactory.getMeasurementLogger().d("No histogram 'data' found");
                return contributions;
            }

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject dataObject = jsonArray.getJSONObject(i);
                String bucket = dataObject.getString("bucket");
                String value = dataObject.getString("value");
                contributions.add(
                        new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger(bucket))
                                .setValue(Integer.parseInt(value))
                                .build());
            }
            return contributions;
        } catch (NumberFormatException | JSONException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Malformed histogram payload");
            return contributions;
        }
    }

    @VisibleForTesting
    static byte[] encodeWithCbor(List<AggregateHistogramContribution> contributions)
            throws CborException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final CborBuilder cborBuilder = new CborBuilder();

        final Map payloadMap = new Map();
        final Array dataArray = new Array();

        for (AggregateHistogramContribution contribution : contributions) {
            final byte[] value =
                    ByteBuffer.allocate(AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE)
                            .putInt(contribution.getValue())
                            .array();
            final byte[] bucket = new byte[AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE];
            final byte[] src = contribution.getKey().toByteArray();
            final int bytesExcludingSign = (int) Math.ceil(contribution.getKey().bitLength() / 8d);
            final int length = Math.min(bytesExcludingSign, AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE);
            final int position = bucket.length - length;
            // Excluding sign bit that BigInteger#toByteArray adds to the first element of the array
            final int srcPosExcludingSign = src[0] == 0 ? 1 : 0;
            System.arraycopy(src, srcPosExcludingSign, bucket, position, length);

            final Map dataMap = new Map();
            dataMap.put(new UnicodeString("bucket"), new ByteString(bucket));
            dataMap.put(new UnicodeString("value"), new ByteString(value));
            dataArray.add(dataMap);
        }
        payloadMap.put(new UnicodeString("operation"), new UnicodeString("histogram"));
        payloadMap.put(new UnicodeString("data"), dataArray);

        new CborEncoder(outputStream).encode(cborBuilder.add(payloadMap).build());
        return outputStream.toByteArray();
    }

    @VisibleForTesting
    static byte[] encryptWithHpke(byte[] publicKey, byte[] plainText, byte[] contextInfo) {
        return HpkeJni.encrypt(publicKey, plainText, contextInfo);
    }

    @VisibleForTesting
    static String encodeWithBase64(byte[] value) {
        return sBase64Encoder.encodeToString(value);
    }
}
