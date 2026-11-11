'use client';

import { use } from 'react';

export default function Tag({ params }: { params: Promise<{ tag: string }> }) {
  const { tag } = use(params);
  return <h3>{tag}</h3>;
}
