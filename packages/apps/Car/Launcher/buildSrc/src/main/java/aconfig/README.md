# AAOS AConfig gradle Plugin
This plugin is copied over from:
```
$ANDROID_BUILD_TOP/packages/apps/ManagedProvisioning/studio-dev/ManagedProvisioningGradleProject/buildSrc/src/main/java/
```

This gradle plugin generates Trunk-stable Flag helper classes.

## Using in module's build.gradle
Add `id 'aconfig'` in plugins blocks and specify `packageName` and `.aconfig src file`
For example:
```
plugins {
    id 'aconfig'
}

aconfig {
    aconfigDeclaration {
        packageName.set("com.example.package.name")
        srcFile.setFrom(files("some_flags.aconfig"))
    }
}
```
