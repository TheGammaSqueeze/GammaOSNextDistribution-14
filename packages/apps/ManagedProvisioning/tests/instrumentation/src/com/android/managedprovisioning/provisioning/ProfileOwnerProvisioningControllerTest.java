/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.CreateAndProvisionManagedProfileTask;

import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link ProfileOwnerProvisioningController}.
 */

public class ProfileOwnerProvisioningControllerTest extends ProvisioningControllerBaseTest {

    private static final int TEST_PARENT_USER_ID = 1;
    private static final int TEST_PROFILE_USER_ID = 2;
    private static final ComponentName TEST_ADMIN = new ComponentName("com.test.admin",
            "com.test.admin.AdminReceiver");
    private static final ProvisioningParams PROVISIONING_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminComponentName(TEST_ADMIN)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .build();

    @Mock private ProvisioningControllerCallback mCallback;
    private static final Context mContext = InstrumentationRegistry.getTargetContext();

    private static final String TEST_ERROR_MESSAGE = "test error message";

    private static final AbstractProvisioningTask TASK =
            new AddWifiNetworkTask(mContext,
                    PROVISIONING_PARAMS,
                    createTaskCallback());

    @SmallTest
    public void testRunAllTasks() throws Exception {
        // GIVEN device profile owner provisioning was invoked
        createController();

        // WHEN starting the test run
        mController.start(mHandler);

        // THEN the create and provision managed profile task is run
        verifyTaskRun(CreateAndProvisionManagedProfileTask.class);

        // WHEN the task completes successfully
        CreateAndProvisionManagedProfileTask createAndProvisionManagedProfileTask = mock(
                CreateAndProvisionManagedProfileTask.class);
        when(createAndProvisionManagedProfileTask.getProfileUserId()).thenReturn(
                TEST_PROFILE_USER_ID);
        mController.onSuccess(createAndProvisionManagedProfileTask);

        // THEN the provisioning complete callback should have happened
        verify(mCallback).provisioningTasksCompleted();
    }

    @SmallTest
    public void testCancel() throws Exception {
        // GIVEN device profile owner provisioning was invoked
        createController();

        // WHEN starting the test run
        mController.start(mHandler);

        // THEN the create and provision managed profile task is run
        AbstractProvisioningTask task = verifyTaskRun(CreateAndProvisionManagedProfileTask.class);

        // latch used to wait for onCancelled callback
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                latch.countDown();
                return null;
            }
        }).when(mCallback).cleanUpCompleted();

        // WHEN the user cancels the provisioning progress
        mController.cancel();

        // THEN the activity is informed that progress has been cancelled
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // WHEN the create and provision managed profile task eventually finishes
        mController.onSuccess(task);

        // THEN no more tasks should be run
        assertNull(mHandler.getLastTask());
    }

    @SmallTest
    public void testError() throws Exception {
        // GIVEN device profile owner provisioning was invoked
        createController();

        // WHEN starting the test run
        mController.start(mHandler);

        // THEN the create and provision managed profile task is run
        AbstractProvisioningTask task = verifyTaskRun(CreateAndProvisionManagedProfileTask.class);

        // WHEN the task encountered an error
        mController.onError(task, 0, /* errorMessage= */ null);

        // THEN the activity should be informed about the error
        verify(mCallback).error(R.string.cant_set_up_profile,
                mContext.getString(R.string.managed_provisioning_error_text), false);
    }

    @SmallTest
    public void testErrorWithStringMessage() {
        createController();
        mController.start(mHandler);

        mController.onError(TASK, /* errorCode= */ 0, TEST_ERROR_MESSAGE);

        verify(mCallback).error(anyInt(), eq(TEST_ERROR_MESSAGE), eq(false));
    }

    private void createController() {
        mController = ProfileOwnerProvisioningController.createInstance(
                mContext,
                PROVISIONING_PARAMS,
                TEST_PARENT_USER_ID,
                mCallback);
    }

    private static AbstractProvisioningTask.Callback createTaskCallback() {
        return new AbstractProvisioningTask.Callback() {
            @Override
            public void onSuccess(AbstractProvisioningTask task) {

            }

            @Override
            public void onError(AbstractProvisioningTask task, int errorCode,
                    String errorMessage) {

            }
        };
    }
}
