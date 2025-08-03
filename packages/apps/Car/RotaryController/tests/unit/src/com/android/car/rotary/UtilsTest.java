/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class UtilsTest {

    @Mock
    private InputMethodManager mMockedInputMethodManager;

    @Test
    public void refreshNode_nodeIsNull_returnsNull() {
        AccessibilityNodeInfo result = Utils.refreshNode(null);

        assertThat(result).isNull();
    }

    @Test
    public void refreshNode_nodeRefreshed_returnsNode() {
        AccessibilityNodeInfo input = mock(AccessibilityNodeInfo.class);
        when(input.refresh()).thenReturn(true);

        AccessibilityNodeInfo result = Utils.refreshNode(input);

        assertThat(result).isNotNull();
    }

    @Test
    public void refreshNode_nodeNotRefreshed_returnsNull() {
        AccessibilityNodeInfo input = mock(AccessibilityNodeInfo.class);
        when(input.refresh()).thenReturn(false);

        AccessibilityNodeInfo result = Utils.refreshNode(input);

        assertThat(result).isNull();
    }

    @Test
    public void refreshNode_nodeNotRefreshed_recycleNode() {
        AccessibilityNodeInfo input = mock(AccessibilityNodeInfo.class);
        when(input.refresh()).thenReturn(false);

        Utils.refreshNode(input);

        verify(input).recycle();
    }

    @Test
    public void testIsInstalledIme_invalidImeConfigs() {
        assertThat(Utils.isInstalledIme(null, mMockedInputMethodManager)).isFalse();
        assertThat(Utils.isInstalledIme("blah/someIme", mMockedInputMethodManager)).isFalse();
    }

    @Test
    public void testIsInstalledIme_validImeConfig() {
        InputMethodInfo methodInfo = mock(InputMethodInfo.class);
        when(methodInfo.getComponent()).thenReturn(
                ComponentName.unflattenFromString("blah/someIme"));
        List<InputMethodInfo> availableInputMethods = Collections.singletonList(methodInfo);
        when(mMockedInputMethodManager.getInputMethodList()).thenReturn(availableInputMethods);

        assertThat(Utils.isInstalledIme("blah/someIme", mMockedInputMethodManager)).isTrue();
    }
}
