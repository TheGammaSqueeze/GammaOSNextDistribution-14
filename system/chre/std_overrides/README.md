### CHRE Overrides

CHRE provides nanoapps with a minimal list of overrides to standard library
functions that are not supported by CHRE, but can be redirected to a supported
function, or when failing silently should be of minimal consequences to the
nanoapp. Two possible scenarios where the developers might need to use these
overrides are:

* Generated code
* Third-party code/libraries

The overrides makefile is included in the nanoapp build by and enabled by
default, but the developers can disable it by setting a flag in the nanoapp Makefile:
`CHRE_STD_OVERRIDES_ALLOWED = false`

It is expected that the nanoapp developers only leverage these overrides while
working towards zero overrides usage.

