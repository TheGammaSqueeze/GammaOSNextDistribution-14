/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.leaudio;

import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BroadcastItemsAdapter
        extends RecyclerView.Adapter<BroadcastItemsAdapter.BroadcastItemHolder> {
    private List<BluetoothLeBroadcastMetadata> mBroadcastMetadataList = new ArrayList<>();
    private final Map<Integer /* broadcastId */, Boolean /* isPlaying */> mBroadcastPlaybackMap =
            new HashMap<>();
    private OnItemClickListener mOnItemClickListener;

    @NonNull
    @Override
    public BroadcastItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item_view = LayoutInflater.from(parent.getContext()).inflate(R.layout.broadcast_item,
                parent, false);
        return new BroadcastItemHolder(item_view, mOnItemClickListener);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull BroadcastItemHolder holder, int position) {
        BluetoothLeBroadcastMetadata meta = mBroadcastMetadataList.get(position);
        Integer broadcastId = meta.getBroadcastId();
        Boolean isPlaybackStateKnown = mBroadcastPlaybackMap.containsKey(broadcastId);

        String broadcastText =
            "ID: " + broadcastId + "(" + String.format("0x%06x", broadcastId) + ")";

        ColorStateList color = ColorStateList.valueOf(Color.WHITE);

        if (isPlaybackStateKnown) {
            // Set card color based on the playback state
            Boolean isPlaying = mBroadcastPlaybackMap.getOrDefault(broadcastId, false);

            if (isPlaying) {
              color = ColorStateList.valueOf(Color.parseColor("#92b141"));
              broadcastText += " ▶️";
            } else {
              broadcastText += " ⏸";
            }
        }

        broadcastText += ("\nAddress: " + meta.getSourceDevice().getAddress());
        broadcastText += ("\nName: " + meta.getSourceDevice().getName());
        broadcastText += ("\nBroadcastName: " + meta.getBroadcastName());
        broadcastText += ("\nSID: " + String.valueOf(meta.getSourceAdvertisingSid()));
        broadcastText += ("\nisPublic: " + String.valueOf(meta.isPublicBroadcast()));
        broadcastText += ("\nisEncrypted: " + String.valueOf(meta.isEncrypted()));

        holder.background.setCardBackgroundColor(color);
        holder.mTextViewBroadcastId.setText(broadcastText);
    }

    @Override
    public int getItemCount() {
        return mBroadcastMetadataList.size();
    }

    public void updateBroadcastsMetadata(List<BluetoothLeBroadcastMetadata> broadcasts) {
        mBroadcastMetadataList = broadcasts;
        notifyDataSetChanged();
    }

    public void updateBroadcastMetadata(BluetoothLeBroadcastMetadata broadcast) {
        mBroadcastMetadataList.removeIf(bc -> (bc.getBroadcastId() == broadcast.getBroadcastId()));
        mBroadcastMetadataList.add(broadcast);
        notifyDataSetChanged();
    }

    public void addBroadcasts(Integer broadcastId) {
        if (!mBroadcastPlaybackMap.containsKey(broadcastId))
            mBroadcastPlaybackMap.put(broadcastId, false);
    }

    public void removeBroadcast(Integer broadcastId) {
        mBroadcastMetadataList.removeIf(bc -> (broadcastId.equals(bc.getBroadcastId())));
        mBroadcastPlaybackMap.remove(broadcastId);
        notifyDataSetChanged();
    }

    public void setBroadcasts(List<BluetoothLeBroadcastMetadata> broadcasts) {
        mBroadcastMetadataList.clear();
        mBroadcastMetadataList.addAll(broadcasts);

        for (BluetoothLeBroadcastMetadata b : broadcasts) {
            int broadcastId = b.getBroadcastId();
            if (mBroadcastPlaybackMap.containsKey(broadcastId)) {
                continue;
            }
//          mBroadcastPlaybackMap.remove(broadcastId);
            mBroadcastPlaybackMap.put(broadcastId, false);
        }
        notifyDataSetChanged();
    }

    public void updateBroadcastPlayback(Integer broadcastId, boolean isPlaying) {
        mBroadcastPlaybackMap.put(broadcastId, isPlaying);
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(Integer broadcastId);
    }

    class BroadcastItemHolder extends RecyclerView.ViewHolder {
        private final TextView mTextViewBroadcastId;
        private final CardView background;

        public BroadcastItemHolder(@NonNull View itemView, OnItemClickListener listener) {
            super(itemView);

            mTextViewBroadcastId = itemView.findViewById(R.id.broadcast_id_text);
            background = itemView.findViewById(R.id.broadcast_item_card_view);

            itemView.setOnClickListener(v -> {
                if (listener == null) return;

                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Integer broadcastId = mBroadcastMetadataList.get(position).getBroadcastId();
                    listener.onItemClick(broadcastId);
                }
            });
        }
    }
}
