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

import android.Manifest;
import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_PRIVILEGED,
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.ACCESS_FINE_LOCATION,};
    LeAudioRecycleViewAdapter recyclerViewAdapter;
    private LeAudioViewModel leAudioViewModel;

    /** Returns true if any of the required permissions is missing. */
    private boolean isPermissionMissing() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this,
                    permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private void initialize() {
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup each component
        setupLeAudioViewModel();
        setupRecyclerViewAdapter();
        setupViewModelObservers();

        // The 'refresh devices' button
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            leAudioViewModel.queryDevices();
            ObjectAnimator.ofFloat(fab, "rotation", 0f, 360f).setDuration(500).start();
        });
    }

    /** Request permission if missing. */
    private void setupPermissions() {
        if (isPermissionMissing()) {
            ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                        for (String permission : REQUIRED_PERMISSIONS) {
                            if (!Objects.requireNonNull(result.get(permission))) {
                                Toast.makeText(getApplicationContext(),
                                        "LeAudio test apk permission denied.", Toast.LENGTH_SHORT)
                                        .show();
                                finish();
                                return;
                            }
                        }
                        initialize();
                    });

            permissionLauncher.launch(REQUIRED_PERMISSIONS);
        } else {
            initialize();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setupPermissions();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupLeAudioViewModel();
        cleanupRecyclerViewAdapter();
        cleanupViewModelObservers();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Intent intent = null;

        switch (item.getItemId()) {
            case R.id.action_scan:
                // Clicking this gives no device or receiver context - no extras for this intent.
                intent = new Intent(MainActivity.this, BroadcastScanActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_broadcast:
                if (leAudioViewModel.getBluetoothEnabledLive().getValue() == null
                        || !leAudioViewModel.getBluetoothEnabledLive().getValue()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, 1);
                } else if (leAudioViewModel.isLeAudioBroadcastSourceSupported()) {
                    intent = new Intent(MainActivity.this, BroadcasterActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivityForResult(intent, 0);
                } else {
                    Toast.makeText(MainActivity.this, "Broadcast Source is not supported.",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.onCreate
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // check if the request code is same as what was passed in request
        if (requestCode == 0xc0de) {
            if (intent != null) {
                String message = intent.getStringExtra("MESSAGE");
                Toast.makeText(MainActivity.this, message + "(" + resultCode + ")",
                        Toast.LENGTH_SHORT).show();
            }

            // TODO: Depending on the resultCode we should either stop the sync or try the PAST
            leAudioViewModel.stopBroadcastObserving();
        }
    }

    @Override
    public void onBackPressed() {
        finishActivity(0);
        super.onBackPressed();
    }

    private void setupLeAudioViewModel() {
        leAudioViewModel = ViewModelProviders.of(this).get(LeAudioViewModel.class);

        // Observe bluetooth adapter state
        leAudioViewModel.getBluetoothEnabledLive().observe(this, is_enabled -> {
            if (is_enabled) {
                List<LeAudioDeviceStateWrapper> deviceList =
                        leAudioViewModel.getAllLeAudioDevicesLive().getValue();
                if (deviceList == null || deviceList.size() == 0)
                    leAudioViewModel.queryDevices();
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }

            Toast.makeText(MainActivity.this,
                    "Bluetooth is " + (is_enabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT)
                    .show();
        });
    }

    private void cleanupLeAudioViewModel() {
        leAudioViewModel.getBluetoothEnabledLive().removeObservers(this);
    }

    void setupRecyclerViewAdapter() {
        recyclerViewAdapter = new LeAudioRecycleViewAdapter(this);

        // Set listeners
        setupViewsListItemClickListener();
        setupViewsProfileUiEventListeners();

        // Generic stuff
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(recyclerViewAdapter);
        recyclerView.setHasFixedSize(true);
    }

    void cleanupRecyclerViewAdapter() {
        cleanupViewsListItemClickListener();
        cleanupViewsProfileUiEventListeners();
    }

    private void setupViewsListItemClickListener() {
        recyclerViewAdapter.setOnItemClickListener(device -> {
            // Not used anymore
        });
    }

    private void cleanupViewsListItemClickListener() {
        recyclerViewAdapter.setOnItemClickListener(null);
    }

    private void setupViewsProfileUiEventListeners() {
        recyclerViewAdapter.setOnLeAudioInteractionListener(
                new LeAudioRecycleViewAdapter.OnLeAudioInteractionListener() {
                    @Override
                    public void onConnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        Toast.makeText(MainActivity.this,
                                "Connecting Le Audio to "
                                        + leAudioDeviceStateWrapper.device.toString(),
                                Toast.LENGTH_SHORT).show();
                        leAudioViewModel.connectLeAudio(leAudioDeviceStateWrapper.device, true);
                    }

                    @Override
                    public void onDisconnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        Toast.makeText(MainActivity.this,
                                "Disconnecting Le Audio from "
                                        + leAudioDeviceStateWrapper.device.toString(),
                                Toast.LENGTH_SHORT).show();
                        leAudioViewModel.connectLeAudio(leAudioDeviceStateWrapper.device, false);
                    }

                    @Override
                    public void onStreamActionClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, Integer group_id,
                            Integer content_type, Integer action) {
                        leAudioViewModel.streamAction(group_id, action, content_type);
                    }

                    @Override
                    public void onGroupSetClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, Integer group_id) {
                        leAudioViewModel.groupSet(leAudioDeviceStateWrapper.device, group_id);
                    }

                    @Override
                    public void onGroupUnsetClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, Integer group_id) {
                        leAudioViewModel.groupUnset(leAudioDeviceStateWrapper.device, group_id);
                    }

                    @Override
                    public void onGroupDestroyClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, Integer group_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onGroupSetLockClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, Integer group_id,
                            boolean lock) {
                        leAudioViewModel.groupSetLock(group_id, lock);
                    }

                    @Override
                    public void onMicrophoneMuteChanged(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, boolean mute,
                            boolean is_from_user) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        recyclerViewAdapter.setOnVolumeControlInteractionListener(
                new LeAudioRecycleViewAdapter.OnVolumeControlInteractionListener() {
                    @Override
                    public void onConnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onDisconnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onVolumeChanged(LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                            int volume, boolean is_from_user) {
                        if (is_from_user) {
                            leAudioViewModel.setVolume(leAudioDeviceStateWrapper.device, volume);
                        }
                    }

                    @Override
                    public void onCheckedChanged(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper,
                            boolean is_checked) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputGetStateButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputGainValueChanged(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id,
                            int value) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputMuteSwitched(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id,
                            boolean is_muted) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputSetGainModeButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id,
                            boolean is_auto) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputGetGainPropsButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputGetTypeButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputGetStatusButton(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputGetDescriptionButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onInputSetDescriptionButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int input_id,
                            String description) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onOutputGetGainButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onOutputGainOffsetGainValueChanged(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id,
                            int value) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onOutputGetLocationButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onOutputSetLocationButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id,
                            int location) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onOutputGetDescriptionButtonClicked(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onOutputSetDescriptionButton(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int output_id,
                            String description) {
                        // Not available anymore
                        Toast.makeText(MainActivity.this,
                                "Operation not supported on this API version", Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        recyclerViewAdapter.setOnHapInteractionListener(
                new LeAudioRecycleViewAdapter.OnHapInteractionListener() {
                    @Override
                    public void onConnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        Toast.makeText(MainActivity.this,
                                "Connecting HAP to " + leAudioDeviceStateWrapper.device.toString(),
                                Toast.LENGTH_SHORT).show();
                        leAudioViewModel.connectHap(leAudioDeviceStateWrapper.device, true);
                    }

                    @Override
                    public void onDisconnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        Toast.makeText(MainActivity.this,
                                "Disconnecting HAP from "
                                        + leAudioDeviceStateWrapper.device.toString(),
                                Toast.LENGTH_SHORT).show();
                        leAudioViewModel.connectHap(leAudioDeviceStateWrapper.device, false);
                    }

                    @Override
                    public void onReadPresetInfoClicked(BluetoothDevice device, int preset_index) {
                        leAudioViewModel.hapReadPresetInfo(device, preset_index);
                    }

                    @Override
                    public void onSetActivePresetClicked(BluetoothDevice device, int preset_index) {
                        leAudioViewModel.hapSetActivePreset(device, preset_index);
                    }

                    @Override
                    public void onSetActivePresetForGroupClicked(BluetoothDevice device, int preset_index) {
                        leAudioViewModel.hapSetActivePresetForGroup(device, preset_index);
                    }

                    @Override
                    public void onChangePresetNameClicked(BluetoothDevice device, int preset_index,
                            String name) {
                        leAudioViewModel.hapChangePresetName(device, preset_index, name);
                    }

                    @Override
                    public void onPreviousDevicePresetClicked(BluetoothDevice device) {
                        leAudioViewModel.hapPreviousDevicePreset(device);
                    }

                    @Override
                    public void onNextDevicePresetClicked(BluetoothDevice device) {
                        leAudioViewModel.hapNextDevicePreset(device);
                    }

                    @Override
                    public void onPreviousGroupPresetClicked(BluetoothDevice device) {
                        final int group_id = leAudioViewModel.hapGetHapGroup(device);
                        final boolean sent = leAudioViewModel.hapPreviousGroupPreset(group_id);
                        if (!sent)
                            Toast.makeText(MainActivity.this,
                                    "Group " + group_id + " operation failed", Toast.LENGTH_SHORT)
                                    .show();
                    }

                    @Override
                    public void onNextGroupPresetClicked(BluetoothDevice device) {
                        final int group_id = leAudioViewModel.hapGetHapGroup(device);
                        final boolean sent = leAudioViewModel.hapNextGroupPreset(group_id);
                        if (!sent)
                            Toast.makeText(MainActivity.this,
                                    "Group " + group_id + " operation failed", Toast.LENGTH_SHORT)
                                    .show();
                    }
                });

        recyclerViewAdapter.setOnBassInteractionListener(
                new LeAudioRecycleViewAdapter.OnBassInteractionListener() {
                    @Override
                    public void onConnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        Toast.makeText(MainActivity.this,
                                "Connecting BASS to " + leAudioDeviceStateWrapper.device.toString(),
                                Toast.LENGTH_SHORT).show();
                        leAudioViewModel.connectBass(leAudioDeviceStateWrapper.device, true);
                    }

                    @Override
                    public void onDisconnectClick(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper) {
                        Toast.makeText(MainActivity.this,
                                "Disconnecting BASS from "
                                        + leAudioDeviceStateWrapper.device.toString(),
                                Toast.LENGTH_SHORT).show();
                        leAudioViewModel.connectBass(leAudioDeviceStateWrapper.device, false);
                    }

                    @Override
                    public void onReceiverSelected(
                            LeAudioDeviceStateWrapper leAudioDeviceStateWrapper, int receiver_id) {
                        // Do nothing here, the UI is updated elsewhere and we already have the
                        // latest state value as well
                    }

                    @Override
                    public void onBroadcastCodeEntered(BluetoothDevice device, int receiver_id,
                            byte[] broadcast_code) {
                        leAudioViewModel.setBroadcastCode(device, receiver_id, broadcast_code);
                    }

                    @Override
                    public void onStopSyncReq(BluetoothDevice device, int receiver_id) {
                        // TODO: When is onStopSyncReq called? and what does below code do?

//                        List<BluetoothBroadcastAudioScanBaseConfig> configs = new ArrayList<>();
//                        // JT@CC: How come you can call this with null metadata when the
//                        // constructor has the @Nonull annotation for the param?
//                        BluetoothBroadcastAudioScanBaseConfig stop_config =
//                                new BluetoothBroadcastAudioScanBaseConfig(0, new byte[] {});
//                        configs.add(stop_config);
//
//                        leAudioViewModel.modifyBroadcastSource(device, receiver_id, false, configs);
                    }

                    @Override
                    public void onRemoveSourceReq(BluetoothDevice device, int receiver_id) {
                        leAudioViewModel.removeBroadcastSource(device, receiver_id);
                    }

                    @Override
                    public void onStopObserving() {
                        leAudioViewModel.stopBroadcastObserving();
                    }
                });
    }

    private void cleanupViewsProfileUiEventListeners() {
        recyclerViewAdapter.setOnLeAudioInteractionListener(null);
        recyclerViewAdapter.setOnVolumeControlInteractionListener(null);
        recyclerViewAdapter.setOnHapInteractionListener(null);
        recyclerViewAdapter.setOnBassInteractionListener(null);
    }

    // This sets the initial values and set up the observers
    private void setupViewModelObservers() {
        List<LeAudioDeviceStateWrapper> devices =
                leAudioViewModel.getAllLeAudioDevicesLive().getValue();

        if (devices != null)
            recyclerViewAdapter.updateLeAudioDeviceList(devices);
        leAudioViewModel.getAllLeAudioDevicesLive().observe(this, bluetoothDevices -> {
            recyclerViewAdapter.updateLeAudioDeviceList(bluetoothDevices);
        });
    }

    private void cleanupViewModelObservers() {
        leAudioViewModel.getAllLeAudioDevicesLive().removeObservers(this);
    }
}
