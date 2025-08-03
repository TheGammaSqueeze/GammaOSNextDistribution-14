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

package android.localemanager.cts.util;

import android.content.Intent;
import android.os.LocaleList;
import android.os.Parcelable;

/**
 * Static utility methods used across the test apps.
 */
public final class LocaleUtils {

    private LocaleUtils() {}

    /**
     * Constructs a new intent with given action. Retrieves information from the passed intent
     * and puts it in the extras of the new returned intent.
     *
     * <p> The passed intent contains package name and the locales. The intent action is expected to
     * be either of the following types.
     * <ul>
     * <li> {@link LocaleConstants#TEST_APP_BROADCAST_INFO_PROVIDER_ACTION}
     * <li> {@link LocaleConstants#INSTALLER_APP_BROADCAST_INFO_PROVIDER_ACTION}
     * </ul>
     *
     */
    public static Intent constructResultIntent(String action, Intent intent) {
        return new Intent(action)
                .putExtra(Intent.EXTRA_PACKAGE_NAME,
                        intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
                .putExtra(Intent.EXTRA_LOCALE_LIST,
                            (Parcelable) intent.getParcelableExtra(Intent.EXTRA_LOCALE_LIST));
    }

    /**
     * Constructs a new intent with given action. Also puts the given package name and the locales
     * in the extras of the intent.
     *
     * <p> The action is expected to be of the type
     * {@link LocaleConstants#TEST_APP_CREATION_INFO_PROVIDER_ACTION}
     */
    public static Intent constructResultIntent(String action, String packageName,
            LocaleList locales) {
        return new Intent(action)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_LOCALE_LIST, locales);
    }
}
