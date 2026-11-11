export let metadata = { title: 'About' };

export function generateMetadata() { // Noncompliant
  return { title: 'About us' };
}

export default function Layout({ children }) {
  return <div>{children}</div>;
}
