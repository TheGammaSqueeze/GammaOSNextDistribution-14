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

package com.android.server.healthconnect;

import android.annotation.NonNull;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.UserHandle;

import com.android.server.healthconnect.utils.FilesUtil;

import java.io.File;
import java.util.Objects;

/** @hide */
public class HealthConnectUserContext extends ContextWrapper {
    private final UserHandle mUserHandle;

    public HealthConnectUserContext(@NonNull Context context, @NonNull UserHandle userHandle) {
        super(context);
        Objects.requireNonNull(context);
        Objects.requireNonNull(userHandle);

        mUserHandle = userHandle;
    }

    @NonNull
    public UserHandle getCurrentUserHandle() {
        return mUserHandle;
    }

    @Override
    public File getDatabasePath(String name) {
        File systemCeUserHcDir =
                FilesUtil.getDataSystemCeHCDirectoryForUser(mUserHandle.getIdentifier());
        systemCeUserHcDir.mkdirs();

        return new File(systemCeUserHcDir, name);
    }
}
