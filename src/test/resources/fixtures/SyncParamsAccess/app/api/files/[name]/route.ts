export async function GET(req: Request, context: { params: Promise<{ name: string }> }) {
  const { name } = await context.params;
  return new Response(name);
}

export async function POST(req: Request, { params }: { params: Promise<{ name: string }> }) {
  const url = new URL(req.url);
  const n = (await params).name;
  return new Response(url.searchParams.get('x') + n);
}
