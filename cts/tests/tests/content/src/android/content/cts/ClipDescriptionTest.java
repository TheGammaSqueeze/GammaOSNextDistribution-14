/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLinks;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

/**
 * To run:
 * cts-tradefed run singleCommand cts-dev -m CtsContentTestCases -t android.content.cts.ClipDescriptionTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
//@AppModeFull // TODO(Instant) Should clip board data be visible?
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ClipDescriptionTest {
    @Rule public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private UiDevice mUiDevice;
    private Context mContext;

    private static final String SIXTY_CHARS =
            "Hello this is a string of sixty characters in length exactly";

    @Before
    public void setUp() throws Exception {
        if (mRavenwood.isUnderRavenwood() || Process.isSdkSandbox()) return;

        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();

        // Clear any dialogs and launch an activity as focus is needed to access clipboard.
        mUiDevice.pressHome();
        mUiDevice.pressBack();
        launchActivity(MockActivity.class);
    }

    @UiThreadTest
    @Test
    @IgnoreUnderRavenwood(blockedBy = ClipboardManager.class)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes cannot access ClipboardManager.")
    public void testGetTimestamp() {
        final ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        final long timestampBeforeSet = System.currentTimeMillis();
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Dummy text", "Text"));
        final long timestampAfterSet = System.currentTimeMillis();
        final long timestamp = clipboardManager.getPrimaryClipDescription().getTimestamp();
        if (timestamp < timestampBeforeSet || timestamp > timestampAfterSet) {
            fail("Value of timestamp is not as expected.\n"
                    + "timestamp before setting clip: " + logTime(timestampBeforeSet) + "\n"
                    + "timestamp after setting clip: " + logTime(timestampAfterSet) + "\n"
                    + "actual timestamp: " + logTime(timestamp) + "\n"
                    + "clipdata: " + clipboardManager.getPrimaryClip());
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ClipboardManager.class)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes cannot access ClipboardManager.")
    public void testIsStyledText() {
        ClipDescription clipDescription = new ClipDescription(
                "label", new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

        // False as clip description is not attached to anything.
        assertThat(clipDescription.isStyledText()).isFalse();

        SpannableString spannable = new SpannableString("Hello this is some text");
        spannable.setSpan(new UnderlineSpan(), 6, 10, 0);
        ClipData clipData = new ClipData(clipDescription, new ClipData.Item(spannable));

        assertThat(clipDescription.isStyledText()).isTrue();

        ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clipData);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isTrue();

        ClipData clipData2 = ClipData.newPlainText("label", spannable);
        assertThat(clipData2.getDescription().isStyledText()).isTrue();
        clipboardManager.setPrimaryClip(clipData2);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isTrue();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ClipboardManager.class)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes cannot access ClipboardManager.")
    public void testNotStyledText() {
        ClipDescription clipDescription = new ClipDescription(
                "label", new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });
        ClipData clipData = new ClipData(clipDescription, new ClipData.Item("Just text"));
        assertThat(clipDescription.isStyledText()).isFalse();

        ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clipData);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isFalse();

        ClipData clipData2 = ClipData.newPlainText("label", "Just some text");
        assertThat(clipData2.getDescription().isStyledText()).isFalse();
        clipboardManager.setPrimaryClip(clipData2);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isFalse();

        // Test that a URI is not considered styled text.
        ClipData clipDataUri = ClipData.newRawUri("label", Uri.parse("content://test"));
        assertThat(clipDataUri.getDescription().isStyledText()).isFalse();
        clipboardManager.setPrimaryClip(clipDataUri);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isFalse();
    }

    @Test
    public void testClassificationNotCompletedBeforeCopy() {
        ClipDescription clipDescription = new ClipDescription(
                "label", new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });
        assertThat(clipDescription.getClassificationStatus())
                .isEqualTo(ClipDescription.CLASSIFICATION_NOT_COMPLETE);

        ClipData clipData = ClipData.newPlainText("label", "Just some text");
        assertThat(clipDescription.getClassificationStatus())
                .isEqualTo(ClipDescription.CLASSIFICATION_NOT_COMPLETE);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ClipboardManager.class)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes cannot access ClipboardManager.")
    public void testClassificationNotPerformedForVeryLongText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(SIXTY_CHARS);
        }
        String longString = builder.toString();
        ClipData clipData = ClipData.newPlainText("label", longString);

        ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clipData);
        ClipDescription description = clipboardManager.getPrimaryClipDescription();
        assertThat(description.getClassificationStatus())
                .isEqualTo(ClipDescription.CLASSIFICATION_NOT_PERFORMED);
        assertThat(clipboardManager.getPrimaryClip().getItemAt(0).getTextLinks())
                .isNull();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ClipboardManager.class)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes cannot access ClipboardManager.")
    public void testClassificationConfidenceValuesAreValid() throws InterruptedException {
        ClipData clipData = ClipData.newPlainText(
                "label", "Hi Sam, try www.android.com on 05/04/2021 then visit "
                        + "221B Baker Street, London and email test@example.com");
        ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clipData);

        ClipDescription description = clipboardManager.getPrimaryClipDescription();
        for (int i = 0; i < 10; i++) {
            description = clipboardManager.getPrimaryClipDescription();
            if (description.getClassificationStatus()
                    != ClipDescription.CLASSIFICATION_NOT_COMPLETE) {
                break;
            }
            Thread.sleep(1000);
        }

        if (description.getClassificationStatus() == ClipDescription.CLASSIFICATION_NOT_PERFORMED) {
            return;
        }

        float urlConfidence = description.getConfidenceScore(TextClassifier.TYPE_URL);
        float dateConfidence = description.getConfidenceScore(TextClassifier.TYPE_DATE);
        float addressConfidence = description.getConfidenceScore(TextClassifier.TYPE_ADDRESS);
        float emailConfidence = description.getConfidenceScore(TextClassifier.TYPE_EMAIL);

        assertThat(urlConfidence).isIn(Range.closed(0f, 1f));
        assertThat(dateConfidence).isIn(Range.closed(0f, 1f));
        assertThat(addressConfidence).isIn(Range.closed(0f, 1f));
        assertThat(emailConfidence).isIn(Range.closed(0f, 1f));

        if (urlConfidence > 0 || dateConfidence > 0 || addressConfidence > 0
                || emailConfidence > 0) {
            TextLinks textLinks =
                    clipboardManager.getPrimaryClip().getItemAt(0).getTextLinks();
            assertThat(getHighestConfidence(TextClassifier.TYPE_URL, textLinks))
                    .isEqualTo(urlConfidence);
            assertThat(getHighestConfidence(TextClassifier.TYPE_DATE, textLinks))
                    .isEqualTo(dateConfidence);
            assertThat(getHighestConfidence(TextClassifier.TYPE_ADDRESS, textLinks))
                    .isEqualTo(addressConfidence);
            assertThat(getHighestConfidence(TextClassifier.TYPE_EMAIL, textLinks))
                    .isEqualTo(emailConfidence);
        }
    }

    private float getHighestConfidence(String entity, TextLinks textLinks) {
        float result = 0;
        for (TextLinks.TextLink textLink : textLinks.getLinks()) {
            result = Math.max(result, textLink.getConfidenceScore(entity));
        }
        return result;
    }

    /**
     * Convert a System.currentTimeMillis() value to a time of day value like
     * that printed in logs. MM-DD-YY HH:MM:SS.MMM
     *
     * @param millis since the epoch (1/1/1970)
     * @return String representation of the time.
     */
    public static String logTime(long millis) {
        Calendar c = Calendar.getInstance();
        if (millis >= 0) {
            c.setTimeInMillis(millis);
            return String.format("%tm-%td-%ty %tH:%tM:%tS.%tL", c, c, c, c, c, c, c);
        } else {
            return Long.toString(millis);
        }
    }

    private void launchActivity(Class<? extends Activity> clazz) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mContext.getPackageName(), clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.pkg(clazz.getPackageName())), 15000);
    }
}
