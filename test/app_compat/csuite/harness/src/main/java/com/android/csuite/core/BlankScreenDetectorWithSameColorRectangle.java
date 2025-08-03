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

package com.android.csuite.core;

import com.android.csuite.core.TestUtils.TestArtifactReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;

import com.google.common.annotations.VisibleForTesting;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;

import javax.imageio.ImageIO;

/**
 * A class that helps detect the presence of a blank screen in an app using the approach of first
 * finding the largest same-color rectangle area, and then comparing it to the total area of the
 * original image.
 */
public class BlankScreenDetectorWithSameColorRectangle {
    /**
     * @param image against which to calculate the blank screen area.
     * @return a BlankScreen object which represents the largest same-color rectangle within the
     *     given image.
     */
    public static BlankScreen getBlankScreen(BufferedImage image) {
        return new BlankScreen(maxSameColorRectangle(image), image);
    }

    /**
     * Given an RGB image, finds the biggest same-color rectangle.
     *
     * @param image within which to look for the largest same-color rectangle.
     * @return the Rectangle object representing the largest same-color rectangle.
     */
    @VisibleForTesting
    static Rectangle maxSameColorRectangle(BufferedImage image) {
        int[][] imageMatrix = getPixels(image);
        int[][] similarityMatrix = new int[imageMatrix.length][imageMatrix[0].length];
        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[0].length; j++) {
                similarityMatrix[i][j] = 0;
            }
        }
        Rectangle maxRectangle = new Rectangle();

        for (int i = 0; i < similarityMatrix.length; i++) {
            for (int j = 0; j < similarityMatrix[0].length; j++) {
                if (i == 0) {
                    similarityMatrix[i][j] = 1;
                } else if (imageMatrix[i][j] == imageMatrix[i - 1][j]) {
                    similarityMatrix[i][j] = similarityMatrix[i - 1][j] + 1;
                } else {
                    similarityMatrix[i][j] = 1;
                }
            }
            Rectangle currentBiggestRectangle = maxSubRectangle(similarityMatrix[i], i);
            if (getRectangleArea(currentBiggestRectangle) > getRectangleArea(maxRectangle)) {
                maxRectangle = currentBiggestRectangle;
            }
        }
        return maxRectangle;
    }

    /**
     * Finds the SubRectangle with the largest possible area given a row of column heights and its
     * index in a larger matrix.
     *
     * @param heightsRow an array representing the height of each column of same-colorex pixels.
     * @param index the index of the given array in the larger two-dimensional matrix.
     * @return the Rectangle object representing the largest same-color rectangle.
     */
    @VisibleForTesting
    static Rectangle maxSubRectangle(int[] heightsRow, int index) {
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        Rectangle maxRectangle = new Rectangle();

        for (int i = 0; i < heightsRow.length; i++) {
            while (!stack.isEmpty() && heightsRow[stack.peek()] > heightsRow[i]) {
                int height = heightsRow[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                int area = height * width;
                if (area > getRectangleArea(maxRectangle)) {
                    int leftCornerXCoord = stack.isEmpty() ? 0 : stack.peek() + 1;
                    int leftCornerYCoord = index - height + 1;
                    maxRectangle.setRect(leftCornerXCoord, leftCornerYCoord, width, height);
                }
            }
            stack.push(i);
        }

        while (!stack.isEmpty()) {
            int height = heightsRow[stack.pop()];
            int width =
                    stack.isEmpty() ? heightsRow.length : (heightsRow.length - stack.peek() - 1);
            int area = height * width;
            if (area > getRectangleArea(maxRectangle)) {
                int leftCornerXCoord = stack.isEmpty() ? 0 : stack.peek() + 1;
                int leftCornerYCoord = index - height + 1;
                maxRectangle.setRect(leftCornerXCoord, leftCornerYCoord, width, height);
            }
        }
        return maxRectangle;
    }

    /**
     * Converts a BufferedImage to a two-dimensional array containing the int representation of its
     * pixels.
     *
     * @param image which to convert.
     * @return a two-dimensional array containing the int representation of the image's pixels.
     */
    private static int[][] getPixels(BufferedImage image) {
        int[][] pixels = new int[image.getHeight()][image.getWidth()];
        for (int i = 0; i < pixels.length; i++) {
            for (int j = 0; j < pixels[0].length; j++) {
                pixels[i][j] = image.getRGB(j, i);
            }
        }
        return pixels;
    }

    private static long getRectangleArea(Rectangle rectangle) {
        return (long) rectangle.width * (long) rectangle.height;
    }

    /**
     * Saves the image containing the screenshot and drawing of the blank screen rectangle to the
     * test artifacts.
     *
     * @param prefix the file name prefix.
     * @param blankScreen the representation of the blank screen to write to artifacts.
     * @param testArtifactReceiver the interface which allows to save test artifacts.
     * @param deviceSerial the serial number of the device which will be used to name the artifacts.
     */
    public static void saveBlankScreenArtifact(
            String prefix,
            BlankScreen blankScreen,
            TestArtifactReceiver testArtifactReceiver,
            String deviceSerial) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(blankScreen.getScreenImage(), "png", baos);
        } catch (IOException e) {
            CLog.e(
                    "Failed to write the screenshot to byte array when saving blank screen"
                            + " artifact.");
        }
        testArtifactReceiver.addTestArtifact(
                prefix + "_screenshot_blankscreen_" + deviceSerial,
                LogDataType.PNG,
                baos.toByteArray());
    }

    /**
     * A representation of a same-color rectangle within a larger image which may represent a blank
     * screen in a mobile app.
     */
    public static final class BlankScreen {
        // The screen capture within which the blank screen rectangle is computed.
        private BufferedImage mScreenImage;
        // The same-color rectangle arearepresenting the blank screen.
        private Rectangle mBlankScreenRectangle;

        public BufferedImage getScreenImage() {
            return mScreenImage;
        }

        public Rectangle getBlankScreenRectangle() {
            return mBlankScreenRectangle;
        }

        public BlankScreen(Rectangle r, BufferedImage image) {
            this.mScreenImage = image;
            this.mBlankScreenRectangle = r;
            drawBlankScreenRectangle();
        }

        // Draws the outline of the rectangle which is interpreted as a blank screen within the
        // context of the larger image.
        private void drawBlankScreenRectangle() {
            Graphics g = mScreenImage.getGraphics();
            g.setColor(Color.MAGENTA);
            g.drawRect(
                    mBlankScreenRectangle.x,
                    mBlankScreenRectangle.y,
                    mBlankScreenRectangle.width,
                    mBlankScreenRectangle.height);
            g.dispose();
        }

        // Returns the percentage of the larger image occupied by the blank screen rectangle.
        public double getBlankScreenPercent() {
            return ((double) mBlankScreenRectangle.width * mBlankScreenRectangle.height)
                    / ((double) mScreenImage.getHeight() * mScreenImage.getWidth());
        }
    }
}
