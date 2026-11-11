'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

export function Navbar() {
  const params = useSearchParams(); // Noncompliant {{renders it without a boundary}}
  return <Link href={`/?tab=${params.get('tab') ?? ''}`}>Home</Link>;
}
