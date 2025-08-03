/**
 * Copyright (c) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.thread;

import android.net.thread.ActiveOperationalDataset;
import android.net.thread.IActiveOperationalDatasetReceiver;
import android.net.thread.IOperationalDatasetCallback;
import android.net.thread.IOperationReceiver;
import android.net.thread.IScheduleMigrationReceiver;
import android.net.thread.IStateCallback;
import android.net.thread.PendingOperationalDataset;

/**
* Interface for communicating with ThreadNetworkControllerService.
* @hide
*/
interface IThreadNetworkController {
    void registerStateCallback(in IStateCallback callback);
    void unregisterStateCallback(in IStateCallback callback);
    void registerOperationalDatasetCallback(in IOperationalDatasetCallback callback);
    void unregisterOperationalDatasetCallback(in IOperationalDatasetCallback callback);

    void join(in ActiveOperationalDataset activeOpDataset, in IOperationReceiver receiver);
    void scheduleMigration(in PendingOperationalDataset pendingOpDataset, in IOperationReceiver receiver);
    void leave(in IOperationReceiver receiver);

    void setTestNetworkAsUpstream(in String testNetworkInterfaceName, in IOperationReceiver receiver);

    int getThreadVersion();
    void createRandomizedDataset(String networkName, IActiveOperationalDatasetReceiver receiver);

    void setEnabled(boolean enabled, in IOperationReceiver receiver);
}
