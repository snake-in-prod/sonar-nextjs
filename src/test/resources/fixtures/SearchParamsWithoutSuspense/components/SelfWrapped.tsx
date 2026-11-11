'use client';

import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';

function Inner() {
  const q = useSearchParams().get('q');
  return <p>{q}</p>;
}

// compliant: the module wraps its own hook user in Suspense
export default function SelfWrapped() {
  return (
    <Suspense fallback={null}>
      <Inner />
    </Suspense>
  );
}
