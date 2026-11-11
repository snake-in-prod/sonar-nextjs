import Image from 'next/image';
import type { ImageProps } from 'next/image';

type Props = { src: string; title: string; cover?: boolean; rest: Partial<ImageProps> };

export function Hero({ src, title }: Props) {
  return (
    <div className="relative h-64">
      <Image src={src} alt={title} fill /> {/* Noncompliant {{Set "sizes" on this fill Image}} */}
    </div>
  );
}

export function Banner({ src }: Props) {
  return (
    <div className="relative h-32">
      <Image // Noncompliant
        src={src}
        alt=""
        fill={true}
        priority
        className="object-cover"
      />
    </div>
  );
}

export function Poster({ src }: Props) {
  return (
    <figure>
      <Image src={src} alt="poster" fill style={{ objectFit: 'cover' }}></Image> {/* Noncompliant */}
    </figure>
  );
}

// compliant: sizes is set
export function Card({ src }: Props) {
  return <Image src={src} alt="" fill sizes="(max-width: 768px) 100vw, 33vw" />;
}

// compliant: sizes before fill, as an expression
export function Tile({ src }: Props) {
  return <Image sizes={`${100 / 3}vw`} src={src} alt="" fill />;
}

// compliant: width and height, no fill
export function Avatar({ src }: Props) {
  return <Image src={src} alt="avatar" width={48} height={48} />;
}

// compliant: fill is explicitly false
export function Thumb({ src }: Props) {
  return <Image src={src} alt="" width={64} height={64} fill={false} />;
}

// compliant: fill is computed, sizes may well be meant to come with it
export function Media({ src, cover }: Props) {
  return <Image src={src} alt="" fill={cover} width={cover ? undefined : 320} />;
}

// compliant: a spread may carry sizes
export function Spread({ src, rest }: Props) {
  return <Image src={src} alt="" fill {...rest} />;
}

// compliant: "fill" as a value, not the prop
export function Text({ src }: Props) {
  return <Image src={src} alt="fill" width={10} height={10} style={{ objectFit: 'fill' }} />;
}
