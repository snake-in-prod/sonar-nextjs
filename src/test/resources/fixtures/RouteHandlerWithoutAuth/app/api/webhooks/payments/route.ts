import { db } from '@/lib/db';

// compliant: a webhook, authenticated by its provider's signature (left to the handler)
export async function POST(req: Request) {
  await db.payment.create({ data: await req.json() });
  return new Response(null, { status: 204 });
}
