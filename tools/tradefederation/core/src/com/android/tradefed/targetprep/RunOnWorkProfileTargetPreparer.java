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

package com.android.tradefed.targetprep;

import static com.android.tradefed.device.UserInfo.UserType.MANAGED_PROFILE;

import com.android.tradefed.config.OptionClass;

/**
 * An {@link ITargetPreparer} that creates a work profile in setup, and marks that tests should be
 * run in that user.
 *
 * <p>In teardown, the work profile is removed.
 *
 * <p>If a work profile already exists, it will be used rather than creating a new one, and it will
 * not be removed in teardown.
 *
 * <p>If the device does not have the managed_users feature, or does not have capacity to create a
 * new user when one is required, then the instrumentation argument skip-tests-reason will be set,
 * and the user will not be changed. Tests running on the device can read this argument to respond
 * to this state.
 */
@OptionClass(alias = "run-on-work-profile")
public class RunOnWorkProfileTargetPreparer extends ProfileTargetPreparer {

    public RunOnWorkProfileTargetPreparer() {
        super(MANAGED_PROFILE, "android.os.usertype.profile.MANAGED");
    }
}
