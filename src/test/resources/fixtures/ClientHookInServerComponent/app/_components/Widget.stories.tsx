import { useState } from 'react';
import Story from './Story';

export const Default = () => {
  const [v] = useState(0);
  return <Story v={v} />;
};
