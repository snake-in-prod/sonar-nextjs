'use server';

import Hashids from 'hashids';
import { signIn, unstable_update } from '@/lib/auth';
import { db } from '@/lib/db';

const codeHashids = new Hashids('salt');

// compliant: query results filtered in memory with the input, not a database call
export async function removeItems(input: { ids: string[] }) {
  const cart = await db.cart.findFirst({ where: { id: 1 } });
  const items = cart.items.filter((item) => !input.ids.includes(item.id));
  await db.cart.update({ where: { id: 1 }, data: { items } });
}

// rows read with an input are not inputs themselves
export async function repriceTrades(account: string) {
  const trades = await db.trade.findMany({ where: { account } }); // Noncompliant
  await db.$transaction(trades.map((t) => db.trade.update({ where: { id: t.id }, data: { price: 1 } })));
}

// compliant: a hashid decodes to numbers or nothing
export async function removeCode(input: { id: string }) {
  const [id] = codeHashids.decode(input.id);
  await db.code.delete({ where: { id } });
}

// compliant: next-auth calls, its credentials provider checks what it gets
export async function login(formData: FormData) {
  await signIn('credentials', formData);
}

export async function rename(name: string) {
  await unstable_update({ user: { name } });
  await db.user.update({ where: { id: 1 }, data: { name } }); // Noncompliant
}
