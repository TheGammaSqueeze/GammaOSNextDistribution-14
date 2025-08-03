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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.InputType;
import android.text.Selection;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextSnapshot;
import android.view.inputmethod.cts.util.InputConnectionTestUtils;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class TextSnapshotTest {

    @Test
    public void testConstruction() {
        // Empty text
        verify(surroundingText("[]", 0), -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // A single character
        verify(surroundingText("0[]]", 0), -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        verify(surroundingText("012[345]6789", 0), -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        verify(surroundingText("012[345]6789", 1), -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        verify(surroundingText("012[345]6789", 2), -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        verify(surroundingText("012[345]6789", 0), -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        verify(surroundingText("012[345]6789", 0), 3, 6, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        verify(surroundingText("012[345]6789", 2), 3, 6, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    @Test(expected = NullPointerException.class)
    public void testNullSurroundingText1() {
        new TextSnapshot(null, -1, -1, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidComposeRegion() {
        new TextSnapshot(surroundingText("012[345]6789", 0), -2, -2,
                InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidComposeRegion2() {
        new TextSnapshot(surroundingText("012[345]6789", 0), 0, -2,
                InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidComposeRegion3() {
        new TextSnapshot(surroundingText("012[345]6789", 0), -2, 0,
                InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidComposeRegion4() {
        new TextSnapshot(surroundingText("012[345]6789", 0), 2, 1,
                InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    @Test
    public void testTextSnapshotNotEquals() {
        final TextSnapshot textSnapshot1 =
                new TextSnapshot(new SurroundingText("hello", 5, 5, 0), -1, -1, 0);
        final TextSnapshot textSnapshot2 =
                new TextSnapshot(new SurroundingText("hello", 5, 5, 0), -1, -1, 0);
        assertThat(textSnapshot1).isNotEqualTo(textSnapshot2);
    }

    private static SurroundingText surroundingText(@NonNull String formatString, int offset) {
        final CharSequence text = InputConnectionTestUtils.formatString(formatString);
        final int selectionStart = Selection.getSelectionStart(text);
        assertThat(selectionStart).isGreaterThan(-1);
        final int selectionEnd = Selection.getSelectionEnd(text);
        assertThat(selectionEnd).isGreaterThan(-1);
        return new SurroundingText(
                text.subSequence(offset, text.length() - offset),
                selectionStart - offset,
                selectionEnd - offset,
                offset);
    }

    private static void verify(@NonNull SurroundingText surroundingText, int compositionStart,
            int compositionEnd, int cursorCapsMode) {
        final TextSnapshot snapshot =
                new TextSnapshot(surroundingText, compositionStart, compositionEnd, cursorCapsMode);

        assertThat(snapshot.getSurroundingText().getText()).isEqualTo(surroundingText.getText());
        assertThat(snapshot.getSurroundingText().getOffset())
                .isEqualTo(surroundingText.getOffset());
        assertThat(snapshot.getSurroundingText().getSelectionStart())
                .isEqualTo(surroundingText.getSelectionStart());
        assertThat(snapshot.getSurroundingText().getSelectionEnd())
                .isEqualTo(surroundingText.getSelectionEnd());

        assertThat(snapshot.getSelectionStart())
                .isEqualTo(surroundingText.getSelectionStart() + surroundingText.getOffset());
        assertThat(snapshot.getSelectionEnd())
                .isEqualTo(surroundingText.getSelectionEnd() + surroundingText.getOffset());

        assertThat(snapshot.getCompositionStart()).isEqualTo(compositionStart);
        assertThat(snapshot.getCompositionEnd()).isEqualTo(compositionEnd);

        assertThat(snapshot.getCursorCapsMode()).isEqualTo(cursorCapsMode);
    }
}
