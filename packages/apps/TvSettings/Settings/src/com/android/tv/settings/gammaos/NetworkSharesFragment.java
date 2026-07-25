/*
 * Copyright (C) 2026 GammaOS
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
package com.android.tv.settings.gammaos;

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.gammaos.GammaShareConfig;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.List;

/**
 * The list of configured network shares, with a row to add another.
 *
 * A share is served by the gammaos-sharefs FUSE daemon and appears at /mnt/shares/&lt;name&gt;. This
 * screen edits the same {@code persist.gammaos.share.<n>.*} properties the GammaOS Nano menu edits
 * (see {@link GammaShareConfig}), so a share added here shows up there and the other way round.
 *
 * <p>Built in code rather than from an XML screen because the number of rows depends on how many
 * shares exist.
 */
@Keep
public class NetworkSharesFragment extends SettingsPreferenceFragment {

    public static NetworkSharesFragment newInstance() {
        return new NetworkSharesFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Rebuild every time: a share edited on the screen above this one, or one that has finished
        // connecting since, both need the row to change.
        refresh();
    }

    private void refresh() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        screen.setTitle(R.string.network_shares_title);

        Preference info = new Preference(getContext());
        info.setKey("network_shares_info");
        info.setSelectable(false);
        info.setSummary(R.string.network_shares_info);
        screen.addPreference(info);

        List<GammaShareConfig.Share> shares = GammaShareConfig.loadAll();
        for (GammaShareConfig.Share s : shares) {
            Preference p = new Preference(getContext());
            p.setKey("share_" + s.slot);
            p.setTitle(s.name);
            p.setSummary(summaryFor(s));
            p.setOnPreferenceClickListener(pref -> {
                openEditor(s.slot);
                return true;
            });
            screen.addPreference(p);
        }

        if (shares.size() < GammaShareConfig.MAX_SHARES) {
            Preference add = new Preference(getContext());
            add.setKey("network_shares_add");
            add.setTitle(R.string.network_shares_add);
            add.setSummary(R.string.network_shares_add_summary);
            add.setOnPreferenceClickListener(pref -> {
                int slot = GammaShareConfig.firstFreeSlot();
                if (slot == 0) return true;
                // Seed the slot so the share has an identity from the moment it exists and the
                // editor has something to show. SMB is the common case on a home network.
                GammaShareConfig.Share s = new GammaShareConfig.Share();
                s.slot = slot;
                s.name = getString(R.string.network_shares_default_name, slot);
                s.type = GammaShareConfig.TYPE_SMB;
                GammaShareConfig.save(s);
                openEditor(slot);
                return true;
            });
            screen.addPreference(add);
        } else {
            Preference full = new Preference(getContext());
            full.setKey("network_shares_full");
            full.setSelectable(false);
            full.setSummary(getString(R.string.network_shares_full, GammaShareConfig.MAX_SHARES));
            screen.addPreference(full);
        }
    }

    /** What the user needs to tell two shares apart, plus whether it is actually connected. */
    private String summaryFor(GammaShareConfig.Share s) {
        StringBuilder sb = new StringBuilder();
        sb.append(GammaShareConfig.typeLabel(s.type)).append("  ").append(s.host);
        if (!s.path.isEmpty()) sb.append('/').append(s.path);
        sb.append("  -  ");
        // "off" and "on but not connected" are different situations: the second is worth looking at.
        if (!s.enabled) {
            sb.append(getString(R.string.network_shares_state_off));
        } else if (GammaShareConfig.isMounted(s.name)) {
            sb.append(getString(R.string.network_shares_state_connected));
        } else {
            sb.append(getString(R.string.network_shares_state_connecting));
        }
        return sb.toString();
    }

    private void openEditor(int slot) {
        NetworkShareEditFragment f = NetworkShareEditFragment.newInstance(slot);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, f)
                .addToBackStack(null)
                .commit();
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }
}
