import type { WebTask, WebTaskItem } from 'shared-logic';

export type TaskFilter = 'all' | 'active' | 'completed';

export function filterTasks(
  items: readonly WebTaskItem[],
  filter: TaskFilter,
  query: string,
): WebTaskItem[] {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  return items.filter(({ task }) => {
    const matchesFilter =
      filter === 'all' ||
      (filter === 'active' && !task.isCompleted) ||
      (filter === 'completed' && task.isCompleted);
    const matchesQuery =
      normalizedQuery.length === 0 ||
      task.title.toLocaleLowerCase().includes(normalizedQuery) ||
      task.notes?.toLocaleLowerCase().includes(normalizedQuery) === true;
    return matchesFilter && matchesQuery;
  });
}

export function toDateTimeLocal(instant: string | null | undefined): string {
  if (!instant) return '';
  const date = new Date(instant);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function fromDateTimeLocal(value: string): string | undefined {
  return value ? new Date(value).toISOString() : undefined;
}

export function formatDueDate(
  instant: string | null | undefined,
  locale?: string,
): string | undefined {
  if (!instant) return undefined;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(instant));
}

export function isOverdue(
  task: WebTask,
  now: number = Date.now(),
): boolean {
  return (
    !task.isCompleted &&
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
