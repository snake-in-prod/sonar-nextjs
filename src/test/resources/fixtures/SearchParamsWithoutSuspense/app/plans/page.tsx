import { PlanDialog } from '@/components/Pricing';
import PlanBadge from '@/components/PlanBadge';

export default function PlansPage() {
  return (
    <main>
      <PlanBadge />
      <PlanDialog />
    </main>
  );
}
