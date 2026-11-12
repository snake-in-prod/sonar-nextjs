import type { NextApiRequest, NextApiResponse } from 'next';

export default function handler(req: NextApiRequest, res: NextApiResponse) {
  const { to } = req.query;
  if (req.query.mode === 'plain') {
    return res.redirect(to as string); // Noncompliant
  }
  if (req.query.mode === 'status') {
    return res.redirect(307, req.query.next as string); // Noncompliant
  }
  if (req.query.mode === 'head') {
    res.writeHead(302, { Location: req.query.back as string }); // Noncompliant
    return res.end();
  }
  // compliant: a fixed path, and a status with a fixed path
  res.redirect('/');
  res.redirect(302, '/home');
  res.writeHead(302, { 'Content-Type': 'text/plain' });
  res.end();
}
