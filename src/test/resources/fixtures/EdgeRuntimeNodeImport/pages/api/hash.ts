import bcrypt from 'bcrypt'; // Noncompliant

export const config = { runtime: 'edge' };

export default async function handler(req: Request) {
  return new Response(await bcrypt.hash(await req.text(), 10));
}
