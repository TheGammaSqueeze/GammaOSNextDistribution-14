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
package com.android.systemui.car.privacy;

import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.text.BidiFormatter;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconFactory;
import com.android.systemui.R;
import com.android.systemui.privacy.PrivacyDialog;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link BaseLocalQCProvider} that builds the sensor (such as microphone or camera) privacy
 * panel.
 */
public abstract class SensorQcPanel extends BaseLocalQCProvider
        implements SensorInfoUpdateListener {
    private static final String TAG = "SensorQcPanel";

    private final String mPhoneCallTitle;

    protected Icon mSensorOnIcon;
    protected String mSensorOnTitleText;
    protected Icon mSensorOffIcon;
    protected String mSensorOffTitleText;
    protected String mSensorSubtitleText;

    private SensorPrivacyElementsProvider mSensorPrivacyElementsProvider;
    private SensorInfoProvider mSensorInfoProvider;

    public SensorQcPanel(Context context, SensorInfoProvider infoProvider,
            SensorPrivacyElementsProvider elementsProvider) {
        super(context);
        mSensorInfoProvider = infoProvider;
        mSensorPrivacyElementsProvider = elementsProvider;
        mPhoneCallTitle = context.getString(R.string.ongoing_privacy_dialog_phonecall);
        mSensorOnTitleText = context.getString(R.string.privacy_chip_use_sensor, getSensorName());
        mSensorOffTitleText = context.getString(R.string.privacy_chip_off_content,
                getSensorNameWithFirstLetterCapitalized());
        mSensorSubtitleText = context.getString(R.string.privacy_chip_use_sensor_subtext);

        mSensorOnIcon = Icon.createWithResource(context, getSensorOnIconResourceId());
        mSensorOffIcon = Icon.createWithResource(context, getSensorOffIconResourceId());
    }

    @Override
    public QCItem getQCItem() {
        if (mSensorInfoProvider == null || mSensorPrivacyElementsProvider == null) {
            return null;
        }

        QCList.Builder listBuilder = new QCList.Builder();
        listBuilder.addRow(createSensorToggleRow(mSensorInfoProvider.isSensorEnabled()));

        List<PrivacyDialog.PrivacyElement> elements =
                mSensorPrivacyElementsProvider.getPrivacyElements();

        List<PrivacyDialog.PrivacyElement> activeElements = elements.stream()
                .filter(PrivacyDialog.PrivacyElement::getActive)
                .collect(Collectors.toList());
        addPrivacyElementsToQcList(listBuilder, activeElements);

        List<PrivacyDialog.PrivacyElement> inactiveElements = elements.stream()
                .filter(privacyElement -> !privacyElement.getActive())
                .collect(Collectors.toList());
        addPrivacyElementsToQcList(listBuilder, inactiveElements);

        return listBuilder.build();
    }

    private Optional<ApplicationInfo> getApplicationInfo(PrivacyDialog.PrivacyElement element) {
        return getApplicationInfo(element.getPackageName(), element.getUserId());
    }

    private Optional<ApplicationInfo> getApplicationInfo(String packageName, int userId) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = mContext.getPackageManager()
                    .getApplicationInfoAsUser(packageName, /* flags= */ 0, userId);
            return Optional.of(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Application info not found for: " + packageName);
            return Optional.empty();
        }
    }

    private QCRow createSensorToggleRow(boolean isMicEnabled) {
        QCActionItem actionItem = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH)
                .setChecked(isMicEnabled)
                .build();
        actionItem.setActionHandler(new SensorToggleActionHandler(mSensorInfoProvider));

        return new QCRow.Builder()
                .setIcon(isMicEnabled ? mSensorOnIcon : mSensorOffIcon)
                .setIconTintable(false)
                .setTitle(isMicEnabled ? mSensorOnTitleText : mSensorOffTitleText)
                .setSubtitle(mSensorSubtitleText)
                .addEndItem(actionItem)
                .build();
    }

    private void addPrivacyElementsToQcList(QCList.Builder listBuilder,
            List<PrivacyDialog.PrivacyElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            PrivacyDialog.PrivacyElement element = elements.get(i);
            Optional<ApplicationInfo> applicationInfo = getApplicationInfo(element);
            if (!applicationInfo.isPresent()) continue;

            String appName = element.getPhoneCall()
                    ? mPhoneCallTitle
                    : getAppLabel(applicationInfo.get(), mContext);

            String title;
            if (element.getActive()) {
                title = mContext.getString(R.string.privacy_chip_app_using_sensor_suffix,
                        appName, getSensorShortName());
            } else {
                if (i == elements.size() - 1) {
                    title = mContext
                            .getString(R.string.privacy_chip_app_recently_used_sensor_suffix,
                                    appName, getSensorShortName());
                } else {
                    title = mContext
                            .getString(R.string.privacy_chip_apps_recently_used_sensor_suffix,
                                    appName, elements.size() - 1 - i, getSensorShortName());
                }
            }

            listBuilder.addRow(new QCRow.Builder()
                    .setIcon(getBadgedIcon(mContext, applicationInfo.get()))
                    .setIconTintable(false)
                    .setTitle(title)
                    .build());

            if (!element.getActive()) return;
        }
    }

    protected String getSensorShortName() {
        return null;
    }

    protected String getSensorName() {
        return null;
    }

    protected String getSensorNameWithFirstLetterCapitalized() {
        return null;
    }

    protected abstract @DrawableRes int getSensorOnIconResourceId();

    protected abstract @DrawableRes int getSensorOffIconResourceId();

    private String getAppLabel(@NonNull ApplicationInfo applicationInfo, @NonNull Context context) {
        return BidiFormatter.getInstance()
                .unicodeWrap(applicationInfo.loadSafeLabel(context.getPackageManager(),
                                /* ellipsizeDip= */ 0,
                                /* flags= */ TextUtils.SAFE_STRING_FLAG_TRIM
                                        | TextUtils.SAFE_STRING_FLAG_FIRST_LINE)
                        .toString());
    }

    @NonNull
    private Icon getBadgedIcon(@NonNull Context context,
            @NonNull ApplicationInfo appInfo) {
        UserHandle user = UserHandle.getUserHandleForUid(appInfo.uid);
        try (IconFactory iconFactory = IconFactory.obtain(context)) {
            Drawable d = iconFactory.createBadgedIconBitmap(
                            appInfo.loadUnbadgedIcon(context.getPackageManager()),
                            new IconFactory.IconOptions()
                                    .setShrinkNonAdaptiveIcons(false)
                                    .setUser(user))
                    .newIcon(context);
            BitmapInfo bitmapInfo = iconFactory.createBadgedIconBitmap(
                    d, new IconFactory.IconOptions()
                            .setShrinkNonAdaptiveIcons(false));
            return Icon.createWithBitmap(bitmapInfo.icon);
        }
    }

    @Override
    public void onSensorPrivacyChanged() {
        notifyChange();
    }

    @Override
    public void onPrivacyItemsChanged() {
        notifyChange();
    }

    @Override
    protected void onSubscribed() {
        mSensorInfoProvider.setSensorInfoUpdateListener(this);
    }

    @Override
    protected void onUnsubscribed() {
        mSensorInfoProvider.setSensorInfoUpdateListener(null);
    }

    /**
     * A helper object that retrieves sensor
     * {@link com.android.systemui.privacy.PrivacyDialog.PrivacyElement} list for
     * {@link SensorQcPanel}
     */
    public interface SensorPrivacyElementsProvider {
        /**
         * @return A list of sensors
         * {@link com.android.systemui.privacy.PrivacyDialog.PrivacyElement}
         */
        List<PrivacyDialog.PrivacyElement> getPrivacyElements();
    }

    /**
     * A helper object that allows the {@link SensorQcPanel} to communicate with
     * {@link android.hardware.SensorPrivacyManager}
     */
    public interface SensorInfoProvider {
        /**
         * @return {@code true} if sensor privacy is not enabled (e.g., microphone/camera is on)
         */
        boolean isSensorEnabled();

        /**
         * Toggles sensor privacy
         */
        void toggleSensor();

        /**
         * Informs {@link SensorQcPanel} to update its state.
         */
        void setNotifyUpdateRunnable(Runnable runnable);

        /**
         * Set the listener to monitor the update.
         */
        void setSensorInfoUpdateListener(SensorInfoUpdateListener listener);
    }

    private static class SensorToggleActionHandler implements QCItem.ActionHandler {
        private final SensorInfoProvider mSensorInfoProvider;

        SensorToggleActionHandler(SensorInfoProvider sensorInfoProvider) {
            this.mSensorInfoProvider = sensorInfoProvider;
        }

        @Override
        public void onAction(@NonNull QCItem item, @NonNull Context context,
                @NonNull Intent intent) {
            mSensorInfoProvider.toggleSensor();
        }
    }
}
