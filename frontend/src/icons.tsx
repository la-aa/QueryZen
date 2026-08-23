interface IconProps {
  size?: number
  className?: string
}

function base(props: IconProps, children: React.ReactNode) {
  return (
    <svg
      width={props.size ?? 16}
      height={props.size ?? 16}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={props.className}
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

export function LogoIcon(props: IconProps) {
  return base(props, (
    <>
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5v14c0 1.66 3.58 3 8 3s8-1.34 8-3V5" />
      <path d="M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3" />
    </>
  ))
}

export function QueryIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M4 6h16M4 12h16M4 18h16" />
    </>
  ))
}

export function AuditIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M12 2 4 6v5c0 5.25 3.4 9.74 8 11 4.6-1.26 8-5.75 8-11V6l-8-4Z" />
      <path d="m9 12 2 2 4-4" />
    </>
  ))
}

export function UserIcon(props: IconProps) {
  return base(props, (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.58-7 8-7s8 3 8 7" />
    </>
  ))
}

export function UsersIcon(props: IconProps) {
  return base(props, (
    <>
      <circle cx="9" cy="8" r="4" />
      <path d="M2 21c0-3.31 3.13-6 7-6s7 2.69 7 6" />
      <path d="M16 4.5a4 4 0 0 1 0 7" />
      <path d="M22 21c0-2.6-1.9-4.77-4.5-5.54" />
    </>
  ))
}

export function PlayIcon(props: IconProps) {
  return base(props, (
    <path d="m7 4 13 8-13 8V4Z" />
  ))
}

export function DownloadIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M12 3v12m0 0 4-4m-4 4-4-4" />
      <path d="M4 17v3h16v-3" />
    </>
  ))
}

export function RefreshIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M21 12a9 9 0 1 1-2.64-6.36" />
      <path d="M21 3v6h-6" />
    </>
  ))
}

export function ShieldCheckIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M12 2 4 6v5c0 5.25 3.4 9.74 8 11 4.6-1.26 8-5.75 8-11V6l-8-4Z" />
      <path d="m9 12 2 2 4-4" />
    </>
  ))
}

export function EyeIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
    </>
  ))
}

export function EyeOffIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M3 3l18 18" />
      <path d="M10.6 10.6a3 3 0 0 0 4.2 4.2" />
      <path d="M9.5 5.2A9.8 9.8 0 0 1 12 5c6.5 0 10 7 10 7a17.3 17.3 0 0 1-2.25 3.17M6.6 6.6C3.9 8.3 2 12 2 12s3.5 7 10 7c1.17 0 2.26-.25 3.26-.68" />
    </>
  ))
}

export function LogoutIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="m16 17 5-5-5-5M21 12H9" />
    </>
  ))
}

export function LockIcon(props: IconProps) {
  return base(props, (
    <>
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </>
  ))
}

export function DatabaseIcon(props: IconProps) {
  return base(props, (
    <>
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5v14c0 1.66 3.58 3 8 3s8-1.34 8-3V5" />
      <path d="M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3" />
    </>
  ))
}

export function TableIcon(props: IconProps) {
  return base(props, (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M3 10h18M9 4v16" />
    </>
  ))
}

export function AlertIcon(props: IconProps) {
  return base(props, (
    <>
      <path d="M12 3 2 20h20L12 3Z" />
      <path d="M12 9v4M12 17h.01" />
    </>
  ))
}

export function ClockIcon(props: IconProps) {
  return base(props, (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ))
}

export function KeyIcon(props: IconProps) {
  return base(props, (
    <>
      <circle cx="8" cy="15" r="4" />
      <path d="m11 12 9-9M16 8l-2 2" />
    </>
  ))
}