/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.shared.preference

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.datasources.AggregationCardInfo
import com.android.healthconnect.controller.permissions.connectedapps.ComparablePreference
import com.android.healthconnect.controller.utils.SystemTimeSource
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.DataSourcesElement
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

class CardContainerPreference
constructor(context: Context, private val timeSource: TimeSource = SystemTimeSource) :
    Preference(context), ComparablePreference {

    private var logger: HealthConnectLogger
    var logName: ElementName = DataSourcesElement.DATA_TOTALS_CARD

    init {
        layoutResource = R.layout.widget_card_preference
        isSelectable = false
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    private val mAggregationCardInfo: MutableList<AggregationCardInfo> = mutableListOf()
    private var container: ConstraintLayout? = null
    private var holder: PreferenceViewHolder? = null
    private var isLoading = false
    private var progressBar: ConstraintLayout? = null

    fun setAggregationCardInfo(aggregationCardInfoList: List<AggregationCardInfo>) {
        mAggregationCardInfo.clear()

        if (aggregationCardInfoList.isEmpty()) {
            return
        }
        // We display a max of 2 cards, so we take the first two list items
        if (aggregationCardInfoList.size > 2) {
            this.mAggregationCardInfo.addAll(aggregationCardInfoList.subList(0, 2))
        } else {
            this.mAggregationCardInfo.addAll(aggregationCardInfoList)
        }
    }

    fun setLoading(isLoading: Boolean) {
        this.isLoading = isLoading
        if (container == null) {
            return
        }

        if (!isLoading) {
            holder?.let { onBindViewHolder(it) }
        } else {
            // Get the current width and height on the card container so we don't flash the screen
            val width = container?.width
            val height = container?.height

            container?.removeAllViews()
            val layoutInflater = LayoutInflater.from(context)
            progressBar =
                layoutInflater.inflate(R.layout.widget_loading_preference, null) as ConstraintLayout

            val layoutParams =
                ConstraintLayout.LayoutParams(
                    width ?: ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    height ?: ConstraintLayout.LayoutParams.WRAP_CONTENT)
            progressBar?.layoutParams = layoutParams
            container?.addView(progressBar)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        this.holder = holder
        container = holder.itemView as ConstraintLayout

        if (!isLoading) {
            setupCards()
        } else {
            setLoading(true)
        }

        logger.logImpression(logName)
    }

    private fun setupCards() {

        if (container == null) {
            return
        }

        if (this.mAggregationCardInfo.isEmpty() || this.mAggregationCardInfo.size > 2) {
            return
        }

        if (mAggregationCardInfo.size == 1) {
            val card = addSingleLargeCard(mAggregationCardInfo[0])
            removeAllChildrenExcept(container, card)
        } else {

            // Add both types of cards to the container (they will be invisible)
            val (firstSmallCard, secondSmallCard) =
                addTwoSmallCards(mAggregationCardInfo[0], mAggregationCardInfo[1])

            val (firstLargeCard, secondLargeCard) =
                addTwoLargeCards(mAggregationCardInfo[0], mAggregationCardInfo[1])

            val firstCardText = firstSmallCard.findViewById<TextView>(R.id.card_title_number)
            val secondCardText = secondSmallCard.findViewById<TextView>(R.id.card_title_number)
            val firstCardDate = firstSmallCard.findViewById<TextView>(R.id.card_date)
            val secondCardDate = secondSmallCard.findViewById<TextView>(R.id.card_date)

            // Check for the ellipsized text after the first card has been drawn
            // If there is ellipsized text, remove the small cards and set the large cards to
            // visible
            // If there is no ellipsized text, remove the large cards and set the small cards to
            // visible
            firstSmallCard.post {
                if (isTextEllipsized(firstCardText) ||
                    isTextEllipsized(secondCardText) ||
                    isTextEllipsized(firstCardDate) ||
                    isTextEllipsized(secondCardDate)) {
                    container?.removeView(firstSmallCard)
                    container?.removeView(secondSmallCard)
                    container?.removeView(progressBar)
                    firstLargeCard.visibility = View.VISIBLE
                    secondLargeCard.visibility = View.VISIBLE
                } else {
                    container?.removeView(firstLargeCard)
                    container?.removeView(secondLargeCard)
                    container?.removeView(progressBar)
                    firstSmallCard.visibility = View.VISIBLE
                    secondSmallCard.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Adds a single large [AggregationDataCard] to the provided container. This should be called
     * when there is only one available aggregate.
     */
    private fun addSingleLargeCard(cardInfo: AggregationCardInfo): AggregationDataCard {
        val singleCard =
            AggregationDataCard(
                context, null, AggregationDataCard.CardTypeEnum.LARGE_CARD, cardInfo, timeSource)
        singleCard.id = View.generateViewId()
        val layoutParams =
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT)
        singleCard.layoutParams = layoutParams
        container?.addView(singleCard)
        return singleCard
    }

    /**
     * Adds two small [AggregationDataCard]s to the provided container stacked horizontally. This
     * should be called when there are two available aggregates.
     */
    private fun addTwoSmallCards(
        firstCardInfo: AggregationCardInfo,
        secondCardInfo: AggregationCardInfo
    ): Pair<AggregationDataCard, AggregationDataCard> {
        // Construct the first card
        val firstCard = constructSmallCard(firstCardInfo, addMargin = true)

        // Construct the second card
        val secondCard = constructSmallCard(secondCardInfo, addMargin = false)

        firstCard.visibility = View.INVISIBLE
        secondCard.visibility = View.INVISIBLE
        container?.addView(firstCard)
        container?.addView(secondCard)

        applySmallCardConstraints(firstCard, secondCard)

        return Pair(firstCard, secondCard)
    }

    private fun applySmallCardConstraints(
        firstCard: AggregationDataCard,
        secondCard: AggregationDataCard
    ) {
        // Add the constraints between the two cards in their ConstraintLayout container
        val constraintSet = ConstraintSet()
        constraintSet.clone(container)

        // Constraints for the first card
        constraintSet.connect(
            firstCard.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(
            firstCard.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(
            firstCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(firstCard.id, ConstraintSet.END, secondCard.id, ConstraintSet.START)

        // Constraints for the second card
        constraintSet.connect(secondCard.id, ConstraintSet.START, firstCard.id, ConstraintSet.END)
        constraintSet.connect(
            secondCard.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(
            secondCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(
            secondCard.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        constraintSet.applyTo(container)
    }

    private fun constructSmallCard(
        cardInfo: AggregationCardInfo,
        addMargin: Boolean
    ): AggregationDataCard {
        val card =
            AggregationDataCard(
                context, null, AggregationDataCard.CardTypeEnum.SMALL_CARD, cardInfo, timeSource)
        card.id = View.generateViewId()
        val layoutParams =
            ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT)

        if (addMargin) {
            // Set a right margin of 16dp for the first (leftmost) card
            val marginInDp = 16
            val marginInPx = (marginInDp * context.resources.displayMetrics.density).toInt()
            layoutParams.setMargins(0, 0, marginInPx, 0)
        }

        card.layoutParams = layoutParams

        return card
    }

    /**
     * Adds two large [AggregationDataCard]s to the provided container stacked vertically. This
     * should be called when there are two available aggregates and the text is too large to fit
     * into small cards.
     */
    private fun addTwoLargeCards(
        firstCardInfo: AggregationCardInfo,
        secondCardInfo: AggregationCardInfo
    ): Pair<AggregationDataCard, AggregationDataCard> {
        // Construct the first card
        val firstLongCard = constructLargeCard(firstCardInfo, addMargin = true)
        // Construct the second card
        val secondLongCard = constructLargeCard(secondCardInfo, addMargin = false)

        firstLongCard.visibility = View.GONE
        secondLongCard.visibility = View.GONE

        container?.addView(firstLongCard)
        container?.addView(secondLongCard)

        applyLargeCardConstraints(firstLongCard, secondLongCard)

        return Pair(firstLongCard, secondLongCard)
    }

    private fun applyLargeCardConstraints(
        firstCard: AggregationDataCard,
        secondCard: AggregationDataCard
    ) {
        // Add the constraints between the two cards in their ConstraintLayout container
        val constraintSet = ConstraintSet()
        constraintSet.clone(container)

        // Constraints for the first card
        constraintSet.connect(
            firstCard.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(
            firstCard.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(firstCard.id, ConstraintSet.BOTTOM, secondCard.id, ConstraintSet.TOP)
        constraintSet.connect(
            firstCard.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        // Constraints for the first card
        constraintSet.connect(secondCard.id, ConstraintSet.TOP, firstCard.id, ConstraintSet.BOTTOM)
        constraintSet.connect(
            secondCard.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(
            secondCard.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(
            secondCard.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        constraintSet.applyTo(container)
    }

    private fun constructLargeCard(
        cardInfo: AggregationCardInfo,
        addMargin: Boolean
    ): AggregationDataCard {
        val largeCard =
            AggregationDataCard(
                context, null, AggregationDataCard.CardTypeEnum.LARGE_CARD, cardInfo, timeSource)
        largeCard.id = View.generateViewId()

        val layoutParams =
            ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT)

        if (addMargin) {
            // Set a bottom margin of 16dp for the first (topmost) card
            val marginInDp = 16
            val marginInPx = (marginInDp * context.resources.displayMetrics.density).toInt()
            layoutParams.setMargins(0, 0, 0, marginInPx)
        }

        largeCard.layoutParams = layoutParams
        return largeCard
    }

    /** Returns true if the provided textView is ellipsized (...) */
    private fun isTextEllipsized(textView: TextView): Boolean {
        if (textView.layout != null) {
            val lines = textView.layout.lineCount
            if (lines > 0) {
                if (textView.layout.getEllipsisCount(lines - 1) > 0) {
                    return true
                }
            }
        }
        return false
    }

    private fun removeAllChildrenExcept(container: ConstraintLayout?, childToKeep: View) {
        container?.let {
            for (i in it.childCount - 1 downTo 0) {
                val currentChild = it.getChildAt(i)
                if (currentChild != childToKeep) {
                    it.removeViewAt(i)
                }
            }
        }
    }

    override fun hasSameContents(preference: Preference): Boolean {
        return preference is CardContainerPreference &&
            preference.mAggregationCardInfo == this.mAggregationCardInfo
    }

    override fun isSameItem(preference: Preference): Boolean {
        return preference is CardContainerPreference && this == preference
    }
}
