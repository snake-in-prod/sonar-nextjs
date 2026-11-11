import { db } from '../../../lib/db'; // Noncompliant {{Remove this import or use the Node.js runtime: it loads PrismaClient without a driver adapter or Accelerate, which does not run on the Edge runtime, through lib/db.ts.}}
import { edgeDb } from '../../../lib/edge-db';

export const runtime = 'edge';

export async function GET() {
  return Response.json([await db.user.findMany(), await edgeDb.user.findMany()]);
}
