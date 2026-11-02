import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';

const ToastContext = createContext<(message: string) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const show = useCallback((m: string) => {
    setMessage(m);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setMessage(null), 3200);
  }, []);
  return (
    <ToastContext.Provider value={show}>
      {children}
      {message && (
        <div role="status" className="fixed right-6 bottom-6 z-[80] flex max-w-sm items-center gap-3 rounded-xl border border-[#2A2C33] bg-[#0A0A0B] px-4 py-3 text-sm text-[#F4F5F7] shadow-2xl">
          <span className="h-2 w-2 flex-none rounded-full bg-rose-neon" />
          {message}
        </div>
      )}
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
