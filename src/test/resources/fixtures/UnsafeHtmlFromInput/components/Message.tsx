'use client';

import { useSearchParams } from 'next/navigation';

export function Message() {
  const sp = useSearchParams();
  const message = sp.get('message');
  return (
    <div>
      <div dangerouslySetInnerHTML={{ __html: message ?? '' }} /> {/* Noncompliant */}
      {/* compliant: rendered as text */}
      <div>{message}</div>
    </div>
  );
}
