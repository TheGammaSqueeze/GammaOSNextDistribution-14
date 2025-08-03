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

package com.android.car.carlauncher.recents.view;

import android.view.View;
import android.widget.Button;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.R;

import org.jetbrains.annotations.NotNull;

/**
 * ViewHolder to contain the clear all button.
 */
public class ClearAllViewHolder extends RecyclerView.ViewHolder {
    /**
     * @param onClickListener the onClickListener to be attached to the clear all button.
     */
    public ClearAllViewHolder(@NotNull View itemView, View.OnClickListener onClickListener) {
        super(itemView);
        Button clearAllButton = itemView.findViewById(R.id.recents_clear_all_button);
        clearAllButton.setOnClickListener(onClickListener);
    }
}
