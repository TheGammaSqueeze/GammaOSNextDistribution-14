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

package com.android.car.docklib.view

import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.android.car.docklib.DockInterface
import com.android.car.docklib.R
import com.android.car.docklib.data.DockAppItem
import com.google.android.material.imageview.ShapeableImageView
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class DockItemViewHolder(
        private val dockController: DockInterface,
        itemView: View,
        private val userContext: Context,
        private val carMediaManager: CarMediaManager?,
) : RecyclerView.ViewHolder(itemView) {

    companion object {
        private const val TAG = "DockItemViewHolder"
        private val DEBUG = Build.isDebuggable()

        /**
         * Cleanup callback is used to reset/remove any pending views so it should be called after
         * the new item is ready to be shown. This delay ensures new view is ready before the
         * cleanup.
         *
         * todo(b/319285942): Remove fixed timer
         */
        private const val CLEANUP_DELAY = 500L
        private const val MAX_WAIT_TO_COMPUTE_ICON_COLOR_MS = 500L
    }

    private val staticIconStrokeWidth = itemView.resources
            .getDimension(R.dimen.icon_stroke_width_static)
    private val defaultIconColor = itemView.resources.getColor(
            R.color.icon_default_color,
            null // theme
    )
    private val appIcon: ShapeableImageView = itemView.requireViewById(R.id.dock_app_icon)
    private val iconColorExecutor = Executors.newSingleThreadExecutor()
    private val dockDragListener: DockDragListener
    private val dockItemViewController: DockItemViewController
    private var dockItem: DockAppItem? = null
    private var dockItemLongClickListener: DockItemLongClickListener? = null
    private var droppedIconColor: Int = defaultIconColor
    private var iconColorFuture: Future<Int>? = null

    init {
        val typedValue = TypedValue()
        itemView.resources.getValue(
                R.dimen.icon_colorFilter_alpha_excited,
                typedValue,
                true // resolveRefs
        )
        val excitedIconColorFilterAlpha = typedValue.float

        dockItemViewController = DockItemViewController(
            staticIconStrokeWidth,
            dynamicIconStrokeWidth = itemView.resources
                .getDimension(R.dimen.icon_stroke_width_dynamic),
            excitedIconStrokeWidth = itemView.resources
                .getDimension(R.dimen.icon_stroke_width_excited),
            staticIconStrokeColor = itemView.resources.getColor(
                R.color.icon_static_stroke_color,
                null // theme
            ),
            excitedIconStrokeColor = itemView.resources.getColor(
                R.color.icon_excited_stroke_color,
                null // theme
            ),
            defaultIconColor,
            excitedColorFilter = PorterDuffColorFilter(
                Color.argb(excitedIconColorFilterAlpha, 0f, 0f, 0f),
                PorterDuff.Mode.DARKEN
            ),
            excitedIconColorFilterAlpha,
            exciteAnimationDuration = itemView.resources
                .getInteger(R.integer.excite_icon_animation_duration_ms)
        )

        dockDragListener = DockDragListener(
                resources = this.itemView.resources,
                object : DockDragListener.Callback {
                    override fun dropSuccessful(
                            componentName: ComponentName,
                            cleanupCallback: Runnable?
                    ) {
                        (bindingAdapter as? DockAdapter)
                                ?.setCallback(bindingAdapterPosition, cleanupCallback)
                        dockController.appPinned(componentName, bindingAdapterPosition)
                    }

                    override fun dropAnimationsStarting(componentName: ComponentName) {
                        dockItemViewController.setExcited(isExcited = false)
                        // todo(b/320543972): Increase efficiency of dropping
                        iconColorFuture = iconColorExecutor.submit(
                                Callable { dockController.getIconColorWithScrim(componentName) }
                        )
                    }

                    override fun dropAnimationScaleDownComplete(componentName: ComponentName) {
                        droppedIconColor = iconColorFuture?.get(
                                MAX_WAIT_TO_COMPUTE_ICON_COLOR_MS,
                                TimeUnit.MILLISECONDS
                        ) ?: defaultIconColor
                        dockItemViewController.setUpdating(
                            isUpdating = true,
                            updatingColor = droppedIconColor
                        )
                        dockItemViewController.updateViewBasedOnState(appIcon)
                    }

                    override fun dropAnimationComplete(componentName: ComponentName) {
                        dockItemViewController.setUpdating(isUpdating = false, updatingColor = null)
                    }

                    override fun exciteView() {
                        dockItemViewController.setExcited(isExcited = true)
                        dockItemViewController.animateAppIconExcited(appIcon)
                    }

                    override fun resetView() {
                        dockItemViewController.setExcited(isExcited = false)
                        dockItemViewController.animateAppIconExcited(appIcon)
                    }

                    override fun getDropContainerLocation(): Point {
                        val containerLocation = itemView.locationOnScreen
                        return Point(containerLocation[0], containerLocation[1])
                    }

                    override fun getDropLocation(): Point {
                        val iconLocation = appIcon.locationOnScreen
                        return Point(
                                (iconLocation[0] + staticIconStrokeWidth.toInt()),
                                (iconLocation[1] + staticIconStrokeWidth.toInt())
                        )
                    }

                    override fun getDropWidth(): Float {
                        return (appIcon.width.toFloat() - staticIconStrokeWidth * 2)
                    }

                    override fun getDropHeight(): Float {
                        return (appIcon.height.toFloat() - staticIconStrokeWidth * 2)
                    }
                })
    }

    /**
     * @param callback [Runnable] to be called after the new item is bound
     */
    fun bind(dockAppItem: DockAppItem, callback: Runnable? = null) {
        dockItem = dockAppItem
        itemTypeChanged(dockAppItem)
        appIcon.contentDescription = dockAppItem.name
        appIcon.setImageDrawable(dockAppItem.icon)
        appIcon.postDelayed({ callback?.run() }, CLEANUP_DELAY)
        appIcon.setOnClickListener {
            dockController.launchApp(dockAppItem.component, dockAppItem.isMediaApp)
        }
        dockItemLongClickListener = DockItemLongClickListener(
                dockAppItem,
                pinItemClickDelegate = { dockController.appPinned(dockAppItem.id) },
                unpinItemClickDelegate = { dockController.appUnpinned(dockAppItem.id) },
            dockAppItem.component,
            userContext,
            carMediaManager,
            dockController.getMediaServiceComponents()
        )
        appIcon.onLongClickListener = dockItemLongClickListener

        itemView.setOnDragListener(dockDragListener)
    }

    fun itemTypeChanged(dockAppItem: DockAppItem) {
        dockItem = dockAppItem
        when (dockAppItem.type) {
            DockAppItem.Type.DYNAMIC ->
                dockItemViewController.setDynamic(dockAppItem.iconColorWithScrim)

            DockAppItem.Type.STATIC -> dockItemViewController.setStatic()
        }
        dockItemViewController.updateViewBasedOnState(appIcon)
        dockItemLongClickListener?.setDockAppItem(dockAppItem)
    }

    // TODO: b/301484526 Add animation when app icon is changed
}
