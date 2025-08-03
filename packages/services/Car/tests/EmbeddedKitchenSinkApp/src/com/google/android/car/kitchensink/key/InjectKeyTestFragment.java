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

package com.google.android.car.kitchensink.key;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.util.InjectKeyEventUtils;

import java.util.ArrayList;
import java.util.List;

public final class InjectKeyTestFragment extends Fragment {
    private static final String TAG = InjectKeyTestFragment.class.getSimpleName();

    private static final int KEY_NUM_COL = 3;

    private static final int[] KEY_CODES = {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_NEXT
    };

    private DisplayManager mDisplayManager;
    private Display mCurrentDisplay;
    private Display mTargetDisplay;

    private Spinner mDisplaySpinner;
    private CarOccupantZoneManager mOccupantZoneManager;
    private RecyclerView mKeysLayout;
    private EditText mCustomKeyEditText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisplayManager = getContext().getSystemService(DisplayManager.class);
        mCurrentDisplay = getContext().getDisplay();

        final Runnable r = () -> {
            mOccupantZoneManager = ((KitchenSinkActivity) getActivity()).getOccupantZoneManager();
        };
        ((KitchenSinkActivity) getActivity()).requestRefreshManager(r,
                new Handler(getContext().getMainLooper()));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.injecting_key_fragment, container, false);
        mDisplaySpinner = view.findViewById(R.id.display_select_spinner);
        mKeysLayout = view.findViewById(R.id.key_buttons);
        mKeysLayout.setAdapter(new KeysAdapter(getContext()));
        mKeysLayout.setLayoutManager(new GridLayoutManager(getContext(), KEY_NUM_COL));
        mCustomKeyEditText = view.findViewById(R.id.custom_key_edit);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mDisplaySpinner.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, getDisplays()));
        mDisplaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedDisplayId = (Integer) mDisplaySpinner.getItemAtPosition(position);
                mTargetDisplay = mDisplayManager.getDisplay(selectedDisplayId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // nothing to do
            }
        });
        view.findViewById(R.id.custom_key_button).setOnClickListener((v) -> injectCustomKey());
    }

    private OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        List<OccupantZoneInfo> zones = mOccupantZoneManager.getAllOccupantZones();
        for (OccupantZoneInfo zone : zones) {
            List<Display> displays = mOccupantZoneManager.getAllDisplaysForOccupant(zone);
            for (Display disp : displays) {
                if (disp.getDisplayId() == displayId) {
                    return zone;
                }
            }
        }
        return null;
    }

    /**
     * Generates a custom keycode through text input.
     */
    private void injectCustomKey() {
        String customKey = mCustomKeyEditText.getText().toString();
        if (customKey == null || customKey.length() == 0) {
            return;
        }
        try {
            injectKeyByShell(Integer.parseInt(customKey));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid key code: " + customKey, e);
        }
    }


    private void injectKeyByShell(int keyCode) {
        if (mTargetDisplay == null) {
            return;
        }

        OccupantZoneInfo zone = getOccupantZoneForDisplayId(
                mTargetDisplay.getDisplayId());
        InjectKeyEventUtils.injectKeyByShell(zone, keyCode);
    }

    private ArrayList<Integer> getDisplays() {
        ArrayList<Integer> displayIds = new ArrayList<>();
        Display[] displays = mDisplayManager.getDisplays();
        int uidSelf = Process.myUid();
        for (Display disp : displays) {
            if (!disp.hasAccess(uidSelf)) {
                Log.d(TAG, "Cannot access the display: displayId=" + disp.getDisplayId());
                continue;
            }
            if (mCurrentDisplay != null && disp.getDisplayId() == mCurrentDisplay.getDisplayId()) {
                // skip the current display
                continue;
            }
            displayIds.add(disp.getDisplayId());
        }
        return displayIds;
    }

    private final class KeysAdapter extends RecyclerView.Adapter<ItemViewHolder> {
        private final LayoutInflater mLayoutInflator;

        KeysAdapter(Context context) {
            mLayoutInflator = LayoutInflater.from(context);
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mLayoutInflator.inflate(R.layout.injecting_key_item, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, int position) {
            holder.mButton.setText(KeyEvent.keyCodeToString(KEY_CODES[position]));
            holder.mButton.setOnClickListener(v -> injectKeyByShell(KEY_CODES[position]));
        }

        @Override
        public int getItemCount() {
            return KEY_CODES.length;
        }
    }

    private final class ItemViewHolder extends RecyclerView.ViewHolder {
        Button mButton;

        ItemViewHolder(View itemView) {
            super(itemView);
            mButton = itemView.findViewById(R.id.inject_key_button);
        }
    }
}
