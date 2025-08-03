package com.android.customization.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.PathParser;

/**
 * Drawable that draws a grid rows x cols of icon shapes adjusting their size to fit within its
 * bounds.
 */
public class GridTileDrawable extends Drawable {

    // Path is expected configuration in following dimension: [100 x 100]))
    private static final float PATH_SIZE = 100f;
    private static final float SPACE_BETWEEN_ICONS = 6f;
    private final int mCols;
    private final int mRows;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mShapePath;
    private final Path mTransformedPath;
    private final Matrix mScaleMatrix;
    private float mCellSize = -1f;

    public GridTileDrawable(int cols, int rows, String path) {
        mCols = cols;
        mRows = rows;

        mShapePath = PathParser.createPathFromPathData(path);
        mTransformedPath = new Path(mShapePath);
        mScaleMatrix = new Matrix();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        int longestSide = Math.max(mRows, mCols);
        mCellSize = (float) bounds.width() / longestSide;

        float scaleFactor = (mCellSize - 2 * SPACE_BETWEEN_ICONS) / PATH_SIZE;
        mScaleMatrix.setScale(scaleFactor, scaleFactor);
        mShapePath.transform(mScaleMatrix, mTransformedPath);
    }

    @Override
    public void draw(Canvas canvas) {
        double size = getBounds().width();

        for (int r = 0; r < mRows; r++) {
            for (int c = 0; c < mCols; c++) {
                int saveCount = canvas.save();
                float y = (float) ((r * size / mRows) + SPACE_BETWEEN_ICONS);
                float x = (float) ((c * size / mCols) + SPACE_BETWEEN_ICONS);
                canvas.translate(x, y);
                canvas.drawPath(mTransformedPath, mPaint);
                canvas.restoreToCount(saveCount);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
