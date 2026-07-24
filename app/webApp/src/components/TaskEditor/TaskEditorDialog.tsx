import { useId, useState } from 'react';
import type { FormEvent, KeyboardEvent, MouseEvent } from 'react';
import type { WebTask } from 'shared-logic';
import { CloseIcon } from '../Icons.tsx';
import { instantToLocalDate } from '../../taskView.ts';

export interface TaskEditorValues {
  title: string;
  notes?: string;
  priority: string;
  dueDate?: string;
  dueAt?: string;
  isCompleted: boolean;
}

interface TaskEditorDialogProps {
  heading: string;
  initialTask?: WebTask;
  submitLabel: string;
  onCancel(): void;
  onSubmit(values: TaskEditorValues): void;
}

export function TaskEditorDialog({
  heading,
  initialTask,
  submitLabel,
  onCancel,
  onSubmit,
}: TaskEditorDialogProps) {
  const headingId = useId();
  const descriptionId = useId();
  const [title, setTitle] = useState(initialTask?.title ?? '');
  const [notes, setNotes] = useState(initialTask?.notes ?? '');
  const [priority, setPriority] = useState(initialTask?.priority ?? 'none');
  const [dueDate, setDueDate] = useState(
    initialTask?.dueDate ?? instantToLocalDate(initialTask?.dueAt),
  );
  const [preciseDueAt, setPreciseDueAt] = useState(
    initialTask?.dueDate ? undefined : initialTask?.dueAt ?? undefined,
  );
  const [isCompleted, setIsCompleted] = useState(
    initialTask?.isCompleted ?? false,
  );

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedTitle = title.trim();
    if (!normalizedTitle) return;
    onSubmit({
      title: normalizedTitle,
      notes: notes.trim() || undefined,
      priority,
      dueDate: preciseDueAt ? undefined : dueDate || undefined,
      dueAt: preciseDueAt,
      isCompleted,
    });
  };

  const closeFromBackdrop = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onCancel();
  };

  const closeFromKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') onCancel();
  };

  return (
    <div
      className="dialog-backdrop"
      onKeyDown={closeFromKeyboard}
      onMouseDown={closeFromBackdrop}
    >
      <section
        aria-describedby={descriptionId}
        aria-labelledby={headingId}
        aria-modal="true"
        className="dialog-card editor-dialog"
        role="dialog"
      >
        <header className="dialog-header">
          <div>
            <p className="eyebrow">Task details</p>
            <h2 id={headingId}>{heading}</h2>
            <p className="sr-only" id={descriptionId}>
              Enter a title and optional task details.
            </p>
          </div>
          <button
            aria-label="Close task editor"
            className="icon-button"
            onClick={onCancel}
            type="button"
          >
            <CloseIcon />
          </button>
        </header>

        <form className="editor-form" onSubmit={submit}>
          <label className="field field-wide">
            <span>Title</span>
            <input
              autoFocus
              maxLength={120}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="What needs to be done?"
              required
              value={title}
            />
            <small>{title.length}/120</small>
          </label>

          <label className="field field-wide">
            <span>Notes</span>
            <textarea
              maxLength={2000}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="Add context, links, or next steps"
              rows={5}
              value={notes}
            />
            <small>{notes.length}/2000</small>
          </label>

          <div className="field-grid">
            <label className="field">
              <span>Priority</span>
              <select
                onChange={(event) => setPriority(event.target.value)}
                value={priority}
              >
                <option value="none">No priority</option>
                <option value="low">Low</option>
                <option value="medium">Medium</option>
                <option value="high">High</option>
              </select>
            </label>

            <label className="field">
              <span>Due date</span>
              <input
                onInput={(event) => {
                  setDueDate(event.currentTarget.value);
                  setPreciseDueAt(undefined);
                }}
                type="date"
                value={dueDate}
              />
            </label>
          </div>

          {initialTask && (
            <label className="completion-field">
              <input
                checked={isCompleted}
                onChange={(event) => setIsCompleted(event.target.checked)}
                type="checkbox"
              />
              <span>Mark this task as completed</span>
            </label>
          )}

          <footer className="dialog-actions">
            <button className="button button-secondary" onClick={onCancel} type="button">
              Cancel
            </button>
            <button
              className="button button-primary"
              disabled={!title.trim()}
              type="submit"
            >
              {submitLabel}
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
