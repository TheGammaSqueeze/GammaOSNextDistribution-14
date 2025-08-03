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

import android.content.ComponentName;
import android.os.LocaleList;

/**
 * Common constants used across {@link android.localemanager.cts.LocaleManagerTests}
 * and the test apps.
 */
public final class LocaleConstants {

    private LocaleConstants() {}

    public static final LocaleList DEFAULT_SYSTEM_LOCALES =
            LocaleList.forLanguageTags("en-US,fr-FR");

    public static final LocaleList DEFAULT_APP_LOCALES = LocaleList.forLanguageTags("hi,fr-FR");

    public static final String CALLING_PACKAGE = "android.localemanager.cts";

    public static final String TEST_APP_PACKAGE = "android.localemanager.cts.app";

    public static final String INSTALLER_PACKAGE = "android.localemanager.cts.installer";

    public static final String IME_APP_PACKAGE = "android.localemanager.cts.ime";

    public static final String NON_EXISTENT_PACKAGE = "android.localemanager.nonexistentapp";

    public static final ComponentName TEST_APP_MAIN_ACTIVITY = new ComponentName(TEST_APP_PACKAGE,
            TEST_APP_PACKAGE + ".MainActivity");

    public static final ComponentName INSTALLER_APP_MAIN_ACTIVITY =
            new ComponentName(INSTALLER_PACKAGE, INSTALLER_PACKAGE + ".MainActivity");

    public static final ComponentName IME_APP_MAIN_ACTIVITY = new ComponentName(IME_APP_PACKAGE,
            IME_APP_PACKAGE + ".MainActivity");

    public static final String TEST_APP_BROADCAST_INFO_PROVIDER_ACTION =
            "android.locale.cts.action.TEST_APP_BROADCAST_INFO_PROVIDER";

    public static final String TEST_APP_CREATION_INFO_PROVIDER_ACTION =
            "android.locale.cts.action.TEST_APP_CREATION_INFO_PROVIDER";

    public static final String TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION =
            "android.locale.cts.action.TEST_APP_CONFIG_CHANGED_INFO_PROVIDER";

    public static final String INSTALLER_APP_BROADCAST_INFO_PROVIDER_ACTION =
            "android.locale.cts.action.INSTALLER_APP_BROADCAST_INFO_PROVIDER";

    public static final String INSTALLER_APP_CREATION_INFO_PROVIDER_ACTION =
            "android.locale.cts.action.INSTALLER_APP_CREATION_INFO_PROVIDER";

    public static final String TEST_APP_BROADCAST_RECEIVER = TEST_APP_PACKAGE
            + ".TestBroadcastReceiver";

    public static final String INSTALLER_APP_BROADCAST_RECEIVER = INSTALLER_PACKAGE
            + ".InstallerBroadcastReceiver";

    public static final String APP_RECEIVER_ACTION =
            "android.locale.cts.action.RECEIVER_ACTION";

    public static final String IME_APP_CREATION_INFO_PROVIDER_ACTION =
            "android.locale.cts.action.IME_APP_CREATION_INFO_PROVIDER";

    public static final String EXTRA_QUERY_LOCALES = "query_locales";

    public static final String EXTRA_SET_LOCALES = "set_locales";

    public static final String EXTRA_SET_LOCALECONFIG = "set_localeconfig";

    public static final String EXTRA_QUERY_LOCALECONFIG = "query_localeconfig";
}
