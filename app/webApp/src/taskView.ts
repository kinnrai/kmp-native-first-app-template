import type { WebTask, WebTaskItem } from 'shared-logic';

export type TaskSmartView =
  | 'all'
  | 'inbox'
  | 'today'
  | 'upcoming'
  | 'completed';

export function searchTasks(
  items: readonly WebTaskItem[],
  query: string,
): WebTaskItem[] {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  return items.filter(({ task }) => {
    return (
      normalizedQuery.length === 0 ||
      task.title.toLocaleLowerCase().includes(normalizedQuery) ||
      task.notes?.toLocaleLowerCase().includes(normalizedQuery) === true
    );
  });
}

export function instantToLocalDate(
  instant: string | null | undefined,
): string {
  if (!instant) return '';
  return localDateString(new Date(instant));
}

export function instantToLocalDateTime(
  instant: string | null | undefined,
): string {
  if (!instant) return '';
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return '';
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${localDateString(date)}T${hours}:${minutes}`;
}

export function localDateTimeToInstant(
  value: string | null | undefined,
): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

export function formatReminder(
  instant: string | null | undefined,
  locale?: string,
): string | undefined {
  if (!instant) return undefined;
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return undefined;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

export function localDateString(date: Date): string {
  const year = date.getFullYear().toString().padStart(4, '0');
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const day = date.getDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatDueDate(
  dueDate: string | null | undefined,
  dueAt: string | null | undefined,
  locale?: string,
): string | undefined {
  if (dueDate) {
    const [year, month, day] = dueDate.split('-').map(Number);
    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
    }).format(new Date(year, month - 1, day));
  }
  if (!dueAt) return undefined;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(dueAt));
}

export function isOverdue(
  task: WebTask,
  now: number = Date.now(),
): boolean {
  if (task.isCompleted) return false;
  if (task.dueDate) {
    return task.dueDate < localDateString(new Date(now));
  }
  return (
    task.dueAt != null &&
    new Date(task.dueAt).getTime() < now
  );
}

export function formatLastSynced(
  instant: string | null | undefined,
  locale?: string,
): string {
  if (!instant) return 'Not synced yet';
  return `Last synced ${new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(instant))}`;
}
