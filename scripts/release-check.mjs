import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const required = [
  'index.html',
  'package.json',
  'package-lock.json',
  'vite.config.js',
  'capacitor.config.json',
  'src/main.jsx',
  'src/App.jsx',
  'src/pages/Home.jsx',
  'src/pages/Editor.jsx',
  'src/components/UpdateChecker.jsx',
  'public/icon.svg',
  'public/icon-512.png',
  '.github/workflows/build-apk.yml',
  '.env.example',
  '.gitignore',
  'README.md',
];

const missing = required.filter(file => !fs.existsSync(path.join(root, file)));
if (missing.length) {
  console.error('❌ Missing required release files:');
  for (const file of missing) console.error(' - ' + file);
  process.exit(1);
}

const forbiddenRoots = ['node_modules', 'dist', 'android'];
const presentForbidden = forbiddenRoots.filter(name => fs.existsSync(path.join(root, name)) && !fs.statSync(path.join(root, name)).isFile());
if (presentForbidden.length) {
  console.warn('⚠️ Generated folders exist locally and should not be committed: ' + presentForbidden.join(', '));
}

const secretPatterns = [
  { name: 'Google OAuth client secret', pattern: /GOCSPX-[A-Za-z0-9_-]{20,}/ },
  { name: 'GitHub token', pattern: /github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}|gho_[A-Za-z0-9]{20,}/ },
  { name: 'Private key block', pattern: /-----BEGIN (?:RSA |EC |OPENSSH |)PRIVATE KEY-----/ },
];
const scanFiles = ['.env.example', 'README.md', 'package.json', 'vite.config.js', 'capacitor.config.json'];
const leaks = [];
for (const file of scanFiles) {
  const full = path.join(root, file);
  if (!fs.existsSync(full)) continue;
  const text = fs.readFileSync(full, 'utf8');
  for (const s of secretPatterns) {
    if (s.pattern.test(text)) leaks.push(`${file}: ${s.name}`);
  }
}
if (leaks.length) {
  console.error('❌ Possible secret found:');
  for (const leak of leaks) console.error(' - ' + leak);
  process.exit(1);
}

const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
if (pkg.engines?.node !== '20.x') {
  console.error('❌ package.json must keep engines.node = "20.x" for GitHub Actions compatibility.');
  process.exit(1);
}

console.log('✅ Release file check passed');
