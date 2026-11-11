import CartBar from '@/components/CartBar';

// compliant: app/(shop)/loading.tsx wraps this nested layout
export default function CartLayout({ children }: { children: React.ReactNode }) {
  return (
    <section>
      <CartBar />
      {children}
    </section>
  );
}
