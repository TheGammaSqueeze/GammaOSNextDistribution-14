SHELL=/bin/sh
MAKE = make
SUBDIRS ?= wmediumd
BIN = wmediumd/wmediumd
PREFIX ?= /usr
BINDIR ?= /bin

all:

	@for i in $(SUBDIRS); do \
	echo "make all in $$i..."; \
	(cd $$i; $(MAKE) all); done

clean:

	@for i in $(SUBDIRS); do \
	echo "Clearing in $$i..."; \
	(cd $$i; $(MAKE) clean); done

install: all
	install -Dm 0755 $(BIN) $(DESTDIR)$(PREFIX)$(BINDIR)
