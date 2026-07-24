import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { WebTask, WebTaskItem, WebTaskSnapshot } from 'shared-logic';
import type { TaskActions } from '../../taskActions.ts';
import { TaskApp } from './TaskApp.tsx';

function actions(): TaskActions {
  return {
    create: vi.fn(),
    update: vi.fn(),
    toggleCompleted: vi.fn(),
    delete: vi.fn(),
    clearCompleted: vi.fn(),
    keepLocal: vi.fn(),
    useRemote: vi.fn(),
    mergeConflict: vi.fn(),
    plannedTasks: vi.fn(() => []),
    sync: vi.fn(),
    clearActionError: vi.fn(),
  };
}

const readySnapshot = {
  isReady: true,
  tasks: [],
  conflicts: [],
  syncPhase: 'idle',
  pendingCount: 0,
  conflictCount: 0,
  lastSyncedAt: '2026-07-24T08:00:00Z',
  lastError: undefined,
  actionError: undefined,
} as unknown as WebTaskSnapshot;

describe('TaskApp', () => {
  it('creates a task from the empty state', async () => {
    const user = userEvent.setup();
    const taskActions = actions();
    render(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={readySnapshot}
      />,
    );

    expect(
      screen.getByRole('heading', { name: 'Nothing here yet' }),
    ).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'New task' }));
    await user.type(
      screen.getByPlaceholderText('What needs to be done?'),
      'Review pull request',
    );
    await user.click(screen.getByRole('button', { name: 'Create task' }));

    expect(taskActions.create).toHaveBeenCalledWith(
      'Review pull request',
      undefined,
      'none',
      undefined,
      undefined,
      null,
    );
  });

  it('preserves a task project while editing fields', async () => {
    const user = userEvent.setup();
    const taskActions = actions();
    const task = {
      id: 'task-1',
      title: 'Project task',
      notes: undefined,
      projectId: '22222222-2222-4222-8222-222222222222',
      priority: 'none',
      dueDate: undefined,
      dueAt: undefined,
      isCompleted: false,
      createdAt: '2026-07-24T08:00:00Z',
      updatedAt: '2026-07-24T08:00:00Z',
      revision: '1',
    } as unknown as WebTask;
    const item = {
      task,
      syncState: 'synced',
    } as unknown as WebTaskItem;
    taskActions.plannedTasks = vi.fn(() => [item]);
    const snapshot = {
      ...readySnapshot,
      tasks: [item],
    } as unknown as WebTaskSnapshot;

    render(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={snapshot}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Project task' }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(taskActions.update).toHaveBeenCalledWith(
      task.id,
      task.title,
      undefined,
      task.priority,
      undefined,
      undefined,
      false,
      task.projectId,
    );
  });
});
