# DS system test ROM

A bare-metal DS program (no devkitPro, only `arm-none-eabi-gcc`) that shows and publishes
what a game sees of drastic-nano's System settings:

- the firmware user settings copy at `0x027FFC80` (nickname as UTF-16, birthday, favourite
  colour, language), which is what games read;
- the RTC, read by the ARM7 through the serial RTC port the way libnds does;
- the Slot-2 bus as the ARM9 sees it (GBA ROM space, SRAM space).

Build: `./build.sh out.nds [any-ds-rom.nds]` (the optional ROM only lends its header logo).

The results are also written to DS main RAM, `0x02300000` (ARM9 block) and `0x02300200`
(ARM7 block), laid out in `systest.h`, so a harness can read them from drastic-nano's memory:
drastic's ARM9 RAM pointer is `*(hm + 0x35d9930)` with `hm = *(libdrastic + 0x14c000)`.
