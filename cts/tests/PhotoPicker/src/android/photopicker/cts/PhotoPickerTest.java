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

package android.photopicker.cts;

import static android.photopicker.cts.util.PhotoPickerComponentUtils.GET_CONTENT_ACTIVITY_COMPONENT;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImageWithUnknownMimeType;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createMj2VideosAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createMpegVideo;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createSvgImage;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideoWithUnknownMimeType;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideosAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.clearPackageData;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.getDocumentsUiPackageName;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddOrSelectButton;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertContainsMimeType;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertExtension;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertMimeType;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPersistedGrant;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.provider.MediaStore.ACTION_PICK_IMAGES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ClipData;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.photopicker.cts.util.PhotoPickerComponentUtils;
import android.provider.MediaStore;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Photo Picker Device only tests for common flows.
 */
@RunWith(Parameterized.class)
public class PhotoPickerTest extends PhotoPickerBaseTest {

    @Parameter(0)
    public String mAction;

    @Parameters(name = "intent={0}")
    public static Iterable<? extends Object> data() {
        return getTestParameters();
    }

    private List<Uri> mUriList = new ArrayList<>();

    private static int sGetContentTakeOverActivityAliasState;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sGetContentTakeOverActivityAliasState = PhotoPickerComponentUtils
                .enableAndGetOldState(GET_CONTENT_ACTIVITY_COMPONENT);
        clearPackageData(getDocumentsUiPackageName());
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();

        if (mActivity != null) {
            mActivity.finish();
        }

        PhotoPickerComponentUtils.setState(GET_CONTENT_ACTIVITY_COMPONENT,
                sGetContentTakeOverActivityAliasState);
    }

    @Test
    public void testSingleSelect() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        final UiObject item = findItemList(itemCount).get(0);
        clickAndWait(sDevice, item);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(mAction, uri, mContext.getUserId());
        assertPersistedGrant(uri, mContext.getContentResolver());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testSingleSelectForFavoritesAlbum() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId(),
                /* isFavorite */ true));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        UiObject albumsTab = sDevice.findObject(new UiSelector().text(
                "Albums"));
        clickAndWait(sDevice, albumsTab);
        final UiObject album = findItemList(1).get(0);
        clickAndWait(sDevice, album);

        final UiObject item = findItemList(itemCount).get(0);
        clickAndWait(sDevice, item);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(mAction, uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testLaunchPreviewMultipleForVideoAlbum() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        UiObject albumsTab = sDevice.findObject(new UiSelector().text(
                "Albums"));
        clickAndWait(sDevice, albumsTab);
        final UiObject album = findItemList(1).get(0);
        clickAndWait(sDevice, album);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findViewSelectedButton());

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);
    }

    @Test
    public void testSingleSelectWithPreview() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        final UiObject item = findItemList(itemCount).get(0);
        item.longClick();
        sDevice.waitForIdle();

        final UiObject addButton = findPreviewAddOrSelectButton();
        assertThat(addButton.waitForExists(1000)).isTrue();
        clickAndWait(sDevice, addButton);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(mAction, uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect() throws Exception {
        final int imageCount = 4;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));
        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_longPress() throws Exception {
        final int videoCount = 3;
        mUriList.addAll(createMj2VideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(videoCount);

        // Select one item from Photo grid
        clickAndWait(sDevice, itemList.get(0));

        // Preview the item
        UiObject item = itemList.get(1);
        item.longClick();
        sDevice.waitForIdle();

        final UiObject addOrSelectButton = findPreviewAddOrSelectButton();
        assertWithMessage("Timed out waiting for AddOrSelectButton to appear")
                .that(addOrSelectButton.waitForExists(1000)).isTrue();

        // Select the item from Preview
        clickAndWait(sDevice, addOrSelectButton);

        sDevice.pressBack();

        // Select one more item from Photo grid
        clickAndWait(sDevice, itemList.get(2));

        clickAndWait(sDevice, findAddButton());

        // Verify that all 3 items are returned
        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(3);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_preview() throws Exception {
        final int imageCount = 4;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findViewSelectedButton());

        // Swipe left three times
        swipeLeftAndWait();
        swipeLeftAndWait();
        swipeLeftAndWait();

        // Deselect one item
        clickAndWait(sDevice, findPreviewSelectedCheckButton());

        // Return selected items
        clickAndWait(sDevice, findPreviewAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount - 1);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonInitial() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 1);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Initial state of the mute Button
        // Check that initial state of mute button is mute, i.e., volume off
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Click Mute Button
        // Click to unmute the audio
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        // Check that mute button state is unmute, i.e., it shows `volume up` icon
        assertMuteButtonState(muteButton, /* isMuted */ false);
        // Click on the muteButton and check that mute button status is now 'mute'
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Click on the muteButton and check that mute button status is now unmute
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Test 3: Next preview resumes mute state
        // Go back and launch preview again
        sDevice.pressBack();
        clickAndWait(sDevice, findViewSelectedButton());

        waitForBinderCallsToComplete();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonOnSwipe() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();
        final UiObject playerView = findPlayerView();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Swipe resumes mute state, with state of the button is 'volume off' / 'mute'
        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Swipe to next page and check that muteButton is in mute state.
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // set-up and wait for player controls to be sticky
        setUpAndAssertStickyPlayerControls(playerView, playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Swipe resumes mute state, with state of mute button 'volume up' / 'unmute'
        // Click muteButton again to check the next video resumes the previous video's mute state
        clickAndWait(sDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);
        // check that next video resumed previous video's mute state
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // Wait for 1s before checking Play/Pause button's visibility
        playPauseButton.waitForExists(1000);
        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testVideoPreviewAudioFocus() throws Exception {
        final int[] focusStateForTest = new int[1];
        final AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioFocusRequest audioFocusRequest =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.USAGE_MEDIA)
                        .setUsage(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build())
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        focusStateForTest[0] = focusChange;
                    }
                })
                .build();

        // Request AudioFocus
        assertWithMessage("Expected requestAudioFocus result")
                .that(audioManager.requestAudioFocus(audioFocusRequest))
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        // Launch Preview
        launchPreviewMultipleWithVideos(/* videoCount */ 2);
        // Video preview launches in mute mode, hence, test's audio focus shouldn't be lost when
        // video preview starts
        assertThat(focusStateForTest[0]).isEqualTo(0);

        final UiObject muteButton = findMuteButton();
        // unmute the audio of video preview
        clickAndWait(sDevice, muteButton);

        // Remote video preview involves binder calls
        // Wait for Binder calls to complete and device to be idle
        MediaStore.waitForIdle(mContext.getContentResolver());
        sDevice.waitForIdle();

        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Verify that test lost the audio focus because PhotoPicker has requested audio focus now.
        assertThat(focusStateForTest[0]).isEqualTo(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        // Reset the focusStateForTest to verify test loses audio focus when video preview is
        // launched with unmute state
        focusStateForTest[0] = 0;
        // Abandon the audio focus before requesting again. This is necessary to reduce test flakes
        audioManager.abandonAudioFocusRequest(audioFocusRequest);
        // Request AudioFocus from test again
        assertWithMessage("Expected requestAudioFocus result")
                .that(audioManager.requestAudioFocus(audioFocusRequest))
                        .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        // Wait for PhotoPicker to lose Audio Focus
        findPlayButton().waitForExists(SHORT_TIMEOUT);
        // Test requesting audio focus will make PhotoPicker lose audio focus, Verify video is
        // paused when PhotoPicker loses audio focus.
        assertWithMessage("PlayPause button's content description")
                .that(findPlayPauseButton().getContentDescription())
                .isEqualTo("Play");

        // Swipe to next video and verify preview gains audio focus
        swipeLeftAndWait();
        findPauseButton().waitForExists(SHORT_TIMEOUT);
        // Video preview is now in unmute mode. Hence, PhotoPicker will request audio focus. Verify
        // that test lost the audio focus.
        assertThat(focusStateForTest[0]).isEqualTo(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    }

    // Note: This test works independent of the device accessibility state and does not test the
    // controls' auto-hide feature which requires the accessibility state to be disabled.
    @Test
    public void testMultiSelect_previewVideoControlsVisibility() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 2);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        final UiObject playerView = findPlayerView();
        // Click on StyledPlayerView to make the video controls visible
        // Don't click in the center else it may pause the video and hide the controls.
        playerView.clickBottomRight();
        sDevice.waitForIdle();
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Wait for 1s and check that controls are still visible
        assertPlayerControlsDontAutoHide(playPauseButton, muteButton);

        // Swipe left to the next video
        swipeLeftAndWait();

        // Click on the StyledPlayerView and check that controls appear
        // Don't click in the center else it may pause the video and hide the controls.
        playerView.clickBottomRight();
        sDevice.waitForIdle();
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createMj2VideosAndGetUris(videoCount, mContext.getUserId()));
        final int imageCount = 1;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        final String mimeType = "video/mj2";

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        intent.setType(mimeType);
        launchPhotoPickerForIntent(intent);

        // find all items
        final List<UiObject> itemList = findItemList(-1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(videoCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    @Test
    public void testExtraMimeTypeFilter() throws Exception {
        final int mj2VideoCount = 2;
        // Creates 2 videos with mime type: "video/mj2"
        mUriList.addAll(createMj2VideosAndGetUris(mj2VideoCount, mContext.getUserId()));

        final int mp4VideoCount = 3;
        // Creates 3 videos with mime type: "video/mp4"
        mUriList.addAll(createVideosAndGetUris(mp4VideoCount, mContext.getUserId()));

        final int imageCount = 4;
        // Creates 4 images with mime type: "image/dng"
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);

        if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
            intent.setType("*/*");
        }
        final String[] mimeTypes = new String[]{"video/mj2", "image/dng"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        launchPhotoPickerForIntent(intent);

        final int totalCount = mj2VideoCount + imageCount;
        final List<UiObject> itemList = findItemList(totalCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(totalCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Expected number of items returned to be: " + itemCount)
                .that(clipData.getItemCount()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertContainsMimeType(uri, mimeTypes);
        }
    }

    @Test
    public void testMimeTypeFilterPriority() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createMj2VideosAndGetUris(videoCount, mContext.getUserId()));
        final int imageCount = 1;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        // setType has lower priority than EXTRA_MIME_TYPES filters.
        intent.setType("image/*");
        final String mimeType = "video/mj2";
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {mimeType});
        launchPhotoPickerForIntent(intent);

        // find all items
        final List<UiObject> itemList = findItemList(-1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(videoCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Expected number of items returned to be: " + itemCount)
                .that(clipData.getItemCount()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(mAction, uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    @Test
    public void testPickerUriFileExtensions() throws Exception {
        // 1. Create test media items
        mUriList.add(createSvgImage(mContext.getUserId()));
        mUriList.add(createImageWithUnknownMimeType(mContext.getUserId()));
        mUriList.add(createMpegVideo(mContext.getUserId()));
        mUriList.add(createVideoWithUnknownMimeType(mContext.getUserId()));

        final int expectedItemCount = mUriList.size();

        final Map<String, String> mimeTypeToExpectedExtensionMap = Map.of(
                "image/svg+xml", "svg",
                "image/foo", "jpg",
                "video/mpeg", "mpeg",
                "video/foo", "mp4"
        );

        // 2. Launch Picker in multi-select mode for the test mime types
        final Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        // 3. Add all items
        final List<UiObject> itemList = findItemList(expectedItemCount);
        final int itemCount = itemList.size();
        assertWithMessage("Unexpected number of media items found in the picker ui")
                .that(itemCount)
                .isEqualTo(expectedItemCount);

        for (UiObject item : itemList) {
            clickAndWait(sDevice, item);
        }
        clickAndWait(sDevice, findAddButton());

        // 4. Get the activity result data to extract the picker uris
        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Unexpected number of items returned from the picker activity")
                .that(clipData.getItemCount())
                .isEqualTo(itemCount);

        // 5. Assert the picker uri file extension as expected for each item
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertExtension(uri, mimeTypeToExpectedExtensionMap);
        }
    }

    private void assertMuteButtonState(UiObject muteButton, boolean isMuted)
            throws UiObjectNotFoundException {
        // We use content description to assert the state of the mute button, there is no other way
        // to test this.
        final String expectedContentDescription = isMuted ? "Unmute video" : "Mute video";
        final String assertMessage =
                "Expected mute button content description to be " + expectedContentDescription;
        assertWithMessage(assertMessage).that(muteButton.getContentDescription())
                .isEqualTo(expectedContentDescription);
    }

    private void launchPreviewMultipleWithVideos(int videoCount) throws  Exception {
        mUriList.addAll(createVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            clickAndWait(sDevice, itemList.get(i));
        }

        clickAndWait(sDevice, findViewSelectedButton());

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);

        waitForBinderCallsToComplete();
    }

    private void waitForBinderCallsToComplete() {
        // Wait for Binder calls to complete and device to be idle
        MediaStore.waitForIdle(mContext.getContentResolver());
        sDevice.waitForIdle();
    }

    private void setUpAndAssertStickyPlayerControls(UiObject playerView, UiObject playPauseButton,
            UiObject muteButton) throws Exception {
        // Wait for 1s for player view to exist
        playerView.waitForExists(1000);
        // Wait for 1s or Play/Pause button to hide
        playPauseButton.waitUntilGone(1000);
        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(sDevice, playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);
    }

    private void assertPlayerControlsVisible(UiObject playPauseButton, UiObject muteButton) {
        assertVisible(playPauseButton, "Expected play/pause button to be visible");
        assertVisible(muteButton, "Expected mute button to be visible");
    }

    private void assertPlayerControlsDontAutoHide(UiObject playPauseButton, UiObject muteButton) {
        assertWithMessage("Expected play/pause button to not auto hide in 1s")
                .that(playPauseButton.waitUntilGone(1100)).isFalse();
        assertVisible(muteButton, "Expected mute button to be still visible after 1s");
    }

    private void assertVisible(UiObject button, String message) {
        assertWithMessage(message).that(button.exists()).isTrue();
    }

    private static UiObject findViewSelectedButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_view_selected"));
    }

    private static UiObject findPreviewSelectedCheckButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_selected_check_button"));
    }


    private static UiObject findPlayerView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_player_view"));
    }

    private static UiObject findMuteButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_mute"));
    }

    private static UiObject findPlayPauseButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/exo_play_pause"));
    }

    private static UiObject findPauseButton() {
        return new UiObject(new UiSelector().descriptionContains("Pause"));
    }

    private static UiObject findPlayButton() {
        return new UiObject(new UiSelector().descriptionContains("Play"));
    }

    private static UiObject findPreviewVideoImageView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_video_image"));
    }

    private void swipeLeftAndWait() {
        final int width = sDevice.getDisplayWidth();
        final int height = sDevice.getDisplayHeight();
        sDevice.swipe(15 * width / 20, height / 2, width / 20, height / 2, 10);
        sDevice.waitForIdle();
    }

    private static List<String> getTestParameters() {
        return Arrays.asList(
                ACTION_PICK_IMAGES,
                Intent.ACTION_GET_CONTENT
        );
    }

    private void addMultipleSelectionFlag(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_PICK_IMAGES:
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
                        MediaStore.getPickImagesMaxLimit());
                break;
            case Intent.ACTION_GET_CONTENT:
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                break;
            default:
                // do nothing
        }
    }

    private void launchPhotoPickerForIntent(Intent intent) throws Exception {
        // GET_CONTENT needs to have setType
        if (Intent.ACTION_GET_CONTENT.equals(intent.getAction()) && intent.getType() == null) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }

        mActivity.startActivityForResult(intent, REQUEST_CODE);
    }
}
