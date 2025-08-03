/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.uirendering.cts.testclasses;


import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Region;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ExpandingClipTest extends ActivityTestBase {

    @Test
    public void testClipReplace() {
        // The replace op should function correctly on apps compatible with version 27
        Point[] testPoints = {
            new Point(0, 5),
            new Point(10, 5)
        };
        int[] colors = {
            Color.WHITE,
            Color.BLUE
        };
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    canvas.drawRect(0, 0, width, height, paint);

                    // These are exclusive, but if the replace op properly
                    // removes the earlier intersect, only the right portion of
                    // the canvas will be filled with blue.
                    canvas.clipRect(0, 0, 5, height);
                    canvas.clipRect(5, 0, width, height, Region.Op.REPLACE);

                    paint.setColor(Color.BLUE);
                    canvas.drawRect(0, 0, width, height, paint);
                })
                .runWithVerifier(new SamplePointVerifier(testPoints, colors));
    }

    @Test
    public void testClipUnsupportedExpandingOps() {
        // Test that other expanding clip ops are silently ignored for v27
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    // Should not modify clip state so later draw fills bitmap
                    canvas.clipRect(0, 0, 5, 5, Region.Op.REVERSE_DIFFERENCE);
                    canvas.clipRect(5, 5, 10, 10, Region.Op.XOR);
                    canvas.clipRect(10, 10, 15, 15, Region.Op.UNION);

                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    canvas.drawRect(0, 0, width, height, paint);
                })
                .runWithVerifier(new ColorVerifier(Color.RED));
    }
}