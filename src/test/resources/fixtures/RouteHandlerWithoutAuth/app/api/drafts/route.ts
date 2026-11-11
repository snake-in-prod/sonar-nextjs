import { Note } from '@/lib/models';
import prisma from '@/lib/prisma';

export async function POST(req: Request) { // Noncompliant
  const note = new Note(await req.json());
  await note.save();
  return new Response(null, { status: 201 });
}

export async function DELETE(req: Request) { // Noncompliant
  await prisma.draft.delete({ where: { id: await req.text() } });
  return new Response(null, { status: 204 });
}
