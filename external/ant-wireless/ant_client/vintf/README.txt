The compatibility_matrix.xml file in this directory contains a fragment that must be merged into
the framework compatibility matrix located at hardware/interfaces/compatibility_matrix.current.xml
if the ANTHALService apk is included in the system image.

The hal here is listed as non-optional, since the ANTHALService will consider being unable to
bind an error.

