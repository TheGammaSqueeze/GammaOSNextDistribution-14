/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.remoteauth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.connectivity.Connection;
import com.android.server.remoteauth.connectivity.ConnectionException;
import com.android.server.remoteauth.connectivity.ConnectionInfo;
import com.android.server.remoteauth.connectivity.ConnectivityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link com.android.server.remoteauth.RemoteAuthConnectionCache} */
@RunWith(AndroidJUnit4.class)
public class RemoteAuthConnectionCacheTest {
    @Mock private Connection mConnection;
    @Mock private ConnectionInfo mConnectionInfo;
    @Mock private ConnectivityManager mConnectivityManager;
    private RemoteAuthConnectionCache mConnectionCache;

    private static final int CONNECTION_ID = 1;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(CONNECTION_ID).when(mConnectionInfo).getConnectionId();
        doReturn(mConnectionInfo).when(mConnection).getConnectionInfo();
        mConnectionCache = new RemoteAuthConnectionCache(mConnectivityManager);
    }

    @Test
    public void testCreateCache_managerIsNull() {
        assertThrows(NullPointerException.class, () -> new RemoteAuthConnectionCache(null));
    }

    @Test
    public void testGetManager_managerExists() {
        assertEquals(mConnectivityManager, mConnectionCache.getConnectivityManager());
    }

    @Test
    public void testSetConnectionInfo_infoIsNull() {
        assertThrows(NullPointerException.class, () -> mConnectionCache.setConnectionInfo(null));
    }

    @Test
    public void testSetConnectionInfo_infoIsValid() {
        mConnectionCache.setConnectionInfo(mConnectionInfo);

        assertEquals(mConnectionInfo, mConnectionCache.getConnectionInfo(CONNECTION_ID));
    }

    @Test
    public void testSetConnection_connectionIsNull() {
        assertThrows(NullPointerException.class, () -> mConnectionCache.setConnection(null));
    }

    @Test
    public void testGetConnection_connectionAlreadyExists() {
        mConnectionCache.setConnection(mConnection);

        assertEquals(mConnection, mConnectionCache.getConnection(CONNECTION_ID));
    }

    @Test
    public void testGetConnection_connectionInfoDoesntExists() {
        assertNull(mConnectionCache.getConnection(CONNECTION_ID));
    }

    @Test
    public void testGetConnection_failedToConnect() {
        mConnectionCache.setConnectionInfo(mConnectionInfo);
        doReturn(null).when(mConnectivityManager).connect(eq(mConnectionInfo), anyObject());

        assertNull(mConnectionCache.getConnection(CONNECTION_ID));
    }

    @Test
    public void testGetConnection_failedToConnectException() {
        mConnectionCache.setConnectionInfo(mConnectionInfo);
        doThrow(ConnectionException.class)
                .when(mConnectivityManager)
                .connect(eq(mConnectionInfo), anyObject());

        assertNull(mConnectionCache.getConnection(CONNECTION_ID));
    }

    @Test
    public void testGetConnection_connectionSucceed() {
        mConnectionCache.setConnectionInfo(mConnectionInfo);
        doReturn(mConnection).when(mConnectivityManager).connect(eq(mConnectionInfo), anyObject());

        assertEquals(mConnection, mConnectionCache.getConnection(CONNECTION_ID));
    }
}
