# Settings App Helper Interfaces

This package defines interfaces of app-helper libraries in Settings.

## Follow the rules to create the new folders and interface files:

- If you would like to add a new page in the first layer of Settings, for example, `Display`, please
  create a `display` folder and add a file `ISettingsDisplayHelper.java` in the `display` folder.
- If you would like to add a new page **not** in the first layer of Settings, for example,
  `Night Light`, please create a file `ISettingsNightLightHelper.java` under the folder of
  the first layer (`display`) in Settings. It means `ISettingsDisplayHelper.java` and
  `ISettingsNightLightHelper.java` are in the same folder.

The following is an example of the interface files:
```
├──settings
        ├── apps
        │   └── ISettingsAllAppsHelper.java (Settings > Apps > All Apps)
        │   └── ISettingsAppsHelper.java (Settings > Apps)
        ├── display
        │   └── ISettingsDisplayHelper.java (Settings > Display)
        │   └── ISettingsNightLightHelper.java (Settings > Display > Night Light)
        └── ISettingsHomeHelper.java (Settings Home)
```

## Follow the rules to create the methods and variables:

- Put the methods and variables in the same interface if you do something in the page
    - `goTo\<Page\>` functions should be put in the extended interface, for example, `goToDisplay`
      should be put in the `ISettingsHomeHelper.java`.
- Use protected methods and variables if they are reusable
