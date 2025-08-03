# ClusterHomeSample

`ClusterHomeSample` is a sample reference application that runs on the cluster display.
It uses `ClusterHomeManager` API (a.k.a. Cluster2) to interact with the CarService.
It supports both the FULL mode and the LIGHT mode. However, in actual production cases,
the cluster service supports one specific mode, thus the actual cluster application
only needs to run in one specific mode.

## CarService Configuration
In order to enable `ClusterHomeService`, remove `cluster_service` from
`config_allowed_optional_car_features` and add `cluster_home_service`,
in the RRO configuration of the CarService.
```
<string-array translatable="false" name="config_allowed_optional_car_features">
    ...
    <item>cluster_home_service</item>
    ...
</string-array>
```
Set `config_clusterHomeServiceMode` to select what mode the `ClusterHomeService` to run in.
```
<!-- Configures the mode in which ClusterHomeService operates.
     Currently supported modes are:
         0 (full mode): All VHAL properties in ClusterHalService#CORE_PROPERTIES must be
                        available. Otherwise, the entire ClusterHomeService is disabled.
         1 (light mode): ClusterHomeService is always enabled even without any VHAL properties.
-->
<integer name="config_clusterHomeServiceMode">0</integer>
```
`config_clusterHomeActivity` sets the activity that runs on the cluster display.
Note that the activity specified here will run as the system user,
thus the activity's `showForAllUsers` attribute must be set to `true`
in the application's `AndroidManifest.xml` file.
```
<!-- The name of Activity who is in charge of ClusterHome. -->
<string name="config_clusterHomeActivity" translatable="false">com.android.car.cluster.home/.ClusterHomeActivity</string>
```
The followings are used by the `ClusterHomeManager#startVisibilityMonitoring(Activity)` method
to configure parameters for visibility monitoring.
```
<!-- Configurations for ClusterHome visibility monitoring.
     Please refer to {@link SurfaceControl#TrustedPresentationThresholds} for the detail.
-->
<fraction name="config_clusterHomeVisibility_minAlpha">100%</fraction>
<fraction name="config_clusterHomeVisibility_minRendered">99.9%</fraction>
<integer name="config_clusterHomeVisibility_stabilityMs">100</integer>
```

## Application Configuration
### `directBootAware`
A cluster application needs to be able to start regardless of user unlocked state.
Therefore `dirctBootAware` must be set to `true` in the application's `AndroidManifest.xml`.
```
<application android:name=".ClusterHomeApplication"
    ...
    android:directBootAware="true">
```
See https://developer.android.com/privacy-and-security/direct-boot
for more information on `directBootAware`.
### `showForAllUsers`
For the activities that run as the system user, the `showForAllUsers`
attribute must be set to `true` in the `AndroidManifest.xml` file.
```
<activity android:name=".ClusterHomeActivity"
    ...
    android:showForAllUsers="true">
```
See https://developer.android.com/guide/topics/manifest/activity-element#showForAllUsers
for more information on `showForAllUsers`.
## FULL mode

The cluster application makes full use of the `ClusterHomeManager` APIs in the FULL mode.
It starts with the `UI_TYPE_HOME` activity running as user 0, and switches to `UI_TYPE_START`
activity when the current user is unlocked. It can switch to other UI activity types
(e.g. `UI_TYPE_MAPS`, `UI_TYPE_PHONE`, etc.) as necessary.

See `ClusterHomeActivity` for more details.

To run `ClusterHomeService` in the FULL mode, the device needs to have
all the following VHAL properties defined:

- `CLUSTER_SWITCH_UI`
- `CLUSTER_REPORT_STATE`
- `CLUSTER_DISPLAY_STATE`
- `CLUSTER_REQUEST_DISPLAY`

If the service is configured for the FULL mode but any of the above properties is not defined,
`ClusterHomeManager` API will throw an `IllegalStateException`.

## LIGHT mode

In the LIGHT mode, it stays as the `UI_TYPE_HOME` activity that runs as user 0.
`ClusterHomeManager#startVisibilityMonitoring` and `ClusterHomeManager#sendHeartbeat` are
used in the LIGHT mode. The device must implement `CLUSTER_HEARTBEAT` VHAL property
to be able to use these API.

See `ClusterHomeActivityLightMode` for a sample implementation.