# included from the main makefile to include a set of rules.mk to satisfy
# the current MODULE list. If as a byproduct of including the rules.mk
# more stuff shows up on the MODULE list, recurse

# sort and filter out any modules that have already been included
MODULES := $(sort $(MODULES))
MODULES := $(filter-out $(ALLMODULES),$(MODULES))

HOST_MODULES := $(sort $(HOST_MODULES))
HOST_MODULES := $(filter-out $(ALLHOSTMODULES),$(HOST_MODULES))

ifneq ($(MODULES)$(HOST_MODULES),)

ALLMODULES += $(MODULES)
ALLMODULES := $(sort $(ALLMODULES))
INCMODULES := $(MODULES)
MODULES :=

ALLHOSTMODULES += $(HOST_MODULES)
ALLHOSTMODULES := $(sort $(ALLHOSTMODULES))
HOST_MODULES :=

# Needed for a true default
MODULE_ADD_IMPLICIT_DEPS := true

$(info including $(INCMODULES))
include $(addsuffix /rules.mk,$(INCMODULES))

INCMODULES :=

include make/recurse.mk

endif

