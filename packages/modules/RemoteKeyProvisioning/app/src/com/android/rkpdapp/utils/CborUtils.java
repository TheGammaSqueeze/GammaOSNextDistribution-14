/**
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

package com.android.rkpdapp.utils;

import android.content.Context;
import android.hardware.security.keymint.MacedPublicKey;
import android.os.Build;
import android.util.Log;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.database.RkpKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

public class CborUtils {
    public static final int EC_CURVE_P256 = 1;
    public static final int EC_CURVE_25519 = 2;

    public static final String EXTRA_KEYS = "num_extra_attestation_keys";
    public static final String TIME_TO_REFRESH = "time_to_refresh_hours";
    public static final String PROVISIONING_URL = "provisioning_url";

    private static final int RESPONSE_CERT_ARRAY_INDEX = 0;
    private static final int RESPONSE_ARRAY_SIZE = 1;

    private static final int SHARED_CERTIFICATES_INDEX = 0;
    private static final int UNIQUE_CERTIFICATES_INDEX = 1;
    private static final int CERT_ARRAY_ENTRIES = 2;

    private static final int EEK_AND_CURVE_INDEX = 0;
    private static final int CHALLENGE_INDEX = 1;
    private static final int CONFIG_INDEX = 2;

    private static final int CURVE_AND_EEK_CHAIN_LENGTH = 2;
    private static final int CURVE_INDEX = 0;
    private static final int EEK_CERT_CHAIN_INDEX = 1;

    private static final int EEK_ARRAY_ENTRIES_NO_CONFIG = 2;
    private static final int EEK_ARRAY_ENTRIES_WITH_CONFIG = 3;
    private static final String TAG = "RkpdCborUtils";
    private static final byte[] EMPTY_MAP = new byte[] {(byte) 0xA0};
    private static final int KEY_PARAMETER_X = -2;
    private static final int KEY_PARAMETER_Y = -3;
    private static final int COSE_HEADER_ALGORITHM = 1;
    private static final int COSE_ALGORITHM_HMAC_256 = 5;

    /**
     * Parses the signed certificate chains returned by the server. In order to reduce data use over
     * the wire, shared certificate chain prefixes are separated from the remaining unique portions
     * of each individual certificate chain. This method first parses the shared prefix certificates
     * and then prepends them to each unique certificate chain. Each PEM-encoded certificate chain
     * is returned in a byte array.
     *
     * @param serverResp The CBOR blob received from the server which contains all signed
     *                      certificate chains.
     *
     * @return A List object where each byte[] entry is an entire DER-encoded certificate chain.
     */
    public static List<byte[]> parseSignedCertificates(byte[] serverResp) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(serverResp);
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            if (dataItems.size() != RESPONSE_ARRAY_SIZE
                    || !checkType(dataItems.get(RESPONSE_CERT_ARRAY_INDEX),
                                  MajorType.ARRAY, "CborResponse")) {
                Log.e(TAG, "Improper formatting of CBOR response. Expected size 1. Actual: "
                            + dataItems.size());
                return null;
            }
            dataItems = ((Array) dataItems.get(RESPONSE_CERT_ARRAY_INDEX)).getDataItems();
            if (dataItems.size() != CERT_ARRAY_ENTRIES) {
                Log.e(TAG, "Incorrect number of certificate array entries. Expected: 2. Actual: "
                            + dataItems.size());
                return null;
            }
            if (!checkType(dataItems.get(SHARED_CERTIFICATES_INDEX),
                           MajorType.BYTE_STRING, "SharedCertificates")
                    || !checkType(dataItems.get(UNIQUE_CERTIFICATES_INDEX),
                                  MajorType.ARRAY, "UniqueCertificates")) {
                return null;
            }
            byte[] sharedCertificates =
                    ((ByteString) dataItems.get(SHARED_CERTIFICATES_INDEX)).getBytes();
            Array uniqueCertificates = (Array) dataItems.get(UNIQUE_CERTIFICATES_INDEX);
            List<byte[]> uniqueCertificateChains = new ArrayList<>();
            for (DataItem entry : uniqueCertificates.getDataItems()) {
                if (!checkType(entry, MajorType.BYTE_STRING, "UniqueCertificate")) {
                    return null;
                }
                ByteArrayOutputStream concat = new ByteArrayOutputStream();
                // DER encoding specifies certificate chains ordered from leaf to root.
                concat.write(((ByteString) entry).getBytes());
                concat.write(sharedCertificates);
                uniqueCertificateChains.add(concat.toByteArray());
            }
            return uniqueCertificateChains;
        } catch (CborException e) {
            Log.e(TAG, "CBOR decoding failed.", e);
        } catch (IOException e) {
            Log.e(TAG, "Writing bytes failed.", e);
        }
        return null;
    }

    private static boolean checkType(DataItem item, MajorType majorType, String field) {
        if (item.getMajorType() != majorType) {
            Log.e(TAG, "Incorrect CBOR type for field: " + field + ". Expected " + majorType.name()
                        + ". Actual: " + item.getMajorType().name());
            return false;
        }
        return true;
    }

    private static boolean parseDeviceConfig(GeekResponse resp, DataItem deviceConfig) {
        if (!checkType(deviceConfig, MajorType.MAP, "DeviceConfig")) {
            return false;
        }
        Map deviceConfiguration = (Map) deviceConfig;
        DataItem extraKeys =
                deviceConfiguration.get(new UnicodeString(EXTRA_KEYS));
        DataItem timeToRefreshHours =
                deviceConfiguration.get(new UnicodeString(TIME_TO_REFRESH));
        DataItem newUrl =
                deviceConfiguration.get(new UnicodeString(PROVISIONING_URL));
        if (extraKeys != null) {
            if (!checkType(extraKeys, MajorType.UNSIGNED_INTEGER, "ExtraKeys")) {
                return false;
            }
            resp.numExtraAttestationKeys = ((UnsignedInteger) extraKeys).getValue().intValue();
        }
        if (timeToRefreshHours != null) {
            if (!checkType(timeToRefreshHours, MajorType.UNSIGNED_INTEGER, "TimeToRefresh")) {
                return false;
            }
            resp.timeToRefresh =
                    Duration.ofHours(((UnsignedInteger) timeToRefreshHours).getValue().intValue());
        }
        if (newUrl != null) {
            if (!checkType(newUrl, MajorType.UNICODE_STRING, "ProvisioningURL")) {
                return false;
            }
            resp.provisioningUrl = ((UnicodeString) newUrl).getString();
        }
        return true;
    }

    /**
     * Parses the Google Endpoint Encryption Key response provided by the server which contains a
     * Google signed EEK and a challenge for use by the underlying IRemotelyProvisionedComponent HAL
     */
    public static GeekResponse parseGeekResponse(byte[] serverResp) {
        try {
            GeekResponse resp = new GeekResponse();
            ByteArrayInputStream bais = new ByteArrayInputStream(serverResp);
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            if (dataItems.size() != RESPONSE_ARRAY_SIZE
                    || !checkType(dataItems.get(RESPONSE_CERT_ARRAY_INDEX),
                                  MajorType.ARRAY, "CborResponse")) {
                Log.e(TAG, "Improper formatting of CBOR response. Expected size 1. Actual: "
                            + dataItems.size());
                return null;
            }
            List<DataItem> respItems =
                    ((Array) dataItems.get(RESPONSE_CERT_ARRAY_INDEX)).getDataItems();
            if (respItems.size() != EEK_ARRAY_ENTRIES_NO_CONFIG
                    && respItems.size() != EEK_ARRAY_ENTRIES_WITH_CONFIG) {
                Log.e(TAG, "Incorrect number of certificate array entries. Expected: "
                            + EEK_ARRAY_ENTRIES_NO_CONFIG + " or " + EEK_ARRAY_ENTRIES_WITH_CONFIG
                            + ". Actual: " + respItems.size());
                return null;
            }
            if (!checkType(respItems.get(EEK_AND_CURVE_INDEX), MajorType.ARRAY, "EekAndCurveArr")) {
                return null;
            }
            List<DataItem> curveAndEekChains =
                    ((Array) respItems.get(EEK_AND_CURVE_INDEX)).getDataItems();
            for (int i = 0; i < curveAndEekChains.size(); i++) {
                if (!checkType(curveAndEekChains.get(i), MajorType.ARRAY, "EekAndCurve")) {
                    return null;
                }
                List<DataItem> curveAndEekChain =
                        ((Array) curveAndEekChains.get(i)).getDataItems();
                if (curveAndEekChain.size() != CURVE_AND_EEK_CHAIN_LENGTH) {
                    Log.e(TAG, "Wrong size. Expected: " + CURVE_AND_EEK_CHAIN_LENGTH + ". Actual: "
                               + curveAndEekChain.size());
                    return null;
                }
                if (!checkType(curveAndEekChain.get(CURVE_INDEX),
                               MajorType.UNSIGNED_INTEGER, "Curve")
                        || !checkType(curveAndEekChain.get(EEK_CERT_CHAIN_INDEX),
                                                           MajorType.ARRAY, "EekCertChain")) {
                    return null;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                new CborEncoder(baos).encode(curveAndEekChain.get(EEK_CERT_CHAIN_INDEX));
                UnsignedInteger curve = (UnsignedInteger) curveAndEekChain.get(CURVE_INDEX);
                resp.addGeek(curve.getValue().intValue(), baos.toByteArray());
            }
            if (!checkType(respItems.get(CHALLENGE_INDEX), MajorType.BYTE_STRING, "Challenge")) {
                return null;
            }
            resp.setChallenge(((ByteString) respItems.get(CHALLENGE_INDEX)).getBytes());
            if (respItems.size() == EEK_ARRAY_ENTRIES_WITH_CONFIG
                    && !parseDeviceConfig(resp, respItems.get(CONFIG_INDEX))) {
                return null;
            }
            return resp;
        } catch (CborException e) {
            Log.e(TAG, "CBOR parsing/serializing failed.", e);
            return null;
        }
    }

    /**
     * Creates the bundle of data that the server needs in order to make a decision over what
     * device configuration values to return. In general, this boils down to if remote provisioning
     * is turned on at all or not.
     *
     * @return the CBOR encoded provisioning information relevant to the server.
     */
    public static byte[] buildProvisioningInfo(Context context) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(new CborBuilder()
                    .addMap()
                        .put("fingerprint", Build.FINGERPRINT)
                        .put(new UnicodeString("id"),
                             new UnsignedInteger(Settings.getId(context)))
                        .end()
                    .build());
            return baos.toByteArray();
        } catch (CborException e) {
            Log.e(TAG, "CBOR serialization failed.", e);
            return EMPTY_MAP;
        }
    }

    /**
     * Takes the various fields fetched from the server and the remote provisioning service and
     * formats them in the CBOR blob the server is expecting as defined by the
     * IRemotelyProvisionedComponent HAL AIDL files.
     */
    public static byte[] buildCertificateRequest(byte[] deviceInfo, byte[] challenge,
            byte[] protectedData, byte[] macedKeysToSign, Map unverifiedDeviceInfo)
            throws RkpdException {
        // This CBOR library doesn't support adding already serialized CBOR structures into a
        // CBOR builder. Because of this, we have to first deserialize the provided parameters
        // back into the library's CBOR object types, and then reserialize them into the
        // desired structure.
        try {
            Array protectedDataArray = (Array) decodeCbor(protectedData, "ProtectedData",
                    MajorType.ARRAY);
            Array macedKeysToSignArray = (Array) decodeCbor(macedKeysToSign, "MacedKeysToSign",
                    MajorType.ARRAY);
            Map verifiedDeviceInfoMap = (Map) decodeCbor(deviceInfo, "DeviceInfo", MajorType.MAP);

            if (unverifiedDeviceInfo.get(new UnicodeString("fingerprint")) == null) {
                Log.e(TAG, "UnverifiedDeviceInfo is missing a fingerprint entry");
                throw new RkpdException(RkpdException.ErrorCode.INTERNAL_ERROR,
                        "UnverifiedDeviceInfo missing fingerprint entry.");
            }
            // Serialize the actual CertificateSigningRequest structure
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                        .addArray()
                            .add(verifiedDeviceInfoMap)
                            .add(unverifiedDeviceInfo)
                            .end()
                        .add(challenge)
                        .add(protectedDataArray)
                        .add(macedKeysToSignArray)
                        .end()
                    .build());
            return baos.toByteArray();
        } catch (CborException e) {
            Log.e(TAG, "Malformed CBOR", e);
            throw new RkpdException(RkpdException.ErrorCode.INTERNAL_ERROR, "Malformed CBOR", e);
        }
    }

    /**
     * Produce a CBOR Map object which contains the unverified device information for a certificate
     * signing request.
     *
     * @return the CBOR Map object.
     */
    public static Map buildUnverifiedDeviceInfo() {
        Map unverifiedDeviceInfo = new Map();
        unverifiedDeviceInfo.put(new UnicodeString("fingerprint"),
                                    new UnicodeString(Build.FINGERPRINT));
        return unverifiedDeviceInfo;
    }

    /**
     * Extracts provisioned key for storage from Maced key pair received from underlying binder
     * service.
     */
    public static RkpKey extractRkpKeyFromMacedKey(byte[] privKey, String serviceName,
            MacedPublicKey macedPublicKey) throws CborException, RkpdException {
        Array cborMessage = (Array) decodeCbor(macedPublicKey.macedKey, "MacedPublicKeys",
                MajorType.ARRAY);
        List<DataItem> messageArray = cborMessage.getDataItems();
        byte[] macedMessage = getBytesFromBstr(messageArray.get(2));
        Map keyMap = (Map) decodeCbor(macedMessage, "byte stream", MajorType.MAP);
        byte[] xCor = ((ByteString) keyMap.get(new NegativeInteger(KEY_PARAMETER_X))).getBytes();
        if (xCor.length != 32) {
            throw new IllegalStateException("COSE_Key x-coordinate is not correct.");
        }
        byte[] yCor = ((ByteString) keyMap.get(new NegativeInteger(KEY_PARAMETER_Y))).getBytes();
        if (yCor.length != 32) {
            throw new IllegalStateException("COSE_Key y-coordinate is not correct.");
        }
        byte[] rawKey = concatenateByteArrays(xCor, yCor);
        return new RkpKey(privKey, macedPublicKey.macedKey, keyMap, serviceName, rawKey);
    }

    /**
     * Decodes and returns the CBOR encoded DataItem in encodedBytes. Also verifies that the
     * majorType actually matches what is being assumed.
     */
    public static DataItem decodeCbor(byte[] encodedBytes, String debugName,
            MajorType majorType) throws CborException, RkpdException {
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedBytes);
        List<DataItem> dataItems = new CborDecoder(bais).decode();
        if (dataItems.size() != RESPONSE_ARRAY_SIZE
                || !checkType(dataItems.get(RESPONSE_CERT_ARRAY_INDEX), majorType, debugName)) {
            throw new RkpdException(RkpdException.ErrorCode.INTERNAL_ERROR, debugName
                    + " not in proper Cbor format. Expected size 1. Actual: " + dataItems.size());
        }
        return dataItems.get(0);
    }

    private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[] getBytesFromBstr(DataItem item) throws CborException {
        if (item.getMajorType() == MajorType.BYTE_STRING) {
            return ((ByteString) item).getBytes();
        }
        throw new CborException("Error while decoding CBOR. Expected bstr value.");
    }

    /**
     * Make protected headers for certificate request.
     */
    public static Map makeProtectedHeaders() throws CborException {
        Map protectedHeaders = new Map();
        protectedHeaders.put(new UnsignedInteger(COSE_HEADER_ALGORITHM),
                new UnsignedInteger(COSE_ALGORITHM_HMAC_256));
        return protectedHeaders;
    }

    /**
     * Encodes CBOR to byte array.
     */
    public static byte[] encodeCbor(final DataItem dataItem) throws CborException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborEncoder encoder = new CborEncoder(baos);
        encoder.encode(dataItem);
        return baos.toByteArray();
    }
}
