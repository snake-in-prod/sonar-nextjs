'use client';

import { track } from '../lib/analytics';
import { shared } from '../lib/shared';
import { baseUrl } from '../lib/base-url';
import { env } from '../lib/env';

export default function Client({ items }: { items: unknown }) {
  const a = process.env.API_URL; // Noncompliant {{process.env.API_URL is undefined in the browser}}
  const b = process.env['STRIPE_KEY']; // Noncompliant {{STRIPE_KEY}}
  if (process.env.DEBUG && process.env.VERBOSE) { // Noncompliant 2
    console.log(items);
  }
  const c = process.env.NEXT_PUBLIC_API_URL;
  const d = process.env.NODE_ENV === 'development';
  const e = process.env.CUSTOM_KEY;
  const f = process.env.BUILD_LABEL;
  const g = process.env.NEXT_RUNTIME;
  const h = process.env.__NEXT_ROUTER_BASEPATH;
  // compliant: falls back to the public name the browser gets
  const i = process.env.NEXT_PUBLIC_LOG_LEVEL ?? process.env.LOG_LEVEL;
  const j = Boolean(process.env.PRICE_ID || process.env.NEXT_PUBLIC_PRICE_ID);
  track();
  return <p>{a}{b}{c}{d}{e}{f}{g}{h}{i}{j}{shared}{baseUrl}{env.NEXT_PUBLIC_X}</p>;
}
