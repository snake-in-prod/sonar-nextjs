import { drizzle } from 'drizzle-orm/node-postgres';
import { pgTable, text } from 'drizzle-orm/pg-core';

export const posts = pgTable('posts', { title: text('title') });

export const orm = drizzle(process.env.DATABASE_URL!);
