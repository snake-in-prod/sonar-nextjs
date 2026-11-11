import { db } from '@/lib/db';

// compliant: one-time passwords are asked for before signing in
export async function POST(req: Request) {
  const { email } = await req.json();
  await db.otp.create({ data: { email, code: String(Date.now() % 1000000) } });
  return new Response(null, { status: 204 });
}
