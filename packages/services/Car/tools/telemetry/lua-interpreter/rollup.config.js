import {nodeResolve} from '@rollup/plugin-node-resolve'
export default {
  input: 'static/js/index.js',
  output: {file: 'static/js/index.bundle.js', format: 'iife'},
  plugins: [nodeResolve()]
}