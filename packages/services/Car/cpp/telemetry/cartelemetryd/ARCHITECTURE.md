# Architecture of Car Telemetry Service

## Names

- C++ namespace `android.automotive.telemetry` - for all the car telemetry related projects.
- android.telemetry.ICarTelemetry - AIDL interface for collecting car data.
- cartelemetryd (android.automotive.telemetryd) -  a daemon that implements `ICarTelemetry`
                                                   interface.
- CarTelemetryService - a part of CarService that executes scrits. Located in car services dir.

## Structure

```
aidl/                    - Internal AIDL declerations, for public AIDLs, please see
                           //frameworks/hardware/interfaces/automotive/telemetry
products/                - AAOS Telemetry product, it's included in car_base.mk
sepolicy                 - SELinux policies
src/                     - Source code
   TelemetryServer.h     - The main class.
*.rc                     - rc file to start services
*.xml                    - VINTF manifest (TODO: needed?)
```
