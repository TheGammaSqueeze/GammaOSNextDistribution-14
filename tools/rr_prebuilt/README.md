# rr prebuilts

This directory contains the prebuilt binaries for [rr](https://rr-project.org/).
These binaries come from [aosp-rr-dev]. See [toolchain/rr/android/README.md] for
information about how to build and update that project.

[aosp-rr-dev]: https://ci.android.com/builds/branches/aosp-rr-dev/grid
[toolchain/rr/android/README.md]: https://cs.android.com/android/platform/superproject/+/rr-dev:toolchain/rr/android/README.md

## Updating prebuilts

To update the prebuilt binaries in this directory to a newer version from
ci.android.com, run:

```bash
$ ./update.sh $BUILD_ID
```

`$BUILD_ID` is optional. If omitted, the latest completed build will be used.

## Development guide for update.py

The development environment is managed using
[Poetry](https://python-poetry.org/). If you need to make a non-trivial change
you will want to install that so you can run the linters, formatters, and tests.
Once installed, run `poetry install` in this directory to install the
development dependencies.

Run the type checker and linter with `make lint`.

Auto-format the code with `make format`.

Run the tests with `make test`

Run all of the above with `make check` or just `make`.

### Managing dependencies

New dependencies can be added with `poetry add <name>` or removed with `poetry
add <name>`. Updating a dependency to a newer version is also done with `poetry
add`.

Whenever dependencies are updated, run `poetry export --without-hashes --output
requirements.txt` to update the requirements.txt file. That file is used by
update.sh so poetry is only needed by developers of update.py, not callers. Note
that poetry does not generate the correct output for the local fetchartifact
dependency, so after running export, you'll need to manually fix that line.
