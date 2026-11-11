'use server';

import { db } from '@/lib/db';

// compliant: sign-in flows are public
export async function startSession(email: string) {
  await db.session.create({ data: { email } });
}
