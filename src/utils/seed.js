/* Inkwell — src/utils/seed.js
   App welcome note with full editor toolbar examples.
   This note is created for new users and safely refreshed only for the built-in welcome note.
*/

import { K, saveNotes } from '../store/storage';
import { countWords } from './helpers';

const WELCOME_STORAGE_VERSION = 'iw_seed_version';
const CURRENT_SEED_VERSION = 'welcome-all-toolbar-examples-v1.2.1';

export function createWelcomeNote() {
  const now = new Date().toISOString();

  const content = `
    <h1>Welcome to Inkwell v1.2.1</h1>

    <p>
      This is the built-in app welcome note. It shows examples for the main editor toolbar tools.
      You can edit it, delete it, pin it, or create your own note with the plus button.
    </p>

    <hr />

    <h2>1. Headings</h2>
    <h1>Heading 1 example</h1>
    <h2>Heading 2 example</h2>
    <h3>Heading 3 example</h3>
    <p>Paragraph text example for normal writing.</p>

    <h2>2. Text style tools</h2>
    <p>
      <strong>Bold text</strong>,
      <em>italic text</em>,
      <u>underlined text</u>,
      <s>strikethrough text</s>,
      <mark>highlighted text</mark>,
      and <code>inline code</code>.
    </p>

    <h2>3. Quote tool</h2>
    <blockquote>
      This is a quote block. Use it for important thoughts, copied ideas, or reminders.
    </blockquote>

    <h2>4. Bullet list tool</h2>
    <ul>
      <li>Bullet item one</li>
      <li>Bullet item two</li>
      <li>Bullet item three</li>
    </ul>

    <h2>5. Number list tool</h2>
    <ol>
      <li>Numbered step one</li>
      <li>Numbered step two</li>
      <li>Numbered step three</li>
    </ol>

    <h2>6. Checklist tool</h2>
    <ul class="check-list" data-type="taskList">
      <li data-checked="false">
        <label>
          <span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="false" aria-checked="false"></span>
          <span class="check-text">Tap checkbox to mark task done</span>
        </label>
      </li>
      <li data-checked="true">
        <label>
          <span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="true" aria-checked="true"></span>
          <span class="check-text">Completed checklist item</span>
        </label>
      </li>
    </ul>

    <h2>7. Code block tool</h2>
    <p>Use code block for commands, scripts, or snippets.</p>
    <pre><code>git add .
git commit -m "update notes"
git push</code></pre>

    <pre><code>function saveIdea(title, body) {
  return {
    title,
    body,
    updatedAt: new Date().toISOString()
  };
}</code></pre>

    <h2>8. Link tool</h2>
    <p>
      Example link:
      <a href="https://github.com/tharindu899/Inkwell" target="_blank" rel="noreferrer">
        Inkwell GitHub repository
      </a>
    </p>

    <h2>9. Table tool</h2>
    <table>
      <thead>
        <tr>
          <th>Toolbar option</th>
          <th>Example</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>Bold</td>
          <td><strong>Important text</strong></td>
        </tr>
        <tr>
          <td>Checklist</td>
          <td>Task tracking</td>
        </tr>
        <tr>
          <td>Code</td>
          <td><code>npm run build</code></td>
        </tr>
      </tbody>
    </table>

    <h2>10. Divider / horizontal line tool</h2>
    <p>Use a divider to separate note sections.</p>
    <hr />

    <h2>11. Align tools</h2>
    <p style="text-align:left;">Left aligned text example.</p>
    <p style="text-align:center;">Center aligned text example.</p>
    <p style="text-align:right;">Right aligned text example.</p>

    <h2>12. Indent and outdent tools</h2>
    <p style="margin-left:24px;">Indented paragraph example.</p>
    <p style="margin-left:48px;">More indented paragraph example.</p>

    <h2>13. Date / time tool</h2>
    <p>Example date text: <strong>2026-05-31 07:00</strong></p>

    <h2>14. Image tool</h2>
    <p>Use the image button to add a photo into a note.</p>
    <div class="image-placeholder">
      <i class="fa-regular fa-image"></i>
      <span>Image placeholder example</span>
    </div>

    <h2>15. Drawing tool</h2>
    <p>Use the drawing option to add hand drawing or sketch content.</p>
    <div class="image-placeholder drawing-placeholder">
      <i class="fa-solid fa-pen-nib"></i>
      <span>Drawing placeholder example</span>
    </div>

    <h2>16. Markdown typing shortcuts</h2>
    <table>
      <thead>
        <tr>
          <th>Type</th>
          <th>Result</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><code># Title</code></td>
          <td>Heading 1</td>
        </tr>
        <tr>
          <td><code>## Title</code></td>
          <td>Heading 2</td>
        </tr>
        <tr>
          <td><code>- item</code></td>
          <td>Bullet list</td>
        </tr>
        <tr>
          <td><code>1. item</code></td>
          <td>Number list</td>
        </tr>
        <tr>
          <td><code>&#96;&#96;&#96;</code></td>
          <td>Code block</td>
        </tr>
      </tbody>
    </table>

    <h2>17. Tags and notebook</h2>
    <p>
      Use the bottom tag selector to add tags. Use the notebook selector to organize notes.
    </p>

    <h2>18. Reading mode and save</h2>
    <p>
      Reading mode hides editing controls for clean reading. Save keeps your note updated.
    </p>

    <p><strong>Tip:</strong> Select any text in this note and tap toolbar buttons to test every option.</p>
  `.trim();

  return {
    id: 'welcome-note',
    title: 'Welcome to Inkwell v1.2.1',
    content,
    markdown: '',
    notebookId: null,
    notebookName: 'Welcome',
    notebookColor: '#E07B39',
    tags: ['welcome', 'guide', 'toolbar', 'examples', 'v1.2.1'],
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

  if (!rawNotes) {
    saveNotes([createWelcomeNote()]);
    localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
    return;
  }

  try {
    const notes = JSON.parse(rawNotes || '[]');

    if (!Array.isArray(notes) || notes.length === 0) {
      saveNotes([createWelcomeNote()]);
      localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
      return;
    }

    // Only refresh the built-in app welcome note. Real user notes are never overwritten.
    const idx = notes.findIndex(n => n.id === 'welcome-note');
    if (idx >= 0 && !alreadySeeded) {
      const current = notes[idx];
      const nextWelcome = createWelcomeNote();
      notes[idx] = {
        ...nextWelcome,
        createdAt: current.createdAt || nextWelcome.createdAt,
        pinned: current.pinned !== undefined ? current.pinned : true,
      };
      saveNotes(notes);
      localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
      return;
    }

    if (!alreadySeeded) {
      localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
    }
  } catch {
    saveNotes([createWelcomeNote()]);
    localStorage.setItem(WELCOME_STORAGE_VERSION, CURRENT_SEED_VERSION);
  }
}
