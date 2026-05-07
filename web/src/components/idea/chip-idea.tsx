export function ChipIdea({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center justify-center rounded-full bg-muted px-4 py-2 text-base leading-[1.5] font-semibold tracking-[-0.4px] text-muted-foreground">
      # {label}
    </span>
  );
}
