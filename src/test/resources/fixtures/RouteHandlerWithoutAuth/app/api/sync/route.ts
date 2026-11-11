import { db } from '@/lib/db';

// compliant: a bearer token from the environment
export async function POST(req: Request) {
  if (req.headers.get('authorization') !== `Bearer ${process.env.SYNC_SECRET}`) {
    return new Response('Unauthorized', { status: 401 });
  }
  await db.item.createMany({ data: await req.json() });
  return new Response(null, { status: 204 });
}

// compliant: a token in the query string, checked against the environment
export async function DELETE(req: Request) {
  const key = new URL(req.url).searchParams.get('key');
  if (key !== process.env.ADMIN_KEY) {
    return new Response('Unauthorized', { status: 401 });
  }
  await db.item.deleteMany();
  return new Response(null, { status: 204 });
}
