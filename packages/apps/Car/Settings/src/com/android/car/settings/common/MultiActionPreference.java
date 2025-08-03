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

package com.android.car.settings.common;

import static com.android.car.ui.utils.CarUiUtils.requireViewByRefId;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiPreference;

/**
 * A class for preferences that have a main click action along with up to three other actions.
 */
public class MultiActionPreference extends CarUiPreference
        implements BaseActionItem.ActionItemInfoChangeListener {

    /**
     * Identifier enum for the three different action items.
     */
    public enum ActionItem {
        ACTION_ITEM1,
        ACTION_ITEM2,
        ACTION_ITEM3,
    }

    protected BaseActionItem[] mActionItemArray = new BaseActionItem[3];

    public MultiActionPreference(Context context,
            AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    public MultiActionPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public MultiActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MultiActionPreference(Context context) {
        super(context);
        init(null);
    }

    /**
     * Initialize styles and attributes, as well as what types of action items should be created.
     *
     * @param attrs Attribute set from which to read values
     */
    @CallSuper
    protected void init(@Nullable AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.MultiActionPreference);

        try {
            // Single case switch statement for now with the assumption more ActionItems will be
            // added in the future
            switch(a.getInt(
                    R.styleable.MultiActionPreference_action_item_one, -1)) {
                case 0:
                    mActionItemArray[0] = new ToggleButtonActionItem(this);
                    break;
                default:
                    break;
            }

            switch(a.getInt(
                    R.styleable.MultiActionPreference_action_item_two, -1)) {
                case 0:
                    mActionItemArray[1] = new ToggleButtonActionItem(this);
                    break;
                default:
                    break;
            }

            switch(a.getInt(
                    R.styleable.MultiActionPreference_action_item_three, -1)) {
                case 0:
                    mActionItemArray[2] = new ToggleButtonActionItem(this);
                    break;
                default:
                    break;
            }

            int[] actionItemVisibility = {
                    R.styleable.MultiActionPreference_action_item_one_shown,
                    R.styleable.MultiActionPreference_action_item_two_shown,
                    R.styleable.MultiActionPreference_action_item_three_shown};
            int[] actionItemEnabled = {
                    R.styleable.MultiActionPreference_action_item_one_enabled,
                    R.styleable.MultiActionPreference_action_item_two_enabled,
                    R.styleable.MultiActionPreference_action_item_three_enabled};

            for (int i = 0; i < mActionItemArray.length; i++) {
                if (mActionItemArray[i] != null) {
                    mActionItemArray[i].setVisible(a.getBoolean(
                            actionItemVisibility[i], true));
                    mActionItemArray[i].setEnabled(a.getBoolean(
                            actionItemEnabled[i], true));
                }
            }
        } finally {
            a.recycle();
        }

        setLayoutResource(R.layout.multi_action_preference);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View actionContainer = requireViewByRefId(holder.itemView,
                R.id.multi_action_preference_second_action_container);
        FrameLayout container1 = requireViewByRefId(holder.itemView,
                R.id.multi_action_preference_item_container1);
        FrameLayout container2 = requireViewByRefId(holder.itemView,
                R.id.multi_action_preference_item_container2);
        FrameLayout container3 = requireViewByRefId(holder.itemView,
                R.id.multi_action_preference_item_container3);

        boolean isActionContainerVisible = false;
        for (BaseActionItem baseActionItem: mActionItemArray) {
            if (baseActionItem != null) {
                baseActionItem.setPreference(this)
                        .setRestrictedOnClickListener(getOnClickWhileRestrictedListener());

                if (baseActionItem.isVisible()) {
                    isActionContainerVisible = true;
                }
            }
        }

        actionContainer.setVisibility(isActionContainerVisible ? View.VISIBLE : View.GONE);

        if (mActionItemArray[0] != null) {
            mActionItemArray[0].bindViewHolder(container1);
        }

        if (mActionItemArray[1] != null) {
            mActionItemArray[1].bindViewHolder(container2);
        }

        if (mActionItemArray[2] != null) {
            mActionItemArray[2].bindViewHolder(container3);
        }
    }

    @Override
    public void onActionItemChange(BaseActionItem baseActionItem) {
        notifyChanged();
    }

    /**
     * Retrieve the specified BaseActionItem based on the index.
     */
    public BaseActionItem getActionItem(ActionItem actionItem) {
        switch (actionItem) {
            case ACTION_ITEM1:
                return mActionItemArray[0];
            case ACTION_ITEM2:
                return mActionItemArray[1];
            case ACTION_ITEM3:
                return mActionItemArray[2];
            default:
                throw new IllegalArgumentException("Invalid button requested");
        }
    }
}
