// Private packing station. Only the paired operator phone may ask the Mac to issue a Zap.
import { X509Certificate, randomBytes, timingSafeEqual } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { createServer } from 'node:https';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { register } from './zap-issuer.mjs';

export const port = 49371;

function prepareBinding(dir, host, address) {
  const state = join(dir, 'workshop');
  if (!existsSync(state)) {
    const temp = mkdtempSync(join(dir, '.workshop-'));
    try {
      const config = '[req]\ndistinguished_name=dn\nx509_extensions=server\n[dn]\n[server]\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\nsubjectAltName=DNS:' + host + '\n';
      writeFileSync(join(temp, 'openssl.cnf'), config);
      execFileSync('/usr/bin/openssl', ['req', '-new', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha256',
        '-days', '825', '-subj', '/CN=' + host, '-config', join(temp, 'openssl.cnf'),
        '-keyout', join(temp, 'server.key'), '-out', join(temp, 'server.crt')], { stdio: 'ignore' });
      writeFileSync(join(temp, 'token'), randomBytes(32).toString('hex'), {mode: 0o600});
      renameSync(temp, state);
    } finally { rmSync(temp, { recursive: true, force: true }); }
  }
  const cert = readFileSync(join(state, 'server.crt'));
  const key = readFileSync(join(state, 'server.key'));
  const token = readFileSync(join(state, 'token'), 'utf8');
  const pairing = { host, address, port, token, certificate: new X509Certificate(cert).raw.toString('base64') };
  const staging = mkdtempSync(join(state, '.pair-'));
  try {
    writeFileSync(join(staging, 'pair.json'), JSON.stringify(pairing), {mode: 0o600});
    renameSync(join(staging,'pair.json'), join(state,'ZapWorkshopPairing.json'));
  } finally { rmSync(staging, { recursive: true, force: true }); }
  return {cert, key, token};
}

export function stationServer(dir, {cert, key, token}) {
  const expected = Buffer.from('Bearer ' + token);
  const server = createServer({cert, key, minVersion: 'TLSv1.2', maxHeaderSize: 4096}, async (req, res) => {
    const finish = (status, value = '') => {
      res.writeHead(status, {'Cache-Control':'no-store','Content-Type':'text/plain; charset=utf-8'});
      res.end(value);
    };
    const auth = Buffer.from(req.headers.authorization ?? '');
    if (auth.length !== expected.length || !timingSafeEqual(auth, expected)) return finish(403);
    if (req.method === 'GET' && req.url === '/ready') return finish(200, 'ready');
    if (req.method !== 'POST' || req.url !== '/issue') return finish(404);
    if (req.headers['content-length'] !== '14' || req.headers['content-type'] !== 'text/plain') return finish(400);
    try {
      let uid = '';
      for await (const part of req) {
        uid += part.toString('latin1');
        if (uid.length > 14) return finish(400);
      }
      if (!/^[0-9a-fA-F]{14}$/.test(uid)) return finish(400);
      return finish(200, register(dir, uid));
    } catch {
      if (!res.headersSent && !res.destroyed) finish(500);
    }
  });
  server.requestTimeout = 10000;
  server.headersTimeout = 8000;
  server.maxConnections = 16;
  return server;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.umask(0o077);
  const dir = resolve(process.argv[2]);
  const host = execFileSync('/usr/sbin/scutil', ['--get','LocalHostName'], {encoding:'utf8'}).trim() + '.local';
  if (!/^[a-zA-Z0-9-]+\.local$/.test(host) || !existsSync(join(dir,'issuer.json'))) throw Error('Issuer or Mac hostname unavailable.');
  const pairingOnly = process.argv[3] === '--pair-only';
  let address;
  try {
    const route = execFileSync('/sbin/route', ['-n','get','default'], {encoding:'utf8'});
    const iface = route.match(/^\s*interface:\s*(\w+)\s*$/m)?.[1];
    if (!iface) throw Error('Verbinde den Mac mit dem WLAN und starte die Zap-Station erneut.');
    address = execFileSync('/usr/sbin/ipconfig',['getifaddr',iface],{encoding:'utf8'}).trim();
    if (!/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(address)) throw Error('Für die Zap-Station ein privates WLAN verwenden.');
  } catch (error) {
    if (!pairingOnly) throw error;
    address = undefined;
  }
  const config = prepareBinding(dir, host.toLowerCase(), address);
  if (!pairingOnly) {
    const server = stationServer(dir, config);
    server.on('error', e => { console.error(e.code === 'EADDRINUSE' ? 'Der Anschluss ist bereits belegt. Prüfe das offene Stationsfenster.' : 'Zap-Station konnte nicht starten.'); process.exitCode = 1; });
    server.listen(port, address, () => console.log('Zap-Station bereit. Mac und iPhone im selben WLAN lassen. Zum Beenden dieses Fenster schließen.'));
  }
}
