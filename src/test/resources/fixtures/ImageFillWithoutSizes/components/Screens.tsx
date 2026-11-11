import Image from 'next/image';
import logo from '../public/logo.svg';

const BASE = '/icons';
const ICONS = { empty: `${BASE}/empty.svg`, photo: `${BASE}/photo.png` };

// compliant: an SVG source, written in braces, imported, or held by a constant
export function Icons() {
  return (
    <div className="relative h-8 w-8">
      <Image src={'/icons/chat.svg'} alt="" fill />
      <Image src={logo} alt="" fill />
      <Image src={ICONS.empty} alt="" fill />
      <Image src={ICONS.photo} alt="" fill /> {/* Noncompliant */}
    </div>
  );
}

// compliant: a full screen image, 100vw is right
export function FullScreen({ src, slides }: { src: string; slides: string[] }) {
  return (
    <>
      <main className="relative w-full h-screen overflow-hidden">
        <Image src={src} alt="" fill className="object-cover" />
        <div className="absolute inset-0 bg-black/40" />
      </main>
      <div className="relative flex flex-col">
        <div className="sticky top-0 h-[100vh] w-full overflow-hidden">
          <div className="absolute inset-0 bg-black/30"></div>
          {slides.map((s) => (
            <Image key={s} src={s} alt="" fill className="object-cover" />
          ))}
        </div>
      </div>
    </>
  );
}

// a viewport max height is no full screen image
export function Scroller({ src }: { src: string }) {
  return (
    <div className="max-h-[70dvh] overflow-y-auto">
      <div className="relative aspect-video">
        <Image src={src} alt="" fill /> {/* Noncompliant */}
      </div>
    </div>
  );
}

// a width limit comes first: half a screen, an avatar in a full height sidebar
export function Split({ src }: { src: string }) {
  return (
    <div className="grid h-screen grid-cols-2">
      <div className="relative">
        <Image src={src} alt="" fill /> {/* Noncompliant */}
      </div>
      <aside className="h-screen">
        <div className="relative size-8 rounded-full">
          {src && <Image src={src} alt="" fill />} {/* Noncompliant */}
        </div>
      </aside>
    </div>
  );
}
