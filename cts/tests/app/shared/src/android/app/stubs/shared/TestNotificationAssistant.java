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

package android.app.stubs.shared;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.service.notification.Adjustment;
import android.service.notification.NotificationAssistantService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestNotificationAssistant extends NotificationAssistantService {
    public static final String TAG = "TestNotificationAssistant";
    public static final String PKG = "android.app.stubs";
    private static final long CONNECTION_TIMEOUT_MS = 5000;

    private static TestNotificationAssistant sNotificationAssistantInstance = null;
    boolean mIsConnected;
    public List<String> mCurrentCapabilities = new ArrayList<>();
    public boolean mIsPanelOpen = false;
    public String mSnoozedKey;
    public String mSnoozedUntilContext;
    public boolean mNotificationVisible = false;
    public int mNotificationId = 1357;
    public int mNotificationSeenCount = 0;
    public int mNotificationClickCount = 0;
    public int mNotificationRank = -1;
    public int mNotificationFeedback = 0;

    public boolean mMarkSensitiveContent = false;
    public ArrayList<Notification.Action> mSmartActions = null;
    public ArrayList<CharSequence> mSmartReplies = null;
    private NotificationManager mNotificationManager;

    public Map<String, Integer> mRemoved = new HashMap<>();

    /**
     * This controls whether there is a listener connected or not. Depending on the method, if the
     * caller tries to use a listener after it has disconnected, NMS can throw a SecurityException.
     *
     * There is no race between onListenerConnected() and onListenerDisconnected() because they are
     * called in the same thread. The value that getInstance() sees is guaranteed to be the value
     * that was set by onListenerConnected() because of the happens-before established by the
     * condition variable.
     */
    private static final ConditionVariable INSTANCE_AVAILABLE = new ConditionVariable(false);

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
    }

    public void resetData() {
        mIsPanelOpen = false;
        mCurrentCapabilities.clear();
        mNotificationVisible = false;
        mNotificationSeenCount = 0;
        mNotificationClickCount = 0;
        mNotificationRank = -1;
        mNotificationFeedback = 0;
        mSnoozedKey = null;
        mSnoozedUntilContext = null;
        mRemoved.clear();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sNotificationAssistantInstance = this;
        mCurrentCapabilities = mNotificationManager.getAllowedAssistantAdjustments();
        INSTANCE_AVAILABLE.open();
        Log.d(TAG, "TestNotificationAssistant connected");
        mIsConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        INSTANCE_AVAILABLE.close();
        Log.d(TAG, "TestNotificationAssistant disconnected");
        sNotificationAssistantInstance = null;
        mIsConnected = false;
    }

    public static TestNotificationAssistant getInstance() {
        if (INSTANCE_AVAILABLE.block(CONNECTION_TIMEOUT_MS)) {
            return sNotificationAssistantInstance;
        }
        return null;
    }

    @Override
    public void onNotificationSnoozedUntilContext(StatusBarNotification statusBarNotification,
            String s) {
        mSnoozedKey = statusBarNotification.getKey();
        mSnoozedUntilContext = s;
    }

    @Override
    public Adjustment onNotificationEnqueued(StatusBarNotification sbn) {
        return null;
    }

    @Override
    public Adjustment onNotificationEnqueued(StatusBarNotification sbn, NotificationChannel channel,
            RankingMap rankingMap) {
        Bundle signals = new Bundle();
        Ranking ranking = new Ranking();
        rankingMap.getRanking(sbn.getKey(), ranking);
        mNotificationRank = ranking.getRank();
        signals.putInt(Adjustment.KEY_USER_SENTIMENT, Ranking.USER_SENTIMENT_POSITIVE);
        if (mMarkSensitiveContent) {
            signals.putBoolean(Adjustment.KEY_SENSITIVE_CONTENT, true);
        }
        if (mSmartActions != null) {
            signals.putParcelableArrayList(Adjustment.KEY_CONTEXTUAL_ACTIONS, mSmartActions);
        }
        if (mSmartReplies != null) {
            signals.putCharSequenceArrayList(Adjustment.KEY_TEXT_REPLIES, mSmartReplies);
        }
        return new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());
    }

    @Override
    public void onAllowedAdjustmentsChanged() {
        mCurrentCapabilities = mNotificationManager.getAllowedAssistantAdjustments();
    }

    public void resetNotificationVisibilityCounts() {
        mNotificationSeenCount = 0;
    }

    @Override
    public void onNotificationVisibilityChanged(String key, boolean isVisible) {
        if (key.contains(getPackageName() + "|" + mNotificationId)) {
            mNotificationVisible = isVisible;
        }
    }

    @Override
    public void onNotificationsSeen(List<String> keys) {
        mNotificationSeenCount += keys.size();
    }

    @Override
    public void onPanelHidden() {
        mIsPanelOpen = false;
    }

    @Override
    public void onPanelRevealed(int items) {
        mIsPanelOpen = true;
    }

    public void resetNotificationClickCount() {
        mNotificationClickCount = 0;
    }

    @Override
    public void onNotificationClicked(String key) {
        mNotificationClickCount++;
    }

    @Override
    public void onNotificationFeedbackReceived(String key, RankingMap rankingMap, Bundle feedback) {
        mNotificationFeedback = feedback.getInt(FEEDBACK_RATING, 0);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        mRemoved.put(sbn.getKey(), -1);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        if (sbn == null) {
            return;
        }
        mRemoved.put(sbn.getKey(), reason);
    }
}
