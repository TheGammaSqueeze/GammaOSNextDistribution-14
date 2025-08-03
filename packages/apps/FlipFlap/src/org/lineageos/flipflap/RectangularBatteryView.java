/*
 * Copyright (c) 2017-2021 The LineageOS Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * Also add information on how to contact you by electronic and paper mail.
 *
 */

package org.lineageos.flipflap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class RectangularBatteryView extends View {
    private static final String TAG = "RectangularBatteryView";

    private final Context mContext;
    private final Resources mResources;
    private final Paint mPaint;
    private Rect mRect;

    private boolean mBatteryStateReceiverRegistered;
    private final BroadcastReceiver mBatteryStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            if (!FlipFlapUtils.showBatteryStatus(mContext)) {
                mPaint.setColor(mResources.getColor(R.color.full_bat_bg, context.getTheme()));
            } else if (isCharging) {
                mPaint.setColor(mResources.getColor(R.color.charge_bat_bg, context.getTheme()));
            } else if (level >= 15) {
                mPaint.setColor(mResources.getColor(R.color.full_bat_bg, context.getTheme()));
            } else {
                mPaint.setColor(mResources.getColor(R.color.low_bat_bg, context.getTheme()));
            }
            postInvalidate();
        }
    };

    public RectangularBatteryView(Context context) {
        this(context, null);
    }

    public RectangularBatteryView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RectangularBatteryView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Style.FILL);
        mResources = mContext.getResources();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mBatteryStateReceiverRegistered) {
            mContext.registerReceiver(mBatteryStateReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED);
            mBatteryStateReceiverRegistered = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBatteryStateReceiverRegistered) {
            mContext.unregisterReceiver(mBatteryStateReceiver);
            mBatteryStateReceiverRegistered = false;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int width = mResources.getDimensionPixelSize(R.dimen.rectangular_window_width);
        int height = mResources.getDimensionPixelSize(R.dimen.rectangular_window_height);
        mRect = new Rect(0, 0, width, height);
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawRGB(0, 0, 0);
        canvas.drawRect(mRect, mPaint);
    }
}
