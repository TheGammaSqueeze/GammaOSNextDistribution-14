# SPDX-License-Identifier: LGPL-2.1

include $(src)/scripts/utils.mk

bdir:=$(obj)/utest

TARGETS = $(bdir)/trace-utest

OBJS =
OBJS += trace-utest.o
OBJS += tracefs-utest.o

LIBS += -lcunit				\
	-ldl				\
	$(obj)/lib/libtracefs.a

OBJS := $(OBJS:%.o=$(bdir)/%.o)

$(bdir):
	@mkdir -p $(bdir)

$(OBJS): | $(bdir)

$(bdir)/trace-utest: $(OBJS) $(obj)/lib/libtracefs.a
	$(Q)$(do_app_build)

$(bdir)/%.o: %.c
	$(Q)$(call do_fpic_compile)

-include .*.d

test: $(TARGETS)

clean:
	$(Q)$(call do_clean,$(TARGETS) $(bdir)/*.o $(bdir)/.*.d)
