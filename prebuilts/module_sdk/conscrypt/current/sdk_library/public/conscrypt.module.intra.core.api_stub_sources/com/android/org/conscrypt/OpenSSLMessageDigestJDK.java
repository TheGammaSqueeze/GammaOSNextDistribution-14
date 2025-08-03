/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2008 The Android Open Source Project
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


/**
 * Implements the JDK MessageDigest interface using OpenSSL's EVP API.
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class OpenSSLMessageDigestJDK extends java.security.MessageDigestSpi implements java.lang.Cloneable {

private OpenSSLMessageDigestJDK() { throw new RuntimeException("Stub!"); }

protected synchronized void engineReset() { throw new RuntimeException("Stub!"); }

protected int engineGetDigestLength() { throw new RuntimeException("Stub!"); }

protected synchronized void engineUpdate(byte input) { throw new RuntimeException("Stub!"); }

protected synchronized void engineUpdate(byte[] input, int offset, int len) { throw new RuntimeException("Stub!"); }

protected synchronized void engineUpdate(java.nio.ByteBuffer input) { throw new RuntimeException("Stub!"); }

protected synchronized byte[] engineDigest() { throw new RuntimeException("Stub!"); }

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }
/**
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class MD5 extends com.android.org.conscrypt.OpenSSLMessageDigestJDK {

public MD5() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }
}

/**
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class SHA1 extends com.android.org.conscrypt.OpenSSLMessageDigestJDK {

public SHA1() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }
}

/**
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class SHA224 extends com.android.org.conscrypt.OpenSSLMessageDigestJDK {

public SHA224() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }
}

/**
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class SHA256 extends com.android.org.conscrypt.OpenSSLMessageDigestJDK {

public SHA256() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }
}

/**
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class SHA384 extends com.android.org.conscrypt.OpenSSLMessageDigestJDK {

public SHA384() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }
}

/**
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class SHA512 extends com.android.org.conscrypt.OpenSSLMessageDigestJDK {

public SHA512() throws java.security.NoSuchAlgorithmException { throw new RuntimeException("Stub!"); }
}

}

