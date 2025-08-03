Sources for building a prebuilts/sdk tree for metalava use in unit tests.

The directory tree and generated jar files will be build as part of the metalava
unit tests, and placed in $(gettop)/out/metalava/prebuilts/sdk.

The project emulates the following history, in order of earliest to latest event:

- finalized Android API 30 "Android R" (android-30)
- finalized SDK extensions "R-ext" version 1 (android-30-ext1)
- finalized Android API 31 "Android S" and SDK extensions "S-ext", version 2, at the same time (android-31-ext2)
- finalized SDK extensions "S-ext", version 3 (android-31-ext3)
