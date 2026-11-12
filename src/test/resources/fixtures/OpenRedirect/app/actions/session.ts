'use server';

import { redirect } from 'next/navigation';
import { createSafeActionClient } from 'next-safe-action';
import { z } from 'zod';
import { auth, signInWithPassword } from '@/lib/auth';

const ALLOWED = ['/dashboard', '/settings'];
const client = createSafeActionClient();

export async function signIn(values: { email: string; password: string; callbackUrl?: string }) {
  await signInWithPassword(values.email, values.password);
  redirect(values.callbackUrl ?? '/'); // Noncompliant
}

export async function signUp({ email, redirectTo }: { email: string; redirectTo: string }) {
  await signInWithPassword(email, '');
  redirect(redirectTo); // Noncompliant
}

export async function join(input: { next?: string }) {
  const { next } = input;
  redirect(next || '/'); // Noncompliant
}

export async function leave(prevState, data) {
  redirect(data.get('returnTo')); // Noncompliant
}

export const enter = client.schema(z.object({ callbackUrl: z.string() })).action(async ({ parsedInput: { callbackUrl } }) => {
  redirect(callbackUrl); // Noncompliant
});

export const reenter = client.schema(z.object({ next: z.string() })).action(async ({ parsedInput, ctx }) => {
  redirect(parsedInput.next); // Noncompliant
  redirect(ctx.home);
});

// compliant: a plain parameter is no form field, the callers pass fixed paths
export async function go(path: string) {
  redirect(path);
}

// compliant: only a local path is accepted
export async function back(values: { callbackUrl?: string }) {
  const target = values.callbackUrl ?? '/';
  if (!target.startsWith('/') || target.startsWith('//')) {
    redirect('/');
  }
  redirect(target);
}

// compliant: allow-list
export async function open({ redirectTo }: { redirectTo: string }) {
  redirect(ALLOWED.includes(redirectTo) ? redirectTo : '/');
}

// compliant: same origin
export async function resume(data: { next: string }) {
  const url = new URL(data.next, process.env.APP_URL);
  if (url.origin !== process.env.APP_URL) {
    redirect('/');
  }
  redirect(url.toString());
}

// compliant: the user comes from the session, not the client
export async function home() {
  const user = await auth();
  redirect(user.homeUrl);
}
