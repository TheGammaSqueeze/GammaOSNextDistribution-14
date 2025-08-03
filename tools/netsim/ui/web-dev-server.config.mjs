// import { hmrPlugin, presets } from '@open-wc/dev-server-hmr';

/** Use Hot Module replacement by adding --hmr to the start command */
const hmr = process.argv.includes('--hmr');

export default /** @type {import('@web/dev-server').DevServerConfig} */ ({
  open: true,
  appIndex: 'web/index.html',
  watch: !hmr,
  nodeResolve: {
    exportConditions: ['browser', 'development'],
  },
});
