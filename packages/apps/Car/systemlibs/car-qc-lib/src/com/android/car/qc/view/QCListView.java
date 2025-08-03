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

import static com.android.car.qc.view.QCView.QCActionListener;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.lifecycle.Observer;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;

/**
 * Quick Controls view for {@link QCList} instances.
 */
public class QCListView extends LinearLayout implements Observer<QCItem> {

    private QCActionListener mActionListener;

    public QCListView(Context context) {
        super(context);
        init();
    }

    public QCListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public QCListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public QCListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
    }

    /**
     * Set the view's {@link QCActionListener}. This listener will propagate to all QCRows.
     */
    public void setActionListener(QCActionListener listener) {
        mActionListener = listener;
        for (int i = 0; i < getChildCount(); i++) {
            QCRowView view = (QCRowView) getChildAt(i);
            view.setActionListener(mActionListener);
        }
    }

    @Override
    public void onChanged(QCItem qcItem) {
        if (qcItem == null) {
            removeAllViews();
            return;
        }
        if (!qcItem.getType().equals(QCItem.QC_TYPE_LIST)) {
            throw new IllegalArgumentException("Expected QCList type for QCListView but got "
                    + qcItem.getType());
        }
        QCList qcList = (QCList) qcItem;
        int rowCount = qcList.getRows().size();
        for (int i = 0; i < rowCount; i++) {
            if (getChildAt(i) != null) {
                QCRowView view = (QCRowView) getChildAt(i);
                view.setRow(qcList.getRows().get(i));
                view.setActionListener(mActionListener);
            } else {
                QCRowView view = new QCRowView(getContext());
                view.setRow(qcList.getRows().get(i));
                view.setActionListener(mActionListener);
                addView(view);
            }
        }
        if (getChildCount() > rowCount) {
            // remove extra rows
            removeViews(rowCount, getChildCount() - rowCount);
        }
    }
}
