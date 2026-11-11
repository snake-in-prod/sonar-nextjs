import { getServerSession } from 'next-auth';

// a project helper with a neutral name: the rule follows it to the session check
export async function loadContext() {
  const session = await getServerSession();
  if (!session) {
    throw new Error('Unauthorized');
  }
  return session;
}

export function slugify(s: string) {
  return s.toLowerCase().replace(/\s+/g, '-');
}
