/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.testng.Assert.expectThrows;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextSnapshot;
import android.view.inputmethod.cts.util.InputConnectionTestUtils;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class BaseInputConnectionTest {

    private static final int CAPS_MODE_MASK = TextUtils.CAP_MODE_CHARACTERS
            | TextUtils.CAP_MODE_WORDS | TextUtils.CAP_MODE_SENTENCES;

    private static final int MEMORY_EFFICIENT_TEXT_LENGTH = 2048;

    // Retrieve a large range to text to verify the content.
    private static final int TEXT_LENGTH_TO_RETRIEVAL = 1024;

    @Test
    public void testDefaultMethods() {
        // These methods are default to return fixed result.
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();

        assertFalse(connection.beginBatchEdit());
        assertFalse(connection.endBatchEdit());

        // only fit for test default implementation of commitCompletion.
        int completionId = 1;
        String completionString = "commitCompletion test";
        assertFalse(connection.commitCompletion(new CompletionInfo(completionId,
                0, completionString)));

        assertNull(connection.getExtractedText(new ExtractedTextRequest(), 0));

        // only fit for test default implementation of performEditorAction.
        int actionCode = 1;
        int actionId = 2;
        String action = "android.intent.action.MAIN";
        assertTrue(connection.performEditorAction(actionCode));
        assertFalse(connection.performContextMenuAction(actionId));
        assertFalse(connection.performPrivateCommand(action, new Bundle()));
    }

    @Test
    public void testOpComposingSpans() {
        Spannable text = new SpannableString("Test ComposingSpans");
        BaseInputConnection.setComposingSpans(text);
        assertTrue(BaseInputConnection.getComposingSpanStart(text) > -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) > -1);
        BaseInputConnection.removeComposingSpans(text);
        assertTrue(BaseInputConnection.getComposingSpanStart(text) == -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) == -1);
    }

    /**
     * getEditable: Return the target of edit operations. The default implementation
     *              returns its own fake editable that is just used for composing text.
     * clearMetaKeyStates: Default implementation uses
     *              MetaKeyKeyListener#clearMetaKeyState(long, int) to clear the state.
     *              BugId:1738511
     * commitText: Default implementation replaces any existing composing text with the given
     *             text.
     * deleteSurroundingText: The default implementation performs the deletion around the current
     *              selection position of the editable text.
     * getCursorCapsMode: The default implementation uses TextUtils.getCapsMode to get the
     *                  cursor caps mode for the current selection position in the editable text.
     *                  TextUtils.getCapsMode is tested fully in TextUtilsTest#testGetCapsMode.
     * getTextBeforeCursor, getTextAfterCursor: The default implementation performs the deletion
     *                          around the current selection position of the editable text.
     * setSelection: changes the selection position in the current editable text.
     */
    @Test
    public void testOpTextMethods() {
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();

        // return is an default Editable instance with empty source
        final Editable text = connection.getEditable();
        assertNotNull(text);
        assertEquals(0, text.length());

        // Test commitText, not default fake mode
        CharSequence str = "TestCommit ";
        Editable inputText = Editable.Factory.getInstance().newEditable(str);
        connection.commitText(inputText, inputText.length());
        final Editable text2 = connection.getEditable();
        int strLength = str.length();
        assertEquals(strLength, text2.length());
        assertEquals(str.toString(), text2.toString());
        assertEquals(TextUtils.CAP_MODE_WORDS,
                connection.getCursorCapsMode(TextUtils.CAP_MODE_WORDS));
        int offLength = 3;
        CharSequence expected = str.subSequence(strLength - offLength, strLength);
        assertEquals(expected.toString(), connection.getTextBeforeCursor(offLength,
                BaseInputConnection.GET_TEXT_WITH_STYLES).toString());
        connection.setSelection(0, 0);
        expected = str.subSequence(0, offLength);
        assertEquals(expected.toString(), connection.getTextAfterCursor(offLength,
                BaseInputConnection.GET_TEXT_WITH_STYLES).toString());

        // Test deleteSurroundingText
        int end = text2.length();
        connection.setSelection(end, end);
        // Delete the ending space
        assertTrue(connection.deleteSurroundingText(1, 2));
        Editable text3 = connection.getEditable();
        assertEquals(strLength - 1, text3.length());
        String expectedDelString = "TestCommit";
        assertEquals(expectedDelString, text3.toString());
    }

    /**
     * finishComposingText: The default implementation removes the composing state from the
     *                      current editable text.
     * setComposingText: The default implementation places the given text into the editable,
     *                  replacing any existing composing text
     */
    @Test
    public void testFinishComposingText() {
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();
        CharSequence str = "TestFinish";
        Editable inputText = Editable.Factory.getInstance().newEditable(str);
        connection.commitText(inputText, inputText.length());
        final Editable text = connection.getEditable();
        // Test finishComposingText, not default fake mode
        BaseInputConnection.setComposingSpans(text);
        assertTrue(BaseInputConnection.getComposingSpanStart(text) > -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) > -1);
        connection.finishComposingText();
        assertTrue(BaseInputConnection.getComposingSpanStart(text) == -1);
        assertTrue(BaseInputConnection.getComposingSpanEnd(text) == -1);
    }

    /**
     * Updates InputMethodManager with the current fullscreen mode.
     */
    @Test
    public void testReportFullscreenMode() {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();
        connection.reportFullscreenMode(false);
        assertFalse(imm.isFullscreenMode());
        connection.reportFullscreenMode(true);
        // Only IMEs are allowed to report full-screen mode.  Calling this method from the
        // application should have no effect.
        assertFalse(imm.isFullscreenMode());
    }

    private void verifyDeleteSurroundingTextMain(
            final String initialState,
            final int deleteBefore,
            final int deleteAfter,
            final String expectedState) {
        verifyDeleteSurroundingTextMain(initialState, deleteBefore, deleteAfter, expectedState,
                false /* clearSelection */);
    }

    private void verifyDeleteSurroundingTextMain(
            final String initialState,
            final int deleteBefore,
            final int deleteAfter,
            final String expectedState,
            final boolean clearSelection) {
        final CharSequence source = clearSelection ? initialState
                : InputConnectionTestUtils.formatString(initialState);
        final BaseInputConnection ic =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);

        if (clearSelection) {
            Selection.removeSelection(ic.getEditable());
        }

        final boolean result = ic.deleteSurroundingText(deleteBefore, deleteAfter);
        if (!result) {
            assertEquals(expectedState, ic.getEditable().toString());
            return;
        } else if (clearSelection) {
            fail("deleteSurroundingText should return false for invalid selection");
        }

        final CharSequence expectedString =
                clearSelection
                        ? expectedState
                        : InputConnectionTestUtils.formatString(expectedState);
        final int expectedSelectionStart = Selection.getSelectionStart(expectedString);
        final int expectedSelectionEnd = Selection.getSelectionEnd(expectedString);
        verifyTextAndSelection(ic, expectedString, expectedSelectionStart, expectedSelectionEnd);
    }

    /**
     * Tests {@link BaseInputConnection#deleteSurroundingText(int, int)} comprehensively.
     */
    @Test
    public void testDeleteSurroundingText() {
        verifyDeleteSurroundingTextMain("0123456789", 1, 2, "0123456789",
                true /* clearSelection*/);
        verifyDeleteSurroundingTextMain("012[]3456789", 0, 0, "012[]3456789");
        verifyDeleteSurroundingTextMain("012[]3456789", -1, -1, "012[]3456789");
        verifyDeleteSurroundingTextMain("012[]3456789", 1, 2, "01[]56789");
        verifyDeleteSurroundingTextMain("012[]3456789", 10, 1, "[]456789");
        verifyDeleteSurroundingTextMain("012[]3456789", 1, 10, "01[]");
        verifyDeleteSurroundingTextMain("[]0123456789", 3, 3, "[]3456789");
        verifyDeleteSurroundingTextMain("0123456789[]", 3, 3, "0123456[]");
        verifyDeleteSurroundingTextMain("012[345]6789", 0, 0, "012[345]6789");
        verifyDeleteSurroundingTextMain("012[345]6789", -1, -1, "012[345]6789");
        verifyDeleteSurroundingTextMain("012[345]6789", 1, 2, "01[345]89");
        verifyDeleteSurroundingTextMain("012[345]6789", 10, 1, "[345]789");
        verifyDeleteSurroundingTextMain("012[345]6789", 1, 10, "01[345]");
        verifyDeleteSurroundingTextMain("[012]3456789", 3, 3, "[012]6789");
        verifyDeleteSurroundingTextMain("0123456[789]", 3, 3, "0123[789]");
        verifyDeleteSurroundingTextMain("[0123456789]", 0, 0, "[0123456789]");
        verifyDeleteSurroundingTextMain("[0123456789]", 1, 1, "[0123456789]");

        // Surrogate characters do not have any special meanings.  Validating the character sequence
        // is beyond the goal of this API.
        verifyDeleteSurroundingTextMain("0<>[]3456789", 1, 0, "0<[]3456789");
        verifyDeleteSurroundingTextMain("0<>[]3456789", 2, 0, "0[]3456789");
        verifyDeleteSurroundingTextMain("0<>[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextMain("012[]<>56789", 0, 1, "012[]>56789");
        verifyDeleteSurroundingTextMain("012[]<>56789", 0, 2, "012[]56789");
        verifyDeleteSurroundingTextMain("012[]<>56789", 0, 3, "012[]6789");
        verifyDeleteSurroundingTextMain("0<<[]3456789", 1, 0, "0<[]3456789");
        verifyDeleteSurroundingTextMain("0<<[]3456789", 2, 0, "0[]3456789");
        verifyDeleteSurroundingTextMain("0<<[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextMain("012[]<<56789", 0, 1, "012[]<56789");
        verifyDeleteSurroundingTextMain("012[]<<56789", 0, 2, "012[]56789");
        verifyDeleteSurroundingTextMain("012[]<<56789", 0, 3, "012[]6789");
        verifyDeleteSurroundingTextMain("0>>[]3456789", 1, 0, "0>[]3456789");
        verifyDeleteSurroundingTextMain("0>>[]3456789", 2, 0, "0[]3456789");
        verifyDeleteSurroundingTextMain("0>>[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextMain("012[]>>56789", 0, 1, "012[]>56789");
        verifyDeleteSurroundingTextMain("012[]>>56789", 0, 2, "012[]56789");
        verifyDeleteSurroundingTextMain("012[]>>56789", 0, 3, "012[]6789");
    }

    private void verifyDeleteSurroundingTextInCodePointsMain(
            String initialState,
            int deleteBeforeInCodePoints,
            int deleteAfterInCodePoints,
            String expectedState) {
        final CharSequence source = InputConnectionTestUtils.formatString(initialState);
        final BaseInputConnection ic =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);
        ic.deleteSurroundingTextInCodePoints(deleteBeforeInCodePoints, deleteAfterInCodePoints);

        final CharSequence expectedString = InputConnectionTestUtils.formatString(expectedState);
        final int expectedSelectionStart = Selection.getSelectionStart(expectedString);
        final int expectedSelectionEnd = Selection.getSelectionEnd(expectedString);
        verifyTextAndSelection(ic, expectedString, expectedSelectionStart, expectedSelectionEnd);
    }

    /**
     * Tests {@link BaseInputConnection#deleteSurroundingTextInCodePoints(int, int)}
     * comprehensively.
     */
    @Test
    public void testDeleteSurroundingTextInCodePoints() {
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 0, 0, "012[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", -1, -1, "012[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 1, 2, "01[]56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 10, 1, "[]456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]3456789", 1, 10, "01[]");
        verifyDeleteSurroundingTextInCodePointsMain("[]0123456789", 3, 3, "[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0123456789[]", 3, 3, "0123456[]");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 0, 0, "012[345]6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", -1, -1, "012[345]6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 1, 2, "01[345]89");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 10, 1, "[345]789");
        verifyDeleteSurroundingTextInCodePointsMain("012[345]6789", 1, 10, "01[345]");
        verifyDeleteSurroundingTextInCodePointsMain("[012]3456789", 3, 3, "[012]6789");
        verifyDeleteSurroundingTextInCodePointsMain("0123456[789]", 3, 3, "0123[789]");
        verifyDeleteSurroundingTextInCodePointsMain("[0123456789]", 0, 0, "[0123456789]");
        verifyDeleteSurroundingTextInCodePointsMain("[0123456789]", 1, 1, "[0123456789]");

        verifyDeleteSurroundingTextInCodePointsMain("0<>[]3456789", 1, 0, "0[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<>[]3456789", 2, 0, "[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<>[]3456789", 3, 0, "[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>56789", 0, 1, "012[]56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>56789", 0, 2, "012[]6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>56789", 0, 3, "012[]789");

        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 0, "[]<><><><><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 1, "[]<><><><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 2, "[]<><><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 3, "[]<><>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 4, "[]<>");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 5, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 6, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("[]<><><><><>", 0, 1000, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 0, 0, "<><><><><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 1, 0, "<><><><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 2, 0, "<><><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 3, 0, "<><>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 4, 0, "<>[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 5, 0, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 6, 0, "[]");
        verifyDeleteSurroundingTextInCodePointsMain("<><><><><>[]", 1000, 0, "[]");

        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 1, 0, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 2, 0, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 3, 0, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 0, 1, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 0, 2, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 0, 3, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 1, 0, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 2, 0, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 3, 0, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 0, 1, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 0, 2, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 0, 3, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("01<[]>456789", 1, 0, "01<[]>456789");
        verifyDeleteSurroundingTextInCodePointsMain("01<[]>456789", 0, 1, "01<[]>456789");
        verifyDeleteSurroundingTextInCodePointsMain("<12[]3456789", 1, 0, "<1[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<12[]3456789", 2, 0, "<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<12[]3456789", 3, 0, "<12[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<<>[]3456789", 1, 0, "<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<<>[]3456789", 2, 0, "<<>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("<<>[]3456789", 3, 0, "<<>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]34>6789", 0, 1, "012[]4>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]34>6789", 0, 2, "012[]>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]34>6789", 0, 3, "012[]34>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>>6789", 0, 1, "012[]>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>>6789", 0, 2, "012[]<>>6789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<>>6789", 0, 3, "012[]<>>6789");

        // Atomicity test.
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 1, 1, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 2, 1, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0<<[]3456789", 3, 1, "0<<[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 1, 1, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 1, 2, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]<<56789", 1, 3, "012[]<<56789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 1, 1, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 2, 1, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("0>>[]3456789", 3, 1, "0>>[]3456789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 1, 1, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 1, 2, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("012[]>>56789", 1, 3, "012[]>>56789");
        verifyDeleteSurroundingTextInCodePointsMain("01<[]>456789", 1, 1, "01<[]>456789");

        // Do not verify the character sequences in the selected region.
        verifyDeleteSurroundingTextInCodePointsMain("01[><]456789", 1, 0, "0[><]456789");
        verifyDeleteSurroundingTextInCodePointsMain("01[><]456789", 0, 1, "01[><]56789");
        verifyDeleteSurroundingTextInCodePointsMain("01[><]456789", 1, 1, "0[><]56789");
    }

    @Test
    public void testCloseConnection() {
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();

        final CharSequence source = "0123456789";
        connection.commitText(source, source.length());
        connection.setComposingRegion(2, 5);
        final Editable text = connection.getEditable();
        assertEquals(2, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(text));

        // BaseInputConnection#closeConnection() must clear the on-going composition.
        connection.closeConnection();
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        assertEquals(-1, BaseInputConnection.getComposingSpanEnd(text));
    }

    @Test
    public void testGetHandler() {
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();

        // BaseInputConnection must not implement getHandler().
        assertNull(connection.getHandler());
    }

    @Test
    public void testCommitContent() {
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();

        final InputContentInfo inputContentInfo =
                new InputContentInfo(
                        Uri.parse("content://com.example/path"),
                        new ClipDescription("sample content", new String[] {"image/png"}),
                        Uri.parse("https://example.com"));
        // The default implementation should do nothing and just return false.
        assertFalse(connection.commitContent(inputContentInfo, 0 /* flags */, null /* opts */));
    }

    @Test
    public void testGetSelectedText_wrongSelection() {
        final BaseInputConnection connection = InputConnectionTestUtils.createBaseInputConnection();
        Editable editable = connection.getEditable();
        editable.append("hello");
        editable.setSpan(Selection.SELECTION_START, 4, 4, Spanned.SPAN_POINT_POINT);
        editable.removeSpan(Selection.SELECTION_END);

        // Should not crash.
        connection.getSelectedText(0);
    }

    @Test
    public void testGetSurroundingText_hasTextBeforeSelection() {
        // 123456789|
        final CharSequence source = InputConnectionTestUtils.formatString("123456789[]");
        final BaseInputConnection connection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);

        // 9|
        SurroundingText surroundingText1 = connection.getSurroundingText(1, 1, 0);
        assertEquals("9", surroundingText1.getText().toString());
        assertEquals(1, surroundingText1.getSelectionEnd());
        assertEquals(1, surroundingText1.getSelectionEnd());
        assertEquals(8, surroundingText1.getOffset());

        // 123456789|
        SurroundingText surroundingText2 = connection.getSurroundingText(10, 1, 0);
        assertEquals("123456789", surroundingText2.getText().toString());
        assertEquals(9, surroundingText2.getSelectionStart());
        assertEquals(9, surroundingText2.getSelectionEnd());
        assertEquals(0, surroundingText2.getOffset());

        // |
        SurroundingText surroundingText3 = connection.getSurroundingText(0, 10,
                BaseInputConnection.GET_TEXT_WITH_STYLES);
        assertEquals("", surroundingText3.getText().toString());
        assertEquals(0, surroundingText3.getSelectionStart());
        assertEquals(0, surroundingText3.getSelectionEnd());
        assertEquals(9, surroundingText3.getOffset());
    }

    @Test
    public void testGetSurroundingText_hasTextAfterSelection() {
        // |123456789
        final CharSequence source = InputConnectionTestUtils.formatString("[]123456789");
        final BaseInputConnection connection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);

        // |1
        SurroundingText surroundingText1 = connection.getSurroundingText(1, 1,
                BaseInputConnection.GET_TEXT_WITH_STYLES);
        assertEquals("1", surroundingText1.getText().toString());
        assertEquals(0, surroundingText1.getSelectionStart());
        assertEquals(0, surroundingText1.getSelectionEnd());
        assertEquals(0, surroundingText1.getOffset());

        // |
        SurroundingText surroundingText2 = connection.getSurroundingText(10, 1, 0);
        assertEquals("1", surroundingText2.getText().toString());
        assertEquals(0, surroundingText2.getSelectionStart());
        assertEquals(0, surroundingText2.getSelectionEnd());
        assertEquals(0, surroundingText2.getOffset());

        // |123456789
        SurroundingText surroundingText3 = connection.getSurroundingText(0, 10, 0);
        assertEquals("123456789", surroundingText3.getText().toString());
        assertEquals(0, surroundingText3.getSelectionStart());
        assertEquals(0, surroundingText3.getSelectionEnd());
        assertEquals(0, surroundingText3.getOffset());
    }

    @Test
    public void testGetSurroundingText_hasSelection() {
        // 123|45|6789
        final CharSequence source = InputConnectionTestUtils.formatString("123[45]6789");
        final BaseInputConnection connection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);

        // 3|45|6
        SurroundingText surroundingText1 = connection.getSurroundingText(1, 1, 0);
        assertEquals("3456", surroundingText1.getText().toString());
        assertEquals(1, surroundingText1.getSelectionStart());
        assertEquals(3, surroundingText1.getSelectionEnd());
        assertEquals(2, surroundingText1.getOffset());

        // 123|45|6
        SurroundingText surroundingText2 = connection.getSurroundingText(10, 1,
                BaseInputConnection.GET_TEXT_WITH_STYLES);
        assertEquals("123456", surroundingText2.getText().toString());
        assertEquals(3, surroundingText2.getSelectionStart());
        assertEquals(5, surroundingText2.getSelectionEnd());
        assertEquals(0, surroundingText2.getOffset());

        // |45|6789
        SurroundingText surroundingText3 = connection.getSurroundingText(0, 10, 0);
        assertEquals("456789", surroundingText3.getText().toString());
        assertEquals(0, surroundingText3.getSelectionStart());
        assertEquals(2, surroundingText3.getSelectionEnd());
        assertEquals(3, surroundingText3.getOffset());

        // 123|45|6789
        SurroundingText surroundingText4 = connection.getSurroundingText(10, 10,
                BaseInputConnection.GET_TEXT_WITH_STYLES);
        assertEquals("123456789", surroundingText4.getText().toString());
        assertEquals(3, surroundingText4.getSelectionStart());
        assertEquals(5, surroundingText4.getSelectionEnd());
        assertEquals(0, surroundingText4.getOffset());

        // |45|
        SurroundingText surroundingText5 =
                connection.getSurroundingText(0, 0, BaseInputConnection.GET_TEXT_WITH_STYLES);
        assertEquals("45", surroundingText5.getText().toString());
        assertEquals(0, surroundingText5.getSelectionStart());
        assertEquals(2, surroundingText5.getSelectionEnd());
        assertEquals(3, surroundingText5.getOffset());
    }

    @Test
    public void testInvalidGetTextBeforeOrAfterCursorRequest() {
        final CharSequence source = InputConnectionTestUtils.formatString("hello[]");
        final BaseInputConnection connection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);

        // getTextBeforeCursor
        assertEquals("", connection.getTextBeforeCursor(0, 0).toString());
        assertEquals("", connection.getTextBeforeCursor(
                0, BaseInputConnection.GET_TEXT_WITH_STYLES).toString());
        assertEquals("hello", connection.getTextBeforeCursor(10, 0).toString());
        assertEquals("hello", connection.getTextBeforeCursor(
                100, BaseInputConnection.GET_TEXT_WITH_STYLES).toString());
        expectThrows(IllegalArgumentException.class, ()-> connection.getTextBeforeCursor(-1, 0));

        // getTextAfterCursor
        assertEquals("", connection.getTextAfterCursor(0, 0).toString());
        assertEquals("", connection.getTextAfterCursor(
                0, BaseInputConnection.GET_TEXT_WITH_STYLES).toString());
        assertEquals("", connection.getTextAfterCursor(100, 0).toString());
        assertEquals("", connection.getTextAfterCursor(
                100, BaseInputConnection.GET_TEXT_WITH_STYLES).toString());
        expectThrows(IllegalArgumentException.class, ()-> connection.getTextAfterCursor(-1, 0));
    }

    @Test
    public void testTakeSnapshot() {
        final BaseInputConnection connection =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(
                        InputConnectionTestUtils.formatString("0123[456]789"));

        verifyTextSnapshot(connection);

        connection.setSelection(10, 10);
        verifyTextSnapshot(connection);

        connection.setComposingRegion(3, 10);
        verifyTextSnapshot(connection);

        connection.finishComposingText();
        verifyTextSnapshot(connection);
    }

    @Test
    public void testTakeSnapshotForNoSelection() {
        final BaseInputConnection connection =
                InputConnectionTestUtils.createBaseInputConnection(
                        Editable.Factory.getInstance().newEditable("test"));
        // null should be returned for text with no selection.
        assertThat(connection.takeSnapshot()).isNull();
    }

    private void verifyTextSnapshot(@NonNull BaseInputConnection connection) {
        final Editable editable = connection.getEditable();

        final TextSnapshot snapshot = connection.takeSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getSelectionStart()).isEqualTo(Selection.getSelectionStart(editable));
        assertThat(snapshot.getSelectionEnd()).isEqualTo(Selection.getSelectionEnd(editable));
        assertThat(snapshot.getCompositionStart())
                .isEqualTo(BaseInputConnection.getComposingSpanStart(editable));
        assertThat(snapshot.getCompositionEnd())
                .isEqualTo(BaseInputConnection.getComposingSpanEnd(editable));
        assertThat(snapshot.getCursorCapsMode())
                .isEqualTo(connection.getCursorCapsMode(CAPS_MODE_MASK));
        final SurroundingText surroundingText = snapshot.getSurroundingText();
        assertThat(surroundingText).isNotNull();
        final SurroundingText expectedSurroundingText =
                connection.getSurroundingText(
                        MEMORY_EFFICIENT_TEXT_LENGTH / 2,
                        MEMORY_EFFICIENT_TEXT_LENGTH / 2,
                        InputConnection.GET_TEXT_WITH_STYLES);
        assertThat(
                        surroundingText
                                .getText()
                                .toString()
                                .contentEquals(expectedSurroundingText.getText()))
                .isTrue();
        assertThat(surroundingText.getSelectionStart())
                .isEqualTo(expectedSurroundingText.getSelectionStart());
        assertThat(surroundingText.getSelectionStart())
                .isEqualTo(expectedSurroundingText.getSelectionStart());
        assertThat(surroundingText.getSelectionEnd())
                .isEqualTo(expectedSurroundingText.getSelectionEnd());
        assertThat(surroundingText.getOffset()).isEqualTo(expectedSurroundingText.getOffset());
        assertThat(snapshot.getCursorCapsMode())
                .isEqualTo(connection.getCursorCapsMode(CAPS_MODE_MASK));
    }

    @Test
    public void testReplaceText() {
        verifyReplaceText("012[3456]789", 3, 7, "text", 1, "012text[]789");
        verifyReplaceText("012[]3456789", 0, 3, "text", 1, "text[]3456789");
        verifyReplaceText("012[]3456789", 3, 0, "text", 1, "text[]3456789");
        verifyReplaceText("012[]3456789", 0, 10, "text", 1, "text[]");
        verifyReplaceText("0123456789[]", 0, 3, "text", -1, "[]text3456789");
        verifyReplaceText("0123456789[]", 10, 10, "text", 1, "0123456789text[]");
        verifyReplaceText("0123456789[]", 100, 100, "text", 1, "0123456789text[]");
        verifyReplaceText("[]0123456789", 0, 0, "text", 1, "text[]0123456789");
        verifyReplaceText("[]0123456789", 0, 5, "text", 1, "text[]56789");
        verifyReplaceText("[]0123456789", 0, 10, "text", -1, "[]text");
        verifyReplaceText("[0123456789]", 0, 10, "text", 1, "text[]");
        verifyReplaceText("[0123456789]", 0, 8, "text", 1, "text[]89");
    }

    private static void verifyReplaceText(
            final String initialState,
            final int start,
            final int end,
            final String text,
            final int newCursorPosition,
            final String expectedState) {
        final CharSequence source = InputConnectionTestUtils.formatString(initialState);
        final BaseInputConnection ic =
                InputConnectionTestUtils.createBaseInputConnectionWithSelection(source);
        assertTrue(ic.replaceText(start, end, text, newCursorPosition, null));
        final CharSequence expectedString = InputConnectionTestUtils.formatString(expectedState);
        final int expectedSelectionStart = Selection.getSelectionStart(expectedString);
        final int expectedSelectionEnd = Selection.getSelectionEnd(expectedString);
        verifyTextAndSelection(ic, expectedString, expectedSelectionStart, expectedSelectionEnd);
    }

    private static void verifyTextAndSelection(
            BaseInputConnection ic,
            final CharSequence expectedString,
            final int expectedSelectionStart,
            final int expectedSelectionEnd) {
        if (expectedSelectionStart == 0) {
            assertTrue(TextUtils.isEmpty(ic.getTextBeforeCursor(TEXT_LENGTH_TO_RETRIEVAL, 0)));
        } else {
            assertEquals(
                    expectedString.subSequence(0, expectedSelectionStart).toString(),
                    ic.getTextBeforeCursor(TEXT_LENGTH_TO_RETRIEVAL, 0).toString());
        }
        if (expectedSelectionStart == expectedSelectionEnd) {
            assertTrue(TextUtils.isEmpty(ic.getSelectedText(0))); // null is allowed.
        } else {
            assertEquals(
                    expectedString
                            .subSequence(expectedSelectionStart, expectedSelectionEnd)
                            .toString(),
                    ic.getSelectedText(0).toString());
        }
        if (expectedSelectionEnd == expectedString.length()) {
            assertTrue(TextUtils.isEmpty(ic.getTextAfterCursor(TEXT_LENGTH_TO_RETRIEVAL, 0)));
        } else {
            assertEquals(
                    expectedString
                            .subSequence(expectedSelectionEnd, expectedString.length())
                            .toString(),
                    ic.getTextAfterCursor(TEXT_LENGTH_TO_RETRIEVAL, 0).toString());
        }
    }
}
