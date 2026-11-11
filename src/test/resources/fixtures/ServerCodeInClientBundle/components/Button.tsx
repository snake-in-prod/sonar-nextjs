'use client';

import { readdir } from 'fs/promises'; // Noncompliant

export default function Button() {
  readdir('.');
  return <button />;
}
