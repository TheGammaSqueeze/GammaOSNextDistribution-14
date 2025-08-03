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

package com.android.role.controller.behavior;

import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;

import java.util.Collections;
import java.util.List;

/**
 * Class for behavior of the Document Manager role.
 */
public class DocumentManagerRoleBehavior implements RoleBehavior {
    private static final String TAG = "DocumentManagerRoleBehavior";

    @NonNull
    @Override
    public List<String> getDefaultHoldersAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        List<String> qualifyingPackageNames = role.getQualifyingPackagesAsUser(user, context);
        if (qualifyingPackageNames.size() == 1) {
            return qualifyingPackageNames;
        } else {
            Log.e(TAG, "There should be exactly one documenter; found "
                    + qualifyingPackageNames.size() + ": matches=" + qualifyingPackageNames);
            return Collections.emptyList();
        }
    }
}
