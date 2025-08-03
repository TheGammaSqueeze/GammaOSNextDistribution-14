/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver.widget

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.intentresolver.R

class ScrollableActionRow : RecyclerView, ActionRow {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = Adapter(context)

        addItemDecoration(
            MarginDecoration(
                context.resources.getDimensionPixelSize(R.dimen.chooser_action_horizontal_margin),
                context.resources.getDimensionPixelSize(R.dimen.chooser_edge_margin_normal)
            )
        )
    }

    private val actionsAdapter
        get() = adapter as Adapter

    override fun setActions(actions: List<ActionRow.Action>) {
        actionsAdapter.setActions(actions)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setOverScrollMode(
            if (areAllChildrenVisible) View.OVER_SCROLL_NEVER else View.OVER_SCROLL_ALWAYS
        )
    }

    private inner class Adapter(private val context: Context) : RecyclerView.Adapter<ViewHolder>() {
        private val iconSize: Int =
            context.resources.getDimensionPixelSize(R.dimen.chooser_action_view_icon_size)
        private val itemLayout = R.layout.chooser_action_view
        private var actions: List<ActionRow.Action> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(context).inflate(itemLayout, null) as TextView,
                iconSize,
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(actions[position])
        }

        override fun getItemCount() = actions.size

        override fun onViewRecycled(holder: ViewHolder) {
            holder.unbind()
        }

        override fun onFailedToRecycleView(holder: ViewHolder): Boolean {
            holder.unbind()
            return super.onFailedToRecycleView(holder)
        }

        fun setActions(actions: List<ActionRow.Action>) {
            this.actions = ArrayList(actions)
            notifyDataSetChanged()
        }
    }

    private inner class ViewHolder(
        private val view: TextView,
        private val iconSize: Int,
    ) : RecyclerView.ViewHolder(view) {

        fun bind(action: ActionRow.Action) {
            action.icon?.let { icon ->
                icon.setBounds(0, 0, iconSize, iconSize)
                // some drawables (edit) does not gets tinted when set to the top of the text
                // with TextView#setCompoundDrawableRelative
                tintIcon(icon, view)
                view.setCompoundDrawablesRelative(icon, null, null, null)
            }
            view.text = action.label ?: ""
            view.setOnClickListener { action.onClicked.run() }
            view.id = action.id
        }

        fun unbind() {
            view.setOnClickListener(null)
        }

        private fun tintIcon(drawable: Drawable, view: TextView) {
            val tintList = view.compoundDrawableTintList ?: return
            drawable.setTintList(tintList)
            view.compoundDrawableTintMode?.let { drawable.setTintMode(it) }
            view.compoundDrawableTintBlendMode?.let { drawable.setTintBlendMode(it) }
        }
    }

    private class MarginDecoration(private val innerMargin: Int, private val outerMargin: Int) :
        ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            val index = parent.getChildAdapterPosition(view)
            val startMargin = if (index == 0) outerMargin else innerMargin
            val endMargin = if (index == state.itemCount - 1) outerMargin else innerMargin

            if (ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                outRect.right = startMargin
                outRect.left = endMargin
            } else {
                outRect.left = startMargin
                outRect.right = endMargin
            }
        }
    }
}
