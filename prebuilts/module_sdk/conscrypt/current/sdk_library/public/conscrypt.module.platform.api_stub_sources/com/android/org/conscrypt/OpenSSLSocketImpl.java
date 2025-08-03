/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * Public shim allowing us to stay backward-compatible with legacy applications which were using
 * Conscrypt's extended socket API before the introduction of the {@link com.android.org.conscrypt.Conscrypt Conscrypt} class.
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class OpenSSLSocketImpl extends javax.net.ssl.SSLSocket {

OpenSSLSocketImpl() { throw new RuntimeException("Stub!"); }

public void setHostname(java.lang.String hostname) { throw new RuntimeException("Stub!"); }

public java.io.FileDescriptor getFileDescriptor$() { throw new RuntimeException("Stub!"); }

public void setSoWriteTimeout(int writeTimeoutMilliseconds) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public void setHandshakeTimeout(int handshakeTimeoutMilliseconds) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public abstract javax.net.ssl.SSLSession getHandshakeSession();

public abstract void setUseSessionTickets(boolean useSessionTickets);

public abstract void setChannelIdPrivateKey(java.security.PrivateKey privateKey);

/**
 * @deprecated NPN is not supported
 */

@Deprecated
public final byte[] getNpnSelectedProtocol() { throw new RuntimeException("Stub!"); }

/**
 * @deprecated NPN is not supported
 */

@Deprecated
public final void setNpnProtocols(byte[] npnProtocols) { throw new RuntimeException("Stub!"); }

/**
 * @deprecated use {@link #getApplicationProtocol()} instead.
 */

@Deprecated
public final byte[] getAlpnSelectedProtocol() { throw new RuntimeException("Stub!"); }

/**
 * @deprecated Use {@link #setAlpnProtocols(java.lang.String[])} instead.
 */

@Deprecated
public final void setAlpnProtocols(byte[] protocols) { throw new RuntimeException("Stub!"); }

public java.net.InetAddress getInetAddress() { throw new RuntimeException("Stub!"); }

public boolean getOOBInline() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public int getLocalPort() { throw new RuntimeException("Stub!"); }

public void setReceiveBufferSize(int size) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public boolean isInputShutdown() { throw new RuntimeException("Stub!"); }

public void removeHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener) { throw new RuntimeException("Stub!"); }

public void setTcpNoDelay(boolean on) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public boolean isConnected() { throw new RuntimeException("Stub!"); }

public boolean getKeepAlive() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public java.net.InetAddress getLocalAddress() { throw new RuntimeException("Stub!"); }

public void bind(java.net.SocketAddress bindpoint) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public java.nio.channels.SocketChannel getChannel() { throw new RuntimeException("Stub!"); }

public int getSoLinger() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public boolean getTcpNoDelay() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public void setSoLinger(boolean on, int linger) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public final int getSoTimeout() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public boolean isOutputShutdown() { throw new RuntimeException("Stub!"); }

public void close() throws java.io.IOException { throw new RuntimeException("Stub!"); }

public final void connect(java.net.SocketAddress endpoint) throws java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * Try to extract the peer's hostname if it's available from the endpoint address.
 */

public final void connect(java.net.SocketAddress endpoint, int timeout) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public void setTrafficClass(int tc) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public final void setOOBInline(boolean on) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public java.net.SocketAddress getLocalSocketAddress() { throw new RuntimeException("Stub!"); }

public int getReceiveBufferSize() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public void setReuseAddress(boolean on) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public void addHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener) { throw new RuntimeException("Stub!"); }

public int getTrafficClass() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public boolean getReuseAddress() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public final void setSoTimeout(int readTimeoutMilliseconds) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public void setKeepAlive(boolean on) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public java.io.InputStream getInputStream() throws java.io.IOException { throw new RuntimeException("Stub!"); }

public void shutdownOutput() throws java.io.IOException { throw new RuntimeException("Stub!"); }

public void shutdownInput() throws java.io.IOException { throw new RuntimeException("Stub!"); }

public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) { throw new RuntimeException("Stub!"); }

public final int getPort() { throw new RuntimeException("Stub!"); }

public final void sendUrgentData(int data) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public boolean isClosed() { throw new RuntimeException("Stub!"); }

public boolean isBound() { throw new RuntimeException("Stub!"); }

public void setSendBufferSize(int size) throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public int getSendBufferSize() throws java.net.SocketException { throw new RuntimeException("Stub!"); }

public java.io.OutputStream getOutputStream() throws java.io.IOException { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

public java.net.SocketAddress getRemoteSocketAddress() { throw new RuntimeException("Stub!"); }
}

