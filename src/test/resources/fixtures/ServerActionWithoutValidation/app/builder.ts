'use server';

import { z } from 'zod';
import { createSafeActionClient } from 'next-safe-action';
import { createServerAction } from 'zsa';
import { db } from '@/lib/db';

const client = createSafeActionClient();
const todoSchema = z.object({ id: z.string(), title: z.string() });

// compliant: the schema step validates parsedInput and input
export const renameTodo = client.schema(todoSchema).action(async ({ parsedInput: { id, title } }) => {
  await db.todo.update({ where: { id }, data: { title } });
});

export const retitleTodo = createServerAction().input(todoSchema).handler(async ({ input }) => {
  await db.todo.update({ where: { id: input.id }, data: { title: input.title } });
});

export const moveTodo = client.action(async ({ parsedInput }) => {
  await db.todo.update({ where: { id: 1 }, data: parsedInput }); // Noncompliant {{Validate "parsedInput"}}
});

export const tagTodo = client.inputSchema(todoSchema).action(async ({ clientInput }) => {
  await db.todo.update({ where: { id: 1 }, data: clientInput }); // Noncompliant
});

// compliant: ctx comes from the server
export const touchTodo = client.action(async ({ ctx }) => {
  await db.todo.update({ where: { id: ctx.id }, data: {} });
});
