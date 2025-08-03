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

package com.android.car.debuggingrestrictioncontroller.auth;

import android.util.Log;
import com.android.car.debuggingrestrictioncontroller.BuildConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class SelfSignedTrustManager implements X509TrustManager {

  private static final String TAG = SelfSignedTrustManager.class.getSimpleName();
  private static final String ROOT_CA = BuildConfig.ROOT_CA_CERT;
  private static SelfSignedTrustManager instance;
  private X509TrustManager trustManager;

  private SelfSignedTrustManager() throws GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    try {
      keyStore.load(null, null);
    } catch (IOException e) {
      Log.e(TAG, "Creating an empty KeyStore and this error should not happen");
      throw new GeneralSecurityException(e);
    }

    X509Certificate cert =
        (X509Certificate)
            CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(ROOT_CA.getBytes()));
    keyStore.setCertificateEntry(UUID.randomUUID().toString(), cert);

    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(keyStore);
    this.trustManager = (X509TrustManager) tmf.getTrustManagers()[0];
  }

  public static synchronized SelfSignedTrustManager getInstance() throws GeneralSecurityException {
    if (instance == null) {
      instance = new SelfSignedTrustManager();
    }
    return instance;
  }

  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    trustManager.checkClientTrusted(chain, authType);
  }

  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    trustManager.checkServerTrusted(chain, authType);
  }

  public X509Certificate[] getAcceptedIssuers() {
    return trustManager.getAcceptedIssuers();
  }
}
