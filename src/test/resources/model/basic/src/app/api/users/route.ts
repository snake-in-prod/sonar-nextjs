import { db } from '../../../lib/db';

export async function POST(req: Request) {
  return Response.json(await db.user.create({ data: await req.json() }));
}
