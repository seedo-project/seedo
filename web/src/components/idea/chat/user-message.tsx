export function UserMessage({ content }: { content: string }) {
  return (
    <div className="flex w-full justify-end">
      <div className="max-w-[505px] rounded-3xl bg-[#f4f4f5] px-4 py-3 text-[16px] leading-[1.5] tracking-[-0.4px] text-[#27272a] whitespace-pre-wrap">
        {content}
      </div>
    </div>
  );
}
