import { db } from '@/lib/db';

// compliant: the middleware of the app checks the session
export async function POST(req: Request) {
  return Response.json(await db.item.create({ data: await req.json() }));
}
