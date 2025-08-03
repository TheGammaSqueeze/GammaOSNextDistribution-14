# treble_build

## Description
Set of tools to run against the Android source tree and build graph.
In order to run the application it must be built via **m treble_build**
this will also create the needed build graph that the tool uses.

## Basic Commands
- treble_build -h
- treble_build [host, paths, query] [target...]


### host
treble_build host

Report the projects required to build the host tools.

### paths
treble_build [-build] paths [-1] -repo project:sha [-repo project:sha...]

For a given set of commits (project:sha), get the corresponding source
files.  Translate the source files into a set of build outputs using the
path (-1) or paths command.  If the build flag is given build the build
target closest to the source files.

### query
treble_build query -repo project:sha [-repo project:sha...]

For a given set of commits (project:sha), get the corresponding source
files.  Translate the source files into a set of inputs and outputs.

### report
By default a report is generated for all above commands, extra targets can
be included in the report by adding to the end of the command line.

See treble_build -h for options controlling report data.


