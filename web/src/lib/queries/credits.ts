import { createClient } from "@/lib/supabase/server";

export type CreditTransactionType =
  | "CHARGE"
  | "SPEND"
  | "REWARD"
  | "REFUND"
  | "ADJUST";

export type CreditTxRow = {
  id: number;
  amount: number;
  type: CreditTransactionType;
  balanceAfter: number;
  description: string | null;
  createdAt: string;
};

export type CreditOverview = {
  balance: number;
  transactions: CreditTxRow[];
};

export async function fetchCreditOverview(): Promise<CreditOverview | null> {
  const supabase = await createClient();
  const {
    data: { user: authUser },
  } = await supabase.auth.getUser();
  if (!authUser) return null;

  const [{ data: credits }, { data: txs }] = await Promise.all([
    supabase
      .from("user_credits")
      .select("balance")
      .eq("user_id", authUser.id)
      .maybeSingle(),
    supabase
      .from("credit_transactions")
      .select("id, amount, type, balance_after, description, created_at")
      .eq("user_id", authUser.id)
      .order("created_at", { ascending: false })
      .limit(20),
  ]);

  return {
    balance: Number(credits?.balance ?? 0),
    transactions: (txs ?? []).map((t) => ({
      id: t.id,
      amount: t.amount,
      type: t.type as CreditTransactionType,
      balanceAfter: Number(t.balance_after),
      description: t.description,
      createdAt: t.created_at,
    })),
  };
}
