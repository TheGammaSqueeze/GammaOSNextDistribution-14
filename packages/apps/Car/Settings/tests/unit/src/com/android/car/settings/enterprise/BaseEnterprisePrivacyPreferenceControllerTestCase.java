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
package com.android.car.settings.enterprise;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.testutils.TextDrawable;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.applications.ApplicationFeatureProvider.ListOfAppsCallback;
import com.android.car.settingslib.applications.ApplicationFeatureProvider.NumberOfAppsCallback;
import com.android.car.settingslib.applications.UserAppInfo;
import com.android.car.settingslib.enterprise.EnterpriseDefaultApps;
import com.android.car.settingslib.enterprise.EnterprisePrivacyFeatureProvider;
import com.android.internal.util.Preconditions;

import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Base test for all controllers in the enterprise privacy / managed device info screen
 */
abstract class BaseEnterprisePrivacyPreferenceControllerTestCase
        extends BaseEnterprisePreferenceControllerTestCase {

    private static final String TAG = BaseEnterprisePrivacyPreferenceControllerTestCase.class
            .getSimpleName();

    private static final UserInfo DEFAULT_USER_INFO = new UserInfo(42, "Groot", /* flags= */ 0);

    private static final Map<CharSequence, Drawable> ICONS_BY_LABEL = new ArrayMap<>();

    @Mock
    protected ApplicationFeatureProvider mApplicationFeatureProvider;

    @Mock
    protected EnterprisePrivacyFeatureProvider mEnterprisePrivacyFeatureProvider;

    protected void verifyFindPersistentPreferredActivitiesCalledOnce() {
        verify(mApplicationFeatureProvider, times(EnterpriseDefaultApps.values().length))
                .findPersistentPreferredActivities(anyInt(), any());
    }

    protected ArgumentMatcher<Intent[]> matchesIntents(Intent[] intents) {
        return (Intent[] actualIntents) -> {
            if (actualIntents == null) {
                return false;
            }
            if (actualIntents.length != intents.length) {
                return false;
            }
            for (int i = 0; i < intents.length; i++) {
                if (!intents[i].filterEquals(actualIntents[i])) {
                    return false;
                }
            }
            return true;
        };
    }

    protected void assertPreferenceStateSet(Preference preference, int count) {
        String expectedSummary = mRealContext.getResources().getQuantityString(
                R.plurals.enterprise_privacy_number_packages_lower_bound, count, count);
        verifyPreferenceTitleNeverSet(preference);
        verifyPreferenceSummarySet(preference, expectedSummary);
        verifyPreferenceIconNeverSet(preference);
    }

    protected void assertPreferenceGroupStateSet(PreferenceGroup preferenceGroup,
            CharSequence...appLabels) {
        assertWithMessage("preference count")
                .that(preferenceGroup.getPreferenceCount()).isEqualTo(appLabels.length);

        for (int i = 0; i < appLabels.length; i++) {
            Preference pref = preferenceGroup.getPreference(i);
            CharSequence label = appLabels[i];
            assertWithMessage("title at index %s", i).that(pref.getTitle()).isEqualTo(label);
            Drawable icon = getIcon(label);
            assertWithMessage("icon at index %s", i).that(pref.getIcon()).isEqualTo(icon);
            assertWithMessage("order at index %s", i).that(pref.getOrder()).isEqualTo(i);
            assertWithMessage("selectable at index %s", i).that(pref.isSelectable()).isFalse();
        }
    }

    protected void expectUiRefreshed(PreferenceController controller) {
        doNothing().when(controller).refreshUi();
    }

    protected void assertUiRefreshed(PreferenceController controller) {
        verify(controller).refreshUi();
    }

    protected void assertUiNotRefreshed(PreferenceController controller) {
        verify(controller, never()).refreshUi();
    }

    protected UserAppInfo newUserAppInfo(CharSequence label) {
        ApplicationInfo appInfo = new ApplicationInfo() {
            @Override
            public CharSequence loadLabel(PackageManager pm) {
                return label;
            }

            @Override
            public Drawable loadIcon(PackageManager pm) {
                return getIcon(label);
            };

        };
        return new UserAppInfo(DEFAULT_USER_INFO, appInfo);
    }

    protected Drawable getIcon(CharSequence label) {
        Drawable icon = ICONS_BY_LABEL.get(label);
        if (icon != null) {
            Log.d(TAG, "getIcon(" + label + "): returning existing icon " + icon);
            return icon;
        }
        icon = new TextDrawable(label);
        ICONS_BY_LABEL.put(label, icon);
        Log.d(TAG, "getIcon(" + label + "): returning new icon " + icon);
        return icon;
    }

    static final class ListOfAppsCallbackHolder {
        @Nullable
        private ApplicationFeatureProvider.ListOfAppsCallback mCallback;

        void release(UserAppInfo... result) {
            Preconditions.checkState(mCallback != null, "release() called before setCallback()");
            Log.d(TAG, "setting result to " + Arrays.toString(result) + " and releasing latch on"
                    + Thread.currentThread());
            mCallback.onListOfAppsResult(Arrays.asList(result));
        }

        void setCallback(ListOfAppsCallback callback) {
            Log.d(TAG, "setting callback to "  + callback);
            mCallback = Objects.requireNonNull(callback, "callback cannot be null");
        }
    }

    static final class NumberOfAppsCallbackHolder {
        @Nullable
        private NumberOfAppsCallback mCallback;

        void release(int result) {
            Preconditions.checkState(mCallback != null, "release() called before setCallback()");
            Log.d(TAG, "setting result to " + result + " and releasing latch on"
                    + Thread.currentThread());
            mCallback.onNumberOfAppsResult(result);
        }

        void setCallback(NumberOfAppsCallback callback) {
            Log.d(TAG, "setting callback to "  + callback);
            mCallback = Objects.requireNonNull(callback, "callback cannot be null");
        }
    }
}
