# Add a new VNDK APEX

In this document we add a new VNDK APEX for version 30. When you follow this doc with different versions,
change "30" to what you're adding. (eg. 31)

1. Add a new definition in `Android.bp`

```
apex_vndk {
    name: "com.android.vndk.v30",
    defaults: ["vndk-apex-defaults"],
    vndk_version: "30",
    system_ext_specific: true,
}
```

2. Verify

```
m com.android.vndk.v30
```
