import { cookies, headers } from 'next/headers';
import { unstable_cache } from 'next/cache';
import { auth } from '@/auth';
import { getUser } from '@/lib/users';

export async function getCart() {
  'use cache';
  const id = (await cookies()).get('cart')?.value; // Noncompliant {{"use cache"}}
  return id;
}

export const getPlan = unstable_cache(async () => { // Noncompliant
  const session = await auth();
  return session?.user?.plan;
}, ['plan']);

export async function getStrings(locale: string) {
  'use cache';
  // compliant: the value is an argument, part of the cache key
  return fetch(`https://api.example.com/strings/${locale}`);
}

export async function getAuthor(id: string) {
  'use cache';
  // compliant: a user looked up by id, not the session
  return getUser(id);
}

export async function getUserAgent() {
  'use cache: private';
  // compliant: a private cache is kept per user
  return (await headers()).get('user-agent');
}
