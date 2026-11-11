'use client';

import { useState } from 'react';

// compliant: a byte order mark before the directive
export default function Bom() {
  const [v] = useState(0);
  return <s>{v}</s>;
}
