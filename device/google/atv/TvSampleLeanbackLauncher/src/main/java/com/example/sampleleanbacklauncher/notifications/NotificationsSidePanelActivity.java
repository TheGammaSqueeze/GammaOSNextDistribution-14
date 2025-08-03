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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.notifications;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.transition.Scene;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.example.sampleleanbacklauncher.R;

/**
 * Displays a side panel containing a list of notifications.
 */

public class NotificationsSidePanelActivity extends Activity
        implements LoaderManager.LoaderCallbacks<Cursor>{
    private static final String TAG = "NotifsSidePanel";
    private NotificationsPanelAdapter mPanelAdapter;
    private VerticalGridView mNotifsList;
    private View mNoNotifsMessage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ViewGroup root = findViewById(android.R.id.content);

        mPanelAdapter = new NotificationsPanelAdapter(
                NotificationsSidePanelActivity.this,null);

        setContentView(R.layout.notifications_panel_view);

        mNoNotifsMessage = findViewById(R.id.no_notifications_message);
        mNotifsList = findViewById(R.id.notifications_list);
        mNotifsList.setAdapter(mPanelAdapter);
        mNotifsList.setFocusable(true);

        getLoaderManager().initLoader(0, null,
                NotificationsSidePanelActivity.this);
    }

    private void showNoNotificationsMessage() {
        mNotifsList.setVisibility(View.GONE);
        mNoNotifsMessage.setVisibility(View.VISIBLE);
    }

    private void showNotifications() {
        mNoNotifsMessage.setVisibility(View.GONE);
        mNotifsList.setVisibility(View.VISIBLE);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, NotificationsContract.CONTENT_URI,
                TvNotification.PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mPanelAdapter.changeCursor(data);
        if (data != null && data.getCount() > 0) {
            showNotifications();
        } else {
            showNoNotificationsMessage();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mPanelAdapter.changeCursor(null);
    }
}
