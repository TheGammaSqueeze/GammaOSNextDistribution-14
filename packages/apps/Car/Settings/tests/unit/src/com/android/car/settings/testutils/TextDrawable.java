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
package com.android.car.settings.testutils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import java.util.Objects;

/**
 * A simple {@link Drawable} that draws a text.
 *
 * <p>Useful on tests that compare icons.
 */
public final class TextDrawable extends Drawable {

    private static final Paint PAINT = new Paint();

    private final CharSequence mText;

    public TextDrawable(CharSequence label) {
        mText = Objects.requireNonNull(label);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawText(mText, 0, mText.length(), 0, 0, PAINT);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TextDrawable)) {
            return false;
        }
        return mText.equals(((TextDrawable) obj).mText);
    }

    @Override
    public int hashCode() {
        return mText.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[text=" + mText + ", id="
                + System.identityHashCode(this) + "]";
    };
}
