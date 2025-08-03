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

package com.android.tv.settings.library.inputmethod;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Toast;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.InputMethodAndSubtypeUtil;
import com.android.tv.settings.library.settingslib.InputMethodSettingValuesWrapper;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

import java.util.List;

/**
 * Use InputMethodPreferenceController to handle the logic of InputMethodPreference from
 * SettingsLib.
 */
public class InputMethodPreferenceController extends RestrictedPreferenceController {
    private static final String TAG = InputMethodPreferenceController.class.getSimpleName();
    private static final String EMPTY_TEXT = "";
    private static final int NO_WIDGET = 0;

    public interface OnSavePreferenceListener {
        /**
         * Called when this preference needs to be saved its state.
         *
         * Note that this preference is non-persistent and needs explicitly to be saved its state.
         * Because changing one IME state may change other IMEs' state, this is a place to update
         * other IMEs' state as well.
         *
         * @param pref This preference.
         */
        void onSaveInputMethodPreference(InputMethodPreferenceController pref);
    }

    private final InputMethodInfo mImi;
    private final String mTitle;
    private final boolean mHasPriorityInSorting;
    private final OnSavePreferenceListener mOnSaveListener;
    private final InputMethodSettingValuesWrapper mInputMethodSettingValues;
    private final boolean mIsAllowedByOrganization;
    private AlertDialog mDialog = null;
    private boolean mHasSwitch;

    public InputMethodPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager, InputMethodInfo imi,
            CharSequence title,
            boolean isAllowedByOrganization, OnSavePreferenceListener onSaveListener) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
        mImi = imi;
        mTitle = title.toString();
        mIsAllowedByOrganization = isAllowedByOrganization;
        mOnSaveListener = onSaveListener;
        mInputMethodSettingValues = InputMethodSettingValuesWrapper.getInstance(context);
        mHasPriorityInSorting = imi.isSystem()
                && InputMethodAndSubtypeUtil.isValidNonAuxAsciiCapableIme(imi);
        mHasSwitch = true;
    }

    public InputMethodPreferenceController(final Context context, UIUpdateCallback callback,
            int stateIdentifier, PreferenceCompatManager preferenceCompatManager,
            final InputMethodInfo imi,
            final boolean isImeEnabler, final boolean isAllowedByOrganization,
            final InputMethodPreferenceController.OnSavePreferenceListener onSaveListener) {
        this(context, callback, stateIdentifier, preferenceCompatManager, imi,
                imi.loadLabel(context.getPackageManager()),
                isAllowedByOrganization,
                onSaveListener);
        mHasSwitch = isImeEnabler;

    }


    @Override
    public boolean handlePreferenceChange(Object newValue) {
        if (!mHasSwitch) {
            // Prevent disabling an IME because this preference is for invoking a settings activity.
            return true;
        }
        if (mPreferenceCompat.getChecked() == PreferenceCompat.STATUS_ON) {
            // Disable this IME.
            setCheckedInternal(false);
            return true;
        }
        if (mImi.isSystem()) {
            setCheckedInternal(true);
        } else {
            // Once security is confirmed, we might prompt if the IME isn't
            // Direct Boot aware.
            showSecurityWarnDialog();
        }
        return false;
    }

    private void setCheckedInternal(boolean checked) {
        mOnSaveListener.onSaveInputMethodPreference(this);
        mUIUpdateCallback.notifyUpdate(mStateIdentifier, mPreferenceCompat);
    }

    private void showSecurityWarnDialog() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setCancelable(true /* cancelable */);
        builder.setTitle(android.R.string.dialog_alert_title);
        final CharSequence label = mImi.getServiceInfo().applicationInfo.loadLabel(
                mContext.getPackageManager());
        builder.setMessage(ResourcesUtil.getString(mContext, "ime_security_warning", label));
        builder.setPositiveButton(ResourcesUtil.getString(mContext, "ok"),
                (dialog, which) -> setCheckedInternal(true));
        builder.setNegativeButton(ResourcesUtil.getString(mContext, "cancel"), (dialog, which) -> {
            // The user canceled to enable a 3rd party IME.
            setCheckedInternal(false);
        });
        builder.setOnCancelListener((dialog) -> {
            // The user canceled to enable a 3rd party IME.
            setCheckedInternal(false);
        });
        mDialog = builder.create();
        mDialog.show();
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        if (!mDisabledByAdmin) {
            // Always returns true to prevent invoking an intent without catching exceptions.
            if (mHasSwitch) {
                // Prevent invoking a settings activity because this preference is for enabling and
                // disabling an input method.
                return true;
            }
            try {
                final Intent intent = mPreferenceCompat.getIntent();
                if (intent != null) {
                    // Invoke a settings activity of an input method.
                    mContext.startActivity(intent);
                }
            } catch (final ActivityNotFoundException e) {
                Log.d(TAG, "IME's Settings Activity Not Found", e);
                final String message = ResourcesUtil.getString(
                        mContext, "failed_to_open_app_settings_toast",
                        mImi.loadLabel(mContext.getPackageManager()));
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.handlePreferenceTreeClick(status);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void update() {
        mPreferenceCompat.setTitle(mTitle);
        mPreferenceCompat.setType(PreferenceCompat.TYPE_SWITCH);
        final String settingsActivity = mImi.getSettingsActivity();
        if (TextUtils.isEmpty(settingsActivity)) {
            mPreferenceCompat.setIntent(null);
        } else {
            // Set an intent to invoke settings activity of an input method.
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mImi.getPackageName(), settingsActivity);
            mPreferenceCompat.setIntent(intent);
        }
    }

    @Override
    public void init() {
        super.init();
    }

    private boolean isImeEnabler() {
        return mHasSwitch;
    }

    public void updatePreferenceViews() {
        final boolean isAlwaysChecked = mInputMethodSettingValues.isAlwaysCheckedIme(mImi);
        // When this preference has a switch and an input method should be always enabled,
        // this preference should be disabled to prevent accidentally disabling an input method.
        // This preference should also be disabled in case the admin does not allow this input
        // method.
        if (isAlwaysChecked && isImeEnabler()) {
            setDisabledByAdmin(null);
            setEnabled(false);
        } else if (!mIsAllowedByOrganization) {
            RestrictedLockUtils.EnforcedAdmin admin =
                    RestrictedLockUtilsInternal.checkIfInputMethodDisallowed(mContext,
                            mImi.getPackageName(), UserHandle.myUserId());
            setDisabledByAdmin(admin);
        } else {
            setEnabled(true);
        }

        mPreferenceCompat.setChecked(mInputMethodSettingValues.isEnabledImi(mImi));
        if (!isDisabledByAdmin()) {
            mPreferenceCompat.setSummary(getSummaryString());
        }
    }

    private String getSummaryString() {
        final InputMethodManager imm = getInputMethodManager();
        final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(mImi, true);
        return InputMethodAndSubtypeUtil.getSubtypeLocaleNameListAsSentence(
                subtypes, mContext, mImi);
    }

    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return null;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{mImi.getId()};
    }

    public PreferenceCompat getPrefCompat() {
        return mPreferenceCompat;
    }
}
