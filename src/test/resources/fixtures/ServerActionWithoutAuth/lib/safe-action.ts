import { createSafeActionClient } from 'next-safe-action';
import { createServerActionProcedure } from 'zsa';
import { getServerSession } from 'next-auth';
import { loadContext } from './session';

// not a "use server" module: clients and procedures, not endpoints
export const actionClient = createSafeActionClient().use(async ({ next }) => next({ ctx: { at: Date.now() } }));

export const authActionClient = actionClient.use(async ({ next }) => {
  const session = await getServerSession();
  if (!session) {
    throw new Error('Unauthorized');
  }
  return next({ ctx: { session } });
});

async function requireMember({ next }) {
  const session = await loadContext();
  return next({ ctx: { session } });
}

export const memberClient = actionClient.use(requireMember);

export const authedProcedure = createServerActionProcedure().handler(async () => {
  return { session: await loadContext() };
});

export const ownerProcedure = createServerActionProcedure(authedProcedure).handler(async ({ ctx }) => ctx);
