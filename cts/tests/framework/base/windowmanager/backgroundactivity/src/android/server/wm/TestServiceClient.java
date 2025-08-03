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

package android.server.wm;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.server.wm.backgroundactivity.common.ITestService;
import android.view.textclassifier.TextClassification;

public class TestServiceClient implements ITestService {

    ITestService mTestService;

    TestServiceClient(ITestService mTestService) {
        this.mTestService = mTestService;
    }

    @Override
    public PendingIntent generatePendingIntent(ComponentName componentName, int flags,
            Bundle createOptions, ResultReceiver resultReceiver)
            throws RemoteException {
        return mTestService.generatePendingIntent(componentName, flags, createOptions,
                resultReceiver);
    }

    public PendingIntent generatePendingIntent(ComponentName componentName, int flags,
            Bundle createOptions)
            throws RemoteException {
        return generatePendingIntent(componentName, flags, createOptions, null);
    }

    public PendingIntent generatePendingIntent(ComponentName componentName, Bundle createOptions)
            throws RemoteException {
        return generatePendingIntent(componentName, 0, createOptions);
    }

    public PendingIntent generatePendingIntent(ComponentName componentName)
            throws RemoteException {
        return generatePendingIntent(componentName, 0, Bundle.EMPTY);
    }

    @Override
    public PendingIntent generatePendingIntentBroadcast(ComponentName componentName,
            ResultReceiver resultReceiver) throws RemoteException {
        return mTestService.generatePendingIntentBroadcast(componentName, resultReceiver);
    }

    public PendingIntent generatePendingIntentBroadcast(ComponentName componentName)
            throws RemoteException {
        return generatePendingIntentBroadcast(componentName, null);
    }

    @Override
    public void startManageSpaceActivity() throws RemoteException {
        mTestService.startManageSpaceActivity();
    }

    @Override
    public void sendByTextClassification(TextClassification classification) throws RemoteException {
        mTestService.sendByTextClassification(classification);
    }

    @Override
    public void sendPendingIntent(PendingIntent pendingIntent, Bundle sendOptions)
            throws RemoteException {
        mTestService.sendPendingIntent(pendingIntent, sendOptions);
    }

    public void sendPendingIntent(PendingIntent pendingIntent) throws RemoteException {
        sendPendingIntent(pendingIntent, Bundle.EMPTY);
    }

    /**
     * @see android.server.wm.backgroundactivity.common.TestService#sendPendingIntentWithActivity(
     *          PendingIntent, Bundle)
     */
    public void sendPendingIntentWithActivity(PendingIntent pendingIntent, Bundle sendOptions)
            throws RemoteException {
        mTestService.sendPendingIntentWithActivity(pendingIntent, sendOptions);
    }

    /**
     * @see android.server.wm.backgroundactivity.common.TestService#sendPendingIntentWithActivityForResult(
     *          PendingIntent, Bundle)
     */
    public void sendPendingIntentWithActivityForResult(PendingIntent pendingIntent,
            Bundle sendOptions) throws RemoteException {
        mTestService.sendPendingIntentWithActivityForResult(pendingIntent, sendOptions);
    }

    @Override
    public void startActivityIntent(Intent intent) throws RemoteException {
        mTestService.startActivityIntent(intent);
    }

    @Override
    public IBinder asBinder() {
        throw new UnsupportedOperationException();
    }
}
