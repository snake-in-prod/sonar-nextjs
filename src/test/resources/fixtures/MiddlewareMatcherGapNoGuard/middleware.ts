import { NextResponse, type NextRequest } from 'next/server';
import { getToken } from 'next-auth/jwt';

// compliant: sends signed-in users away from the sign-in page, guards nothing
export async function middleware(req: NextRequest) {
  if (await getToken({ req })) {
    return NextResponse.redirect(new URL('/', req.url));
  }
  return NextResponse.next();
}

export const config = { matcher: ['/login'] };
