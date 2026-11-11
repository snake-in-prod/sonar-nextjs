'use client';

import { useSearchParams } from 'next/navigation';

export default function LegacyFilter() {
  const f = useSearchParams()?.get('f');
  return <p>{f}</p>;
}
