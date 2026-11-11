'use client';

import { useSearchParams } from 'react-router-dom';

// compliant: not the next/navigation hook
export default function RouterQuery() {
  const [params] = useSearchParams();
  return <p>{params.get('x')}</p>;
}
