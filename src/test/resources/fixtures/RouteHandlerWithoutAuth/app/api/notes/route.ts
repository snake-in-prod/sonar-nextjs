import { db } from '@/lib/db';
import { loadContext } from '@/lib/session';
import { mystery } from '@/lib/missing';

// compliant: a project helper checks the session
export async function POST(req: Request) {
  const ctx = await loadContext();
  await db.note.create({ data: { text: await req.text(), ownerId: ctx.user.id } });
  return new Response(null, { status: 201 });
}

// compliant: a helper the model cannot see may check the caller
export async function DELETE() {
  await mystery();
  await db.note.deleteMany();
  return new Response(null, { status: 204 });
}

// compliant: reads only
export async function PUT(req: Request) {
  const notes = await db.note.findMany({ where: { text: await req.text() } });
  return Response.json(notes);
}
