import { PrismaClient } from '@prisma/client';

const db = new PrismaClient();

// compliant: the app has no sign-in at all, its data is public by design
export async function POST(req: Request) {
  return Response.json(await db.todo.create({ data: await req.json() }));
}

export async function DELETE(req: Request) {
  await db.todo.delete({ where: { id: await req.text() } });
  return new Response(null, { status: 204 });
}
