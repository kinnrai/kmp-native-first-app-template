import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { WebTaskSnapshot } from 'shared-logic';
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
    );
  });
});
