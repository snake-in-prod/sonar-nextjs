'use server';

import { z } from 'zod';
import { createServerAction } from 'zsa';
import { db } from '@/lib/db';
import { actionClient, authActionClient, memberClient, authedProcedure, ownerProcedure } from '@/lib/safe-action';

const idSchema = z.object({ id: z.string() });

// the client's middleware never checks the session
export const removeDraft = actionClient // Noncompliant {{Check the session in "removeDraft"}}
  .inputSchema(idSchema)
  .action(async ({ parsedInput: { id } }) => {
    await db.draft.delete({ where: { id } });
  });

export const purgeDrafts = createServerAction().handler(async () => { // Noncompliant
  await db.draft.deleteMany();
});

// compliant: the client's middleware checks the session
export const archiveDraft = authActionClient
  .metadata({ name: 'archiveDraft' })
  .schema(idSchema)
  .action(async ({ parsedInput: { id }, ctx }) => {
    await db.draft.update({ where: { id }, data: { archived: true } });
  });

// compliant: a named middleware followed to its session check
export const pinDraft = memberClient.inputSchema(idSchema).action(async ({ parsedInput }) => {
  await db.draft.update({ where: { id: parsedInput.id }, data: { pinned: true } });
});

// compliant: the procedure's handler checks the session, directly or through the procedure it is built on
export const publishDraft = authedProcedure
  .createServerAction()
  .input(idSchema)
  .handler(async ({ input }) => {
    await db.draft.update({ where: { id: input.id }, data: { published: true } });
  });

export const transferDraft = ownerProcedure.createServerAction().input(idSchema).handler(async ({ input }) => {
  await db.draft.update({ where: { id: input.id }, data: { owner: null } });
});

// compliant: a client built here with a session check
const localClient = actionClient.use(async ({ next }) => next({ ctx: await loadUser() }));

async function loadUser() {
  return getCurrentUser();
}

export const lockDraft = localClient.action(async () => {
  await db.draft.updateMany({ data: { locked: true } });
});
