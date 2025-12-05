/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.provider.Settings;

import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.res.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public interface QSHost {
    String TILES_SETTING = Settings.Secure.QS_TILES;
    int POSITION_AT_END = -1;

    /**
     * Returns the default QS tiles for the context.
     * @param res the resources to use to determine the default tiles
     * @return a list of specs of the default tiles
     */
    static java.util.List<String> getDefaultSpecs(Resources res) {
        final ArrayList<String> tiles = new ArrayList<>();

        // Base default list from config.xml
        String defaultTileList = res.getString(R.string.quick_settings_tiles_default);

        // GammaOS:
        //  - persist.gammaos.qs.override_default_tiles
        //      "1" / "true" (case-insensitive) => enable override
        //  - persist.gammaos.qs.override_default_tiles_0 .. _4
        //      CSV fragments of tile specs (each fragment must fit within PROP_VALUE_MAX)
        //
        // The override is evaluated every time this method is called so that
        // changing the props and forcing SystemUI to reload tiles is enough
        // to hot-swap the default layout.
        String overrideEnabled =
                SystemProperties.get("persist.gammaos.qs.override_default_tiles", "0");
        if ("1".equals(overrideEnabled) || "true".equalsIgnoreCase(overrideEnabled)) {
            // Build a single CSV from up to 5 fragments:
            // persist.gammaos.qs.override_default_tiles_0 .. _4
            StringBuilder overrideBuilder = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                String part = SystemProperties.get(
                        "persist.gammaos.qs.override_default_tiles_" + i, "").trim();
                if (part.isEmpty()) {
                    continue;
                }

                if (overrideBuilder.length() > 0
                        && overrideBuilder.charAt(overrideBuilder.length() - 1) != ',') {
                    // Ensure a comma between non-empty fragments if the previous fragment
                    // did not end with one. This makes it safe for callers to omit the
                    // trailing comma at the end of each fragment.
                    overrideBuilder.append(',');
                }

                overrideBuilder.append(part);
            }

            String overrideList = overrideBuilder.toString().trim();
            if (!overrideList.isEmpty()) {
                StringBuilder sanitized = new StringBuilder();
                boolean first = true;

                for (String rawSpec : overrideList.split(",")) {
                    if (rawSpec == null) {
                       continue;
                    }
                    String spec = rawSpec.trim();

                    // Basic sanity:
                    //  - drop empty entries
                    //  - drop entries containing whitespace (never valid tile specs)
                    if (spec.isEmpty() || spec.indexOf(' ') != -1) {
                        continue;
                    }

                    if (!first) {
                        sanitized.append(',');
                    } else {
                        first = false;
                    }
                    sanitized.append(spec);
                }

                if (sanitized.length() > 0) {
                    defaultTileList = sanitized.toString();
                }
            }
        }

        for (String rawSpec : defaultTileList.split(",")) {
            if (rawSpec == null) continue;
            String spec = rawSpec.trim();
            if (spec.isEmpty()) continue;
            tiles.add(spec);
        }
        return tiles;
    }

    Context getContext();
    Context getUserContext();
    int getUserId();
    Collection<QSTile> getTiles();
    void addCallback(Callback callback);
    void removeCallback(Callback callback);
    void removeTile(String tileSpec);
    void removeTiles(Collection<String> specs);

    java.util.List<String> getSpecs();

    /** Create a {@link QSTile} of a {@code tileSpec} type.
     *
     * This should only be called by classes that need to create one-off instances of tiles.
     * Do not use to create {@code custom} tiles without explicitly taking care of its lifecycle.
     */
    QSTile createTile(String tileSpec);

    /**
     * Add a tile to the end
     *
     * @param spec string matching a pre-defined tilespec
     */
    void addTile(String spec);

    /**
     * Add a tile into the requested spot, or at the end if the position is greater than the number
     * of tiles.
     * @param spec string matching a pre-defined tilespec
     * @param requestPosition -1 for end, 0 for beginning, or X for insertion at position X
     */
    void addTile(String spec, int requestPosition);
    void addTile(ComponentName tile);

    /**
     * Adds a custom tile to the set of current tiles.
     * @param tile the component name of the {@link android.service.quicksettings.TileService}
     * @param end if true, the tile will be added at the end. If false, at the beginning.
     */
    void addTile(ComponentName tile, boolean end);
    void removeTileByUser(ComponentName tile);
    void changeTilesByUser(List<String> previousTiles, List<String> newTiles);

    int indexOf(String tileSpec);

    interface Callback {
        void onTilesChanged();
    }
}
