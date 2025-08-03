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

package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.media.RemoteController;
import android.media.RemoteController.OnClientUpdateListener;
import android.platform.test.annotations.AppModeFull;
import android.view.KeyEvent;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link RemoteController}.
 */
@NonMainlineTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class RemoteControllerTest {

    private static final Set<Integer> MEDIA_KEY_EVENT = new HashSet<Integer>();
    static {
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PLAY);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PAUSE);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_HEADSETHOOK);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_STOP);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_NEXT);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_REWIND);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_RECORD);
        MEDIA_KEY_EVENT.add(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
    }

    static OnClientUpdateListener sListener = new OnClientUpdateListener() {
            @Override
            public void onClientChange(boolean clearing) {}
            @Override
            public void onClientPlaybackStateUpdate(int state) {}
            @Override
            public void onClientPlaybackStateUpdate(
                    int state, long stateChangeTimeMs, long currentPosMs, float speed) {}
            @Override
            public void onClientTransportControlUpdate(int transportControlFlags) {}
            @Override
            public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {}
        };

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    private RemoteController createRemoteController() {
        return new RemoteController(mContext, sListener);
    }

    @UiThreadTest
    @Test
    public void testGetEstimatedMediaPosition() {
        assertThat(createRemoteController().getEstimatedMediaPosition() < 0).isTrue();
    }

    @UiThreadTest
    @Test
    public void testSendMediaKeyEvent() {
        RemoteController remoteController = createRemoteController();
        for (Integer mediaKeyEvent : MEDIA_KEY_EVENT) {
            assertThat(remoteController.sendMediaKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent))).isFalse();
        }
    }

    @UiThreadTest
    @Test
    public void testSeekTo_negativeValues() {
        assertThrows("timeMs must be >= 0", IllegalArgumentException.class,
                () -> createRemoteController().seekTo(-1));
    }

    @UiThreadTest
    @Test
    public void testSeekTo() {
        assertThat(createRemoteController().seekTo(0)).isTrue();
    }

    @UiThreadTest
    @Test
    public void testSetArtworkConfiguration() {
        assertThat(createRemoteController().setArtworkConfiguration(1, 1)).isTrue();
    }

    @UiThreadTest
    @Test
    public void testClearArtworkConfiguration() {
        assertThat(createRemoteController().clearArtworkConfiguration()).isTrue();
    }

    @UiThreadTest
    @Test
    public void testSetSynchronizationMode_unregisteredRemoteController() {
        RemoteController remoteController = createRemoteController();
        assertThat(remoteController.setSynchronizationMode(
                RemoteController.POSITION_SYNCHRONIZATION_NONE)).isFalse();
        assertThat(remoteController.setSynchronizationMode(
                RemoteController.POSITION_SYNCHRONIZATION_CHECK)).isFalse();
    }

    @UiThreadTest
    @Test
    public void testEditMetadata() {
        assertThat(createRemoteController().editMetadata()).isNotNull();
    }

    @UiThreadTest
    @Test
    public void testOnClientUpdateListenerUnchanged() throws Exception {
        Map<String, List<Method>> methodMap = new HashMap<String, List<Method>>();
        for (Method method : sListener.getClass().getDeclaredMethods()) {
            if (!methodMap.containsKey(method.getName())) {
                methodMap.put(method.getName(), new ArrayList<Method>());
            }
            methodMap.get(method.getName()).add(method);
        }

        for (Method method : OnClientUpdateListener.class.getDeclaredMethods()) {
            assertWithMessage("Method not found: " + method.getName())
                    .that(methodMap.containsKey(method.getName())).isTrue();
            List<Method> implementedMethodList = methodMap.get(method.getName());
            assertWithMessage("Method signature changed: " + method)
                    .that(matchMethod(method, implementedMethodList)).isTrue();
        }
    }

    private static boolean matchMethod(Method method, List<Method> potentialMatches) {
        for (Method potentialMatch : potentialMatches) {
            if (method.getName().equals(potentialMatch.getName())
                    && method.getReturnType().equals(potentialMatch.getReturnType())
                    && Arrays.equals(method.getTypeParameters(),
                    potentialMatch.getTypeParameters())) {
                return true;
            }
        }
        return false;
    }
}
