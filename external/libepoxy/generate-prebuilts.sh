#!/bin/sh
for xml in gl egl; do
	PYTHONDONTWRITEBYTECODE=1 python src/gen_dispatch.py \
		--srcdir $PWD/prebuilt-intermediates/src \
		--includedir $PWD/prebuilt-intermediates/include/epoxy \
		registry/$xml.xml
done
