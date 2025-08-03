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

import static com.google.common.truth.Truth.assertThat;

import com.android.csuite.core.BlankScreenDetectorWithSameColorRectangle.BlankScreen;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

@RunWith(JUnit4.class)
public class BlankScreenDetectorWithSameColorRectangleTest {

    @Test
    public void maxSubRectangle_returnsBiggestSubRectangle() {
        int[] row = {1, 0, 2};
        int index = 2;

        Rectangle subRectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSubRectangle(row, index);

        assertThat(subRectangle.getX()).isEqualTo(2);
        assertThat(subRectangle.getY()).isEqualTo(1);
    }

    @Test
    public void maxSubRectangle_withWideThinRectangle_returnsBiggestSubRectangle() {
        int[] row = {1, 3, 4, 3, 1, 2, 3, 5, 2};
        int index = 4;

        Rectangle subRectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSubRectangle(row, index);

        assertThat(subRectangle.getWidth() * subRectangle.getHeight()).isEqualTo(9);
        assertThat(subRectangle.getX()).isEqualTo(1);
        assertThat(subRectangle.getY()).isEqualTo(2);
    }

    @Test
    public void maxSubRectangle_withOffsetIndex_returnsBiggestSubRectangle() {
        int[] row = {3, 2, 5, 0};
        int index = 5;

        Rectangle subRectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSubRectangle(row, index);

        assertThat(subRectangle.getWidth() * subRectangle.getHeight()).isEqualTo(6);
        assertThat(subRectangle.getX()).isEqualTo(0);
        assertThat(subRectangle.getY()).isEqualTo(4);
    }

    @Test
    public void maxSameColorRectangle_withFullSameColorRectangle_returnsSameSize() {
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        Rectangle rectangle = new Rectangle(0, 0, 100, 50);
        drawRectangles(image, rectangle);

        Rectangle maxRectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSameColorRectangle(image);

        assertThat(maxRectangle.getWidth() * rectangle.getHeight()).isEqualTo(100 * 50);
        assertThat(maxRectangle.getX()).isEqualTo(0);
        assertThat(maxRectangle.getY()).isEqualTo(0);
    }

    @Test
    public void maxSameColorRectangle_withBottomScreenBand_returnsLargestRectangle() {
        int imageWidth = 100;
        int imageHeight = 200;
        int barWidth = 100;
        int barHeight = 20;
        BufferedImage image =
                new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        drawRectangles(image, new Rectangle(0, imageHeight - barHeight, barWidth, barHeight));

        Rectangle rectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSameColorRectangle(image);

        assertThat(rectangle.getWidth() * rectangle.getHeight())
                .isEqualTo((imageHeight - barHeight) * imageWidth);
        assertThat(rectangle.getX()).isEqualTo(0);
        assertThat(rectangle.getY()).isEqualTo(0);
    }

    @Test
    public void maxSameColorRectangle_withTopScreenBand_returnsLargestRectangle() {
        int imageWidth = 100;
        int imageHeight = 200;
        int barWidth = 100;
        int barHeight = 20;
        BufferedImage image =
                new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        drawRectangles(image, new Rectangle(0, 0, barWidth, barHeight));

        Rectangle rectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSameColorRectangle(image);

        assertThat(rectangle.getWidth() * rectangle.getHeight())
                .isEqualTo((imageHeight - barHeight) * imageWidth);
        assertThat(rectangle.getX()).isEqualTo(0);
        assertThat(rectangle.getY()).isEqualTo(barHeight);
    }

    @Test
    public void maxSameColorRectangle_withLowerLeftRectangle_returnsLargestRectangle() {
        int imageWidth = 100;
        int imageHeight = 60;
        Rectangle smallRectangle = new Rectangle(30, 30, 10, 20);
        BufferedImage image =
                new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        drawRectangles(image, smallRectangle);

        Rectangle rectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSameColorRectangle(image);

        assertThat(rectangle.getWidth() * rectangle.getHeight())
                .isEqualTo((imageWidth - smallRectangle.x - smallRectangle.width) * imageHeight);
        assertThat(rectangle.x).isEqualTo(smallRectangle.x + smallRectangle.width);
        assertThat(rectangle.y).isEqualTo(0);
    }

    @Test
    public void maxSameColorRectangle_withFourRectangles_returnsLargestRectangle() {
        int imageWidth = 100;
        int imageHeight = 60;
        Rectangle r1 = new Rectangle(30, 20, 10, 10);
        Rectangle r2 = new Rectangle(20, 40, 10, 10);
        Rectangle r3 = new Rectangle(70, 10, 10, 10);
        Rectangle r4 = new Rectangle(90, 40, 10, 10);
        BufferedImage image =
                new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        drawRectangles(image, r1, r2, r3, r4);

        Rectangle rectangle =
                BlankScreenDetectorWithSameColorRectangle.maxSameColorRectangle(image);

        assertThat(rectangle.getWidth() * rectangle.getHeight()).isEqualTo(50 * 40);
        assertThat(rectangle.x).isEqualTo(40);
        assertThat(rectangle.y).isEqualTo(20);
    }

    @Test
    public void getBlankScreen_withFullScreenAndBlank_returnsHighRatio() {
        Path imagePath = Path.of("BlankScreenWithFullScreen.png");

        BlankScreen blankScreen =
                BlankScreenDetectorWithSameColorRectangle.getBlankScreen(readImage(imagePath));

        assertThat(blankScreen.getBlankScreenPercent()).isGreaterThan(0.7);
    }

    @Test
    public void getBlankScreen_withNavBarsAndBlank_returnsHighRatio() {
        Path imagePath = Path.of("BlankScreenWithNavBars.png");

        BlankScreen blankScreen =
                BlankScreenDetectorWithSameColorRectangle.getBlankScreen(readImage(imagePath));

        assertThat(blankScreen.getBlankScreenPercent()).isGreaterThan(0.7);
    }

    @Test
    public void getBlankScreen_withWhiteScreenAndBlank_returnsHighRatio() {
        Path imagePath = Path.of("BlankScreenWithWhiteScreen.png");

        BlankScreen blankScreen =
                BlankScreenDetectorWithSameColorRectangle.getBlankScreen(readImage(imagePath));

        assertThat(blankScreen.getBlankScreenPercent()).isGreaterThan(0.7);
    }

    @Test
    public void getBlankScreen_hasNormalScreen_returnsLowRatio() {
        Path imagePath = Path.of("NotBlankScreen.png");

        BlankScreen blankScreen =
                BlankScreenDetectorWithSameColorRectangle.getBlankScreen(readImage(imagePath));

        assertThat(blankScreen.getBlankScreenPercent()).isLessThan(0.7);
    }

    private void drawRectangles(BufferedImage image, Rectangle... rectangles) {
        Graphics2D graphic = image.createGraphics();
        graphic.setColor(Color.BLUE);
        for (Rectangle rectangle : rectangles) {
            graphic.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        }
        graphic.dispose();
    }

    private BufferedImage readImage(Path path) {
        BufferedImage image = null;
        try {
            image = ImageIO.read(path.toFile());
        } catch (IOException e) {
            CLog.e("Failed to read the image at path: " + path.toString());
            Assert.fail();
        }
        return image;
    }
}
