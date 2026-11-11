import { db } from '@/lib/db';
import loadProfile from '@/lib/profile';

// compliant: the helper checks the session (its auth options mention the database client)
export async function POST(req: Request) {
  const user = await loadProfile();
  if (!user) {
    return new Response('Unauthorized', { status: 401 });
  }
  await db.venue.update({ where: { id: 1 }, data: await req.json() });
  return new Response(null, { status: 204 });
}
