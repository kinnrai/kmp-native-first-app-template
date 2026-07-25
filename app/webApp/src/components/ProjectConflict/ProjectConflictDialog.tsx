import { useId, useState } from 'react';
import type { KeyboardEvent, MouseEvent } from 'react';
import type {
  WebTaskProject,
  WebTaskProjectConflict,
} from 'shared-logic';
import type { TaskActions } from '../../taskActions.ts';
import { CloseIcon, FolderIcon, WarningIcon } from '../Icons.tsx';
import {
  ProjectEditorDialog,
} from '../ProjectEditor/ProjectEditorDialog.tsx';
import type {
  ProjectEditorValues,
} from '../ProjectEditor/ProjectEditorDialog.tsx';

interface ProjectConflictDialogProps {
  actions: TaskActions;
  conflict: WebTaskProjectConflict;
  onClose(): void;
}

const fieldNames: Record<string, string> = {
  creation: 'creation',
  deletion: 'deletion',
  name: 'name',
  color: 'color',
};

export function ProjectConflictDialog({
  actions,
  conflict,
  onClose,
}: ProjectConflictDialogProps) {
  const headingId = useId();
  const [isMerging, setIsMerging] = useState(false);
  const mergeSource = conflict.local ?? conflict.remote;

  const finish = (action: () => void) => {
    action();
    onClose();
  };

  const merge = ({ name, color }: ProjectEditorValues) => {
    finish(() => {
      actions.mergeProjectConflict(conflict.projectId, name, color);
    });
  };

  if (isMerging && mergeSource) {
    return (
      <ProjectEditorDialog
        heading="Merge project versions"
        initialProject={mergeSource}
        onCancel={() => setIsMerging(false)}
        onSubmit={merge}
        submitLabel="Save merged project"
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
              <p className="eyebrow">Project sync conflict</p>
              <h2 id={headingId}>Choose which project to keep</h2>
            </div>
          </div>
          <button
            aria-label="Close project conflict dialog"
            className="icon-button"
            onClick={onClose}
            type="button"
          >
            <CloseIcon />
          </button>
        </header>

        <p className="conflict-explanation">
          This project changed here and on the server before either version
          could be synchronized. Tasks remain safe while you choose.
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
          <ProjectVersionCard
            emptyLabel="You deleted this project"
            label="On this device"
            project={conflict.local}
          />
          <ProjectVersionCard
            emptyLabel="Deleted on the server"
            label="On the server"
            project={conflict.remote}
          />
        </div>

        <footer className="dialog-actions conflict-actions">
          <button
            className="button button-secondary"
            onClick={() =>
              finish(() => actions.useRemoteProject(conflict.projectId))
            }
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
            onClick={() =>
              finish(() => actions.keepLocalProject(conflict.projectId))
            }
            type="button"
          >
            {conflict.local ? 'Keep my version' : 'Keep my deletion'}
          </button>
        </footer>
      </section>
    </div>
  );
}

interface ProjectVersionCardProps {
  emptyLabel: string;
  label: string;
  project: WebTaskProject | null | undefined;
}

function ProjectVersionCard({
  emptyLabel,
  label,
  project,
}: ProjectVersionCardProps) {
  return (
    <article className="version-card project-version-card">
      <p className="version-label">{label}</p>
      {project ? (
        <div className="project-version">
          <span className={`project-symbol project-color-${project.color}`}>
            <FolderIcon />
          </span>
          <div>
            <h3>{project.name}</h3>
            <p>{project.color} project</p>
          </div>
        </div>
      ) : (
        <div className="deleted-version">
          <span aria-hidden="true">∅</span>
          <p>{emptyLabel}</p>
        </div>
      )}
    </article>
  );
}
