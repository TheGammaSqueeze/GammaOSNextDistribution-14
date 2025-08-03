/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.v34;

import static android.Manifest.permission_group.LOCATION;
import static android.content.Intent.EXTRA_PERMISSION_GROUP_NAME;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static androidx.core.util.Preconditions.checkStringNotEmpty;

import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ACCOUNT_MANAGEMENT;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ADVERTISING;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ANALYTICS;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_APP_FUNCTIONALITY;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_DEVELOPER_COMMUNICATIONS;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_FRAUD_PREVENTION_SECURITY;
import static com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_PERSONALIZATION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__CANCEL;
import static com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModel.APP_PERMISSION_REQUEST_CODE;
import static com.android.permissioncontroller.permission.ui.v34.PermissionRationaleViewHandler.Result.CANCELLED;

import android.content.Intent;
import android.content.res.Resources;
import android.icu.lang.UCharacter;
import android.os.Build;
import android.os.Bundle;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.util.Preconditions;

import com.android.permission.safetylabel.DataPurposeConstants.Purpose;
import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity;
import com.android.permissioncontroller.permission.ui.SettingsActivity;
import com.android.permissioncontroller.permission.ui.handheld.v34.PermissionRationaleViewHandlerImpl;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModel;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModel.ActivityResultCallback;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModel.PermissionRationaleInfo;
import com.android.permissioncontroller.permission.ui.model.v34.PermissionRationaleViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * An activity which displays runtime permission rationale on behalf of an app. This activity is
 * based on GrantPermissionActivity to keep view behavior and theming consistent.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class PermissionRationaleActivity extends SettingsActivity implements
        PermissionRationaleViewHandler.ResultListener {

    private static final String LOG_TAG = PermissionRationaleActivity.class.getSimpleName();

    private static final String KEY_SESSION_ID = PermissionRationaleActivity.class.getName()
            + "_SESSION_ID";

    /**
     * [Annotation] key for span annotations replacement within the permission rationale purposes
     * string resource
     */
    public static final String ANNOTATION_ID_KEY = "id";
    /**
     * [Annotation] id value for span annotations replacement of link annotations within the
     * permission rationale purposes string resource
     */
    public static final String LINK_ANNOTATION_ID = "link";
    /**
     * [Annotation] id value for span annotations replacement of install source annotations within
     * the permission rationale purposes string resource
     */
    public static final String INSTALL_SOURCE_ANNOTATION_ID = "install_source";
    /**
     * [Annotation] id value for span annotations replacement of purpose list annotations within
     * the permission rationale purposes string resource
     */
    public static final String PURPOSE_LIST_ANNOTATION_ID = "purpose_list";
    /**
     * [Annotation] id value for span annotations replacement of permission name annotations within
     * the permission rationale purposes string resource
     */
    public static final String PERMISSION_NAME_ANNOTATION_ID = "permission_name";

    /**
     * key to the boolean if to show settings_section on the permission rationale dialog provide via
     * intent extra
     */
    public static final String EXTRA_SHOULD_SHOW_SETTINGS_SECTION =
            "com.android.permissioncontroller.extra.SHOULD_SHOW_SETTINGS_SECTION";

    // Data class defines these values in a different natural order. Swap advertising and fraud
    // prevention order for display in permission rationale dialog
    private static final List<Integer> ORDERED_PURPOSES = Arrays.asList(
            PURPOSE_APP_FUNCTIONALITY,
            PURPOSE_ANALYTICS,
            PURPOSE_DEVELOPER_COMMUNICATIONS,
            PURPOSE_ADVERTISING,
            PURPOSE_FRAUD_PREVENTION_SECURITY,
            PURPOSE_PERSONALIZATION,
            PURPOSE_ACCOUNT_MANAGEMENT
    );

    /** Comparator used to update purpose order to expected display order */
    private static final Comparator<Integer> ORDERED_PURPOSE_COMPARATOR =
            Comparator.comparingInt(purposeInt -> ORDERED_PURPOSES.indexOf(purposeInt));

    /** Unique Id of a request. Inherited from GrantPermissionDialog if provide via intent extra */
    private long mSessionId;
    /** Package that shall have permissions granted */
    private String mTargetPackage;
    /** The permission group that initiated the permission rationale details activity */
    private String mPermissionGroupName;
    /** The permission rationale info resulting from the specified permission and group */
    private PermissionRationaleInfo mPermissionRationaleInfo;

    private PermissionRationaleViewHandler mViewHandler;
    private PermissionRationaleViewModel mViewModel;

    private float mOriginalDimAmount;
    private View mRootView;


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (!KotlinUtils.INSTANCE.isPermissionRationaleEnabled()) {
            Log.e(
                    LOG_TAG,
                    "Permission rationale feature disabled");
            finishAfterTransition();
            return;
        }

        if (icicle == null) {
            mSessionId =
                    getIntent().getLongExtra(Constants.EXTRA_SESSION_ID, new Random().nextLong());
        } else {
            mSessionId = icicle.getLong(KEY_SESSION_ID);
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mPermissionGroupName = getIntent().getStringExtra(EXTRA_PERMISSION_GROUP_NAME);
        if (mPermissionGroupName == null) {
            Log.e(
                    LOG_TAG,
                    "null EXTRA_PERMISSION_GROUP_NAME. Must be set for permission rationale");
            finishAfterTransition();
            return;
        }

        mTargetPackage = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (mTargetPackage == null) {
            Log.e(LOG_TAG, "null EXTRA_PACKAGE_NAME. Must be set for permission rationale");
            finishAfterTransition();
            return;
        }

        setFinishOnTouchOutside(false);

        setTitle(getTitleResIdForPermissionGroup(mPermissionGroupName));

        if (DeviceUtils.isTelevision(this)
                || DeviceUtils.isWear(this)
                || DeviceUtils.isAuto(this)) {
            finishAfterTransition();
        } else {
            boolean shouldShowSettingsSection =
                    getIntent().getBooleanExtra(EXTRA_SHOULD_SHOW_SETTINGS_SECTION, true);
            mViewHandler = new PermissionRationaleViewHandlerImpl(this, this,
                    shouldShowSettingsSection);
        }

        PermissionRationaleViewModelFactory factory = new PermissionRationaleViewModelFactory(
                getApplication(), mTargetPackage, mPermissionGroupName, mSessionId, icicle);
        mViewModel = factory.create(PermissionRationaleViewModel.class);
        mViewModel.getPermissionRationaleInfoLiveData()
                .observe(this, this::onPermissionRationaleInfoLoad);

        mRootView = mViewHandler.createView();
        mRootView.setVisibility(View.GONE);
        setContentView(mRootView);
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        mOriginalDimAmount = layoutParams.dimAmount;
        window.setAttributes(layoutParams);

        if (getResources().getBoolean(R.bool.config_useWindowBlur)) {
            java.util.function.Consumer<Boolean> blurEnabledListener = enabled -> {
                mViewHandler.onBlurEnabledChanged(window, enabled);
            };
            mRootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    window.getWindowManager().addCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    window.getWindowManager().removeCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }
            });
        }
        // Restore UI state after lifecycle events. This has to be before we show the first request,
        // as the UI behaves differently for updates and initial creations.
        if (icicle != null) {
            mViewHandler.loadInstanceState(icicle);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mViewHandler == null) {
            return;
        }

        mViewHandler.saveInstanceState(outState);

        outState.putLong(KEY_SESSION_ID, mSessionId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResultCallback callback = mViewModel.getActivityResultCallback();
        if (callback == null || (requestCode != APP_PERMISSION_REQUEST_CODE)) {
            return;
        }
        boolean shouldFinishActivity = callback.shouldFinishActivityForResult(data);
        mViewModel.setActivityResultCallback(null);

        if (shouldFinishActivity) {
            setResultAndFinish(data);
        }
    }

    private void setResultAndFinish(Intent result) {
        setResult(RESULT_OK, result);
        finishAfterTransition();
    }

    @Override
    public void onBackPressed() {
        if (mViewHandler == null) {
            return;
        }
        mViewHandler.onBackPressed();
    }

    // LINT.IfChange(dispatchTouchEvent)
    /**
     * Used to dismiss dialog when tapping outside of dialog bounds
     * Follows the same logic as GrantPermissionActivity
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View rootView = getWindow().getDecorView();
        if (rootView.getTop() != 0) {
            // We are animating the top view, need to compensate for that in motion events.
            ev.setLocation(ev.getX(), ev.getY() - rootView.getTop());
        }
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        if ((x < 0) || (y < 0) || (x > (rootView.getWidth())) || (y > (rootView.getHeight()))) {
            //TODO b/278783474: We should make this activity a fragment of the base GrantPermissions
            // activity
            GrantPermissionsActivity grantActivity = null;
            synchronized (GrantPermissionsActivity.sCurrentGrantRequests) {
                grantActivity = GrantPermissionsActivity.sCurrentGrantRequests
                        .get(new Pair<>(mTargetPackage, getTaskId()));
            }
            if (grantActivity != null
                    && getIntent().getBooleanExtra(EXTRA_SHOULD_SHOW_SETTINGS_SECTION, false)) {
                grantActivity.finishAfterTransition();
            }
            if (MotionEvent.ACTION_DOWN == ev.getAction()) {
                mViewHandler.onCancelled();
            }
            finishAfterTransition();
        }
        return super.dispatchTouchEvent(ev);
    }
    // LINT.ThenChange(GrantPermissionsActivity.java:dispatchTouchEvent)

    @Override
    public void onPermissionRationaleResult(@Nullable String groupName, int result) {
        if (result == CANCELLED) {
            mViewModel.logPermissionRationaleDialogActionReported(
                    PERMISSION_RATIONALE_DIALOG_ACTION_REPORTED__BUTTON_PRESSED__CANCEL);
            finishAfterTransition();
        }
    }

    private void onPermissionRationaleInfoLoad(PermissionRationaleInfo permissionRationaleInfo) {
        if (!mViewModel.getPermissionRationaleInfoLiveData().isInitialized()) {
            return;
        }

        if (permissionRationaleInfo == null) {
            finishAfterTransition();
            return;
        }

        mPermissionRationaleInfo = permissionRationaleInfo;
        showPermissionRationale();
    }

    private void showPermissionRationale() {
        @StringRes int titleResId = getTitleResIdForPermissionGroup(mPermissionGroupName);
        setTitle(titleResId);
        CharSequence title = getString(titleResId);
        CharSequence dataSharingSourceMessage = getDataSharingSourceMessage();

        CharSequence purposeTitle =
                getString(getPurposeTitleResIdForPermissionGroup(mPermissionGroupName));

        List<Integer> purposeList = new ArrayList<>(mPermissionRationaleInfo.getPurposeSet());
        Collections.sort(purposeList, ORDERED_PURPOSE_COMPARATOR);
        List<String> purposeStringList = purposeList.stream()
                .map(this::getStringForPurpose)
                .collect(Collectors.toList());

        CharSequence purposeMessage =
                createPurposeMessageWithBulletSpan(
                        getText(R.string.permission_rationale_purpose_message),
                        purposeStringList);

        CharSequence learnMoreMessage;
        if (mViewModel.canLinkToHelpCenter(this)) {
            learnMoreMessage = setLink(
                    getText(R.string.permission_rationale_data_sharing_varies_message),
                    getLearnMoreLink()
            );
        } else {
            learnMoreMessage =
                    getText(R.string.permission_rationale_data_sharing_varies_message_without_link);
        }

        String groupName = mPermissionRationaleInfo.getGroupName();
        String permissionGroupLabel =
                KotlinUtils.INSTANCE.getPermGroupLabel(this, groupName).toString();
        CharSequence settingsMessage =
                createSettingsMessageWithSpans(
                        getText(getSettingsMessageResIdForPermissionGroup(groupName)),
                        UCharacter.toLowerCase(permissionGroupLabel),
                        getLinkToSettings()
                );

        mViewHandler.updateUi(
                groupName,
                title,
                dataSharingSourceMessage,
                purposeTitle,
                purposeMessage,
                learnMoreMessage,
                settingsMessage
        );

        getWindow().setDimAmount(mOriginalDimAmount);
        if (mRootView.getVisibility() == View.GONE) {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            manager.hideSoftInputFromWindow(mRootView.getWindowToken(), 0);
            mRootView.setVisibility(View.VISIBLE);
        }
    }

    private CharSequence getDataSharingSourceMessage() {
        if (mPermissionRationaleInfo.isPreloadedApp()) {
            return getText(R.string.permission_rationale_data_sharing_device_manufacturer_message);
        }

        String installSourcePackageName = mPermissionRationaleInfo.getInstallSourcePackageName();
        CharSequence installSourceLabel = mPermissionRationaleInfo.getInstallSourceLabel();
        checkStringNotEmpty(installSourcePackageName,
                "installSourcePackageName cannot be null or empty");
        checkStringNotEmpty(installSourceLabel, "installSourceLabel cannot be null or empty");
        return createDataSharingSourceMessageWithSpans(
                getText(R.string.permission_rationale_data_sharing_source_message),
                installSourceLabel,
                getLinkToAppStore(installSourcePackageName));
    }

    @StringRes
    private int getTitleResIdForPermissionGroup(String permissionGroupName) {
        if (LOCATION.equals(permissionGroupName)) {
            return R.string.permission_rationale_location_title;
        }

        String exceptionString =
                String.format("Permission Rationale does not support %s", permissionGroupName);
        throw new IllegalArgumentException(exceptionString);
    }

    @StringRes
    private int getPurposeTitleResIdForPermissionGroup(String permissionGroupName) {
        if (LOCATION.equals(permissionGroupName)) {
            return R.string.permission_rationale_location_purpose_title;
        }

        String exceptionString =
                String.format("Permission Rationale does not support %s", permissionGroupName);
        throw new IllegalArgumentException(exceptionString);
    }

    /**
     * Returns permission settings message string resource id for the given permission group.
     *
     * <p> Supported permission groups: LOCATION
     *
     * @param permissionGroupName permission group for which to get a message string id
     * @throws IllegalArgumentException if passing unsupported permission group
     */
    @StringRes
    private int getSettingsMessageResIdForPermissionGroup(String permissionGroupName) {
        Preconditions.checkArgument(LOCATION.equals(permissionGroupName),
                "Permission Rationale does not support %s", permissionGroupName);

        return R.string.permission_rationale_permission_settings_message;
    }

    private String getStringForPurpose(@Purpose int purpose) {
        switch (purpose) {
            case PURPOSE_APP_FUNCTIONALITY:
                return getString(R.string.permission_rationale_purpose_app_functionality);
            case PURPOSE_ANALYTICS:
                return getString(R.string.permission_rationale_purpose_analytics);
            case PURPOSE_DEVELOPER_COMMUNICATIONS:
                return getString(R.string.permission_rationale_purpose_developer_communications);
            case PURPOSE_FRAUD_PREVENTION_SECURITY:
                return getString(R.string.permission_rationale_purpose_fraud_prevention_security);
            case PURPOSE_ADVERTISING:
                return getString(R.string.permission_rationale_purpose_advertising);
            case PURPOSE_PERSONALIZATION:
                return getString(R.string.permission_rationale_purpose_personalization);
            case PURPOSE_ACCOUNT_MANAGEMENT:
                return getString(R.string.permission_rationale_purpose_account_management);
            default:
                throw new IllegalArgumentException("Invalid purpose: " + purpose);
        }
    }

    private CharSequence createDataSharingSourceMessageWithSpans(
            CharSequence baseText,
            CharSequence installSourceLabel,
            ClickableSpan link) {
        CharSequence updatedText =
                replaceSpan(baseText, INSTALL_SOURCE_ANNOTATION_ID, installSourceLabel);
        updatedText = setLink(updatedText, link);
        return updatedText;
    }

    private CharSequence createPurposeMessageWithBulletSpan(
            CharSequence baseText,
            List<String> purposesList) {
        Resources res = getResources();
        final int bulletSize =
                res.getDimensionPixelSize(R.dimen.permission_rationale_purpose_list_bullet_radius);
        final int bulletIndent =
                res.getDimensionPixelSize(R.dimen.permission_rationale_purpose_list_bullet_indent);

        final int bulletColor =
                getColor(Utils.getColorResId(this, android.R.attr.textColorSecondary));

        String purposesString = TextUtils.join("\n", purposesList);
        SpannableStringBuilder purposesSpan = SpannableStringBuilder.valueOf(purposesString);
        int spanStart = 0;
        for (int i = 0; i < purposesList.size(); i++) {
            final int length = purposesList.get(i).length();
            purposesSpan.setSpan(new BulletSpan(bulletIndent, bulletColor, bulletSize),
                    spanStart, spanStart + length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanStart += length + 1;
        }
        CharSequence updatedText = replaceSpan(baseText, PURPOSE_LIST_ANNOTATION_ID, purposesSpan);
        return updatedText;
    }

    private CharSequence createSettingsMessageWithSpans(
            CharSequence baseText,
            CharSequence permissionName,
            ClickableSpan link) {
        CharSequence updatedText =
                replaceSpan(baseText, PERMISSION_NAME_ANNOTATION_ID, permissionName);
        updatedText = setLink(updatedText, link);
        return updatedText;
    }

    private CharSequence replaceSpan(
            CharSequence baseText,
            String annotationId,
            CharSequence replacementText) {
        SpannableStringBuilder text = SpannableStringBuilder.valueOf(baseText);
        Annotation[] annotations = text.getSpans(0, text.length(), Annotation.class);

        for (android.text.Annotation annotation : annotations) {
            if (!annotation.getKey().equals(ANNOTATION_ID_KEY)
                    || !annotation.getValue().equals(annotationId)) {
                continue;
            }

            int spanStart = text.getSpanStart(annotation);
            int spanEnd = text.getSpanEnd(annotation);
            text.removeSpan(annotation);
            text.replace(spanStart, spanEnd, replacementText);
            break;
        }

        return text;
    }

    private CharSequence setLink(CharSequence baseText, ClickableSpan link) {
        SpannableStringBuilder text = SpannableStringBuilder.valueOf(baseText);
        Annotation[] annotations = text.getSpans(0, text.length(), Annotation.class);

        for (android.text.Annotation annotation : annotations) {
            if (!annotation.getKey().equals(ANNOTATION_ID_KEY)
                    || !annotation.getValue().equals(LINK_ANNOTATION_ID)) {
                continue;
            }

            int spanStart = text.getSpanStart(annotation);
            int spanEnd = text.getSpanEnd(annotation);
            text.removeSpan(annotation);
            text.setSpan(link, spanStart, spanEnd, 0);
            break;
        }

        return text;
    }

    private ClickableSpan getLinkToAppStore(String installSourcePackageName) {
        boolean canLinkToAppStore = mViewModel
                .canLinkToAppStore(PermissionRationaleActivity.this, installSourcePackageName);
        if (!canLinkToAppStore) {
            return null;
        }
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // TODO(b/259961958): metrics for click events
                mViewModel.sendToAppStore(PermissionRationaleActivity.this,
                        installSourcePackageName);
            }
        };
    }

    private ClickableSpan getLinkToSettings() {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // TODO(b/259961958): metrics for click events
                mViewModel.sendToSettingsForPermissionGroup(PermissionRationaleActivity.this,
                        mPermissionGroupName);
            }
        };
    }

    private ClickableSpan getLearnMoreLink() {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // TODO(b/259961958): metrics for click events
                mViewModel.sendToLearnMore(PermissionRationaleActivity.this);
            }
        };
    }
}
