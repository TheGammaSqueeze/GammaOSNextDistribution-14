/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.util.Size;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;


final class ImageReaderArgs {
    private final Size[] mOutputSizes;
    private final int[] mOutputFormats;
    private final Size mInputSize;
    private final int mInputFormat;
    private final int mMaxInputBuffers;
    private final boolean mHas10bitOutput;

    private ImageReaderArgs(Size[] outputSizes, int[] outputFormats,
                            Size inputSize, int inputFormat,
                            int maxInputBuffers, boolean has10bitOutput) {
        mOutputSizes = outputSizes;
        mOutputFormats = outputFormats;
        mInputSize = inputSize;
        mInputFormat = inputFormat;
        mMaxInputBuffers = maxInputBuffers;
        mHas10bitOutput = has10bitOutput;
    }

    public static final ImageReaderArgs EMPTY = new ImageReaderArgs(
            null, null, null, -1, -1, false);

    public static ImageReaderArgs valueOf(Size[] outputSizes, int[] outputFormats,
                                   Size inputSize, int inputFormat,
                                   int maxInputBuffers, boolean has10bitOutput) {
        return new ImageReaderArgs(outputSizes.clone(), outputFormats.clone(),
                                   inputSize, inputFormat, maxInputBuffers, has10bitOutput);
    }

    public Size[] getOutputSizes() {
        return mOutputSizes;
    }

    public int[] getOutputFormats() {
        return mOutputFormats;
    }

    public Size getInputSize() {
        return mInputSize;
    }

    public int getInputFormat() {
        return mInputFormat;
    }

    public int getMaxInputBuffers() {
        return mMaxInputBuffers;
    }

    public boolean getHas10bitOutput() {
        return mHas10bitOutput;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (this == obj) {
            return true;
        } else if (obj instanceof ImageReaderArgs) {
            ImageReaderArgs other = (ImageReaderArgs) obj;
            return (Arrays.equals(mOutputSizes, other.mOutputSizes) &&
                    Arrays.equals(mOutputFormats, other.mOutputFormats) &&
                    Objects.equals(mInputSize, other.mInputSize) &&
                    mInputFormat == other.mInputFormat &&
                    mMaxInputBuffers == other.mMaxInputBuffers &&
                    mHas10bitOutput == other.mHas10bitOutput);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mOutputSizes), Arrays.hashCode(mOutputFormats),
                            mInputSize, mInputFormat, mMaxInputBuffers, mHas10bitOutput);
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();
        output.append(String.format(Locale.getDefault(),
                "outputSizes: %s, ", Arrays.toString(mOutputSizes)));
        output.append(String.format(Locale.getDefault(),
                "outputFormats: %s, ", Arrays.toString(mOutputFormats)));
        output.append(String.format(Locale.getDefault(),
                "inputSize: %s, ", Objects.toString(mInputSize)));
        output.append(String.format(Locale.getDefault(),
                "inputFormat: %d, ", mInputFormat));
        output.append(String.format(Locale.getDefault(),
                "maxInputBuffers: %d, ", mMaxInputBuffers));
        output.append(String.format(Locale.getDefault(),
                "has10bitOutput: %s", mHas10bitOutput));
        return output.toString();
    }
}