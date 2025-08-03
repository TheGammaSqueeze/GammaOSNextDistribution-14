primary.prof.txt is generated from CtsCompilationApp and must be updated if
CompilationTargetActivity.java changes:

$ m CtsCompilationApp
$ adb install $ANDROID_PRODUCT_OUT/data/app/CtsCompilationApp/CtsCompilationApp.apk

# Now run the app manually for a couple of minutes, look for the profile:
$ adb shell ls -l /data/misc/profiles/cur/0/android.compilation.cts/primary.prof
# Once the profile appears and is nonempty, grab it:
$ adb pull /data/misc/profiles/cur/0/android.compilation.cts/primary.prof ./


app_used_by_other_app_1.prof.txt and app_used_by_other_app_2.prof.txt are
manually constructed for AppUsedByOtherApp. The latter one should be a superset
of the former one.
