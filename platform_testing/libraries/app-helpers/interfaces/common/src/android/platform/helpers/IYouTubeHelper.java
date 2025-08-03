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

package android.platform.helpers;

import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;

public interface IYouTubeHelper extends IAppHelper {

    public enum VideoQuality {
        QUALITY_AUTO("Auto"),
        QUALITY_144p("144p"),
        QUALITY_240p("240p"),
        QUALITY_360p("360p"),
        QUALITY_480p("480p"),
        QUALITY_720p("720p"),
        QUALITY_1080p("1080p"),
        QUALITY_MAX("max"),
        QUALITY_HIGHER_QUALITY("Higher picture quality"),
        QUALITY_DATA_SAVER("Data saver"),
        QUALITY_ADVANCED("Advanced");

        private final String text;

        VideoQuality(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    };

    /**
     * Setup expectations: YouTube app is open.
     *
     * <p>This method keeps pressing the back button until YouTube is on the home page.
     */
    public void goToHomePage();

    /**
     * Setup expectations: YouTube app is open.
     *
     * <p>This method keeps pressing the back button until library page button is accessible and
     * clicked.
     */
    public void goToLibraryPage();

    /**
     * Setup expectations: YouTube is on the home page.
     *
     * <p>This method scrolls to the top of the home page and clicks the search button.
     */
    public void goToSearchPage();

    /**
     * Setup expectations: YouTube is on the non-fullscreen video player page.
     *
     * <p>This method changes the video player to fullscreen mode. Has no effect if the video player
     * is already in fullscreen mode.
     */
    public void goToFullscreenMode();

    /**
     * Setup expectations: YouTube is on the home page.
     *
     * <p>This method selects a video on the home page and blocks until the video is playing.
     */
    public void playHomePageVideo();

    /**
     * Setup expectations: YouTube is on the search results page.
     *
     * <p>This method selects a search result video and blocks until the video is playing.
     */
    public void playSearchResultPageVideo();

    /**
     * Setup expectations: Recently opened a video in the YouTube app.
     *
     * <p>This method blocks until the video has loaded.
     *
     * @param timeout wait timeout in milliseconds
     * @return true if video loaded within the timeout, false otherwise
     */
    public boolean waitForVideoToLoad(long timeout);

    /**
     * Setup expectations: Recently initiated a search query in the YouTube app.
     *
     * <p>This method blocks until search results appear.
     *
     * @param timeout wait timeout in milliseconds
     * @return true if search results appeared within timeout, false otherwise
     */
    public boolean waitForSearchResults(long timeout);

    /**
     * Setup expectations: YouTube is on the video player page.
     *
     * <p>This method changes the video quality of the current video.
     *
     * @param quality the desired video quality
     * @see IYouTubeHelper.VideoQuality
     */
    public void setVideoQuality(VideoQuality quality);

    /**
     * Setup expectations: YouTube is on the video player page.
     *
     * <p>This method pauses the video if it is playing.
     */
    public void pauseVideo();

    /**
     * Setup expectations: YouTube is on the video player page.
     *
     * <p>This method resumes the video if it is paused.
     */
    public void resumeVideo();

    /**
     * Setup expectations: Search page is open.
     *
     * <p>This method inputs the search keyword and clicks search button.
     *
     * <p>
     *
     * @param query The keyword to search.
     */
    public default void searchVideo(String query) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Setup expectations: YouTube is on the fullscreen video player page.
     *
     * <p>This method changes the video player to non-fullscreen mode.
     */
    public default void exitFullScreenMode() {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Setup expectation: YouTube is open on home page.
     *
     * <p>Scroll the home page by specified direction.
     *
     * @param direction The direction of the scroll, must be UP or DOWN.
     */
    public void scrollHomePage(Direction direction);

    /**
     * Setup expectations: YouTube app is open on library page.
     *
     * <p>This method will scroll the library page in the specified direction.
     *
     * @param direction The direction of the scroll, must be UP or DOWN.
     */
    public void scrollLibraryPage(Direction direction);

    /**
     * Setup expectations: YouTube app is open.
     *
     * <p>This method will get the current page for scrolling. It's usually the video list.
     *
     * <p>The UiObject2 for YouTube to get scroll view.
     */
    public UiObject2 getScrollView();

    /**
     * Setup expectations: YouTube app is open.
     *
     * <p>This method will swipe the page in the specified direction.
     *
     * @param container UiObject2 of scroll container from getScrollView().
     * @param dir Direction in which to scroll, must be UP or DOWN.
     * @param percent Percent of page to swipe.
     */
    public default void swipePage(UiObject2 container, Direction dir, float percent) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Setup expectations: YouTube is open on a page.
     *
     * <p>This method will fling the page as directed and block until idle.
     */
    public abstract void flingPage(Direction dir);

    /**
     * Setup expectations: Youtube is open.
     *
     * <p>This method back to top on the homepage.
     */
    public void backToTopOnHomepage();

    /**
     * Setup expectation: YouTube is open on home page.
     *
     * <p>Press home button to initiate PIP.
     */
    public void goToYouTubePip();

    /**
     * Setup expectation: YouTube is in PiP.
     *
     * <p>It presses YouTube PiP view twice to return to the main app.
     */
    public void backFromYouTubeFromPip();

    /**
     * Setup expectation: YouTube is on the library page.
     *
     * <p>presses Your videos tab.
     */
    public void goToYourVideos();

    /**
     * Setup expectation: YouTube is on the Your videos page.
     *
     * <p>presses the video name to play.
     */
    public void playYourVideo(String videoName);

    /**
     * Setup expectation: YouTube is in the PIP mode on launcher.
     *
     * @return true if pip mode in launhcer.
     */
    public boolean isYouTubePipModeOnLauncher();

    /**
     * Setup expectation: YouTube is open and the video player is visible on any page.
     *
     * @return true if the video is playing.
     */
    public boolean isVideoPlaying();

    /**
     * Setup expectation: YouTube app is open.
     *
     * @return true if the floaty player is visible.
     */
    public boolean isFloatyPlayerVisible();

    /**
     * Setup expectation: YouTube is open and the floaty player is visible.
     *
     * <p>Clicks the floaty bar's close button to close video player.
     */
    public void closeFloatyPlayer();

    /**
     * Setup expectation: YouTube is on the video player page.
     *
     * @return YouTube player object.
     */
    public default UiObject2 getYouTubePlayer() {
        throw new UnsupportedOperationException("Not yet implemented.");
    }
}
