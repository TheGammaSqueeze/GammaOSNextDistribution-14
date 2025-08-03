#ifndef MTOOLS_MTOOLS_H
#define MTOOLS_MTOOLS_H
/*  Copyright 1996-2005,2007-2011 Alain Knaff.
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
 */
#include "msdos.h"
#include "llong.h"
typedef struct dos_name_t dos_name_t;

#if defined(OS_sco3)
#define MAXPATHLEN 1024
#include <signal.h>
extern int lockf(int, int, off_t);  /* SCO has no proper include file for lockf */
#endif




#ifndef OS_linux
#define BOOTSIZE 512
#else
#define BOOTSIZE 256
#endif

typedef struct doscp_t doscp_t;

#include "stream.h"


extern const char *short_illegals, *long_illegals;

#define maximize(target, max) do { \
  if(target > max) { \
    target = max; \
  } \
} while(0)

#define sizemaximize(target, max) do {		\
  if(max < 0) { \
    if(target > 0) \
      target = 0; \
  } else if(target > (size_t) max) {		\
	  target = (size_t) max;			\
  } \
} while(0)

#define minimize(target, min) do { \
  if(target < min) \
    target = min; \
} while(0)

#ifdef OS_linux
int get_sector_size(int fd);
#endif

int readwrite_sectors(int fd, /* file descriptor */
		      int *drive,
		      int rate,
		      int seektrack,
		      int track, int head, int sector, int size, /* address */
		      char *data,
		      int bytes,
		      int direction,
		      int retries);

char *unix_normalize (doscp_t *cp, char *ans, struct dos_name_t *dn,
		      size_t ans_size);
void dos_name(doscp_t *cp, const char *filename, int verbose, int *mangled,
	      struct dos_name_t *);
struct directory *mk_entry(const dos_name_t *filename, unsigned char attr,
			   unsigned int fat, uint32_t size, time_t date,
			   struct directory *ndir);

struct directory *mk_entry_from_base(const char *base, unsigned char attr,
				     unsigned int fat, uint32_t size, time_t date,
				     struct directory *ndir);

mt_off_t copyfile(Stream_t *Source, Stream_t *Target);
int getfreeMinClusters(Stream_t *Stream, uint32_t ref);

FILE *opentty(int mode);

int is_dir(Stream_t *Dir, char *path);

int dir_grow(Stream_t *Dir, int size);
int match(const wchar_t *, const wchar_t *, wchar_t *, int,  int);

wchar_t *unix_name(doscp_t *fromDos,
		   const char *base, const char *ext, uint8_t Case,
		   wchar_t *answer);
void *safe_malloc(size_t size);
Stream_t *open_dos2unix(Stream_t *Next,int convertCharset);
Stream_t *open_unix2dos(Stream_t *Next,int convertCharset);

extern int got_signal;
/* int do_gotsignal(char *, int);
#define got_signal do_gotsignal(__FILE__, __LINE__) */

void setup_signal(void);
#ifdef HAVE_SIGACTION
typedef struct { struct sigaction sa[4]; } saved_sig_state;
#else
typedef int saved_sig_state;
#endif

void allow_interrupts(saved_sig_state *ss);
void restore_interrupts(saved_sig_state *ss);

#define SET_INT(target, source) \
if(source)target=source

#define compare(ref,testee) ((ref) && (ref) != (testee))

UNUSED(static __inline__ char ch_toupper(char ch))
{
        return (char) toupper( (unsigned char) ch);
}

UNUSED(static __inline__ char ch_tolower(char ch))
{
        return (char) tolower( (unsigned char) ch);
}

UNUSED(static __inline__ wchar_t ch_towupper(wchar_t ch))
{
        return (wchar_t) towupper( (wint_t) ch);
}

UNUSED(static __inline__ wchar_t ch_towlower(wchar_t ch))
{
        return (wchar_t) towlower( (wint_t) ch);
}

UNUSED(static __inline__ void init_random(void))
{
	srandom((unsigned int)time (0));
}

UNUSED(static __inline__ size_t ptrdiff (const char *end, const char *begin))
{
	return (size_t) (end-begin);
}

Stream_t *GetFs(Stream_t *Fs);

void label_name_uc(doscp_t *cp, const char *filename, int verbose,
		   int *mangled, dos_name_t *ans);

void label_name_pc(doscp_t *cp, const char *filename, int verbose,
		   int *mangled, dos_name_t *ans);

/* environmental variables */
extern unsigned int mtools_skip_check;
extern unsigned int mtools_fat_compatibility;
extern unsigned int mtools_ignore_short_case;
extern unsigned int mtools_no_vfat;
extern unsigned int mtools_numeric_tail;
extern unsigned int mtools_dotted_dir;
extern unsigned int mtools_lock_timeout;
extern unsigned int mtools_twenty_four_hour_clock;
extern const char *mtools_date_string;
extern uint8_t mtools_rate_0, mtools_rate_any;
extern unsigned int mtools_default_codepage;
extern int mtools_raw_tty;

extern int batchmode;

char get_default_drive(void);
void set_cmd_line_image(char *img);
void check_number_parse_errno(char c, const char *optarg, char *endptr);
void read_config(void);
off_t str_to_offset_with_end(const char *str, char **endp);
mt_off_t str_to_off_with_end(const char *str, char **endp);
off_t str_to_offset(char *str);
uint32_t parseSize(char *sizeStr);
unsigned int strtoui(const char *nptr, char **endptr, int base);
unsigned int atoui(const char *nptr);
#ifndef HAVE_STRTOI
int strtoi(const char *nptr, char **endptr, int base);
#endif
unsigned long atoul(const char *nptr);
uint8_t strtou8(const char *nptr, char **endptr, int base);
uint8_t atou8(const char *str);
uint16_t strtou16(const char *nptr, char **endptr, int base);
uint16_t atou16(const char *str);
uint32_t strtou32(const char *nptr, char **endptr, int base);
uint32_t atou32(const char *str);

#define New(type) ((type*)(calloc(1,sizeof(type))))
#define Grow(adr,n,type) ((type*)(realloc((char *)adr,n*sizeof(type))))
#define Free(adr) (free((char *)adr))
#define NewArray(size,type) ((type*)(calloc((size),sizeof(type))))

void mattrib(int argc, char **argv, int type);
void mbadblocks(int argc, char **argv, int type);
void mcat(int argc, char **argv, int type);
void mcd(int argc, char **argv, int type);
void mcopy(int argc, char **argv, int type);
void mdel(int argc, char **argv, int type);
void mdir(int argc, char **argv, int type);
void mdoctorfat(int argc, char **argv, int type);
void mdu(int argc, char **argv, int type);
void mformat(int argc, char **argv, int type);
void minfo(int argc, char **argv, int type);
void mlabel(int argc, char **argv, int type);
void mmd(int argc, char **argv, int type);
void mmount(int argc, char **argv, int type);
void mmove(int argc, char **argv, int type);
void mpartition(int argc, char **argv, int type);
void mshortname(int argc, char **argv, int mtype);
void mshowfat(int argc, char **argv, int mtype);
void mtoolstest(int argc, char **argv, int type);
void mzip(int argc, char **argv, int type);

extern int noPrivileges;
void init_privs(void);
void reclaim_privs(void);
void drop_privs(void);
void destroy_privs(void);
uid_t get_real_uid(void);
void closeExec(int fd);

extern const char *progname;

void print_sector(const char *message, unsigned char *data, int size);
time_t getTimeNow(time_t *now);

#ifdef USING_NEW_VOLD
char *getVoldName(struct device *dev, char *name);
#endif


Stream_t *OpenDir(const char *filename);
/* int unix_dir_loop(Stream_t *Stream, MainParam_t *mp);
int unix_loop(MainParam_t *mp, char *arg); */

struct dirCache_t **getDirCacheP(Stream_t *Stream);
int isRootDir(Stream_t *Stream);
unsigned int getStart(Stream_t *Dir, struct directory *dir);
unsigned int countBlocks(Stream_t *Dir, unsigned int block);
char getDrive(Stream_t *Stream);


void printOom(void);
int ask_confirmation(const char *, ...)  __attribute__ ((format (printf, 1, 2)));

int helpFlag(int, char **);

char *get_homedir(void);
#define EXPAND_BUF 2048
const char *expand(const char *, char *);
FILE *open_mcwd(const char *mode);
void unlink_mcwd(void);

#ifndef OS_mingw32msvc
ssize_t safePopenOut(const char **command, char *output, size_t len);
#endif

#define ROUND_DOWN(value, grain) ((value) - (value) % (grain))
#define ROUND_UP(value, grain) ROUND_DOWN((value) + (grain)-1, (grain))

#ifndef O_BINARY
#define O_BINARY 0
#endif

#endif
