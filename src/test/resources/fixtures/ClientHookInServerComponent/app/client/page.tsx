'use client';

import { useState } from 'react';

export default function ClientPage() {
  const [v, setV] = useState(0);
  return <button onClick={() => setV(v + 1)}>{v}</button>;
}
