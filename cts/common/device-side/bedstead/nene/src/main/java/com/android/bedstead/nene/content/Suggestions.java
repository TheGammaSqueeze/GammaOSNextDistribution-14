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

package com.android.bedstead.nene.content;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_CONTENT_SUGGESTIONS;

import android.app.contentsuggestions.ContentSuggestionsManager;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.UndoableContext;

/** Helper methods related to content suggestions. */
public final class Suggestions {
    public static final Suggestions sInstance = new Suggestions();

    private static final ContentSuggestionsManager sContentSuggestionsManager =
            TestApis.context().instrumentedContext()
                    .getSystemService(ContentSuggestionsManager.class);

    private Suggestions() {

    }

    public UndoableContext setDefaultServiceEnabled(boolean value) {
        return setDefaultServiceEnabled(TestApis.users().instrumented(), value);
    }

    public UndoableContext setDefaultServiceEnabled(UserReference user, boolean value) {
        boolean currentValue = defaultServiceEnabled(user);
        if (currentValue == value) {
            // Nothing to do
            return UndoableContext.EMPTY;
        }

        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS)) {
            sContentSuggestionsManager.setDefaultServiceEnabled(user.id(), value);
        }

        return new UndoableContext(() -> {
            try (PermissionContext p =
                         TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS)) {
                sContentSuggestionsManager.setDefaultServiceEnabled(user.id(), currentValue);
            }
        });
    }

    public boolean defaultServiceEnabled() {
        return defaultServiceEnabled(TestApis.users().instrumented());
    }

    public boolean defaultServiceEnabled(UserReference user) {
        try {
            return ShellCommand.builder("cmd content_suggestions")
                    .addOperand("get default-service-enabled")
                    .addOperand(user.id())
                    .executeAndParseOutput(s -> s.contains("true"));
        } catch (AdbException e) {
            throw new NeneException("Error checking default-service-enabled", e);
        }
    }

    private static final int TEMPORARY_SERVICE_DURATION_MS = 3600000; // 1 hour

    public UndoableContext setTemporaryService(ComponentReference component) {
        return setTemporaryService(TestApis.users().instrumented(), component);
    }

    public UndoableContext setTemporaryService(UserReference user, ComponentReference component) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS)) {
            sContentSuggestionsManager.setTemporaryService(
                    user.id(), component.flattenToString(), TEMPORARY_SERVICE_DURATION_MS);
        }
        return new UndoableContext(
                () -> {
                    try (PermissionContext p =
                                 TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS)) {
                        TestApis.content().suggestions().clearTemporaryService(user);
                    }
                });
    }

    public void clearTemporaryService() {
        clearTemporaryService(TestApis.users().instrumented());
    }

    public void clearTemporaryService(UserReference user) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS)) {
            sContentSuggestionsManager.resetTemporaryService(user.id());
        }
    }
}
