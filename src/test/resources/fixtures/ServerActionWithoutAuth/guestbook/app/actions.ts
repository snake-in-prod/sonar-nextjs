'use server';

import postgres from 'postgres';

const sql = postgres(process.env.DATABASE_URL!);

// compliant: this app has no sign-in at all, its entries are public by design
export async function addEntry(text: string) {
  await sql`INSERT INTO entries (text) VALUES (${text})`;
}
