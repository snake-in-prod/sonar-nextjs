import { useState } from 'react';

export default function Home() {
  const [v] = useState(0);
  return <p>{v}</p>;
}
