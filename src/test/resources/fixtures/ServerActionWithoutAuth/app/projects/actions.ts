'use server';

import { getServerSession } from 'next-auth';
import { z } from 'zod';
import { db } from '@/lib/db';
import { authenticated, runNow, validated, withAuth, withLogging } from '@/lib/wrappers';

// compliant: the wrapper checks the session, directly or through a helper
export const renameProject = withAuth(async (user, id: string, name: string) => {
  await db.project.update({ where: { id, ownerId: user.id }, data: { name } });
});

export const archiveProject = authenticated(async function (id: string) {
  await db.project.update({ where: { id }, data: { archived: true } });
});

// the wrappers never check the session
export const deleteProject = validated(z.string(), async (id) => { // Noncompliant {{Check the session in "deleteProject"}}
  await db.project.delete({ where: { id } });
});

export const clearProjects = withLogging(async () => { // Noncompliant
  await db.project.deleteMany();
});

// compliant: a wrapper of this module that checks the session
const withOwner = (fn) => async (...args) => {
  const session = await getServerSession();
  if (!session) {
    throw new Error('Unauthorized');
  }
  return fn(session, ...args);
};

export const transferProject = withOwner(async (session, id: string) => {
  await db.project.update({ where: { id }, data: { ownerId: session.user.id } });
});

async function wipe() {
  await db.project.deleteMany();
}

export default withLogging(wipe); // Noncompliant

// not a wrapper: no Server Action the rule can see
export const seeded = runNow(async () => {
  await db.project.deleteMany();
});
