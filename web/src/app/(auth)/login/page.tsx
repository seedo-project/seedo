import Image from "next/image";
import Link from "next/link";
import { Suspense, type ComponentType, type SVGProps } from "react";
import { FcGoogle } from "react-icons/fc";
import { SiNaver } from "react-icons/si";

import { LoginForm } from "@/components/auth/login-form";
import { AppleIcon, KakaoIcon } from "@/components/auth/social-icons";
import { Skeleton } from "@/components/ui/skeleton";

type SocialLogin = {
  name: string;
  label: string;
  Icon: ComponentType<SVGProps<SVGSVGElement>>;
  bg: string;
  iconClass: string;
};

const SOCIAL_LOGINS: SocialLogin[] = [
  {
    name: "naver",
    label: "네이버",
    Icon: SiNaver,
    bg: "bg-[#03C75A]",
    iconClass: "size-3.5 text-white",
  },
  {
    name: "kakao",
    label: "카카오",
    Icon: KakaoIcon,
    bg: "bg-[#FEE500]",
    iconClass: "size-[18px]",
  },
  {
    name: "google",
    label: "구글",
    Icon: FcGoogle,
    bg: "bg-white border border-border",
    iconClass: "size-5",
  },
  {
    name: "apple",
    label: "애플",
    Icon: AppleIcon,
    bg: "bg-black",
    iconClass: "size-[18px] text-white",
  },
];

function LoginFormFallback() {
  return (
    <div className="w-[400px]">
      <div className="flex gap-3">
        <div className="flex flex-1 flex-col gap-3">
          <Skeleton className="h-12 rounded-md" />
          <Skeleton className="h-12 rounded-md" />
        </div>
        <Skeleton className="h-auto w-[104px] self-stretch rounded-md" />
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <main className="flex min-h-svh flex-col items-center pt-[180px]">
      <p className="text-xs leading-normal font-medium text-muted-foreground">
        일상에서 <span className="font-bold">싹</span>트는 아이디어
      </p>

      <Image
        src="/seedo/logo.svg"
        alt="Seedo"
        width={160}
        height={40}
        priority
        className="mt-1"
      />

      <div className="mt-16">
        <Suspense fallback={<LoginFormFallback />}>
          <LoginForm />
        </Suspense>
      </div>

      <div className="mt-8 h-px w-[400px] bg-border" />

      <div className="mt-8 flex items-center gap-5">
        {SOCIAL_LOGINS.map(({ name, label, Icon, bg, iconClass }) => (
          <button
            key={name}
            type="button"
            aria-label={`${label}로 로그인`}
            className={`flex size-8 items-center justify-center overflow-hidden rounded-full ${bg}`}
          >
            <Icon className={iconClass} />
          </button>
        ))}
      </div>

      <Link
        href="/sign-up"
        className="mt-8 text-sm font-medium text-muted-foreground underline underline-offset-2 hover:text-foreground"
      >
        회원가입
      </Link>
    </main>
  );
}
