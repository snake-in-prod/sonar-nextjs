import { db } from '../../../lib/db';

// the middleware checks the session, but its matcher leaves /api out
export async function POST(req: Request) { // Noncompliant
  return Response.json(await db.item.create({ data: await req.json() }));
}
