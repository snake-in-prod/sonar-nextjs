'use client';

import { redirect, useSearchParams } from 'next/navigation';

export function Gate({ ready }: { ready: boolean }) {
  const sp = useSearchParams();
  if (ready) {
    redirect(sp.get('next') || '/'); // Noncompliant
  }
  return null;
}
