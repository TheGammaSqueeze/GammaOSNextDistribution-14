package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.bitmapverifiers.RegionVerifier;
import android.uirendering.cts.testclasses.view.UnclippedBlueView;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.uirendering.cts.util.CompareUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This tests view clipping by modifying properties of blue_padded_layout, and validating
 * the resulting rect of content.
 *
 * Since the layout is blue on a white background, this is always done with a RectVerifier.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewClippingTests extends ActivityTestBase {
    static final Rect FULL_RECT = new Rect(0, 0, 90, 90);
    static final Rect BOUNDS_RECT = new Rect(0, 0, 80, 80);
    static final Rect PADDED_RECT = new Rect(15, 16, 63, 62);
    static final Rect OUTLINE_RECT = new Rect(1, 2, 78, 79);
    static final Rect ANTI_ALIAS_OUTLINE_RECT = new Rect(20, 10, 80, 80);
    static final Rect CLIP_BOUNDS_RECT = new Rect(10, 20, 50, 60);
    static final Rect CONCAVE_OUTLINE_RECT1 = new Rect(0, 0, 10, 90);
    static final Rect CONCAVE_TEST_RECT1 = new Rect(0, 10, 90, 90);
    static final Rect CONCAVE_OUTLINE_RECT2 = new Rect(0, 0, 90, 10);
    static final Rect CONCAVE_TEST_RECT2 = new Rect(10, 0, 90, 90);

    static final ViewInitializer BOUNDS_CLIP_INIT =
            rootView -> ((ViewGroup)rootView).setClipChildren(true);

    static final ViewInitializer PADDING_CLIP_INIT = rootView -> {
        ViewGroup child = (ViewGroup) rootView.findViewById(R.id.child);
        child.setClipToPadding(true);
        child.setWillNotDraw(true);
        child.addView(new UnclippedBlueView(rootView.getContext()));
    };

    static final ViewInitializer OUTLINE_CLIP_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
        child.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(OUTLINE_RECT);
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer OUTLINE_CLIP_AA_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
        ((ViewGroup) (child.getParent())).setBackgroundColor(Color.BLACK);
        child.setOutlineProvider(new ViewOutlineProvider() {
            Path mPath = new Path();
            @Override
            public void getOutline(View view, Outline outline) {
                mPath.rewind();
                // We're using the AA outline rect as a starting point, but shifting one of the
                // vertices slightly to force AA for this not-quite-rectangle
                mPath.moveTo(ANTI_ALIAS_OUTLINE_RECT.left, ANTI_ALIAS_OUTLINE_RECT.top);
                mPath.lineTo(ANTI_ALIAS_OUTLINE_RECT.right, ANTI_ALIAS_OUTLINE_RECT.top);
                mPath.lineTo(ANTI_ALIAS_OUTLINE_RECT.right, ANTI_ALIAS_OUTLINE_RECT.bottom);
                mPath.lineTo(ANTI_ALIAS_OUTLINE_RECT.left + 1f, ANTI_ALIAS_OUTLINE_RECT.bottom);
                mPath.close();
                outline.setPath(mPath);
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer CONCAVE_CLIP_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
        ((ViewGroup) (child.getParent())).setBackgroundColor(Color.BLACK);
        child.setOutlineProvider(new ViewOutlineProvider() {
            Path mPath = new Path();
            @Override
            public void getOutline(View view, Outline outline) {
                mPath.rewind();
                mPath.addRect(CONCAVE_OUTLINE_RECT1.left, CONCAVE_OUTLINE_RECT1.top,
                        CONCAVE_OUTLINE_RECT1.right, CONCAVE_OUTLINE_RECT1.bottom,
                        Path.Direction.CW);
                mPath.addRect(CONCAVE_OUTLINE_RECT2.left, CONCAVE_OUTLINE_RECT2.top,
                        CONCAVE_OUTLINE_RECT2.right, CONCAVE_OUTLINE_RECT2.bottom,
                        Path.Direction.CW);
                outline.setPath(mPath);
                assertTrue(outline.canClip());
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer CLIP_BOUNDS_CLIP_INIT =
            view -> view.setClipBounds(CLIP_BOUNDS_RECT);

    static BitmapVerifier makeClipVerifier(Rect blueBoundsRect) {
        // very high error tolerance, since all these tests care about is clip alignment
        return new RectVerifier(Color.WHITE, Color.BLUE, blueBoundsRect, 75);
    }

    static BitmapVerifier makeConcaveClipVerifier() {
        return new RegionVerifier()
                .addVerifier(CONCAVE_TEST_RECT1, new RectVerifier(Color.BLACK, Color.BLUE,
                        CONCAVE_OUTLINE_RECT1, 75))
                .addVerifier(CONCAVE_TEST_RECT2, new RectVerifier(Color.BLACK, Color.BLUE,
                        CONCAVE_OUTLINE_RECT2, 75));
    }

    @Test
    public void testSimpleUnclipped() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, null)
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testSimpleBoundsClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, BOUNDS_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(BOUNDS_RECT));
    }

    @Test
    public void testSimpleClipBoundsClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, CLIP_BOUNDS_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(CLIP_BOUNDS_RECT));
    }

    @Test
    public void testSimplePaddingClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, PADDING_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(PADDED_RECT));
    }
    // TODO: add tests with clip + scroll, and with interesting combinations of the above

    @Test
    public void testSimpleOutlineClip() {
        // NOTE: Only HW is supported
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_INIT, true)
                .runWithVerifier(makeClipVerifier(OUTLINE_RECT));

        // SW ignores the outline clip
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_INIT, false)
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testAntiAliasedOutlineClip() {
        PerPixelBitmapVerifier antiAliasVerifier = new PerPixelBitmapVerifier() {

            int mNumAntiAliasedPixels = 0;
            int[] mTargetBitmapPixels = null;
            int mOffset = 0;
            int mStride = 0;
            int mWidth = 0;
            int mHeight = 0;

            private int getPixelColor(int x, int y, int fallback) {
                if (x < 0 || x >= mWidth || y < 0 || y >= mHeight) {
                    return fallback;
                }
                int index = indexFromXAndY(x, y, mStride, mOffset);
                return mTargetBitmapPixels[index];
            }

            @Override
            protected int getExpectedColor(int x, int y) {
                return ANTI_ALIAS_OUTLINE_RECT.contains(x, y) ? Color.BLUE : Color.BLACK;
            }

            @Override
            protected boolean verifyPixel(int x, int y, int observedColor) {
                boolean withinVerticalBounds = y > ANTI_ALIAS_OUTLINE_RECT.top
                        && y < ANTI_ALIAS_OUTLINE_RECT.bottom;
                boolean result;
                final int antiAliasThreshold = 1;
                boolean onHorizontalBoundary =
                        Math.abs(x - ANTI_ALIAS_OUTLINE_RECT.left) <= antiAliasThreshold
                        || Math.abs(x - ANTI_ALIAS_OUTLINE_RECT.right) <= antiAliasThreshold;
                boolean onVerticalBoundary =
                        Math.abs(y - ANTI_ALIAS_OUTLINE_RECT.top) <= antiAliasThreshold
                        || Math.abs(y - ANTI_ALIAS_OUTLINE_RECT.bottom) <= antiAliasThreshold;
                if (x == ANTI_ALIAS_OUTLINE_RECT.left && withinVerticalBounds) {
                    // Verify that the blue channel if the pixel above the current one is lower
                    // indicating the offset of 1 pixel from the outline provider path is being
                    // reflected in the rendered result. Additionally verify that the current pixel
                    // is either the inner or foreground colors or is an anti-aliased color in
                    // between to make sure we fail on unexpected colors that may have descending
                    // color channels
                    boolean isAntiAliasedPixel = CompareUtils.verifyPixelBetweenColors(
                            observedColor, Color.BLACK, Color.BLUE);
                    boolean isInnerOrOuterColor = observedColor == Color.BLUE
                            || observedColor == Color.BLACK;
                    boolean isValidColor = isInnerOrOuterColor || isAntiAliasedPixel;
                    int previousBlueChannel = Color.blue(getPixelColor(x, y, Color.BLUE));
                    int blueChannel = Color.blue(observedColor);
                    result = blueChannel <= previousBlueChannel && isValidColor;
                    if (isAntiAliasedPixel && blueChannel <= previousBlueChannel) {
                        // To ensure that anti-aliasing is applied, keep count of the colors
                        // in between the inner and outer regions that are neither of these colors
                        mNumAntiAliasedPixels++;
                    }
                } else if (onHorizontalBoundary || onVerticalBoundary) {
                    // If we are on the edges of the rectangle accept either blue or black or
                    // any color in between as we may not be on a pixel boundary.
                    result = Color.BLUE == observedColor
                            || Color.BLACK == observedColor
                            || CompareUtils.verifyPixelBetweenColors(observedColor, Color.BLACK,
                                    Color.BLUE);
                } else {
                    // Otherwise, we are either on the interior or exterior of the content
                    // and fallback on traditional pixel verification
                    result = super.verifyPixel(x, y, observedColor);
                }

                return result;
            }

            @Override
            public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
                mTargetBitmapPixels = bitmap;
                mOffset = offset;
                mStride = stride;
                mWidth = width;
                mHeight = height;
                boolean result = super.verify(bitmap, offset, stride, width, height);
                // Verify that we have seen pixel color values in between the outer and inner
                // colors indicating that anti-aliasing has been applied.
                return result && mNumAntiAliasedPixels > (height * 0.1f);
            }
        };
        // NOTE: Only HW is supported
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_AA_INIT, true)
                .runWithVerifier(antiAliasVerifier);
    }

    @Test
    public void testOvalOutlineClip() {
        // As of Android T, Outline clipping is enabled for all shapes.
        createTest()
                .addLayout(R.layout.blue_padded_layout, view -> {
                    view.setOutlineProvider(new ViewOutlineProvider() {
                        Path mPath = new Path();
                        @Override
                        public void getOutline(View view, Outline outline) {
                            mPath.reset();
                            mPath.addOval(0, 0, view.getWidth(), view.getHeight(),
                                    Path.Direction.CW);
                            outline.setPath(mPath);
                            assertTrue(outline.canClip());
                        }
                    });
                    view.setClipToOutline(false); // should do nothing
                })
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testConcaveOutlineClip() {
        // As of Q, Outline#setPath (previously called setConvexPath) no longer throws on a concave
        // path, but it does not result in clipping, which is only supported when explicitly calling
        // one of the other setters. (hw no-op's the arbitrary path, and sw doesn't support Outline
        // clipping.)
        // As of T, path clipping is enabled for all Outline shapes.
        createTest()
                .addLayout(R.layout.blue_padded_layout, CONCAVE_CLIP_INIT, true)
                .runWithVerifier(makeConcaveClipVerifier());
    }
}
