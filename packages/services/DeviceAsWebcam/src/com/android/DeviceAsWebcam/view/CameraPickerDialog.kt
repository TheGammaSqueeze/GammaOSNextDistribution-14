/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.DeviceAsWebcam.view

import android.app.AlertDialog
import android.app.Dialog
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.DeviceAsWebcam.CameraCategory
import com.android.DeviceAsWebcam.CameraId
import com.android.DeviceAsWebcam.CameraInfo
import com.android.DeviceAsWebcam.R
import java.util.function.Consumer

/**
 * Class to create an AlertDialog for the camera picker. This class handles the AlertDialog's
 * lifecycle.
 */
class CameraPickerDialog(private val mOnItemSelected: Consumer<CameraId>) : DialogFragment() {
    // Map from lens facing to ListItems
    private var mDialogItems: Map<Int, List<ListItem>> = mapOf()

    // List Adapter to be used by the Dialog's Recycler View. This lives as long
    // as the activity so we don't re-populate the data every time the dialog is created.
    private val mCameraPickerListAdapter: CameraPickerListAdapter =
        CameraPickerListAdapter(listOf(), mOnItemSelected)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(activity!!).setCancelable(true).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val customView =
            dialog.layoutInflater.inflate(R.layout.camera_picker_dialog, /*root=*/ null)

        val containerView: View = customView.findViewById(R.id.selector_container_view)!!
        containerView.background.alpha = (0.75 * 0xFF).toInt()

        val recyclerView: RecyclerView = customView.findViewById(R.id.camera_selector_view)!!
        recyclerView.layoutManager = LinearLayoutManager(dialog.context)
        recyclerView.adapter = mCameraPickerListAdapter
        dialog.setView(customView)
        return dialog
    }

    /**
     * Updates the dialog's backing data structures with a new list of available cameras.
     * This will clobber any previous state so callers should send the full list every time.
     */
    fun updateAvailableCameras(availableCameras: List<ListItem>, selectedCameraId: CameraId) {
        // create a map by grouping the entries by their lensFacing values.
        mDialogItems = availableCameras.groupBy({ it.lensFacing }, { it })
        updateSelectedCamera(selectedCameraId)
    }

    /**
     * Updates the currently selected camera and updates the UI if necessary
     */
    fun updateSelectedCamera(selectedCameraId: CameraId) {
        mDialogItems.forEach { entry ->
            entry.value.find { it.isSelected }?.isSelected = false
            entry.value.find { it.cameraId == selectedCameraId }?.isSelected = true
        }

        updateListAdapter()
    }

    /**
     * Re-generates the information required by {@link CameraPickerListAdapter} from
     * {@link #mDialogItems}, invalidating the recycler view if necessary.
     */
    private fun updateListAdapter() {
        val lensFacingOrder =
            listOf(CameraCharacteristics.LENS_FACING_BACK, CameraCharacteristics.LENS_FACING_FRONT)

        val dialogViewListItems: MutableList<CameraPickerListAdapter.ViewListItem> = mutableListOf()
        for (lensFacing in lensFacingOrder) {
            if (!mDialogItems.containsKey(lensFacing) || mDialogItems[lensFacing]!!.isEmpty()) {
                continue
            }

            dialogViewListItems.add(getHeaderViewListItem(lensFacing))
            mDialogItems[lensFacing]!!.stream().map(::getCameraViewListItem)
                .forEach(dialogViewListItems::add)
        }
        mCameraPickerListAdapter.updateBackingData(dialogViewListItems)
    }

    /**
     * Utility function to create a {@link ViewListItem} of type {@link ViewListItem.Type.HEADING}
     * for a given {@code lensFacing}
     */
    private fun getHeaderViewListItem(lensFacing: Int): CameraPickerListAdapter.ViewListItem {
        val drawable = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_BACK -> R.drawable.ic_camera_rear
            else -> R.drawable.ic_camera_front
        }
        val text = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_BACK -> R.string.list_item_text_back_camera
            else -> R.string.list_item_text_front_camera
        }

        return CameraPickerListAdapter.ViewListItem(
            type = CameraPickerListAdapter.ViewListItem.Type.HEADING,
            cameraId = null,
            textResource = text,
            drawable = drawable,
            selected = false
        )
    }

    /**
     * Utility function to create the corresponding {@link ViewListItem} from the given
     * {@link ListItem}
     */
    private fun getCameraViewListItem(listItem: ListItem): CameraPickerListAdapter.ViewListItem {
        val text: Int
        val drawable: Int
        when (listItem.cameraCategory) {
            CameraCategory.UNKNOWN -> {
                text = R.string.list_item_text_unknown_camera
                drawable = R.drawable.ic_camera_category_unknown
            }

            CameraCategory.STANDARD -> {
                text = R.string.list_item_text_standard_camera
                drawable = R.drawable.ic_camera_category_standard
            }

            CameraCategory.WIDE_ANGLE -> {
                text = R.string.list_item_text_wide_angle_camera
                drawable = R.drawable.ic_camera_category_wide_angle
            }

            CameraCategory.ULTRA_WIDE -> {
                text = R.string.list_item_text_ultra_wide_camera
                drawable = R.drawable.ic_camera_category_wide_angle
            }

            CameraCategory.TELEPHOTO -> {
                text = R.string.list_item_text_telephoto_camera
                drawable = R.drawable.ic_camera_category_telephoto
            }

            CameraCategory.OTHER -> {
                text = R.string.list_item_text_other_camera
                drawable = R.drawable.ic_camera_category_other
            }
        }

        return CameraPickerListAdapter.ViewListItem(
            type = CameraPickerListAdapter.ViewListItem.Type.ELEMENT,
            cameraId = listItem.cameraId,
            textResource = text,
            drawable = drawable,
            selected = listItem.isSelected
        )
    }

    /**
     * Internal class to manage the AlertDialog's RecyclerView that acts as the Camera Picker.
     */
    private class CameraPickerListAdapter(
        private var mViewListItems: List<ViewListItem>,
        private val mOnItemSelectedListener: Consumer<CameraId>
    ) : RecyclerView.Adapter<CameraPickerListAdapter.CameraPickerViewHolder>() {

        override fun onCreateViewHolder(
            viewgroup: ViewGroup, viewType: Int
        ): CameraPickerViewHolder {
            val holderType = ViewListItem.Type.entries[viewType]
            return CameraPickerViewHolder.getCameraPickerViewHolder(viewgroup, holderType)
        }

        override fun getItemCount(): Int {
            return mViewListItems.size
        }

        override fun onBindViewHolder(viewHolder: CameraPickerViewHolder, position: Int) {
            val item = mViewListItems[position]
            viewHolder.setupViewHolder(item)

            if (item.type == ViewListItem.Type.ELEMENT) {
                viewHolder.mView.setOnClickListener {
                    mOnItemSelectedListener.accept(item.cameraId!!)
                }
            } else {
                viewHolder.mView.setOnClickListener(null)
            }
        }

        override fun onBindViewHolder(
            holder: CameraPickerViewHolder, position: Int, payloads: MutableList<Any>
        ) {
            if (payloads.isEmpty()) {
                return onBindViewHolder(holder, position)
            }

            // notifyItemChange is called with "true" payload if the diff between state is the
            // "selected" value only;  otherwise the payload is false.

            // If the payload here contains any "false", then we don't know what all has changed,
            // so defer to the base onBindViewHolder which re-inits the view
            if (payloads.contains(false)) {
                return onBindViewHolder(holder, position)
            }

            // Only "true" in payloads, just update the isChecked value of the view
            holder.mRadioButton?.isChecked = mViewListItems[position].selected
        }

        override fun getItemViewType(position: Int): Int {
            return mViewListItems[position].type.ordinal
        }

        fun updateBackingData(newData: List<ViewListItem>) {
            if (mViewListItems.size != newData.size) {
                mViewListItems = newData
                notifyDataSetChanged()
            }

            // Contains the index at which the item has changes, and if the only change
            // at the position is the ViewListItem.selected value. This would happen when
            // updateBackingData is called because the user picked a new camera to stream
            // from.
            val modifiedPositions: MutableList<Pair<Int, Boolean>> = mutableListOf()

            mViewListItems.zip(newData).forEachIndexed { idx, (currentItem, newItem) ->
                if (currentItem != newItem) {
                    modifiedPositions.add(
                        Pair(
                            idx, ViewListItem.onlySelectedChanged(currentItem, newItem)
                        )
                    )
                }
            }
            mViewListItems = newData
            modifiedPositions.forEach { (modifiedPos, onlySelectedChanged) ->
                notifyItemChanged(modifiedPos, /*payload=*/ onlySelectedChanged)
            }
        }

        /**
         * Private class to mux header list items and the camera list items.
         *
         * {@link CameraPickerListAdapter} delegates to this class to populate and set up the
         * actual view in the RecyclerView.
         */
        private class CameraPickerViewHolder private constructor(val mView: View) :
            RecyclerView.ViewHolder(mView) {
            companion object {
                /**
                 * Static helper to construct an instance of this class for different
                 * {@link ViewListItem}s.
                 */
                fun getCameraPickerViewHolder(
                    viewGroup: ViewGroup, type: ViewListItem.Type
                ): CameraPickerViewHolder {
                    val layoutId = when (type) {
                        ViewListItem.Type.HEADING -> R.layout.list_item_header
                        ViewListItem.Type.ELEMENT -> R.layout.list_item_camera
                    }

                    val view = LayoutInflater.from(viewGroup.context)
                        .inflate(layoutId, viewGroup, /*attachToRoot=*/ false)
                    return CameraPickerViewHolder(view)
                }
            }

            private val mTextView: TextView = mView.findViewById(R.id.text_view)!!
            private val mImageView: ImageView = mView.findViewById(R.id.image_view)!!
            val mRadioButton: RadioButton? = mView.findViewById(R.id.radio_button)

            /**
             * Method to set up the text views and drawables for the given ViewHolder.
             *
             * {@code item} is the {@link ViewListItem} that this View is displaying.
             * {@code item.type} must match the type that was passed in
             * {@link #getCameraPickerViewHolder}.
             */
            fun setupViewHolder(item: ViewListItem) {
                when (item.type) {
                    ViewListItem.Type.HEADING -> setupHeader(item)
                    ViewListItem.Type.ELEMENT -> setupElement(item)
                }
            }

            fun setupHeader(item: ViewListItem) {
                mTextView.setText(item.textResource)
                mImageView.setImageResource(item.drawable)
            }

            fun setupElement(item: ViewListItem) {
                mTextView.setText(item.textResource)
                mImageView.setImageResource(item.drawable)
                mRadioButton!!.isChecked = item.selected
            }
        }

        /**
         * Internal representation used by the {@link CameraPickerListAdapter}. This closely tracks
         * the information needed by the RecyclerView and Adapter to display the information on
         * screen.
         */
        data class ViewListItem(
            val type: Type,
            val cameraId: CameraId?,
            val textResource: Int,
            val drawable: Int,
            val selected: Boolean
        ) {
            enum class Type {
                HEADING, ELEMENT
            }

            companion object {
                /**
                 * returns true if the only change between two objects is their "selected" value
                 */
                fun onlySelectedChanged(o1: ViewListItem, o2: ViewListItem): Boolean {
                    return o1.type == o2.type
                            && o1.cameraId == o2.cameraId
                            && o1.textResource == o2.textResource
                            && o1.drawable == o2.drawable
                            && o1.selected != o2.selected
                }
            }
        }
    }

    /**
     * Utility class used to track the current state of Dialog.
     */
    data class ListItem(
        val lensFacing: Int,
        val cameraId: CameraId,
        val cameraCategory: CameraCategory,
        var isSelected: Boolean
    ) {
        constructor(cameraInfo: CameraInfo) : this(
            cameraInfo.lensFacing,
            cameraInfo.cameraId,
            cameraInfo.cameraCategory,
            isSelected = false
        )
    }


}