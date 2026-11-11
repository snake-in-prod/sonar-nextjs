'use client';

import { useSearchParams } from 'next/navigation';

function useTier() {
  return useSearchParams().get('tier'); // Noncompliant
}

// calls the hook through a custom hook of its module
export default function PlanBadge() {
  return <span>{useTier()}</span>;
}
