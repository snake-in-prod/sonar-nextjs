'use server';

import { eq } from 'drizzle-orm';
import { orm, posts } from '@/lib/orm';
import { db } from '@/lib/db';

declare const stripe: { accounts: { retrieve(id: string): Promise<{ id: string; ready: boolean }> } };

// compliant: a getter that only mirrors the account status it fetched into the local record
export async function getPayoutStatus(input: { storeId: string }) {
  const store = await db.store.findFirst({ where: { id: input.storeId } });
  const account = await stripe.accounts.retrieve(store.accountId);
  if (account.ready && !store.ready) {
    await orm.transaction(async (tx) => {
      await tx.update(posts).set({ title: account.id }).where(eq(posts.title, input.storeId));
    });
    await db.store.update({ where: { id: input.storeId }, data: { ready: account.ready } });
  }
  return account;
}

// the record created holds the caller's value
export async function getOrCreateProfile(userId: string) { // Noncompliant
  const profile = await db.profile.findUnique({ where: { userId } });
  return profile ?? db.profile.create({ data: { userId } });
}

// a value built from the caller's one is the caller's too
export async function fetchLabel(input: { name: string }) { // Noncompliant
  const label = input.name.trim();
  await db.label.update({ where: { id: 1 }, data: { label } });
}

// a delete is never a sync
export async function loadAndPrune(input: { id: string }) { // Noncompliant
  await db.cache.delete({ where: { id: input.id } });
  return db.cache.findMany();
}

// a record lookup by id is no session check
export async function promoteUser(userId: string) { // Noncompliant
  const user = await getUser(userId);
  await db.user.update({ where: { id: user.id }, data: { role: 'admin' } });
}

declare function getUser(id: string): Promise<{ id: string }>;
