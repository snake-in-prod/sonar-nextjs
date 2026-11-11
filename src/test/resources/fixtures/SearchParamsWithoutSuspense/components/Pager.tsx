'use client';

import { useSearchParams } from 'next/navigation';

export default function Pager() {
  const page = useSearchParams().get('page');
  return <span>{page}</span>;
}
