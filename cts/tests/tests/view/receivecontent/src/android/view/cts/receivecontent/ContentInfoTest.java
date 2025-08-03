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

package android.view.cts.receivecontent;

import static android.view.ContentInfo.SOURCE_APP;
import static android.view.ContentInfo.SOURCE_AUTOFILL;
import static android.view.ContentInfo.SOURCE_CLIPBOARD;
import static android.view.ContentInfo.SOURCE_DRAG_AND_DROP;
import static android.view.ContentInfo.SOURCE_INPUT_METHOD;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.util.Pair;
import android.view.ContentInfo;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.inputmethod.InputContentInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.inputmethod.IInputContentUriToken;
import com.android.internal.view.IDragAndDropPermissions;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link ContentInfo}.
 *
 * <p>To run: {@code atest CtsViewReceiveContentTestCases:ContentInfoTest}
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ContentInfoTest {

    @Test
    public void testPartition_multipleItems() throws Exception {
        Uri sampleUri = Uri.parse("content://com.example/path");
        ClipData clip = ClipData.newPlainText("", "Hello");
        clip.addItem(new ClipData.Item("Hi", "<b>Salut</b>"));
        clip.addItem(new ClipData.Item(sampleUri));
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                        .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                        .setLinkUri(Uri.parse("http://example.com"))
                        .setExtras(new Bundle())
                        .build();

        // Test splitting when some items match and some don't.
        Pair<ContentInfo, ContentInfo> split;
        split = payload.partition(item -> item.getUri() != null);
        assertThat(split.first.getClip().getItemCount()).isEqualTo(1);
        assertThat(split.second.getClip().getItemCount()).isEqualTo(2);
        assertThat(split.first.getClip().getItemAt(0).getUri()).isEqualTo(sampleUri);
        assertThat(split.first.getClip().getDescription())
                .isNotSameInstanceAs(payload.getClip().getDescription());
        assertThat(split.second.getClip().getDescription())
                .isNotSameInstanceAs(payload.getClip().getDescription());
        assertThat(split.first.getSource()).isEqualTo(SOURCE_CLIPBOARD);
        assertThat(split.first.getLinkUri()).isNotNull();
        assertThat(split.first.getExtras()).isNotNull();
        assertThat(split.second.getSource()).isEqualTo(SOURCE_CLIPBOARD);
        assertThat(split.second.getLinkUri()).isNotNull();
        assertThat(split.second.getExtras()).isNotNull();

        // Test splitting when none of the items match.
        split = payload.partition(item -> false);
        assertThat(split.first).isNull();
        assertThat(split.second).isSameInstanceAs(payload);

        // Test splitting when all of the items match.
        split = payload.partition(item -> true);
        assertThat(split.first).isSameInstanceAs(payload);
        assertThat(split.second).isNull();
    }

    @Test
    public void testPartition_singleItem() throws Exception {
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                        .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                        .setLinkUri(Uri.parse("http://example.com"))
                        .setExtras(new Bundle())
                        .build();

        Pair<ContentInfo, ContentInfo> split;
        split = payload.partition(item -> false);
        assertThat(split.first).isNull();
        assertThat(split.second).isSameInstanceAs(payload);

        split = payload.partition(item -> true);
        assertThat(split.first).isSameInstanceAs(payload);
        assertThat(split.second).isNull();
    }

    @Test
    public void testBuilder_copy() throws Exception {
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo original =
                new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                        .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                        .setLinkUri(Uri.parse("http://example.com"))
                        .setExtras(new Bundle())
                        .build();

        // Verify that that calling the builder with a ContentInfo instance creates a shallow copy.
        ContentInfo copy = new ContentInfo.Builder(original).build();
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy.getClip()).isSameInstanceAs(original.getClip());
        assertThat(copy.getSource()).isEqualTo(original.getSource());
        assertThat(copy.getFlags()).isEqualTo(original.getFlags());
        assertThat(copy.getLinkUri()).isSameInstanceAs(original.getLinkUri());
        assertThat(copy.getExtras()).isSameInstanceAs(original.getExtras());
    }

    @Test
    public void testBuilder_copyAndUpdate() throws Exception {
        ClipData clip1 = ClipData.newPlainText("", "Hello");
        ContentInfo original =
                new ContentInfo.Builder(clip1, SOURCE_CLIPBOARD)
                        .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                        .setLinkUri(Uri.parse("http://example.com"))
                        .setExtras(new Bundle())
                        .build();

        // Verify that calling setters after initializing the builder with a ContentInfo instance
        // updates the fields.
        ClipData clip2 = ClipData.newPlainText("", "Bye");
        ContentInfo copy =
                new ContentInfo.Builder(original)
                        .setClip(clip2)
                        .setSource(SOURCE_APP)
                        .setFlags(0)
                        .setLinkUri(null)
                        .setExtras(null)
                        .build();
        assertThat(copy.getClip().getItemAt(0).getText()).isEqualTo("Bye");
        assertThat(copy.getSource()).isEqualTo(SOURCE_APP);
        assertThat(copy.getFlags()).isEqualTo(0);
        assertThat(copy.getLinkUri()).isEqualTo(null);
        assertThat(copy.getExtras()).isEqualTo(null);
    }

    @Test
    public void testReleasePermissions() throws Exception {
        // Verify that releasePermissions() doesn't throw when there is no permissions metadata.
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_CLIPBOARD).build();
        payload.releasePermissions();
    }

    @Test
    public void testReleasePermissions_ime() throws Exception {
        // Verify that releasePermissions() makes the appropriate call when IME permissions
        // metadata is present.
        IInputContentUriToken token = mock(IInputContentUriToken.class);
        InputContentInfo inputContentInfo =
                new InputContentInfo(
                        Uri.parse("content://example/1"),
                        new ClipDescription("", new String[] {"image/png"}));
        inputContentInfo.setUriToken(token);
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_INPUT_METHOD)
                        .setInputContentInfo(inputContentInfo)
                        .build();
        payload.releasePermissions();
        verify(token).release();
    }

    @Test
    public void testReleasePermissions_ime_afterCopy() throws Exception {
        IInputContentUriToken token = mock(IInputContentUriToken.class);
        InputContentInfo inputContentInfo =
                new InputContentInfo(
                        Uri.parse("content://example/1"),
                        new ClipDescription("", new String[] {"image/png"}));
        inputContentInfo.setUriToken(token);
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_INPUT_METHOD)
                        .setInputContentInfo(inputContentInfo)
                        .build();

        // Verify that making a copy of the payload via the builder carries along the IME
        // permissions metadata.
        ContentInfo copy = new ContentInfo.Builder(payload).build();
        copy.releasePermissions();
        verify(token).release();
    }

    @Test
    public void testReleasePermissions_dragAndDrop() throws Exception {
        // Verify that releasePermissions() makes the appropriate call when permissions metadata
        // for drag-and-drop is present.
        IDragAndDropPermissions permission = mock(IDragAndDropPermissions.class);
        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getDragAndDropPermissions()).thenReturn(permission);
        DragAndDropPermissions dragAndDropPermissions = DragAndDropPermissions.obtain(dragEvent);

        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_DRAG_AND_DROP)
                        .setDragAndDropPermissions(dragAndDropPermissions)
                        .build();
        payload.releasePermissions();
        verify(permission).release();
    }

    @Test
    public void testReleasePermissions_dragAndDrop_afterCopy() throws Exception {
        IDragAndDropPermissions permission = mock(IDragAndDropPermissions.class);
        DragEvent dragEvent = mock(DragEvent.class);
        when(dragEvent.getDragAndDropPermissions()).thenReturn(permission);
        DragAndDropPermissions dragAndDropPermissions = DragAndDropPermissions.obtain(dragEvent);
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_DRAG_AND_DROP)
                        .setDragAndDropPermissions(dragAndDropPermissions)
                        .build();

        // Verify that making a copy of the payload via the builder carries along the permissions
        // metadata for drag-and-drop.
        ContentInfo copy = new ContentInfo.Builder(payload).build();
        copy.releasePermissions();
        verify(permission).release();
    }

    @Test
    public void testParcel_withMinimalMetadata() throws Exception {
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo original = new ContentInfo.Builder(clip, SOURCE_AUTOFILL).build();

        ContentInfo fromParcel = parcelAndUnparcel(original);
        assertThat(fromParcel.getClip().getItemAt(0).getText()).isEqualTo("Hello");
        assertThat(fromParcel.getSource()).isEqualTo(SOURCE_AUTOFILL);
        assertThat(fromParcel.getFlags()).isEqualTo(0);
        assertThat(fromParcel.getLinkUri()).isEqualTo(null);
        assertThat(fromParcel.getExtras()).isEqualTo(null);
    }

    @Test
    public void testParcel_withAllMetadata() throws Exception {
        ClipData clip = ClipData.newPlainText("", "Hello");
        Bundle extras = new Bundle();
        extras.putString("one", "sample value");
        ContentInfo original =
                new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                        .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                        .setLinkUri(Uri.parse("http://example.com"))
                        .setExtras(extras)
                        .build();

        ContentInfo fromParcel = parcelAndUnparcel(original);
        assertThat(fromParcel.getClip().getItemAt(0).getText()).isEqualTo("Hello");
        assertThat(fromParcel.getSource()).isEqualTo(original.getSource());
        assertThat(fromParcel.getFlags()).isEqualTo(original.getFlags());
        assertThat(fromParcel.getLinkUri()).isEqualTo(original.getLinkUri());
        assertThat(fromParcel.getExtras().keySet()).isEqualTo(extras.keySet());
        assertThat(fromParcel.getExtras().getString("one")).isEqualTo("sample value");
    }

    @Test
    public void testParcel_imePermissionsMetadata() throws Exception {
        ClipData clip = ClipData.newPlainText("", "Hello");
        InputContentInfo inputContentInfo =
                new InputContentInfo(
                        Uri.parse("content://example/1"),
                        new ClipDescription("", new String[] {"image/png"}));
        ContentInfo payload =
                new ContentInfo.Builder(clip, SOURCE_INPUT_METHOD)
                        .setInputContentInfo(inputContentInfo)
                        .build();

        // Verify that releasePermissions() doesn't throw an exception when IME permissions
        // metadata is present after going through parceling.
        ContentInfo fromParcel = parcelAndUnparcel(payload);
        fromParcel.releasePermissions();
    }

    private static ContentInfo parcelAndUnparcel(ContentInfo payload) {
        Parcel parcel = Parcel.obtain();
        payload.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return ContentInfo.CREATOR.createFromParcel(parcel);
    }
}
