/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package javax.crypto;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;

import android.annotation.SystemApi;

import java.util.*;
import java.security.*;
import sun.security.jca.*;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class KeyAgreement {

protected KeyAgreement(javax.crypto.KeyAgreementSpi keyAgreeSpi, java.security.Provider provider, java.lang.String algorithm) { throw new RuntimeException("Stub!"); }

public final java.lang.String getAlgorithm() { throw new RuntimeException("Stub!"); }

public static final javax.crypto.KeyAgreement getInstance(java.lang.String algorithm) throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

public static final javax.crypto.KeyAgreement getInstance(java.lang.String algorithm, java.lang.String provider) throws java.security.NoSuchAlgorithmException, java.security.NoSuchProviderException { throw new RuntimeException("Stub!"); }

public static final javax.crypto.KeyAgreement getInstance(java.lang.String algorithm, java.security.Provider provider) throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

public final java.security.Provider getProvider() { throw new RuntimeException("Stub!"); }

public final void init(java.security.Key key) throws java.security.InvalidKeyException { throw new RuntimeException("Stub!"); }

public final void init(java.security.Key key, java.security.SecureRandom random) throws java.security.InvalidKeyException { throw new RuntimeException("Stub!"); }

public final void init(java.security.Key key, java.security.spec.AlgorithmParameterSpec params) throws java.security.InvalidAlgorithmParameterException, java.security.InvalidKeyException { throw new RuntimeException("Stub!"); }

public final void init(java.security.Key key, java.security.spec.AlgorithmParameterSpec params, java.security.SecureRandom random) throws java.security.InvalidAlgorithmParameterException, java.security.InvalidKeyException { throw new RuntimeException("Stub!"); }

public final java.security.Key doPhase(java.security.Key key, boolean lastPhase) throws java.lang.IllegalStateException, java.security.InvalidKeyException { throw new RuntimeException("Stub!"); }

public final byte[] generateSecret() throws java.lang.IllegalStateException { throw new RuntimeException("Stub!"); }

public final int generateSecret(byte[] sharedSecret, int offset) throws java.lang.IllegalStateException, javax.crypto.ShortBufferException { throw new RuntimeException("Stub!"); }

public final javax.crypto.SecretKey generateSecret(java.lang.String algorithm) throws java.lang.IllegalStateException, java.security.InvalidKeyException, java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

@SystemApi(client = MODULE_LIBRARIES)
public javax.crypto.KeyAgreementSpi getCurrentSpi() { throw new RuntimeException("Stub!"); }
}

