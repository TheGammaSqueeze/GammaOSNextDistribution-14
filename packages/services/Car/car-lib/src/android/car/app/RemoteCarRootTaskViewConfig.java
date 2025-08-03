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
import android.content.ComponentName;

import java.util.List;

/**
 * This class provides the required configuration to create a {@link RemoteCarRootTaskView}.
 *
 * @hide
 */
public final class RemoteCarRootTaskViewConfig {
    private static final String TAG = RemoteCarRootTaskViewConfig.class.getSimpleName();

    private final int mDisplayId;
    private final List<ComponentName> mAllowListedActivities;

    private RemoteCarRootTaskViewConfig(int displayId,
            @NonNull List<ComponentName> allowListedActivities) {
        mDisplayId = displayId;
        mAllowListedActivities = allowListedActivities;
    }

    /** See {@link Builder#setDisplayId(int)}. */
    public int getDisplayId() {
        return mDisplayId;
    }

    /** See {@link Builder#setAllowListedActivities(List)}. */
    public List<ComponentName> getAllowListedActivities() {
        return mAllowListedActivities;
    }

    @Override
    public String toString() {
        return TAG + " {"
                + " displayId=" + mDisplayId
                + " allowListedActivities= " + mAllowListedActivities
                + '}';
    }

    /**
     * A builder class for {@link RemoteCarRootTaskViewConfig}.
     *
     * @hide
     */
    public static final class Builder {
        private int mDisplayId;

        private List<ComponentName> mAllowListedActivities;

        public Builder() {
        }

        /** Sets the display Id of the display which the root task will be created for. */
        @NonNull
        public Builder setDisplayId(int displayId) {
            mDisplayId = displayId;
            return this;
        }

        /**
         * Sets the initial list of all the allow listed activities which will be persisted on the
         * root task that is embedded inside the task view.
         */
        @NonNull
        public Builder setAllowListedActivities(
                @NonNull List<ComponentName> allowListedActivities) {
            mAllowListedActivities = allowListedActivities;
            return this;
        }

        /** Creates the {@link RemoteCarRootTaskViewConfig} object. */
        @NonNull
        public RemoteCarRootTaskViewConfig build() {
            return new RemoteCarRootTaskViewConfig(mDisplayId, mAllowListedActivities);
        }
    }
}
