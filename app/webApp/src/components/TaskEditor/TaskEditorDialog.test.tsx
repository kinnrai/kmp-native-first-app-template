import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TaskEditorDialog } from './TaskEditorDialog.tsx';

describe('TaskEditorDialog', () => {
  it('normalizes and submits a new task', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(
      <TaskEditorDialog
        heading="Create a task"
        onCancel={vi.fn()}
        onSubmit={onSubmit}
        submitLabel="Create task"
      />,
    );

    await user.type(
      screen.getByPlaceholderText('What needs to be done?'),
      '  Plan release  ',
    );
    await user.type(
      screen.getByPlaceholderText('Add context, links, or next steps'),
      '  Coordinate launch  ',
    );
    await user.selectOptions(screen.getByLabelText('Priority'), 'high');
    fireEvent.input(screen.getByLabelText('Due date'), {
      target: { value: '2026-07-25' },
    });
    await user.click(screen.getByRole('button', { name: 'Create task' }));

    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Plan release',
      notes: 'Coordinate launch',
      priority: 'high',
      dueDate: '2026-07-25',
      dueAt: undefined,
      isCompleted: false,
    });
  });
});
