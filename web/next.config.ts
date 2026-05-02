import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 모노레포 상위에 다른 lockfile이 있을 때 workspace root 추론을 명시적으로 고정
  turbopack: {
    root: path.join(__dirname),
  },
};

export default nextConfig;
