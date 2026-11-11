'use client';

import { useEffect, useState } from 'react';

export async function loadCount() {
  return 1;
}

export default function Counter() {
  const [n, setN] = useState(0);
  useEffect(() => {
    (async () => setN(await loadCount()))();
  }, []);
  const onClick = async () => setN(await loadCount());
  return <button onClick={onClick}>{n}</button>;
}
