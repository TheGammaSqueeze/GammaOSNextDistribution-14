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

package android.media.misc.cts;

import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.ArrayList;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

/**
 * Test {@link android.media.session.PlaybackState}.
 */
@AppModeFull(reason = "TODO: evaluate and port to instant")
public class PlaybackStateTest extends AndroidTestCase {

    private static final long TEST_POSITION = 20000L;
    private static final long TEST_BUFFERED_POSITION = 15000L;
    private static final long TEST_UPDATE_TIME = 100000L;
    private static final long TEST_ACTIONS =
            PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO;
    private static final long TEST_QUEUE_ITEM_ID = 23L;
    private static final float TEST_PLAYBACK_SPEED = 3.0f;
    private static final float TEST_PLAYBACK_SPEED_ON_REWIND = -2.0f;
    private static final float DELTA = 1e-7f;

    private static final String TEST_ERROR_MSG = "test-error-msg";
    private static final String TEST_CUSTOM_ACTION = "test-custom-action";
    private static final String TEST_CUSTOM_ACTION_NAME = "test-custom-action-name";
    private static final int TEST_ICON_RESOURCE_ID = android.R.drawable.ic_media_next;

    private static final String EXTRAS_KEY = "test-key";
    private static final String EXTRAS_VALUE = "test-value";

    private static final String TAG = "PlaybackStateTest";

    private static boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    /**
     * Test default values of {@link PlaybackState}.
     */
    public void testBuilder() {
        PlaybackState state = new PlaybackState.Builder().build();

        assertEquals(new ArrayList<PlaybackState.CustomAction>(), state.getCustomActions());
        assertEquals(0, state.getState());
        assertEquals(0L, state.getPosition());
        assertEquals(0L, state.getBufferedPosition());
        assertEquals(0.0f, state.getPlaybackSpeed(), DELTA);
        assertEquals(0L, state.getActions());
        assertNull(state.getErrorMessage());
        assertEquals(0L, state.getLastPositionUpdateTime());
        assertEquals(MediaSession.QueueItem.UNKNOWN_ID, state.getActiveQueueItemId());
        assertNull(state.getExtras());
    }

    /**
     * Test following setter methods of {@link PlaybackState.Builder}:
     * {@link PlaybackState.Builder#setState(int, long, float)}
     * {@link PlaybackState.Builder#setActions(long)}
     * {@link PlaybackState.Builder#setActiveQueueItemId(long)}
     * {@link PlaybackState.Builder#setBufferedPosition(long)}
     * {@link PlaybackState.Builder#setErrorMessage(CharSequence)}
     * {@link PlaybackState.Builder#setExtras(Bundle)}
     */
    public void testBuilder_setterMethods() {
        Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, TEST_POSITION, TEST_PLAYBACK_SPEED)
                .setActions(TEST_ACTIONS)
                .setActiveQueueItemId(TEST_QUEUE_ITEM_ID)
                .setBufferedPosition(TEST_BUFFERED_POSITION)
                .setErrorMessage(TEST_ERROR_MSG)
                .setExtras(extras)
                .build();
        assertEquals(PlaybackState.STATE_PLAYING, state.getState());
        assertEquals(TEST_POSITION, state.getPosition());
        assertEquals(TEST_PLAYBACK_SPEED, state.getPlaybackSpeed(), DELTA);
        assertEquals(TEST_ACTIONS, state.getActions());
        assertEquals(TEST_QUEUE_ITEM_ID, state.getActiveQueueItemId());
        assertEquals(TEST_BUFFERED_POSITION, state.getBufferedPosition());
        assertEquals(TEST_ERROR_MSG, state.getErrorMessage().toString());
        assertEquals(EXTRAS_VALUE, state.getExtras().get(EXTRAS_KEY));
    }

    /**
     * Test {@link PlaybackState.Builder#setState(int, long, float, long)}.
     */
    public void testBuilder_setStateWithUpdateTime() {
        PlaybackState state = new PlaybackState.Builder().setState(
                PlaybackState.STATE_REWINDING, TEST_POSITION,
                TEST_PLAYBACK_SPEED_ON_REWIND, TEST_UPDATE_TIME).build();
        assertEquals(PlaybackState.STATE_REWINDING, state.getState());
        assertEquals(TEST_POSITION, state.getPosition());
        assertEquals(TEST_PLAYBACK_SPEED_ON_REWIND, state.getPlaybackSpeed(), DELTA);
        assertEquals(TEST_UPDATE_TIME, state.getLastPositionUpdateTime());
    }

    /**
     * Test {@link PlaybackState.Builder#addCustomAction(String, String, int)}.
     */
    public void testBuilder_addCustomAction() {
        ArrayList<PlaybackState.CustomAction> actions = new ArrayList<>();
        PlaybackState.Builder builder = new PlaybackState.Builder();

        for (int i = 0; i < 5; i++) {
            actions.add(new PlaybackState.CustomAction.Builder(
                    TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
                    .build());
            builder.addCustomAction(
                    TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i);
        }

        PlaybackState state = builder.build();
        assertEquals(actions.size(), state.getCustomActions().size());
        for (int i = 0; i < actions.size(); i++) {
            assertCustomActionEquals(actions.get(i), state.getCustomActions().get(i));
        }
    }

    /**
     * Test {@link PlaybackState.Builder#addCustomAction(PlaybackState.CustomAction)}.
     */
    public void testBuilder_addCustomActionWithCustomActionObject() {
        Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

        ArrayList<PlaybackState.CustomAction> actions = new ArrayList<>();
        PlaybackState.Builder builder = new PlaybackState.Builder();

        for (int i = 0; i < 5; i++) {
            actions.add(new PlaybackState.CustomAction.Builder(
                    TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
                    .setExtras(extras)
                    .build());
            builder.addCustomAction(new PlaybackState.CustomAction.Builder(
                    TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
                    .setExtras(extras)
                    .build());
        }

        PlaybackState state = builder.build();
        assertEquals(actions.size(), state.getCustomActions().size());
        for (int i = 0; i < actions.size(); i++) {
            assertCustomActionEquals(actions.get(i), state.getCustomActions().get(i));
        }
    }

    /**
     * Test {@link PlaybackState#writeToParcel(Parcel, int)}.
     */
    public void testWriteToParcel() {
        Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

        PlaybackState.CustomAction customAction1 = new PlaybackState.CustomAction
                .Builder(TEST_CUSTOM_ACTION, TEST_CUSTOM_ACTION_NAME, TEST_ICON_RESOURCE_ID)
                .setExtras(extras)
                .build();

        PlaybackState.Builder builder =
                new PlaybackState.Builder().setState(PlaybackState.STATE_CONNECTING, TEST_POSITION,
                TEST_PLAYBACK_SPEED, TEST_UPDATE_TIME)
                .setActions(TEST_ACTIONS)
                .setActiveQueueItemId(TEST_QUEUE_ITEM_ID)
                .setBufferedPosition(TEST_BUFFERED_POSITION)
                .setErrorMessage(TEST_ERROR_MSG)
                .setExtras(extras);

        for (int i = 0; i < 5; i++) {
            builder.addCustomAction(new PlaybackState.CustomAction.Builder(
                    TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
                    .setExtras(extras)
                    .build());
        }
        PlaybackState state = builder.build();

        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PlaybackState stateOut = PlaybackState.CREATOR.createFromParcel(parcel);
        assertEquals(PlaybackState.STATE_CONNECTING, stateOut.getState());
        assertEquals(TEST_POSITION, stateOut.getPosition());
        assertEquals(TEST_PLAYBACK_SPEED, stateOut.getPlaybackSpeed(), DELTA);
        assertEquals(TEST_UPDATE_TIME, stateOut.getLastPositionUpdateTime());
        assertEquals(TEST_BUFFERED_POSITION, stateOut.getBufferedPosition());
        assertEquals(TEST_ACTIONS, stateOut.getActions());
        assertEquals(TEST_QUEUE_ITEM_ID, stateOut.getActiveQueueItemId());
        assertEquals(TEST_ERROR_MSG, stateOut.getErrorMessage());
        assertEquals(EXTRAS_VALUE, stateOut.getExtras().get(EXTRAS_KEY));

        assertEquals(state.getCustomActions().size(), stateOut.getCustomActions().size());
        for (int i = 0; i < state.getCustomActions().size(); i++) {
            assertCustomActionEquals(state.getCustomActions().get(i),
                    stateOut.getCustomActions().get(i));
        }
        parcel.recycle();
    }

    /**
     * Test {@link PlaybackState#describeContents()}.
     */
    public void testDescribeContents() {
        assertEquals(0, new PlaybackState.Builder().build().describeContents());
    }

    /**
     * Test {@link PlaybackState.CustomAction}.
     */
    public void testCustomAction() {
        Bundle extras = new Bundle();
        extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

        // Test Builder/Getters
        PlaybackState.CustomAction customAction = new PlaybackState.CustomAction
                .Builder(TEST_CUSTOM_ACTION, TEST_CUSTOM_ACTION_NAME, TEST_ICON_RESOURCE_ID)
                .setExtras(extras)
                .build();
        assertEquals(TEST_CUSTOM_ACTION, customAction.getAction());
        assertEquals(TEST_CUSTOM_ACTION_NAME, customAction.getName().toString());
        assertEquals(TEST_ICON_RESOURCE_ID, customAction.getIcon());
        assertEquals(EXTRAS_VALUE, customAction.getExtras().get(EXTRAS_KEY));

        // Test describeContents
        assertEquals(0, customAction.describeContents());

        // Test writeToParcel
        Parcel parcel = Parcel.obtain();
        customAction.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertCustomActionEquals(customAction,
                PlaybackState.CustomAction.CREATOR.createFromParcel(parcel));
        parcel.recycle();
    }

    /**
     * Tests that each ACTION_* constant does not overlap.
     */
    public void testActionConstantDoesNotOverlap() {
        long[] actionConstants = new long[] {
                PlaybackState.ACTION_STOP,
                PlaybackState.ACTION_PAUSE,
                PlaybackState.ACTION_PLAY,
                PlaybackState.ACTION_REWIND,
                PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                PlaybackState.ACTION_SKIP_TO_NEXT,
                PlaybackState.ACTION_FAST_FORWARD,
                PlaybackState.ACTION_SET_RATING,
                PlaybackState.ACTION_SEEK_TO,
                PlaybackState.ACTION_PLAY_PAUSE,
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID,
                PlaybackState.ACTION_PLAY_FROM_SEARCH,
                PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM,
                PlaybackState.ACTION_PLAY_FROM_URI,
                PlaybackState.ACTION_PREPARE,
                PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID,
                PlaybackState.ACTION_PREPARE_FROM_SEARCH,
                PlaybackState.ACTION_PREPARE_FROM_URI,
                PlaybackState.ACTION_SET_PLAYBACK_SPEED};

        // Check that the values are not overlapped.
        for (int i = 0; i < actionConstants.length; i++) {
            for (int j = i + 1; j < actionConstants.length; j++) {
                assertEquals(0, actionConstants[i] & actionConstants[j]);
            }
        }
    }

    public void testIsActive() {
        if (!MediaUtils.check(sIsAtLeastS, "testIsActive() requires Android 12")) {
            return;
        }
        int[] activeStates = new int[] {
                PlaybackState.STATE_FAST_FORWARDING,
                PlaybackState.STATE_REWINDING,
                PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackState.STATE_SKIPPING_TO_NEXT,
                PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_CONNECTING,
                PlaybackState.STATE_PLAYING};

        int[] nonActiveStates = new int[] {
                PlaybackState.STATE_NONE,
                PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_ERROR};

        for (int i = 0; i < activeStates.length; i++) {
            PlaybackState activePlaybackState = new PlaybackState.Builder()
                    .setState(activeStates[i], 0, 1.0f)
                    .build();
            assertTrue(activePlaybackState.isActive());
        }
        for (int i = 0; i < nonActiveStates.length; i++) {
            PlaybackState nonActivePlaybackState = new PlaybackState.Builder()
                    .setState(nonActiveStates[i], 0, 1.0f)
                    .build();
            assertFalse(nonActivePlaybackState.isActive());
        }
    }

    private void assertCustomActionEquals(PlaybackState.CustomAction action1,
            PlaybackState.CustomAction action2) {
        assertEquals(action1.getAction(), action2.getAction());
        assertEquals(action1.getName(), action2.getName());
        assertEquals(action1.getIcon(), action2.getIcon());

        // To be the same, two extras should be both null or both not null.
        assertEquals(action1.getExtras() != null, action2.getExtras() != null);
        if (action1.getExtras() != null) {
            assertEquals(action1.getExtras().get(EXTRAS_KEY), action2.getExtras().get(EXTRAS_KEY));
        }
    }
}
