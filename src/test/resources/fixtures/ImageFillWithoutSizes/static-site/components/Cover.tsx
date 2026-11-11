import Image from 'next/image';

// compliant: next.config sets images.unoptimized, no image of this app has a srcset
export function Cover({ src }: { src: string }) {
  return (
    <div className="relative h-64 w-64">
      <Image src={src} alt="" fill />
    </div>
  );
}
