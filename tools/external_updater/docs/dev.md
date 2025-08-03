# external_updater developer documentation

The documentation on this page is for developers of `external_updater`. If
you're looking for user documentation, see the [README.md].

## Development environment

Development of `external_updater` requires a full checkout of the main branch of
AOSP, and a lunched target (any target). See [Get started with Android
Development] for a guide on setting up an AOSP build environment.

Note: This project uses Python 3.11.

Not all the Python tools used here are available in AOSP. For managing those, we
recommend using Poetry. To install the necessary Python dependencies:

```bash
$ poetry install
```

[README.md]: ../README.md
[Get started with Android Development]: https://source.android.com/docs/setup

## Development tasks

The easiest way to activate the virtual environment is
to create a nested shell with:

```bash
$ poetry shell
```

`poetry shell` will activate the virtual environment by creating a nested shell.
To deactivate this virtual environment simply use `deactivate` or `exit`.


Poetry provides a `run` command to execute the given command inside the
project's virtual environment. As an example, execute the following command to
run Mypy on `base_updater.py`:

```bash
$ poetry run mypy base_updater.py
```
