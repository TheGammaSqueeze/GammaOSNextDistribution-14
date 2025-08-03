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

package com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor;

import android.net.Uri;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.gba.TlsParams;
import android.telephony.gba.UaSecurityProtocolIdentifier;
import android.util.Log;

import com.google.auto.value.AutoValue;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.SettableFuture;

import org.apache.http.auth.Credentials;

import java.security.Principal;
import java.util.concurrent.Executor;

/** Provides GBA authentication credentials. */
public class GbaAuthenticationProvider {

    private static final String TAG = "GbaAuthenticationProvider";
    private final TelephonyManager telephonyManager;
    private final String contentServerUrl;
    private final Executor executor;

    public GbaAuthenticationProvider(
            TelephonyManager telephonyManager, String contentServerUrl, Executor executor) {
        this.telephonyManager = telephonyManager;
        this.contentServerUrl = contentServerUrl;
        this.executor = executor;
    }

    public SettableFuture<Credentials> provideCredentials(boolean forceBootstrapping) {
        SettableFuture<Credentials> credentialsFuture = SettableFuture.create();

        UaSecurityProtocolIdentifier.Builder builder =
                new UaSecurityProtocolIdentifier.Builder();
        try {
            PersistableBundle carrierConfig = telephonyManager.getCarrierConfig();
            int organization = carrierConfig.getInt(
                    CarrierConfigManager.KEY_GBA_UA_SECURITY_ORGANIZATION_INT);
            int protocol = carrierConfig.getInt(
                    CarrierConfigManager.KEY_GBA_UA_SECURITY_PROTOCOL_INT);
            int cipherSuite = carrierConfig.getInt(
                    CarrierConfigManager.KEY_GBA_UA_TLS_CIPHER_SUITE_INT);
            Log.i(TAG, "organization:" + organization + ", protocol:" + protocol + ", cipherSuite:"
                    + cipherSuite + ", contentServerUrl:" + contentServerUrl);

            builder.setOrg(organization)
                    .setProtocol(protocol);
            if (cipherSuite == TlsParams.TLS_NULL_WITH_NULL_NULL) {
                builder.setTlsCipherSuite(TlsParams.TLS_RSA_WITH_AES_128_CBC_SHA);
            } else {
                builder.setTlsCipherSuite(cipherSuite);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
            credentialsFuture.setException(e);
            return credentialsFuture;
        }
        UaSecurityProtocolIdentifier spId = builder.build();
        TelephonyManager.BootstrapAuthenticationCallback callback =
                new TelephonyManager.BootstrapAuthenticationCallback() {
                    @Override
                    public void onKeysAvailable(byte[] gbaKey, String btId) {
                        Log.i(TAG, "onKeysAvailable: String key:[" + new String(gbaKey) + "] btid:["
                                + btId + "]" + "Base64 key:[" + BaseEncoding.base64().encode(gbaKey)
                                + "]");
                        credentialsFuture.set(GbaCredentials.create(btId, gbaKey));
                    }

                    @Override
                    public void onAuthenticationFailure(int reason) {
                        Log.i(TAG, "onAuthenticationFailure:" + reason);
                        credentialsFuture.setException(
                                new BootstrapAuthenticationException(reason));
                    }
                };
        telephonyManager.bootstrapAuthenticationRequest(
                TelephonyManager.APPTYPE_ISIM,
                Uri.parse(contentServerUrl),
                spId,
                forceBootstrapping,
                executor,
                callback);

        return credentialsFuture;
    }

    @SuppressWarnings("AndroidJdkLibsChecker")
    @AutoValue
    abstract static class GbaCredentials implements Credentials {

        public static GbaCredentials create(String btId, byte[] gbaKey) {
            return new AutoValue_GbaAuthenticationProvider_GbaCredentials(
                    GbaPrincipal.create(btId), BaseEncoding.base64().encode(gbaKey));
        }

        @Override
        public abstract Principal getUserPrincipal();

        @Override
        public abstract String getPassword();
    }

    @AutoValue
    abstract static class GbaPrincipal implements Principal {

        public static GbaPrincipal create(String name) {
            return new AutoValue_GbaAuthenticationProvider_GbaPrincipal(name);
        }

        @Override
        public abstract String getName();
    }

    static class BootstrapAuthenticationException extends Exception {
        BootstrapAuthenticationException(int reason) {
            super("Bootstrap authentication request failure: " + reason);
        }
    }
}
