/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.instrumented.utils;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.ConnectionListener;
import com.google.android.enterprise.connectedapps.UserConnector;
import java.util.concurrent.CountDownLatch;

public class UserConnectorTestUtilities {

  private static final int TIMEOUT_MS = 10_000;

  private final UserConnector connector;

  public UserConnectorTestUtilities(UserConnector connector) {
    this.connector = connector;
  }

  public void waitForConnected(UserHandle userHandle) {
    CountDownLatch connectionLatch = new CountDownLatch(1);

    ConnectionListener connectionListener =
        () -> {
          if (connector.isConnected(userHandle)) {
            connectionLatch.countDown();
          }
        };

    connector.addConnectionListener(userHandle, connectionListener);
    connectionListener.connectionChanged();

    try {
      connectionLatch.await(TIMEOUT_MS, MILLISECONDS);
    } catch (InterruptedException e) {
      throw new AssertionError("Error waiting to connect", e);
    } finally {
      connector.removeConnectionListener(userHandle, connectionListener);
    }
  }

  public void waitForDisconnected(UserHandle userHandle) {
    CountDownLatch connectionLatch = new CountDownLatch(1);

    ConnectionListener connectionListener =
        () -> {
          if (!connector.isConnected(userHandle)) {
            connectionLatch.countDown();
          }
        };

    connector.addConnectionListener(userHandle, connectionListener);
    connectionListener.connectionChanged();

    try {
      connectionLatch.await(TIMEOUT_MS, MILLISECONDS);
    } catch (InterruptedException e) {
      throw new AssertionError("Error waiting to disconnect", e);
    } finally {
      connector.removeConnectionListener(userHandle, connectionListener);
    }
  }

  public void addConnectionHolderAndWait(UserHandle userHandle, Object connectionHolder) {
    connector.addConnectionHolder(userHandle, connectionHolder);
    waitForConnected(userHandle);
  }
}
