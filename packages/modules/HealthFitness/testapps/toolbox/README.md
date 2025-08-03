## Setup

Build and run test the app using the following command

```
m HealthConnectToolbox &&
adb install $OUT/system/app/HealthConnectToolbox/HealthConnectToolbox.apk
```

## Workflows

### Requesting Permissions

* Click on Request Permissions on Home Screen
* It will open the Request Permissions page on Health Connect showing toggles for all the
  permissions.
* Allow/Disallow the permissions as per the use case.

### Insert Data

* Click Insert Data on Home Screen
* Select the Data Category.
* Select the Data Type.
* There are two types of Records:
    * Interval Record - These records take place over an interval of time therefore they have a
      start time and an end time. They generate a single value or a range of values at the end of
      the whole interval. For example Elevation Gained, record contains a start time, an end time
      and the total elevation gained in that time period. To insert such a record, users can enter a
      start time, end time and the recorded values for the given record type.
    * Instantaneous Record - Contrary to the previous records, these records are recorded at a given
      instant and not over a range of time. They can be inserted similar to an interval record, just
      not with a start and end time, instead with a single time stamp.
* On confirming insert of data, users will be given the inserted record's UUID. Save this UUID to
  update this data later.

### Update Data

* Updating data will follow the same workflow as insert.
* Users can goto the page to insert data, enter the updated values and click Update Data.
* Users will be required to enter the UUID of the record they want to update.

[Demo video for the aforementioned workflows.](https://drive.google.com/file/d/1kbO2duqZ4NGJ9gpRJe3C-3MCjq6F7eFn/view?usp=sharing&resourcekey=0-A9DL0nlGNr56jfcKLHE7IQ)


