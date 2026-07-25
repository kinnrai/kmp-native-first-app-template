import { describe, expect, it } from 'vitest';
import type { WebTask, WebTaskItem } from 'shared-logic';
import {
  formatDueDate,
  instantToLocalDate,
  isOverdue,
  searchTasks,
} from './taskView.ts';

function item(
  id: string,
  title: string,
  options: {
    completed?: boolean;
    dueDate?: string;
    dueAt?: string;
    notes?: string;
  } = {},
): WebTaskItem {
  const task = {
    id,
    title,
    notes: options.notes,
    priority: 'none',
    dueDate: options.dueDate,
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
  it('searches titles or notes inside an already planned view', () => {
    const tasks = [
      item('1', 'Plan release', { notes: 'Coordinate the team' }),
      item('2', 'Buy groceries', { completed: true }),
    ];

    expect(searchTasks(tasks, '')).toEqual(tasks);
    expect(searchTasks(tasks, 'TEAM')).toEqual([tasks[0]]);
    expect(searchTasks(tasks, 'missing')).toEqual([]);
  });

  it('projects precise instants into the browser calendar date', () => {
    expect(instantToLocalDate('2026-07-24T09:30:00Z')).toBe('2026-07-24');
  });

  it('formats date-only deadlines without converting through UTC', () => {
    expect(formatDueDate('2026-07-24', undefined, 'en-US')).toBe(
      'Jul 24, 2026',
    );
  });

  it('only marks active past-due tasks as overdue', () => {
    const dueAt = '2026-07-24T08:00:00Z';
    const now = Date.parse('2026-07-24T09:00:00Z');

    expect(isOverdue(item('1', 'Late', { dueAt }).task, now)).toBe(true);
    expect(
      isOverdue(item('2', 'Done', { completed: true, dueAt }).task, now),
    ).toBe(false);
    expect(
      isOverdue(item('3', 'Calendar late', { dueDate: '2026-07-23' }).task, now),
    ).toBe(true);
  });
});
