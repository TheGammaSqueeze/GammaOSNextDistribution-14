/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;

import java.util.Timer;
import java.util.TimerTask;

public class CallNotificationService extends ConnectionService {
  public static final int DURATION_MS = 7000;

  @Override
  public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
      ConnectionRequest request) {
    Connection sampleConnection = new Connection() {};
    Timer timer = new Timer();
    timer.schedule(new Task(sampleConnection), DURATION_MS);
    return sampleConnection;
  }

  static class Task extends TimerTask {
    Connection mConnection;

    Task(Connection sampleConnection) {
      this.mConnection = sampleConnection;
    }

    @Override
    public void run() {
      destroyConnection();
    }

    private void destroyConnection() {
      if (mConnection != null) {
        mConnection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        mConnection.destroy();
      }
    }
  }
}
