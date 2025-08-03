/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2009 The Android Open Source Project
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


package com.android.org.conscrypt;

import java.security.cert.TrustAnchor;

/**
 * Indexes {@code TrustAnchor} instances so they can be found in O(1)
 * time instead of O(N).
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TrustedCertificateIndex {

public TrustedCertificateIndex() { throw new RuntimeException("Stub!"); }

public java.security.cert.TrustAnchor index(java.security.cert.X509Certificate cert) { throw new RuntimeException("Stub!"); }

public java.security.cert.TrustAnchor findByIssuerAndSignature(java.security.cert.X509Certificate cert) { throw new RuntimeException("Stub!"); }

public java.security.cert.TrustAnchor findBySubjectAndPublicKey(java.security.cert.X509Certificate cert) { throw new RuntimeException("Stub!"); }

public java.util.Set<java.security.cert.TrustAnchor> findAllByIssuerAndSignature(java.security.cert.X509Certificate cert) { throw new RuntimeException("Stub!"); }
}

