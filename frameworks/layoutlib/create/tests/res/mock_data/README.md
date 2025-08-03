# Update mock_android.jar

## Build mock_android.jar

First, use set up instructions from layoutlib tutorial to make commands like `m` work.

Then, run the following in the repository root:

```
m mock_android
mv out/host/linux-x86/framework/mock_android.jar frameworks/layoutlib/create/tests/res/data/mock_android.jar
```

## Build problems

If you see a build error like:

```
[ 29% 351/1203] including frameworks/layoutlib/out/test/create/mock_data/Android.mk ...
FAILED:
build/make/core/base_rules.mk:325: error: frameworks/layoutlib/out/test/create/mock_data: MODULE.linux.JAVA_LIBRARIES.mock_android already defined by frameworks/layoutlib/create/tests/res/mock_data.
16:26:52 ckati failed with: exit status 1
```

a quick solution would be to remove `frameworks/layoutlib/out/test/create/mock_data/Android.mk`.
