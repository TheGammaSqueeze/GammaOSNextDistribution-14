This tool is used to analyze cts reports. Three scripts are included and can be used separately.

# Terminology
### Report
A report can be either a standard CTS result zip file, or a test_result.xml file.
### Information files
"Information files" indicates three files: `info.json`, `result.csv`, `summary.csv`

# Scripts
## parse_cts_report.py
### usage
```
./parse_cts_report.py -r REPORT -d OUTPUT_DIR [--abi [{armeabi-v7a,arm64-v8a,x86,x86_64} ...]]
```

The `-r` flag must be followed by exactly one report.

The `-d` flag specifies the directory in which the information files will be stored.

The `--abi` flag can be used to select one or more test ABIs to be parsed.

## aggregate_cts_reports.py
### usage
```
./aggregate_cts_reports.py -r REPORT [REPORT ...] -d OUTPUT_DIR [--ignore-abi] [--abi [{armeabi-v7a,arm64-v8a,x86,x86_64} ...]]
```

The `-r` flag can be followed by one or more reports.

The `-d` flag has the same behavior as `parse_cts_report.py`.

`--ignore-abi` is a boolean flag. If users specify this flag, tests ABI would be ignored while doing the aggregation. It means that two tests would be considered as the same one as long as they have the same module_name#class_name.test_name.

The `--abi` flag can be used to select one or more test ABIs to be aggregated.

## compare_cts_reports.py
### usage
```
./compare_cts_reports.py [-h] [-r CTS_REPORTS [CTS_REPORTS ...]] [-f CTS_REPORTS] --mode {1,2,n} --output-dir OUTPUT_DIR [--csv CSV] [--output-files] [--ignore-abi]
```

One `-r` flag is followed by a group of report files that you want to aggregate.

One `-f` flag is followed by **one** path to a folder, which contains parsed information files.

The `-m` flag has to be specified: `1` for one-way mode, `2` for two-way mode, and `n` for n-way mode.

The `-d` flag has to be specified. Behavior same as `parse_cts_report.py`.

`--csv` allows users to specify the file name of the comparison result. The default value is `diff.csv`.

`--output-files/-o` is a boolean flag. If users specify this flag, the parsed results of reports after flags `-r` will be outputed into the information files.

`--ignore-abi` is a boolean flag, which has the same behavior as `aggregate_cts_reports.py`.

### modes
#### One-way Comparison
The two reports from user input are report A and report B, respectively. Be careful that the order matters.

One-way comparison lists the tests that fail in report A and the corresponding results in both reports.

#### Two-way Comparison
Two-way comparison lists the tests where report A and report B have different results. If a test only exists in one of the reports, the test result of the other report will be indicated as `null`.

#### N-way Comparison
N-way comparison mode is used to show a summary of several test reports.
- The summaries are based on each module_name-abi pair. We call this pair "module" to simplify the explanation.
- Modules are sorted by the lowest pass rate among all reports. That is, if a module has a pass rate of 0.0 in one of the reports, it will be listed on the very top of `diff.csv`.

## Tests
```
./test_parse_cts_report.py
./test_aggregate_cts_reports.py
./test_compare_cts_reports.py
```
