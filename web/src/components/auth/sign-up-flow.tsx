"use client";

import { Eye } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import {
  type FormEvent,
  type SVGProps,
  useCallback,
  useEffect,
  useState,
} from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { createClient } from "@/lib/supabase/client";
import { toast } from "@/lib/toast";

/**
 * Figma의 ShowHide 컴포넌트 — 사선 그어진 눈 (hide 상태).
 * 출처: Figma 디자인 자산 인라인. currentColor로 색 통일.
 */
function EyeOffIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 20 17.5"
      fill="currentColor"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      {...props}
    >
      <path d="M17.4697 0.21967C17.7626 -0.0732234 18.2374 -0.0732232 18.5303 0.21967C18.8231 0.512568 18.8231 0.987345 18.5303 1.28022L2.53027 17.2802C2.2374 17.5731 1.76262 17.573 1.46973 17.2802C1.17684 16.9873 1.17684 16.5126 1.46973 16.2197L3.57715 14.1113C1.39097 12.8278 3.10932e-05 10.9038 0 8.74995C0 4.88395 4.47715 1.74994 10 1.74994C11.8216 1.74994 13.5284 2.09314 14.999 2.6894L17.4697 0.21967ZM17.3057 4.85737C17.582 4.58147 18.028 4.56341 18.2988 4.84467C19.3726 5.96016 20 7.30358 20 8.74995C19.9999 12.6159 15.5228 15.7499 10 15.7499C9.37453 15.7499 8.76245 15.7093 8.16895 15.6318C7.55919 15.552 7.3457 14.8178 7.78027 14.3828C7.9574 14.2056 8.2084 14.1248 8.45703 14.1552C8.95538 14.2164 9.47103 14.2499 10 14.2499C12.4914 14.2499 14.6827 13.5404 16.2109 12.4707C17.7438 11.3976 18.5 10.0591 18.5 8.74995C18.5 7.80842 18.1084 6.85221 17.3184 5.99018C17.0239 5.66897 16.9975 5.16549 17.3057 4.85737ZM10 3.24994C7.5086 3.24994 5.31734 3.95945 3.78906 5.02924C2.25617 6.10233 1.5 7.44081 1.5 8.74995C1.50003 10.0591 2.25617 11.3976 3.78906 12.4707C4.06433 12.6633 4.36153 12.8435 4.67773 13.0107L6.69238 10.996C6.25643 10.3556 6.00001 9.58313 6 8.74995C6 6.54081 7.79086 4.74995 10 4.74995C10.8333 4.74995 11.6066 5.0053 12.2471 5.44135L13.833 3.85541C12.6913 3.47281 11.3928 3.24994 10 3.24994ZM10 6.24995C8.61929 6.24995 7.5 7.36923 7.5 8.74995C7.50001 9.16706 7.60217 9.56 7.7832 9.90522L11.1553 6.53315C10.81 6.35211 10.4171 6.24995 10 6.24995Z" />
    </svg>
  );
}

const TERMS_TITLE = "Seedo 서비스 이용약관 (필수)";
// TODO: 정식 약관 확정 후 본문 교체. 현재는 MVP 임시안.
const TERMS_BODY = `개정일자: 2026년 5월 12일

제1조 (목적)
본 약관은 Seedo(이하 "서비스")를 이용함에 있어 운영자와 회원 간의 권리, 의무 및 책임사항, 이용 조건과 절차를 규정함을 목적으로 합니다.

제2조 (용어의 정의)
① "서비스"란 일상 속 아이디어를 정리하고, 개발자가 이를 채택해 프로젝트로 발전시킬 수 있도록 돕는 Seedo 플랫폼을 의미합니다.
② "회원"이란 본 약관에 동의하고 회원가입 절차를 완료한 자를 말합니다.
③ "아이디어"란 회원이 작성·발행한 기획 문서를 말하며, 다른 회원이 크레딧을 지불하고 열람·채택할 수 있습니다.
④ "크레딧"이란 서비스 내에서 아이디어 구매·보상 등의 거래에 사용되는 가상의 단위를 말합니다.

제3조 (회원의 권리와 의무)
① 회원은 본인의 계정 정보를 안전하게 관리할 책임이 있습니다.
② 회원은 타인의 권리를 침해하거나 법령에 위배되는 콘텐츠를 작성·배포할 수 없습니다.
③ 회원은 본인이 작성한 아이디어에 대한 저작권을 보유하며, 서비스 운영을 위한 범위 내에서 운영자에게 이용권을 부여합니다.

제4조 (크레딧 정책)
① 크레딧은 충전·구매·채택 보상·관리자 조정을 통해 잔액이 변동되며, 모든 거래 내역은 보존됩니다.
② 본인이 작성한 아이디어는 본인이 구매할 수 없습니다.
③ 환불 정책은 별도 고지되는 기준에 따릅니다.

제5조 (약관의 개정)
운영자는 관련 법령을 준수하는 범위에서 본 약관을 개정할 수 있으며, 개정 시 사전에 공지합니다.`;

type Step = "terms" | "info";

export function SignUpFlow() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("terms");
  const [marketingConsent, setMarketingConsent] = useState(false);

  const handleBack = () => {
    if (step === "info") setStep("terms");
    else router.push("/login");
  };

  return (
    <main className="flex min-h-svh items-center justify-center bg-muted px-4 py-12">
      <div className="flex w-[440px] flex-col items-center gap-5 rounded-xl bg-card px-5 pb-5">
        <div className="flex w-full flex-col items-center">
          <header className="flex w-full items-center justify-between py-5">
            <button
              type="button"
              onClick={handleBack}
              aria-label="이전 단계"
              className="flex size-7 items-center px-[5.833px] py-[4.667px]"
            >
              <Image
                src="/seedo/back-arrow.svg"
                alt=""
                width={10}
                height={18}
                className="h-[18.249px] w-[10px]"
              />
            </button>
            <h1 className="text-xl font-bold tracking-[-0.5px] text-foreground">
              회원가입
            </h1>
            <div className="size-7" aria-hidden />
          </header>

          {step === "terms" ? (
            <TermsStep
              onNext={(marketing) => {
                setMarketingConsent(marketing);
                setStep("info");
              }}
            />
          ) : (
            <InfoStep marketingConsent={marketingConsent} />
          )}
        </div>
      </div>
    </main>
  );
}

function TermsStep({ onNext }: { onNext: (marketing: boolean) => void }) {
  const [required, setRequired] = useState(false);
  const [marketing, setMarketing] = useState(false);

  const allAgreed = required && marketing;
  const toggleAll = (checked: boolean) => {
    setRequired(checked);
    setMarketing(checked);
  };

  return (
    <div className="flex w-[400px] flex-col items-start gap-3">
      <div className="h-[440px] w-full overflow-y-auto rounded-md border border-border p-4">
        <p className="text-sm leading-[1.5] font-bold tracking-[-0.35px] text-muted-foreground">
          {TERMS_TITLE}
        </p>
        <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] whitespace-pre-line text-muted-foreground">
          {TERMS_BODY}
        </p>
      </div>

      <div className="flex w-full flex-col items-start gap-2 px-3">
        <label className="flex cursor-pointer items-center gap-2 select-none">
          <Checkbox
            checked={allAgreed}
            onCheckedChange={(v) => toggleAll(v === true)}
          />
          <span className="text-sm leading-[1.5] font-bold tracking-[-0.35px] text-foreground">
            전체동의
          </span>
        </label>
        <label className="flex cursor-pointer items-center gap-2 select-none">
          <Checkbox
            checked={required}
            onCheckedChange={(v) => setRequired(v === true)}
          />
          <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            (필수) 서비스 이용약관에 동의합니다
          </span>
        </label>
        <label className="flex cursor-pointer items-center gap-2 select-none">
          <Checkbox
            checked={marketing}
            onCheckedChange={(v) => setMarketing(v === true)}
          />
          <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-muted-foreground">
            (선택) 광고성 정보 이메일/SMS 수신에 동의합니다
          </span>
        </label>
      </div>

      <PrimaryButton
        type="button"
        disabled={!required}
        onClick={() => onNext(marketing)}
        className="self-center"
      >
        다음으로
      </PrimaryButton>
    </div>
  );
}

type Gender = "male" | "female" | "unspecified";

const GENDER_OPTIONS: { value: Gender; label: string; widthClass: string }[] = [
  { value: "male", label: "남성", widthClass: "w-[72px]" },
  { value: "female", label: "여성", widthClass: "w-[72px]" },
  { value: "unspecified", label: "밝히고 싶지 않음", widthClass: "flex-[1_0_0] min-w-px" },
];

// 한글/영문/숫자/_ 만, 2~20자. 백엔드 V8 에서도 동일 정책 가정.
const NICKNAME_PATTERN = /^[a-zA-Z0-9가-힣_]{2,20}$/;

type NicknameStatus =
  | "idle"
  | "invalid-format"
  | "checking"
  | "available"
  | "taken"
  | "error";

function InfoStep({ marketingConsent }: { marketingConsent: boolean }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [nickname, setNickname] = useState("");
  const [nicknameStatus, setNicknameStatus] = useState<NicknameStatus>("idle");
  const [year, setYear] = useState("");
  const [month, setMonth] = useState("");
  const [day, setDay] = useState("");
  const [gender, setGender] = useState<Gender | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 닉네임 debounce + 중복 체크. public_profiles view (V7) 가 anon SELECT 허용.
  const checkNickname = useCallback(async (value: string) => {
    if (!NICKNAME_PATTERN.test(value)) {
      setNicknameStatus(value === "" ? "idle" : "invalid-format");
      return;
    }
    setNicknameStatus("checking");
    const supabase = createClient();
    const { data, error: e } = await supabase
      .from("public_profiles")
      .select("id")
      .eq("nickname", value)
      .maybeSingle();
    if (e) {
      setNicknameStatus("error");
      return;
    }
    setNicknameStatus(data ? "taken" : "available");
  }, []);

  useEffect(() => {
    const handle = setTimeout(() => {
      checkNickname(nickname.trim());
    }, 400);
    return () => clearTimeout(handle);
  }, [nickname, checkNickname]);

  const isValidBirthDate = (() => {
    if (!/^\d{4}$/.test(year) || !/^\d{1,2}$/.test(month) || !/^\d{1,2}$/.test(day)) {
      return false;
    }
    const y = Number(year);
    const m = Number(month);
    const d = Number(day);
    const date = new Date(y, m - 1, d);
    return (
      date.getFullYear() === y &&
      date.getMonth() === m - 1 &&
      date.getDate() === d
    );
  })();

  const passwordStatus = checkPasswordStatus(password);
  const passwordMatch =
    passwordConfirm.length === 0
      ? "idle"
      : password === passwordConfirm
        ? "match"
        : "mismatch";

  const isValid =
    name.trim().length > 0 &&
    nicknameStatus === "available" &&
    isValidBirthDate &&
    gender !== null &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) &&
    passwordStatus === "valid" &&
    passwordMatch === "match";

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isValid) return;
    setSubmitting(true);
    setError(null);
    const supabase = createClient();
    const { error: signUpError } = await supabase.auth.signUp({
      email,
      password,
      options: {
        data: {
          name: name.trim(),
          nickname: nickname.trim(),
          birth_date: `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`,
          gender,
          marketing_consent: marketingConsent,
        },
      },
    });
    setSubmitting(false);
    if (signUpError) {
      // 23505 (UNIQUE 위반) — race 로 사전 체크를 빠져나간 경우. 메시지 변환.
      const msg = signUpError.message ?? "";
      if (/duplicate|unique|already/i.test(msg)) {
        setError("이미 사용 중인 닉네임 또는 이메일입니다");
        setNicknameStatus("taken");
        toast.error("이미 사용 중인 닉네임 또는 이메일입니다");
      } else {
        setError(msg);
        toast.error("회원가입에 실패했습니다");
      }
      return;
    }
    toast.success("가입이 완료되었습니다");
    // signUp 직후 Supabase 가 세션을 자동 발급 — hard navigation 으로 서버 cookies 동기화.
    window.location.href = "/idea";
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="flex w-[400px] flex-col items-start gap-9 py-5"
    >
      <div className="flex w-full flex-col items-start gap-3">
        <FieldRow label="이름">
          <TextInput
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="name"
          />
        </FieldRow>

        <FieldRow label="닉네임">
          <div className="flex w-[276px] flex-col gap-1">
            <TextInput
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="2~20자, 한글/영문/숫자/_"
              maxLength={20}
              autoComplete="nickname"
            />
            <NicknameStatusHint status={nicknameStatus} />
          </div>
        </FieldRow>

        <FieldRow label="생년월일">
          <div className="flex w-[276px] items-center gap-1.5">
            <SegmentInput
              value={year}
              onChange={(v) => setYear(v.replace(/\D/g, "").slice(0, 4))}
              unit="년"
              className="w-[88px]"
            />
            <SegmentInput
              value={month}
              onChange={(v) => setMonth(v.replace(/\D/g, "").slice(0, 2))}
              unit="월"
              className="w-[88px]"
            />
            <SegmentInput
              value={day}
              onChange={(v) => setDay(v.replace(/\D/g, "").slice(0, 2))}
              unit="일"
              className="w-[88px]"
            />
          </div>
        </FieldRow>

        <FieldRow label="성별">
          <div className="flex w-[276px] items-center gap-1.5">
            {GENDER_OPTIONS.map(({ value, label, widthClass }) => {
              const selected = gender === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => setGender(value)}
                  className={`flex h-12 items-center justify-center rounded-md text-sm leading-[1.5] font-medium tracking-[-0.35px] ${widthClass} ${
                    selected
                      ? "bg-[#27272a] text-white"
                      : "bg-[#e4e4e7] text-[#a1a1aa]"
                  }`}
                >
                  {label}
                </button>
              );
            })}
          </div>
        </FieldRow>
      </div>

      <div className="flex w-full flex-col items-start gap-3">
        <FieldRow label="아이디(이메일)">
          <TextInput
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="이메일"
            autoComplete="email"
          />
        </FieldRow>
        <FieldRow label="비밀번호">
          <div className="flex w-[276px] flex-col gap-1">
            <PasswordInput
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8-15자의 영문/숫자 또는 특수문자 조합"
              autoComplete="new-password"
            />
            <PasswordStatusHint status={passwordStatus} />
          </div>
        </FieldRow>
        <FieldRow label="비밀번호 확인">
          <div className="flex w-[276px] flex-col gap-1">
            <PasswordInput
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              placeholder="비밀번호 재입력"
              autoComplete="new-password"
            />
            <PasswordMatchHint status={passwordMatch} />
          </div>
        </FieldRow>
      </div>

      {error && (
        <p className="w-full text-center text-sm text-destructive">{error}</p>
      )}

      <PrimaryButton
        type="submit"
        disabled={!isValid || submitting}
        className="self-center"
      >
        가입하기
      </PrimaryButton>
    </form>
  );
}

type PasswordStatus = "idle" | "too-short" | "too-long" | "weak" | "valid";
type PasswordMatch = "idle" | "match" | "mismatch";

// 길이 8~15 + 영문/숫자/특수문자 중 2종 이상 조합.
function checkPasswordStatus(pw: string): PasswordStatus {
  if (pw.length === 0) return "idle";
  if (pw.length < 8) return "too-short";
  if (pw.length > 15) return "too-long";
  const groups =
    Number(/[a-zA-Z]/.test(pw)) +
    Number(/\d/.test(pw)) +
    Number(/[^a-zA-Z0-9]/.test(pw));
  if (groups < 2) return "weak";
  return "valid";
}

function PasswordStatusHint({ status }: { status: PasswordStatus }) {
  const map: Record<PasswordStatus, { text: string; tone: string } | null> = {
    idle: null,
    "too-short": { text: "8자 이상 입력하세요", tone: "text-destructive" },
    "too-long": { text: "15자까지 입력 가능합니다", tone: "text-destructive" },
    weak: {
      text: "영문/숫자/특수문자 중 2종 이상 조합해주세요",
      tone: "text-destructive",
    },
    valid: { text: "사용 가능한 비밀번호입니다", tone: "text-emerald-600" },
  };
  const msg = map[status];
  if (!msg) return null;
  return <p className={`px-1 text-xs ${msg.tone}`}>{msg.text}</p>;
}

function PasswordMatchHint({ status }: { status: PasswordMatch }) {
  if (status === "idle") return null;
  const isMatch = status === "match";
  return (
    <p
      className={`px-1 text-xs ${
        isMatch ? "text-emerald-600" : "text-destructive"
      }`}
    >
      {isMatch ? "비밀번호가 일치합니다" : "비밀번호가 일치하지 않습니다"}
    </p>
  );
}

function NicknameStatusHint({ status }: { status: NicknameStatus }) {
  const messages: Record<NicknameStatus, { text: string; tone: string } | null> = {
    idle: null,
    "invalid-format": {
      text: "2~20자, 한글/영문/숫자/_ 만 사용 가능합니다",
      tone: "text-destructive",
    },
    checking: { text: "확인 중...", tone: "text-muted-foreground" },
    available: {
      text: "사용 가능한 닉네임입니다",
      tone: "text-emerald-600",
    },
    taken: {
      text: "이미 사용 중인 닉네임입니다",
      tone: "text-destructive",
    },
    error: {
      text: "확인에 실패했습니다. 잠시 후 다시 시도해주세요",
      tone: "text-destructive",
    },
  };
  const msg = messages[status];
  if (!msg) return null;
  return <p className={`px-1 text-xs ${msg.tone}`}>{msg.text}</p>;
}

function FieldRow({
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

function TextInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`h-12 w-[276px] rounded-md border border-input px-4 py-3 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:font-normal placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/30 ${
        props.className ?? ""
      }`}
    />
  );
}

function PasswordInput({
  className,
  value,
  ...props
}: Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">) {
  const [show, setShow] = useState(false);
  const hasValue = typeof value === "string" && value.length > 0;
  // 입력값 비면 show 상태 리셋 (다음 입력 시 hide부터)
  useEffect(() => {
    if (!hasValue) setShow(false);
  }, [hasValue]);

  return (
    <div className="relative w-[276px]">
      <input
        {...props}
        value={value}
        type={show ? "text" : "password"}
        className={`h-12 w-full rounded-md border border-input py-3 ${
          hasValue ? "pr-12" : "pr-4"
        } pl-4 text-sm leading-[1.5] tracking-[-0.35px] text-foreground placeholder:font-normal placeholder:text-muted-foreground focus:ring-2 focus:ring-primary/30 focus:outline-none ${className ?? ""}`}
      />
      {hasValue && (
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          aria-label={show ? "비밀번호 숨기기" : "비밀번호 보이기"}
          className="absolute top-1/2 right-3 flex size-6 -translate-y-1/2 items-center justify-center text-muted-foreground/70 hover:text-muted-foreground"
        >
          {show ? (
            <Eye className="size-[18px]" />
          ) : (
            <EyeOffIcon className="h-[17.5px] w-[20px]" />
          )}
        </button>
      )}
    </div>
  );
}

function SegmentInput({
  value,
  onChange,
  unit,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  unit: string;
  className?: string;
}) {
  const hasValue = value.length > 0;
  return (
    <div className={`relative ${className ?? ""}`}>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        inputMode="numeric"
        className="h-12 w-full rounded-md border border-input py-3 pr-8 pl-4 text-right text-sm leading-[1.5] font-medium tracking-[-0.35px] text-foreground focus:ring-2 focus:ring-primary/30 focus:outline-none"
      />
      <span
        className={`pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-sm leading-[1.5] tracking-[-0.35px] ${
          hasValue
            ? "font-medium text-foreground"
            : "font-normal text-muted-foreground/70"
        }`}
      >
        {unit}
      </span>
    </div>
  );
}

function PrimaryButton({
  children,
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...props}
      className={`flex h-12 items-center justify-center rounded-md px-10 py-3 text-sm leading-[1.3] font-semibold tracking-[-0.35px] transition-colors ${
        props.disabled
          ? "bg-[#e4e4e7] text-[#a1a1aa]"
          : "bg-[#27272a] text-white hover:bg-[#3f3f46]"
      } ${className ?? ""}`}
    >
      {children}
    </button>
  );
}
