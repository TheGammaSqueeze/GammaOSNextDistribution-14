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

import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Instrumentation tests for checking Transcoding quality for given inputs. */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public final class TranscodeQualityTest {
  private static final String MEDIA_DIR = WorkDir.getMediaDirString();
  private static final double EXPECTED_MINIMUM_SSIM = 0.9;

  private final String fromMediaType;
  private final String toMediaType;
  private final int width;
  private final int height;
  private final float frameRate;
  private final String testFile;
  private final boolean isWithinCddRequirements;
  private final String testId;

  public TranscodeQualityTest(String fromMediaType, String toMediaType, int width, int height,
      float frameRate, String testFile, boolean isWithinCddRequirements, String testId) {
    this.fromMediaType = fromMediaType;
    this.toMediaType = toMediaType;
    this.width = width;
    this.height = height;
    this.frameRate = frameRate;
    this.testFile = testFile;
    this.isWithinCddRequirements = isWithinCddRequirements;
    this.testId = testId;
  }

  @Parameterized.Parameters(name = "{index}_{7}")
  public static Collection<Object[]> input() {
    // fromMediaType, toMediaType, width, height, frameRate, clip, isWithinCddRequirements
    // Note: isWithinCddRequirements is the test which we never skip as the input and output formats
    // should be within CDD requirements on all supported API versions.
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][] {
        {MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H264, 1920, 1080, 30.00f,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, 1920, 1080, 30.00f,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H264, 320, 240, 30.00f,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            true},
        {MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, 320, 240, 30.00f,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, 642, 642, 30.00f,
            MediaEditingUtil.MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H265, 1920, 1080, 30.00f,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264, 1920, 1080, 30.00f,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H265, 720, 480, 30.00f,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264, 720, 480, 30.00f,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING,
            false},
        {MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264, 642, 642, 30.00f,
            MediaEditingUtil.MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING,
            false},
    }));
    return prepareParamList(exhaustiveArgsList);
  }

  public static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
    List<Object[]> argsList = new ArrayList<>();
    int argLength = exhaustiveArgsList.get(0).length;

    for (Object[] arg : exhaustiveArgsList) {
      String from = arg[0].toString();
      String to = arg[1].toString();
      // Trim the mime baseType with slash.
      int lastIndex = from.lastIndexOf('/');
      if (lastIndex != -1) {
        from = from.substring(lastIndex + 1);
      }
      lastIndex = to.lastIndexOf('/');
      if (lastIndex != -1) {
        to = to.substring(lastIndex + 1);
      }

      String testID = String.format("transcode_%s_To_%s_%dx%d_%dfps_%s_ssim", from, to,
          (int) arg[2], (int) arg[3], Math.round((float) arg[4]),
          (boolean) arg[6] ? "ForceEncoding" : "");
      Object[] argUpdate = new Object[argLength + 1];
      System.arraycopy(arg, 0, argUpdate, 0, argLength);
      argUpdate[argLength] = testID;
      argsList.add(argUpdate);
    }
    return argsList;
  }

  public static Transformer createTransformerForForceEncode(Context context, String toMediaType) {
    return new Transformer.Builder(context)
        .setTransformationRequest(
            new TransformationRequest.Builder().setVideoMimeType(toMediaType).build())
        .setEncoderFactory(new AndroidTestUtil.ForceEncodeEncoderFactory(context))
        .build();
  }

  public static Transformer createTransformer(Context context, String toMediaType) {
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
  public void transcodeTest() throws Exception {
    Format decFormat = new Format.Builder()
        .setSampleMimeType(fromMediaType)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .build();
    Format encFormat = new Format.Builder()
        .setSampleMimeType(toMediaType)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(frameRate)
        .build();

    Preconditions.assertTestFileExists(MEDIA_DIR + testFile);
    Context context = ApplicationProvider.getApplicationContext();
    if (!isWithinCddRequirements) {
      Assume.assumeTrue("Skipping transcodeTest for " + testId,
          !AndroidTestUtil.skipAndLogIfFormatsUnsupported(
              context, testId, decFormat, encFormat));
    }

    Transformer transformer;
    if (isWithinCddRequirements) {
      transformer = createTransformerForForceEncode(context, toMediaType);
    } else {
      transformer = createTransformer(context, toMediaType);
    }

    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MEDIA_DIR + testFile));
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true)
        .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, editedMediaItem);
    assertThat(result.ssim).isGreaterThan(EXPECTED_MINIMUM_SSIM);
  }
}
