# FAFT Links

_Self-link: [go/faft-links]_

**FAFT**, short for "Fully Automated Firmware Tests", refers to the automated
firmware end-to-end tests written for ChromiumOS.

FAFT tests were originally written for the remote test driver [Tauto]. There is
a 2021 initiative to convert FAFT tests to another remote driver, [Tast]. That
initiative is called [FAFT2Tast]. All new tests should be written in [Tast].

[Tauto]: https://chromium.googlesource.com/chromiumos/third_party/autotest/
[Tast]: https://chromium.googlesource.com/chromiumos/platform/tast/
[FAFT2Tast]: https://goto.google.com/faft2tast-overview

## FAFT related links

*Note:* Go links requires access to the Google interanet.

Link name           | Go link              | Source location
------------------- | -------------------- | ---------------
FAFT Links          | [go/faft-links]      | [docs/faft-links.md]
FAFT Running manual | [go/faft-running]    | [docs/faft-how-to-run-doc.md]
FAFT PD             | [go/faft-pd]         | [docs/faft-pd.md]
FAFT for bringup    | [go/faft-bringup]    | tast-tests/src/chromiumos/tast/remote/firmware/bringup.md
Tast FAFT Codelab   | [go/tast-faft-codelab] | tast-tests/src/chromiumos/tast/remote/firmware/codelab/README.md
FAFT Code overview (deprecated)  | [go/faft-code]       | [docs/faft-code.md]
FAFT Design Doc    | [go/faft-design-doc] | [docs/faft-design-doc.md]

[go/faft-links]: https://goto.google.com/faft-links
[docs/faft-links.md]: faft-links.md

[go/faft-design-doc]: https://goto.google.com/faft-design-doc
[docs/faft-design-doc.md]: faft-design-doc.md

[go/faft-pd]: https://goto.google.com/faft-pd
[docs/faft-pd.md]: faft-pd.md

[go/faft-running]: https://goto.google.com/faft-running
[docs/faft-how-to-run-doc.md]: faft-how-to-run-doc.md

[go/faft-code]: https://goto.google.com/faft-code
[docs/faft-code.md]: faft-code.md

[go/tast-faft-codelab]: https://chromium.googlesource.com/chromiumos/platform/tast-tests/+/HEAD/src/chromiumos/tast/remote/firmware/codelab/README.md
[go/faft-bringup]: https://chromium.googlesource.com/chromiumos/platform/tast-tests/+/HEAD/src/chromiumos/tast/remote/firmware/bringup.md

## FAFT Users Chat

There is a Google Chat room for FAFT users.

* Go-link: [go/faft-users-chat]
* External-facing link: https://chat.google.com/room/AAAAsHQFTo8

If you are unable to access the chatroom via these links, please get in touch
with the ChromeOS Firmware Engprod team. This will definitely happen if you
don't have an @google.com email address.

[go/faft-users-chat]: https://goto.google.com/faft-users-chat
