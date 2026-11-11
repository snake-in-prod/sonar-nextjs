import { db } from '@/lib/db';

async function handler(req: Request) {
  await db.order.updateMany({ data: await req.json() });
  return new Response(null, { status: 204 });
}

export { handler as PATCH }; // Noncompliant
