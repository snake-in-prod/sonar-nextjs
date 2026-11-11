import { db } from '@/lib/db';

export async function GET() {
  return Response.json(await db.item.findMany());
}

export async function POST(req: Request) { // Noncompliant {{Check the session or a token in this POST handler}}
  const body = await req.json();
  return Response.json(await db.item.create({ data: body }));
}

export const DELETE = async () => { // Noncompliant
  await db.item.deleteMany();
  return new Response(null, { status: 204 });
};
