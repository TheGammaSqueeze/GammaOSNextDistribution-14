/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.layoutlib.bridge.intensive.util;

import android.annotation.NonNull;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.io.File.separatorChar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


// Adapted by taking the relevant pieces of code from the following classes:
//
// com.android.tools.idea.rendering.ImageUtils,
// com.android.tools.idea.tests.gui.framework.fixture.layout.ImageFixture and
// com.android.tools.idea.rendering.RenderTestBase
/**
 * Utilities related to image processing.
 */
public class ImageUtils {
    /**
     * Normally, this test will fail when there is a missing golden image. However, when
     * you create creating a new test, it's useful to be able to turn this off such that
     * you can generate all the missing golden images in one go, rather than having to run
     * the test repeatedly to get to each new render assertion generating its golden images.
     */
    private static final boolean FAIL_ON_MISSING_GOLDEN = true;

    private static final double MAX_PERCENT_DIFFERENCE = 0.1;

    public static void requireSimilar(@NonNull String relativePath, @NonNull BufferedImage image)
            throws IOException {
        InputStream is = ImageUtils.class.getClassLoader().getResourceAsStream(relativePath);
        if (is == null) {
            String message = "Unable to load golden image: " + relativePath + "\n";
            message = saveImageAndAppendMessage(image, message, relativePath);
            if (FAIL_ON_MISSING_GOLDEN) {
                fail(message);
            } else {
                System.out.println(message);
            }
        }
        else {
            try {
                BufferedImage goldenImage = ImageIO.read(is);
                assertImageSimilar(relativePath, goldenImage, image, MAX_PERCENT_DIFFERENCE);
            } finally {
                is.close();
            }
        }
    }

    public static void assertImageSimilar(String relativePath, BufferedImage goldenImage,
            BufferedImage image, double maxPercentDifferent) throws IOException {
        if (goldenImage.getType() != TYPE_INT_ARGB) {
            BufferedImage temp = new BufferedImage(goldenImage.getWidth(), goldenImage.getHeight(),
                    TYPE_INT_ARGB);
            temp.getGraphics().drawImage(goldenImage, 0, 0, null);
            goldenImage = temp;
        }
        assertEquals(TYPE_INT_ARGB, goldenImage.getType());

        int imageWidth = Math.min(goldenImage.getWidth(), image.getWidth());
        int imageHeight = Math.min(goldenImage.getHeight(), image.getHeight());

        // Blur the images to account for the scenarios where there are pixel
        // differences
        // in where a sharp edge occurs
        // goldenImage = blur(goldenImage, 6);
        // image = blur(image, 6);

        int width = 3 * imageWidth;
        @SuppressWarnings("UnnecessaryLocalVariable")
        int height = imageHeight; // makes code more readable
        BufferedImage deltaImage = new BufferedImage(width, height, TYPE_INT_ARGB);
        Graphics g = deltaImage.getGraphics();

        // Compute delta map
        long delta = 0;
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int goldenRgb = goldenImage.getRGB(x, y);
                int rgb = image.getRGB(x, y);
                if (goldenRgb == rgb) {
                    deltaImage.setRGB(imageWidth + x, y, 0x00808080);
                    continue;
                }

                // If the pixels have no opacity, don't delta colors at all
                if (((goldenRgb & 0xFF000000) == 0) && (rgb & 0xFF000000) == 0) {
                    deltaImage.setRGB(imageWidth + x, y, 0x00808080);
                    continue;
                }

                int deltaR = ((rgb & 0xFF0000) >>> 16) - ((goldenRgb & 0xFF0000) >>> 16);
                int newR = 128 + deltaR & 0xFF;
                int deltaG = ((rgb & 0x00FF00) >>> 8) - ((goldenRgb & 0x00FF00) >>> 8);
                int newG = 128 + deltaG & 0xFF;
                int deltaB = (rgb & 0x0000FF) - (goldenRgb & 0x0000FF);
                int newB = 128 + deltaB & 0xFF;

                int avgAlpha = ((((goldenRgb & 0xFF000000) >>> 24)
                        + ((rgb & 0xFF000000) >>> 24)) / 2) << 24;

                int newRGB = avgAlpha | newR << 16 | newG << 8 | newB;
                deltaImage.setRGB(imageWidth + x, y, newRGB);

                delta += Math.abs(deltaR);
                delta += Math.abs(deltaG);
                delta += Math.abs(deltaB);
            }
        }

        // 3 different colors, 256 color levels
        long total = imageHeight * imageWidth * 3L * 256L;
        float percentDifference = (float) (delta * 100 / (double) total);

        String error = null;
        String imageName = getName(relativePath);
        if (percentDifference > maxPercentDifferent) {
            error = String.format("Images differ (by %.1f%%)", percentDifference);
        } else if (Math.abs(goldenImage.getWidth() - image.getWidth()) >= 2) {
            error = "Widths differ too much for " + imageName + ": " +
                    goldenImage.getWidth() + "x" + goldenImage.getHeight() +
                    "vs" + image.getWidth() + "x" + image.getHeight();
        } else if (Math.abs(goldenImage.getHeight() - image.getHeight()) >= 2) {
            error = "Heights differ too much for " + imageName + ": " +
                    goldenImage.getWidth() + "x" + goldenImage.getHeight() +
                    "vs" + image.getWidth() + "x" + image.getHeight();
        }

        if (error != null) {
            // Expected on the left
            // Golden on the right
            g.drawImage(goldenImage, 0, 0, null);
            g.drawImage(image, 2 * imageWidth, 0, null);

            // Labels
            if (imageWidth > 80) {
                g.setColor(Color.RED);
                g.drawString("Expected", 10, 20);
                g.drawString("Actual", 2 * imageWidth + 10, 20);
            }

            File output = new File(getFailureDir(), "delta-" + imageName);
            if (output.exists()) {
                boolean deleted = output.delete();
                assertTrue(deleted);
            }
            ImageIO.write(deltaImage, "PNG", output);
            error += " - see details in file://" + output.getPath() + "\n";
            error = saveImageAndAppendMessage(image, error, relativePath);
            System.out.println(error);
            fail(error);
        }

        g.dispose();
    }

    /**
     * Directory where to write the generated image and deltas.
     */
    @NonNull
    private static File getFailureDir() {
        File failureDir;
        String failureDirString = System.getProperty("test_failure.dir");
        if (failureDirString != null) {
            failureDir = new File(failureDirString);
        } else {
            String workingDirString = System.getProperty("user.dir");
            failureDir = new File(workingDirString, "out/failures");
        }

        //noinspection ResultOfMethodCallIgnored
        failureDir.mkdirs();
        return failureDir; //$NON-NLS-1$
    }

    /**
     * Saves the generated golden image and appends the info message to an initial message
     */
    @NonNull
    private static String saveImageAndAppendMessage(@NonNull BufferedImage image,
            @NonNull String initialMessage, @NonNull String relativePath) throws IOException {
        File output = new File(getFailureDir(), getName(relativePath));
        if (output.exists()) {
            boolean deleted = output.delete();
            assertTrue(deleted);
        }
        ImageIO.write(image, "PNG", output);
        initialMessage += "Golden image for current rendering stored at " + output.getPath();
//        initialMessage += "\nRun the following command to accept the changes:\n";
//        initialMessage += String.format("mv %1$s %2$s", output.getPath(),
//                ImageUtils.class.getResource(relativePath).getPath());
        // The above has been commented out, since the destination path returned is in out dir
        // and it makes the tests pass without the code being actually checked in.
        return initialMessage;
    }

    private static String getName(@NonNull String relativePath) {
        return relativePath.substring(relativePath.lastIndexOf(separatorChar) + 1);
    }
}
