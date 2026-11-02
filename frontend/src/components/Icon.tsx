const PATHS = {
  dashboard: 'M3 3h7v9H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 16h7v5H3z',
  tasks: 'M9 11l3 3 8-8M20 12v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h9',
  calendar: 'M4 5h16v16H4zM4 10h16M9 3v4M15 3v4',
  reports: 'M5 20V11M12 20V5M19 20v-6M3 20h18',
  members: 'M13 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0M2 21v-1a7 7 0 0 1 14 0v1M17 4a4 4 0 0 1 0 8M22 21v-1a6 6 0 0 0-4-5.6',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 0 0 4 0',
  settings: 'M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1M15 4v4M9 10v4M17 16v4',
  search: 'M17 11a6 6 0 1 1-12 0a6 6 0 1 1 12 0M20 20l-4-4',
  sun: 'M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0',
  moon: 'M21 13A9 9 0 1 1 11 3a7 7 0 0 0 10 10z',
  chevron: 'M7 10l5 5 5-5',
  logout: 'M15 4h4v16h-4M10 17l5-5-5-5M15 12H3',
  plus: 'M12 5v14M5 12h14',
  x: 'M6 6l12 12M18 6L6 18',
  clip: 'M21 11l-8.5 8.5a5 5 0 0 1-7-7L14 4a3.5 3.5 0 0 1 5 5l-8.5 8.5a2 2 0 0 1-3-3L15 7',
  menu: 'M4 6h16M4 12h16M4 18h16',
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, size = 18, className = '' }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className}>
      <path d={PATHS[name]} />
    </svg>
  );
}
