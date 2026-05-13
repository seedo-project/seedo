import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

export function AssistantMessage({ content }: { content: string }) {
  return (
    <div className="w-full text-[16px] leading-[1.5] tracking-[-0.4px] text-[#27272a]">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => (
            <h1 className="mb-3 text-[20px] font-bold leading-[1.5] tracking-[-0.5px]">{children}</h1>
          ),
          h2: ({ children }) => (
            <h2 className="mb-3 mt-2 text-[20px] font-bold leading-[1.5] tracking-[-0.5px]">{children}</h2>
          ),
          h3: ({ children }) => (
            <h3 className="mb-2 mt-2 text-[18px] font-bold leading-[1.5] tracking-[-0.45px]">{children}</h3>
          ),
          p: ({ children }) => <p className="mb-3 last:mb-0 whitespace-pre-wrap">{children}</p>,
          ul: ({ children }) => <ul className="mb-3 list-disc pl-5">{children}</ul>,
          ol: ({ children }) => <ol className="mb-3 list-decimal pl-5">{children}</ol>,
          li: ({ children }) => <li className="mb-1">{children}</li>,
          strong: ({ children }) => <strong className="font-bold">{children}</strong>,
          a: ({ children, href }) => (
            <a href={href} target="_blank" rel="noreferrer" className="underline">
              {children}
            </a>
          ),
          code: ({ children }) => (
            <code className="rounded bg-[#f4f4f5] px-1 py-0.5 text-[14px]">{children}</code>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
