'use client';

import { useSearchParams } from 'next/navigation';
import { useRouter } from '@/lib/local-router';

export function OtherRouter() {
  const router = useRouter();
  const searchParams = useSearchParams();
  // compliant: not the Next.js router
  router.push(searchParams.get('next'));
}
