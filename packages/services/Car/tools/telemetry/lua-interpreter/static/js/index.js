import {json} from '@codemirror/lang-json';
import {StreamLanguage} from '@codemirror/language';
import {lua} from '@codemirror/legacy-modes/mode/lua';
import {EditorView, placeholder} from '@codemirror/view';
import {nord} from 'cm6-theme-nord';
import {basicSetup} from 'codemirror';

/**
 * Waits until the webpage loads and then it calls the
 * anonymous function, which calls main.
 */
window.onload = () => {
  main();
};

/**
 * Configures the code editors and populating published data buttons.
 */
function main() {
  const /** !HTMLElement */ scriptInput =
      document.getElementById('script-input');

  const /** !HTMLElement */ publishedDataInput =
      document.getElementById('published-data-input');

  const /** !HTMLElement */ savedStateInput =
      document.getElementById('saved-state-input');

  // Adds a custom theme to the CodeMirrors. Makes it so that the CodeMirror
  // takes up the space of its parent container, defines the behavior of the
  // contents if they overflow past the parent container to add a scroll-bar and
  // customizes the scroll-bar appearence.
  const /** !Extension */ customTheme = EditorView.theme({
    '&': {
      height: '100%',
      width: 'auto',
    },
    '.cm-scroller': {overflow: 'auto'},
  });

  configureCodeMirror(scriptInput, [
    basicSetup,
    nord,
    StreamLanguage.define(lua),
    placeholder(scriptInput.placeholder),
    customTheme,
    EditorView.lineWrapping,
  ]);

  const /** !EditorView */ publishedDataEditorView =
      configureCodeMirror(publishedDataInput, [
        basicSetup,
        nord,
        json(),
        placeholder(publishedDataInput.placeholder),
        customTheme,
        EditorView.lineWrapping,
      ]);

  configureCodeMirror(savedStateInput, [
    basicSetup,
    nord,
    json(),
    placeholder(savedStateInput.placeholder),
    customTheme,
    EditorView.lineWrapping,
  ]);

  setupPublishedDataButtonGroup(publishedDataEditorView);
}

/**
 * Adds a CodeMirror with the given extensions in place of the textArea.
 * Additionally links the input of the CodeMirror with the textArea.
 * @param {!HTMLTextAreaElement} textArea The TextArea that is replaced with a
 *     CodeMirror.
 * @param {!Array<!Extension>} extensions Extensions to add to the configuration
 *     of the CodeMirror. Possible extensions are here
 *     https://codemirror.net/docs/extensions/.
 * @return {!EditorView} The newly configured EditorView.
 */
function configureCodeMirror(textArea, extensions) {
  const /** !EditorView */ editor =
      new EditorView({doc: textArea.value, extensions: extensions});

  // Replaces textArea with code editor.
  textArea.parentNode.insertBefore(editor.dom, textArea);

  // The code editor and textArea have no reference of each other,
  // so value must be supplied from the editor when submitting.
  textArea.form.addEventListener('submit', () => {
    textArea.value = editor.state.doc.toString();
  });

  return editor;
}

/**
 * Populates the Published Data button group with buttons corresponding to the
 * available published data types.
 *
 * @param {!EditorView} publishedDataEditorView EditorView for the published
 *     data.
 */
function setupPublishedDataButtonGroup(publishedDataEditorView) {
  fetch('/get_published_data_file_names_and_content', {method: 'POST'})
      .then((response) => {
        // json() is necessary here to transform the response to an object
        // usable by Javascript.
        return response.json();
      })
      .then((responseObject) => {
        const /** !Array<string> */ fileNames = responseObject['file_names'];
        const /** !HTMLElement */ buttonGroup =
            document.getElementById('published-data-button-group');

        for (const [i, fileName] of fileNames.entries()) {
          buttonGroup.appendChild(createPopulatingDataButton(
              fileName, publishedDataEditorView, responseObject[fileName]));

          if (i !== fileNames.length - 1) {
            const /** !HTMLSpanElement */ separator =
                document.createElement('span');
            separator.classList.add('mdc-theme--on-surface');
            separator.textContent = '·';
            buttonGroup.appendChild(separator);
          }
        }
      });
}

/**
 * Creates a button that replaces the contents of the
 * given EditorView on click with the data passed in.
 *
 * Each button follows a similar layout in HTML:
 * <button type="button" class="mdc-button">
 *  <span class="mdc-button__ripple"></span>
 *  <span class="mdc-button__label">memory_publisher</span>
 * </button>
 *
 * @param {string} label The label of the button.
 * @param {!EditorView} editorView EditorView that represents the
 *     target of the data.
 * @param {string} newContent The data used to replace contents of EditorView.
 * @return {!HTMLButtonElement} The newly created button.
 */
function createPopulatingDataButton(label, editorView, newContent) {
  const /** !HTMLButtonElement */ button = document.createElement('button');
  button.type = 'button';
  button.classList.add('mdc-button');

  // Material design component that creates ripple effect onClick.
  const /** !HTMLSpanElement */ rippleSpan = document.createElement('span');
  rippleSpan.classList.add('mdc-button__ripple');

  const /** !HTMLSpanElement */ labelSpan = document.createElement('span');
  labelSpan.textContent = label;
  button.appendChild(rippleSpan);
  button.appendChild(labelSpan);

  button.addEventListener('click', () => {
    editorView.dispatch({
      changes: {
        from: 0,
        to: editorView.state.doc.length,
        insert: newContent,
      },
    });
  })

  return button;
}
