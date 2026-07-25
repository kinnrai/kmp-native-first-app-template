import { describe, expect, it, vi } from 'vitest';
import type { WebTask, WebTaskItem } from 'shared-logic';
import { scheduleForegroundReminders } from './reminders.ts';

function item(
  id: string,
  reminderAt: string | undefined,
  options: { completed?: boolean; syncState?: string } = {},
): WebTaskItem {
  return {
    task: {
      id,
      title: `Task ${id}`,
      notes: undefined,
      projectId: undefined,
      priority: 'none',
      dueDate: undefined,
      dueAt: undefined,
      isCompleted: options.completed ?? false,
      createdAt: '2026-07-24T00:00:00Z',
      updatedAt: '2026-07-24T00:00:00Z',
      revision: '1',
      reminderAt,
    } as unknown as WebTask,
    syncState: options.syncState ?? 'synced',
  } as unknown as WebTaskItem;
}

describe('foreground reminder scheduling', () => {
  it('delivers only future, active, non-conflicting reminders', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-24T09:00:00Z'));
    const onDue = vi.fn();
    const cleanup = scheduleForegroundReminders(
      [
        item('future', '2026-07-24T09:01:00Z'),
        item('completed', '2026-07-24T09:01:00Z', { completed: true }),
        item('conflict', '2026-07-24T09:01:00Z', { syncState: 'conflict' }),
        item('past', '2026-07-24T08:59:00Z'),
      ],
      onDue,
      Date.now(),
    );

    vi.advanceTimersByTime(60_000);
    expect(onDue).toHaveBeenCalledTimes(1);
    expect(onDue).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'future' }),
    );
    cleanup();
    vi.useRealTimers();
  });
});
