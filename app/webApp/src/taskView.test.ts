import { describe, expect, it } from 'vitest';
import type { WebTask, WebTaskItem } from 'shared-logic';
import {
  filterTasks,
  fromDateTimeLocal,
  isOverdue,
  toDateTimeLocal,
} from './taskView.ts';

function item(
  id: string,
  title: string,
  options: {
    completed?: boolean;
    dueAt?: string;
    notes?: string;
  } = {},
): WebTaskItem {
  const task = {
    id,
    title,
    notes: options.notes,
    priority: 'none',
    dueAt: options.dueAt,
    isCompleted: options.completed ?? false,
    createdAt: '2026-07-24T00:00:00Z',
    updatedAt: '2026-07-24T00:00:00Z',
    revision: '1',
  } as unknown as WebTask;
  return {
    task,
    syncState: 'synced',
  } as unknown as WebTaskItem;
}

describe('task view helpers', () => {
  it('filters completion state and searches titles or notes', () => {
    const tasks = [
      item('1', 'Plan release', { notes: 'Coordinate the team' }),
      item('2', 'Buy groceries', { completed: true }),
    ];

    expect(filterTasks(tasks, 'active', '')).toEqual([tasks[0]]);
    expect(filterTasks(tasks, 'completed', '')).toEqual([tasks[1]]);
    expect(filterTasks(tasks, 'all', 'TEAM')).toEqual([tasks[0]]);
    expect(filterTasks(tasks, 'all', 'missing')).toEqual([]);
  });

  it('round-trips a browser-local date through an API instant', () => {
    const localValue = '2026-07-24T09:30';

    expect(toDateTimeLocal(fromDateTimeLocal(localValue))).toBe(localValue);
  });

  it('only marks active past-due tasks as overdue', () => {
    const dueAt = '2026-07-24T08:00:00Z';
    const now = Date.parse('2026-07-24T09:00:00Z');

    expect(isOverdue(item('1', 'Late', { dueAt }).task, now)).toBe(true);
    expect(
      isOverdue(item('2', 'Done', { completed: true, dueAt }).task, now),
    ).toBe(false);
  });
});
