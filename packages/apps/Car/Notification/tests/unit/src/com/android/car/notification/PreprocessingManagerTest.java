/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.notification;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.service.notification.NotificationListenerService.Ranking.USER_SENTIMENT_NEGATIVE;
import static android.service.notification.NotificationListenerService.Ranking.USER_SENTIMENT_NEUTRAL;
import static android.service.notification.NotificationListenerService.Ranking.USER_SENTIMENT_POSITIVE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.SnoozeCriterion;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.testing.TestableContext;
import android.testing.TestableResources;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class PreprocessingManagerTest {

    private static final String PKG = "com.package.PREPROCESSING_MANAGER_TEST";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final long POST_TIME = 12345l;
    private static final UserHandle USER_HANDLE = new UserHandle(12);
    private static final String GROUP_KEY_A = "GROUP_KEY_A";
    private static final String GROUP_KEY_B = "GROUP_KEY_B";
    private static final String GROUP_KEY_C = "GROUP_KEY_C";
    private static final int MAX_STRING_LENGTH = 10;
    private static final int DEFAULT_MIN_GROUPING_THRESHOLD = 4;
    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
    @Mock
    private StatusBarNotification mStatusBarNotification1;
    @Mock
    private StatusBarNotification mStatusBarNotification2;
    @Mock
    private StatusBarNotification mStatusBarNotification3;
    @Mock
    private StatusBarNotification mStatusBarNotification4;
    @Mock
    private StatusBarNotification mStatusBarNotification5;
    @Mock
    private StatusBarNotification mStatusBarNotification6;
    @Mock
    private StatusBarNotification mStatusBarNotification7;
    @Mock
    private StatusBarNotification mStatusBarNotification8;
    @Mock
    private StatusBarNotification mStatusBarNotification9;
    @Mock
    private StatusBarNotification mStatusBarNotification10;
    @Mock
    private StatusBarNotification mStatusBarNotification11;
    @Mock
    private StatusBarNotification mStatusBarNotification12;
    @Mock
    private StatusBarNotification mAdditionalStatusBarNotification;
    @Mock
    private StatusBarNotification mSummaryAStatusBarNotification;
    @Mock
    private StatusBarNotification mSummaryBStatusBarNotification;
    @Mock
    private StatusBarNotification mSummaryCStatusBarNotification;
    @Mock
    private CarUxRestrictions mCarUxRestrictions;
    @Mock
    private CarUxRestrictionManagerWrapper mCarUxRestrictionManagerWrapper;
    @Mock
    private PreprocessingManager.CallStateListener mCallStateListener1;
    @Mock
    private PreprocessingManager.CallStateListener mCallStateListener2;
    @Mock
    private Notification mMediaNotification;
    @Mock
    private Notification mSummaryNotification;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private NotificationDataManager mNotificationDataManager;

    private PreprocessingManager mPreprocessingManager;

    private Notification mForegroundNotification;
    private Notification mBackgroundNotification;
    private Notification mNavigationNotification;

    // Following AlertEntry var names describe the type of notifications they wrap.
    private AlertEntry mLessImportantBackground;
    private AlertEntry mLessImportantForeground;
    private AlertEntry mMedia;
    private AlertEntry mNavigation;
    private AlertEntry mImportantBackground;
    private AlertEntry mImportantForeground;
    private AlertEntry mImportantForeground2;
    private AlertEntry mImportantForeground3;
    private AlertEntry mImportantForeground4;
    private AlertEntry mImportantForeground5;
    private AlertEntry mImportantForeground6;
    private AlertEntry mImportantForeground7;

    private List<AlertEntry> mAlertEntries;
    private Map<String, AlertEntry> mAlertEntriesMap;
    private NotificationListenerService.RankingMap mRankingMap;

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        // prevents less important foreground notifications from not being filtered due to the
        // application and package setup.
        PackageInfo packageInfo = mock(PackageInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        packageInfo.packageName = PKG;
        when(applicationInfo.isPrivilegedApp()).thenReturn(true);
        when(applicationInfo.isSystemApp()).thenReturn(true);
        when(applicationInfo.isSignedWithPlatformKey()).thenReturn(true);
        packageInfo.applicationInfo = applicationInfo;
        when(mPackageManager.getPackageInfoAsUser(anyString(), anyInt(), anyInt())).thenReturn(
                packageInfo);
        mContext.setMockPackageManager(mPackageManager);

        mPreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);

        mForegroundNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        mBackgroundNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        mNavigationNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ true, /* isGroupSummary= */ true);

        when(mMediaNotification.isMediaNotification()).thenReturn(true);

        // Key describes the notification that the StatusBarNotification contains.
        when(mStatusBarNotification1.getKey()).thenReturn("KEY_LESS_IMPORTANT_BACKGROUND");
        when(mStatusBarNotification2.getKey()).thenReturn("KEY_LESS_IMPORTANT_FOREGROUND");
        when(mStatusBarNotification3.getKey()).thenReturn("KEY_MEDIA");
        when(mStatusBarNotification4.getKey()).thenReturn("KEY_NAVIGATION");
        when(mStatusBarNotification5.getKey()).thenReturn("KEY_IMPORTANT_BACKGROUND");
        when(mStatusBarNotification6.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND");
        when(mStatusBarNotification7.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND_2");
        when(mStatusBarNotification8.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND_3");
        when(mStatusBarNotification9.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND_4");
        when(mStatusBarNotification10.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND_5");
        when(mStatusBarNotification11.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND_6");
        when(mStatusBarNotification12.getKey()).thenReturn("KEY_IMPORTANT_FOREGROUND_7");
        when(mSummaryAStatusBarNotification.getKey()).thenReturn("KEY_SUMMARY_A");
        when(mSummaryBStatusBarNotification.getKey()).thenReturn("KEY_SUMMARY_B");
        when(mSummaryCStatusBarNotification.getKey()).thenReturn("KEY_SUMMARY_C");

        when(mStatusBarNotification1.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification2.getGroupKey()).thenReturn(GROUP_KEY_B);
        when(mStatusBarNotification3.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification4.getGroupKey()).thenReturn(GROUP_KEY_B);
        when(mStatusBarNotification5.getGroupKey()).thenReturn(GROUP_KEY_B);
        when(mStatusBarNotification6.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mSummaryAStatusBarNotification.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mSummaryBStatusBarNotification.getGroupKey()).thenReturn(GROUP_KEY_B);
        when(mSummaryCStatusBarNotification.getGroupKey()).thenReturn(GROUP_KEY_C);

        when(mStatusBarNotification1.getNotification()).thenReturn(mBackgroundNotification);
        when(mStatusBarNotification2.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification3.getNotification()).thenReturn(mMediaNotification);
        when(mStatusBarNotification4.getNotification()).thenReturn(mNavigationNotification);
        when(mStatusBarNotification5.getNotification()).thenReturn(mBackgroundNotification);
        when(mStatusBarNotification6.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification7.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification8.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification9.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification10.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification11.getNotification()).thenReturn(mForegroundNotification);
        when(mStatusBarNotification12.getNotification()).thenReturn(mForegroundNotification);
        when(mSummaryAStatusBarNotification.getNotification()).thenReturn(mSummaryNotification);
        when(mSummaryBStatusBarNotification.getNotification()).thenReturn(mSummaryNotification);
        when(mSummaryCStatusBarNotification.getNotification()).thenReturn(mSummaryNotification);

        when(mStatusBarNotification1.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification2.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification3.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification4.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification5.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification6.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification7.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification8.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification9.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification10.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification11.getPackageName()).thenReturn(PKG);
        when(mStatusBarNotification12.getPackageName()).thenReturn(PKG);
        when(mSummaryAStatusBarNotification.getPackageName()).thenReturn(PKG);
        when(mSummaryBStatusBarNotification.getPackageName()).thenReturn(PKG);
        when(mSummaryCStatusBarNotification.getPackageName()).thenReturn(PKG);

        when(mSummaryNotification.isGroupSummary()).thenReturn(true);

        // Always start system with no phone calls in progress.
        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        initTestData(/* includeAdditionalNotifs= */ false);
    }

    @Test
    public void onFilter_showLessImportantNotifications_doesNotFilterNotifications() {
        List<AlertEntry> unfiltered = mAlertEntries.stream().collect(Collectors.toList());
        mPreprocessingManager
                .filter(/* showLessImportantNotifications= */ true, mAlertEntries, mRankingMap);

        assertThat(mAlertEntries.equals(unfiltered)).isTrue();
    }

    @Test
    public void onFilter_dontShowLessImportantNotifications_filtersLessImportantForeground()
            throws PackageManager.NameNotFoundException {
        mPreprocessingManager
                .filter( /* showLessImportantNotifications= */ false, mAlertEntries, mRankingMap);

        assertThat(mAlertEntries.contains(mLessImportantBackground)).isTrue();
        assertThat(mAlertEntries.contains(mLessImportantForeground)).isFalse();
    }

    @Test
    public void onFilter_dontShowLessImportantNotifications_doesNotFilterMoreImportant() {
        mPreprocessingManager
                .filter(/* showLessImportantNotifications= */ false, mAlertEntries, mRankingMap);

        assertThat(mAlertEntries.contains(mImportantBackground)).isTrue();
        assertThat(mAlertEntries.contains(mImportantForeground)).isTrue();
    }

    @Test
    public void onFilter_dontShowLessImportantNotifications_filtersMediaAndNavigation() {
        mPreprocessingManager
                .filter(/* showLessImportantNotifications= */ false, mAlertEntries, mRankingMap);

        assertThat(mAlertEntries.contains(mMedia)).isFalse();
        assertThat(mAlertEntries.contains(mNavigation)).isFalse();
    }

    @Test
    public void onFilter_doShowLessImportantNotifications_doesNotFilterMediaOrNavigation() {
        mPreprocessingManager
                .filter(/* showLessImportantNotifications= */ true, mAlertEntries, mRankingMap);

        assertThat(mAlertEntries.contains(mMedia)).isTrue();
        assertThat(mAlertEntries.contains(mNavigation)).isTrue();
    }

    @Test
    public void onFilter_doShowLessImportantNotifications_filtersCalls() {
        StatusBarNotification callSBN = mock(StatusBarNotification.class);
        Notification callNotification = new Notification();
        callNotification.category = Notification.CATEGORY_CALL;
        when(callSBN.getNotification()).thenReturn(callNotification);
        List<AlertEntry> entries = new ArrayList<>();
        entries.add(new AlertEntry(callSBN));

        mPreprocessingManager.filter(true, entries, mRankingMap);
        assertThat(entries).isEmpty();
    }

    @Test
    public void onFilter_dontShowLessImportantNotifications_filtersCalls() {
        StatusBarNotification callSBN = mock(StatusBarNotification.class);
        Notification callNotification = new Notification();
        callNotification.category = Notification.CATEGORY_CALL;
        when(callSBN.getNotification()).thenReturn(callNotification);
        List<AlertEntry> entries = new ArrayList<>();
        entries.add(new AlertEntry(callSBN));

        mPreprocessingManager.filter(false, entries, mRankingMap);
        assertThat(entries).isEmpty();
    }

    @Test
    public void onOptimizeForDriving_alertEntryHasNonMessageNotification_trimsNotificationTexts() {
        when(mCarUxRestrictions.getMaxRestrictedStringLength()).thenReturn(MAX_STRING_LENGTH);
        when(mCarUxRestrictionManagerWrapper.getCurrentCarUxRestrictions())
                .thenReturn(mCarUxRestrictions);
        mPreprocessingManager.setCarUxRestrictionManagerWrapper(mCarUxRestrictionManagerWrapper);

        Notification nonMessageNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ true, /* isGroupSummary= */ true);
        nonMessageNotification.extras
                .putString(Notification.EXTRA_TITLE, generateStringOfLength(100));
        nonMessageNotification.extras
                .putString(Notification.EXTRA_TEXT, generateStringOfLength(100));
        nonMessageNotification.extras
                .putString(Notification.EXTRA_TITLE_BIG, generateStringOfLength(100));
        nonMessageNotification.extras
                .putString(Notification.EXTRA_SUMMARY_TEXT, generateStringOfLength(100));

        when(mNavigation.getNotification()).thenReturn(nonMessageNotification);

        AlertEntry optimized = mPreprocessingManager.optimizeForDriving(mNavigation);
        Bundle trimmed = optimized.getNotification().extras;

        for (String key : trimmed.keySet()) {
            switch (key) {
                case Notification.EXTRA_TITLE:
                case Notification.EXTRA_TEXT:
                case Notification.EXTRA_TITLE_BIG:
                case Notification.EXTRA_SUMMARY_TEXT:
                    CharSequence text = trimmed.getCharSequence(key);
                    assertThat(text.length() <= MAX_STRING_LENGTH).isTrue();
                default:
                    continue;
            }
        }
    }

    @Test
    public void onOptimizeForDriving_alertEntryHasMessageNotification_doesNotTrimMessageTexts() {
        when(mCarUxRestrictions.getMaxRestrictedStringLength()).thenReturn(MAX_STRING_LENGTH);
        when(mCarUxRestrictionManagerWrapper.getCurrentCarUxRestrictions())
                .thenReturn(mCarUxRestrictions);
        mPreprocessingManager.setCarUxRestrictionManagerWrapper(mCarUxRestrictionManagerWrapper);

        Notification messageNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ true, /* isGroupSummary= */ true);
        messageNotification.extras
                .putString(Notification.EXTRA_TITLE, generateStringOfLength(100));
        messageNotification.extras
                .putString(Notification.EXTRA_TEXT, generateStringOfLength(100));
        messageNotification.extras
                .putString(Notification.EXTRA_TITLE_BIG, generateStringOfLength(100));
        messageNotification.extras
                .putString(Notification.EXTRA_SUMMARY_TEXT, generateStringOfLength(100));
        messageNotification.category = Notification.CATEGORY_MESSAGE;

        when(mImportantForeground.getNotification()).thenReturn(messageNotification);

        AlertEntry optimized = mPreprocessingManager.optimizeForDriving(mImportantForeground);
        Bundle trimmed = optimized.getNotification().extras;

        for (String key : trimmed.keySet()) {
            switch (key) {
                case Notification.EXTRA_TITLE:
                case Notification.EXTRA_TEXT:
                case Notification.EXTRA_TITLE_BIG:
                case Notification.EXTRA_SUMMARY_TEXT:
                    CharSequence text = trimmed.getCharSequence(key);
                    assertThat(text.length() <= MAX_STRING_LENGTH).isFalse();
                default:
                    continue;
            }
        }
    }

    @Test
    public void onGroup_groupsNotificationsByGroupKey() {
        setConfig(/* recentOld= */ true, /* launcherIcon= */ true, /* groupingThreshold= */ 2);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        List<NotificationGroup> groupResult = mPreprocessingManager.group(mAlertEntries);
        String[] actualGroupKeys = new String[groupResult.size()];
        String[] expectedGroupKeys = {GROUP_KEY_A, GROUP_KEY_B, GROUP_KEY_C};

        for (int i = 0; i < groupResult.size(); i++) {
            actualGroupKeys[i] = groupResult.get(i).getGroupKey();
        }

        Arrays.sort(actualGroupKeys);
        Arrays.sort(expectedGroupKeys);

        assertThat(actualGroupKeys).isEqualTo(expectedGroupKeys);
    }

    @Test
    public void onGroup_highGroupingThreshold_noGroups() {
        setConfig(/* recentOld= */ true, /* launcherIcon= */ true, DEFAULT_MIN_GROUPING_THRESHOLD);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        List<NotificationGroup> groupResult = mPreprocessingManager.group(mAlertEntries);
        String[] actualGroupKeys = new String[groupResult.size()];
        String[] expectedGroupKeys = {GROUP_KEY_A, GROUP_KEY_B, GROUP_KEY_B, GROUP_KEY_C};

        for (int i = 0; i < groupResult.size(); i++) {
            actualGroupKeys[i] = groupResult.get(i).getGroupKey();
        }

        Arrays.sort(actualGroupKeys);
        Arrays.sort(expectedGroupKeys);

        assertThat(actualGroupKeys).isEqualTo(expectedGroupKeys);
    }

    @Test
    public void onGroup_groupsNotificationsBySeenUnseen() {
        setConfig(/* recentOld= */ true, /* launcherIcon= */ true, DEFAULT_MIN_GROUPING_THRESHOLD);
        initTestData(/* includeAdditionalNotifs= */ true);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        when(mNotificationDataManager.isNotificationSeen(mLessImportantForeground))
                .thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mLessImportantBackground))
                .thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mMedia)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantBackground)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground2)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground3)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground4)).thenReturn(false);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground5)).thenReturn(false);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground6)).thenReturn(false);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground7)).thenReturn(false);
        when(mNotificationDataManager.isNotificationSeen(mNavigation)).thenReturn(false);
        when(mStatusBarNotification1.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification2.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification3.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification4.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification5.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification6.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification7.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification8.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification9.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification10.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification11.getGroupKey()).thenReturn(GROUP_KEY_A);
        when(mStatusBarNotification12.getGroupKey()).thenReturn(GROUP_KEY_A);

        mPreprocessingManager.setNotificationDataManager(mNotificationDataManager);

        Set expectedResultUnseen = new HashSet();
        expectedResultUnseen.add(mImportantBackground.getKey());
        expectedResultUnseen.add(mNavigation.getKey());
        expectedResultUnseen.add(mImportantForeground4.getKey());
        expectedResultUnseen.add(mImportantForeground5.getKey());
        expectedResultUnseen.add(mImportantForeground6.getKey());
        expectedResultUnseen.add(mImportantForeground7.getKey());
        Set expectedResultSeen = new HashSet();
        expectedResultSeen.add(mImportantBackground.getKey());
        expectedResultSeen.add(mLessImportantForeground.getKey());
        expectedResultSeen.add(mImportantForeground2.getKey());
        expectedResultSeen.add(mImportantForeground3.getKey());
        expectedResultSeen.add(mMedia.getKey());
        expectedResultSeen.add(mImportantForeground.getKey());

        List<NotificationGroup> groupResult = mPreprocessingManager.group(mAlertEntries);
        Set actualResultSeen = new HashSet();
        Set actualResultUnseen = new HashSet();
        for (int j = 0; j < groupResult.size(); j++) {
            NotificationGroup group = groupResult.get(j);
            List<AlertEntry> childNotifications = group.getChildNotifications();
            for (int i = 0; i < childNotifications.size(); i++) {
                if (group.isSeen()) {
                    actualResultSeen.add(childNotifications.get(i).getKey());
                } else {
                    actualResultUnseen.add(childNotifications.get(i).getKey());
                }
            }
            if (group.getGroupSummaryNotification() != null) {
                if (group.isSeen()) {
                    actualResultSeen.add(group.getGroupSummaryNotification().getKey());
                } else {
                    actualResultUnseen.add(group.getGroupSummaryNotification().getKey());
                }
            }
        }
        assertThat(actualResultSeen).isEqualTo(expectedResultSeen);
        assertThat(actualResultUnseen).isEqualTo(expectedResultUnseen);
    }

    @Test
    public void onGroup_autoGeneratedGroupWithNoGroupChildren_doesNotShowGroupSummary() {
        List<AlertEntry> list = new ArrayList<>();
        list.add(getEmptyAutoGeneratedGroupSummary());
        List<NotificationGroup> groupResult = mPreprocessingManager.group(list);

        assertThat(groupResult.size() == 0).isTrue();
    }

    @Test
    public void addCallStateListener_preCall_triggerChanges() {
        InOrder listenerInOrder = Mockito.inOrder(mCallStateListener1);
        mPreprocessingManager.addCallStateListener(mCallStateListener1);
        listenerInOrder.verify(mCallStateListener1).onCallStateChanged(false);

        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        listenerInOrder.verify(mCallStateListener1).onCallStateChanged(true);
    }

    @Test
    public void addCallStateListener_midCall_triggerChanges() {
        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        mPreprocessingManager.addCallStateListener(mCallStateListener1);

        verify(mCallStateListener1).onCallStateChanged(true);
    }

    @Test
    public void addCallStateListener_postCall_triggerChanges() {
        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        mPreprocessingManager.addCallStateListener(mCallStateListener1);

        verify(mCallStateListener1).onCallStateChanged(false);
    }

    @Test
    public void addSameCallListenerTwice_dedupedCorrectly() {
        mPreprocessingManager.addCallStateListener(mCallStateListener1);

        verify(mCallStateListener1).onCallStateChanged(false);
        mPreprocessingManager.addCallStateListener(mCallStateListener1);

        verify(mCallStateListener1, times(1)).onCallStateChanged(false);
    }

    @Test
    public void removeCallStateListener_midCall_triggerChanges() {
        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        mPreprocessingManager.addCallStateListener(mCallStateListener1);
        // Should get triggered with true before calling removeCallStateListener
        mPreprocessingManager.removeCallStateListener(mCallStateListener1);

        intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        verify(mCallStateListener1, never()).onCallStateChanged(false);
    }

    @Test
    public void multipleCallStateListeners_triggeredAppropriately() {
        InOrder listenerInOrder1 = Mockito.inOrder(mCallStateListener1);
        InOrder listenerInOrder2 = Mockito.inOrder(mCallStateListener2);
        mPreprocessingManager.addCallStateListener(mCallStateListener1);
        listenerInOrder1.verify(mCallStateListener1).onCallStateChanged(false);

        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        mPreprocessingManager.addCallStateListener(mCallStateListener2);
        mPreprocessingManager.removeCallStateListener(mCallStateListener1);

        listenerInOrder1.verify(mCallStateListener1).onCallStateChanged(true);
        listenerInOrder2.verify(mCallStateListener2).onCallStateChanged(true);

        intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE);
        mPreprocessingManager.mIntentReceiver.onReceive(mContext, intent);

        // only listener 2 should be triggered w/ false
        listenerInOrder1.verifyNoMoreInteractions();
        listenerInOrder2.verify(mCallStateListener2).onCallStateChanged(false);
    }

    @Test
    public void onGroup_removesNotificationGroupWithOnlySummaryNotification() {
        List<AlertEntry> list = new ArrayList<>();
        list.add(new AlertEntry(mSummaryCStatusBarNotification));
        List<NotificationGroup> groupResult = mPreprocessingManager.group(list);

        assertThat(groupResult.isEmpty()).isTrue();
    }

    @Test
    public void onGroup_splitsNotificationsBySeenAndUnseen() {
        List<AlertEntry> list = new ArrayList<>();
        list.add(new AlertEntry(mSummaryCStatusBarNotification));

        List<NotificationGroup> groupResult = mPreprocessingManager.group(list);

        assertThat(groupResult.isEmpty()).isTrue();
    }

    @Test
    public void onGroup_childNotificationHasTimeStamp_groupHasMostRecentTimeStamp() {
        mBackgroundNotification.when = 0;
        mForegroundNotification.when = 1;
        mNavigationNotification.when = 2;

        mBackgroundNotification.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, true);
        mForegroundNotification.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, true);
        mNavigationNotification.extras.putBoolean(Notification.EXTRA_SHOW_WHEN, true);

        List<NotificationGroup> groupResult = mPreprocessingManager.group(mAlertEntries);

        groupResult.forEach(group -> {
            AlertEntry groupSummaryNotification = group.getGroupSummaryNotification();
            if (groupSummaryNotification != null
                    && groupSummaryNotification.getNotification() != null) {
                assertThat(groupSummaryNotification.getNotification()
                        .extras.getBoolean(Notification.EXTRA_SHOW_WHEN)).isTrue();
            }
        });
    }

    @Test
    public void onRank_ranksNotificationGroups() {
        setConfig(/* recentOld= */ true, /* launcherIcon= */ true, /* groupThreshold= */ 2);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        List<NotificationGroup> groupResult = mPreprocessingManager.group(mAlertEntries);
        List<NotificationGroup> rankResult = mPreprocessingManager.rank(groupResult, mRankingMap);

        // generateRankingMap ranked the notifications in the reverse order.
        String[] expectedOrder = {
                GROUP_KEY_C,
                GROUP_KEY_B,
                GROUP_KEY_A
        };

        for (int i = 0; i < rankResult.size(); i++) {
            String actualGroupKey = rankResult.get(i).getGroupKey();
            String expectedGroupKey = expectedOrder[i];

            assertThat(actualGroupKey).isEqualTo(expectedGroupKey);
        }
    }

    @Test
    public void onRank_ranksNotificationsInEachGroup() {
        setConfig(/* recentOld= */true, /* launcherIcon= */ true, /* groupThreshold= */ 2);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        List<NotificationGroup> groupResult = mPreprocessingManager.group(mAlertEntries);
        List<NotificationGroup> rankResult = mPreprocessingManager.rank(groupResult, mRankingMap);
        NotificationGroup groupB = rankResult.get(1);

        // first make sure that we have Group B
        assertThat(groupB.getGroupKey()).isEqualTo(GROUP_KEY_B);

        // generateRankingMap ranked the non-background notifications in the reverse order
        String[] expectedOrder = {
                "KEY_NAVIGATION",
                "KEY_LESS_IMPORTANT_FOREGROUND"
        };

        for (int i = 0; i < groupB.getChildNotifications().size(); i++) {
            String actualKey = groupB.getChildNotifications().get(i).getKey();
            String expectedGroupKey = expectedOrder[i];

            assertThat(actualKey).isEqualTo(expectedGroupKey);
        }
    }

    @Test
    public void onAdditionalGroupAndRank_isGroupSummary_returnsTheSameGroupsAsStandardGroup() {
        Notification additionalNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        additionalNotification.category = Notification.CATEGORY_MESSAGE;
        when(mAdditionalStatusBarNotification.getKey()).thenReturn("ADDITIONAL");
        when(mAdditionalStatusBarNotification.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mAdditionalStatusBarNotification.getNotification()).thenReturn(additionalNotification);
        AlertEntry additionalAlertEntry = new AlertEntry(mAdditionalStatusBarNotification);

        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        List<AlertEntry> copy = mPreprocessingManager.filter(/* showLessImportantNotifications= */
                false, new ArrayList<>(mAlertEntries), mRankingMap);
        copy.add(additionalAlertEntry);
        copy.add(new AlertEntry(mSummaryCStatusBarNotification));
        List<NotificationGroup> expected = mPreprocessingManager.group(copy);
        String[] expectedKeys = new String[expected.size()];
        for (int i = 0; i < expectedKeys.length; i++) {
            expectedKeys[i] = expected.get(i).getGroupKey();
        }

        List<NotificationGroup> actual = mPreprocessingManager
                .additionalGroupAndRank(additionalAlertEntry, mRankingMap, /* isUpdate= */ false);

        String[] actualKeys = new String[actual.size()];
        for (int i = 0; i < actualKeys.length; i++) {
            actualKeys[i] = actual.get(i).getGroupKey();
        }
        // We do not care about the order since they are not ranked yet.
        Arrays.sort(actualKeys);
        Arrays.sort(expectedKeys);
        assertThat(actualKeys).isEqualTo(expectedKeys);
    }

    @Test
    public void onAdditionalGroupAndRank_isGroupSummary_maintainsPreviousRanking() {
        Map<String, AlertEntry> testCopy = new HashMap<>(mAlertEntriesMap);
        // Seed the list with the notifications
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);

        String key = "NEW_KEY";
        String groupKey = "NEW_GROUP_KEY";
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);

        AlertEntry newEntry = new AlertEntry(newSbn);

        // Change the ordering, add a new notification and validate that the existing
        // notifications don't reorder
        AlertEntry first = mAlertEntries.get(0);
        mAlertEntries.remove(0);
        mAlertEntries.add(first);

        List<NotificationGroup> additionalRanked = mPreprocessingManager.additionalGroupAndRank(
                        newEntry, generateRankingMap(mAlertEntries), /* isUpdate= */ false)
                .stream()
                .filter(g -> !g.getGroupKey().equals(groupKey))
                .collect(Collectors.toList());

        List<NotificationGroup> standardRanked = mPreprocessingManager.rank(
                mPreprocessingManager.process(/* showLessImportantNotifications = */ false,
                        testCopy, mRankingMap), mRankingMap);

        assertThat(additionalRanked.size()).isEqualTo(standardRanked.size());

        for (int i = 0; i < additionalRanked.size(); i++) {
            assertThat(additionalRanked.get(i).getGroupKey()).isEqualTo(
                    standardRanked.get(i).getGroupKey());
        }
    }

    @Test
    public void onAdditionalGroupAndRank_isGroupSummary_prependsHighRankNotification() {
        // Seed the list
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);

        String key = "NEW_KEY";
        String groupKey = "NEW_GROUP_KEY";
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);

        AlertEntry newEntry = new AlertEntry(newSbn);
        mAlertEntries.add(newEntry);

        List<NotificationGroup> result = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ false);
        assertThat(result.get(0).getSingleNotification()).isEqualTo(newEntry);
    }

    @Test
    public void onAdditionalGroupAndRank_notGroupSummary_isUpdate_notificationUpdated() {
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground)).thenReturn(false);
        // Seed the list
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        String key = mImportantForeground.getKey();
        String groupKey = mImportantForeground.getStatusBarNotification().getGroupKey();
        Notification newNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);
        when(newSbn.getId()).thenReturn(123);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> result = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ true);

        assertThat(result.get(0).getSingleNotification().getStatusBarNotification().getId())
                .isEqualTo(123);
    }

    @Test
    public void onAdditionalGroupAndRank_updateToNotificationInSeenGroup_newUnseenGroupCreated() {
        when(mStatusBarNotification6.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification7.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification8.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification9.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification10.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification11.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification12.getGroupKey()).thenReturn(GROUP_KEY_C);
        initTestData(/* includeAdditionalNotifs= */ true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground2)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground3)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground4)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground5)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground6)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground7)).thenReturn(true);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        mPreprocessingManager.setNotificationDataManager(mNotificationDataManager);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        List<NotificationGroup> processedGroupsWithGroupKeyC = getGroupsWithGroupKey(GROUP_KEY_C,
                        mPreprocessingManager.getOldProcessedNotifications());
        // assert notifications with GROUP_KEY_C are grouped into one seen NotificationGroup.
        assertThat(processedGroupsWithGroupKeyC).hasSize(1);
        assertThat(processedGroupsWithGroupKeyC.get(0).isSeen()).isTrue();
        // Create a notification with same key and group key to be sent as an update
        String key = mImportantForeground.getKey();
        Notification newNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(newSbn.getId()).thenReturn(123);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> result = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ true);

        List<NotificationGroup> unSeenGroupsWithGroupKeyC = getGroupsWithSeenState(
                /* isSeen= */ false, getGroupsWithGroupKey(GROUP_KEY_C, result));
        assertThat(unSeenGroupsWithGroupKeyC).hasSize(1);
        assertThat(unSeenGroupsWithGroupKeyC.get(0).getSingleNotification()).isEqualTo(newEntry);
    }

    @Test
    public void onAdditionalGroupAndRank_updateToNotificationInSeenGroup_oldGroupNotDeleted() {
        // If the old group size is more than zero, it should not be deleted
        when(mStatusBarNotification6.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification7.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification8.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification9.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification10.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification11.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mStatusBarNotification12.getGroupKey()).thenReturn(GROUP_KEY_C);
        initTestData(/* includeAdditionalNotifs= */ true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground2)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground3)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground4)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground5)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground6)).thenReturn(true);
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground7)).thenReturn(true);
        PreprocessingManager.refreshInstance();
        mPreprocessingManager = PreprocessingManager.getInstance(mContext);
        mPreprocessingManager.setNotificationDataManager(mNotificationDataManager);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        List<NotificationGroup> processedGroupsWithGroupKeyC = getGroupsWithGroupKey(GROUP_KEY_C,
                        mPreprocessingManager.getOldProcessedNotifications());
        assertThat(processedGroupsWithGroupKeyC).hasSize(1);
        assertThat(processedGroupsWithGroupKeyC.get(0).getChildNotifications()).hasSize(7);
        // Create a notification with same key and group key to be sent as an update
        String key = mImportantForeground.getKey();
        Notification newNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(newSbn.getId()).thenReturn(123);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> result = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ true);

        List<NotificationGroup> seenGroupsWithGroupKeyC = getGroupsWithSeenState(
                /* isSeen= */ true, getGroupsWithGroupKey(GROUP_KEY_C, result));
        assertThat(seenGroupsWithGroupKeyC).hasSize(1);
        assertThat(seenGroupsWithGroupKeyC.get(0).getChildNotifications()).hasSize(6);
    }

    @Test
    public void onAdditionalGroupAndRank_updateToNotificationInSeenGroup_oldGroupDeleted() {
        // If the old group size is zero, it should not be deleted
        when(mNotificationDataManager.isNotificationSeen(mImportantForeground)).thenReturn(true);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        List<NotificationGroup> processedGroupsWithGroupKeyC = getGroupsWithGroupKey(GROUP_KEY_C,
                        mPreprocessingManager.getOldProcessedNotifications());
        assertThat(processedGroupsWithGroupKeyC).hasSize(1);
        assertThat(processedGroupsWithGroupKeyC.get(0).getChildNotifications()).hasSize(1);
        // Create a notification with same key and group key to be sent as an update
        String key = mImportantForeground.getKey();
        Notification newNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(newSbn.getId()).thenReturn(123);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> result = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ true);

        List<NotificationGroup> seenGroupsWithGroupKeyC = getGroupsWithSeenState(
                /* isSeen= */ true, getGroupsWithGroupKey(GROUP_KEY_C, result));
        assertThat(seenGroupsWithGroupKeyC).hasSize(0);
    }

    @Test
    public void onAdditionalGroupAndRank_newNotification_setAsSeenInDataManger() {
        String key = "TEST_KEY";
        mPreprocessingManager.setNotificationDataManager(mNotificationDataManager);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn("groupKey");
        AlertEntry newEntry = new AlertEntry(newSbn);

        mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ false);

        ArgumentCaptor<AlertEntry> arg = ArgumentCaptor.forClass(AlertEntry.class);
        verify(mNotificationDataManager).setNotificationAsSeen(arg.capture());
        assertThat(arg.getValue().getKey()).isEqualTo(key);
    }

    @Test
    public void onAdditionalGroupAndRank_addToExistingGroup_groupSurpassGroupingThresholdExist() {
        String key = "TEST_KEY";
        String groupKey = "TEST_GROUP_KEY";
        int numberOfGroupNotifications = 5;
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.config_minimumGroupingThreshold, /* value= */ 4);
        generateNotificationsWithSameGroupKey(numberOfGroupNotifications, groupKey);
        generateGroupSummaryNotification(groupKey);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> rawResult = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ false);

        List<NotificationGroup> resultNotificationGroups = rawResult.stream()
                .filter(ng -> TextUtils.equals(ng.getGroupKey(), groupKey))
                .collect(Collectors.toList());
        assertThat(resultNotificationGroups.size()).isEqualTo(1);
        List<AlertEntry> resultAlertEntries = resultNotificationGroups.get(0)
                .getChildNotifications();
        assertThat(resultAlertEntries.size()).isEqualTo(numberOfGroupNotifications + 1);
        assertThat(resultAlertEntries.get(resultAlertEntries.size() - 1).getKey()).isEqualTo(key);
    }

    @Test
    public void onAdditionalGroupAndRank_addNewNotification_notSurpassGroupingThreshold() {
        String key = "TEST_KEY";
        String groupKey = "TEST_GROUP_KEY";
        int numberOfGroupNotifications = 2;
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.config_minimumGroupingThreshold, /* value= */ 4);
        generateNotificationsWithSameGroupKey(numberOfGroupNotifications, groupKey);
        generateGroupSummaryNotification(groupKey);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> rawResult = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ false);

        List<NotificationGroup> resultNotificationGroups = rawResult.stream()
                .filter(ng -> TextUtils.equals(ng.getGroupKey(), groupKey))
                .collect(Collectors.toList());
        assertThat(resultNotificationGroups.size()).isEqualTo(numberOfGroupNotifications + 1);
    }

    @Test
    public void onAdditionalGroupAndRank_createsNewGroup_surpassGroupingThreshold() {
        String key = "TEST_KEY";
        String groupKey = "TEST_GROUP_KEY";
        int numberOfGroupNotifications = 3;
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.config_minimumGroupingThreshold, /* value= */ 4);
        generateNotificationsWithSameGroupKey(numberOfGroupNotifications, groupKey);
        generateGroupSummaryNotification(groupKey);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> rawResult = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ false);

        List<NotificationGroup> resultNotificationGroups = rawResult.stream()
                .filter(ng -> TextUtils.equals(ng.getGroupKey(), groupKey))
                .collect(Collectors.toList());
        assertThat(resultNotificationGroups.size()).isEqualTo(1);
        assertThat(resultNotificationGroups.get(0).getChildCount())
                .isEqualTo(numberOfGroupNotifications + 1);
    }

    @Test
    public void onAdditionalGroupAndRank_doesNotGroup_groupSummaryMissing() {
        String key = "TEST_KEY";
        String groupKey = "TEST_GROUP_KEY";
        int numberOfGroupNotifications = 3;
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.config_minimumGroupingThreshold, /* value= */ 4);
        generateNotificationsWithSameGroupKey(numberOfGroupNotifications, groupKey);
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        Notification newNotification = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ false);
        StatusBarNotification newSbn = mock(StatusBarNotification.class);
        when(newSbn.getNotification()).thenReturn(newNotification);
        when(newSbn.getKey()).thenReturn(key);
        when(newSbn.getGroupKey()).thenReturn(groupKey);
        AlertEntry newEntry = new AlertEntry(newSbn);

        List<NotificationGroup> rawResult = mPreprocessingManager.additionalGroupAndRank(newEntry,
                generateRankingMap(mAlertEntries), /* isUpdate= */ false);

        List<NotificationGroup> resultNotificationGroups = rawResult.stream()
                .filter(ng -> TextUtils.equals(ng.getGroupKey(), groupKey))
                .collect(Collectors.toList());
        assertThat(resultNotificationGroups.size()).isEqualTo(numberOfGroupNotifications + 1);
    }

    @Test
    public void onUpdateNotifications_notificationRemoved_removesNotification() {
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);

        List<NotificationGroup> newList =
                mPreprocessingManager.updateNotifications(
                        /* showLessImportantNotifications= */ false,
                        mImportantForeground,
                        CarNotificationListener.NOTIFY_NOTIFICATION_REMOVED,
                        mRankingMap);

        assertThat(mPreprocessingManager.getOldNotifications().containsKey(
                mImportantForeground.getKey())).isFalse();
    }

    @Test
    public void onUpdateNotification_notificationPosted_isUpdate_putsNotification() {
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        int beforeSize = mPreprocessingManager.getOldNotifications().size();
        Notification newNotification = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle("NEW_TITLE")
                .setGroup(OVERRIDE_GROUP_KEY)
                .setGroupSummary(false)
                .build();
        newNotification.category = Notification.CATEGORY_NAVIGATION;
        when(mImportantForeground.getStatusBarNotification().getNotification())
                .thenReturn(newNotification);
        List<NotificationGroup> newList =
                mPreprocessingManager.updateNotifications(
                        /* showLessImportantNotifications= */ false,
                        mImportantForeground,
                        CarNotificationListener.NOTIFY_NOTIFICATION_POSTED,
                        mRankingMap);

        int afterSize = mPreprocessingManager.getOldNotifications().size();
        AlertEntry updated = (AlertEntry) mPreprocessingManager.getOldNotifications().get(
                mImportantForeground.getKey());
        assertThat(updated).isNotNull();
        assertThat(updated.getNotification().category).isEqualTo(Notification.CATEGORY_NAVIGATION);
        assertThat(afterSize).isEqualTo(beforeSize);
    }

    @Test
    public void onUpdateNotification_notificationPosted_isNotUpdate_addsNotification() {
        mPreprocessingManager.init(mAlertEntriesMap, mRankingMap);
        int beforeSize = mPreprocessingManager.getOldNotifications().size();
        Notification additionalNotification = generateNotification(/* isForeground= */ true,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        additionalNotification.category = Notification.CATEGORY_MESSAGE;
        when(mAdditionalStatusBarNotification.getKey()).thenReturn("ADDITIONAL");
        when(mAdditionalStatusBarNotification.getGroupKey()).thenReturn(GROUP_KEY_C);
        when(mAdditionalStatusBarNotification.getNotification()).thenReturn(additionalNotification);
        AlertEntry additionalAlertEntry = new AlertEntry(mAdditionalStatusBarNotification);

        List<NotificationGroup> newList =
                mPreprocessingManager.updateNotifications(
                        /* showLessImportantNotifications= */ false,
                        additionalAlertEntry,
                        CarNotificationListener.NOTIFY_NOTIFICATION_POSTED,
                        mRankingMap);

        int afterSize = mPreprocessingManager.getOldNotifications().size();
        AlertEntry posted = (AlertEntry) mPreprocessingManager.getOldNotifications().get(
                additionalAlertEntry.getKey());
        assertThat(posted).isNotNull();
        assertThat(posted.getKey()).isEqualTo("ADDITIONAL");
        assertThat(afterSize).isEqualTo(beforeSize + 1);
    }

    private void setConfig(boolean recentOld, boolean launcherIcon, int groupThreshold) {
        TestableResources testableResources = mContext.getOrCreateTestableResources();
        testableResources.removeOverride(R.bool.config_showRecentAndOldHeaders);
        testableResources.removeOverride(R.bool.config_useLauncherIcon);
        testableResources.removeOverride(R.integer.config_minimumGroupingThreshold);
        testableResources.addOverride(R.bool.config_showRecentAndOldHeaders, recentOld);
        testableResources.addOverride(R.bool.config_useLauncherIcon, launcherIcon);
        testableResources.addOverride(R.integer.config_minimumGroupingThreshold, groupThreshold);
    }

    /**
     * Wraps StatusBarNotifications with AlertEntries and generates AlertEntriesMap and
     * RankingsMap.
     */
    private void initTestData(boolean includeAdditionalNotifs) {
        mAlertEntries = new ArrayList<>();
        mLessImportantBackground = new AlertEntry(mStatusBarNotification1);
        mLessImportantForeground = new AlertEntry(mStatusBarNotification2);
        mMedia = new AlertEntry(mStatusBarNotification3);
        mNavigation = new AlertEntry(mStatusBarNotification4);
        mImportantBackground = new AlertEntry(mStatusBarNotification5);
        mImportantForeground = new AlertEntry(mStatusBarNotification6);
        if (includeAdditionalNotifs) {
            mImportantForeground2 = new AlertEntry(mStatusBarNotification7);
            mImportantForeground3 = new AlertEntry(mStatusBarNotification8);
            mImportantForeground4 = new AlertEntry(mStatusBarNotification9);
            mImportantForeground5 = new AlertEntry(mStatusBarNotification10);
            mImportantForeground6 = new AlertEntry(mStatusBarNotification11);
            mImportantForeground7 = new AlertEntry(mStatusBarNotification12);
        }
        mAlertEntries.add(mLessImportantBackground);
        mAlertEntries.add(mLessImportantForeground);
        mAlertEntries.add(mMedia);
        mAlertEntries.add(mNavigation);
        mAlertEntries.add(mImportantBackground);
        mAlertEntries.add(mImportantForeground);
        if (includeAdditionalNotifs) {
            mAlertEntries.add(mImportantForeground2);
            mAlertEntries.add(mImportantForeground3);
            mAlertEntries.add(mImportantForeground4);
            mAlertEntries.add(mImportantForeground5);
            mAlertEntries.add(mImportantForeground6);
            mAlertEntries.add(mImportantForeground7);
        }
        mAlertEntriesMap = new HashMap<>();
        mAlertEntriesMap.put(mLessImportantBackground.getKey(), mLessImportantBackground);
        mAlertEntriesMap.put(mLessImportantForeground.getKey(), mLessImportantForeground);
        mAlertEntriesMap.put(mMedia.getKey(), mMedia);
        mAlertEntriesMap.put(mNavigation.getKey(), mNavigation);
        mAlertEntriesMap.put(mImportantBackground.getKey(), mImportantBackground);
        mAlertEntriesMap.put(mImportantForeground.getKey(), mImportantForeground);
        if (includeAdditionalNotifs) {
            mAlertEntriesMap.put(mImportantForeground2.getKey(), mImportantForeground2);
            mAlertEntriesMap.put(mImportantForeground3.getKey(), mImportantForeground3);
            mAlertEntriesMap.put(mImportantForeground4.getKey(), mImportantForeground4);
            mAlertEntriesMap.put(mImportantForeground5.getKey(), mImportantForeground5);
            mAlertEntriesMap.put(mImportantForeground6.getKey(), mImportantForeground6);
            mAlertEntriesMap.put(mImportantForeground7.getKey(), mImportantForeground7);
        }
        mRankingMap = generateRankingMap(mAlertEntries);
    }

    private AlertEntry getEmptyAutoGeneratedGroupSummary() {
        Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setGroup(OVERRIDE_GROUP_KEY)
                .setGroupSummary(true)
                .build();
        StatusBarNotification statusBarNotification = new StatusBarNotification(
                PKG, OP_PKG, ID, TAG, UID, INITIAL_PID, notification, USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        statusBarNotification.setOverrideGroupKey(OVERRIDE_GROUP_KEY);

        return new AlertEntry(statusBarNotification);
    }

    private Notification generateNotification(boolean isForeground, boolean isNavigation,
            boolean isGroupSummary) {
        Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setGroup(OVERRIDE_GROUP_KEY)
                .setGroupSummary(isGroupSummary)
                .build();

        if (isForeground) {
            // this will reset flags previously set like FLAG_GROUP_SUMMARY
            notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
        }

        if (isNavigation) {
            notification.category = Notification.CATEGORY_NAVIGATION;
        }
        return notification;
    }

    private String generateStringOfLength(int length) {
        String string = "";
        for (int i = 0; i < length; i++) {
            string += "*";
        }

        return string;
    }

    /**
     * Ranks the provided alertEntries in reverse order.
     *
     * All methods that follow afterwards help assigning diverse attributes to the {@link
     * android.service.notification.NotificationListenerService.Ranking} instances.
     */
    private NotificationListenerService.RankingMap generateRankingMap(
            List<AlertEntry> alertEntries) {
        NotificationListenerService.Ranking[] rankings =
                new NotificationListenerService.Ranking[alertEntries.size()];
        for (int i = 0; i < alertEntries.size(); i++) {
            String key = alertEntries.get(i).getKey();
            int rank = alertEntries.size() - i; // ranking in reverse order;
            NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
            ranking.populate(
                    key,
                    rank,
                    !isIntercepted(i),
                    getVisibilityOverride(i),
                    getSuppressedVisualEffects(i),
                    getImportance(i),
                    getExplanation(key),
                    getOverrideGroupKey(key),
                    getChannel(key, i),
                    getPeople(key, i),
                    getSnoozeCriteria(key, i),
                    getShowBadge(i),
                    getUserSentiment(i),
                    getHidden(i),
                    lastAudiblyAlerted(i),
                    getNoisy(i),
                    getSmartActions(key, i),
                    getSmartReplies(key, i),
                    canBubble(i),
                    isVisuallyInterruptive(i),
                    isConversation(i),
                    /* shortcutInfo= */ null,
                    getRankingAdjustment(i),
                    isBubble(i),
                    /* proposedImportance= */ 0,
                    /* sensitiveContent= */ false
            );
            rankings[i] = ranking;
        }

        NotificationListenerService.RankingMap rankingMap
                = new NotificationListenerService.RankingMap(rankings);

        return rankingMap;
    }

    private void generateNotificationsWithSameGroupKey(int numberOfNotifications, String groupKey) {
        for (int i = 0; i < numberOfNotifications; i++) {
            String key = "BASE_KEY_" + i;
            Notification notification = generateNotification(/* isForeground= */ false,
                    /* isNavigation= */ false, /* isGroupSummary= */ false);
            StatusBarNotification sbn = mock(StatusBarNotification.class);
            when(sbn.getNotification()).thenReturn(notification);
            when(sbn.getKey()).thenReturn(key);
            when(sbn.getGroupKey()).thenReturn(groupKey);
            AlertEntry alertEntry = new AlertEntry(sbn);
            mAlertEntries.add(alertEntry);
            mAlertEntriesMap.put(alertEntry.getKey(), alertEntry);
        }
    }

    private void generateGroupSummaryNotification(String groupKey) {
        Notification groupSummary = generateNotification(/* isForeground= */ false,
                /* isNavigation= */ false, /* isGroupSummary= */ true);
        StatusBarNotification sbn = mock(StatusBarNotification.class);
        when(sbn.getNotification()).thenReturn(groupSummary);
        when(sbn.getKey()).thenReturn("KEY_GROUP_SUMMARY");
        when(sbn.getGroupKey()).thenReturn(groupKey);
        AlertEntry alertEntry = new AlertEntry(sbn);
        mAlertEntries.add(alertEntry);
        mAlertEntriesMap.put(alertEntry.getKey(), alertEntry);
    }

    private int getVisibilityOverride(int index) {
        return index * 9;
    }

    private String getOverrideGroupKey(String key) {
        return key + key;
    }

    private boolean isIntercepted(int index) {
        return index % 2 == 0;
    }

    private int getSuppressedVisualEffects(int index) {
        return index * 2;
    }

    private int getImportance(int index) {
        return index;
    }

    private String getExplanation(String key) {
        return key + "explain";
    }

    private NotificationChannel getChannel(String key, int index) {
        return new NotificationChannel(key, key, getImportance(index));
    }

    private boolean getShowBadge(int index) {
        return index % 3 == 0;
    }

    private int getUserSentiment(int index) {
        switch (index % 3) {
            case 0:
                return USER_SENTIMENT_NEGATIVE;
            case 1:
                return USER_SENTIMENT_NEUTRAL;
            case 2:
                return USER_SENTIMENT_POSITIVE;
        }
        return USER_SENTIMENT_NEUTRAL;
    }

    private boolean getHidden(int index) {
        return index % 2 == 0;
    }

    private long lastAudiblyAlerted(int index) {
        return index * 2000;
    }

    private boolean getNoisy(int index) {
        return index < 1;
    }

    private ArrayList<String> getPeople(String key, int index) {
        ArrayList<String> people = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            people.add(i + key);
        }
        return people;
    }

    private ArrayList<SnoozeCriterion> getSnoozeCriteria(String key, int index) {
        ArrayList<SnoozeCriterion> snooze = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            snooze.add(new SnoozeCriterion(key + i, getExplanation(key), key));
        }
        return snooze;
    }

    private ArrayList<Notification.Action> getSmartActions(String key, int index) {
        ArrayList<Notification.Action> actions = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            PendingIntent intent = PendingIntent.getBroadcast(
                    mContext,
                    index /*requestCode*/,
                    new Intent("ACTION_" + key),
                    FLAG_IMMUTABLE);
            actions.add(new Notification.Action.Builder(null /*icon*/, key, intent).build());
        }
        return actions;
    }

    private ArrayList<CharSequence> getSmartReplies(String key, int index) {
        ArrayList<CharSequence> choices = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            choices.add("choice_" + key + "_" + i);
        }
        return choices;
    }

    private List<NotificationGroup> getGroupsWithGroupKey(String groupKey,
            List<NotificationGroup> notificationGroups) {
        return filterGroups(ng -> TextUtils.equals(groupKey, ng.getGroupKey()), notificationGroups);
    }

    private List<NotificationGroup> getGroupsWithSeenState(boolean isSeen,
            List<NotificationGroup> notificationGroups) {
        return filterGroups(ng -> ng.isSeen() == isSeen, notificationGroups);
    }

    private List<NotificationGroup> filterGroups(Function<NotificationGroup, Boolean> filter,
            List<NotificationGroup> notificationGroups) {
        return notificationGroups.stream().filter(filter::apply).toList();
    }

    private boolean canBubble(int index) {
        return index % 4 == 0;
    }

    private boolean isVisuallyInterruptive(int index) {
        return index % 4 == 0;
    }

    private boolean isConversation(int index) {
        return index % 4 == 0;
    }

    private int getRankingAdjustment(int index) {
        return index % 3 - 1;
    }

    private boolean isBubble(int index) {
        return index % 4 == 0;
    }
}
