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
package com.android.car.notification;

import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.service.notification.NotificationListenerService.Ranking.USER_SENTIMENT_NEUTRAL;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.notification.utils.MockMessageNotificationBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NotificationDataManagerTest {

    private static final String PKG_1 = "package_1";
    private static final String PKG_2 = "package_2";
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

    private AlertEntry mMessageNotification;
    private AlertEntry mNonMessageNotification;

    private NotificationDataManager mNotificationDataManager;

    @Before
    public void setup() {
        Context mContext = ApplicationProvider.getApplicationContext();
        Notification notificationMessageHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setHasMessagingStyle(true)
                .setHasReplyAction(true)
                .setHasMarkAsRead(true)
                .build();
        Notification notificationNavigationHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build();

        mMessageNotification = new AlertEntry(new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, notificationMessageHeadsUp, USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME));
        mNonMessageNotification = new AlertEntry(new StatusBarNotification(PKG_2, OP_PKG,
                ID, TAG, UID, INITIAL_PID, notificationNavigationHeadsUp, USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME));

        mNotificationDataManager = NotificationDataManager.getInstance();
    }

    @After
    public void tearDown() {
        NotificationDataManager.refreshInstance();
    }

    @Test
    public void addNewMessageNotification_newNonMessageNotification_doesNothing() {
        mNotificationDataManager.addNewMessageNotification(mNonMessageNotification);

        assertThat(mNotificationDataManager.isMessageNotificationMuted(mNonMessageNotification))
                .isFalse();
    }

    @Test
    public void addNewMessageNotification_notificationExists_muteStateNotUpdated() {
        mNotificationDataManager.addNewMessageNotification(mMessageNotification);
        mNotificationDataManager.toggleMute(mMessageNotification);

        mNotificationDataManager.addNewMessageNotification(mMessageNotification);
        assertThat(mNotificationDataManager.isMessageNotificationMuted(mMessageNotification))
                .isTrue();
    }

    @Test
    public void toggleMute_nonMessagingNotification_doesNothing() {
        mNotificationDataManager.addNewMessageNotification(mNonMessageNotification);
        mNotificationDataManager.toggleMute(mNonMessageNotification);

        assertThat(mNotificationDataManager.isMessageNotificationMuted(mNonMessageNotification))
                .isFalse();
    }

    @Test
    public void toggleMute_messagingNotification_togglesMuteState() {
        mNotificationDataManager.addNewMessageNotification(mMessageNotification);
        mNotificationDataManager.toggleMute(mMessageNotification);

        assertThat(mNotificationDataManager.isMessageNotificationMuted(mMessageNotification))
                .isTrue();
    }

    @Test
    public void toggleMute_notAddedNotification_doesNothing() {
        mNotificationDataManager.toggleMute(mNonMessageNotification);
        assertThat(mNotificationDataManager.isMessageNotificationMuted(mNonMessageNotification))
                .isFalse();
    }

    @Test
    public void toggleMute_notAddedMessageNotification_doesNothing() {
        mNotificationDataManager.toggleMute(mMessageNotification);
        assertThat(mNotificationDataManager.isMessageNotificationMuted(mMessageNotification))
                .isFalse();
    }

    @Test
    public void updateUnseenNotificationGroups_addHighImportanceNotification_updatesUnseenCount() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);
        NotificationListenerService.RankingMap rankingMap =
                generateRankingMap(Collections.singletonList(mMessageNotification.getKey()),
                        Collections.singletonList(IMPORTANCE_HIGH));

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);

        assertThat(mNotificationDataManager.getNonLowImportanceUnseenNotificationCount(rankingMap))
                .isEqualTo(1);
    }

    @Test
    public void updateUnseenNotificationGroups_addLowImportanceNotification_unseenCountZero() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);
        NotificationListenerService.RankingMap rankingMap =
                generateRankingMap(Collections.singletonList(mMessageNotification.getKey()),
                        Collections.singletonList(IMPORTANCE_LOW));

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);

        assertThat(mNotificationDataManager.getNonLowImportanceUnseenNotificationCount(rankingMap))
                .isEqualTo(0);
    }

    @Test
    public void setVisibleNotificationsAsSeen_notificationIsSeen() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        mNotificationDataManager.setVisibleNotificationsAsSeen(
                Collections.singletonList(mMessageNotification));

        assertThat(mNotificationDataManager.getSeenNotifications()).asList().containsExactly(
                mMessageNotification.getKey());
    }

    @Test
    public void isNotificationSeen_notificationIsSeen_returnTrue() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        mNotificationDataManager.setVisibleNotificationsAsSeen(
                Collections.singletonList(mMessageNotification));

        assertThat(mNotificationDataManager.isNotificationSeen(mMessageNotification)).isTrue();
    }

    @Test
    public void isNotificationSeen_notificationIsNotSeen_returnFalse() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup1 = new NotificationGroup();
        notificationGroup1.addNotification(mMessageNotification);
        NotificationGroup notificationGroup2 = new NotificationGroup();
        notificationGroup2.addNotification(mNonMessageNotification);
        notificationGroups.add(notificationGroup1);
        notificationGroups.add(notificationGroup2);

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        mNotificationDataManager.setVisibleNotificationsAsSeen(
                Collections.singletonList(mMessageNotification));

        assertThat(mNotificationDataManager.isNotificationSeen(mNonMessageNotification)).isFalse();
    }

    @Test
    public void isNotificationSeen_notificationNotPresentInUnseenMap_returnTrue() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);

        assertThat(mNotificationDataManager.isNotificationSeen(mNonMessageNotification)).isTrue();
    }

    @Test
    public void setVisibleNotificationsAsSeen_notificationIsSeen_decrementsUnseenCount() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);
        NotificationListenerService.RankingMap rankingMap =
                generateRankingMap(Collections.singletonList(mMessageNotification.getKey()),
                        Collections.singletonList(IMPORTANCE_HIGH));

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        mNotificationDataManager.setVisibleNotificationsAsSeen(
                Collections.singletonList(mMessageNotification));

        assertThat(mNotificationDataManager.getNonLowImportanceUnseenNotificationCount(rankingMap))
                .isEqualTo(0);
    }

    @Test
    public void setVisibleNotificationsAsSeen_notificationIsSeen_notificationIsVisibleToUser() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        mNotificationDataManager.setVisibleNotificationsAsSeen(
                Collections.singletonList(mMessageNotification));

        assertThat(mNotificationDataManager.getVisibleNotifications()).containsExactly(
                mMessageNotification);
    }

    @Test
    public void clearAll_clearsAllUnseenData() {
        List<NotificationGroup> notificationGroups = new ArrayList<>();

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(mMessageNotification);
        notificationGroups.add(notificationGroup);
        NotificationListenerService.RankingMap rankingMap =
                generateRankingMap(Collections.singletonList(mMessageNotification.getKey()),
                        Collections.singletonList(IMPORTANCE_HIGH));

        mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        mNotificationDataManager.clearAll();

        assertThat(mNotificationDataManager.getNonLowImportanceUnseenNotificationCount(rankingMap))
                .isEqualTo(0);
    }

    private NotificationListenerService.RankingMap generateRankingMap(
            List<String> keys, List<Integer> importances) {
        NotificationListenerService.Ranking[] rankings =
                new NotificationListenerService.Ranking[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int rank = keys.size() - i; // ranking in reverse order;
            int importance = importances.get(i);
            NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
            ranking.populate(
                    key,
                    rank,
                    /* matchesInterruptionFilter= */ false,
                    /* visibilityOverride= */ 0,
                    /* suppressedVisualEffects= */ 0,
                    importance,
                    /* explanation= */ "",
                    /* overrideGroupKey= */ "",
                    new NotificationChannel(key, key, importance),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    /* showBadge= */ false,
                    USER_SENTIMENT_NEUTRAL,
                    /* hidden= */ false,
                    /* lastAudiblyAlertedMs= */ 0,
                    /* noisy= */ false,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    /* canBubble= */ false,
                    /* visuallyInterruptive= */ false,
                    /* isConversation= */ false,
                    /* shortcutInfo= */ null,
                    /* rankingAdjustment= */ 0,
                    /* isBubble= */ false,
                    /* proposedImportance= */ 0,
                    /* sensitiveContent= */ false
            );
            rankings[i] = ranking;
        }

        return new NotificationListenerService.RankingMap(rankings);
    }
}

