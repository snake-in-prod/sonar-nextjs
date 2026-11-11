import { db } from './db';

// not a "use server" module: these functions are not endpoints
export async function purgeAll() {
  await db.post.deleteMany();
}

export async function addLine(variantId: string) {
  await db.line.create({ data: { variantId } });
}
