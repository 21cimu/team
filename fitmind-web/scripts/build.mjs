import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const steps = [
  ['TypeScript', join(root, 'node_modules', 'typescript', 'bin', 'tsc')],
  ['Vite', join(root, 'node_modules', 'vite', 'bin', 'vite.js'), 'build'],
];

for (const [name, script, ...args] of steps) {
  const result = spawnSync(process.execPath, [script, ...args], {
    cwd: root,
    stdio: 'inherit',
  });

  if (result.signal) {
    console.error(`${name} stopped with signal ${result.signal}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}
