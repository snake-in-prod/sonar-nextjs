import { getServerSession } from 'next-auth';

export function unused() {}

// the app checks a session somewhere: its unguarded actions and route handlers count as unprotected
export async function viewer() {
  return getServerSession();
}
