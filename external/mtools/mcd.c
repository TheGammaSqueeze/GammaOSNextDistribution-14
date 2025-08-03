/*  Copyright 1986-1992 Emmet P. Gray.
 *  Copyright 1996,1997,2000-2002,2009 Alain Knaff.
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
 * mcd.c: Change MSDOS directories
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mainloop.h"
#include "mtools.h"


static int mcd_callback(direntry_t *entry, MainParam_t *mp UNUSEDP)
{
	FILE *fp;

	if (!(fp = open_mcwd("w"))){
		fprintf(stderr,"mcd: Can't open mcwd .file for writing\n");
		return ERROR_ONE;
	}

	fprintPwd(fp, entry,0);
	fprintf(fp, "\n");
	fclose(fp);
	return GOT_ONE | STOP_NOW;
}

static void usage(int ret) NORETURN;
static void usage(int ret)
{
		fprintf(stderr, "Mtools version %s, dated %s\n",
			mversion, mdate);
		fprintf(stderr, "Usage: %s: [-V] [-i image] msdosdirectory\n",
			progname);
		exit(ret);
}


void mcd(int argc, char **argv, int type UNUSEDP) NORETURN;
void mcd(int argc, char **argv, int type UNUSEDP)
{
	struct MainParam_t mp;
	int c;
	
	while ((c = getopt(argc, argv, "i:")) != EOF) {
		switch(c) {
		case 'i':
			set_cmd_line_image(optarg);
			break;
		case 'h':
			usage(0);
		default:
			usage(1);
		}
	}

	if (argc > optind + 1)
		usage(1);

	init_mp(&mp);
	mp.lookupflags = ACCEPT_DIR | NO_DOTS;
	mp.dirCallback = mcd_callback;
	if (argc == 1) {
		printf("%s\n", mp.mcwd);
		exit(0);
	} else
		exit(main_loop(&mp, argv + optind, 1));
}
