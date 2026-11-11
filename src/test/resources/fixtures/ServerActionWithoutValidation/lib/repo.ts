import { db } from './db';

// not a Server Action: its callers are server code
export async function removeAll(id: string) {
  await db.post.deleteMany({ where: { authorId: id } });
}
