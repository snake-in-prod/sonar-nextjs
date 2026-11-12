'use server';

import { redirect } from 'next/navigation';
import { createSafeActionClient } from 'next-safe-action';
import { z } from 'zod';
import { service, withSchema } from '../../lib/with-schema';

const client = createSafeActionClient();
const Local = z.object({ next: z.string().startsWith('/') });
const Free = z.object({ next: z.string().optional() });

export const goFree = client.schema(Free).action(async ({ parsedInput }) => {
  redirect(parsedInput.next); // Noncompliant
});

// clientInput is what the client sent, before the schema
export const goRaw = client.schema(Local).action(async ({ clientInput }) => {
  redirect(clientInput.next); // Noncompliant
});

export const wrappedFree = withSchema(Free, async (data) => {
  redirect(data.next); // Noncompliant
});

export const servedFree = service({ schema: Free }, async ({ input }) => {
  redirect(input.next); // Noncompliant
});

// compliant: the schemas accept only local paths
export const go = client.schema(Local).action(async ({ parsedInput: { next } }) => {
  redirect(next);
});

export const goObject = client.inputSchema(Local).action(async ({ parsedInput }) => {
  redirect(parsedInput.next);
});

export const wrapped = withSchema(Local, async (data) => {
  redirect(data.next);
});

export const served = service({ schema: Local }, async ({ input }) => {
  redirect(input.next);
});
