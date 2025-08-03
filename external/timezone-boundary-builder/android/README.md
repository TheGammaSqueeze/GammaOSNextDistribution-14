This directory contains Android's additions to timezone-boundary-builder
project.

Directories:

`android_inputs/`
  - Files downloaded from upstream to seed `android_downloads/`

`android_downloads/`
  - The .json files used to generate the output. Equivalent to upstream's
    `downloads/` directory.

`android_dist/`
  - The generated output files. Equivalent to upstream's `dist/` directory.

`tools/`
  - Scripts used to execute the timezone-boundary-builder generation process.

Android initially intends to use the time zone boundary file, `combined.json`,
released by upstream. Therefore, this copy of timezone-boundary-builder may be
unnecessary until / unless Android wants to make local modifications.

-----

The `combined.json` generation process:

Execute `android/tools/host/clear_android_data_files.sh` to clear out most
working files and reset the local environment.

Before running the generation process, decide if you want to use the same input
files as have been used by upstream.

Upstream make the files used available in a file called `input-files.zip`, which
can be downloaded and unpacked into the `android/android_downloads/` directory
using `android/tools/host/download_input_files.sh`.

When not using upstream's input files, the generation process will first
download boundary data from OpenStreetMap, which is slow. Because OpenStreetMap
is constantly evolving, the latest features downloaded will usually have issues
that need to be fixed before proceeding. The script can be restarted multiple
times and will usually restart downloads from where it failed last time.

Local edits to input files may be necessary to make adjustments needed for
Android in future.

timezone-boundary-builder requires the nodejs runtime for execution with various
dependencies, so Android's `android/tools/host/run_tzbb.sh` uses a Docker
container to setup the runtime environment in a repeatable manner.

Execute `android/tools/host/run_tzbb.sh --help` to see options.

The following can be used to speed up generation times:

```
      --skip_analyze_diffs  Skip analysis of diffs between versions    [boolean]
      --skip_shapefile      Skip shapefile creation                    [boolean]
      --skip_validation     Skip validation                            [boolean]
```

After executing `android/tools/host/run_tzdbb.sh`, in `android/android_dist/`
there will be many .json files alongside (a newly generated) `input-files.zip`
and `combined.json` that constitute the main output files.

The contents of `android/android_downloads/` and `android/android_dist/` can be
committed to form a full record of how the latest output file was generated.

The `combined.json` and `input-files.zip` can be passed to the new step of the
reference data generation pipeline, which is held in a separate git project.

