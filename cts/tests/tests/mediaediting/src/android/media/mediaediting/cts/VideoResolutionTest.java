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
import static org.junit.Assert.assertTrue;

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

import com.android.compatibility.common.util.ApiTest;
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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instrumentation tests for transform Video Resolution for given inputs.
 */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public final class VideoResolutionTest {
  private static final String MEDIA_DIR = WorkDir.getMediaDirString();

  private final String mediaType;
  private final String testFile;
  private final int inpWidth;
  private final int inpHeight;
  private final int outWidth;
  private final int outHeight;
  private final String testId;

  public VideoResolutionTest(String mediaType, String testFile, int inpWidth, int inpHeight,
        int outWidth, int outHeight, String testId) {
    this.mediaType = mediaType;
    this.testFile = testFile;
    this.inpWidth = inpWidth;
    this.inpHeight = inpHeight;
    this.outWidth = outWidth;
    this.outHeight = outHeight;
    this.testId = testId;
  }

  @Parameterized.Parameters(name = "{index}_{6}")
  public static Collection<Object[]> input() {
    // mediaType, clip, params
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][] {
        // H264
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_3840_2160"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_2560_1920"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_1280_720"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_720_480"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_480_360"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_352_288"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_320_240"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_176_144"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_642_240"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_640_322"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_642_642"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_352_288"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_352_322"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_354_290"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_720_480"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_480_360"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_352_288"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_176_144"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_642_240"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_640_322"},
        {MimeTypes.VIDEO_H264,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_642_642"},
        // Hevc
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_3840_2160"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_720_576"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_640_360"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_352_288"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_176_144"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_642_240"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_640_322"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_642_642"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_352_288"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_352_322"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_354_290"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_720_576"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_640_360"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_352_288"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_176_144"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_642_240"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_640_322"},
        {MimeTypes.VIDEO_H265,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_642_642"},
    }));
    return prepareParamList(exhaustiveArgsList);
  }

  private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
    List<Object[]> argsList = new ArrayList<>();

    for (Object[] arg : exhaustiveArgsList) {
      // Parse the string to get params.
      String params = arg[2].toString();
      Matcher matcher = Pattern.compile("^(\\d+)_(\\d+)_to_(\\d+)_(\\d+)$").matcher(params);
      assertTrue(matcher.find());
      int inputWidth = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
      int inputHeight = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
      int outputWidth = Integer.parseInt(Objects.requireNonNull(matcher.group(3)));
      int outputHeight = Integer.parseInt(Objects.requireNonNull(matcher.group(4)));

      String mediaType = arg[0].toString();
      // Trim the mime baseType with slash.
      int lastIndex = mediaType.lastIndexOf('/');
      String mimeType = null;
      if (lastIndex != -1) {
        mimeType = mediaType.substring(lastIndex + 1);
      }
      String testID = String.format("transform_%s_%s", mimeType, arg[2]);

      argsList.add(
          new Object[] {mediaType, arg[1].toString() /* testFile */, inputWidth, inputHeight,
                outputWidth, outputHeight, testID});
    }
    return argsList;
  }

  private static Transformer createTransformer(Context context, String toMediaType) {
    return (new Transformer.Builder(context)
        .setTransformationRequest(
            new TransformationRequest.Builder().setVideoMimeType(toMediaType).build())
        .build());
  }

  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecList#findDecoderForFormat",
      "android.media.MediaFormat#createVideoFormat"})
  @Test
  public void transformTest() throws Exception {
    Format decFormat = new Format.Builder()
        .setSampleMimeType(mediaType)
        .setWidth(inpWidth)
        .setHeight(inpHeight)
        .build();
    Format encFormat = new Format.Builder()
        .setSampleMimeType(mediaType)
        .setWidth(outWidth)
        .setHeight(outHeight)
        .build();

    Preconditions.assertTestFileExists(MEDIA_DIR + testFile);
    Context context = ApplicationProvider.getApplicationContext();
    Assume.assumeTrue("Skipping transformTest for " + testId,
        !AndroidTestUtil.skipAndLogIfFormatsUnsupported(context, testId, decFormat, encFormat));

    Transformer transformer = createTransformer(context, mediaType);
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MEDIA_DIR + testFile));
    ImmutableList<Effect> videoEffects = ImmutableList.of(
        Presentation.createForWidthAndHeight(outWidth, outHeight, 0 /* LAYOUT_SCALE_TO_FIT */));
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
        .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
        .setRemoveAudio(true)
        .build();
    ExportTestResult result = new TransformerAndroidTestRunner.Builder(context, transformer).build()
        .run(testId, editedMediaItem);
    Format muxedFormat = MediaEditingUtil.getMuxedWidthHeight(result.filePath);
    assertThat(muxedFormat.width).isEqualTo(outWidth);
    assertThat(muxedFormat.height).isEqualTo(outHeight);
  }
}
