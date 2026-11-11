export const runtime = 'edge';

export default function Inline() {
  async function subscribe(formData: FormData) {
    'use server';
    console.log(formData.get('email'));
  }
  return <form action={subscribe} />;
}
