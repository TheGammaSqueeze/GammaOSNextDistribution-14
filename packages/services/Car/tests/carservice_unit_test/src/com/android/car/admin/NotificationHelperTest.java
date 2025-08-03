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
package com.android.car.admin;

import static android.app.Notification.EXTRA_TEXT;
import static android.app.Notification.EXTRA_TITLE;
import static android.app.Notification.FLAG_ONGOING_EVENT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;

import static com.android.car.admin.NotificationHelper.CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION;
import static com.android.car.admin.NotificationHelper.CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS;
import static com.android.car.admin.NotificationHelper.CHANNEL_ID_DEFAULT;
import static com.android.car.admin.NotificationHelper.INTENT_EXTRA_NOTIFICATION_ID;
import static com.android.car.admin.NotificationHelper.NEW_USER_DISCLAIMER_NOTIFICATION_ID;
import static com.android.car.admin.NotificationHelper.newNotificationBuilder;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SparseArray;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.R;
import com.android.car.admin.ui.ManagedDeviceTextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@RunWith(MockitoJUnitRunner.class)
public final class NotificationHelperTest {
    private static final long TIMEOUT_MS = 1_000;
    private static final String APP_SUFFIX = ".app";

    private final Context mRealContext = InstrumentationRegistry.getInstrumentation()
            .getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final Map<String, ApplicationInfo> mApplicationInfosByUserPackage =
            new ArrayMap<>();

    private Context mSpiedContext;

    private  NotificationHelper mNotificationHelper;

    @Mock private PackageManager mMockPackageManager;
    @Mock private NotificationManager mMockNotificationManager;

    @Captor private ArgumentCaptor<Notification> mNotificationCaptor;
    @Captor private ArgumentCaptor<Integer> mIntCaptor;

    @Before
    public void setup() throws Exception {
        mSpiedContext = spy(mRealContext);
        when(mSpiedContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mSpiedContext.getSystemService(NotificationManager.class))
                .thenReturn(mMockNotificationManager);
        mockPackageManager();
        mNotificationHelper = new NotificationHelper(mSpiedContext);
    }

    @Test
    public void testNewNotificationBuilder_nullContext() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> newNotificationBuilder(/* context= */ null, IMPORTANCE_HIGH));

        assertWithMessage("exception message").that(exception.getMessage()).contains("context");
    }

    @Test
    public void testCancelNotificationAsUser() {
        UserHandle userHandle = UserHandle.of(100);

        mNotificationHelper.cancelNotificationAsUser(userHandle, /* notificationId= */ 150);

        verify(mMockNotificationManager).cancelAsUser(NotificationHelper.TAG, /* id= */ 150,
                userHandle);
    }

    @Test
    public void testShowUserDisclaimerNotification() {
        int userId = 11;
        mNotificationHelper.showUserDisclaimerNotification(UserHandle.of(userId));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(mMockNotificationManager).notifyAsUser(eq(NotificationHelper.TAG),
                eq(NEW_USER_DISCLAIMER_NOTIFICATION_ID), captor.capture(),
                eq(UserHandle.of(userId)));

        Notification notification = captor.getValue();
        assertWithMessage("notification").that(notification).isNotNull();
        assertNotificationContents(notification);
    }

    @Test
    public void testCancelUserDisclaimerNotification() throws Exception {
        UserHandle user = UserHandle.of(11);
        PendingIntent pendingIntent = NotificationHelper.getPendingUserDisclaimerIntent(
                mSpiedContext, /* extraFlags = */ 0, user);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        pendingIntent.registerCancelListener(pi -> cancelLatch.countDown());

        mNotificationHelper.cancelUserDisclaimerNotification(user);

        verify(mMockNotificationManager).cancelAsUser(NotificationHelper.TAG,
                NEW_USER_DISCLAIMER_NOTIFICATION_ID, user);

        // Assert pending intent was canceled (latch is counted down by the CancelListener)
        JavaMockitoHelper.await(cancelLatch, TIMEOUT_MS);
    }

    @Test
    public void testShowResourceOveruseNotificationsAsUser() throws Exception {
        UserHandle userHandle = UserHandle.of(100);

        SparseArray<String> expectedHeadsUpPackagesById = new SparseArray<>();
        expectedHeadsUpPackagesById.put(169, "system_package.A");
        expectedHeadsUpPackagesById.put(150, "vendor_package.A");
        expectedHeadsUpPackagesById.put(151, "third_party_package.A");

        SparseArray<String> expectedNotificationCenterPackagesById = new SparseArray<>();
        expectedNotificationCenterPackagesById.put(152, "system_package.B");
        expectedNotificationCenterPackagesById.put(153, "vendor_package.B");
        expectedNotificationCenterPackagesById.put(154, "third_party_package.B");

        injectApplicationInfos(List.of(
                constructApplicationInfo("system_package.A", UserHandle.getUid(100, 1000),
                        ApplicationInfo.FLAG_SYSTEM),
                constructApplicationInfo("vendor_package.A", UserHandle.getUid(100, 1001),
                        ApplicationInfo.FLAG_SYSTEM),
                constructApplicationInfo("third_party_package.A",
                        UserHandle.getUid(100, 1002), /* infoFlags= */ 0),
                constructApplicationInfo("system_package.B", UserHandle.getUid(100, 2000),
                        ApplicationInfo.FLAG_SYSTEM),
                constructApplicationInfo("vendor_package.B", UserHandle.getUid(100, 2001),
                        ApplicationInfo.FLAG_SYSTEM),
                constructApplicationInfo("third_party_package.B",
                        UserHandle.getUid(100, 2002), /* infoFlags= */ 0)));

        mNotificationHelper.showResourceOveruseNotificationsAsUser(userHandle,
                expectedHeadsUpPackagesById, expectedNotificationCenterPackagesById);

        SparseArray<Notification> expectedNotificationsById = new SparseArray<>();
        expectedNotificationsById.put(169, constructNotification(userHandle, "system_package.A",
                /* notificationId= */ 169, NotificationManager.IMPORTANCE_HIGH));
        expectedNotificationsById.put(150, constructNotification(userHandle, "vendor_package.A",
                /* notificationId= */ 150, NotificationManager.IMPORTANCE_HIGH));
        expectedNotificationsById.put(151, constructNotification(userHandle,
                "third_party_package.A", /* notificationId= */ 151,
                NotificationManager.IMPORTANCE_HIGH));
        expectedNotificationsById.put(152, constructNotification(userHandle, "system_package.B",
                /* notificationId= */ 152, NotificationManager.IMPORTANCE_DEFAULT));
        expectedNotificationsById.put(153, constructNotification(userHandle, "vendor_package.B",
                /* notificationId= */ 153, NotificationManager.IMPORTANCE_DEFAULT));
        expectedNotificationsById.put(154, constructNotification(userHandle,
                "third_party_package.B", /* notificationId= */ 154,
                NotificationManager.IMPORTANCE_DEFAULT));

        captureAndVerifyUserNotifications(expectedNotificationsById, userHandle);
    }

    private void assertNotificationContents(Notification notification) {
        assertWithMessage("notification icon").that(notification.getSmallIcon()).isNotNull();
        assertWithMessage("notification channel").that(notification.getChannelId())
                .isEqualTo(CHANNEL_ID_DEFAULT);
        assertWithMessage("notification flags has FLAG_ONGOING_EVENT")
                .that(notification.flags & FLAG_ONGOING_EVENT).isEqualTo(FLAG_ONGOING_EVENT);

        assertWithMessage("notification content pending intent")
                .that(notification.contentIntent)
                .isNotNull();
        assertWithMessage("notification content pending intent is immutable")
                .that(notification.contentIntent.isImmutable()).isTrue();
        // Need android.permission.GET_INTENT_SENDER_INTENT to get the Intent
        Intent intent;
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            intent = notification.contentIntent.getIntent();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        assertWithMessage("content intent").that(intent).isNotNull();
        assertWithMessage("content intent component").that(intent.getComponent())
                .isEqualTo(ComponentName.unflattenFromString(mRealContext.getString(
                        com.android.car.R.string.config_newUserDisclaimerActivity
                )));

        assertWithMessage("notification extras").that(notification.extras).isNotNull();
        assertWithMessage("value of extra %s", EXTRA_TITLE)
                .that(notification.extras.getString(EXTRA_TITLE))
                .isEqualTo(mRealContext.getString(R.string.new_user_managed_notification_title));
        assertWithMessage("value of extra %s", EXTRA_TEXT)
                .that(notification.extras.getString(EXTRA_TEXT))
                .isEqualTo(ManagedDeviceTextView.getManagedDeviceText(mRealContext).toString());
    }

    private Notification constructNotification(UserHandle userHandle, String packageName,
            int notificationId, int importance) {
        CharSequence title = TextUtils.expandTemplate(
                mSpiedContext.getText(R.string.resource_overuse_notification_title),
                packageName + APP_SUFFIX);
        String actionTitlePrioritizeApp = mSpiedContext.getString(
                R.string.resource_overuse_notification_button_prioritize_app);
        String contextText =
                mSpiedContext.getString(R.string.resource_overuse_notification_text_disabled_app);
        String negativeActionText = mSpiedContext.getString(
                R.string.resource_overuse_notification_button_close_app);

        PendingIntent positiveActionPendingIntent =
                NotificationHelper.getPendingIntent(mSpiedContext,
                        CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS, userHandle, packageName,
                        notificationId);
        PendingIntent negativeActionPendingIntent = NotificationHelper.getPendingIntent(
                mSpiedContext,
                CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION, userHandle, packageName,
                notificationId);

        return NotificationHelper.newNotificationBuilder(mSpiedContext, importance)
                .setSmallIcon(R.drawable.car_ic_warning)
                .setContentTitle(title)
                .setContentText(contextText)
                .setCategory(Notification.CATEGORY_CAR_WARNING)
                .addAction(new Notification.Action.Builder(/* icon= */ null,
                        negativeActionText, negativeActionPendingIntent).build())
                .addAction(new Notification.Action.Builder(/* icon= */ null,
                        actionTitlePrioritizeApp, positiveActionPendingIntent).build())
                .setDeleteIntent(negativeActionPendingIntent)
                .build();
    }

    private void injectApplicationInfos(
            List<ApplicationInfo> applicationInfos) {
        for (ApplicationInfo applicationInfo : applicationInfos) {
            int userId = UserHandle.getUserId(applicationInfo.uid);
            String userPackageId = userId + ":" + applicationInfo.packageName;
            assertWithMessage("Duplicate application infos provided for user package id: %s",
                    userPackageId).that(mApplicationInfosByUserPackage.containsKey(userPackageId))
                    .isFalse();
            mApplicationInfosByUserPackage.put(userPackageId, applicationInfo);
        }
    }

    private void mockPackageManager() throws Exception {
        when(mMockPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), any()))
                .thenAnswer(args -> {
                    int userId = ((UserHandle) args.getArgument(2)).getIdentifier();
                    String userPackageId = userId + ":" + args.getArgument(0);
                    ApplicationInfo applicationInfo =
                            mApplicationInfosByUserPackage.get(userPackageId);
                    if (applicationInfo == null) {
                        throw new PackageManager.NameNotFoundException(
                                "User package id '" + userPackageId + "' not found");
                    }
                    return applicationInfo;
                });
    }

    private void captureAndVerifyUserNotifications(
            SparseArray<Notification> expectedNotificationsById, UserHandle expectedUser)
            throws Exception {
        verify(mMockNotificationManager, times(expectedNotificationsById.size()))
                .notifyAsUser(eq(NotificationHelper.TAG), mIntCaptor.capture(),
                        mNotificationCaptor.capture(), eq(expectedUser));

        List<Notification> actualNotifications = mNotificationCaptor.getAllValues();
        List<Integer> actualNotificationIds = mIntCaptor.getAllValues();

        for (int i = 0; i < actualNotifications.size(); i++) {
            Notification actual = actualNotifications.get(i);
            int notificationId = actualNotificationIds.get(i);
            isNotificationEqualTo(actual, expectedNotificationsById.get(notificationId),
                    notificationId);
        }
    }

    private void isNotificationEqualTo(Notification actual, Notification expected,
            int notificationId) throws Exception {
        assertWithMessage("Notification for id %s", notificationId).that(actual).isNotNull();
        assertWithMessage("Notification.actions.length for id %s", notificationId)
                .that(actual.actions.length).isEqualTo(expected.actions.length);
        assertWithMessage("Notification.getChannelId() for id %s", notificationId)
                .that(actual.getChannelId()).isEqualTo(expected.getChannelId());
        for (int i = 0; i < actual.actions.length; i++) {
            isNotificationActionEqualTo(actual.actions[i], expected.actions[i], notificationId);
        }
        assertWithMessage("Notification.getSmallIcon().getResId() for id %s", notificationId)
                .that(actual.getSmallIcon().getResId())
                .isEqualTo(expected.getSmallIcon().getResId());
        isIntentEqualTo(getIntent(actual.deleteIntent), getIntent(expected.deleteIntent),
                notificationId);
        isBundleEqualTo(actual.extras, expected.extras, notificationId, Notification.EXTRA_TITLE,
                Notification.EXTRA_TEXT);
    }

    private void isNotificationActionEqualTo(Notification.Action actual,
            Notification.Action expected, int notificationId) throws Exception {
        assertWithMessage("Action.title for id %s", notificationId).that(actual.title.toString())
                .isEqualTo(expected.title.toString());
        assertWithMessage("Action.actionIntent.isImmutable() for id %s", notificationId)
                .that(actual.actionIntent.isImmutable())
                .isEqualTo(expected.actionIntent.isImmutable());
        assertWithMessage("Action.actionIntent for id %s", notificationId).that(actual.actionIntent)
                .isEqualTo(expected.actionIntent);
        isIntentEqualTo(getIntent(actual.actionIntent), getIntent(expected.actionIntent),
                notificationId);
    }

    private void isIntentEqualTo(Intent actual, Intent expected, int notificationId)
            throws Exception {
        assertWithMessage("Intent.getAction() for id %s", notificationId).that(actual.getAction())
                .isEqualTo(expected.getAction());
        assertWithMessage("Intent.getPackage() for id %s", notificationId).that(actual.getPackage())
                .isEqualTo(expected.getPackage());
        assertWithMessage("Intent.getFlags() for id %s", notificationId).that(actual.getFlags())
                .isEqualTo(expected.getFlags());
        isBundleEqualTo(actual.getExtras(), expected.getExtras(), notificationId,
                Intent.EXTRA_PACKAGE_NAME, Intent.EXTRA_USER, INTENT_EXTRA_NOTIFICATION_ID);
    }

    private void isBundleEqualTo(Bundle actual, Bundle expected, int notificationId,
            String... extraNames) throws Exception {
        assertWithMessage("Bundle.getSize() for notification %s", notificationId)
                .that(actual.getSize()).isEqualTo(expected.getSize());
        for (String extraName : extraNames) {
            switch (extraName) {
                case Intent.EXTRA_USER:
                    assertWithMessage(extraName + " for id %s", notificationId)
                            .that((UserHandle) actual.getParcelable(extraName))
                            .isEqualTo(expected.getParcelable(extraName));
                    break;
                case Notification.EXTRA_TITLE:
                case Notification.EXTRA_TEXT:
                    assertWithMessage(extraName + " for id %s", notificationId)
                            .that(actual.getCharSequence(extraName).toString())
                            .isEqualTo(expected.getCharSequence(extraName).toString());
                    break;
                default:
                    assertWithMessage(extraName + " for id %s", notificationId)
                            .that(actual.getString(extraName))
                            .isEqualTo(expected.getString(extraName));
            }
        }
    }

    private Intent getIntent(PendingIntent pendingIntent) {
        Intent intent;
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            intent = pendingIntent.getIntent();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
        return intent;
    }

    private static ApplicationInfo constructApplicationInfo(String pkgName, int pkgUid,
            int infoFlags) {
        return new ApplicationInfo() {{
            name = pkgName + APP_SUFFIX;
            packageName = pkgName;
            uid = pkgUid;
            flags = infoFlags;
        }};
    }
}
