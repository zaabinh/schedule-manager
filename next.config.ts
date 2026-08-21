import type { NextConfig } from "next";

function readBackendOrigin() {
  const value = process.env.BACKEND_ORIGIN?.replace(/\/$/u, "");
  if (!value) return undefined;
  const url = new URL(value);
  if (!(["http:", "https:"].includes(url.protocol)) || url.origin !== value) {
    throw new Error("BACKEND_ORIGIN must be an absolute HTTP(S) origin without a path or trailing slash");
  }
  return url.origin;
}

const backendOrigin = readBackendOrigin();

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Vercel injects a Next.js build adapter and does not need standalone output.
  // Next.js 16.3 currently fails when both modes are enabled because the adapter
  // omits next-server.js.nft.json while the standalone finalizer still reads it.
  output: process.env.VERCEL ? undefined : "standalone",
  poweredByHeader: false,
  async rewrites() {
    return backendOrigin ? [{
      source: "/api/v1/:path*",
      destination: `${backendOrigin}/api/v1/:path*`,
    }] : [];
  },
  async headers() {
    return [{ source: "/(.*)", headers: [
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
      { key: "Cross-Origin-Embedder-Policy", value: "credentialless" },
      { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
      { key: "Cross-Origin-Resource-Policy", value: "same-origin" },
    ] }];
  },
};

export default nextConfig;
