'use server';

import { z } from 'zod';
import { db } from '@/lib/db';
import { validatedAction, withUser } from '@/lib/wrappers';

const noteSchema = z.object({ title: z.string() });

// compliant: the wrapper parses the input
export const addNote = validatedAction(noteSchema, async (data, formData) => {
  await db.note.create({ data: { title: data.title, body: formData.get('body') } });
});

// the user comes from the wrapper, the note id from the caller
export const removeNote = withUser(async (user, id: string) => {
  await db.note.delete({ where: { id, ownerId: user.id } }); // Noncompliant {{Validate "id"}}
});

// compliant: only the user, which the wrapper adds
export const clearNotes = withUser(async (user) => {
  await db.note.deleteMany({ where: { ownerId: user.id } });
});
