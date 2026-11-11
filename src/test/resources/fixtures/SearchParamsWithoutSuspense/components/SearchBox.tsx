'use client';

import { useSearchParams } from 'next/navigation';

export default function SearchBox() {
  const q = useSearchParams().get('q');
  return <input defaultValue={q ?? ''} />;
}
