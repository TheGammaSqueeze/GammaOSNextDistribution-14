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

package com.example.sampleleanbacklauncher.search;

import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Trace;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Toast;

import com.example.sampleleanbacklauncher.R;

public class SearchFragment extends Fragment
        implements View.OnClickListener, View.OnFocusChangeListener {
    private static final String TAG = "SearchFragment";

    private static final String EXTRA_SEARCH_TYPE = "search_type";

    private static final int SEARCH_TYPE_VOICE = 1;
    private static final int SEARCH_TYPE_KEYBOARD = 2;

    private View mSearchOrbVoice;
    private View mSearchOrbKeyboard;

    public static SearchFragment newInstance() {
        Bundle args = new Bundle();

        SearchFragment fragment = new SearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.search, container, false);

        final ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
            }
        };

        mSearchOrbVoice = root.findViewById(R.id.search_orb_voice);
        mSearchOrbVoice.setOnFocusChangeListener(this);
        mSearchOrbVoice.setOutlineProvider(outlineProvider);
        mSearchOrbVoice.setClipToOutline(true);
        mSearchOrbVoice.setOnClickListener(this);

        mSearchOrbKeyboard = root.findViewById(R.id.search_orb_keyboard);
        mSearchOrbKeyboard.setOnFocusChangeListener(this);
        mSearchOrbKeyboard.setOutlineProvider(outlineProvider);
        mSearchOrbKeyboard.setClipToOutline(true);
        mSearchOrbKeyboard.setOnClickListener(this);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSearchOrbVoice.setOnFocusChangeListener(null);
        mSearchOrbKeyboard.setOnFocusChangeListener(null);
    }

    @Override
    public void onClick(View v) {
        final Intent intent = new Intent(Intent.ACTION_ASSIST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.putExtra(EXTRA_SEARCH_TYPE,
                v == mSearchOrbKeyboard ? SEARCH_TYPE_KEYBOARD : SEARCH_TYPE_VOICE);
        try {
            startActivity(intent);
            mSearchOrbVoice.requestFocus();
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Exception launching intent " + intent, e);
            Toast.makeText(getContext(), getString(R.string.app_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        Trace.beginSection("SearchFragment.onFocusChange");
        try {
            final View root = getView();
            if (root == null) {
                return;
            }
            root.requestRectangleOnScreen(
                    new Rect(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight()));

            final int visibility = hasFocus ? View.VISIBLE : View.GONE;
            if (v == mSearchOrbKeyboard) {
                root.findViewById(R.id.search_text_keyboard).setVisibility(visibility);
            } else {
                root.findViewById(R.id.search_text_voice).setVisibility(visibility);
            }

            final Resources resources = getResources();
            float elevation = resources.getDimension(hasFocus
                    ? R.dimen.search_item_focused_z : R.dimen.search_item_unfocused_z);
            float scale = hasFocus
                    ? resources.getFraction(R.fraction.search_item_focused_zoom, 1, 1) : 1.0f;
            int duration = resources.getInteger(R.integer.search_orb_scale_duration_ms);

            v.animate().z(elevation).scaleX(scale).scaleY(scale).setDuration(duration);
        } finally {
            Trace.endSection();
        }
    }
}
