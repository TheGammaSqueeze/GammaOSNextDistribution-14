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

package com.android.tv.settings;

import android.content.ContentProviderClient;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.system.LeanbackPickerDialogFragment;
import com.android.tv.settings.system.LeanbackPickerDialogPreference;
import com.android.tv.twopanelsettings.slices.SliceFragment;
import com.android.tv.twopanelsettings.slices.SlicePreference;
import com.android.tv.twopanelsettings.slices.SlicesConstants;

/**
 * Base class for settings fragments. Handles launching fragments and dialogs in a reasonably
 * generic way. Subclasses should only override onPreferenceStartInitialScreen.
 */

public abstract class BaseSettingsFragment extends LeanbackSettingsFragmentCompat implements
        SliceFragment.OnePanelSliceFragmentContainer {
    @Override
    public final boolean onPreferenceStartFragment(PreferenceFragmentCompat caller,
            Preference pref) {
        if (pref.getFragment() != null) {
            if (pref instanceof SlicePreference) {
                SlicePreference slicePref = (SlicePreference) pref;
                if (slicePref.getUri() == null || !isUriValid(slicePref.getUri())) {
                    return false;
                }
                Bundle b = pref.getExtras();
                b.putString(SlicesConstants.TAG_TARGET_URI, slicePref.getUri());
                b.putCharSequence(SlicesConstants.TAG_SCREEN_TITLE, slicePref.getTitle());
            }
        }
        final Fragment f =
                Fragment.instantiate(getActivity(), pref.getFragment(), pref.getExtras());
        f.setTargetFragment(caller, 0);
        if (f instanceof PreferenceFragmentCompat || f instanceof PreferenceDialogFragmentCompat) {
            startPreferenceFragment(f);
        } else {
            startImmersiveFragment(f);
        }
        return true;
    }

    private boolean isUriValid(String uri) {
        if (uri == null) {
            return false;
        }
        ContentProviderClient client =
                getContext().getContentResolver().acquireContentProviderClient(Uri.parse(uri));
        if (client != null) {
            client.close();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final boolean onPreferenceStartScreen(PreferenceFragmentCompat caller,
            PreferenceScreen pref) {
        return false;
    }

    /** Navigate back to the previous fragment **/
    public void navigateBack() {
        FragmentManager fragmentManager = getChildFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragmentCompat caller,
            Preference pref) {
        final Fragment f;
        if (pref instanceof LeanbackPickerDialogPreference) {
            final LeanbackPickerDialogPreference dialogPreference = (LeanbackPickerDialogPreference)
                    pref;
            f = dialogPreference.getType().equals("date")
                    ? LeanbackPickerDialogFragment.newDatePickerInstance(pref.getKey())
                    : LeanbackPickerDialogFragment.newTimePickerInstance(pref.getKey());
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);
            return true;
        } else {
            return super.onPreferenceDisplayDialog(caller, pref);
        }
    }
}
