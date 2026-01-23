/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * GammaOS: storage helpers for per-display media volume.
 *
 * <p>The backing store is {@link Settings.Global} using {@link #SETTING_KEY}.</p>
 */
final class GammaMultiVolumeStore {
    static final String PROP_ENABLED = "persist.gammaos.audio.multivolume";
    static final String PROP_DUALSTACK_ACTIVE = "sys.gammaos.dualstack.active";
    static final String SETTING_KEY = "gammaos_audio_display_volume_map";

    private GammaMultiVolumeStore() {}

    static boolean isMultiVolumeEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLED, false)
                && !SystemProperties.getBoolean(PROP_DUALSTACK_ACTIVE, false);
    }

    static int getVolumeForDisplay(@NonNull Context context, int displayId, int fallback) {
        final SparseIntArray map = readMap(context);
        final int v = map.get(displayId, Integer.MIN_VALUE);
        if (v != Integer.MIN_VALUE) {
            return v;
        }
        final int defaultDisplay = map.get(0, Integer.MIN_VALUE);
        return defaultDisplay != Integer.MIN_VALUE ? defaultDisplay : fallback;
    }

    static void setVolumeForDisplay(@NonNull Context context, int displayId, int volume) {
        final SparseIntArray map = readMap(context);
        map.put(displayId, volume);
        if (map.indexOfKey(0) < 0) {
            map.put(0, volume);
        }
        Settings.Global.putString(context.getContentResolver(), SETTING_KEY, serialize(map));
    }

    @NonNull
    static SparseIntArray readMap(@NonNull Context context) {
        final String raw = Settings.Global.getString(context.getContentResolver(), SETTING_KEY);
        return parse(raw);
    }

    @NonNull
    static SparseIntArray parse(@Nullable String raw) {
        final SparseIntArray out = new SparseIntArray();
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        final String[] entries = raw.split(";");
        for (String entry : entries) {
            if (TextUtils.isEmpty(entry)) {
                continue;
            }
            final int sep = entry.indexOf('=');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            try {
                final int displayId = Integer.parseInt(entry.substring(0, sep));
                final int volume = Integer.parseInt(entry.substring(sep + 1));
                out.put(displayId, volume);
            } catch (NumberFormatException e) {
                // Ignore malformed entry.
            }
        }
        return out;
    }

    @NonNull
    static String serialize(@NonNull SparseIntArray map) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < map.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(map.keyAt(i)).append('=').append(map.valueAt(i));
        }
        return sb.toString();
    }
}
