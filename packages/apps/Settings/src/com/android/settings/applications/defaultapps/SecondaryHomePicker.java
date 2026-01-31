/*
 * Copyright (C) 2026 The GammaOS Project
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

package com.android.settings.applications.defaultapps;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.ActivityPicker;
import com.android.settings.R;
import com.android.settingslib.applications.DefaultAppInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picker for GammaOS secondary home.
 *
 * Why not only CATEGORY_SECONDARY_HOME?
 *   Most third-party launchers only declare CATEGORY_HOME, not CATEGORY_SECONDARY_HOME, which would
 *   make them invisible in the list. GammaOS intentionally allows any activity to be used as the
 *   "secondary home" target via persist.gammaos.secondary_home, so we surface:
 *     - MAIN + CATEGORY_SECONDARY_HOME (specialized secondary display launchers)
 *     - MAIN + CATEGORY_HOME (regular launchers)
 *
 * For power users, we also provide an activity picker to choose any MAIN activity explicitly.
 */
public class SecondaryHomePicker extends DefaultAppPickerFragment {

    private static final int REQUEST_PICK_ACTIVITY = 1001;
    private static final String KEY_PICK_ACTIVITY = "secondary_home_pick_activity";

    // Keep candidates reasonably broad; we still only list HOME/SECONDARY_HOME here.
    private static final int QUERY_FLAGS =
            PackageManager.MATCH_DIRECT_BOOT_AWARE
                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecondaryHomePreferenceController.ensureDefaultValue();
        normalizePackageToComponentIfNeeded();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.secondary_home_settings;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    protected void addStaticPreferences(PreferenceScreen screen) {
        final Preference pick = new Preference(getPrefContext());
        pick.setKey(KEY_PICK_ACTIVITY);
        pick.setTitle(R.string.secondary_home_pick_activity_title);
        pick.setSummary(R.string.secondary_home_pick_activity_summary);
        pick.setOnPreferenceClickListener(pref -> {
            launchActivityPicker();
            return true;
        });
        screen.addPreference(pick);
    }

    @Override
    protected String getDefaultKey() {
        return SecondaryHomePreferenceController.getCurrentKey();
    }

    @Override
    protected boolean setDefaultKey(String key) {
        SecondaryHomePreferenceController.setCurrentKey(key);
        return true;
    }

    @Override
    protected List<DefaultAppInfo> getCandidates() {
        final Context context = getContext();
        if (context == null) {
            return Collections.emptyList();
        }

        final String currentKey = getDefaultKey();
        final Collator collator = Collator.getInstance();

        final Map<String, DefaultAppInfo> byKey = new HashMap<>();
        final Set<String> seen = new HashSet<>();

        // 1) Specialized secondary-display launchers.
        addCandidatesForCategory(context, Intent.CATEGORY_SECONDARY_HOME, byKey, seen);

        // 2) Regular launchers.
        addCandidatesForCategory(context, Intent.CATEGORY_HOME, byKey, seen);

        // 3) Ensure the current selection is visible even if it doesn't match the above intents.
        final ComponentName currentCn = ComponentName.unflattenFromString(currentKey);
        if (currentCn != null) {
            final String k = currentCn.flattenToString();
            if (!byKey.containsKey(k)) {
                byKey.put(k, new DefaultAppInfo(context, mPm, mUserId, currentCn));
            }
        }

        final ArrayList<DefaultAppInfo> out = new ArrayList<>(byKey.values());
        Collections.sort(out, (a, b) -> {
            final CharSequence la = a.loadLabel();
            final CharSequence lb = b.loadLabel();
            return collator.compare(
                    la == null ? "" : la.toString(),
                    lb == null ? "" : lb.toString());
        });

        // Move current selection to top for clarity.
        if (!TextUtils.isEmpty(currentKey)) {
            for (int i = 0; i < out.size(); i++) {
                final DefaultAppInfo info = out.get(i);
                if (TextUtils.equals(currentKey, info.getKey())) {
                    out.remove(i);
                    out.add(0, info);
                    break;
                }
            }
        }

        return out;
    }

    private void addCandidatesForCategory(Context context, @NonNull String category,
            Map<String, DefaultAppInfo> byKey, Set<String> seen) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);

        final List<ResolveInfo> res =
                mPm.queryIntentActivitiesAsUser(intent, QUERY_FLAGS, mUserId);
        if (res == null) {
            return;
        }

        for (ResolveInfo ri : res) {
            if (ri == null || ri.activityInfo == null) {
                continue;
            }
            final ComponentName cn = new ComponentName(
                    ri.activityInfo.packageName, ri.activityInfo.name);
            final String key = cn.flattenToString();
            if (seen.add(key)) {
                byKey.put(key, new DefaultAppInfo(context, mPm, mUserId, cn));
            }
        }
    }

    private void launchActivityPicker() {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        final Intent picker = new Intent(context, ActivityPicker.class);
        picker.putExtra(Intent.EXTRA_TITLE,
                context.getString(R.string.secondary_home_pick_activity_title));
        // Broad, advanced: show any MAIN activity (includes HOME/SECONDARY_HOME/LAUNCHER/etc).
        picker.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_MAIN));
        startActivityForResult(picker, REQUEST_PICK_ACTIVITY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_ACTIVITY || resultCode != Activity.RESULT_OK
                || data == null) {
            return;
        }
        final ComponentName cn = data.getComponent();
        if (cn == null) {
            return;
        }
        setDefaultKey(cn.flattenToString());
        updateCandidates();
    }

    /**
     * GammaOS also accepts a raw package name in persist.gammaos.secondary_home, but Settings
     * behaves better if we store an explicit component. If a legacy package-only value is found,
     * attempt to resolve it to a concrete MAIN activity and persist that.
     */
    private void normalizePackageToComponentIfNeeded() {
        final String cur = SecondaryHomePreferenceController.getCurrentKey();
        if (TextUtils.isEmpty(cur) || cur.indexOf('/') > 0) {
            return;
        }

        final ComponentName resolved = resolveMainActivityForPackage(cur);
        if (resolved != null) {
            SecondaryHomePreferenceController.setCurrentKey(resolved.flattenToString());
        }
    }

    private ComponentName resolveMainActivityForPackage(@NonNull String pkg) {
        // Prefer the standard "launch intent" (usually MAIN|LAUNCHER).
        try {
            final Intent launch = mPm.getLaunchIntentForPackage(pkg);
            if (launch != null && launch.getComponent() != null) {
                return launch.getComponent();
            }
        } catch (Throwable ignored) { }

        // Fallback: search MAIN|SECONDARY_HOME then MAIN|HOME within the package.
        final Intent sec = new Intent(Intent.ACTION_MAIN);
        sec.addCategory(Intent.CATEGORY_SECONDARY_HOME);
        sec.setPackage(pkg);
        final ComponentName secCn = resolveFirst(sec);
        if (secCn != null) {
            return secCn;
        }

        final Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setPackage(pkg);
        return resolveFirst(home);
    }

    private ComponentName resolveFirst(Intent intent) {
        try {
            final List<ResolveInfo> res =
                    mPm.queryIntentActivitiesAsUser(intent, QUERY_FLAGS, mUserId);
            if (res == null || res.isEmpty()) {
                return null;
            }
            final ResolveInfo ri = res.get(0);
            if (ri == null || ri.activityInfo == null) {
                return null;
            }
            return new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
        } catch (Throwable ignored) { }
        return null;
    }
}