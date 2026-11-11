import { getCurrentUser } from '@/lib/session';
import ProfileTabs from '@/components/ProfileTabs';

// compliant: the session helper reads cookies, the page is dynamic
export default async function Profile() {
  const user = await getCurrentUser();
  return <ProfileTabs name={user.name} />;
}
