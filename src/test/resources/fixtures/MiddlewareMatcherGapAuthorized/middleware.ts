import NextAuth from 'next-auth';
import { authConfig } from './auth.config';

export default NextAuth(authConfig).auth;

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|.*\\.png$).*)'], // Noncompliant {{matcher and authorized callback leave out 2 routes that check nothing themselves (/account/billing, /settings): protect them in the callback}}
};
