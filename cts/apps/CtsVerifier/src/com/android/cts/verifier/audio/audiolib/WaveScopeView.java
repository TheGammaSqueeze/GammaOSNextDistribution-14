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

package com.android.cts.verifier.audio.audiolib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class WaveScopeView extends View {
    @SuppressWarnings("unused")
    private static final String TAG = "WaveScopeView";

    private final Paint mPaint = new Paint();

    private int mBackgroundColor = Color.WHITE;
    private int mTraceColor = Color.BLACK;
    private int mLimitsColor = Color.YELLOW;
    private int mZeroColor = Color.RED;
    private int mTextColor = Color.CYAN;

    private float mDisplayFontSize = 32f;

    private float[] mPCMFloatBuffer;

    private int mNumChannels = 2;
    private int mNumFrames = 0;

    private boolean mDisplayBufferSize = true;
    private boolean mDisplayMaxMagnitudes = false;
    private boolean mDisplayPersistentMaxMagnitude = false;
    private float mPersistentMaxMagnitude;
    private boolean mDisplayLimits = false;
    private boolean mDisplayZero = false;
    private float mVerticalInset = 10.0f;

    private float[] mPointsBuffer;

    // Horrible kludge
    private static int mCachedWidth = 0;

    public WaveScopeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setBackgroundColor(int color) { mBackgroundColor = color; }

    public void setTraceColor(int color) {
        mTraceColor = color;
    }

    public void setLimitsColor(int color) {
        mLimitsColor = color;
    }

    public void setZeroColor(int color) {
        mZeroColor = color;
    }

    public boolean getDisplayBufferSize() {
        return mDisplayBufferSize;
    }

    public void setDisplayBufferSize(boolean display) {
        mDisplayBufferSize = display;
    }

    public void setDisplayMaxMagnitudes(boolean display) {
        mDisplayMaxMagnitudes = display;
    }

    public void setDisplayPersistentMaxMagnitude(boolean display) {
        mDisplayPersistentMaxMagnitude = display;
    }

    public void setDisplayLimits(boolean display) {
        mDisplayLimits = display;
    }

    public void setDisplayZero(boolean display) {
        mDisplayZero = display;
    }

    /**
     * Clears persistent max magnitude so a new value can be calculated.
     */
    public void resetPersistentMaxMagnitude() {
        mPersistentMaxMagnitude = 0.0f;
    }

    public void setPCMFloatBuff(float[] smplFloatBuff, int numChans, int numFrames) {
        mPCMFloatBuffer = smplFloatBuff;

        mNumChannels = numChans;
        mNumFrames = numFrames;

        setupPointBuffer();

        invalidate();
    }

    /**
     * Specifies the number of channels contained in the data buffer to display
     * @param numChannels
     */
    public void setNumChannels(int numChannels) {
        mNumChannels = numChannels;
        setupPointBuffer();
    }

    private void setupPointBuffer() {
        int width = getWidth();

        // Horrible kludge
        if (width == 0) {
            width = mCachedWidth;
        } else {
            mCachedWidth = width;
        }

        // Canvas.drawLines() uses 2 points (float pairs) per line-segment
        // Only reallocate if we need more space.
        if (mPointsBuffer == null || (mNumFrames * 4) > mPointsBuffer.length) {
            mPointsBuffer = new float[mNumFrames * 4];
        }
        float xIncr = (float) width / (float) mNumFrames;

        float X = 0;
        int len = mPointsBuffer.length;
        for (int pntIndex = 0; pntIndex < len;) {
            mPointsBuffer[pntIndex] = X;
            pntIndex += 2; // skip Y

            X += xIncr;

            mPointsBuffer[pntIndex] = X;
            pntIndex += 2; // skip Y
        }
    }

    /**
     * Draws 1 channel of an interleaved block of FLOAT samples.
     * @param cvs The Canvas to draw into.
     * @param samples The (potentially) multi-channel sample block.
     * @param numFrames The number of FRAMES in the specified sample block.
     * @param numChans The number of interleaved channels in the specified sample block.
     * @param chanIndex The (0-based) index of the channel to draw.
     * @param zeroY The Y-coordinate of sample value 0 (zero).
     */
    private static final int FLOATS_PER_POINT = 2;
    private void drawChannelFloat(Canvas cvs, float[] samples, int numFrames, int numChans,
            int chanIndex, float zeroY) {

        float height = getHeight() - mVerticalInset * 2.0f * (float) numChans;
        zeroY +=  mVerticalInset;

        if (mDisplayZero) {
            mPaint.setColor(mZeroColor);
            float endX = mPointsBuffer[mPointsBuffer.length - FLOATS_PER_POINT];
            cvs.drawLine(0, zeroY, endX, zeroY, mPaint);
        }

        // 2 here is the range between MIN_FLOAT_SAMPLE (-1.0) and MAX_FLOAT_SAMPLE (1.0)
        float yScale = height / (float) (2 * numChans);
        int pntIndex = 1; // of the first Y coordinate
        int smplIndex = chanIndex;
        float yCoord = zeroY - (samples[smplIndex] * yScale);
        // use a local reference to the points in case a realloc rolls around.
        float[] localPointsBuffer = mPointsBuffer;
        if (mDisplayMaxMagnitudes) {
            float maxMagnitude = 0f;
            // ensure we don't step past the end of the points buffer
            for (int frame = 0; frame < numFrames; frame++) {
                localPointsBuffer[pntIndex] = yCoord;
                pntIndex += FLOATS_PER_POINT;

                float smpl = samples[smplIndex];
                if (smpl > maxMagnitude) {
                    maxMagnitude = smpl;
                } else if (-smpl > maxMagnitude) {
                    maxMagnitude = -smpl;
                }

                yCoord = zeroY - (smpl * yScale);

                localPointsBuffer[pntIndex] = yCoord;
                pntIndex += FLOATS_PER_POINT;

                smplIndex += numChans;
            }
            mPaint.setColor(mTextColor);
            mPaint.setTextSize(mDisplayFontSize);
            cvs.drawText(String.format("%.2f", maxMagnitude), 0, zeroY, mPaint);

            mPaint.setColor(mTraceColor);
            cvs.drawLines(localPointsBuffer, mPaint);
        } else {
            for (int frame = 0; frame < numFrames; frame++) {
                localPointsBuffer[pntIndex] = yCoord;
                pntIndex += FLOATS_PER_POINT;

                yCoord = zeroY - (samples[smplIndex] * yScale);

                localPointsBuffer[pntIndex] = yCoord;
                pntIndex += FLOATS_PER_POINT;

                smplIndex += numChans;
            }
            mPaint.setColor(mTraceColor);
            cvs.drawLines(localPointsBuffer, mPaint);
        }

        if (mDisplayPersistentMaxMagnitude) {
            smplIndex = chanIndex;
            for (int frame = 0; frame < numFrames; frame++) {
                if (samples[smplIndex] > mPersistentMaxMagnitude) {
                    mPersistentMaxMagnitude = samples[smplIndex];
                } else if (-samples[smplIndex] > mPersistentMaxMagnitude) {
                    mPersistentMaxMagnitude = -samples[smplIndex];
                }

                yCoord = mDisplayFontSize + (chanIndex * (getHeight() / mNumChannels));
                mPaint.setColor(mTextColor);
                mPaint.setTextSize(mDisplayFontSize);
                cvs.drawText(String.format("%.2f", mPersistentMaxMagnitude), 0, yCoord, mPaint);
            }
        }

        if (mDisplayLimits) {
            mPaint.setColor(mLimitsColor);
            float endX = mPointsBuffer[mPointsBuffer.length - FLOATS_PER_POINT];
            yCoord = zeroY + yScale;
            cvs.drawLine(0, yCoord, endX, yCoord, mPaint);
            yCoord = zeroY + -yScale;
            cvs.drawLine(0, yCoord, endX, yCoord, mPaint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int height = getHeight();
        mPaint.setColor(mBackgroundColor);
        canvas.drawRect(0, 0, getWidth(), height, mPaint);

        if (mDisplayBufferSize) {
            // Buffer Size
            mPaint.setColor(mTextColor);
            mPaint.setTextSize(mDisplayFontSize);
            canvas.drawText("" + mNumFrames + " frames", 0, height, mPaint);
        }

        if (mPCMFloatBuffer != null) {
            float yOffset = height / (2.0f * mNumChannels);
            float yDelta = height / (float) mNumChannels;
            for(int channel = 0; channel < mNumChannels; channel++) {
                drawChannelFloat(canvas, mPCMFloatBuffer, mNumFrames, mNumChannels, channel, yOffset);
                yOffset += yDelta;
            }
        }
    }
}
