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

package com.android.car.qc.view;

import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;
import static com.android.car.qc.view.QCView.QCActionListener;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.lifecycle.Observer;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;
import com.android.car.qc.R;
import com.android.car.ui.utils.CarUiUtils;
import com.android.car.ui.uxr.DrawableStateToggleButton;

/**
 * Quick Controls view for {@link QCTile} instances.
 */
public class QCTileView extends FrameLayout implements Observer<QCItem> {
    private static final String TAG = "QCTileView";

    private View mTileWrapper;
    private DrawableStateToggleButton mToggleButton;
    private TextView mSubtitle;
    private QCActionListener mActionListener;

    public QCTileView(Context context) {
        super(context);
        init(context);
    }

    public QCTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QCTileView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public QCTileView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    /**
     * Set the tile's {@link QCActionListener}.
     */
    public void setActionListener(QCActionListener listener) {
        mActionListener = listener;
    }

    private void init(Context context) {
        View.inflate(context, R.layout.qc_tile_view, /* root= */ this);
        mTileWrapper = findViewById(R.id.qc_tile_wrapper);
        mToggleButton = findViewById(R.id.qc_tile_toggle_button);
        mSubtitle = findViewById(android.R.id.summary);
        mToggleButton.setText(null);
        mToggleButton.setTextOn(null);
        mToggleButton.setTextOff(null);
    }

    @Override
    public void onChanged(QCItem qcItem) {
        if (qcItem == null) {
            removeAllViews();
            return;
        }
        if (!qcItem.getType().equals(QCItem.QC_TYPE_TILE)) {
            throw new IllegalArgumentException("Expected QCTile type for QCTileView but got "
                    + qcItem.getType());
        }
        QCTile qcTile = (QCTile) qcItem;
        mSubtitle.setText(qcTile.getSubtitle());
        CarUiUtils.makeAllViewsEnabled(mToggleButton, qcTile.isEnabled());
        mToggleButton.setOnCheckedChangeListener(null);
        mToggleButton.setChecked(qcTile.isChecked());
        mToggleButton.setEnabled(qcTile.isEnabled() || qcTile.isClickableWhileDisabled());
        mTileWrapper.setEnabled(
                (qcTile.isEnabled() || qcTile.isClickableWhileDisabled()) && qcTile.isAvailable());
        mTileWrapper.setOnClickListener(v -> {
            if (!qcTile.isEnabled()) {
                if (qcTile.getDisabledClickAction() != null) {
                    try {
                        qcTile.getDisabledClickAction().send(getContext(), 0, new Intent());
                        if (mActionListener != null) {
                            mActionListener.onQCAction(qcTile, qcTile.getDisabledClickAction());
                        }
                    } catch (PendingIntent.CanceledException e) {
                        Log.d(TAG, "Error sending intent", e);
                    }
                } else if (qcTile.getDisabledClickActionHandler() != null) {
                    qcTile.getDisabledClickActionHandler().onAction(qcTile, getContext(),
                            new Intent());
                    if (mActionListener != null) {
                        mActionListener.onQCAction(qcTile, qcTile.getDisabledClickActionHandler());
                    }
                }
                return;
            }
            mToggleButton.toggle();
        });
        Drawable icon = QCViewUtils.getToggleIcon(mContext, qcTile.getIcon(), qcTile.isAvailable());
        mToggleButton.setButtonDrawable(icon);
        mToggleButton.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    Intent intent = new Intent();
                    intent.putExtra(QC_ACTION_TOGGLE_STATE, isChecked);
                    if (qcTile.getPrimaryAction() != null) {
                        try {
                            qcTile.getPrimaryAction().send(getContext(), 0, intent);
                            if (mActionListener != null) {
                                mActionListener.onQCAction(qcTile, qcTile.getPrimaryAction());
                            }
                        } catch (PendingIntent.CanceledException e) {
                            Log.d(TAG, "Error sending intent", e);
                        }
                    } else if (qcTile.getActionHandler() != null) {
                        qcTile.getActionHandler().onAction(qcTile, getContext(), intent);
                        if (mActionListener != null) {
                            mActionListener.onQCAction(qcTile, qcTile.getActionHandler());
                        }
                    }
                });
    }
}
