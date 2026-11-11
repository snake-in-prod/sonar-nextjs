import { useRouter } from 'next/router';

export default function Notice() {
  const router = useRouter();
  return (
    <div>
      <p dangerouslySetInnerHTML={{ __html: router.query.message as string }} /> {/* Noncompliant */}
      {/* compliant: rendered as text */}
      <p>{router.query.message}</p>
    </div>
  );
}
