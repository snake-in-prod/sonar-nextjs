import { PrismaClient } from '@prisma/client';

const db = new PrismaClient();

// the user id comes from the caller: anyone can like for anyone
export async function DELETE(req: Request) { // Noncompliant
  const url = new URL(req.url);
  await db.like.deleteMany({ where: { userId: url.searchParams.get('user_id') ?? '' } });
  return new Response(null, { status: 204 });
}
