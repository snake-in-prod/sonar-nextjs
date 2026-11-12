import { NextResponse, type NextRequest } from 'next/server';

export async function POST(request: Request) {
  const { redirectTo } = await request.json();
  return NextResponse.redirect(redirectTo); // Noncompliant
}

export async function PUT(req: NextRequest) {
  const body = await req.json();
  return NextResponse.redirect(new URL(body.next, req.url)); // Noncompliant
}

export async function PATCH(req: NextRequest) {
  const form = await req.formData();
  return NextResponse.redirect(form.get('next') as string); // Noncompliant
}

export async function DELETE(req: NextRequest) {
  const { next } = await req.json();
  // compliant: only a local path is accepted
  if (typeof next !== 'string' || !next.startsWith('/') || next.startsWith('//')) {
    return NextResponse.redirect(new URL('/', req.url));
  }
  return NextResponse.redirect(new URL(next, req.url));
}
