/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.sdksandbox.testutils;

import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;

import com.android.server.sdksandbox.proto.Activity.ActivityAllowlists;
import com.android.server.sdksandbox.proto.Activity.AllowedActivities;
import com.android.server.sdksandbox.proto.BroadcastReceiver.AllowedBroadcastReceivers;
import com.android.server.sdksandbox.proto.BroadcastReceiver.BroadcastReceiverAllowlists;
import com.android.server.sdksandbox.proto.ContentProvider.AllowedContentProviders;
import com.android.server.sdksandbox.proto.ContentProvider.ContentProviderAllowlists;
import com.android.server.sdksandbox.proto.Services.AllowedService;
import com.android.server.sdksandbox.proto.Services.AllowedServices;
import com.android.server.sdksandbox.proto.Services.ServiceAllowlists;

import java.util.List;

/** Utility class to get encoded string for various restrictions */
public class ProtoUtil {
    /** Encode authorities for ContentProvider Allowlist */
    public static String encodeContentProviderAllowlist(
            ArrayMap<Integer, List<String>> authorities) {
        ContentProviderAllowlists.Builder contentProviderAllowlistsBuilder =
                ContentProviderAllowlists.newBuilder();

        authorities.entrySet().stream()
                .forEach(
                        x -> {
                            AllowedContentProviders allowedContentProvidersBuilder =
                                    AllowedContentProviders.newBuilder()
                                            .addAllAuthorities(x.getValue())
                                            .build();
                            contentProviderAllowlistsBuilder.putAllowlistPerTargetSdk(
                                    x.getKey(), allowedContentProvidersBuilder);
                        });
        ContentProviderAllowlists contentProviderAllowlists =
                contentProviderAllowlistsBuilder.build();

        return Base64.encodeToString(
                contentProviderAllowlists.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Encode authorities for ContentProvider Allowlist */
    public static String encodeContentProviderAllowlist(ArraySet<String> authorities) {
        AllowedContentProviders allowedContentProvidersBuilder =
                AllowedContentProviders.newBuilder().addAllAuthorities(authorities).build();
        return Base64.encodeToString(
                allowedContentProvidersBuilder.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Encode intent actions for startActivity Allowlist */
    public static String encodeActivityAllowlist(ArrayMap<Integer, List<String>> actions) {
        ActivityAllowlists.Builder activityAllowlistsBuilder = ActivityAllowlists.newBuilder();

        actions.entrySet().stream()
                .forEach(
                        x -> {
                            AllowedActivities allowedActivitiesBuilder =
                                    AllowedActivities.newBuilder()
                                            .addAllActions(x.getValue())
                                            .build();
                            activityAllowlistsBuilder.putAllowlistPerTargetSdk(
                                    x.getKey(), allowedActivitiesBuilder);
                        });
        ActivityAllowlists activityAllowlists = activityAllowlistsBuilder.build();
        String res =
                Base64.encodeToString(
                        activityAllowlists.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);

        return res;
    }

    /** Encode intent actions for startActivity Allowlist */
    public static String encodeActivityAllowlist(ArraySet<String> actions) {
        AllowedActivities allowedActivitiesBuilder =
                AllowedActivities.newBuilder().addAllActions(actions).build();
        return Base64.encodeToString(
                allowedActivitiesBuilder.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Encode intent actions for broadcastReceivers Allowlist */
    public static String encodeBroadcastReceiverAllowlist(
            ArrayMap<Integer, List<String>> intentActions) {
        BroadcastReceiverAllowlists.Builder broadcastReceiverAllowlistBuilder =
                BroadcastReceiverAllowlists.newBuilder();

        intentActions.entrySet().stream()
                .forEach(
                        x -> {
                            AllowedBroadcastReceivers allowedBroadcastReceivers =
                                    AllowedBroadcastReceivers.newBuilder()
                                            .addAllIntentActions(x.getValue())
                                            .build();

                            broadcastReceiverAllowlistBuilder.putAllowlistPerTargetSdk(
                                    x.getKey(), allowedBroadcastReceivers);
                        });
        BroadcastReceiverAllowlists broadcastReceiverAllowlist =
                broadcastReceiverAllowlistBuilder.build();
        return Base64.encodeToString(
                broadcastReceiverAllowlist.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Encode intent actions for broadcastReceivers Allowlist */
    public static String encodeBroadcastReceiverAllowlist(ArraySet<String> actions) {
        AllowedBroadcastReceivers allowedBroadcastReceivers =
                AllowedBroadcastReceivers.newBuilder().addAllIntentActions(actions).build();
        return Base64.encodeToString(
                allowedBroadcastReceivers.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Encode intent action, packageName, component className, component packageName for Service
     * Allowlist
     */
    public static String encodeServiceAllowlist(
            ArrayMap<Integer, List<ArrayMap<String, String>>> services) {
        ServiceAllowlists.Builder serviceAllowlistsBuilder = ServiceAllowlists.newBuilder();

        services.entrySet().stream()
                .forEach(
                        x -> {
                            serviceAllowlistsBuilder.putAllowlistPerTargetSdk(
                                    x.getKey(), encodeAllowedServices(x.getValue()));
                        });
        ServiceAllowlists serviceAllowlists = serviceAllowlistsBuilder.build();
        return Base64.encodeToString(
                serviceAllowlists.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Encode intent action, packageName, component className, component packageName for Service
     * Allowlist
     */
    public static String encodeServiceAllowlist(List<ArrayMap<String, String>> services) {
        return Base64.encodeToString(
                encodeAllowedServices(services).toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static AllowedServices encodeAllowedServices(List<ArrayMap<String, String>> services) {
        AllowedServices.Builder allowedServicesBuilder = AllowedServices.newBuilder();
        services.forEach(
                service -> {
                    allowedServicesBuilder.addAllowedServices(getAllowedService(service));
                });
        return allowedServicesBuilder.build();
    }

    private static AllowedService getAllowedService(ArrayMap<String, String> service) {
        return AllowedService.newBuilder()
                .setAction(service.get("action"))
                .setPackageName(service.get("packageName"))
                .setComponentClassName(service.get("componentClassName"))
                .setComponentPackageName(service.get("componentPackageName"))
                .build();
    }
}
