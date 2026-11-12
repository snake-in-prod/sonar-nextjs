import type { NextApiRequest, NextApiResponse } from 'next';

const ALLOWED = ['/dashboard', '/settings'];

export default function handler(req: NextApiRequest, res: NextApiResponse) {
  if (req.method === 'PUT') {
    return res.redirect(302, req.body.callbackUrl); // Noncompliant
  }
  const { next } = req.body;
  if (req.method === 'PATCH') {
    return res.redirect(next); // Noncompliant
  }
  // compliant: allow-list
  if (ALLOWED.includes(req.body.returnTo)) {
    return res.redirect(req.body.returnTo);
  }
  res.redirect('/');
}
