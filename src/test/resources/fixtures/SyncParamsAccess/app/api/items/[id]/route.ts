import { db } from '../../../../lib/db';

export async function GET(req: Request, { params }: { params: { id: string } }) {
  const item = await db.find(params['id']); // Noncompliant
  return Response.json(item);
}

export const DELETE = async (req: Request, ctx: { params: { id: string } }) => {
  await db.remove(ctx.params.id); // Noncompliant
  return new Response(null, { status: 204 });
};
