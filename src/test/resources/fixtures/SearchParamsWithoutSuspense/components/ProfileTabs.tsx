'use client';

import { useSearchParams } from 'next/navigation';

export default function ProfileTabs({ name }: { name: string }) {
  const tab = useSearchParams().get('tab');
  return <h2>{name} {tab}</h2>;
}
