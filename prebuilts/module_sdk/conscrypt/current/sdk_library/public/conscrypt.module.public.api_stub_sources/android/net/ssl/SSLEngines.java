/*
 * Copyright (C) 2018 The Android Open Source Project
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


package android.net.ssl;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * Static utility methods for accessing additional functionality of supported instances of
 * {@link javax.net.ssl.SSLEngine SSLEngine}.  Engines from the platform TLS provider will be compatible with all
 * methods in this class.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class SSLEngines {

private SSLEngines() { throw new RuntimeException("Stub!"); }

/**
 * Returns whether the given engine can be used with the methods in this class.  In general,
 * only engines from the platform TLS provider are supported.
 */

public static boolean isSupportedEngine(@android.annotation.NonNull javax.net.ssl.SSLEngine engine) { throw new RuntimeException("Stub!"); }

/**
 * Enables or disables the use of session tickets.
 *
 * <p>This function must be called before the handshake is started or it will have no effect.
 *
 * @param engine the engine
 * @param useSessionTickets whether to enable or disable the use of session tickets
 * @throws java.lang.IllegalArgumentException if the given engine is not a platform engine
 */

public static void setUseSessionTickets(@android.annotation.NonNull javax.net.ssl.SSLEngine engine, boolean useSessionTickets) { throw new RuntimeException("Stub!"); }

/**
 * Exports a value derived from the TLS master secret as described in RFC 5705.
 *
 * A number of protocols leverage Transport Layer Security (TLS) to perform key
 * establishment but then use some of the keying material for their own purposes.
 *
 * This method allows an application to export keying material from a TLS connection.
 * The exported material will be the same on the client and server if they pass in
 * the same values for {@code label} and {@code context}.  See RFC 5705 for further
 * details.
 *
 * @param engine the engine to use for exporting keying material
 * @param label the label to use in calculating the exported value.  This must be
 * an ASCII-only string.
 * @param context the application-specific context value to use in calculating the
 * exported value.  This may be {@code null} to use no application context, which is
 * treated differently than an empty byte array.
 * @param length the number of bytes of keying material to return.
 * @return a value of the specified length, or {@code null} if the handshake has not yet
 * completed or the connection has been closed.
 * @throws javax.net.ssl.SSLException if the value could not be exported.
 */

@android.annotation.Nullable
public static byte[] exportKeyingMaterial(@android.annotation.NonNull javax.net.ssl.SSLEngine engine, @android.annotation.NonNull java.lang.String label, @android.annotation.Nullable byte[] context, int length) throws javax.net.ssl.SSLException { throw new RuntimeException("Stub!"); }
}

