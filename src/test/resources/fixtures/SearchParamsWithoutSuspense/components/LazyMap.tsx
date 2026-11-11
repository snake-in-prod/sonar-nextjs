'use client';

import { useSearchParams } from 'next/navigation';

export default function LazyMap() {
  const at = useSearchParams().get('at');
  return <p>{at}</p>;
}
