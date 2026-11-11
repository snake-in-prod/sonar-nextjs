export default function Layout({
  children,
  params: { path }, // Noncompliant
}: {
  children: React.ReactNode;
  params: { path: string[] };
}) {
  return <div data-path={path.join('/')}>{children}</div>;
}
