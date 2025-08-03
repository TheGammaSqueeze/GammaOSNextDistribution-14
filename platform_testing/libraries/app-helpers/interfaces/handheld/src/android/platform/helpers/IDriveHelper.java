/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.support.test.uiautomator.Direction;

public interface IDriveHelper extends IAppHelper {

    /**
     * Setup expectations: Google Drive is opened and the navigation button is visible.
     *
     * <p>This method will go to "My Drive" page.
     */
    public void goToMyDrive();

    /**
     * Setup expectations: Google Drive is opened and the navigation button is visible.
     *
     * <p>This method will go to "Shared with me" page.
     */
    public void goToSharedWithMe();

    /**
     * Setup expectations: Google Drive is opened and the navigation drawer is visible.
     *
     * <p>This method will open the navigation drawer.
     */
    public void openNavigationDrawer();

    /**
     * Setup expectations: Google Drive is opened and the search button is visible.
     *
     * <p>This method will input searching filename.
     */
    public void searchFile(String fileName);

    /**
     * Setup expectations: Google Drive is opened and the search result is displayed.
     *
     * <p>This method will click the menu of search result by index.
     *
     * @param index The index of the search result to select
     */
    public void clickSearchResultActionButton(int index);

    /**
     * Setup expectations: Google Drive is opened and click the action button of search result file.
     *
     * <p>This method will click the download button
     */
    public void clickDownloadFileButton();

    /**
     * Setup expectations: Existing file is removed before download. Click download file button.
     *
     * <p>This method will check if file is created in Download folder.
     *
     * @param fileName file to search
     * @return true if the file is created in Download folder
     */
    public boolean isStartDownloading(String fileName);

    /**
     * Setup expectations: Start downloading file.
     *
     * <p>This method will check if download is still processing.
     *
     * @param fileName file to download
     * @return true if the file modified date is still updating.
     */
    public boolean isDownloading(String fileName);

    /**
     * Setup expectations: File is downloaded in Download folder.
     *
     * <p>This method will remove the existing file in Download folder.
     *
     * @param fileName file to download
     */
    public void removeDownloadedFile(String fileName);

    /**
     * Setup expectations: Google Drive is open and navigation button is visible.
     *
     * <p>This method will click into settings page and click clear cache.
     */
    public void clearCache();

    /**
     * Setup expectations: Google Drive is open and file list is visible.
     *
     * <p>This method will click on the first file.
     */
    public void openFile();

    /**
     * Setup expectations: Google Drive is open to a scrollable recycler or viewport
     *
     * <p>This method will scroll the page in the direction and amount passed in
     *
     * @return true if scrollable view is findable, or false if not.
     */
    public boolean scrollPage(Direction dir, float value);

    /**
     * Setup expectations: on a doc page without scrollable recycler or viewport
     *
     * <p>Scroll vertically up or down
     */
    public void scrollVert(Direction dir);

    /**
     * Setup expectations: Google Drive is open and Files tab is selected at the bottom
     *
     * @param fileName File name to check whether it exist or not
     */
    boolean doesFileExist(String fileName);

    /**
     * Setup expectations: Google Drive is open and Files tab is selected at the bottom
     *
     * @param fileName File name to upload from local device documents
     */
    void uploadFileFromLocalDeviceDocuments(String fileName);

    /**
     * Setup expectations: Google Drive is open and Files tab is selected at the bottom
     *
     * <p>Given file name is visible and clickable to open
     *
     * @param fileName File name to open
     */
    void openFile(String fileName);

    /**
     * Setup expectations: Google Drive is open and Click on file to open in Docs Editor
     *
     * <p>Waits until the document is fully opened in Docs Editor
     */
    void waitForDocumentToOpen();
}
