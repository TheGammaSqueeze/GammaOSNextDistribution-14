/*  Copyright 1999-2003,2007,2009 Alain Knaff.
 *  This file is part of mtools.
 *
 *  Mtools is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Mtools is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Mtools.  If not, see <http://www.gnu.org/licenses/>.
 *
 * mcat.c
 * Same thing as cat /dev/fd0 or cat file >/dev/fd0
 * Something, that isn't possible with floppyd anymore.
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "mainloop.h"
#include "open_image.h"

static void usage(void) NORETURN;
static void usage(void)
{
	fprintf(stderr, "Mtools version %s, dated %s\n",
		mversion, mdate);
	fprintf(stderr, "Usage: mcat [-V] [-w] device\n");
	fprintf(stderr, "       -w write on device else read\n");
	exit(1);
}

#ifdef __CYGWIN__
#define BUF_SIZE 512u
#else
#define BUF_SIZE 16000u
#endif

static size_t bufLen(size_t blocksize, mt_off_t totalSize, mt_off_t address)
{
	if(totalSize == 0)
		return blocksize;
	if((mt_off_t) blocksize > totalSize - address)
		return (size_t) (totalSize - address);
	return blocksize;
}

void mcat(int argc, char **argv, int type UNUSEDP) NORETURN;
void mcat(int argc, char **argv, int type UNUSEDP)
{
	struct device *dev;
	struct device out_dev;
	char drive, name[EXPAND_BUF];
        char errmsg[200];
        Stream_t *Stream;
	char buf[BUF_SIZE];

	mt_off_t address = 0;
	mt_off_t maxSize = 0;

	char mode = O_RDONLY;
	int c;

	noPrivileges = 1;

	if (argc < 2) {
		usage();
	}

	while ((c = getopt(argc,argv, "wi:"))!= EOF) {
		switch (c) {
		case 'w':
			mode = O_WRONLY;
			break;
		case 'i':
			set_cmd_line_image(optarg);
			break;
		default:
			usage();
		}
	}

	if (argc - optind > 1)
		usage();
	if(argc - optind == 1) {
		if(!argv[optind][0] || argv[optind][1] != ':')
			usage();
		drive = ch_toupper(argv[argc -1][0]);
	} else {
		drive = get_default_drive();
	}

        /* check out a drive whose letter and parameters match */
        sprintf(errmsg, "Drive '%c:' not supported", drive);
        Stream = NULL;
        for (dev=devices; dev->name; dev++) {
                FREE(&Stream);
                if (dev->drive != drive)
                        continue;
                out_dev = *dev;
                expand(dev->name,name);
#ifdef USING_NEW_VOLD
                strcpy(name, getVoldName(dev, name));
#endif

		Stream = OpenImage(&out_dev, dev, name, mode,
				   errmsg, ALWAYS_GET_GEOMETRY, mode, &maxSize,
				   NULL, NULL);
                if( !Stream)
                        continue;
                break;
        }

        /* print error msg if needed */
        if ( dev->drive == 0 )
		goto exit_1;

	if (mode == O_WRONLY) {
		size_t len;
		mt_off_t size=0;
		if(chs_to_totsectors(&out_dev, errmsg) < 0 ||
		   check_if_sectors_fit(out_dev.tot_sectors,
					maxSize, 512, errmsg))
			goto exit_1;
		size = 512 * (mt_off_t) out_dev.tot_sectors;
		while ((len = fread(buf, 1,
				    bufLen(BUF_SIZE, size, address),
				    stdin)) > 0) {
			ssize_t r = PWRITES(Stream, buf, address, len);
			fprintf(stderr, "Wrote to %d\n", (int) address);
			if(r < 0)
				break;
			address += len;
		}
	} else {
		ssize_t len;
		while ((len = PREADS(Stream, buf, address, BUF_SIZE)) > 0) {
			fwrite(buf, 1, (size_t) len, stdout);
			address += (size_t) len;
		}
	}

	FREE(&Stream);
	exit(0);
exit_1:
	FREE(&Stream);
	fprintf(stderr,"%s\n",errmsg);
	exit(1);
}
