import 'server-only';
import { PrismaClient } from '@prisma/client';
import { url } from './helpers.js';

export const db = new PrismaClient({ datasourceUrl: url ?? process.env.DATABASE_URL ?? process.env['DB_FALLBACK'] });
