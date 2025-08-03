Trusty userspace build system
=============================

The userspace build system is built on top of the lk build system, along with
the xbin extension for building multiple independent modules. Libraries are
built independently into static library archives and their build flags are
cached for use by future modules. When a library or app depends on another
library, its cached flags are added to the current module flags and re-exported
in the case of a library.

The top-level entry point for building a userspace app is the
`trusty-build-rule` function in user-tasks.mk. This helper function kicks off
generation of the necessary build rules using the new module system. See the doc
comment on the function's definition for more details on usage.

Library rules files must include `make/library.mk` after the appropriate
variables have been configured, and similarly, apps must include
`make/trusted_app.mk`. These makefiles handle the generation of the appropriate
build rules for the app or library. See the comments at the beginning of these
makefiles for a full list of the relevant variables that can be set in
library/app rules files to control the build.

`library.mk` includes `userspace_recurse.mk` to handle isolation of libraries
from each other and the main app, along with proper dependency chains and
propagation of flags up from dependencies. Users writing rules for libraries
should never need to interact with this makefile directly.


Example Library Rules
---------------------

Note the use of `library.mk` rather than `module.mk`. Apps rules are structured
the same, but must include `make/trusted_app.mk` instead of `library.mk`.

Libraries and apps must use the MODULE_MODULE_LIBRARY_DEPS variable to add other
libraries as dependencies. See `library.mk` for a full list of input variables
available for use in library `rules.mk` files.

    LOCAL_DIR := $(GET_LOCAL_DIR)
    MODULE := $(LOCAL_DIR)
    
    MODULE_SRCS := $(LOCAL_DIR)/source_file.c
    
    MODULE_LIBRARY_DEPS := trusty/user/base/lib/tipc
    
    include make/library.mk

Include Diagram
---------------

user-tasks.mk is included by lk make/build.mk as an extra build rule.

    user-tasks.mk
       |
       |--> arch/$(ARCH)/toolchain.mk
       |
       |--> For each SDK library $(LIB) in $(TRUSTY_SDK_MODULES):
       |      | MODULE := $(LIB)
       |      |
       |      |     /-------------------------------------------------\
       |      |     |                                                 |
       |      |     v                                                 |
       |     make/userspace_recurse.mk                                |
       |        |                                                     |
       |        | Reset all GLOBAL_ and MODULE_ variables to get      |
       |        | a clean state.                                      |
       |        |                                                     |
       |        |--> $(LIB)/rules.mk                                  |
       |        |      |                                              |
       |        |      |--> make/library.mk                           |
       |        |      |      |                                       |
       |        |      |      | Cache flags for this module in        |
       |        |      |      | _MODULES_$(MODULE)_$(FLAG) variables. |
       |        |      |      |                                       |
       |        |      |      | For each DEPENDENCY_MODULE in         |
       |        |      |      | $(MODULE_LIBRARY_DEPS)                |
       |        |      |      |---------------------------------------/
       |        |      |      |
       |        |      |      | Cache library and ldflags for this module in
       |        |      |      | _MODULES_$(MODULE)_$(FLAG) variables.
       |        |      |      |
       |        |      |      \--> make/module.mk --> make/compile.mk
       |        |      |
       |        |      | Restore GLOBAL_ and MODULE_ variables to saved values
       |        |      |
       |        |      | Add dependency's flags to current module's
       |        |      | private flags.
       |        |
       |        |
       |        | Restore GLOBAL_ and MODULE_ variables to saved values.
       |
       |
       \--> For each user task $TASK in $(ALL_USER_TASKS):
              | MODULE := $(TASK)
              |
             make/userspace_recurse.mk
                |
                | Reset all GLOBAL_ and MODULE_ variables to get
                | a clean state.
                |
                |--> $(TASK)/rules.mk
                |      |
                |      \--> make/trusted_app.mk
                |            | TRUSTY_APP = true
                |            |
                |            |    /---------------------------------------------------------------\
                |            |    |                                                               |
                |            |    v                                                               |
                |            |--> make/library.mk                                                 |
                |            |        |                                                           |
                |            |        | Cache flags for this module in _MODULES_$(MODULE)_$(FLAG) |
                |            |        | variables.                                                |
                |            |        |                                                           |
                |            |        | For each DEPENDENCY_MODULE in                             |
                |            |        | $(MODULE_LIBRARY_DEPS):                                   |
                |            |        |--> make/userspace_recurse.mk                              |
                |            |        |     |                                                     |
                |            |        |     | Reset all GLOBAL_ and MODULE_ variables to get      |
                |            |        |     | a clean state.                                      |
                |            |        |     |                                                     |
                |            |        |     | TRUSTY_APP = false                                  |
                |            |        |     |                                                     |
                |            |        |     |--> $(DEP)/rules.mk                                  |
                |            |        |     |      |                                              |
                |            |        |     |      \______________________________________________/
                |            |        |     |
                |            |        |     |
                |            |        |     | Restore GLOBAL_ and MODULE_ variables to saved values
                |            |        |     |
                |            |        |     | Add dependency's flags to current module's
                |            |        |     | private flags.
                |            |        |
                |            |        | Cache library and ldflags for this module in
                |            |        | _MODULES_$(MODULE)_$(FLAG) variables.
                |            |        |
                |            |        \--> make/module.mk --> make/compile.mk
                |            |
                |            |
                |            | Build app ELF binary from objects and all dependency libraries
                |            | (formerly done by xbin.mk).
                |
                |
                | Restore GLOBAL_ and MODULE_ variables to saved values.
