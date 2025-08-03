/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.mediaediting.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;

import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.Preconditions;

import com.google.common.collect.ImmutableList;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Instrumentation tests for checking transform aspects ratio for given inputs.
 */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public final class TransformVideoAspectRatio {

  private static final String MEDIA_DIR = WorkDir.getMediaDirString();

  private final String mediaType;
  private final int width;
  private final int height;
  private final float requestedAspectRatio;
  private final String testFile;
  private final String testId;

  public TransformVideoAspectRatio(String mediaType, int width, int height,
      float requestedAspectRatio, String testFile, String testId) {
    this.mediaType = mediaType;
    this.width = width;
    this.height = height;
    this.requestedAspectRatio = requestedAspectRatio;
    this.testFile = testFile;
    this.testId = testId;
  }

  @Parameterized.Parameters(name = "{index}_{5}")
  public static Collection<Object[]> input() {
    // mediaType, width, height, aspectRatio, clip
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][] {
        // H264
        {MimeTypes.VIDEO_H264, 1920, 1080, (float) 1 / 2,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING},
        {MimeTypes.VIDEO_H264, 1920, 1080, (float) 4 / 3,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING},
        {MimeTypes.VIDEO_H264, 1920, 1080, (float) 3 / 2,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING},
        {MimeTypes.VIDEO_H264, 320, 240, (float) 1 / 4,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING},
        {MimeTypes.VIDEO_H264, 320, 240, (float) 1 / 3,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING},
        {MimeTypes.VIDEO_H264, 320, 240, (float) 5 / 6,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING},
        {MimeTypes.VIDEO_H264, 642, 642, (float) 1 / 4,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING},
        {MimeTypes.VIDEO_H264, 642, 642, (float) 1 / 3,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING},
        {MimeTypes.VIDEO_H264, 642, 642, (float) 3 / 4,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING},
        // Hevc
        {MimeTypes.VIDEO_H265, 1920, 1080, (float) 1 / 2,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 1920, 1080, (float) 5 / 6,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 1920, 1080, (float) 4 / 3,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 1920, 1080, (float) 3 / 2,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 720, 480, (float) 1 / 4,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 720, 480, (float) 1 / 2,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 720, 480, (float) 5 / 6,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING},
        {MimeTypes.VIDEO_H265, 642, 642, (float) 3 / 4,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING},
        {MimeTypes.VIDEO_H265, 642, 642, (float) 5 / 6,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING},
        {MimeTypes.VIDEO_H265, 642, 642, (float) 4 / 3,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING},
        {MimeTypes.VIDEO_H265, 608, 1080, (float) 1 / 2,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_608W_1080H_4S_URI_STRING},
        {MimeTypes.VIDEO_H265, 608, 1080, (float) 3 / 4,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_608W_1080H_4S_URI_STRING},
        {MimeTypes.VIDEO_H265, 608, 1080, (float) 5 / 6,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_608W_1080H_4S_URI_STRING},
        {MimeTypes.VIDEO_H265, 608, 1080, (float) 3 / 2,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_608W_1080H_4S_URI_STRING},
    }));
    return prepareParamList(exhaustiveArgsList);
  }

  public static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
    List<Object[]> argsList = new ArrayList<>();
    int argLength = exhaustiveArgsList.get(0).length;

    for (Object[] arg : exhaustiveArgsList) {
      String from = arg[0].toString();
      // Trim the mime baseType with slash.
      int lastIndex = from.lastIndexOf('/');
      if (lastIndex != -1) {
        from = from.substring(lastIndex + 1);
      }

      String testId = String.format("transform_%s_%dx%d_To_aspectRatio_%f", from, (int) arg[1],
          (int) arg[2], (float) arg[3]);
      Object[] argUpdate = Arrays.copyOf(arg, argLength + 1);
      argUpdate[argLength] = testId;
      argsList.add(argUpdate);
    }
    return argsList;
  }

  private Format createDecFormat() {
    return new Format.Builder()
        .setSampleMimeType(mediaType)
        .setWidth(width)
        .setHeight(height)
        .build();
  }

  private Format createEncFormat() {
    float requestedWidth, requestedHeight;
    float inputAspectRatio = (float) width / height;
    if (requestedAspectRatio > 1) {
      if (requestedAspectRatio >= inputAspectRatio) {
        requestedWidth = height * requestedAspectRatio;
        requestedHeight = height;
      } else {
        requestedWidth = width;
        requestedHeight = width / requestedAspectRatio;
      }
    } else {
      if (requestedAspectRatio >= inputAspectRatio) {
        requestedWidth = height;
        requestedHeight = height * requestedAspectRatio;
      } else {
        requestedWidth = width / requestedAspectRatio;
        requestedHeight = width;
      }
    }
    return new Format.Builder()
        .setSampleMimeType(mediaType)
        .setWidth(Math.round(requestedWidth))
        .setHeight(Math.round(requestedHeight))
        .build();
  }

  private static Transformer createTransformer(Context context, String toMediaType) {
    return new Transformer.Builder(context).setTransformationRequest(
        new TransformationRequest.Builder().setVideoMimeType(toMediaType).build()).build();
  }

  @Test
  public void transcodeTest() throws Exception {
    Preconditions.assertTestFileExists(MEDIA_DIR + testFile);
    Context context = ApplicationProvider.getApplicationContext();
    Assume.assumeTrue("Skipping transcodeTest for" + testId,
        !AndroidTestUtil.skipAndLogIfFormatsUnsupported(
            context, testId, createDecFormat(), createEncFormat()));

    Transformer transformer = createTransformer(context, mediaType);
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MEDIA_DIR + testFile));
    ImmutableList<Effect> videoEffects = ImmutableList.of(
        Presentation.createForAspectRatio(requestedAspectRatio, 0 /* LAYOUT_SCALE_TO_FIT */));
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
        .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
        .setRemoveAudio(true)
        .build();
    ExportTestResult result = new TransformerAndroidTestRunner.Builder(context, transformer).build()
        .run(testId, editedMediaItem);

    float inputAspectRatio = (float) width / height;
    Format muxedOutputFormat = MediaEditingUtil.getMuxedWidthHeight(result.filePath);
    if (requestedAspectRatio > 1) {
      if (requestedAspectRatio >= inputAspectRatio) {
        assertThat(muxedOutputFormat.width).isEqualTo(
            Math.round(height * requestedAspectRatio));
        assertThat(muxedOutputFormat.height).isEqualTo(height);
      } else {
        assertThat(muxedOutputFormat.width).isEqualTo(width);
        assertThat(muxedOutputFormat.height).isEqualTo(
            Math.round(width / requestedAspectRatio));
      }
    } else {
      // Encoders commonly support higher maximum widths than maximum heights.
      // VideoTranscodingSamplePipeline#getSurfaceInfo may rotate frame before encoding, so the
      // encoded frame's width >= height, and sets rotationDegrees in the output Format to ensure
      // the frame is displayed in the correct orientation.
      assertThat(muxedOutputFormat.rotationDegrees).isEqualTo(90);
      if (requestedAspectRatio >= inputAspectRatio) {
        assertThat(muxedOutputFormat.width).isEqualTo(height);
        assertThat(muxedOutputFormat.height).isEqualTo(
            Math.round(height * requestedAspectRatio));
      } else {
        assertThat(muxedOutputFormat.width).isEqualTo(Math.round(width / requestedAspectRatio));
        assertThat(muxedOutputFormat.height).isEqualTo(width);
      }
    }
  }
}
