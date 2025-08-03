/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.chassis.car.ui.plugin.toolbar;

import static com.android.car.ui.utils.CarUiUtils.requireViewByRefId;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.asynclayoutinflater.view.AsyncLayoutInflater;

import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.uxr.DrawableStateView;

import com.chassis.car.ui.plugin.R;

import java.util.function.Consumer;

class MenuItemRenderer implements MenuItem.Listener {

    private static final int[] RESTRICTED_STATE = new int[]{R.attr.state_ux_restricted};

    private final int mMenuItemIconSize;

    private boolean mToolbarIsSearching;

    private final MenuItem mMenuItem;
    private final ViewGroup mParentView;
    private View mView;
    private View mIconContainer;
    private ImageView mIconView;
    private Switch mSwitch;
    private TextView mTextView;
    private TextView mTextWithIconView;
    private boolean mIndividualClickListeners;

    MenuItemRenderer(MenuItem item, ViewGroup parentView) {
        mMenuItem = item;
        mParentView = parentView;
        mMenuItem.setListener(this);

        mMenuItemIconSize = parentView.getContext().getResources()
                .getDimensionPixelSize(R.dimen.car_ui_toolbar_menu_item_icon_size);
    }

    void setToolbarIsSearching(boolean searching) {
        if (searching != mToolbarIsSearching) {
            mToolbarIsSearching = searching;

            if (mMenuItem.isSearch()) {
                updateView();
            }
        }
    }

    @Override
    public void onMenuItemChanged(MenuItem changedItem) {
        updateView();
    }

    void createView(Consumer<View> callback) {
        AsyncLayoutInflater inflater = new AsyncLayoutInflater(mParentView.getContext());
        @LayoutRes int layout = mMenuItem.isPrimary()
                ? R.layout.car_ui_toolbar_menu_item_primary
                : R.layout.car_ui_toolbar_menu_item;
        inflater.inflate(layout, mParentView, (View view, int resid,
                ViewGroup parent) -> {
            mView = view;

            mIconContainer =
                    requireViewByRefId(mView, R.id.car_ui_toolbar_menu_item_icon_container);
            mIconView = requireViewByRefId(mView, R.id.car_ui_toolbar_menu_item_icon);
            mSwitch = requireViewByRefId(mView, R.id.car_ui_toolbar_menu_item_switch);
            mTextView = requireViewByRefId(mView, R.id.car_ui_toolbar_menu_item_text);
            mTextWithIconView =
                    requireViewByRefId(mView, R.id.car_ui_toolbar_menu_item_text_with_icon);
            mIndividualClickListeners = mView.getContext().getResources()
                    .getBoolean(R.bool.car_ui_toolbar_menuitem_individual_click_listeners);

            updateView();
            callback.accept(mView);
        });
    }

    private void updateView() {
        if (mView == null) {
            return;
        }

        mView.setId(mMenuItem.getId());

        boolean hasIcon = mMenuItem.getIcon() != null;
        boolean hasText = !TextUtils.isEmpty(mMenuItem.getTitle());
        boolean textAndIcon = mMenuItem.isShowingIconAndTitle();
        boolean checkable = mMenuItem.isCheckable();

        if (!mMenuItem.isVisible()
                || (mMenuItem.isSearch() && mToolbarIsSearching)
                || (!checkable && !hasIcon && !hasText)) {
            mView.setVisibility(View.GONE);
            return;
        }
        mView.setVisibility(View.VISIBLE);
        mView.setContentDescription(mMenuItem.getTitle());

        View clickTarget;
        if (checkable) {
            mSwitch.setChecked(mMenuItem.isChecked());
            clickTarget = mSwitch;
        } else if (hasText && hasIcon && textAndIcon) {
            mMenuItem.getIcon().setBounds(0, 0, mMenuItemIconSize, mMenuItemIconSize);
            mTextWithIconView.setCompoundDrawables(mMenuItem.getIcon(), null, null, null);
            mTextWithIconView.setText(mMenuItem.getTitle());
            clickTarget = mTextWithIconView;
        } else if (hasIcon) {
            mIconView.setImageDrawable(mMenuItem.getIcon());
            clickTarget = mIconContainer;
        } else { // hasText will be true
            mTextView.setText(mMenuItem.getTitle());
            clickTarget = mTextView;
        }

        mIconContainer.setVisibility(clickTarget == mIconContainer ? View.VISIBLE : View.GONE);
        mTextView.setVisibility(clickTarget == mTextView ? View.VISIBLE : View.GONE);
        mTextWithIconView.setVisibility(clickTarget == mTextWithIconView
                ? View.VISIBLE : View.GONE);
        mSwitch.setVisibility(clickTarget == mSwitch ? View.VISIBLE : View.GONE);

        if (!mIndividualClickListeners) {
            clickTarget = mView;
        }

        if (!mMenuItem.isTinted() && hasIcon) {
            mMenuItem.getIcon().setTintList(null);
        }

        recursiveSetEnabledAndDrawableState(mView);
        mView.setActivated(mMenuItem.isActivated());

        if (mMenuItem.getOnClickListener() != null
                || mMenuItem.isCheckable()
                || mMenuItem.isActivatable()) {
            clickTarget.setOnClickListener(v -> mMenuItem.performClick());
        } else if (clickTarget == mView) {
            mView.setOnClickListener(null);
            mView.setClickable(false);
        }
    }

    private void recursiveSetEnabledAndDrawableState(View view) {
        view.setEnabled(mMenuItem.isEnabled());

        int[] drawableState = mMenuItem.isRestricted() ? RESTRICTED_STATE : null;
        if (view instanceof ImageView) {
            ((ImageView) view).setImageState(drawableState, true);
        } else if (view instanceof DrawableStateView) {
            ((DrawableStateView) view).setExtraDrawableState(drawableState, null);
        }

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = ((ViewGroup) view);
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                recursiveSetEnabledAndDrawableState(viewGroup.getChildAt(i));
            }
        }
    }
}
