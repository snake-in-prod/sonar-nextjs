import { use, useId, useMemo, useCallback, cache } from 'react';
import { notFound } from 'next/navigation';

const load = cache(async () => ['a']);

export default function Blog() {
  const id = useId();
  const posts = use(load());
  const sorted = useMemo(() => [...posts].sort(), [posts]);
  const label = 'useState';
  if (!posts) notFound();
  return <ul id={id}>{sorted.map((p) => <li key={p}>{p}</li>)}</ul>;
}
