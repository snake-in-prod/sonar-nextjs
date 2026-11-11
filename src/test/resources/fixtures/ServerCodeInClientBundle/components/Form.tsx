'use client';

import type { Pool } from 'pg';
import { db } from '../lib/db';
import path from 'path';
import { createHash } from 'crypto';
import { saveUser } from '../lib/actions';
import { config } from '../lib/config';
import { baseUrl } from '../lib/url';
import Button from './Button';
import { env } from '../lib/env';
import { siteName } from '../lib/settings';
import dynamic from 'next/dynamic';
import { Prisma } from '@prisma/client';
import { Row } from '../lib/users';
import { join } from 'node:path';
import { format } from '../lib/format';

const fs = require('fs');
const Lazy = dynamic(() => import('../lib/users'));
type Db = typeof db;
type Post = Prisma.PostGetPayload<{}>;

export default function Form({ pool, rows }: { pool: Pool; rows: Array<Row> }) {
  const id = createHash('sha256').update(path.join('a', join('b', format(' c ')))).digest('hex');
  return <form action={saveUser}><Button />{id}{config.api}{baseUrl}{env.NEXT_PUBLIC_X}{siteName}<Lazy />{fs ? 1 : 0}</form>;
}
