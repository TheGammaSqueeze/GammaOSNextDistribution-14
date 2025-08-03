
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'policy/AccessibilityTest',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_AccessibilityTest.policy_AccessibilityTest',
        ),
        test_common.define_test(
            'policy/AlternateErrorPages',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_AlternateErrorPages.policy_AlternateErrorPages',
        ),
        test_common.define_test(
            'policy/ArcAudioCaptureAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ArcAudioCaptureAllowed.policy_ArcAudioCaptureAllowed',
        ),
        test_common.define_test(
            'policy/ArcBackupRestoreServiceEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ArcBackupRestoreServiceEnabled.policy_ArcBackupRestoreServiceEnabled',
        ),
        test_common.define_test(
            'policy/ArcDisableScreenshots',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ArcDisableScreenshots.policy_ArcDisableScreenshots',
        ),
        test_common.define_test(
            'policy/ArcExternalStorageDisabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ArcExternalStorageDisabled.policy_ArcExternalStorageDisabled',
        ),
        test_common.define_test(
            'policy/ArcVideoCaptureAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ArcVideoCaptureAllowed.policy_ArcVideoCaptureAllowed',
        ),
        test_common.define_test(
            'policy/AudioOutputAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_AudioOutputAllowed.policy_AudioOutputAllowed',
        ),
        test_common.define_test(
            'policy/AutotestSanity',
            suites = ['bvt-perbuild', 'ent-nightly', 'policy', 'smoke'],
            main_package = 'autotest_lib.client.site_tests.policy_AutotestCheck.policy_AutotestCheck',
        ),
        test_common.define_test(
            'policy/BookmarkBarEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_BookmarkBarEnabled.policy_BookmarkBarEnabled',
        ),
        test_common.define_test(
            'policy/ChromeOsLockOnIdleSuspend',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ChromeOsLockOnIdleSuspend.policy_ChromeOsLockOnIdleSuspend',
        ),
        test_common.define_test(
            'policy/CookiesAllowedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_CookiesAllowedForUrls.policy_CookiesAllowedForUrls',
        ),
        test_common.define_test(
            'policy/CookiesBlockedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_CookiesBlockedForUrls.policy_CookiesBlockedForUrls',
        ),
        test_common.define_test(
            'policy/CookiesSessionOnlyForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_CookiesSessionOnlyForUrls.policy_CookiesSessionOnlyForUrls',
        ),
        test_common.define_test(
            'policy/DefaultGeolocationSetting',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DefaultGeolocationSetting.policy_DefaultGeolocationSetting',
        ),
        test_common.define_test(
            'policy/DefaultImagesSetting',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DefaultImagesSetting.policy_DefaultImagesSetting',
        ),
        test_common.define_test(
            'policy/DefaultJavaScriptSetting',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DefaultJavaScriptSetting.policy_DefaultJavaScriptSetting',
        ),
        test_common.define_test(
            'policy/DefaultNotificationsSetting',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DefaultNotificationsSetting.policy_DefaultNotificationsSetting',
        ),
        test_common.define_test(
            'policy/DefaultSearchProvider',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DefaultSearchProvider.policy_DefaultSearchProvider',
        ),
        test_common.define_test(
            'policy/DefaultSearchProviderEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DefaultSearchProviderEnabled.policy_DefaultSearchProviderEnabled',
        ),
        test_common.define_test(
            'policy/DeveloperToolsAvailability',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeveloperToolsAvailability.policy_DeveloperToolsAvailability',
        ),
        test_common.define_test(
            'policy/DeviceAllowBluetooth',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceAllowBluetooth.policy_DeviceAllowBluetooth',
        ),
        test_common.define_test(
            'policy/DeviceAutoUpdateDisabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceAutoUpdateDisabled.policy_DeviceAutoUpdateDisabled',
        ),
        test_common.define_test(
            'policy/DeviceBootOnAcEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceBootOnAcEnabled.policy_DeviceBootOnAcEnabled',
        ),
        test_common.define_test(
            'policy/DeviceCharging',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceCharging.policy_DeviceCharging',
        ),
        test_common.define_test(
            'policy/DeviceEphemeralUsersEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceEphemeralUsersEnabled.policy_DeviceEphemeralUsersEnabled',
        ),
        test_common.define_test(
            'policy/DeviceScheduledCharging',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceScheduledCharging.policy_DeviceScheduledCharging',
        ),
        test_common.define_test(
            'policy/DeviceWilcoDtcAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DeviceWilcoDtcAllowed.policy_DeviceWilcoDtcAllowed',
        ),
        test_common.define_test(
            'policy/DisableScreenshots',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DisableScreenshots.policy_DisableScreenshots',
        ),
        test_common.define_test(
            'policy/DownloadDirectory',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DownloadDirectory.policy_DownloadDirectory',
        ),
        test_common.define_test(
            'policy/DriveDisabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_DriveDisabled.policy_DriveDisabled',
        ),
        test_common.define_test(
            'policy/EditBookmarksEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_EditBookmarksEnabled.policy_EditBookmarksEnabled',
        ),
        test_common.define_test(
            'policy/EnrollmentRetainment',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_EnrollmentRetainment.policy_EnrollmentRetainment',
        ),
        test_common.define_test(
            'policy/EnterpriseForceInstallCustom',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_EnterpriseForceInstallCustom.policy_EnterpriseForceInstallCustom',
        ),
        test_common.define_test(
            'policy/ExtensionAllowedTypes',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ExtensionAllowedTypes.policy_ExtensionAllowedTypes',
        ),
        test_common.define_test(
            'policy/ExtensionControl',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ExtensionControl.policy_ExtensionControl',
        ),
        test_common.define_test(
            'policy/ExtensionPolicy',
            suites = ['ent-nightly', 'policy'],
            main_package = 'autotest_lib.client.site_tests.policy_ExtensionPolicy.policy_ExtensionPolicy',
        ),
        test_common.define_test(
            'policy/ExternalStorageDisabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ExternalStorageDisabled.policy_ExternalStorageDisabled',
        ),
        test_common.define_test(
            'policy/ExternalStorageReadOnly',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ExternalStorageReadOnly.policy_ExternalStorageReadOnly',
        ),
        test_common.define_test(
            'policy/ForceGoogleSafeSearch',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ForceGoogleSafeSearch.policy_ForceGoogleSafeSearch',
        ),
        test_common.define_test(
            'policy/ForceYouTubeRestrict',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ForceYouTubeRestrict.policy_ForceYouTubeRestrict',
        ),
        test_common.define_test(
            'policy/ForceYouTubeSafetyMode',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ForceYouTubeSafetyMode.policy_ForceYouTubeSafetyMode',
        ),
        test_common.define_test(
            'policy/GlobalNetworkSettings',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_GlobalNetworkSettings.policy_GlobalNetworkSettings',
        ),
        test_common.define_test(
            'policy/HomepageLocation',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_HomepageLocation.policy_HomepageLocation',
        ),
        test_common.define_test(
            'policy/ImagesAllowedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ImagesAllowedForUrls.policy_ImagesAllowedForUrls',
        ),
        test_common.define_test(
            'policy/ImagesBlockedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ImagesBlockedForUrls.policy_ImagesBlockedForUrls',
        ),
        test_common.define_test(
            'policy/JavaScriptAllowedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_JavaScriptAllowedForUrls.policy_JavaScriptAllowedForUrls',
        ),
        test_common.define_test(
            'policy/JavaScriptBlockedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_JavaScriptBlockedForUrls.policy_JavaScriptBlockedForUrls',
        ),
        test_common.define_test(
            'policy/KeyPermissions',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_KeyPermissions.policy_KeyPermissions',
        ),
        test_common.define_test(
            'policy/KeyboardDefaultFunctionKeys',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_KeyboardDefaultToFunctionKeys.policy_KeyboardDefaultFunctionKeys',
        ),
        test_common.define_test(
            'policy/KioskModeEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_KioskModeEnabled.policy_KioskModeEnabled',
        ),
        test_common.define_test(
            'policy/ManagedBookmarks',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ManagedBookmarks.policy_ManagedBookmarks',
        ),
        test_common.define_test(
            'policy/NewTabPageLocation',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_NewTabPageLocation.policy_HomepageLocation',
        ),
        test_common.define_test(
            'policy/NotificationsAllowedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_NotificationsAllowedForUrls.policy_NotificationsAllowedForUrls',
        ),
        test_common.define_test(
            'policy/NotificationsBlockedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_NotificationsBlockedForUrls.policy_NotificationsAllowedForUrls',
        ),
        test_common.define_test(
            'policy/PinnedLauncherApps',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PinnedLauncherApps.policy_PinnedLauncherApps',
        ),
        test_common.define_test(
            'policy/PlatformKeys',
            suites = ['ent-nightly', 'policy'],
            main_package = 'autotest_lib.client.site_tests.policy_PlatformKeys.policy_PlatformKeys',
        ),
        test_common.define_test(
            'policy/PluginsAllowedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PluginsAllowedForUrls.policy_PluginsAllowedForUrls',
        ),
        test_common.define_test(
            'policy/PluginsBlockedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PluginsBlockedForUrls.policy_PluginsBlockedForUrls',
        ),
        test_common.define_test(
            'policy/PolicyRefreshRate',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PolicyRefreshRate.policy_PolicyRefreshRate',
        ),
        test_common.define_test(
            'policy/PopupsAllowedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PopupsAllowedForUrls.policy_PopupsAllowedForUrls',
        ),
        test_common.define_test(
            'policy/PopupsBlockedForUrls',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PopupsBlockedForUrls.policy_PopupsBlockedForUrls',
        ),
        test_common.define_test(
            'policy/PowerManagementIdleSettings',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PowerManagementIdleSettings.policy_PowerManagementIdleSettings',
        ),
        test_common.define_test(
            'policy/PrintingEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PrintingEnabled.policy_PrintingEnabled',
        ),
        test_common.define_test(
            'policy/PromptForDownloadLocation',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_PromptForDownloadLocation.policy_PromptForDownloadLocation',
        ),
        test_common.define_test(
            'policy/ProxySettings',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ProxySettings.policy_ProxySettings',
        ),
        test_common.define_test(
            'policy/ReportUploadFrequency',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ReportUploadFrequency.policy_ReportUploadFrequency',
        ),
        test_common.define_test(
            'policy/RestoreOnStartupURLs',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_RestoreOnStartupURLs.policy_RestoreOnStartupURLs',
        ),
        test_common.define_test(
            'policy/SafeBrowsingEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_SafeBrowsingEnabled.policy_SafeBrowsingEnabled',
        ),
        test_common.define_test(
            'policy/SavingBrowserHistoryDisabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_SavingBrowserHistoryDisabled.policy_SavingBrowserHistoryDisabled',
        ),
        test_common.define_test(
            'policy/ScreenBrightnessPercent',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ScreenBrightnessPercent.policy_ScreenBrightnessPercent',
        ),
        test_common.define_test(
            'policy/SearchSuggestEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_SearchSuggestEnabled.policy_SearchSuggestEnabled',
        ),
        test_common.define_test(
            'policy/SecondaryGoogleAccountSigninAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_SecondaryGoogleAccountSigninAllowed.policy_SecondaryGoogleAccountSigninAllowed',
        ),
        test_common.define_test(
            'policy/ShowHomeButton',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ShowHomeButton.policy_ShowHomeButton',
        ),
        test_common.define_test(
            'policy/ShowLogoutButtonInTray',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_ShowLogoutButtonInTray.policy_ShowLogoutButtonInTray',
        ),
        test_common.define_test(
            'policy/SystemTimezone',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_SystemTimezone.policy_SystemTimezone',
        ),
        test_common.define_test(
            'policy/TouchVirtualKeyboardEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_TouchVirtualKeyboardEnabled.policy_TouchVirtualKeyboardEnabled',
        ),
        test_common.define_test(
            'policy/TranslateEnabled',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_TranslateEnabled.policy_TranslateEnabled',
        ),
        test_common.define_test(
            'policy/UIUtilsSmokeTest',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_UIUtilsSmokeTest.policy_UIUtilsSmokeTest',
        ),
        test_common.define_test(
            'policy/UserNativePrintersAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_UserNativePrintersAllowed.policy_UserNativePrintersAllowed',
        ),
        test_common.define_test(
            'policy/VirtualMachinesAllowed',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_VirtualMachinesAllowed.policy_VirtualMachinesAllowed',
        ),
        test_common.define_test(
            'policy/WiFiAutoconnect',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_WiFiAutoconnect.policy_WiFiAutoconnect',
        ),
        test_common.define_test(
            'policy/WiFiPrecedence',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_WiFiPrecedence.policy_WiFiPrecedence',
        ),
        test_common.define_test(
            'policy/WiFiTypes',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_WiFiTypes.policy_WiFiTypes',
        ),
        test_common.define_test(
            'policy/WiFiTypesServer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'policy/WilcoOnNonWilcoDevice',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_WilcoOnNonWilcoDevice.policy_WilcoOnNonWilcoDevice',
        ),
        test_common.define_test(
            'policy/WilcoUSBPowershare',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.policy_WilcoUSBPowershare.policy_WilcoUSBPowershare',
        )
    ]
