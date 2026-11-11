'use client';

import { useSearchParams } from 'next/navigation';

// compliant: the layout above the only page using it wraps its children in Suspense
export function AreaFilter() {
  const area = useSearchParams().get('area');
  return <span>{area}</span>;
}
