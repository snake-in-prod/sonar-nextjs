import { useState as useLocalState } from 'react';

const Settings = () => {
  const [v] = useLocalState<string>(''); // Noncompliant {{useState}}
  return <p>{v}</p>;
};

export default Settings;
