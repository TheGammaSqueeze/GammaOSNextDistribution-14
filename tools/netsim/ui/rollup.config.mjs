// Import rollup plugins
import { rollupPluginHTML as html } from '@web/rollup-plugin-html';
import { copy } from '@web/rollup-plugin-copy';
import resolve from '@rollup/plugin-node-resolve';
import { terser } from 'rollup-plugin-terser';
import summary from 'rollup-plugin-summary';
import { rollupImportMapPlugin } from "rollup-plugin-import-map";

export default {
  plugins: [
    // Entry point for application build; can specify a glob to build multiple
    // HTML files for non-SPA app
    html({
      input: ['./web/index.html', './web/dev.html']
    }),
    // Add Import maps from libraries to CDN urls
    rollupImportMapPlugin([
      {
        "imports": {
          'lit': 'https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js',
          'lit/decorators.js': 'https://cdn.skypack.dev/pin/lit@v2.5.0-jYRq0AKQogjUdUh7SCAE/mode=imports/optimized/lit/decorators.js',
          'lit/directives/live.js': 'https://cdn.jsdelivr.net/gh/lit/dist@2/all/lit-all.min.js',
          'lit/directives/style-map.js': 'https://cdn.jsdelivr.net/gh/lit/dist@2/all/lit-all.min.js',
        }
      }
    ]),
    // Resolve bare module specifiers to relative paths
    resolve(),
    // Minify JS
    terser({
      ecma: 2020,
      module: true,
      warnings: true,
    }),
    // Print bundle summary
    summary(),
    // Copy any static assets to build directory
    copy({
      patterns: ['./assets/*'],
    }),
  ],
  output: {
    dir: 'dist',
    preserveModules: true,
    preserveModulesRoot: 'web'
  },
  preserveEntrySignatures: 'strict',
};