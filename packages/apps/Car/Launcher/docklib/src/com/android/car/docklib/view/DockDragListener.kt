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

import android.content.ClipData
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.DragEvent
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.View
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.core.animation.Animator
import androidx.core.animation.PathInterpolator
import androidx.core.animation.PropertyValuesHolder
import androidx.core.animation.ValueAnimator
import com.android.car.docklib.R
import java.util.function.Consumer

/**
 * [View.OnDragListener] for Dock. Receives a drop and moves it to correct location,
 * transformed to the given size. This should be applied to all individual items in the dock that
 * wants to receive a drop.
 */
@OpenForTesting
open class DockDragListener(
    resources: Resources,
    private val callback: Callback
) : View.OnDragListener {
    companion object {
        @VisibleForTesting
        const val APP_ITEM_DRAG_TAG = "com.android.car.launcher.APP_ITEM_DRAG_TAG"

        @VisibleForTesting
        const val PVH_POSITION_X = "PVH_POSITION_X"

        @VisibleForTesting
        const val PVH_POSITION_Y = "PVH_POSITION_Y"

        @VisibleForTesting
        const val PVH_SCALE_X = "PVH_SCALE_X"

        @VisibleForTesting
        const val PVH_SCALE_Y = "PVH_SCALE_Y"

        private const val TAG = "DockDragListener"
        private val DEBUG = Build.isDebuggable()
    }

    private val scaleDownDuration =
        resources.getInteger(R.integer.drop_animation_scale_down_duration_ms).toLong()
    private val scaleUpDuration =
        resources.getInteger(R.integer.drop_animation_scale_up_duration_ms).toLong()
    private val scaleDownWidth =
        resources.getDimension(R.dimen.drop_animation_scale_down_width)
    private var surfaceControl: SurfaceControl? = null

    override fun onDrag(view: View, dragEvent: DragEvent): Boolean {
        when (dragEvent.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                return APP_ITEM_DRAG_TAG.contentEquals(dragEvent.clipDescription?.label)

            DragEvent.ACTION_DRAG_ENTERED -> {
                callback.exciteView()
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                callback.resetView()
                return true
            }

            DragEvent.ACTION_DROP -> {
                val item: ClipData.Item
                try {
                    item = dragEvent.clipData.getItemAt(0)
                    if (item.text == null) throw NullPointerException("ClipData item text is null")
                } catch (e: Exception) {
                    when (e) {
                        is IndexOutOfBoundsException, is NullPointerException -> {
                            if (DEBUG) Log.d(TAG, "No/Invalid clipData sent with the drop: $e")
                            callback.resetView()
                            return false
                        }

                        else -> {
                            throw e
                        }
                    }
                }

                val component: ComponentName? =
                    ComponentName.unflattenFromString(item.text.toString())
                if (component == null) {
                    if (DEBUG) {
                        Log.d(TAG, "Invalid component string sent with drop: " + item.text)
                    }
                    callback.resetView()
                    return false
                }
                if (DEBUG) Log.d(TAG, "Dropped component: $component")

                // todo(b/312718542): hidden api(dragEvent.dragSurface) usage
                dragEvent.dragSurface?.let {
                    surfaceControl = it
                    animateSurfaceIn(it, dragEvent, component)
                    return true
                } ?: run {
                    if (DEBUG) Log.d(TAG, "Could not retrieve the drag surface")
                    // drag is success but animation is not possible since there is no dragSurface
                    callback.dropSuccessful(component)
                    return false
                }
            }
        }
        return false
    }

    /**
     * Animates the surface from where the it was dropped to the final position and size. Also
     * responsible for cleaning up the surface after the animation.
     */
    private fun animateSurfaceIn(
        surfaceControl: SurfaceControl,
        dragEvent: DragEvent,
        component: ComponentName
    ) {
        callback.dropAnimationsStarting(component)
        val dropContainerLocation = callback.getDropContainerLocation()
        // todo(b/312718542): hidden api(offsetX and offsetY) usage
        val fromX: Float = dropContainerLocation.x + (dragEvent.x - dragEvent.offsetX)
        val fromY: Float = dropContainerLocation.y + (dragEvent.y - dragEvent.offsetY)

        val dropLocation = callback.getDropLocation()
        val toFinalX: Float = dropLocation.x.toFloat()
        val toFinalY: Float = dropLocation.y.toFloat()
        val toScaleDownLocationX = toFinalX + scaleDownWidth
        val toScaleDownLocationY = toFinalY + scaleDownWidth

        val toFinalWidth: Float = callback.getDropWidth()
        val toFinalHeight: Float = callback.getDropHeight()
        val toFinalScaleX: Float = toFinalWidth / surfaceControl.width
        val toFinalScaleY: Float = toFinalHeight / surfaceControl.height
        val toScaleDownX: Float =
            ((toFinalWidth - (scaleDownWidth * 2)) / surfaceControl.width).coerceAtLeast(0f)
        val toScaleDownY: Float =
            ((toFinalHeight - (scaleDownWidth * 2)) / surfaceControl.height).coerceAtLeast(0f)
        if (DEBUG && (toScaleDownX <= 0 || toScaleDownY <= 0)) {
            Log.w(
                TAG,
                "Reached negative/zero scale, decrease the value of " +
                        "drop_animation_scale_down_width"
            )
        }

        val scaleDownAnimator = getAnimator(
            surfaceControl,
            fromX = fromX,
            fromY = fromY,
            toX = toScaleDownLocationX,
            toY = toScaleDownLocationY,
            toScaleX = toScaleDownX,
            toScaleY = toScaleDownY,
            animationDuration = scaleDownDuration,
        )
        val scaleUpAnimator = getAnimator(
            surfaceControl,
            fromX = toScaleDownLocationX,
            fromY = toScaleDownLocationY,
            toX = toFinalX,
            toY = toFinalY,
            fromScaleX = toScaleDownX,
            fromScaleY = toScaleDownY,
            toScaleX = toFinalScaleX,
            toScaleY = toFinalScaleY,
            animationDuration = scaleUpDuration,
        )

        scaleDownAnimator.addListener(getAnimatorListener(onAnimationEnd = { isCancelled ->
            if (!isCancelled) {
                callback.dropAnimationScaleDownComplete(component)
                scaleUpAnimator.start()
            }
        }))
        scaleUpAnimator.addListener(getAnimatorListener(onAnimationEnd = { isCancelled ->
            callback.dropAnimationComplete(component)
            if (!isCancelled) callback.dropSuccessful(component, getCleanUpCallback(surfaceControl))
        }))

        scaleDownAnimator.start()
    }

    /**
     * Get the animator responsible for animating the [surfaceControl] from [fromX], [fromY]
     * to its final position [toX], [toY] with correct scale [toScaleX], [toScaleY].
     * Default values are added to make this method easier to test. Generally all parameters are
     * expected to be sent by the caller.
     */
    @VisibleForTesting
    open fun getAnimator(
        surfaceControl: SurfaceControl,
        fromX: Float = 0f,
        fromY: Float = 0f,
        toX: Float = 0f,
        toY: Float = 0f,
        fromScaleX: Float = 1f,
        fromScaleY: Float = 1f,
        toScaleX: Float = 1f,
        toScaleY: Float = 1f,
        animationDuration: Long = 0L,
    ): ValueAnimator {
        if (DEBUG) {
            Log.d(
                TAG,
                "getAnimator{ " +
                        "surfaceControl: $surfaceControl, " +
                        "(fromX: $fromX, fromY: $fromY), " +
                        "(toX: $toX, toY: $toY), " +
                        "(fromScaleX: $fromScaleX, fromScaleY: $fromScaleY), " +
                        "(toScaleX: $toScaleX, toScaleY: $toScaleY) " +
                        "}"
            )
        }

        val pvhX: PropertyValuesHolder =
            PropertyValuesHolder.ofFloat(PVH_POSITION_X, fromX, toX)
        val pvhY: PropertyValuesHolder =
            PropertyValuesHolder.ofFloat(PVH_POSITION_Y, fromY, toY)
        val pvhScaleX =
            PropertyValuesHolder.ofFloat(PVH_SCALE_X, fromScaleX, toScaleX)
        val pvhScaleY =
            PropertyValuesHolder.ofFloat(PVH_SCALE_Y, fromScaleY, toScaleY)

        val animator: ValueAnimator =
            ValueAnimator.ofPropertyValuesHolder(
                pvhX,
                pvhY,
                pvhScaleX,
                pvhScaleY
            )
        animator.setDuration(animationDuration)
        animator.interpolator = PathInterpolator(0f, 0f, 0f, 1f)
        val trx = Transaction()
        animator.addUpdateListener(getAnimatorUpdateListener(surfaceControl, trx))
        animator.addListener(getAnimatorListener { trx.close() })
        return animator
    }

    /**
     * Not expected to be used directly or overridden.
     *
     * @param trx Transaction used to animate the [surfaceControl] in place.
     */
    @VisibleForTesting
    fun getAnimatorUpdateListener(
        surfaceControl: SurfaceControl,
        trx: Transaction
    ): Animator.AnimatorUpdateListener {
        return Animator.AnimatorUpdateListener { updatedAnimation ->
            if (updatedAnimation is ValueAnimator) {
                trx.setPosition(
                    surfaceControl,
                    updatedAnimation.getAnimatedValue(PVH_POSITION_X) as Float,
                    updatedAnimation.getAnimatedValue(PVH_POSITION_Y) as Float
                ).setScale(
                    surfaceControl,
                    updatedAnimation.getAnimatedValue(PVH_SCALE_X) as Float,
                    updatedAnimation.getAnimatedValue(PVH_SCALE_Y) as Float
                ).apply()
            }
        }
    }

    /**
     * @param onAnimationEnd called with boolean(isCancelled) set to false when animation is ended
     * and to true when cancelled.
     */
    @VisibleForTesting
    fun getAnimatorListener(
        onAnimationEnd: Consumer<Boolean>
    ): Animator.AnimatorListener {
        return object : Animator.AnimatorListener {
            private var isCancelled = false
            override fun onAnimationStart(var1: Animator) {
                isCancelled = false
            }

            override fun onAnimationEnd(var1: Animator) {
                if (!isCancelled) {
                    onAnimationEnd.accept(isCancelled)
                }
            }

            override fun onAnimationCancel(var1: Animator) {
                isCancelled = true
                onAnimationEnd.accept(isCancelled)
            }

            override fun onAnimationRepeat(var1: Animator) {
                // no-op
            }
        }
    }

    private fun getCleanUpCallback(surfaceControl: SurfaceControl): () -> Unit {
        return {
            if (DEBUG) Log.d(TAG, "cleanup callback called")
            if (surfaceControl.isValid) {
                if (DEBUG) Log.d(TAG, "Surface is valid")
                val cleanupTrx = Transaction()
                cleanupTrx.hide(surfaceControl)
                cleanupTrx.remove(surfaceControl)
                cleanupTrx.apply()
                cleanupTrx.close()
            }
        }
    }

    /**
     * [DockDragListener] communicates events back and requests data from the caller using
     * this callback.
     */
    interface Callback {
        /**
         * Drop is accepted/successful for the [componentName]
         *
         * @param cleanupCallback [Runnable] to be called when the dropped item is ready/drawn.
         */
        fun dropSuccessful(componentName: ComponentName, cleanupCallback: Runnable? = null) {}

        /**
         * Drop animations about to start.
         */
        fun dropAnimationsStarting(componentName: ComponentName) {}

        /**
         * Drop animation scale down completed.
         */
        fun dropAnimationScaleDownComplete(componentName: ComponentName) {}

        /**
         * Drop animation completed.
         */
        fun dropAnimationComplete(componentName: ComponentName) {}

        /**
         * Excite the view to indicate the item can be dropped in this position when dragged inside
         * the drop bounds.
         */
        fun exciteView() {}

        /**
         * Reset the view after a drop or if the drop failed or if the item is dragged outside the
         * drop bounds.
         */
        fun resetView() {}

        /**
         * Get the location of the container that holds the dropped item
         */
        fun getDropContainerLocation(): Point

        /**
         * Get the final location of the dropped item
         */
        fun getDropLocation(): Point

        /**
         * Get the final width of the dropped item
         */
        fun getDropWidth(): Float

        /**
         * Get the final height of the dropped item
         */
        fun getDropHeight(): Float
    }
}
