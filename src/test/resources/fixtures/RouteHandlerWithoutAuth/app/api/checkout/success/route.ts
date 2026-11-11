import { db } from '@/lib/db';

// compliant: a payment success return, its data comes from the payment provider
export async function POST(req: Request) {
  const { sessionId } = await req.json();
  await db.purchase.create({ data: { sessionId } });
  return new Response(null, { status: 204 });
}
