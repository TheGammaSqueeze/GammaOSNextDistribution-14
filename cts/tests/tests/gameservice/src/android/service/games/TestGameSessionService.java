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

package android.service.games;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
import android.service.games.testing.GameSessionEventInfo;
import android.service.games.testing.OnSystemBarVisibilityChangedInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


/**
 * Test implementation of {@link GameSessionService}.
 */
public final class TestGameSessionService extends GameSessionService {
    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static final Set<String> sActiveSessions = new HashSet<>();
    @GuardedBy("sLock")
    @Nullable
    private static TestGameSession sFocusedSession;
    @GuardedBy("sLock")
    @Nullable
    private static ArrayList<GameSessionEventInfo> sGameSessionEventHistory = new ArrayList<>();

    @Override
    public GameSession onNewSession(CreateGameSessionRequest createGameSessionRequest) {
        return new TestGameSession(this, createGameSessionRequest.getGamePackageName(),
                createGameSessionRequest.getTaskId());
    }

    static void reset() {
        synchronized (sLock) {
            sActiveSessions.clear();
            sFocusedSession = null;
            sGameSessionEventHistory.clear();
        }
    }

    static Set<String> getActiveSessions() {
        synchronized (sLock) {
            return sActiveSessions;
        }
    }

    static ArrayList<GameSessionEventInfo> getGameSessionEventHistory() {
        synchronized (sLock) {
            return sGameSessionEventHistory;
        }
    }

    @Nullable
    static TestGameSession getFocusedSession() {
        synchronized (sLock) {
            return sFocusedSession;
        }
    }

    static final class TestGameSession extends GameSession {
        private final Context mContext;
        private final String mPackageName;
        private final int mTaskId;
        private final Rect mTouchableBounds = new Rect();
        private final FrameLayout mRootView;
        private final OnSystemBarVisibilityChangedInfo mOnSystemBarVisibilityChangedInfo;

        private TestGameSession(Context context, String packageName, int taskId) {
            mContext = context;
            mPackageName = packageName;
            mTaskId = taskId;
            mRootView = new FrameLayout(context);
            mOnSystemBarVisibilityChangedInfo = new OnSystemBarVisibilityChangedInfo();
        }

        String getPackageName() {
            return mPackageName;
        }

        int getTaskId() {
            return mTaskId;
        }

        Rect getTouchableBounds() {
            return new Rect(mTouchableBounds);
        }

        OnSystemBarVisibilityChangedInfo getOnSystemBarVisibilityChangedInfo() {
            return mOnSystemBarVisibilityChangedInfo;
        }

        @Override
        public void onCreate() {
            synchronized (sLock) {
                sActiveSessions.add(mPackageName);
                sGameSessionEventHistory.add(
                        GameSessionEventInfo.create(
                                mPackageName,
                                mTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED));
            }

            final TextView textView = new TextView(mContext);
            textView.setText("Overlay was rendered on: " + mPackageName);
            textView.setBackgroundColor(Color.MAGENTA);
            final FrameLayout.LayoutParams textViewLayoutParams =
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            textViewLayoutParams.leftMargin = 100;
            textViewLayoutParams.rightMargin = 100;
            textView.setLayoutParams(textViewLayoutParams);
            mRootView.addView(textView);

            setTaskOverlayView(mRootView,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));

            mRootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.getRootSurfaceControl().setTouchableRegion(new Region());
                    v.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
            textView.addOnLayoutChangeListener(
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        boolean isViewVisible = v.getGlobalVisibleRect(mTouchableBounds);
                        if (!isViewVisible) {
                            mTouchableBounds.setEmpty();
                        }
                        v.getRootSurfaceControl().setTouchableRegion(new Region(mTouchableBounds));
                    });
        }

        @Override
        public void onTransientSystemBarVisibilityFromRevealGestureChanged(
                boolean visibleDueToGesture) {
            if (visibleDueToGesture) {
                mOnSystemBarVisibilityChangedInfo.incrementTimesShown();
            } else {
                mOnSystemBarVisibilityChangedInfo.incrementTimesHidden();
            }
        }

        @Override
        public void onGameTaskFocusChanged(boolean focused) {
            if (focused) {
                synchronized (sLock) {
                    sFocusedSession = this;
                }
                return;
            }

            synchronized (sLock) {
                if (sFocusedSession == this) {
                    sFocusedSession = null;
                }
            }
        }

        @Override
        public void onDestroy() {
            synchronized (sLock) {
                sActiveSessions.remove(mPackageName);
                sGameSessionEventHistory.add(
                        GameSessionEventInfo.create(
                                mPackageName,
                                mTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_DESTROYED));
            }
        }
    }
}
