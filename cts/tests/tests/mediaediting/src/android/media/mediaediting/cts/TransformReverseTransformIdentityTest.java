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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;

import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.Transformer;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instrumentation tests which verify that quality shouldn't be reduced too much when
 * scaling/resizing and then reversing the operation.
 */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public final class TransformReverseTransformIdentityTest {
  private static final String MEDIA_DIR = WorkDir.getMediaDirString();
  private static final int SET_SCALE = 1;
  private static final int SET_RESOLUTION = 2;
  private static final double EXPECTED_MINIMUM_SSIM = 0.8;

  private final String mediaType;
  private final int transformationRequestType;
  private final String testFile;
  private final int inpWidth;
  private final int inpHeight;
  private final int outWidth;
  private final int outHeight;
  private final String testId;

  public TransformReverseTransformIdentityTest(String mediaType, int transformationRequestType,
      String testFile, int inpWidth, int inpHeight, int outWidth, int outHeight, String testId) {
    this.mediaType = mediaType;
    this.transformationRequestType = transformationRequestType;
    this.testFile = testFile;
    this.inpWidth = inpWidth;
    this.inpHeight = inpHeight;
    this.outWidth = outWidth;
    this.outHeight = outHeight;
    this.testId = testId;
  }

  @Parameterized.Parameters(name = "{index}_{7}")
  public static Collection<Object[]> input() {
    // mediaType, transformationRequestType, clip, ssim, params
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][] {
        // Scale X and Y
        {MimeTypes.VIDEO_H264, SET_SCALE,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            "642_642_to_642_1284"},
        {MimeTypes.VIDEO_H265, SET_SCALE,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_1440_480"},
        {MimeTypes.VIDEO_H265, SET_SCALE,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_1920_2160"},

        // Resolution
        {MimeTypes.VIDEO_H264, SET_RESOLUTION,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            "320_240_to_640_480"},
        {MimeTypes.VIDEO_H264, SET_RESOLUTION,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            "1920_1080_to_960_540"},
        {MimeTypes.VIDEO_H265, SET_RESOLUTION,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            "720_480_to_1440_960"},
        {MimeTypes.VIDEO_H265, SET_RESOLUTION,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            "1920_1080_to_640_360"},
    }));
    return prepareParamList(exhaustiveArgsList);
  }

  private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
    List<Object[]> argsList = new ArrayList<>();

    for (Object[] arg : exhaustiveArgsList) {
      // Parse the string to get params.
      String params = arg[3].toString();
      Matcher matcher = Pattern.compile("^(\\d+)_(\\d+)_to_(\\d+)_(\\d+)$").matcher(params);
      assertTrue(matcher.find());
      int inputWidth = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
      int inputHeight = Integer.parseInt(Objects.requireNonNull(matcher.group(2)));
      int outputWidth = Integer.parseInt(Objects.requireNonNull(matcher.group(3)));
      int outputHeight = Integer.parseInt(Objects.requireNonNull(matcher.group(4)));

      // Generate testId
      String transformType;
      int transformationType = Integer.parseInt(arg[1].toString());
      if (transformationType == SET_SCALE) {
        transformType =
            "ScaleX_" + outputWidth / inputWidth + "_ScaleY_" + outputHeight / inputHeight;
      } else {
        transformType = "ResolutionChange";
      }
      String mediaType = arg[0].toString();
      // Trim the mime baseType with slash.
      int lastIndex = mediaType.lastIndexOf('/');
      String mimeType = null;
      if (lastIndex != -1) {
        mimeType = mediaType.substring(lastIndex + 1);
      }
      String testID = String.format("transform_%s_%s_%s", transformType, mimeType, params);

      argsList.add(
          new Object[] {mediaType, transformationType, arg[2].toString() /* testFile */, inputWidth,
              inputHeight, outputWidth, outputHeight, testID});
    }
    return argsList;
  }

  private static boolean checkCodecSupported(Context context, String testId, String mediaType,
      int inpWidth, int inpHeight, int outWidth, int outHeight)
      throws JSONException, IOException, DecoderQueryException {
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
    boolean transformCodecSupported = !AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, decFormat, encFormat);
    boolean reverseTransformCodecSupported = !AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, encFormat, decFormat);
    return transformCodecSupported && reverseTransformCodecSupported;
  }

  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecList#findDecoderForFormat",
      "android.media.MediaFormat#createVideoFormat"})
  @Test
  public void transformTest() throws Exception {
    Preconditions.assertTestFileExists(MEDIA_DIR + testFile);
    Context context = ApplicationProvider.getApplicationContext();
    Assume.assumeTrue("Skipping transformTest for " + testId,
        checkCodecSupported(context, testId, mediaType, inpWidth, inpHeight, outWidth, outHeight));

    Transformer transformer = new Transformer.Builder(context).build();
    Effects videoEffects, reverseVideoEffects;
    if (transformationRequestType == SET_SCALE) {
      videoEffects = new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.of(
          new ScaleAndRotateTransformation.Builder().setScale((float) outWidth / inpWidth,
              (float) outHeight / inpHeight).build()));
      reverseVideoEffects = new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.of(
          new ScaleAndRotateTransformation.Builder().setScale((float) inpWidth / outWidth,
              (float) inpHeight / outHeight).build()));
    } else {
      videoEffects = new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.of(
          Presentation.createForWidthAndHeight(outWidth, outHeight, 0 /* LAYOUT_SCALE_TO_FIT */)));
      reverseVideoEffects = new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.of(
          Presentation.createForWidthAndHeight(inpWidth, inpHeight, 0 /* LAYOUT_SCALE_TO_FIT */)));
    }

    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(
        MediaItem.fromUri(Uri.parse(MEDIA_DIR + testFile))).setEffects(videoEffects)
        .setRemoveAudio(true).build();
    ExportTestResult transformationResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    Format muxedOutputFormat = MediaEditingUtil.getMuxedWidthHeight(transformationResult.filePath);
    if (outWidth > outHeight) {
      assertThat(muxedOutputFormat.width).isEqualTo(outWidth);
      assertThat(muxedOutputFormat.height).isEqualTo(outHeight);
    } else {
      // Encoders commonly support higher maximum widths than maximum heights.
      // VideoTranscodingSamplePipeline#getSurfaceInfo may rotate frame before encoding, so the
      // encoded frame's width >= height, and sets rotationDegrees in the output Format to ensure
      // the frame is displayed in the correct orientation.
      assertThat(muxedOutputFormat.rotationDegrees).isEqualTo(90);
      assertThat(muxedOutputFormat.width).isEqualTo(outHeight);
      assertThat(muxedOutputFormat.height).isEqualTo(outWidth);
    }

    EditedMediaItem reverseEditedMediaItem = new EditedMediaItem.Builder(
        MediaItem.fromUri(transformationResult.filePath)).setEffects(reverseVideoEffects)
        .setRemoveAudio(true).build();
    ExportTestResult reverseTransformationResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId + "_reverseTransform", reverseEditedMediaItem);

    muxedOutputFormat = MediaEditingUtil.getMuxedWidthHeight(reverseTransformationResult.filePath);
    assertThat(muxedOutputFormat.width).isEqualTo(inpWidth);
    assertThat(muxedOutputFormat.height).isEqualTo(inpHeight);

    double ssim = SsimHelper.calculate(context,
        checkNotNull(Uri.parse(MEDIA_DIR + testFile)).toString(),
        reverseTransformationResult.filePath);
    assertThat(ssim).isGreaterThan(EXPECTED_MINIMUM_SSIM);
  }
}
