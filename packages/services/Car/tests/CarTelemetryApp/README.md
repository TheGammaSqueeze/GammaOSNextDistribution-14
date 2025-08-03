# CarTelemetry Demo Vendor Application

This directory contains source code for a test application that CarService binds to at boot.

The application uses CarTelemetryManager APIs to add metrics configurations and collect telemetry.

## Usage

The `Car Telemetry Collector` app is the UI component of this test application. It allows the adding/removing of configs and viewing retrieved data and errors on screen.

CarService should bind to the vendor service, the list of early start up applications that
CarService binds to are listed in `vendor/auto/embedded/products/rro_overlay/CarServiceOverlay/res/values/config.xml`.

Add

```
<item>com.android.car.cartelemetryapp/.CarMetricsCollectorService#bind=bind,user=system,trigger=userUnlocked</item>
```

to `config_earlyStartupServices` in `vendor/auto/embedded/products/rro_overlay/CarServiceOverlay/res/values/config.xml`


```shell
m CarTelemetryApp
adb root && adb remount && adb sync system && adb shell stop && adb shell start
```

Verify that `adb logcat -b all -T 10000 -v color | egrep -i 'CAR.TELEMETRY|CarMetricsCollector'`
contains the messages related to addMetricsConfig

Note that stats reports are created after 10 minutes. Keep the process running and verify logcat.

## Privileged Permission

CarTelemetryManager APIs are protected with a
permission `android.car.permission.USE_CAR_TELEMETRY_SERVICE`.

The application declares the permission in `AndroidManifest.xml` and has a permission allowlist file
at `frameworks/base/data/etc/car/com.android.car.cartelemetryapp.xml`

## MetricsConfigs in assets/ Folder

The assets/ folder contains the MetricsConfigs proto binaries. Examples:

### activity_foreground_state_changed_config

```
name: activity_foreground_state_changed_config
version: 1
script:
    """
    function onActivityForegroundStateChanged(published_data, state)
        result = {}
        n = 0
        for k, v in pairs(published_data) do
            result[k] = v[1]
            n = n + 1
        end
        result.n = n
        on_script_finished(result)
    end
    """
subscribers:
  - handler: onActivityForegroundStateChanged
    publisher:
        stats:
            system_metric: ACTIVITY_FOREGROUND_STATE_CHANGED
    priority: 0
```

### process_memory_metrics_config

```
name: process_memory_metrics_config
version: 1
script:
    """
    function calculateAverage(tbl)
        sum = 0
        size = 0
        for _, value in ipairs(tbl) do
            sum = sum + value
            size = size + 1
        end
        return sum/size
    end
    function onProcessMemory(published_data, state)
        result = {}
        result.page_fault_avg = calculateAverage(published_data.page_fault)
        result.major_page_fault_avg = calculateAverage("
                        + "published_data.page_major_fault)
        result.oom_adj_score_avg = calculateAverage("
                        + "published_data.oom_adj_score)
        result.rss_in_bytes_avg = calculateAverage("
                        + "published_data.rss_in_bytes)
        result.swap_in_bytes_avg = calculateAverage("
                        + "published_data.swap_in_bytes)
        result.cache_in_bytes_avg = calculateAverage("
                        + "published_data.cache_in_bytes)
        on_script_finished(result)
    end
    """
subscribers:
  - handler: onProcessMemory
    publisher:
        stats:
            system_metric: PROCESS_MEMORY_STATE
    priority: 0
```
