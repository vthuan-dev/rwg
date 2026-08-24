"use client";

import React, { useId, useRef, useState } from "react";

export interface TabItem {
  value: string;
  label: string;
  content: React.ReactNode;
}

/**
 * Hai tab Mixing / Special Code.
 *
 * Tự dựng thay vì dùng thư viện: dự án không có Radix, và một cặp tab thì không đáng
 * thêm phụ thuộc mới.
 *
 * Bàn phím hoạt động đúng theo thông lệ của tab: mũi tên trái/phải chuyển tab, Home và
 * End nhảy về đầu/cuối. Chỉ tab đang mở nằm trong thứ tự Tab (`tabIndex`), nên người
 * dùng bàn phím không phải bấm Tab qua từng mục mới tới được nội dung.
 */
export const BetTabs: React.FC<{ tabs: TabItem[] }> = ({ tabs }) => {
  const [active, setActive] = useState(tabs[0]?.value ?? "");
  const baseId = useId();
  const buttonsRef = useRef<(HTMLButtonElement | null)[]>([]);

  const focusTab = (index: number) => {
    const bounded = (index + tabs.length) % tabs.length;
    setActive(tabs[bounded].value);
    buttonsRef.current[bounded]?.focus();
  };

  const onKeyDown = (event: React.KeyboardEvent, index: number) => {
    if (event.key === "ArrowRight") {
      event.preventDefault();
      focusTab(index + 1);
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      focusTab(index - 1);
    } else if (event.key === "Home") {
      event.preventDefault();
      focusTab(0);
    } else if (event.key === "End") {
      event.preventDefault();
      focusTab(tabs.length - 1);
    }
  };

  return (
    <div className="flex flex-col gap-y-0">
      <div
        className="flex w-full rounded-none bg-transparent p-0 shadow-[0_2px_5px_0_rgba(0,0,0,0.1)]"
        role="tablist"
      >
        {tabs.map((tab, index) => {
          const isActive = tab.value === active;
          return (
            <button
              aria-controls={`${baseId}-panel-${tab.value}`}
              aria-selected={isActive}
              className={[
                "flex-1 rounded-none border-b-2 py-3 text-[0.75rem] font-bold text-white",
                isActive
                  ? "border-b-primary bg-[#131313] shadow-none"
                  : "border-b-transparent bg-[#1D1D1D] opacity-25",
              ].join(" ")}
              id={`${baseId}-tab-${tab.value}`}
              key={tab.value}
              onClick={() => setActive(tab.value)}
              onKeyDown={(event) => onKeyDown(event, index)}
              ref={(el) => {
                buttonsRef.current[index] = el;
              }}
              role="tab"
              tabIndex={isActive ? 0 : -1}
              type="button"
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {tabs.map((tab) => (
        <div
          aria-labelledby={`${baseId}-tab-${tab.value}`}
          className="mt-0 flex-1 outline-none"
          hidden={tab.value !== active}
          id={`${baseId}-panel-${tab.value}`}
          key={tab.value}
          role="tabpanel"
          tabIndex={0}
        >
          {tab.content}
        </div>
      ))}
    </div>
  );
};
