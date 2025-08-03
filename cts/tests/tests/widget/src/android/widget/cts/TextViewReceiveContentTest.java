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

package android.widget.cts;

import static android.view.ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT;
import static android.view.ContentInfo.SOURCE_AUTOFILL;
import static android.view.ContentInfo.SOURCE_CLIPBOARD;
import static android.view.ContentInfo.SOURCE_DRAG_AND_DROP;
import static android.view.ContentInfo.SOURCE_INPUT_METHOD;
import static android.view.ContentInfo.SOURCE_PROCESS_TEXT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.QwertyKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.style.UnderlineSpan;
import android.view.ContentInfo;
import android.view.DragEvent;
import android.view.OnReceiveContentListener;
import android.view.View.MeasureSpec;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.TextViewOnReceiveContentListener;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.WindowUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.Objects;

/**
 * Tests for {@link TextView#performReceiveContent} and related code.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextViewReceiveContentTest {
    public static final Uri SAMPLE_CONTENT_URI = Uri.parse("content://com.example/path");
    @Rule
    public ActivityTestRule<TextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewCtsActivity.class);

    private Activity mActivity;
    private TextView mTextView;
    private OnReceiveContentListener mDefaultReceiver;
    private OnReceiveContentListener mMockReceiver;
    private ClipboardManager mClipboardManager;

    @Before
    public void before() {
        mActivity = mActivityRule.getActivity();
        WindowUtil.waitForFocus(mActivity);
        mTextView = mActivity.findViewById(R.id.textview_text);
        mDefaultReceiver = new TextViewOnReceiveContentListener();

        mMockReceiver = Mockito.mock(OnReceiveContentListener.class);
        when(mMockReceiver.onReceiveContent(any(), any())).thenReturn(null);

        mClipboardManager = mActivity.getSystemService(ClipboardManager.class);
        mClipboardManager.clearPrimaryClip();

        configureAppTargetSdkToS();
    }

    @After
    public void after() {
        resetTargetSdk();
    }

    // ============================================================================================
    // Tests to verify TextView APIs/accessors/defaults related to OnReceiveContentListener.
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_nullEditorInfo() throws Exception {
        initTextViewForEditing("xz", 1);
        try {
            mTextView.onCreateInputConnection(null);
            Assert.fail("Expected exception");
        } catch (NullPointerException expected) {
        }
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Call onCreateInputConnection() and assert that contentMimeTypes is not set when there is
        // no custom receiver configured.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        assertThat(editorInfo.contentMimeTypes).isNull();
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/plain", "image/png", "video/mp4"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Call onCreateInputConnection() and assert that contentMimeTypes is set from the receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        assertThat(editorInfo.contentMimeTypes).isEqualTo(receiverMimeTypes);
    }

    @UiThreadTest
    @Test
    public void testTextView_onCreateInputConnection_customReceiver_oldTargetSdk()
            throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/plain", "image/png", "video/mp4"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Call onCreateInputConnection() and assert that contentMimeTypes is set from the receiver.
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        assertThat(ic).isNotNull();
        assertThat(editorInfo.contentMimeTypes).isEqualTo(receiverMimeTypes);
    }

    // ============================================================================================
    // Tests to verify the behavior of TextViewOnReceiveContentListener.
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_text() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "y");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_styledText() throws Exception {
        initTextViewForEditing("xz", 1);

        UnderlineSpan underlineSpan = new UnderlineSpan();
        SpannableStringBuilder ssb = new SpannableStringBuilder("hi world");
        ssb.setSpan(underlineSpan, 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ClipData clip = ClipData.newPlainText("test", ssb);

        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xhi worldz", 9);
        int spanStart = mTextView.getEditableText().getSpanStart(underlineSpan);
        assertThat(spanStart).isEqualTo(4);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_text_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "y");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_styledText_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        UnderlineSpan underlineSpan = new UnderlineSpan();
        SpannableStringBuilder ssb = new SpannableStringBuilder("hi world");
        ssb.setSpan(underlineSpan, 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ClipData clip = ClipData.newPlainText("test", ssb);

        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("xhi worldz", 9);
        int spanStart = mTextView.getEditableText().getSpanStart(underlineSpan);
        assertThat(spanStart).isEqualTo(-1);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_html() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_html_convertToPlainText() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("x*y*z", 4);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", "html", null, SAMPLE_CONTENT_URI));
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("xhtmlz", 5);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_unsupportedMimeType_convertToPlainText()
            throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("text", "html", null, SAMPLE_CONTENT_URI));
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD,
                FLAG_CONVERT_TO_PLAIN_TEXT);

        assertTextAndCursorPosition("xtextz", 5);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_multipleItemsInClipData() throws Exception {
        initTextViewForEditing("xz", 1);

        ClipData clip = ClipData.newPlainText("test", "ONE");
        clip.addItem(new ClipData.Item("TWO"));
        clip.addItem(new ClipData.Item("THREE"));

        // Verify the resulting text when pasting a clip that contains multiple text items.
        String expectedText = "xONE\nTWO\nTHREEz";
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);
        assertTextAndCursorPosition(expectedText, 14);

        // Verify the resulting text when inserting the same clip via drag-and-drop. The result
        // should be the same as when pasting.
        initTextViewForEditing("xz", 1);
        onReceive(mDefaultReceiver, clip, SOURCE_DRAG_AND_DROP, 0);
        assertTextAndCursorPosition(expectedText, 14);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_noSelectionPriorToPaste() throws Exception {
        // Set the text and then clear the selection (ie, ensure that nothing is selected and
        // that the cursor is not present).
        initTextViewForEditing("xz", 0);
        Selection.removeSelection(mTextView.getEditableText());
        assertTextAndCursorPosition("xz", -1);

        // Pasting should still work (should just insert the text at the beginning).
        ClipData clip = ClipData.newPlainText("test", "y");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("yxz", 1);
    }

    @UiThreadTest
    @Test
    public void testDefaultReceiver_onReceive_selectionStartAndEndSwapped() throws Exception {
        initTextViewForEditing("", 0);

        // Set the selection such that "end" is before "start".
        mTextView.setText("hey", BufferType.EDITABLE);
        Selection.setSelection(mTextView.getEditableText(), 3, 1);
        assertTextAndSelection("hey", 3, 1);

        // Pasting should still work (should still successfully overwrite the selection).
        ClipData clip = ClipData.newPlainText("test", "i");
        onReceive(mDefaultReceiver, clip, SOURCE_CLIPBOARD, 0);

        assertTextAndCursorPosition("hi", 2);
    }

    // ============================================================================================
    // Tests to verify that the OnReceiveContentListener is invoked for all the appropriate user
    // interactions:
    // * Paste from clipboard ("Paste" and "Paste as plain text" actions)
    // * Content insertion from IME
    // * Drag and drop
    // * Autofill
    // * Process text (Intent.ACTION_PROCESS_TEXT)
    // ============================================================================================

    @UiThreadTest
    @Test
    public void testPaste_noCustomReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy text to the clipboard.
        ClipData clip = ClipData.newPlainText("test", "y");
        copyToClipboard(clip);

        // Trigger the "Paste" action. This should execute the default receiver.
        boolean result = triggerContextMenuAction(android.R.id.paste);
        assertThat(result).isTrue();
        assertTextAndCursorPosition("xyz", 2);
    }

    @UiThreadTest
    @Test
    public void testPaste_customReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy text to the clipboard.
        ClipData clip = ClipData.newPlainText("test", "y");
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/plain"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the "Paste" action and assert that the custom receiver was executed.
        triggerContextMenuAction(android.R.id.paste);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_CLIPBOARD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testPaste_customReceiver_unsupportedMimeType() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy a URI to the clipboard with a MIME type that's not supported by the receiver.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("y", null, SAMPLE_CONTENT_URI));
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/plain", "video/avi"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the "Paste" action and assert that the custom receiver was executed.
        triggerContextMenuAction(android.R.id.paste);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_CLIPBOARD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testPasteAsPlainText_noCustomReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy HTML to the clipboard.
        ClipData clip = ClipData.newHtmlText("test", "*y*", "<b>y</b>");
        copyToClipboard(clip);

        // Trigger the "Paste as plain text" action. This should execute the platform paste
        // handling, so the content should be inserted according to whatever behavior is implemented
        // in the OS version that's running.
        boolean result = triggerContextMenuAction(android.R.id.pasteAsPlainText);
        assertThat(result).isTrue();
        assertTextAndCursorPosition("x*y*z", 4);
    }

    @UiThreadTest
    @Test
    public void testPasteAsPlainText_customReceiver() throws Exception {
        // Setup: Populate the text field.
        initTextViewForEditing("xz", 1);

        // Setup: Copy text to the clipboard.
        ClipData clip = ClipData.newPlainText("test", "y");
        copyToClipboard(clip);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/plain"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the "Paste as plain text" action and assert that the custom receiver was
        // executed.
        triggerContextMenuAction(android.R.id.pasteAsPlainText);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                contentEq(clip, SOURCE_CLIPBOARD, FLAG_CONVERT_TO_PLAIN_TEXT));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Trigger the IME's commitContent() call and assert its outcome.
        boolean result = triggerImeCommitContent("image/png");
        assertThat(result).isFalse();
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/*", "image/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        triggerImeCommitContent("image/png");
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_INPUT_METHOD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/*", "image/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        triggerImeCommitContent("video/mp4");
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_INPUT_METHOD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_customReceiver_oldTargetSdk() throws Exception {
        configureAppTargetSdkToR();
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/*", "image/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the IME's commitContent() call and assert that the custom receiver was executed.
        triggerImeCommitContent("image/png");
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_INPUT_METHOD, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_linkUri() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/*", "image/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the IME's commitContent() call with a linkUri and assert receiver extras.
        Uri sampleLinkUri = Uri.parse("http://example.com");
        triggerImeCommitContent("image/png", sampleLinkUri, null);
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                contentEq(clip, SOURCE_INPUT_METHOD, 0, sampleLinkUri, null));
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_opts() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/*", "image/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the IME's commitContent() call with opts and assert receiver extras.
        String sampleOptValue = "sampleOptValue";
        triggerImeCommitContent("image/png", null, sampleOptValue);
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                contentEq(clip, SOURCE_INPUT_METHOD, 0, null, sampleOptValue));
    }

    @UiThreadTest
    @Test
    public void testImeCommitContent_linkUriAndOpts() throws Exception {
        initTextViewForEditing("xz", 1);

        // Setup: Configure the receiver to a mock impl.
        String[] receiverMimeTypes = new String[] {"text/*", "image/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger the IME's commitContent() call with a linkUri & opts and assert receiver extras.
        Uri sampleLinkUri = Uri.parse("http://example.com");
        String sampleOptValue = "sampleOptValue";
        triggerImeCommitContent("image/png", sampleLinkUri, sampleOptValue);
        ClipData clip = ClipData.newRawUri("expected", SAMPLE_CONTENT_URI);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView),
                contentEq(clip, SOURCE_INPUT_METHOD, 0, sampleLinkUri, sampleOptValue));
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 2);

        // Trigger drop event. This should execute the default receiver.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerDropEvent(clip);
        assertTextAndCursorPosition("yxz", 1);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_customReceiver() throws Exception {
        initTextViewForEditing("xz", 2);
        String[] receiverMimeTypes = new String[] {"text/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger drop event and assert that the custom receiver was executed.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_DRAG_AND_DROP, 0));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor is moved to the location of the drop before calling the receiver.
        assertTextAndCursorPosition("xz", 0);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_customReceiver_nonEditableTextView() throws Exception {
        // Initialize the view and assert preconditions.
        mTextView.setText("Hello");
        assertTextAndSelection("Hello", -1, -1);
        assertThat(mTextView.isTextSelectable()).isFalse();
        assertThat(mTextView.getText()).isNotInstanceOf(Editable.class);

        // Configure the listener.
        String[] receiverMimeTypes = new String[] {"text/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger drop event and assert that the custom receiver was executed.
        ClipData clip = ClipData.newPlainText("test", "y");
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_DRAG_AND_DROP, 0));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor/selection should not change since the view is not editable.
        assertTextAndSelection("Hello", -1, -1);
    }

    /**
     * This test checks the edge case where a {@link TextView} starts as non-editable and becomes
     * editable during dragging. The test simulates this scenario by setting up an editable
     * {@link TextView}, clearing its focus and then injecting an
     * {@link DragEvent#ACTION_DRAG_LOCATION} event without a prior
     * {@link DragEvent#ACTION_DRAG_STARTED} or {@link DragEvent#ACTION_DRAG_ENTERED} event.
     */
    @UiThreadTest
    @Test
    public void testDragAndDrop_nonEditableTextViewChangedToEditable_actionDragLocation()
            throws Exception {
        // Setup an editable TextView and assert that its insertion controller is enabled.
        initTextViewForEditing("Test drag and drop", 4);
        assertThat(mTextView.getEditorForTesting().getInsertionController()).isNotNull();

        // Focus on another view and assert that the TextView we are going to test doesn't have
        // focus (but still has its insertion controller enabled).
        TextView anotherTextView = mActivity.findViewById(R.id.textview_singleLine);
        anotherTextView.setTextIsSelectable(true);
        anotherTextView.requestFocus();
        assertThat(mTextView.hasFocus()).isFalse();
        assertThat(mTextView.getEditorForTesting().getInsertionController()).isNotNull();

        // Trigger an ACTION_DRAG_LOCATION event without any prior drag events. The TextView should
        // still gracefully handle the event and update its cursor position for the event's
        // location.
        DragEvent dragEvent = createDragEvent(DragEvent.ACTION_DRAG_LOCATION, mTextView.getX(),
                mTextView.getY(), null);
        assertThat(mTextView.onDragEvent(dragEvent)).isTrue();
        assertTextAndCursorPosition("Test drag and drop", 0);
    }

    /**
     * This test checks the edge case where a {@link TextView} starts as non-editable and becomes
     * editable during dragging. The test simulates this scenario by setting up an editable
     * {@link TextView}, clearing its focus and then injecting an
     * {@link DragEvent#ACTION_DROP} event without a prior
     * {@link DragEvent#ACTION_DRAG_STARTED} or {@link DragEvent#ACTION_DRAG_ENTERED} or
     * {@link DragEvent#ACTION_DRAG_LOCATION} event.
     */
    @UiThreadTest
    @Test
    public void testDragAndDrop_nonEditableTextViewChangedToEditable_actionDrop() throws Exception {
        // Setup an editable TextView and assert that its insertion controller is enabled.
        initTextViewForEditing("Test drag and drop", 4);
        assertThat(mTextView.getEditorForTesting().getInsertionController()).isNotNull();

        // Focus on another view and assert that the TextView we are going to test doesn't have
        // focus (but still has its insertion controller enabled).
        TextView anotherTextView = mActivity.findViewById(R.id.textview_singleLine);
        anotherTextView.setTextIsSelectable(true);
        anotherTextView.requestFocus();
        assertThat(mTextView.hasFocus()).isFalse();
        assertThat(mTextView.getEditorForTesting().getInsertionController()).isNotNull();

        // Trigger an ACTION_DROP event without any prior drag events. The TextView should still
        // gracefully handle the event and accept the drop.
        ClipData clip = ClipData.newPlainText("test", "Hi ");
        DragEvent dragEvent = createDragEvent(DragEvent.ACTION_DROP, mTextView.getX(),
                mTextView.getY(), clip);
        assertThat(mTextView.onDragEvent(dragEvent)).isTrue();
        assertTextAndCursorPosition("Hi Test drag and drop", 3);
    }

    @UiThreadTest
    @Test
    public void testDragAndDrop_customReceiver_unsupportedMimeType() throws Exception {
        initTextViewForEditing("xz", 2);
        String[] receiverMimeTypes = new String[] {"text/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger drop event and assert that the custom receiver was executed.
        ClipData clip = new ClipData("test", new String[]{"video/mp4"},
                new ClipData.Item("y", null, SAMPLE_CONTENT_URI));
        triggerDropEvent(clip);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_DRAG_AND_DROP, 0));
        verifyNoMoreInteractions(mMockReceiver);
        // Note: The cursor is moved to the location of the drop before calling the receiver.
        assertTextAndCursorPosition("xz", 0);
    }

    @UiThreadTest
    @Test
    public void testAutofill_noCustomReceiver() throws Exception {
        initTextViewForEditing("xz", 1);

        // Trigger autofill. This should execute the default receiver.
        triggerAutofill("y");
        assertTextAndCursorPosition("y", 1);
    }

    @UiThreadTest
    @Test
    public void testAutofill_customReceiver() throws Exception {
        initTextViewForEditing("xz", 1);
        String[] receiverMimeTypes = new String[] {"text/*"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        // Trigger autofill and assert that the custom receiver was executed.
        triggerAutofill("y");
        ClipData clip = ClipData.newPlainText("", "y");
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_AUTOFILL, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndCursorPosition("xz", 1);
    }

    @UiThreadTest
    @Test
    public void testProcessText_noCustomReceiver() throws Exception {
        initTextViewForEditing("Original text", 0);
        Selection.setSelection(mTextView.getEditableText(), 0, mTextView.getText().length());

        String newText = "Replacement text";
        triggerProcessTextOnActivityResult(newText);
        assertTextAndCursorPosition(newText, newText.length());
    }

    @UiThreadTest
    @Test
    public void testProcessText_customReceiver() throws Exception {
        String originalText = "Original text";
        initTextViewForEditing(originalText, 0);
        Selection.setSelection(mTextView.getEditableText(), 0, originalText.length());
        assertTextAndSelection(originalText, 0, originalText.length());

        String[] receiverMimeTypes = new String[] {"text/plain"};
        mTextView.setOnReceiveContentListener(receiverMimeTypes, mMockReceiver);

        String newText = "Replacement text";
        triggerProcessTextOnActivityResult(newText);
        ClipData clip = ClipData.newPlainText("", newText);
        verify(mMockReceiver, times(1)).onReceiveContent(
                eq(mTextView), contentEq(clip, SOURCE_PROCESS_TEXT, 0));
        verifyNoMoreInteractions(mMockReceiver);
        assertTextAndSelection(originalText, 0, originalText.length());
    }


    private void initTextViewForEditing(final String text, final int cursorPosition) {
        mTextView.setKeyListener(QwertyKeyListener.getInstance(false, Capitalize.NONE));
        mTextView.setTextIsSelectable(true);
        mTextView.requestFocus();

        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        mTextView.setText(ssb, BufferType.EDITABLE);
        mTextView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        Selection.setSelection(mTextView.getEditableText(), cursorPosition);

        assertWithMessage("TextView should have focus").that(mTextView.hasFocus()).isTrue();
        assertTextAndCursorPosition(text, cursorPosition);
    }

    private void assertTextAndCursorPosition(String expectedText, int cursorPosition) {
        assertTextAndSelection(expectedText, cursorPosition, cursorPosition);
    }

    private void assertTextAndSelection(String expectedText, int start, int end) {
        assertThat(mTextView.getText().toString()).isEqualTo(expectedText);
        int[] expected = new int[]{start, end};
        int[] actual = new int[]{mTextView.getSelectionStart(), mTextView.getSelectionEnd()};
        assertWithMessage("Unexpected selection start/end indexes")
                .that(actual).isEqualTo(expected);
    }

    private void onReceive(final OnReceiveContentListener receiver,
            final ClipData clip, final int source, final int flags) {
        ContentInfo payload =
                new ContentInfo.Builder(clip, source)
                .setFlags(flags)
                .build();
        receiver.onReceiveContent(mTextView, payload);
    }

    private void resetTargetSdk() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
    }

    private void configureAppTargetSdkToR() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.R;
    }

    private void configureAppTargetSdkToS() {
        mActivity.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.S;
    }

    private void copyToClipboard(ClipData clip) {
        mClipboardManager.setPrimaryClip(clip);
    }

    private boolean triggerContextMenuAction(final int actionId) {
        return mTextView.onTextContextMenuItem(actionId);
    }

    private boolean triggerImeCommitContent(String mimeType) {
        return triggerImeCommitContent(mimeType, null, null);
    }

    private boolean triggerImeCommitContent(String mimeType, Uri linkUri, String extra) {
        final InputContentInfo contentInfo = new InputContentInfo(
                SAMPLE_CONTENT_URI,
                new ClipDescription("from test", new String[]{mimeType}),
                linkUri);
        final Bundle opts;
        if (extra == null) {
            opts = null;
        } else {
            opts = new Bundle();
            opts.putString(ContentInfoArgumentMatcher.EXTRA_KEY, extra);
        }
        EditorInfo editorInfo = new EditorInfo();
        InputConnection ic = mTextView.onCreateInputConnection(editorInfo);
        return ic.commitContent(contentInfo, 0, opts);
    }

    private void triggerAutofill(CharSequence text) {
        mTextView.autofill(AutofillValue.forText(text));
    }

    private boolean triggerDropEvent(ClipData clip) {
        DragEvent dropEvent = createDragEvent(DragEvent.ACTION_DROP, mTextView.getX(),
                mTextView.getY(), clip);
        return mTextView.onDragEvent(dropEvent);
    }

    private static DragEvent createDragEvent(int action, float x, float y, ClipData clip) {
        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getAction()).thenReturn(action);
        when(dragEvent.getX()).thenReturn(x);
        when(dragEvent.getY()).thenReturn(y);
        when(dragEvent.getClipData()).thenReturn(clip);
        return dragEvent;
    }

    private void triggerProcessTextOnActivityResult(CharSequence replacementText) {
        Intent data = new Intent();
        data.putExtra(Intent.EXTRA_PROCESS_TEXT, replacementText);
        mTextView.onActivityResult(TextView.PROCESS_TEXT_REQUEST_CODE, Activity.RESULT_OK, data);
    }

    private static ContentInfo contentEq(@NonNull ClipData clip, int source, int flags) {
        return argThat(new ContentInfoArgumentMatcher(clip, source, flags, null, null));
    }

    private static ContentInfo contentEq(@NonNull ClipData clip, int source, int flags,
            Uri linkUri, String extra) {
        return argThat(new ContentInfoArgumentMatcher(clip, source, flags, linkUri, extra));
    }

    private static class ContentInfoArgumentMatcher implements ArgumentMatcher<ContentInfo> {
        public static final String EXTRA_KEY = "testExtra";

        @NonNull private final ClipData mClip;
        private final int mSource;
        private final int mFlags;
        @Nullable private final Uri mLinkUri;
        @Nullable private final String mExtra;

        private ContentInfoArgumentMatcher(@NonNull ClipData clip, int source, int flags,
                @Nullable Uri linkUri, @Nullable String extra) {
            mClip = clip;
            mSource = source;
            mFlags = flags;
            mLinkUri = linkUri;
            mExtra = extra;
        }

        @Override
        public boolean matches(ContentInfo actual) {
            ClipData.Item expectedItem = mClip.getItemAt(0);
            ClipData.Item actualItem = actual.getClip().getItemAt(0);
            return Objects.equals(expectedItem.getText(), actualItem.getText())
                    && Objects.equals(expectedItem.getUri(), actualItem.getUri())
                    && mSource == actual.getSource()
                    && mFlags == actual.getFlags()
                    && Objects.equals(mLinkUri, actual.getLinkUri())
                    && extrasMatch(actual.getExtras());
        }

        private boolean extrasMatch(Bundle actualExtras) {
            if (mExtra == null) {
                return actualExtras == null;
            }
            String actualExtraValue = actualExtras.getString(EXTRA_KEY);
            return Objects.equals(mExtra, actualExtraValue);
        }
    }
}
