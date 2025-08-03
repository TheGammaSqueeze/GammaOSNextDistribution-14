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

package android.car.app;


import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Intent;

/**
 * This class provides the required configuration to create a
 * {@link ControlledRemoteCarTaskView}.
 * @hide
 */
@SystemApi
public final class ControlledRemoteCarTaskViewConfig {
    private static final String TAG = ControlledRemoteCarTaskViewConfig.class.getSimpleName();

    final Intent mActivityIntent;
    final boolean mShouldAutoRestartOnTaskRemoval;
    final boolean mShouldCaptureGestures;
    final boolean mShouldCaptureLongPress;

    private ControlledRemoteCarTaskViewConfig(
            Intent activityIntent,
            boolean shouldAutoRestartOnTaskRemoval,
            boolean shouldCaptureGestures,
            boolean shouldCaptureLongPress) {
        mActivityIntent = activityIntent;
        mShouldAutoRestartOnTaskRemoval = shouldAutoRestartOnTaskRemoval;
        mShouldCaptureGestures = shouldCaptureGestures;
        mShouldCaptureLongPress = shouldCaptureLongPress;
    }

    /** See {@link Builder#setActivityIntent(Intent)}. */
    @NonNull
    public Intent getActivityIntent() {
        return mActivityIntent;
    }

    /** See {@link Builder#setShouldAutoRestartOnTaskRemoval(boolean)}. */
    public boolean shouldAutoRestartOnCrash() {
        return mShouldAutoRestartOnTaskRemoval;
    }

    /** See {@link Builder#setShouldCaptureGestures(boolean)}. */
    public boolean shouldCaptureGestures() {
        return mShouldCaptureGestures;
    }

    /** See {@link Builder#setShouldCaptureLongPress(boolean)}. */
    public boolean shouldCaptureLongPress() {
        return mShouldCaptureLongPress;
    }

    @Override
    public String toString() {
        return TAG + " {"
                + "activityIntent=" + mActivityIntent
                + ", mShouldAutoRestartOnTaskRemoval=" + mShouldAutoRestartOnTaskRemoval
                + ", shouldCaptureGestures=" + mShouldCaptureGestures
                + ", shouldCaptureLongPress=" + mShouldCaptureLongPress
                + '}';
    }

    /**
     * A builder class for {@link ControlledRemoteCarTaskViewConfig}.
     * @hide
     */
    @SystemApi
    public static final class Builder {
        private Intent mActivityIntent;
        private boolean mShouldAutoRestartOnCrash;
        private boolean mShouldCaptureGestures;
        private boolean mShouldCaptureLongPress;

        public Builder() {
        }

        /**
         * Sets the intent of the activity that is meant to be started in this {@link
         * ControlledRemoteCarTaskView}.
         *
         * @param activityIntent the intent of the activity that is meant to be started in this
         *                       task view.
         */
        @NonNull
        public Builder setActivityIntent(@NonNull Intent activityIntent) {
            mActivityIntent = activityIntent;
            return this;
        }

        /**
         * Sets the auto restart functionality. If set, the {@link ControlledRemoteCarTaskView}
         * will restart the task by re-launching the intent set via {@link
         * #setActivityIntent(Intent)} when the task is removed.
         *
         * The task might get removed because of multiple reasons like:
         * <ul>
         *     <li>Due to memory pressure</li>
         *     <li>Due to the apk update</li>
         *     <li>etc.</li>
         * </ul>
         *
         * @param shouldAutoRestartOnTaskRemoval denotes if the auto restart functionality should be
         *                                 enabled or not.
         */
        @NonNull
        public Builder setShouldAutoRestartOnTaskRemoval(boolean shouldAutoRestartOnTaskRemoval) {
            mShouldAutoRestartOnCrash = shouldAutoRestartOnTaskRemoval;
            return this;
        }

        /**
         * Enables the swipe gesture capturing over {@link ControlledRemoteCarTaskView}. When
         * enabled, the swipe gestures won't be sent to the embedded app and will instead be
         * forwarded to the host activity.
         *
         * @param shouldCaptureGestures denotes if the swipe gesture capturing should be enabled or
         *                              not.
         */
        @NonNull
        public Builder setShouldCaptureGestures(boolean shouldCaptureGestures) {
            mShouldCaptureGestures = shouldCaptureGestures;
            return this;
        }

        /**
         * Enables the long press capturing over {@link ControlledRemoteCarTaskView}. When enabled,
         * the long press won't be sent to the embedded app and will instead be sent to the listener
         * specified via {@link
         * ControlledRemoteCarTaskView#setOnLongClickListener(View.OnLongClickListener)}.
         *
         * <p>If disabled, the listener supplied via {@link
         * ControlledRemoteCarTaskView#setOnLongClickListener(View.OnLongClickListener)} won't be
         * called.
         *
         * @param shouldCaptureLongPress denotes if the long press capturing should be enabled or
         *                               not.
         */
        @NonNull
        public Builder setShouldCaptureLongPress(boolean shouldCaptureLongPress) {
            mShouldCaptureLongPress = shouldCaptureLongPress;
            return this;
        }

        /** Creates the {@link ControlledRemoteCarTaskViewConfig} object. */
        @NonNull
        public ControlledRemoteCarTaskViewConfig build() {
            if (mActivityIntent == null) {
                throw new IllegalArgumentException("mActivityIntent can't be null");
            }
            return new ControlledRemoteCarTaskViewConfig(
                    mActivityIntent, mShouldAutoRestartOnCrash, mShouldCaptureGestures,
                    mShouldCaptureLongPress);
        }
    }
}
