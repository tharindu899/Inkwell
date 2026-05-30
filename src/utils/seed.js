/* Inkwell — src/utils/seed.js
   First-time version: no example/demo notes.
   Only one welcome note is created so users know how to start.
*/

import { K, saveNotes } from '../store/storage';
import { countWords } from './helpers';

const WELCOME_STORAGE_VERSION = 'iw_seed_version';
const CURRENT_SEED_VERSION = 'welcome-only-v1';

export function createWelcomeNote() {
  const now = new Date().toISOString();
  const content = `
    <h2>Welcome to Inkwell</h2>
    <p>This is your first note. You can edit it, delete it, or tap the plus button to create a new note.</p>
    <blockquote>Start writing your ideas, tasks, code snippets, journals, and plans here.</blockquote>
    <ul>
      <li>Create notes from the plus button</li>
      <li>Use the editor toolbar for formatting</li>
      <li>Search your notes anytime</li>
      <li>Your notes save in this browser</li>
    </ul>
  `.trim();

  return {
    id: 'welcome-note',
    title: 'Welcome to Inkwell',
    content,
    notebookId: null,
    notebookName: 'Welcome',
    notebookColor: '#E07B39',
    tags: ['welcome'],
    pinned: true,
    favorite: false,
    createdAt: now,
    updatedAt: now,
    wordCount: countWords(content.replace(/<[^>]*>/g, ' ')),
  };
}

export function seedIfEmpty() {
  const rawNotes = localStorage.getItem(K.notes);
  const alreadySeeded = localStorage.getItem(WELCOME_STORAGE_VERSION) === CURRENT_SEED_VERSION;

  // First-time visitors get one welcome note only. No examples/demo notes are inserted.
  if (!rawNotes) {
    saveNotes([createWelcomeNote()]);
    localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
    return;
  }

  // Do not overwrite real user notes on later visits.
  if (alreadySeeded) return;

  try {
    const notes = JSON.parse(rawNotes || '[]');
    if (Array.isArray(notes) && notes.length === 0) {
      saveNotes([createWelcomeNote()]);
      localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
    }
  } catch {
    saveNotes([createWelcomeNote()]);
    localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
  }
}
