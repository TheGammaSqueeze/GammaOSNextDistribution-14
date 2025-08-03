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


/**
 * Caches client sessions. Indexes by host and port. Users are typically
 * looking to reuse any session for a given host and port.
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ClientSessionContext implements javax.net.ssl.SSLSessionContext {

ClientSessionContext() { throw new RuntimeException("Stub!"); }

/**
 * Applications should not use this method. Instead use {@link com.android.org.conscrypt.Conscrypt#setClientSessionCache(javax.net.ssl.SSLContext,com.android.org.conscrypt.SSLClientSessionCache)  }.
 */

public void setPersistentCache(com.android.org.conscrypt.SSLClientSessionCache persistentCache) { throw new RuntimeException("Stub!"); }

public final void setSessionTimeout(int seconds) throws java.lang.IllegalArgumentException { throw new RuntimeException("Stub!"); }

public final int getSessionCacheSize() { throw new RuntimeException("Stub!"); }

public final int getSessionTimeout() { throw new RuntimeException("Stub!"); }

/**
 * This method is provided for API-compatibility only, not intended for use. No guarantees
 * are made WRT performance.
 */

public final java.util.Enumeration<byte[]> getIds() { throw new RuntimeException("Stub!"); }

/**
 * This is provided for API-compatibility only, not intended for use. No guarantees are
 * made WRT performance or the validity of the returned session.
 */

public final javax.net.ssl.SSLSession getSession(byte[] sessionId) { throw new RuntimeException("Stub!"); }

public final void setSessionCacheSize(int size) throws java.lang.IllegalArgumentException { throw new RuntimeException("Stub!"); }
}

