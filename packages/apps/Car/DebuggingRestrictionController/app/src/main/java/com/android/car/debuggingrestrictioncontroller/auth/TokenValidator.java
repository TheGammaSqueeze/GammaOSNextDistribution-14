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

import androidx.annotation.NonNull;
import com.android.car.debuggingrestrictioncontroller.BuildConfig;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

public final class TokenValidator {

  private static final String TAG = TokenValidator.class.getSimpleName();
  private static final String TOKEN_ISSUER_HOST_NAME = BuildConfig.TOKEN_ISSUER_HOST_NAME;
  private static final HostnameVerifier HOSTNAME_VERIFIER =
      HttpsURLConnection.getDefaultHostnameVerifier();

  private static final Duration ACCEPTABLE_TIME_SKEW = Duration.ofMinutes(1);

  public static TokenPayload parseAndVerify(
      @NonNull String authTokenString, @NonNull String expectedNonce)
      throws GeneralSecurityException {
    JsonWebSignature jws;
    // Step 0: Parse the string into a JWS
    try {
      jws =
          JsonWebSignature.parser(JacksonFactory.getDefaultInstance())
              .setPayloadClass(TokenPayload.class)
              .parse(authTokenString);
    } catch (IOException e) {
      throw new GeneralSecurityException(e);
    }

    // Step 1: Verify the signature of the JWS and retrieve the signature certificate
    X509Certificate cert;
    if (!BuildConfig.TOKEN_USES_SELF_SIGNED_CA) {
      cert = jws.verifySignature();
    } else {
      cert = jws.verifySignature(SelfSignedTrustManager.getInstance());
    }
    if (cert == null) {
      throw new SignatureException("Invalid signature");
    }

    // Step 2: verify the signature certificate matches the specified hostname
    StubSSLSession session = new StubSSLSession();
    session.certificates = new Certificate[] {cert};
    if (!HOSTNAME_VERIFIER.verify(TOKEN_ISSUER_HOST_NAME, session)) {
      throw new GeneralSecurityException(new UnknownHostException("Unexpected hostname"));
    }

    // Step 3: verify the payload
    TokenPayload payload = (TokenPayload) jws.getPayload();
    if (payload.getNonce().trim().equals(expectedNonce)) {
      throw new GeneralSecurityException("Nonce mismatch");
    }

    if (Instant.now()
        .minus(ACCEPTABLE_TIME_SKEW)
        .isAfter(Instant.ofEpochSecond(payload.getExpirationTimeSeconds()))) {
      throw new CertificateExpiredException("Token expired");
    }
    return payload;
  }

  private static class StubSSLSession implements SSLSession {

    public Certificate[] certificates = new Certificate[0];

    @Override
    public int getApplicationBufferSize() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getCipherSuite() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getCreationTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getLastAccessedTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Certificate[] getLocalCertificates() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Principal getLocalPrincipal() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPacketBufferSize() {
      throw new UnsupportedOperationException();
    }

    @Override
    public javax.security.cert.X509Certificate[] getPeerCertificateChain()
        throws SSLPeerUnverifiedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
      return certificates;
    }

    @Override
    public String getPeerHost() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPeerPort() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getProtocol() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SSLSessionContext getSessionContext() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getValue(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String[] getValueNames() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void invalidate() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public void putValue(String name, Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeValue(String name) {
      throw new UnsupportedOperationException();
    }
  }
}
