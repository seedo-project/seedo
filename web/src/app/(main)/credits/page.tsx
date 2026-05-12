import { redirect } from "next/navigation";

import { CoinIcon } from "@/components/idea/idea-icons";
import { fetchCreditOverview } from "@/lib/queries/credits";
import { formatRelativeKo } from "@/lib/format";

import { TopUpButton } from "./_components/top-up-button";

const TYPE_LABEL: Record<string, { label: string; color: string }> = {
  CHARGE: { label: "충전", color: "text-emerald-600" },
  REWARD: { label: "채택 보상", color: "text-emerald-600" },
  REFUND: { label: "환불", color: "text-emerald-600" },
  SPEND: { label: "구매", color: "text-foreground" },
  ADJUST: { label: "관리자 조정", color: "text-muted-foreground" },
};

export default async function CreditsPage() {
  const data = await fetchCreditOverview();
  if (!data) redirect("/login");

  return (
    <main className="mx-auto w-[720px] pt-10 pb-16">
      <section className="flex flex-col items-start gap-2 rounded-2xl border border-border bg-card px-7 py-6">
        <p className="text-sm font-medium text-muted-foreground">보유 크레딧</p>
        <div className="flex items-center gap-3">
          <CoinIcon className="size-7 text-primary" aria-hidden />
          <span className="text-3xl font-bold tracking-[-0.6px] text-foreground">
            {data.balance.toLocaleString("ko-KR")}
          </span>
          <span className="text-base font-medium text-muted-foreground">
            크레딧
          </span>
        </div>
        <div className="mt-3">
          <TopUpButton />
        </div>
      </section>

      <section className="mt-10 flex flex-col gap-3">
        <h2 className="text-base font-semibold text-foreground">최근 거래 내역</h2>
        {data.transactions.length === 0 ? (
          <div className="rounded-md border border-border px-5 py-10 text-center text-sm text-muted-foreground">
            아직 거래 내역이 없습니다
          </div>
        ) : (
          <ul className="flex flex-col divide-y divide-border rounded-md border border-border">
            {data.transactions.map((tx) => {
              const meta =
                TYPE_LABEL[tx.type] ?? {
                  label: tx.type,
                  color: "text-foreground",
                };
              const sign = tx.amount > 0 ? "+" : "";
              return (
                <li
                  key={tx.id}
                  className="flex items-center justify-between px-5 py-3"
                >
                  <div className="flex flex-col">
                    <span className={`text-sm font-semibold ${meta.color}`}>
                      {meta.label}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {tx.description ?? ""} · {formatRelativeKo(tx.createdAt)}
                    </span>
                  </div>
                  <div className="flex flex-col items-end">
                    <span
                      className={`text-sm font-bold ${
                        tx.amount > 0
                          ? "text-emerald-600"
                          : "text-foreground"
                      }`}
                    >
                      {sign}
                      {tx.amount.toLocaleString("ko-KR")}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      잔액 {tx.balanceAfter.toLocaleString("ko-KR")}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </main>
  );
}
