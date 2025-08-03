#!/system/bin/sh
# Script to start "jazzer_setup" on the device
#
base=/system
export CLASSPATH=$base/framework/jazzer_setup.jar
exec app_process $base/bin com.jazzer.JazzerSetup "$@"
