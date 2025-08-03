/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GEN_SYSCALL_NUMBERS_RISCV64_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GEN_SYSCALL_NUMBERS_RISCV64_H_

namespace berberis {

enum {
  GUEST_NR_accept = 202,
  GUEST_NR_accept4 = 242,
  GUEST_NR_acct = 89,
  GUEST_NR_add_key = 217,
  GUEST_NR_adjtimex = 171,
  GUEST_NR_bind = 200,
  GUEST_NR_bpf = 280,
  GUEST_NR_brk = 214,
  GUEST_NR_capget = 90,
  GUEST_NR_capset = 91,
  GUEST_NR_chdir = 49,
  GUEST_NR_chroot = 51,
  GUEST_NR_clock_adjtime = 266,
  GUEST_NR_clock_getres = 114,
  GUEST_NR_clock_gettime = 113,
  GUEST_NR_clock_nanosleep = 115,
  GUEST_NR_clock_settime = 112,
  GUEST_NR_clone = 220,
  GUEST_NR_clone3 = 435,
  GUEST_NR_close = 57,
  GUEST_NR_close_range = 436,
  GUEST_NR_connect = 203,
  GUEST_NR_copy_file_range = 285,
  GUEST_NR_delete_module = 106,
  GUEST_NR_dup = 23,
  GUEST_NR_dup3 = 24,
  GUEST_NR_epoll_create1 = 20,
  GUEST_NR_epoll_ctl = 21,
  GUEST_NR_epoll_pwait = 22,
  GUEST_NR_epoll_pwait2 = 441,
  GUEST_NR_eventfd2 = 19,
  GUEST_NR_execve = 221,
  GUEST_NR_execveat = 281,
  GUEST_NR_exit = 93,
  GUEST_NR_exit_group = 94,
  GUEST_NR_faccessat = 48,
  GUEST_NR_faccessat2 = 439,
  GUEST_NR_fadvise64 = 223,
  GUEST_NR_fallocate = 47,
  GUEST_NR_fanotify_init = 262,
  GUEST_NR_fanotify_mark = 263,
  GUEST_NR_fchdir = 50,
  GUEST_NR_fchmod = 52,
  GUEST_NR_fchmodat = 53,
  GUEST_NR_fchown = 55,
  GUEST_NR_fchownat = 54,
  GUEST_NR_fcntl = 25,
  GUEST_NR_fdatasync = 83,
  GUEST_NR_fgetxattr = 10,
  GUEST_NR_finit_module = 273,
  GUEST_NR_flistxattr = 13,
  GUEST_NR_flock = 32,
  GUEST_NR_fremovexattr = 16,
  GUEST_NR_fsconfig = 431,
  GUEST_NR_fsetxattr = 7,
  GUEST_NR_fsmount = 432,
  GUEST_NR_fsopen = 430,
  GUEST_NR_fspick = 433,
  GUEST_NR_fstat = 80,
  GUEST_NR_fstatfs = 44,
  GUEST_NR_fsync = 82,
  GUEST_NR_ftruncate = 46,
  GUEST_NR_futex = 98,
  GUEST_NR_futex_waitv = 449,
  GUEST_NR_get_mempolicy = 236,
  GUEST_NR_get_robust_list = 100,
  GUEST_NR_getcpu = 168,
  GUEST_NR_getcwd = 17,
  GUEST_NR_getdents64 = 61,
  GUEST_NR_getegid = 177,
  GUEST_NR_geteuid = 175,
  GUEST_NR_getgid = 176,
  GUEST_NR_getgroups = 158,
  GUEST_NR_getitimer = 102,
  GUEST_NR_getpeername = 205,
  GUEST_NR_getpgid = 155,
  GUEST_NR_getpid = 172,
  GUEST_NR_getppid = 173,
  GUEST_NR_getpriority = 141,
  GUEST_NR_getrandom = 278,
  GUEST_NR_getresgid = 150,
  GUEST_NR_getresuid = 148,
  GUEST_NR_getrlimit = 163,
  GUEST_NR_getrusage = 165,
  GUEST_NR_getsid = 156,
  GUEST_NR_getsockname = 204,
  GUEST_NR_getsockopt = 209,
  GUEST_NR_gettid = 178,
  GUEST_NR_gettimeofday = 169,
  GUEST_NR_getuid = 174,
  GUEST_NR_getxattr = 8,
  GUEST_NR_init_module = 105,
  GUEST_NR_inotify_add_watch = 27,
  GUEST_NR_inotify_init1 = 26,
  GUEST_NR_inotify_rm_watch = 28,
  GUEST_NR_io_cancel = 3,
  GUEST_NR_io_destroy = 1,
  GUEST_NR_io_getevents = 4,
  GUEST_NR_io_pgetevents = 292,
  GUEST_NR_io_setup = 0,
  GUEST_NR_io_submit = 2,
  GUEST_NR_io_uring_enter = 426,
  GUEST_NR_io_uring_register = 427,
  GUEST_NR_io_uring_setup = 425,
  GUEST_NR_ioctl = 29,
  GUEST_NR_ioprio_get = 31,
  GUEST_NR_ioprio_set = 30,
  GUEST_NR_kcmp = 272,
  GUEST_NR_kexec_file_load = 294,
  GUEST_NR_kexec_load = 104,
  GUEST_NR_keyctl = 219,
  GUEST_NR_kill = 129,
  GUEST_NR_landlock_add_rule = 445,
  GUEST_NR_landlock_create_ruleset = 444,
  GUEST_NR_landlock_restrict_self = 446,
  GUEST_NR_lgetxattr = 9,
  GUEST_NR_linkat = 37,
  GUEST_NR_listen = 201,
  GUEST_NR_listxattr = 11,
  GUEST_NR_llistxattr = 12,
  GUEST_NR_lookup_dcookie = 18,
  GUEST_NR_lremovexattr = 15,
  GUEST_NR_lseek = 62,
  GUEST_NR_lsetxattr = 6,
  GUEST_NR_madvise = 233,
  GUEST_NR_mbind = 235,
  GUEST_NR_membarrier = 283,
  GUEST_NR_memfd_create = 279,
  GUEST_NR_memfd_secret = 447,
  GUEST_NR_migrate_pages = 238,
  GUEST_NR_mincore = 232,
  GUEST_NR_mkdirat = 34,
  GUEST_NR_mknodat = 33,
  GUEST_NR_mlock = 228,
  GUEST_NR_mlock2 = 284,
  GUEST_NR_mlockall = 230,
  GUEST_NR_mmap = 222,
  GUEST_NR_mount = 40,
  GUEST_NR_mount_setattr = 442,
  GUEST_NR_move_mount = 429,
  GUEST_NR_move_pages = 239,
  GUEST_NR_mprotect = 226,
  GUEST_NR_mq_getsetattr = 185,
  GUEST_NR_mq_notify = 184,
  GUEST_NR_mq_open = 180,
  GUEST_NR_mq_timedreceive = 183,
  GUEST_NR_mq_timedsend = 182,
  GUEST_NR_mq_unlink = 181,
  GUEST_NR_mremap = 216,
  GUEST_NR_msgctl = 187,
  GUEST_NR_msgget = 186,
  GUEST_NR_msgrcv = 188,
  GUEST_NR_msgsnd = 189,
  GUEST_NR_msync = 227,
  GUEST_NR_munlock = 229,
  GUEST_NR_munlockall = 231,
  GUEST_NR_munmap = 215,
  GUEST_NR_name_to_handle_at = 264,
  GUEST_NR_nanosleep = 101,
  GUEST_NR_newfstatat = 79,
  GUEST_NR_nfsservctl = 42,
  GUEST_NR_open_by_handle_at = 265,
  GUEST_NR_open_tree = 428,
  GUEST_NR_openat = 56,
  GUEST_NR_openat2 = 437,
  GUEST_NR_perf_event_open = 241,
  GUEST_NR_personality = 92,
  GUEST_NR_pidfd_getfd = 438,
  GUEST_NR_pidfd_open = 434,
  GUEST_NR_pidfd_send_signal = 424,
  GUEST_NR_pipe2 = 59,
  GUEST_NR_pivot_root = 41,
  GUEST_NR_pkey_alloc = 289,
  GUEST_NR_pkey_free = 290,
  GUEST_NR_pkey_mprotect = 288,
  GUEST_NR_ppoll = 73,
  GUEST_NR_prctl = 167,
  GUEST_NR_pread64 = 67,
  GUEST_NR_preadv = 69,
  GUEST_NR_preadv2 = 286,
  GUEST_NR_prlimit64 = 261,
  GUEST_NR_process_madvise = 440,
  GUEST_NR_process_mrelease = 448,
  GUEST_NR_process_vm_readv = 270,
  GUEST_NR_process_vm_writev = 271,
  GUEST_NR_pselect6 = 72,
  GUEST_NR_ptrace = 117,
  GUEST_NR_pwrite64 = 68,
  GUEST_NR_pwritev = 70,
  GUEST_NR_pwritev2 = 287,
  GUEST_NR_quotactl = 60,
  GUEST_NR_quotactl_fd = 443,
  GUEST_NR_read = 63,
  GUEST_NR_readahead = 213,
  GUEST_NR_readlinkat = 78,
  GUEST_NR_readv = 65,
  GUEST_NR_reboot = 142,
  GUEST_NR_recvfrom = 207,
  GUEST_NR_recvmmsg = 243,
  GUEST_NR_recvmsg = 212,
  GUEST_NR_remap_file_pages = 234,
  GUEST_NR_removexattr = 14,
  GUEST_NR_renameat = 38,
  GUEST_NR_renameat2 = 276,
  GUEST_NR_request_key = 218,
  GUEST_NR_restart_syscall = 128,
  GUEST_NR_rseq = 293,
  GUEST_NR_rt_sigaction = 134,
  GUEST_NR_rt_sigpending = 136,
  GUEST_NR_rt_sigprocmask = 135,
  GUEST_NR_rt_sigqueueinfo = 138,
  GUEST_NR_rt_sigreturn = 139,
  GUEST_NR_rt_sigsuspend = 133,
  GUEST_NR_rt_sigtimedwait = 137,
  GUEST_NR_rt_tgsigqueueinfo = 240,
  GUEST_NR_sched_get_priority_max = 125,
  GUEST_NR_sched_get_priority_min = 126,
  GUEST_NR_sched_getaffinity = 123,
  GUEST_NR_sched_getattr = 275,
  GUEST_NR_sched_getparam = 121,
  GUEST_NR_sched_getscheduler = 120,
  GUEST_NR_sched_rr_get_interval = 127,
  GUEST_NR_sched_setaffinity = 122,
  GUEST_NR_sched_setattr = 274,
  GUEST_NR_sched_setparam = 118,
  GUEST_NR_sched_setscheduler = 119,
  GUEST_NR_sched_yield = 124,
  GUEST_NR_seccomp = 277,
  GUEST_NR_semctl = 191,
  GUEST_NR_semget = 190,
  GUEST_NR_semop = 193,
  GUEST_NR_semtimedop = 192,
  GUEST_NR_sendfile = 71,
  GUEST_NR_sendmmsg = 269,
  GUEST_NR_sendmsg = 211,
  GUEST_NR_sendto = 206,
  GUEST_NR_set_mempolicy = 237,
  GUEST_NR_set_mempolicy_home_node = 450,
  GUEST_NR_set_robust_list = 99,
  GUEST_NR_set_tid_address = 96,
  GUEST_NR_setdomainname = 162,
  GUEST_NR_setfsgid = 152,
  GUEST_NR_setfsuid = 151,
  GUEST_NR_setgid = 144,
  GUEST_NR_setgroups = 159,
  GUEST_NR_sethostname = 161,
  GUEST_NR_setitimer = 103,
  GUEST_NR_setns = 268,
  GUEST_NR_setpgid = 154,
  GUEST_NR_setpriority = 140,
  GUEST_NR_setregid = 143,
  GUEST_NR_setresgid = 149,
  GUEST_NR_setresuid = 147,
  GUEST_NR_setreuid = 145,
  GUEST_NR_setrlimit = 164,
  GUEST_NR_setsid = 157,
  GUEST_NR_setsockopt = 208,
  GUEST_NR_settimeofday = 170,
  GUEST_NR_setuid = 146,
  GUEST_NR_setxattr = 5,
  GUEST_NR_shmat = 196,
  GUEST_NR_shmctl = 195,
  GUEST_NR_shmdt = 197,
  GUEST_NR_shmget = 194,
  GUEST_NR_shutdown = 210,
  GUEST_NR_sigaltstack = 132,
  GUEST_NR_signalfd4 = 74,
  GUEST_NR_socket = 198,
  GUEST_NR_socketpair = 199,
  GUEST_NR_splice = 76,
  GUEST_NR_statfs = 43,
  GUEST_NR_statx = 291,
  GUEST_NR_swapoff = 225,
  GUEST_NR_swapon = 224,
  GUEST_NR_symlinkat = 36,
  GUEST_NR_sync = 81,
  GUEST_NR_sync_file_range = 84,
  GUEST_NR_syncfs = 267,
  GUEST_NR_sysinfo = 179,
  GUEST_NR_syslog = 116,
  GUEST_NR_tee = 77,
  GUEST_NR_tgkill = 131,
  GUEST_NR_timer_create = 107,
  GUEST_NR_timer_delete = 111,
  GUEST_NR_timer_getoverrun = 109,
  GUEST_NR_timer_gettime = 108,
  GUEST_NR_timer_settime = 110,
  GUEST_NR_timerfd_create = 85,
  GUEST_NR_timerfd_gettime = 87,
  GUEST_NR_timerfd_settime = 86,
  GUEST_NR_times = 153,
  GUEST_NR_tkill = 130,
  GUEST_NR_truncate = 45,
  GUEST_NR_umask = 166,
  GUEST_NR_umount2 = 39,
  GUEST_NR_uname = 160,
  GUEST_NR_unlinkat = 35,
  GUEST_NR_unshare = 97,
  GUEST_NR_userfaultfd = 282,
  GUEST_NR_utimensat = 88,
  GUEST_NR_vhangup = 58,
  GUEST_NR_vmsplice = 75,
  GUEST_NR_wait4 = 260,
  GUEST_NR_waitid = 95,
  GUEST_NR_write = 64,
  GUEST_NR_writev = 66,
};

inline int ToHostSyscallNumber(int nr) {
  switch (nr) {
    case 202:  // __NR_accept
      return 43;
    case 242:  // __NR_accept4
      return 288;
    case 89:  // __NR_acct
      return 163;
    case 217:  // __NR_add_key
      return 248;
    case 171:  // __NR_adjtimex
      return 159;
    case 200:  // __NR_bind
      return 49;
    case 280:  // __NR_bpf
      return 321;
    case 214:  // __NR_brk
      return 12;
    case 90:  // __NR_capget
      return 125;
    case 91:  // __NR_capset
      return 126;
    case 49:  // __NR_chdir
      return 80;
    case 51:  // __NR_chroot
      return 161;
    case 266:  // __NR_clock_adjtime
      return 305;
    case 114:  // __NR_clock_getres
      return 229;
    case 113:  // __NR_clock_gettime
      return 228;
    case 115:  // __NR_clock_nanosleep
      return 230;
    case 112:  // __NR_clock_settime
      return 227;
    case 220:  // __NR_clone
      return 56;
    case 435:  // __NR_clone3
      return 435;
    case 57:  // __NR_close
      return 3;
    case 436:  // __NR_close_range
      return 436;
    case 203:  // __NR_connect
      return 42;
    case 285:  // __NR_copy_file_range
      return 326;
    case 106:  // __NR_delete_module
      return 176;
    case 23:  // __NR_dup
      return 32;
    case 24:  // __NR_dup3
      return 292;
    case 20:  // __NR_epoll_create1
      return 291;
    case 21:  // __NR_epoll_ctl
      return 233;
    case 22:  // __NR_epoll_pwait
      return 281;
    case 441:  // __NR_epoll_pwait2
      return 441;
    case 19:  // __NR_eventfd2
      return 290;
    case 221:  // __NR_execve
      return 59;
    case 281:  // __NR_execveat
      return 322;
    case 93:  // __NR_exit
      return 60;
    case 94:  // __NR_exit_group
      return 231;
    case 48:  // __NR_faccessat
      return 269;
    case 439:  // __NR_faccessat2
      return 439;
    case 223:  // __NR_fadvise64
      return 221;
    case 47:  // __NR_fallocate
      return 285;
    case 262:  // __NR_fanotify_init
      return 300;
    case 263:  // __NR_fanotify_mark
      return 301;
    case 50:  // __NR_fchdir
      return 81;
    case 52:  // __NR_fchmod
      return 91;
    case 53:  // __NR_fchmodat
      return 268;
    case 55:  // __NR_fchown
      return 93;
    case 54:  // __NR_fchownat
      return 260;
    case 25:  // __NR_fcntl
      return 72;
    case 83:  // __NR_fdatasync
      return 75;
    case 10:  // __NR_fgetxattr
      return 193;
    case 273:  // __NR_finit_module
      return 313;
    case 13:  // __NR_flistxattr
      return 196;
    case 32:  // __NR_flock
      return 73;
    case 16:  // __NR_fremovexattr
      return 199;
    case 431:  // __NR_fsconfig
      return 431;
    case 7:  // __NR_fsetxattr
      return 190;
    case 432:  // __NR_fsmount
      return 432;
    case 430:  // __NR_fsopen
      return 430;
    case 433:  // __NR_fspick
      return 433;
    case 80:  // __NR_fstat
      return 5;
    case 44:  // __NR_fstatfs
      return 138;
    case 82:  // __NR_fsync
      return 74;
    case 46:  // __NR_ftruncate
      return 77;
    case 98:  // __NR_futex
      return 202;
    case 449:  // __NR_futex_waitv
      return 449;
    case 236:  // __NR_get_mempolicy
      return 239;
    case 100:  // __NR_get_robust_list
      return 274;
    case 168:  // __NR_getcpu
      return 309;
    case 17:  // __NR_getcwd
      return 79;
    case 61:  // __NR_getdents64
      return 217;
    case 177:  // __NR_getegid
      return 108;
    case 175:  // __NR_geteuid
      return 107;
    case 176:  // __NR_getgid
      return 104;
    case 158:  // __NR_getgroups
      return 115;
    case 102:  // __NR_getitimer
      return 36;
    case 205:  // __NR_getpeername
      return 52;
    case 155:  // __NR_getpgid
      return 121;
    case 172:  // __NR_getpid
      return 39;
    case 173:  // __NR_getppid
      return 110;
    case 141:  // __NR_getpriority
      return 140;
    case 278:  // __NR_getrandom
      return 318;
    case 150:  // __NR_getresgid
      return 120;
    case 148:  // __NR_getresuid
      return 118;
    case 163:  // __NR_getrlimit
      return 97;
    case 165:  // __NR_getrusage
      return 98;
    case 156:  // __NR_getsid
      return 124;
    case 204:  // __NR_getsockname
      return 51;
    case 209:  // __NR_getsockopt
      return 55;
    case 178:  // __NR_gettid
      return 186;
    case 169:  // __NR_gettimeofday
      return 96;
    case 174:  // __NR_getuid
      return 102;
    case 8:  // __NR_getxattr
      return 191;
    case 105:  // __NR_init_module
      return 175;
    case 27:  // __NR_inotify_add_watch
      return 254;
    case 26:  // __NR_inotify_init1
      return 294;
    case 28:  // __NR_inotify_rm_watch
      return 255;
    case 3:  // __NR_io_cancel
      return 210;
    case 1:  // __NR_io_destroy
      return 207;
    case 4:  // __NR_io_getevents
      return 208;
    case 292:  // __NR_io_pgetevents
      return 333;
    case 0:  // __NR_io_setup
      return 206;
    case 2:  // __NR_io_submit
      return 209;
    case 426:  // __NR_io_uring_enter
      return 426;
    case 427:  // __NR_io_uring_register
      return 427;
    case 425:  // __NR_io_uring_setup
      return 425;
    case 29:  // __NR_ioctl
      return 16;
    case 31:  // __NR_ioprio_get
      return 252;
    case 30:  // __NR_ioprio_set
      return 251;
    case 272:  // __NR_kcmp
      return 312;
    case 294:  // __NR_kexec_file_load
      return 320;
    case 104:  // __NR_kexec_load
      return 246;
    case 219:  // __NR_keyctl
      return 250;
    case 129:  // __NR_kill
      return 62;
    case 445:  // __NR_landlock_add_rule
      return 445;
    case 444:  // __NR_landlock_create_ruleset
      return 444;
    case 446:  // __NR_landlock_restrict_self
      return 446;
    case 9:  // __NR_lgetxattr
      return 192;
    case 37:  // __NR_linkat
      return 265;
    case 201:  // __NR_listen
      return 50;
    case 11:  // __NR_listxattr
      return 194;
    case 12:  // __NR_llistxattr
      return 195;
    case 18:  // __NR_lookup_dcookie
      return 212;
    case 15:  // __NR_lremovexattr
      return 198;
    case 62:  // __NR_lseek
      return 8;
    case 6:  // __NR_lsetxattr
      return 189;
    case 233:  // __NR_madvise
      return 28;
    case 235:  // __NR_mbind
      return 237;
    case 283:  // __NR_membarrier
      return 324;
    case 279:  // __NR_memfd_create
      return 319;
    case 447:  // __NR_memfd_secret
      return 447;
    case 238:  // __NR_migrate_pages
      return 256;
    case 232:  // __NR_mincore
      return 27;
    case 34:  // __NR_mkdirat
      return 258;
    case 33:  // __NR_mknodat
      return 259;
    case 228:  // __NR_mlock
      return 149;
    case 284:  // __NR_mlock2
      return 325;
    case 230:  // __NR_mlockall
      return 151;
    case 222:  // __NR_mmap
      return 9;
    case 40:  // __NR_mount
      return 165;
    case 442:  // __NR_mount_setattr
      return 442;
    case 429:  // __NR_move_mount
      return 429;
    case 239:  // __NR_move_pages
      return 279;
    case 226:  // __NR_mprotect
      return 10;
    case 185:  // __NR_mq_getsetattr
      return 245;
    case 184:  // __NR_mq_notify
      return 244;
    case 180:  // __NR_mq_open
      return 240;
    case 183:  // __NR_mq_timedreceive
      return 243;
    case 182:  // __NR_mq_timedsend
      return 242;
    case 181:  // __NR_mq_unlink
      return 241;
    case 216:  // __NR_mremap
      return 25;
    case 187:  // __NR_msgctl
      return 71;
    case 186:  // __NR_msgget
      return 68;
    case 188:  // __NR_msgrcv
      return 70;
    case 189:  // __NR_msgsnd
      return 69;
    case 227:  // __NR_msync
      return 26;
    case 229:  // __NR_munlock
      return 150;
    case 231:  // __NR_munlockall
      return 152;
    case 215:  // __NR_munmap
      return 11;
    case 264:  // __NR_name_to_handle_at
      return 303;
    case 101:  // __NR_nanosleep
      return 35;
    case 79:  // __NR_newfstatat
      return 262;
    case 42:  // __NR_nfsservctl
      return 180;
    case 265:  // __NR_open_by_handle_at
      return 304;
    case 428:  // __NR_open_tree
      return 428;
    case 56:  // __NR_openat
      return 257;
    case 437:  // __NR_openat2
      return 437;
    case 241:  // __NR_perf_event_open
      return 298;
    case 92:  // __NR_personality
      return 135;
    case 438:  // __NR_pidfd_getfd
      return 438;
    case 434:  // __NR_pidfd_open
      return 434;
    case 424:  // __NR_pidfd_send_signal
      return 424;
    case 59:  // __NR_pipe2
      return 293;
    case 41:  // __NR_pivot_root
      return 155;
    case 289:  // __NR_pkey_alloc
      return 330;
    case 290:  // __NR_pkey_free
      return 331;
    case 288:  // __NR_pkey_mprotect
      return 329;
    case 73:  // __NR_ppoll
      return 271;
    case 167:  // __NR_prctl
      return 157;
    case 67:  // __NR_pread64
      return 17;
    case 69:  // __NR_preadv
      return 295;
    case 286:  // __NR_preadv2
      return 327;
    case 261:  // __NR_prlimit64
      return 302;
    case 440:  // __NR_process_madvise
      return 440;
    case 448:  // __NR_process_mrelease
      return 448;
    case 270:  // __NR_process_vm_readv
      return 310;
    case 271:  // __NR_process_vm_writev
      return 311;
    case 72:  // __NR_pselect6
      return 270;
    case 117:  // __NR_ptrace
      return 101;
    case 68:  // __NR_pwrite64
      return 18;
    case 70:  // __NR_pwritev
      return 296;
    case 287:  // __NR_pwritev2
      return 328;
    case 60:  // __NR_quotactl
      return 179;
    case 443:  // __NR_quotactl_fd
      return 443;
    case 63:  // __NR_read
      return 0;
    case 213:  // __NR_readahead
      return 187;
    case 78:  // __NR_readlinkat
      return 267;
    case 65:  // __NR_readv
      return 19;
    case 142:  // __NR_reboot
      return 169;
    case 207:  // __NR_recvfrom
      return 45;
    case 243:  // __NR_recvmmsg
      return 299;
    case 212:  // __NR_recvmsg
      return 47;
    case 234:  // __NR_remap_file_pages
      return 216;
    case 14:  // __NR_removexattr
      return 197;
    case 38:  // __NR_renameat
      return 264;
    case 276:  // __NR_renameat2
      return 316;
    case 218:  // __NR_request_key
      return 249;
    case 128:  // __NR_restart_syscall
      return 219;
    case 293:  // __NR_rseq
      return 334;
    case 134:  // __NR_rt_sigaction
      return 13;
    case 136:  // __NR_rt_sigpending
      return 127;
    case 135:  // __NR_rt_sigprocmask
      return 14;
    case 138:  // __NR_rt_sigqueueinfo
      return 129;
    case 139:  // __NR_rt_sigreturn
      return 15;
    case 133:  // __NR_rt_sigsuspend
      return 130;
    case 137:  // __NR_rt_sigtimedwait
      return 128;
    case 240:  // __NR_rt_tgsigqueueinfo
      return 297;
    case 125:  // __NR_sched_get_priority_max
      return 146;
    case 126:  // __NR_sched_get_priority_min
      return 147;
    case 123:  // __NR_sched_getaffinity
      return 204;
    case 275:  // __NR_sched_getattr
      return 315;
    case 121:  // __NR_sched_getparam
      return 143;
    case 120:  // __NR_sched_getscheduler
      return 145;
    case 127:  // __NR_sched_rr_get_interval
      return 148;
    case 122:  // __NR_sched_setaffinity
      return 203;
    case 274:  // __NR_sched_setattr
      return 314;
    case 118:  // __NR_sched_setparam
      return 142;
    case 119:  // __NR_sched_setscheduler
      return 144;
    case 124:  // __NR_sched_yield
      return 24;
    case 277:  // __NR_seccomp
      return 317;
    case 191:  // __NR_semctl
      return 66;
    case 190:  // __NR_semget
      return 64;
    case 193:  // __NR_semop
      return 65;
    case 192:  // __NR_semtimedop
      return 220;
    case 71:  // __NR_sendfile
      return 40;
    case 269:  // __NR_sendmmsg
      return 307;
    case 211:  // __NR_sendmsg
      return 46;
    case 206:  // __NR_sendto
      return 44;
    case 237:  // __NR_set_mempolicy
      return 238;
    case 450:  // __NR_set_mempolicy_home_node
      return 450;
    case 99:  // __NR_set_robust_list
      return 273;
    case 96:  // __NR_set_tid_address
      return 218;
    case 162:  // __NR_setdomainname
      return 171;
    case 152:  // __NR_setfsgid
      return 123;
    case 151:  // __NR_setfsuid
      return 122;
    case 144:  // __NR_setgid
      return 106;
    case 159:  // __NR_setgroups
      return 116;
    case 161:  // __NR_sethostname
      return 170;
    case 103:  // __NR_setitimer
      return 38;
    case 268:  // __NR_setns
      return 308;
    case 154:  // __NR_setpgid
      return 109;
    case 140:  // __NR_setpriority
      return 141;
    case 143:  // __NR_setregid
      return 114;
    case 149:  // __NR_setresgid
      return 119;
    case 147:  // __NR_setresuid
      return 117;
    case 145:  // __NR_setreuid
      return 113;
    case 164:  // __NR_setrlimit
      return 160;
    case 157:  // __NR_setsid
      return 112;
    case 208:  // __NR_setsockopt
      return 54;
    case 170:  // __NR_settimeofday
      return 164;
    case 146:  // __NR_setuid
      return 105;
    case 5:  // __NR_setxattr
      return 188;
    case 196:  // __NR_shmat
      return 30;
    case 195:  // __NR_shmctl
      return 31;
    case 197:  // __NR_shmdt
      return 67;
    case 194:  // __NR_shmget
      return 29;
    case 210:  // __NR_shutdown
      return 48;
    case 132:  // __NR_sigaltstack
      return 131;
    case 74:  // __NR_signalfd4
      return 289;
    case 198:  // __NR_socket
      return 41;
    case 199:  // __NR_socketpair
      return 53;
    case 76:  // __NR_splice
      return 275;
    case 43:  // __NR_statfs
      return 137;
    case 291:  // __NR_statx
      return 332;
    case 225:  // __NR_swapoff
      return 168;
    case 224:  // __NR_swapon
      return 167;
    case 36:  // __NR_symlinkat
      return 266;
    case 81:  // __NR_sync
      return 162;
    case 84:  // __NR_sync_file_range
      return 277;
    case 267:  // __NR_syncfs
      return 306;
    case 179:  // __NR_sysinfo
      return 99;
    case 116:  // __NR_syslog
      return 103;
    case 77:  // __NR_tee
      return 276;
    case 131:  // __NR_tgkill
      return 234;
    case 107:  // __NR_timer_create
      return 222;
    case 111:  // __NR_timer_delete
      return 226;
    case 109:  // __NR_timer_getoverrun
      return 225;
    case 108:  // __NR_timer_gettime
      return 224;
    case 110:  // __NR_timer_settime
      return 223;
    case 85:  // __NR_timerfd_create
      return 283;
    case 87:  // __NR_timerfd_gettime
      return 287;
    case 86:  // __NR_timerfd_settime
      return 286;
    case 153:  // __NR_times
      return 100;
    case 130:  // __NR_tkill
      return 200;
    case 45:  // __NR_truncate
      return 76;
    case 166:  // __NR_umask
      return 95;
    case 39:  // __NR_umount2
      return 166;
    case 160:  // __NR_uname
      return 63;
    case 35:  // __NR_unlinkat
      return 263;
    case 97:  // __NR_unshare
      return 272;
    case 282:  // __NR_userfaultfd
      return 323;
    case 88:  // __NR_utimensat
      return 280;
    case 58:  // __NR_vhangup
      return 153;
    case 75:  // __NR_vmsplice
      return 278;
    case 260:  // __NR_wait4
      return 61;
    case 95:  // __NR_waitid
      return 247;
    case 64:  // __NR_write
      return 1;
    case 66:  // __NR_writev
      return 20;
    default:
      return -1;
  }
}

inline int ToGuestSyscallNumber(int nr) {
  switch (nr) {
    case 156:  // __NR__sysctl - missing on riscv64
      return -1;
    case 43:  // __NR_accept
      return 202;
    case 288:  // __NR_accept4
      return 242;
    case 21:  // __NR_access - missing on riscv64
      return -1;
    case 163:  // __NR_acct
      return 89;
    case 248:  // __NR_add_key
      return 217;
    case 159:  // __NR_adjtimex
      return 171;
    case 183:  // __NR_afs_syscall - missing on riscv64
      return -1;
    case 37:  // __NR_alarm - missing on riscv64
      return -1;
    case 158:  // __NR_arch_prctl - missing on riscv64
      return -1;
    case 49:  // __NR_bind
      return 200;
    case 321:  // __NR_bpf
      return 280;
    case 12:  // __NR_brk
      return 214;
    case 125:  // __NR_capget
      return 90;
    case 126:  // __NR_capset
      return 91;
    case 80:  // __NR_chdir
      return 49;
    case 90:  // __NR_chmod - missing on riscv64
      return -1;
    case 92:  // __NR_chown - missing on riscv64
      return -1;
    case 161:  // __NR_chroot
      return 51;
    case 305:  // __NR_clock_adjtime
      return 266;
    case 229:  // __NR_clock_getres
      return 114;
    case 228:  // __NR_clock_gettime
      return 113;
    case 230:  // __NR_clock_nanosleep
      return 115;
    case 227:  // __NR_clock_settime
      return 112;
    case 56:  // __NR_clone
      return 220;
    case 435:  // __NR_clone3
      return 435;
    case 3:  // __NR_close
      return 57;
    case 436:  // __NR_close_range
      return 436;
    case 42:  // __NR_connect
      return 203;
    case 326:  // __NR_copy_file_range
      return 285;
    case 85:  // __NR_creat - missing on riscv64
      return -1;
    case 174:  // __NR_create_module - missing on riscv64
      return -1;
    case 176:  // __NR_delete_module
      return 106;
    case 32:  // __NR_dup
      return 23;
    case 33:  // __NR_dup2 - missing on riscv64
      return -1;
    case 292:  // __NR_dup3
      return 24;
    case 213:  // __NR_epoll_create - missing on riscv64
      return -1;
    case 291:  // __NR_epoll_create1
      return 20;
    case 233:  // __NR_epoll_ctl
      return 21;
    case 214:  // __NR_epoll_ctl_old - missing on riscv64
      return -1;
    case 281:  // __NR_epoll_pwait
      return 22;
    case 441:  // __NR_epoll_pwait2
      return 441;
    case 232:  // __NR_epoll_wait - missing on riscv64
      return -1;
    case 215:  // __NR_epoll_wait_old - missing on riscv64
      return -1;
    case 284:  // __NR_eventfd - missing on riscv64
      return -1;
    case 290:  // __NR_eventfd2
      return 19;
    case 59:  // __NR_execve
      return 221;
    case 322:  // __NR_execveat
      return 281;
    case 60:  // __NR_exit
      return 93;
    case 231:  // __NR_exit_group
      return 94;
    case 269:  // __NR_faccessat
      return 48;
    case 439:  // __NR_faccessat2
      return 439;
    case 221:  // __NR_fadvise64
      return 223;
    case 285:  // __NR_fallocate
      return 47;
    case 300:  // __NR_fanotify_init
      return 262;
    case 301:  // __NR_fanotify_mark
      return 263;
    case 81:  // __NR_fchdir
      return 50;
    case 91:  // __NR_fchmod
      return 52;
    case 268:  // __NR_fchmodat
      return 53;
    case 93:  // __NR_fchown
      return 55;
    case 260:  // __NR_fchownat
      return 54;
    case 72:  // __NR_fcntl
      return 25;
    case 75:  // __NR_fdatasync
      return 83;
    case 193:  // __NR_fgetxattr
      return 10;
    case 313:  // __NR_finit_module
      return 273;
    case 196:  // __NR_flistxattr
      return 13;
    case 73:  // __NR_flock
      return 32;
    case 57:  // __NR_fork - missing on riscv64
      return -1;
    case 199:  // __NR_fremovexattr
      return 16;
    case 431:  // __NR_fsconfig
      return 431;
    case 190:  // __NR_fsetxattr
      return 7;
    case 432:  // __NR_fsmount
      return 432;
    case 430:  // __NR_fsopen
      return 430;
    case 433:  // __NR_fspick
      return 433;
    case 5:  // __NR_fstat
      return 80;
    case 138:  // __NR_fstatfs
      return 44;
    case 74:  // __NR_fsync
      return 82;
    case 77:  // __NR_ftruncate
      return 46;
    case 202:  // __NR_futex
      return 98;
    case 449:  // __NR_futex_waitv
      return 449;
    case 261:  // __NR_futimesat - missing on riscv64
      return -1;
    case 177:  // __NR_get_kernel_syms - missing on riscv64
      return -1;
    case 239:  // __NR_get_mempolicy
      return 236;
    case 274:  // __NR_get_robust_list
      return 100;
    case 211:  // __NR_get_thread_area - missing on riscv64
      return -1;
    case 309:  // __NR_getcpu
      return 168;
    case 79:  // __NR_getcwd
      return 17;
    case 78:  // __NR_getdents - missing on riscv64
      return -1;
    case 217:  // __NR_getdents64
      return 61;
    case 108:  // __NR_getegid
      return 177;
    case 107:  // __NR_geteuid
      return 175;
    case 104:  // __NR_getgid
      return 176;
    case 115:  // __NR_getgroups
      return 158;
    case 36:  // __NR_getitimer
      return 102;
    case 52:  // __NR_getpeername
      return 205;
    case 121:  // __NR_getpgid
      return 155;
    case 111:  // __NR_getpgrp - missing on riscv64
      return -1;
    case 39:  // __NR_getpid
      return 172;
    case 181:  // __NR_getpmsg - missing on riscv64
      return -1;
    case 110:  // __NR_getppid
      return 173;
    case 140:  // __NR_getpriority
      return 141;
    case 318:  // __NR_getrandom
      return 278;
    case 120:  // __NR_getresgid
      return 150;
    case 118:  // __NR_getresuid
      return 148;
    case 97:  // __NR_getrlimit
      return 163;
    case 98:  // __NR_getrusage
      return 165;
    case 124:  // __NR_getsid
      return 156;
    case 51:  // __NR_getsockname
      return 204;
    case 55:  // __NR_getsockopt
      return 209;
    case 186:  // __NR_gettid
      return 178;
    case 96:  // __NR_gettimeofday
      return 169;
    case 102:  // __NR_getuid
      return 174;
    case 191:  // __NR_getxattr
      return 8;
    case 175:  // __NR_init_module
      return 105;
    case 254:  // __NR_inotify_add_watch
      return 27;
    case 253:  // __NR_inotify_init - missing on riscv64
      return -1;
    case 294:  // __NR_inotify_init1
      return 26;
    case 255:  // __NR_inotify_rm_watch
      return 28;
    case 210:  // __NR_io_cancel
      return 3;
    case 207:  // __NR_io_destroy
      return 1;
    case 208:  // __NR_io_getevents
      return 4;
    case 333:  // __NR_io_pgetevents
      return 292;
    case 206:  // __NR_io_setup
      return 0;
    case 209:  // __NR_io_submit
      return 2;
    case 426:  // __NR_io_uring_enter
      return 426;
    case 427:  // __NR_io_uring_register
      return 427;
    case 425:  // __NR_io_uring_setup
      return 425;
    case 16:  // __NR_ioctl
      return 29;
    case 173:  // __NR_ioperm - missing on riscv64
      return -1;
    case 172:  // __NR_iopl - missing on riscv64
      return -1;
    case 252:  // __NR_ioprio_get
      return 31;
    case 251:  // __NR_ioprio_set
      return 30;
    case 312:  // __NR_kcmp
      return 272;
    case 320:  // __NR_kexec_file_load
      return 294;
    case 246:  // __NR_kexec_load
      return 104;
    case 250:  // __NR_keyctl
      return 219;
    case 62:  // __NR_kill
      return 129;
    case 445:  // __NR_landlock_add_rule
      return 445;
    case 444:  // __NR_landlock_create_ruleset
      return 444;
    case 446:  // __NR_landlock_restrict_self
      return 446;
    case 94:  // __NR_lchown - missing on riscv64
      return -1;
    case 192:  // __NR_lgetxattr
      return 9;
    case 86:  // __NR_link - missing on riscv64
      return -1;
    case 265:  // __NR_linkat
      return 37;
    case 50:  // __NR_listen
      return 201;
    case 194:  // __NR_listxattr
      return 11;
    case 195:  // __NR_llistxattr
      return 12;
    case 212:  // __NR_lookup_dcookie
      return 18;
    case 198:  // __NR_lremovexattr
      return 15;
    case 8:  // __NR_lseek
      return 62;
    case 189:  // __NR_lsetxattr
      return 6;
    case 6:  // __NR_lstat - missing on riscv64
      return -1;
    case 28:  // __NR_madvise
      return 233;
    case 237:  // __NR_mbind
      return 235;
    case 324:  // __NR_membarrier
      return 283;
    case 319:  // __NR_memfd_create
      return 279;
    case 447:  // __NR_memfd_secret
      return 447;
    case 256:  // __NR_migrate_pages
      return 238;
    case 27:  // __NR_mincore
      return 232;
    case 83:  // __NR_mkdir - missing on riscv64
      return -1;
    case 258:  // __NR_mkdirat
      return 34;
    case 133:  // __NR_mknod - missing on riscv64
      return -1;
    case 259:  // __NR_mknodat
      return 33;
    case 149:  // __NR_mlock
      return 228;
    case 325:  // __NR_mlock2
      return 284;
    case 151:  // __NR_mlockall
      return 230;
    case 9:  // __NR_mmap
      return 222;
    case 154:  // __NR_modify_ldt - missing on riscv64
      return -1;
    case 165:  // __NR_mount
      return 40;
    case 442:  // __NR_mount_setattr
      return 442;
    case 429:  // __NR_move_mount
      return 429;
    case 279:  // __NR_move_pages
      return 239;
    case 10:  // __NR_mprotect
      return 226;
    case 245:  // __NR_mq_getsetattr
      return 185;
    case 244:  // __NR_mq_notify
      return 184;
    case 240:  // __NR_mq_open
      return 180;
    case 243:  // __NR_mq_timedreceive
      return 183;
    case 242:  // __NR_mq_timedsend
      return 182;
    case 241:  // __NR_mq_unlink
      return 181;
    case 25:  // __NR_mremap
      return 216;
    case 71:  // __NR_msgctl
      return 187;
    case 68:  // __NR_msgget
      return 186;
    case 70:  // __NR_msgrcv
      return 188;
    case 69:  // __NR_msgsnd
      return 189;
    case 26:  // __NR_msync
      return 227;
    case 150:  // __NR_munlock
      return 229;
    case 152:  // __NR_munlockall
      return 231;
    case 11:  // __NR_munmap
      return 215;
    case 303:  // __NR_name_to_handle_at
      return 264;
    case 35:  // __NR_nanosleep
      return 101;
    case 262:  // __NR_newfstatat
      return 79;
    case 180:  // __NR_nfsservctl
      return 42;
    case 2:  // __NR_open - missing on riscv64
      return -1;
    case 304:  // __NR_open_by_handle_at
      return 265;
    case 428:  // __NR_open_tree
      return 428;
    case 257:  // __NR_openat
      return 56;
    case 437:  // __NR_openat2
      return 437;
    case 34:  // __NR_pause - missing on riscv64
      return -1;
    case 298:  // __NR_perf_event_open
      return 241;
    case 135:  // __NR_personality
      return 92;
    case 438:  // __NR_pidfd_getfd
      return 438;
    case 434:  // __NR_pidfd_open
      return 434;
    case 424:  // __NR_pidfd_send_signal
      return 424;
    case 22:  // __NR_pipe - missing on riscv64
      return -1;
    case 293:  // __NR_pipe2
      return 59;
    case 155:  // __NR_pivot_root
      return 41;
    case 330:  // __NR_pkey_alloc
      return 289;
    case 331:  // __NR_pkey_free
      return 290;
    case 329:  // __NR_pkey_mprotect
      return 288;
    case 7:  // __NR_poll - missing on riscv64
      return -1;
    case 271:  // __NR_ppoll
      return 73;
    case 157:  // __NR_prctl
      return 167;
    case 17:  // __NR_pread64
      return 67;
    case 295:  // __NR_preadv
      return 69;
    case 327:  // __NR_preadv2
      return 286;
    case 302:  // __NR_prlimit64
      return 261;
    case 440:  // __NR_process_madvise
      return 440;
    case 448:  // __NR_process_mrelease
      return 448;
    case 310:  // __NR_process_vm_readv
      return 270;
    case 311:  // __NR_process_vm_writev
      return 271;
    case 270:  // __NR_pselect6
      return 72;
    case 101:  // __NR_ptrace
      return 117;
    case 182:  // __NR_putpmsg - missing on riscv64
      return -1;
    case 18:  // __NR_pwrite64
      return 68;
    case 296:  // __NR_pwritev
      return 70;
    case 328:  // __NR_pwritev2
      return 287;
    case 178:  // __NR_query_module - missing on riscv64
      return -1;
    case 179:  // __NR_quotactl
      return 60;
    case 443:  // __NR_quotactl_fd
      return 443;
    case 0:  // __NR_read
      return 63;
    case 187:  // __NR_readahead
      return 213;
    case 89:  // __NR_readlink - missing on riscv64
      return -1;
    case 267:  // __NR_readlinkat
      return 78;
    case 19:  // __NR_readv
      return 65;
    case 169:  // __NR_reboot
      return 142;
    case 45:  // __NR_recvfrom
      return 207;
    case 299:  // __NR_recvmmsg
      return 243;
    case 47:  // __NR_recvmsg
      return 212;
    case 216:  // __NR_remap_file_pages
      return 234;
    case 197:  // __NR_removexattr
      return 14;
    case 82:  // __NR_rename - missing on riscv64
      return -1;
    case 264:  // __NR_renameat
      return 38;
    case 316:  // __NR_renameat2
      return 276;
    case 249:  // __NR_request_key
      return 218;
    case 219:  // __NR_restart_syscall
      return 128;
    case 84:  // __NR_rmdir - missing on riscv64
      return -1;
    case 334:  // __NR_rseq
      return 293;
    case 13:  // __NR_rt_sigaction
      return 134;
    case 127:  // __NR_rt_sigpending
      return 136;
    case 14:  // __NR_rt_sigprocmask
      return 135;
    case 129:  // __NR_rt_sigqueueinfo
      return 138;
    case 15:  // __NR_rt_sigreturn
      return 139;
    case 130:  // __NR_rt_sigsuspend
      return 133;
    case 128:  // __NR_rt_sigtimedwait
      return 137;
    case 297:  // __NR_rt_tgsigqueueinfo
      return 240;
    case 146:  // __NR_sched_get_priority_max
      return 125;
    case 147:  // __NR_sched_get_priority_min
      return 126;
    case 204:  // __NR_sched_getaffinity
      return 123;
    case 315:  // __NR_sched_getattr
      return 275;
    case 143:  // __NR_sched_getparam
      return 121;
    case 145:  // __NR_sched_getscheduler
      return 120;
    case 148:  // __NR_sched_rr_get_interval
      return 127;
    case 203:  // __NR_sched_setaffinity
      return 122;
    case 314:  // __NR_sched_setattr
      return 274;
    case 142:  // __NR_sched_setparam
      return 118;
    case 144:  // __NR_sched_setscheduler
      return 119;
    case 24:  // __NR_sched_yield
      return 124;
    case 317:  // __NR_seccomp
      return 277;
    case 185:  // __NR_security - missing on riscv64
      return -1;
    case 23:  // __NR_select - missing on riscv64
      return -1;
    case 66:  // __NR_semctl
      return 191;
    case 64:  // __NR_semget
      return 190;
    case 65:  // __NR_semop
      return 193;
    case 220:  // __NR_semtimedop
      return 192;
    case 40:  // __NR_sendfile
      return 71;
    case 307:  // __NR_sendmmsg
      return 269;
    case 46:  // __NR_sendmsg
      return 211;
    case 44:  // __NR_sendto
      return 206;
    case 238:  // __NR_set_mempolicy
      return 237;
    case 450:  // __NR_set_mempolicy_home_node
      return 450;
    case 273:  // __NR_set_robust_list
      return 99;
    case 205:  // __NR_set_thread_area - missing on riscv64
      return -1;
    case 218:  // __NR_set_tid_address
      return 96;
    case 171:  // __NR_setdomainname
      return 162;
    case 123:  // __NR_setfsgid
      return 152;
    case 122:  // __NR_setfsuid
      return 151;
    case 106:  // __NR_setgid
      return 144;
    case 116:  // __NR_setgroups
      return 159;
    case 170:  // __NR_sethostname
      return 161;
    case 38:  // __NR_setitimer
      return 103;
    case 308:  // __NR_setns
      return 268;
    case 109:  // __NR_setpgid
      return 154;
    case 141:  // __NR_setpriority
      return 140;
    case 114:  // __NR_setregid
      return 143;
    case 119:  // __NR_setresgid
      return 149;
    case 117:  // __NR_setresuid
      return 147;
    case 113:  // __NR_setreuid
      return 145;
    case 160:  // __NR_setrlimit
      return 164;
    case 112:  // __NR_setsid
      return 157;
    case 54:  // __NR_setsockopt
      return 208;
    case 164:  // __NR_settimeofday
      return 170;
    case 105:  // __NR_setuid
      return 146;
    case 188:  // __NR_setxattr
      return 5;
    case 30:  // __NR_shmat
      return 196;
    case 31:  // __NR_shmctl
      return 195;
    case 67:  // __NR_shmdt
      return 197;
    case 29:  // __NR_shmget
      return 194;
    case 48:  // __NR_shutdown
      return 210;
    case 131:  // __NR_sigaltstack
      return 132;
    case 282:  // __NR_signalfd - missing on riscv64
      return -1;
    case 289:  // __NR_signalfd4
      return 74;
    case 41:  // __NR_socket
      return 198;
    case 53:  // __NR_socketpair
      return 199;
    case 275:  // __NR_splice
      return 76;
    case 4:  // __NR_stat - missing on riscv64
      return -1;
    case 137:  // __NR_statfs
      return 43;
    case 332:  // __NR_statx
      return 291;
    case 168:  // __NR_swapoff
      return 225;
    case 167:  // __NR_swapon
      return 224;
    case 88:  // __NR_symlink - missing on riscv64
      return -1;
    case 266:  // __NR_symlinkat
      return 36;
    case 162:  // __NR_sync
      return 81;
    case 277:  // __NR_sync_file_range
      return 84;
    case 306:  // __NR_syncfs
      return 267;
    case 139:  // __NR_sysfs - missing on riscv64
      return -1;
    case 99:  // __NR_sysinfo
      return 179;
    case 103:  // __NR_syslog
      return 116;
    case 276:  // __NR_tee
      return 77;
    case 234:  // __NR_tgkill
      return 131;
    case 201:  // __NR_time - missing on riscv64
      return -1;
    case 222:  // __NR_timer_create
      return 107;
    case 226:  // __NR_timer_delete
      return 111;
    case 225:  // __NR_timer_getoverrun
      return 109;
    case 224:  // __NR_timer_gettime
      return 108;
    case 223:  // __NR_timer_settime
      return 110;
    case 283:  // __NR_timerfd_create
      return 85;
    case 287:  // __NR_timerfd_gettime
      return 87;
    case 286:  // __NR_timerfd_settime
      return 86;
    case 100:  // __NR_times
      return 153;
    case 200:  // __NR_tkill
      return 130;
    case 76:  // __NR_truncate
      return 45;
    case 184:  // __NR_tuxcall - missing on riscv64
      return -1;
    case 95:  // __NR_umask
      return 166;
    case 166:  // __NR_umount2
      return 39;
    case 63:  // __NR_uname
      return 160;
    case 87:  // __NR_unlink - missing on riscv64
      return -1;
    case 263:  // __NR_unlinkat
      return 35;
    case 272:  // __NR_unshare
      return 97;
    case 134:  // __NR_uselib - missing on riscv64
      return -1;
    case 323:  // __NR_userfaultfd
      return 282;
    case 136:  // __NR_ustat - missing on riscv64
      return -1;
    case 132:  // __NR_utime - missing on riscv64
      return -1;
    case 280:  // __NR_utimensat
      return 88;
    case 235:  // __NR_utimes - missing on riscv64
      return -1;
    case 58:  // __NR_vfork - missing on riscv64
      return -1;
    case 153:  // __NR_vhangup
      return 58;
    case 278:  // __NR_vmsplice
      return 75;
    case 236:  // __NR_vserver - missing on riscv64
      return -1;
    case 61:  // __NR_wait4
      return 260;
    case 247:  // __NR_waitid
      return 95;
    case 1:  // __NR_write
      return 64;
    case 20:  // __NR_writev
      return 66;
    default:
      return -1;
  }
}

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GEN_SYSCALL_NUMBERS_RISCV64_H_