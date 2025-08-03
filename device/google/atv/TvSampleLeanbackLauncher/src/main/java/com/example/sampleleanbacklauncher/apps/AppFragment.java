/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.example.sampleleanbacklauncher.apps;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.IdRes;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.collection.ArrayMap;
import androidx.recyclerview.widget.SortedList;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedListAdapterCallback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;

import com.example.sampleleanbacklauncher.R;
import com.example.sampleleanbacklauncher.notifications.NotificationsContract;
import com.example.sampleleanbacklauncher.util.LauncherAsyncTaskLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class AppFragment extends Fragment {
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ROW_TYPE_APPS, ROW_TYPE_GAMES, ROW_TYPE_SETTINGS})
    public @interface RowType {}
    public static final String ROW_TYPE_APPS = "apps";
    public static final String ROW_TYPE_GAMES = "games";
    public static final String ROW_TYPE_SETTINGS = "settings";

    private static final String TAG = "AppFragment";
    private static final boolean DEBUG = false;

    private static final String ARG_ROW_TYPE = "AppFragment.ROW_TYPE";

    private static final int ITEM_LOADER_ID = 1;
    private static final int NOTIFS_COUNT_LOADER_ID = 2;

    @RowType
    private String mRowType;

    private AppAdapter mAdapter;

    private LaunchItemsManager mLaunchItemsManager;
    private final ServiceConnection mLaunchItemsServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mLaunchItemsManager =
                    ((LaunchItemsManager.LocalBinder) service).getLaunchItemsManager();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mLaunchItemsManager = null;
        }
    };

    public static AppFragment newInstance(@RowType String rowType) {
        Bundle args = new Bundle(1);
        args.putString(ARG_ROW_TYPE, rowType);

        AppFragment fragment = new AppFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection WrongConstant
        mRowType = getArguments().getString(ARG_ROW_TYPE, ROW_TYPE_APPS);
        final Context context = getContext();
        context.bindService(new Intent(context, LaunchItemsManager.class),
                mLaunchItemsServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.app_launch_row, container, false);
        // Since there's multiple instances of this root view in the parent, they need a unique
        // id so that view state save/restore works correctly.
        root.setId(getRootViewId());
        final TextView rowTitle = (TextView) root.findViewById(R.id.row_title);
        rowTitle.setText(getRowTitle());
        final RecyclerView list = (RecyclerView) root.findViewById(android.R.id.list);
        mAdapter = new AppAdapter();
        getLoaderManager().initLoader(ITEM_LOADER_ID, null, new ItemLoaderCallbacks());
        list.setAdapter(mAdapter);

        if (mRowType == ROW_TYPE_SETTINGS) {
            getLoaderManager().initLoader(NOTIFS_COUNT_LOADER_ID, null,
                    new NotifsCountLoaderCallbacks());
        }

        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Context context = getContext();
        context.unbindService(mLaunchItemsServiceConnection);
    }

    private @IdRes int getRootViewId() {
        switch (mRowType) {
            case ROW_TYPE_APPS:
                return R.id.apps_row;
            case ROW_TYPE_GAMES:
                return R.id.games_row;
            case ROW_TYPE_SETTINGS:
                return R.id.settings_row;
        }
        throw new IllegalStateException("Unknown row type");
    }

    public CharSequence getRowTitle() {
        switch (mRowType) {
            case ROW_TYPE_APPS:
                return getText(R.string.apps_row_title);
            case ROW_TYPE_GAMES:
                return getText(R.string.games_row_title);
            case ROW_TYPE_SETTINGS:
                return getText(R.string.settings_row_title);
        }
        throw new IllegalStateException("Unknown row type");
    }

    @MainThread
    private void updateList(Set<LaunchItem> newItems) {
        if (!isAdded() || mAdapter == null) {
            return;
        }

        final SortedList<LaunchItem> items = mAdapter.getLaunchItems();

        if (newItems == null) {
            items.clear();
            return;
        }

        items.beginBatchedUpdates();
        try {
            for (int i = 0; i < items.size(); ) {
                final LaunchItem item = items.get(i);
                if (newItems.contains(item)) {
                    i++;
                } else {
                    items.remove(item);
                }
            }
            items.addAll(newItems);
        } finally {
            items.endBatchedUpdates();
        }
    }

    void onItemClicked(int adapterPosition) {
        SortedList<LaunchItem> launchItems = mAdapter.getLaunchItems();
        if (adapterPosition >= launchItems.size()) {
            Log.e(TAG, "Item clicked out of bounds, index " + adapterPosition +
                    " size " + launchItems.size());
            return;
        }
        final LaunchItem item = launchItems.get(adapterPosition);
        try {
            startActivity(item.getIntent());
            if (mLaunchItemsManager != null) {
                mLaunchItemsManager.notifyItemLaunched(item);
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Exception launching intent " + item.getIntent(), e);
            Toast.makeText(getContext(), getString(R.string.app_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final View mView;

        public AppViewHolder(View v) {
            super(v);
            mView = v;
        }

        public void bind(LaunchItem launchItem) {
            mView.findViewById(R.id.frame).setContentDescription(launchItem.getLabel());
            mView.findViewById(R.id.frame).setOnClickListener(this);

            mView.findViewById(R.id.banner).setVisibility(View.VISIBLE);

            mView.findViewById(R.id.ll).setVisibility(View.VISIBLE);

            TextView launchItemLabel = mView.findViewById(R.id.label);
            launchItemLabel.setText(launchItem.getLabel());

            ImageView launchItemIcon = mView.findViewById(R.id.icon);
            launchItemIcon.setImageDrawable(launchItem.getIcon());
        }

        @Override
        public void onClick(View v) {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                onItemClicked(position);
            }
        }
    }

    public class AppAdapter extends RecyclerView.Adapter<AppViewHolder> {
        private final SortedList<LaunchItem> mLaunchItems;
        private final Map<ComponentName, Long> mItemIdMap = new ArrayMap<>();
        private long mNextItemId = 0;

        public AppAdapter() {
            mLaunchItems = new SortedList<>(LaunchItem.class,
                    new SortedListAdapterCallback<LaunchItem>(this) {
                        @Override
                        public int compare(LaunchItem o1, LaunchItem o2) {
                            return o1.compareTo(o2);
                        }

                        @Override
                        public boolean areContentsTheSame(LaunchItem oldItem, LaunchItem newItem) {
                            return oldItem.areContentsTheSame(newItem);
                        }

                        @Override
                        public boolean areItemsTheSame(LaunchItem item1, LaunchItem item2) {
                            return Objects.equals(item1, item2);
                        }
                    });
            setHasStableIds(true);
        }

        @Override
        public AppViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new AppViewHolder(inflater.inflate(R.layout.launch_item, parent, false));
        }

        @Override
        public void onBindViewHolder(AppViewHolder holder, int position) {
            holder.bind(mLaunchItems.get(position));
        }

        @Override
        public int getItemCount() {
            return mLaunchItems.size();
        }

        public SortedList<LaunchItem> getLaunchItems() {
            return mLaunchItems;
        }

        @Override
        public long getItemId(int position) {
            ComponentName componentName = mLaunchItems.get(position).getIntent().getComponent();
            Long id = mItemIdMap.get(componentName);
            if (id != null) {
                return id;
            } else {
                long newId = mNextItemId++;
                mItemIdMap.put(componentName, newId);
                return newId;
            }
        }
    }

    private class ItemLoaderCallbacks implements LoaderManager.LoaderCallbacks<Set<LaunchItem>> {
        @Override
        public Loader<Set<LaunchItem>> onCreateLoader(int id, Bundle args) {
            return new ItemLoader(getContext(), mRowType);
        }

        @Override
        public void onLoadFinished(Loader<Set<LaunchItem>> loader, Set<LaunchItem> data) {
            updateList(data);
        }

        @Override
        public void onLoaderReset(Loader<Set<LaunchItem>> loader) {}
    }

    private static class ItemLoader extends LauncherAsyncTaskLoader<Set<LaunchItem>> {

        private final String mRowType;

        private LaunchItemsManager mLaunchItemsManager;
        private ServiceConnection mConnection;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onContentChanged();
            }
        };

        public ItemLoader(Context context, String rowType) {
            super(context);
            mRowType = rowType;
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            if (mConnection == null) {
                mConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        mLaunchItemsManager =
                                ((LaunchItemsManager.LocalBinder) service).getLaunchItemsManager();
                        startListening();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        stopListening();
                        mLaunchItemsManager = null;
                    }
                };
                getContext().bindService(new Intent(getContext(), LaunchItemsManager.class),
                        mConnection, Context.BIND_AUTO_CREATE);
            }
        }

        private void startListening() {
            switch (mRowType) {
                case ROW_TYPE_APPS:
                    LocalBroadcastManager.getInstance(getContext()).registerReceiver(mReceiver,
                            new IntentFilter(LaunchItemsManager.ACTION_APP_LIST_INVALIDATED));
                    break;
                case ROW_TYPE_GAMES:
                    LocalBroadcastManager.getInstance(getContext()).registerReceiver(mReceiver,
                            new IntentFilter(LaunchItemsManager.ACTION_GAME_LIST_INVALIDATED));
                    break;
                case ROW_TYPE_SETTINGS:
                    LocalBroadcastManager.getInstance(getContext()).registerReceiver(mReceiver,
                            new IntentFilter(LaunchItemsManager.ACTION_SETTINGS_LIST_INVALIDATED));
                    break;
            }
            // Force a load to pick up any changes since we stopped listening
            onContentChanged();
        }

        private void stopListening() {
            LocalBroadcastManager.getInstance(getContext())
                    .unregisterReceiver(mReceiver);
        }

        @Override
        protected void onReset() {
            super.onReset();
            if (mConnection != null) {
                getContext().unbindService(mConnection);
                mConnection = null;
            }
        }

        @Override
        public Set<LaunchItem> loadInBackground() {
            if (mLaunchItemsManager != null) {
                switch (mRowType) {
                    case ROW_TYPE_APPS:
                        return mLaunchItemsManager.getAppItems();
                    case ROW_TYPE_GAMES:
                        return mLaunchItemsManager.getGameItems();
                    case ROW_TYPE_SETTINGS:
                        return mLaunchItemsManager.getSettingsItems();
                    default:
                        throw new IllegalStateException("Unknown row type");
                }
            } else {
                return null;
            }
        }
    }

    private class NotifsCountLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getContext(), NotificationsContract.NOTIFS_COUNT_URI,
                    null, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) {
                Log.d(TAG, "onLoadFinished() called with: " + "loader = [" + loader + "], data = ["
                        + DatabaseUtils.dumpCursorToString(data) + "]");
            }
            if (mLaunchItemsManager != null) {
                mLaunchItemsManager.updateNotifsCountCursor(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) {
                Log.d(TAG, "onLoaderReset() called with: " + "loader = [" + loader + "]");
            }
            if (mLaunchItemsManager != null) {
                mLaunchItemsManager.updateNotifsCountCursor(null);
            }
        }
    }
}
