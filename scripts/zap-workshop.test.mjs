import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { execFileSync } from 'node:child_process';
import { createDecipheriv, createPublicKey, verify } from 'node:crypto';
import { mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { request } from 'node:https';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { stationServer } from './zap-workshop.mjs';

const script = name => fileURLToPath(new URL(name, import.meta.url));
let folder, issuer, binding, server, stationPort, trust;
before(async () => {
  folder = mkdtempSync(join(tmpdir(),'jn-workshop-check-'));
  issuer = join(folder,'issuer');
  execFileSync(process.execPath, [script('./zap-issuer.mjs'),'init',issuer,join(folder,'trust.json')]);
  execFileSync(process.execPath, [script('./zap-workshop.mjs'),issuer,'--pair-only']);
  trust = JSON.parse(readFileSync(join(folder,'trust.json')));
  const state = join(issuer,'workshop');
  binding = JSON.parse(readFileSync(join(state,'ZapWorkshopPairing.json')));
  server = stationServer(issuer,{token:binding.token, key:readFileSync(join(state,'server.key')),cert:readFileSync(join(state,'server.crt'))});
  server.listen(0,'127.0.0.1');
  await once(server,'listening');
  stationPort = server.address().port;
});
after(async () => {
  if (server?.listening) await new Promise(resolve => server.close(resolve));
  if (folder) rmSync(folder,{recursive:true,force:true});
});

function call(path, body, token = binding.token) {
  return new Promise((resolve,reject) => {
    const req = request({hostname:'127.0.0.1',port:stationPort,path,
      servername:binding.host,ca:readFileSync(join(issuer,'workshop/server.crt')),
      method:body === undefined ? 'GET':'POST',headers: {
        Authorization:'Bearer '+token,...(body !== undefined && {'Content-Type':'text/plain','Content-Length':Buffer.byteLength(body)})
      }}, res => {
        let value = '';
        res.on('data',b=>value+=b);
        res.on('end',()=>resolve({status:res.statusCode,value}));
      });
    req.on('error',reject);
    req.end(body);
  });
}

test('unauthorized reads and writes leave the registry empty', async () => {
  assert.equal((await call('/ready',undefined,'wrong')).status,403);
  assert.equal((await call('/issue','04aaaaaaaaaaaa','0'.repeat(64))).status,403);
  assert.equal((await call('/ready')).value,'ready');
  assert.equal((await call('/issue','wrong')).status,400);
  assert.deepEqual(readdirSync(join(issuer,'registrations')),[]);
});

test('two concurrent authenticated scans return one registered, UID-bound product credential', async () => {
  const uid = '04aabbccddeeff';
  const [a,b] = await Promise.all([call('/issue',uid),call('/issue',uid)]);
  assert.equal(a.status,200); assert.deepEqual(a,b);
  assert.deepEqual(readdirSync(join(issuer,'registrations')),[uid+'.txt']);
  assert.equal(a.value,execFileSync(process.execPath,[script('./zap-issuer.mjs'),'register',issuer,uid],{encoding:'utf8'}).trim());
  assert.ok(a.value.startsWith('JNZ1.'));
  const envelope = Buffer.from(a.value.slice(5),'base64');
  const sealed = envelope.subarray(64);
  const key = createPublicKey({key:{kty:'OKP',crv:'Ed25519',x:Buffer.from(trust.verificationKey,'base64').toString('base64url')},format:'jwk'});
  const bindingFor = id => Buffer.concat([Buffer.from('JustNoise/Zap/1'),Buffer.from(id,'hex')]);
  const signed = id => verify(null,Buffer.concat([bindingFor(id),sealed]),key,envelope.subarray(0,64));
  assert.ok(signed(uid)); assert.equal(signed('04aabbccddee00'),false);
  const dec = createDecipheriv('aes-256-gcm',Buffer.from(trust.contentKey,'base64'),sealed.subarray(0,12));
  dec.setAAD(bindingFor(uid)); dec.setAuthTag(sealed.subarray(-16));
  assert.equal(Buffer.concat([dec.update(sealed.subarray(12,-16)),dec.final()]).toString(),'justnoise.zap.activation.v1');
});
