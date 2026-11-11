import { createEnv } from '@t3-oss/env-nextjs';

export const env = createEnv({
  runtimeEnv: { DATABASE_URL: process.env.DATABASE_URL, NEXT_PUBLIC_X: process.env.NEXT_PUBLIC_X },
});
