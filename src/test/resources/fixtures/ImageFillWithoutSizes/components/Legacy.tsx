import Image from 'next/legacy/image';

// compliant: next/legacy/image uses layout="fill", outside this rule
export function Legacy({ src }: { src: string }) {
  return <Image src={src} alt="" layout="fill" />;
}
