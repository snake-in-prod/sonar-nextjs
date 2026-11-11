'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import DOMPurify from 'dompurify';

export function Preview() {
  const searchParams = useSearchParams();
  const [html, setHtml] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  useEffect(() => {
    const url = searchParams.get('url');
    if (!url) {
      setHtml('');
      return;
    }
    fetch(`/api/proxy?url=${encodeURIComponent(url)}`)
      .then((r) => r.json())
      .then((meta) => {
        setTitle(meta.title);
      });
    const load = async () => {
      const res = await fetch(`/api/proxy?url=${encodeURIComponent(url)}`);
      const data = await res.json();
      setHtml(data.html);
    };
    load();
  }, [searchParams]);
  return (
    <div>
      {/* compliant: React state is not followed, the response is often the app's own API */}
      <div dangerouslySetInnerHTML={{ __html: html ?? '' }} />
      {/* compliant: set from a callback parameter the flow cannot see */}
      <div dangerouslySetInnerHTML={{ __html: title }} />
    </div>
  );
}

export function CleanPreview({ url }: { url: string }) {
  const [clean, setClean] = useState('');
  useEffect(() => {
    const load = async () => {
      const res = await fetch(url);
      const text = await res.text();
      setClean(DOMPurify.sanitize(text));
    };
    load();
  }, [url]);
  // compliant: sanitized before it is stored
  return <div dangerouslySetInnerHTML={{ __html: clean }} />;
}
