import { NextResponse } from 'next/server';

export default function handler(req: { query: { next?: string } }) {
  return NextResponse.redirect(req.query.next ?? '/'); // Noncompliant
}
