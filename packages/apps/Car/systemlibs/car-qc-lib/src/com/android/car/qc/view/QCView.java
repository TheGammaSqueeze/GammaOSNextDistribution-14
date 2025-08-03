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

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.android.car.qc.QCItem;

/**
 * Base Quick Controls View - supports {@link QCItem.QC_TYPE_TILE} and {@link QCItem.QC_TYPE_LIST}
 */
public class QCView extends FrameLayout implements Observer<QCItem> {
    @QCItem.QCItemType
    private String mType;
    private Observer<QCItem> mChildObserver;
    private QCActionListener mActionListener;

    public QCView(Context context) {
        super(context);
    }

    public QCView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QCView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public QCView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Set the view's {@link QCActionListener}. This listener will propagate to all sub-views.
     */
    public void setActionListener(QCActionListener listener) {
        mActionListener = listener;
        if (mChildObserver instanceof QCTileView) {
            ((QCTileView) mChildObserver).setActionListener(mActionListener);
        } else if (mChildObserver instanceof QCListView) {
            ((QCListView) mChildObserver).setActionListener(mActionListener);
        }
    }

    @Override
    public void onChanged(QCItem qcItem) {
        if (qcItem == null) {
            removeAllViews();
            mChildObserver = null;
            mType = null;
            return;
        }
        if (!isValidQCItemType(qcItem)) {
            throw new IllegalArgumentException("Expected QCTile or QCList type but got "
                    + qcItem.getType());
        }
        if (qcItem.getType().equals(mType)) {
            mChildObserver.onChanged(qcItem);
            return;
        }
        removeAllViews();
        mType = qcItem.getType();
        if (mType.equals(QCItem.QC_TYPE_TILE)) {
            QCTileView view = new QCTileView(getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL);
            view.onChanged(qcItem);
            view.setActionListener(mActionListener);
            addView(view, params);
            mChildObserver = view;
        } else {
            QCListView view = new QCListView(getContext());
            view.onChanged(qcItem);
            view.setActionListener(mActionListener);
            addView(view);
            mChildObserver = view;
        }
    }

    private boolean isValidQCItemType(QCItem qcItem) {
        String type = qcItem.getType();
        return type.equals(QCItem.QC_TYPE_TILE) || type.equals(QCItem.QC_TYPE_LIST);
    }

    /**
     * Listener to be called when an action occurs on a QCView.
     */
    public interface QCActionListener {
        /**
         * Called when an interaction has occurred with an element in this view.
         * @param item the specific item within the {@link QCItem} that was interacted with.
         * @param action the action that was executed - is generally either a
         *               {@link android.app.PendingIntent} or {@link QCItem.ActionHandler}
         */
        void onQCAction(@NonNull QCItem item, @NonNull Object action);
    }
}
