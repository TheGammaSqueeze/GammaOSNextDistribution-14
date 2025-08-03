This directory contains comment stripped versions of
  //system/bpf/bpfloader/bpfloader.rc
from previous versions of Android.

Generated via:
  (cd ../../../../../system/bpf && git cat-file -p remotes/aosp/android11-release:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk30-11-R.rc
  (cd ../../../../../system/bpf && git cat-file -p remotes/aosp/android12-release:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk31-12-S.rc
  (cd ../../../../../system/bpf && git cat-file -p remotes/aosp/android13-release:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk33-13-T.rc
  (cd ../../../../../system/bpf && git cat-file -p remotes/aosp/android14-release:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk34-14-U.rc
  (cd ../../../../../system/bpf && git cat-file -p remotes/aosp/main:bpfloader/bpfloader.rc;              ) | egrep -v '^ *#' > bpfloader-sdk34-14-U-QPR2.rc

this is entirely equivalent to:
  (cd /android1/system/bpf && git cat-file -p remotes/goog/rvc-dev:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk30-11-R.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/sc-dev:bpfloader/bpfloader.rc;  ) | egrep -v '^ *#' > bpfloader-sdk31-12-S.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/tm-dev:bpfloader/bpfloader.rc;  ) | egrep -v '^ *#' > bpfloader-sdk33-13-T.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/udc-dev:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk34-14-U.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/main:bpfloader/bpfloader.rc;    ) | egrep -v '^ *#' > bpfloader-sdk34-14-U-QPR2.rc

it is also equivalent to:
  (cd /android1/system/bpf && git cat-file -p remotes/goog/rvc-qpr-dev:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk30-11-R.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/sc-v2-dev:bpfloader/bpfloader.rc;   ) | egrep -v '^ *#' > bpfloader-sdk31-12-S.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/tm-qpr-dev:bpfloader/bpfloader.rc;  ) | egrep -v '^ *#' > bpfloader-sdk33-13-T.rc
  (cd /android1/system/bpf && git cat-file -p remotes/goog/udc-qpr-dev:bpfloader/bpfloader.rc; ) | egrep -v '^ *#' > bpfloader-sdk34-14-U.rc

ie. there were no changes between R/S/T and R/S/T QPR3, and no change between U and U QPR1.

Note: Sv2 sdk/api level is actually 32, it just didn't change anything wrt. bpf, so doesn't matter.


Key takeaways:

= R bpfloader:
  - CHOWN + SYS_ADMIN
  - asynchronous startup
  - platform only
  - proc file setup handled by initrc

= S bpfloader
  - adds NET_ADMIN
  - synchronous startup
  - platform + mainline tethering offload

= T bpfloader
  - platform + mainline networking (including tethering offload)
  - supported btf for maps via exec of btfloader

= U bpfloader
  - proc file setup moved into bpfloader binary
  - explicitly specified user and groups:
    group root graphics network_stack net_admin net_bw_acct net_bw_stats net_raw system
    user root

= U QPR2 bpfloader
  - drops support of btf for maps
  - invocation of /system/bin/netbpfload binary, which after handling *all*
    networking bpf related things executes the platform /system/bin/bpfloader
    which handles non-networking bpf.

Note that there is now a copy of 'netbpfload' provided by the tethering apex
mainline module at /apex/com.android.tethering/bin/netbpfload, which due
to the use of execve("/system/bin/bpfloader") relies on T+ selinux which was
added for btf map support (specifically the ability to exec the "btfloader").
