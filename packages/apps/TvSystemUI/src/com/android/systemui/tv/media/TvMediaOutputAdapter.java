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

package com.android.systemui.tv.media;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.widget.RecyclerView;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.media.dialog.MediaItem;
import com.android.systemui.media.dialog.MediaOutputController;
import com.android.systemui.tv.res.R;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adapter for showing the {@link MediaItem}s in the {@link TvMediaOutputDialogActivity}.
 */
public class TvMediaOutputAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = TvMediaOutputAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final TvMediaOutputController mMediaOutputController;
    private final MediaOutputController.Callback mCallback;
    private final Context mContext;
    protected List<MediaItem> mMediaItemList = new CopyOnWriteArrayList<>();

    private final int mFocusedRadioTint;
    private final int mUnfocusedRadioTint;
    private final int mCheckedRadioTint;

    TvMediaOutputAdapter(Context context, TvMediaOutputController mediaOutputController,
            MediaOutputController.Callback callback) {
        mContext = context;
        mMediaOutputController = mediaOutputController;
        mCallback = callback;

        Resources res = mContext.getResources();
        mFocusedRadioTint = res.getColor(R.color.media_dialog_radio_button_focused);
        mUnfocusedRadioTint = res.getColor(R.color.media_dialog_radio_button_unfocused);
        mCheckedRadioTint = res.getColor(R.color.media_dialog_radio_button_checked);

        setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= mMediaItemList.size()) {
            Log.e(TAG, "Incorrect position for item type: " + position);
            return MediaItem.MediaItemType.TYPE_GROUP_DIVIDER;
        }
        return mMediaItemList.get(position).getMediaItemType();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View mHolderView = LayoutInflater.from(mContext)
                .inflate(MediaItem.getMediaLayoutId(viewType), parent, false);

        switch (viewType) {
            case MediaItem.MediaItemType.TYPE_GROUP_DIVIDER:
                return new DividerViewHolder(mHolderView);
            case MediaItem.MediaItemType.TYPE_PAIR_NEW_DEVICE:
            case MediaItem.MediaItemType.TYPE_DEVICE:
                return new DeviceViewHolder(mHolderView);
            default:
                Log.e(TAG, "unknown viewType: " + viewType);
                return new DeviceViewHolder(mHolderView);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position >= getItemCount()) {
            Log.e(TAG, "Tried to bind at position > list size (" + getItemCount() + ")");
        }

        MediaItem currentMediaItem = mMediaItemList.get(position);
        switch (currentMediaItem.getMediaItemType()) {
            case MediaItem.MediaItemType.TYPE_GROUP_DIVIDER ->
                    ((DividerViewHolder) viewHolder).onBind(currentMediaItem.getTitle());
            case MediaItem.MediaItemType.TYPE_PAIR_NEW_DEVICE ->
                    ((DeviceViewHolder) viewHolder).onBindNewDevice();
            case MediaItem.MediaItemType.TYPE_DEVICE -> ((DeviceViewHolder) viewHolder).onBind(
                    currentMediaItem.getMediaDevice().get(), position);
            default -> Log.d(TAG, "Incorrect position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return mMediaItemList.size();
    }

    @Override
    public long getItemId(int position) {
        MediaItem item = mMediaItemList.get(position);
        if (item.getMediaDevice().isPresent()) {
            return item.getMediaDevice().get().getId().hashCode();
        }
        if (item.getMediaItemType() == MediaItem.MediaItemType.TYPE_GROUP_DIVIDER) {
            if (item.getTitle() == null || item.getTitle().isEmpty()) {
                return MediaItem.MediaItemType.TYPE_GROUP_DIVIDER;
            }
            return item.getTitle().hashCode();
        }
        return item.getMediaItemType();
    }

    public void updateItems() {
        mMediaItemList.clear();
        mMediaItemList.addAll(mMediaOutputController.getMediaItemList());
        if (DEBUG) {
            Log.d(TAG, "updateItems");
            for (MediaItem mediaItem : mMediaItemList) {
                Log.d(TAG, mediaItem.toString());
            }
        }
        notifyDataSetChanged();
    }

    private class DeviceViewHolder extends RecyclerView.ViewHolder {
        final ImageView mIcon;
        final TextView mTitle;
        final TextView mSubtitle;
        final RadioButton mRadioButton;

        DeviceViewHolder(View itemView) {
            super(itemView);
            mIcon = itemView.requireViewById(R.id.media_output_item_icon);
            mTitle = itemView.requireViewById(R.id.media_dialog_item_title);
            mSubtitle = itemView.requireViewById(R.id.media_dialog_item_subtitle);
            mRadioButton = itemView.requireViewById(R.id.media_dialog_radio_button);
        }

        void onBind(MediaDevice mediaDevice, int position) {
            // Title
            mTitle.setText(mediaDevice.getName());

            // Subtitle
            setSummary(mediaDevice);

            // Icon
            Drawable icon;
            if (mediaDevice.getState()
                    == LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED) {
                icon =
                        mContext.getDrawable(
                                com.android.systemui.R.drawable.media_output_status_failed);
            } else {
                icon = mediaDevice.getIconWithoutBackground();
            }
            if (icon == null) {
                if (DEBUG) Log.d(TAG, "Using default icon for " + mediaDevice);
                icon = mContext.getDrawable(
                        com.android.settingslib.R.drawable.ic_media_speaker_device);
            }
            mIcon.setImageDrawable(icon);

            mRadioButton.setVisibility(mediaDevice.isConnected() ? View.VISIBLE : View.GONE);
            mRadioButton.setChecked(isCurrentlyConnected(mediaDevice));
            setRadioButtonColor();

            itemView.setOnFocusChangeListener((view, focused) -> {
                setSummary(mediaDevice);
                setRadioButtonColor();
                mTitle.setSelected(focused);
                mSubtitle.setSelected(focused);
            });

            itemView.setOnClickListener(v -> transferOutput(mediaDevice));
        }

        private void setRadioButtonColor() {
            if (itemView.hasFocus()) {
                mRadioButton.getButtonDrawable().setTint(
                        mRadioButton.isChecked() ? mCheckedRadioTint : mFocusedRadioTint);
            } else {
                mRadioButton.getButtonDrawable().setTint(mUnfocusedRadioTint);
            }
        }

        private void setSummary(MediaDevice mediaDevice) {
            CharSequence summary;
            if (mediaDevice.getState()
                    == LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED) {
                summary = mContext.getString(
                        com.android.systemui.R.string.media_output_dialog_connect_failed);
            } else {
                summary = mediaDevice.getSummaryForTv(itemView.hasFocus()
                        ? R.color.media_dialog_low_battery_focused
                        : R.color.media_dialog_low_battery_unfocused);
            }

            mSubtitle.setText(summary);
            mSubtitle.setVisibility(summary == null || summary.isEmpty()
                    ? View.GONE : View.VISIBLE);
        }

        private void transferOutput(MediaDevice mediaDevice) {
            if (mMediaOutputController.isAnyDeviceTransferring()) {
                // Don't interrupt ongoing transfer
                return;
            }
            if (isCurrentlyConnected(mediaDevice)) {
                if (DEBUG) Log.d(TAG, "Device is already selected as the active output");
                return;
            }
            mMediaOutputController.setTemporaryAllowListExceptionIfNeeded(mediaDevice);
            mMediaOutputController.connectDevice(mediaDevice);
            mediaDevice.setState(LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
            notifyDataSetChanged();
        }

        /**
         * The single currentConnected device or the only selected device
         */
        boolean isCurrentlyConnected(MediaDevice device) {
            return TextUtils.equals(device.getId(),
                    mMediaOutputController.getCurrentConnectedMediaDevice().getId())
                    || (mMediaOutputController.getSelectedMediaDevice().size() == 1
                    && isDeviceIncluded(mMediaOutputController.getSelectedMediaDevice(), device));
        }

        void onBindNewDevice() {
            mIcon.setImageResource(com.android.systemui.R.drawable.ic_add);
            mTitle.setText(R.string.media_output_dialog_pairing_new);
            mSubtitle.setVisibility(View.GONE);
            mRadioButton.setVisibility(View.GONE);

            itemView.setOnClickListener(v -> launchBluetoothSettings());
        }

        private void launchBluetoothSettings() {
            mCallback.dismissDialog();

            Intent bluetoothIntent = new Intent("android.settings.SLICE_SETTINGS");
            Bundle extra = new Bundle();
            extra.putString("slice_uri",
                    "content://com.google.android.tv.btservices.settings.sliceprovider/general");
            bluetoothIntent.putExtras(extra);
            bluetoothIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mContext.startActivity(bluetoothIntent);
        }

        private boolean isDeviceIncluded(List<MediaDevice> deviceList, MediaDevice targetDevice) {
            for (MediaDevice device : deviceList) {
                if (TextUtils.equals(device.getId(), targetDevice.getId())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class DividerViewHolder extends RecyclerView.ViewHolder {
        final TextView mHeaderText;
        final View mDividerLine;

        DividerViewHolder(@NonNull View itemView) {
            super(itemView);
            mHeaderText = itemView.requireViewById(R.id.media_output_group_header);
            mDividerLine = itemView.requireViewById(R.id.media_output_divider_line);
        }

        void onBind(String groupDividerTitle) {
            boolean hasText = groupDividerTitle != null && !groupDividerTitle.isEmpty();
            mHeaderText.setVisibility(hasText ? View.VISIBLE : View.GONE);
            mDividerLine.setVisibility(hasText ? View.GONE : View.VISIBLE);
            if (hasText) {
                mHeaderText.setText(groupDividerTitle);
            }
        }

    }
}
