'use server';

import { redirect } from 'next/navigation';
import { revalidatePath } from 'next/cache';
import { z } from 'zod';
import { auth } from '@/auth';
import { db } from '@/lib/db';

const ROLES = ['reader', 'editor'];
const PostSchema = z.object({ title: z.string().max(100) });

export async function setRole(formData: FormData) {
  await db.user.update({ where: { id: 1 }, data: { role: formData.get('role') } }); // Noncompliant {{Validate "formData"}}
  revalidatePath('/users');
}

export async function removePost(id: string) {
  await db.post.delete({ where: { id } }); // Noncompliant
}

// each input once per action, at its first use; different inputs apart
export async function removeProject(id: string, ownerId: string) {
  await db.task.deleteMany({ where: { projectId: id } }); // Noncompliant
  await db.member.deleteMany({ where: { projectId: id } });
  await db.project.delete({ where: { id, ownerId } }); // Noncompliant {{Validate "ownerId"}}
  await db.log.create({ data: { text: `removed ${id}` } });
}

export async function saveContact(formData: FormData) {
  const name = formData.get('name');
  const email = formData.get('email');
  await db.contact.create({ data: { name } }); // Noncompliant {{Validate "name"}}
  await db.contact.update({ where: { email }, data: { name } }); // Noncompliant {{Validate "email"}}
}

// a value built from an input is that input
export async function saveDraft(data: Draft) {
  const draft = { title: data.title, body: data.body };
  await db.draft.update({ where: { id: 1 }, data }); // Noncompliant
  await db.history.create({ data: draft });
}

export async function importPost(formData: FormData) {
  const data = Object.fromEntries(formData);
  await db.post.create({ data }); // Noncompliant
}

export async function goNext(formData: FormData) {
  redirect(formData.get('next') as string); // Noncompliant {{before redirecting}}
}

export async function findByName(name: string) {
  return db.$queryRawUnsafe(`SELECT * FROM users WHERE name = '${name}'`); // Noncompliant
}

export async function saveProfile(input: { bio: string }) {
  const session = await auth();
  if (!session) {
    throw new Error('Unauthorized');
  }
  await db.profile.update({ where: { userId: session.user.id }, data: input }); // Noncompliant
}

// compliant: a schema parse
export async function createPost(formData: FormData) {
  const post = PostSchema.parse(Object.fromEntries(formData));
  await db.post.create({ data: post });
}

// compliant: safeParse
export async function editPost(raw: unknown) {
  const result = PostSchema.safeParse(raw);
  if (!result.success) {
    return { error: 'invalid' };
  }
  await db.post.update({ where: { id: 1 }, data: result.data });
}

// compliant: explicit checks
export async function renamePost(formData: FormData) {
  const title = formData.get('title');
  if (typeof title !== 'string' || title.length > 100) {
    return;
  }
  await db.post.update({ where: { id: 1 }, data: { title } });
}

// compliant: an allow-list
export async function grantRole(role: string) {
  return ROLES.includes(role) ? db.user.update({ where: { id: 1 }, data: { role } }) : null;
}

// compliant: coerced to a number
export async function deleteComment(formData: FormData) {
  const id = Number(formData.get('id'));
  await db.comment.delete({ where: { id } });
}

// compliant: only server values reach the database
export async function touch(note: string) {
  const session = await auth();
  console.log(note);
  await db.visit.create({ data: { userId: session?.user.id, at: new Date() } });
}

// compliant: a fixed path prefix, not a redirect to anywhere
export async function openPost(id: string) {
  redirect(`/posts/${id}`);
}
