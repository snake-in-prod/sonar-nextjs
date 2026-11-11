'use server';

import { db } from './db';

export async function saveUser() {
  await db.query('update');
}
