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

package com.android.intentresolver.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.util.PluralsMessageFormatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.intentresolver.R
import com.android.intentresolver.util.throttle
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TRANSITION_NAME = "screenshot_preview_image"
private const val PLURALS_COUNT = "count"
private const val ADAPTER_UPDATE_INTERVAL_MS = 150L
private const val MIN_ASPECT_RATIO = 0.4f
private const val MIN_ASPECT_RATIO_STRING = "2:5"
private const val MAX_ASPECT_RATIO = 2.5f
private const val MAX_ASPECT_RATIO_STRING = "5:2"

private typealias CachingImageLoader = suspend (Uri, Boolean) -> Bitmap?

class ScrollableImagePreviewView : RecyclerView, ImagePreviewView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        context
            .obtainStyledAttributes(attrs, R.styleable.ScrollableImagePreviewView, defStyleAttr, 0)
            .use { a ->
                var innerSpacing =
                    a.getDimensionPixelSize(
                        R.styleable.ScrollableImagePreviewView_itemInnerSpacing,
                        -1
                    )
                if (innerSpacing < 0) {
                    innerSpacing =
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                3f,
                                context.resources.displayMetrics
                            )
                            .toInt()
                }
                outerSpacing =
                    a.getDimensionPixelSize(
                        R.styleable.ScrollableImagePreviewView_itemOuterSpacing,
                        -1
                    )
                if (outerSpacing < 0) {
                    outerSpacing =
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                16f,
                                context.resources.displayMetrics
                            )
                            .toInt()
                }
                super.addItemDecoration(SpacingDecoration(innerSpacing, outerSpacing))

                maxWidthHint =
                    a.getDimensionPixelSize(R.styleable.ScrollableImagePreviewView_maxWidthHint, -1)
            }
        val itemAnimator = ItemAnimator()
        super.setItemAnimator(itemAnimator)
        super.setAdapter(Adapter(context, itemAnimator.getAddDuration()))
    }

    private var batchLoader: BatchPreviewLoader? = null
    private val previewAdapter
        get() = adapter as Adapter

    /**
     * A hint about the maximum width this view can grow to, this helps to optimize preview loading.
     */
    var maxWidthHint: Int = -1
    private var requestedHeight: Int = 0
    private var isMeasured = false
    private var maxAspectRatio = MAX_ASPECT_RATIO
    private var maxAspectRatioString = MAX_ASPECT_RATIO_STRING
    private var outerSpacing: Int = 0

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (!isMeasured) {
            isMeasured = true
            updateMaxWidthHint(widthSpec)
            updateMaxAspectRatio()
            maybeLoadAspectRatios()
        }
    }

    private fun updateMaxWidthHint(widthSpec: Int) {
        if (maxWidthHint > 0) return
        if (View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.UNSPECIFIED) {
            maxWidthHint = View.MeasureSpec.getSize(widthSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setOverScrollMode(
            if (areAllChildrenVisible) View.OVER_SCROLL_NEVER else View.OVER_SCROLL_ALWAYS
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        batchLoader?.totalItemCount?.let(previewAdapter::reset)
        maybeLoadAspectRatios()
    }

    override fun onDetachedFromWindow() {
        batchLoader?.cancel()
        super.onDetachedFromWindow()
    }

    override fun setTransitionElementStatusCallback(callback: TransitionElementStatusCallback?) {
        previewAdapter.transitionStatusElementCallback = callback
    }

    override fun getTransitionView(): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val vh = getChildViewHolder(child)
            if (vh is PreviewViewHolder && vh.image.transitionName != null) return child
        }
        return null
    }

    override fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        error("This method is not supported")
    }

    override fun setItemAnimator(animator: RecyclerView.ItemAnimator?) {
        error("This method is not supported")
    }

    fun setImageLoader(imageLoader: CachingImageLoader) {
        previewAdapter.imageLoader = imageLoader
    }

    fun setLoading(totalItemCount: Int) {
        previewAdapter.reset(totalItemCount)
    }

    fun setPreviews(previews: Flow<Preview>, totalItemCount: Int) {
        previewAdapter.reset(totalItemCount)
        batchLoader?.cancel()
        batchLoader =
            BatchPreviewLoader(
                previewAdapter.imageLoader ?: error("Image loader is not set"),
                previews,
                totalItemCount,
                onUpdate = previewAdapter::addPreviews,
                onCompletion = {
                    batchLoader = null
                    if (!previewAdapter.hasPreviews) {
                        onNoPreviewCallback?.run()
                    }
                    previewAdapter.markLoaded()
                }
            )
        maybeLoadAspectRatios()
    }

    private fun maybeLoadAspectRatios() {
        if (isMeasured && isAttachedToWindow()) {
            batchLoader?.let { it.loadAspectRatios(getMaxWidth(), this::updatePreviewSize) }
        }
    }

    var onNoPreviewCallback: Runnable? = null

    private fun getMaxWidth(): Int =
        when {
            maxWidthHint > 0 -> maxWidthHint
            isLaidOut -> width
            else -> measuredWidth
        }

    private fun updateMaxAspectRatio() {
        val padding = outerSpacing * 2
        val w = maxOf(padding, getMaxWidth() - padding)
        val h = if (isLaidOut) height else measuredHeight
        if (w > 0 && h > 0) {
            maxAspectRatio =
                (w.toFloat() / h.toFloat()).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
            maxAspectRatioString =
                when {
                    maxAspectRatio <= MIN_ASPECT_RATIO -> MIN_ASPECT_RATIO_STRING
                    maxAspectRatio >= MAX_ASPECT_RATIO -> MAX_ASPECT_RATIO_STRING
                    else -> "$w:$h"
                }
        }
    }

    /**
     * Sets [preview]'s aspect ratio based on the preview image size.
     *
     * @return adjusted preview width
     */
    private fun updatePreviewSize(preview: Preview, width: Int, height: Int): Int {
        val effectiveHeight = if (isLaidOut) height else measuredHeight
        return if (width <= 0 || height <= 0) {
            preview.aspectRatioString = "1:1"
            effectiveHeight
        } else {
            val aspectRatio =
                (width.toFloat() / height.toFloat()).coerceIn(MIN_ASPECT_RATIO, maxAspectRatio)
            preview.aspectRatioString =
                when {
                    aspectRatio <= MIN_ASPECT_RATIO -> MIN_ASPECT_RATIO_STRING
                    aspectRatio >= maxAspectRatio -> maxAspectRatioString
                    else -> "$width:$height"
                }
            (effectiveHeight * aspectRatio).toInt()
        }
    }

    class Preview
    internal constructor(
        val type: PreviewType,
        val uri: Uri,
        val editAction: Runnable?,
        internal var aspectRatioString: String
    ) {
        constructor(
            type: PreviewType,
            uri: Uri,
            editAction: Runnable?
        ) : this(type, uri, editAction, "1:1")
    }

    enum class PreviewType {
        Image,
        Video,
        File
    }

    private class Adapter(
        private val context: Context,
        private val fadeInDurationMs: Long,
    ) : RecyclerView.Adapter<ViewHolder>() {
        private val previews = ArrayList<Preview>()
        private val imagePreviewDescription =
            context.resources.getString(R.string.image_preview_a11y_description)
        private val videoPreviewDescription =
            context.resources.getString(R.string.video_preview_a11y_description)
        private val filePreviewDescription =
            context.resources.getString(R.string.file_preview_a11y_description)
        var imageLoader: CachingImageLoader? = null
        private var firstImagePos = -1
        private var totalItemCount: Int = 0

        private var isLoading = false
        private val hasOtherItem
            get() = previews.size < totalItemCount
        val hasPreviews: Boolean
            get() = previews.isNotEmpty()

        var transitionStatusElementCallback: TransitionElementStatusCallback? = null

        fun reset(totalItemCount: Int) {
            firstImagePos = -1
            previews.clear()
            this.totalItemCount = maxOf(0, totalItemCount)
            isLoading = this.totalItemCount > 0
            notifyDataSetChanged()
        }

        fun markLoaded() {
            if (!isLoading) return
            isLoading = false
            if (hasOtherItem) {
                notifyItemChanged(previews.size)
            } else {
                notifyItemRemoved(previews.size)
            }
        }

        fun addPreviews(newPreviews: Collection<Preview>) {
            if (newPreviews.isEmpty()) return
            val insertPos = previews.size
            val hadOtherItem = hasOtherItem
            val oldItemCount = getItemCount()
            previews.addAll(newPreviews)
            if (firstImagePos < 0) {
                val pos = newPreviews.indexOfFirst { it.type == PreviewType.Image }
                if (pos >= 0) firstImagePos = insertPos + pos
            }
            if (insertPos == 0) {
                if (oldItemCount > 0) {
                    notifyItemRangeRemoved(0, oldItemCount)
                }
                notifyItemRangeInserted(insertPos, getItemCount())
            } else {
                notifyItemRangeInserted(insertPos, newPreviews.size)
                when {
                    hadOtherItem && !hasOtherItem -> {
                        notifyItemRemoved(previews.size)
                    }
                    !hadOtherItem && hasOtherItem -> {
                        notifyItemInserted(previews.size)
                    }
                    else -> notifyItemChanged(previews.size)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, itemType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(itemType, parent, false)
            return when (itemType) {
                R.layout.image_preview_other_item -> OtherItemViewHolder(view)
                R.layout.image_preview_loading_item -> LoadingItemViewHolder(view)
                else ->
                    PreviewViewHolder(
                        view,
                        imagePreviewDescription,
                        videoPreviewDescription,
                        filePreviewDescription,
                    )
            }
        }

        override fun getItemCount(): Int = previews.size + if (isLoading || hasOtherItem) 1 else 0

        override fun getItemViewType(position: Int): Int =
            when {
                position == previews.size && isLoading -> R.layout.image_preview_loading_item
                position == previews.size -> R.layout.image_preview_other_item
                else -> R.layout.image_preview_image_item
            }

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            when (vh) {
                is OtherItemViewHolder -> vh.bind(totalItemCount - previews.size)
                is LoadingItemViewHolder -> vh.bind()
                is PreviewViewHolder ->
                    vh.bind(
                        previews[position],
                        imageLoader ?: error("ImageLoader is missing"),
                        fadeInDurationMs,
                        isSharedTransitionElement = position == firstImagePos,
                        previewReadyCallback =
                            if (
                                position == firstImagePos && transitionStatusElementCallback != null
                            ) {
                                this::onTransitionElementReady
                            } else {
                                null
                            }
                    )
            }
        }

        override fun onViewRecycled(vh: ViewHolder) {
            vh.unbind()
        }

        override fun onFailedToRecycleView(vh: ViewHolder): Boolean {
            vh.unbind()
            return super.onFailedToRecycleView(vh)
        }

        private fun onTransitionElementReady(name: String) {
            transitionStatusElementCallback?.apply {
                onTransitionElementReady(name)
                onAllTransitionElementsReady()
            }
            transitionStatusElementCallback = null
        }
    }

    private sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun unbind()
    }

    private class PreviewViewHolder(
        view: View,
        private val imagePreviewDescription: String,
        private val videoPreviewDescription: String,
        private val filePreviewDescription: String,
    ) : ViewHolder(view) {
        val image = view.requireViewById<ImageView>(R.id.image)
        private val badgeFrame = view.requireViewById<View>(R.id.badge_frame)
        private val badge = view.requireViewById<ImageView>(R.id.badge)
        private val editActionContainer = view.findViewById<View?>(R.id.edit)
        private var scope: CoroutineScope? = null

        fun bind(
            preview: Preview,
            imageLoader: CachingImageLoader,
            fadeInDurationMs: Long,
            isSharedTransitionElement: Boolean,
            previewReadyCallback: ((String) -> Unit)?
        ) {
            image.setImageDrawable(null)
            image.alpha = 1f
            image.clearAnimation()
            (image.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
                params.dimensionRatio = preview.aspectRatioString
            }
            image.transitionName =
                if (isSharedTransitionElement) {
                    TRANSITION_NAME
                } else {
                    null
                }
            when (preview.type) {
                PreviewType.Image -> {
                    itemView.contentDescription = imagePreviewDescription
                    badgeFrame.visibility = View.GONE
                }
                PreviewType.Video -> {
                    itemView.contentDescription = videoPreviewDescription
                    badge.setImageResource(R.drawable.ic_file_video)
                    badgeFrame.visibility = View.VISIBLE
                }
                else -> {
                    itemView.contentDescription = filePreviewDescription
                    badge.setImageResource(R.drawable.chooser_file_generic)
                    badgeFrame.visibility = View.VISIBLE
                }
            }
            preview.editAction?.also { onClick ->
                editActionContainer?.apply {
                    setOnClickListener { onClick.run() }
                    visibility = View.VISIBLE
                }
            }
            resetScope().launch {
                loadImage(preview, imageLoader)
                if (preview.type == PreviewType.Image && previewReadyCallback != null) {
                    image.waitForPreDraw()
                    previewReadyCallback(TRANSITION_NAME)
                } else if (image.isAttachedToWindow()) {
                    fadeInPreview(fadeInDurationMs)
                }
            }
        }

        private suspend fun loadImage(preview: Preview, imageLoader: CachingImageLoader) {
            val bitmap =
                runCatching {
                        // it's expected for all loading/caching optimizations to be implemented by
                        // the loader
                        imageLoader(preview.uri, true)
                    }
                    .getOrNull()
            image.setImageBitmap(bitmap)
        }

        private suspend fun fadeInPreview(durationMs: Long) =
            suspendCancellableCoroutine { continuation ->
                val animation =
                    AlphaAnimation(0f, 1f).apply {
                        duration = durationMs
                        interpolator = DecelerateInterpolator()
                        setAnimationListener(
                            object : AnimationListener {
                                override fun onAnimationStart(animation: Animation?) = Unit
                                override fun onAnimationRepeat(animation: Animation?) = Unit

                                override fun onAnimationEnd(animation: Animation?) {
                                    continuation.resumeWith(Result.success(Unit))
                                }
                            }
                        )
                    }
                image.startAnimation(animation)
                continuation.invokeOnCancellation {
                    image.clearAnimation()
                    image.alpha = 1f
                }
            }

        private fun resetScope(): CoroutineScope =
            CoroutineScope(Dispatchers.Main.immediate).also {
                scope?.cancel()
                scope = it
            }

        override fun unbind() {
            scope?.cancel()
            scope = null
        }
    }

    private class OtherItemViewHolder(view: View) : ViewHolder(view) {
        private val label = view.requireViewById<TextView>(R.id.label)

        fun bind(count: Int) {
            label.text =
                PluralsMessageFormatter.format(
                    itemView.context.resources,
                    mapOf(PLURALS_COUNT to count),
                    R.string.other_files
                )
        }

        override fun unbind() = Unit
    }

    private class LoadingItemViewHolder(view: View) : ViewHolder(view) {
        fun bind() = Unit
        override fun unbind() = Unit
    }

    private class SpacingDecoration(private val innerSpacing: Int, private val outerSpacing: Int) :
        ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            val itemCount = parent.adapter?.itemCount ?: return
            val pos = parent.getChildAdapterPosition(view)
            var startMargin = if (pos == 0) outerSpacing else innerSpacing
            var endMargin = if (pos == itemCount - 1) outerSpacing else 0

            if (ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                outRect.set(endMargin, 0, startMargin, 0)
            } else {
                outRect.set(startMargin, 0, endMargin, 0)
            }
        }
    }

    /**
     * ItemAnimator to handle a special case of addng first image items into the view. The view is
     * used with wrap_content width spec thus after adding the first views it, generally, changes
     * its size and position breaking the animation. This class handles that by preserving loading
     * idicator position in this special case.
     */
    private inner class ItemAnimator() : DefaultItemAnimator() {
        private var animatedVH: ViewHolder? = null
        private var originalTranslation = 0f

        override fun recordPreLayoutInformation(
            state: State,
            viewHolder: RecyclerView.ViewHolder,
            changeFlags: Int,
            payloads: MutableList<Any>
        ): ItemHolderInfo {
            return super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads).let {
                holderInfo ->
                if (viewHolder is LoadingItemViewHolder && getChildCount() == 1) {
                    LoadingItemHolderInfo(holderInfo, parentLeft = left)
                } else {
                    holderInfo
                }
            }
        }

        override fun animateDisappearance(
            viewHolder: RecyclerView.ViewHolder,
            preLayoutInfo: ItemHolderInfo,
            postLayoutInfo: ItemHolderInfo?
        ): Boolean {
            if (viewHolder is LoadingItemViewHolder && preLayoutInfo is LoadingItemHolderInfo) {
                val view = viewHolder.itemView
                animatedVH = viewHolder
                originalTranslation = view.getTranslationX()
                view.setTranslationX(
                    (preLayoutInfo.parentLeft - left + preLayoutInfo.left).toFloat() - view.left
                )
            }
            return super.animateDisappearance(viewHolder, preLayoutInfo, postLayoutInfo)
        }

        override fun onRemoveFinished(viewHolder: RecyclerView.ViewHolder) {
            if (animatedVH === viewHolder) {
                viewHolder.itemView.setTranslationX(originalTranslation)
                animatedVH = null
            }
            super.onRemoveFinished(viewHolder)
        }

        private inner class LoadingItemHolderInfo(
            holderInfo: ItemHolderInfo,
            val parentLeft: Int,
        ) : ItemHolderInfo() {
            init {
                left = holderInfo.left
                top = holderInfo.top
                right = holderInfo.right
                bottom = holderInfo.bottom
                changeFlags = holderInfo.changeFlags
            }
        }
    }

    @VisibleForTesting
    class BatchPreviewLoader(
        private val imageLoader: CachingImageLoader,
        private val previews: Flow<Preview>,
        val totalItemCount: Int,
        private val onUpdate: (List<Preview>) -> Unit,
        private val onCompletion: () -> Unit,
    ) {
        private var scope: CoroutineScope = createScope()

        private fun createScope() = CoroutineScope(Dispatchers.Main.immediate)

        fun cancel() {
            scope.cancel()
            scope = createScope()
        }

        fun loadAspectRatios(maxWidth: Int, previewSizeUpdater: (Preview, Int, Int) -> Int) {
            val previewInfos = ArrayList<PreviewWidthInfo>(totalItemCount)
            var blockStart = 0 // inclusive
            var blockEnd = 0 // exclusive

            // replay 2 items to guarantee that we'd get at least one update
            val reportFlow = MutableSharedFlow<Any>(replay = 2)
            val updateEvent = Any()
            val completedEvent = Any()

            // collects updates from [reportFlow] throttling adapter updates;
            scope.launch(Dispatchers.Main) {
                reportFlow
                    .takeWhile { it !== completedEvent }
                    .throttle(ADAPTER_UPDATE_INTERVAL_MS)
                    .collect {
                        val updates = ArrayList<Preview>(blockEnd - blockStart)
                        while (blockStart < blockEnd) {
                            if (previewInfos[blockStart].width > 0) {
                                updates.add(previewInfos[blockStart].preview)
                            }
                            blockStart++
                        }
                        if (updates.isNotEmpty()) {
                            onUpdate(updates)
                        }
                    }
                onCompletion()
            }

            // Collects [previews] flow and loads aspect ratios, emits updates into [reportFlow]
            // when a next sequential block of preview aspect ratios is loaded: initially emits when
            // enough preview elements is loaded to fill the viewport.
            scope.launch {
                var blockWidth = 0
                var isFirstBlock = true

                val jobs = ArrayList<Job>()
                previews.collect { preview ->
                    val i = previewInfos.size
                    val pair = PreviewWidthInfo(preview)
                    previewInfos.add(pair)

                    val job = launch {
                        pair.width =
                            runCatching {
                                    // TODO: decide on adding a timeout. The worst case I can
                                    //  imagine is one of the first images never loads so we never
                                    //  fill the initial viewport and does not show the previews at
                                    //  all.
                                    imageLoader(preview.uri, isFirstBlock)?.let { bitmap ->
                                        previewSizeUpdater(preview, bitmap.width, bitmap.height)
                                    }
                                        ?: 0
                                }
                                .getOrDefault(0)

                        if (i == blockEnd) {
                            while (
                                blockEnd < previewInfos.size && previewInfos[blockEnd].width >= 0
                            ) {
                                blockWidth += previewInfos[blockEnd].width
                                blockEnd++
                            }
                            if (isFirstBlock && blockWidth >= maxWidth) {
                                isFirstBlock = false
                            }
                            if (!isFirstBlock) {
                                reportFlow.emit(updateEvent)
                            }
                        }
                    }
                    jobs.add(job)
                }
                jobs.joinAll()
                // in case all previews have failed to load
                reportFlow.emit(updateEvent)
                reportFlow.emit(completedEvent)
            }
        }
    }

    private class PreviewWidthInfo(val preview: Preview) {
        // -1 encodes that the preview has not been processed,
        // 0 means failed, > 0 is a preview width
        var width: Int = -1
    }
}
