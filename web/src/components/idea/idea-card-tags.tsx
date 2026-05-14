"use client";

import { useLayoutEffect, useRef, useState } from "react";

import { ChipIdea } from "./chip-idea";

const ELLIPSIS = "…";
const GAP_X = 8;

export function IdeaCardTags({ tags }: { tags: string[] }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const mirrorRef = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<{ count: number; ellipsis: boolean }>({
    count: tags.length,
    ellipsis: false,
  });

  useLayoutEffect(() => {
    const wrap = wrapRef.current;
    const mirror = mirrorRef.current;
    if (!wrap || !mirror) return;

    const compute = () => {
      mirror.style.width = `${wrap.clientWidth}px`;
      const kids = Array.from(mirror.children) as HTMLElement[];
      const tagEls = kids.slice(0, tags.length);
      const ellipsisEl = kids[tags.length];
      if (tagEls.length === 0 || !ellipsisEl) {
        setState({ count: 0, ellipsis: false });
        return;
      }

      const rowTops = Array.from(
        new Set(tagEls.map((k) => k.offsetTop)),
      ).sort((a, b) => a - b);

      if (rowTops.length <= 2) {
        setState({ count: tags.length, ellipsis: false });
        return;
      }

      const row3Top = rowTops[2];
      const firstOverflowIdx = tagEls.findIndex(
        (k) => k.offsetTop >= row3Top,
      );
      const cutoff = firstOverflowIdx <= 0 ? 1 : firstOverflowIdx;

      const lastFit = tagEls[cutoff - 1];
      const ellipsisW = ellipsisEl.offsetWidth;
      const remaining =
        wrap.clientWidth - (lastFit.offsetLeft + lastFit.offsetWidth);
      const take = remaining >= ellipsisW + GAP_X ? cutoff : cutoff - 1;

      setState({ count: Math.max(0, take), ellipsis: true });
    };

    compute();
    const ro = new ResizeObserver(compute);
    ro.observe(wrap);
    return () => ro.disconnect();
  }, [tags]);

  return (
    <div ref={wrapRef} className="relative w-full">
      <div
        ref={mirrorRef}
        aria-hidden
        className="pointer-events-none invisible absolute top-0 left-0 flex flex-wrap gap-x-2 gap-y-2.5"
      >
        {tags.map((t) => (
          <ChipIdea key={t} label={t} />
        ))}
        <ChipIdea label={ELLIPSIS} />
      </div>
      <div className="flex w-full flex-wrap gap-x-2 gap-y-2.5">
        {tags.slice(0, state.count).map((t) => (
          <ChipIdea key={t} label={t} />
        ))}
        {state.ellipsis && <ChipIdea label={ELLIPSIS} />}
      </div>
    </div>
  );
}
