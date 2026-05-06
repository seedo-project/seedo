"use client";

import { Eye } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { type FormEvent, type SVGProps, useEffect, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { createClient } from "@/lib/supabase/client";

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

const TERMS_TITLE = "서비스 이용약관동의(필수)";
const TERMS_BODY = `개정일자 : 2026년 1월 29일
제1조 (목적)
본 약관은 웍스피어 유한책임회사 (이하 "회사")가 운영하는 "서비스"를 이용함에 있어 "회사"와 회원간의 이용 조건 및 제반 절차, 권리, 의무 및 책임사항, 기타 필요한 사항을 규정함을 목적으로 한다.
제2조 (용어의 정의)
이 약관에서 사용하는 용어의 정의는 아래와 같다.
① "사이트"라 함은 회사가 서비스를 "회원"에게 제공하기 위하여 컴퓨터 등 정보통신설비를 이용하여 설정한 가상의 영업장 또는 회사가 운영하는 웹사이트, 모바일웹, 앱 등의 서비스를 제공하는 모든 매체를 통칭하며, 통합된 하나의 회원 계정(아이디 및 비밀번호)을 이용하여 서비스를 제공받을 수 있는 아래의 사이트를 말한다.
- www.jobkorea.co.kr
- www.albamon.com
- m.jobkorea.co.kr
- m.albamon.com
② "서비스"라 함은 회사가 운영하는 사이트를 통해 개인이 구직, 교육 등의 목적으로 등록하는 자료를 DB화하여 각각의 목적에 맞게 분류 가공, 집계하여 정보를 제공하는 서비스와 사이트에서 제공하는 모든 부대 서비스를 말한다.
③ "회원"이라 함은 "회사"가 제공하는 서비스를 이용하거나 이용하려는 자로, 페이스북 등 외부서비스 연동을 통해 "회사"와 이용계약을 체결한자 또는 체결하려는 자를 포함하며, 아이디와 비밀번호의 설정 등 회원가입 절차를 거쳐 회원가입확인 이메일 등을 통해 회사로부터 회원으로 인정받은 "개인회원"을 말한다.
④ "아이디"라 함은 회원가입시 회원의 식별과 회원의 서비스 이용을 위하여 회원이 선정하고 "회사"가 부여하는 문자와 숫자의 조합을 말한다.
⑤ "비밀번호"라 함은 위 제4항에 따라 회원이 회원가입시 아이디를 설정하면서 아이디를 부여받은 자와 동일인임을 확인하고 "회원"의 권익을 보호하기 위하여 "회원"이 선정한 문자와 숫자의 조합을 말한다.
⑥ "비회원"이라 함은 회원가입절차를 거치지 않고 "회사"가 제공하는 서비스를 이용하거나 하려 는 자를 말한다.
제3조 (약관의 명시와 개정)
① "회사"는 이 약관의 내용과 상호, 영업소 소재지, 대표자의 성명, 사업자등록번호, 연락처 등을 이용자가 알 수 있도록 초기 화면에 게시하거나 기타의 방법으로 이용자에게 공지해야 한다.
② "회사"는 약관의 규제에 관한 법률, 전기통신기본법, 전기통신사업법, 정보통신망 이용촉진 및 정보보호 등에 관한 법률 등 관련법을 위배하지 않는 범위에서 이 약관을 개정할 수 있다.`;

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
    <main className="flex min-h-svh items-center justify-center bg-[#f4f4f5] px-4 py-12">
      <div className="flex w-[440px] flex-col items-center gap-5 rounded-xl bg-white px-5 pb-5">
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
            <h1 className="text-xl font-bold tracking-[-0.5px] text-[#27272a]">
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
  const [allAgreed, setAllAgreed] = useState(false);
  const [marketing, setMarketing] = useState(false);

  return (
    <div className="flex w-[400px] flex-col items-start gap-3">
      <div className="h-[440px] w-full overflow-y-auto rounded-md border border-[#e4e4e7] p-4">
        <p className="text-sm leading-[1.5] font-bold tracking-[-0.35px] text-[#71717a]">
          {TERMS_TITLE}
        </p>
        <p className="text-sm leading-[1.5] font-medium tracking-[-0.35px] whitespace-pre-line text-[#71717a]">
          {TERMS_BODY}
        </p>
      </div>

      <div className="flex w-full flex-col items-start gap-2 px-3">
        <label className="flex cursor-pointer items-center gap-2 select-none">
          <Checkbox
            checked={allAgreed}
            onCheckedChange={(v) => {
              const checked = v === true;
              setAllAgreed(checked);
              setMarketing(checked);
            }}
          />
          <span className="text-sm leading-[1.5] tracking-[-0.35px] text-[#71717a]">
            <span className="font-bold text-[#52525b]">전체동의</span>{" "}
            <span className="font-medium">
              (필수) 선택항목 포함 모든 약관에 동의합니다.
            </span>
          </span>
        </label>
        <label className="flex cursor-pointer items-center gap-2 select-none">
          <Checkbox
            checked={marketing}
            onCheckedChange={(v) => {
              const checked = v === true;
              setMarketing(checked);
              // 마케팅이 유일한 선택 항목이므로 allAgreed와 동기화
              setAllAgreed(checked);
            }}
          />
          <span className="text-sm leading-[1.5] font-medium tracking-[-0.35px] text-[#71717a]">
            (선택) 광고성 정보 이메일/SMS 수신 동의
          </span>
        </label>
      </div>

      <PrimaryButton
        type="button"
        disabled={!allAgreed}
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

function InfoStep({ marketingConsent }: { marketingConsent: boolean }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [year, setYear] = useState("");
  const [month, setMonth] = useState("");
  const [day, setDay] = useState("");
  const [gender, setGender] = useState<Gender | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  const isValid =
    name.trim().length > 0 &&
    isValidBirthDate &&
    gender !== null &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) &&
    password.length >= 8 &&
    password.length <= 15 &&
    password === passwordConfirm;

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
          birth_date: `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`,
          gender,
          marketing_consent: marketingConsent,
        },
      },
    });
    setSubmitting(false);
    if (signUpError) {
      setError(signUpError.message);
      return;
    }
    router.push("/idea");
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
          <PasswordInput
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="8-15자의 영문/숫자 또는 특수문자 조합"
            autoComplete="new-password"
          />
        </FieldRow>
        <FieldRow label="비밀번호 확인">
          <PasswordInput
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            placeholder="비밀번호 재입력"
            autoComplete="new-password"
          />
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

function FieldRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex w-full items-center justify-between">
      <span className="text-base leading-[1.5] font-semibold tracking-[-0.4px] text-[#27272a]">
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
      className={`h-12 w-[276px] rounded-md border border-[#d4d4d8] px-4 py-3 text-sm leading-[1.5] tracking-[-0.35px] text-[#27272a] placeholder:font-normal placeholder:text-[#d4d4d8] focus:outline-none focus:ring-2 focus:ring-primary/30 ${
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
        className={`h-12 w-full rounded-md border border-[#d4d4d8] py-3 ${
          hasValue ? "pr-12" : "pr-4"
        } pl-4 text-sm leading-[1.5] tracking-[-0.35px] text-[#27272a] placeholder:font-normal placeholder:text-[#d4d4d8] focus:ring-2 focus:ring-primary/30 focus:outline-none ${className ?? ""}`}
      />
      {hasValue && (
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          aria-label={show ? "비밀번호 숨기기" : "비밀번호 보이기"}
          className="absolute top-1/2 right-3 flex size-6 -translate-y-1/2 items-center justify-center text-[#d4d4d8] hover:text-[#71717a]"
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
        className="h-12 w-full rounded-md border border-[#d4d4d8] py-3 pr-8 pl-4 text-right text-sm leading-[1.5] font-medium tracking-[-0.35px] text-[#27272a] focus:ring-2 focus:ring-primary/30 focus:outline-none"
      />
      <span
        className={`pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-sm leading-[1.5] tracking-[-0.35px] ${
          hasValue
            ? "font-medium text-[#27272a]"
            : "font-normal text-[#d4d4d8]"
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
