"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

export type ProfileMock = {
  name: string;
  birthYear: string;
  birthMonth: string;
  birthDay: string;
  gender: "MALE" | "FEMALE" | "UNDISCLOSED";
  email: string;
};

const GENDER_OPTIONS = [
  { value: "MALE", label: "남성" },
  { value: "FEMALE", label: "여성" },
  { value: "UNDISCLOSED", label: "밝히고 싶지 않음" },
] as const;

export function ProfilePanel({ profile }: { profile: ProfileMock }) {
  const [gender, setGender] = useState<ProfileMock["gender"]>(profile.gender);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);

  return (
    <div className="flex flex-col gap-9">
      <div className="flex flex-col gap-3">
        <Row label="이름">
          <input
            type="text"
            defaultValue={profile.name}
            className="h-12 w-[276px] rounded-md border border-input bg-card px-4 py-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground focus:outline-none"
          />
        </Row>

        <Row label="생년월일">
          <div className="flex items-center gap-1.5">
            <input
              type="text"
              aria-label="출생연도"
              defaultValue={`${profile.birthYear}년`}
              className="h-12 w-[88px] rounded-md border border-input bg-card px-4 py-3 text-center text-sm leading-[1.5] tracking-[-0.35px] text-foreground focus:outline-none"
            />
            <input
              type="text"
              aria-label="출생월"
              defaultValue={`${profile.birthMonth}월`}
              className="h-12 w-[88px] rounded-md border border-input bg-card px-4 py-3 text-center text-sm leading-[1.5] tracking-[-0.35px] text-foreground focus:outline-none"
            />
            <input
              type="text"
              aria-label="출생일"
              defaultValue={`${profile.birthDay}일`}
              className="h-12 w-[88px] rounded-md border border-input bg-card px-4 py-3 text-center text-sm leading-[1.5] tracking-[-0.35px] text-foreground focus:outline-none"
            />
          </div>
        </Row>

        <Row label="성별">
          <div className="flex w-[276px] items-center gap-1.5">
            {GENDER_OPTIONS.map((opt, i) => {
              const active = gender === opt.value;
              const isLast = i === GENDER_OPTIONS.length - 1;
              return (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setGender(opt.value)}
                  aria-pressed={active}
                  className={`flex h-12 items-center justify-center rounded-md px-4 py-3 text-sm leading-[1.5] tracking-[-0.35px] transition-colors ${
                    isLast ? "flex-1" : "w-[72px]"
                  } ${
                    active
                      ? "bg-foreground text-background"
                      : "bg-zinc-200 text-zinc-400 hover:bg-zinc-300"
                  }`}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        </Row>
      </div>

      <div className="flex flex-col gap-3">
        <Row label="아이디(이메일)">
          <input
            type="email"
            defaultValue={profile.email}
            className="h-12 w-[276px] rounded-md border border-input bg-card px-4 py-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground focus:outline-none"
          />
        </Row>

        <Row label="비밀번호">
          <PasswordField
            visible={showPassword}
            onToggle={() => setShowPassword((v) => !v)}
            ariaLabel="비밀번호"
          />
        </Row>

        <Row label="비밀번호 확인">
          <PasswordField
            visible={showPasswordConfirm}
            onToggle={() => setShowPasswordConfirm((v) => !v)}
            ariaLabel="비밀번호 확인"
          />
        </Row>
      </div>
    </div>
  );
}

function Row({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex w-full items-center justify-between">
      <span className="text-base leading-[1.5] font-semibold tracking-[-0.4px] text-foreground">
        {label}
      </span>
      {children}
    </div>
  );
}

function PasswordField({
  visible,
  onToggle,
  ariaLabel,
}: {
  visible: boolean;
  onToggle: () => void;
  ariaLabel: string;
}) {
  return (
    <div className="flex h-12 w-[276px] items-center justify-between rounded-md border border-input bg-card px-4 py-3">
      <input
        type={visible ? "text" : "password"}
        aria-label={ariaLabel}
        defaultValue="dummypassword"
        className="flex-1 bg-transparent text-sm leading-[1.5] tracking-[-0.35px] text-foreground focus:outline-none"
      />
      <button
        type="button"
        onClick={onToggle}
        aria-label={visible ? `${ariaLabel} 숨기기` : `${ariaLabel} 보기`}
        className="ml-2 text-muted-foreground hover:text-foreground"
      >
        {visible ? (
          <Eye className="size-5" aria-hidden />
        ) : (
          <EyeOff className="size-5" aria-hidden />
        )}
      </button>
    </div>
  );
}
