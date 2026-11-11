import { auth } from '@/auth';
import { db } from '@/lib/db';

export async function PUT(req: Request, { params }: { params: Promise<{ id: string }> }) { // Noncompliant
  const { id } = await params;
  await db.item.update({ where: { id }, data: await req.json() });
  return new Response(null, { status: 204 });
}

// compliant: the session is checked
export async function PATCH(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const session = await auth();
  if (!session) {
    return new Response('Unauthorized', { status: 401 });
  }
  const { id } = await params;
  await db.item.update({ where: { id }, data: await req.json() });
  return new Response(null, { status: 204 });
}
