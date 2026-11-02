export function LogoMark({ size = 32, className = '' }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className={className}>
      <rect x="0.5" y="0.5" width="31" height="31" rx="9" fill="#0A0A0B" stroke="#2A2C33" />
      <rect x="8" y="7" width="4.5" height="18" rx="2.25" fill="#1DA1D8" />
      <rect x="8" y="7" width="16" height="4.5" rx="2.25" fill="#1DA1D8" />
      <rect x="8" y="14.5" width="9" height="4.5" rx="2.25" fill="#FF2E7E" />
      <circle cx="21.5" cy="16.75" r="2.25" fill="#FF2E7E" />
    </svg>
  );
}

export function Logo({ size = 32 }: { size?: number }) {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark size={size} />
      <span className="font-display text-xl font-bold tracking-tight">Flowlane</span>
    </span>
  );
}
