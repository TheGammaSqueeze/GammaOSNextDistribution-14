/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "ITS-StatsImage-JNI"
#include <android/log.h>
#include <inttypes.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <string>
#include <unordered_map>
#include <vector>
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// StatsFormat defines the different possible stats image formats that can be
// used.
enum StatsFormat {
  RAW10_STATS = 0,
  RAW10_QUAD_BAYER_STATS,
  RAW16_STATS,
  RAW16_QUAD_BAYER_STATS,
};

// A map from strings to StatsFormat values, which can be used to convert from
// a string representation of a stats image format to the corresponding
// StatsFormat enum value.
static std::unordered_map<std::string, StatsFormat> const statsFormatMap = {
    {"Raw10Stats", RAW10_STATS},
    {"Raw10QuadBayerStats", RAW10_QUAD_BAYER_STATS},
    {"Raw16Stats", RAW16_STATS},
    {"Raw16QuadBayerStats", RAW16_QUAD_BAYER_STATS}
};

/**
 * Gets the pixel value in the image buffer given buffer pos and pixel index.
 *
 * Parameters:
 *   buf: unsigned char*
 *     The buffer containing the image data.
 *   statsFormat: StatsFormat
 *     A enum StatsFormat value that specifies the format of stats images.
 *     See it's valid values in `enum StatsFormat`.
 *   pixelIndex: const int
 *     The index of the pixel to compute the value of.
 *
 * Returns:
 *   pixelValue: int
 *     The value of the current pixel.
 */
int getPixelValue(unsigned char* buf,
                  StatsFormat statsFormat,
                  const int pixelIndex) {
    int pixelValue;
    switch (statsFormat) {
        case RAW10_STATS:
        case RAW10_QUAD_BAYER_STATS: {
            // For RAW10 images, each 4 consecutive pixels are packed into 5 bytes.

            // The index of the current pixel in the 4 pixels group.
            int pixelSubIndex = pixelIndex % 4;
            // The number of bytes before the current 4 pixels group.
            int byteIndex = (pixelIndex / 4) * 5;
            // pbuf points to the start of the current 4 pixels group.
            unsigned char* pbuf = buf + byteIndex;
            // The lower 2 bits.
            int low = ((*(pbuf + 4)) >> (pixelSubIndex * 2)) & 0x3;
            // The higher 8 bits.
            int high = *(pbuf + pixelSubIndex);
            pixelValue = (high << 2) | low;
            break;
        }

        case RAW16_STATS:
        case RAW16_QUAD_BAYER_STATS: {
            // For RAW16 images, each pixel consists of 16 consecutive bytes.

            // The number of bytes before the current pixel.
            int byteIndex = pixelIndex * 2;
            // pbuf points to the starting byte of the current pixel.
            unsigned char* pbuf = buf + byteIndex;
            // The lower 8 bits.
            int low = *pbuf;
            // The higher 8 bits.
            int high = *(pbuf + 1);
            pixelValue = (high << 8) | low;
            break;
        }

        default: {
            pixelValue = 0;
            break;
        }
    }

    return pixelValue;
}

/**
 * Computes the mean and variance of each channel for grid cell (gy, gx).
 *
 * Parameters:
 *   buf: unsigned char*
 *     The image buffer.
 *   statsFormat: StatsFormat
 *     A enum StatsFormat value that specifies the format of stats images.
 *     See it's valid values in `enum StatsFormat`.
 *   isQuadBayer: bool
 *     Whether the image is a quad bayer image.
 *   paw: int
 *     The pixel array width.
 *   ngx: int
 *     The number of grid cells in the x direction.
 *   aax: int
 *     The x coordinate of the top-left corner of the grid cell.
 *   aay: int
 *     The y coordinate of the top-left corner of the grid cell.
 *   gw: int
 *     The width of the grid cell.
 *   gh: int
 *     The height of the grid cell.
 *   gy: int
 *     The y index of the grid cell.
 *   gx: int
 *     The x index of the grid cell.
 *   numOfChannels: int
 *     The number of channels in the image.
 *   means: std::vector<float>&
 *     The output vector for the mean values.
 *   vars: std::vector<float>&
 *     The output vector for the variance values.
 */
void computeSingleCellStats(unsigned char* buf,
                            StatsFormat statsFormat,
                            bool isQuadBayer,
                            const int paw,
                            int ngx,
                            const int aax,
                            const int aay,
                            const int gw,
                            const int gh,
                            int gy,
                            int gx,
                            const int numOfChannels,
                            std::vector<float>& means,
                            std::vector<float>& vars) {
    // Vector of sums of pixel values in each channel.
    std::vector<double> sum(numOfChannels, 0);
    // Vector of sums of squared pixel values in each channel.
    std::vector<double> sumSq(numOfChannels, 0);
    // Vector of number of pixels in each channel.
    std::vector<int> count(numOfChannels, 0);

    // Iterate over pixels in the grid cell.
    for (int y = aay + gy * gh; y < aay + (gy + 1) * gh; y++) {
        int pixelIndex = y * paw + gx * gw + aax;
        // The offset of channels of different y values.
        int chOffsetY = isQuadBayer ? (y & 0x3) * 4 : (y & 0x1) * 2;
        for (int x = aax + gx * gw; x < aax + (gx + 1) * gw; x++) {
            // ch is the channel index with range [0, numOfChannels-1].
            int chOffsetX = isQuadBayer ? (x & 0x3) : (x & 1);
            int ch = chOffsetY + chOffsetX;
            int pixelValue = getPixelValue(buf, statsFormat, pixelIndex);

            sum[ch] += pixelValue;
            sumSq[ch] += pixelValue * pixelValue;
            count[ch]++;
            pixelIndex++;
        }
    }

    const int baseIndex = (gy * ngx + gx) * numOfChannels;
    for (int ch = 0; ch < numOfChannels; ch++) {
        if (count[ch] == 0) {
            ALOGE("Found zero count at grid cell (gy, gx, ch) = (%d, %d, %d).",
                  gy, gx, ch);
            continue;
        }
        // Computes mean and variance using double precision to avoid negative
        // variance.
        double m = sum[ch] / (double) count[ch];
        double mSq = sumSq[ch] / (double) count[ch];
        // In probability theory, Var(X) = E[X^2] - E[X]^2.
        double variance = mSq - m * m;
        int index = baseIndex + ch;
        means[index] = (float) m;
        vars[index] = (float) variance;
        if (vars[index] < 0) {
            ALOGE("Variance < 0 at grid cell (gy, gx, ch) = (%d, %d, %d): "
                  "m=%lf, mSq=%lf, double variance=%lf, float variance=%f.",
                  gy, gx, ch, m, mSq, variance, vars[index]);
        }
    }
}

/**
 * Computes the mean and variance values for each grid cell in the active array
 * crop region.
 *
 *
 * Parameters:
 *   env: JNIEnv*
 *     The JNI environment.
 *   thiz: jobject
 *     A reference to the object that called this method.
 *   img: jbyteArray
 *     The full pixel array read from the sensor.
 *   statsFormatJStr: jstring
 *     A jstring value that specifies the format of stats images.
 *     See it's valid values in `enum StatsFormat`.
 *   width: jint
 *     The width of the raw image (before transformed to pixel array).
 *   height: jint
 *     The height of the raw image (before transformed to pixel array).
 *   aax: jint
 *     The x coordinate of the top-left corner of the active array crop region.
 *   aay: jint
 *     The y coordinate of the top-left corner of the active array crop region.
 *   aaw: jint
 *     The width of the active array crop region.
 *   aah: jint
 *     The height of the active array crop region.
 *   gridWidth: jint
 *     The width of stats grid cell.
 *   gridHeight: jint
 *     The height of stats grid cell.
 *
 * Returns:
 *   jfloatArray
 *     A new array of floats containing the mean and variance stats image.
 */
jfloatArray com_android_cts_verifier_camera_its_computeStatsImage(
    JNIEnv* env,
    jobject thiz,
    jbyteArray img,
    jstring statsFormatJStr,
    jint width,
    jint height,
    jint aax,
    jint aay,
    jint aaw,
    jint aah,
    jint gridWidth,
    jint gridHeight) {
    int bufSize = (int) (env->GetArrayLength(img));
    unsigned char* buf =
        (unsigned char*) env->GetByteArrayElements(img, /*is_copy*/ NULL);
    const char* statsFormatChars =
        (const char*) env->GetStringUTFChars(statsFormatJStr, /*is_copy*/ NULL);

    // Size of the full raw image pixel array.
    const int paw = width;
    const int pah = height;
    // Size of each grid cell.
    const int gw = gridWidth;
    const int gh = gridHeight;

    // Set the width or height of active crop region to the width or height of
    // the raw image if the size of active crop region is larger than the size
    // of actual image.
    aaw = std::min(paw, aaw);
    aah = std::min(pah, aah);

    // Set the top-left coordinate of active crop region to (0, 0) if the
    // width or height of active crop region is equal to the width or height of
    // the raw image.
    if (paw == aaw) {
        aax = 0;
    }
    if (pah == aah) {
        aay = 0;
    }

    // Number of grid cells (rounding down to full cells only at right+bottom
    // edges).
    const int ngx = aaw / gw;
    const int ngy = aah / gh;

    const bool isQuadBayer = strstr(statsFormatChars, "QuadBayer") != NULL;
    // Quad bayer and standard bayer stats images have 16 and 4 channels
    // respectively.
    const int numOfChannels = isQuadBayer ? 16 : 4;
    ALOGI("Computing stats image... "
          "bufSize=%d, raw image shape (width, height) = (%d, %d), "
          "crop region (aax, aay, aaw, aah) = (%d, %d, %d, %d), "
          "grid shape (gw, gh) = (%d, %d), "
          "stats image shape (ngx, ngy) = (%d, %d), stats image format: %s, "
          "numOfChannels=%d.",
          bufSize, paw, pah, aax, aay, aaw, aah, gw, gh, ngx, ngy,
          statsFormatChars, numOfChannels);

    // A stats format of enum type can speed up stats image computations.
    auto iterator = statsFormatMap.find(std::string(statsFormatChars));
    jfloatArray ret = NULL;
    if (iterator != statsFormatMap.end()) {
        StatsFormat statsFormat = iterator->second;
        const int statsImageSize = ngy * ngx * numOfChannels;
        std::vector<float> means(statsImageSize, 0);
        std::vector<float> vars(statsImageSize, 0);

        // Computes stats for each grid cell.
        for (int gy = 0; gy < ngy; gy++) {
            for (int gx = 0; gx < ngx; gx++) {
                computeSingleCellStats(buf, statsFormat, isQuadBayer, paw, ngx,
                                       aax, aay, gw, gh, gy, gx, numOfChannels,
                                       means, vars);
            }
        }

        ret = env->NewFloatArray(statsImageSize * 2);
        env->SetFloatArrayRegion(ret, 0, statsImageSize, (float*) means.data());
        env->SetFloatArrayRegion(ret, statsImageSize, statsImageSize,
                                 (float*) vars.data());
    } else {
        ALOGE("Unsupported stats image format: %s.", statsFormatChars);
    }

    // Release the array memory.
    env->ReleaseByteArrayElements(img, (jbyte*) buf, 0 /* mode */);
    env->ReleaseStringUTFChars(statsFormatJStr, statsFormatChars);

    return ret;
}

static JNINativeMethod gMethods[] = {
    {"computeStatsImage", "([BLjava/lang/String;IIIIIIII)[F",
     (void*) com_android_cts_verifier_camera_its_computeStatsImage},
};

int register_com_android_cts_verifier_camera_its_StatsImage(JNIEnv* env) {
    jclass clazz =
        env->FindClass("com/android/cts/verifier/camera/its/StatsImage");

    return env->RegisterNatives(clazz, gMethods,
                                sizeof(gMethods) / sizeof(JNINativeMethod));
}
