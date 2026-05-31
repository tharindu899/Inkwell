/* Inkwell — src/utils/seed.js
   First-time welcome note with toolbar examples.
   This note is created only for new users or empty local storage.
*/

import { K, saveNotes } from '../store/storage';
import { countWords } from './helpers';

const WELCOME_STORAGE_VERSION = 'iw_seed_version';
const CURRENT_SEED_VERSION = 'welcome-toolbar-v2';

export function createWelcomeNote() {
  const now = new Date().toISOString();

  const content = `
    <h1>Welcome to Inkwell</h1>

    <p>
      This welcome note shows examples for the editor toolbar. You can edit this note,
      delete it, or tap the plus button to create your own.
    </p>

    <hr />

    <h2>Text formatting</h2>
    <p>
      Use the toolbar to make text
      <strong>bold</strong>,
      <em>italic</em>,
      <u>underlined</u>,
      and <s>strikethrough</s>.
    </p>

    <h1>H1 heading example</h1>
    <h2>H2 heading example</h2>
    <h3>H3 heading example</h3>
    <p>This is a normal paragraph. Use paragraph mode for regular notes and journals.</p>

    <blockquote>
      Quote example: keep important thoughts, reminders, or copied ideas here.
    </blockquote>

    <h2>Lists</h2>
    <ul>
      <li>Bullet list item one</li>
      <li>Bullet list item two</li>
      <li>Bullet list item three</li>
    </ul>

    <ol>
      <li>Numbered step one</li>
      <li>Numbered step two</li>
      <li>Numbered step three</li>
    </ol>

    <ul class="check-list" data-type="taskList">
      <li data-checked="false">
        <label>
          <span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="false" aria-checked="false"></span>
          <span class="check-text">Checklist task example</span>
        </label>
      </li>
      <li data-checked="true">
        <label>
          <span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="true" aria-checked="true"></span>
          <span class="check-text">Completed task example</span>
        </label>
      </li>
    </ul>

    <h2>Code and inline code</h2>
    <p>
      Inline code looks like <code>localStorage.setItem()</code> inside a sentence.
    </p>

    <pre><code>function helloInkwell() {
  const note = "Write beautifully";
  console.log(note);
}</code></pre>

    <h2>Table</h2>
    <table>
      <thead>
        <tr>
          <th>Tool</th>
          <th>Example use</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>Heading</td>
          <td>Make sections clear</td>
        </tr>
        <tr>
          <td>Checklist</td>
          <td>Track tasks</td>
        </tr>
        <tr>
          <td>Code block</td>
          <td>Save snippets</td>
        </tr>
      </tbody>
    </table>

    <h2>Link</h2>
    <p>
      Link example:
      <a href="https://github.com/tharindu899/Inkwell" target="_blank" rel="noreferrer">
        Inkwell GitHub repository
      </a>
    </p>

    <h2>Image placeholder</h2>
    <p>
      Use the image tool to insert pictures into your notes.
    </p>
    <div class="image-placeholder">
      <i class="fa-regular fa-image"></i>
      <span>Image example placeholder</span>
    </div>

    <h2>Tags and notebooks</h2>
    <p>
      Add tags from the bottom tag selector and choose a notebook from the notebook selector.
    </p>

    <p><strong>Tip:</strong> Try selecting text, then tap toolbar buttons to format it.</p>
  `.trim();

  return {
    id: 'welcome-note',
    title: 'Welcome to Inkwell',
    content,
    markdown: '',
    notebookId: null,
    notebookName: 'Welcome',
    notebookColor: '#E07B39',
    tags: ['welcome', 'guide', 'toolbar'],
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

  // First-time visitors get one welcome guide note only.
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
