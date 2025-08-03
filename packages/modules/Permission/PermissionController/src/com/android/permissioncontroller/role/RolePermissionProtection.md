<!--
  Copyright (C) 2021 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License
  -->

# Using role for permission protection

Since Android S, it is possible to use `role` as a protection flag for permission definitions. This
allows more fine-grained control over which apps may get certain permissions, instead of the
`signature` or `privileged` protection that allows any platform-signed or privileged app to obtain
the permission. This was previously achieved via role-like protection flags, e.g. `installer` and
`verifier`, but they are essentially roles with a poorer implementation, and each of them takes one
bit from the protection flags so that we will be running out of bits soon.

The following outlines how to add a new permission with the role protection flag.

## Add (or modify) the permission definition

Edit `frameworks/base/core/res/AndroidManifest.xml` to add your new permission:

```xml
<!-- The javadoc for your new permission. -->
<permission
    android:name="android.permission.YOUR_NEW_PERMISSION"
    android:protectionLevel="internal|role" />
```

The choice of `android:protectionLevel` depends on your actual situation/requirements. More
specifically:

- `internal|role`: This enforces that only role may grant this permission to apps, which is the
preferred protection if possible.
- `signature|role`: This allows platform-signed apps to get this permission in addition to role
grants.
- `<previous-protection-level>|role`: Adding the `role` protection flag while keeping the original
protection level of an existing permission may allow a smoother transition to role permission
protection.

## Add the role

Edit [roles.xml](../../../../../res/xml/roles.xml) to add your new role:

```xml
<role
    name="android.app.role.SYSTEM_YOUR_ROLE_NAME"
    defaultHolders="config_systemYourRoleName"
    exclusive="true"
    minSdkVersion="33"
    static="true"
    systemOnly="true"
    visible="false">
    <permissions>
        <permission name="android.permission.YOUR_NEW_PERMISSION" />
    </permissions>
</role>
```

The role is named with the `SYSTEM_` prefix to indicate that it is an invisible and system app only
role. The config resource `config_systemYourRoleName` will be added in the next step, and more
details about role are available in [Android Role for system developers](Role.md). The
`minSdkVersion` attribute should normally be set to the current SDK version, to avoid making the
role available on older platforms.

If you are writing a CTS test for an API protected by an `internal|role` permission, you probably
want to grant the permission to Shell as well. This can be achieved by adding the permission to the
`SYSTEM_SHELL` role in `roles.xml`.

## Add the default role holder config

Now that we have a role, we need to define a platform config resource to initialize it with your
desired package name so that it may obtain the permission.

Edit `frameworks/base/core/res/res/values/config.xml` to add your new config resource:

```xml
<string name="config_systemYourRoleName" translatable="false">your.package.name</string>
```

You may also leave the package name empty in platform and override in a device/vendor-specific
overlay.

Since role is modularized, we also need to make this config resource a system API for access by
role.

Edit `frameworks/base/core/res/res/values/public-staging.xml` to expose the new config resource as
a system API:

```xml
<staging-public-group type="string" first-id="0xXXXXXXXX">
    <!-- @hide @SystemApi -->
    <public name="config_systemYourRoleName" />
</staging-public-group>
```

Then run `m update-api` to update the API text files.

## Upload the changes for review

Finally, upload the changes to Gerrit in one topic, then add the appropriate owners for review. Once
the changes are submitted, you will have a working role protected permission.
