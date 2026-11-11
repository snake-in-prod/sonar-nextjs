'use client';

import { useSearchParams } from 'next/navigation';

export default function SearchPage() {
  const q = useSearchParams().get('q'); // Noncompliant {{"/search"}}
  return <p>Results for {q}</p>;
}
