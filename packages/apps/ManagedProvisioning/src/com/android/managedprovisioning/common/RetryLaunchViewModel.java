/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static com.android.managedprovisioning.common.RetryLaunchViewModel.LaunchActivityFailureEvent.REASON_EXCEEDED_MAXIMUM_NUMBER_ACTIVITY_LAUNCH_RETRIES;

import static java.util.Objects.requireNonNull;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

final class RetryLaunchViewModel extends AndroidViewModel {
    static final int VIEW_MODEL_EVENT_LAUNCH_ACTIVITY = 1;
    static final int VIEW_MODEL_EVENT_LAUNCH_FAILURE = 2;
    static final int VIEW_MODEL_EVENT_WAITING_FOR_RETRY = 3;

    private final MutableLiveData<ViewModelEvent> mObservableEvents = new MutableLiveData<>();
    private final Runnable mRunnable = RetryLaunchViewModel.this::tryStartActivity;
    private final Handler mHandler;
    private final CanLaunchActivityChecker mCanLaunchActivityChecker;
    private final Config mConfig;
    private final Intent mActivityIntent;

    private int mNumberOfStartUpdaterTries = 0;
    private boolean mIsWaitingForActivityResult;

    RetryLaunchViewModel(
            @NonNull Application application,
            Intent activityIntent,
            Handler handler,
            CanLaunchActivityChecker canLaunchActivityChecker,
            Config config) {
        super(application);
        mActivityIntent = requireNonNull(activityIntent);
        mHandler = requireNonNull(handler);
        mCanLaunchActivityChecker = requireNonNull(canLaunchActivityChecker);
        mConfig = requireNonNull(config);
    }

    MutableLiveData<ViewModelEvent> observeViewModelEvents() {
        return mObservableEvents;
    }

    /**
     * Tries to start the role holder updater.
     * <ol>
     * <li>If the activity can be launched, it is launched.</li>
     * <li>If the activity cannot be currently launched (e.g. if the app it belongs to is being
     * updated), then we schedule a retry, up to {@link Config#getLaunchActivityMaxRetries()}
     * times total.</li>
     * <li>If we exceed the max retry thresholds, we post a failure event.</li>
     * </ol>
     *
     * @see LaunchActivityEvent
     * @see LaunchActivityFailureEvent
     */
    void tryStartActivity() {
        boolean canLaunchActivity = mCanLaunchActivityChecker.canLaunchActivity(
                getApplication().getApplicationContext(), mActivityIntent);
        if (canLaunchActivity) {
            launchActivity(mActivityIntent);
        } else {
            ProvisionLogger.loge("Cannot launch activity " + mActivityIntent.getAction());
            tryRescheduleActivityLaunch();
        }
    }

    void stopLaunchRetries() {
        mHandler.removeCallbacks(mRunnable);
    }

    boolean isWaitingForActivityResult() {
        return mIsWaitingForActivityResult;
    }

    void markWaitingForActivityResult() {
        mIsWaitingForActivityResult = true;
    }

    /**
     * Tries to reschedule the role holder updater launch.
     */
    private void tryRescheduleActivityLaunch() {
        if (canRetryLaunchActivity(mNumberOfStartUpdaterTries)) {
            scheduleRetryLaunchActivity();
            mObservableEvents.postValue(new LaunchActivityWaitingForRetryEvent());
        } else {
            ProvisionLogger.loge("Exceeded maximum number of activity launch retries.");
            mObservableEvents.postValue(
                    new LaunchActivityFailureEvent(
                            REASON_EXCEEDED_MAXIMUM_NUMBER_ACTIVITY_LAUNCH_RETRIES));
        }
    }

    private boolean canRetryLaunchActivity(int numTries) {
        return numTries < mConfig.getLaunchActivityMaxRetries();
    }

    private void launchActivity(Intent intent) {
        mObservableEvents.postValue(new LaunchActivityEvent(intent));
    }

    private void scheduleRetryLaunchActivity() {
        mHandler.postDelayed(mRunnable, mConfig.getLaunchActivityRetryMillis());
        mNumberOfStartUpdaterTries++;
    }

    static class LaunchActivityEvent extends ViewModelEvent {
        private final Intent mIntent;

        LaunchActivityEvent(Intent intent) {
            super(VIEW_MODEL_EVENT_LAUNCH_ACTIVITY);
            mIntent = requireNonNull(intent);
        }

        Intent getIntent() {
            return mIntent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LaunchActivityEvent)) return false;
            LaunchActivityEvent that = (LaunchActivityEvent) o;
            return Objects.equals(mIntent.getAction(), that.mIntent.getAction())
                    && Objects.equals(mIntent.getExtras(), that.mIntent.getExtras());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIntent.getAction(), mIntent.getExtras());
        }

        @Override
        public String toString() {
            return "LaunchActivityEvent{"
                    + "mIntent=" + mIntent + '}';
        }
    }

    static class LaunchActivityFailureEvent extends ViewModelEvent {
        static final int REASON_EXCEEDED_MAXIMUM_NUMBER_ACTIVITY_LAUNCH_RETRIES = 1;

        private final int mReason;

        LaunchActivityFailureEvent(int reason) {
            super(VIEW_MODEL_EVENT_LAUNCH_FAILURE);
            mReason = reason;
        }

        int getReason() {
            return mReason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LaunchActivityFailureEvent)) return false;
            LaunchActivityFailureEvent that = (LaunchActivityFailureEvent) o;
            return mReason == that.mReason;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mReason);
        }

        @Override
        public String toString() {
            return "LaunchActivityFailureEvent{"
                    + "mReason=" + mReason + '}';
        }
    }

    static class LaunchActivityWaitingForRetryEvent extends ViewModelEvent {
        LaunchActivityWaitingForRetryEvent() {
            super(VIEW_MODEL_EVENT_WAITING_FOR_RETRY);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LaunchActivityWaitingForRetryEvent)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(VIEW_MODEL_EVENT_WAITING_FOR_RETRY);
        }

        @Override
        public String toString() {
            return "LaunchActivityWaitingForRetryEvent{}";
        }
    }

    static class RetryLaunchViewModelFactory implements ViewModelProvider.Factory {
        private final Application mApplication;
        private final Intent mActivityIntent;
        private final Config mConfig;
        private final Utils mUtils;

        RetryLaunchViewModelFactory(
                Application application,
                Intent activityIntent,
                Config config,
                Utils utils) {
            mApplication = requireNonNull(application);
            mActivityIntent = requireNonNull(activityIntent);
            mConfig = requireNonNull(config);
            mUtils = requireNonNull(utils);
        }

        @Override
        public <T extends ViewModel> T create(Class<T> aClass) {
            return (T) new RetryLaunchViewModel(
                    mApplication,
                    mActivityIntent,
                    new Handler(Looper.getMainLooper()),
                    new DefaultCanLaunchActivityChecker(mUtils),
                    mConfig);
        }
    }

    interface CanLaunchActivityChecker {
        boolean canLaunchActivity(Context context, Intent intent);
    }

    interface Config {
        long getLaunchActivityRetryMillis();

        int getLaunchActivityMaxRetries();
    }

    static class DefaultCanLaunchActivityChecker implements CanLaunchActivityChecker {

        private final Utils mUtils;

        DefaultCanLaunchActivityChecker(Utils utils) {
            mUtils = requireNonNull(utils);
        }

        @Override
        public boolean canLaunchActivity(Context context, Intent intent) {
            return mUtils.canResolveIntentAsUser(context, intent, UserHandle.USER_SYSTEM);
        }

    }
}
