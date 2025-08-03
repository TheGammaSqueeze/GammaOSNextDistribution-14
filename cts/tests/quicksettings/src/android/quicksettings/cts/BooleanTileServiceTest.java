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

package android.quicksettings.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.service.quicksettings.Tile;

import com.android.systemui.qs.nano.QsTileState;

import org.junit.Test;

public class BooleanTileServiceTest extends BaseTileServiceTest {
    private final static String TAG = "BooleanTileServiceTest";

    @Test
    public void testTileIsBoundAndListening() throws Exception {
        startTileService();
        expandSettings(true);
        waitForListening(true);
    }

    @Test
    public void testTileInDumpAndHasBooleanState() throws Exception {
        initializeAndListen();

        final QsTileState tileState = findTileState();
        assertNotNull(tileState);
        assertTrue(tileState.hasBooleanState());
    }

    @Test
    public void testTileStartsInactive() throws Exception {
        initializeAndListen();

        assertEquals(Tile.STATE_INACTIVE, mTileService.getQsTile().getState());
    }

    @Test
    public void testValueTracksState() throws Exception {
        initializeAndListen();

        QsTileState tileState = findTileState();

        // Tile starts inactive
        assertFalse(tileState.getBooleanState());

        ((ToggleableTestTileService) mTileService).toggleState();

        // Close and open QS to make sure that state is refreshed
        expandSettings(false);
        waitForListening(false);
        expandSettings(true);
        waitForListening(true);

        assertEquals(Tile.STATE_ACTIVE, mTileService.getQsTile().getState());

        tileState = findTileState();

        assertTrue(tileState.getBooleanState());
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected String getComponentName() {
        return ToggleableTestTileService.getComponentName().flattenToString();
    }

    @Override
    protected String getTileServiceClassName() {
        return ToggleableTestTileService.class.getName();
    }
}
