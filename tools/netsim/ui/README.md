<p align="center">
  <img width="200" src="https://open-wc.org/hero.png"></img>
</p>

## netsim web UI

This directory contains the Web UI for netsim.

## Prerequisite

The netsimd web server must be up and running.

## Build Commands

Firstly, you must enter the ui directory and run npm install.

```sh
cd $REPO/tools/netsim/ui
npm install
```

Command for compiling and building web UI:

```sh
npm run build
```

Command for translating netsim's model.proto into model.ts:

```sh
npm run tsproto
```

Command for running local web development server:

```sh
npm start
```

Local web server will be served in `http://localhost:8000/web/`

## Scripts

- `build` compiles TypeScript into JavaScript and bundle to distribution with rollup
- `tsproto` translates netsim's model.proto into model.ts
- `start` runs your app for development, reloading on file changes

## Tooling configs

- `package.json` contains all npm packages and scripts for web development
- `rollup.config.mjs` applies import mappings to CDNs and bundles to distribution
- `tsconfig.json` has configurations for typescript compiling.

## Authors

[Hyun Jae Moon] hyunjaemoon@google.com

[Bill Schilit] schilit@google.com