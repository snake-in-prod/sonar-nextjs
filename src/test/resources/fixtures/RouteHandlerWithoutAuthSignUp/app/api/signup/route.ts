import { PrismaClient } from '@prisma/client';

const db = new PrismaClient();

// compliant: signing up is public; its presence tells the app has users
export async function POST(req: Request) {
  return Response.json(await db.user.create({ data: await req.json() }));
}
