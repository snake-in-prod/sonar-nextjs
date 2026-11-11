import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { verify } from './lib/token'; // Noncompliant {{Remove this import or use the Node.js runtime: it loads the Node.js module "crypto", which does not run on the Edge runtime, through lib/token.ts.}}
import type { User } from './lib/db';
import { isPublic } from './lib/paths';

export function middleware(req: NextRequest) {
  const user: User | null = null;
  if (isPublic(req.nextUrl.pathname) || verify(req.cookies.get('t')?.value ?? '')) {
    return NextResponse.next();
  }
  return NextResponse.redirect(new URL('/login', req.url));
}
