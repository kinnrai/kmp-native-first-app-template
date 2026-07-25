import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

function IconFrame({ children, ...props }: IconProps) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      viewBox="0 0 24 24"
      {...props}
    >
      {children}
    </svg>
  );
}

export function AddIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M12 5v14M5 12h14" />
    </IconFrame>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m5 12 4 4L19 6" />
    </IconFrame>
  );
}

export function ChevronIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m9 18 6-6-6-6" />
    </IconFrame>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M6 6l12 12M18 6 6 18" />
    </IconFrame>
  );
}

export function DeleteIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" />
    </IconFrame>
  );
}

export function EditIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="m4 20 4.2-1 10.6-10.6a2 2 0 0 0-2.8-2.8L5.4 16.2 4 20Z" />
      <path d="m14.5 7.1 2.8 2.8" />
    </IconFrame>
  );
}

export function FolderIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M3 6.5h6l2 2h10v9.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6.5Z" />
      <path d="M3 10h18" />
    </IconFrame>
  );
}

export function InboxIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M4 5h16v14H4zM4 14h4l2 2h4l2-2h4" />
    </IconFrame>
  );
}

export function SearchIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <circle cx="11" cy="11" r="6" />
      <path d="m16 16 4 4" />
    </IconFrame>
  );
}

export function SyncIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M20 7h-5V2M4 17h5v5" />
      <path d="M18.5 11A7 7 0 0 0 6.2 6.2L4 8M5.5 13a7 7 0 0 0 12.3 4.8L20 16" />
    </IconFrame>
  );
}

export function WarningIcon(props: IconProps) {
  return (
    <IconFrame {...props}>
      <path d="M12 3 2.8 20h18.4L12 3Z" />
      <path d="M12 9v5m0 3v.01" />
    </IconFrame>
  );
}
