'use client';

import { useSearchParams } from 'next/navigation';

export default function ResultsTable({ initial }: { initial?: string }) {
  const q = useSearchParams().get('q') ?? initial;
  return <table summary={q} />;
}
