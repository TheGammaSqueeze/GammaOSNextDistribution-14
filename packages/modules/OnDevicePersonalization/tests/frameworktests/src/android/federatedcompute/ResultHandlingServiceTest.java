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

package android.federatedcompute;

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;
import static android.federatedcompute.common.TrainingInterval.SCHEDULING_MODE_ONE_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public final class ResultHandlingServiceTest {
    private static final String TASK_NAME = "task-name";
    private static final String TEST_POPULATION = "testPopulation";
    private static final int JOB_ID = 12345;
    private static final byte[] SELECTION_CRITERIA = new byte[] {10, 0, 1};
    private static final TrainingOptions TRAINING_OPTIONS =
            new TrainingOptions.Builder()
                    .setPopulationName(TEST_POPULATION)
                    .setTrainingInterval(
                            new TrainingInterval.Builder()
                                    .setSchedulingMode(SCHEDULING_MODE_ONE_TIME)
                                    .build())
                    .build();
    private static final ArrayList<ExampleConsumption> EXAMPLE_CONSUMPTIONS =
            createExampleConsumptionList();

    private boolean mSuccess = false;
    private boolean mHandleResultCalled = false;
    private int mErrorCode = 0;
    private final CountDownLatch mLatch = new CountDownLatch(1);

    private IResultHandlingService mBinder;
    private final TestResultHandlingService mTestResultHandlingService =
            new TestResultHandlingService();

    @Before
    public void doBeforeEachTest() {
        mTestResultHandlingService.onCreate();
        mBinder = IResultHandlingService.Stub.asInterface(mTestResultHandlingService.onBind(null));
    }

    @Test
    public void testHandleResult_success() throws Exception {
        Bundle input = new Bundle();
        input.putString(ClientConstants.EXTRA_TASK_ID, TASK_NAME);
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, TEST_POPULATION);
        input.putInt(ClientConstants.EXTRA_COMPUTATION_RESULT, STATUS_SUCCESS);
        input.putParcelableArrayList(
                ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, EXAMPLE_CONSUMPTIONS);

        mBinder.handleResult(input, new TestFederatedComputeCallback());

        mLatch.await();
        assertTrue(mHandleResultCalled);
        assertTrue(mSuccess);
    }

    @Test
    public void testHandleResult_failure() throws Exception {
        Bundle input = new Bundle();
        input.putString(ClientConstants.EXTRA_TASK_ID, TASK_NAME);
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, TEST_POPULATION);
        input.putInt(ClientConstants.EXTRA_COMPUTATION_RESULT, STATUS_SUCCESS);
        input.putParcelableArrayList(ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, null);

        mBinder.handleResult(input, new TestFederatedComputeCallback());

        mLatch.await();
        assertTrue(mHandleResultCalled);
        assertThat(mErrorCode).isEqualTo(STATUS_INTERNAL_ERROR);
    }

    class TestResultHandlingService extends ResultHandlingService {
        @Override
        public void handleResult(Bundle input, Consumer<Integer> callback) {
            mHandleResultCalled = true;
            ArrayList<ExampleConsumption> exampleConsumptionList =
                    input.getParcelableArrayList(
                            ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST,
                            ExampleConsumption.class);
            if (exampleConsumptionList == null || exampleConsumptionList.isEmpty()) {
                callback.accept(STATUS_INTERNAL_ERROR);
                return;
            }
            callback.accept(STATUS_SUCCESS);
        }
    }

    private static ArrayList<ExampleConsumption> createExampleConsumptionList() {
        ArrayList<ExampleConsumption> exampleList = new ArrayList<>();
        exampleList.add(
                new ExampleConsumption.Builder()
                        .setTaskId("taskName")
                        .setExampleCount(100)
                        .setSelectionCriteria(SELECTION_CRITERIA)
                        .build());
        return exampleList;
    }

    class TestFederatedComputeCallback extends IFederatedComputeCallback.Stub {
        @Override
        public void onSuccess() {
            mSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onFailure(int errorCode) {
            mErrorCode = errorCode;
            mLatch.countDown();
        }
    }
}
