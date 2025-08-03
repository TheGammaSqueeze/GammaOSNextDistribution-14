Android PGO Profiles
====================

For the latest version of this doc, please make sure to visit:
[Android PGO Profiles](https://android.googlesource.com/toolchain/pgo-profiles/+/master/README.md)


Getting the necessary dependencies
----------------------------------

First, you need to install `python-pip`, as well as 2 packages from it.

```
$ sudo apt-get install python-pip
$ sudo pip install --upgrade google-api-python-client
$ sudo pip install --upgrade oauth2client
```

Updating PGO Profiles
---------------------

We only update profiles in the Android release branch, so this script should
be run directly from that location. The following is a sample command line for
updating the PGO profiles:

```
$ python toolchain/pgo-profiles/scripts/update_profiles.py -b <BUG_NUMBER> --add-do-not-merge <BUILD_ID> ALL --profdata-suffix <YYYY-MM-DD>
$ repo upload --cbr toolchain/pgo-profiles/
# Each separate profile will be uploaded as a single patch, so that it can get appropriate reviews.
```

Note that you need to change that command to supply 3 pieces of information:
- `BUG_NUMBER`: The buganizer bug number that should be part of the commit
                message. For Android R, that bug number is 150254950.
- `BUILD_ID`: The build id from which to extract the PGO profiles. You should
              check [go/ab](http://go/ab) for the given release branch.
- `YYYY-MM-DD`: The date of the build that the profiles come from (to keep it
                easier to check for stale profiles).


More Information
----------------

PGO-related questions should be sent to the Android LLVM team's public list:
[android-llvm@googlegroups.com](https://groups.google.com/forum/#!forum/android-llvm)

