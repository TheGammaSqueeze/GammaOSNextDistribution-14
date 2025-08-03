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
package com.android.compatibility.common.deviceinfo;

import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.PURPOSE_VERIFY;

import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ID_ATTESTATION;

import static com.google.android.attestation.ParsedAttestationRecord.createParsedAttestationRecord;

import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.pm.PackageManager;
import android.security.keystore.DeviceIdAttestationException;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;

import com.android.compatibility.common.util.DeviceInfoStore;

import com.google.android.attestation.AuthorizationList;
import com.google.android.attestation.ParsedAttestationRecord;
import com.google.android.attestation.RootOfTrust;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Feature Keystore Attestation device info collector. Collector collects information from the
 * device's KeyMint implementation as reflected by the data in the key attestation record. These
 * will be collected across devices and do not collect and PII outside of the device information.
 */
public final class KeystoreAttestationDeviceInfo extends DeviceInfo {
    private static final String LOG_TAG = "KeystoreAttestationDeviceInfo";
    private static final String TEST_ALIAS_KEYSTORE = "testKeystore";
    private static final String TEST_ALIAS_STRONG_BOX = "testStrongBox";
    private static final byte[] CHALLENGE = "challenge".getBytes();

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        collectKeystoreAttestation(store);
        if (getContext()
                .getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            collectStrongBoxAttestation(store);
        }
    }

    private void generateKeyPair(String algorithm, KeyGenParameterSpec spec)
            throws NoSuchAlgorithmException,
                    NoSuchProviderException,
                    InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance(algorithm, "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        KeyPair kp = keyPairGenerator.generateKeyPair();

        if (kp == null) {
            Log.e(LOG_TAG, "Key generation failed");
            return;
        }
    }

    private void collectKeystoreAttestation(DeviceInfoStore localStore) throws Exception {
        KeyStore mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mKeyStore.load(null);
        mKeyStore.deleteEntry(TEST_ALIAS_KEYSTORE);

        localStore.startGroup("keymint_key_attestation");
        loadCertAndCollectAttestation(mKeyStore, localStore, TEST_ALIAS_KEYSTORE, false);
        localStore.endGroup();
    }

    private void collectStrongBoxAttestation(DeviceInfoStore localStore) throws Exception {
        KeyStore mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mKeyStore.load(null);
        mKeyStore.deleteEntry(TEST_ALIAS_STRONG_BOX);

        localStore.startGroup("strong_box_key_attestation");
        loadCertAndCollectAttestation(mKeyStore, localStore, TEST_ALIAS_STRONG_BOX, true);
        localStore.endGroup();
    }

    private void loadCertAndCollectAttestation(
            KeyStore keystore,
            DeviceInfoStore localStore,
            String testAlias,
            boolean isStrongBoxBacked)
            throws Exception {
        Objects.requireNonNull(keystore);
        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(testAlias, PURPOSE_SIGN | PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setDigests(DIGEST_SHA256)
                        .setDevicePropertiesAttestationIncluded(
                                getContext()
                                        .getPackageManager()
                                        .hasSystemFeature(FEATURE_DEVICE_ID_ATTESTATION))
                        .setAttestationChallenge(CHALLENGE)
                        .setIsStrongBoxBacked(isStrongBoxBacked)
                        .build();

        generateKeyPair(KEY_ALGORITHM_EC, spec);

        Certificate[] certificates = keystore.getCertificateChain(testAlias);
        assertTrue(certificates.length >= 1);

        final AuthorizationList keyDetailsList;

        /* convert Certificate to List of X509Certificate */
        List<X509Certificate> x509Certificates = new ArrayList<>();
        for (Certificate certificate : certificates) {
            if (certificate instanceof X509Certificate) {
                x509Certificates.add((X509Certificate) certificate);
            }
        }
        assertTrue(x509Certificates.size() >= 1);

        ParsedAttestationRecord parsedAttestationRecord =
                createParsedAttestationRecord(x509Certificates);

        keyDetailsList = parsedAttestationRecord.teeEnforced;

        collectStoredInformation(localStore, keyDetailsList);
    }

    private static void collectStoredInformation(
            DeviceInfoStore localStore, AuthorizationList keyDetailsList)
            throws DeviceIdAttestationException, IOException {
        if (keyDetailsList.rootOfTrust.isPresent()) {
            collectRootOfTrust(keyDetailsList.rootOfTrust, localStore);
        }
        if (keyDetailsList.osVersion.isPresent()) {
            localStore.addResult("os_version", keyDetailsList.osVersion.get());
        }
        if (keyDetailsList.osPatchLevel.isPresent()) {
            localStore.addResult("patch_level", keyDetailsList.osPatchLevel.get());
        }
        if (keyDetailsList.attestationIdBrand.isPresent()) {
            localStore.addResult(
                    "attestation_id_brand",
                    new String(keyDetailsList.attestationIdBrand.get(), UTF_8));
        }
        if (keyDetailsList.attestationIdDevice.isPresent()) {
            localStore.addResult(
                    "attestation_id_device",
                    new String(keyDetailsList.attestationIdDevice.get(), UTF_8));
        }
        if (keyDetailsList.attestationIdProduct.isPresent()) {
            localStore.addResult(
                    "attestation_id_product",
                    new String(keyDetailsList.attestationIdProduct.get(), UTF_8));
        }
        if (keyDetailsList.attestationIdManufacturer.isPresent()) {
            localStore.addResult(
                    "attestation_id_manufacturer",
                    new String(keyDetailsList.attestationIdManufacturer.get(), UTF_8));
        }
        if (keyDetailsList.attestationIdModel.isPresent()) {
            localStore.addResult(
                    "attestation_id_model",
                    new String(keyDetailsList.attestationIdModel.get(), UTF_8));
        }
        if (keyDetailsList.vendorPatchLevel.isPresent()) {
            localStore.addResult("vendor_patch_level", keyDetailsList.vendorPatchLevel.get());
        }
        if (keyDetailsList.bootPatchLevel.isPresent()) {
            localStore.addResult("boot_patch_level", keyDetailsList.bootPatchLevel.get());
        }
    }

    private static void collectRootOfTrust(
            Optional<RootOfTrust> rootOfTrust, DeviceInfoStore localStore) throws IOException {
        if (rootOfTrust.isPresent()) {
            localStore.addResult(
                    "verified_boot_key",
                    Base64.getEncoder().encodeToString(rootOfTrust.get().verifiedBootKey));
            localStore.addResult("device_locked", rootOfTrust.get().deviceLocked);
            localStore.addResult("verified_boot_state", rootOfTrust.get().verifiedBootState.name());
            localStore.addResult(
                    "verified_boot_hash",
                    Base64.getEncoder().encodeToString(rootOfTrust.get().verifiedBootHash));
        }
    }
}
