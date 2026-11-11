import { cookies } from 'next/headers';
import { unstable_cache } from 'next/cache';
import { auth } from '@/auth';
import { db } from '@/lib/db';

const API = 'https://api.example.com';

export default async function Dashboard() {
  const session = await auth();
  const token = (await cookies()).get('token')?.value;
  const userId = session?.user?.id;

  const orders = await fetch(`${API}/orders?user=${userId}`, { next: { revalidate: 60 } }); // Noncompliant {{Do not cache this fetch}}
  const profile = await fetch(`${API}/me`, { headers: { Authorization: `Bearer ${token}` }, cache: 'force-cache' }); // Noncompliant
  const prefs = await fetch(`${API}/prefs`, { // Noncompliant
    method: 'POST',
    body: JSON.stringify({ userId }),
    next: { revalidate: false },
  });
  const getStats = unstable_cache(async () => db.stats.findMany({ where: { userId } }), ['stats']); // Noncompliant

  // compliant: not cached
  const live = await fetch(`${API}/orders?user=${userId}`, { cache: 'no-store' });
  // compliant: cached, but the same for everyone
  const products = await fetch(`${API}/products`, { next: { revalidate: 3600, tags: [`user-${userId}`] } });
  // compliant: revalidate 0 is not cached
  const fresh = await fetch(`${API}/me`, { headers: { Authorization: `Bearer ${token}` }, next: { revalidate: 0 } });
  // compliant: the user id is an argument of the cached function, so part of its key
  const byArg = await unstable_cache(async (id: string) => db.stats.findMany({ where: { userId: id } }), ['stats'])(userId);
  // compliant: the user id is a key part
  const byKey = unstable_cache(async () => db.stats.findMany({ where: { userId } }), ['stats', String(userId)]);
  // compliant: options the rule cannot see
  const options = { next: { revalidate: 60 } };
  const other = await fetch(`${API}/orders?user=${userId}`, options);

  return [orders, profile, prefs, getStats, live, products, fresh, byArg, byKey, other].length;
}
