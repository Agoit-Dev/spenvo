import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { after, before, test } from 'node:test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ID = 'spenvo-dev';

const rules = readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules },
  });
});

after(async () => {
  await testEnv.cleanup();
});

async function limpiarDatos() {
  await testEnv.clearFirestore();
}

function authed(uid) {
  return testEnv.authenticatedContext(uid);
}

const UID_OWNER = 'owner-1';
const UID_ADMIN = 'admin-1';
const UID_EDITOR = 'editor-1';
const UID_VIEWER = 'viewer-1';
const UID_OUTSIDER = 'outsider-1';
const PLAN_ID = 'plan-1';

const planData = { id: PLAN_ID, nombre: 'Casa', moneda: 'ARS', createdBy: UID_OWNER };

// Setup: crea el plan y los accesos de cada rol (ACEPTADA) para tests de plan.
async function setupPlanConMiembros() {
  const owner = authed(UID_OWNER);
  await setDoc(doc(owner.firestore(), 'planes_financieros', PLAN_ID), planData);
  await setDoc(
    doc(owner.firestore(), 'acceso_plan_financiero', `${UID_OWNER}_${PLAN_ID}`),
    { usuarioId: UID_OWNER, planId: PLAN_ID, rol: 'owner', invitacionEstado: 'aceptada' },
  );
  await setDoc(
    doc(owner.firestore(), 'acceso_plan_financiero', `${UID_ADMIN}_${PLAN_ID}`),
    { usuarioId: UID_ADMIN, planId: PLAN_ID, rol: 'admin', invitacionEstado: 'aceptada' },
  );
  await setDoc(
    doc(owner.firestore(), 'acceso_plan_financiero', `${UID_EDITOR}_${PLAN_ID}`),
    { usuarioId: UID_EDITOR, planId: PLAN_ID, rol: 'editor', invitacionEstado: 'aceptada' },
  );
  await setDoc(
    doc(owner.firestore(), 'acceso_plan_financiero', `${UID_VIEWER}_${PLAN_ID}`),
    { usuarioId: UID_VIEWER, planId: PLAN_ID, rol: 'viewer', invitacionEstado: 'aceptada' },
  );
}

// ---------------- planes_financieros ----------------
test('owner crea un plan', async () => {
  await limpiarDatos();
  const owner = authed(UID_OWNER);
  await assertSucceeds(setDoc(doc(owner.firestore(), 'planes_financieros', 'plan-owner'), {
    id: 'plan-owner', nombre: 'X', moneda: 'ARS', createdBy: UID_OWNER,
  }));
});

test('usuario no puede crear plan con createdBy ajeno', async () => {
  await limpiarDatos();
  const owner = authed(UID_OWNER);
  await assertFails(setDoc(doc(owner.firestore(), 'planes_financieros', 'plan-mal'), {
    id: 'plan-mal', nombre: 'X', moneda: 'ARS', createdBy: 'otro-uid',
  }));
});

test('miembro viewer puede leer plan, outsider no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const viewer = authed(UID_VIEWER);
  await assertSucceeds(getDoc(doc(viewer.firestore(), 'planes_financieros', PLAN_ID)));
  const outsider = authed(UID_OUTSIDER);
  await assertFails(getDoc(doc(outsider.firestore(), 'planes_financieros', PLAN_ID)));
});

test('editor actualiza plan, viewer no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), { nombre: 'Casa2' }));
  const viewer = authed(UID_VIEWER);
  await assertFails(updateDoc(doc(viewer.firestore(), 'planes_financieros', PLAN_ID), { nombre: 'Casa3' }));
});

test('nadie puede borrar un plan', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const owner = authed(UID_OWNER);
  await assertFails(deleteDoc(doc(owner.firestore(), 'planes_financieros', PLAN_ID)));
});

// ---------------- acceso_plan_financiero ----------------
test('usuario crea su propio acceso owner', async () => {
  await limpiarDatos();
  const owner = authed(UID_OWNER);
  await setDoc(doc(owner.firestore(), 'planes_financieros', 'plan-owner2'), {
    id: 'plan-owner2', nombre: 'X', moneda: 'ARS', createdBy: UID_OWNER,
  });
  await assertSucceeds(setDoc(
    doc(owner.firestore(), 'acceso_plan_financiero', `${UID_OWNER}_plan-owner2`),
    { usuarioId: UID_OWNER, planId: 'plan-owner2', rol: 'owner', invitacionEstado: 'aceptada' },
  ));
});

test('admin invita a un miembro', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const admin = authed(UID_ADMIN);
  await assertSucceeds(setDoc(
    doc(admin.firestore(), 'acceso_plan_financiero', `nuevo-miembro_${PLAN_ID}`),
    { usuarioId: 'nuevo-miembro', planId: PLAN_ID, rol: 'editor', invitacionEstado: 'pendiente' },
  ));
});

test('usuario acepta su propia invitacion pendiente', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const admin = authed(UID_ADMIN);
  await setDoc(
    doc(admin.firestore(), 'acceso_plan_financiero', `${UID_OUTSIDER}_${PLAN_ID}`),
    { usuarioId: UID_OUTSIDER, planId: PLAN_ID, rol: 'editor', invitacionEstado: 'pendiente' },
  );
  const invitado = authed(UID_OUTSIDER);
  await assertSucceeds(updateDoc(
    doc(invitado.firestore(), 'acceso_plan_financiero', `${UID_OUTSIDER}_${PLAN_ID}`),
    { invitacionEstado: 'aceptada' },
  ));
});

test('outsider no puede crear acceso a plan ajeno', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const outsider = authed(UID_OUTSIDER);
  await assertFails(setDoc(
    doc(outsider.firestore(), 'acceso_plan_financiero', `${UID_OUTSIDER}_${PLAN_ID}`),
    { usuarioId: UID_OUTSIDER, planId: PLAN_ID, rol: 'viewer', invitacionEstado: 'aceptada' },
  ));
});

// ---------------- categorias ----------------
test('editor crea categoria, viewer no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(setDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    id: 'cat-1', planId: PLAN_ID, nombre: 'Comida', tipo: 'gasto',
  }));
  const viewer = authed(UID_VIEWER);
  await assertFails(setDoc(doc(viewer.firestore(), 'categorias', 'cat-2'), {
    id: 'cat-2', planId: PLAN_ID, nombre: 'Ocio', tipo: 'gasto',
  }));
});

test('miembro lee categoria, outsider no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await setDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    id: 'cat-1', planId: PLAN_ID, nombre: 'Comida', tipo: 'gasto',
  });
  const viewer = authed(UID_VIEWER);
  await assertSucceeds(getDoc(doc(viewer.firestore(), 'categorias', 'cat-1')));
  const outsider = authed(UID_OUTSIDER);
  await assertFails(getDoc(doc(outsider.firestore(), 'categorias', 'cat-1')));
});

// ---------------- gastos / ingresos ----------------
test('editor crea gasto, viewer no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(setDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    id: 'gasto-1', planId: PLAN_ID, categoriaId: 'cat-1', monto: 2500,
  }));
  const viewer = authed(UID_VIEWER);
  await assertFails(setDoc(doc(viewer.firestore(), 'gastos', 'gasto-2'), {
    id: 'gasto-2', planId: PLAN_ID, categoriaId: 'cat-1', monto: 100,
  }));
});

test('miembro lee gasto, outsider no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await setDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    id: 'gasto-1', planId: PLAN_ID, categoriaId: 'cat-1', monto: 2500,
  });
  const viewer = authed(UID_VIEWER);
  await assertSucceeds(getDoc(doc(viewer.firestore(), 'gastos', 'gasto-1')));
  const outsider = authed(UID_OUTSIDER);
  await assertFails(getDoc(doc(outsider.firestore(), 'gastos', 'gasto-1')));
});

test('editor crea ingreso', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(setDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    id: 'ing-1', planId: PLAN_ID, categoriaId: 'cat-1', monto: 5000,
  }));
});
