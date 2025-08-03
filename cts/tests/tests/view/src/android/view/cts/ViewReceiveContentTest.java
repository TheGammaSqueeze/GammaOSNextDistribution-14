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

package android.view.cts;

import static android.view.ContentInfo.SOURCE_CLIPBOARD;
import static android.view.ContentInfo.SOURCE_DRAG_AND_DROP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.ContentInfo;
import android.view.DragEvent;
import android.view.OnReceiveContentListener;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

import java.util.Objects;

/**
 * Tests for {@link View#performReceiveContent} and related code.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ViewReceiveContentTest {
    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule = new ActivityTestRule<>(
            ViewTestCtsActivity.class);

    private ViewTestCtsActivity mActivity;
    private OnReceiveContentListener mReceiver;

    @Before
    public void before() {
        mActivity = mActivityRule.getActivity();
        mReceiver = mock(OnReceiveContentListener.class);
    }

    @Test
    public void testOnReceiveContent_mimeTypes() {
        View view = new View(mActivity);

        // MIME types are null by default
        assertThat(view.getReceiveContentMimeTypes()).isNull();

        // Setting MIME types with a non-null callback works
        String[] mimeTypes = new String[] {"image/*", "video/mp4"};
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        assertThat(view.getReceiveContentMimeTypes()).isEqualTo(mimeTypes);

        // Setting null MIME types and null callback works
        view.setOnReceiveContentListener(null, null);
        assertThat(view.getReceiveContentMimeTypes()).isNull();

        // Setting empty MIME types and null callback works
        view.setOnReceiveContentListener(new String[0], null);
        assertThat(view.getReceiveContentMimeTypes()).isNull();

        // Setting MIME types with a null callback works
        view.setOnReceiveContentListener(mimeTypes, null);
        assertThat(view.getReceiveContentMimeTypes()).isEqualTo(mimeTypes);

        // Setting null or empty MIME types with a non-null callback is not allowed
        try {
            view.setOnReceiveContentListener(null, mReceiver);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            view.setOnReceiveContentListener(new String[0], mReceiver);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }

        // Passing "*/*" as a MIME type is not allowed
        try {
            view.setOnReceiveContentListener(new String[] {"image/gif", "*/*"}, mReceiver);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void testPerformReceiveContent() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"image/*", "video/mp4"};
        ContentInfo samplePayloadGif = sampleUriPayload(SOURCE_CLIPBOARD, "image/gif");
        ContentInfo samplePayloadPdf = sampleUriPayload(SOURCE_CLIPBOARD, "application/pdf");

        // Calling performReceiveContent() returns the payload if there's no listener (default)
        assertThat(view.performReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);

        // Calling performReceiveContent() calls the configured listener
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(any(), any())).thenReturn(null);
        assertThat(view.performReceiveContent(samplePayloadGif)).isNull();

        // Calling performReceiveContent() calls the configured listener even if the MIME type of
        // the content is not in the set of supported MIME types
        assertThat(view.performReceiveContent(samplePayloadPdf)).isNull();

        // Clearing the listener restores default behavior
        view.setOnReceiveContentListener(null, null);
        assertThat(view.performReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);
    }

    @Test
    public void testOnReceiveContent() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"image/*", "video/mp4"};
        ContentInfo samplePayloadGif = sampleUriPayload(SOURCE_CLIPBOARD, "image/gif");

        // Calling onReceiveContent() returns the payload if there's no listener
        assertThat(view.performReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);

        // Calling onReceiveContent() returns the payload even if there is a listener
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(any(), any())).thenReturn(null);
        assertThat(view.onReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);
    }

    @Test
    public void testOnDragEvent_noOnReceiveContentListener() {
        View view = new View(mActivity);

        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getAction()).thenReturn(DragEvent.ACTION_DRAG_STARTED);
        assertThat(view.onDragEvent(dragEvent)).isFalse();

        when(dragEvent.getAction()).thenReturn(DragEvent.ACTION_DROP);
        assertThat(view.onDragEvent(dragEvent)).isFalse();
    }

    @Test
    public void testOnDragEvent_withOnReceiveContentListener() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"text/*", "image/*", "video/mp4"};
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(any(), any())).thenReturn(null);

        // For an ACTION_DRAG_STARTED, we expect true to be returned (no class to the listener yet).
        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getAction()).thenReturn(DragEvent.ACTION_DRAG_STARTED);
        assertThat(view.onDragEvent(dragEvent)).isTrue();

        // For an ACTION_DROP, we expect the listener to be invoked with the content from the drag
        // event.
        when(dragEvent.getAction()).thenReturn(DragEvent.ACTION_DROP);
        ClipData clip = new ClipData(
                new ClipDescription("test", new String[] {"image/jpeg"}),
                new ClipData.Item(Uri.parse("content://example/1")));
        when(dragEvent.getClipData()).thenReturn(clip);
        assertThat(view.onDragEvent(dragEvent)).isTrue();
        verify(mReceiver).onReceiveContent(same(view), contentEq(clip, SOURCE_DRAG_AND_DROP, 0));
    }

    @Test
    public void testOnDragEvent_withOnReceiveContentListener_noneOfTheContentAccepted() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"text/*", "image/*"};
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(same(view), any(ContentInfo.class))).thenAnswer(
                (Answer<ContentInfo>) invocation -> invocation.getArgument(1));

        // When the return value from OnReceiveContentListener.onReceiveContent is the same
        // payload instance that was passed into it, View.onDragEvent should return false.
        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getAction()).thenReturn(DragEvent.ACTION_DROP);
        ClipData clip = new ClipData(
                new ClipDescription("test", new String[] {"video/mp4"}),
                new ClipData.Item(Uri.parse("content://example/1")));
        when(dragEvent.getClipData()).thenReturn(clip);
        assertThat(view.onDragEvent(dragEvent)).isFalse();
        verify(mReceiver).onReceiveContent(same(view), contentEq(clip, SOURCE_DRAG_AND_DROP, 0));
    }

    @Test
    public void testOnDragEvent_withOnReceiveContentListener_someOfTheContentAccepted() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"text/*", "image/*"};
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(same(view), any(ContentInfo.class))).thenReturn(
                sampleUriPayload(SOURCE_DRAG_AND_DROP, "video/mp4"));

        // When the return value from OnReceiveContentListener.onReceiveContent is not the same
        // payload instance that was passed into it, View.onDragEvent should return true.
        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getAction()).thenReturn(DragEvent.ACTION_DROP);
        ClipData clip = new ClipData(
                new ClipDescription("test", new String[] {"video/mp4"}),
                new ClipData.Item(Uri.parse("content://example/1")));
        when(dragEvent.getClipData()).thenReturn(clip);
        assertThat(view.onDragEvent(dragEvent)).isTrue();
        verify(mReceiver).onReceiveContent(same(view), contentEq(clip, SOURCE_DRAG_AND_DROP, 0));
    }

    private static ContentInfo sampleUriPayload(int source, String ... mimeTypes) {
        ClipData clip = new ClipData(
                new ClipDescription("test", mimeTypes),
                new ClipData.Item(Uri.parse("content://example/1")));
        return new ContentInfo.Builder(clip, source).build();
    }

    private static ContentInfo contentEq(ClipData clip, int source, int flags) {
        return argThat(new ContentInfoArgumentMatcher(clip, source, flags));
    }

    private static class ContentInfoArgumentMatcher implements ArgumentMatcher<ContentInfo> {
        private final ClipData mClip;
        private final int mSource;
        private final int mFlags;

        private ContentInfoArgumentMatcher(ClipData clip, int source, int flags) {
            mClip = clip;
            mSource = source;
            mFlags = flags;
        }

        @Override
        public boolean matches(ContentInfo actual) {
            ClipData.Item expectedItem = mClip.getItemAt(0);
            ClipData.Item actualItem = actual.getClip().getItemAt(0);
            return Objects.equals(expectedItem.getText(), actualItem.getText())
                    && Objects.equals(expectedItem.getUri(), actualItem.getUri())
                    && mSource == actual.getSource()
                    && mFlags == actual.getFlags()
                    && actual.getExtras() == null;
        }
    }
}
