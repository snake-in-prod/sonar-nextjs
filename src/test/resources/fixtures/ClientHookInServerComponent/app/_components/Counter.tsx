import React from 'react';

export default function Counter() {
  React.useEffect(() => { // Noncompliant {{useEffect}}
    document.title = 'x';
  }, []);
  return <span />;
}
