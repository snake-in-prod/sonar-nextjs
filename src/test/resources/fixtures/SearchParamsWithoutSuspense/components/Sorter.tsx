'use client';

import { useSearchParams } from 'next/navigation';

export default function Sorter() {
  const sort = useSearchParams().get('sort');
  return <span>{sort}</span>;
}
