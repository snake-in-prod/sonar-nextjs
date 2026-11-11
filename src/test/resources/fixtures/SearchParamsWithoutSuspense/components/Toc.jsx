'use client';

import { useSearchParams } from 'next/navigation';

export default function Toc({ slug }) {
  const section = useSearchParams().get('section'); // Noncompliant
  return <nav>{slug}#{section}</nav>;
}
