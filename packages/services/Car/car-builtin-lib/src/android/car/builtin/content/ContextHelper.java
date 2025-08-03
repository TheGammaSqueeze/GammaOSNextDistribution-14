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

package android.car.builtin.content;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.Objects;

/**
 * Helper for {@link Context}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ContextHelper {
    private ContextHelper() {
        throw new UnsupportedOperationException();
    }

    /** Returns display id relevant for the context */
    public static int getDisplayId(@NonNull Context context) {
        return context.getDisplayId();
    }

    /** Returns associated display id relevant for the context */
    public static int getAssociatedDisplayId(@NonNull Context context) {
        return context.getAssociatedDisplayId();
    }

    /**
     * Same as {@code context.startActivityAsUser(intent, options, user)}.
     */
    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)
    public static void startActivityAsUser(@NonNull Context context, @NonNull Intent intent,
            @Nullable Bundle options, @NonNull UserHandle user) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(user, "user");

        context.startActivityAsUser(intent, options, user);
    }

}
