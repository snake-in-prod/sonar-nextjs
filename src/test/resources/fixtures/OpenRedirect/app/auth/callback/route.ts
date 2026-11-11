import { NextResponse, type NextRequest } from 'next/server';

export async function GET(request: NextRequest) {
  const url = request.nextUrl.searchParams.get('url');
  if (!url) {
    return NextResponse.redirect(new URL(request.nextUrl.searchParams.get('to') ?? '/', request.url)); // Noncompliant
  }
  return NextResponse.redirect(url); // Noncompliant
}

export async function POST(request: NextRequest) {
  const { searchParams, origin } = new URL(request.url);
  const next = searchParams.get('next') ?? '/';
  // compliant: the origin comes first, the value can only add a path
  return NextResponse.redirect(`${origin}${next}`);
}

export async function PUT(request: NextRequest) {
  const target = new URL(request.nextUrl.searchParams.get('to') ?? '/', request.url);
  // compliant: same-origin check
  if (target.origin !== request.nextUrl.origin) {
    return NextResponse.redirect(new URL('/', request.url));
  }
  return NextResponse.redirect(target);
}
