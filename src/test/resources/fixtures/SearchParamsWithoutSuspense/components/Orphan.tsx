'use client';

import { useSearchParams } from 'next/navigation';

// compliant: no page renders this component
export default function Orphan() {
  return <p>{useSearchParams().get('o')}</p>;
}
