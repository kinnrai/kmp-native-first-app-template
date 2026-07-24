import { useId, useState } from 'react';
import type { KeyboardEvent, MouseEvent } from 'react';
import type { WebTask, WebTaskConflict } from 'shared-logic';
import type { TaskActions } from '../../taskActions.ts';
import { formatDueDate } from '../../taskView.ts';
import { CloseIcon, WarningIcon } from '../Icons.tsx';
import {
  TaskEditorDialog,
} from '../TaskEditor/TaskEditorDialog.tsx';
import type { TaskEditorValues } from '../TaskEditor/TaskEditorDialog.tsx';

interface TaskConflictDialogProps {
  actions: TaskActions;
  conflict: WebTaskConflict;
  onClose(): void;
}

const fieldNames: Record<string, string> = {
  creation: 'creation',
  deletion: 'deletion',
  title: 'title',
  notes: 'notes',
  priority: 'priority',
  due_date: 'due date',
  due_at: 'due date',
  completion: 'completion',
};

export function TaskConflictDialog({
  actions,
  conflict,
  onClose,
}: TaskConflictDialogProps) {
  const headingId = useId();
  const [isMerging, setIsMerging] = useState(false);
  const mergeSource = conflict.local ?? conflict.remote;

  const finish = (action: () => void) => {
    action();
    onClose();
  };

  const merge = (values: TaskEditorValues) => {
    finish(() => {
      actions.mergeConflict(
        conflict.taskId,
        values.title,
        values.notes,
        values.priority,
        values.dueDate,
        values.dueAt,
        values.isCompleted,
      );
    });
  };

  if (isMerging && mergeSource) {
    return (
      <TaskEditorDialog
        heading="Merge task versions"
        initialTask={mergeSource}
        onCancel={() => setIsMerging(false)}
        onSubmit={merge}
        submitLabel="Save merged task"
      />
    );
  }

  const closeFromBackdrop = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose();
  };

  const closeFromKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') onClose();
  };

  return (
    <div
      className="dialog-backdrop"
      onKeyDown={closeFromKeyboard}
      onMouseDown={closeFromBackdrop}
    >
      <section
        aria-labelledby={headingId}
        aria-modal="true"
        className="dialog-card conflict-dialog"
        role="dialog"
      >
        <header className="dialog-header">
          <div className="conflict-heading">
            <span className="warning-symbol">
              <WarningIcon />
            </span>
            <div>
              <p className="eyebrow">Sync conflict</p>
              <h2 id={headingId}>Choose which changes to keep</h2>
            </div>
          </div>
          <button
            aria-label="Close conflict dialog"
            className="icon-button"
            onClick={onClose}
            type="button"
          >
            <CloseIcon />
          </button>
        </header>

        <p className="conflict-explanation">
          This task changed here and on the server before either version could
          be synchronized. Review both versions before continuing.
        </p>

        <p className="conflict-fields">
          Conflicting fields:{' '}
          <strong>
            {conflict.conflictingFields
              .map((field) => fieldNames[field] ?? field)
              .join(', ')}
          </strong>
        </p>

        <div className="version-grid">
          <TaskVersionCard
            emptyLabel="You deleted this task"
            label="On this device"
            task={conflict.local}
          />
          <TaskVersionCard
            emptyLabel="Deleted on the server"
            label="On the server"
            task={conflict.remote}
          />
        </div>

        <footer className="dialog-actions conflict-actions">
          <button
            className="button button-secondary"
            onClick={() => finish(() => actions.useRemote(conflict.taskId))}
            type="button"
          >
            {conflict.remote ? 'Use server version' : 'Accept server deletion'}
          </button>
          {mergeSource && (
            <button
              className="button button-secondary"
              onClick={() => setIsMerging(true)}
              type="button"
            >
              Merge manually
            </button>
          )}
          <button
            className="button button-primary"
            onClick={() => finish(() => actions.keepLocal(conflict.taskId))}
            type="button"
          >
            {conflict.local ? 'Keep my version' : 'Keep my deletion'}
          </button>
        </footer>
      </section>
    </div>
  );
}

interface TaskVersionCardProps {
  emptyLabel: string;
  label: string;
  task: WebTask | null | undefined;
}

function TaskVersionCard({
  emptyLabel,
  label,
  task,
}: TaskVersionCardProps) {
  return (
    <article className="version-card">
      <p className="version-label">{label}</p>
      {task ? (
        <>
          <h3>{task.title}</h3>
          <p>{task.notes || 'No notes'}</p>
          <dl>
            <div>
              <dt>Priority</dt>
              <dd>{task.priority}</dd>
            </div>
            <div>
              <dt>Due</dt>
              <dd>
                {formatDueDate(task.dueDate, task.dueAt) ?? 'No due date'}
              </dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{task.isCompleted ? 'Completed' : 'Active'}</dd>
            </div>
          </dl>
        </>
      ) : (
        <div className="deleted-version">
          <span aria-hidden="true">∅</span>
          <p>{emptyLabel}</p>
        </div>
      )}
    </article>
  );
}
