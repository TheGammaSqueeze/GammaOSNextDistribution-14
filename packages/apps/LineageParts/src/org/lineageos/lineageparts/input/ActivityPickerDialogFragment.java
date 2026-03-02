/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import org.lineageos.lineageparts.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActivityPickerDialogFragment extends DialogFragment {

    public interface OnActivityPickedListener {
        void onActivityPicked(ComponentName componentName);
    }

    private OnActivityPickedListener mListener;
    private ActivityListAdapter mAdapter;

    public void setOnActivityPickedListener(OnActivityPickedListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = requireContext();
        PackageManager pm = context.getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        List<ActivityItem> items = new ArrayList<>();
        for (ResolveInfo ri : resolveInfos) {
            ActivityInfo ai = ri.activityInfo;
            items.add(new ActivityItem(
                    ri.loadLabel(pm).toString(),
                    ri.loadIcon(pm),
                    new ComponentName(ai.packageName, ai.name)));
        }
        Collections.sort(items, (a, b) ->
                a.label.compareToIgnoreCase(b.label));

        View view = LayoutInflater.from(context).inflate(
                R.layout.activity_picker_dialog, null);

        mAdapter = new ActivityListAdapter(context, items);

        ListView listView = view.findViewById(R.id.list_view);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener((parent, v, position, id) -> {
            ActivityItem item = mAdapter.getItem(position);
            if (item != null && mListener != null) {
                mListener.onActivityPicked(item.componentName);
            }
            dismiss();
        });

        SearchView searchView = view.findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.filter(newText);
                return true;
            }
        });

        return new AlertDialog.Builder(context)
                .setTitle(R.string.activity_picker_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    static class ActivityItem {
        final String label;
        final Drawable icon;
        final ComponentName componentName;

        ActivityItem(String label, Drawable icon, ComponentName componentName) {
            this.label = label;
            this.icon = icon;
            this.componentName = componentName;
        }
    }

    static class ActivityListAdapter extends BaseAdapter {
        private final Context mContext;
        private final List<ActivityItem> mAllItems;
        private List<ActivityItem> mFilteredItems;

        ActivityListAdapter(Context context, List<ActivityItem> items) {
            mContext = context;
            mAllItems = items;
            mFilteredItems = new ArrayList<>(items);
        }

        void filter(String query) {
            if (query == null || query.isEmpty()) {
                mFilteredItems = new ArrayList<>(mAllItems);
            } else {
                String lowerQuery = query.toLowerCase();
                mFilteredItems = new ArrayList<>();
                for (ActivityItem item : mAllItems) {
                    if (item.label.toLowerCase().contains(lowerQuery)) {
                        mFilteredItems.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mFilteredItems.size();
        }

        @Override
        public ActivityItem getItem(int position) {
            return mFilteredItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(
                        R.layout.preference_icon, parent, false);
            }
            ActivityItem item = getItem(position);
            ImageView icon = convertView.findViewById(android.R.id.icon);
            TextView title = convertView.findViewById(android.R.id.title);
            TextView summary = convertView.findViewById(android.R.id.summary);
            icon.setImageDrawable(item.icon);
            title.setText(item.label);
            if (summary != null) {
                summary.setVisibility(View.GONE);
            }
            return convertView;
        }
    }
}
