/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.helpers.media;

import android.app.Instrumentation;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.platform.test.scenario.tapl_common.Gestures;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaController {
    private static final String TAG = MediaController.class.getSimpleName();
    private static final String PKG = "com.android.systemui";
    private static final String HIDE_BTN_RES = "dismiss";
    private static final BySelector PLAY_BTN_SELECTOR =
        By.res(PKG, "actionPlayPause").descContains("Play");
    private static final BySelector PAUSE_BTN_SELECTOR =
        By.res(PKG, "actionPlayPause").descContains("Pause");
    private static final BySelector SKIP_NEXT_BTN_SELECTOR =
        By.res(PKG, "actionNext").descContains("Next");
    private static final BySelector SKIP_PREV_BTN_SELECTOR =
        By.res(PKG, "actionPrev").descContains("Previous");
    private static final int WAIT_TIME_MILLIS = 10_000;
    private static final int LONG_PRESS_TIME_MILLIS = 1_000;
    private static final long UI_WAIT_TIMEOUT = 3_000;

    private final UiObject2 mUiObject;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice mDevice = UiDevice.getInstance(mInstrumentation);
    private final List<Integer> mStateChanges;
    private Runnable mStateListener;
    private static final Object sStateListenerLock = new Object();

    MediaController(MediaInstrumentation media, UiObject2 uiObject) {
        media.addMediaSessionStateChangedListeners(this::onMediaSessionStageChanged);
        mUiObject = uiObject;
        mStateChanges = new ArrayList<>();
    }

    public void play() {
        runToNextState(
                () -> {
                    mInstrumentation.getUiAutomation().clearCache();
                    mUiObject.wait(Until.findObject(PLAY_BTN_SELECTOR), WAIT_TIME_MILLIS).click();
                },
                PlaybackState.STATE_PLAYING);
    }

    public void pause() {
        runToNextState(
                () -> {
                    mInstrumentation.getUiAutomation().clearCache();
                    Gestures.click(
                            mUiObject.wait(Until.findObject(PAUSE_BTN_SELECTOR), WAIT_TIME_MILLIS),
                            "Pause button");
                },
                PlaybackState.STATE_PAUSED);
    }

    public void skipToNext() {
        runToNextState(
                () -> Gestures.click(
                        mUiObject.wait(Until.findObject(SKIP_NEXT_BTN_SELECTOR), WAIT_TIME_MILLIS),
                        "Next button"),
                PlaybackState.STATE_SKIPPING_TO_NEXT);
    }

    public void skipToPrev() {
        runToNextState(
                () -> Gestures.click(
                        mUiObject.wait(Until.findObject(SKIP_PREV_BTN_SELECTOR), WAIT_TIME_MILLIS),
                        "Previous button"),
                PlaybackState.STATE_SKIPPING_TO_PREVIOUS);
    }

    private void runToNextState(Runnable runnable, int state) {
        mStateChanges.clear();
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (sStateListenerLock) {
            mStateListener = latch::countDown;
        }
        runnable.run();
        try {
            if (!latch.await(WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException(
                        "PlaybackState didn't change to state:" + state + " and timeout.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
        if (!mStateChanges.contains(state)) {
            throw new RuntimeException(String.format("Fail to run to next state(%d).", state));
        }
    }

    private void onMediaSessionStageChanged(int state) {
        mStateChanges.add(state);
        if (mStateListener != null) {
            synchronized (sStateListenerLock) {
                mStateListener.run();
                mStateListener = null;
            }
        }
    }

    public String title() {
        UiObject2 header =
            mUiObject.wait(Until.findObject(By.res(PKG, "header_title")), WAIT_TIME_MILLIS);
        if (header == null) {
            return "";
        }
        return header.getText();
    }

    /**
     * Long press for {@link #LONG_PRESS_TIME_MILLIS} ms on UMO then clik the hide button.
     */
    public void longPressAndHide() {
        if (mUiObject == null) {
            throw new RuntimeException("UMO should exist to do long press.");
        }

        mUiObject.click(LONG_PRESS_TIME_MILLIS);
        UiObject2 hideBtn = mUiObject.wait(
            Until.findObject(By.res(PKG, HIDE_BTN_RES)), WAIT_TIME_MILLIS);
        if (hideBtn == null) {
            throw new RuntimeException("Hide button should exist after long press on UMO.");
        }
        hideBtn.clickAndWait(Until.newWindow(), UI_WAIT_TIMEOUT);
    }

    /**
     * Checks if the current media session is using the given MediaMetadata.
     *
     * @param meta MediaMetadata to get media title and artist.
     * @return boolean
     */
    public boolean hasMetadata(MediaMetadata meta) {
        Log.d(
                TAG,
                "[Check metadata] hasMetadata: By.header_title="
                        + meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                        + "By.header_artist="
                        + meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
        final BySelector mediaTitleSelector =
            By.res(PKG, "header_title").text(meta.getString(MediaMetadata.METADATA_KEY_TITLE));
        final BySelector mediaArtistSelector =
            By.res(PKG, "header_artist")
                .text(meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
        mInstrumentation.getUiAutomation().clearCache();
        final boolean titleCheckResult = mUiObject.hasObject(mediaTitleSelector);
        final boolean artistCheckResult = mUiObject.hasObject(mediaArtistSelector);

        Log.d(
                TAG,
                "[Check metadata] title: " + titleCheckResult + ". artist: " + artistCheckResult);
        return titleCheckResult && artistCheckResult;
    }

    public boolean swipe(Direction direction) {
        Rect bound = mUiObject.getVisibleBounds();
        final int startX;
        final int endX;
        switch (direction) {
            case LEFT:
                startX = (bound.right + bound.centerX()) / 2;
                endX = bound.left;
                break;
            case RIGHT:
                startX = (bound.left + bound.centerX()) / 2;
                endX = bound.right;
                break;
            default:
                throw new RuntimeException(
                    String.format("swipe to %s on UMO isn't supported.", direction));
        }
        return mDevice.swipe(startX, bound.centerY(), endX, bound.centerY(), 10);
    }

    public Rect getVisibleBound() {
        return mUiObject.getVisibleBounds();
    }
}
