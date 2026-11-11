import fs from 'node:fs'; // Noncompliant {{Remove this import or use the Node.js runtime: the Node.js module "node:fs" does not run on the Edge runtime.}}
import jwt from 'jsonwebtoken'; // Noncompliant
import { neon } from '@neondatabase/serverless';
import type { Pool } from 'pg';

export const runtime = 'edge';

export async function GET() {
  const pool: Pool | null = null;
  return new Response(fs.readFileSync('font.ttf').length + jwt.sign({}, 'k') + neon(''));
}
