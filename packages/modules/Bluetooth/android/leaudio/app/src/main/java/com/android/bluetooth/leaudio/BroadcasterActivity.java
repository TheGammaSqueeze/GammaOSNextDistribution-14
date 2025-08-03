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

import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BroadcasterActivity extends AppCompatActivity {
    private BroadcasterViewModel mViewModel;

    private final String BROADCAST_PREFS_KEY = "BROADCAST_PREFS_KEY";
    private final String PREF_SEP = ":";
    private final String VALUE_NOT_SET = "undefined";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.broadcaster_activity);

        FloatingActionButton fab = findViewById(R.id.broadcast_fab);
        fab.setOnClickListener(
                view -> {
                    if (mViewModel.getBroadcastCount() < mViewModel.getMaximumNumberOfBroadcast()) {
                        // Start Dialog with the broadcast input details
                        AlertDialog.Builder alert = new AlertDialog.Builder(this);
                        LayoutInflater inflater = getLayoutInflater();
                        alert.setTitle("Add the Broadcast:");

                        View alertView =
                                inflater.inflate(R.layout.broadcaster_add_broadcast_dialog, null);
                        final EditText code_input_text =
                                alertView.findViewById(R.id.broadcast_code_input);
                        final EditText program_info =
                                alertView.findViewById(R.id.broadcast_program_info_input);
                        final NumberPicker contextPicker =
                                alertView.findViewById(R.id.context_picker);
                        final EditText broadcast_name =
                                alertView.findViewById(R.id.broadcast_name_input);
                        final CheckBox publicCheckbox =
                                alertView.findViewById(R.id.is_public_checkbox);
                        final EditText public_content =
                                alertView.findViewById(R.id.broadcast_public_content_input);
                        // Add context type selector
                        contextPicker.setMinValue(1);
                        contextPicker.setMaxValue(
                                alertView
                                                .getResources()
                                                .getStringArray(R.array.content_types)
                                                .length
                                        - 1);
                        contextPicker.setDisplayedValues(
                                alertView.getResources().getStringArray(R.array.content_types));
                        final Button loadButton = alertView.findViewById(R.id.load_button);
                        loadButton.setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        showSelectSavedBroadcastAlert(
                                                code_input_text,
                                                program_info,
                                                contextPicker,
                                                broadcast_name,
                                                publicCheckbox,
                                                public_content);
                                    }
                                });
                        final Button clearButton = alertView.findViewById(R.id.clear_button);
                        clearButton.setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        SharedPreferences broadcastsPrefs =
                                                getSharedPreferences(BROADCAST_PREFS_KEY, 0);
                                        SharedPreferences.Editor editor = broadcastsPrefs.edit();
                                        editor.clear();
                                        editor.commit();
                                        Toast.makeText(
                                                        BroadcasterActivity.this,
                                                        "Saved broadcasts cleared",
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                });
                        alert.setView(alertView)
                                .setNegativeButton(
                                        "Cancel",
                                        (dialog, which) -> {
                                            // Do nothing
                                        })
                                .setNeutralButton(
                                        "Start",
                                        (dialog, which) -> {
                                            BluetoothLeBroadcastSettings broadcastSettings =
                                                    createBroadcastSettingsFromUI(
                                                            program_info.getText().toString(),
                                                            public_content.getText().toString(),
                                                            contextPicker.getValue(),
                                                            publicCheckbox.isChecked(),
                                                            broadcast_name.getText().toString(),
                                                            code_input_text.getText().toString());

                                            if (mViewModel.startBroadcast(broadcastSettings))
                                                Toast.makeText(
                                                                BroadcasterActivity.this,
                                                                "Broadcast was created.",
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                        })
                                .setPositiveButton(
                                        "Start & save",
                                        (dialog, which) -> {
                                            BluetoothLeBroadcastSettings broadcastSettings =
                                                    createBroadcastSettingsFromUI(
                                                            program_info.getText().toString(),
                                                            public_content.getText().toString(),
                                                            contextPicker.getValue(),
                                                            publicCheckbox.isChecked(),
                                                            broadcast_name.getText().toString(),
                                                            code_input_text.getText().toString());

                                            if (mViewModel.startBroadcast(broadcastSettings)) {
                                                // Save only if started successfully
                                                if (saveBroadcastToSharedPref(
                                                        program_info.getText().toString(),
                                                        public_content.getText().toString(),
                                                        contextPicker.getValue(),
                                                        publicCheckbox.isChecked(),
                                                        broadcast_name.getText().toString(),
                                                        code_input_text.getText().toString())) {
                                                    Toast.makeText(
                                                                    BroadcasterActivity.this,
                                                                    "Broadcast was created and"
                                                                            + " saved",
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                } else {
                                                    Toast.makeText(
                                                                    BroadcasterActivity.this,
                                                                    "Broadcast was created, but not"
                                                                            + " saved (already"
                                                                            + " exists).",
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                }
                                            }
                                        });

                        alert.show();
                    } else {
                        Toast.makeText(
                                        BroadcasterActivity.this,
                                        "Maximum number of broadcasts reached: "
                                                + Integer.valueOf(
                                                                mViewModel
                                                                        .getMaximumNumberOfBroadcast())
                                                        .toString(),
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        RecyclerView recyclerView = findViewById(R.id.broadcaster_recycle_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        final BroadcastItemsAdapter itemsAdapter = new BroadcastItemsAdapter();
        itemsAdapter.setOnItemClickListener(
                broadcastId -> {
                    AlertDialog.Builder alert = new AlertDialog.Builder(this);
                    alert.setTitle("Broadcast Info:");

                    // Load and fill in the metadata layout
                    final View metaLayout =
                            getLayoutInflater().inflate(R.layout.broadcast_metadata, null);
                    alert.setView(metaLayout);

                    BluetoothLeBroadcastMetadata metadata = null;
                    for (BluetoothLeBroadcastMetadata b : mViewModel.getAllBroadcastMetadata()) {
                        if (b.getBroadcastId() == broadcastId) {
                            metadata = b;
                            break;
                        }
                    }

                    if (metadata != null) {
                        TextView addr_text = metaLayout.findViewById(R.id.device_addr_text);
                        addr_text.setText(
                                "Device Address: " + metadata.getSourceDevice().toString());

                        addr_text = metaLayout.findViewById(R.id.adv_sid_text);
                        addr_text.setText("Advertising SID: " + metadata.getSourceAdvertisingSid());

                        addr_text = metaLayout.findViewById(R.id.pasync_interval_text);
                        addr_text.setText("Pa Sync Interval: " + metadata.getPaSyncInterval());

                        addr_text = metaLayout.findViewById(R.id.is_encrypted_text);
                        addr_text.setText(
                                "Is Encrypted: " + (metadata.isEncrypted() ? "Yes" : "No"));

                        boolean isPublic = metadata.isPublicBroadcast();
                        addr_text = metaLayout.findViewById(R.id.is_public_text);
                        addr_text.setText("Is Public Broadcast: " + (isPublic ? "Yes" : "No"));

                        String name = metadata.getBroadcastName();
                        addr_text = metaLayout.findViewById(R.id.broadcast_name_text);
                        if (isPublic && name != null) {
                            addr_text.setText("Public Name: " + name);
                        } else {
                            addr_text.setVisibility(View.INVISIBLE);
                        }

                        BluetoothLeAudioContentMetadata publicMetadata =
                                metadata.getPublicBroadcastMetadata();
                        addr_text = metaLayout.findViewById(R.id.public_program_info_text);
                        if (isPublic && publicMetadata != null) {
                            addr_text.setText("Public Info: " + publicMetadata.getProgramInfo());
                        } else {
                            addr_text.setVisibility(View.INVISIBLE);
                        }

                        byte[] code = metadata.getBroadcastCode();
                        addr_text = metaLayout.findViewById(R.id.broadcast_code_text);
                        if (code != null) {
                            addr_text.setText(
                                    "Broadcast Code: " + new String(code, StandardCharsets.UTF_8));
                        } else {
                            addr_text.setVisibility(View.INVISIBLE);
                        }

                        addr_text = metaLayout.findViewById(R.id.presentation_delay_text);
                        addr_text.setText(
                                "Presentation Delay: "
                                        + metadata.getPresentationDelayMicros()
                                        + " [us]");
                    }

                    alert.setNeutralButton(
                            "Stop",
                            (dialog, which) -> {
                                mViewModel.stopBroadcast(broadcastId);
                            });
                    alert.setPositiveButton(
                            "Modify",
                            (dialog, which) -> {
                                // Open activity for progam info
                                AlertDialog.Builder modifyAlert = new AlertDialog.Builder(this);
                                modifyAlert.setTitle("Modify the Broadcast:");

                                LayoutInflater inflater = getLayoutInflater();
                                View alertView =
                                        inflater.inflate(
                                                R.layout.broadcaster_add_broadcast_dialog, null);
                                EditText program_info_input_text =
                                        alertView.findViewById(R.id.broadcast_program_info_input);
                                EditText broadcast_name_input_text =
                                        alertView.findViewById(R.id.broadcast_name_input);
                                EditText public_content_input_text =
                                        alertView.findViewById(R.id.broadcast_public_content_input);

                                // The Code cannot be changed, so just hide it
                                final EditText code_input_text =
                                        alertView.findViewById(R.id.broadcast_code_input);
                                code_input_text.setVisibility(View.GONE);
                                // Public broadcast flag cannot be changed, so just hide it
                                final CheckBox public_input_checkbox =
                                        alertView.findViewById(R.id.is_public_checkbox);
                                public_input_checkbox.setVisibility(View.GONE);
                                // Context picker cannot be changed, so just hide it
                                final NumberPicker content_input_text =
                                        alertView.findViewById(R.id.context_picker);
                                content_input_text.setVisibility(View.GONE);
                                // Can't load when modify, so just hide buttons
                                final Button loadButton = alertView.findViewById(R.id.load_button);
                                loadButton.setVisibility(View.GONE);
                                final Button clearButton =
                                        alertView.findViewById(R.id.clear_button);
                                clearButton.setVisibility(View.GONE);

                                modifyAlert
                                        .setView(alertView)
                                        .setNegativeButton(
                                                "Cancel",
                                                (modifyDialog, modifyWhich) -> {
                                                    // Do nothing
                                                })
                                        .setPositiveButton(
                                                "Update",
                                                (modifyDialog, modifyWhich) -> {
                                                    BluetoothLeAudioContentMetadata.Builder
                                                            contentBuilder =
                                                                    new BluetoothLeAudioContentMetadata
                                                                            .Builder();
                                                    String programInfo =
                                                            program_info_input_text
                                                                    .getText()
                                                                    .toString();
                                                    if (!programInfo.isEmpty()) {
                                                        contentBuilder.setProgramInfo(programInfo);
                                                    }

                                                    final BluetoothLeAudioContentMetadata.Builder
                                                            publicContentBuilder =
                                                                    new BluetoothLeAudioContentMetadata
                                                                            .Builder();
                                                    final String publicContent =
                                                            public_content_input_text
                                                                    .getText()
                                                                    .toString();
                                                    if (!publicContent.isEmpty()) {
                                                        publicContentBuilder.setProgramInfo(
                                                                publicContent);
                                                    }

                                                    BluetoothLeBroadcastSubgroupSettings.Builder
                                                            subgroupBuilder =
                                                                    new BluetoothLeBroadcastSubgroupSettings
                                                                                    .Builder()
                                                                            .setContentMetadata(
                                                                                    contentBuilder
                                                                                            .build());

                                                    final String broadcastName =
                                                            broadcast_name_input_text
                                                                    .getText()
                                                                    .toString();
                                                    BluetoothLeBroadcastSettings.Builder builder =
                                                            new BluetoothLeBroadcastSettings
                                                                            .Builder()
                                                                    .setBroadcastName(
                                                                            broadcastName.isEmpty()
                                                                                    ? null
                                                                                    : broadcastName)
                                                                    .setPublicBroadcastMetadata(
                                                                            publicContentBuilder
                                                                                    .build());

                                                    // builder expect at least one subgroup setting
                                                    builder.addSubgroupSettings(
                                                            subgroupBuilder.build());

                                                    if (mViewModel.updateBroadcast(
                                                            broadcastId, builder.build()))
                                                        Toast.makeText(
                                                                        BroadcasterActivity.this,
                                                                        "Broadcast was updated.",
                                                                        Toast.LENGTH_SHORT)
                                                                .show();
                                                });

                                modifyAlert.show();
                            });

                    alert.show();
                    Log.d("CC", "Num broadcasts: " + mViewModel.getBroadcastCount());
                });
        recyclerView.setAdapter(itemsAdapter);

        // Get the initial state
        mViewModel = ViewModelProviders.of(this).get(BroadcasterViewModel.class);
        final List<BluetoothLeBroadcastMetadata> metadata = mViewModel.getAllBroadcastMetadata();
        itemsAdapter.updateBroadcastsMetadata(metadata.isEmpty() ? new ArrayList<>() : metadata);

        // Put a watch on updates
        mViewModel.getBroadcastUpdateMetadataLive().observe(this, audioBroadcast -> {
            itemsAdapter.updateBroadcastMetadata(audioBroadcast);

            Toast.makeText(BroadcasterActivity.this,
                    "Updated broadcast " + audioBroadcast.getBroadcastId(), Toast.LENGTH_SHORT)
                    .show();
        });

        // Put a watch on any error reports
        mViewModel.getBroadcastStatusMutableLive().observe(this, msg -> {
            Toast.makeText(BroadcasterActivity.this, msg, Toast.LENGTH_SHORT).show();
        });

        // Put a watch on broadcast playback states
        mViewModel.getBroadcastPlaybackStartedMutableLive().observe(this, reasonAndBidPair -> {
            Toast.makeText(BroadcasterActivity.this, "Playing broadcast " + reasonAndBidPair.second
                    + ", reason " + reasonAndBidPair.first, Toast.LENGTH_SHORT).show();

            itemsAdapter.updateBroadcastPlayback(reasonAndBidPair.second, true);
        });

        mViewModel.getBroadcastPlaybackStoppedMutableLive().observe(this, reasonAndBidPair -> {
            Toast.makeText(BroadcasterActivity.this, "Paused broadcast " + reasonAndBidPair.second
                    + ", reason " + reasonAndBidPair.first, Toast.LENGTH_SHORT).show();

            itemsAdapter.updateBroadcastPlayback(reasonAndBidPair.second, false);
        });

        mViewModel.getBroadcastAddedMutableLive().observe(this, broadcastId -> {
            itemsAdapter.addBroadcasts(broadcastId);

            Toast.makeText(BroadcasterActivity.this,
                    "Broadcast was added broadcastId: " + broadcastId, Toast.LENGTH_SHORT).show();
        });

        // Put a watch on broadcast removal
        mViewModel.getBroadcastRemovedMutableLive().observe(this, reasonAndBidPair -> {
            itemsAdapter.removeBroadcast(reasonAndBidPair.second);

            Toast.makeText(
                    BroadcasterActivity.this, "Broadcast was removed " + " broadcastId: "
                            + reasonAndBidPair.second + ", reason: " + reasonAndBidPair.first,
                    Toast.LENGTH_SHORT).show();
        });

        // Prevent destruction when loses focus
        this.setFinishOnTouchOutside(false);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private BluetoothLeBroadcastSettings createBroadcastSettingsFromUI(
            String programInfo,
            String publicContent,
            int contextTypeUI,
            boolean isPublic,
            String broadcastName,
            String broadcastCode) {

        final BluetoothLeAudioContentMetadata.Builder contentBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        if (!programInfo.isEmpty()) {
            contentBuilder.setProgramInfo(programInfo);
        }

        final BluetoothLeAudioContentMetadata.Builder publicContentBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        if (!publicContent.isEmpty()) {
            publicContentBuilder.setProgramInfo(publicContent);
        }

        // Extract raw metadata
        byte[] metaBuffer = contentBuilder.build().getRawMetadata();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(metaBuffer, 0, metaBuffer.length);

        // Extend raw metadata with context type
        final int contextValue = 1 << (contextTypeUI - 1);
        stream.write((byte) 0x03); // Length
        stream.write((byte) 0x02); // Type for the Streaming Audio Context
        stream.write((byte) (contextValue & 0x00FF)); // Value LSB
        stream.write((byte) ((contextValue & 0xFF00) >> 8)); // Value MSB

        BluetoothLeBroadcastSubgroupSettings.Builder subgroupBuilder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                        .setContentMetadata(
                                BluetoothLeAudioContentMetadata.fromRawBytes(stream.toByteArray()));
        BluetoothLeBroadcastSettings.Builder builder =
                new BluetoothLeBroadcastSettings.Builder()
                        .setPublicBroadcast(isPublic)
                        .setBroadcastName(broadcastName.isEmpty() ? null : broadcastName)
                        .setBroadcastCode(broadcastCode.isEmpty() ? null : broadcastCode.getBytes())
                        .setPublicBroadcastMetadata(publicContentBuilder.build());

        // builder expect at least one subgroup setting
        builder.addSubgroupSettings(subgroupBuilder.build());
        return builder.build();
    }

    private boolean saveBroadcastToSharedPref(
            String programInfo,
            String publicContent,
            int contextTypeUI,
            boolean isPublic,
            String broadcastName,
            String broadcastCode) {

        SharedPreferences broadcastsPrefs = getSharedPreferences(BROADCAST_PREFS_KEY, 0);
        if (broadcastsPrefs.contains(broadcastName)) {
            return false;
        } else {
            String toStore =
                    programInfo
                            + PREF_SEP
                            + publicContent
                            + PREF_SEP
                            + contextTypeUI
                            + PREF_SEP
                            + isPublic
                            + PREF_SEP
                            + broadcastName
                            + PREF_SEP;
            if (broadcastCode.isEmpty()) {
                toStore += VALUE_NOT_SET;
            } else {
                toStore += broadcastCode;
            }
            SharedPreferences.Editor editor = broadcastsPrefs.edit();
            editor.putString(broadcastName, toStore);
            editor.commit();
        }
        return true;
    }

    private final void showSelectSavedBroadcastAlert(
            final EditText code_input_text,
            final EditText program_info,
            final NumberPicker contextPicker,
            final EditText broadcast_name,
            final CheckBox publicCheckbox,
            final EditText public_content) {

        ArrayList<String> listSavedBroadcast = new ArrayList();

        final SharedPreferences broadcastsPrefs = getSharedPreferences(BROADCAST_PREFS_KEY, 0);
        Map<String, ?> allEntries = broadcastsPrefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            listSavedBroadcast.add(entry.getKey());
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Select saved broadcast");
        alertDialog
                .setSingleChoiceItems(
                        listSavedBroadcast.toArray(new String[listSavedBroadcast.size()]),
                        0,
                        (dialog, which) -> {
                            String[] broadcastValues =
                                    broadcastsPrefs
                                            .getString(listSavedBroadcast.get(which), "")
                                            .split(PREF_SEP);
                            if (broadcastValues.length != 6) {
                                Toast.makeText(
                                                this,
                                                "Could not retrieve "
                                                        + listSavedBroadcast.get(which)
                                                        + ".",
                                                Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            program_info.setText(broadcastValues[0]);
                            public_content.setText(broadcastValues[1]);
                            contextPicker.setValue(Integer.valueOf(broadcastValues[2]));
                            publicCheckbox.setChecked(Boolean.parseBoolean(broadcastValues[3]));
                            broadcast_name.setText(broadcastValues[4]);
                            if (!VALUE_NOT_SET.equals(broadcastValues[5])) {
                                code_input_text.setText(broadcastValues[5]);
                            }
                            dialog.dismiss();
                        })
                .setNegativeButton("Cancel", (dialog, which) -> {});
        AlertDialog savedBroadcastsAlertDialog = alertDialog.create();
        savedBroadcastsAlertDialog.show();
    }
}
