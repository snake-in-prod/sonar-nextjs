'use client';

import { useSearchParams } from 'next/navigation';

// compliant: the page renders PlanDialog only, Plans calls the hook and no page renders it
export function Plans() {
  const plan = useSearchParams().get('plan');
  return <p>{plan}</p>;
}

export function PlanDialog() {
  return <dialog>Choose a plan</dialog>;
}
