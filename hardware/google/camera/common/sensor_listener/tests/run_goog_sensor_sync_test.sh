# Test P4 back wide physical camera
adb root
adb shell am start -n com.google.android.mcamera/.MainActivity --es type photo --es cameraId 0
adb shell /vendor/bin/lib_sensor_listener_test/lib_sensor_listener_test --gtest_filter=GoogSensorSyncTest.TestP4BackWideCamera
adb shell am force-stop com.google.android.mcamera

sleep 10

# Test P4 back tele physical camera
adb shell am start -n com.google.android.mcamera/.MainActivity --es type photo --es cameraId 2
adb shell /vendor/bin/lib_sensor_listener_test/lib_sensor_listener_test --gtest_filter=GoogSensorSyncTest.TestP4BackTeleCamera
adb shell am force-stop com.google.android.mcamera
