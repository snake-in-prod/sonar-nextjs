import { db } from '../../../lib/db';

// the public site settings: read only, nothing to protect
export async function GET() {
  return Response.json(await db.user.findFirst());
}
