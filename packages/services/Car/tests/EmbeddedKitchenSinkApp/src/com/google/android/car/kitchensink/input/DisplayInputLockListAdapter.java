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

package com.google.android.car.kitchensink.input;

import android.annotation.NonNull;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.input.DisplayInputLockTestFragment.DisplayInputLockItem;

import java.util.ArrayList;

public final class DisplayInputLockListAdapter extends ArrayAdapter<DisplayInputLockItem> {
    private ArrayList<DisplayInputLockItem> mDisplayInputLockList;
    private final DisplayInputLockTestFragment mFragment; // for calling on button press

    private static class ViewHolder {
        public TextView mDisplayView;
        public Switch mInputLockSwitch;
    }

    public DisplayInputLockListAdapter(Context context, ArrayList<DisplayInputLockItem> items,
            DisplayInputLockTestFragment fragment) {
        super(context, R.layout.display_input_lock_listitem, items);
        mFragment = fragment;
        mDisplayInputLockList = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.display_input_lock_listitem, parent,
                    /* root= */ false);
            viewHolder.mDisplayView = convertView.findViewById(R.id.input_lock_display_name);
            viewHolder.mInputLockSwitch = convertView.findViewById(R.id.input_lock_switch);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        viewHolder.mDisplayView.setText("Display "
                + mDisplayInputLockList.get(position).mDisplayId);
        viewHolder.mInputLockSwitch.setChecked(mDisplayInputLockList.get(position).mIsLockEnabled);
        viewHolder.mInputLockSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mFragment.requestUpdateDisplayInputLockSetting(
                            mDisplayInputLockList.get(position).mDisplayId, isChecked);
                }
            });

        return convertView;
    }

    public void setListItems(@NonNull ArrayList<DisplayInputLockItem> newItems) {
        mDisplayInputLockList = newItems;
        notifyDataSetChanged();
    }
}
