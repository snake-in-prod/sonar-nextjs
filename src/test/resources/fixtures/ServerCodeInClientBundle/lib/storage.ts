import { S3Client } from '@aws-sdk/client-s3';

// top-level code: bundled whatever the client imports
const accessKeyId = process.env.ACCESS_KEY?.trim();

export const s3 = new S3Client({ credentials: { accessKeyId } });

export async function upload(url: string, file: File) {
  return fetch(url, { method: 'PUT', body: file });
}
