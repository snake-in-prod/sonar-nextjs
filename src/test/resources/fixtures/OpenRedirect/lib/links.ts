import { NextResponse } from 'next/server';

export function linkRedirect(params?: { path?: string }) {
  // compliant: a helper argument named params, not the route params
  return NextResponse.redirect(new URL(params?.path ?? '/accounts', 'https://example.com'));
}
