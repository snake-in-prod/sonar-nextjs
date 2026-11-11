import { getServerSession } from 'next-auth';
import { authOptions } from './auth-options';

const loadProfile = async () => {
  const session = await getServerSession(authOptions);
  return session?.user;
};

export default loadProfile;
