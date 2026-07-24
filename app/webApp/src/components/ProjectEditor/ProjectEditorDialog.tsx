import { useId, useState } from 'react';
import type { FormEvent, KeyboardEvent, MouseEvent } from 'react';
import type { WebTaskProject } from 'shared-logic';
import { CloseIcon, DeleteIcon, FolderIcon } from '../Icons.tsx';

export interface ProjectEditorValues {
  name: string;
  color: string;
}

interface ProjectEditorDialogProps {
  heading: string;
  initialProject?: WebTaskProject;
  onCancel(): void;
  onDelete?(): void;
  onSubmit(values: ProjectEditorValues): void;
  submitLabel: string;
}

const projectColors = [
  { value: 'blue', label: 'Blue' },
  { value: 'green', label: 'Green' },
  { value: 'orange', label: 'Orange' },
  { value: 'purple', label: 'Purple' },
  { value: 'rose', label: 'Rose' },
  { value: 'slate', label: 'Slate' },
] as const;

export function ProjectEditorDialog({
  heading,
  initialProject,
  onCancel,
  onDelete,
  onSubmit,
  submitLabel,
}: ProjectEditorDialogProps) {
  const headingId = useId();
  const descriptionId = useId();
  const [name, setName] = useState(initialProject?.name ?? '');
  const [color, setColor] = useState(initialProject?.color ?? 'blue');

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedName = name.trim();
    if (!normalizedName) return;
    onSubmit({ name: normalizedName, color });
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
        className="dialog-card project-editor-dialog"
        role="dialog"
      >
        <header className="dialog-header">
          <div>
            <p className="eyebrow">Project details</p>
            <h2 id={headingId}>{heading}</h2>
            <p className="sr-only" id={descriptionId}>
              Enter a project name and choose its identifying color.
            </p>
          </div>
          <button
            aria-label="Close project editor"
            className="icon-button"
            onClick={onCancel}
            type="button"
          >
            <CloseIcon />
          </button>
        </header>

        <form className="editor-form" onSubmit={submit}>
          <label className="field field-wide">
            <span>Name</span>
            <input
              autoFocus
              maxLength={80}
              onChange={(event) => setName(event.target.value)}
              placeholder="For example, Website launch"
              required
              value={name}
            />
            <small>{name.length}/80</small>
          </label>

          <fieldset className="color-field">
            <legend>Color</legend>
            <div className="color-options">
              {projectColors.map((option) => (
                <label
                  className={`color-option project-color-${option.value}`}
                  key={option.value}
                >
                  <input
                    checked={color === option.value}
                    name="project-color"
                    onChange={() => setColor(option.value)}
                    type="radio"
                    value={option.value}
                  />
                  <span className="color-swatch">
                    <FolderIcon />
                  </span>
                  <span>{option.label}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <footer className="dialog-actions project-dialog-actions">
            {onDelete && (
              <button
                className="button button-danger"
                onClick={onDelete}
                type="button"
              >
                <DeleteIcon />
                Delete project
              </button>
            )}
            <span className="dialog-action-spacer" />
            <button
              className="button button-secondary"
              onClick={onCancel}
              type="button"
            >
              Cancel
            </button>
            <button
              className="button button-primary"
              disabled={!name.trim()}
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
