import { getServerSession } from 'next-auth';

export async function loadContext() {
  const session = await getServerSession();
  if (!session) {
    throw new Error('Unauthorized');
  }
  return session;
}
