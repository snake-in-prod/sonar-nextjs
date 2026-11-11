'use cache';

import { headers } from 'next/headers';

export default async function Page() {
  const h = await headers(); // Noncompliant
  return h.get('accept-language');
}
