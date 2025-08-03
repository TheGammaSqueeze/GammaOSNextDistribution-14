/*
 * Copyright (c) 1996, 2021, Oracle and/or its affiliates. All rights reserved.
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


package java.security;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class SecureRandom extends java.util.Random {

public SecureRandom() { throw new RuntimeException("Stub!"); }

public SecureRandom(byte[] seed) { throw new RuntimeException("Stub!"); }

protected SecureRandom(java.security.SecureRandomSpi secureRandomSpi, java.security.Provider provider) { throw new RuntimeException("Stub!"); }

public static java.security.SecureRandom getInstance(java.lang.String algorithm) throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

public static java.security.SecureRandom getInstance(java.lang.String algorithm, java.lang.String provider) throws java.security.NoSuchAlgorithmException, java.security.NoSuchProviderException { throw new RuntimeException("Stub!"); }

public static java.security.SecureRandom getInstance(java.lang.String algorithm, java.security.Provider provider) throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static java.security.SecureRandom getInstance(java.lang.String algorithm, java.security.SecureRandomParameters params) throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static java.security.SecureRandom getInstance(java.lang.String algorithm, java.security.SecureRandomParameters params, java.lang.String provider) throws java.security.NoSuchAlgorithmException, java.security.NoSuchProviderException { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static java.security.SecureRandom getInstance(java.lang.String algorithm, java.security.SecureRandomParameters params, java.security.Provider provider) throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

public final java.security.Provider getProvider() { throw new RuntimeException("Stub!"); }

public java.lang.String getAlgorithm() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.security.SecureRandomParameters getParameters() { throw new RuntimeException("Stub!"); }

public void setSeed(byte[] seed) { throw new RuntimeException("Stub!"); }

public void setSeed(long seed) { throw new RuntimeException("Stub!"); }

public void nextBytes(byte[] bytes) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void nextBytes(byte[] bytes, java.security.SecureRandomParameters params) { throw new RuntimeException("Stub!"); }

protected final int next(int numBits) { throw new RuntimeException("Stub!"); }

public static byte[] getSeed(int numBytes) { throw new RuntimeException("Stub!"); }

public byte[] generateSeed(int numBytes) { throw new RuntimeException("Stub!"); }

public static java.security.SecureRandom getInstanceStrong() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void reseed() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void reseed(java.security.SecureRandomParameters params) { throw new RuntimeException("Stub!"); }
}

