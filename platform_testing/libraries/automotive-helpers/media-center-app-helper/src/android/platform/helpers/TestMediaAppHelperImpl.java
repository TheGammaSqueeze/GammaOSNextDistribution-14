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

package android.platform.helpers;

import android.app.Instrumentation;

import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import java.util.List;

/** Helper class for Test Media app for MediaCenter test */
public class TestMediaAppHelperImpl extends AbstractStandardAppHelper
        implements IAutoTestMediaAppHelper {
    // Wait Time
    private static final int SHORT_RESPONSE_WAIT_MS = 1000;

    public TestMediaAppHelperImpl(Instrumentation instr) {
        super(instr);
    }

    /** {@inheritDoc} */
    @Override
    public void exit() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().wait1Second();
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void open() {
        // Nothing to open
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadMediaInLocalMediaTestApp() {
        // Open Account type
        clickAndWait(AutomotiveConfigConstants.TEST_MEDIA_ACCOUNT_TYPE, "Account Type");
        // Select Paid Account type
        clickAndWait(AutomotiveConfigConstants.TEST_MEDIA_ACCOUNT_TYPE_PAID, "Account Type: Paid");
        // open Root node type
        clickAndWait(AutomotiveConfigConstants.TEST_MEDIA_ROOT_NODE_TYPE, "Root node type");
        // select Browsable content
        clickAndWait(
                AutomotiveConfigConstants.TEST_MEDIA_ROOT_NODE_TYPE_BROWSABLE, "Browsable Content");
        // close settings
        clickAndWait(AutomotiveConfigConstants.TEST_MEDIA_APP_CLOSE_SETTING, "Close Settings");
        selectSongInTestMediaApp();
    }

    private void selectSongInTestMediaApp() {
        BySelector songListSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MEDIA_SONGS_LIST);

        List<UiObject2> songList = getSpectatioUiUtil().findUiObjects(songListSelector);
        getSpectatioUiUtil()
                .validateUiObjects(songList, AutomotiveConfigConstants.MEDIA_SONGS_LIST);
        getSpectatioUiUtil().clickAndWait(songList.get(1));
        getSpectatioUiUtil().wait1Second();

        // minimize songs
        BySelector goBackToSongsListSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.MEDIA_APP_NAVIGATION_ICON);
        UiObject2 goBackToSongsList = getSpectatioUiUtil().findUiObject(goBackToSongsListSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        goBackToSongsList, AutomotiveConfigConstants.MEDIA_APP_NAVIGATION_ICON);
        getSpectatioUiUtil().wait1Second();
    }

    private void clickAndWait(String autoConfigConstants, String fieldName) {
        BySelector mediaTestAppFieldSelector = getUiElementFromConfig(autoConfigConstants);
        UiObject2 mediaTestAppField = getSpectatioUiUtil().findUiObject(mediaTestAppFieldSelector);

        getSpectatioUiUtil()
                .validateUiObject(
                        mediaTestAppField, String.format("Test Media App field: %s " + fieldName));
        getSpectatioUiUtil().clickAndWait(mediaTestAppField);
        getSpectatioUiUtil().wait1Second();
    }

}
