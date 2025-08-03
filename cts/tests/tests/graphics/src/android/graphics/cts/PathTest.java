/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PathIterator;
import android.graphics.RectF;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PathTest {

    // Test constants
    private static final float LEFT = 10.0f;
    private static final float RIGHT = 50.0f;
    private static final float TOP = 10.0f;
    private static final float BOTTOM = 50.0f;
    private static final float XCOORD = 40.0f;
    private static final float YCOORD = 40.0f;
    private static final int SQUARE = 10;
    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;
    private static final int START_X = 10;
    private static final int START_Y = 20;
    private static final int OFFSET_X = 30;
    private static final int OFFSET_Y = 40;

    @Test
    public void testConstructor() {
        // new the Path instance
        new Path();

        // another the Path instance with different params
        new Path(new Path());
    }

    @Test
    public void testAddRect1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(rect, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddRect2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.addRect(LEFT, TOP, RIGHT, BOTTOM, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testMoveTo() {
        Path path = new Path();
        path.moveTo(10.0f, 10.0f);
    }

    @Test
    public void testSet() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path path1 = new Path();
        addRectToPath(path1);
        path.set(path1);
        verifyPathsAreEquivalent(path, path1);
    }

    @Test
    public void testSetCleanOld() {
        Path path = new Path();
        addRectToPath(path);
        path.addRect(new RectF(0, 0, 10, 10), Path.Direction.CW);
        Path path1 = new Path();
        path1.addRect(new RectF(10, 10, 20, 20), Path.Direction.CW);
        path.set(path1);
        verifyPathsAreEquivalent(path, path1);
    }

    @Test
    public void testSetEmptyPath() {
        Path path = new Path();
        addRectToPath(path);
        Path path1 = new Path();
        path.set(path1);
        verifyPathsAreEquivalent(path, path1);
    }

    @Test
    public void testAccessFillType() {
        // set the expected value
        Path.FillType expected1 = Path.FillType.EVEN_ODD;
        Path.FillType expected2 = Path.FillType.INVERSE_EVEN_ODD;
        Path.FillType expected3 = Path.FillType.INVERSE_WINDING;
        Path.FillType expected4 = Path.FillType.WINDING;

        // new the Path instance
        Path path = new Path();
        // set FillType by {@link Path#setFillType(FillType)}
        path.setFillType(Path.FillType.EVEN_ODD);
        assertEquals(expected1, path.getFillType());
        path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
        assertEquals(expected2, path.getFillType());
        path.setFillType(Path.FillType.INVERSE_WINDING);
        assertEquals(expected3, path.getFillType());
        path.setFillType(Path.FillType.WINDING);
        assertEquals(expected4, path.getFillType());
    }

    @Test
    public void testRQuadTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.rQuadTo(5.0f, 5.0f, 10.0f, 10.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testTransform1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path dst = new Path();
        addRectToPath(path);
        path.transform(new Matrix(), dst);
        assertFalse(dst.isEmpty());
    }

    @Test
    public void testLineTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.lineTo(XCOORD, YCOORD);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testClose() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        path.close();
    }

    @Test
    public void testQuadTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.quadTo(20.0f, 20.0f, 40.0f, 40.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddCircle() {
        // new the Path instance
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.addCircle(XCOORD, YCOORD, 10.0f, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testArcTo1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.arcTo(oval, 0.0f, 30.0f, true);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testArcTo2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.arcTo(oval, 0.0f, 30.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testDeprecatedComputeBounds1() {
        RectF expected = new RectF(0.0f, 0.0f, 0.0f, 0.0f);
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
        path.computeBounds(bounds, false);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
    }

    @Test
    public void testDeprecatedComputeBounds2() {
        RectF expected = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF bounds = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(bounds, Path.Direction.CW);
        path.computeBounds(bounds, true);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
        path.computeBounds(bounds, false);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
    }

    @Test
    public void testComputeBounds1() {
        RectF expected = new RectF(0.0f, 0.0f, 0.0f, 0.0f);
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF bounds = new RectF();
        path.computeBounds(bounds);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
        path.computeBounds(bounds);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
    }

    @Test
    public void testComputeBounds2() {
        RectF expected = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF bounds = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(bounds, Path.Direction.CW);
        path.computeBounds(bounds);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
        path.computeBounds(bounds);
        assertEquals(expected.width(), bounds.width(), 0.0f);
        assertEquals(expected.height(), bounds.height(), 0.0f);
    }

    @Test
    public void testSetLastPoint() {
        Path path = new Path();
        path.setLastPoint(10.0f, 10.0f);
    }

    @Test
    public void testRLineTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.rLineTo(10.0f, 10.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testIsEmpty() {

        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testRewind() {
        Path.FillType expected = Path.FillType.EVEN_ODD;

        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        path.rewind();
        path.setFillType(Path.FillType.EVEN_ODD);
        assertTrue(path.isEmpty());
        assertEquals(expected, path.getFillType());
    }

    @Test
    public void testAddOval() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addOval(oval, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testIsRect() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
    }

    @Test
    public void testAddPath1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path src = new Path();
        addRectToPath(src);
        path.addPath(src, 10.0f, 10.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddPath2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path src = new Path();
        addRectToPath(src);
        path.addPath(src);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddPath3() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path src = new Path();
        addRectToPath(src);
        Matrix matrix = new Matrix();
        path.addPath(src, matrix);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddRoundRect1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRoundRect(rect, XCOORD, YCOORD, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testAddRoundRect2() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) {
            radii[i] = 10.0f + i * 5.0f;
        }
        path.addRoundRect(rect, radii, Path.Direction.CW);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testIsConvex1() {
        Path path = new Path();
        path.addRect(0, 0, 100, 10, Path.Direction.CW);
        assertTrue(path.isConvex());

        path.addRect(0, 0, 10, 100, Path.Direction.CW);
        assertFalse(path.isConvex()); // path is concave
    }

    @Test
    public void testIsConvex2() {
        Path path = new Path();
        path.addRect(0, 0, 40, 40, Path.Direction.CW);
        assertTrue(path.isConvex());

        path.addRect(10, 10, 30, 30, Path.Direction.CCW);
        assertFalse(path.isConvex()); // path has hole, isn't convex
    }

    @Test
    public void testIsConvex3() {
        Path path = new Path();
        path.addRect(0, 0, 10, 10, Path.Direction.CW);
        assertTrue(path.isConvex());

        path.addRect(0, 20, 10, 10, Path.Direction.CW);
        assertFalse(path.isConvex()); // path isn't one convex shape
    }

    @Test
    public void testIsInverseFillType() {
        Path path = new Path();
        assertFalse(path.isInverseFillType());
        path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
        assertTrue(path.isInverseFillType());
    }

    @Test
    public void testOffset1() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        addRectToPath(path);
        Path dst = new Path();
        path.offset(XCOORD, YCOORD, dst);
        assertFalse(dst.isEmpty());
    }

    @Test
    public void testCubicTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.cubicTo(10.0f, 10.0f, 20.0f, 20.0f, 30.0f, 30.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    @ApiTest(apis = {"android.graphics.Path#getPathIterator", "android.graphics.PathIterator#next",
            "android.graphics.PathIterator.Segment#getVerb",
            "android.graphics.PathIterator.Segment#getPoints",
            "android.graphics.PathIterator.Segment#getConicWeight",
            "android.graphics.Path#isEmpty", "android.graphics.Path#conicTo"})
    public void testConicTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.conicTo(10.0f, 10.0f, 20.0f, 20.0f, 2f);
        assertFalse(path.isEmpty());
        int verbIndex = 0;
        for (PathIterator it = path.getPathIterator(); it.hasNext(); ) {
            PathIterator.Segment segment = it.next();
            int verb = segment.getVerb();
            float[] points = segment.getPoints();
            float weight = segment.getConicWeight();
            switch (verb) {
                case PathIterator.VERB_CONIC:
                    assertEquals(0f, points[0], 0f);
                    assertEquals(0f, points[1], 0f);
                    assertEquals(10f, points[2], 0f);
                    assertEquals(10f, points[3], 0f);
                    assertEquals(20f, points[4], 0f);
                    assertEquals(20f, points[5], 0f);
                    assertEquals(2f, weight, 0f);
                    break;
                default:
                    break;
            }
        }
    }

    @Test
    public void testReset() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        Path path1 = new Path();
        addRectToPath(path1);
        path.set(path1);
        assertFalse(path.isEmpty());
        path.reset();
        assertTrue(path.isEmpty());
    }

    @Test
    public void testResetPreservesFillType() {
        Path path = new Path();

        final Path.FillType defaultFillType = path.getFillType();
        final Path.FillType fillType = Path.FillType.INVERSE_EVEN_ODD;

        // This test is only meaningful if it changes from the default.
        assertFalse(fillType.equals(defaultFillType));

        path.setFillType(fillType);
        path.reset();
        assertEquals(path.getFillType(), fillType);
    }

    @Test
    public void testToggleInverseFillType() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.toggleInverseFillType();
        assertTrue(path.isInverseFillType());
    }

    @Test
    public void testAddArc() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        RectF oval = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addArc(oval, 0.0f, 30.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testRCubicTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.rCubicTo(10.0f, 10.0f, 11.0f, 11.0f, 12.0f, 12.0f);
        assertFalse(path.isEmpty());
    }

    @Test
    @ApiTest(apis = {"android.graphics.Path#getPathIterator", "android.graphics.PathIterator#next",
            "android.graphics.PathIterator.Segment#getVerb",
            "android.graphics.PathIterator.Segment#getPoints",
            "android.graphics.PathIterator.Segment#getConicWeight",
            "android.graphics.Path#isEmpty", "android.graphics.Path#rConicTo"})
    public void testRConicTo() {
        Path path = new Path();
        assertTrue(path.isEmpty());
        path.moveTo(5f, 15f);
        path.rConicTo(10.0f, 10.0f, 20.0f, 20.0f, 2f);
        assertFalse(path.isEmpty());
        int verbIndex = 0;
        for (PathIterator it = path.getPathIterator(); it.hasNext(); ) {
            PathIterator.Segment segment = it.next();
            int verb = segment.getVerb();
            float[] points = segment.getPoints();
            float weight = segment.getConicWeight();
            switch (verb) {
                case PathIterator.VERB_MOVE:
                    assertEquals(5f, points[0], 0f);
                    assertEquals(15f, points[1], 0f);
                    break;
                case PathIterator.VERB_CONIC:
                    assertEquals(5f, points[0], 0f);
                    assertEquals(15f, points[1], 0f);
                    assertEquals(15f, points[2], 0f);
                    assertEquals(25f, points[3], 0f);
                    assertEquals(25f, points[4], 0f);
                    assertEquals(35f, points[5], 0f);
                    assertEquals(2f, weight, 0f);
                    break;
                default:
                    break;
            }
        }
    }

    @Test
    public void testOffsetTextPath() {
        Paint paint = new Paint();
        Path path = new Path();
        paint.setTextSize(20);
        String text = "abc";
        paint.getTextPath(text, 0, text.length() - 1, 0, 0, path);
        RectF expectedRect = new RectF();
        path.computeBounds(expectedRect);
        assertFalse(expectedRect.isEmpty());
        int offset = 10;
        expectedRect.offset(offset, offset);

        path.offset(offset, offset);
        RectF offsettedRect = new RectF();
        path.computeBounds(offsettedRect);
        assertEquals(expectedRect, offsettedRect);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testApproximate_lowError() {
        new Path().approximate(-0.1f);
    }

    @Test
    public void testApproximate_rect_cw() {
        Path path = new Path();
        path.addRect(0, 0, 100, 100, Path.Direction.CW);
        assertArrayEquals(new float[] {
                0, 0, 0,
                0.25f, 100, 0,
                0.50f, 100, 100,
                0.75f, 0, 100,
                1, 0, 0,
        }, path.approximate(1f), 0);
    }

    @Test
    public void testApproximate_rect_ccw() {
        Path path = new Path();
        path.addRect(0, 0, 100, 100, Path.Direction.CCW);
        assertArrayEquals(new float[] {
                0, 0, 0,
                0.25f, 0, 100,
                0.50f, 100, 100,
                0.75f, 100, 0,
                1, 0, 0,
        }, path.approximate(1f), 0);
    }

    @Test
    public void testApproximate_empty() {
        Path path = new Path();
        assertArrayEquals(new float[] {
                0, 0, 0,
                1, 0, 0,
        }, path.approximate(0.5f), 0);
    }

    @Test
    public void testApproximate_circle() {
        Path path = new Path();
        path.addCircle(0, 0, 50, Path.Direction.CW);
        assertTrue(path.approximate(0.25f).length > 20);
    }

    @Test
    public void testPathOffset() {
        Path actualPath = new Path();
        actualPath.addRect(START_X, START_Y, START_X + SQUARE, START_Y + SQUARE, Direction.CW);
        actualPath.offset(OFFSET_X, OFFSET_Y);

        Path expectedPath = new Path();
        expectedPath.addRect(START_X + OFFSET_X, START_Y + OFFSET_Y, START_X + OFFSET_X + SQUARE,
                START_Y + OFFSET_Y + SQUARE, Direction.CW);

        verifyPathsAreEquivalent(actualPath, expectedPath);
    }

    @Test
    public void testPathOffset2() {
        Path actualPath = new Path();
        actualPath.moveTo(0, 0);
        actualPath.offset(OFFSET_X, OFFSET_Y);
        actualPath.lineTo(OFFSET_X + 20, OFFSET_Y);


        Path expectedPath = new Path();
        expectedPath.moveTo(OFFSET_X, OFFSET_Y);
        expectedPath.lineTo(OFFSET_X + 20, OFFSET_Y);

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        verifyPathsAreEquivalent(actualPath, expectedPath, paint);
    }

    @Test
    public void testPathOffsetWithDestination() {
        Path initialPath = new Path();
        initialPath.addRect(START_X, START_Y, START_X + SQUARE, START_Y + SQUARE, Direction.CW);
        Path actualPath = new Path();
        initialPath.offset(OFFSET_X, OFFSET_Y, actualPath);

        Path expectedPath = new Path();
        expectedPath.addRect(START_X + OFFSET_X, START_Y + OFFSET_Y, START_X + OFFSET_X + SQUARE,
                START_Y + OFFSET_Y + SQUARE, Direction.CW);

        verifyPathsAreEquivalent(actualPath, expectedPath);
    }

    /** This test just ensures the process doesn't crash. The actual output is not interesting
     *  hence the lack of asserts, as the only behavior that's being asserted is that it
     *  doesn't crash.
     */
    @Test
    public void testUseAfterFinalize() throws Throwable {
        PathAbuser pathAbuser = new PathAbuser();

        // Basic test that we created a path successfully
        assertTrue(pathAbuser.isEmpty());
        addRectToPath(pathAbuser);
        assertTrue(pathAbuser.isRect(null));
        assertFalse(pathAbuser.isEmpty());

        // Now use-after-finalize.
        pathAbuser.destroy();
        pathAbuser.isEmpty();
        pathAbuser.isRect(null);
        pathAbuser.destroy();
        pathAbuser.isEmpty();
        pathAbuser.isRect(null);
        pathAbuser.destroy();
    }

    @Test
    @ApiTest(apis = {"android.graphics.Path#moveTo", "android.graphics.Path#lineTo",
            "android.graphics.Path#quadTo", "android.graphics.Path#conicTo",
            "android.graphics.Path#cubicTo", "android.graphics.Path#close",
            "android.graphics.Path#getGenerationId",
    })
    public void testGenerationId() {
        Path path = new Path();
        path.moveTo(1f, 2f);
        int generationId = path.getGenerationId();

        path.lineTo(3f, 4f);
        assertNotEquals(generationId, path.getGenerationId());
        generationId = path.getGenerationId();

        path.moveTo(5f, 6f);
        assertNotEquals(generationId, path.getGenerationId());
        generationId = path.getGenerationId();

        path.quadTo(7f, 8f, 9f, 10f);
        assertNotEquals(generationId, path.getGenerationId());
        generationId = path.getGenerationId();

        path.conicTo(11f, 12f, 13f, 14f, 2f);
        assertNotEquals(generationId, path.getGenerationId());
        generationId = path.getGenerationId();

        path.cubicTo(15f, 16f, 17f, 18f, 19f, 20f);
        assertNotEquals(generationId, path.getGenerationId());
        generationId = path.getGenerationId();

        path.close();
        assertNotEquals(generationId, path.getGenerationId());
    }

    @Test
    @ApiTest(apis = {"android.graphics.Path#moveTo", "android.graphics.Path#lineTo",
            "android.graphics.PathIterator#next",
            "android.graphics.Path#isInterpolatable",
            "android.graphics.Path#interpolate",
    })
    public void testPathInterpolation() {
        Path startPath = new Path();
        Path endPath = new Path();
        startPath.moveTo(100f, 100f);
        startPath.lineTo(200f, 300f);
        endPath.moveTo(200f, 200f);
        endPath.lineTo(600f, 700f);

        Path interpolatedPath = new Path();
        assertTrue(startPath.isInterpolatable(endPath));

        startPath.interpolate(endPath, .5f, interpolatedPath);
        PathIterator iterator = interpolatedPath.getPathIterator();
        float[] points = new float[8];
        int verb = iterator.next(points, 0);
        assertEquals(PathIterator.VERB_MOVE, verb);
        assertEquals(150f, points[0], .001f);
        assertEquals(150f, points[1], .001f);
        verb = iterator.next(points, 0);
        assertEquals(PathIterator.VERB_LINE, verb);
        assertEquals(400f, points[2], .001f);
        assertEquals(500f, points[3], .001f);
    }

    private void assertPointsEqual(float[] points1, float[] points2, int numToCheck) {
        for (int i = 0; i < numToCheck; ++i) {
            assertEquals("point " + i + "not equal", points1[i], points2[i], 0f);
        }
    }

    private static final class PathAbuser extends Path {
        public void destroy() throws Throwable {
            finalize();
        }
    }

    private static void verifyPathsAreEquivalent(Path actual, Path expected) {
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        verifyPathsAreEquivalent(actual, expected, paint);
    }

    private static void verifyPathsAreEquivalent(Path actual, Path expected, Paint paint) {
        Bitmap actualBitmap = drawAndGetBitmap(actual, paint);
        Bitmap expectedBitmap = drawAndGetBitmap(expected, paint);
        assertTrue(actualBitmap.sameAs(expectedBitmap));
    }

    private static Bitmap drawAndGetBitmap(Path path, Paint paint) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawPath(path, paint);
        return bitmap;
    }

    private static void addRectToPath(Path path) {
        RectF rect = new RectF(LEFT, TOP, RIGHT, BOTTOM);
        path.addRect(rect, Path.Direction.CW);
    }
}
