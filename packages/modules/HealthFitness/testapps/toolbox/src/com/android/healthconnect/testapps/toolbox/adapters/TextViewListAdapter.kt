/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *    http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.testapps.toolbox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.testapps.toolbox.R

class TextViewListAdapter(
    private val mDataSet: List<Any>,
    private val mOnBindViewHolderCallback:
        (viewHolder: TextViewListViewHolder, position: Int) -> Unit,
) : RecyclerView.Adapter<TextViewListViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TextViewListViewHolder {
        val view =
            LayoutInflater.from(viewGroup.context).inflate(R.layout.text_view, viewGroup, false)

        return TextViewListViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: TextViewListViewHolder, position: Int) {
        mOnBindViewHolderCallback.invoke(viewHolder, position)
    }

    override fun getItemCount() = mDataSet.size
}

class TextViewListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val textView: TextView

    init {
        textView = view.findViewById(R.id.list_item_textview)
    }
}
