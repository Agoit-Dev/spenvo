import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, uploadBytes, getBytes } from 'firebase/storage';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { after, before, test } from 'node:test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ID = 'spenvo-dev';

const rules = readFileSync(path.join(__dirname, '..', 'storage.rules'), 'utf8');

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: { rules },
  });
});

after(async () => {
  await testEnv.cleanup();
});

function authed(uid) {
  return testEnv.authenticatedContext(uid);
}

function unauthed() {
  return testEnv.unauthenticatedContext();
}

const UID_OWNER = 'owner-1';
const UID_OTHER = 'other-1';

const IMAGE_BYTES = new Uint8Array([0xff, 0xd8, 0xff, 0xe0]);
const IMAGE_METADATA = { contentType: 'image/jpeg' };

function avatarRef(context, uid) {
  return ref(context.storage(), `avatars/${uid}/avatar.jpg`);
}

// ---------------- avatars/{uid}/avatar.jpg ----------------

test('el dueño puede escribir su propio avatar', async () => {
  const owner = authed(UID_OWNER);
  await assertSucceeds(uploadBytes(avatarRef(owner, UID_OWNER), IMAGE_BYTES, IMAGE_METADATA));
});

test('el dueño puede leer su propio avatar', async () => {
  const owner = authed(UID_OWNER);
  await assertSucceeds(uploadBytes(avatarRef(owner, UID_OWNER), IMAGE_BYTES, IMAGE_METADATA));
  await assertSucceeds(getBytes(avatarRef(owner, UID_OWNER)));
});

test('un usuario no puede escribir el avatar de otro uid', async () => {
  const other = authed(UID_OTHER);
  await assertFails(uploadBytes(avatarRef(other, UID_OWNER), IMAGE_BYTES, IMAGE_METADATA));
});

test('un usuario no puede leer el avatar de otro uid', async () => {
  const owner = authed(UID_OWNER);
  await assertSucceeds(uploadBytes(avatarRef(owner, UID_OWNER), IMAGE_BYTES, IMAGE_METADATA));
  const other = authed(UID_OTHER);
  await assertFails(getBytes(avatarRef(other, UID_OWNER)));
});

test('un archivo mayor a 5MB es denegado', async () => {
  const owner = authed(UID_OWNER);
  const oversized = new Uint8Array(5 * 1024 * 1024 + 1);
  await assertFails(uploadBytes(avatarRef(owner, UID_OWNER), oversized, IMAGE_METADATA));
});

test('un content-type que no es imagen es denegado', async () => {
  const owner = authed(UID_OWNER);
  await assertFails(
    uploadBytes(avatarRef(owner, UID_OWNER), IMAGE_BYTES, { contentType: 'application/pdf' }),
  );
});

test('un usuario no autenticado no puede leer ni escribir', async () => {
  const anon = unauthed();
  await assertFails(uploadBytes(avatarRef(anon, UID_OWNER), IMAGE_BYTES, IMAGE_METADATA));
  await assertFails(getBytes(avatarRef(anon, UID_OWNER)));
});
