package com.android.permissioncontroller.safetycenter.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.safetycenter.SafetyCenterIssue
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.StringUtils
import com.android.permissioncontroller.safetycenter.ui.MoreIssuesCardAnimator
import com.android.permissioncontroller.safetycenter.ui.MoreIssuesCardData
import java.text.NumberFormat
import java.time.Duration

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MoreIssuesHeaderView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    init {
        inflate(context, R.layout.view_more_issues, this)
    }

    private val moreIssuesCardAnimator = MoreIssuesCardAnimator()
    private val statusIconView: ImageView by lazyView(R.id.status_icon)
    private val titleView: TextView by lazyView(R.id.title)
    private val expandCollapseLayout: View by lazyView(R.id.widget_frame)
    private val counterView: TextView by lazyView(R.id.widget_title)
    private val expandCollapseIcon: ImageView by lazyView(R.id.widget_icon)
    private var cornerAnimator: ValueAnimator? = null

    fun showExpandableHeader(
        previousData: MoreIssuesCardData?,
        nextData: MoreIssuesCardData,
        title: String,
        @DrawableRes overrideChevronIconResId: Int?,
        onClick: () -> Unit
    ) {
        titleView.text = title
        updateStatusIcon(previousData?.severityLevel, nextData.severityLevel)
        updateExpandCollapseButton(
            previousData?.isExpanded,
            nextData.isExpanded,
            overrideChevronIconResId
        )
        updateIssueCount(previousData?.hiddenIssueCount, nextData.hiddenIssueCount)
        updateBackground(previousData?.isExpanded, nextData.isExpanded)
        setOnClickListener { onClick() }

        val expansionString =
            StringUtils.getIcuPluralsString(
                context,
                R.string.safety_center_more_issues_card_expand_action,
                nextData.hiddenIssueCount
            )
        // Replacing the on-click label to indicate the number of hidden issues. The on-click
        // command is set to null so that it uses the existing expansion behaviour.
        ViewCompat.replaceAccessibilityAction(
            this,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            expansionString,
            null
        )
    }

    fun showStaticHeader(title: String, severityLevel: Int) {
        titleView.text = title
        updateStatusIcon(previousSeverityLevel = null, severityLevel)
        expandCollapseLayout.isVisible = false
        setOnClickListener(null)
        isClickable = false
        setBackgroundResource(android.R.color.transparent)
    }

    private fun updateExpandCollapseButton(
        wasExpanded: Boolean?,
        isExpanded: Boolean,
        @DrawableRes overrideChevronIconResId: Int?
    ) {
        expandCollapseLayout.isVisible = true
        if (overrideChevronIconResId != null) {
            expandCollapseIcon.setImageResource(overrideChevronIconResId)
        } else if (wasExpanded != null && wasExpanded != isExpanded) {
            if (isExpanded) {
                expandCollapseIcon.animate(
                    R.drawable.more_issues_expand_anim,
                    R.drawable.ic_collapse_issues
                )
            } else {
                expandCollapseIcon.animate(
                    R.drawable.more_issues_collapse_anim,
                    R.drawable.ic_expand_issues
                )
            }
        } else {
            expandCollapseIcon.setImageResource(
                if (isExpanded) {
                    R.drawable.ic_collapse_issues
                } else {
                    R.drawable.ic_expand_issues
                }
            )
        }
    }

    private fun updateStatusIcon(previousSeverityLevel: Int?, endSeverityLevel: Int) {
        statusIconView.isVisible = true
        moreIssuesCardAnimator.cancelStatusAnimation(statusIconView)
        if (previousSeverityLevel != null && previousSeverityLevel != endSeverityLevel) {
            moreIssuesCardAnimator.animateStatusIconsChange(
                statusIconView,
                previousSeverityLevel,
                endSeverityLevel,
                selectIconResId(endSeverityLevel)
            )
        } else {
            statusIconView.setImageResource(selectIconResId(endSeverityLevel))
        }
    }

    @DrawableRes
    private fun selectIconResId(severityLevel: Int): Int {
        return when (severityLevel) {
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK -> R.drawable.ic_safety_info
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.ic_safety_recommendation
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING -> R.drawable.ic_safety_warn
            else -> {
                Log.e(TAG, "Unexpected SafetyCenterIssue.IssueSeverityLevel: $severityLevel")
                R.drawable.ic_safety_null_state
            }
        }
    }

    private fun updateIssueCount(previousCount: Int?, endCount: Int) {
        moreIssuesCardAnimator.cancelTextChangeAnimation(counterView)

        val numberFormat = NumberFormat.getInstance()
        val previousText = previousCount?.let(numberFormat::format)
        val newText = numberFormat.format(endCount)
        val animateTextChange =
            !previousText.isNullOrEmpty() && !TextUtils.equals(previousText, newText)

        if (animateTextChange) {
            counterView.text = previousText
            Log.v(TAG, "Starting more issues card text animation")
            moreIssuesCardAnimator.animateChangeText(counterView, newText)
        } else {
            counterView.text = newText
        }
    }

    private fun updateBackground(wasExpanded: Boolean?, isExpanded: Boolean) {
        if (background !is RippleDrawable) {
            setBackgroundResource(R.drawable.safety_center_more_issues_card_background)
        }
        (background?.mutate() as? RippleDrawable)?.let { ripple ->
            val topRadius = context.resources.getDimension(R.dimen.sc_card_corner_radius_large)
            val bottomRadiusStart =
                if (wasExpanded ?: isExpanded) {
                    context.resources.getDimension(R.dimen.sc_card_corner_radius_xsmall)
                } else {
                    topRadius
                }
            val bottomRadiusEnd =
                if (isExpanded) {
                    context.resources.getDimension(R.dimen.sc_card_corner_radius_xsmall)
                } else {
                    topRadius
                }
            val cornerRadii =
                floatArrayOf(
                    topRadius,
                    topRadius,
                    topRadius,
                    topRadius,
                    bottomRadiusStart,
                    bottomRadiusStart,
                    bottomRadiusStart,
                    bottomRadiusStart
                )
            setCornerRadii(ripple, cornerRadii)
            if (bottomRadiusEnd != bottomRadiusStart) {
                cornerAnimator?.removeAllUpdateListeners()
                cornerAnimator?.removeAllListeners()
                cornerAnimator?.cancel()
                val animator =
                    ValueAnimator.ofFloat(bottomRadiusStart, bottomRadiusEnd)
                        .setDuration(CORNER_RADII_ANIMATION_DURATION.toMillis())
                if (isExpanded) {
                    animator.startDelay = CORNER_RADII_ANIMATION_DELAY.toMillis()
                }
                animator.addUpdateListener {
                    cornerRadii.fill(it.animatedValue as Float, fromIndex = 4, toIndex = 8)
                    setCornerRadii(ripple, cornerRadii)
                }
                animator.start()
                cornerAnimator = animator
            }
        }
    }

    private fun setCornerRadii(ripple: RippleDrawable, cornerRadii: FloatArray) {
        for (index in 0 until ripple.numberOfLayers) {
            (ripple.getDrawable(index).mutate() as? GradientDrawable)?.let {
                it.cornerRadii = cornerRadii
            }
        }
    }

    private fun ImageView.animate(@DrawableRes animationRes: Int, @DrawableRes imageRes: Int) {
        (drawable as? AnimatedVectorDrawable)?.clearAnimationCallbacks()
        setImageResource(animationRes)
        (drawable as? AnimatedVectorDrawable)?.apply {
            registerAnimationCallback(
                object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        setImageResource(imageRes)
                    }
                }
            )
            start()
        }
    }

    companion object {
        val TAG: String = MoreIssuesHeaderView::class.java.simpleName
        private val CORNER_RADII_ANIMATION_DELAY = Duration.ofMillis(250)
        private val CORNER_RADII_ANIMATION_DURATION = Duration.ofMillis(120)
    }
}
