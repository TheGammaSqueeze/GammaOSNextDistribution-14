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
 * limitations under the License.
 */

package android.system.helpers;

import static android.content.Context.CONTEXT_IGNORE_SECURITY;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implement common helper methods for Quick settings.
 *
 * @deprecated use classes from the "systemui-tapl" library instead
 */
@Deprecated
public class QuickSettingsHelper {
    private static final String LOG_TAG = QuickSettingsHelper.class.getSimpleName();
    private static final int LONG_TIMEOUT = 2000;
    private static final int SHORT_TIMEOUT = 500;
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String QS_DEFAULT_TILES_RES = "quick_settings_tiles_default";
    private static final BySelector FOOTER_SELECTOR = By.res(SYSTEMUI_PACKAGE, "qs_footer");
    private static final String SYSUI_QS_TILES_SETTING = "sysui_qs_tiles";
    private static final String SET_QS_TILES_COMMAND = "cmd statusbar set-tiles ";

    @NonNull private final UiDevice mDevice;
    @NonNull private final Instrumentation mInstrumentation;
    private List<String> mDefaultQSTileList = null;
    private List<String> mPreviousQSTileList = null;
    private final CommandsHelper mCommandsHelper;

    public QuickSettingsHelper(@NonNull UiDevice device, @NonNull Instrumentation inst) {
        this.mDevice = device;
        mInstrumentation = inst;
        mCommandsHelper = CommandsHelper.getInstance(mInstrumentation);
        try {
            obtainDefaultQSTiles();
        } catch (Exception e) {
            Log.e(LOG_TAG, "obtainDefaultQSTiles fails!", e);
        }
    }

    private void obtainDefaultQSTiles() throws PackageManager.NameNotFoundException {
        final Context sysUIContext =
                mInstrumentation
                        .getContext()
                        .createPackageContext(SYSTEMUI_PACKAGE, CONTEXT_IGNORE_SECURITY);
        final int qsTileListResId =
                sysUIContext
                        .getResources()
                        .getIdentifier(QS_DEFAULT_TILES_RES, "string", SYSTEMUI_PACKAGE);
        final String defaultQSTiles = sysUIContext.getString(qsTileListResId);
        mDefaultQSTileList = Arrays.asList(defaultQSTiles.split(","));
    }

    public enum QuickSettingDefaultTiles {
        WIFI("Wi-Fi"),
        SIM("Mobile data"),
        DND("Do not disturb"),
        FLASHLIGHT("Flashlight"),
        SCREEN("Auto-rotate screen"),
        BLUETOOTH("Bluetooth"),
        AIRPLANE("Airplane mode"),
        BRIGHTNESS("Display brightness");

        private final String name;

        QuickSettingDefaultTiles(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    public enum QuickSettingEditMenuTiles {
        LOCATION("Location"),
        HOTSPOT("Hotspot"),
        INVERTCOLORS("Invert colors"),
        DATASAVER("Data Saver"),
        CAST("Cast"),
        NEARBY("Nearby");

        private final String name;

        QuickSettingEditMenuTiles(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    public void addQuickSettingTileFromEditMenu(String quickSettingTile,
            String quickSettingTileToReplace, String quickSettingTileToCheckForInCSV)
            throws Exception {
        // Draw down quick settings
        launchQuickSetting();
        // Press Edit button
        UiObject2 quickSettingEdit = mDevice.wait(Until.findObject
                (By.descContains("Edit")), LONG_TIMEOUT);
        quickSettingEdit.click();
        // Scroll down to bottom to see all QS options on Edit
        swipeDown();
        // Drag and drop QS item onto existing QS tile to replace it
        // This is because we need specific coordinates on which to
        // drop the quick setting tile.
        UiObject2 quickSettingTileObject = mDevice.wait(Until.findObject
                (By.descContains(quickSettingTile)), LONG_TIMEOUT);
        Point destination = mDevice.wait(Until.findObject
                (By.descContains(quickSettingTileToReplace)), LONG_TIMEOUT)
                .getVisibleCenter();
        Assert.assertNotNull(quickSettingTile + " in Edit menu can't be found",
                quickSettingTileObject);
        Assert.assertNotNull(quickSettingTileToReplace + " in QS menu can't be found",
                destination);
        // Long press the icon, then drag it to the destination slowly.
        // Without the long press, it ends up scrolling down quick settings.
        quickSettingTileObject.click(2000);
        quickSettingTileObject.drag(destination, 1000);
        // Hit the back button in the QS menu to go back to quick settings.
        mDevice.wait(Until.findObject(By.descContains("Navigate up")), LONG_TIMEOUT);
        // Retrieve the quick settings CSV string and verify that the newly
        // added item is present.
        String quickSettingsList =
                Settings.Secure.getString(
                        mInstrumentation.getContext().getContentResolver(), SYSUI_QS_TILES_SETTING);
        Assert.assertTrue(
                quickSettingTile + " not present in qs tiles after addition.",
                quickSettingsList.contains(quickSettingTileToCheckForInCSV));
    }

    /** Sets default quick settings tile list pre-load in SystemUI resource. */
    public void setQuickSettingsDefaultTiles() {
        modifyQSTileList(mDefaultQSTileList);
    }

    /** Gets the default list of QuickSettings */
    public List<String> getQSDefaultTileList() {
        return mDefaultQSTileList;
    }

    /**
     * Set the tileName to be the first item for QS tiles.
     *
     * @param tileName tile name that will been set to the first position.
     */
    public void setFirstQS(@NonNull String tileName) {
        mPreviousQSTileList = getCurrentTilesList();

        ArrayList<String> list = new ArrayList<>(mPreviousQSTileList);
        for (int i = 0; i < list.size(); ++i) {
            if (TextUtils.equals(tileName, list.get(i))) {
                list.remove(i);
                break;
            }
        }
        list.add(0, tileName);
        modifyQSTileList(list);
    }

    public List<String> getCurrentTilesList() {
        String previousQSTiles =
                Settings.Secure.getString(
                        mInstrumentation.getContext().getContentResolver(), SYSUI_QS_TILES_SETTING);
        return Arrays.asList(previousQSTiles.split(","));
    }

    /** Reset to previous QS tile list if exist */
    public void resetToPreviousQSTileList() {
        if (mPreviousQSTileList == null) {
            return;
        }
        modifyQSTileList(mPreviousQSTileList);
    }

    /**
     * Sets customized tile list to secure settings entry 'sysui_qs_tiles' directly.
     *
     * @param list The quick settings tile list to be set
     */
    public void modifyQSTileList(@NonNull List<String> list) {
        if (list.isEmpty()) {
            return;
        }

        try {
            String settings = String.join(",", list);
            mCommandsHelper.executeShellCommand(SET_QS_TILES_COMMAND + settings);
            Thread.sleep(LONG_TIMEOUT);
        } catch (Resources.NotFoundException | InterruptedException e) {
            Log.e(LOG_TAG, "modifyQSTileList fails!", e);
        }
    }

    /** Opens quick settings panel through {@link UiDevice#openQuickSettings()} */
    public void launchQuickSetting() {
        mDevice.pressHome();
        mDevice.openQuickSettings();
        // Quick Settings isn't always open when this is complete. Explicitly wait for the Quick
        // Settings footer to make sure that the buttons are accessible when the bar is open and
        // this call is complete.
        mDevice.wait(Until.findObject(FOOTER_SELECTOR), SHORT_TIMEOUT);
        // Wait an extra bit for the animation to complete. If we return to early, future callers
        // that are trying to find the location of the footer will get incorrect coordinates
        mDevice.waitForIdle(LONG_TIMEOUT);
    }

    public void swipeUp() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight(),
                mDevice.getDisplayWidth() / 2, 0, 30);
        Thread.sleep(SHORT_TIMEOUT);
    }

    public void swipeDown() throws Exception {
        mDevice.swipe(
                mDevice.getDisplayWidth() / 2,
                0,
                mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight(),
                20);
        Thread.sleep(SHORT_TIMEOUT);
    }

    public void swipeLeft() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2, 0,
                mDevice.getDisplayHeight() / 2, 5);
    }
}
