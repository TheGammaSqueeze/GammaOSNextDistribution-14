/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.tv.btservices.settings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.google.android.tv.btservices.R;
import java.util.List;

public class ResponseFragment extends GuidedStepFragment {

    private static final String TAG ="Atom.ResponseFragment";

    interface Listener {
        void onChoice(String key, int choice);
        void onText(String key, String text);
    }

    // The preference key associated with this fragment
    private static final String ARG_KEY = "arg_key";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_SUMMARY = "arg_summary";
    private static final String ARG_ICON = "arg_icon";
    private static final String ARG_CHOICES = "arg_choices";
    private static final String ARG_NAME = "arg_name";
    private static final String ARG_DEFAULT_CHOICE = "arg_default_choice";

    public static final int DEFAULT_CHOICE_UNDEFINED = -1;

    public static void prepareArgs(Bundle args, String key, int titleResId, int summaryResId,
            int iconResId, int[] choices, String name, int defaultChoice) {
        args.putString(ARG_KEY, key);
        args.putInt(ARG_TITLE, titleResId);
        args.putInt(ARG_SUMMARY, summaryResId);
        args.putInt(ARG_ICON, iconResId);
        args.putIntArray(ARG_CHOICES, choices);
        args.putString(ARG_NAME, name);
        args.putInt(ARG_DEFAULT_CHOICE, defaultChoice);
    }

    private int[] getChoices() {
        Bundle args = getArguments();
        int[] choices = new int[0];
        try {
            int[] tmp = args.getIntArray(ARG_CHOICES);
            if (tmp != null) {
                choices = tmp;
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception in reading choices: " + e);
        }
        return choices;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    private String getTitleImpl() {
        Bundle args = getArguments();
        String name = args.getString(ARG_NAME);
        if (!TextUtils.isEmpty(name)) {
            return getString(args.getInt(ARG_TITLE), name);
        }
        return getString(args.getInt(ARG_TITLE));
    }

    private String getSummaryImpl() {
        Bundle args = getArguments();
        return args.getInt(ARG_SUMMARY) != 0 ? getString(args.getInt(ARG_SUMMARY)) : null;
    }

    private Drawable getDrawableImpl() {
        Bundle args = getArguments();
        return args.getInt(ARG_ICON) != 0 ? getResources().getDrawable(args.getInt(ARG_ICON)) :
                null;
    }

    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getTitleImpl(),
                getSummaryImpl(),
                null,
                getDrawableImpl());
    }

    private void dismissKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(
                getView().getApplicationWindowToken(), 0);
    }

    private Listener getListener() {
        Listener listener = getTargetFragment() instanceof Listener ?
                (Listener) getTargetFragment() : null;
        if (listener == null) {
            listener = getActivity() instanceof Listener ? (Listener) getActivity() : null;
        }
        return listener;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        final int defaultChoice = args.getInt(ARG_DEFAULT_CHOICE, DEFAULT_CHOICE_UNDEFINED);
        if(defaultChoice != DEFAULT_CHOICE_UNDEFINED) {
            if (defaultChoice < getChoices().length) {
                setSelectedActionPosition(defaultChoice);
            } else {
                Log.w(TAG, "Default choice out of bounds: " + defaultChoice);
            }
        }
    }
    @Override
    public int onProvideTheme() {
        return R.style.ResponseGuidedStepTheme;
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        Listener listener = getListener();
        if (listener == null) {
            Log.e(TAG, "onGuidedActionEditedAndProceed: no listener");
            return GuidedAction.ACTION_ID_CANCEL;
        }

        Bundle args = getArguments();
        final String existingName = args.getString(ARG_NAME);
        final String key = args.getString(ARG_KEY);
        final String newName = action.getTitle() != null ? action.getTitle().toString() : "";

        // We need to dismiss the keyboard ourselves since the behavior of dismissing the response
        // after an input completes is not one of the typical flows handled by
        // GuidedStepFragment.
        dismissKeyboard();
        if (!TextUtils.equals(existingName, newName) && !TextUtils.isEmpty(newName)) {
            listener.onText(key, action.getTitle().toString());
        }
        return action.getId();
    }
    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        int[] choices = getChoices();
        Context context = getActivity();
        for (int choice: choices) {
            actions.add(new GuidedAction.Builder(context)
                    .title(getString(choice))
                    .id(choice)
                    .build());
        }

        // If no choices were given, we know this is a text input.
        if (choices.length == 0) {
            Bundle args = getArguments();
            final String existingName = args.getString(ARG_NAME);
            actions.add(new GuidedAction.Builder(context)
                    .title(existingName)
                    .editable(true)
                    .build());
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        Bundle args = getArguments();
        final String key = args.getString(ARG_KEY);
        final int[] choices = getChoices();
        final long id = action.getId();
        Listener listener = getListener();
        if (listener == null) {
            return;
        }

        for (int choice: choices) {
            if (choice == id) {
                listener.onChoice(key, choice);
                break;
            }
        }
    }
}
