# Validation Tools
This is a collection of validation tools for better development & integration
productivity for AAOS devices.

## Validating new app releases
Incoming quality control is an important practice to prevent new technical debts
added to risk the device development. These scripts illustrate a basic check for
a new app bundle release against a known good virtual or physical device build.
So that, you know what changed better & if they break any basic use cases.

1. Prepare a new app release in a directory, a device under test reachable
via adb & aapt is available in the shell environment.
```
appDir="/path/to/appDir"
renameCsv="/path/to/renameCsvFile"
```

- As sometime mk file change the file name when copying XML files to the device at the build time, you can supply a CSV file to guide the script. The format is as:
```
name,newName
privapp-permissions-in-app-release.xml,privapp-permissions-on-device.xml
```

2. batch_install_app.sh: find & install all APKs in a given directory to
a device via adb. Launch their launchable activities if any & capture
screenshots. To use:

```
./batch_install_app.sh ${appDir}
```

3. batch_check_permission.sh: find & diff permissions XML files in a given
directory against those on a device via adb. To use:

```
./batch_check_permission.sh ${appDir} ${renameCsv}
```

4. test.sh has all the commands above to make is easier to use.
