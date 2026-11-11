export const generateMetadata = async () => ({ title: 'Shop' }); // Noncompliant {{from this layout}}

export const metadata = { title: 'Shop' };

export default function Layout({ children }: { children: React.ReactNode }) {
  return <section>{children}</section>;
}
