'use client';

import { useSearchParams } from 'next/navigation';

export default function Tabs({ theme }: { theme?: string }) {
  const tab = useSearchParams().get('tab');
  return <div className={theme}>{tab}</div>;
}
