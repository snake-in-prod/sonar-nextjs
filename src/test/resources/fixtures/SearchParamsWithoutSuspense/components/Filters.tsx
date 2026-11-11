'use client';

import { useSearchParams as useQuery } from 'next/navigation';

export default function Filters() {
  const query = useQuery(); // Noncompliant
  return <span>{query.get('color')}</span>;
}
