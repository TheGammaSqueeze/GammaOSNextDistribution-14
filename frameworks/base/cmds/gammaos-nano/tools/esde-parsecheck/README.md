# esde-parsecheck

A host-side robustness check for the ES-DE theme parser.

The ES-DE engine's parser (`NanoEsdeTheme.cpp`, `nanoesde::Theme`) is GL-free, so
it can be compiled and exercised on a workstation without a device or the Soong
build. This tool links it against `external/tinyxml2` and loads every theme in a
directory across its **full declared capability matrix** (every `variant` x
`colorScheme` x `aspectRatio`, plus every `fontSize`), asserting that each load
returns valid. It catches parse regressions - a dropped element, a broken
`<include>`/`${variable}` resolution, an aspect-specific layout that fails to
merge - across the whole permutation space that a device A/B pass can only spot
one screen at a time.

## Run

```sh
./run.sh [themes_dir]        # default: /work/esde_ref_themes
```

It prints one line per theme with the combo count and `ok` or the first failure,
then a summary. Exit code is non-zero if any combination is invalid. `log/log.h`
is a tiny stub for Android's logging macros so the parser links on the host.

## Example

```
art-book-next-es-de            20 variants x schemes x aspects x fonts = 8064 combos  ok
...
=== 15 themes, 23613 combos, 0 invalid (0 themes with failures) ===
```

`fontSize` is a render scalar and does not change include resolution, so it is
covered once per theme with defaults rather than multiplied through the whole
cross-product (keeping the run to seconds while still exercising every declared
value).
