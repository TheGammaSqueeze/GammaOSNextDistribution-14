# Quickstart performance test suite release history

## 1.6

### New
* `nearby_share_stress_test.py` for testing Nearby Share using Wifi only.

### Fixes
* Change discovery medium to BLE only.
* Increase 1G file transfer timeout to 400s.
* Disable GMS auto-updates for the duration of the test.

## 1.5

### New
* `esim_transfer_stress_test.py` for testing eSIM transfer using Bluetooth only.
* `quick_start_stress_test.py` for testing the Quickstart flow using both
   Bluetooth and Wifi.