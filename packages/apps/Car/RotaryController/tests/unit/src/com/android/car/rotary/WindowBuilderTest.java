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
package com.android.car.rotary;

import static android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM;
import static android.view.accessibility.AccessibilityWindowInfo.UNDEFINED_WINDOW_ID;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Rect;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class WindowBuilderTest {
    @Test
    public void testBuildDefaultWindow() {
        AccessibilityWindowInfo window = new WindowBuilder().build();
        assertThat(window.getId()).isEqualTo(UNDEFINED_WINDOW_ID);
        assertThat(window.getType()).isEqualTo(0);
        assertThat(window.getDisplayId()).isEqualTo(Display.DEFAULT_DISPLAY);
        assertThat(window.isFocused()).isFalse();
    }

    @Test
    public void testSetId() {
        AccessibilityWindowInfo window = new WindowBuilder().setId(0x42).build();
        assertThat(window.getId()).isEqualTo(0x42);
    }

    @Test
    public void testSetRoot() {
        AccessibilityNodeInfo root = new NodeBuilder(new ArrayList<>()).build();
        AccessibilityWindowInfo window = new WindowBuilder().setRoot(root).build();
        assertThat(window.getRoot()).isEqualTo(root);
    }

    @Test
    public void testSetBoundsInScreen() {
        Rect setBounds = new Rect(100, 200, 300, 400);
        AccessibilityWindowInfo window = new WindowBuilder().setBoundsInScreen(setBounds).build();
        Rect retrievedBounds = new Rect();
        window.getBoundsInScreen(retrievedBounds);
        assertThat(retrievedBounds).isEqualTo(setBounds);
    }

    @Test
    public void testSetType() {
        AccessibilityWindowInfo window =
                new WindowBuilder().setType(TYPE_SYSTEM).build();
        assertThat(window.getType()).isEqualTo(TYPE_SYSTEM);
    }

    @Test
    public void testSetDisplayId() {
        AccessibilityWindowInfo window =
                new WindowBuilder().setDisplayId(3).build();
        assertThat(window.getDisplayId()).isEqualTo(3);
    }

    @Test
    public void testSetFocused() {
        AccessibilityWindowInfo window = new WindowBuilder().setFocused(true).build();
        assertThat(window.isFocused()).isTrue();
    }

    @Test
    public void testSetTaskId() {
        AccessibilityWindowInfo window =
                new WindowBuilder().setTaskId(3).build();
        assertThat(window.getTaskId()).isEqualTo(3);
    }
}
