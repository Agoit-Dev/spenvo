import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, collection, query, where } from 'firebase/firestore';
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

function authed(uid, claims = {}) {
  return testEnv.authenticatedContext(uid, claims);
}

const UID_OWNER = 'owner-1';
const UID_ADMIN = 'admin-1';
const UID_EDITOR = 'editor-1';
const UID_VIEWER = 'viewer-1';
const UID_OUTSIDER = 'outsider-1';
const PLAN_ID = 'plan-1';

const planData = {
  id: PLAN_ID, nombre: 'Casa', moneda: 'ARS', createdBy: UID_OWNER, createdAt: new Date('2024-01-01'),
};

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

test('editor actualiza plan con editedBy propio, viewer no', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), {
    nombre: 'Casa2', editedBy: UID_EDITOR, editedAt: new Date(),
  }));
  const viewer = authed(UID_VIEWER);
  await assertFails(updateDoc(doc(viewer.firestore(), 'planes_financieros', PLAN_ID), {
    nombre: 'Casa3', editedBy: UID_VIEWER, editedAt: new Date(),
  }));
});

test('actualizar plan con editedBy ajeno es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), {
    nombre: 'Casa2', editedBy: UID_ADMIN, editedAt: new Date(),
  }));
});

test('actualizar plan sin editedAt timestamp es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), {
    nombre: 'Casa2', editedBy: UID_EDITOR, editedAt: 'no-es-timestamp',
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), {
    nombre: 'Casa2', editedBy: UID_EDITOR,
  }));
});

test('actualizar plan cambiando createdBy o createdAt es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), {
    editedBy: UID_EDITOR, editedAt: new Date(), createdBy: UID_ADMIN,
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'planes_financieros', PLAN_ID), {
    editedBy: UID_EDITOR, editedAt: new Date(), createdAt: new Date('2099-01-01'),
  }));
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

async function crearCategoriaBase() {
  const editor = authed(UID_EDITOR);
  await setDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    id: 'cat-1', planId: PLAN_ID, nombre: 'Comida', tipo: 'gasto',
  });
}

test('editor actualiza categoria con editedBy propio', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearCategoriaBase();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(updateDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    nombre: 'Comida y bebida', editedBy: UID_EDITOR, editedAt: new Date(),
  }));
});

test('actualizar categoria con editedBy ajeno es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearCategoriaBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    editedBy: UID_ADMIN, editedAt: new Date(),
  }));
});

test('actualizar categoria sin editedAt timestamp es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearCategoriaBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    editedBy: UID_EDITOR, editedAt: 'no-es-timestamp',
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    editedBy: UID_EDITOR,
  }));
});

test('actualizar categoria cambiando planId es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearCategoriaBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'categorias', 'cat-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), planId: 'otro-plan',
  }));
});

test('nadie puede borrar una categoria directamente', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearCategoriaBase();
  const editor = authed(UID_EDITOR);
  await assertFails(deleteDoc(doc(editor.firestore(), 'categorias', 'cat-1')));
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

async function crearGastoBase() {
  const editor = authed(UID_EDITOR);
  await setDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    id: 'gasto-1', planId: PLAN_ID, categoriaId: 'cat-1', montoUnidadesMenores: 2500,
    creadoPor: UID_EDITOR, createdAt: new Date('2024-01-01'),
  });
}

async function crearIngresoBase() {
  const editor = authed(UID_EDITOR);
  await setDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    id: 'ing-1', planId: PLAN_ID, categoriaId: 'cat-1', montoUnidadesMenores: 5000,
    creadoPor: UID_EDITOR, createdAt: new Date('2024-01-01'),
  });
}

test('editor actualiza gasto con editedBy propio', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearGastoBase();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    montoUnidadesMenores: 3000, editedBy: UID_EDITOR, editedAt: new Date(),
  }));
});

test('actualizar gasto con editedBy ajeno es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearGastoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    editedBy: UID_ADMIN, editedAt: new Date(),
  }));
});

test('actualizar gasto sin editedAt timestamp es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearGastoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    editedBy: UID_EDITOR, editedAt: 'no-es-timestamp',
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    editedBy: UID_EDITOR,
  }));
});

test('actualizar gasto cambiando creadoPor, createdAt o planId es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearGastoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), creadoPor: UID_ADMIN,
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), createdAt: new Date('2099-01-01'),
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'gastos', 'gasto-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), planId: 'otro-plan',
  }));
});

test('nadie puede borrar un gasto directamente', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearGastoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(deleteDoc(doc(editor.firestore(), 'gastos', 'gasto-1')));
});

test('editor actualiza ingreso con editedBy propio', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearIngresoBase();
  const editor = authed(UID_EDITOR);
  await assertSucceeds(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    montoUnidadesMenores: 6000, editedBy: UID_EDITOR, editedAt: new Date(),
  }));
});

test('actualizar ingreso con editedBy ajeno es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearIngresoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    editedBy: UID_ADMIN, editedAt: new Date(),
  }));
});

test('actualizar ingreso sin editedAt timestamp es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearIngresoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    editedBy: UID_EDITOR, editedAt: 'no-es-timestamp',
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    editedBy: UID_EDITOR,
  }));
});

test('actualizar ingreso cambiando creadoPor, createdAt o planId es denegado', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearIngresoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), creadoPor: UID_ADMIN,
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), createdAt: new Date('2099-01-01'),
  }));
  await assertFails(updateDoc(doc(editor.firestore(), 'ingresos', 'ing-1'), {
    editedBy: UID_EDITOR, editedAt: new Date(), planId: 'otro-plan',
  }));
});

test('nadie puede borrar un ingreso directamente', async () => {
  await limpiarDatos();
  await setupPlanConMiembros();
  await crearIngresoBase();
  const editor = authed(UID_EDITOR);
  await assertFails(deleteDoc(doc(editor.firestore(), 'ingresos', 'ing-1')));
});

// ---------------- usuarios ----------------
test('cualquier autenticado puede hacer get de un usuario conocido', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'usuarios', 'u1'), {
    uid: 'u1', nombreUsuario: 'gatoazul1', createdAt: new Date(), updatedAt: new Date(),
  });
  const u2 = authed('u2');
  await assertSucceeds(getDoc(doc(u2.firestore(), 'usuarios', 'u1')));
});

test('deniega list sobre la coleccion usuarios', async () => {
  await limpiarDatos();
  const u2 = authed('u2');
  await assertFails(getDocs(collection(u2.firestore(), 'usuarios')));
});

test('el dueno puede crear su propio doc de usuarios', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertSucceeds(setDoc(doc(u1.firestore(), 'usuarios', 'u1'), {
    uid: 'u1', nombreUsuario: 'gatoazul1', createdAt: new Date(), updatedAt: new Date(),
  }));
});

test('no se puede crear un doc de usuarios apuntando a otro uid', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertFails(setDoc(doc(u1.firestore(), 'usuarios', 'u2'), {
    uid: 'u2', nombreUsuario: 'gatoazul1', createdAt: new Date(), updatedAt: new Date(),
  }));
});

// ---------------- nombres_usuario ----------------
test('cualquier autenticado puede hacer get de un nombreUsuario conocido', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1'), { usuarioId: 'u1' });
  const u2 = authed('u2');
  await assertSucceeds(getDoc(doc(u2.firestore(), 'nombres_usuario', 'gatoazul1')));
});

test('deniega list sobre la coleccion nombres_usuario', async () => {
  await limpiarDatos();
  const u2 = authed('u2');
  await assertFails(getDocs(collection(u2.firestore(), 'nombres_usuario')));
});

test('el dueno puede reservar un nombreUsuario para si mismo', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertSucceeds(setDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1'), { usuarioId: 'u1' }));
});

test('no se puede reservar un nombreUsuario apuntando a otro uid', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertFails(setDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1'), { usuarioId: 'u2' }));
});

test('no se puede sobrescribir una reserva de nombreUsuario existente', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1'), { usuarioId: 'u1' });
  await assertFails(setDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1'), { usuarioId: 'u1' }));
});

test('solo el dueno puede borrar su propia reserva de nombreUsuario', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1'), { usuarioId: 'u1' });
  const u2 = authed('u2');
  await assertFails(deleteDoc(doc(u2.firestore(), 'nombres_usuario', 'gatoazul1')));
  await assertSucceeds(deleteDoc(doc(u1.firestore(), 'nombres_usuario', 'gatoazul1')));
});

// ---------------- emails_usuario ----------------
test('cualquier autenticado puede hacer get de un email conocido', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com'), { usuarioId: 'u1' });
  const u2 = authed('u2');
  await assertSucceeds(getDoc(doc(u2.firestore(), 'emails_usuario', 'ana@example.com')));
});

test('deniega list sobre la coleccion emails_usuario', async () => {
  await limpiarDatos();
  const u2 = authed('u2');
  await assertFails(getDocs(collection(u2.firestore(), 'emails_usuario')));
});

test('el dueno puede indexar su propio email', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertSucceeds(setDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com'), { usuarioId: 'u1' }));
});

test('no se puede indexar un email apuntando a otro uid', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertFails(setDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com'), { usuarioId: 'u2' }));
});

test('no se puede sobrescribir un email ya indexado', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com'), { usuarioId: 'u1' });
  await assertFails(setDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com'), { usuarioId: 'u1' }));
});

test('solo el dueno puede borrar su propio indice de email', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com'), { usuarioId: 'u1' });
  const u2 = authed('u2');
  await assertFails(deleteDoc(doc(u2.firestore(), 'emails_usuario', 'ana@example.com')));
  await assertSucceeds(deleteDoc(doc(u1.firestore(), 'emails_usuario', 'ana@example.com')));
});

// ---------------- invitaciones_pendientes_por_email ----------------
test('cualquier autenticado puede crear una invitacion pendiente', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await assertSucceeds(setDoc(
    doc(u1.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1'),
    { email: 'ana@example.com', planId: 'p1', rol: 'editor', invitadoPor: 'u1', createdAt: new Date() },
  ));
});

test('get directo sobre invitaciones_pendientes_por_email es denegado', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(
    doc(u1.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1'),
    { email: 'ana@example.com', planId: 'p1', rol: 'editor', invitadoPor: 'u1', createdAt: new Date() },
  );
  const ana = authed('u2', { email: 'ana@example.com' });
  await assertFails(getDoc(doc(ana.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1')));
});

test('list solo funciona filtrando por el propio email verificado', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(
    doc(u1.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1'),
    { email: 'ana@example.com', planId: 'p1', rol: 'editor', invitadoPor: 'u1', createdAt: new Date() },
  );
  const ana = authed('u2', { email: 'ana@example.com' });
  await assertSucceeds(getDocs(
    query(collection(ana.firestore(), 'invitaciones_pendientes_por_email'), where('email', '==', 'ana@example.com')),
  ));
  const u3 = authed('u3');
  await assertFails(getDocs(
    query(collection(u3.firestore(), 'invitaciones_pendientes_por_email'), where('email', '==', 'ana@example.com')),
  ));
});

test('deniega list sin filtrar sobre invitaciones_pendientes_por_email, incluso para el propio email', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(
    doc(u1.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1'),
    { email: 'ana@example.com', planId: 'p1', rol: 'editor', invitadoPor: 'u1', createdAt: new Date() },
  );
  const ana = authed('u2', { email: 'ana@example.com' });
  await assertFails(getDocs(collection(ana.firestore(), 'invitaciones_pendientes_por_email')));
});

test('solo el invitado con el email coincidente puede borrar la invitacion pendiente', async () => {
  await limpiarDatos();
  const u1 = authed('u1');
  await setDoc(
    doc(u1.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1'),
    { email: 'ana@example.com', planId: 'p1', rol: 'editor', invitadoPor: 'u1', createdAt: new Date() },
  );
  const otro = authed('u3', { email: 'otro@example.com' });
  await assertFails(deleteDoc(doc(otro.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1')));
  const ana = authed('u2', { email: 'ana@example.com' });
  await assertSucceeds(deleteDoc(doc(ana.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1')));
});

// ---------------- acceso_plan_financiero create: auto-resolucion de invitacion pendiente ----------------
async function seedInvitacionPendiente(rol) {
  const seeder = authed('owner-seed');
  await setDoc(doc(seeder.firestore(), 'planes_financieros', 'p1'), {
    id: 'p1', nombre: 'Casa', moneda: 'ARS', createdBy: 'owner-seed',
  });
  await setDoc(
    doc(seeder.firestore(), 'invitaciones_pendientes_por_email', 'ana@example.com_p1'),
    { email: 'ana@example.com', planId: 'p1', rol, invitadoPor: 'owner-seed', createdAt: new Date() },
  );
}

test('el propio invitado puede auto-otorgarse el rol exacto de su invitacion pendiente', async () => {
  await limpiarDatos();
  await seedInvitacionPendiente('editor');
  const ana = authed('u1', { email: 'ana@example.com' });
  await assertSucceeds(setDoc(doc(ana.firestore(), 'acceso_plan_financiero', 'u1_p1'), {
    usuarioId: 'u1', planId: 'p1', rol: 'editor', invitacionEstado: 'pendiente',
    createdAt: new Date(), updatedAt: new Date(),
  }));
});

test('no puede auto-otorgarse un rol distinto al de la invitacion pendiente', async () => {
  await limpiarDatos();
  await seedInvitacionPendiente('editor');
  const ana = authed('u1', { email: 'ana@example.com' });
  await assertFails(setDoc(doc(ana.firestore(), 'acceso_plan_financiero', 'u1_p1'), {
    usuarioId: 'u1', planId: 'p1', rol: 'owner', invitacionEstado: 'pendiente',
    createdAt: new Date(), updatedAt: new Date(),
  }));
});

test('no puede auto-otorgarse acceso sin una invitacion pendiente para su email verificado', async () => {
  await limpiarDatos();
  await seedInvitacionPendiente('editor');
  const otro = authed('u1', { email: 'otro@example.com' });
  await assertFails(setDoc(doc(otro.firestore(), 'acceso_plan_financiero', 'u1_p1'), {
    usuarioId: 'u1', planId: 'p1', rol: 'editor', invitacionEstado: 'pendiente',
    createdAt: new Date(), updatedAt: new Date(),
  }));
});
