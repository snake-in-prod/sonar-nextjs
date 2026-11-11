import NextImage from 'next/image';
import { Image } from './ui';

export default function Gallery({ items }) {
  return items.map((it) => (
    <div key={it.id} className="relative aspect-square">
      <NextImage src={it.src} alt={it.alt} fill /> {/* Noncompliant */}
      <Image src={it.src} alt={it.alt} fill />
    </div>
  ));
}
