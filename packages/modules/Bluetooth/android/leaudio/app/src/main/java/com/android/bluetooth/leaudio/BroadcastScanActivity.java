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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

public class BroadcastScanActivity extends AppCompatActivity {
    private BluetoothDevice device;
    private BroadcastScanViewModel mViewModel;
    private BroadcastItemsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.broadcast_scan_activity);

        RecyclerView recyclerView = findViewById(R.id.broadcast_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        adapter = new BroadcastItemsAdapter();
        adapter.setOnItemClickListener(broadcastId -> {
            mViewModel.scanForBroadcasts(device, false);

            BluetoothLeBroadcastMetadata broadcast = null;
            for (BluetoothLeBroadcastMetadata b : mViewModel.getAllBroadcasts().getValue()) {
                if (Objects.equals(b.getBroadcastId(), broadcastId)) {
                    broadcast = b;
                    break;
                }
            }

            if (broadcast == null) {
                Toast.makeText(recyclerView.getContext(), "Matching broadcast not found."
                                + " broadcastId=" + broadcastId, Toast.LENGTH_SHORT).show();
                return;
            }

            // Set broadcast source on peer only if scan delegator device context is available
            if (device != null) {
                // Start Dialog with the broadcast input details
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                LayoutInflater inflater = getLayoutInflater();
                alert.setTitle("Add the Broadcast:");

                View alertView =
                        inflater.inflate(R.layout.broadcast_scan_add_encrypted_source_dialog,
                                         null);

                boolean isPublic = broadcast.isPublicBroadcast();
                TextView addr_text = alertView.findViewById(R.id.broadcast_with_pbp_text);
                addr_text.setText("Broadcast with PBP: " + (isPublic ? "Yes" : "No"));

                String name = broadcast.getBroadcastName();
                addr_text = alertView.findViewById(R.id.broadcast_name_text);
                if (isPublic && name != null) {
                    addr_text.setText("Public Name: " + name);
                } else {
                    addr_text.setVisibility(View.INVISIBLE);
                }

                BluetoothLeAudioContentMetadata publicMetadata =
                        broadcast.getPublicBroadcastMetadata();
                addr_text = alertView.findViewById(R.id.public_program_info_text);
                if (isPublic && publicMetadata != null) {
                    addr_text.setText("Public Info: " + publicMetadata.getProgramInfo());
                } else {
                    addr_text.setVisibility(View.INVISIBLE);
                }

                final EditText channels_input_text =
                        alertView.findViewById(R.id.broadcast_channel_map);

                final EditText code_input_text =
                        alertView.findViewById(R.id.broadcast_code_input);
                BluetoothLeBroadcastMetadata.Builder builder = new
                        BluetoothLeBroadcastMetadata.Builder(broadcast);

                alert.setView(alertView).setNegativeButton("Cancel", (dialog, which) -> {
                    // Do nothing
                }).setPositiveButton("Add", (dialog, which) -> {
                    BluetoothLeBroadcastMetadata metadata;
                    if (code_input_text.getText() == null) {
                        Toast.makeText(recyclerView.getContext(), "Invalid broadcast code",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (code_input_text.getText().length() == 0) {
                        Toast.makeText(recyclerView.getContext(), "Adding not encrypted broadcast "
                                       + "source broadcastId="
                                       + broadcastId, Toast.LENGTH_SHORT).show();
                        metadata = builder.setEncrypted(false).build();
                    } else {
                        if ((code_input_text.getText().length() > 16) ||
                                (code_input_text.getText().length() < 4)) {
                            Toast.makeText(recyclerView.getContext(),
                                           "Invalid Broadcast code length",
                                           Toast.LENGTH_SHORT).show();

                            return;
                        }

                        metadata = builder.setBroadcastCode(
                                        code_input_text.getText().toString().getBytes())
                               .setEncrypted(true)
                               .build();
                    }

                    if ((channels_input_text.getText() != null)
                            && (channels_input_text.getText().length() != 0)) {
                        int channelMap = Integer.parseInt(channels_input_text.getText().toString());
                        // Apply a single channel map preference to all subgroups
                        for (BluetoothLeBroadcastSubgroup subGroup : metadata.getSubgroups()) {
                            List<BluetoothLeBroadcastChannel> channels = subGroup.getChannels();
                            for (int i = 0; i < channels.size(); i++) {
                                BluetoothLeBroadcastChannel channel = channels.get(i);
                                // Set the channel preference value according to the map
                                if (channel.getChannelIndex() != 0) {
                                    if ((channelMap & (1 << (channel.getChannelIndex() - 1))) != 0) {
                                        BluetoothLeBroadcastChannel.Builder bob
                                                = new BluetoothLeBroadcastChannel.Builder(channel);
                                        bob.setSelected(true);
                                        channels.set(i, bob.build());
                                    }
                                }
                            }
                        }
                    }

                    Toast.makeText(recyclerView.getContext(), "Adding broadcast source"
                                    + " broadcastId=" + broadcastId, Toast.LENGTH_SHORT).show();
                    mViewModel.addBroadcastSource(device, metadata);
                });

                alert.show();
            }
        });
        recyclerView.setAdapter(adapter);

        mViewModel = ViewModelProviders.of(this).get(BroadcastScanViewModel.class);
        mViewModel.getAllBroadcasts().observe(this, audioBroadcasts -> {
            // Update Broadcast list in the adapter
            adapter.setBroadcasts(audioBroadcasts);
        });

        Intent intent = getIntent();
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mViewModel.scanForBroadcasts(device, false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mViewModel.getAllBroadcasts().getValue() != null)
            adapter.setBroadcasts(mViewModel.getAllBroadcasts().getValue());

        mViewModel.scanForBroadcasts(device, true);
        mViewModel.refreshBroadcasts();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
    }
}
