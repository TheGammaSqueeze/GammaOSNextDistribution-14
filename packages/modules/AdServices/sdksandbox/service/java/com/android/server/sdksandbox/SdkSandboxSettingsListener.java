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

package com.android.server.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.sdksandbox.proto.Activity.ActivityAllowlists;
import com.android.server.sdksandbox.proto.Activity.AllowedActivities;
import com.android.server.sdksandbox.proto.BroadcastReceiver.AllowedBroadcastReceivers;
import com.android.server.sdksandbox.proto.BroadcastReceiver.BroadcastReceiverAllowlists;
import com.android.server.sdksandbox.proto.ContentProvider.AllowedContentProviders;
import com.android.server.sdksandbox.proto.ContentProvider.ContentProviderAllowlists;
import com.android.server.sdksandbox.proto.Services.AllowedServices;
import com.android.server.sdksandbox.proto.Services.ServiceAllowlists;

import com.google.protobuf.Parser;

import java.util.Map;
import java.util.Objects;

class SdkSandboxSettingsListener implements DeviceConfig.OnPropertiesChangedListener {

    private static final String TAG = "SdkSandboxManager";
    private static final String PROPERTY_DISABLE_SDK_SANDBOX = "disable_sdk_sandbox";
    private static final boolean DEFAULT_VALUE_DISABLE_SDK_SANDBOX = true;

    // Prefix all the keys with sdksandbox_ as the namespace is shared with PPAPI
    /**
     * Property to enforce restrictions for SDK sandbox processes. If the value of this property is
     * {@code true}, the restrictions will be enforced.
     */
    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    private static final boolean DEFAULT_VALUE_ENFORCE_RESTRICTIONS = true;

    /** We need to keep in sync with the property used in ProcessList */
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    private static final boolean DEFAULT_VALUE_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS = false;
    private static final String PROPERTY_CUSTOMIZED_SDK_CONTEXT_ENABLED =
            "sdksandbox_customized_sdk_context_enabled";
    private static final boolean DEFAULT_VALUE_CUSTOMIZED_SDK_CONTEXT_ENABLED = false;

    private static final String PROPERTY_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_broadcastreceiver_allowlist_per_targetSdkVersion";

    // Property for the canary set allowlist indicating which broadcast receivers can be registered
    // by the sandbox.
    private static final String PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_next_broadcastreceiver_allowlist";

    private static final String PROPERTY_CONTENTPROVIDER_ALLOWLIST =
            "contentprovider_allowlist_per_targetSdkVersion";

    // Property indicating the ContentProvider canary allowlist.
    private static final String PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST =
            "sdksandbox_next_contentprovider_allowlist";

    private static final String PROPERTY_SERVICES_ALLOWLIST =
            "services_allowlist_per_targetSdkVersion";

    // Property for canary set for service restrictions
    private static final String PROPERTY_NEXT_SERVICE_ALLOWLIST =
            "sdksandbox_next_service_allowlist";

    private static final String PROPERTY_ACTIVITY_ALLOWLIST =
            "sdksandbox_activity_allowlist_per_targetSdkVersion";
    private static final String PROPERTY_NEXT_ACTIVITY_ALLOWLIST =
            "sdksandbox_next_activity_allowlist";
    private final Context mContext;
    private final Object mLock = new Object();
    private final SdkSandboxManagerService mSdkSandboxManagerService;

    @GuardedBy("mLock")
    private boolean mKillSwitchEnabled =
            DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_DISABLE_SDK_SANDBOX,
                    DEFAULT_VALUE_DISABLE_SDK_SANDBOX);

    @GuardedBy("mLock")
    private boolean mCustomizedSdkContextEnabled =
            DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_CUSTOMIZED_SDK_CONTEXT_ENABLED,
                    DEFAULT_VALUE_CUSTOMIZED_SDK_CONTEXT_ENABLED);

    @GuardedBy("mLock")
    private boolean mEnforceRestrictions =
            DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_ENFORCE_RESTRICTIONS,
                    DEFAULT_VALUE_ENFORCE_RESTRICTIONS);

    @GuardedBy("mLock")
    private boolean mSdkSandboxApplyRestrictionsNext =
            DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                    DEFAULT_VALUE_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);

    @GuardedBy("mLock")
    private Map<Integer, AllowedServices> mServiceAllowlistPerTargetSdkVersion =
            getServicesAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_SERVICES_ALLOWLIST));

    @GuardedBy("mLock")
    private AllowedServices mNextServiceAllowlist =
            getNextServiceDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_SERVICE_ALLOWLIST));

    @GuardedBy("mLock")
    private ArrayMap<Integer, ArraySet<String>> mContentProviderAllowlistPerTargetSdkVersion =
            getContentProviderDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_CONTENTPROVIDER_ALLOWLIST));

    @GuardedBy("mLock")
    private ArraySet<String> mNextContentProviderAllowlist =
            getNextContentProviderDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES,
                            PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST));

    @Nullable
    @GuardedBy("mLock")
    private ArrayMap<Integer, ArraySet<String>> mBroadcastReceiverAllowlistPerTargetSdkVersion =
            getBroadcastReceiverDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES,
                            PROPERTY_BROADCASTRECEIVER_ALLOWLIST));

    @GuardedBy("mLock")
    private ArraySet<String> mNextBroadcastReceiverAllowlist =
            getNextBroadcastReceiverDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES,
                            PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST));

    @Nullable
    @GuardedBy("mLock")
    private ArrayMap<Integer, ArraySet<String>> mActivityAllowlistPerTargetSdkVersion =
            getActivityDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ACTIVITY_ALLOWLIST));

    @Nullable
    @GuardedBy("mLock")
    private ArraySet<String> mNextActivityAllowlist =
            getNextActivityDeviceConfigAllowlist(
                    DeviceConfig.getProperty(
                            DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_NEXT_ACTIVITY_ALLOWLIST));

    SdkSandboxSettingsListener(Context context, SdkSandboxManagerService sdkSandboxManagerService) {
        mContext = context;
        mSdkSandboxManagerService = sdkSandboxManagerService;
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_ADSERVICES, mContext.getMainExecutor(), this);
    }

    @Override
    public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
        synchronized (mLock) {
            if (!properties.getNamespace().equals(DeviceConfig.NAMESPACE_ADSERVICES)) {
                return;
            }
            for (String name : properties.getKeyset()) {
                if (name == null) {
                    continue;
                }

                switch (name) {
                    case PROPERTY_DISABLE_SDK_SANDBOX:
                        boolean killSwitchPreviouslyEnabled = mKillSwitchEnabled;
                        mKillSwitchEnabled =
                                properties.getBoolean(
                                        PROPERTY_DISABLE_SDK_SANDBOX,
                                        DEFAULT_VALUE_DISABLE_SDK_SANDBOX);
                        if (mKillSwitchEnabled && !killSwitchPreviouslyEnabled) {
                            Log.i(TAG, "SDK sandbox killswitch has become enabled");
                            this.mSdkSandboxManagerService.stopAllSandboxes();
                        }
                        break;
                    case PROPERTY_CUSTOMIZED_SDK_CONTEXT_ENABLED:
                        mCustomizedSdkContextEnabled =
                                properties.getBoolean(
                                        PROPERTY_CUSTOMIZED_SDK_CONTEXT_ENABLED,
                                        DEFAULT_VALUE_CUSTOMIZED_SDK_CONTEXT_ENABLED);
                        break;
                    case PROPERTY_ENFORCE_RESTRICTIONS:
                        mEnforceRestrictions =
                                properties.getBoolean(
                                        PROPERTY_ENFORCE_RESTRICTIONS,
                                        DEFAULT_VALUE_ENFORCE_RESTRICTIONS);
                        break;
                    case PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS:
                        mSdkSandboxApplyRestrictionsNext =
                                properties.getBoolean(
                                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                                        DEFAULT_VALUE_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
                        break;
                    case PROPERTY_SERVICES_ALLOWLIST:
                        mServiceAllowlistPerTargetSdkVersion =
                                getServicesAllowlist(
                                        properties.getString(PROPERTY_SERVICES_ALLOWLIST, null));
                        break;
                    case PROPERTY_NEXT_SERVICE_ALLOWLIST:
                        mNextServiceAllowlist =
                                getNextServiceDeviceConfigAllowlist(
                                        properties.getString(
                                                PROPERTY_NEXT_SERVICE_ALLOWLIST, null));
                        break;
                    case PROPERTY_CONTENTPROVIDER_ALLOWLIST:
                        mContentProviderAllowlistPerTargetSdkVersion =
                                getContentProviderDeviceConfigAllowlist(
                                        properties.getString(
                                                PROPERTY_CONTENTPROVIDER_ALLOWLIST, null));
                        break;
                    case PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST:
                        mNextContentProviderAllowlist =
                                getNextContentProviderDeviceConfigAllowlist(
                                        properties.getString(
                                                PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST, null));
                        break;
                    case PROPERTY_BROADCASTRECEIVER_ALLOWLIST:
                        mBroadcastReceiverAllowlistPerTargetSdkVersion =
                                getBroadcastReceiverDeviceConfigAllowlist(
                                        properties.getString(
                                                PROPERTY_BROADCASTRECEIVER_ALLOWLIST, null));
                        break;
                    case PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST:
                        mNextBroadcastReceiverAllowlist =
                                getNextBroadcastReceiverDeviceConfigAllowlist(
                                        properties.getString(
                                                PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST, null));
                        break;
                    case PROPERTY_ACTIVITY_ALLOWLIST:
                        mActivityAllowlistPerTargetSdkVersion =
                                getActivityDeviceConfigAllowlist(
                                        properties.getString(PROPERTY_ACTIVITY_ALLOWLIST, null));
                        break;
                    case PROPERTY_NEXT_ACTIVITY_ALLOWLIST:
                        mNextActivityAllowlist =
                                getNextActivityDeviceConfigAllowlist(
                                        properties.getString(
                                                PROPERTY_NEXT_ACTIVITY_ALLOWLIST, null));
                        break;
                    default:
                }
            }
        }
    }

    public boolean isKillSwitchEnabled() {
        synchronized (mLock) {
            return mKillSwitchEnabled;
        }
    }

    public void setKillSwitchState(boolean enabled) {
        synchronized (mLock) {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    PROPERTY_DISABLE_SDK_SANDBOX,
                    Boolean.toString(enabled),
                    false);
            mKillSwitchEnabled = enabled;
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void unregisterPropertiesListener() {
        DeviceConfig.removeOnPropertiesChangedListener(this);
    }

    public boolean isCustomizedSdkContextEnabled() {
        // Can only be enabled on U+ devices
        if (!SdkLevel.isAtLeastU()) {
            return false;
        }
        synchronized (mLock) {
            return mCustomizedSdkContextEnabled;
        }
    }

    public boolean areRestrictionsEnforced() {
        synchronized (mLock) {
            return mEnforceRestrictions;
        }
    }

    public boolean applySdkSandboxRestrictionsNext() {
        synchronized (mLock) {
            return mSdkSandboxApplyRestrictionsNext;
        }
    }

    public AllowedServices getServiceAllowlistForTargetSdkVersion(int targetSdkVersion) {
        synchronized (mLock) {
            return mServiceAllowlistPerTargetSdkVersion.get(targetSdkVersion);
        }
    }

    public AllowedServices getNextServiceAllowlist() {
        synchronized (mLock) {
            return mNextServiceAllowlist;
        }
    }

    public ArrayMap<Integer, ArraySet<String>> getContentProviderAllowlistPerTargetSdkVersion() {
        synchronized (mLock) {
            return mContentProviderAllowlistPerTargetSdkVersion;
        }
    }

    public ArraySet<String> getNextContentProviderAllowlist() {
        synchronized (mLock) {
            return mNextContentProviderAllowlist;
        }
    }

    @Nullable
    public ArrayMap<Integer, ArraySet<String>> getBroadcastReceiverAllowlistPerTargetSdkVersion() {
        synchronized (mLock) {
            return mBroadcastReceiverAllowlistPerTargetSdkVersion;
        }
    }

    @Nullable
    public ArraySet<String> getNextBroadcastReceiverAllowlist() {
        synchronized (mLock) {
            return mNextBroadcastReceiverAllowlist;
        }
    }

    @Nullable
    public ArrayMap<Integer, ArraySet<String>> getActivityAllowlistPerTargetSdkVersion() {
        synchronized (mLock) {
            return mActivityAllowlistPerTargetSdkVersion;
        }
    }

    @Nullable
    public ArraySet<String> getNextActivityAllowlist() {
        synchronized (mLock) {
            return mNextActivityAllowlist;
        }
    }

    /**
     * Helper function to decode a proto property
     *
     * @param property The property which needs to be decoded
     * @param base64value The base64 value of the property
     * @return The decoded value of the property passed as the parameter
     */
    private static byte[] getDecodedPropertyValue(
            @NonNull String property, @NonNull String base64value) {
        try {
            return Base64.decode(base64value, Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error while decoding " + property + " Error: " + e);
        }
        return null;
    }

    @Nullable
    private static <T> T getDeviceConfigProtoProperty(
            Parser<T> parser, @NonNull String property, @Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            Log.d(TAG, "Property " + property + " is empty.");
            return null;
        }
        final byte[] decode = getDecodedPropertyValue(property, value);
        if (Objects.isNull(decode)) {
            return null;
        }

        T proto = null;
        try {
            proto = parser.parseFrom(decode);
        } catch (Exception e) {
            Log.e(TAG, "Error while parsing " + property + ". Error: ", e);
        }

        return proto;
    }

    @NonNull
    private static Map<Integer, AllowedServices> getServicesAllowlist(@Nullable String value) {
        final ServiceAllowlists allowedServicesProto =
                getDeviceConfigProtoProperty(
                        ServiceAllowlists.parser(), PROPERTY_SERVICES_ALLOWLIST, value);
        return allowedServicesProto == null
                ? new ArrayMap<>()
                : allowedServicesProto.getAllowlistPerTargetSdkMap();
    }

    @Nullable
    private AllowedServices getNextServiceDeviceConfigAllowlist(@Nullable String value) {
        return getDeviceConfigProtoProperty(
                AllowedServices.parser(), PROPERTY_NEXT_SERVICE_ALLOWLIST, value);
    }

    @NonNull
    private static ArrayMap<Integer, ArraySet<String>> getContentProviderDeviceConfigAllowlist(
            @Nullable String value) {
        ContentProviderAllowlists contentProviderAllowlistsProto =
                getDeviceConfigProtoProperty(
                        ContentProviderAllowlists.parser(),
                        PROPERTY_CONTENTPROVIDER_ALLOWLIST,
                        value);
        // Content providers are restricted by default. If the property is not set, or it is an
        // empty string, there are no content providers to allowlist.
        if (contentProviderAllowlistsProto == null) {
            return new ArrayMap<>();
        }

        ArrayMap<Integer, ArraySet<String>> allowedContentProviders = new ArrayMap<>();

        contentProviderAllowlistsProto
                .getAllowlistPerTargetSdkMap()
                .forEach(
                        (sdkVersion, allowList) -> {
                            allowedContentProviders.put(
                                    sdkVersion, new ArraySet<>(allowList.getAuthoritiesList()));
                        });
        return allowedContentProviders;
    }

    @Nullable
    private static ArraySet<String> getNextContentProviderDeviceConfigAllowlist(
            @Nullable String value) {
        AllowedContentProviders allowedContentProviders =
                getDeviceConfigProtoProperty(
                        AllowedContentProviders.parser(),
                        PROPERTY_NEXT_CONTENTPROVIDER_ALLOWLIST,
                        value);
        if (allowedContentProviders == null) {
            return null;
        }
        return new ArraySet<>(allowedContentProviders.getAuthoritiesList());
    }

    @Nullable
    private static ArrayMap<Integer, ArraySet<String>> getBroadcastReceiverDeviceConfigAllowlist(
            @Nullable String value) {
        BroadcastReceiverAllowlists broadcastReceiverAllowlistsProto =
                getDeviceConfigProtoProperty(
                        BroadcastReceiverAllowlists.parser(),
                        PROPERTY_BROADCASTRECEIVER_ALLOWLIST,
                        value);

        if (broadcastReceiverAllowlistsProto == null) {
            return null;
        }

        ArrayMap<Integer, ArraySet<String>> allowedBroadcastReceivers = new ArrayMap<>();

        broadcastReceiverAllowlistsProto
                .getAllowlistPerTargetSdkMap()
                .forEach(
                        (sdkVersion, allowList) -> {
                            allowedBroadcastReceivers.put(
                                    sdkVersion, new ArraySet<>(allowList.getIntentActionsList()));
                        });
        return allowedBroadcastReceivers;
    }

    @Nullable
    private static ArraySet<String> getNextBroadcastReceiverDeviceConfigAllowlist(
            @Nullable String value) {
        AllowedBroadcastReceivers allowedBroadcastReceivers =
                getDeviceConfigProtoProperty(
                        AllowedBroadcastReceivers.parser(),
                        PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST,
                        value);
        if (allowedBroadcastReceivers == null) {
            return null;
        }
        return new ArraySet<>(allowedBroadcastReceivers.getIntentActionsList());
    }

    @Nullable
    private static ArrayMap<Integer, ArraySet<String>> getActivityDeviceConfigAllowlist(
            @Nullable String value) {
        ActivityAllowlists activityAllowlistsProto =
                getDeviceConfigProtoProperty(
                        ActivityAllowlists.parser(), PROPERTY_ACTIVITY_ALLOWLIST, value);

        if (activityAllowlistsProto == null) {
            return null;
        }

        ArrayMap<Integer, ArraySet<String>> allowedActivities = new ArrayMap<>();

        activityAllowlistsProto
                .getAllowlistPerTargetSdkMap()
                .forEach(
                        (sdkVersion, allowList) -> {
                            allowedActivities.put(
                                    sdkVersion, new ArraySet<>(allowList.getActionsList()));
                        });
        return allowedActivities;
    }

    @Nullable
    private static ArraySet<String> getNextActivityDeviceConfigAllowlist(@Nullable String value) {
        AllowedActivities allowedActivities =
                getDeviceConfigProtoProperty(
                        AllowedActivities.parser(), PROPERTY_NEXT_ACTIVITY_ALLOWLIST, value);
        if (allowedActivities == null) {
            return null;
        }
        return new ArraySet<>(allowedActivities.getActionsList());
    }
}
