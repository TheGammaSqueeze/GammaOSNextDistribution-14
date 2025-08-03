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

package com.android.cts.verifier.security;

import static android.keystore.cts.CertificateUtils.createCertificate;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.AppUriAuthenticationPolicy;
import android.security.AttestedKeyPair;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.security.auth.x500.X500Principal;

/**
 * CTS verifier test for credential management on unmanaged device.
 *
 * This activity is responsible for starting the credential management app flow. It performs the
 * following verifications:
 *  Can successfully request to become the Credential management app
 *  The credential management app is correctly set
 *  The authentication policy is correctly set
 *  The credential management app can generate a key pair
 *  The credential management app can install a certificate
 *  The credential management app can successfully predefine which alias should be used for
 *  authentication to a remote service
 *  The chosen alias can be used to get the private key to sign data and the public key to
 *  validate the signature.
 */
public class CredentialManagementAppActivity extends DialogTestListActivity {

    private static final String TAG = "CredentialManagementAppActivity";

    private static final int REQUEST_MANAGE_CREDENTIALS_STATUS = 0;

    private static final String TEST_APP_PACKAGE_NAME = "com.android.cts.verifier";
    private static final Uri TEST_URI = Uri.parse("https://test.com");
    private static final String TEST_ALIAS = "testAlias";
    private static final AppUriAuthenticationPolicy AUTHENTICATION_POLICY =
            new AppUriAuthenticationPolicy.Builder()
                    .addAppAndUriMapping(TEST_APP_PACKAGE_NAME, TEST_URI, TEST_ALIAS)
                    .build();
    private static final String KEY_ALGORITHM = "RSA";
    private static final byte[] DATA = "test".getBytes();

    private DevicePolicyManager mDevicePolicyManager;

    private DialogTestListItem mRequestManageCredentials;
    private DialogTestListItem mCheckIsCredentialManagementApp;
    private DialogTestListItem mCheckAuthenticationPolicy;
    private DialogTestListItem mGenerateKeyPair;
    private DialogTestListItem mCreateAndInstallCertificate;
    private DialogTestListItem mRequestCertificateForAuthentication;
    private DialogTestListItem mSignDataWithKey;
    private DialogTestListItem mVerifySignature;
    private DialogTestListItem mRemoveCredentialManagementApp;

    private AttestedKeyPair mAttestedKeyPair;
    private X509Certificate mCertificate;
    private String mChosenAlias;
    private byte[] mSignature;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mHasCredentialManagementApp = false;

    public CredentialManagementAppActivity() {
        super(R.layout.credential_management_app_test,
                R.string.credential_management_app_test,
                R.string.credential_management_app_info,
                R.string.credential_management_app_info);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDevicePolicyManager = getSystemService(DevicePolicyManager.class);
    }

    @Override
    public void finish() {
        super.finish();
        if (mHasCredentialManagementApp) {
            mExecutor.execute(
                    () -> KeyChain.removeCredentialManagementApp(getApplicationContext()));
        }
    }

    @Override
    protected void setupTests(final ArrayTestListAdapter testAdapter) {
        mRequestManageCredentials = new DialogTestListItem(this,
                R.string.request_manage_credentials,
                "request_manage_credentials") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                Intent intent = KeyChain.createManageCredentialsIntent(AUTHENTICATION_POLICY);
                startActivityForResult(intent, REQUEST_MANAGE_CREDENTIALS_STATUS);
            }
        };
        testAdapter.add(mRequestManageCredentials);
        mCheckIsCredentialManagementApp = new DialogTestListItem(this,
                R.string.is_credential_management_app,
                "is_credential_management_app") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkIsCredentialManagementApp();
            }
        };
        testAdapter.add(mCheckIsCredentialManagementApp);
        mCheckAuthenticationPolicy = new DialogTestListItem(this,
                R.string.credential_management_app_policy,
                "credential_management_app_policy") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkAuthenticationPolicy();
            }
        };
        testAdapter.add(mCheckAuthenticationPolicy);
        mGenerateKeyPair = new DialogTestListItem(this,
                R.string.generate_key_pair,
                "generate_key_pair") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                generateKeyPair();
            }
        };
        testAdapter.add(mGenerateKeyPair);
        mCreateAndInstallCertificate = new DialogTestListItem(this,
                R.string.create_and_install_certificate,
                "create_and_install_certificate") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                createAndInstallCertificate();
            }
        };
        testAdapter.add(mCreateAndInstallCertificate);
        mRequestCertificateForAuthentication = new DialogTestListItem(this,
                R.string.request_certificate_authentication,
                "request_certificate_authentication") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                requestCertificateForAuthentication();
            }
        };
        testAdapter.add(mRequestCertificateForAuthentication);
        mSignDataWithKey = new DialogTestListItem(this,
                R.string.sign_data_with_key,
                "sign_data_with_key") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                getPrivateKeyAndSignData();
            }
        };
        testAdapter.add(mSignDataWithKey);
        mVerifySignature = new DialogTestListItem(this,
                R.string.verify_signature,
                "verify_signature") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                getPublicKeyAndVerifySignature();
            }
        };
        testAdapter.add(mVerifySignature);
        mRemoveCredentialManagementApp = new DialogTestListItem(this,
                R.string.remove_credential_management_app,
                "remove_credential_management_app") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                removeCredentialManagementApp();
            }
        };
        testAdapter.add(mRemoveCredentialManagementApp);
    }

    private void checkIsCredentialManagementApp() {
        mExecutor.execute(() -> {
            final boolean isCredMngApp =
                    KeyChain.isCredentialManagementApp(getApplicationContext());
            mHandler.post(() -> setResult(mCheckIsCredentialManagementApp, isCredMngApp));
        });
    }

    private void checkAuthenticationPolicy() {
        mExecutor.execute(() -> {
            final AppUriAuthenticationPolicy authenticationPolicy =
                    KeyChain.getCredentialManagementAppPolicy(getApplicationContext());
            mHandler.post(() -> setResult(mCheckAuthenticationPolicy,
                    authenticationPolicy.equals(AUTHENTICATION_POLICY)));
        });
    }

    private void generateKeyPair() {
        mExecutor.execute(() -> {
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                    TEST_ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(
                            KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                            KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build();
            mAttestedKeyPair = mDevicePolicyManager.generateKeyPair(null, KEY_ALGORITHM,
                    keyGenParameterSpec, 0);
            mHandler.post(() -> setResult(mGenerateKeyPair, mAttestedKeyPair != null));
        });
    }

    private void createAndInstallCertificate() {
        X500Principal issuer = new X500Principal("CN=SelfSigned, O=Android, C=US");
        X500Principal subject = new X500Principal("CN=Subject, O=Android, C=US");
        try {
            mCertificate = createCertificate(mAttestedKeyPair.getKeyPair(), subject, issuer);
            setResult(mCreateAndInstallCertificate,
                    mDevicePolicyManager.setKeyPairCertificate(null, TEST_ALIAS,
                            Arrays.asList(new X509Certificate[]{mCertificate}), false));
        } catch (Exception e) {
            Log.w(TAG, "Failed to create certificate", e);
            setResult(mCreateAndInstallCertificate, false);
        }
    }

    private void requestCertificateForAuthentication() {
        String[] keyTypes = new String[]{KEY_ALGORITHM};
        Principal[] issuers = new Principal[0];
        TestKeyChainAliasCallback callback = new TestKeyChainAliasCallback();
        KeyChain.choosePrivateKeyAlias(this, callback, keyTypes, issuers, TEST_URI, null);
    }

    private void getPrivateKeyAndSignData() {
        // Get private key with chosen alias
        mExecutor.execute(() -> {
            try {
                final PrivateKey privateKey = KeyChain.getPrivateKey(
                        getApplicationContext(), mChosenAlias);
                mHandler.post(() -> {
                    // Sign data with the private key
                    try {
                        Signature sign = Signature.getInstance("SHA256withRSA");
                        sign.initSign(privateKey);
                        sign.update(DATA);
                        mSignature = sign.sign();
                    } catch (NoSuchAlgorithmException | InvalidKeyException
                            | SignatureException e) {
                        Log.w(TAG, "Failed to sign data with key", e);
                    }
                    setResult(mSignDataWithKey, mSignature != null);
                });
            } catch (KeyChainException | InterruptedException e) {
                Log.w(TAG, "Failed to get the private key", e);
            }
        });
    }

    private void getPublicKeyAndVerifySignature() {
        // Get public key from certificate with chosen alias
        mExecutor.execute(() -> {
            try {
                X509Certificate[] certChain =
                        KeyChain.getCertificateChain(getApplicationContext(), mChosenAlias);
                mHandler.post(() -> {
                    boolean verified = false;
                    if (certChain != null && certChain.length > 0) {
                        PublicKey publicKey = certChain[0].getPublicKey();
                        // Verify the signature with the public key
                        try {
                            Signature verify = Signature.getInstance("SHA256withRSA");
                            verify.initVerify(publicKey);
                            verify.update(DATA);
                            verified = verify.verify(mSignature);
                        } catch (NoSuchAlgorithmException | InvalidKeyException
                                | SignatureException e) {
                            Log.w(TAG, "Failed to verify signature", e);
                        }
                    }
                    setResult(mVerifySignature, verified);
                });
            } catch (KeyChainException | InterruptedException e) {
                Log.w(TAG, "Failed to get the public key", e);
            }
        });
    }

    private void removeCredentialManagementApp() {
        mExecutor.execute(() -> {
            final boolean result =
                    KeyChain.removeCredentialManagementApp(getApplicationContext());
            mHandler.post(() -> {
                setResult(mRemoveCredentialManagementApp, result);
                if (result) {
                    mHasCredentialManagementApp = false;
                }
            });
        });
    }

    private void setResult(DialogTestListItem testListItem, boolean passed) {
        if (passed) {
            setTestResult(testListItem, TestResult.TEST_RESULT_PASSED);
        } else {
            setTestResult(testListItem, TestResult.TEST_RESULT_FAILED);
        }
    }

    @Override
    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_MANAGE_CREDENTIALS_STATUS:
                setResult(mRequestManageCredentials, resultCode == RESULT_OK);
                if (resultCode == RESULT_OK) {
                    mHasCredentialManagementApp = true;
                }
                break;
            default:
                super.handleActivityResult(requestCode, resultCode, data);
        }
    }

    private class TestKeyChainAliasCallback implements KeyChainAliasCallback {
        @Override
        public void alias(String alias) {
            mChosenAlias = alias;
            setResult(mRequestCertificateForAuthentication, mChosenAlias.equals(TEST_ALIAS));
        }
    }
}
