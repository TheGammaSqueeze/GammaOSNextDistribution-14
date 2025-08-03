/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps track of the state of the TileService for the current test in {@link BaseTileServiceTest}.
 * <p>
 * It must be {@link #reset} for each test.
 * <p>
 * The possible states are:
 * <table>
 *     <tr>
 *         <th>State</th>
 *         <th>{@link #isTileAdded()}</th>
 *         <th>{@link #getTileServiceClass()}</th>
 *         <th>{@link #isListening()}</th>
 *         <th>{@link #getCurrentInstance()}</th>
 *     </tr>
 *     <tr>
 *         <td>Tile has not been added</td>
 *         <td>{@code false}</td>
 *         <td>{@code null}</td>
 *         <td>{@code false}</td>
 *         <td>{@code null}</td>
 *     </tr>
 *     <tr>
 *         <td>Tile has been added, QS is closed</td>
 *         <td>{@code true}</td>
 *         <td>Class name for either {@link TestTileService} or
 *             {@link ToggleableTestTileService}</td>
 *         <td>{@code false}</td>
 *         <td>{@code null}</td>
 *     </tr>
 *     <tr>
 *         <td>Tile has been added, QS is open</td>
 *         <td>{@code true}</td>
 *         <td>Class name for either {@link TestTileService} or
 *             {@link ToggleableTestTileService}</td>
 *         <td>{@code true}</td>
 *         <td>Currently bound instance of the {@link TestTileService} or
 *             {@link ToggleableTestTileService}</td>
 *     </tr>
 * </table>
 *
 * In the last state, the instance can be obtained to call its public methods.
 * <p>
 * Additionally, if the tile has been clicked in the last listening state, it will be marked as
 * such.
 */
class CurrentTestState {

    private CurrentTestState() {}
    private static AtomicReference<TestTileService> sInstance = new AtomicReference<>();
    private static AtomicBoolean sHasTileBeenClicked = new AtomicBoolean(false);
    private static AtomicReference<String> sClassName = new AtomicReference<>();

    public static void reset() {
        sInstance.set(null);
        sHasTileBeenClicked.set(false);
        sClassName.set(null);
    }

    public static TestTileService getCurrentInstance() {
        return sInstance.get();
    }

    public static void setCurrentInstance(TestTileService instance) {
        CurrentTestState.sInstance.set(instance);
    }

    public static boolean isListening() {
        return sInstance.get() != null;
    }

    public static void setTileServiceClass(String className) {
        sClassName.set(className);
    }

    public static boolean isTileAdded() {
        return sClassName.get() != null;
    }

    public static String getTileServiceClass() {
        return sClassName.get();
    }

    public static void setTileHasBeenClicked(boolean clicked) {
        sHasTileBeenClicked.set(clicked);
    }

    public static boolean hasTileBeenClicked() {
        return sHasTileBeenClicked.get();
    }
}
