# rust-analyzer-chromiumos-wrapper

## Intro

rust-analyzer is an LSP server for the Rust language. It allows editors like
vim, emacs, or VS Code to provide IDE-like features for Rust.

This program, `rust-analyzer-chromiumos-wrapper`, is a wrapper around
`rust-analyzer`. It exists to translate paths between an instance of
rust-analyzer running inside the chromiumos chroot and a client running outside
the chroot.

It is of course possible to simply run `rust-analyzer` outside the chroot, but
version mismatch issues may lead to a suboptimal experience.

It should run outside the chroot. If invoked in a `chromiumos` repo in a
subdirectory of either `chromiumos/src` or `chromiumos/chroot`, it will attempt
to invoke `rust-analyzer` inside the chroot and translate paths. Otherwise, it
will attempt to invoke a `rust-analyzer` outside the chroot and will not
translate paths.

It only supports a limited set of rust-analyzer's command line options, which
aren't necessary for acting as a LSP server anyway.

## Quickstart

*Outside* the chroot, install the `rust-analyzer-chromiumos-wrapper` binary:

```
cargo install --path /path-to-a-chromiumos-checkout/src/third_party/toolchain-utils/rust-analyzer-chromiumos-wrapper
```

Make sure `~/.cargo/bin` is in your PATH, or move/symlink
`~/.cargo/bin/rust-analyzer-chromiumos-wrapper` to a location in your PATH.

Configure your editor to use the binary `rust-analyzer-chromiumos-wrapper` as
`rust-analyzer`. This configuration is specific to your editor, but see the
[Rust analyzer manual] for more about several different editors.

The following sections explain how this can be done for various editors.

[Rust analyzer manual]: https://rust-analyzer.github.io/manual.html

## Neovim

In Neovim, if you're using [nvim-lspconfig], this can be done by
putting the following in your `init.lua`:

```
require('lspconfig')['rust_analyzer'].setup {
  cmd = {'rust-analyzer-chromiumos-wrapper'},
}
```

[nvim-lspconfig]: https://github.com/neovim/nvim-lspconfig

## VSCode

In VSCode the [rust-analyzer extension] handles interaction with the LSP.
After installation, `rust-analyzer` is configured via `settings.json`. To use
`rust-analyzer-chromiumos-wrapper` for chromiumos, edit the repositories
`.vscode/settings.json` file. It should be present in any chromiumos checkout
that you edited with VSCode.

Then add the following line:
```
"rust-analyzer.server.path": "/usr/local/google/home/bkersting/.cargo/bin/rust-analyzer-chromiumos-wrapper"
```

Due to having all chromiumos crates distributed in the workspace (and no global
`Cargo.toml` defining the workspace), the crates you would like edit with
rust-analyzer need to be declared in the `rust-analyzer.linkedProjects`
setting. If you e.g. want to work on libchromeos-rs, put the following lines
into `settings.json`:
```
"rust-analyzer.linkedProjects": [
    "/path-to-chromiumos/src/platform2/libchromeos-rs/Cargo.toml",
]
```

[rust-analyzer extension]: https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer

## Misc

Inside chroot we already have a rust-analyzer installation that is installed
with the rust toolchain.

A wrapper isn't necessary for clangd, because clangd supports the option
`--path-mappings` to translate paths. In principle a similar option could be
added to `rust-analyzer`, obviating the need for this wrapper. See this
[issue on github].

[issue on github]: https://github.com/rust-lang/rust-analyzer/issues/12485
