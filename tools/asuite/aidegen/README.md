# AIDEGen

AIDEGen aims to automate the project setup process for developers to work on
Java or C/C++project in popular IDE environment. Developers no longer need to manually
configure an IntelliJ project, such as all the project dependencies. It's a
**command line tool** that offers the following features:

*   Configure Android Studio or IntelliJ project files with the relevant module
    dependencies resolved.

*   Launch IDE for a specified sub-project or build target, i.e. frameworks/base
    or Settings.

*   Launch IDE for specified folder(s) which contains build targets, i.e. cts.

*   Auto configure JDK and Android coding style for IntelliJ.

## 1. Prerequisites:

*   IDE installation, choose one of prefer IDE, including Android Studio,
    IntelliJ IDEA, Eclipse, CLion and VS Code.

*   Setup Android development environment.

```
$ source build/envsetup.sh && lunch <TARGET>
```

## 2. Basic Usage:

### Example 1: Launch IDE with module name

Example to generate and launch IntelliJ project for framework and Settings:

```
$ aidegen Settings framework
```

### Example 2: Launch IDE with module path

Example to generate and launch IntelliJ project for framework and Settings:

```
$ aidegen packages/apps/Settings frameworks/base
```

### Example 3: Launch IDE with build skipped

Example to generate and launch IntelliJ project for framework and Settings and
skip build time:

```
$ aidegen Settings framework -s
```

### Example 4: Launch IDE with native module

Example to generate and launch CLion project:

```
$ aidegen <module> -i c
```
The native projects will be launched in CLion and you have to select,
**Tools > CMake > Change Project Root**
Change project root to the source directory: the relative path which is the directory shown in CLion’s project view. For example, if frameworks/base/media is your project, please change project root directly to frameworks/base/media.

## 3. Optional arguments:

Developers can also use the following optional arguments with AIDEGen commands.

| Option | Long option       | Description                                                              |
|:------:|:------------------|--------------------------------------------------------------------------|
|  `-d`  | `--depth`         | The depth of module referenced by source.                                |
|  `-i`  | `--ide`           | Launch IDE type, j=IntelliJ s=Android Studio e=Eclipse c=CLion v=VS Code |
|  `-p`  | `--ide-path`      | Specify user's IDE installed path.                                       |
|  `-n`  | `--no_launch`     | Do not launch IDE.                                                       |
|  `-r`  | `--config-reset`  | Reset all AIDEGen's saved configurations.                                |
|  `-s`  | `--skip-build`    | Skip building jars or modules.                                           |
|  `-v`  | `--verbose`       | Displays DEBUG level logging.                                            |
|  `-a`  | `--android-tree`  | Generate whole Android source tree project file for IDE.                 |
|  `-e`  | `--exclude-paths` | Exclude the directories in IDE.                                          |
|  `-l`  | `--language`      | Launch IDE with a specific language,j=java c=C/C++ r=Rust                |
|  `-h`  | `--help`          | Shows help message and exits.                                            |

## 4. Troubleshooting tips:

If you get an error like: "Dependent modules dictionary is empty." or other errors, try a make
clean.

## 5. FAQ:

Q1. If I already have an IDE project file, and I run command AIDEGen to generate
the same project file again, what will happen?

A1: The former IDEA project file will be overwritten by the newly generated one
from the aidegen command.

Q2: When do I need to re-run AIDEGen?

A2: Re-run AIDEGen after repo sync.

## 6. Hint:

1. In IntelliJ, uses [File] > [Invalidate Caches / Restart...] to force
       project panel updated when your IDE didn't sync.

2. If you run aidegen on a remote desktop, make sure there is no IntelliJ
       running in a different desktop session.

## 6. Feedback:

If you have any questions or feedback, contact aidegen_tnn@google.com.

If you have any bugs or feature requests email them to buganizer-system+429701@google.com.


