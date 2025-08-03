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

import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Locale;

public class DatePanel extends LinearLayout {
    private static final String TAG = "DatePanel";

    private final Context mContext;

    public DatePanel(Context context) {
        this(context, null);
    }

    public DatePanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DatePanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mContext = context;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        final CharSequence dateFormat = getDateFormat(mContext);
        TextClock dateView = findViewById(R.id.date_regular);
        dateView.setFormat12Hour(dateFormat);
        dateView.setFormat24Hour(dateFormat);
    }

    private static CharSequence getDateFormat(Context context) {
        final String dateFormat = context.getString(R.string.abbrev_wday_month_day_no_year);
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), dateFormat);
    }
}
