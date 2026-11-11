'use client';

import { DEFAULT_OPTION, imageUrl } from '../lib/constants';
import { gateway } from '../lib/constants';
import { mongoUrl } from '../lib/constants'; // Noncompliant {{the server environment variable MONGODB_URI into the browser bundle through lib/constants.ts}}
import { signed } from '../lib/constants'; // Noncompliant {{SIGNING_SECRET}}
import { signedHeader } from '../lib/constants'; // Noncompliant {{HMAC_SECRET}}
import { upload } from '../lib/storage'; // Noncompliant {{the server environment variable ACCESS_KEY into the browser bundle through lib/storage.ts}}
import { IUser } from '../lib/models';
import { Grid } from '../lib/grid';
import { signOut } from '../lib/session';

export function Shop({ user }: { user: (IUser & { id: string }) | null }) {
  return (
    <form action={signOut}>
      {DEFAULT_OPTION}{imageUrl('a')}{gateway}{mongoUrl}{signed('x')}{signedHeader('y')}{String(upload)}{user?.id}
      <Grid getId={() => user?.id} />
    </form>
  );
}
