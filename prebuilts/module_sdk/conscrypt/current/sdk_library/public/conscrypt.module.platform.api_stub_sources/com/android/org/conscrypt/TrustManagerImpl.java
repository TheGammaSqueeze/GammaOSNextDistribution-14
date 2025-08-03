/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2016 The Android Open Source Project
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

// License from Apache Harmony:
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package com.android.org.conscrypt;

import javax.net.ssl.X509ExtendedTrustManager;
import java.security.cert.CertificateException;

/**
 *
 * TrustManager implementation. The implementation is based on CertPathValidator
 * PKIX and CertificateFactory X509 implementations. This implementations should
 * be provided by some certification provider.
 *
 * @see javax.net.ssl.X509ExtendedTrustManager
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TrustManagerImpl extends javax.net.ssl.X509ExtendedTrustManager {

/**
 * Creates X509TrustManager based on a keystore
 */

public TrustManagerImpl(java.security.KeyStore keyStore) { throw new RuntimeException("Stub!"); }

public TrustManagerImpl(java.security.KeyStore keyStore, com.android.org.conscrypt.CertPinManager manager, com.android.org.conscrypt.ConscryptCertStore certStore) { throw new RuntimeException("Stub!"); }

public void checkClientTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

public void checkClientTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType, java.net.Socket socket) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

public void checkClientTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

public void checkServerTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

/**
 * For backward compatibility with older Android API that used String for the hostname only.
 */

public java.util.List<java.security.cert.X509Certificate> checkServerTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType, java.lang.String hostname) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

/**
 * Returns the full trusted certificate chain found from {@code certs}.
 *
 * Throws {@link java.security.cert.CertificateException CertificateException} when no trusted chain can be found from {@code certs}.
 */

public java.util.List<java.security.cert.X509Certificate> getTrustedChainForServer(java.security.cert.X509Certificate[] certs, java.lang.String authType, java.net.Socket socket) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

/**
 * Returns the full trusted certificate chain found from {@code certs}.
 *
 * Throws {@link java.security.cert.CertificateException CertificateException} when no trusted chain can be found from {@code certs}.
 */

public java.util.List<java.security.cert.X509Certificate> getTrustedChainForServer(java.security.cert.X509Certificate[] certs, java.lang.String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

public void checkServerTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType, java.net.Socket socket) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

public void checkServerTrusted(java.security.cert.X509Certificate[] chain, java.lang.String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException { throw new RuntimeException("Stub!"); }

public void handleTrustStorageUpdate() { throw new RuntimeException("Stub!"); }

public java.security.cert.X509Certificate[] getAcceptedIssuers() { throw new RuntimeException("Stub!"); }
}

