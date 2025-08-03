# [Android Automotive](https://source.android.com/devices/automotive) Developer Tools

AADevT contains tools for AA device developers to improve their productivity.

* Fully unbundle from Android versions, and use the AOSP trunk based
development model.

* Bridge the app ecosystem for [Android Automotive OS](https://developer.android.com/training/cars)
because device developers are the key producers for the core apps.

* Leverage [Android app developer tools](https://developer.android.com/studio/intro)
that a few million developers use.

[TOC]

## Dev Tools
This contains tools designed specifically to simplify the AA device development
workflows.

* clone_proj.sh to clone a git project for the unbundled development workflows
in instead of the whole Android repo.

## Change Reports

### change_report.py
change_report.py creates a diff statistic CSV file from 2 versions of a codebase.
This is useful when the git commit history is somehow not obtainable. What you
need is to get 2 versions of a codebase downloaded on your disk first.

* You can compare specific folders of concern for a quick result, or when
there is a code patch change.
* This skips all symlinks & ignores common repository metadata folders, e.g.
.git, etc.
* It can take a long time & generates a large CSV file for the whole Android
codebase & especially if they are many changes. For example:
  * Android 11 QPR1 vs QPR2 takes more than 8 min. & generates a 5MB CSV file.
  * Android 10 QPR3 vs Android 11 QPR2 takes more than 11 min. & generates a
  95MB CSV file.
* To reduce time, you should always remove **out**, the build output folder first.
* For example, to compare Android 11 QPR1 vs QPR2 AOSP codebases on your disk.

```
python3 change_report.py --old_dir ~/android/android11-qpr1-release \
  --new_dir ~/android/android11-qpr2-release \
  --csv_file ~/change_reports/change_report_android11-qpr1-release_android11-qpr2-release.csv
```

* An output example: [change_report-new_vs_old_codebase.csv](dev/resource/change_report-new_vs_old_codebase.csv)
is the change report between **dev/resource/old_codebase** and
**new_codebase**.
* The **states** are:
  * SAME = 0
  * NEW = 1
  * REMOVED = 2
  * MODIFIED = 3
  * INCOMPARABLE = 4

### sysui_oem_diff.sh
sysui_oem_diff.sh generates a summary of code changes between 2 revisions.
Which gives you a rough idea of changes on files and Lines of Code.

* The report is especailly useful to discuss the pain points on sysui/notif
customization with AAOS team.
* For example, to generate the change report for Android 11 to 10 QPR3: [sysui_gcar_android10-qpr3-release_android11-release.txt](dev/resource/sysui_gcar_android10-qpr3-release_android11-release.txt)

```
./sysui_oem_diff.sh ~/Android/android11-release remotes/aosp/android10-qpr3-release remotes/aosp/android11-release > sysui_gcar_android10-qpr3-release_android11-release.txt
```

## System Performance Tuning
AAOS system performance turning is hard. Here are the tools to make it a bit
easier for the device developers.

* time_to_init_disp.sh to measure an app's [Time the Initial Display](https://developer.android.com/topic/performance/vitals/launch-time#time-initial)

## [Android Virtual Device as a Development Platform](avd/README.md)
