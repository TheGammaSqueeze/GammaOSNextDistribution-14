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

package android.adservices.lint.test

import android.adservices.lint.BackCompatAndroidProcessDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BackCompatAndroidProcessDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = BackCompatAndroidProcessDetector()

    override fun getIssues(): List<Issue> = listOf(BackCompatAndroidProcessDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun validConfig_doesNotThrow() {
        lint().files((
                manifest("""
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:androidprv="http://schemas.android.com/apk/prv/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="android.ext.services"
    android:versionCode="309999900"
    android:versionName="2019-09"
    coreApp="true">

    <!-- Sample text and unimportant tags -->
    <protected-broadcast android:name="com.android.ext.adservices.PACKAGE_CHANGED"/>
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
    <permission android:name="android.permission.ACCESS_ADSERVICES_TOPICS"
                android:label="@string/permlab_accessAdServicesTopics"
                android:description="@string/permdesc_accessAdServicesTopics"
                android:protectionLevel="normal"/>

    <application
        android:name=".ExtServicesApplication"
        android:label="@string/app_name"
        android:forceQueryable="true"
        android:allowBackup="false"
        android:supportsRtl="true"
        android:icon="@drawable/ic_android_icon"
        android:theme="@style/FilterTouches">
        <!-- Activity with process -->
        <activity
            android:name="com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity"
            android:exported="true"
            android:enabled="false"
            android:theme="@style/Theme.SubSettingsBase"
            android:process=".adservices">
            <intent-filter android:priority="1">
                <action android:name="android.adservices.ui.SETTINGS"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <!-- Service with process -->
        <service android:name="com.android.adservices.adselection.AdSelectionService"
                 android:exported="true"
                 android:visibleToInstantApps="false"
                 android:process=".adservices">
            <intent-filter android:priority="1">
                <action android:name="android.adservices.adselection.AD_SELECTION_SERVICE"/>
            </intent-filter>
        </service>

        <!-- Provider with process -->
        <provider
            android:name=
                "com.android.adservices.service.measurement.attribution.TriggerContentProvider"
            android:authorities="com.android.ext.adservices.provider.trigger"
            android:exported="false"
            android:process=".adservices"
        />

        <!-- Receiver with process -->
        <receiver android:name="com.android.adservices.service.common.AdExtBootCompletedReceiver"
                  android:enabled="@bool/isAdExtBootCompletedReceiverEnabled"
                  android:exported="true"
                  android:process=".adservices">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>
    </application>
</manifest>                """))).run().expectClean()
    }

    @Test
    fun invalidConfig_fourMissingProcesses_throws() {
        lint().files((
                manifest("""
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:androidprv="http://schemas.android.com/apk/prv/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="android.ext.services"
    android:versionCode="309999900"
    android:versionName="2019-09"
    coreApp="true">

    <application
        android:name=".ExtServicesApplication"
        android:label="@string/app_name"
        android:forceQueryable="true"
        android:allowBackup="false"
        android:supportsRtl="true"
        android:icon="@drawable/ic_android_icon"
        android:theme="@style/FilterTouches">

        <!-- Activity missing process -->
        <activity
            android:name="com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity"
            android:exported="true"
            android:enabled="false"
            android:theme="@style/Theme.SubSettingsBase">
            <intent-filter android:priority="1">
                <action android:name="android.adservices.ui.SETTINGS"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <!-- Service missing process -->
        <service android:name="com.android.adservices.adselection.AdSelectionService"
                 android:exported="true"
                 android:visibleToInstantApps="false">
            <intent-filter android:priority="1">
                <action android:name="android.adservices.adselection.AD_SELECTION_SERVICE"/>
            </intent-filter>
        </service>

        <!-- Provider missing process -->
        <provider
            android:name=
                "com.android.adservices.service.measurement.attribution.TriggerContentProvider"
            android:authorities="com.android.ext.adservices.provider.trigger"
            android:exported="false"
        />

        <!-- Receiver missing process -->
        <receiver android:name="com.android.adservices.service.common.AdExtBootCompletedReceiver"
                  android:enabled="@bool/isAdExtBootCompletedReceiverEnabled"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>
    </application>
</manifest>                """))).run().expect("AndroidManifest.xml: Error: AdExtServicesManifest.xml is missing an `android:process=\".adservices\" at line 24 [MissingAndroidProcessInAdExtServicesManifest]\n" +
                "AndroidManifest.xml: Error: AdExtServicesManifest.xml is missing an `android:process=\".adservices\" at line 34 [MissingAndroidProcessInAdExtServicesManifest]\n" +
                "AndroidManifest.xml: Error: AdExtServicesManifest.xml is missing an `android:process=\".adservices\" at line 46 [MissingAndroidProcessInAdExtServicesManifest]\n" +
                "AndroidManifest.xml: Error: AdExtServicesManifest.xml is missing an `android:process=\".adservices\" at line 51 [MissingAndroidProcessInAdExtServicesManifest]\n" +
                "4 errors, 0 warnings")
    }

    @Test
    fun validConfig_missingProcessesInNonAdExtServicesManifest_doesNotThrow() {
        lint().files((
                manifest("src/main/AndroidManifestOther.xml",
                        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:androidprv="http://schemas.android.com/apk/prv/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="android.ext.services"
    android:versionCode="309999900"
    android:versionName="2019-09"
    coreApp="true">

    <application
        android:name=".ExtServicesApplication"
        android:label="@string/app_name"
        android:forceQueryable="true"
        android:allowBackup="false"
        android:supportsRtl="true"
        android:icon="@drawable/ic_android_icon"
        android:theme="@style/FilterTouches">

        <!-- Activity missing process -->
        <activity
            android:name="com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity"
            android:exported="true"
            android:enabled="false"
            android:theme="@style/Theme.SubSettingsBase">
            <intent-filter android:priority="1">
                <action android:name="android.adservices.ui.SETTINGS"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>
    </application>
</manifest>                """))).run().expectClean()
    }

}