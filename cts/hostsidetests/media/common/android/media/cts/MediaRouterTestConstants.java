/*
 * Copyright 2022 The Android Open Source Project
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

package android.media.cts;


/** Holds constants shared between host and device side tests. */
public final class MediaRouterTestConstants {
    public static final String MEDIA_ROUTER_PROVIDER_1_PACKAGE =
            "android.media.router.cts.provider1";
    public static final String MEDIA_ROUTER_PROVIDER_1_APK =
            "CtsMediaRouterHostSideTestProviderApp1.apk";
    public static final String MEDIA_ROUTER_PROVIDER_2_PACKAGE =
            "android.media.router.cts.provider2";
    public static final String MEDIA_ROUTER_PROVIDER_2_APK =
            "CtsMediaRouterHostSideTestProviderApp2.apk";
    public static final String MEDIA_ROUTER_PROVIDER_3_PACKAGE =
            "android.media.router.cts.provider3";
    public static final String MEDIA_ROUTER_PROVIDER_3_APK =
            "CtsMediaRouterHostSideTestProviderApp3.apk";
    public static final String MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE =
            "android.media.router.cts.providerwithpackagemanagerspam";
    public static final String MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_APK =
            "CtsMediaRouterHostSideTestProviderWithPackageManagerSpam.apk";
    public static final String MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_PACKAGE =
            "android.media.router.cts.provider_self_scan_only";
    public static final String MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_APK =
            "CtsMediaRouterHostSideTestProviderSelfScanOnlyApp.apk";
    public static final String MEDIA_ROUTER_TEST_PACKAGE =
            "android.media.router.cts.bluetoothpermissionsapp";

    public static final String DEVICE_SIDE_TEST_CLASS =
            "android.media.router.cts.bluetoothpermissionsapp.MediaRouter2DeviceTest";

    public static final String MEDIA_ROUTER_TEST_APK =
            "CtsMediaRouterHostSideTestBluetoothPermissionsApp.apk";

    public static final String MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE =
            "android.media.router.cts.modifyaudioroutingapp";
    public static final String MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_APK =
            "CtsMediaRouterHostSideTestModifyAudioRoutingApp.apk";
    public static final String DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING =
            "android.media.router.cts.modifyaudioroutingapp"
                    + ".MediaRouter2DeviceTestWithModifyAudioRouting";
    public static final String PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_PACKAGE =
            "android.media.router.cts.proxyroutingapp";

    public static final String PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_APK =
            "CtsMediaRouterHostSideTestMediaRoutingControlApp.apk";

    public static final String PROXY_MEDIA_ROUTER_WITH_MEDIA_ROUTING_CONTROL_APP_TEST_CLASS =
            "android.media.router.cts.proxyroutingapp.MediaRouter2DeviceTest";

    public static final String MEDIA_ROUTER_SECONDARY_USER_HELPER_APK =
            "CtsProxyMediaRouterSecondaryUserTestHelperApp.apk";

    public static final String MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE =
            "android.media.router.cts.secondaryusertesthelper";

    public static final String PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_APK =
            "CtsProxyMediaRouterTestHelperApp.apk";
    public static final String PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_PACKAGE =
            "android.media.router.cts.proxymediacontentcontrolapp";
    public static final String PROXY_MEDIA_ROUTER_WITH_MEDIA_CONTENT_CONTROL_HELPER_TEST_CLASS =
            "android.media.router.cts.proxymediacontentcontrolapp.MediaRouter2DeviceTest";

    public static final String ROUTE_ID_APP_1_ROUTE_1 = "route_1-1";
    public static final String ROUTE_ID_APP_1_ROUTE_2 = "route_1-2";
    public static final String ROUTE_ID_APP_1_ROUTE_3 = "route_1-3";
    public static final String ROUTE_ID_APP_1_ROUTE_4 = "route_1-4";

    public static final String ROUTE_ID_APP_2_ROUTE_1 = "route_2-1";
    public static final String ROUTE_ID_APP_2_ROUTE_2 = "route_2-2";
    public static final String ROUTE_ID_APP_2_ROUTE_3 = "route_2-3";
    public static final String ROUTE_ID_APP_2_ROUTE_4 = "route_2-4";

    public static final String ROUTE_ID_APP_3_ROUTE_1 = "route_3-1";
    public static final String ROUTE_ID_APP_3_ROUTE_2 = "route_3-2";
    public static final String ROUTE_ID_APP_3_ROUTE_3 = "route_3-3";
    public static final String ROUTE_ID_APP_3_ROUTE_4 = "route_3-4";
    public static final String ROUTE_ID_APP_3_ROUTE_5 = "route_3-5";

    public static final String ROUTE_ID_SELF_SCAN_ONLY = "route_self_scan_only";

    public static final String ROUTE_NAME_1 = "route 1";
    public static final String ROUTE_NAME_2 = "route 2";
    public static final String ROUTE_NAME_3 = "route 3";
    public static final String ROUTE_NAME_4 = "route 4";
    public static final String ROUTE_NAME_5 = "route 5";
    public static final String ROUTE_NAME_SELF_SCAN_ONLY = "self_scan_only_route";

    public static final String ROUTE_DEDUPLICATION_ID_1 = "dedup_id_1";
    public static final String ROUTE_DEDUPLICATION_ID_2 = "dedup_id_2";
    public static final String ROUTE_DEDUPLICATION_ID_3 = "dedup_id_3";

    public static final String FEATURE_SAMPLE = "android.media.cts.FEATURE_SAMPLE";

    public static final String TARGET_USER_ID_KEY = "TARGET_USER_ID_KEY";

    private MediaRouterTestConstants() {
        // Private to prevent instantiation.
    }
}
