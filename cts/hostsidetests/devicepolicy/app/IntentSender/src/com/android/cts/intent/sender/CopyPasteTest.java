/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.intent.sender;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.test.InstrumentationTestCase;

import java.util.concurrent.Semaphore;

public class CopyPasteTest extends InstrumentationTestCase
        implements ClipboardManager.OnPrimaryClipChangedListener {

    private IntentSenderActivity mActivity;
    private ClipboardManager mClipboard;
    private Semaphore mNotified;

    private static final String INITIAL_TEXT = "initial text";
    private static final String NEW_TEXT = "new text";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        mActivity = launchActivity(context.getPackageName(), IntentSenderActivity.class, null);
        mClipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void tearDown() throws Exception {
        mActivity.finish();
        super.tearDown();
    }

    public void testCopyInitialText() throws Exception {
        ClipData clip = ClipData.newPlainText(""/*label*/, INITIAL_TEXT);
        mClipboard.setPrimaryClip(clip);
        assertEquals(INITIAL_TEXT , getTextFromClipboard());
    }

    public void testCopyNewText() throws Exception {
        ClipData clip = ClipData.newPlainText(""/*label*/, NEW_TEXT);
        mClipboard.setPrimaryClip(clip);
        assertEquals(NEW_TEXT , getTextFromClipboard());
    }

    public void testClipboardHasInitialTextOrNull() throws Exception {
        String clipboardText = getTextFromClipboard();
        assertTrue("The clipboard text is " + clipboardText + " but should be <null> or "
                + INITIAL_TEXT, clipboardText == null || clipboardText.equals(INITIAL_TEXT));
    }

    public void testClipboardHasNewText() throws Exception {
        assertEquals(NEW_TEXT, getTextFromClipboard());
    }

    private String getTextFromClipboard() {
        ClipData clip = mClipboard.getPrimaryClip();
        if (clip == null) {
            return null;
        }
        ClipData.Item item = clip.getItemAt(0);
        if (item == null) {
            return null;
        }
        CharSequence text = item.getText();
        if (text == null) {
            return null;
        }
        return text.toString();
    }


    @Override
    public void onPrimaryClipChanged() {
        mNotified.release();
    }

}
