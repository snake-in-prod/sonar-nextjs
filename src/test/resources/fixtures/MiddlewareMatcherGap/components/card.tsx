import clsx from 'clsx';

export function Card({ children }: { children: React.ReactNode }) {
  return <div className={clsx('card')}>{children}</div>;
}
