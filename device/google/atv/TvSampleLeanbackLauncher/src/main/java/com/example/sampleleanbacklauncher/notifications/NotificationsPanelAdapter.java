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

import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.sampleleanbacklauncher.R;

/**
 * Adapter for the {@link RecyclerView} in the notifications side panel which displayed
 * both dismissible and non-dismissible notifications.
 */
public class NotificationsPanelAdapter<VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<NotificationsPanelAdapter.NotificationPanelViewHolder> {
    private static final String TAG = "NotifsPanelAdapter";
    private static final boolean DEBUG = false;

    private static final int TYPE_DISMISSIBLE = 0;
    private static final int TYPE_PERSISTENT = 1;

    private Cursor mCursor;

    public NotificationsPanelAdapter(Context context, Cursor cursor) {
        mCursor = cursor;
        setHasStableIds(true);
    }

    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public int getItemCount() {
        if (mCursor != null) {
            return mCursor.getCount();
        }
        return 0;
    }

    @Override
    public NotificationPanelViewHolder onCreateViewHolder(
            ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        View trayItem;
        if (viewType == TYPE_DISMISSIBLE) {
            trayItem = inflater.inflate(R.layout.notification_panel_item_dismissible,
                    parent, false);
        } else {
            trayItem = inflater.inflate(R.layout.notification_panel_item,
                    parent, false);
        }

        return new NotificationPanelViewHolder(trayItem);
    }

    @Override
    public void onBindViewHolder(NotificationPanelViewHolder holder,
                                 int position) {
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("Can't move cursor to position " + position);
        }
        onBindViewHolder(holder, mCursor);
    }

    @Override
    public int getItemViewType(int position) {
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("Can't move cursor to position " + position);
        }

        boolean dismissible = mCursor.getInt(TvNotification.COLUMN_INDEX_DISMISSIBLE) != 0;
        boolean ongoing = mCursor.getInt(TvNotification.COLUMN_INDEX_ONGOING) != 0;
        if (ongoing || !dismissible) {
            return TYPE_PERSISTENT;
        } else {
            return TYPE_DISMISSIBLE;
        }
    }

    @Override
    public long getItemId(int position) {
        if (!mCursor.moveToPosition(position)) {
            Log.wtf(TAG, "Can't move cursor to position " + position);
            return View.NO_ID;
        }

        String key = mCursor.getString(TvNotification.COLUMN_INDEX_KEY);
        return key.hashCode();
    }

    public void onBindViewHolder(NotificationPanelViewHolder viewHolder, Cursor cursor) {
        TvNotification notif = TvNotification.fromCursor(cursor);
        viewHolder.setNotification(notif);
    }

    public static class NotificationPanelViewHolder extends RecyclerView.ViewHolder {
        public NotificationPanelViewHolder(View itemView) {
            super(itemView);
        }

        public void setNotification(TvNotification notification) {
            ((NotificationPanelItemView) itemView).setNotification(notification);
        }
    }

    /**
     * Swap in a new Cursor, and close the old Cursor.
     *
     * @param newCursor The new cursor to be used.
     */
    public void changeCursor(Cursor newCursor) {
        if (DEBUG) {
            Log.d(TAG, "changeCursor() called with: " + "newCursor = [" +
                    DatabaseUtils.dumpCursorToString(newCursor) + "]");
        }

        mCursor = newCursor;
        notifyDataSetChanged();
    }
}