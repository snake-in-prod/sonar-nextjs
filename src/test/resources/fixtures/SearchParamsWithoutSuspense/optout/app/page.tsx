'use client';

import { useSearchParams } from 'next/navigation';

// compliant: next.config turns missingSuspenseWithCSRBailout off, client rendering is chosen
export default function Home() {
  const tab = useSearchParams().get('tab');
  return <p>{tab}</p>;
}
