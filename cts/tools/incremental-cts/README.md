# Note
incremental-cts is an EXPERIMENTAL project. It's for analysis purpose only and is NOT meant to be used for approval runs.

# Examples

## Generate dEQP dependency
This command will generate a dEQP dependency file named `dEQP-dependency.txt`:
```
python3 incremental_deqp.py -s device_serial_number -t /path/to/test_resources
--generate_deps_only
```

## Check if current build could skip dEQP

Before running this command, please create `extra_deqp_dependency.txt` file and
copy it to test resources directory. `extra_deqp_dependency.txt` includes the
dEQP dependencies that can't be detected by this tool such as firmwares.
```
python3 incremental_deqp.py -s device_serial_number -t /path/to/test_resources
-b /path/to/target_file
```

Target file is an build artifact produced by Android build system, e.g.
https://ci.android.com/builds/submitted/7230538/aosp_arm64-userdebug/latest/aosp_arm64-target_files-7230538.zip
