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

package android.view.inputmethod.cts.util;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;

import java.util.ArrayList;

/**
 * Utility methods for {@link Settings.Secure}.
 */
public final class SecureSettingsUtils {

    private static final char INPUT_METHOD_SEPARATOR = ':';
    private static final char INPUT_METHOD_SUBTYPE_SEPARATOR = ';';

    /**
     * Not intended to be instantiated.
     */
    private SecureSettingsUtils() {
    }

    /**
     * Update {@link Settings.Secure#ENABLED_INPUT_METHODS} so that the given IME can be enabled
     * with the specified array of {@link InputMethodSubtype}.
     *
     * @param context {@link Context} to be used to access
     *                {@link Settings.Secure#ENABLED_INPUT_METHODS}.
     * @param imeId IME ID to be updated.
     * @param subtypes An array of {@link InputMethodSubtype}. Can be empty.
     */
    public static void updateEnabledInputMethods(@NonNull Context context, @NonNull String imeId,
            @NonNull InputMethodSubtype[] subtypes) {
        final ContentResolver resolver = context.getContentResolver();
        final String enabledInputMethods = SystemUtil.runWithShellPermissionIdentity(
                () -> Settings.Secure.getString(resolver,
                        Settings.Secure.ENABLED_INPUT_METHODS));
        final var imeSplitter = new TextUtils.SimpleStringSplitter(INPUT_METHOD_SEPARATOR);
        imeSplitter.setString(enabledInputMethods);
        final StringBuilder sb = new StringBuilder();

        while (imeSplitter.hasNext()) {
            final String element = imeSplitter.next();
            final var subtypeSplitter =
                    new TextUtils.SimpleStringSplitter(INPUT_METHOD_SUBTYPE_SEPARATOR);
            subtypeSplitter.setString(element);
            final var id = subtypeSplitter.next();
            final ArrayList<String> subtypeHashCodes = new ArrayList<>();
            while (subtypeSplitter.hasNext()) {
                subtypeHashCodes.add(subtypeSplitter.next());
            }
            if (TextUtils.equals(imeId, id)) {
                subtypeHashCodes.clear();
                for (var subtype : subtypes) {
                    subtypeHashCodes.add(Integer.toString(subtype.hashCode()));
                }
            }
            if (sb.length() > 0) {
                sb.append(INPUT_METHOD_SEPARATOR);
            }
            sb.append(imeId);
            for (var hashCode : subtypeHashCodes) {
                sb.append(INPUT_METHOD_SUBTYPE_SEPARATOR).append(hashCode);
            }
        }
        final String newSecureSettings = sb.toString();
        SystemUtil.runWithShellPermissionIdentity(() ->
                Settings.Secure.putString(resolver,
                        Settings.Secure.ENABLED_INPUT_METHODS, newSecureSettings));
    }

    /**
     * Returns {@link ContentResolver} that can be used to get/put {@link Settings.Secure} even for
     * the given user.
     *
     * <p>Requires {@link Manifest.permission#INTERACT_ACROSS_USERS_FULL} permission.</p>
     *
     * @param context Any {@link Context}.
     * @param userId User ID to be queried about.
     * @return {@link ContentResolver} that is associated with {@code userId}.
     */
    @NonNull
    private static ContentResolver getContentResolverForUser(@NonNull Context context, int userId) {
        try {
            return context.createPackageContextAsUser("android", 0, UserHandle.of(userId))
                    .getContentResolver();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calls {@link Settings.Secure#putInt(ContentResolver, String, int)} for the given user.
     *
     * @param context Any {@link Context}.
     * @param name A key of {@link Settings.Secure}.
     * @param value The value of {@link Settings.Secure}.
     * @param userId The target user ID.
     */
    public static void putInt(@NonNull Context context, @NonNull String name, int value,
            int userId) {
        if (context.getUserId() == userId && UserHandle.myUserId() == userId) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                Settings.Secure.putInt(context.getContentResolver(), name, value);
            }, Manifest.permission.WRITE_SECURE_SETTINGS);
            return;
        }

        SystemUtil.runWithShellPermissionIdentity(() -> Settings.Secure.putInt(
                        getContentResolverForUser(context, userId), name, value),
                Manifest.permission.WRITE_SECURE_SETTINGS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    /**
     * Calls {@link Settings.Secure#getString(ContentResolver, String)} for the given user.
     *
     * @param context Any {@link Context}.
     * @param name A key of {@link Settings.Secure}.
     * @param userId The target user ID.
     * @return {@link String} value of {@code key}.
     */
    @Nullable
    public static String getString(@NonNull Context context, @NonNull String name, int userId) {
        if (context.getUserId() == userId && UserHandle.myUserId() == userId) {
            return Settings.Secure.getString(context.getContentResolver(), name);
        }
        return SystemUtil.runWithShellPermissionIdentity(
                () -> Settings.Secure.getString(getContentResolverForUser(context, userId), name),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }
}
