import type { WebTaskItem } from 'shared-logic';

export type WebReminderPermission =
  | 'unsupported'
  | 'default'
  | 'granted'
  | 'denied';

export interface WebReminderTask {
  id: string;
  title: string;
  reminderAt: string | null | undefined;
  isCompleted: boolean;
  syncState: string;
}

export function getReminderPermission(): WebReminderPermission {
  if (typeof Notification === 'undefined') return 'unsupported';
  return Notification.permission;
}

/**
 * Requesting notification permission is intentionally kept behind a user
 * action. Browsers increasingly reject permission prompts triggered by page
 * load or other passive effects.
 */
export async function requestReminderPermission(): Promise<WebReminderPermission> {
  if (typeof Notification === 'undefined') return 'unsupported';
  try {
    return await Notification.requestPermission();
  } catch {
    return getReminderPermission();
  }
}

export function showForegroundReminder(task: WebReminderTask): boolean {
  if (getReminderPermission() !== 'granted') return false;
  try {
    const notification = new Notification('Task reminder', {
      body: task.title,
      tag: `task-reminder.${task.id}`,
    });
    notification.onclick = () => window.focus();
    return true;
  } catch {
    // Some mobile browsers expose Notification but only support the
    // ServiceWorkerRegistration.showNotification path for persistent alerts.
    return false;
  }
}

/**
 * Schedules only reminders that can be delivered while this page is open.
 * The caller owns the cleanup function and should recreate the schedule when
 * the task snapshot changes.
 */
export function scheduleForegroundReminders(
  items: readonly WebTaskItem[],
  onDue: (task: WebReminderTask) => void,
  now: number = Date.now(),
): () => void {
  const pending = items
    .map(({ task, syncState }) => ({
      id: task.id,
      title: task.title,
      reminderAt: task.reminderAt,
      isCompleted: task.isCompleted,
      syncState,
    }))
    .filter((task): task is WebReminderTask => {
      if (
        task.isCompleted ||
        task.syncState === 'conflict' ||
        !task.reminderAt
      ) {
        return false;
      }
      const timestamp = Date.parse(task.reminderAt);
      return Number.isFinite(timestamp) && timestamp > now;
    });

  let timer: number | undefined;
  let cancelled = false;

  const scheduleNext = () => {
    if (cancelled || pending.length === 0) return;

    pending.sort(
      (left, right) =>
        Date.parse(left.reminderAt ?? '') - Date.parse(right.reminderAt ?? ''),
    );
    const next = pending[0];
    if (!next?.reminderAt) return;

    const delay = Math.max(Date.parse(next.reminderAt) - Date.now(), 1);
    // setTimeout has a signed 32-bit millisecond limit. Re-arm long-future
    // reminders in chunks so they still work for dates more than 24 days away.
    timer = window.setTimeout(() => {
      if (cancelled) return;
      const dueAt = Date.now();
      const due = pending.filter(
        (task) =>
          task.reminderAt != null && Date.parse(task.reminderAt) <= dueAt,
      );
      due.forEach((task) => {
        const index = pending.indexOf(task);
        if (index >= 0) pending.splice(index, 1);
        onDue(task);
      });
      scheduleNext();
    }, Math.min(delay, 2_147_483_647));
  };

  scheduleNext();
  return () => {
    cancelled = true;
    if (timer !== undefined) window.clearTimeout(timer);
  };
}
