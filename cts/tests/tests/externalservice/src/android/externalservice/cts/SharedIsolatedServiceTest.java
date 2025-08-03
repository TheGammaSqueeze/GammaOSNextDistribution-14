/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.externalservice.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.externalservice.common.RunningServiceInfo;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.test.AndroidTestCase;
import android.util.Log;

import static org.junit.Assert.assertThrows;

public class SharedIsolatedServiceTest extends AndroidTestCase {
    private static final String TAG = "SharedIsolatedServiceTest";

    static final String sServicePackage = "android.externalservice.service";

    private ConditionVariable mCondition = new ConditionVariable(false);
    private Connection mConnection = new Connection(mCondition);

    public void tearDown() {
        if (mConnection.mService != null) {
            getContext().unbindService(mConnection);
        }
    }

    public void testBasicConnection() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExternalService"));
        mCondition.close();

        assertTrue(getContext().bindIsolatedService(
                intent,
                Context.BIND_AUTO_CREATE
                        | Context.BIND_EXTERNAL_SERVICE
                        | Context.BIND_SHARED_ISOLATED_PROCESS,
                "testInstance",
                getContext().getMainExecutor(),
                mConnection));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), mConnection.mName.getPackageName());
        assertNotSame(sServicePackage, mConnection.mName.getPackageName());
    }

    public void testSharedIsolatedProcessNotAllowed() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExternalServiceSharedProcessNotAllowed"));
        mCondition.close();

        assertThrows(SecurityException.class, () ->
                getContext().bindIsolatedService(
                intent,
                Context.BIND_AUTO_CREATE
                        | Context.BIND_EXTERNAL_SERVICE
                        | Context.BIND_SHARED_ISOLATED_PROCESS,
                "testInstance",
                getContext().getMainExecutor(),
                mConnection));
    }

    public void testMultipleServicesWithSingleInstance() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExternalService"));
        mCondition.close();
        assertTrue(getContext().bindIsolatedService(
                intent,
                Context.BIND_AUTO_CREATE
                        | Context.BIND_EXTERNAL_SERVICE
                        | Context.BIND_SHARED_ISOLATED_PROCESS,
                "testInstance",
                getContext().getMainExecutor(),
                mConnection));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), mConnection.mName.getPackageName());
        assertNotSame(sServicePackage, mConnection.mName.getPackageName());

        RunningServiceInfo serviceInfo1 = identifyService(new Messenger(mConnection.mService));

        Connection connection2 = new Connection(mCondition);
        intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExternalServiceWithZygote"));
        mCondition.close();

        assertTrue(getContext().bindIsolatedService(
                intent,
                Context.BIND_AUTO_CREATE
                        | Context.BIND_EXTERNAL_SERVICE
                        | Context.BIND_SHARED_ISOLATED_PROCESS,
                "testInstance",
                getContext().getMainExecutor(),
                connection2));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), connection2.mName.getPackageName());
        assertNotSame(sServicePackage, connection2.mName.getPackageName());

        RunningServiceInfo serviceInfo2 = identifyService(new Messenger(connection2.mService));

        assertNotNull(serviceInfo1);
        assertNotNull(serviceInfo2);
        assertEquals(serviceInfo1.pid, serviceInfo2.pid);
        assertEquals(serviceInfo1.uid, serviceInfo2.uid);
    }

    public void testMultipleServicesWithMultiInstance() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExternalService"));
        mCondition.close();
        assertTrue(getContext().bindIsolatedService(
                intent,
                Context.BIND_AUTO_CREATE
                        | Context.BIND_EXTERNAL_SERVICE
                        | Context.BIND_SHARED_ISOLATED_PROCESS,
                "testInstance",
                getContext().getMainExecutor(),
                mConnection));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), mConnection.mName.getPackageName());
        assertNotSame(sServicePackage, mConnection.mName.getPackageName());

        RunningServiceInfo serviceInfo1 = identifyService(new Messenger(mConnection.mService));

        Connection connection2 = new Connection(mCondition);
        intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExternalServiceWithZygote"));
        mCondition.close();

        assertTrue(getContext().bindIsolatedService(
                intent,
                Context.BIND_AUTO_CREATE
                        | Context.BIND_EXTERNAL_SERVICE
                        | Context.BIND_SHARED_ISOLATED_PROCESS,
                "testInstance2",
                getContext().getMainExecutor(),
                connection2));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), connection2.mName.getPackageName());
        assertNotSame(sServicePackage, connection2.mName.getPackageName());

        RunningServiceInfo serviceInfo2 = identifyService(new Messenger(connection2.mService));

        assertNotNull(serviceInfo1);
        assertNotNull(serviceInfo2);
        assertNotSame(serviceInfo1.pid, serviceInfo2.pid);
        assertNotSame(serviceInfo1.uid, serviceInfo2.uid);
    }

    /** Given a Messenger, this will message the service to retrieve its UID, PID, and package name.
     * On success, returns a RunningServiceInfo. On failure, returns null. */
    private RunningServiceInfo identifyService(Messenger service) {
        try {
            return RunningServiceInfo.identifyService(service, TAG, mCondition);
        } catch (RemoteException e) {
            fail("Unexpected remote exception: " + e);
            return null;
        }
    }
    private static class Connection implements ServiceConnection {
        IBinder mService = null;
        ComponentName mName = null;
        ConditionVariable mConditionVariable;

        Connection(ConditionVariable conditionVariable) {
            mConditionVariable = conditionVariable;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
            this.mService = service;
            this.mName = name;
            mConditionVariable.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }
    }
}
