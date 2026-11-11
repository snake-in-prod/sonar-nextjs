'use client';

import { readFileSync } from 'fs'; // Noncompliant {{the Node.js module "fs"}}
import { spawn } from 'node:child_process'; // Noncompliant
import { createHmac } from 'crypto'; // Noncompliant {{Node.js crypto (createHmac)}}
import * as nodeCrypto from 'crypto'; // Noncompliant {{Node.js crypto (timingSafeEqual)}}
import { Pool } from 'pg'; // Noncompliant {{the database client "pg"}}
import { PrismaClient } from '@prisma/client'; // Noncompliant {{PrismaClient}}
import 'server-only'; // Noncompliant {{"server-only"}}
import { db } from '../lib/db'; // Noncompliant {{through lib/db.ts}}
import { getUser } from '../lib/users'; // Noncompliant {{the database client "pg" into the browser bundle through lib/db.ts}}
import { stripe } from '../lib/stripe'; // Noncompliant {{STRIPE_SECRET_KEY}}
import { secretThing } from '../lib/guarded'; // Noncompliant {{"server-only" into the browser bundle through lib/guarded.ts}}
import { Widget } from '../lib/barrel'; // Noncompliant
import { sign } from '../lib/sign'; // Noncompliant {{Node.js crypto (createHmac) into the browser bundle through lib/sign.ts}}

export default function Panel() {
  const pool = new Pool();
  const client = new PrismaClient();
  readFileSync('x');
  spawn('ls');
  createHmac('sha256', 'k');
  nodeCrypto.timingSafeEqual(a, b);
  sign('x');
  return <div>{String(db)}{getUser()}{stripe.id}{secretThing}<Widget /></div>;
}
