'use client';

import { useEffect, useState } from 'react';

// compliant: the code tests whether params is a promise before reading it
export default function Item({ params }: { params: { id: string } | Promise<{ id: string }> }) {
  const [id, setId] = useState('');
  useEffect(() => {
    const load = async () => {
      if (params instanceof Promise) {
        setId((await params).id);
      } else {
        setId(params.id);
      }
    };
    load();
  }, [params]);
  return <p>{id}</p>;
}
