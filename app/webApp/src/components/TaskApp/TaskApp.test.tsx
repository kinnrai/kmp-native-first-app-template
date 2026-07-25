import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type {
  WebTask,
  WebTaskItem,
  WebTaskProject,
  WebTaskProjectConflict,
  WebTaskProjectItem,
  WebTaskSnapshot,
} from 'shared-logic';
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
    createProject: vi.fn(),
    updateProject: vi.fn(),
    deleteProject: vi.fn(),
    keepLocalProject: vi.fn(),
    useRemoteProject: vi.fn(),
    mergeProjectConflict: vi.fn(),
    plannedTasks: vi.fn(() => []),
    sync: vi.fn(),
    clearActionError: vi.fn(),
  };
}

const readySnapshot = {
  isReady: true,
  tasks: [],
  conflicts: [],
  projects: [],
  projectConflicts: [],
  syncPhase: 'idle',
  pendingCount: 0,
  conflictCount: 0,
  lastSyncedAt: '2026-07-24T08:00:00Z',
  lastError: undefined,
  actionError: undefined,
} as unknown as WebTaskSnapshot;

function projectItem(
  id: string,
  name: string,
  color: string = 'blue',
  syncState: string = 'synced',
): WebTaskProjectItem {
  return {
    project: {
      id,
      name,
      color,
      createdAt: '2026-07-24T08:00:00Z',
      updatedAt: '2026-07-24T08:00:00Z',
      revision: '1',
    } as unknown as WebTaskProject,
    syncState,
  } as unknown as WebTaskProjectItem;
}

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
      undefined,
      undefined,
    );
  });

  it('creates a task in the selected project', async () => {
    const user = userEvent.setup();
    const taskActions = actions();
    const project = projectItem('project-1', 'Website launch', 'purple');
    const snapshot = {
      ...readySnapshot,
      projects: [project],
    } as unknown as WebTaskSnapshot;

    render(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={snapshot}
      />,
    );

    await user.click(screen.getByText('Website launch'));
    await user.click(screen.getByRole('button', { name: 'New task' }));
    await user.type(
      screen.getByPlaceholderText('What needs to be done?'),
      'Write launch copy',
    );
    await user.click(screen.getByRole('button', { name: 'Create task' }));

    expect(taskActions.create).toHaveBeenCalledWith(
      'Write launch copy',
      undefined,
      'none',
      undefined,
      undefined,
      undefined,
      'project-1',
    );
  });

  it('moves a task to Inbox while editing', async () => {
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
      projects: [
        projectItem(
          '22222222-2222-4222-8222-222222222222',
          'Website launch',
        ),
      ],
    } as unknown as WebTaskSnapshot;

    render(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={snapshot}
      />,
    );

    await user.click(screen.getByText('Project task'));
    await user.selectOptions(
      screen.getByLabelText('Project'),
      '',
    );
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(taskActions.update).toHaveBeenCalledWith(
      task.id,
      task.title,
      undefined,
      task.priority,
      undefined,
      undefined,
      undefined,
      false,
      undefined,
    );
  });

  it('creates and deletes a project through the sidebar', async () => {
    const user = userEvent.setup();
    const taskActions = actions();
    const existing = projectItem('project-1', 'Website launch', 'green');
    const snapshot = {
      ...readySnapshot,
      projects: [existing],
    } as unknown as WebTaskSnapshot;
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);

    const { rerender } = render(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={readySnapshot}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Create project' }));
    await user.type(
      screen.getByPlaceholderText('For example, Website launch'),
      '  Product launch  ',
    );
    await user.click(screen.getByLabelText('Purple'));
    await user.click(
      screen
        .getAllByRole('button', { name: 'Create project' })
        .find((button) => button.getAttribute('type') === 'submit')!,
    );

    expect(taskActions.createProject).toHaveBeenCalledWith(
      'Product launch',
      'purple',
    );

    rerender(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={snapshot}
      />,
    );
    await user.click(
      screen.getByRole('button', { name: 'Edit Website launch' }),
    );
    await user.click(
      screen.getByRole('button', { name: 'Delete project' }),
    );

    expect(confirm).toHaveBeenCalledWith(
      'Delete “Website launch”? The project will be removed from this device and the server.',
    );
    expect(taskActions.deleteProject).toHaveBeenCalledWith('project-1');
    confirm.mockRestore();
  });

  it('merges a project conflict from the project navigation', async () => {
    const user = userEvent.setup();
    const taskActions = actions();
    const localProject = projectItem(
      'project-1',
      'Local launch',
      'orange',
      'conflict',
    );
    const conflict = {
      projectId: 'project-1',
      mutationKind: 'update',
      base: localProject.project,
      local: localProject.project,
      remote: {
        ...localProject.project,
        name: 'Server launch',
        color: 'rose',
      } as unknown as WebTaskProject,
      conflictingFields: ['name', 'color'],
      detectedAt: '2026-07-24T09:00:00Z',
    } as unknown as WebTaskProjectConflict;
    const snapshot = {
      ...readySnapshot,
      projects: [localProject],
      projectConflicts: [conflict],
      conflictCount: 1,
    } as unknown as WebTaskSnapshot;

    render(
      <TaskApp
        actions={taskActions}
        isOnline
        snapshot={snapshot}
      />,
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Resolve Local launch conflict',
      }),
    );
    await user.click(screen.getByRole('button', { name: 'Merge manually' }));
    const name = screen.getByLabelText(/Name/);
    await user.clear(name);
    await user.type(name, 'Combined launch');
    await user.click(screen.getByLabelText('Rose'));
    await user.click(
      screen.getByRole('button', { name: 'Save merged project' }),
    );

    expect(taskActions.mergeProjectConflict).toHaveBeenCalledWith(
      'project-1',
      'Combined launch',
      'rose',
    );
  });
});
