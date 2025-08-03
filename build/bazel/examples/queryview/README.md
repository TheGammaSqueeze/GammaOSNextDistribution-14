# Examples of Bazel queries in the QueryView

**Warning**: This feature is undergoing active development and the generated
Bazel BUILD files and other user-facing APIs will change without warning. We
recommend to use this feature for learning, exploration, information gathering
and debugging purposes only.

## Usage

Setup your AOSP product config and create the queryview directory:

```
source build/envsetup.sh
lunch aosp_arm # or your preferred target
m queryview
```

Then, run `bazel query` with the `queryview` config, and `--query_file` pointing
to the file containing the query expression to run for the current product
configuration:

```
bazel query --config=queryview --query_file=build/bazel/examples/queries/android_app.txt
```

Alternatively, you can also write the expression directly in the command:

```
bazel query --config=queryview 'kind("android_app rule", //...)'
```

## Examples

*   `android_apps`: lists all `android_app` modules for the current product configuration
*   `nocrt`: modules with `nocrt: True`
*   `apex_available`: modules with `//apex_available:platform` in the `apex_available` list property
*   `libc_variant`: all variants for `libc`
