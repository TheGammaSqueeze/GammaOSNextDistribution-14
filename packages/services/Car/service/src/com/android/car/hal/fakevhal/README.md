# Fake VHAL mode in Car Service

### How to enable fake VHAL mode.

**Note**: Fake VHAL can only be enabled in eng or userdebug build.

For default usage to enable fake VHAL mode, run `enable-fake-vhal.sh` and verify the result shows

```
Car Service connects to FakeVehicleStub: true
```

To disable, run `disable-fake-vhal.sh` and verify the result shows

```
Car Service connects to FakeVehicleStub: false
```

#### Push ENABLE file and optional custom config files to device

1.  Push ENABLE file and optional custom config files.

    Option A. Use the default config file only. Push an empty ENABLE file to device.

    ```
    $ touch /tmp/ENABLE
    $ adb push /tmp/ENABLE /data/system/car/fake_vhal_config/ENABLE
    ```

    Option B. Add custom config file names to ENABLE file. Push both ENABLE file and custom config files to device.

    ```
    $ echo <custom-config-file-name> > /tmp/ENABLE
    $ adb push /tmp/ENABLE /data/system/car/fake_vhal_config/ENABLE
    $ adb push <path-to-file>/<custom-config-file-name> \
      /data/system/car/fake_vhal_config/<custom-config-file-name>
    ```

#### Activate fake VHAL mode.

1.  Reboot device

    ```
    $ adb shell stop && adb shell start
    $ adb wait-for-device # wait until the device is connected.
    ```

1.  Check if Car Service is connecting to fake VHAL.

    ```
    $ adb shell cmd car_service check-fake-vhal
    Car Service connects to FakeVehicleStub: true
    ```

1.  Delete ENABLE file from device and restart service to disable fake VHAL.

    ```
    $ adb shell
    $ cd data/system/car/fake_vhal_config
    $ rm ENABLE
    $ exit
    $ adb shell stop && adb shell start
    ```
