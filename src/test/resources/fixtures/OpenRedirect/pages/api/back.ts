import type { NextApiRequest, NextApiResponse } from 'next';

const ALLOWED = ['/dashboard', '/settings'];

export default function handler(req: NextApiRequest, res: NextApiResponse) {
  const returnTo = req.query.returnTo as string;
  // compliant: checked against an allow-list
  if (!ALLOWED.includes(returnTo)) {
    return res.redirect('/');
  }
  res.redirect(302, returnTo);
}

export function local(req: NextApiRequest, res: NextApiResponse) {
  const next = String(req.query.next ?? '/');
  // compliant: only a local path is accepted
  if (!next.startsWith('/') || next.startsWith('//')) {
    return res.status(400).end();
  }
  res.writeHead(302, { Location: next });
  res.end();
}

export function fromSession(req: NextApiRequest, res: NextApiResponse, session: { url: string }) {
  // compliant: not a request value
  res.redirect(session.url);
}

export function preview(req: NextApiRequest, res: NextApiResponse) {
  if (req.query.secret !== process.env.PREVIEW_SECRET || !req.query.slug) {
    return res.status(401).json({ message: 'Invalid token' });
  }
  // compliant: only who knows the preview secret gets here
  res.redirect(req.query.slug as string);
}

export function openPreview(req: NextApiRequest, res: NextApiResponse) {
  if (!req.query.secret || !req.query.slug) {
    return res.status(401).json({ message: 'Invalid token' });
  }
  // any secret passes: anyone can build the link
  res.redirect(req.query.slug as string); // Noncompliant
}
