'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';

export function Message({ results }: { results: { report: string }[] }) {
  const sp = useSearchParams();
  const message = sp.get('message');
  const [formData, setFormData] = useState({ svg: '' });
  useEffect(() => {
    const c = sp.get('client');
    setFormData({ svg: c ?? '' });
  }, [sp]);
  return (
    <div>
      <div dangerouslySetInnerHTML={{ __html: message ?? '' }} /> {/* Noncompliant */}
      {/* compliant: rendered as text */}
      <div>{message}</div>
      {/* compliant: c here is the callback parameter, not the query value of the same name */}
      {results.map((c) => <div dangerouslySetInnerHTML={{ __html: c.report }} />)}
      {/* compliant: form state named formData, not a FormData */}
      <div dangerouslySetInnerHTML={{ __html: formData.svg }} />
    </div>
  );
}
