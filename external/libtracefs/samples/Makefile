# SPDX-License-Identifier: LGPL-2.1

#
# The samples are pulled out of the examples used in the man pages
# that are located in the Documentation directory.
#

include $(src)/scripts/utils.mk

EXAMPLES :=
EXAMPLES += dynevents
EXAMPLES += kprobes
EXAMPLES += eprobes
EXAMPLES += uprobes
EXAMPLES += synth
EXAMPLES += error
EXAMPLES += filter
EXAMPLES += function-filter
EXAMPLES += hist
EXAMPLES += hist-cont
EXAMPLES += tracer
EXAMPLES += stream
EXAMPLES += instances-affinity
EXAMPLES += cpu

TARGETS :=
TARGETS += sqlhist
TARGETS += $(EXAMPLES)

sdir := $(obj)/bin

TARGETS := $(patsubst %,$(sdir)/%,$(TARGETS))

all: $(TARGETS)

$(bdir)/sqlhist.c: $(src)/Documentation/libtracefs-sql.txt
	$(call extract_example,$<,$@)

$(bdir)/%.c: ../Documentation/libtracefs-%.txt
	$(call extract_example,$<,$@)

$(sdir):
	@mkdir -p $(sdir)

sqlhist: $(sdir)/sqlhist

$(TARGETS): $(sdir)

# sqlhist is unique and stands on its own
$(sdir)/sqlhist: $(bdir)/sqlhist.c $(LIBTRACEFS_STATIC)
	$(call do_sample_build,$@,$<)

$(sdir)/%: $(bdir)/%.o
	$(call do_sample_build,$@,$<)

$(EXAMPLES): $(patsubst %,$(sdir)/%,$(TARGETS))

## The intermediate files get removed by Make.
## To examine the .c files created by one of the man pages,
## uncomment the below, and replace the XX with the exec example
## name, and the file will not be discarded by make.
#
# $(bdir)/XX.o: $(bdir)/XX.c
#	$(CC) -g -Wall $(CFLAGS) -c -o $@ $^ -I../include/ $(LIBTRACEEVENT_INCLUDES)

$(bdir)/%.o: $(bdir)/%.c
	$(call do_sample_obj,$@,$^)

$(bdir)/XX.o: $(bdir)/hist.c
	$(CC) -g -Wall $(CFLAGS) -c -o $@ $^ -I../include/ $(LIBTRACEEVENT_INCLUDES)

clean:
	$(Q)$(call do_clean,$(sdir)/* $(bdir)/sqlhist.c $(bdir)/sqlhist.o)

.PHONY: sqlhist
