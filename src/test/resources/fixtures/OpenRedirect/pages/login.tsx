import { useRouter } from 'next/router';

export default function Login() {
  const router = useRouter();

  async function onSubmit() {
    await fetch('/api/login', { method: 'POST' });
    const returnUrl = (router.query.returnUrl as string) || '/';
    router.push(returnUrl); // Noncompliant
  }

  function onCancel() {
    const { from } = router.query;
    // compliant: checked to be a local path
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) {
      router.push(from);
    }
  }

  function onSearch(query: string) {
    // compliant: a local named query, not the request query
    router.push(query);
  }

  return { onSubmit, onCancel, onSearch };
}
