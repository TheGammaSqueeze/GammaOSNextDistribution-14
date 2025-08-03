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

package com.android.ondevicepersonalization.services.statsd;

import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_CLASS__UNKNOWN;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_REDIRECT;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_RESPONSE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EXECUTE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__FEDERATED_COMPUTE_SCHEDULE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_GET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_KEYSET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_PUT;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_REMOVE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_JOINED_EVENTS;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_REQUESTS;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__MODEL_MANAGER_RUN;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_GET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_KEYSET;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REQUEST_SURFACE_PACKAGE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_DOWNLOAD_COMPLETED;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EVENT;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EXECUTE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_RENDER;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_TRAINING_EXAMPLE;
import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_WEB_TRIGGER;


import com.android.ondevicepersonalization.internal.util.DataClass;

/**
 * Class holds OnDevicePersonalizationApiCalled defined at
 * frameworks/proto_logging/stats/atoms/ondevicepersonalization/ondevicepersonalization_extension_atoms.proto
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public class ApiCallStats {
    public static final int API_EXECUTE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EXECUTE;
    public static final int API_REQUEST_SURFACE_PACKAGE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REQUEST_SURFACE_PACKAGE;
    public static final int API_SERVICE_ON_EXECUTE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EXECUTE;
    public static final int API_SERVICE_ON_DOWNLOAD_COMPLETED =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_DOWNLOAD_COMPLETED;
    public static final int API_SERVICE_ON_RENDER =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_RENDER;
    public static final int API_SERVICE_ON_EVENT =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_EVENT;
    public static final int API_SERVICE_ON_TRAINING_EXAMPLE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_TRAINING_EXAMPLE;
    public static final int API_SERVICE_ON_WEB_TRIGGER =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__SERVICE_ON_WEB_TRIGGER;
    public static final int API_REMOTE_DATA_GET =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_GET;
    public static final int API_REMOTE_DATA_KEYSET =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__REMOTE_DATA_KEYSET;
    public static final int API_LOCAL_DATA_GET =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_GET;
    public static final int API_LOCAL_DATA_KEYSET =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_KEYSET;
    public static final int API_LOCAL_DATA_PUT =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_PUT;
    public static final int API_LOCAL_DATA_REMOVE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOCAL_DATA_REMOVE;
    public static final int API_EVENT_URL_CREATE_WITH_RESPONSE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_RESPONSE;
    public static final int API_EVENT_URL_CREATE_WITH_REDIRECT =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__EVENT_URL_CREATE_WITH_REDIRECT;
    public static final int API_LOG_READER_GET_REQUESTS =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_REQUESTS;
    public static final int API_LOG_READER_GET_JOINED_EVENTS =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__LOG_READER_GET_JOINED_EVENTS;
    public static final int API_FEDERATED_COMPUTE_SCHEDULE =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__FEDERATED_COMPUTE_SCHEDULE;
    public static final int API_MODEL_MANAGER_RUN =
            ON_DEVICE_PERSONALIZATION_API_CALLED__API_NAME__MODEL_MANAGER_RUN;

    private int mApiClass = ON_DEVICE_PERSONALIZATION_API_CALLED__API_CLASS__UNKNOWN;
    private final @Api int mApiName;
    private int mLatencyMillis = 0;
    private int mResponseCode = 0;
    private int mOverheadLatencyMillis = 0;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/statsd/ApiCallStats.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @android.annotation.IntDef(prefix = "API_", value = {
        API_EXECUTE,
        API_REQUEST_SURFACE_PACKAGE,
        API_SERVICE_ON_EXECUTE,
        API_SERVICE_ON_DOWNLOAD_COMPLETED,
        API_SERVICE_ON_RENDER,
        API_SERVICE_ON_EVENT,
        API_SERVICE_ON_TRAINING_EXAMPLE,
        API_SERVICE_ON_WEB_TRIGGER,
        API_REMOTE_DATA_GET,
        API_REMOTE_DATA_KEYSET,
        API_LOCAL_DATA_GET,
        API_LOCAL_DATA_KEYSET,
        API_LOCAL_DATA_PUT,
        API_LOCAL_DATA_REMOVE,
        API_EVENT_URL_CREATE_WITH_RESPONSE,
        API_EVENT_URL_CREATE_WITH_REDIRECT,
        API_LOG_READER_GET_REQUESTS,
        API_LOG_READER_GET_JOINED_EVENTS,
        API_FEDERATED_COMPUTE_SCHEDULE,
        API_MODEL_MANAGER_RUN
    })
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface Api {}

    @DataClass.Generated.Member
    public static String apiToString(@Api int value) {
        switch (value) {
            case API_EXECUTE:
                    return "API_EXECUTE";
            case API_REQUEST_SURFACE_PACKAGE:
                    return "API_REQUEST_SURFACE_PACKAGE";
            case API_SERVICE_ON_EXECUTE:
                    return "API_SERVICE_ON_EXECUTE";
            case API_SERVICE_ON_DOWNLOAD_COMPLETED:
                    return "API_SERVICE_ON_DOWNLOAD_COMPLETED";
            case API_SERVICE_ON_RENDER:
                    return "API_SERVICE_ON_RENDER";
            case API_SERVICE_ON_EVENT:
                    return "API_SERVICE_ON_EVENT";
            case API_SERVICE_ON_TRAINING_EXAMPLE:
                    return "API_SERVICE_ON_TRAINING_EXAMPLE";
            case API_SERVICE_ON_WEB_TRIGGER:
                    return "API_SERVICE_ON_WEB_TRIGGER";
            case API_REMOTE_DATA_GET:
                    return "API_REMOTE_DATA_GET";
            case API_REMOTE_DATA_KEYSET:
                    return "API_REMOTE_DATA_KEYSET";
            case API_LOCAL_DATA_GET:
                    return "API_LOCAL_DATA_GET";
            case API_LOCAL_DATA_KEYSET:
                    return "API_LOCAL_DATA_KEYSET";
            case API_LOCAL_DATA_PUT:
                    return "API_LOCAL_DATA_PUT";
            case API_LOCAL_DATA_REMOVE:
                    return "API_LOCAL_DATA_REMOVE";
            case API_EVENT_URL_CREATE_WITH_RESPONSE:
                    return "API_EVENT_URL_CREATE_WITH_RESPONSE";
            case API_EVENT_URL_CREATE_WITH_REDIRECT:
                    return "API_EVENT_URL_CREATE_WITH_REDIRECT";
            case API_LOG_READER_GET_REQUESTS:
                    return "API_LOG_READER_GET_REQUESTS";
            case API_LOG_READER_GET_JOINED_EVENTS:
                    return "API_LOG_READER_GET_JOINED_EVENTS";
            case API_FEDERATED_COMPUTE_SCHEDULE:
                    return "API_FEDERATED_COMPUTE_SCHEDULE";
            case API_MODEL_MANAGER_RUN:
                    return "API_MODEL_MANAGER_RUN";
            default: return Integer.toHexString(value);
        }
    }

    @DataClass.Generated.Member
    /* package-private */ ApiCallStats(
            int apiClass,
            @Api int apiName,
            int latencyMillis,
            int responseCode,
            int overheadLatencyMillis) {
        this.mApiClass = apiClass;
        this.mApiName = apiName;

        if (!(mApiName == API_EXECUTE)
                && !(mApiName == API_REQUEST_SURFACE_PACKAGE)
                && !(mApiName == API_SERVICE_ON_EXECUTE)
                && !(mApiName == API_SERVICE_ON_DOWNLOAD_COMPLETED)
                && !(mApiName == API_SERVICE_ON_RENDER)
                && !(mApiName == API_SERVICE_ON_EVENT)
                && !(mApiName == API_SERVICE_ON_TRAINING_EXAMPLE)
                && !(mApiName == API_SERVICE_ON_WEB_TRIGGER)
                && !(mApiName == API_REMOTE_DATA_GET)
                && !(mApiName == API_REMOTE_DATA_KEYSET)
                && !(mApiName == API_LOCAL_DATA_GET)
                && !(mApiName == API_LOCAL_DATA_KEYSET)
                && !(mApiName == API_LOCAL_DATA_PUT)
                && !(mApiName == API_LOCAL_DATA_REMOVE)
                && !(mApiName == API_EVENT_URL_CREATE_WITH_RESPONSE)
                && !(mApiName == API_EVENT_URL_CREATE_WITH_REDIRECT)
                && !(mApiName == API_LOG_READER_GET_REQUESTS)
                && !(mApiName == API_LOG_READER_GET_JOINED_EVENTS)
                && !(mApiName == API_FEDERATED_COMPUTE_SCHEDULE)
                && !(mApiName == API_MODEL_MANAGER_RUN)) {
            throw new java.lang.IllegalArgumentException(
                    "apiName was " + mApiName + " but must be one of: "
                            + "API_EXECUTE(" + API_EXECUTE + "), "
                            + "API_REQUEST_SURFACE_PACKAGE(" + API_REQUEST_SURFACE_PACKAGE + "), "
                            + "API_SERVICE_ON_EXECUTE(" + API_SERVICE_ON_EXECUTE + "), "
                            + "API_SERVICE_ON_DOWNLOAD_COMPLETED(" + API_SERVICE_ON_DOWNLOAD_COMPLETED + "), "
                            + "API_SERVICE_ON_RENDER(" + API_SERVICE_ON_RENDER + "), "
                            + "API_SERVICE_ON_EVENT(" + API_SERVICE_ON_EVENT + "), "
                            + "API_SERVICE_ON_TRAINING_EXAMPLE(" + API_SERVICE_ON_TRAINING_EXAMPLE + "), "
                            + "API_SERVICE_ON_WEB_TRIGGER(" + API_SERVICE_ON_WEB_TRIGGER + "), "
                            + "API_REMOTE_DATA_GET(" + API_REMOTE_DATA_GET + "), "
                            + "API_REMOTE_DATA_KEYSET(" + API_REMOTE_DATA_KEYSET + "), "
                            + "API_LOCAL_DATA_GET(" + API_LOCAL_DATA_GET + "), "
                            + "API_LOCAL_DATA_KEYSET(" + API_LOCAL_DATA_KEYSET + "), "
                            + "API_LOCAL_DATA_PUT(" + API_LOCAL_DATA_PUT + "), "
                            + "API_LOCAL_DATA_REMOVE(" + API_LOCAL_DATA_REMOVE + "), "
                            + "API_EVENT_URL_CREATE_WITH_RESPONSE(" + API_EVENT_URL_CREATE_WITH_RESPONSE + "), "
                            + "API_EVENT_URL_CREATE_WITH_REDIRECT(" + API_EVENT_URL_CREATE_WITH_REDIRECT + "), "
                            + "API_LOG_READER_GET_REQUESTS(" + API_LOG_READER_GET_REQUESTS + "), "
                            + "API_LOG_READER_GET_JOINED_EVENTS(" + API_LOG_READER_GET_JOINED_EVENTS + "), "
                            + "API_FEDERATED_COMPUTE_SCHEDULE(" + API_FEDERATED_COMPUTE_SCHEDULE + "), "
                            + "API_MODEL_MANAGER_RUN(" + API_MODEL_MANAGER_RUN + ")");
        }

        this.mLatencyMillis = latencyMillis;
        this.mResponseCode = responseCode;
        this.mOverheadLatencyMillis = overheadLatencyMillis;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public int getApiClass() {
        return mApiClass;
    }

    @DataClass.Generated.Member
    public @Api int getApiName() {
        return mApiName;
    }

    @DataClass.Generated.Member
    public int getLatencyMillis() {
        return mLatencyMillis;
    }

    @DataClass.Generated.Member
    public int getResponseCode() {
        return mResponseCode;
    }

    @DataClass.Generated.Member
    public int getOverheadLatencyMillis() {
        return mOverheadLatencyMillis;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(ApiCallStats other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        ApiCallStats that = (ApiCallStats) o;
        //noinspection PointlessBooleanExpression
        return true
                && mApiClass == that.mApiClass
                && mApiName == that.mApiName
                && mLatencyMillis == that.mLatencyMillis
                && mResponseCode == that.mResponseCode
                && mOverheadLatencyMillis == that.mOverheadLatencyMillis;
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mApiClass;
        _hash = 31 * _hash + mApiName;
        _hash = 31 * _hash + mLatencyMillis;
        _hash = 31 * _hash + mResponseCode;
        _hash = 31 * _hash + mOverheadLatencyMillis;
        return _hash;
    }

    /**
     * A builder for {@link ApiCallStats}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static class Builder {

        private int mApiClass;
        private @Api int mApiName;
        private int mLatencyMillis;
        private int mResponseCode;
        private int mOverheadLatencyMillis;

        private long mBuilderFieldsSet = 0L;

        public Builder(
                @Api int apiName) {
            mApiName = apiName;

            if (!(mApiName == API_EXECUTE)
                    && !(mApiName == API_REQUEST_SURFACE_PACKAGE)
                    && !(mApiName == API_SERVICE_ON_EXECUTE)
                    && !(mApiName == API_SERVICE_ON_DOWNLOAD_COMPLETED)
                    && !(mApiName == API_SERVICE_ON_RENDER)
                    && !(mApiName == API_SERVICE_ON_EVENT)
                    && !(mApiName == API_SERVICE_ON_TRAINING_EXAMPLE)
                    && !(mApiName == API_SERVICE_ON_WEB_TRIGGER)
                    && !(mApiName == API_REMOTE_DATA_GET)
                    && !(mApiName == API_REMOTE_DATA_KEYSET)
                    && !(mApiName == API_LOCAL_DATA_GET)
                    && !(mApiName == API_LOCAL_DATA_KEYSET)
                    && !(mApiName == API_LOCAL_DATA_PUT)
                    && !(mApiName == API_LOCAL_DATA_REMOVE)
                    && !(mApiName == API_EVENT_URL_CREATE_WITH_RESPONSE)
                    && !(mApiName == API_EVENT_URL_CREATE_WITH_REDIRECT)
                    && !(mApiName == API_LOG_READER_GET_REQUESTS)
                    && !(mApiName == API_LOG_READER_GET_JOINED_EVENTS)
                    && !(mApiName == API_FEDERATED_COMPUTE_SCHEDULE)
                    && !(mApiName == API_MODEL_MANAGER_RUN)) {
                throw new java.lang.IllegalArgumentException(
                        "apiName was " + mApiName + " but must be one of: "
                                + "API_EXECUTE(" + API_EXECUTE + "), "
                                + "API_REQUEST_SURFACE_PACKAGE(" + API_REQUEST_SURFACE_PACKAGE + "), "
                                + "API_SERVICE_ON_EXECUTE(" + API_SERVICE_ON_EXECUTE + "), "
                                + "API_SERVICE_ON_DOWNLOAD_COMPLETED(" + API_SERVICE_ON_DOWNLOAD_COMPLETED + "), "
                                + "API_SERVICE_ON_RENDER(" + API_SERVICE_ON_RENDER + "), "
                                + "API_SERVICE_ON_EVENT(" + API_SERVICE_ON_EVENT + "), "
                                + "API_SERVICE_ON_TRAINING_EXAMPLE(" + API_SERVICE_ON_TRAINING_EXAMPLE + "), "
                                + "API_SERVICE_ON_WEB_TRIGGER(" + API_SERVICE_ON_WEB_TRIGGER + "), "
                                + "API_REMOTE_DATA_GET(" + API_REMOTE_DATA_GET + "), "
                                + "API_REMOTE_DATA_KEYSET(" + API_REMOTE_DATA_KEYSET + "), "
                                + "API_LOCAL_DATA_GET(" + API_LOCAL_DATA_GET + "), "
                                + "API_LOCAL_DATA_KEYSET(" + API_LOCAL_DATA_KEYSET + "), "
                                + "API_LOCAL_DATA_PUT(" + API_LOCAL_DATA_PUT + "), "
                                + "API_LOCAL_DATA_REMOVE(" + API_LOCAL_DATA_REMOVE + "), "
                                + "API_EVENT_URL_CREATE_WITH_RESPONSE(" + API_EVENT_URL_CREATE_WITH_RESPONSE + "), "
                                + "API_EVENT_URL_CREATE_WITH_REDIRECT(" + API_EVENT_URL_CREATE_WITH_REDIRECT + "), "
                                + "API_LOG_READER_GET_REQUESTS(" + API_LOG_READER_GET_REQUESTS + "), "
                                + "API_LOG_READER_GET_JOINED_EVENTS(" + API_LOG_READER_GET_JOINED_EVENTS + "), "
                                + "API_FEDERATED_COMPUTE_SCHEDULE(" + API_FEDERATED_COMPUTE_SCHEDULE + "), "
                                + "API_MODEL_MANAGER_RUN(" + API_MODEL_MANAGER_RUN + ")");
            }

        }

        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setApiClass(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mApiClass = value;
            return this;
        }

        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setApiName(@Api int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mApiName = value;
            return this;
        }

        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setLatencyMillis(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mLatencyMillis = value;
            return this;
        }

        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setResponseCode(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mResponseCode = value;
            return this;
        }

        @DataClass.Generated.Member
        public @android.annotation.NonNull Builder setOverheadLatencyMillis(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mOverheadLatencyMillis = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @android.annotation.NonNull ApiCallStats build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x20; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mApiClass = ON_DEVICE_PERSONALIZATION_API_CALLED__API_CLASS__UNKNOWN;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mLatencyMillis = 0;
            }
            if ((mBuilderFieldsSet & 0x8) == 0) {
                mResponseCode = 0;
            }
            if ((mBuilderFieldsSet & 0x10) == 0) {
                mOverheadLatencyMillis = 0;
            }
            ApiCallStats o = new ApiCallStats(
                    mApiClass,
                    mApiName,
                    mLatencyMillis,
                    mResponseCode,
                    mOverheadLatencyMillis);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x20) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1708986262126L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/src/com/android/ondevicepersonalization/services/statsd/ApiCallStats.java",
            inputSignatures = "public static final  int API_EXECUTE\npublic static final  int API_REQUEST_SURFACE_PACKAGE\npublic static final  int API_SERVICE_ON_EXECUTE\npublic static final  int API_SERVICE_ON_DOWNLOAD_COMPLETED\npublic static final  int API_SERVICE_ON_RENDER\npublic static final  int API_SERVICE_ON_EVENT\npublic static final  int API_SERVICE_ON_TRAINING_EXAMPLE\npublic static final  int API_SERVICE_ON_WEB_TRIGGER\npublic static final  int API_REMOTE_DATA_GET\npublic static final  int API_REMOTE_DATA_KEYSET\npublic static final  int API_LOCAL_DATA_GET\npublic static final  int API_LOCAL_DATA_KEYSET\npublic static final  int API_LOCAL_DATA_PUT\npublic static final  int API_LOCAL_DATA_REMOVE\npublic static final  int API_EVENT_URL_CREATE_WITH_RESPONSE\npublic static final  int API_EVENT_URL_CREATE_WITH_REDIRECT\npublic static final  int API_LOG_READER_GET_REQUESTS\npublic static final  int API_LOG_READER_GET_JOINED_EVENTS\npublic static final  int API_FEDERATED_COMPUTE_SCHEDULE\npublic static final  int API_MODEL_MANAGER_RUN\nprivate  int mApiClass\nprivate final @com.android.ondevicepersonalization.services.statsd.ApiCallStats.Api int mApiName\nprivate  int mLatencyMillis\nprivate  int mResponseCode\nprivate  int mOverheadLatencyMillis\nclass ApiCallStats extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
