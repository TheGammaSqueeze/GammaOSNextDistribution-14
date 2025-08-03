# BumbleBluetoothTests

Bumble Bluetooth tests are instrumented Android-specific multi-device tests using a reference
peer device implementing the Pandora APIs.

## Architecture

BumbleBluetoothTests is an Android APK that offers enhanced control over Android compared to Avatar
by interacting directly with the Device Under Test (DUT) via Android APIs. Instead of mocking every
API call, it communicates with actual reference devices using gRPC and limits peer device interactions
to the Pandora APIs.

Here is an overview of the BumbleBluetoothTests architecture:
![BumbleBluetoothTests architecture](asset/java-bumble-test-setup.png)

A simple LE connection test looks like this:

```kotlin
// Setup a Bumble Pandora device for the duration of the test.
// Acting as a Pandora client, it can be interacted with through the Pandora APIs.
@Rule @JvmField val mBumble = PandoraDevice()

/**
 * Tests the Bluetooth GATT connection process with a mock callback.
 * This verifies both successful connection and disconnection events for a
 * remote Bluetooth device.
 *
 * @throws Exception if there's an unexpected error during the test execution.
 */
@Test
fun testGattConnect() {
    // 1. Advertise the host's Bluetooth capabilities using another
    //    gRPC call:
    // - `hostBlocking()` accesses another gRPC service related to the host.
    //   The following `advertise(...)` sends an advertise request to the server, setting
    //   specific attributes.
    mBumble
        .hostBlocking()
        .advertise(
            AdvertiseRequest.newBuilder()
                .setLegacy(true)
                .setConnectable(true)
                .setOwnAddressType(OwnAddressType.RANDOM)
                .build()
        )

    // 2. Create a mock callback to handle Bluetooth GATT (Generic Attribute Profile) related events.
    val gattCallback = mock(BluetoothGattCallback::class.java)

    // 3. Fetch a remote Bluetooth device instance (here, Bumble).
    val bumbleDevice =
        bluetoothAdapter.getRemoteLeDevice(
            Utils.BUMBLE_RANDOM_ADDRESS,
            BluetoothDevice.ADDRESS_TYPE_RANDOM // Specify address type as RANDOM because the device advertises with this address type.
        )

    // 4. Connect to the Bumble device and expect a successful connection callback.
    var bumbleGatt = bumbleDevice.connectGatt(context, false, gattCallback)
    verify(gattCallback, timeout(TIMEOUT))
        .onConnectionStateChange(
            any(),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(BluetoothProfile.STATE_CONNECTED)
        )

    // 5. Disconnect from the Bumble device and expect a successful disconnection callback.
    bumbleGatt.disconnect()
    verify(gattCallback, timeout(TIMEOUT))
        .onConnectionStateChange(
            any(),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(BluetoothProfile.STATE_DISCONNECTED)
        )
}
```
