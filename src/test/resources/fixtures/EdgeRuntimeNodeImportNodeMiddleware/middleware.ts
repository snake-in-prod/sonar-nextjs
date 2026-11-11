import { NextResponse } from 'next/server';
import { readFileSync } from 'fs';

export function middleware() {
  return readFileSync('flags.json').length ? NextResponse.next() : NextResponse.json({});
}

export const config: { runtime: string; matcher: string[] } = {
  matcher: ['/((?!_next).*)'],
  runtime: 'nodejs',
};
