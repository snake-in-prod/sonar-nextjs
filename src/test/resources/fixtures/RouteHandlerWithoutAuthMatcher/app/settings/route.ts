import { db } from '../../lib/db';

// compliant: the negative lookahead leaves out api, not settings
export async function PUT(req: Request) {
  return Response.json(await db.setting.update({ where: { id: 1 }, data: await req.json() }));
}
