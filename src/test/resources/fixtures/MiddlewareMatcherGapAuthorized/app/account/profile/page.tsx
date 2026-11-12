'use client';

import { useState } from 'react';

// a stub: loads and changes nothing yet
export default function Profile() {
  const [name, setName] = useState('');
  return <input value={name} onChange={(e) => setName(e.target.value)} />;
}
