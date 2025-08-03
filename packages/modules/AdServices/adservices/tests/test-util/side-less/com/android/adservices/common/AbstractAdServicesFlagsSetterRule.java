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
package com.android.adservices.common;

import static com.android.adservices.service.FlagsConstants.ARRAY_SPLITTER_COMMA;
import static com.android.adservices.service.FlagsConstants.NAMESPACE_ADSERVICES;

import com.android.adservices.common.Logger.RealLogger;
import com.android.adservices.common.NameValuePair.Matcher;
import com.android.adservices.service.FlagsConstants;

import java.util.Objects;

// TODO(b/294423183): add unit tests for the most relevant / less repetitive stuff (don't need to
// test all setters / getters, for example)
/**
 * Rule used to properly set AdService flags - it will take care of permissions, restoring values at
 * the end, setting {@link android.provider.DeviceConfig} or {@link android.os.SystemProperties},
 * etc...
 */
////////////////////////////////////////////////////////////////////////////////////////////////////
// NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let your   //
// test call setFlags(flagName) (statically import FlagsConstant.flagName), which will make it    //
// easier to transition the test to an annotated-base approach.                                   //
////////////////////////////////////////////////////////////////////////////////////////////////////
abstract class AbstractAdServicesFlagsSetterRule<T extends AbstractAdServicesFlagsSetterRule<T>>
        extends AbstractFlagsSetterRule<T> {

    private static final String ALLOWLIST_SEPARATOR = ARRAY_SPLITTER_COMMA;

    // TODO(b/295321663): static import from AdServicesCommonConstants instead
    public static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";

    protected static final String LOGCAT_LEVEL_VERBOSE = "VERBOSE";

    // TODO(b/295321663): move these constants (and those from LogFactory) to AdServicesCommon
    protected static final String LOGCAT_TAG_ADSERVICES = "adservices";
    protected static final String LOGCAT_TAG_ADSERVICES_SERVICE = LOGCAT_TAG_ADSERVICES + "-system";
    protected static final String LOGCAT_TAG_TOPICS = LOGCAT_TAG_ADSERVICES + ".topics";
    protected static final String LOGCAT_TAG_FLEDGE = LOGCAT_TAG_ADSERVICES + ".fledge";
    protected static final String LOGCAT_TAG_MEASUREMENT = LOGCAT_TAG_ADSERVICES + ".measurement";
    protected static final String LOGCAT_TAG_UI = LOGCAT_TAG_ADSERVICES + ".ui";
    protected static final String LOGCAT_TAG_ADID = LOGCAT_TAG_ADSERVICES + ".adid";
    protected static final String LOGCAT_TAG_APPSETID = LOGCAT_TAG_ADSERVICES + ".appsetid";
    protected static final String LOGCAT_TAG_SHARED = "adservices-shared";

    // TODO(b/294423183): instead of hardcoding the SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX, we
    // should dynamically calculate it based on setLogcatTag() calls
    private static final Matcher PROPERTIES_PREFIX_MATCHER =
            (prop) ->
                    prop.name.startsWith(SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX)
                            || prop.name.startsWith(
                                    SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX + "adservices");

    protected AbstractAdServicesFlagsSetterRule(
            RealLogger logger,
            DeviceConfigHelper.InterfaceFactory deviceConfigInterfaceFactory,
            SystemPropertiesHelper.Interface systemPropertiesInterface) {
        super(
                logger,
                NAMESPACE_ADSERVICES,
                SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX,
                deviceConfigInterfaceFactory,
                systemPropertiesInterface);
    }

    @Override
    protected Matcher getSystemPropertiesMatcher() {
        return PROPERTIES_PREFIX_MATCHER;
    }
    // Helper methods to set more commonly used flags such as kill switches.
    // Less common flags can be set directly using setFlags methods.

    /** Overrides the flag that sets the global AdServices kill switch. */
    public T setGlobalKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_GLOBAL_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getAdServicesEnabled}.
     */
    public T setAdServicesEnabled(boolean value) {
        return setFlag(FlagsConstants.KEY_ADSERVICES_ENABLED, value);
    }

    /** Overrides the flag that sets the AppsetId kill switch. */
    public T setAppsetIdKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_APPSETID_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics kill switch. */
    public T setTopicsKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_TOPICS_KILL_SWITCH, value);
    }

    /** Overrides the flag that sets the Topics Device Classifier kill switch. */
    public T setTopicsOnDeviceClassifierKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_TOPICS_ON_DEVICE_CLASSIFIER_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getEnableBackCompat()}.
     */
    public T setEnableBackCompat(boolean value) {
        return setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, value);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMeasurementRollbackDeletionAppSearchKillSwitch()}.
     */
    public T setMeasurementRollbackDeletionAppSearchKillSwitch(boolean value) {
        return setFlag(
                FlagsConstants.KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH, value);
    }

    /**
     * Overrides flag used by {@link com.android.adservices.service.PhFlags#getPpapiAppAllowList()}.
     */
    // <p> TODO (b/303901926) - apply consistent naming to allow list methods
    public T setPpapiAppAllowList(String value) {
        return setOrCacheFlag(FlagsConstants.KEY_PPAPI_APP_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getPpapiAppSignatureAllowList()}. NOTE: this will
     * completely override the allow list, *not* append to it.
     */
    // <p> TODO (b/303901926) - apply consistent naming to allow list methods
    public T overridePpapiAppSignatureAllowList(String value) {
        return setFlag(FlagsConstants.KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST, value);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMsmtApiAppAllowList()}.
     */
    public T setMsmtApiAppAllowList(String value) {
        return setOrCacheFlag(
                FlagsConstants.KEY_MSMT_API_APP_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getWebContextClientAppAllowList()}.
     */
    public T setMsmtWebContextClientAllowList(String value) {
        return setOrCacheFlagWithSeparator(
                FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, value, ALLOWLIST_SEPARATOR);
    }

    /**
     * Overrides flag used by {@link
     * com.android.adservices.service.PhFlags#getMddBackgroundTaskKillSwitch()}.
     */
    public T setMddBackgroundTaskKillSwitch(boolean value) {
        return setFlag(FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH, value);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets all flags needed to enable compatibility mode, according to the Android version of the
     * device running the test.
     */
    public T setCompatModeFlags() {
        return runOrCache(
                "setCompatModeFlags()",
                () -> {
                    if (isAtLeastT()) {
                        mLog.d("setCompatModeFlags(): ignored on SDK %d", getDeviceSdk());
                        // Do nothing; this method is intended to set flags for Android S- only.
                        return;
                    }

                    if (isAtLeastS()) {
                        mLog.d("setCompatModeFlags(): setting flags for S+");
                        setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, true);
                        setFlag(
                                FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH,
                                FlagsConstants.APPSEARCH_ONLY);
                        setFlag(
                                FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH,
                                FlagsConstants.APPSEARCH_ONLY);
                        setFlag(FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA, true);
                        setFlag(
                                FlagsConstants
                                        .KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
                                false);
                        return;
                    }
                    mLog.d("setCompatModeFlags(): setting flags for R+");
                    setFlag(FlagsConstants.KEY_ENABLE_BACK_COMPAT, true);
                    setFlag(FlagsConstants.KEY_ENABLE_ADEXT_DATA_SERVICE_DEBUG_PROXY, true);
                });
    }

    public T setDebugUxFlagsForRvcUx() {
        return runOrCache(
                "setDebugUxFlagsForRvcUx()",
                () -> {
                    if (!isAtLeastS() && isAtLeastR()) {
                        setFlag(FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true);
                        setFlag(FlagsConstants.KEY_DEBUG_UX, "RVC_UX");
                        return;
                    }
                });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the common AdServices {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     */
    public T setDefaultLogcatTags() {
        setLogcatTag(LOGCAT_TAG_ADSERVICES, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_SHARED, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_ADSERVICES_SERVICE, LOGCAT_LEVEL_VERBOSE);
        return getThis();
    }

    /**
     * Sets all AdServices {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     */
    public T setAllLogcatTags() {
        setDefaultLogcatTags();
        setLogcatTag(LOGCAT_TAG_TOPICS, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_FLEDGE, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_MEASUREMENT, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_ADID, LOGCAT_LEVEL_VERBOSE);
        setLogcatTag(LOGCAT_TAG_APPSETID, LOGCAT_LEVEL_VERBOSE);
        return getThis();
    }

    /**
     * Sets Measurement {@code logcat} tags.
     *
     * <p>This method is usually set automatically by the factory methods, but should be set again
     * (on host-side tests) after reboot.
     */
    public T setMeasurementTags() {
        setLogcatTag(LOGCAT_TAG_MEASUREMENT, LOGCAT_LEVEL_VERBOSE);
        return getThis();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTE: DO NOT add new setXyz() methods, unless they need non-trivial logic. Instead, let    //
    // your test call setFlags(flagName) (statically import FlagsConstant.flagName), which will   //
    // make it easier to transition the test to an annotated-base approach.                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private T setOrCacheFlagWithSeparator(String name, String value, String separator) {
        return setOrCacheFlag(name, value, Objects.requireNonNull(separator));
    }
}
