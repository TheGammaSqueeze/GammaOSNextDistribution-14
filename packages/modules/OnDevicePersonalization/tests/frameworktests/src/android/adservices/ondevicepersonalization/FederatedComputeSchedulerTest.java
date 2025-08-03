/*
 * Copyright 2022 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.federatedcompute.common.TrainingOptions;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

/**
 * Unit Tests of RemoteData API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FederatedComputeSchedulerTest {
    FederatedComputeScheduler mFederatedComputeScheduler = new FederatedComputeScheduler(
            IFederatedComputeService.Stub.asInterface(
                    new FederatedComputeService()));

    private boolean mCancelCalled = false;
    private boolean mScheduleCalled = false;

    @Test
    public void testScheduleSuccess() {
        TrainingInterval interval =
                new TrainingInterval.Builder()
                        .setMinimumInterval(Duration.ofHours(10))
                        .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_ONE_TIME)
                        .build();
        FederatedComputeScheduler.Params params = new FederatedComputeScheduler.Params(interval);
        FederatedComputeInput input =
                new FederatedComputeInput.Builder().setPopulationName("population").build();
        mFederatedComputeScheduler.schedule(params, input);
        assertTrue(mScheduleCalled);
    }

    @Test
    public void testScheduleNull() {
        FederatedComputeScheduler fcs = new FederatedComputeScheduler(null);
        TrainingInterval interval =
                new TrainingInterval.Builder()
                        .setMinimumInterval(Duration.ofHours(10))
                        .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_ONE_TIME)
                        .build();
        FederatedComputeScheduler.Params params = new FederatedComputeScheduler.Params(interval);
        FederatedComputeInput input =
                new FederatedComputeInput.Builder().setPopulationName("population").build();
        assertThrows(IllegalStateException.class, () -> fcs.schedule(params, input));
    }

    @Test
    public void testScheduleErr() {
        TrainingInterval interval =
                new TrainingInterval.Builder()
                        .setMinimumInterval(Duration.ofHours(10))
                        .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_ONE_TIME)
                        .build();
        FederatedComputeScheduler.Params params = new FederatedComputeScheduler.Params(interval);
        FederatedComputeInput input =
                new FederatedComputeInput.Builder().setPopulationName("err").build();
        assertThrows(
                IllegalStateException.class,
                () -> mFederatedComputeScheduler.schedule(params, input));
    }

    @Test
    public void testCancelSuccess() {
        FederatedComputeInput input =
                new FederatedComputeInput.Builder().setPopulationName("population").build();
        mFederatedComputeScheduler.cancel(input);
        assertTrue(mCancelCalled);
    }

    @Test
    public void testCancelNull() {
        FederatedComputeInput input =
                new FederatedComputeInput.Builder().setPopulationName("population").build();
        FederatedComputeScheduler fcs = new FederatedComputeScheduler(null);
        assertThrows(IllegalStateException.class, () -> fcs.cancel(input));
    }

    @Test
    public void testCancelErr() {
        FederatedComputeInput input =
                new FederatedComputeInput.Builder().setPopulationName("err").build();
        assertThrows(IllegalStateException.class, () -> mFederatedComputeScheduler.cancel(input));
    }

    class FederatedComputeService extends IFederatedComputeService.Stub {
        @Override
        public void schedule(
                TrainingOptions trainingOptions,
                IFederatedComputeCallback iFederatedComputeCallback)
                throws RemoteException {
            mScheduleCalled = true;
            if (trainingOptions.getPopulationName().equals("err")) {
                iFederatedComputeCallback.onFailure(1);
            }
            iFederatedComputeCallback.onSuccess();
        }

        @Override
        public void cancel(String s, IFederatedComputeCallback iFederatedComputeCallback)
                throws RemoteException {
            mCancelCalled = true;
            if (s.equals("err")) {
                iFederatedComputeCallback.onFailure(1);
            }
            iFederatedComputeCallback.onSuccess();
        }
    }
}
