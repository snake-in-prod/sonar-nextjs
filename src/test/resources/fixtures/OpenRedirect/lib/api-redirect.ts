import type { NextApiRequest, NextApiResponse } from 'next';

// a helper of the API routes: typed with NextApiResponse
export function finish(req: NextApiRequest, res: NextApiResponse) {
  res.redirect(req.query.callbackUrl as string); // Noncompliant
}
