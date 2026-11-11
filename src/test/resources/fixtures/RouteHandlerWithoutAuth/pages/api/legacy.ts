import { db } from '../../lib/db';

// compliant: a Pages Router API route, not an App Router route handler
export default async function handler(req: any, res: any) {
  await db.item.deleteMany();
  res.status(204).end();
}
