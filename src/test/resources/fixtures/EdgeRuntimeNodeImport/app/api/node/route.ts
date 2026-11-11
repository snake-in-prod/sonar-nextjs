import fs from 'fs';

export async function GET() {
  return new Response(fs.readFileSync('data.json'));
}
