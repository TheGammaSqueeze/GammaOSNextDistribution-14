/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.permissionui.cts

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_OTHER
import android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.DeviceConfig
import android.provider.Settings
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.modules.utils.build.SdkLevel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before

abstract class BaseUsePermissionTest : BasePermissionTest() {
    companion object {
        const val APP_APK_NAME_31 = "CtsUsePermissionApp31.apk"
        const val APP_APK_NAME_LATEST = "CtsUsePermissionAppLatest.apk"

        const val APP_APK_PATH_22 = "$APK_DIRECTORY/CtsUsePermissionApp22.apk"
        const val APP_APK_PATH_22_CALENDAR_ONLY =
            "$APK_DIRECTORY/CtsUsePermissionApp22CalendarOnly.apk"
        const val APP_APK_PATH_22_NONE = "$APK_DIRECTORY/CtsUsePermissionApp22None.apk"
        const val APP_APK_PATH_23 = "$APK_DIRECTORY/CtsUsePermissionApp23.apk"
        const val APP_APK_PATH_25 = "$APK_DIRECTORY/CtsUsePermissionApp25.apk"
        const val APP_APK_PATH_26 = "$APK_DIRECTORY/CtsUsePermissionApp26.apk"
        const val APP_APK_PATH_28 = "$APK_DIRECTORY/CtsUsePermissionApp28.apk"
        const val APP_APK_PATH_29 = "$APK_DIRECTORY/CtsUsePermissionApp29.apk"
        const val APP_APK_PATH_30 = "$APK_DIRECTORY/CtsUsePermissionApp30.apk"
        const val APP_APK_PATH_31 = "$APK_DIRECTORY/$APP_APK_NAME_31"
        const val APP_APK_PATH_32 = "$APK_DIRECTORY/CtsUsePermissionApp32.apk"

        const val APP_APK_PATH_30_WITH_BACKGROUND =
            "$APK_DIRECTORY/CtsUsePermissionApp30WithBackground.apk"
        const val APP_APK_PATH_30_WITH_BLUETOOTH =
            "$APK_DIRECTORY/CtsUsePermissionApp30WithBluetooth.apk"
        const val APP_APK_PATH_LATEST = "$APK_DIRECTORY/CtsUsePermissionAppLatest.apk"
        const val APP_APK_PATH_LATEST_NONE = "$APK_DIRECTORY/CtsUsePermissionAppLatestNone.apk"
        const val APP_APK_PATH_WITH_OVERLAY = "$APK_DIRECTORY/CtsUsePermissionAppWithOverlay.apk"
        const val APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31 =
            "$APK_DIRECTORY/CtsCreateNotificationChannelsApp31.apk"
        const val APP_APK_PATH_MEDIA_PERMISSION_33_WITH_STORAGE =
            "$APK_DIRECTORY/CtsMediaPermissionApp33WithStorage.apk"
        const val APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE =
            "$APK_DIRECTORY/CtsUsePermissionAppImplicitUserSelectStorage.apk"
        const val APP_APK_PATH_STORAGE_33 = "$APK_DIRECTORY/CtsUsePermissionAppStorage33.apk"
        const val APP_APK_PATH_OTHER_APP = "$APK_DIRECTORY/CtsDifferentPkgNameApp.apk"
        const val APP_PACKAGE_NAME = "android.permissionui.cts.usepermission"
        const val OTHER_APP_PACKAGE_NAME = "android.permissionui.cts.usepermissionother"
        const val TEST_INSTALLER_PACKAGE_NAME = "android.permissionui.cts"

        const val ALLOW_ALL_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_all_button"
        const val SELECT_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_selected_button"
        const val DONT_SELECT_MORE_BUTTON =
            "com.android.permissioncontroller:id/permission_dont_allow_more_selected_button"
        const val ALLOW_BUTTON = "com.android.permissioncontroller:id/permission_allow_button"
        const val ALLOW_FOREGROUND_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        const val DENY_BUTTON = "com.android.permissioncontroller:id/permission_deny_button"
        const val DENY_AND_DONT_ASK_AGAIN_BUTTON =
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button"
        const val NO_UPGRADE_BUTTON =
            "com.android.permissioncontroller:id/permission_no_upgrade_button"
        const val NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON =
            "com.android.permissioncontroller:" +
                "id/permission_no_upgrade_and_dont_ask_again_button"

        const val ALLOW_ALWAYS_RADIO_BUTTON =
            "com.android.permissioncontroller:id/allow_always_radio_button"
        const val ALLOW_RADIO_BUTTON = "com.android.permissioncontroller:id/allow_radio_button"
        const val ALLOW_FOREGROUND_RADIO_BUTTON =
            "com.android.permissioncontroller:id/allow_foreground_only_radio_button"
        const val ASK_RADIO_BUTTON = "com.android.permissioncontroller:id/ask_radio_button"
        const val DENY_RADIO_BUTTON = "com.android.permissioncontroller:id/deny_radio_button"
        const val SELECT_RADIO_BUTTON = "com.android.permissioncontroller:id/select_radio_button"
        const val EDIT_PHOTOS_BUTTON = "com.android.permissioncontroller:id/edit_selected_button"

        const val NOTIF_TEXT = "permgrouprequest_notifications"
        const val ALLOW_BUTTON_TEXT = "grant_dialog_button_allow"
        const val ALLOW_ALL_FILES_BUTTON_TEXT = "app_permission_button_allow_all_files"
        const val ALLOW_FOREGROUND_BUTTON_TEXT = "grant_dialog_button_allow_foreground"
        const val ALLOW_FOREGROUND_PREFERENCE_TEXT = "permission_access_only_foreground"
        const val ASK_BUTTON_TEXT = "app_permission_button_ask"
        const val ALLOW_ONE_TIME_BUTTON_TEXT = "grant_dialog_button_allow_one_time"
        const val DENY_BUTTON_TEXT = "grant_dialog_button_deny"
        const val DENY_ANYWAY_BUTTON_TEXT = "grant_dialog_button_deny_anyway"
        const val DENY_AND_DONT_ASK_AGAIN_BUTTON_TEXT =
            "grant_dialog_button_deny_and_dont_ask_again"
        const val NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON_TEXT = "grant_dialog_button_no_upgrade"
        const val ALERT_DIALOG_MESSAGE = "android:id/message"
        const val ALERT_DIALOG_OK_BUTTON = "android:id/button1"
        const val APP_PERMISSION_RATIONALE_CONTAINER_VIEW =
            "com.android.permissioncontroller:id/app_permission_rationale_container"
        const val APP_PERMISSION_RATIONALE_CONTENT_VIEW =
            "com.android.permissioncontroller:id/app_permission_rationale_content"
        const val GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW =
            "com.android.permissioncontroller:id/permission_rationale_container"
        const val PERMISSION_RATIONALE_ACTIVITY_TITLE_VIEW =
            "com.android.permissioncontroller:id/permission_rationale_title"
        const val DATA_SHARING_SOURCE_TITLE_ID =
            "com.android.permissioncontroller:id/data_sharing_source_title"
        const val DATA_SHARING_SOURCE_MESSAGE_ID =
            "com.android.permissioncontroller:id/data_sharing_source_message"
        const val PURPOSE_TITLE_ID = "com.android.permissioncontroller:id/purpose_title"
        const val PURPOSE_MESSAGE_ID = "com.android.permissioncontroller:id/purpose_message"
        const val LEARN_MORE_TITLE_ID = "com.android.permissioncontroller:id/learn_more_title"
        const val LEARN_MORE_MESSAGE_ID = "com.android.permissioncontroller:id/learn_more_message"
        const val PERMISSION_RATIONALE_SETTINGS_SECTION =
            "com.android.permissioncontroller:id/settings_section"
        const val SETTINGS_TITLE_ID = "com.android.permissioncontroller:id/settings_title"
        const val SETTINGS_MESSAGE_ID = "com.android.permissioncontroller:id/settings_message"

        const val REQUEST_LOCATION_MESSAGE = "permgrouprequest_location"

        const val DATA_SHARING_UPDATES = "Data sharing updates for location"
        const val DATA_SHARING_UPDATES_SUBTITLE =
            "These apps have changed the way they may share your location data. They may not" +
                " have shared it before, or may now share it for advertising or marketing" +
                " purposes."
        const val DATA_SHARING_NO_UPDATES_MESSAGE = "No updates at this time"
        const val UPDATES_IN_LAST_30_DAYS = "Updated within 30 days"
        const val DATA_SHARING_UPDATES_FOOTER_MESSAGE =
            "The developers of these apps provided info about their data sharing practices" +
                " to an app store. They may update it over time.\n\nData sharing" +
                " practices may vary based on your app version, use, region, and age."
        const val LEARN_ABOUT_DATA_SHARING = "Learn about data sharing"
        const val LOCATION_PERMISSION = "Location permission"
        const val APP_PACKAGE_NAME_SUBSTRING = "android.permissionui"
        const val NOW_SHARED_WITH_THIRD_PARTIES =
            "Your location data is now shared with third " + "parties"
        const val NOW_SHARED_WITH_THIRD_PARTIES_FOR_ADS =
            "Your location data is now shared with " + "third parties for advertising or marketing"
        const val PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS = "data_sharing_update_period_millis"
        const val PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP =
            "max_safety_labels_persisted_per_app"

        // The highest SDK for which the system will show a "low SDK" warning when launching the app
        const val MAX_SDK_FOR_SDK_WARNING = 27
        const val MIN_SDK_FOR_RUNTIME_PERMS = 23

        val TEST_INSTALLER_ACTIVITY_COMPONENT_NAME =
            ComponentName(context, TestInstallerActivity::class.java)

        val MEDIA_PERMISSIONS: Set<String> =
            mutableSetOf(
                    Manifest.permission.ACCESS_MEDIA_LOCATION,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
                .apply {
                    if (SdkLevel.isAtLeastU()) {
                        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                }
                .toSet()

        val STORAGE_AND_MEDIA_PERMISSIONS =
            MEDIA_PERMISSIONS.plus(Manifest.permission.READ_EXTERNAL_STORAGE)
                .plus(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        @JvmStatic protected val PICKER_ENABLED_SETTING = "photo_picker_prompt_enabled"

        @JvmStatic
        protected fun isPhotoPickerPermissionPromptEnabled(): Boolean {
            return SdkLevel.isAtLeastU() &&
                !isTv &&
                !isAutomotive &&
                !isWatch &&
                callWithShellPermissionIdentity {
                    DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_PRIVACY,
                        PICKER_ENABLED_SETTING,
                        true
                    )
                }
        }
    }

    enum class PermissionState {
        ALLOWED,
        DENIED,
        DENIED_WITH_PREJUDICE
    }

    private val platformResources = context.createPackageContext("android", 0).resources
    private val permissionToLabelResNameMap =
        mapOf(
            // Contacts
            android.Manifest.permission.READ_CONTACTS to "@android:string/permgrouplab_contacts",
            android.Manifest.permission.WRITE_CONTACTS to "@android:string/permgrouplab_contacts",
            // Calendar
            android.Manifest.permission.READ_CALENDAR to "@android:string/permgrouplab_calendar",
            android.Manifest.permission.WRITE_CALENDAR to "@android:string/permgrouplab_calendar",
            // SMS
            android.Manifest.permission.SEND_SMS to "@android:string/permgrouplab_sms",
            android.Manifest.permission.RECEIVE_SMS to "@android:string/permgrouplab_sms",
            android.Manifest.permission.READ_SMS to "@android:string/permgrouplab_sms",
            android.Manifest.permission.RECEIVE_WAP_PUSH to "@android:string/permgrouplab_sms",
            android.Manifest.permission.RECEIVE_MMS to "@android:string/permgrouplab_sms",
            "android.permission.READ_CELL_BROADCASTS" to "@android:string/permgrouplab_sms",
            // Storage
            android.Manifest.permission.READ_EXTERNAL_STORAGE to
                "@android:string/permgrouplab_storage",
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE to
                "@android:string/permgrouplab_storage",
            // Location
            android.Manifest.permission.ACCESS_FINE_LOCATION to
                "@android:string/permgrouplab_location",
            android.Manifest.permission.ACCESS_COARSE_LOCATION to
                "@android:string/permgrouplab_location",
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION to
                "@android:string/permgrouplab_location",
            // Phone
            android.Manifest.permission.READ_PHONE_STATE to "@android:string/permgrouplab_phone",
            android.Manifest.permission.CALL_PHONE to "@android:string/permgrouplab_phone",
            "android.permission.ACCESS_IMS_CALL_SERVICE" to "@android:string/permgrouplab_phone",
            android.Manifest.permission.READ_CALL_LOG to "@android:string/permgrouplab_phone",
            android.Manifest.permission.WRITE_CALL_LOG to "@android:string/permgrouplab_phone",
            android.Manifest.permission.ADD_VOICEMAIL to "@android:string/permgrouplab_phone",
            android.Manifest.permission.USE_SIP to "@android:string/permgrouplab_phone",
            android.Manifest.permission.PROCESS_OUTGOING_CALLS to
                "@android:string/permgrouplab_phone",
            // Microphone
            android.Manifest.permission.RECORD_AUDIO to "@android:string/permgrouplab_microphone",
            // Camera
            android.Manifest.permission.CAMERA to "@android:string/permgrouplab_camera",
            // Body sensors
            android.Manifest.permission.BODY_SENSORS to "@android:string/permgrouplab_sensors",
            android.Manifest.permission.BODY_SENSORS_BACKGROUND to
                "@android:string/permgrouplab_sensors",
            // Bluetooth
            android.Manifest.permission.BLUETOOTH_CONNECT to
                "@android:string/permgrouplab_nearby_devices",
            android.Manifest.permission.BLUETOOTH_SCAN to
                "@android:string/permgrouplab_nearby_devices",
            // Aural
            android.Manifest.permission.READ_MEDIA_AUDIO to
                "@android:string/permgrouplab_readMediaAural",
            // Visual
            android.Manifest.permission.READ_MEDIA_IMAGES to
                "@android:string/permgrouplab_readMediaVisual",
            android.Manifest.permission.READ_MEDIA_VIDEO to
                "@android:string/permgrouplab_readMediaVisual"
        )

    @Before
    @After
    fun uninstallApp() {
        uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
    }

    override fun installPackage(
        apkPath: String,
        reinstall: Boolean,
        grantRuntimePermissions: Boolean,
        expectSuccess: Boolean,
        installSource: String?
    ) {
        installPackage(
            apkPath,
            reinstall,
            grantRuntimePermissions,
            expectSuccess,
            installSource,
            false
        )
    }

    fun installPackage(
        apkPath: String,
        reinstall: Boolean = false,
        grantRuntimePermissions: Boolean = false,
        expectSuccess: Boolean = true,
        installSource: String? = null,
        skipClearLowSdkDialog: Boolean = false
    ) {
        super.installPackage(
            apkPath,
            reinstall,
            grantRuntimePermissions,
            expectSuccess,
            installSource
        )

        val targetSdk = getTargetSdk()
        // If the targetSDK is high enough, the low sdk warning won't show. If the SDK is
        // below runtime permissions, the dialog will be delayed by the permission review screen.
        // If success is not expected, don't bother trying
        if (
            targetSdk > MAX_SDK_FOR_SDK_WARNING ||
                targetSdk < MIN_SDK_FOR_RUNTIME_PERMS ||
                !expectSuccess ||
                skipClearLowSdkDialog
        ) {
            return
        }

        val finishOnCreateIntent =
            Intent().apply {
                component =
                    ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.FinishOnCreateActivity")
                flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
            }

        // Check if an activity resolves for the test app. If it doesn't, then our test app doesn't
        // have the usual set of activities, and likely won't be opened, and thus, won't show the
        // dialog
        callWithShellPermissionIdentity {
            context.packageManager.resolveActivity(finishOnCreateIntent, PackageManager.MATCH_ALL)
        }
            ?: return

        // Start the test app, and expect the targetSDK warning dialog
        context.startActivity(finishOnCreateIntent)
        clearTargetSdkWarning()
        // Kill the test app, so that the next time we launch, we don't see the app warning dialog
        killTestApp()
    }

    protected fun clearTargetSdkWarning(timeoutMillis: Long = TIMEOUT_MILLIS) {
        if (SdkLevel.isAtLeastV()) {
            // In V and above, the target SDK dialog can be disabled via system property
            return
        }

        waitFindObjectOrNull(By.res("android:id/button1"), timeoutMillis)?.let {
            try {
                it.click()
            } catch (e: StaleObjectException) {
                // Click sometimes fails with StaleObjectException (b/280430717).
                e.printStackTrace()
            }
        }
    }

    protected fun killTestApp() {
        pressBack()
        pressBack()
        runWithShellPermissionIdentity {
            val am = context.getSystemService(ActivityManager::class.java)!!
            am.forceStopPackage(APP_PACKAGE_NAME)
        }
        waitForIdle()
    }

    protected fun clickPermissionReviewContinue() {
        if (isAutomotive || isWatch) {
            clickAndWaitForWindowTransition(
                By.text(getPermissionControllerString("review_button_continue")),
                TIMEOUT_MILLIS * 2
            )
        } else {
            clickAndWaitForWindowTransition(
                By.res("com.android.permissioncontroller:id/continue_button")
            )
        }
    }

    protected fun clickPermissionReviewContinueAndClearSdkWarning() {
        clickPermissionReviewContinue()
        clearTargetSdkWarning()
    }

    protected fun installPackageWithInstallSourceAndEmptyMetadata(apkName: String) {
        installPackageViaSession(apkName, AppMetadata.createEmptyAppMetadata())
    }

    protected fun installPackageWithInstallSourceAndMetadata(apkName: String) {
        installPackageViaSession(apkName, AppMetadata.createDefaultAppMetadata())
    }

    protected fun installPackageWithInstallSourceAndMetadataFromStore(apkName: String) {
        installPackageViaSession(
            apkName,
            AppMetadata.createDefaultAppMetadata(),
            PACKAGE_SOURCE_STORE
        )
    }

    protected fun installPackageWithInstallSourceAndMetadataFromLocalFile(apkName: String) {
        installPackageViaSession(
            apkName,
            AppMetadata.createDefaultAppMetadata(),
            PACKAGE_SOURCE_LOCAL_FILE
        )
    }

    protected fun installPackageWithInstallSourceAndMetadataFromDownloadedFile(apkName: String) {
        installPackageViaSession(
            apkName,
            AppMetadata.createDefaultAppMetadata(),
            PACKAGE_SOURCE_DOWNLOADED_FILE
        )
    }

    protected fun installPackageWithInstallSourceFromDownloadedFileAndAllowHardRestrictedPerms(
        apkName: String
    ) {
        installPackageViaSession(
            apkName,
            AppMetadata.createDefaultAppMetadata(),
            PACKAGE_SOURCE_DOWNLOADED_FILE,
            allowlistedRestrictedPermissions = SessionParams.RESTRICTED_PERMISSIONS_ALL
        )
    }

    protected fun installPackageWithInstallSourceAndMetadataFromOther(apkName: String) {
        installPackageViaSession(
            apkName,
            AppMetadata.createDefaultAppMetadata(),
            PACKAGE_SOURCE_OTHER
        )
    }

    protected fun installPackageWithInstallSourceAndNoMetadata(apkName: String) {
        installPackageViaSession(apkName)
    }

    protected fun installPackageWithInstallSourceAndInvalidMetadata(apkName: String) {
        installPackageViaSession(apkName, AppMetadata.createInvalidAppMetadata())
    }

    protected fun installPackageWithInstallSourceAndMetadataWithoutTopLevelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName,
            AppMetadata.createInvalidAppMetadataWithoutTopLevelVersion()
        )
    }

    protected fun installPackageWithInstallSourceAndMetadataWithInvalidTopLevelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName,
            AppMetadata.createInvalidAppMetadataWithInvalidTopLevelVersion()
        )
    }

    protected fun installPackageWithInstallSourceAndMetadataWithoutSafetyLabelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName,
            AppMetadata.createInvalidAppMetadataWithoutSafetyLabelVersion()
        )
    }

    protected fun installPackageWithInstallSourceAndMetadataWithInvalidSafetyLabelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName,
            AppMetadata.createInvalidAppMetadataWithInvalidSafetyLabelVersion()
        )
    }

    protected fun installPackageWithoutInstallSource(apkName: String) {
        // TODO(b/257293222): Update/remove when hooking up PackageManager APIs
        installPackage(apkName)
    }

    protected fun assertPermissionRationaleActivityTitleIsVisible(expected: Boolean) {
        findView(By.res(PERMISSION_RATIONALE_ACTIVITY_TITLE_VIEW), expected = expected)
    }

    protected fun assertPermissionRationaleActivityDataSharingSourceSectionVisible(
        expected: Boolean
    ) {
        findView(By.res(DATA_SHARING_SOURCE_TITLE_ID), expected = expected)
        findView(By.res(DATA_SHARING_SOURCE_MESSAGE_ID), expected = expected)
    }

    protected fun assertPermissionRationaleActivityPurposeSectionVisible(expected: Boolean) {
        findView(By.res(PURPOSE_TITLE_ID), expected = expected)
        findView(By.res(PURPOSE_MESSAGE_ID), expected = expected)
    }

    protected fun assertPermissionRationaleActivityLearnMoreSectionVisible(expected: Boolean) {
        findView(By.res(LEARN_MORE_TITLE_ID), expected = expected)
        findView(By.res(LEARN_MORE_MESSAGE_ID), expected = expected)
    }

    protected fun assertPermissionRationaleActivitySettingsSectionVisible(expected: Boolean) {
        findView(By.res(PERMISSION_RATIONALE_SETTINGS_SECTION), expected = expected)
        findView(By.res(SETTINGS_TITLE_ID), expected = expected)
        findView(By.res(SETTINGS_MESSAGE_ID), expected = expected)
    }

    protected fun assertPermissionRationaleDialogIsVisible(
        expected: Boolean,
        showSettingsSection: Boolean = true
    ) {
        assertPermissionRationaleActivityTitleIsVisible(expected)
        assertPermissionRationaleActivityDataSharingSourceSectionVisible(expected)
        assertPermissionRationaleActivityPurposeSectionVisible(expected)
        assertPermissionRationaleActivityLearnMoreSectionVisible(expected)
        if (expected) {
            assertPermissionRationaleActivitySettingsSectionVisible(showSettingsSection)
        }
    }

    protected fun assertPermissionRationaleContainerOnGrantDialogIsVisible(expected: Boolean) {
        findView(By.res(GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW), expected = expected)
    }

    protected fun clickPermissionReviewCancel() {
        if (isAutomotive || isWatch) {
            clickAndWaitForWindowTransition(
                By.text(getPermissionControllerString("review_button_cancel"))
            )
        } else {
            clickAndWaitForWindowTransition(
                By.res("com.android.permissioncontroller:id/cancel_button")
            )
        }
    }

    protected fun approvePermissionReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {
            clickPermissionReviewContinueAndClearSdkWarning()
        }
    }

    protected fun cancelPermissionReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_CANCELED) {
            clickPermissionReviewCancel()
        }
    }

    protected fun assertAppDoesNotNeedPermissionReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {}
    }

    protected inline fun startAppActivityAndAssertResultCode(
        expectedResultCode: Int,
        block: () -> Unit
    ) {
        val future =
            startActivityForFuture(
                Intent().apply {
                    component =
                        ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.FinishOnCreateActivity")
                }
            )
        block()
        assertEquals(
            expectedResultCode,
            future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).resultCode
        )
    }

    protected inline fun requestAppPermissionsForNoResult(
        vararg permissions: String?,
        crossinline block: () -> Unit
    ) {
        // Request the permissions
        doAndWaitForWindowTransition {
            context.startActivity(
                Intent().apply {
                    component =
                        ComponentName(
                            APP_PACKAGE_NAME,
                            "$APP_PACKAGE_NAME.RequestPermissionsActivity"
                        )
                    putExtra("$APP_PACKAGE_NAME.PERMISSIONS", permissions)
                    addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
        }
        // Perform the post-request action
        block()
    }

    protected inline fun requestAppPermissions(
        vararg permissions: String?,
        askTwice: Boolean = false,
        waitForWindowTransition: Boolean = true,
        crossinline block: () -> Unit
    ): Instrumentation.ActivityResult {
        // Request the permissions
        lateinit var future: CompletableFuture<Instrumentation.ActivityResult>
        doAndWaitForWindowTransition {
            future =
                startActivityForFuture(
                    Intent().apply {
                        component =
                            ComponentName(
                                APP_PACKAGE_NAME,
                                "$APP_PACKAGE_NAME.RequestPermissionsActivity"
                            )
                        putExtra("$APP_PACKAGE_NAME.PERMISSIONS", permissions)
                        putExtra("$APP_PACKAGE_NAME.ASK_TWICE", askTwice)
                    }
                )
        }

        // Notification permission prompt is shown first, so get it out of the way
        clickNotificationPermissionRequestAllowButtonIfAvailable()
        // Perform the post-request action
        if (waitForWindowTransition) {
            doAndWaitForWindowTransition { block() }
        } else {
            block()
        }
        return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    }

    protected inline fun requestAppPermissionsAndAssertResult(
        permissions: Array<out String?>,
        permissionAndExpectedGrantResults: Array<out Pair<String?, Boolean>>,
        askTwice: Boolean = false,
        waitForWindowTransition: Boolean = !isWatch,
        crossinline block: () -> Unit
    ) {
        val result =
            requestAppPermissions(
                *permissions,
                askTwice = askTwice,
                waitForWindowTransition = waitForWindowTransition,
                block = block
            )
        assertEquals(
            "Permission request result had unexpected resultCode:",
            Activity.RESULT_OK,
            result.resultCode
        )

        val responseSize: Int =
            result.resultData!!.getStringArrayExtra("$APP_PACKAGE_NAME.PERMISSIONS")!!.size
        assertEquals(
            "Permission request result had unexpected number of grant results:",
            responseSize,
            result.resultData!!.getIntArrayExtra("$APP_PACKAGE_NAME.GRANT_RESULTS")!!.size
        )

        // Note that the behavior around requesting `null` permissions changed in the platform
        // in Android U. Currently, null permissions are ignored and left out of the result set.
        assertTrue(
            "Permission request result had fewer permissions than request",
            permissions.size >= responseSize
        )
        assertEquals(
            "Permission request result had unexpected grant results:",
            permissionAndExpectedGrantResults.filter { it.first != null }.toList(),
            result.resultData!!
                .getStringArrayExtra("$APP_PACKAGE_NAME.PERMISSIONS")!!
                .filterNotNull()
                .zip(
                    result.resultData!!.getIntArrayExtra("$APP_PACKAGE_NAME.GRANT_RESULTS")!!.map {
                        it == PackageManager.PERMISSION_GRANTED
                    }
                )
        )

        permissionAndExpectedGrantResults.forEach {
            it.first?.let { permission -> assertAppHasPermission(permission, it.second) }
        }
    }

    protected inline fun requestAppPermissionsAndAssertResult(
        vararg permissionAndExpectedGrantResults: Pair<String?, Boolean>,
        askTwice: Boolean = false,
        waitForWindowTransition: Boolean = !isWatch,
        crossinline block: () -> Unit
    ) {
        requestAppPermissionsAndAssertResult(
            permissionAndExpectedGrantResults.map { it.first }.toTypedArray(),
            permissionAndExpectedGrantResults,
            askTwice,
            waitForWindowTransition,
            block
        )
    }

    // Perform the requested action, then wait both for the action to complete, and for at least
    // one window transition to occur since the moment the action begins executing.
    protected inline fun doAndWaitForWindowTransition(crossinline block: () -> Unit) {
        val timeoutOccurred =
            !uiDevice.performActionAndWait(
                { block() },
                Until.newWindow(),
                NEW_WINDOW_TIMEOUT_MILLIS
            )

        if (timeoutOccurred) {
            throw RuntimeException("Timed out waiting for window transition.")
        }
    }

    protected fun findPermissionRequestAllowButton(timeoutMillis: Long = 20000) {
        if (isAutomotive || isWatch) {
            waitFindObject(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)), timeoutMillis)
        } else {
            waitFindObject(By.res(ALLOW_BUTTON), timeoutMillis)
        }
    }

    protected fun clickPermissionRequestAllowButton(timeoutMillis: Long = 20000) {
        if (isAutomotive || isWatch) {
            click(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)), timeoutMillis)
        } else {
            click(By.res(ALLOW_BUTTON), timeoutMillis)
        }
    }

    protected fun clickPermissionRequestAllowAllButton(timeoutMillis: Long = 20000) {
        click(By.res(ALLOW_ALL_BUTTON), timeoutMillis)
    }

    /**
     * Only for use in tests that are not testing the notification permission popup, on T devices
     */
    protected fun clickNotificationPermissionRequestAllowButtonIfAvailable() {
        if (!SdkLevel.isAtLeastT()) {
            return
        }

        if (
            waitFindObjectOrNull(
                By.text(getPermissionControllerString(NOTIF_TEXT, APP_PACKAGE_NAME)),
                1000
            ) != null
        ) {
            if (isAutomotive) {
                click(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)))
            } else {
                click(By.res(ALLOW_BUTTON))
            }
        }
    }

    protected fun clickPermissionRequestSettingsLinkAndAllowAlways() {
        clickPermissionRequestSettingsLink()
        eventually({ clickAllowAlwaysInSettings() }, TIMEOUT_MILLIS * 2)
        pressBack()
    }

    protected fun clickAllowAlwaysInSettings() {
        if (isAutomotive || isTv || isWatch) {
            click(By.text(getPermissionControllerString("app_permission_button_allow_always")))
        } else {
            click(By.res("com.android.permissioncontroller:id/allow_always_radio_button"))
        }
    }

    protected fun clickAllowForegroundInSettings() {
        click(By.res(ALLOW_FOREGROUND_RADIO_BUTTON))
    }

    protected fun clicksDenyInSettings() {
        if (isAutomotive || isWatch) {
            click(By.text(getPermissionControllerString("app_permission_button_deny")))
        } else {
            click(By.res("com.android.permissioncontroller:id/deny_radio_button"))
        }
    }

    protected fun findPermissionRequestAllowForegroundButton(timeoutMillis: Long = 20000) {
        if (isAutomotive || isWatch) {
            waitFindObject(
                By.text(getPermissionControllerString(ALLOW_FOREGROUND_BUTTON_TEXT)),
                timeoutMillis
            )
        } else {
            waitFindObject(By.res(ALLOW_FOREGROUND_BUTTON), timeoutMillis)
        }
    }

    protected fun clickPermissionRequestAllowForegroundButton(timeoutMillis: Long = 10_000) {
        if (isAutomotive || isWatch) {
            click(
                By.text(getPermissionControllerString(ALLOW_FOREGROUND_BUTTON_TEXT)),
                timeoutMillis
            )
        } else {
            click(By.res(ALLOW_FOREGROUND_BUTTON), timeoutMillis)
        }
    }

    protected fun clickPermissionRequestDenyButton() {
        if (isAutomotive || isWatch || isTv) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(By.res(DENY_BUTTON))
        }
    }

    protected fun clickPermissionRequestSettingsLinkAndDeny() {
        clickPermissionRequestSettingsLink()
        eventually({ clicksDenyInSettings() }, TIMEOUT_MILLIS * 2)
        pressBack()
    }

    protected fun clickPermissionRequestSettingsLink() {
        eventually {
            // UiObject2 doesn't expose CharSequence.
            val node =
                if (isAutomotive) {
                    // Should match "Allow in settings." (location) and "go to settings." (body
                    // sensors)
                    uiAutomation.rootInActiveWindow
                        .findAccessibilityNodeInfosByText(" settings.")[0]
                } else {
                    uiAutomation.rootInActiveWindow
                        .findAccessibilityNodeInfosByViewId(
                            "com.android.permissioncontroller:id/detail_message"
                        )[0]
                }
            if (!node.isVisibleToUser) {
                scrollToBottom()
            }
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpan = text.getSpans(0, text.length, ClickableSpan::class.java)[0]
            // We could pass in null here in Java, but we need an instance in Kotlin.
            doAndWaitForWindowTransition { clickableSpan.onClick(View(context)) }
        }
    }

    protected fun clickPermissionRequestDenyAndDontAskAgainButton() {
        if (isAutomotive) {
            click(By.text(getPermissionControllerString(DENY_AND_DONT_ASK_AGAIN_BUTTON_TEXT)))
        } else if (isWatch) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(By.res(DENY_AND_DONT_ASK_AGAIN_BUTTON))
        }
    }

    // Only used in TV and Watch form factors
    protected fun clickPermissionRequestDontAskAgainButton() {
        if (isWatch) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(
                By.res("com.android.permissioncontroller:id/permission_deny_dont_ask_again_button")
            )
        }
    }

    protected fun clickPermissionRequestNoUpgradeAndDontAskAgainButton() {
        if (isAutomotive) {
            click(By.text(getPermissionControllerString(NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON_TEXT)))
        } else {
            click(By.res(NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON))
        }
    }

    protected fun clickPermissionRationaleContentInAppPermission() {
        clickAndWaitForWindowTransition(By.res(APP_PERMISSION_RATIONALE_CONTENT_VIEW))
    }

    protected fun clickPermissionRationaleViewInGrantDialog() {
        clickAndWaitForWindowTransition(By.res(GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW))
    }

    protected fun grantAppPermissionsByUi(vararg permissions: String) {
        setAppPermissionState(*permissions, state = PermissionState.ALLOWED, isLegacyApp = false)
    }

    protected fun grantRuntimePermissions(vararg permissions: String) {
        for (permission in permissions) {
            uiAutomation.grantRuntimePermission(APP_PACKAGE_NAME, permission)
        }
    }

    protected fun revokeAppPermissionsByUi(
        vararg permissions: String,
        isLegacyApp: Boolean = false
    ) {
        setAppPermissionState(
            *permissions,
            state = PermissionState.DENIED,
            isLegacyApp = isLegacyApp
        )
    }

    private fun navigateToAppPermissionSettings() {
        if (isTv) {
            // Dismiss DeprecatedTargetSdkVersionDialog, if present
            if (waitFindObjectOrNull(By.text(APP_PACKAGE_NAME), 1000L) != null) {
                pressBack()
            }
            pressHome()
        } else {
            pressBack()
            pressBack()
            pressBack()
        }

        // Try multiple times as the AppInfo page might have read stale data
        eventually(
            {
                try {
                    // Open the app details settings
                    doAndWaitForWindowTransition {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", APP_PACKAGE_NAME, null)
                                addCategory(Intent.CATEGORY_DEFAULT)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                        )
                    }
                    if (isTv) {
                        pressDPadDown()
                        pressDPadDown()
                        pressDPadDown()
                        pressDPadDown()
                    }
                    // Open the permissions UI
                    clickAndWaitForWindowTransition(byTextRes(R.string.permissions).enabled(true))
                } catch (e: Exception) {
                    pressBack()
                    throw e
                }
            },
            TIMEOUT_MILLIS
        )
    }

    private fun getTargetSdk(packageName: String = APP_PACKAGE_NAME): Int {
        return callWithShellPermissionIdentity {
            try {
                context.packageManager.getApplicationInfo(packageName, 0).targetSdkVersion
            } catch (e: PackageManager.NameNotFoundException) {
                -1
            }
        }
    }
    protected fun navigateToIndividualPermissionSetting(
        permission: String,
        manuallyNavigate: Boolean = false,
    ) {
        navigateToIndividualPermissionSetting(permission, APP_PACKAGE_NAME, manuallyNavigate)
    }

    protected fun navigateToIndividualPermissionSetting(
        permission: String,
        packageName: String,
        manuallyNavigate: Boolean = false,
    ) {
        val useLegacyNavigation = isWatch || isAutomotive || manuallyNavigate
        if (useLegacyNavigation) {
            navigateToAppPermissionSettings()
            val permissionLabel = getPermissionLabel(permission)
            if (isWatch) {
                clickAndWaitForWindowTransition(By.text(permissionLabel), 40_000)
            } else {
                clickPermissionControllerUi(By.text(permissionLabel))
            }
            return
        }
        doAndWaitForWindowTransition {
            runWithShellPermissionIdentity {
                context.startActivity(
                    Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
                        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                        putExtra(Intent.EXTRA_PERMISSION_NAME, permission)
                        putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            }
        }
    }

    /** Starts activity with intent [ACTION_REVIEW_APP_DATA_SHARING_UPDATES]. */
    fun startAppDataSharingUpdatesActivity() {
        doAndWaitForWindowTransition {
            runWithShellPermissionIdentity {
                context.startActivity(
                    Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                        addFlags(FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    private fun setAppPermissionState(
        vararg permissions: String,
        state: PermissionState,
        isLegacyApp: Boolean,
        manuallyNavigate: Boolean = false,
    ) {
        val useLegacyNavigation = isWatch || isAutomotive || manuallyNavigate
        if (useLegacyNavigation) {
            navigateToAppPermissionSettings()
        }

        val navigatedGroupLabels = mutableSetOf<String>()
        for (permission in permissions) {
            // Find the permission screen
            val permissionLabel = getPermissionLabel(permission)
            if (navigatedGroupLabels.contains(getPermissionLabel(permission))) {
                continue
            }
            navigatedGroupLabels.add(permissionLabel)
            if (useLegacyNavigation) {
                if (isWatch) {
                    click(By.text(permissionLabel), 40_000)
                } else if (isAutomotive) {
                    clickPermissionControllerUi(permissionLabel)
                } else {
                    clickPermissionControllerUi(By.text(permissionLabel))
                }
            } else {
                doAndWaitForWindowTransition {
                    runWithShellPermissionIdentity {
                        context.startActivity(
                            Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
                                putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
                                putExtra(Intent.EXTRA_PERMISSION_NAME, permission)
                                putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                        )
                    }
                }
            }

            val wasGranted =
                if (isAutomotive) {
                    // Automotive doesn't support one time permissions, and thus
                    // won't show an "Ask every time" message
                    !waitFindObject(
                            By.text(getPermissionControllerString("app_permission_button_deny"))
                        )
                        .isChecked
                } else if (isTv || isWatch) {
                    !(waitFindObject(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
                        .isChecked ||
                        (!isLegacyApp &&
                            hasAskButton(permission) &&
                            waitFindObject(By.text(getPermissionControllerString(ASK_BUTTON_TEXT)))
                                .isChecked))
                } else {
                    !(waitFindObject(By.res(DENY_RADIO_BUTTON)).isChecked ||
                        (!isLegacyApp &&
                            hasAskButton(permission) &&
                            waitFindObject(By.res(ASK_RADIO_BUTTON)).isChecked))
                }
            var alreadyChecked = false
            val button =
                waitFindObject(
                    if (isAutomotive) {
                        // Automotive doesn't support one time permissions, and thus
                        // won't show an "Ask every time" message
                        when (state) {
                            PermissionState.ALLOWED ->
                                if (showsForegroundOnlyButton(permission)) {
                                    By.text(
                                        getPermissionControllerString(
                                            "app_permission_button_allow_foreground"
                                        )
                                    )
                                } else {
                                    By.text(
                                        getPermissionControllerString("app_permission_button_allow")
                                    )
                                }
                            PermissionState.DENIED ->
                                By.text(getPermissionControllerString("app_permission_button_deny"))
                            PermissionState.DENIED_WITH_PREJUDICE ->
                                By.text(getPermissionControllerString("app_permission_button_deny"))
                        }
                    } else if (isTv || isWatch) {
                        when (state) {
                            PermissionState.ALLOWED ->
                                if (showsForegroundOnlyButton(permission)) {
                                    By.text(
                                        getPermissionControllerString(
                                            ALLOW_FOREGROUND_PREFERENCE_TEXT
                                        )
                                    )
                                } else {
                                    byAnyText(
                                        getPermissionControllerResString(ALLOW_BUTTON_TEXT),
                                        getPermissionControllerResString(
                                            ALLOW_ALL_FILES_BUTTON_TEXT
                                        )
                                    )
                                }
                            PermissionState.DENIED ->
                                if (!isLegacyApp && hasAskButton(permission)) {
                                    By.text(getPermissionControllerString(ASK_BUTTON_TEXT))
                                } else {
                                    By.text(getPermissionControllerString(DENY_BUTTON_TEXT))
                                }
                            PermissionState.DENIED_WITH_PREJUDICE ->
                                By.text(getPermissionControllerString(DENY_BUTTON_TEXT))
                        }
                    } else {
                        when (state) {
                            PermissionState.ALLOWED ->
                                if (showsForegroundOnlyButton(permission)) {
                                    By.res(ALLOW_FOREGROUND_RADIO_BUTTON)
                                } else if (showsAlwaysButton(permission)) {
                                    By.res(ALLOW_ALWAYS_RADIO_BUTTON)
                                } else {
                                    By.res(ALLOW_RADIO_BUTTON)
                                }
                            PermissionState.DENIED ->
                                if (!isLegacyApp && hasAskButton(permission)) {
                                    By.res(ASK_RADIO_BUTTON)
                                } else {
                                    By.res(DENY_RADIO_BUTTON)
                                }
                            PermissionState.DENIED_WITH_PREJUDICE -> By.res(DENY_RADIO_BUTTON)
                        }
                    }
                )
            alreadyChecked = button.isChecked
            if (!alreadyChecked) {
                button.click()
            }

            val shouldShowStorageWarning =
                SdkLevel.isAtLeastT() &&
                    getTargetSdk() <= Build.VERSION_CODES.S_V2 &&
                    permission in MEDIA_PERMISSIONS
            if (shouldShowStorageWarning) {
                if (isWatch) {
                    click(
                        By.desc(
                            getPermissionControllerString("media_confirm_dialog_positive_button")
                        )
                    )
                } else {
                    click(By.res(ALERT_DIALOG_OK_BUTTON))
                }
            } else if (!alreadyChecked && isLegacyApp && wasGranted) {
                if (!isTv) {
                    // Wait for alert dialog to popup, then scroll to the bottom of it
                    if (isWatch) {
                        waitFindObject(
                            By.text(getPermissionControllerString("old_sdk_deny_warning"))
                        )
                    } else {
                        waitFindObject(By.res(ALERT_DIALOG_MESSAGE))
                    }
                    scrollToBottom()
                }

                // Due to the limited real estate, Wear uses buttons with icons instead of text
                // for dialogs
                if (isWatch) {
                    click(By.desc(getPermissionControllerString("ok")))
                } else {
                    val resources =
                        context
                            .createPackageContext(packageManager.permissionControllerPackageName, 0)
                            .resources
                    val confirmTextRes =
                        resources.getIdentifier(
                            "com.android.permissioncontroller:string/grant_dialog_button_deny_anyway",
                            null,
                            null
                        )

                    val confirmText = resources.getString(confirmTextRes)
                    click(byTextStartsWithCaseInsensitive(confirmText))
                }
            }
            pressBack()
        }
        pressBack()
        pressBack()
    }

    private fun getPermissionLabel(permission: String): String {
        val labelResName = permissionToLabelResNameMap[permission]
        assertNotNull("Unknown permission $permission", labelResName)
        val labelRes = platformResources.getIdentifier(labelResName, null, null)
        return platformResources.getString(labelRes)
    }

    private fun hasAskButton(permission: String): Boolean =
        when (permission) {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION -> true
            else -> false
        }
    private fun showsAllowPhotosButton(permission: String): Boolean {
        if (!isPhotoPickerPermissionPromptEnabled()) {
            return false
        }
        return when (permission) {
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO -> true
            else -> false
        }
    }

    private fun showsForegroundOnlyButton(permission: String): Boolean =
        when (permission) {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO -> true
            else -> false
        }

    private fun showsAlwaysButton(permission: String): Boolean =
        when (permission) {
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION -> true
            else -> false
        }

    private fun scrollToBottom() {
        val scrollable =
            UiScrollable(UiSelector().scrollable(true)).apply {
                if (isWatch) {
                    swipeDeadZonePercentage = 0.1
                } else {
                    swipeDeadZonePercentage = 0.25
                }
            }
        waitForIdle()
        if (scrollable.exists()) {
            try {
                scrollable.flingToEnd(10)
            } catch (e: UiObjectNotFoundException) {
                // flingToEnd() sometimes still fails despite waitForIdle() and the exists() check
                // (b/246984354).
                e.printStackTrace()
            }
        }
    }

    private fun byTextRes(textRes: Int): BySelector = By.text(context.getString(textRes))

    private fun byTextStartsWithCaseInsensitive(prefix: String): BySelector =
        By.text(Pattern.compile("(?i)^${Pattern.quote(prefix)}.*$"))

    protected fun assertAppHasPermission(permissionName: String, expectPermission: Boolean) {
        val checkPermissionResult = packageManager.checkPermission(permissionName, APP_PACKAGE_NAME)
        assertTrue(
            "Invalid permission check result: $checkPermissionResult",
            checkPermissionResult == PackageManager.PERMISSION_GRANTED ||
                checkPermissionResult == PackageManager.PERMISSION_DENIED
        )
        if (!expectPermission && checkPermissionResult == PackageManager.PERMISSION_GRANTED) {
            Assert.fail(
                "Unexpected permission check result for $permissionName: " +
                    "expected -1 (PERMISSION_DENIED) but was 0 (PERMISSION_GRANTED)"
            )
        }
        if (expectPermission && checkPermissionResult == PackageManager.PERMISSION_DENIED) {
            Assert.fail(
                "Unexpected permission check result for $permissionName: " +
                    "expected 0 (PERMISSION_GRANTED) but was -1 (PERMISSION_DENIED)"
            )
        }
    }

    protected fun assertAppHasCalendarAccess(expectAccess: Boolean) {
        val future =
            startActivityForFuture(
                Intent().apply {
                    component =
                        ComponentName(
                            APP_PACKAGE_NAME,
                            "$APP_PACKAGE_NAME.CheckCalendarAccessActivity"
                        )
                }
            )
        clickNotificationPermissionRequestAllowButtonIfAvailable()
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertTrue(result.resultData!!.hasExtra("$APP_PACKAGE_NAME.HAS_ACCESS"))
        assertEquals(
            expectAccess,
            result.resultData!!.getBooleanExtra("$APP_PACKAGE_NAME.HAS_ACCESS", false)
        )
    }

    protected fun assertPermissionFlags(permName: String, vararg flags: Pair<Int, Boolean>) {
        val user = Process.myUserHandle()
        SystemUtil.runWithShellPermissionIdentity {
            val currFlags = packageManager.getPermissionFlags(permName, APP_PACKAGE_NAME, user)
            for ((flag, set) in flags) {
                assertEquals("flag $flag: ", set, currFlags and flag != 0)
            }
        }
    }
}
