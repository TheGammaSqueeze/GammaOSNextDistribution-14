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

package android.car.app;


import android.annotation.NonNull;

/**
 * This class provides the required configuration to create a {@link RemoteCarRootTaskView}.
 *
 * @hide
 */
public final class RemoteCarDefaultRootTaskViewConfig {
    private static final String TAG = RemoteCarDefaultRootTaskViewConfig.class.getSimpleName();

    private final int mDisplayId;
    private final boolean mEmbedHomeTask;
    private final boolean mEmbedRecentsTask;
    private final boolean mEmbedAssistantTask;

    private RemoteCarDefaultRootTaskViewConfig(int displayId, boolean embedHomeTask,
            boolean embedRecentsTask, boolean embedAssistantTask) {
        mDisplayId = displayId;
        mEmbedHomeTask = embedHomeTask;
        mEmbedRecentsTask = embedRecentsTask;
        mEmbedAssistantTask = embedAssistantTask;
    }

    /** See {@link Builder#setDisplayId(int)}. */
    public int getDisplayId() {
        return mDisplayId;
    }

    /** See {@link Builder#embedHomeTask(boolean)}. */
    public boolean embedsHomeTask() {
        return mEmbedHomeTask;
    }

    /** See {@link Builder#embedRecentsTask(boolean)}. */
    public boolean embedsRecentsTask() {
        return mEmbedRecentsTask;
    }

    /** See {@link Builder#embedAssistantTask(boolean)}. */
    public boolean embedsAssistantTask() {
        return mEmbedAssistantTask;
    }

    @Override
    public String toString() {
        return TAG + " {"
                + " displayId=" + mDisplayId
                + " embedHomeTask=" + mEmbedHomeTask
                + " embedRecentsTask=" + mEmbedRecentsTask
                + " embedAssistantTask=" + mEmbedAssistantTask
                + '}';
    }

    /**
     * A builder class for {@link RemoteCarDefaultRootTaskViewConfig}.
     *
     * @hide
     */
    public static final class Builder {
        private int mDisplayId;
        private boolean mEmbedHomeTask;
        private boolean mEmbedRecentsTask;
        private boolean mEmbedAssistantTask;

        public Builder() {
        }

        /** Sets the display Id of the display which the root task will be created for. */
        @NonNull
        public Builder setDisplayId(int displayId) {
            mDisplayId = displayId;
            return this;
        }

        /** Creates the {@link RemoteCarDefaultRootTaskViewConfig} object. */
        @NonNull
        public RemoteCarDefaultRootTaskViewConfig build() {
            return new RemoteCarDefaultRootTaskViewConfig(mDisplayId, mEmbedHomeTask,
                    mEmbedRecentsTask, mEmbedAssistantTask);
        }

        /**
         * Sets the flag indicating whether the tasks with {@code ACTIVITY_TYPE_HOME} should be
         * embedded in the root task.
         */
        @NonNull
        public Builder embedHomeTask(boolean embedHomeTask) {
            mEmbedHomeTask = embedHomeTask;
            return this;
        }

        /**
         * Sets the flag indicating whether the tasks with {@code ACTIVITY_TYPE_RECENTS} should be
         * embedded in the root task.
         */
        @NonNull
        public Builder embedRecentsTask(boolean embedRecentsTask) {
            mEmbedRecentsTask = embedRecentsTask;
            return this;
        }

        /**
         * Sets the flag indicating whether the tasks with {@code ACTIVITY_TYPE_ASSISTANT}
         * should be embedded in the root task.
         */
        @NonNull
        public Builder embedAssistantTask(boolean embedAssistantTask) {
            mEmbedAssistantTask = embedAssistantTask;
            return this;
        }
    }
}
