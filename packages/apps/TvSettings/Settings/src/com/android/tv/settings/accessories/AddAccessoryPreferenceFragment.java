/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.annotation.CallSuper;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.leanback.preference.BaseLeanbackPreferenceFragmentCompat;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.tv.settings.R;

import java.util.List;

/**
 * The screen in TV settings that shows when bluetooth searching/pairing is active.
 */
public class AddAccessoryPreferenceFragment extends BaseLeanbackPreferenceFragmentCompat implements
        LifecycleOwner {

    private SparseArray<Drawable> mResizedDrawables = new SparseArray<>();
    private final Lifecycle mLifecycle = new Lifecycle(this);

    public AddAccessoryPreferenceFragment() {
        super();
    }

    public static AddAccessoryPreferenceFragment newInstance() {
        return new AddAccessoryPreferenceFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final AddAccessoryActivity activity = (AddAccessoryActivity) getActivity();
        final Context themedContext = getPreferenceManager().getContext();

        PreferenceScreen screen =
                getPreferenceManager().createPreferenceScreen(themedContext);
        updateList(screen, activity.getBluetoothDevices(), activity.getCurrentTargetAddress(),
                activity.getCurrentTargetStatus(), activity.getCancelledAddress());
    }

    public void updateList(PreferenceScreen screen, List<BluetoothDevice> devices,
                           String currentTargetAddress, String currentTargetStatus,
                           String cancelledAddress) {
        final Context themedContext = getPreferenceManager().getContext();
        screen.removeAll();

        if (devices == null) {
            return;
        }
        // Add entries for the discovered Bluetooth devices
        for (BluetoothDevice bt : devices) {
            final Preference preference = new Preference(themedContext);
            if (currentTargetAddress.equalsIgnoreCase(bt.getAddress()) &&
                    !currentTargetStatus.isEmpty()) {
                preference.setSummary(currentTargetStatus);
            } else if (cancelledAddress.equalsIgnoreCase(bt.getAddress())) {
                preference.setSummary(R.string.accessory_state_canceled);
            } else {
                preference.setSummary(bt.getAddress());
            }
            preference.setIcon(getDeviceDrawable(bt));
            preference.setKey(bt.getAddress());
            preference.setTitle(bt.getName());
            screen.addPreference(preference);
        }
        setPreferenceScreen(screen);
    }

    public void clearList() {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            screen.removeAll();
        }
    }

    private Drawable getDeviceDrawable(BluetoothDevice device) {
        final int resId = AccessoriesFragment.getImageIdForDevice(device, false);
        Drawable drawable = mResizedDrawables.get(resId);
        if (drawable == null) {
            final Drawable tempDrawable = getActivity().getDrawable(resId);
            // icons for TwoPanel have a bigger icon offset
            final int iconOffset =
                    getResources().getDimensionPixelSize(R.dimen.preference_icon_offset);
            final int iconWidth =
                    getResources().getDimensionPixelSize(R.dimen.lb_dialog_list_item_icon_width)
                            + iconOffset;
            final int iconHeight =
                    getResources().getDimensionPixelSize(R.dimen.lb_dialog_list_item_icon_height)
                            + iconOffset;
            tempDrawable.setBounds(0, 0, iconWidth, iconHeight);
            final Bitmap bitmap =
                    Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            tempDrawable.draw(canvas);
            drawable = new BitmapDrawable(getResources(), bitmap);
            mResizedDrawables.put(resId, drawable);
        }
        return drawable;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final AddAccessoryActivity activity = (AddAccessoryActivity) getActivity();
        activity.onActionClicked(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    public void advanceSelection() {
        final int preferenceCount = getPreferenceScreen().getPreferenceCount();
        if (preferenceCount > 0) {
            final VerticalGridView vgv = (VerticalGridView) getListView();
            final int position = (vgv.getSelectedPosition() + 1) % preferenceCount;
            vgv.setSelectedPositionSmooth(position);
        }
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycle;
    }

    @CallSuper
    @Override
    public void onResume() {
        mLifecycle.handleLifecycleEvent(ON_RESUME);
        super.onResume();
    }

    @CallSuper
    @Override
    public void onPause() {
        mLifecycle.handleLifecycleEvent(ON_PAUSE);
        super.onPause();
    }
}
