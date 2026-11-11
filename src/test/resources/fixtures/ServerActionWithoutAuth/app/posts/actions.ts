'use server';

import { cookies } from 'next/headers';
import { revalidatePath, revalidateTag } from 'next/cache';
import { sql } from '@vercel/postgres';
import { auth } from '@/auth';
import { db } from '@/lib/db';
import { orm, posts } from '@/lib/orm';
import { loadContext, slugify } from '@/lib/session';
import { ensureContext } from '@/lib/guards';
import { mystery } from '@/lib/missing';
import { purgeAll } from '@/lib/writer';

export async function deletePost(id: string) { // Noncompliant {{Check the session in "deletePost"}}
  await db.post.delete({ where: { id } });
}

export async function createPost(title: string) { // Noncompliant
  await orm.insert(posts).values({ title });
}

export const publishPost = async (id: string) => { // Noncompliant
  await fetch('https://api.example.com/publish', { method: 'POST', body: JSON.stringify({ id }) });
};

export async function clearPosts() { // Noncompliant
  await purgeAll();
  revalidatePath('/posts');
}

// compliant: revalidation alone refreshes the cache, it changes no data
export async function refreshPosts(slug: string) {
  revalidatePath(`/posts/${slug}`);
  revalidateTag('posts', 'max');
}

export async function wipeComments(postId: string) { // Noncompliant
  await sql`DELETE FROM comments WHERE post_id = ${postId}`;
}

export async function renamePost(id: string, title: string) { // Noncompliant
  const slug = slugify(title);
  await db.post.update({ where: { id }, data: { title, slug } });
}

// compliant: the session is checked here
export async function updatePost(id: string, title: string) {
  const session = await auth();
  if (!session) {
    throw new Error('Unauthorized');
  }
  await db.post.update({ where: { id, authorId: session.user.id }, data: { title } });
}

// compliant: a project helper checks the session
export async function archivePost(id: string) {
  await loadContext();
  await db.post.update({ where: { id }, data: { archived: true } });
}

// compliant: the same helper, re-exported under another name
export async function hidePost(id: string) {
  await ensureContext();
  await db.post.update({ where: { id }, data: { hidden: true } });
}

// compliant: reads only
export async function listPosts() {
  return db.post.findMany();
}

// compliant: the backend checks the credentials it receives
export async function notifyBackend(id: string) {
  const token = process.env.BACKEND_TOKEN;
  await fetch('https://api.example.com/notify', { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
}

// compliant: a helper the model cannot see may check the session
export async function pinPost(id: string) {
  await mystery();
  await db.post.update({ where: { id }, data: { pinned: true } });
}

// compliant: a custom session cookie
export async function likePost(id: string) {
  const user = (await cookies()).get('uid')?.value;
  await db.like.create({ data: { postId: id, userId: user } });
}

// compliant: public by design
export async function subscribeToNewsletter(email: string) {
  await db.subscriber.create({ data: { email } });
}

// compliant: not exported, not an endpoint
async function purgeDrafts() {
  await db.post.deleteMany({ where: { draft: true } });
}
