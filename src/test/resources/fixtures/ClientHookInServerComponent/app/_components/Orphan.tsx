import { useState } from 'react';

export default function Orphan() {
  const [x] = useState(1);
  return <i>{x}</i>;
}
