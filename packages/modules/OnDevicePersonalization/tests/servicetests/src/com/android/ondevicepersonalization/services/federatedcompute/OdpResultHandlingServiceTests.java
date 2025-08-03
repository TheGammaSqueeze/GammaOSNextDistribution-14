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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.federatedcompute.common.ClientConstants.RESULT_HANDLING_SERVICE_ACTION;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;
import static android.federatedcompute.common.ClientConstants.STATUS_TRAINING_FAILED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class OdpResultHandlingServiceTests {
    @Rule public final ServiceTestRule serviceRule = new ServiceTestRule();
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private static final String SERVICE_CLASS = "com.test.TestPersonalizationService";
    private boolean mCallbackOnSuccessCalled = false;
    private boolean mCallbackOnFailureCalled = false;

    private EventsDao mEventsDao;

    @Before
    public void setup() {
        mEventsDao = EventsDao.getInstanceForTest(mContext);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testHandleResult() throws Exception {
        Intent mIntent = new Intent();
        mIntent.setAction(RESULT_HANDLING_SERVICE_ACTION).setPackage(mContext.getPackageName());
        IBinder binder = serviceRule.bindService(mIntent);
        assertNotNull(binder);

        Bundle input = new Bundle();
        ContextData contextData = new ContextData(mContext.getPackageName(), SERVICE_CLASS);
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "population");
        input.putString(ClientConstants.EXTRA_TASK_ID, "task_name");
        input.putInt(ClientConstants.EXTRA_COMPUTATION_RESULT, STATUS_SUCCESS);
        ArrayList<ExampleConsumption> exampleConsumptions = new ArrayList<>();
        exampleConsumptions.add(
                new ExampleConsumption.Builder()
                        .setTaskId("task_name")
                        .setExampleCount(100)
                        .setSelectionCriteria(new byte[] {10, 0, 1})
                        .setResumptionToken(new byte[] {10, 0, 1})
                        .build());
        input.putParcelableArrayList(
                ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, exampleConsumptions);

        ((IResultHandlingService.Stub) binder).handleResult(input, new TestCallback());
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mCallbackOnSuccessCalled);
        assertFalse(mCallbackOnFailureCalled);

        EventState state1 =
                mEventsDao.getEventState(
                        OdpExampleStoreService.getTaskIdentifier("population", "task_name"),
                        new ComponentName(mContext.getPackageName(), SERVICE_CLASS));
        assertArrayEquals(new byte[] {10, 0, 1}, state1.getToken());
    }

    @Test
    public void testHandleResultTrainingFailed() throws Exception {
        Intent mIntent = new Intent();
        mIntent.setAction(RESULT_HANDLING_SERVICE_ACTION).setPackage(mContext.getPackageName());
        IBinder binder = serviceRule.bindService(mIntent);
        assertNotNull(binder);

        Bundle input = new Bundle();
        ContextData contextData = new ContextData(mContext.getPackageName(), SERVICE_CLASS);
        input.putByteArray(
                ClientConstants.EXTRA_CONTEXT_DATA, ContextData.toByteArray(contextData));
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, "population");
        input.putString(ClientConstants.EXTRA_TASK_ID, "task_name");
        input.putInt(ClientConstants.EXTRA_COMPUTATION_RESULT, STATUS_TRAINING_FAILED);
        ArrayList<ExampleConsumption> exampleConsumptions = new ArrayList<>();
        exampleConsumptions.add(
                new ExampleConsumption.Builder()
                        .setTaskId("task")
                        .setExampleCount(100)
                        .setSelectionCriteria(new byte[] {10, 0, 1})
                        .setResumptionToken(new byte[] {10, 0, 1})
                        .build());
        input.putParcelableArrayList(
                ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, exampleConsumptions);

        ((IResultHandlingService.Stub) binder).handleResult(input, new TestCallback());
        mLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mCallbackOnSuccessCalled);
        assertFalse(mCallbackOnFailureCalled);
    }

    public class TestCallback implements IFederatedComputeCallback {
        @Override
        public void onSuccess() throws RemoteException {
            mCallbackOnSuccessCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onFailure(int i) throws RemoteException {
            mCallbackOnFailureCalled = true;
            mLatch.countDown();
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }
}
