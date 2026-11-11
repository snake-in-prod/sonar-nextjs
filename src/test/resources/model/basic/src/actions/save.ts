"use server";

import { db } from '@/lib/db';

export async function save(n: number) {
  await db.counter.update({ data: { n } });
}

export const remove = async (id: string) => {
  await db.counter.delete({ where: { id } });
};

const helper = 1;
