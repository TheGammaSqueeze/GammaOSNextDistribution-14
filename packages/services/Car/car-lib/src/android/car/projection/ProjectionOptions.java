/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.projection;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityOptions;
import android.car.builtin.app.ActivityManagerHelper;
import android.content.ComponentName;
import android.os.Bundle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class holds OEM customization for projection receiver app.  It is created by Car Service.
 *
 * @hide
 */
@SystemApi
public class ProjectionOptions {
    private static final String KEY_PREFIX = "android.car.projection.";

    /** Immersive full screen mode (all system bars are hidden) */
    public static final int UI_MODE_FULL_SCREEN = 0;

    /** Show status and navigation bars. */
    public static final int UI_MODE_BLENDED = 1;

    private static final int UI_MODE_DEFAULT = UI_MODE_FULL_SCREEN;

    /** @hide */
    @IntDef({UI_MODE_FULL_SCREEN, UI_MODE_BLENDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProjectionUiMode {}

    /** Indicates that head unit didn't specify information about access point mode.  This value
     * can only be seen on Android SDK 31 and below. */
    public static final int AP_MODE_NOT_SPECIFIED = 0;

    /** Projection access point was created such that it may provide Internet access. */
    public static final int AP_MODE_TETHERED = 1;

    /**
     * Projection access point was created as local-only hotspot, without Internet access and the
     * credentials will be auto-generated for every access point initialization.
     */
    public static final int AP_MODE_LOHS_DYNAMIC_CREDENTIALS = 2;

    /**
     * Projection access point was created as local-only hotspot, without Internet access and the
     * credentials will persist reboots.  Credentials still can be reseted by user or app request.
     */
    public static final int AP_MODE_LOHS_STATIC_CREDENTIALS = 3;

    /** @hide */
    @IntDef({AP_MODE_NOT_SPECIFIED, AP_MODE_TETHERED, AP_MODE_LOHS_DYNAMIC_CREDENTIALS,
            AP_MODE_LOHS_STATIC_CREDENTIALS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProjectionAccessPointMode {}

    private static final String KEY_ACTIVITY_OPTIONS = KEY_PREFIX + "activityOptions";
    private static final String KEY_UI_MODE = KEY_PREFIX + "systemUiFlags";
    private static final String KEY_CONSENT_ACTIVITY = KEY_PREFIX + "consentActivity";
    private static final String KEY_ACCESS_POINT_MODE = KEY_PREFIX + "ap_mode";

    private final ActivityOptions mActivityOptions;
    private final int mUiMode;
    private final ComponentName mConsentActivity;
    private final int mApMode;

    /**
     * Creates new instance for given {@code Bundle}
     *
     * @param bundle contains OEM specific information
     */
    public ProjectionOptions(Bundle bundle) {
        Bundle activityOptionsBundle = bundle.getBundle(KEY_ACTIVITY_OPTIONS);
        mActivityOptions = activityOptionsBundle != null
                ? ActivityManagerHelper.createActivityOptions(activityOptionsBundle) : null;
        mUiMode = bundle.getInt(KEY_UI_MODE, UI_MODE_DEFAULT);
        mConsentActivity = bundle.getParcelable(KEY_CONSENT_ACTIVITY);
        mApMode = bundle.getInt(KEY_ACCESS_POINT_MODE, AP_MODE_NOT_SPECIFIED);
    }

    private ProjectionOptions(Builder builder) {
        mActivityOptions = builder.mActivityOptions;
        mUiMode = builder.mUiMode;
        mConsentActivity = builder.mConsentActivity;
        mApMode = builder.mApMode;
    }

    /**
     * Returns combination of flags from View.SYSTEM_UI_FLAG_* which will be used by projection
     * receiver app during rendering.
     */
    public @ProjectionUiMode int getUiMode() {
        return mUiMode;
    }

    /**
     * Returns projection access point mode.
     *
     * <p>The result could be one of the following values:
     * <ul>
     *     <li>{@link #AP_MODE_NOT_SPECIFIED}</li>
     *     <li>{@link #AP_MODE_TETHERED}</li>
     *     <li>{@link #AP_MODE_LOHS_DYNAMIC_CREDENTIALS}</li>
     *     <li>{@link #AP_MODE_LOHS_STATIC_CREDENTIALS}</li>
     * </ul>
     */
    public @ProjectionAccessPointMode int getProjectionAccessPointMode() {
        return mApMode;
    }

    /**
     * Returns {@link ActivityOptions} that needs to be applied when launching projection activity
     */
    public @Nullable ActivityOptions getActivityOptions() {
        return mActivityOptions;
    }

    /**
     * Returns package/activity name of the consent activity provided by OEM which needs to be shown
     * for all mobile devices unless user accepted the consent.
     *
     * <p>If the method returns null then consent dialog should not be shown.
     */
    public @Nullable ComponentName getConsentActivity() {
        return mConsentActivity;
    }

    /** Converts current object to {@link Bundle} */
    public @NonNull Bundle toBundle() {
        Bundle bundle = new Bundle();
        if (mActivityOptions != null) {
            bundle.putBundle(KEY_ACTIVITY_OPTIONS, mActivityOptions.toBundle());
        }
        bundle.putParcelable(KEY_CONSENT_ACTIVITY, mConsentActivity);
        if (mUiMode != UI_MODE_DEFAULT) {
            bundle.putInt(KEY_UI_MODE, mUiMode);
        }
        bundle.putInt(KEY_ACCESS_POINT_MODE, mApMode);
        return bundle;
    }

    /** @hide */
    public static Builder builder() {
        return new Builder();
    }

    /** @hide */
    public static class Builder {
        private ActivityOptions mActivityOptions;
        private int mUiMode = UI_MODE_DEFAULT;
        private ComponentName mConsentActivity;
        private int mApMode = AP_MODE_NOT_SPECIFIED;

        /** Sets {@link ActivityOptions} to launch projection activity. */
        public Builder setProjectionActivityOptions(ActivityOptions activityOptions) {
            mActivityOptions = activityOptions;
            return this;
        }

        /** Set UI for projection activity. It can be one of {@code UI_MODE_*} constants. */
        public Builder setUiMode(@ProjectionUiMode int uiMode) {
            mUiMode = uiMode;
            return this;
        }

        /** Sets consent activity which will be shown before starting projection. */
        public Builder setConsentActivity(ComponentName consentActivity) {
            mConsentActivity = consentActivity;
            return this;
        }

        /** Sets projection access point mode */
        public Builder setAccessPointMode(@ProjectionAccessPointMode int accessPointMode) {
            this.mApMode = accessPointMode;
            return this;
        }

        /** Creates an instance of {@link android.car.projection.ProjectionOptions} */
        public ProjectionOptions build() {
            return new ProjectionOptions(this);
        }
    }

    /** @hide */
    @Override
    public String toString() {
        return toBundle().toString();
    }
}
