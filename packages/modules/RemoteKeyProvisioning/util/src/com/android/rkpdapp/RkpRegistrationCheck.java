/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.rkpdapp;

import android.hardware.security.keymint.DeviceInfo;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.hardware.security.keymint.MacedPublicKey;
import android.hardware.security.keymint.ProtectedData;
import android.hardware.security.keymint.RpcHardwareInfo;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

/**
 * Command-line utility that verifies each KeyMint instance on this device is able to
 * get production RKP keys.
 */
public class RkpRegistrationCheck {
    private static final String TAG = "RegistrationTest";
    private static final int COSE_HEADER_ALGORITHM = 1;
    private static final int COSE_ALGORITHM_HMAC_256 = 5;

    private static final int SHARED_CERTIFICATES_INDEX = 0;
    private static final int UNIQUE_CERTIFICATES_INDEX = 1;

    private static final int TIMEOUT_MS = 20_000;
    private final String mRequestId = UUID.randomUUID().toString();
    private final String mInstanceName;

    private static class NotRegisteredException extends Exception {
    }

    private static class FetchEekResponse {
        private static final int EEK_AND_CURVE_INDEX = 0;
        private static final int CHALLENGE_INDEX = 1;

        private static final int CURVE_INDEX = 0;
        private static final int EEK_CERT_CHAIN_INDEX = 1;

        private final byte[] mChallenge;
        private final HashMap<Integer, byte[]> mCurveToGeek = new HashMap<>();

        FetchEekResponse(DataItem response) throws CborException, RemoteException {
            List<DataItem> respItems = ((Array) response).getDataItems();
            List<DataItem> allEekChains =
                    ((Array) respItems.get(EEK_AND_CURVE_INDEX)).getDataItems();
            for (DataItem entry : allEekChains) {
                List<DataItem> curveAndEekChain = ((Array) entry).getDataItems();
                UnsignedInteger curve = (UnsignedInteger) curveAndEekChain.get(CURVE_INDEX);
                mCurveToGeek.put(curve.getValue().intValue(),
                        encodeCbor(curveAndEekChain.get(EEK_CERT_CHAIN_INDEX)));
            }

            mChallenge = ((ByteString) respItems.get(CHALLENGE_INDEX)).getBytes();
        }

        public byte[] getEekChain(int curve) {
            return mCurveToGeek.get(curve);
        }

        public byte[] getChallenge() {
            return mChallenge;
        }
    }

    /** Main entry point. */
    public static void main(String[] args) {
        if (SystemProperties.get("remote_provisioning.hostname").isEmpty()) {
            System.out.println(
                    "The RKP server hostname is not configured -- RKP is disabled.");
        }

        new RkpRegistrationCheck("default").checkNow();
        new RkpRegistrationCheck("strongbox").checkNow();
    }

    RkpRegistrationCheck(String instanceName) {
        mInstanceName = instanceName;
    }

    void checkNow() {
        System.out.println();
        System.out.println("Checking to see if the device key for HAL '" + mInstanceName
                + "' has been registered...");

        if (!isValidInstance()) {
            System.err.println("Skipping registration check for '" + mInstanceName + "'.");
            System.err.println("The HAL does not exist.");
            return;
        }

        try {
            FetchEekResponse eekResponse = fetchEek();
            String serviceName = IRemotelyProvisionedComponent.DESCRIPTOR + "/" + mInstanceName;
            IRemotelyProvisionedComponent binder = IRemotelyProvisionedComponent.Stub.asInterface(
                    ServiceManager.waitForDeclaredService(serviceName));
            byte[] csr = generateCsr(binder, eekResponse);
            X509Certificate[] certs = signCertificates(csr, eekResponse.getChallenge());
            Log.i(TAG, "Cert chain:");
            for (X509Certificate c : certs) {
                Log.i(TAG, "  " + c.toString());
            }
            System.out.println("SUCCESS: Device key for '" + mInstanceName + "' is registered");
        } catch (ServiceSpecificException e) {
            Log.e(TAG, e.getMessage(), e);
            System.err.println("Error getting CSR for '" + mInstanceName + "': '" + e
                    + "', skipping.");
        } catch (NotRegisteredException e) {
            Log.e(TAG, e.getMessage(), e);
            System.out.println("FAIL: Device key for '" + mInstanceName + "' is NOT registered");
        } catch (IOException | CborException | RemoteException | CertificateException e) {
            Log.e(TAG, e.getMessage(), e);
            System.err.println("Error checking device registration for '" + mInstanceName
                    + "': '" + e + "', skipping.");
        }
    }

    private boolean isValidInstance() {
        // The SE policy checks appear to be very strict for shell, and we'll get a security
        // exception for any HALs not actually declared. Instead, check to see if the given
        // instance is in the list we can query.
        String[] instances = ServiceManager.getDeclaredInstances(
                IRemotelyProvisionedComponent.DESCRIPTOR);
        for (String i : instances) {
            if (i.equals(mInstanceName)) {
                return true;
            }
        }
        return false;
    }

    private Uri getBaseUri() {
        String hostnameProperty = "remote_provisioning.hostname";
        String hostname = SystemProperties.get(hostnameProperty);
        if (hostname.isEmpty()) {
            throw new RuntimeException("System property '" + hostnameProperty + "' is empty. "
                    + "This device does not support RKP.");
        }
        return new Uri.Builder().scheme("https").authority(hostname).appendPath("v1").build();
    }

    FetchEekResponse fetchEek()
            throws IOException, CborException, RemoteException, NotRegisteredException {
        final Uri uri = getBaseUri().buildUpon().appendEncodedPath(":fetchEekChain").build();

        final ByteArrayOutputStream input = new ByteArrayOutputStream();
        new CborEncoder(input).encode(new CborBuilder()
                .addMap()
                .put("fingerprint", getFingerprint())
                .put(new UnicodeString("id"), new UnsignedInteger(0))
                .end()
                .build());

        return new FetchEekResponse(httpPost(uri, input.toByteArray()));
    }

    private String getFingerprint() {
        // Fake a user build fingerprint so that we will get 444 on unregistered devices instead
        // of test certs.
        Log.i(TAG, "Original fingerprint: " + Build.FINGERPRINT);
        String fingerprint = Build.FINGERPRINT
                .replace(":userdebug", ":user")
                .replace(":eng", ":user")
                .replace("cf_", "cephalopod_");
        Log.i(TAG, "Modified (prod-like) fingerprint: " + fingerprint);
        return fingerprint;
    }

    X509Certificate[] signCertificates(byte[] csr, byte[] challenge)
            throws IOException, CborException, CertificateException,
            NotRegisteredException {
        String encodedChallenge = Base64.encodeToString(challenge,
                Base64.URL_SAFE | Base64.NO_WRAP);
        final Uri uri = getBaseUri().buildUpon()
                .appendEncodedPath(":signCertificates")
                .appendQueryParameter("challenge", encodedChallenge)
                .build();
        DataItem response = httpPost(uri, csr);
        List<DataItem> dataItems = ((Array) response).getDataItems();
        byte[] sharedCertificates = ((ByteString) dataItems.get(
                SHARED_CERTIFICATES_INDEX)).getBytes();
        DataItem leaf = ((Array) dataItems.get(UNIQUE_CERTIFICATES_INDEX)).getDataItems().get(0);

        ByteArrayOutputStream fullChainWriter = new ByteArrayOutputStream();
        fullChainWriter.write(((ByteString) leaf).getBytes());
        fullChainWriter.write(sharedCertificates);

        ByteArrayInputStream fullChainReader = new ByteArrayInputStream(
                fullChainWriter.toByteArray());
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        ArrayList<Certificate> parsedCerts = new ArrayList<>(
                certFactory.generateCertificates(fullChainReader));
        return parsedCerts.toArray(new X509Certificate[0]);
    }

    DataItem httpPost(Uri uri, byte[] input)
            throws IOException, CborException, NotRegisteredException {
        uri = uri.buildUpon().appendQueryParameter("requestId", mRequestId).build();
        Log.i(TAG, "querying " + uri);
        HttpURLConnection con = (HttpURLConnection) new URL(uri.toString()).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(TIMEOUT_MS);
        con.setReadTimeout(TIMEOUT_MS);
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(input, 0, input.length);
        }

        Log.i(TAG, "HTTP status: " + con.getResponseCode());

        if (con.getResponseCode() == 444) {
            throw new NotRegisteredException();
        }

        if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Server connection failed for url: " + uri
                    + ", HTTP response code: " + con.getResponseCode());
        }

        BufferedInputStream inputStream = new BufferedInputStream(con.getInputStream());
        ByteArrayOutputStream cborBytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
            cborBytes.write(buffer, 0, read);
        }
        inputStream.close();
        byte[] response = cborBytes.toByteArray();
        Log.i(TAG, "response (CBOR): " + Base64.encodeToString(response,
                Base64.URL_SAFE | Base64.NO_WRAP));
        return decodeCbor(response);
    }

    byte[] generateCsr(IRemotelyProvisionedComponent irpc, FetchEekResponse eekResponse)
            throws RemoteException, CborException {
        Map unverifiedDeviceInfo = new Map().put(
                new UnicodeString("fingerprint"), new UnicodeString(getFingerprint()));

        RpcHardwareInfo hwInfo = irpc.getHardwareInfo();

        MacedPublicKey[] macedKeysToSign = new MacedPublicKey[]{new MacedPublicKey()};
        irpc.generateEcdsaP256KeyPair(false, macedKeysToSign[0]);

        if (hwInfo.versionNumber < 3) {
            Log.i(TAG, "Generating CSRv1");
            DeviceInfo deviceInfo = new DeviceInfo();
            ProtectedData protectedData = new ProtectedData();
            byte[] geekChain = eekResponse.getEekChain(hwInfo.supportedEekCurve);
            byte[] csrTag = irpc.generateCertificateRequest(false, macedKeysToSign, geekChain,
                    eekResponse.getChallenge(), deviceInfo, protectedData);
            Array mac0Message = buildMac0MessageForV1Csr(macedKeysToSign[0], csrTag);
            return encodeCbor(new CborBuilder()
                    .addArray()
                    .addArray()
                    .add(decodeCbor(deviceInfo.deviceInfo))
                    .add(unverifiedDeviceInfo)
                    .end()
                    .add(eekResponse.getChallenge())
                    .add(decodeCbor(protectedData.protectedData))
                    .add(mac0Message)
                    .end()
                    .build().get(0));
        } else {
            Log.i(TAG, "Generating CSRv2");
            byte[] csrBytes = irpc.generateCertificateRequestV2(macedKeysToSign,
                    eekResponse.getChallenge());
            Array array = (Array) decodeCbor(csrBytes);
            array.add(unverifiedDeviceInfo);
            return encodeCbor(array);
        }
    }

    Array buildMac0MessageForV1Csr(MacedPublicKey macedKeyToSign, byte[] csrTag)
            throws CborException {
        DataItem macedPayload = ((Array) decodeCbor(
                macedKeyToSign.macedKey)).getDataItems().get(2);
        Map macedCoseKey = (Map) decodeCbor(((ByteString) macedPayload).getBytes());
        byte[] macedKeys = encodeCbor(new Array().add(macedCoseKey));

        Map protectedHeaders = new Map().put(
                new UnsignedInteger(COSE_HEADER_ALGORITHM),
                new UnsignedInteger(COSE_ALGORITHM_HMAC_256));
        return new Array()
                .add(new ByteString(encodeCbor(protectedHeaders)))
                .add(new Map())
                .add(new ByteString(macedKeys))
                .add(new ByteString(csrTag));
    }

    static DataItem decodeCbor(byte[] encodedBytes) throws CborException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedBytes);
        return new CborDecoder(inputStream).decode().get(0);
    }

    static byte[] encodeCbor(final DataItem dataItem) throws CborException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new CborEncoder(outputStream).encode(dataItem);
        return outputStream.toByteArray();
    }
}
