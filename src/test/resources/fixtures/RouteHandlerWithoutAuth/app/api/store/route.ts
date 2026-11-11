import { db } from '@/lib/db';

async function authenticateRequest(req: Request) {
  const header = req.headers.get('authorization');
  return { authenticated: header === `Bearer ${process.env.STORE_KEY}` };
}

// compliant: a local helper checks the bearer token
export async function POST(req: Request) {
  const auth = await authenticateRequest(req);
  if (!auth.authenticated) {
    return new Response('Unauthorized', { status: 401 });
  }
  await db.trade.createMany({ data: await req.json() });
  return new Response(null, { status: 204 });
}
