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
package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import com.android.permissioncontroller.R

/**
 * Determines the correct background drawable for a given element in the Safety Center card list.
 *
 * This class helps transform a flat list of elements (which are typically Preferences in the
 * PreferenceScreen's recycler view) into a visually grouped list of cards by providing the correct
 * background drawable for each element based on both its position in the flat list of elements and
 * its conceptual position in the card list. While Preferences have a nested XML structure, with
 * Preferences nested inside of PreferenceGroups, they are displayed as a flat list of views, with
 * the PreferenceGroup's view inserted as a header above its constituent preference's views.
 *
 * A list is a group conceptually-related of cards. The top card in the list has large top corners
 * and the bottom card in the list has large bottom corners. Corners between cards in the list are
 * small. In the Safety Center, the entry list is a single list.
 *
 * A card is a group of one or more elements that appear as a single visual card, without corners
 * between its constituent elements. In the Safety Center, a single expanded entry list group is a
 * single card composed of a list of separate preferences (one for the header and one for each entry
 * in the group).
 */
internal enum class PositionInCardList(val backgroundDrawableResId: Int) {
    INSIDE_GROUP(R.drawable.safety_group_entry_background),
    LIST_START_END(R.drawable.safety_entity_top_large_bottom_large_background),
    LIST_START(R.drawable.safety_entity_top_large_bottom_flat_background),
    LIST_START_CARD_END(R.drawable.safety_entity_top_large_bottom_small_background),
    CARD_START(R.drawable.safety_entity_top_small_bottom_flat_background),
    CARD_START_END(R.drawable.safety_entity_top_small_bottom_small_background),
    CARD_START_LIST_END(R.drawable.safety_entity_top_small_bottom_large_background),
    CARD_ELEMENT(R.drawable.safety_entity_top_flat_bottom_flat_background),
    CARD_END(R.drawable.safety_entity_top_flat_bottom_small_background),
    LIST_END(R.drawable.safety_entity_top_flat_bottom_large_background);

    fun getTopMargin(context: Context): Int =
        when (this) {
            CARD_START,
            CARD_START_END,
            CARD_START_LIST_END -> context.resources.getDimensionPixelSize(R.dimen.sc_card_margin)
            LIST_START,
            LIST_START_CARD_END,
            LIST_START_END -> context.resources.getDimensionPixelSize(R.dimen.sc_list_margin_top)
            else -> 0
        }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun calculate(
            isListStart: Boolean,
            isListEnd: Boolean,
            isCardStart: Boolean = !isListStart,
            isCardEnd: Boolean = !isListEnd
        ): PositionInCardList =
            if (isListStart && isListEnd) {
                LIST_START_END
            } else if (isListStart && isCardEnd) {
                LIST_START_CARD_END
            } else if (isListEnd && isCardStart) {
                CARD_START_LIST_END
            } else if (isCardStart && isCardEnd) {
                CARD_START_END
            } else if (isListStart) {
                LIST_START
            } else if (isListEnd) {
                LIST_END
            } else if (isCardStart) {
                CARD_START
            } else if (isCardEnd) {
                CARD_END
            } else {
                CARD_ELEMENT
            }
    }
}
