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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.MainThread;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallExistingPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.VerifyAdminPackageTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller that manages the provisioning process. It controls the order of provisioning tasks,
 * reacts to errors and user cancellation.
 */
public abstract class AbstractProvisioningController implements AbstractProvisioningTask.Callback {

    @VisibleForTesting
    static final int MSG_RUN_TASK = 1;

    protected final Context mContext;
    protected final ProvisioningParams mParams;
    protected int mUserId;

    private final ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final ProvisioningControllerCallback mCallback;
    private Handler mWorkerHandler;

    // Provisioning hasn't started yet
    private static final int STATUS_NOT_STARTED = 0;
    // Provisioning tasks are being run
    private static final int STATUS_RUNNING = 1;
    // Provisioning tasks have completed
    private static final int STATUS_TASKS_COMPLETED = 2;
    // An error occurred during provisioning
    private static final int STATUS_ERROR = 3;
    // Provisioning is being cancelled
    private static final int STATUS_CANCELLING = 4;
    // Cleanup has completed. This happens after STATUS_ERROR or STATUS_CANCELLING
    private static final int STATUS_CLEANED_UP = 5;

    private int mStatus = STATUS_NOT_STARTED;
    private List<AbstractProvisioningTask> mTasks = new ArrayList<>();

    protected int mCurrentTaskIndex;

    public AbstractProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        mContext = checkNotNull(context);
        mParams = checkNotNull(params);
        mUserId = userId;
        mCallback = checkNotNull(callback);
        mProvisioningAnalyticsTracker = new ProvisioningAnalyticsTracker(
                MetricsWriterFactory.getMetricsWriter(mContext, new SettingsFacade()),
                new ManagedProvisioningSharedPreferences(context));
    }

    @MainThread
    protected synchronized void addTasks(AbstractProvisioningTask... tasks) {
        for (AbstractProvisioningTask task : tasks) {
            mTasks.add(task);
        }
    }

    protected abstract void setUpTasks();
    protected abstract int getErrorTitle();
    protected abstract String getErrorMsgId(AbstractProvisioningTask task, int errorCode);
    protected abstract boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode);

    /**
     * Start the provisioning process. The tasks loaded in {@link #setUpTasks()} ()} will be
     * processed one by one and the respective callbacks will be given to the UI.
     */
    @MainThread
    public synchronized void start(Looper looper) {
        start(new ProvisioningTaskHandler(looper));
    }

    @VisibleForTesting
    void start(Handler handler) {
        if (mStatus != STATUS_NOT_STARTED) {
            return;
        }
        mWorkerHandler = checkNotNull(handler);

        mStatus = STATUS_RUNNING;
        runTask(0);
    }

    /**
     * Cancel the provisioning progress. When the cancellation is complete, the
     * {@link ProvisioningControllerCallback#cleanUpCompleted()} callback will be given.
     */
    @MainThread
    public synchronized void cancel() {
        ProvisionLogger.logd("Cancel called, current status is " + mStatus);
        mStatus = STATUS_CANCELLING;
        cleanup(STATUS_CLEANED_UP);
    }

    private void runTask(int index) {
        if (mTasks.isEmpty()) {
            tasksCompleted();
            return;
        }
        AbstractProvisioningTask nextTask = mTasks.get(index);
        Message msg = mWorkerHandler.obtainMessage(MSG_RUN_TASK, mUserId, 0 /* arg2 not used */,
                nextTask);
        mWorkerHandler.sendMessage(msg);
    }

    private void tasksCompleted() {
        mStatus = STATUS_TASKS_COMPLETED;
        mCurrentTaskIndex = -1;
        mCallback.provisioningTasksCompleted();
    }

    @Override
    // Note that this callback might come on the main thread
    public synchronized void onSuccess(AbstractProvisioningTask task) {
        if (mStatus != STATUS_RUNNING) {
            return;
        }

        mCurrentTaskIndex++;
        if (mCurrentTaskIndex == mTasks.size()) {
            tasksCompleted();
        } else {
            runTask(mCurrentTaskIndex);
        }
    }

    @Override
    // Note that this callback might come on the main thread
    public synchronized void onError(AbstractProvisioningTask task, int errorCode,
            String errorMessage) {
        mStatus = STATUS_ERROR;
        cleanup(STATUS_ERROR);
        mProvisioningAnalyticsTracker.logProvisioningError(mContext, task, errorCode);
        if (errorMessage == null) {
            mCallback.error(getErrorTitle(), getErrorMsgId(task, errorCode),
                    getRequireFactoryReset(task, errorCode));
        } else {
            mCallback.error(
                    getErrorTitle(), errorMessage, getRequireFactoryReset(task, errorCode));
        }
    }

    private void cleanup(final int newStatus) {
        mWorkerHandler.post(() -> {
                mStatus = newStatus;
                mCallback.cleanUpCompleted();
            });
    }

    protected final void addDownloadAndInstallDeviceOwnerPackageTasks() {
        if (mParams.deviceAdminDownloadInfo == null) {
            return;
        }

        DownloadPackageTask downloadTask = new DownloadPackageTask(
                mContext, mParams, mParams.deviceAdminDownloadInfo, this);
        addTasks(downloadTask,
                new VerifyAdminPackageTask(
                        downloadTask, mContext, mParams, mParams.deviceAdminDownloadInfo, this),
                new InstallPackageTask(downloadTask, mContext, mParams, this,
                        mParams.inferDeviceAdminPackageName()));

        // TODO(b/170333009): add unit test for headless system user mode
        if (UserManager.isHeadlessSystemUserMode() && mUserId != UserHandle.USER_SYSTEM) {
            ProvisionLogger.logd("Adding InstallExistingPackageTask for system user on "
                      + "headless system user mode");
            addTasks(new InstallExistingPackageTask(mParams.inferDeviceAdminPackageName(), mContext,
                    mParams, /* callback= */ this, UserHandle.USER_SYSTEM));
        }
    }

    /**
     * Handler that runs the provisioning tasks.
     *
     * <p>We're using a {@link HandlerThread} for all the provisioning tasks in order to not
     * block the UI thread.</p>
     */
    protected static class ProvisioningTaskHandler extends Handler {
        public ProvisioningTaskHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN_TASK) {
                AbstractProvisioningTask task = (AbstractProvisioningTask) msg.obj;
                int userId = msg.arg1;
                ProvisionLogger.logd("Running task: " + task.getClass().getSimpleName()
                        + " for user " + userId);
                task.run(userId);
            } else {
                ProvisionLogger.loge("Unknown message: " + msg.what);
            }
        }
    }
}
