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

package com.android.libraries.ts43authentication;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.SubscriptionInfo;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.libraries.entitlement.Ts43Authentication;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TS.43 authentication library that directs EAP-AKA and OIDC authentication requests to the
 * entitlement server and returns a {@link Ts43Authentication.Ts43AuthToken} on success or an
 * {@link AuthenticationException} on failure.
 */
public class Ts43AuthenticationLibrary extends Handler {
    private static final String TAG = "Ts43AuthLibrary";

    /**
     * Configuration key for the list of {@code SHA256} signing certificates and packages that are
     * permitted to make authentication requests. This will be used to verify the
     * {@code packageName} that is passed to authentication requests.
     * If this is a {@code null} or an empty list, all requests will be allowed to go through.
     * <p>
     * This will be a list of carrier certificate hashes, followed by optional package names,
     * for example {@code "SHA256"} or {@code "SHA256:package1,package2,package3..."}.
     * <p>
     * {@code null} by default
     */
    public static final String KEY_ALLOWED_CERTIFICATES_STRING_ARRAY = "allowed_certificates";

    /**
     * Configuration key for whether the {@code appName} passed to the entitlement server should
     * have the signing certificate of the calling application appended to it.
     * If this is {@code true}, the {@code appName} will be {@code "<SHA>|<packageName>"}, where
     * {@code <SHA>} is the {@code SHA256} hash of the package's signing certificate and
     * {@code <packageName>} is the package name of the calling application.
     * If this is {@code false}, the {@code appName} will just be the {@code packageName} of the
     * calling application.
     * <p>
     * {@code false} by default
     */
    public static final String KEY_APPEND_SHA_TO_APP_NAME_BOOL = "append_sha_to_app_name";

    /**
     * Configuration key to override the {@code appName} passed to the entitlement server.
     * If this value is not {@code null}, the value of this config will be passed directly as the
     * {@code appName} of the authentication request, taking precedence over
     * {@link #KEY_APPEND_SHA_TO_APP_NAME_BOOL}.
     * <p>
     * {@code null} by default
     */
    public static final String KEY_OVERRIDE_APP_NAME_STRING = "override_app_name";

    /**
     * Configuration keys for the {@link PersistableBundle} passed to authentication requests.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
            KEY_ALLOWED_CERTIFICATES_STRING_ARRAY,
            KEY_APPEND_SHA_TO_APP_NAME_BOOL,
            KEY_OVERRIDE_APP_NAME_STRING,
    })
    public @interface ConfigurationKey {}

    private static final int EVENT_REQUEST_EAP_AKA_AUTHENTICATION = 0;
    private static final int EVENT_REQUEST_OIDC_AUTHENTICATION_SERVER = 1;
    private static final int EVENT_REQUEST_OIDC_AUTHENTICATION = 2;

    private final ReentrantLock mLock = new ReentrantLock();
    private final Context mContext;
    private final PackageManager mPackageManager;

    /**
     * Create an instance of the TS.43 Authentication Library.
     *
     * @param context The application context.
     * @param looper The looper to run authentication requests on.
     */
    public Ts43AuthenticationLibrary(Context context, Looper looper) {
        super(looper);
        mContext = context;
        mPackageManager = mContext.getPackageManager();
    }

    private static class EapAkaAuthenticationRequest {
        private final String mAppName;
        @Nullable private final String mAppVersion;
        private final int mSlotIndex;
        private final URL mEntitlementServerAddress;
        @Nullable private final String mEntitlementVersion;
        private final String mAppId;
        private final Executor mExecutor;
        private final AuthenticationOutcomeReceiver<
                Ts43Authentication.Ts43AuthToken, AuthenticationException> mCallback;

        private EapAkaAuthenticationRequest(String appName, @Nullable String appVersion,
                int slotIndex, URL entitlementServerAddress, @Nullable String entitlementVersion,
                String appId, Executor executor, AuthenticationOutcomeReceiver<
                        Ts43Authentication.Ts43AuthToken, AuthenticationException> callback) {
            mAppName = appName;
            mAppVersion = appVersion;
            mSlotIndex = slotIndex;
            mEntitlementServerAddress = entitlementServerAddress;
            mEntitlementVersion = entitlementVersion;
            mAppId = appId;
            mExecutor = executor;
            mCallback = callback;
        }
    }

    private static class OidcAuthenticationServerRequest {
        private final String mAppName;
        @Nullable private final String mAppVersion;
        private final int mSlotIndex;
        private final URL mEntitlementServerAddress;
        @Nullable private final String mEntitlementVersion;
        private final String mAppId;
        private final Executor mExecutor;
        private final AuthenticationOutcomeReceiver<URL, AuthenticationException> mCallback;

        private OidcAuthenticationServerRequest(String appName, @Nullable String appVersion,
                int slotIndex, URL entitlementServerAddress, @Nullable String entitlementVersion,
                String appId, Executor executor,
                AuthenticationOutcomeReceiver<URL, AuthenticationException> callback) {
            mAppName = appName;
            mAppVersion = appVersion;
            mSlotIndex = slotIndex;
            mEntitlementServerAddress = entitlementServerAddress;
            mEntitlementVersion = entitlementVersion;
            mAppId = appId;
            mExecutor = executor;
            mCallback = callback;
        }
    }

    private static class OidcAuthenticationRequest {
        private final URL mEntitlementServerAddress;
        @Nullable private final String mEntitlementVersion;
        private final URL mAesUrl;
        private final Executor mExecutor;
        private final AuthenticationOutcomeReceiver<
                Ts43Authentication.Ts43AuthToken, AuthenticationException> mCallback;

        private OidcAuthenticationRequest(URL entitlementServerAddress,
                @Nullable String entitlementVersion, URL aesUrl, Executor executor,
                AuthenticationOutcomeReceiver<
                        Ts43Authentication.Ts43AuthToken, AuthenticationException> callback) {
            mEntitlementServerAddress = entitlementServerAddress;
            mEntitlementVersion = entitlementVersion;
            mAesUrl = aesUrl;
            mExecutor = executor;
            mCallback = callback;
        }
    }

    /**
     * Request authentication from the TS.43 server with EAP-AKA as described in
     * TS.43 Service Entitlement Configuration section 2.8.1.
     *
     * @param configs The configurations that should be applied to this authentication request.
     *        The keys of the bundle must be in {@link ConfigurationKey}.
     * @param packageName The package name for the calling application, used to validate the
     *        identity of the calling application. This will be sent as-is as the {@code app_name}
     *        in the HTTP GET request to the entitlement server unless
     *        {@link #KEY_APPEND_SHA_TO_APP_NAME_BOOL} or {@link #KEY_OVERRIDE_APP_NAME_STRING} is
     *        set in the configuration bundle.
     * @param appVersion The optional appVersion of the calling application, passed as the
     *        {@code app_version} in the HTTP GET request to the entitlement server.
     * @param slotIndex The logical SIM slot index involved in ODSA operation.
     *        See {@link SubscriptionInfo#getSubscriptionId()}.
     * @param entitlementServerAddress The entitlement server address.
     * @param entitlementVersion The TS.43 entitlement version to use. For example, {@code "9.0"}.
     *        If this is {@code null}, version {@code "2.0"} will be used by default.
     * @param appId Application id.
     *        For example, {@code "ap2004"} for VoWifi and {@code "ap2009"} for ODSA primary device.
     *        Refer to GSMA Service Entitlement Configuration section 2.3.
     * @param executor The executor on which the callback will be called.
     * @param callback The callback to receive the results of the authentication request.
     *        If authentication is successful,
     *        {@link AuthenticationOutcomeReceiver#onResult(Object)} will return a
     *        {@link Ts43Authentication.Ts43AuthToken} with the token and validity.
     *        If the authentication fails,
     *        {@link AuthenticationOutcomeReceiver#onError(Throwable)} will return an
     *        {@link AuthenticationException} with the failure details.
     */
    public void requestEapAkaAuthentication(PersistableBundle configs, String packageName,
            @Nullable String appVersion, int slotIndex, URL entitlementServerAddress,
            @Nullable String entitlementVersion, String appId, Executor executor,
            AuthenticationOutcomeReceiver<
                    Ts43Authentication.Ts43AuthToken, AuthenticationException> callback) {
        String[] allowedPackageInfo = configs.getStringArray(KEY_ALLOWED_CERTIFICATES_STRING_ARRAY);
        String certificate = getMatchingCertificate(allowedPackageInfo, packageName);
        if (isCallingPackageAllowed(allowedPackageInfo, packageName, certificate)) {
            obtainMessage(EVENT_REQUEST_EAP_AKA_AUTHENTICATION, new EapAkaAuthenticationRequest(
                    getAppName(configs, packageName, certificate), appVersion, slotIndex,
                    entitlementServerAddress, entitlementVersion, appId, executor, callback))
                    .sendToTarget();
        } else {
            executor.execute(() -> callback.onError(new AuthenticationException(
                    AuthenticationException.ERROR_INVALID_APP_NAME,
                    "Failed to verify the identity of the calling application")));
        }
    }

    /**
     * Get the URL of OIDC (OpenID Connect) server as described in
     * TS.43 Service Entitlement Configuration section 2.8.2.
     * The client should present the content of the URL to the user to continue the authentication
     * process. After receiving a response from the authentication server, the client can call
     * {@link #requestOidcAuthentication(
     * PersistableBundle, String, URL, String, URL, Executor, AuthenticationOutcomeReceiver)} to get
     * the authentication token.
     *
     * @param configs The configurations that should be applied to this authentication request.
     *        The keys of the bundle must be in {@link ConfigurationKey}.
     * @param packageName The package name for the calling application, used to validate the
     *        identity of the calling application. This will be sent as-is as the {@code app_name}
     *        in the HTTP GET request to the entitlement server unless
     *        {@link #KEY_APPEND_SHA_TO_APP_NAME_BOOL} or {@link #KEY_OVERRIDE_APP_NAME_STRING} is
     *        set in the configuration bundle.
     * @param appVersion The optional appVersion of the calling application, passed as the
     *        {@code app_version} in the HTTP GET request to the entitlement server.
     * @param slotIndex The logical SIM slot index involved in ODSA operation.
     *        See {@link SubscriptionInfo#getSubscriptionId()}.
     * @param entitlementServerAddress The entitlement server address.
     * @param entitlementVersion The TS.43 entitlement version to use. For example, {@code "9.0"}.
     *        If this is {@code null}, version {@code "2.0"} will be used by default.
     * @param appId Application id.
     *        For example, {@code "ap2004"} for VoWifi and {@code "ap2009"} for ODSA primary device.
     *        Refer to GSMA Service Entitlement Configuration section 2.3.
     * @param executor The executor on which the callback will be called.
     * @param callback The callback to receive the results of the authentication server request.
     *        If the request is successful, {@link AuthenticationOutcomeReceiver#onResult(Object)}
     *        will return a {@link URL} with all the required parameters for the client to launch a
     *        user interface for users to complete the authentication process. The parameters in URL
     *        include {@code client_id}, {@code redirect_uri}, {@code state}, and {@code nonce}.
     *        If the authentication fails, {@link AuthenticationOutcomeReceiver#onError(Throwable)}
     *        will return an {@link AuthenticationException} with the failure details.
     */
    public void requestOidcAuthenticationServer(PersistableBundle configs,
            String packageName, @Nullable String appVersion, int slotIndex,
            URL entitlementServerAddress, @Nullable String entitlementVersion,
            String appId, Executor executor,
            AuthenticationOutcomeReceiver<URL, AuthenticationException> callback) {
        String[] allowedPackageInfo = configs.getStringArray(KEY_ALLOWED_CERTIFICATES_STRING_ARRAY);
        String certificate = getMatchingCertificate(allowedPackageInfo, packageName);
        if (isCallingPackageAllowed(allowedPackageInfo, packageName, certificate)) {
            obtainMessage(EVENT_REQUEST_OIDC_AUTHENTICATION_SERVER,
                    new OidcAuthenticationServerRequest(
                            getAppName(configs, packageName, certificate), appVersion, slotIndex,
                            entitlementServerAddress, entitlementVersion, appId, executor,
                            callback)).sendToTarget();
        } else {
            executor.execute(() -> callback.onError(new AuthenticationException(
                    AuthenticationException.ERROR_INVALID_APP_NAME,
                    "Failed to verify the identity of the calling application")));
        }
    }

    /**
     * Request authentication from the TS.43 server with OIDC (OpenID Connect) as described in
     * TS.43 Service Entitlement Configuration section 2.8.2.
     *
     * @param configs The configurations that should be applied to this authentication request.
     *        The keys of the bundle must be in {@link ConfigurationKey}.
     * @param packageName The package name for the calling application, used to validate the
     *        identity of the calling application.
     * @param entitlementServerAddress The entitlement server address.
     * @param entitlementVersion The TS.43 entitlement version to use. For example, {@code "9.0"}.
     *        If this is {@code null}, version {@code "2.0"} will be used by default.
     * @param aesUrl The AES URL used to retrieve the authentication token. The parameters in the
     *        URL include the OIDC authentication code {@code code} and {@code state}.
     * @param executor The executor on which the callback will be called.
     * @param callback The callback to receive the results of the authentication request.
     *        If authentication is successful,
     *        {@link AuthenticationOutcomeReceiver#onResult(Object)} will return a
     *        {@link Ts43Authentication.Ts43AuthToken} with the token and validity.
     *        If the authentication fails, {@link AuthenticationOutcomeReceiver#onError(Throwable)}
     *        will return an {@link AuthenticationException} with the failure details.
     */
    public void requestOidcAuthentication(PersistableBundle configs,
            String packageName, URL entitlementServerAddress,
            @Nullable String entitlementVersion, URL aesUrl, Executor executor,
            AuthenticationOutcomeReceiver<
                    Ts43Authentication.Ts43AuthToken, AuthenticationException> callback) {
        String[] allowedPackageInfo = configs.getStringArray(KEY_ALLOWED_CERTIFICATES_STRING_ARRAY);
        String certificate = getMatchingCertificate(allowedPackageInfo, packageName);
        if (isCallingPackageAllowed(allowedPackageInfo, packageName, certificate)) {
            obtainMessage(EVENT_REQUEST_OIDC_AUTHENTICATION, new OidcAuthenticationRequest(
                    entitlementServerAddress, entitlementVersion, aesUrl, executor, callback))
                    .sendToTarget();
        } else {
            executor.execute(() -> callback.onError(new AuthenticationException(
                    AuthenticationException.ERROR_INVALID_APP_NAME,
                    "Failed to verify the identity of the calling application")));
        }
    }

    @Nullable private String getMatchingCertificate(@Nullable String[] allowedPackageInfo,
            String packageName) {
        if (allowedPackageInfo == null || allowedPackageInfo.length == 0) {
            // No need to find a matching certificates if the allowlist is empty.
            Log.d(TAG, "No need to find a matching certificate because the allowlist is empty");
            return null;
        }

        // At this point an allowlist exists. A matching certificate must be found in order for
        // the authentication request to be validated. If this method returns {@code null} because
        // a matching certificate is unable to be found, the authentication request will be denied.
        List<String> allowedCertificates =
                getAllowedCertificatesForPackage(allowedPackageInfo, packageName);
        if (allowedCertificates.isEmpty()) {
            // If there are no allowed certificates for the given package, return null.
            Log.e(TAG, "No allowed certificates found for package: " + packageName);
            return null;
        }

        Signature[] signatures = getMainPackageSignatures(packageName);
        if (signatures == null) {
            // If there are no package signatures for the given package, return null.
            Log.e(TAG, "No package signatures for package: " + packageName);
            return null;
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // If parsing SHA-256 is not supported, return null.
            Log.wtf(TAG, "Unable to parse SHA-256 hash");
            return null;
        }

        // Check whether there is a match between the hashes from the package signature and the
        // hash from the certificates in the allowlist.
        for (Signature signature : signatures) {
            byte[] signatureHash = md.digest(signature.toByteArray());
            for (String certificate : allowedCertificates) {
                byte[] certificateHash = hexStringToBytes(certificate);
                if (Arrays.equals(signatureHash, certificateHash)) {
                    Log.d(TAG, "Found matching certificate for package " + packageName + ": "
                            + certificate);
                    return certificate;
                }
            }
        }
        Log.e(TAG, "No matching certificates for package: " + packageName);
        return null;
    }

    private List<String> getAllowedCertificatesForPackage(String[] allowedPackageInfo,
            String packageName) {
        List<String> allowedCertificates = new ArrayList<>();
        for (String packageInfo : allowedPackageInfo) {
            // packageInfo format: 1) "SHA256" or 2) "SHA256:package1,package2,package3..."
            String[] splitPackageInfo = packageInfo.split(":", -1);
            if (splitPackageInfo.length == 1) {
                // Case 1: Certificate only
                allowedCertificates.add(packageInfo);
            } else if (splitPackageInfo.length == 2) {
                // Case 2: Certificate and allowed packages
                String certificate = splitPackageInfo[0];
                String packages = splitPackageInfo[1];
                for (String allowedPackage : packages.split(",", -1)) {
                    // Add the certificate only if the package name is specified in the allowlist.
                    if (allowedPackage.equals(packageName)) {
                        allowedCertificates.add(certificate);
                        break;
                    }
                }
            }
        }
        return allowedCertificates;
    }

    @Nullable private Signature[] getMainPackageSignatures(String packageName) {
        PackageInfo packageInfo;
        try {
            if (Build.VERSION.SDK_INT < 33) {
                packageInfo = mPackageManager.getPackageInfo(packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                packageInfo = mPackageManager.getPackageInfo(packageName,
                        PackageManager.PackageInfoFlags.of(
                                PackageManager.GET_SIGNING_CERTIFICATES));
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to find package name: " + packageName);
            return null;
        }
        Signature[] signatures = packageInfo.signatures;
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo != null) {
            if (signingInfo.hasMultipleSigners()) {
                // If the application is signed by multiple signers, return the first signature.
                signatures = signingInfo.getApkContentsSigners();
            } else {
                // If there is a signing certificate history, return the most recent signature,
                // which is the last element in the list.
                signatures = signingInfo.getSigningCertificateHistory();
            }
        }
        if (signatures == null) {
            Log.e(TAG, "Unable to find package signatures for package: " + packageName);
        } else {
            Log.d(TAG, "Found package signatures for " + packageName + ": "
                    + Arrays.toString(signatures));
        }
        return signatures;
    }

    @Nullable private byte[] hexStringToBytes(@Nullable String hex) {
        if (hex == null) {
            return null;
        }

        int length = hex.length();
        byte[] ret = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            ret[i / 2] =
                    (byte) ((hexCharToInt(hex.charAt(i)) << 4) | hexCharToInt(hex.charAt(i + 1)));
        }
        return ret;
    }

    private int hexCharToInt(char hex) {
        return Integer.parseInt(String.valueOf(hex), 16);
    }

    private boolean isCallingPackageAllowed(@Nullable String[] allowedPackageInfo,
            String packageName, @Nullable String certificate) {
        // Check that the package name matches that of the calling package.
        if (!isPackageNameValidForCaller(packageName)) {
            return false;
        }

        // Check if we need to check for a certificate.
        if (allowedPackageInfo == null || allowedPackageInfo.length == 0) {
            return true;
        } else {
            // Check that a matching certificate exists.
            return certificate != null;
        }
    }

    private boolean isPackageNameValidForCaller(String packageName) {
        String[] packages = mPackageManager.getPackagesForUid(Binder.getCallingUid());
        for (String uidPackage : packages) {
            if (packageName.equals(uidPackage)) {
                return true;
            }
        }
        Log.e(TAG, "Package name " + packageName + " does not match those of the calling UID: "
                + Arrays.toString(packages));
        return false;
    }

    private String getAppName(PersistableBundle configs, String packageName,
            @Nullable String certificate) {
        if (configs.getString(KEY_OVERRIDE_APP_NAME_STRING) != null) {
            return configs.getString(KEY_OVERRIDE_APP_NAME_STRING);
        } else if (configs.getBoolean(KEY_APPEND_SHA_TO_APP_NAME_BOOL) && certificate != null) {
            return certificate + "|" + packageName;
        } else {
            return packageName;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_REQUEST_EAP_AKA_AUTHENTICATION:
                onRequestEapAkaAuthentication((EapAkaAuthenticationRequest) msg.obj);
                break;
            case EVENT_REQUEST_OIDC_AUTHENTICATION_SERVER:
                onRequestOidcAuthenticationServer((OidcAuthenticationServerRequest) msg.obj);
                break;
            case EVENT_REQUEST_OIDC_AUTHENTICATION:
                onRequestOidcAuthentication((OidcAuthenticationRequest) msg.obj);
                break;
            default:
                // unknown request
        }
    }

    private void onRequestEapAkaAuthentication(EapAkaAuthenticationRequest request) {
        request.mExecutor.execute(() -> {
            mLock.lock();
            try {
                Ts43Authentication authLibrary = new Ts43Authentication(mContext,
                        request.mEntitlementServerAddress, request.mEntitlementVersion);
                Ts43Authentication.Ts43AuthToken authToken = authLibrary.getAuthToken(
                        request.mSlotIndex, request.mAppId, request.mAppName, request.mAppVersion);
                request.mCallback.onResult(authToken);
            } catch (ServiceEntitlementException exception) {
                request.mCallback.onError(new AuthenticationException(exception));
            } finally {
                mLock.unlock();
            }
        });
    }

    private void onRequestOidcAuthenticationServer(OidcAuthenticationServerRequest request) {
        request.mExecutor.execute(() -> {
            mLock.lock();
            try {
                Ts43Authentication authLibrary = new Ts43Authentication(mContext,
                        request.mEntitlementServerAddress, request.mEntitlementVersion);
                URL url = authLibrary.getOidcAuthServer(
                        mContext, request.mSlotIndex, request.mEntitlementServerAddress,
                        request.mEntitlementVersion, request.mAppId, request.mAppName,
                        request.mAppVersion);
                request.mCallback.onResult(url);
            } catch (ServiceEntitlementException exception) {
                request.mCallback.onError(new AuthenticationException(exception));
            } finally {
                mLock.unlock();
            }
        });
    }

    private void onRequestOidcAuthentication(OidcAuthenticationRequest request) {
        request.mExecutor.execute(() -> {
            mLock.lock();
            try {
                Ts43Authentication authLibrary = new Ts43Authentication(mContext,
                        request.mEntitlementServerAddress, request.mEntitlementVersion);
                Ts43Authentication.Ts43AuthToken authToken = authLibrary.getAuthToken(
                        request.mAesUrl);
                request.mCallback.onResult(authToken);
            } catch (ServiceEntitlementException exception) {
                request.mCallback.onError(new AuthenticationException(exception));
            } finally {
                mLock.unlock();
            }
        });
    }
}
