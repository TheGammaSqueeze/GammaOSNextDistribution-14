#!/bin/bash

# This is the rule from the end of Makefile, with $* instead of $^ on the
# first line, and $ instead of $$ on the other lines, but otherwise identical.
# (Which is why the indentation is weird!)

	files="$*" ; \
	for s in `grep -B 3 '\<dlsym' $files | sed -n '/snprintf/{s:.*"\([^"]*\)".*:\1:;s:%s::;p}'` ; do \
		sed -n '/'$s'[^ ]* =/{s:.* \([^ ]*'$s'[^ ]*\) .*:extern char \1[] __attribute__((weak)); if (!strcmp(sym, "\1")) return \1;:;p}' $files ; \
	done
