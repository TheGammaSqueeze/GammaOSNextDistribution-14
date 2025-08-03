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

import static android.car.Car.CAR_OCCUPANT_ZONE_SERVICE;
import static android.car.Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.settings.CarSettings;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.List;

public final class DisplayInputLockTestFragment extends Fragment {
    private static final String TAG = "DisplayInputLock";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private Car mCar;
    private CarOccupantZoneManager mOccupantZoneManager;
    private DisplayManager mDisplayManager;

    // Array of display unique ids from the display input lock setting.
    private final ArraySet<String> mDisplayInputLockSetting = new ArraySet<>();

    private final ArrayList<DisplayInputLockItem> mDisplayInputLockItems = new ArrayList<>();
    private DisplayInputLockListAdapter mDisplayInputLockListAdapter;

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            if (DEBUG) {
                Log.d(TAG, "onDisplayAdded: display " + displayId);
            }
            Display display = mDisplayManager.getDisplay(displayId);
            mDisplayInputLockItems.add(new DisplayInputLockItem(displayId,
                        mDisplayInputLockSetting.contains(display.getUniqueId())));
            mDisplayInputLockListAdapter.setListItems(mDisplayInputLockItems);
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            if (DEBUG) {
                Log.d(TAG, "onDisplayRemoved: display " + displayId);
            }
            mDisplayInputLockItems.removeIf(item -> item.mDisplayId == displayId);
            mDisplayInputLockListAdapter.setListItems(mDisplayInputLockItems);
        }

        @Override
        public void onDisplayChanged(int displayId) {
        }
    };

    static class DisplayInputLockItem {
        public final int mDisplayId;
        public boolean mIsLockEnabled;

        DisplayInputLockItem(int displayId, boolean isLockEnabled) {
            mDisplayId = displayId;
            mIsLockEnabled = isLockEnabled;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDisplayManager = getContext().getSystemService(DisplayManager.class);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.display_input_lock_test_fragment, container,
                /* root= */ false);
        connectCar();

        ListView inputLockListView = view.findViewById(R.id.display_input_lock_list);
        initDisplayInputLockData();
        mDisplayInputLockListAdapter = new DisplayInputLockListAdapter(getContext(),
                mDisplayInputLockItems, this);
        inputLockListView.setAdapter(mDisplayInputLockListAdapter);
        mDisplayManager.registerDisplayListener(mDisplayListener, /* handler= */ null);
        Uri uri = Settings.Global.getUriFor(CarSettings.Global.DISPLAY_INPUT_LOCK);
        getContext().getContentResolver()
                .registerContentObserver(uri, /* notifyForDescendants= */ false, mSettingObserver);

        return view;
    }

    @Override
    public void onDestroyView() {
        getContext().getContentResolver().unregisterContentObserver(mSettingObserver);
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        super.onDestroyView();
    }

    /**
     * Requests update to the display input lock setting value.
     *
     * @param displayId The display for which the input lock is updated.
     * @param enabled Whether to enable the display input lock.
     */
    public void requestUpdateDisplayInputLockSetting(int displayId, boolean enabled) {
        String displayUniqueId = getDisplayUniqueId(displayId);
        if (mDisplayInputLockSetting.contains(displayUniqueId) == enabled) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "requestUpdateDisplayInputLockSetting: displayId=" + displayId
                    + ", enabled=" + enabled);
        }
        if (enabled) {
            mDisplayInputLockSetting.add(displayUniqueId);
        } else {
            mDisplayInputLockSetting.remove(displayUniqueId);
        }
        mDisplayInputLockItems.stream().filter(item -> item.mDisplayId == displayId)
                .findAny().ifPresent(item -> item.mIsLockEnabled = enabled);
        writeDisplayInputLockSetting(getContext().getContentResolver(),
                CarSettings.Global.DISPLAY_INPUT_LOCK,
                makeDisplayInputLockSetting(mDisplayInputLockSetting));
    }

    @Nullable
    private String getDisplayInputLockSetting(@NonNull ContentResolver resolver) {
        return Settings.Global.getString(resolver,
                CarSettings.Global.DISPLAY_INPUT_LOCK);
    }

    private void writeDisplayInputLockSetting(@NonNull ContentResolver resolver,
            @NonNull String settingKey, @NonNull String value) {
        Settings.Global.putString(resolver, settingKey, value);
    }

    private String makeDisplayInputLockSetting(@Nullable ArraySet<String> inputLockSetting) {
        if (inputLockSetting == null) {
            return "";
        }

        String settingValue = TextUtils.join(",", inputLockSetting);
        if (DEBUG) {
            Log.d(TAG, "makeDisplayInputLockSetting(): add new input lock setting: "
                    + settingValue);
        }
        return settingValue;
    }

    private String getDisplayUniqueId(int displayId) {
        Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            return "";
        }
        return display.getUniqueId();
    }

    private int findDisplayIdByUniqueId(@NonNull String displayUniqueId, Display[] displays) {
        for (int i = 0; i < displays.length; i++) {
            Display display = displays[i];
            if (displayUniqueId.equals(display.getUniqueId())) {
                return display.getDisplayId();
            }
        }
        return Display.INVALID_DISPLAY;
    }

    private void parseDisplayInputLockSettingValue(@NonNull String settingKey,
            @Nullable String value) {
        mDisplayInputLockSetting.clear();
        if (value == null || value.isEmpty()) {
            return;
        }

        Display[] displays = mDisplayManager.getDisplays();
        String[] entries = value.split(",");
        for (String uniqueId : entries) {
            if (findDisplayIdByUniqueId(uniqueId, displays) == Display.INVALID_DISPLAY) {
                Log.w(TAG, "Invalid display id: " + uniqueId);
                continue;
            }
            mDisplayInputLockSetting.add(uniqueId);
        }
    }

    private void initDisplayInputLockData() {
        // Read a setting value from the global setting of rear seat input lock.
        String settingValue = getDisplayInputLockSetting(getContext().getContentResolver());
        parseDisplayInputLockSettingValue(CarSettings.Global.DISPLAY_INPUT_LOCK, settingValue);

        List<CarOccupantZoneManager.OccupantZoneInfo> zonelist =
                mOccupantZoneManager.getAllOccupantZones();
        // Make input lock list items for passenger displays with the setting value.
        mDisplayInputLockItems.clear();
        for (CarOccupantZoneManager.OccupantZoneInfo zone : zonelist) {
            if (zone.occupantType == OCCUPANT_TYPE_DRIVER) {
                continue;
            }

            Display display = mOccupantZoneManager.getDisplayForOccupant(zone,
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
            if (display != null) {
                int displayId = display.getDisplayId();
                mDisplayInputLockItems.add(new DisplayInputLockItem(displayId,
                        mDisplayInputLockSetting.contains(display.getUniqueId())));
            }
        }
    }

    private boolean updateDisplayInputLockData() {
        // Read a new setting value from the global setting of rear seat input lock.
        String settingValue = getDisplayInputLockSetting(getContext().getContentResolver());
        ArraySet<String> oldInputLockSetting = new ArraySet<>(mDisplayInputLockSetting);
        parseDisplayInputLockSettingValue(CarSettings.Global.DISPLAY_INPUT_LOCK, settingValue);

        if (mDisplayInputLockSetting.equals(oldInputLockSetting)) {
            // Input lock setting is same, no need to update UI.
            if (DEBUG) {
                Log.d(TAG, "Input lock setting is same, no need to update UI.");
            }
            return false;
        }

        // Update input lock items from the setting value.
        for (DisplayInputLockItem item : mDisplayInputLockItems) {
            item.mIsLockEnabled = mDisplayInputLockSetting.contains(
                        getDisplayUniqueId(item.mDisplayId));
        }

        return true;
    }

    private void refreshDisplayInputLockList() {
        if (updateDisplayInputLockData()) {
            mDisplayInputLockListAdapter.setListItems(mDisplayInputLockItems);
        }
    }

    private final ContentObserver mSettingObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (isResumed()) {
                if (DEBUG) {
                    Log.d(TAG, "Content has changed for URI "  + uri);
                }
                refreshDisplayInputLockList();
            }
        }
    };

    private void connectCar() {
        mCar = Car.createCar(getContext(), /* handler= */ null,
                CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mOccupantZoneManager = (CarOccupantZoneManager)
                            car.getCarManager(CAR_OCCUPANT_ZONE_SERVICE);
                });
    }
}
