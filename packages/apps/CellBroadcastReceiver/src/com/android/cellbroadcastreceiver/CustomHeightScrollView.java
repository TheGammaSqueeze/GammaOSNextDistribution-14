/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * Custom scroll view to display text with a max height inside a {@link CellBroadcastAlertDialog}
 */
public class CustomHeightScrollView extends ScrollView {
    private int mMaximumHeight = 0;

    public CustomHeightScrollView(Context context) {
        super(context);
    }

    public CustomHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomHeightScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setMaximumHeight(int height) {
        mMaximumHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if (mMaximumHeight > 0) {
            int measuredHeight = getMeasuredHeight();
            if (getLayoutParams().height != LayoutParams.MATCH_PARENT) {
                if (measuredHeight > mMaximumHeight) {
                    setMeasuredDimension(getMeasuredWidth(), mMaximumHeight);
                    return;
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

