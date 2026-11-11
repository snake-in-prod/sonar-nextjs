import { cookies } from 'next/headers';

export async function getCurrentUser() {
  const token = (await cookies()).get('session')?.value;
  return { name: token ?? 'guest' };
}
