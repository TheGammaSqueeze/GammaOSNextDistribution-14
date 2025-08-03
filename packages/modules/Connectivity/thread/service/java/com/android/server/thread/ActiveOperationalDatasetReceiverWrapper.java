/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.thread;

import static android.net.thread.ThreadNetworkException.ERROR_UNAVAILABLE;

import android.net.thread.ActiveOperationalDataset;
import android.net.thread.IActiveOperationalDatasetReceiver;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link IActiveOperationalDatasetReceiver} wrapper which makes it easier to invoke the
 * callbacks.
 */
final class ActiveOperationalDatasetReceiverWrapper {
    private final IActiveOperationalDatasetReceiver mReceiver;

    private static final Object sPendingReceiversLock = new Object();

    @GuardedBy("sPendingReceiversLock")
    private static final Set<ActiveOperationalDatasetReceiverWrapper> sPendingReceivers =
            new HashSet<>();

    public ActiveOperationalDatasetReceiverWrapper(IActiveOperationalDatasetReceiver receiver) {
        this.mReceiver = receiver;

        synchronized (sPendingReceiversLock) {
            sPendingReceivers.add(this);
        }
    }

    public static void onOtDaemonDied() {
        synchronized (sPendingReceiversLock) {
            for (ActiveOperationalDatasetReceiverWrapper receiver : sPendingReceivers) {
                try {
                    receiver.mReceiver.onError(ERROR_UNAVAILABLE, "Thread daemon died");
                } catch (RemoteException e) {
                    // The client is dead, do nothing
                }
            }
            sPendingReceivers.clear();
        }
    }

    public void onSuccess(ActiveOperationalDataset dataset) {
        synchronized (sPendingReceiversLock) {
            sPendingReceivers.remove(this);
        }

        try {
            mReceiver.onSuccess(dataset);
        } catch (RemoteException e) {
            // The client is dead, do nothing
        }
    }

    public void onError(int errorCode, String errorMessage) {
        synchronized (sPendingReceiversLock) {
            sPendingReceivers.remove(this);
        }

        try {
            mReceiver.onError(errorCode, errorMessage);
        } catch (RemoteException e) {
            // The client is dead, do nothing
        }
    }
}
