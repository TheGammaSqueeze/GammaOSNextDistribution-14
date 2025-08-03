/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.security.Provider;

/**
 * Provider that uses BoringSSL to perform the actual cryptographic operations.
 * <p>
 * Every algorithm should have its IANA assigned OID as an alias. See the following URLs for each
 * type: <ul> <li><a
 * href="http://www.iana.org/assignments/hash-function-text-names/hash-function-text-names.xml">Hash
 * functions</a></li> <li><a href="http://www.iana.org/assignments/dssc/dssc.xml">Signature
 * algorithms</a></li> <li><a
 * href="http://csrc.nist.gov/groups/ST/crypto_apps_infra/csor/algorithms.html">NIST cryptographic
 * algorithms</a></li>
 * </ul>
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class OpenSSLProvider extends java.security.Provider {

public OpenSSLProvider() { super(null, (double)0, null); throw new RuntimeException("Stub!"); }
}

