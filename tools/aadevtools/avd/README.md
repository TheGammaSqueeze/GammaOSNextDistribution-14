# Developer Tools for [AVD as a Development Platform](https://source.android.com/devices/automotive/start/avd)
This is a collection of developer tools to use AAOS AVDs for a better produtivity.

## Use Android Studio & Emulator
These scripts provide simple steps to add AVD images. So users can use them by the AVD manager GUI or [the command line](https://developer.android.com/studio/run/emulator-commandline).

### Add new AVD images
* add_avd_img.sh adds an AVD image to Android SDK dir.
    * test_avd_avd_image.sh is the test & also examples of how to use it.
* batch_add_avd_img.sh adds all AVD images listed in the input CSV file & properly patch to be used for Automotive device profiles.
    * avd_img_list.csv is the default CSV file if non is provided & also as an example.
* create_avd.sh creates a minimal AVD configuration from an AVD image.
    * test_create_avd.sh is the test & also examples of how to use it.
* patch_avd.sh changes an AVD configuration for bigger RAM, heap & data disk. So it can perform properly.
* patch_all_avds.sh changes all AVD configuration for more RAM & heap. So it can perform properly.
    * test_patch_all_avds.sh is the test & also examples of how to use it.
* set_avds_force_cold_boot.sh sets Cold Boot or Quick Boot for all AVD configurations.
    * test_set_avds_force_cold_boot.sh is the test & also examples of how to use it.