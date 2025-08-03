/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.harmony.xnet.tests.support;

import javax.net.ssl.SSLSocketFactory;

import java.net.InetAddress;
import java.net.Socket;
import java.io.IOException;
import java.net.UnknownHostException;


public class SSLSocketFactoryImpl extends SSLSocketFactory {

    public SSLSocketFactoryImpl() {
        super();
    }

    public Socket createSocket(Socket socket, String s, int i, boolean flag)
                               throws IOException {
        if (socket == null) {
            throw new IOException("incorrect socket");
        }
        if (i < 0 || i > 65535) {
            throw new IOException("incorrect port");
        }
        if (s == null || s.equals("")) {
            throw new UnknownHostException("incorrect host");
        }

        if (!flag) {
            socket = new Socket(s, i);
        } else {
            socket = new Socket(s, i);
            socket.close();
        }
        return socket;
    }

    public String[] getDefaultCipherSuites() {
        return null;
    }

    public String[] getSupportedCipherSuites() {
        return null;
    }

    /**
     * @see javax.net.SocketFactory#createSocket(java.lang.String, int)
     */
    @Override
    public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException {
        // it is a fake
        return null;
    }

    /**
     * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
        // it is a fake
        return null;
    }

    /**
     * @see javax.net.SocketFactory#createSocket(java.lang.String, int, java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException {
        // it is a fake
        return null;
    }

    /**
     * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int, java.net.InetAddress, int)
     */
    @Override
    public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException {
        // it is a fake
        return null;
    }
}
