'use client';
import { useState } from 'react';

export default function AdminLogin() {
  const [name, setName] = useState('');
  return <form><input value={name} onChange={(e) => setName(e.target.value)} /></form>;
}
