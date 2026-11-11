'use client';

import { useSearchParams } from 'next/navigation';

export function TagFilter() {
  const tag = useSearchParams()?.get('tag'); // Noncompliant {{"/blog"}}
  return <b>{tag}</b>;
}
