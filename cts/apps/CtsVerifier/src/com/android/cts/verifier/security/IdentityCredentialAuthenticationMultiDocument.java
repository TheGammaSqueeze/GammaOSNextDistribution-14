/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricPrompt.AuthenticationCallback;
import android.hardware.biometrics.BiometricPrompt.AuthenticationResult;
import android.hardware.biometrics.BiometricPrompt.CryptoObject;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.security.identity.AccessControlProfile;
import android.security.identity.AccessControlProfileId;
import android.security.identity.CredentialDataRequest;
import android.security.identity.CredentialDataResult;
import android.security.identity.IdentityCredential;
import android.security.identity.IdentityCredentialStore;
import android.security.identity.PersonalizationData;
import android.security.identity.PresentationSession;
import android.security.identity.WritableIdentityCredential;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

/**
 * @hide
 */
public class IdentityCredentialAuthenticationMultiDocument extends PassFailButtons.Activity {
    private static final boolean DEBUG = false;
    private static final String TAG = "IdentityCredentialAuthenticationMultiDocument";

    private static final int BIOMETRIC_REQUEST_PERMISSION_CODE = 0;

    private static final int PRESENTATION_SESSION_FEATURE_VERSION_NEEDED = 202201;

    private BiometricManager mBiometricManager;
    private KeyguardManager mKeyguardManager;

    protected int getTitleRes() {
        return R.string.sec_identity_credential_authentication_multi_document_test;
    }

    private int getDescriptionRes() {
        return R.string.sec_identity_credential_authentication_multi_document_test_info;
    }

    // Returns 0 if Identity Credential is not implemented. Otherwise returns the feature version.
    //
    private static int getFeatureVersion(Context appContext) {
        PackageManager pm = appContext.getPackageManager();

        if (pm.hasSystemFeature(PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
            FeatureInfo[] infos = pm.getSystemAvailableFeatures();
            for (int n = 0; n < infos.length; n++) {
                FeatureInfo info = infos[n];
                if (info.name.equals(PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
                    return info.version;
                }
            }
        }

        // Use of the system feature is not required since Android 12. So for Android 11
        // return 202009 which is the feature version shipped with Android 11.
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
        if (store != null) {
            return 202009;
        }

        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sec_screen_lock_keys_main);
        setPassFailButtonClickListeners();
        setInfoResources(getTitleRes(), getDescriptionRes(), -1);
        getPassButton().setEnabled(false);
        requestPermissions(new String[]{Manifest.permission.USE_BIOMETRIC},
                BIOMETRIC_REQUEST_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] state) {
        if (requestCode == BIOMETRIC_REQUEST_PERMISSION_CODE
                && state[0] == PackageManager.PERMISSION_GRANTED) {
            mBiometricManager = getSystemService(BiometricManager.class);
            mKeyguardManager = getSystemService(KeyguardManager.class);
            Button startTestButton = findViewById(R.id.sec_start_test_button);

            if (!mKeyguardManager.isKeyguardSecure()) {
                // Show a message that the user hasn't set up a lock screen.
                showToast("Secure lock screen hasn't been set up.\n Go to "
                          + "'Settings -> Security -> Screen lock' to set up a lock screen");
                startTestButton.setEnabled(false);
                return;
            }

            startTestButton.setOnClickListener(v -> startTest());
        }
    }

    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.i(TAG, "Showing Toast: " + message);
    }

    private void provisionCredential(IdentityCredentialStore store,
                                     String credentialName) throws Exception {
        store.deleteCredentialByName(credentialName);
        WritableIdentityCredential wc = store.createCredential(
                credentialName, "org.iso.18013-5.2019.mdl");

        // 'Bar' encoded as CBOR tstr
        byte[] barCbor = {0x63, 0x42, 0x61, 0x72};

        AccessControlProfile acp = new AccessControlProfile.Builder(new AccessControlProfileId(0))
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationTimeout(0)
                .build();
        LinkedList<AccessControlProfileId> idsProfile0 = new LinkedList<AccessControlProfileId>();
        idsProfile0.add(new AccessControlProfileId(0));
        PersonalizationData pd = new PersonalizationData.Builder()
                                 .addAccessControlProfile(acp)
                                 .putEntry("org.iso.18013-5.2019", "Foo", idsProfile0, barCbor)
                                 .build();
        byte[] proofOfProvisioningSignature = wc.personalize(pd);

        // Create authentication keys.
        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        credential.setAvailableAuthenticationKeys(1, 10);
        Collection<X509Certificate> dynAuthKeyCerts = credential.getAuthKeysNeedingCertification();
        credential.storeStaticAuthenticationData(dynAuthKeyCerts.iterator().next(), new byte[0]);
    }

    private int getFooStatus(PresentationSession session, String credentialName) throws Exception {
        Map<String, Collection<String>> isEntriesToRequest = new LinkedHashMap<>();
        isEntriesToRequest.put("org.iso.18013-5.2019", Arrays.asList("Foo"));

        CredentialDataResult rd = session.getCredentialData(
                credentialName,
                new CredentialDataRequest.Builder()
                .setIssuerSignedEntriesToRequest(isEntriesToRequest)
                .build());
        return rd.getIssuerSignedEntries().getStatus("org.iso.18013-5.2019", "Foo");
    }

    protected void startTest() {
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(this);
        if (store == null) {
            showToast("No Identity Credential support, test passed.");
            getPassButton().setEnabled(true);
            return;
        }
        int featureVersion = getFeatureVersion(this);
        Log.i(TAG, "Identity Credential featureVersion: " + featureVersion);
        if (featureVersion < PRESENTATION_SESSION_FEATURE_VERSION_NEEDED) {
            showToast(String.format(
                          Locale.US,
                          "Identity Credential version %d or later is required but "
                          + "version %d was found. Test passed.",
                          PRESENTATION_SESSION_FEATURE_VERSION_NEEDED,
                          featureVersion));
            getPassButton().setEnabled(true);
            return;
        }

        final int result = mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG);
        switch (result) {
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                showToast("No strong biometrics (Class 3) enrolled.\n"
                        + "Go to 'Settings -> Security' to enroll");
                Button startTestButton = findViewById(R.id.sec_start_test_button);
                startTestButton.setEnabled(false);
                return;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                showToast("No strong biometrics (Class 3), test passed.");
                showToast("No Identity Credential support, test passed.");
                getPassButton().setEnabled(true);
                return;
        }

        try {
            provisionCredential(store, "credential1");
            provisionCredential(store, "credential2");

            PresentationSession session = store.createPresentationSession(
                    IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

            // First, check that Foo cannot be retrieved without authentication.
            //
            int status = getFooStatus(session, "credential1");
            if (status != CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED) {
                showToast("Unexpected credential1 pre-auth status "
                          + status + " expected STATUS_USER_AUTHENTICATION_FAILED");
                return;
            }
            status = getFooStatus(session, "credential2");
            if (status != CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED) {
                showToast("Unexpected credential2 pre-auth status "
                          + status + " expected STATUS_USER_AUTHENTICATION_FAILED");
                return;
            }

            // Try one more time, this time with a CryptoObject that we'll use with
            // BiometricPrompt. This should work.
            //
            CryptoObject cryptoObject = new BiometricPrompt.CryptoObject(session);
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this);
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG);
            builder.setTitle("Identity Credential");
            builder.setDescription("Authenticate to unlock multiple credentials.");
            builder.setNegativeButton("Cancel",
                    getMainExecutor(),
                    (dialogInterface, i) -> showToast("Canceled biometric prompt."));
            final BiometricPrompt prompt = builder.build();
            final AuthenticationCallback callback = new AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(AuthenticationResult authResult) {
                    try {
                        // Check that Foo can be retrieved because we used
                        // the CryptoObject to auth with.
                        int status = getFooStatus(session, "credential1");
                        if (status != CredentialDataResult.Entries.STATUS_OK) {
                            showToast("Unexpected credential1 post-auth status "
                                      + status + " expected STATUS_OK");
                            return;
                        }
                        status = getFooStatus(session, "credential2");
                        if (status != CredentialDataResult.Entries.STATUS_OK) {
                            showToast("Unexpected credential2 post-auth status "
                                      + status + " expected STATUS_OK");
                            return;
                        }

                        // Finally, check that Foo cannot be retrieved again from another session
                        PresentationSession anotherSession = store.createPresentationSession(
                                IdentityCredentialStore
                                    .CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
                        status = getFooStatus(anotherSession, "credential1");
                        if (status
                                != CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED) {
                            showToast("Unexpected credential1 other session status "
                                      + status + " expected STATUS_USER_AUTHENTICATION_FAILED");
                            return;
                        }
                        status = getFooStatus(anotherSession, "credential2");
                        if (status
                                != CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED) {
                            showToast("Unexpected credential2 other session status "
                                      + status + " expected STATUS_USER_AUTHENTICATION_FAILED");
                            return;
                        }

                        showToast("Test passed.");
                        getPassButton().setEnabled(true);

                    } catch (Exception e) {
                        showToast("Unexpected exception " + e);
                    }
                }
            };

            prompt.authenticate(cryptoObject, new CancellationSignal(), getMainExecutor(),
                    callback);
        } catch (Exception e) {
            showToast("Unexpection exception " + e);
        }
    }
}
