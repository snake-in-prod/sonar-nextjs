'use client';

import { useSearchParams } from 'next/navigation';

// compliant: the page calls next-intl/server without setRequestLocale, it renders per request
export default function IntlFilter() {
  const sort = useSearchParams().get('sort');
  return <span>{sort}</span>;
}
