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

import static android.bluetooth.BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_BAD_CODE;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_CODE_REQUIRED;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_FAILED_TO_SYNCHRONIZE;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_NO_PAST;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED;
import static android.bluetooth.BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCINFO_REQUEST;

import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.ParcelUuid;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LeAudioRecycleViewAdapter
        extends RecyclerView.Adapter<LeAudioRecycleViewAdapter.ViewHolder> {
    private final AppCompatActivity parent;
    private OnItemClickListener clickListener;
    private OnLeAudioInteractionListener leAudioInteractionListener;
    private OnVolumeControlInteractionListener volumeControlInteractionListener;
    private OnBassInteractionListener bassInteractionListener;
    private OnHapInteractionListener hapInteractionListener;
    private final ArrayList<LeAudioDeviceStateWrapper> devices;

    private int GROUP_NODE_ADDED = 1;
    private int GROUP_NODE_REMOVED = 2;

    public LeAudioRecycleViewAdapter(AppCompatActivity context) {
        this.parent = context;
        devices = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.le_audio_device_fragment,
                parent, false);
        return new ViewHolder(v);
    }

    // As we scroll this methods rebinds devices below to our ViewHolders which are reused when
    // they go off the screen. This is also called when notifyItemChanged(position) is called
    // without the payloads.
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeAudioDeviceStateWrapper leAudioDeviceStateWrapper = devices.get(position);

        if (leAudioDeviceStateWrapper != null) {
            holder.deviceName.setText(parent.getString(R.string.notes_icon) + " "
                    + leAudioDeviceStateWrapper.device.getName() + " ["
                    + leAudioDeviceStateWrapper.device + "]");

            if (leAudioDeviceStateWrapper.device.getUuids() != null) {
                holder.itemView.findViewById(R.id.le_audio_switch)
                        .setEnabled(Arrays.asList(leAudioDeviceStateWrapper.device.getUuids())
                                .contains(ParcelUuid
                                        .fromString(parent.getString(R.string.svc_uuid_le_audio))));

                holder.itemView.findViewById(R.id.vc_switch)
                        .setEnabled(Arrays.asList(leAudioDeviceStateWrapper.device.getUuids())
                                .contains(ParcelUuid.fromString(
                                        parent.getString(R.string.svc_uuid_volume_control))));

                holder.itemView.findViewById(R.id.hap_switch).setEnabled(
                        Arrays.asList(leAudioDeviceStateWrapper.device.getUuids()).contains(
                                ParcelUuid.fromString(parent.getString(R.string.svc_uuid_has))));

                holder.itemView.findViewById(R.id.bass_switch)
                        .setEnabled(Arrays.asList(leAudioDeviceStateWrapper.device.getUuids())
                                .contains(ParcelUuid.fromString(parent.getString(R.string.svc_uuid_broadcast_audio))));
            }
        }

        // Set state observables
        setLeAudioStateObservers(holder, leAudioDeviceStateWrapper);
        setVolumeControlStateObservers(holder, leAudioDeviceStateWrapper);
        setVolumeControlUiStateObservers(holder, leAudioDeviceStateWrapper);
        setBassStateObservers(holder, leAudioDeviceStateWrapper);
        setHasStateObservers(holder, leAudioDeviceStateWrapper);
        setBassUiStateObservers(holder, leAudioDeviceStateWrapper);
    }

    private void setLeAudioStateObservers(@NonNull ViewHolder holder,
            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
        LeAudioDeviceStateWrapper.LeAudioData le_audio_svc_data =
                leAudioDeviceStateWrapper.leAudioData;
        if (le_audio_svc_data != null) {
            if (le_audio_svc_data.isConnectedMutable.hasObservers())
                le_audio_svc_data.isConnectedMutable.removeObservers(this.parent);
            le_audio_svc_data.isConnectedMutable.observe(this.parent, is_connected -> {
                // FIXME: How to prevent the callback from firing when we set this by code
                if (is_connected != holder.leAudioConnectionSwitch.isChecked()) {
                    holder.leAudioConnectionSwitch.setActivated(false);
                    holder.leAudioConnectionSwitch.setChecked(is_connected);
                    holder.leAudioConnectionSwitch.setActivated(true);
                }

                if (holder.itemView.findViewById(R.id.le_audio_layout)
                        .getVisibility() != (is_connected ? View.VISIBLE : View.GONE))
                    holder.itemView.findViewById(R.id.le_audio_layout)
                            .setVisibility(is_connected ? View.VISIBLE : View.GONE);
            });

            holder.itemView.findViewById(R.id.le_audio_layout)
                    .setVisibility(le_audio_svc_data.isConnectedMutable.getValue() != null
                            && le_audio_svc_data.isConnectedMutable.getValue() ? View.VISIBLE
                                    : View.GONE);

            if (le_audio_svc_data.nodeStatusMutable.hasObservers())
                le_audio_svc_data.nodeStatusMutable.removeObservers(this.parent);
            le_audio_svc_data.nodeStatusMutable.observe(this.parent, group_id_node_status_pair -> {
                final Integer status = group_id_node_status_pair.second;
                final Integer group_id = group_id_node_status_pair.first;

                if (status == GROUP_NODE_REMOVED)
                    holder.leAudioGroupIdText
                            .setText(((Integer) BluetoothLeAudio.GROUP_ID_INVALID).toString());
                else
                    holder.leAudioGroupIdText.setText(group_id.toString());
            });

            if (le_audio_svc_data.groupStatusMutable.hasObservers())
                le_audio_svc_data.groupStatusMutable.removeObservers(this.parent);
            le_audio_svc_data.groupStatusMutable.observe(this.parent, group_id_node_status_pair -> {
                final Integer group_id = group_id_node_status_pair.first;
                final Integer status = group_id_node_status_pair.second.first;
                final Integer flags = group_id_node_status_pair.second.second;

                // If our group.. actually we shouldn't get this event if it's nor ours,
                // right?
                if (holder.leAudioGroupIdText.getText().equals(group_id.toString())) {
                    holder.leAudioGroupStatusText.setText(status >= 0
                            ? this.parent.getResources()
                                    .getStringArray(R.array.group_statuses)[status]
                            : this.parent.getResources().getString(R.string.unknown));
                    holder.leAudioGroupFlagsText.setText(flags > 0 ? flags.toString()
                            : this.parent.getResources().getString(R.string.none));
                }
            });

            if (le_audio_svc_data.groupLockStateMutable.hasObservers())
                le_audio_svc_data.groupLockStateMutable.removeObservers(this.parent);
            le_audio_svc_data.groupLockStateMutable.observe(this.parent,
                    group_id_node_status_pair -> {
                        final Integer group_id = group_id_node_status_pair.first;
                        final Boolean locked = group_id_node_status_pair.second;

                        // If our group.. actually we shouldn't get this event if it's nor ours,
                        // right?
                        if (holder.leAudioGroupIdText.getText().equals(group_id.toString())) {
                            holder.leAudioSetLockStateText.setText(this.parent.getResources()
                                    .getString(locked ? R.string.group_locked
                                            : R.string.group_unlocked));
                        }
                    });

            if (le_audio_svc_data.microphoneStateMutable.hasObservers())
                le_audio_svc_data.microphoneStateMutable.removeObservers(this.parent);
            le_audio_svc_data.microphoneStateMutable.observe(this.parent, microphone_state -> {
                holder.leAudioGroupMicrophoneState.setText(this.parent.getResources()
                        .getStringArray(R.array.mic_states)[microphone_state]);
                holder.leAudioGroupMicrophoneSwitch.setActivated(false);
            });
        }
    }

    private void setHasStateObservers(@NonNull ViewHolder holder,
            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
        LeAudioDeviceStateWrapper.HapData hap_svc_data = leAudioDeviceStateWrapper.hapData;
        if (hap_svc_data != null) {
            if (hap_svc_data.hapStateMutable.hasObservers())
                hap_svc_data.hapStateMutable.removeObservers(this.parent);
            hap_svc_data.hapStateMutable.observe(this.parent, hap_state -> {
                holder.leAudioHapState.setText(this.parent.getResources()
                        .getStringArray(R.array.profile_states)[hap_state]);

                boolean is_connected = (hap_state == BluetoothHapClient.STATE_CONNECTED);
                if (is_connected != holder.hapConnectionSwitch.isChecked()) {
                    holder.hapConnectionSwitch.setActivated(false);
                    holder.hapConnectionSwitch.setChecked(is_connected);
                    holder.hapConnectionSwitch.setActivated(true);
                }

                if (holder.itemView.findViewById(R.id.hap_layout)
                        .getVisibility() != (is_connected ? View.VISIBLE : View.GONE))
                    holder.itemView.findViewById(R.id.hap_layout)
                            .setVisibility(is_connected ? View.VISIBLE : View.GONE);
            });

            if (hap_svc_data.hapFeaturesMutable.hasObservers())
                hap_svc_data.hapFeaturesMutable.removeObservers(this.parent);

            hap_svc_data.hapFeaturesMutable.observe(this.parent, features -> {
                try {
                    // Get hidden feature bits
                    Field field =
                            BluetoothHapClient.class.getDeclaredField("FEATURE_TYPE_MONAURAL");
                    field.setAccessible(true);
                    Integer FEATURE_TYPE_MONAURAL = (Integer) field.get(null);

                    field = BluetoothHapClient.class.getDeclaredField("FEATURE_TYPE_BANDED");
                    field.setAccessible(true);
                    Integer FEATURE_TYPE_BANDED = (Integer) field.get(null);

                    field = BluetoothHapClient.class
                            .getDeclaredField("FEATURE_SYNCHRONIZATED_PRESETS");
                    field.setAccessible(true);
                    Integer FEATURE_SYNCHRONIZATED_PRESETS = (Integer) field.get(null);

                    field = BluetoothHapClient.class
                            .getDeclaredField("FEATURE_INDEPENDENT_PRESETS");
                    field.setAccessible(true);
                    Integer FEATURE_INDEPENDENT_PRESETS = (Integer) field.get(null);

                    field = BluetoothHapClient.class.getDeclaredField("FEATURE_DYNAMIC_PRESETS");
                    field.setAccessible(true);
                    Integer FEATURE_DYNAMIC_PRESETS = (Integer) field.get(null);

                    field = BluetoothHapClient.class.getDeclaredField("FEATURE_WRITABLE_PRESETS");
                    field.setAccessible(true);
                    Integer FEATURE_WRITABLE_PRESETS = (Integer) field.get(null);

                    int hearing_aid_type_idx = (features & FEATURE_TYPE_MONAURAL) != 0 ? 0
                            : ((features & FEATURE_TYPE_BANDED) != 0 ? 1 : 2);
                    String hearing_aid_type = this.parent.getResources()
                            .getStringArray(R.array.hearing_aid_types)[hearing_aid_type_idx];
                    String preset_synchronization_support = this.parent.getResources()
                            .getStringArray(R.array.preset_synchronization_support)[(features
                                    & FEATURE_SYNCHRONIZATED_PRESETS) != 0 ? 1 : 0];
                    String independent_presets = this.parent.getResources()
                            .getStringArray(R.array.independent_presets)[(features
                                    & FEATURE_INDEPENDENT_PRESETS) != 0 ? 1 : 0];
                    String dynamic_presets = this.parent.getResources().getStringArray(
                            R.array.dynamic_presets)[(features & FEATURE_DYNAMIC_PRESETS) != 0 ? 1
                                    : 0];
                    String writable_presets_support = this.parent.getResources()
                            .getStringArray(R.array.writable_presets_support)[(features
                                    & FEATURE_WRITABLE_PRESETS) != 0 ? 1 : 0];
                    holder.leAudioHapFeatures.setText(hearing_aid_type + " / "
                            + preset_synchronization_support + " / " + independent_presets + " / "
                            + dynamic_presets + " / " + writable_presets_support);

                } catch (IllegalAccessException | NoSuchFieldException e) {
                    // Do nothing
                    holder.leAudioHapFeatures.setText("Hidden API for feature fields unavailable.");
                }
            });

            if (hap_svc_data.hapPresetsMutable.hasActiveObservers())
                hap_svc_data.hapPresetsMutable.removeObservers(this.parent);
            hap_svc_data.hapPresetsMutable.observe(this.parent, hapPresetsList -> {
                List<String> all_ids = hapPresetsList.stream()
                        .map(info -> "" + info.getIndex() + " " + info.getName()
                                + (info.isWritable() ? " [wr" : " [")
                                + (info.isAvailable() ? "a]" : "]"))
                        .collect(Collectors.toList());

                ArrayAdapter<Integer> adapter = new ArrayAdapter(this.parent,
                        android.R.layout.simple_spinner_item, all_ids);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                holder.leAudioHapPresetsSpinner.setAdapter(adapter);

                if (hap_svc_data.viewsData != null) {
                    Integer select_pos =
                            ((ViewHolderHapPersistentData) hap_svc_data.viewsData).selectedPresetPositionMutable
                                    .getValue();
                    if (select_pos != null)
                        holder.leAudioHapPresetsSpinner.setSelection(select_pos);
                }
            });

            if (hap_svc_data.hapActivePresetIndexMutable.hasObservers())
                hap_svc_data.hapActivePresetIndexMutable.removeObservers(this.parent);
            hap_svc_data.hapActivePresetIndexMutable.observe(this.parent, active_preset_index -> {
                holder.leAudioHapActivePresetIndex.setText(String.valueOf(active_preset_index));
            });

            if (hap_svc_data.hapActivePresetIndexMutable.hasObservers())
                hap_svc_data.hapActivePresetIndexMutable.removeObservers(this.parent);
            hap_svc_data.hapActivePresetIndexMutable.observe(this.parent, active_preset_index -> {
                holder.leAudioHapActivePresetIndex.setText(String.valueOf(active_preset_index));
            });
        } else {
            holder.itemView.findViewById(R.id.hap_layout).setVisibility(View.GONE);
        }
    }

    private void setVolumeControlStateObservers(@NonNull ViewHolder holder,
            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
        LeAudioDeviceStateWrapper.VolumeControlData vc_svc_data =
                leAudioDeviceStateWrapper.volumeControlData;
        if (vc_svc_data != null) {
            if (vc_svc_data.isConnectedMutable.hasObservers())
                vc_svc_data.isConnectedMutable.removeObservers(this.parent);
            vc_svc_data.isConnectedMutable.observe(this.parent, is_connected -> {
                // FIXME: How to prevent the callback from firing when we set this by code
                if (is_connected != holder.vcConnectionSwitch.isChecked()) {
                    holder.vcConnectionSwitch.setActivated(false);
                    holder.vcConnectionSwitch.setChecked(is_connected);
                    holder.vcConnectionSwitch.setActivated(true);
                }

                if (holder.itemView.findViewById(R.id.vc_layout)
                        .getVisibility() != (is_connected ? View.VISIBLE : View.GONE))
                    holder.itemView.findViewById(R.id.vc_layout)
                            .setVisibility(is_connected ? View.VISIBLE : View.GONE);
            });

            holder.itemView.findViewById(R.id.vc_layout)
                    .setVisibility(vc_svc_data.isConnectedMutable.getValue() != null
                            && vc_svc_data.isConnectedMutable.getValue() ? View.VISIBLE
                                    : View.GONE);

            if (vc_svc_data.volumeStateMutable.hasObservers())
                vc_svc_data.volumeStateMutable.removeObservers(this.parent);
            vc_svc_data.volumeStateMutable.observe(this.parent, state -> {
                holder.volumeSeekBar.setProgress(state);
            });

            if (vc_svc_data.mutedStateMutable.hasObservers())
                vc_svc_data.mutedStateMutable.removeObservers(this.parent);
            vc_svc_data.mutedStateMutable.observe(this.parent, state -> {
                holder.muteSwitch.setActivated(false);
                holder.muteSwitch.setChecked(state);
                holder.muteSwitch.setActivated(true);
            });

            if (vc_svc_data.numInputsMutable.hasObservers())
                vc_svc_data.numInputsMutable.removeObservers(this.parent);
            vc_svc_data.numInputsMutable.observe(this.parent, num_inputs -> {
                List<Integer> range = new ArrayList<>();
                if (num_inputs != 0)
                    range = IntStream.rangeClosed(1, num_inputs).boxed()
                            .collect(Collectors.toList());
                ArrayAdapter<Integer> adapter =
                        new ArrayAdapter(this.parent, android.R.layout.simple_spinner_item, range);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                holder.inputIdxSpinner.setAdapter(adapter);
            });

            if (vc_svc_data.viewsData != null) {
                Integer select_pos =
                        ((ViewHolderVcPersistentData) vc_svc_data.viewsData).selectedInputPosition;
                if (select_pos != null)
                    holder.inputIdxSpinner.setSelection(select_pos);
            }

            if (vc_svc_data.inputDescriptionsMutable.hasObservers())
                vc_svc_data.inputDescriptionsMutable.removeObservers(this.parent);
            vc_svc_data.inputDescriptionsMutable.observe(this.parent, integerStringMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputDescriptionText
                            .setText(integerStringMap.getOrDefault(input_id, ""));
                }
            });

            if (vc_svc_data.inputStateGainMutable.hasObservers())
                vc_svc_data.inputStateGainMutable.removeObservers(this.parent);
            vc_svc_data.inputStateGainMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputGainSeekBar
                            .setProgress(integerIntegerMap.getOrDefault(input_id, 0));
                }
            });

            if (vc_svc_data.inputStateGainModeMutable.hasObservers())
                vc_svc_data.inputStateGainModeMutable.removeObservers(this.parent);
            vc_svc_data.inputStateGainModeMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputGainModeText.setText(this.parent.getResources().getStringArray(
                            R.array.gain_modes)[integerIntegerMap.getOrDefault(input_id, 1)]);
                }
            });

            if (vc_svc_data.inputStateGainUnitMutable.hasObservers())
                vc_svc_data.inputStateGainUnitMutable.removeObservers(this.parent);
            vc_svc_data.inputStateGainUnitMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    // TODO: Use string map with units instead of plain numbers
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputGainPropsUnitText
                            .setText(integerIntegerMap.getOrDefault(input_id, 0).toString());
                }
            });

            if (vc_svc_data.inputStateGainMinMutable.hasObservers())
                vc_svc_data.inputStateGainMinMutable.removeObservers(this.parent);
            vc_svc_data.inputStateGainMinMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputGainPropsMinText
                            .setText(integerIntegerMap.getOrDefault(input_id, 0).toString());
                    holder.inputGainSeekBar.setMin(integerIntegerMap.getOrDefault(input_id, -255));
                }
            });

            if (vc_svc_data.inputStateGainMaxMutable.hasObservers())
                vc_svc_data.inputStateGainMaxMutable.removeObservers(this.parent);
            vc_svc_data.inputStateGainMaxMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputGainPropsMaxText
                            .setText(integerIntegerMap.getOrDefault(input_id, 0).toString());
                    holder.inputGainSeekBar.setMax(integerIntegerMap.getOrDefault(input_id, 255));
                }
            });

            if (vc_svc_data.inputStateMuteMutable.hasObservers())
                vc_svc_data.inputStateMuteMutable.removeObservers(this.parent);
            vc_svc_data.inputStateMuteMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    holder.inputMuteSwitch.setActivated(false);
                    holder.inputMuteSwitch
                            .setChecked(integerIntegerMap.getOrDefault(input_id, false));
                    holder.inputMuteSwitch.setActivated(true);
                }
            });

            if (vc_svc_data.inputStatusMutable.hasObservers())
                vc_svc_data.inputStatusMutable.removeObservers(this.parent);
            vc_svc_data.inputStatusMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    // TODO: Use string map with units instead of plain numbers
                    holder.inputStatusText
                            .setText(integerIntegerMap.getOrDefault(input_id, -1).toString());
                }
            });

            if (vc_svc_data.inputTypeMutable.hasObservers())
                vc_svc_data.inputTypeMutable.removeObservers(this.parent);
            vc_svc_data.inputTypeMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.inputIdxSpinner.getSelectedItem() != null) {
                    Integer input_id =
                            Integer.valueOf(holder.inputIdxSpinner.getSelectedItem().toString());
                    // TODO: Use string map with units instead of plain numbers
                    holder.inputTypeText
                            .setText(integerIntegerMap.getOrDefault(input_id, -1).toString());
                }
            });

            vc_svc_data.numOffsetsMutable.observe(this.parent, num_offsets -> {
                List<Integer> range = new ArrayList<>();
                if (num_offsets != 0)
                    range = IntStream.rangeClosed(1, num_offsets).boxed()
                            .collect(Collectors.toList());
                ArrayAdapter<Integer> adapter =
                        new ArrayAdapter(this.parent, android.R.layout.simple_spinner_item, range);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                holder.outputIdxSpinner.setAdapter(adapter);
            });

            if (vc_svc_data.viewsData != null) {
                Integer select_pos =
                        ((ViewHolderVcPersistentData) vc_svc_data.viewsData).selectedOutputPosition;
                if (select_pos != null)
                    holder.outputIdxSpinner.setSelection(select_pos);
            }

            if (vc_svc_data.outputVolumeOffsetMutable.hasObservers())
                vc_svc_data.outputVolumeOffsetMutable.removeObservers(this.parent);
            vc_svc_data.outputVolumeOffsetMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.outputIdxSpinner.getSelectedItem() != null) {
                    Integer output_id =
                            Integer.valueOf(holder.outputIdxSpinner.getSelectedItem().toString());
                    holder.outputGainOffsetSeekBar
                            .setProgress(integerIntegerMap.getOrDefault(output_id, 0));
                }
            });

            if (vc_svc_data.outputLocationMutable.hasObservers())
                vc_svc_data.outputLocationMutable.removeObservers(this.parent);
            vc_svc_data.outputLocationMutable.observe(this.parent, integerIntegerMap -> {
                if (holder.outputIdxSpinner.getSelectedItem() != null) {
                    Integer output_id =
                            Integer.valueOf(holder.outputIdxSpinner.getSelectedItem().toString());
                    holder.outputLocationText.setText(this.parent.getResources().getStringArray(
                            R.array.audio_locations)[integerIntegerMap.getOrDefault(output_id, 0)]);
                }
            });

            if (vc_svc_data.outputDescriptionMutable.hasObservers())
                vc_svc_data.outputDescriptionMutable.removeObservers(this.parent);
            vc_svc_data.outputDescriptionMutable.observe(this.parent, integerStringMap -> {
                if (holder.outputIdxSpinner.getSelectedItem() != null) {
                    Integer output_id =
                            Integer.valueOf(holder.outputIdxSpinner.getSelectedItem().toString());
                    holder.outputDescriptionText
                            .setText(integerStringMap.getOrDefault(output_id, "no description"));
                }
            });
        } else {
            holder.itemView.findViewById(R.id.vc_layout).setVisibility(View.GONE);
        }
    }

    private void setVolumeControlUiStateObservers(@NonNull ViewHolder holder,
            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
        if (leAudioDeviceStateWrapper.volumeControlData == null)
            return;

        ViewHolderVcPersistentData vData =
                (ViewHolderVcPersistentData) leAudioDeviceStateWrapper.volumeControlData.viewsData;
        if (vData == null)
            return;

        if (vData.isInputsCollapsedMutable.hasObservers())
            vData.isInputsCollapsedMutable.removeObservers(this.parent);
        vData.isInputsCollapsedMutable.observe(this.parent, aBoolean -> {
            Float rbegin = aBoolean ? 0.0f : 180.0f;
            Float rend = aBoolean ? 180.0f : 0.0f;

            ObjectAnimator.ofFloat(holder.inputFoldableIcon, "rotation", rbegin, rend)
                    .setDuration(300).start();
            holder.inputFoldable.setVisibility(aBoolean ? View.GONE : View.VISIBLE);
        });
        vData.isInputsCollapsedMutable.setValue(holder.inputFoldable.getVisibility() == View.GONE);

        if (vData.isOutputsCollapsedMutable.hasObservers())
            vData.isOutputsCollapsedMutable.removeObservers(this.parent);
        vData.isOutputsCollapsedMutable.observe(this.parent, aBoolean -> {
            Float rbegin = aBoolean ? 0.0f : 180.0f;
            Float rend = aBoolean ? 180.0f : 0.0f;

            ObjectAnimator.ofFloat(holder.outputFoldableIcon, "rotation", rbegin, rend)
                    .setDuration(300).start();
            holder.outputFoldable.setVisibility(aBoolean ? View.GONE : View.VISIBLE);
        });
        vData.isOutputsCollapsedMutable
                .setValue(holder.outputFoldable.getVisibility() == View.GONE);
    }

    private void setBassStateObservers(
            @NonNull ViewHolder holder, LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
        LeAudioDeviceStateWrapper.BassData bass_svc_data = leAudioDeviceStateWrapper.bassData;
        if (bass_svc_data != null) {
            if (bass_svc_data.isConnectedMutable.hasObservers())
                bass_svc_data.isConnectedMutable.removeObservers(this.parent);
            bass_svc_data.isConnectedMutable.observe(this.parent, is_connected -> {
                // FIXME: How to prevent the callback from firing when we set this by code
                if (is_connected != holder.bassConnectionSwitch.isChecked()) {
                    holder.bassConnectionSwitch.setActivated(false);
                    holder.bassConnectionSwitch.setChecked(is_connected);
                    holder.bassConnectionSwitch.setActivated(true);
                }

                if (holder.itemView.findViewById(R.id.bass_layout)
                        .getVisibility() != (is_connected ? View.VISIBLE : View.GONE))
                    holder.itemView.findViewById(R.id.bass_layout)
                            .setVisibility(is_connected ? View.VISIBLE : View.GONE);
            });

            holder.itemView
                    .findViewById(R.id.bass_layout)
                    .setVisibility(
                            bass_svc_data.isConnectedMutable.getValue() != null
                                            && bass_svc_data.isConnectedMutable.getValue()
                                    ? View.VISIBLE
                                    : View.GONE);

            if (bass_svc_data.receiverStatesMutable.hasActiveObservers())
                bass_svc_data.receiverStatesMutable.removeObservers(this.parent);
            bass_svc_data.receiverStatesMutable.observe(this.parent,
                    integerReceiverStateHashMap -> {
                        List<Integer> all_ids = integerReceiverStateHashMap.entrySet().stream()
                                .map(Map.Entry::getKey).collect(Collectors.toList());

                        ArrayAdapter<Integer> adapter = new ArrayAdapter(this.parent,
                                android.R.layout.simple_spinner_item, all_ids);
                        adapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item);
                        holder.bassReceiverIdSpinner.setAdapter(adapter);

                        if (bass_svc_data.viewsData != null) {
                            Integer select_pos =
                                    ((ViewHolderBassPersistentData) bass_svc_data.viewsData).selectedReceiverPositionMutable
                                            .getValue();
                            if (select_pos != null)
                                holder.bassReceiverIdSpinner.setSelection(select_pos);
                        }
                    });
        } else {
            holder.itemView.findViewById(R.id.bass_layout).setVisibility(View.GONE);
        }
    }

    private void setBassUiStateObservers(@NonNull ViewHolder holder, LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
        if (leAudioDeviceStateWrapper.bassData == null)
            return;

        ViewHolderBassPersistentData vData = (ViewHolderBassPersistentData)leAudioDeviceStateWrapper.bassData.viewsData;
        if (vData == null)
            return;

        if (vData.selectedReceiverPositionMutable.hasObservers())
            vData.selectedReceiverPositionMutable.removeObservers(this.parent);

        vData.selectedReceiverPositionMutable.observe(
                this.parent,
                aInteger -> {
                    int receiver_id =
                            Integer.parseInt(
                                    holder.bassReceiverIdSpinner
                                            .getItemAtPosition(aInteger)
                                            .toString());
                    bassInteractionListener.onReceiverSelected(
                            leAudioDeviceStateWrapper, receiver_id);

                    Map<Integer, BluetoothLeBroadcastReceiveState> states =
                            leAudioDeviceStateWrapper.bassData.receiverStatesMutable.getValue();

                    Log.d(
                            "LeAudioRecycleViewAdapter",
                            "BluetoothLeBroadcastReceiveState "
                                    + holder.bassReceiverIdSpinner.getSelectedItem());
                    if (states != null) {
                        if (states.containsKey(receiver_id)) {
                            BluetoothLeBroadcastReceiveState state =
                                    states.get(holder.bassReceiverIdSpinner.getSelectedItem());
                            int paSyncState = state.getPaSyncState();
                            int bigEncryptionState = state.getBigEncryptionState();
                            long bisSyncState = 0;

                            if (state.getNumSubgroups() == 1) {
                                bisSyncState = state.getBisSyncState().get(0);
                            } else if (state.getNumSubgroups() > 1) {
                                // TODO: Add multiple subgroup support
                                Log.w(
                                        "LeAudioRecycleViewAdapter",
                                        "There is more than one subgroup in " + "BIG");
                                bisSyncState = state.getBisSyncState().get(0);
                            }

                            Resources res = this.parent.getResources();
                            String paStateStr = null;
                            String encStateStr = null;
                            String bisSyncStateStr = null;

                            if (paSyncState == 0xffff) { // invalid sync state
                                paSyncState = PA_SYNC_STATE_IDLE;
                            }
                            if (bigEncryptionState == 0xffff) { // invalid encryption state
                                bigEncryptionState = BIG_ENCRYPTION_STATE_NOT_ENCRYPTED;
                            }
                            Log.d(
                                    "LeAudioRecycleViewAdapter",
                                    "paSyncState "
                                            + paSyncState
                                            + " bigEncryptionState"
                                            + bigEncryptionState);

                            /* PA Sync state */
                            if (paSyncState == PA_SYNC_STATE_IDLE) {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_cast_black_24dp);
                                paStateStr =
                                        res.getString(R.string.broadcast_pa_sync_state_pa_not_sync);
                            } else if (paSyncState == PA_SYNC_STATE_SYNCINFO_REQUEST) {
                                paStateStr =
                                        res.getString(
                                                R.string.broadcast_pa_sync_state_syncinfo_req);
                            } else if (paSyncState == PA_SYNC_STATE_SYNCHRONIZED) {
                                paStateStr =
                                        res.getString(R.string.broadcast_pa_sync_state_pa_sync);
                            } else if (paSyncState == PA_SYNC_STATE_FAILED_TO_SYNCHRONIZE) {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_warning_black_24dp);
                                paStateStr =
                                        res.getString(
                                                R.string.broadcast_pa_sync_state_sync_pa_failed);
                            } else if (paSyncState == PA_SYNC_STATE_NO_PAST) {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_warning_black_24dp);
                                paStateStr =
                                        res.getString(R.string.broadcast_pa_sync_state_no_past);
                            } else {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_warning_black_24dp);
                                paStateStr = res.getString(R.string.broadcast_state_rfu);
                            }

                            /* ENC state */
                            if (bigEncryptionState == BIG_ENCRYPTION_STATE_NOT_ENCRYPTED) {
                                encStateStr =
                                        res.getString(R.string.broadcast_big_encryption_not_enc);
                            } else if (bigEncryptionState == BIG_ENCRYPTION_STATE_CODE_REQUIRED) {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_vpn_key_black_24dp);
                                encStateStr =
                                        res.getString(
                                                R.string.broadcast_big_encryption_code_required);
                            } else if (bigEncryptionState == BIG_ENCRYPTION_STATE_DECRYPTING) {
                                encStateStr =
                                        res.getString(R.string.broadcast_big_encryption_decrypting);
                            } else if (bigEncryptionState == BIG_ENCRYPTION_STATE_BAD_CODE) {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_warning_black_24dp);
                                encStateStr =
                                        res.getString(R.string.broadcast_big_encryption_bad_code);
                            } else {
                                encStateStr = res.getString(R.string.broadcast_state_rfu);
                            }

                            /* BIS state */
                            if (bisSyncState == 0x00000000) {
                                bisSyncStateStr =
                                        res.getString(
                                                R.string.broadcast_bis_sync_state_bis_not_sync);
                            } else if (bisSyncState == 0xffffffff) {
                                bisSyncStateStr =
                                        res.getString(
                                                R.string.broadcast_bis_sync_state_bis_not_sync);
                            } else {
                                holder.bassScanButton.setImageResource(
                                        R.drawable.ic_bluetooth_searching_black_24dp);
                                bisSyncStateStr =
                                        res.getString(R.string.broadcast_bis_sync_state_bis_sync);
                            }

                            holder.bassReceiverPaStateText.setText(paStateStr);
                            holder.bassReceiverEncStateText.setText(encStateStr);
                            holder.bassReceiverBisStateText.setText(bisSyncStateStr);
                        }
                    }
                });
    }

    @Override
    public long getItemId(int position) {
        return devices.get(position).device.getAddress().hashCode();
    }

    @Override
    public int getItemCount() {
        return devices != null ? devices.size() : 0;
    }

    // Listeners registration routines
    // -------------------------------
    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLeAudioInteractionListener(@Nullable OnLeAudioInteractionListener listener) {
        this.leAudioInteractionListener = listener;
    }

    public void setOnVolumeControlInteractionListener(
            @Nullable OnVolumeControlInteractionListener listener) {
        this.volumeControlInteractionListener = listener;
    }

    public void setOnBassInteractionListener(@Nullable OnBassInteractionListener listener) {
        this.bassInteractionListener = listener;
    }

    public void setOnHapInteractionListener(@Nullable OnHapInteractionListener listener) {
        this.hapInteractionListener = listener;
    }

    // Device list update routine
    // -----------------------------
    public void updateLeAudioDeviceList(@Nullable List<LeAudioDeviceStateWrapper> devices) {
        this.devices.clear();
        this.devices.addAll(devices);

        // FIXME: Is this the right way of doing it?
        for (LeAudioDeviceStateWrapper dev_state : this.devices) {
            if (dev_state.volumeControlData != null)
                if (dev_state.volumeControlData.viewsData == null)
                    dev_state.volumeControlData.viewsData = new ViewHolderVcPersistentData();
            if (dev_state.bassData != null)
                if (dev_state.bassData.viewsData == null)
                    dev_state.bassData.viewsData = new ViewHolderBassPersistentData();
            if (dev_state.leAudioData != null)
                if (dev_state.leAudioData.viewsData == null)
                    dev_state.leAudioData.viewsData = new ViewHolderHapPersistentData();
        }

        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);
    }

    public interface OnLeAudioInteractionListener {
        void onConnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onDisconnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onStreamActionClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                Integer group_id, Integer content_type, Integer action);

        void onGroupSetClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                Integer group_id);

        void onGroupUnsetClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                Integer group_id);

        void onGroupDestroyClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                Integer group_id);

        void onGroupSetLockClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                Integer group_id, boolean lock);

        void onMicrophoneMuteChanged(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                boolean mute, boolean is_from_user);
    }

    public interface OnVolumeControlInteractionListener {
        void onConnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onDisconnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onVolumeChanged(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int value,
                boolean is_from_user);

        void onCheckedChanged(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                boolean is_checked);

        void onInputGetStateButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id);

        void onInputGainValueChanged(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id, int value);

        void onInputMuteSwitched(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id,
                boolean is_muted);

        void onInputSetGainModeButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id, boolean is_auto);

        void onInputGetGainPropsButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id);

        void onInputGetTypeButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id);

        void onInputGetStatusButton(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id);

        void onInputGetDescriptionButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id);

        void onInputSetDescriptionButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int input_id, String description);

        void onOutputGetGainButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int output_id);

        void onOutputGainOffsetGainValueChanged(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int output_id, int value);

        void onOutputGetLocationButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int output_id);

        void onOutputSetLocationButtonClicked(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int output_id, int location);

        void onOutputGetDescriptionButtonClicked(
                LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id);

        void onOutputSetDescriptionButton(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                int output_id, String description);
    }

    public interface OnHapInteractionListener {
        void onConnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onDisconnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onChangePresetNameClicked(BluetoothDevice device, int preset_index, String name);

        void onReadPresetInfoClicked(BluetoothDevice device, int preset_index);

        void onSetActivePresetClicked(BluetoothDevice device, int preset_index);

        void onSetActivePresetForGroupClicked(BluetoothDevice device, int preset_index);

        void onNextDevicePresetClicked(BluetoothDevice device);

        void onPreviousDevicePresetClicked(BluetoothDevice device);

        void onNextGroupPresetClicked(BluetoothDevice device);

        void onPreviousGroupPresetClicked(BluetoothDevice device);
    }

    public interface OnBassInteractionListener {
        void onConnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onDisconnectClick(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper);

        void onReceiverSelected(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int receiver_id);

        void onBroadcastCodeEntered(BluetoothDevice device, int receiver_id, byte[] broadcast_code);

        void onStopSyncReq(BluetoothDevice device, int receiver_id);

        void onRemoveSourceReq(BluetoothDevice device, int receiver_id);

        void onStopObserving();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView deviceName;

        // Le Audio View stuff
        private Switch leAudioConnectionSwitch;
        private Button leAudioStartStreamButton;
        private Button leAudioStopStreamButton;
        private Button leAudioSuspendStreamButton;
        private Button leAudioGroupSetButton;
        private Button leAudioGroupUnsetButton;
        private Button leAudioGroupDestroyButton;
        private TextView leAudioGroupIdText;
        private TextView leAudioGroupStatusText;
        private TextView leAudioGroupFlagsText;

        // Iso Set stuff
        private Button leAudioSetLockButton;
        private Button leAudioSetUnlockButton;
        private TextView leAudioSetLockStateText;

        // LeAudio Microphone stuff
        private Switch leAudioGroupMicrophoneSwitch;
        private TextView leAudioGroupMicrophoneState;

        // LeAudio HAP stuff
        private Switch hapConnectionSwitch;
        private TextView leAudioHapState;
        private TextView leAudioHapFeatures;
        private TextView leAudioHapActivePresetIndex;
        private Spinner leAudioHapPresetsSpinner;
        private Button leAudioHapChangePresetNameButton;
        private Button leAudioHapSetActivePresetButton;
        private Button leAudioHapSetActivePresetForGroupButton;
        private Button leAudioHapReadPresetInfoButton;
        private Button leAudioHapNextDevicePresetButton;
        private Button leAudioHapPreviousDevicePresetButton;
        private Button leAudioHapNextGroupPresetButton;
        private Button leAudioHapPreviousGroupPresetButton;

        // VC View stuff
        private Switch vcConnectionSwitch;
        private SeekBar volumeSeekBar;
        private Switch muteSwitch;
        // VC Ext Input stuff
        private ImageButton inputFoldableIcon;
        private View inputFoldable;
        private Spinner inputIdxSpinner;
        private ImageButton inputGetStateButton;
        private SeekBar inputGainSeekBar;
        private Switch inputMuteSwitch;
        private ImageButton inputSetGainModeButton;
        private ImageButton inputGetGainPropsButton;
        private ImageButton inputGetTypeButton;
        private ImageButton inputGetStatusButton;
        private ImageButton inputGetDescriptionButton;
        private ImageButton inputSetDescriptionButton;
        private TextView inputGainModeText;
        private TextView inputGainPropsUnitText;
        private TextView inputGainPropsMinText;
        private TextView inputGainPropsMaxText;
        private TextView inputTypeText;
        private TextView inputStatusText;
        private TextView inputDescriptionText;
        // VC Ext Output stuff
        private ImageButton outputFoldableIcon;
        private View outputFoldable;
        private Spinner outputIdxSpinner;
        private ImageButton outpuGetGainButton;
        private SeekBar outputGainOffsetSeekBar;
        private ImageButton outputGetLocationButton;
        private ImageButton outputSetLocationButton;
        private ImageButton outputGetDescriptionButton;
        private ImageButton outputSetDescriptionButton;
        private TextView outputLocationText;
        private TextView outputDescriptionText;

        // BASS View stuff
        private Switch bassConnectionSwitch;
        private Spinner bassReceiverIdSpinner;
        private TextView bassReceiverPaStateText;
        private TextView bassReceiverEncStateText;
        private TextView bassReceiverBisStateText;
        private ImageButton bassScanButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name);

            SetupLeAudioView(itemView);
            setupVcView(itemView);
            setupHapView(itemView);
            setupBassView(itemView);

            // Notify viewmodel via parent's click listener
            itemView.setOnClickListener(view -> {
                Integer position = getAdapterPosition();
                if (clickListener != null && position != RecyclerView.NO_POSITION) {
                    clickListener.onItemClick(devices.get(position));
                }
            });
        }

        private void setupHapView(@NonNull View itemView) {
            hapConnectionSwitch = itemView.findViewById(R.id.hap_switch);
            hapConnectionSwitch.setActivated(true);

            hapConnectionSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (bassInteractionListener != null) {
                    if (b)
                        hapInteractionListener
                                .onConnectClick(devices.get(ViewHolder.this.getAdapterPosition()));
                    else
                        hapInteractionListener.onDisconnectClick(
                                devices.get(ViewHolder.this.getAdapterPosition()));
                }
            });

            leAudioHapState = itemView.findViewById(R.id.hap_profile_state_text);
            leAudioHapFeatures = itemView.findViewById(R.id.hap_profile_features_text);
            leAudioHapActivePresetIndex =
                    itemView.findViewById(R.id.hap_profile_active_preset_index_text);
            leAudioHapPresetsSpinner = itemView.findViewById(R.id.hap_presets_spinner);
            leAudioHapChangePresetNameButton =
                    itemView.findViewById(R.id.hap_change_preset_name_button);
            leAudioHapSetActivePresetButton =
                    itemView.findViewById(R.id.hap_set_active_preset_button);
            leAudioHapSetActivePresetForGroupButton =
                    itemView.findViewById(R.id.hap_set_active_preset_for_group_button);
            leAudioHapReadPresetInfoButton =
                    itemView.findViewById(R.id.hap_read_preset_info_button);
            leAudioHapNextDevicePresetButton =
                    itemView.findViewById(R.id.hap_next_device_preset_button);
            leAudioHapPreviousDevicePresetButton =
                    itemView.findViewById(R.id.hap_previous_device_preset_button);
            leAudioHapNextGroupPresetButton =
                    itemView.findViewById(R.id.hap_next_group_preset_button);
            leAudioHapPreviousGroupPresetButton =
                    itemView.findViewById(R.id.hap_previous_group_preset_button);

            leAudioHapPresetsSpinner
                    .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> adapterView, View view,
                                int position, long l) {
                            LeAudioDeviceStateWrapper device =
                                    devices.get(ViewHolder.this.getAdapterPosition());
                            ((ViewHolderHapPersistentData) device.leAudioData.viewsData).selectedPresetPositionMutable
                                    .setValue(position);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> adapterView) {
                            // Nothing to do here
                        }
                    });

            leAudioHapChangePresetNameButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    if (leAudioHapPresetsSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known preset, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                    alert.setTitle("Set a name");
                    final EditText input = new EditText(itemView.getContext());
                    alert.setView(input);
                    alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                        Integer index = Integer.valueOf(leAudioHapPresetsSpinner.getSelectedItem()
                                .toString().split("\\s")[0]);
                        hapInteractionListener.onChangePresetNameClicked(
                                devices.get(ViewHolder.this.getAdapterPosition()).device, index,
                                input.getText().toString());
                    });
                    alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                        // Do nothing
                    });
                    alert.show();
                }
            });

            leAudioHapSetActivePresetButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    if (leAudioHapPresetsSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known preset, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer index = Integer.valueOf(
                            leAudioHapPresetsSpinner.getSelectedItem().toString().split("\\s")[0]);
                    hapInteractionListener.onSetActivePresetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device, index);
                }
            });

            leAudioHapSetActivePresetForGroupButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    if (leAudioHapPresetsSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known preset, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer index = Integer.valueOf(
                            leAudioHapPresetsSpinner.getSelectedItem().toString().split("\\s")[0]);
                    hapInteractionListener.onSetActivePresetForGroupClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device, index);
                }
            });

            leAudioHapReadPresetInfoButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    if (leAudioHapPresetsSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known preset, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer index = Integer.valueOf(
                            leAudioHapPresetsSpinner.getSelectedItem().toString().split("\\s")[0]);
                    hapInteractionListener.onReadPresetInfoClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device, index);
                }
            });

            leAudioHapNextDevicePresetButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    hapInteractionListener.onNextDevicePresetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device);
                }
            });

            leAudioHapPreviousDevicePresetButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    hapInteractionListener.onPreviousDevicePresetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device);
                }
            });

            leAudioHapNextGroupPresetButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    hapInteractionListener.onNextGroupPresetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device);
                }
            });

            leAudioHapPreviousGroupPresetButton.setOnClickListener(view -> {
                if (hapInteractionListener != null) {
                    hapInteractionListener.onPreviousGroupPresetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()).device);
                }
            });
        }

        private void SetupLeAudioView(@NonNull View itemView) {
            leAudioConnectionSwitch = itemView.findViewById(R.id.le_audio_switch);
            leAudioStartStreamButton = itemView.findViewById(R.id.start_stream_button);
            leAudioStopStreamButton = itemView.findViewById(R.id.stop_stream_button);
            leAudioSuspendStreamButton = itemView.findViewById(R.id.suspend_stream_button);
            leAudioGroupSetButton = itemView.findViewById(R.id.group_set_button);
            leAudioGroupUnsetButton = itemView.findViewById(R.id.group_unset_button);
            leAudioGroupDestroyButton = itemView.findViewById(R.id.group_destroy_button);
            leAudioGroupIdText = itemView.findViewById(R.id.group_id_text);
            leAudioGroupStatusText = itemView.findViewById(R.id.group_status_text);
            leAudioGroupFlagsText = itemView.findViewById(R.id.group_flags_text);
            leAudioSetLockButton = itemView.findViewById(R.id.set_lock_button);
            leAudioSetUnlockButton = itemView.findViewById(R.id.set_unlock_button);
            leAudioSetLockStateText = itemView.findViewById(R.id.lock_state_text);
            leAudioGroupMicrophoneSwitch = itemView.findViewById(R.id.group_mic_mute_state_switch);
            leAudioGroupMicrophoneState = itemView.findViewById(R.id.group_mic_mute_state_text);

            leAudioConnectionSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (leAudioInteractionListener != null) {
                    if (b)
                        leAudioInteractionListener
                                .onConnectClick(devices.get(ViewHolder.this.getAdapterPosition()));
                    else
                        leAudioInteractionListener.onDisconnectClick(
                                devices.get(ViewHolder.this.getAdapterPosition()));
                }
            });

            leAudioStartStreamButton.setOnClickListener(view -> {
                AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                alert.setTitle("Pick a content type");
                NumberPicker input = new NumberPicker(itemView.getContext());
                input.setMinValue(1);
                input.setMaxValue(
                        itemView.getResources().getStringArray(R.array.content_types).length - 1);
                input.setDisplayedValues(
                        itemView.getResources().getStringArray(R.array.content_types));
                alert.setView(input);
                alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                    final Integer group_id = Integer
                            .parseInt(ViewHolder.this.leAudioGroupIdText.getText().toString());
                    if (leAudioInteractionListener != null && group_id != null)
                        leAudioInteractionListener.onStreamActionClicked(
                                devices.get(ViewHolder.this.getAdapterPosition()), group_id,
                                1 << (input.getValue() - 1), 0);
                });
                alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                    // Do nothing
                });
                alert.show();
            });

            leAudioSuspendStreamButton.setOnClickListener(view -> {
                final Integer group_id =
                        Integer.parseInt(ViewHolder.this.leAudioGroupIdText.getText().toString());
                if (leAudioInteractionListener != null && group_id != null)
                    leAudioInteractionListener.onStreamActionClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id, 0, 1);
            });

            leAudioStopStreamButton.setOnClickListener(view -> {
                final Integer group_id =
                        Integer.parseInt(ViewHolder.this.leAudioGroupIdText.getText().toString());
                if (leAudioInteractionListener != null && group_id != null)
                    leAudioInteractionListener.onStreamActionClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id, 0, 2);
            });

            leAudioGroupSetButton.setOnClickListener(view -> {
                AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                alert.setTitle("Pick a group ID");
                final EditText input = new EditText(itemView.getContext());
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setRawInputType(Configuration.KEYBOARD_12KEY);
                alert.setView(input);
                alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                    final Integer group_id = Integer.valueOf(input.getText().toString());
                    leAudioInteractionListener.onGroupSetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id);
                });
                alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                    // Do nothing
                });
                alert.show();
            });

            leAudioGroupUnsetButton.setOnClickListener(view -> {
                final Integer group_id = Integer.parseInt(
                        ViewHolder.this.leAudioGroupIdText.getText().toString().equals("Unknown")
                                ? "0"
                                : ViewHolder.this.leAudioGroupIdText.getText().toString());
                if (leAudioInteractionListener != null)
                    leAudioInteractionListener.onGroupUnsetClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id);
            });

            leAudioGroupDestroyButton.setOnClickListener(view -> {
                final Integer group_id =
                        Integer.parseInt(ViewHolder.this.leAudioGroupIdText.getText().toString());
                if (leAudioInteractionListener != null)
                    leAudioInteractionListener.onGroupDestroyClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id);
            });

            leAudioSetLockButton.setOnClickListener(view -> {
                AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                alert.setTitle("Pick a group ID");
                final EditText input = new EditText(itemView.getContext());
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setRawInputType(Configuration.KEYBOARD_12KEY);
                alert.setView(input);
                alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                    final Integer group_id = Integer.valueOf(input.getText().toString());
                    if (leAudioInteractionListener != null)
                        leAudioInteractionListener.onGroupSetLockClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id, true);

                });
                alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                    // Do nothing
                });
                alert.show();
            });

            leAudioSetUnlockButton.setOnClickListener(view -> {
                AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                alert.setTitle("Pick a group ID");
                final EditText input = new EditText(itemView.getContext());
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setRawInputType(Configuration.KEYBOARD_12KEY);
                alert.setView(input);
                alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                    final Integer group_id = Integer.valueOf(input.getText().toString());
                    if (leAudioInteractionListener != null)
                        leAudioInteractionListener.onGroupSetLockClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), group_id, false);

                });
                alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                    // Do nothing
                });
                alert.show();
            });

            leAudioGroupMicrophoneSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (leAudioInteractionListener != null)
                    leAudioInteractionListener.onMicrophoneMuteChanged(
                            devices.get(ViewHolder.this.getAdapterPosition()), b, true);
            });
        }

        private void setupVcView(@NonNull View itemView) {
            vcConnectionSwitch = itemView.findViewById(R.id.vc_switch);
            vcConnectionSwitch.setActivated(true);
            volumeSeekBar = itemView.findViewById(R.id.volume_seek_bar);
            muteSwitch = itemView.findViewById(R.id.mute_switch);
            muteSwitch.setActivated(true);
            inputFoldableIcon = itemView.findViewById(R.id.vc_input_foldable_icon);
            inputFoldable = itemView.findViewById(R.id.ext_input_foldable);
            inputIdxSpinner = itemView.findViewById(R.id.num_inputs_spinner);
            inputGetStateButton = itemView.findViewById(R.id.inputGetStateButton);
            inputGainSeekBar = itemView.findViewById(R.id.inputGainSeekBar);
            inputMuteSwitch = itemView.findViewById(R.id.inputMuteSwitch);
            inputMuteSwitch.setActivated(true);
            inputSetGainModeButton = itemView.findViewById(R.id.inputSetGainModeButton);
            inputGetGainPropsButton = itemView.findViewById(R.id.inputGetGainPropsButton);
            inputGetTypeButton = itemView.findViewById(R.id.inputGetTypeButton);
            inputGetStatusButton = itemView.findViewById(R.id.inputGetStatusButton);
            inputGetDescriptionButton = itemView.findViewById(R.id.inputGetDescriptionButton);
            inputSetDescriptionButton = itemView.findViewById(R.id.inputSetDescriptionButton);
            inputGainModeText = itemView.findViewById(R.id.inputGainModeText);
            inputGainPropsUnitText = itemView.findViewById(R.id.inputGainPropsUnitText);
            inputGainPropsMinText = itemView.findViewById(R.id.inputGainPropsMinText);
            inputGainPropsMaxText = itemView.findViewById(R.id.inputGainPropsMaxText);
            inputTypeText = itemView.findViewById(R.id.inputTypeText);
            inputStatusText = itemView.findViewById(R.id.inputStatusText);
            inputDescriptionText = itemView.findViewById(R.id.inputDescriptionText);

            outputFoldableIcon = itemView.findViewById(R.id.vc_output_foldable_icon);
            outputFoldable = itemView.findViewById(R.id.ext_output_foldable);
            outputIdxSpinner = itemView.findViewById(R.id.num_outputs_spinner);
            outpuGetGainButton = itemView.findViewById(R.id.outputGetGainButton);
            outputGainOffsetSeekBar = itemView.findViewById(R.id.outputGainSeekBar);
            outputGetLocationButton = itemView.findViewById(R.id.outputGetLocationButton);
            outputSetLocationButton = itemView.findViewById(R.id.outputSetLocationButton);
            outputGetDescriptionButton = itemView.findViewById(R.id.outputGetDescriptionButton);
            outputSetDescriptionButton = itemView.findViewById(R.id.outputSetDescriptionButton);
            outputLocationText = itemView.findViewById(R.id.outputLocationText);
            outputDescriptionText = itemView.findViewById(R.id.outputDescriptionText);

            vcConnectionSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (volumeControlInteractionListener != null) {
                    if (b)
                        volumeControlInteractionListener
                                .onConnectClick(devices.get(ViewHolder.this.getAdapterPosition()));
                    else
                        volumeControlInteractionListener.onDisconnectClick(
                                devices.get(ViewHolder.this.getAdapterPosition()));
                }
            });

            volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    // Nothing to do here
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Nothing to do here
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Set value only on release
                    if (volumeControlInteractionListener != null)
                        volumeControlInteractionListener.onVolumeChanged(
                                devices.get(ViewHolder.this.getAdapterPosition()),
                                seekBar.getProgress(), true);
                }
            });

            muteSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (volumeControlInteractionListener != null)
                    volumeControlInteractionListener
                            .onCheckedChanged(devices.get(ViewHolder.this.getAdapterPosition()), b);
            });

            inputFoldableIcon.setOnClickListener(view -> {
                ViewHolderVcPersistentData vData = (ViewHolderVcPersistentData) devices
                        .get(ViewHolder.this.getAdapterPosition()).volumeControlData.viewsData;
                if (vData != null)
                    vData.isInputsCollapsedMutable
                            .setValue(!vData.isInputsCollapsedMutable.getValue());
            });

            inputIdxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                        long l) {
                    Integer index = ViewHolder.this.getAdapterPosition();
                    ((ViewHolderVcPersistentData) devices
                            .get(index).volumeControlData.viewsData).selectedInputPosition =
                                    position;
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Nothing to do here
                }
            });

            outputFoldableIcon.setOnClickListener(view -> {
                ViewHolderVcPersistentData vData = (ViewHolderVcPersistentData) devices
                        .get(ViewHolder.this.getAdapterPosition()).volumeControlData.viewsData;
                vData.isOutputsCollapsedMutable
                        .setValue(!vData.isOutputsCollapsedMutable.getValue());
            });

            outputIdxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                        long l) {
                    Integer index = ViewHolder.this.getAdapterPosition();
                    ((ViewHolderVcPersistentData) devices
                            .get(index).volumeControlData.viewsData).selectedOutputPosition =
                                    position;
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Nothing to do here
                }
            });

            inputGetStateButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Integer input_id =
                            Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onInputGetStateButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), input_id);
                }
            });

            inputGainSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean is_user_set) {
                    // Nothing to do here
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // Nothing to do here
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (volumeControlInteractionListener != null) {
                        if (inputIdxSpinner.getSelectedItem() == null) {
                            Toast.makeText(seekBar.getContext(),
                                    "No known ext. input, please reconnect.", Toast.LENGTH_SHORT)
                                    .show();
                            return;
                        }
                        Integer input_id =
                                Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                        volumeControlInteractionListener.onInputGainValueChanged(
                                devices.get(ViewHolder.this.getAdapterPosition()), input_id,
                                seekBar.getProgress());
                    }
                }
            });

            inputMuteSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(compoundButton.getContext(),
                                "No known ext. input, please reconnect.", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    Integer input_id =
                            Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onInputMuteSwitched(
                            devices.get(ViewHolder.this.getAdapterPosition()), input_id, b);
                }
            });

            inputSetGainModeButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                    alert.setTitle("Select Gain mode");
                    NumberPicker input = new NumberPicker(itemView.getContext());
                    input.setMinValue(0);
                    input.setMaxValue(2);
                    input.setDisplayedValues(
                            itemView.getResources().getStringArray(R.array.gain_modes));
                    alert.setView(input);
                    alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                        Integer input_id =
                                Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                        volumeControlInteractionListener.onInputSetGainModeButtonClicked(
                                devices.get(ViewHolder.this.getAdapterPosition()), input_id,
                                input.getValue() == 2);
                    });
                    alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                        // Do nothing
                    });
                    alert.show();
                }
            });

            inputGetGainPropsButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Integer input_id =
                            Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onInputGetGainPropsButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), input_id);
                }
            });

            inputGetTypeButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Integer input_id =
                            Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onInputGetTypeButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), input_id);
                }
            });

            inputGetStatusButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Integer input_id =
                            Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onInputGetStatusButton(
                            devices.get(ViewHolder.this.getAdapterPosition()), input_id);
                }
            });

            inputGetDescriptionButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Integer input_id =
                            Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onInputGetDescriptionButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), input_id);
                }
            });

            inputSetDescriptionButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (inputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. input, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                    alert.setTitle("Set a description");
                    final EditText input = new EditText(itemView.getContext());
                    alert.setView(input);
                    alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                        Integer input_id =
                                Integer.valueOf(inputIdxSpinner.getSelectedItem().toString());
                        volumeControlInteractionListener.onInputSetDescriptionButtonClicked(
                                devices.get(ViewHolder.this.getAdapterPosition()), input_id,
                                input.getText().toString());
                    });
                    alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                        // Do nothing
                    });
                    alert.show();
                }
            });

            outpuGetGainButton.setOnClickListener(view -> {
                if (outputIdxSpinner.getSelectedItem() == null) {
                    Toast.makeText(view.getContext(), "No known ext. output, please reconnect.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Integer output_id = Integer.valueOf(outputIdxSpinner.getSelectedItem().toString());
                if (volumeControlInteractionListener != null)
                    volumeControlInteractionListener.onOutputGetGainButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), output_id);
            });

            outputGainOffsetSeekBar
                    .setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int value,
                                boolean is_from_user) {
                            // Do nothing here
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                            // Do nothing here
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            if (outputIdxSpinner.getSelectedItem() == null) {
                                Toast.makeText(seekBar.getContext(),
                                        "No known ext. output, please reconnect.",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Integer output_id =
                                    Integer.valueOf(outputIdxSpinner.getSelectedItem().toString());
                            if (volumeControlInteractionListener != null)
                                volumeControlInteractionListener.onOutputGainOffsetGainValueChanged(
                                        devices.get(ViewHolder.this.getAdapterPosition()),
                                        output_id, seekBar.getProgress());
                        }
                    });

            outputGetLocationButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (outputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. output, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer output_id =
                            Integer.valueOf(outputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onOutputGetLocationButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), output_id);
                }
            });

            outputSetLocationButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (outputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. output, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                    alert.setTitle("Pick an Audio Location");
                    NumberPicker input = new NumberPicker(itemView.getContext());
                    input.setMinValue(0);
                    input.setMaxValue(
                            itemView.getResources().getStringArray(R.array.audio_locations).length
                                    - 1);
                    input.setDisplayedValues(
                            itemView.getResources().getStringArray(R.array.audio_locations));
                    alert.setView(input);
                    alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                        Integer output_id =
                                Integer.valueOf(outputIdxSpinner.getSelectedItem().toString());
                        volumeControlInteractionListener.onOutputSetLocationButtonClicked(
                                devices.get(ViewHolder.this.getAdapterPosition()), output_id,
                                input.getValue());
                    });
                    alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                        // Do nothing
                    });
                    alert.show();
                }
            });

            outputGetDescriptionButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (outputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. output, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Integer output_id =
                            Integer.valueOf(outputIdxSpinner.getSelectedItem().toString());
                    volumeControlInteractionListener.onOutputGetDescriptionButtonClicked(
                            devices.get(ViewHolder.this.getAdapterPosition()), output_id);
                }
            });

            outputSetDescriptionButton.setOnClickListener(view -> {
                if (volumeControlInteractionListener != null) {
                    if (outputIdxSpinner.getSelectedItem() == null) {
                        Toast.makeText(view.getContext(), "No known ext. output, please reconnect.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder alert = new AlertDialog.Builder(itemView.getContext());
                    alert.setTitle("Set a description");
                    final EditText input = new EditText(itemView.getContext());
                    alert.setView(input);
                    alert.setPositiveButton("Ok", (dialog, whichButton) -> {
                        Integer output_id =
                                Integer.valueOf(outputIdxSpinner.getSelectedItem().toString());
                        volumeControlInteractionListener.onOutputSetDescriptionButton(
                                devices.get(ViewHolder.this.getAdapterPosition()), output_id,
                                input.getText().toString());
                    });
                    alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
                        // Do nothing
                    });
                    alert.show();
                }
            });
        }

        private void setupBassView(@NonNull View itemView) {
            bassConnectionSwitch = itemView.findViewById(R.id.bass_switch);
            bassConnectionSwitch.setActivated(true);
            bassReceiverIdSpinner = itemView.findViewById(R.id.num_receiver_spinner);
            bassReceiverPaStateText = itemView.findViewById(R.id.receiver_pa_state_text);
            bassReceiverEncStateText = itemView.findViewById(R.id.receiver_enc_state_text);
            bassReceiverBisStateText = itemView.findViewById(R.id.receiver_bis_state_text);
            bassScanButton = itemView.findViewById(R.id.broadcast_button);

            bassConnectionSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (!compoundButton.isActivated())
                    return;

                if (bassInteractionListener != null) {
                    if (b)
                        bassInteractionListener.onConnectClick(
                                devices.get(ViewHolder.this.getAdapterPosition()));
                    else
                        bassInteractionListener.onDisconnectClick(
                                devices.get(ViewHolder.this.getAdapterPosition()));
                }
            });

            bassReceiverIdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                    LeAudioDeviceStateWrapper device = devices.get(ViewHolder.this.getAdapterPosition());
                    ((ViewHolderBassPersistentData) device.bassData.viewsData).selectedReceiverPositionMutable.setValue(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Nothing to do here
                }
            });

            bassScanButton.setOnClickListener(
                    view -> {
                        /* Broadcast receiver is not selected */
                        if (bassReceiverIdSpinner.getSelectedItem() == null) {
                            Toast.makeText(
                                            view.getContext(),
                                            "Receiver not selected",
                                            Toast.LENGTH_SHORT)
                                    .show();
                            return;
                        }

                        int receiver_id =
                                Integer.parseInt(
                                        bassReceiverIdSpinner.getSelectedItem().toString());
                        LeAudioDeviceStateWrapper leAudioDeviceStateWrapper =
                                devices.get(ViewHolder.this.getAdapterPosition());
                        bassInteractionListener.onReceiverSelected(
                                leAudioDeviceStateWrapper, receiver_id);

                        Map<Integer, BluetoothLeBroadcastReceiveState> states =
                                leAudioDeviceStateWrapper.bassData.receiverStatesMutable.getValue();

                        if (states != null) {
                            if (states.containsKey(receiver_id)) {
                                BluetoothLeBroadcastReceiveState state =
                                        states.get(bassReceiverIdSpinner.getSelectedItem());
                                int paSyncState = state.getPaSyncState();
                                int bigEncryptionState = state.getBigEncryptionState();
                                long bisSyncState = 0;

                                if (state.getNumSubgroups() == 1) {
                                    bisSyncState = state.getBisSyncState().get(0);
                                } else if (state.getNumSubgroups() > 1) {
                                    // TODO: Add multiple subgroup support
                                    Log.w(
                                            "LeAudioRecycleViewAdapter",
                                            "There is more than one subgroup in " + "BIG");
                                    bisSyncState = state.getBisSyncState().get(0);
                                }

                                /* Stop synchronization */
                                if ((paSyncState != PA_SYNC_STATE_IDLE)
                                        || (bisSyncState != 0x00000000)) {
                                    AlertDialog.Builder alert =
                                            new AlertDialog.Builder(itemView.getContext());
                                    alert.setTitle("Stop the synchronization?");

                                    BluetoothDevice device =
                                            devices.get(ViewHolder.this.getAdapterPosition())
                                                    .device;
                                    if (bassReceiverIdSpinner.getSelectedItem() == null) {
                                        Toast.makeText(
                                                        view.getContext(),
                                                        "Not available",
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                        return;
                                    }

                                    alert.setPositiveButton(
                                            "Yes",
                                            (dialog, whichButton) -> {
                                                bassInteractionListener.onRemoveSourceReq(
                                                        device, receiver_id);
                                            });
                                    // FIXME: To modify source we need the valid broadcaster_id
                                    // context so
                                    //        we should start scan here again
                                    // alert.setNeutralButton("Modify", (dialog, whichButton) -> {
                                    //
                                    // TODO: Open the scan dialog to get the broadcast_id
                                    //       bassInteractionListener.onStopSyncReq(device,
                                    // receiver_id,
                                    //       broadcast_id);
                                    // });
                                    alert.setNegativeButton(
                                            "No",
                                            (dialog, whichButton) -> {
                                                // Do nothing
                                            });
                                    alert.show();
                                } else if (bisSyncState == 0x00000000) {
                                    /* Add or Remove source */
                                    AlertDialog.Builder alert =
                                            new AlertDialog.Builder(itemView.getContext());
                                    alert.setTitle(
                                            "Scan and add a source or remove the currently set "
                                                    + "one.");

                                    BluetoothDevice device =
                                            devices.get(ViewHolder.this.getAdapterPosition())
                                                    .device;

                                    alert.setPositiveButton(
                                            "Scan",
                                            (dialog, whichButton) -> {
                                                // Scan for new announcements
                                                Intent intent =
                                                        new Intent(
                                                                this.itemView.getContext(),
                                                                BroadcastScanActivity.class);
                                                intent.addFlags(
                                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                                intent.putExtra(
                                                        BluetoothDevice.EXTRA_DEVICE,
                                                        devices.get(
                                                                        ViewHolder.this
                                                                                .getAdapterPosition())
                                                                .device);
                                                parent.startActivityForResult(intent, 666);
                                            });
                                    alert.setNeutralButton(
                                            "Cancel",
                                            (dialog, whichButton) -> {
                                                // Do nothing
                                            });
                                    if (receiver_id != -1) {
                                        final int remove_receiver_id = receiver_id;
                                        alert.setNegativeButton(
                                                "Remove",
                                                (dialog, whichButton) -> {
                                                    bassInteractionListener.onRemoveSourceReq(
                                                            device, remove_receiver_id);
                                                });
                                    }
                                    alert.show();
                                } else if ((bigEncryptionState == BIG_ENCRYPTION_STATE_BAD_CODE)
                                        || (bigEncryptionState
                                                == BIG_ENCRYPTION_STATE_CODE_REQUIRED)) {
                                    /* Deliver broadcast key */
                                    AlertDialog.Builder alert =
                                            new AlertDialog.Builder(itemView.getContext());
                                    alert.setTitle("Please enter broadcast encryption code...");
                                    EditText pass_input_view = new EditText(itemView.getContext());
                                    pass_input_view.setFilters(
                                            new InputFilter[] {new InputFilter.LengthFilter(16)});
                                    alert.setView(pass_input_view);

                                    BluetoothDevice device =
                                            devices.get(ViewHolder.this.getAdapterPosition())
                                                    .device;
                                    if (bassReceiverIdSpinner.getSelectedItem() == null) {
                                        Toast.makeText(
                                                        view.getContext(),
                                                        "Not available",
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                        return;
                                    }

                                    alert.setPositiveButton(
                                            "Set",
                                            (dialog, whichButton) -> {
                                                byte[] code =
                                                        pass_input_view
                                                                .getText()
                                                                .toString()
                                                                .getBytes();
                                                bassInteractionListener.onBroadcastCodeEntered(
                                                        device, receiver_id, code);
                                            });
                                    alert.setNegativeButton(
                                            "Cancel",
                                            (dialog, whichButton) -> {
                                                // Do nothing
                                            });
                                    alert.show();
                                }
                            }
                        }
                    });
        }
    }

    private class ViewHolderVcPersistentData {
        Integer selectedInputPosition;
        Integer selectedOutputPosition;

        MutableLiveData<Boolean> isInputsCollapsedMutable = new MutableLiveData<>();
        MutableLiveData<Boolean> isOutputsCollapsedMutable = new MutableLiveData<>();
    }

    private class ViewHolderBassPersistentData {
        MutableLiveData<Integer> selectedReceiverPositionMutable = new MutableLiveData<>();
    }

    private class ViewHolderHapPersistentData {
        MutableLiveData<Integer> selectedPresetPositionMutable = new MutableLiveData<>();
    }
}
