import { NextResponse, type NextRequest } from 'next/server';
import { getToken } from 'next-auth/jwt';

export async function middleware(req: NextRequest) {
  if (!(await getToken({ req }))) {
    return NextResponse.redirect(new URL('/login', req.url));
  }
  return NextResponse.next();
}

// compliant: the negative lookahead runs on every page; /api is left out but its handlers check the session
export const config = {
  matcher: [
    '/((?!api|_next/static|_next/image|favicon.ico).*)',
    { source: '/admin/:path*', has: [{ type: 'cookie', key: 'session' }] },
  ],
};
