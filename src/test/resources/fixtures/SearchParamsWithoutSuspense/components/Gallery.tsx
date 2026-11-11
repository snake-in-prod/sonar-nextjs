'use client';

import { useSearchParams } from 'next/navigation';

export default function Gallery({ id }: { id: string }) {
  const photo = useSearchParams().get('photo');
  return <img alt={id} src={`/photos/${photo}`} />;
}
