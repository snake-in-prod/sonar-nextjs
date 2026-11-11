import { useFormStatus } from 'react-dom';
import { redirect } from 'next/navigation';

export default function Form() {
  const { pending } = useFormStatus(); // Noncompliant {{useFormStatus}}
  if (!pending) redirect('/');
  return <button disabled={pending} />;
}
