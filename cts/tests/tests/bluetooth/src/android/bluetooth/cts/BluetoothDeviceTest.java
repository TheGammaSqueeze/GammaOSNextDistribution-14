/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothDevice.ACCESS_ALLOWED;
import static android.bluetooth.BluetoothDevice.ACCESS_REJECTED;
import static android.bluetooth.BluetoothDevice.ACCESS_UNKNOWN;
import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.bluetooth.BluetoothDevice.TRANSPORT_BREDR;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.OobData;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.UnsupportedEncodingException;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothDeviceTest {

    private Context mContext;
    private boolean mHasBluetooth;
    private boolean mHasCompanionDevice;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private final String mFakeDeviceAddress = "00:11:22:AA:BB:CC";
    private BluetoothDevice mFakeDevice;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        mHasCompanionDevice = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_COMPANION_DEVICE_SETUP);

        if (mHasBluetooth && mHasCompanionDevice) {
            BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
            mFakeDevice = mAdapter.getRemoteDevice(mFakeDeviceAddress);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mHasBluetooth && mHasCompanionDevice) {
            mAdapter = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void setAlias_getAlias() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();

        AttributionSource source = AttributionSource.myAttributionSource();
        assertEquals("android.bluetooth.cts", source.getPackageName());

        // Verifies that when there is no alias, we return the device name
        assertNull(mFakeDevice.getAlias());

        assertThrows(IllegalArgumentException.class, () -> mFakeDevice.setAlias(""));

        String testDeviceAlias = "Test Device Alias";

        // This should throw a SecurityException because there is no CDM association
        assertThrows("BluetoothDevice.setAlias without"
                + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                SecurityException.class, () -> mFakeDevice.setAlias(testDeviceAlias));

        runShellCommand(String.format(
                "cmd companiondevice associate %d %s %s", userId, packageName, mFakeDeviceAddress));
        String output = runShellCommand("dumpsys companiondevice");
        assertTrue("Package name missing from output", output.contains(packageName));
        assertTrue("Device address missing from output",
                output.toLowerCase().contains(mFakeDeviceAddress.toLowerCase()));

        // Takes time to update the CDM cache, so sleep to ensure the association is cached
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Device properties don't exist for non-existent BluetoothDevice, so calling setAlias with
         * permissions should return false
         */
        assertEquals(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED, mFakeDevice
                .setAlias(testDeviceAlias));
        runShellCommand(String.format("cmd companiondevice disassociate %d %s %s", userId,
                    packageName, mFakeDeviceAddress));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertNull(mFakeDevice.getAlias());
        assertEquals(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED,
                mFakeDevice.setAlias(testDeviceAlias));
    }

    @Test
    public void getAddressType() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertEquals(BluetoothDevice.ADDRESS_TYPE_PUBLIC, mFakeDevice.getAddressType());
    }

    @Test
    public void getIdentityAddress() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows("No BLUETOOTH_PRIVILEGED permission", SecurityException.class,
                () -> mFakeDevice.getIdentityAddress());
    }

    @Test
    public void getConnectionHandle() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows("No BLUETOOTH_PRIVILEGED permission", SecurityException.class,
                () -> mFakeDevice.getConnectionHandle(TRANSPORT_LE));

        // but it should work after we get the permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        var handle = mFakeDevice.getConnectionHandle(TRANSPORT_LE);
        assertEquals(handle, BluetoothDevice.ERROR);
    }

    @Test
    public void getAnonymizedAddress() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertEquals("XX:XX:XX:XX:BB:CC", mFakeDevice.getAnonymizedAddress());
    }

    @Test
    public void getBatteryLevel() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertEquals(BluetoothDevice.BATTERY_LEVEL_UNKNOWN, mFakeDevice.getBatteryLevel());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getBatteryLevel());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(BluetoothDevice.BATTERY_LEVEL_BLUETOOTH_OFF, mFakeDevice.getBatteryLevel());
    }

    @Test
    public void isBondingInitiatedLocally() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertFalse(mFakeDevice.isBondingInitiatedLocally());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isBondingInitiatedLocally());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.isBondingInitiatedLocally());
    }

    @Test
    public void prepareToEnterProcess() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mFakeDevice.prepareToEnterProcess(null);
    }

    @Test
    public void setPin() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertFalse(mFakeDevice.setPin((String) null));
        assertFalse(mFakeDevice.setPin("12345678901234567")); // check PIN too big

        assertFalse(mFakeDevice.setPin("123456")); //device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.setPin("123456"));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.setPin("123456"));
    }

    @Test
    public void connect_disconnect() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.connect());
        assertThrows(SecurityException.class, () -> mFakeDevice.disconnect());
    }

    @Test
    public void cancelBondProcess() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.cancelBondProcess());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.cancelBondProcess());
    }

    @Test
    public void createBond() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.createBond(TRANSPORT_AUTO));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.createBond(TRANSPORT_AUTO));
    }

    @Test
    public void createBondOutOfBand() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        OobData data = new OobData.ClassicBuilder(
                new byte[16], new byte[2], new byte[7]).build();

        assertThrows(IllegalArgumentException.class, () -> mFakeDevice.createBondOutOfBand(
                TRANSPORT_AUTO, null, null));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice
                .createBondOutOfBand(TRANSPORT_AUTO, data, null));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @Test
    public void getUuids() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertNull(mFakeDevice.getUuids());
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getUuids());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertNull(mFakeDevice.getUuids());
    }

    @Test
    public void isEncrypted() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        //Device is not connected
        assertFalse(mFakeDevice.isEncrypted());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isEncrypted());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.isEncrypted());
    }

    @Test
    public void removeBond() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        //Device is not bonded
        assertFalse(mFakeDevice.removeBond());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.removeBond());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.removeBond());
    }

    @Test
    public void setPinByteArray() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(NullPointerException.class, () -> mFakeDevice.setPin((byte[]) null));

        // check PIN too big
        assertFalse(mFakeDevice.setPin(convertPinToBytes("12345678901234567")));
        assertFalse(mFakeDevice.setPin(convertPinToBytes("123456"))); // device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPin(convertPinToBytes("123456")));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.setPin(convertPinToBytes("123456")));
    }

    @Test
    public void connectGatt() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(NullPointerException.class, () -> mFakeDevice
                .connectGatt(mContext, false, null,
                TRANSPORT_AUTO, BluetoothDevice.PHY_LE_1M_MASK));

        assertThrows(NullPointerException.class, () ->
                mFakeDevice.connectGatt(mContext, false, null,
                TRANSPORT_AUTO, BluetoothDevice.PHY_LE_1M_MASK, null));
    }

    @Test
    public void fetchUuidsWithSdp() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // TRANSPORT_AUTO doesn't need BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_BREDR));
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_LE));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO));
    }

    @Test
    public void messageAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if MAP is not enabled.
        assumeTrue(mHasBluetooth && mHasCompanionDevice
                   && TestUtils.isProfileEnabled(BluetoothProfile.MAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setMessageAccessPermission(ACCESS_ALLOWED));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setMessageAccessPermission(ACCESS_UNKNOWN));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setMessageAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.setMessageAccessPermission(ACCESS_UNKNOWN));
        assertEquals(ACCESS_UNKNOWN, mFakeDevice.getMessageAccessPermission());
        assertTrue(mFakeDevice.setMessageAccessPermission(ACCESS_ALLOWED));
        assertEquals(ACCESS_ALLOWED, mFakeDevice.getMessageAccessPermission());
        assertTrue(mFakeDevice.setMessageAccessPermission(ACCESS_REJECTED));
        assertEquals(ACCESS_REJECTED, mFakeDevice.getMessageAccessPermission());
    }

    @Test
    public void phonebookAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if PBAP is not enabled.
        assumeTrue(mHasBluetooth && mHasCompanionDevice
                   && TestUtils.isProfileEnabled(BluetoothProfile.PBAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPhonebookAccessPermission(ACCESS_ALLOWED));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPhonebookAccessPermission(ACCESS_UNKNOWN));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPhonebookAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.setPhonebookAccessPermission(ACCESS_UNKNOWN));
        assertEquals(ACCESS_UNKNOWN, mFakeDevice.getPhonebookAccessPermission());
        assertTrue(mFakeDevice.setPhonebookAccessPermission(ACCESS_ALLOWED));
        assertEquals(ACCESS_ALLOWED, mFakeDevice.getPhonebookAccessPermission());
        assertTrue(mFakeDevice.setPhonebookAccessPermission(ACCESS_REJECTED));
        assertEquals(ACCESS_REJECTED, mFakeDevice.getPhonebookAccessPermission());
    }

    @Test
    public void simAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if SAP is not enabled.
        assumeTrue(mHasBluetooth && mHasCompanionDevice
                   && TestUtils.isProfileEnabled(BluetoothProfile.SAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setSimAccessPermission(ACCESS_ALLOWED));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setSimAccessPermission(ACCESS_UNKNOWN));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setSimAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.setSimAccessPermission(ACCESS_UNKNOWN));
        assertEquals(ACCESS_UNKNOWN, mFakeDevice.getSimAccessPermission());
        assertTrue(mFakeDevice.setSimAccessPermission(ACCESS_ALLOWED));
        assertEquals(ACCESS_ALLOWED, mFakeDevice.getSimAccessPermission());
        assertTrue(mFakeDevice.setSimAccessPermission(ACCESS_REJECTED));
        assertEquals(ACCESS_REJECTED, mFakeDevice.getSimAccessPermission());
    }

    @Test
    public void isRequestAudioPolicyAsSinkSupported() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(SecurityException.class,
                () -> mFakeDevice.isRequestAudioPolicyAsSinkSupported());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertEquals(BluetoothStatusCodes.FEATURE_NOT_CONFIGURED,
                mFakeDevice.isRequestAudioPolicyAsSinkSupported());
    }

    @Test
    public void setGetAudioPolicy() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        BluetoothSinkAudioPolicy demoAudioPolicy = new BluetoothSinkAudioPolicy.Builder().build();

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class,
                () -> mFakeDevice.requestAudioPolicyAsSink(demoAudioPolicy));
        assertThrows(SecurityException.class, () -> mFakeDevice.getRequestedAudioPolicyAsSink());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertEquals(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED,
                mFakeDevice.requestAudioPolicyAsSink(demoAudioPolicy));
        assertNull(mFakeDevice.getRequestedAudioPolicyAsSink());

        BluetoothSinkAudioPolicy newPolicy = new BluetoothSinkAudioPolicy.Builder(demoAudioPolicy)
                .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                .setActiveDevicePolicyAfterConnection(BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED)
                .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                .build();

        assertEquals(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED,
                mFakeDevice.requestAudioPolicyAsSink(newPolicy));
        assertNull(mFakeDevice.getRequestedAudioPolicyAsSink());

        assertEquals(BluetoothSinkAudioPolicy.POLICY_ALLOWED, newPolicy.getCallEstablishPolicy());
        assertEquals(BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED,
                newPolicy.getActiveDevicePolicyAfterConnection());
        assertEquals(BluetoothSinkAudioPolicy.POLICY_ALLOWED, newPolicy.getInBandRingtonePolicy());
    }

    private byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            return null;
        }
        return pinBytes;
    }

    @Test
    public void getPackageNameOfBondingApplication() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class,
                () -> mFakeDevice.getPackageNameOfBondingApplication());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(SecurityException.class,
                () -> mFakeDevice.getPackageNameOfBondingApplication());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT);
        // Since no application actually start bonding with this device, this should return null
        assertNull(mFakeDevice.getPackageNameOfBondingApplication());

        mFakeDevice.createBond();
        assertEquals(mContext.getPackageName(),
                mFakeDevice.getPackageNameOfBondingApplication());

        // Clean up create bond
        // Either cancel the bonding process or remove bond
        mFakeDevice.cancelBondProcess();
        mFakeDevice.removeBond();
    }

    @RequiresFlagsEnabled(Flags.FLAG_METADATA_API_INACTIVE_AUDIO_DEVICE_UPON_CONNECTION)
    @Test
    public void setActiveAudioDevicePolicy_getActiveAudioDevicePolicy() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        // This should throw a SecurityException because no BLUETOOTH_CONNECT permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThrows(
                SecurityException.class,
                () ->
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
        assertThrows(SecurityException.class, () -> device.getActiveAudioDevicePolicy());

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(
                SecurityException.class,
                () ->
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
        assertThrows(SecurityException.class, () -> device.getActiveAudioDevicePolicy());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertEquals(
                BluetoothDevice.ACTIVE_AUDIO_DEVICE_POLICY_DEFAULT,
                device.getActiveAudioDevicePolicy());
        assertEquals(
                BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED,
                device.setActiveAudioDevicePolicy(
                        BluetoothDevice
                                .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
    }
}
