/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.display;

import static android.os.UserManager.DISALLOW_CONFIG_BRIGHTNESS;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.enterprise.EnterpriseUtils;

/** Business logic for controlling the adaptive brightness setting. */
public class AdaptiveBrightnessTogglePreferenceController extends
        PreferenceController<TwoStatePreference> {

    private static final Logger LOG =
            new Logger(AdaptiveBrightnessTogglePreferenceController.class);
    private static final Uri ADAPTIVE_BRIGHTNESS_URI = Settings.System.getUriFor(
            Settings.System.SCREEN_BRIGHTNESS_MODE);
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mAdaptiveBrightnessObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            refreshUi();
        }
    };

    public AdaptiveBrightnessTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_BRIGHTNESS)) {
                showActionDisabledByAdminDialog();
            } else {
                Toast.makeText(getContext(),
                        getContext().getString(R.string.action_unavailable),
                        Toast.LENGTH_LONG).show();
                LOG.d(getContext().getString(R.string.action_unavailable));
            }
        });
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();
        getContext().getContentResolver().registerContentObserver(ADAPTIVE_BRIGHTNESS_URI,
                /* notifyForDescendants= */ false, mAdaptiveBrightnessObserver);
    }

    @Override
    protected void onStopInternal() {
        super.onStopInternal();
        getContext().getContentResolver().unregisterContentObserver(mAdaptiveBrightnessObserver);
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(isAdaptiveBrightnessChecked());
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        if (!supportsAdaptiveBrightness()) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (hasUserRestrictionByUm(getContext(), DISALLOW_CONFIG_BRIGHTNESS)
                || hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_BRIGHTNESS)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean enableAdaptiveBrightness = (boolean) newValue;
        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                enableAdaptiveBrightness ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return true;
    }

    private boolean isAdaptiveBrightnessChecked() {
        int brightnessMode = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return brightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
    }

    private boolean supportsAdaptiveBrightness() {
        return getContext().getResources().getBoolean(R.bool.config_automatic_brightness_available);
    }

    private void showActionDisabledByAdminDialog() {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        DISALLOW_CONFIG_BRIGHTNESS),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
