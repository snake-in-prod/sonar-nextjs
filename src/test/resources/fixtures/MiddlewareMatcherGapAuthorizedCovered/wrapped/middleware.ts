import { NextResponse } from 'next/server';
import { auth } from './auth';

// compliant: with a function of its own, the callback's false no longer redirects; the function decides
export default auth((req) => {
  if (!req.auth) return NextResponse.redirect(new URL('/login', req.url));
});
