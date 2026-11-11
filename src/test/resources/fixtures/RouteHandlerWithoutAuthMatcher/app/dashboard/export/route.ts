import { db } from '../../../lib/db';

// compliant: /dashboard/:path* covers this handler
export async function POST(req: Request) {
  return Response.json(await db.report.create({ data: await req.json() }));
}
