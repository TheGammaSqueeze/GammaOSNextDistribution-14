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

package com.android.bedstead.nene;

import com.android.bedstead.nene.accessibility.Accessibility;
import com.android.bedstead.nene.accounts.Accounts;
import com.android.bedstead.nene.activities.Activities;
import com.android.bedstead.nene.adb.Adb;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.bluetooth.Bluetooth;
import com.android.bedstead.nene.broadcasts.Broadcasts;
import com.android.bedstead.nene.bugreports.BugReports;
import com.android.bedstead.nene.certificates.Certificates;
import com.android.bedstead.nene.content.Content;
import com.android.bedstead.nene.context.Context;
import com.android.bedstead.nene.credentials.Credentials;
import com.android.bedstead.nene.device.Device;
import com.android.bedstead.nene.devicepolicy.DevicePolicy;
import com.android.bedstead.nene.dumpsys.Dumpsys;
import com.android.bedstead.nene.flags.Flags;
import com.android.bedstead.nene.inputmethods.InputMethods;
import com.android.bedstead.nene.instrumentation.Instrumentation;
import com.android.bedstead.nene.location.Locations;
import com.android.bedstead.nene.logcat.Logcat;
import com.android.bedstead.nene.notifications.Notifications;
import com.android.bedstead.nene.packages.Packages;
import com.android.bedstead.nene.permissions.Permissions;
import com.android.bedstead.nene.properties.Properties;
import com.android.bedstead.nene.resources.Resources;
import com.android.bedstead.nene.roles.Roles;
import com.android.bedstead.nene.services.Services;
import com.android.bedstead.nene.settings.Settings;
import com.android.bedstead.nene.systemproperties.SystemProperties;
import com.android.bedstead.nene.telecom.Telecom;
import com.android.bedstead.nene.telephony.Telephony;
import com.android.bedstead.nene.ui.Ui;
import com.android.bedstead.nene.users.Users;
import com.android.bedstead.nene.wallpaper.Wallpaper;
import com.android.bedstead.nene.wifi.Wifi;

/**
 * Entry point to Nene Test APIs.
 */
public final class TestApis {

    /** Access Test APIs related to Users. */
    public static Users users() {
        return Users.sInstance;
    }

    /** Access Test APIs related to Packages. */
    public static Packages packages() {
        return Packages.sInstance;
    }

    /** Access Test APIs related to device policy. */
    public static DevicePolicy devicePolicy() {
        return DevicePolicy.INSTANCE;
    }

    /** Access Test APIs related to permissions. */
    public static Permissions permissions() {
        return Permissions.sInstance;
    }

    /** Access Test APIs related to context. */
    public static Context context() {
        return Context.sInstance;
    }

    /** Access Test APIs relating to Settings. */
    public static Settings settings() {
        return Settings.INSTANCE;
    }

    /** Access Test APIs related to System Properties. */
    public static SystemProperties systemProperties() {
        return SystemProperties.sInstance;
    }

    /** Access Test APIs related to activities. */
    @Experimental
    public static Activities activities() {
        return Activities.sInstance;
    }

    /** Access Test APIs related to notifications. */
    public static Notifications notifications() {
        return Notifications.INSTANCE;
    }

    /** Access Test APIs related to the device. */
    public static Device device() {
        return Device.sInstance;
    }

    /** Access Test APIs related to location. */
    @Experimental
    public static Locations location() {
        return Locations.sInstance;
    }

    /** Access Test APIs related to accessibility. */
    @Experimental
    public static Accessibility accessibility() {
        return Accessibility.INSTANCE;
    }

    /** Access Test APIs related to bluetooth. */
    @Experimental
    public static Bluetooth bluetooth() {
        return Bluetooth.sInstance;
    }

    /** Access Test APIs related to wifi. */
    public static Wifi wifi() {
        return Wifi.INSTANCE;
    }

    /** Access Test APIs related to input methods. */
    @Experimental
    public static InputMethods inputMethods() {
        return InputMethods.sInstance;
    }

    /** Access Test APIs related to instrumentation. */
    @Experimental
    public static Instrumentation instrumentation() {
        return Instrumentation.sInstance;
    }

    /** Access Test APIs related to roles. */
    @Experimental
    public static Roles roles() {
        return Roles.sInstance;
    }

    /** Access Test APIs related to accounts. */
    @Experimental
    public static Accounts accounts() {
        return Accounts.sInstance;
    }

    /** Access Test APIs related to ui. */
    @Experimental
    public static Ui ui() {
        return Ui.sInstance;
    }

    /** Access Test APIs related to flags. */
    public static Flags flags() {
        return Flags.sInstance;
    }

    /** Access Test APIs related to resources. */
    public static Resources resources() {
        return Resources.sInstance;
    }

    /** Access Test APIs related to broadcasts. */
    public static Broadcasts broadcasts() {
        return Broadcasts.sInstance;
    }

    /** Access Test APIs related to telecom */
    @Experimental
    public static Telecom telecom() {
        return Telecom.INSTANCE;
    }

    /** Access Test APIs related to logcat */
    @Experimental
    public static Logcat logcat() {
        return Logcat.sInstance;
    }

    /** Access Test APIs related to credential manager. */
    public static Credentials credentials() {
        return Credentials.sInstance;
    }

    /** Access Test APIs related to content. */
    public static Content content() {
        return Content.sInstance;
    }

    /** Access Test APIs related to system services. */
    public static Services services() {
        return Services.sInstance;
    }

    /** Access Test APIs related to adb. */
    public static Adb adb() {
        return Adb.INSTANCE;
    }

    /** Access Test APIs related to certificates. */
    public static Certificates certificates() {
        return Certificates.sInstance;
    }

    /** Access Test APIs related to wallpaper. */
    public static Wallpaper wallpaper() {
        return Wallpaper.INSTANCE;
    }

    /** Access Test APIs related to telephony. */
    public static Telephony telephony() {
        return Telephony.INSTANCE;
    }

    /** Access Test APIs related to bug reports. */
    public static BugReports bugReports() {
        return BugReports.INSTANCE;
    }

    /** Test APIs related to System Properties. */
    public static Properties properties() {
        return Properties.INSTANCE;
    }

    /** Access Test APIs related to dumpsys. */
    public static Dumpsys dumpsys() {
        return Dumpsys.INSTANCE;
    }

    /** @deprecated Use statically */
    @Deprecated()
    public TestApis() {

    }
}

