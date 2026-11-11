'use client';

import { memo } from 'react';

async function load() {
  return 'x';
}

function Lazy() {
  return <span onClick={() => load()} />;
}

export default memo(Lazy);
