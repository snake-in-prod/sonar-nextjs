export { default } from 'next-auth/middleware';

export const config = { matcher: ['/dashboard/:path*', '/((?!api|_next/static|favicon.ico).*)'] };
