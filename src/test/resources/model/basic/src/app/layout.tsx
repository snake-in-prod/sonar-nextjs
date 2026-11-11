import { Header } from './_components/Header';

export const metadata = { title: 'Basic' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html><body><Header />{children}</body></html>;
}
