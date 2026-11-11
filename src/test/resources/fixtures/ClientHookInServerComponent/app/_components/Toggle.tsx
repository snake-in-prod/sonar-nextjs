'use client';

import { useState } from 'react';
import Menu from './Menu';

export default function Toggle() {
  const [on, setOn] = useState(false);
  return <Menu open={on} onToggle={() => setOn(!on)} />;
}
